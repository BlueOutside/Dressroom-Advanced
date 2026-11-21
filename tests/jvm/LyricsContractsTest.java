package dev.denver.dressroomadvanced;

import dev.denver.dressroomadvanced.lyrics.LyricLine;
import dev.denver.dressroomadvanced.lyrics.LyricsCache;
import dev.denver.dressroomadvanced.lyrics.LyricsController;
import dev.denver.dressroomadvanced.lyrics.LyricsEntry;
import dev.denver.dressroomadvanced.lyrics.LrcParser;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class LyricsContractsTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message + " (check #" + checks + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        testLrcParser();
        testLrcFindActiveLine();
        testLyricsEntrySerialization();
        testLyricsCacheDiskLruAndExpiry();
        testPlaybackPositionMath();
        System.out.println("Lyrics contracts: " + checks + " checks passed successfully.");
    }

    private static void testLrcParser() {
        String lrc = """
                [ti:Test Song]
                [ar:Test Artist]
                [al:Test Album]
                [offset:+200]
                [00:05.50]First line of lyrics
                [00:12.345]Second line with 3-digit ms
                [00:20.1]Third line with 1-digit ms
                [00:30.00][00:45.00]Chorus line repeated twice
                """;

        List<LyricLine> lines = LrcParser.parse(lrc);
        check(lines.size() == 5, "Expected 5 parsed lyric lines, got " + lines.size());

        // 00:05.50 = 5500ms + 200ms offset = 5700ms
        check(lines.get(0).timestampMs() == 5700L, "Expected line 0 at 5700ms, got: " + lines.get(0).timestampMs());
        check("First line of lyrics".equals(lines.get(0).text()), "Line 0 text mismatch: " + lines.get(0).text());

        // 00:12.345 = 12345ms + 200ms = 12545ms
        check(lines.get(1).timestampMs() == 12545L, "Expected line 1 at 12545ms, got: " + lines.get(1).timestampMs());

        // 00:20.1 = 20100ms + 200ms = 20300ms
        check(lines.get(2).timestampMs() == 20300L, "Expected line 2 at 20300ms, got: " + lines.get(2).timestampMs());

        // Repeated lines at 30s and 45s (+200ms)
        check(lines.get(3).timestampMs() == 30200L, "Expected line 3 at 30200ms, got: " + lines.get(3).timestampMs());
        check(lines.get(4).timestampMs() == 45200L, "Expected line 4 at 45200ms, got: " + lines.get(4).timestampMs());
        check("Chorus line repeated twice".equals(lines.get(3).text()), "Chorus text mismatch");
        check("Chorus line repeated twice".equals(lines.get(4).text()), "Chorus text mismatch");

        // Enhanced LRC with inline word-level tags
        String enhancedLrc = "[01:51.01]<01:51.01>Never <01:51.35>gonna <01:51.65>make <01:52.05>you <01:52.42>cry";
        List<LyricLine> enhancedLines = LrcParser.parse(enhancedLrc);
        check(enhancedLines.size() == 1, "Expected 1 line from enhanced LRC");
        check("Never gonna make you cry".equals(enhancedLines.get(0).text()),
                "Enhanced LRC inline tags not cleanly stripped: " + enhancedLines.get(0).text());

        // Empty input
        check(LrcParser.parse(null).isEmpty(), "Null input must return empty list");
        check(LrcParser.parse("   \n\t").isEmpty(), "Blank input must return empty list");
    }

    private static void testLrcFindActiveLine() {
        String lrc = """
                [00:10.00]Line 1
                [00:20.00]Line 2
                [00:30.00]Line 3
                """;
        List<LyricLine> lines = LrcParser.parse(lrc);

        // Before first line
        check(LrcParser.findActiveLineIndex(lines, 0L) == -1, "Before first line should return -1");
        check(LrcParser.findActiveLineIndex(lines, 9999L) == -1, "At 9999ms should return -1");

        // Exactly on line 1
        check(LrcParser.findActiveLineIndex(lines, 10000L) == 0, "At 10000ms should return index 0");
        // Between line 1 and line 2
        check(LrcParser.findActiveLineIndex(lines, 15000L) == 0, "At 15000ms should return index 0");

        // Exactly on line 2
        check(LrcParser.findActiveLineIndex(lines, 20000L) == 1, "At 20000ms should return index 1");
        // Between line 2 and line 3
        check(LrcParser.findActiveLineIndex(lines, 29999L) == 1, "At 29999ms should return index 1");

        // Exactly on line 3
        check(LrcParser.findActiveLineIndex(lines, 30000L) == 2, "At 30000ms should return index 2");
        // Past line 3
        check(LrcParser.findActiveLineIndex(lines, 60000L) == 2, "Past end should return index 2");
    }

    private static void testLyricsEntrySerialization() throws Exception {
        LyricsEntry entry = new LyricsEntry(
                "Daft Punk",
                "Get Lucky",
                "Random Access Memories",
                248000L,
                "[00:10.00]Like the legend of the phoenix",
                "Like the legend of the phoenix\nAll ends with beginnings",
                "lrclib",
                1700000000000L,
                false);

        check(entry.hasSyncedLyrics(), "Entry should have synced lyrics");
        check(entry.hasPlainLyrics(), "Entry should have plain lyrics");
        check(entry.hasLyrics(), "Entry should have lyrics");
        check(!entry.notFound(), "Entry should not be marked as notFound");

        JSONObject json = entry.toJson();
        LyricsEntry restored = LyricsEntry.fromJson(json);

        check(entry.artist().equals(restored.artist()), "Artist mismatch");
        check(entry.title().equals(restored.title()), "Title mismatch");
        check(entry.album().equals(restored.album()), "Album mismatch");
        check(entry.durationMs() == restored.durationMs(), "Duration mismatch");
        check(entry.syncedLyrics().equals(restored.syncedLyrics()), "Synced lyrics mismatch");
        check(entry.plainLyrics().equals(restored.plainLyrics()), "Plain lyrics mismatch");
        check(entry.source().equals(restored.source()), "Source mismatch");
        check(entry.timestampMs() == restored.timestampMs(), "Timestamp mismatch");
        check(entry.notFound() == restored.notFound(), "notFound mismatch");

        // Negative entry
        LyricsEntry notFound = LyricsEntry.notFound("Unknown Artist", "Unknown Song", "", 0L);
        check(notFound.notFound(), "Negative entry must have notFound=true");
        check(!notFound.hasLyrics(), "Negative entry must have hasLyrics=false");
        check("none".equals(notFound.source()), "Negative entry source should be 'none'");
        check(!notFound.isExpired(notFound.timestampMs() + 1000L, 86400000L), "Should not be expired yet");
        check(notFound.isExpired(notFound.timestampMs() + 86400001L, 86400000L), "Should be expired past TTL");
    }

    private static void testLyricsCacheDiskLruAndExpiry() throws Exception {
        File tempDir = Files.createTempDirectory("lyrics_cache_test").toFile();
        try {
            LyricsCache cache = new LyricsCache(tempDir);
            check(cache.getCacheInfo().trackCount() == 0, "Initial cache count should be 0");
            check(cache.getCacheInfo().totalBytes() == 0L, "Initial cache bytes should be 0");

            // Write entry 1
            LyricsEntry e1 = new LyricsEntry("Artist1", "Track1", "Album1", 180000L,
                    "[00:01.00]Hello", "Hello", "lrclib", System.currentTimeMillis(), false);
            cache.put(e1);

            LyricsEntry read1 = cache.get("Artist1", "Track1");
            check(read1 != null, "Entry 1 should be cached");
            check("Hello".equals(read1.plainLyrics()), "Entry 1 lyrics mismatch");
            check(cache.getCacheInfo().trackCount() == 1, "Cache count should be 1");
            check(cache.getCacheInfo().totalBytes() > 0, "Cache bytes should be > 0");

            // Write negative entry with expired timestamp
            long expiredTime = System.currentTimeMillis() - (LyricsCache.NEGATIVE_TTL_MS + 5000L);
            LyricsEntry expiredNeg = new LyricsEntry("Ghost", "Inexistent", "", 0L,
                    null, null, "none", expiredTime, true);
            cache.put(expiredNeg);

            // Read expired negative entry -> should be auto-purged and return null
            LyricsEntry readExpired = cache.get("Ghost", "Inexistent");
            check(readExpired == null, "Expired negative entry should return null on read");

            // Clear cache
            cache.clear();
            check(cache.getCacheInfo().trackCount() == 0, "Cache count after clear should be 0");
            check(cache.get("Artist1", "Track1") == null, "Get after clear should return null");
        } finally {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
            tempDir.delete();
        }
    }

    private static void testPlaybackPositionMath() {
        // Test basic playback calculations
        check(LyricsController.calculatePosition(null) == 0L, "Null state position should be 0");
    }
}
