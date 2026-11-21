package dev.denver.dressroomadvanced;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves per-track Spotify Canvas looping portrait {@code .cnvs.mp4} motion covers across any
 * media player. Uses Spotify's {@code CANVAS_URI} / {@code MEDIA_ID} metadata fast-paths when
 * available, and anonymous Web Player TOTP auth + Pathfinder v2 {@code searchDesktop} + public
 * Canvas endpoint otherwise, requiring neither user login nor the Spotify app.
 */
final class SpotifyCanvasAnimatedCoverProvider implements AnimatedCoverProvider {
    static final String PROVIDER_ID = "spotify_canvas";

    private static final String SECRET_DICT_URL =
            "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json";
    private static final String SERVER_TIME_URL = "https://open.spotify.com/api/server-time";
    private static final String TOKEN_URL_BASE = "https://open.spotify.com/api/token";
    private static final String PATHFINDER_URL =
            "https://api-partner.spotify.com/pathfinder/v2/query";
    private static final String CANVAS_API_URL_PREFIX =
            "https://spotify-canva.vercel.app/api/canvas?trackId=";

    private static final int CONNECT_TIMEOUT_MS = 3500;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final long SECRET_REFRESH_INTERVAL_MS = 6L * 3600L * 1000L;
    private static final long TOKEN_REFRESH_MARGIN_MS = 60_000L;
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";

    private static final Object AUTH_LOCK = new Object();
    private static SpotifyCanvasResolver.TotpSecret cachedSecret;
    private static long secretFetchedAtMs;
    private static SpotifyCanvasResolver.AnonymousToken cachedToken;

    private record HttpResult(int status, String body) {
        boolean isOk() {
            return status >= 200 && status < 300 && body != null;
        }
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public AnimatedCoverAsset resolve(
            TrackQuery query, boolean allowNetwork, AnimatedCoverCache cache) {
        if (query == null || !query.isValid() || cache == null) return null;

        String trackKey = SpotifyCanvasResolver.trackCacheKey(query);
        String directTrackId = SpotifyCanvasResolver.extractSpotifyTrackId(query.mediaId());
        String directTrackAlias = directTrackId.isEmpty() ? "" : PROVIDER_ID + "|id|" + directTrackId;
        String directCanvasUrl = SpotifyCanvasResolver.unwrapCanvasMediaUri(query.canvasUri());
        String directUriAlias =
                directCanvasUrl.isEmpty()
                        ? ""
                        : PROVIDER_ID + "|uri|" + AnimatedCoverCache.sha256Hex(directCanvasUrl);

        AnimatedCoverAsset cached =
                checkCachedAliases(cache, directUriAlias, directTrackAlias, trackKey);
        if (cached != null) return cached;

        if (!allowNetwork) return null;

        boolean hasDirectCanvasUrl = !directCanvasUrl.isEmpty();
        if (!hasDirectCanvasUrl) {
            if (!directTrackAlias.isEmpty() && cache.isNegative(directTrackAlias)) return null;
            if (directTrackAlias.isEmpty() && !trackKey.isEmpty() && cache.isNegative(trackKey)) {
                return null;
            }
        }

        if (hasDirectCanvasUrl) {
            String assetKey =
                    PROVIDER_ID
                            + ":"
                            + SpotifyCanvasResolver.deriveCanvasAssetId("", directCanvasUrl);
            AnimatedCoverAsset downloaded =
                    obtainOrDownloadCanvas(directCanvasUrl, assetKey, cache);
            if (downloaded != null) {
                recordAliases(
                        cache,
                        trackKey,
                        "",
                        directTrackAlias,
                        directUriAlias,
                        downloaded.cacheKey());
                Log.i(
                        DressroomModule.TAG,
                        "Cached Spotify Canvas ("
                                + downloaded.videoWidth()
                                + "x"
                                + downloaded.videoHeight()
                                + ") from session URI for "
                                + describeTrack(query));
                return downloaded;
            }
        }

        Set<String> candidateTrackIds = new LinkedHashSet<>();
        if (!directTrackId.isEmpty()) {
            candidateTrackIds.add(directTrackId);
        }

        boolean searchConfirmedOk = false;
        boolean hadTransientError = false;
        if (candidateTrackIds.isEmpty()) {
            String searchTerm =
                    MediaTextNormalize.cleanSearchQuery(
                            query.artist(), query.song().isEmpty() ? query.album() : query.song());
            if (!searchTerm.isEmpty()) {
                HttpResult searchResult = executePathfinderSearch(searchTerm);
                if (searchResult != null && searchResult.isOk()) {
                    searchConfirmedOk = true;
                    List<SpotifyCanvasResolver.TrackCandidate> matches =
                            SpotifyCanvasResolver.parseSearchTrackCandidates(
                                    searchResult.body(), query);
                    int limit = Math.min(3, matches.size());
                    for (int i = 0; i < limit; i++) {
                        candidateTrackIds.add(matches.get(i).trackId());
                    }
                } else {
                    hadTransientError = true;
                }
            }
        }

        int confirmedEmptyCount = 0;
        for (String trackId : candidateTrackIds) {
            if (Thread.currentThread().isInterrupted()) return null;
            String candidateAlias = PROVIDER_ID + "|id|" + trackId;
            AnimatedCoverAsset byId = cache.getByAlias(candidateAlias);
            if (byId != null) {
                recordAliases(
                        cache,
                        trackKey,
                        candidateAlias,
                        directTrackAlias,
                        directUriAlias,
                        byId.cacheKey());
                return byId;
            }

            HttpResult canvasResult =
                    httpGet(
                            CANVAS_API_URL_PREFIX + trackId,
                            null,
                            null,
                            "application/json",
                            MAX_JSON_BYTES);
            if (canvasResult == null || !canvasResult.isOk()) {
                hadTransientError = true;
                continue;
            }

            SpotifyCanvasResolver.CanvasVideo canvas =
                    SpotifyCanvasResolver.parseCanvasResponse(canvasResult.body());
            if (canvas == null) {
                confirmedEmptyCount++;
                continue;
            }

            String assetKey =
                    PROVIDER_ID
                            + ":"
                            + SpotifyCanvasResolver.deriveCanvasAssetId(
                                    canvas.canvasId(), canvas.videoUrl());
            AnimatedCoverAsset asset =
                    obtainOrDownloadCanvas(canvas.videoUrl(), assetKey, cache);
            if (asset != null) {
                recordAliases(
                        cache,
                        trackKey,
                        candidateAlias,
                        directTrackAlias,
                        directUriAlias,
                        asset.cacheKey());
                Log.i(
                        DressroomModule.TAG,
                        "Cached Spotify Canvas ("
                                + asset.videoWidth()
                                + "x"
                                + asset.videoHeight()
                                + ") for "
                                + describeTrack(query));
                return asset;
            }
            hadTransientError = true;
        }

        if (!hadTransientError
                && (confirmedEmptyCount > 0 || (candidateTrackIds.isEmpty() && searchConfirmedOk))) {
            if (!trackKey.isEmpty()) cache.markNegative(trackKey);
            if (!directTrackAlias.isEmpty() && confirmedEmptyCount > 0) {
                cache.markNegative(directTrackAlias);
            }
            Log.i(DressroomModule.TAG, "No Spotify Canvas found for " + describeTrack(query));
        }
        return null;
    }

    private static AnimatedCoverAsset checkCachedAliases(
            AnimatedCoverCache cache,
            String directUriAlias,
            String directTrackAlias,
            String trackKey) {
        String[] aliases = {directUriAlias, directTrackAlias, trackKey};
        for (String alias : aliases) {
            if (alias == null || alias.isEmpty()) continue;
            AnimatedCoverAsset hit = cache.getByAlias(alias);
            if (hit != null) {
                recordAliases(cache, trackKey, "", directTrackAlias, directUriAlias, hit.cacheKey());
                return hit;
            }
        }
        return null;
    }

    private static void recordAliases(
            AnimatedCoverCache cache,
            String trackKey,
            String candidateAlias,
            String directTrackAlias,
            String directUriAlias,
            String assetKey) {
        if (trackKey != null && !trackKey.isEmpty()) {
            cache.putAlias(trackKey, assetKey);
            cache.clearNegative(trackKey);
        }
        if (candidateAlias != null && !candidateAlias.isEmpty()) {
            cache.putAlias(candidateAlias, assetKey);
            cache.clearNegative(candidateAlias);
        }
        if (directTrackAlias != null && !directTrackAlias.isEmpty()) {
            cache.putAlias(directTrackAlias, assetKey);
            cache.clearNegative(directTrackAlias);
        }
        if (directUriAlias != null && !directUriAlias.isEmpty()) {
            cache.putAlias(directUriAlias, assetKey);
        }
    }

    private static AnimatedCoverAsset obtainOrDownloadCanvas(
            String videoUrl, String assetKey, AnimatedCoverCache cache) {
        AnimatedCoverAsset existing = cache.getByAssetKey(assetKey);
        if (existing != null) return existing;
        File temp;
        try {
            temp = cache.createTempFile();
        } catch (IOException error) {
            return null;
        }
        if (!downloadFile(videoUrl, temp, AnimatedCoverCache.MAX_SINGLE_FILE_BYTES)) {
            temp.delete();
            return null;
        }
        int[] dims = probeVideoDimensions(temp);
        int width = dims[0];
        int height = dims[1];
        return cache.commitAsset(
                PROVIDER_ID, assetKey, temp, videoUrl, width, height, height >= width);
    }

    private static int[] probeVideoDimensions(File videoFile) {
        int width = 720;
        int height = 1280;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(videoFile.getAbsolutePath());
            int w =
                    parsePositiveInt(
                            retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h =
                    parsePositiveInt(
                            retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot =
                    parsePositiveInt(
                            retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (w > 0 && h > 0) {
                if (rot == 90 || rot == 270) {
                    width = h;
                    height = w;
                } else {
                    width = w;
                    height = h;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return new int[] {width, height};
    }

    private static int parsePositiveInt(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static HttpResult executePathfinderSearch(String searchTerm) {
        String body = SpotifyCanvasResolver.buildPathfinderSearchRequestBody(searchTerm, 5);
        SpotifyCanvasResolver.AnonymousToken token = getAnonymousToken(false);
        if (token == null) return null;
        HttpResult result = postPathfinder(body, token.accessToken());
        if (result != null && result.status() == 401) {
            SpotifyCanvasResolver.AnonymousToken refreshed = getAnonymousToken(true);
            if (refreshed == null) return result;
            result = postPathfinder(body, refreshed.accessToken());
        }
        return result;
    }

    private static HttpResult postPathfinder(String jsonBody, String accessToken) {
        return httpPostJson(
                PATHFINDER_URL,
                jsonBody,
                "Bearer " + accessToken,
                "https://open.spotify.com",
                "https://open.spotify.com/",
                MAX_JSON_BYTES);
    }

    private static SpotifyCanvasResolver.AnonymousToken getAnonymousToken(boolean forceRefresh) {
        synchronized (AUTH_LOCK) {
            long now = System.currentTimeMillis();
            if (!forceRefresh
                    && cachedToken != null
                    && now + TOKEN_REFRESH_MARGIN_MS < cachedToken.expiresAtMs()) {
                return cachedToken;
            }
            SpotifyCanvasResolver.TotpSecret secret = obtainSecret(forceRefresh, now);
            long serverSeconds = fetchServerTimeSeconds(now);
            String code = SpotifyCanvasResolver.computeTotp(secret.secretBytes(), serverSeconds);
            if (code.isEmpty()) return null;

            String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
            String url =
                    TOKEN_URL_BASE
                            + "?reason=init&productType=mobile-web-player&totp="
                            + encodedCode
                            + "&totpVer="
                            + secret.version()
                            + "&totpServer="
                            + encodedCode;
            HttpResult tokenResp =
                    httpGet(
                            url,
                            "https://open.spotify.com/",
                            "https://open.spotify.com/",
                            "application/json",
                            MAX_JSON_BYTES);
            if (tokenResp == null || !tokenResp.isOk()) return null;
            SpotifyCanvasResolver.AnonymousToken parsed =
                    SpotifyCanvasResolver.parseAnonymousToken(
                            tokenResp.body(), System.currentTimeMillis());
            if (parsed != null) cachedToken = parsed;
            return parsed;
        }
    }

    private static SpotifyCanvasResolver.TotpSecret obtainSecret(boolean forceRefresh, long nowMs) {
        if (!forceRefresh
                && cachedSecret != null
                && nowMs - secretFetchedAtMs < SECRET_REFRESH_INTERVAL_MS) {
            return cachedSecret;
        }
        HttpResult res = httpGet(SECRET_DICT_URL, null, null, "application/json", MAX_JSON_BYTES);
        if (res != null && res.isOk()) {
            SpotifyCanvasResolver.TotpSecret fetched =
                    SpotifyCanvasResolver.parseLatestSecret(res.body());
            if (fetched != null) {
                cachedSecret = fetched;
                secretFetchedAtMs = nowMs;
                return fetched;
            }
        }
        if (cachedSecret != null) return cachedSecret;
        cachedSecret =
                new SpotifyCanvasResolver.TotpSecret(
                        SpotifyCanvasResolver.FALLBACK_TOTP_VERSION,
                        SpotifyCanvasResolver.FALLBACK_TOTP_SECRET);
        secretFetchedAtMs = nowMs;
        return cachedSecret;
    }

    private static long fetchServerTimeSeconds(long fallbackNowMs) {
        HttpResult res =
                httpGet(
                        SERVER_TIME_URL,
                        "https://open.spotify.com/",
                        "https://open.spotify.com/",
                        "application/json",
                        MAX_JSON_BYTES);
        if (res != null && res.isOk()) {
            long sec = SpotifyCanvasResolver.parseServerTimeSeconds(res.body());
            if (sec > 0L) return sec;
        }
        return fallbackNowMs / 1000L;
    }

    private static HttpResult httpGet(
            String urlString, String origin, String referer, String accept, int maxBytes) {
        if (!AppleHlsResolver.isHttpsUrl(urlString)) return null;
        HttpURLConnection conn = null;
        try {
            conn = openHttpsConnection(urlString, "GET", origin, referer, accept);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return new HttpResult(status, null);
            String body = readResponseBody(conn.getInputStream(), maxBytes);
            return new HttpResult(status, body);
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpResult httpPostJson(
            String urlString,
            String jsonBody,
            String authorization,
            String origin,
            String referer,
            int maxBytes) {
        if (!AppleHlsResolver.isHttpsUrl(urlString)) return null;
        HttpURLConnection conn = null;
        try {
            conn = openHttpsConnection(urlString, "POST", origin, referer, "application/json");
            if (authorization != null && !authorization.isEmpty()) {
                conn.setRequestProperty("Authorization", authorization);
            }
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return new HttpResult(status, null);
            String body = readResponseBody(conn.getInputStream(), maxBytes);
            return new HttpResult(status, body);
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readResponseBody(InputStream in, int maxBytes) throws IOException {
        try (InputStream stream = in;
                ByteArrayOutputStream out = new ByteArrayOutputStream(8192)) {
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) return null;
                total += read;
                if (total > maxBytes) return null;
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static boolean downloadFile(String urlString, File target, long maxBytes) {
        if (!AppleHlsResolver.isHttpsUrl(urlString)) return false;
        HttpURLConnection conn = null;
        try {
            conn = openHttpsConnection(urlString, "GET", null, null, "video/mp4,*/*;q=0.8");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return false;
            long contentLength = conn.getContentLengthLong();
            if (contentLength > maxBytes) return false;

            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
                    FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[32768];
                long total = 0L;
                int read;
                while ((read = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return false;
                    total += read;
                    if (total > maxBytes) return false;
                    out.write(buf, 0, read);
                }
                out.getFD().sync();
                return total > 1024L;
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openHttpsConnection(
            String urlString, String method, String origin, String referer, String accept)
            throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        if (accept != null && !accept.isEmpty()) conn.setRequestProperty("Accept", accept);
        if (origin != null && !origin.isEmpty()) conn.setRequestProperty("Origin", origin);
        if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);
        return conn;
    }

    private static String describeTrack(TrackQuery query) {
        String label = query.song().isEmpty() ? query.album() : query.song();
        return query.artist() + " - " + label;
    }
}
