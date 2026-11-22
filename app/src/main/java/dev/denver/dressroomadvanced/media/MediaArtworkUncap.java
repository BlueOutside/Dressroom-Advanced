package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uncaps SystemUI's {@code MediaDataLoader} album-art decode dimensions (stock 180dp x 184dp,
 * ~675px on QHD+), enables high-resolution {@code https://} artwork loading with CDN size upgrades,
 * and upgrades low-resolution ({@code <= 720px}) inline/cached covers via catalog lookup when
 * players omit {@code ALBUM_ART_URI} or supply a 600px legacy thumbnail.
 */
final class MediaArtworkUncap {
    private static final String LOADER_CLASS =
            "com.android.systemui.media.controls.domain.pipeline.MediaDataLoader";
    private static final String RESULT_CLASS =
            "com.android.systemui.media.controls.domain.pipeline.MediaDataLoader$MediaDataLoaderResult";
    private static final String[] IMAGE_LOADER_CLASSES = {
        "com.android.systemui.graphics.ImageLoaderImpl",
        "com.android.systemui.graphics.ImageLoader"
    };
    private static final String[] METADATA_URI_KEYS = {
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    };
    private static final int MIN_TARGET_PX = 1440;
    private static final int MAX_TARGET_PX = 2048;
    private static final int LOW_RES_THRESHOLD_PX = 720;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 3500;
    private static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int CACHE_ENTRIES = 10;

    private static final Pattern MZSTATIC_DIM =
            Pattern.compile(
                    "/(\\d+)x(\\d+)([a-zA-Z0-9_-]*)\\.(jpg|jpeg|png|webp)$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_WH_DIM =
            Pattern.compile("=w(\\d+)-h(\\d+)([a-zA-Z0-9_-]*)$");
    private static final Pattern GOOGLE_S_DIM = Pattern.compile("=s(\\d+)([a-zA-Z0-9_-]*)$");
    private static final Pattern DZCDN_DIM =
            Pattern.compile(
                    "/(\\d+)x(\\d+)(-[^/]+\\.(?:jpg|jpeg|png|webp))$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIDAL_DIM =
            Pattern.compile("/(\\d+)x(\\d+)\\.(jpg|jpeg|png|webp)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNDCDN_DIM =
            Pattern.compile("-(?:large|t\\d+x\\d+)\\.(jpg|jpeg|png)$", Pattern.CASE_INSENSITIVE);

    private static final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Integer> activeTarget = new ThreadLocal<>();
    private static final Map<String, Bitmap> httpCache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(CACHE_ENTRIES + 2, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                            return size() > CACHE_ENTRIES;
                        }
                    });
    private static final Set<String> negativeLookupCache =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(
                            new LinkedHashMap<String, Boolean>(32, 0.75f, true) {
                                @Override
                                protected boolean removeEldestEntry(
                                        Map.Entry<String, Boolean> eldest) {
                                    return size() > 24;
                                }
                            }));
    private static final ExecutorService httpPool =
            Executors.newFixedThreadPool(
                    3,
                    runnable -> {
                        Thread t = new Thread(runnable, "DressroomArtHttp");
                        t.setDaemon(true);
                        return t;
                    });
    private static final LatestCoverRequests<Bitmap> requests = new LatestCoverRequests<>(httpPool, 3);
    private static volatile WeakReference<Context> lastLoaderContext = new WeakReference<>(null);
    private static volatile Method iconGetBitmapMethod;
    private static volatile boolean iconGetBitmapResolved;

    private MediaArtworkUncap() {}

    static void install(DressroomModule owner, ClassLoader loader) {
        if (owner == null || loader == null || !installed.add(loader)) return;
        MediaArtworkRefresh.install(owner, loader);
        try {
            Class<?> loaderClass = Class.forName(LOADER_CLASS, false, loader);
            Field contextField = loaderClass.getDeclaredField("context");
            Field widthField = loaderClass.getDeclaredField("artworkWidth");
            Field heightField = loaderClass.getDeclaredField("artworkHeight");
            contextField.setAccessible(true);
            widthField.setAccessible(true);
            heightField.setAccessible(true);

            for (Constructor<?> ctor : loaderClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                owner.hook(ctor)
                        .setId("dressroom-advanced.media-artwork-uncap-init")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    applyTargetSize(
                                            chain.getThisObject(),
                                            contextField,
                                            widthField,
                                            heightField,
                                            true);
                                    return result;
                                });
            }

            int uriHooks = 0;
            for (Method method : loaderClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 2 || params[0] != Uri.class) continue;
                method.setAccessible(true);
                uriHooks++;
                final int hookIndex = uriHooks;
                owner.hook(method)
                        .setId("dressroom-advanced.media-artwork-uncap-uri-" + hookIndex)
                        .intercept(
                                chain -> {
                                    Object self = chain.getThisObject();
                                    int target =
                                            applyTargetSize(
                                                    self,
                                                    contextField,
                                                    widthField,
                                                    heightField,
                                                    false);
                                    if (chain.getArg(0) instanceof Uri uri && isHttpScheme(uri)) {
                                        return loadHttpArtwork(uri, target);
                                    }
                                    Integer previous = activeTarget.get();
                                    activeTarget.set(target);
                                    try {
                                        return chain.proceed();
                                    } finally {
                                        if (previous == null) activeTarget.remove();
                                        else activeTarget.set(previous);
                                    }
                                });
            }

            installResultUpgradeHook(owner, loader);
            installImageLoaderFallback(owner, loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            owner.report("Media artwork uncap unavailable", error);
        }
    }

    private static void installResultUpgradeHook(DressroomModule owner, ClassLoader loader) {
        try {
            Class<?> resultClass = Class.forName(RESULT_CLASS, false, loader);
            Field artistField = resultClass.getDeclaredField("artist");
            Field songField = resultClass.getDeclaredField("song");
            Field artworkIconField = resultClass.getDeclaredField("artworkIcon");
            Field tokenField = resultClass.getDeclaredField("token");
            artistField.setAccessible(true);
            songField.setAccessible(true);
            artworkIconField.setAccessible(true);
            tokenField.setAccessible(true);

            int count = 0;
            for (Constructor<?> ctor : resultClass.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length > 0
                        && params[params.length - 1]
                                .getName()
                                .endsWith("DefaultConstructorMarker")) {
                    continue;
                }
                ctor.setAccessible(true);
                final int hookIndex = ++count;
                owner.hook(ctor)
                        .setId("dressroom-advanced.media-artwork-result-upgrade-" + hookIndex)
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    Object self = chain.getThisObject();
                                    if (self != null) {
                                        upgradeLowResResult(
                                                self,
                                                artistField,
                                                songField,
                                                artworkIconField,
                                                tokenField);
                                    }
                                    return result;
                                });
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            DressroomModule.note("MediaDataLoaderResult upgrade hook unavailable", error);
        }
    }

    private static void upgradeLowResResult(
            Object self,
            Field artistField,
            Field songField,
            Field artworkIconField,
            Field tokenField) {
        try {
            Context ctx = lastLoaderContext.get();
            String artist = charSeqToString(artistField.get(self));
            String song = charSeqToString(songField.get(self));
            MediaSession.Token token = (MediaSession.Token) tokenField.get(self);
            MediaMetadata metadata = readMetadata(ctx, token);
            String album =
                    metadata != null
                            ? charSeqToString(
                                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))
                            : "";
            String firstArtUri = "";
            if (metadata != null) {
                for (String key : METADATA_URI_KEYS) {
                    String candidateUri = metadata.getString(key);
                    if (candidateUri != null && !candidateUri.isEmpty()) {
                        firstArtUri = candidateUri;
                        break;
                    }
                }
            }
            if (!artist.isEmpty() && (!song.isEmpty() || !album.isEmpty())) {
                String mediaId =
                        metadata != null
                                ? firstNonEmptyString(
                                        metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                                        metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI))
                                : "";
                String canvasUri =
                        metadata != null
                                ? charSeqToString(
                                        metadata.getString(LiveMediaTracker.SPOTIFY_CANVAS_URI_KEY))
                                : "";
                LiveMediaTracker.onLoaderMetadata(
                        ctx,
                        artist,
                        song,
                        album,
                        LiveMediaTracker.packageForToken(ctx, token),
                        firstArtUri,
                        mediaId,
                        canvasUri);
            }

            Icon currentIcon = (Icon) artworkIconField.get(self);
            Bitmap currentBmp = extractIconBitmap(currentIcon, ctx);
            int curW = currentBmp != null ? currentBmp.getWidth() : 0;
            int curH = currentBmp != null ? currentBmp.getHeight() : 0;
            int curMax = Math.max(curW, curH);
            if (curMax > LOW_RES_THRESHOLD_PX) {
                return;
            }
            int targetPx = resolveTargetPx(ctx);
            int minDesired = Math.max(LOW_RES_THRESHOLD_PX, curMax);

            List<String> uris = new ArrayList<>();
            if (metadata != null) {
                for (String key : METADATA_URI_KEYS) {
                    String uriStr = metadata.getString(key);
                    if (uriStr == null || uriStr.isEmpty()) continue;
                    Uri uri = Uri.parse(uriStr);
                    if (isHttpScheme(uri)) {
                        if (!uris.contains(uriStr)) uris.add(uriStr);
                        Bitmap fromUri = getCachedHttpBitmap(uriStr);
                        if (fromUri != null
                                && !fromUri.isRecycled()
                                && Math.max(fromUri.getWidth(), fromUri.getHeight()) > minDesired) {
                            artworkIconField.set(self, Icon.createWithBitmap(fromUri));
                            return;
                        }
                    }
                }
            }

            if (artist.isEmpty() || song.isEmpty()) return;
            Bitmap catalogBmp = getCachedHttpBitmap(catalogKey(artist, song, album));
            if (catalogBmp != null
                    && !catalogBmp.isRecycled()
                    && Math.max(catalogBmp.getWidth(), catalogBmp.getHeight()) > minDesired) {
                artworkIconField.set(self, Icon.createWithBitmap(catalogBmp));
                return;
            }
            var query = new AnimatedCoverProvider.TrackQuery(artist, song, album,
                    LiveMediaTracker.packageForToken(ctx, token), firstArtUri, targetPx);
            requestUriUpgrade(uris, 0, targetPx, minDesired, ctx, token, query)
                    .thenCompose(bitmap -> {
                        if (bitmap != null || !MediaArtworkRefresh.current(ctx, token, query))
                            return CompletableFuture.completedFuture(bitmap);
                        return requestCatalogArtwork(artist, song, album, targetPx, minDesired);
                    })
                    .thenAccept(bitmap -> {
                        if (bitmap != null && !bitmap.isRecycled()) MediaArtworkRefresh.track(ctx, query);
                    });
        } catch (IllegalAccessException | RuntimeException error) {
            DressroomModule.note("MediaDataLoaderResult artwork upgrade", error);
        }
    }

    private static CompletableFuture<Bitmap> requestUriUpgrade(
            List<String> uris, int index, int target, int minSide,
            Context context, MediaSession.Token token, AnimatedCoverProvider.TrackQuery query) {
        if (index >= uris.size()) return CompletableFuture.completedFuture(null);
        return requestHttpArtwork(uris.get(index), target).exceptionally(error -> null)
                .thenCompose(bitmap -> {
                    if (bitmap != null && !bitmap.isRecycled()
                            && Math.max(bitmap.getWidth(), bitmap.getHeight()) > minSide)
                        return CompletableFuture.completedFuture(bitmap);
                    if (!MediaArtworkRefresh.current(context, token, query))
                        return CompletableFuture.completedFuture(null);
                    return requestUriUpgrade(uris, index + 1, target, minSide, context, token, query);
                });
    }

    private static Bitmap extractIconBitmap(Icon icon, Context context) {
        if (icon == null) return null;
        int type = icon.getType();
        if (type != Icon.TYPE_BITMAP && type != Icon.TYPE_ADAPTIVE_BITMAP) return null;
        Method getter = resolveIconGetBitmap();
        if (getter != null) {
            try {
                Object bmp = getter.invoke(icon);
                if (bmp instanceof Bitmap bitmap && !bitmap.isRecycled()) {
                    return bitmap;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (context != null) {
            try {
                Drawable d = icon.loadDrawable(context);
                if (d instanceof BitmapDrawable bd
                        && bd.getBitmap() != null
                        && !bd.getBitmap().isRecycled()) {
                    return bd.getBitmap();
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Method resolveIconGetBitmap() {
        if (iconGetBitmapResolved) return iconGetBitmapMethod;
        synchronized (MediaArtworkUncap.class) {
            if (iconGetBitmapResolved) return iconGetBitmapMethod;
            try {
                Method m = Icon.class.getDeclaredMethod("getBitmap");
                m.setAccessible(true);
                iconGetBitmapMethod = m;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            iconGetBitmapResolved = true;
            return iconGetBitmapMethod;
        }
    }

    private static MediaMetadata readMetadata(Context context, MediaSession.Token token) {
        if (context == null || token == null) return null;
        try {
            return new MediaController(context, token).getMetadata();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String charSeqToString(Object value) {
        return value instanceof CharSequence cs ? cs.toString().trim() : "";
    }

    private static String firstNonEmptyString(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static boolean isHttpScheme(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    private static Bitmap getCachedHttpBitmap(String key) {
        if (key == null) return null;
        Bitmap cached = httpCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (cached != null) {
            httpCache.remove(key);
        }
        return null;
    }

    private static Bitmap loadHttpArtwork(Uri uri, int targetPx) {
        String key = uri.toString();
        Bitmap cached = getCachedHttpBitmap(key);
        if (cached != null) return cached;
        Context context = lastLoaderContext.get();
        requestHttpArtwork(key, targetPx).thenAccept(bitmap -> {
            if (bitmap != null && !bitmap.isRecycled()) MediaArtworkRefresh.uri(context, key);
        });
        return null;
    }

    private static CompletableFuture<Bitmap> requestHttpArtwork(String key, int targetPx) {
        Bitmap cached = getCachedHttpBitmap(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return requests.request(key, () -> {
            Bitmap bitmap = downloadAndDecodeWithUpgrade(key, targetPx);
            if (bitmap != null && !bitmap.isRecycled()) httpCache.put(key, bitmap);
            return bitmap;
        });
    }

    private static String catalogKey(String artist, String song, String album) {
        return "catalog|"
                        + normalizeForMatch(primaryArtist(artist))
                        + "|"
                        + normalizeForMatch(song)
                        + "|"
                        + normalizeForMatch(album);
    }

    private static CompletableFuture<Bitmap> requestCatalogArtwork(
            String artist, String song, String album, int targetPx, int minSideExclusive) {
        String searchKey = catalogKey(artist, song, album);
        Bitmap cached = getCachedHttpBitmap(searchKey);
        if (cached != null
                && Math.max(cached.getWidth(), cached.getHeight()) > minSideExclusive) {
            return CompletableFuture.completedFuture(cached);
        }
        if (negativeLookupCache.contains(searchKey)) {
            return CompletableFuture.completedFuture(null);
        }
        return requests.request(searchKey, () -> {
            Bitmap bitmap = fetchCatalogArtwork(artist, song, album, targetPx, minSideExclusive);
            if (bitmap != null && !bitmap.isRecycled()) httpCache.put(searchKey, bitmap);
            else negativeLookupCache.add(searchKey);
            return bitmap;
        });
    }

    private static Bitmap fetchCatalogArtwork(
            String artist, String song, String album, int targetPx, int minSideExclusive) {
        String query = cleanSearchQuery(artist, song);
        if (query.isEmpty()) return null;

        Bitmap itunesBmp =
                queryItunesArtwork(query, artist, song, album, targetPx, minSideExclusive);
        if (itunesBmp != null) return itunesBmp;

        return queryDeezerArtwork(query, artist, song, album, targetPx, minSideExclusive);
    }

    private static Bitmap queryItunesArtwork(
            String query,
            String artist,
            String song,
            String album,
            int targetPx,
            int minSideExclusive) {
        try {
            String url =
                    "https://itunes.apple.com/search?term="
                            + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                            + "&entity=song&limit=5";
            String json = downloadText(url);
            if (json == null || json.isEmpty()) return null;
            JSONArray results = new JSONObject(json).optJSONArray("results");
            if (results == null) return null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                String rArtist = item.optString("artistName", "");
                String rTrack = item.optString("trackName", "");
                String rAlbum = item.optString("collectionName", "");
                if (!matchesTrack(artist, song, album, rArtist, rTrack, rAlbum)) continue;
                String art100 = item.optString("artworkUrl100", "");
                if (art100.isEmpty()) continue;
                String hiResUrl = upgradeArtworkUrl(art100, targetPx);
                Bitmap bmp = downloadAndDecodeSingle(hiResUrl, targetPx);
                if (bmp != null
                        && !bmp.isRecycled()
                        && Math.max(bmp.getWidth(), bmp.getHeight()) > minSideExclusive) {
                    return bmp;
                }
                break;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Bitmap queryDeezerArtwork(
            String query,
            String artist,
            String song,
            String album,
            int targetPx,
            int minSideExclusive) {
        try {
            String url =
                    "https://api.deezer.com/search?q="
                            + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                            + "&limit=5";
            String json = downloadText(url);
            if (json == null || json.isEmpty()) return null;
            JSONArray data = new JSONObject(json).optJSONArray("data");
            if (data == null) return null;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject artistObj = item.optJSONObject("artist");
                JSONObject albumObj = item.optJSONObject("album");
                String rArtist = artistObj != null ? artistObj.optString("name", "") : "";
                String rTrack = item.optString("title", "");
                String rAlbum = albumObj != null ? albumObj.optString("title", "") : "";
                if (!matchesTrack(artist, song, album, rArtist, rTrack, rAlbum)) continue;
                String coverUrl =
                        albumObj != null
                                ? albumObj.optString(
                                        "cover_xl", albumObj.optString("cover_big", ""))
                                : "";
                if (coverUrl.isEmpty()) continue;
                String hiResUrl = upgradeArtworkUrl(coverUrl, targetPx);
                Bitmap bmp = downloadAndDecodeSingle(hiResUrl, targetPx);
                if (bmp != null
                        && !bmp.isRecycled()
                        && Math.max(bmp.getWidth(), bmp.getHeight()) > minSideExclusive) {
                    return bmp;
                }
                break;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String primaryArtist(String artist) {
        return AnimatedCoverProvider.MediaTextNormalize.primaryArtist(artist);
    }

    static String cleanSearchQuery(String artist, String song) {
        return AnimatedCoverProvider.MediaTextNormalize.cleanSearchQuery(artist, song);
    }

    static String normalizeForMatch(String input) {
        return AnimatedCoverProvider.MediaTextNormalize.normalize(input);
    }

    static boolean matchesTrack(
            String queryArtist,
            String querySong,
            String queryAlbum,
            String candidateArtist,
            String candidateSong,
            String candidateAlbum) {
        return AnimatedCoverProvider.MediaTextNormalize.matchesTrack(
                queryArtist, querySong, queryAlbum, candidateArtist, candidateSong, candidateAlbum);
    }

    private static Bitmap downloadAndDecodeWithUpgrade(String originalUrl, int targetPx) {
        String upgraded = upgradeArtworkUrl(originalUrl, targetPx);
        if (!upgraded.equals(originalUrl)) {
            Bitmap hiRes = downloadAndDecodeSingle(upgraded, targetPx);
            if (hiRes != null) {
                Log.i(
                        DressroomModule.TAG,
                        "Loaded upgraded HTTPS media artwork: "
                                + hiRes.getWidth()
                                + "x"
                                + hiRes.getHeight());
                return hiRes;
            }
        }
        Bitmap direct = downloadAndDecodeSingle(originalUrl, targetPx);
        if (direct != null) {
            Log.i(
                    DressroomModule.TAG,
                    "Loaded HTTPS media artwork: "
                            + direct.getWidth()
                            + "x"
                            + direct.getHeight());
        }
        return direct;
    }

    static String upgradeArtworkUrl(String rawUrl, int targetPx) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";
        int desired = Math.max(MIN_TARGET_PX, Math.min(MAX_TARGET_PX, targetPx));
        String host;
        try {
            host = java.net.URI.create(rawUrl).getHost();
        } catch (IllegalArgumentException ignored) {
            return rawUrl;
        }
        if (host == null) return rawUrl;
        String lowerHost = host.toLowerCase(Locale.ROOT);
        int qIdx = rawUrl.indexOf('?');
        String base = qIdx >= 0 ? rawUrl.substring(0, qIdx) : rawUrl;
        String query = qIdx >= 0 ? rawUrl.substring(qIdx) : "";

        if (lowerHost.endsWith(".mzstatic.com") || lowerHost.equals("mzstatic.com")) {
            int appleDim = Math.min(2000, desired);
            Matcher m = MZSTATIC_DIM.matcher(base);
            if (m.find()) {
                int w = parsePositiveInt(m.group(1));
                int h = parsePositiveInt(m.group(2));
                if (w < appleDim || h < appleDim) {
                    return m.replaceFirst(
                                    "/" + appleDim + "x" + appleDim + m.group(3) + "." + m.group(4))
                            + query;
                }
            }
            return rawUrl;
        }

        if (lowerHost.endsWith(".googleusercontent.com") || lowerHost.endsWith(".ggpht.com")) {
            int googleDim = Math.min(1600, desired);
            Matcher wh = GOOGLE_WH_DIM.matcher(base);
            if (wh.find()) {
                int w = parsePositiveInt(wh.group(1));
                int h = parsePositiveInt(wh.group(2));
                if (w < googleDim || h < googleDim) {
                    return wh.replaceFirst("=w" + googleDim + "-h" + googleDim + wh.group(3))
                            + query;
                }
                return rawUrl;
            }
            Matcher s = GOOGLE_S_DIM.matcher(base);
            if (s.find() && parsePositiveInt(s.group(1)) < googleDim) {
                return s.replaceFirst("=s" + googleDim + s.group(2)) + query;
            }
            return rawUrl;
        }

        if (lowerHost.endsWith(".dzcdn.net")) {
            Matcher m = DZCDN_DIM.matcher(base);
            if (m.find()
                    && (parsePositiveInt(m.group(1)) < 1400
                            || parsePositiveInt(m.group(2)) < 1400)) {
                return m.replaceFirst("/1400x1400" + m.group(3)) + query;
            }
            return rawUrl;
        }

        if (lowerHost.equals("resources.tidal.com")) {
            Matcher m = TIDAL_DIM.matcher(base);
            if (m.find()
                    && (parsePositiveInt(m.group(1)) < 1280
                            || parsePositiveInt(m.group(2)) < 1280)) {
                return m.replaceFirst("/1280x1280." + m.group(3)) + query;
            }
            return rawUrl;
        }

        if (lowerHost.endsWith(".sndcdn.com")) {
            Matcher m = SNDCDN_DIM.matcher(base);
            if (m.find()) {
                return m.replaceFirst("-t1080x1080." + m.group(1)) + query;
            }
            return rawUrl;
        }

        if (lowerHost.equals("i.scdn.co") && base.contains("/image/ab67616d00001e02")) {
            return base.replace("/image/ab67616d00001e02", "/image/ab67616d0000b273") + query;
        }

        return rawUrl;
    }

    private static int parsePositiveInt(String text) {
        if (text == null) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String downloadText(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setUseCaches(true);
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return null;
            try (InputStream in = conn.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream(8192)) {
                byte[] buf = new byte[4096];
                int total = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_JSON_BYTES) return null;
                    out.write(buf, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Bitmap downloadAndDecodeSingle(String urlString, int targetPx) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                return null;
            }
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setUseCaches(true);
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            int contentLength = conn.getContentLength();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                return null;
            }
            byte[] bytes;
            try (InputStream in = conn.getInputStream();
                    ByteArrayOutputStream out =
                            new ByteArrayOutputStream(
                                    contentLength > 0 ? Math.min(contentLength, 524288) : 65536)) {
                byte[] buf = new byte[16384];
                int total = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        return null;
                    }
                    out.write(buf, 0, read);
                }
                bytes = out.toByteArray();
            }
            if (bytes.length == 0) return null;
            ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
            return ImageDecoder.decodeBitmap(
                    source,
                    (decoder, info, src) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        decoder.setMutableRequired(false);
                        int w = info.getSize().getWidth();
                        int h = info.getSize().getHeight();
                        if (w > targetPx || h > targetPx) {
                            float scale = Math.min(targetPx / (float) w, targetPx / (float) h);
                            decoder.setTargetSize(
                                    Math.max(1, Math.round(w * scale)),
                                    Math.max(1, Math.round(h * scale)));
                        }
                    });
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void installImageLoaderFallback(DressroomModule owner, ClassLoader loader) {
        int count = 0;
        for (String name : IMAGE_LOADER_CLASSES) {
            Class<?> imageLoaderClass;
            try {
                imageLoaderClass = Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                continue;
            }
            if (imageLoaderClass.isInterface()) continue;
            for (Method method : imageLoaderClass.getDeclaredMethods()) {
                if (!"loadBitmap".equals(method.getName())
                        || Modifier.isStatic(method.getModifiers())
                        || Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 4 || params[1] != int.class || params[2] != int.class) continue;
                method.setAccessible(true);
                final int paramCount = params.length;
                final int hookIndex = ++count;
                owner.hook(method)
                        .setId("dressroom-advanced.media-artwork-uncap-decode-" + hookIndex)
                        .intercept(
                                chain -> {
                                    Integer target = activeTarget.get();
                                    if (target == null || target <= 0) return chain.proceed();
                                    int reqW = ((Number) chain.getArg(1)).intValue();
                                    int reqH = ((Number) chain.getArg(2)).intValue();
                                    if (reqW >= target && reqH >= target) return chain.proceed();
                                    Object[] args = new Object[paramCount];
                                    for (int i = 0; i < paramCount; i++) {
                                        args[i] = chain.getArg(i);
                                    }
                                    args[1] = Math.max(reqW, target);
                                    args[2] = Math.max(reqH, target);
                                    return chain.proceed(args);
                                });
            }
        }
    }

    private static int applyTargetSize(
            Object self,
            Field contextField,
            Field widthField,
            Field heightField,
            boolean logChange) {
        int target = MAX_TARGET_PX;
        if (self == null) return target;
        try {
            if (contextField.get(self) instanceof Context ctx) {
                lastLoaderContext = new WeakReference<>(ctx);
                target = resolveTargetPx(ctx);
            }
            int prevW = widthField.getInt(self);
            int prevH = heightField.getInt(self);
            if (prevW < target || prevH < target) {
                int nextW = Math.max(prevW, target);
                int nextH = Math.max(prevH, target);
                widthField.setInt(self, nextW);
                heightField.setInt(self, nextH);
                if (logChange) {
                    Log.i(
                            DressroomModule.TAG,
                            "Uncapped SystemUI MediaDataLoader artwork size: "
                                    + prevW
                                    + "x"
                                    + prevH
                                    + " -> "
                                    + nextW
                                    + "x"
                                    + nextH);
                }
            }
        } catch (IllegalAccessException | RuntimeException error) {
            DressroomModule.note("MediaDataLoader artwork field update", error);
        }
        return target;
    }

    static int resolveTargetPx(Context context) {
        if (context == null || context.getResources() == null) return MAX_TARGET_PX;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        if (dm == null) return MAX_TARGET_PX;
        int longestSide = Math.max(dm.widthPixels, dm.heightPixels);
        return Math.max(MIN_TARGET_PX, Math.min(MAX_TARGET_PX, longestSide));
    }
}
