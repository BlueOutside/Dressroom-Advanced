package dev.denver.dressroomadvanced.lyrics;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Free keyless lyrics repository querying LRCLIB with fallback to lyrics.ovh,
 * backed by internal local disk cache and negative lookup caching.
 */
public final class LyricsRepository {
    private static final String TAG = "LyricsRepository";
    private static final String USER_AGENT =
            "Dressroom-Advanced (https://github.com/BlueOutside/Dressroom-Advanced)";
    private static final int TIMEOUT_MS = 5000;

    private static volatile LyricsRepository instance;
    private final LyricsCache cache;
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<String, Future<LyricsEntry>> activeFetches = new ConcurrentHashMap<>();

    public LyricsRepository(LyricsCache cache) {
        this.cache = cache;
    }

    public static LyricsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (LyricsRepository.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext() != null
                            ? context.getApplicationContext()
                            : context;
                    LyricsCache c = new LyricsCache(new java.io.File(app.getFilesDir(), LyricsCache.CACHE_DIR_NAME));
                    instance = new LyricsRepository(c);
                }
            }
        }
        return instance;
    }

    public LyricsCache cache() {
        return cache;
    }

    /**
     * Resolves lyrics for the track, checking cache first and falling back to network.
     * Blocks current thread - should be called on a background thread.
     */
    public LyricsEntry getLyrics(String artist, String title, String album, long durationMs) {
        if ((artist == null || artist.trim().isEmpty()) && (title == null || title.trim().isEmpty())) {
            return LyricsEntry.notFound(artist, title, album, durationMs);
        }

        LyricsSanitizer.SanitizedTrack sanitized = LyricsSanitizer.sanitize(artist, title);
        String cleanArtist = sanitized.artist();
        String cleanTitle = sanitized.title();

        // 1. Check local disk cache (under raw and cleaned names)
        LyricsEntry cached = cache.get(artist, title);
        if (cached == null && (!cleanArtist.equals(artist) || !cleanTitle.equals(title))) {
            cached = cache.get(cleanArtist, cleanTitle);
        }
        if (cached != null && cached.matchesDuration(durationMs)) {
            if (cached.notFound()) {
                if (!cached.isExpired(System.currentTimeMillis(), LyricsCache.NEGATIVE_TTL_MS)) {
                    return cached;
                }
            } else {
                return cached;
            }
        }

        // 2. Fetch from network (deduplicated by cleaned track key)
        String fetchKey = LyricsCache.cacheKey(cleanArtist, cleanTitle)
                + "|" + album + "|" + durationMs;
        try {
            Future<LyricsEntry> future = activeFetches.computeIfAbsent(
                    fetchKey,
                    k -> fetchPool.submit(() -> fetchFromNetwork(artist, title, cleanArtist, cleanTitle, album, durationMs)));
            LyricsEntry result = future.get();
            activeFetches.remove(fetchKey);
            return result;
        } catch (Exception e) {
            activeFetches.remove(fetchKey);
            Log.w(TAG, "Failed resolving lyrics for " + artist + " - " + title, e);
            return LyricsEntry.notFound(cleanArtist, cleanTitle, album, durationMs);
        }
    }

    private void storeBoth(LyricsEntry entry, String rawArtist, String rawTitle, String cleanArtist, String cleanTitle) {
        if (entry == null) return;
        cache.put(cleanArtist, cleanTitle, entry);
        if (!rawArtist.equalsIgnoreCase(cleanArtist) || !rawTitle.equalsIgnoreCase(cleanTitle)) {
            cache.put(rawArtist, rawTitle, entry);
        }
    }

    private LyricsEntry fetchFromNetwork(
            String rawArtist,
            String rawTitle,
            String cleanArtist,
            String cleanTitle,
            String album,
            long durationMs) {
        long durationSec = durationMs > 0 ? Math.round(durationMs / 1000.0) : 0L;

        // Step 1A: LRCLIB Exact Match with cleaned title and artist
        LyricsEntry entry = fetchLrclibExact(cleanArtist, cleanTitle, album, durationSec);
        if (entry == null && album != null && !album.trim().isEmpty()) {
            entry = fetchLrclibExact(cleanArtist, cleanTitle, null, durationSec);
        }
        if (entry != null && entry.hasLyrics()) {
            storeBoth(entry, rawArtist, rawTitle, cleanArtist, cleanTitle);
            return entry;
        }

        // Step 1B: LRCLIB Search Fallback with cleaned title and artist
        entry = fetchLrclibSearch(cleanArtist, cleanTitle, durationSec);
        if (entry != null && entry.hasLyrics()) {
            storeBoth(entry, rawArtist, rawTitle, cleanArtist, cleanTitle);
            return entry;
        }

        // Step 1C: If cleanTitle contained featured artists, try stripping them
        String noFeat = LyricsSanitizer.stripFeatures(cleanTitle);
        if (!noFeat.equalsIgnoreCase(cleanTitle)) {
            entry = fetchLrclibExact(cleanArtist, noFeat, null, durationSec);
            if (entry == null) {
                entry = fetchLrclibSearch(cleanArtist, noFeat, durationSec);
            }
            if (entry != null && entry.hasLyrics()) {
                storeBoth(entry, rawArtist, rawTitle, cleanArtist, cleanTitle);
                return entry;
            }
        }

        // Step 1D: If raw differed from clean, try raw as fallback
        if (!rawArtist.equalsIgnoreCase(cleanArtist) || !rawTitle.equalsIgnoreCase(cleanTitle)) {
            entry = fetchLrclibExact(rawArtist, rawTitle, album, durationSec);
            if (entry == null) {
                entry = fetchLrclibSearch(rawArtist, rawTitle, durationSec);
            }
            if (entry != null && entry.hasLyrics()) {
                storeBoth(entry, rawArtist, rawTitle, cleanArtist, cleanTitle);
                return entry;
            }
        }

        // Step 2: Secondary Fallback - lyrics.ovh
        entry = fetchLyricsOvh(cleanArtist, cleanTitle, album, durationMs);
        if (entry == null && (!rawArtist.equalsIgnoreCase(cleanArtist) || !rawTitle.equalsIgnoreCase(cleanTitle))) {
            entry = fetchLyricsOvh(rawArtist, rawTitle, album, durationMs);
        }
        if (entry != null && entry.hasLyrics()) {
            storeBoth(entry, rawArtist, rawTitle, cleanArtist, cleanTitle);
            return entry;
        }

        // Step 3: Negative Caching
        LyricsEntry notFound = LyricsEntry.notFound(cleanArtist, cleanTitle, album, durationMs);
        storeBoth(notFound, rawArtist, rawTitle, cleanArtist, cleanTitle);
        return notFound;
    }

    private LyricsEntry fetchLrclibExact(String artist, String title, String album, long durationSec) {
        try {
            StringBuilder urlBuilder = new StringBuilder("https://lrclib.net/api/get?");
            urlBuilder.append("artist_name=").append(encode(artist));
            urlBuilder.append("&track_name=").append(encode(title));
            if (album != null && !album.trim().isEmpty()) {
                urlBuilder.append("&album_name=").append(encode(album));
            }
            if (durationSec > 0) {
                urlBuilder.append("&duration=").append(durationSec);
            }

            HttpURLConnection conn = null;
            try {
                conn = openConnection(urlBuilder.toString());
                int code = conn.getResponseCode();
                if (code == 200) {
                    String response = readResponse(conn.getInputStream());
                    JSONObject json = new JSONObject(response);
                    return lrclibEntry(json, artist, title, album, durationSec * 1000L);
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "LRCLIB exact get failed: " + e.getMessage());
        }
        return null;
    }

    private LyricsEntry fetchLrclibSearch(String artist, String title, long targetDurationSec) {
        try {
            String url = "https://lrclib.net/api/search?artist_name=" + encode(artist)
                    + "&track_name=" + encode(title);
            HttpURLConnection conn = null;
            try {
                conn = openConnection(url);
                int code = conn.getResponseCode();
                if (code == 200) {
                    String response = readResponse(conn.getInputStream());
                    JSONArray array = new JSONArray(response);
                    if (array.length() == 0) return null;

                    JSONObject bestMatch = null;
                    int bestScore = -1;

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.getJSONObject(i);
                        String synced = optNullableString(item, "syncedLyrics");
                        String plain = optNullableString(item, "plainLyrics");
                        boolean hasSynced = (synced != null && !synced.trim().isEmpty())
                                || item.optBoolean("hasWordSync", false);
                        boolean hasPlain = plain != null && !plain.trim().isEmpty();
                        if (!hasSynced && !hasPlain) continue;

                        long duration = item.optLong("duration", 0L);
                        if (targetDurationSec > 0L && duration > 0L
                                && Math.abs(duration - targetDurationSec) > 5L) continue;
                        boolean durationClose = targetDurationSec > 0 && duration > 0
                                && Math.abs(duration - targetDurationSec) <= 5;

                        int score = 0;
                        if (hasSynced && durationClose) score = 4;
                        else if (hasSynced) score = 3;
                        else if (hasPlain && durationClose) score = 2;
                        else if (hasPlain) score = 1;
                        if (item.optBoolean("hasWordSync", false)) score += 2;

                        if (score > bestScore) {
                            bestScore = score;
                            bestMatch = item;
                        }
                    }

                    if (bestMatch != null && bestScore > 0) {
                        String album = bestMatch.optString("albumName", "");
                        long duration = bestMatch.optLong("duration", targetDurationSec) * 1000L;
                        return lrclibEntry(bestMatch, artist, title, album, duration);
                    }
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "LRCLIB search failed: " + e.getMessage());
        }
        return null;
    }

    private LyricsEntry lrclibEntry(JSONObject json, String artist, String title, String album, long durationMs) {
        String synced = optNullableString(json, "syncedLyrics");
        String plain = optNullableString(json, "plainLyrics");
        List<LyricLine> timed = List.of();
        try {
            Lyricsfile.Document document = Lyricsfile.parse(optNullableString(json, "lyricsfile"));
            timed = document.lines();
            if (document.plain() != null) plain = document.plain();
            if (document.instrumental()) plain = "\u266A Instrumental \u266A";
        } catch (RuntimeException invalid) {
            Log.d(TAG, "Invalid Lyricsfile, using LRC/plain fallback: " + invalid.getClass().getSimpleName());
        }
        if (json.optBoolean("instrumental", false) && (plain == null || plain.isEmpty()))
            plain = "\u266A Instrumental \u266A";
        LyricsEntry entry = new LyricsEntry(artist, title, album, durationMs, synced, plain,
                "lrclib", System.currentTimeMillis(), false, timed);
        return entry.hasLyrics() ? entry : null;
    }

    private LyricsEntry fetchLyricsOvh(String artist, String title, String album, long durationMs) {
        try {
            String url = "https://api.lyrics.ovh/v1/" + encodePath(artist) + "/" + encodePath(title);
            HttpURLConnection conn = null;
            try {
                conn = openConnection(url);
                int code = conn.getResponseCode();
                if (code == 200) {
                    String response = readResponse(conn.getInputStream());
                    JSONObject json = new JSONObject(response);
                    String lyrics = optNullableString(json, "lyrics");
                    if (lyrics != null && !lyrics.trim().isEmpty()) {
                        return new LyricsEntry(
                                artist, title, album, durationMs,
                                null, lyrics.trim(), "lyrics.ovh", System.currentTimeMillis(), false);
                    }
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "lyrics.ovh failed: " + e.getMessage());
        }
        return null;
    }

    private static String optNullableString(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) return null;
        String value = obj.optString(key, null);
        return (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim()))
                ? null
                : value;
    }

    private static HttpURLConnection openConnection(String spec) throws Exception {
        URL url = new URL(spec);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private static String readResponse(InputStream in) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String encode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value.trim();
        }
    }

    private static String encodePath(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return value.trim();
        }
    }
}
