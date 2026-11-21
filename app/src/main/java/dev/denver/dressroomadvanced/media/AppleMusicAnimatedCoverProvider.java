package dev.denver.dressroomadvanced;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Apple Music {@code editorialVideo} motion covers via public iTunes catalog search +
 * public Apple Music SSR {@code serialized-server-data} HTML + single-file fMP4 extraction. Works
 * for any media player whose active metadata matches an Apple Music catalog release.
 */
final class AppleMusicAnimatedCoverProvider implements AnimatedCoverProvider {
    static final String PROVIDER_ID = "apple_music";

    private static final int CONNECT_TIMEOUT_MS = 3500;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_HTML_BYTES = 3 * 1024 * 1024;
    private static final int MAX_M3U8_BYTES = 256 * 1024;
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";

    private static volatile Boolean hevcHardwareSupported;

    private record CatalogMatch(String collectionId, String collectionViewUrl) {}

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public AnimatedCoverAsset resolve(
            TrackQuery query, boolean allowNetwork, AnimatedCoverCache cache) {
        if (query == null || !query.isValid() || cache == null) return null;

        String trackKey = query.normalizedTrackKey();
        String albumKey = query.normalizedAlbumKey();

        AnimatedCoverAsset cached = checkCachedAliases(cache, trackKey, albumKey);
        if (cached != null) return cached;

        if (!allowNetwork) return null;
        if (cache.isNegative(trackKey) || (!albumKey.isEmpty() && cache.isNegative(albumKey))) {
            return null;
        }

        Set<String> checkedIds = new HashSet<>();
        List<CatalogMatch> itunesMatches = lookupItunesMatches(query);
        AnimatedCoverAsset fromItunes =
                resolveFromCandidateMatches(itunesMatches, checkedIds, query, trackKey, albumKey, cache);
        if (fromItunes != null) return fromItunes;

        // Apple's legacy iTunes Search API omits or isolates many EPs/explicit releases; fall back
        // to Apple Music's public web search SSR catalog when iTunes yielded no motion cover.
        List<CatalogMatch> webMatches = lookupWebSearchMatches(query);
        AnimatedCoverAsset fromWeb =
                resolveFromCandidateMatches(webMatches, checkedIds, query, trackKey, albumKey, cache);
        if (fromWeb != null) return fromWeb;

        Log.i(
                DressroomModule.TAG,
                "No motion cover found (itunes="
                        + itunesMatches.size()
                        + ", web="
                        + webMatches.size()
                        + ") for "
                        + query.artist()
                        + " - "
                        + (query.song().isEmpty() ? query.album() : query.song()));
        markNoMotionCover(cache, trackKey, albumKey);
        return null;
    }

    private static AnimatedCoverAsset resolveFromCandidateMatches(
            List<CatalogMatch> matches,
            Set<String> checkedIds,
            TrackQuery query,
            String trackKey,
            String albumKey,
            AnimatedCoverCache cache) {
        if (matches.isEmpty()) return null;
        for (CatalogMatch match : matches) {
            if (checkedIds.contains(match.collectionId())) continue;
            String appleAssetKey = PROVIDER_ID + ":" + match.collectionId();
            AnimatedCoverAsset byCollection = cache.getByAssetKey(appleAssetKey);
            if (byCollection != null) {
                recordAliases(cache, trackKey, albumKey, appleAssetKey);
                return byCollection;
            }
        }

        for (CatalogMatch match : matches) {
            if (!checkedIds.add(match.collectionId())) continue;
            String html = downloadString(match.collectionViewUrl(), MAX_HTML_BYTES, true);
            if (html == null || html.isEmpty()) continue;

            AppleHlsResolver.AppleEditorialVideo editorial =
                    AppleHlsResolver.extractEditorialVideoFromHtml(html);
            if (editorial.isEmpty()) continue;

            String appleAssetKey = PROVIDER_ID + ":" + match.collectionId();
            AnimatedCoverAsset downloaded =
                    downloadBestMotionStream(editorial, query.maxWidthPx(), appleAssetKey, cache);
            if (downloaded != null) {
                recordAliases(cache, trackKey, albumKey, appleAssetKey);
                Log.i(
                        DressroomModule.TAG,
                        "Cached live cover ("
                                + downloaded.videoWidth()
                                + "x"
                                + downloaded.videoHeight()
                                + ") for "
                                + query.artist()
                                + " - "
                                + (query.song().isEmpty() ? query.album() : query.song()));
                return downloaded;
            }
        }
        return null;
    }

    private static AnimatedCoverAsset checkCachedAliases(
            AnimatedCoverCache cache, String trackKey, String albumKey) {
        AnimatedCoverAsset byTrack = cache.getByAlias(trackKey);
        if (byTrack != null) return byTrack;
        if (!albumKey.isEmpty()) {
            AnimatedCoverAsset byAlbum = cache.getByAlias(albumKey);
            if (byAlbum != null) {
                cache.putAlias(trackKey, byAlbum.cacheKey());
                return byAlbum;
            }
        }
        return null;
    }

    private static void recordAliases(
            AnimatedCoverCache cache, String trackKey, String albumKey, String appleAssetKey) {
        cache.putAlias(trackKey, appleAssetKey);
        cache.clearNegative(trackKey);
        if (!albumKey.isEmpty()) {
            cache.putAlias(albumKey, appleAssetKey);
            cache.clearNegative(albumKey);
        }
    }

    private static void markNoMotionCover(
            AnimatedCoverCache cache, String trackKey, String albumKey) {
        cache.markNegative(trackKey);
        if (!albumKey.isEmpty()) cache.markNegative(albumKey);
    }

    private static List<CatalogMatch> lookupItunesMatches(TrackQuery query) {
        Map<String, ScoredMatch> candidates = new LinkedHashMap<>();
        String country = resolveCountryCode();

        if (!query.song().isEmpty()) {
            String songQuery = MediaTextNormalize.cleanSearchQuery(query.artist(), query.song());
            collectItunesMatches(songQuery, "song", country, query, candidates);
            if (candidates.isEmpty() && !"US".equalsIgnoreCase(country)) {
                collectItunesMatches(songQuery, "song", "US", query, candidates);
            }
        }
        if (candidates.isEmpty() && !query.album().isEmpty()) {
            String albumQuery = MediaTextNormalize.cleanSearchQuery(query.artist(), query.album());
            collectItunesMatches(albumQuery, "album", country, query, candidates);
            if (candidates.isEmpty() && !"US".equalsIgnoreCase(country)) {
                collectItunesMatches(albumQuery, "album", "US", query, candidates);
            }
        }
        return rankMatches(candidates);
    }

    private static List<CatalogMatch> lookupWebSearchMatches(TrackQuery query) {
        Map<String, ScoredMatch> candidates = new LinkedHashMap<>();
        String country = resolveCountryCode();
        String songTerm =
                !query.song().isEmpty()
                        ? MediaTextNormalize.cleanSearchQuery(query.artist(), query.song())
                        : MediaTextNormalize.cleanSearchQuery(query.artist(), query.album());
        collectAppleWebSearchMatches(songTerm, country, query, candidates);
        if (candidates.isEmpty() && !"US".equalsIgnoreCase(country)) {
            collectAppleWebSearchMatches(songTerm, "US", query, candidates);
        }
        if (candidates.isEmpty() && !query.album().isEmpty() && !query.song().isEmpty()) {
            String albumTerm = MediaTextNormalize.cleanSearchQuery(query.artist(), query.album());
            collectAppleWebSearchMatches(albumTerm, country, query, candidates);
            if (candidates.isEmpty() && !"US".equalsIgnoreCase(country)) {
                collectAppleWebSearchMatches(albumTerm, "US", query, candidates);
            }
        }
        return rankMatches(candidates);
    }

    private static List<CatalogMatch> rankMatches(Map<String, ScoredMatch> candidates) {
        List<ScoredMatch> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingInt(ScoredMatch::score).reversed());
        List<CatalogMatch> out = new ArrayList<>(Math.min(3, ordered.size()));
        for (int i = 0; i < Math.min(3, ordered.size()); i++) {
            out.add(ordered.get(i).match());
        }
        return out;
    }

    private record ScoredMatch(int score, CatalogMatch match) {}

    private static void collectItunesMatches(
            String term,
            String entity,
            String country,
            TrackQuery query,
            Map<String, ScoredMatch> out) {
        if (term.isEmpty()) return;
        try {
            String url =
                    "https://itunes.apple.com/search?term="
                            + URLEncoder.encode(term, StandardCharsets.UTF_8.name())
                            + "&entity="
                            + entity
                            + "&limit=8&country="
                            + URLEncoder.encode(country, StandardCharsets.UTF_8.name());
            String json = downloadString(url, MAX_JSON_BYTES, false);
            if (json == null || json.isEmpty()) return;
            JSONArray results = new JSONObject(json).optJSONArray("results");
            if (results == null) return;

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                String cArtist = item.optString("artistName", "");
                String cTrack = item.optString("trackName", "");
                String cAlbum = item.optString("collectionName", "");
                if (!MediaTextNormalize.matchesTrack(
                        query.artist(), query.song(), query.album(), cArtist, cTrack, cAlbum)) {
                    continue;
                }
                long collectionId = item.optLong("collectionId", 0L);
                String collectionUrl = item.optString("collectionViewUrl", "");
                if (collectionId > 0L && AppleHlsResolver.isHttpsUrl(collectionUrl)) {
                    int score = scoreCandidate(query, cTrack, cAlbum, "album".equals(entity));
                    putHigherScored(
                            out,
                            new ScoredMatch(
                                    score,
                                    new CatalogMatch(Long.toString(collectionId), collectionUrl)));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectAppleWebSearchMatches(
            String term, String country, TrackQuery query, Map<String, ScoredMatch> out) {
        if (term.isEmpty()) return;
        try {
            String url =
                    "https://music.apple.com/"
                            + country.toLowerCase(Locale.ROOT)
                            + "/search?term="
                            + URLEncoder.encode(term, StandardCharsets.UTF_8.name());
            String html = downloadString(url, MAX_HTML_BYTES, true);
            if (html == null || html.isEmpty()) return;

            for (AppleHlsResolver.SearchLockup lockup :
                    AppleHlsResolver.extractSearchCandidatesFromHtml(html)) {
                String cArtist = AppleHlsResolver.artistFromSearchSubtitle(lockup.subtitle());
                boolean isAlbum = lockup.kind().toLowerCase(Locale.ROOT).startsWith("album");
                String cTrack = isAlbum ? "" : lockup.title();
                String cAlbum = isAlbum ? lockup.title() : "";
                if (!MediaTextNormalize.matchesTrack(
                        query.artist(), query.song(), query.album(), cArtist, cTrack, cAlbum)) {
                    continue;
                }
                int score = scoreCandidate(query, cTrack, cAlbum, isAlbum);
                putHigherScored(
                        out,
                        new ScoredMatch(
                                score,
                                new CatalogMatch(lockup.collectionId(), lockup.albumUrl())));
            }
        } catch (Exception ignored) {
        }
    }

    private static void putHigherScored(Map<String, ScoredMatch> map, ScoredMatch candidate) {
        String id = candidate.match().collectionId();
        ScoredMatch existing = map.get(id);
        if (existing == null || candidate.score() > existing.score()) {
            map.put(id, candidate);
        }
    }

    private static int scoreCandidate(
            TrackQuery query, String candSong, String candAlbum, boolean isAlbum) {
        String qSong = MediaTextNormalize.normalize(query.song());
        String qAlbum = MediaTextNormalize.normalize(query.album());
        String cSong = MediaTextNormalize.normalize(candSong);
        String cAlbum = MediaTextNormalize.normalize(candAlbum);

        int score = 0;
        if (!qSong.isEmpty() && cSong.equals(qSong)) score += 120;
        else if (!qSong.isEmpty() && !cSong.isEmpty()) score += 40;
        if (!qAlbum.isEmpty() && cAlbum.equals(qAlbum)) score += 100;
        else if (!qAlbum.isEmpty() && !cAlbum.isEmpty()) score += 30;
        if (isAlbum) score += 10;

        for (String noise : new String[] {"remix", "sped up", "slowed", "instrumental"}) {
            boolean inCand = cSong.contains(noise) || cAlbum.contains(noise);
            boolean inQuery = qSong.contains(noise) || qAlbum.contains(noise);
            if (inCand && !inQuery) score -= 35;
        }
        return score;
    }

    private static AnimatedCoverAsset downloadBestMotionStream(
            AppleHlsResolver.AppleEditorialVideo editorial,
            int maxWidthPx,
            String assetKey,
            AnimatedCoverCache cache) {
        boolean tall = editorial.tallM3u8Url() != null && !editorial.tallM3u8Url().isEmpty();
        String masterUrl = tall ? editorial.tallM3u8Url() : editorial.squareM3u8Url();
        AnimatedCoverAsset resolved =
                resolveAndDownloadMaster(masterUrl, maxWidthPx, tall, assetKey, cache);
        if (resolved != null || !tall) return resolved;

        String fallbackSquare = editorial.squareM3u8Url();
        if (fallbackSquare != null && !fallbackSquare.isEmpty()) {
            return resolveAndDownloadMaster(fallbackSquare, maxWidthPx, false, assetKey, cache);
        }
        return null;
    }

    private static AnimatedCoverAsset resolveAndDownloadMaster(
            String masterUrl,
            int maxWidthPx,
            boolean tall,
            String assetKey,
            AnimatedCoverCache cache) {
        if (!AppleHlsResolver.isHttpsUrl(masterUrl)) return null;
        String masterText = downloadString(masterUrl, MAX_M3U8_BYTES, false);
        if (masterText == null || masterText.isEmpty()) return null;

        List<AppleHlsResolver.VideoVariant> variants =
                AppleHlsResolver.parseMasterPlaylist(masterText, masterUrl);
        AppleHlsResolver.VideoVariant chosen =
                AppleHlsResolver.selectBestVariant(variants, maxWidthPx, supportsHardwareHevc());
        if (chosen == null) return null;

        String variantText = downloadString(chosen.uri(), MAX_M3U8_BYTES, false);
        if (variantText == null || variantText.isEmpty()) return null;

        String mp4Url =
                AppleHlsResolver.resolveSingleMp4FromVariantPlaylist(variantText, chosen.uri());
        if (mp4Url == null || mp4Url.isEmpty()) return null;

        File temp;
        try {
            temp = cache.createTempFile();
        } catch (IOException error) {
            return null;
        }

        if (!downloadFile(mp4Url, temp, AnimatedCoverCache.MAX_SINGLE_FILE_BYTES)) {
            temp.delete();
            return null;
        }
        return cache.commitAsset(
                PROVIDER_ID, assetKey, temp, mp4Url, chosen.width(), chosen.height(), tall);
    }

    private static String resolveCountryCode() {
        String code = Locale.getDefault().getCountry();
        if (code != null && code.length() == 2 && code.chars().allMatch(Character::isLetter)) {
            return code.toUpperCase(Locale.ROOT);
        }
        return "US";
    }

    private static boolean supportsHardwareHevc() {
        Boolean cached = hevcHardwareSupported;
        if (cached != null) return cached;
        boolean supported = false;
        try {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            for (MediaCodecInfo info : codecs) {
                if (info.isEncoder() || !info.isHardwareAccelerated()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(type)) {
                        supported = true;
                        break;
                    }
                }
                if (supported) break;
            }
        } catch (RuntimeException ignored) {
            supported = false;
        }
        hevcHardwareSupported = supported;
        return supported;
    }

    private static String downloadString(String urlString, int maxBytes, boolean htmlMode) {
        if (!AppleHlsResolver.isHttpsUrl(urlString)) return null;
        HttpURLConnection conn = null;
        try {
            conn = openHttpsConnection(urlString, htmlMode);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return null;
            try (InputStream in = conn.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream(8192)) {
                byte[] buf = new byte[8192];
                int total = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    total += read;
                    if (total > maxBytes) return null;
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

    private static boolean downloadFile(String urlString, File target, long maxBytes) {
        if (!AppleHlsResolver.isHttpsUrl(urlString)) return false;
        HttpURLConnection conn = null;
        try {
            conn = openHttpsConnection(urlString, false);
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

    private static HttpURLConnection openHttpsConnection(String urlString, boolean htmlMode)
            throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty(
                "Accept",
                htmlMode
                        ? "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                        : "application/json,application/vnd.apple.mpegurl,*/*;q=0.8");
        if (htmlMode) {
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        }
        return conn;
    }
}
