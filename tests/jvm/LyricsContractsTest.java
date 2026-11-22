package dev.denver.dressroomadvanced;

import dev.denver.dressroomadvanced.lyrics.LyricLine;
import dev.denver.dressroomadvanced.lyrics.LyricsCache;
import dev.denver.dressroomadvanced.lyrics.LyricsController;
import dev.denver.dressroomadvanced.lyrics.LyricsEntry;
import dev.denver.dressroomadvanced.lyrics.LrcParser;
import dev.denver.dressroomadvanced.lyrics.Lyricsfile;
import dev.denver.dressroomadvanced.lyrics.LyricScrollMotion;

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
        testEnhancedWordTimings();
        testConcurrentAndOverlappingLyrics();
        testPlainLrcUsesSourceTimings();
        testLyricsfile();
        testContinuousLyricScroll();
        testInterludeInsertion();
        testLrcFindActiveLine();
        testLyricsEntrySerialization();
        testLyricsCacheDiskLruAndExpiry();
        testLyricsFontPreferenceHelpers();
        testPlaybackPositionMath();
        testAppleMusicChoreographyAndHorizontalBounds();
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

    private static void testEnhancedWordTimings() {
        String enhanced = "[00:10.00]<00:10.00>Never <00:10.40>gonna <00:10.80>give <00:11.20>you <00:11.60>up<00:12.20>";
        List<LyricLine> lines = LrcParser.parse(enhanced);
        check(lines.size() == 1, "Expected 1 enhanced line");
        LyricLine line = lines.get(0);
        check(line.hasWordTimings(), "Enhanced line must preserve word timings");
        check(line.words().size() == 5, "Expected 5 timed words, got " + line.words().size());
        check("Never".equals(line.words().get(0).text()), "First word mismatch: " + line.words().get(0).text());
        check("up".equals(line.words().get(4).text()), "Last word mismatch: " + line.words().get(4).text());
        check(line.words().get(0).startMs() == 10_000L, "First word start mismatch");
        check(line.words().get(0).endMs() == 10_400L, "First word end mismatch");
        check(line.words().get(4).endMs() == 12_200L, "Trailing closing tag must set last word end");
        check(line.endTimestampMs() == 12_200L, "Trailing closing tag must set line end");

        // Progress checks across word timings
        check(line.progressAt(9_900L) == 0f, "Progress before start must be 0");
        float midFirst = line.progressAt(10_200L);
        check(midFirst > 0.05f && midFirst < 0.25f, "Mid first word progress out of range: " + midFirst);
        float midFourth = line.progressAt(11_400L);
        check(midFourth > midFirst && midFourth < 0.95f, "Mid fourth word progress should advance smoothly: " + midFourth);
        check(line.progressAt(12_300L) == 1f, "Progress past end must be 1");
    }

    private static void testConcurrentAndOverlappingLyrics() {
        String duetLrc = """
                [00:10.00]Hold me closer tonight
                [00:10.00](Hold me closer now)
                [00:15.00]<00:15.00>I can hear the rhythm calling out my name<00:18.00>
                [00:16.20](Calling out my name)
                [00:22.00]Final solo line
                """;
        List<LyricLine> lines = LrcParser.parse(duetLrc);
        check(lines.size() == 5, "Expected 5 duet lines, got " + lines.size());

        // Same-timestamp duet lines must BOTH be concurrently active at 11s
        List<Integer> at11s = LrcParser.findActiveLineIndices(lines, 11_000L);
        check(at11s.size() == 2 && at11s.contains(0) && at11s.contains(1),
                "Same-timestamp duet lines should animate concurrently, got " + at11s);
        check(lines.get(1).isBackgroundVocal(), "Parenthetical sibling should be marked backgroundVocal");

        // Staggered lead + parenthetical backing vocal must BOTH be concurrently active at 16.5s
        List<Integer> at16500 = LrcParser.findActiveLineIndices(lines, 16_500L);
        check(at16500.size() == 2 && at16500.contains(2) && at16500.contains(3),
                "Lead and incoming backing vocal should overlap concurrently, got " + at16500);
        check(lines.get(3).isBackgroundVocal(), "Staggered parenthetical line should be marked backgroundVocal");
        check(!lines.get(4).isBackgroundVocal(), "Non-parenthetical line must not be backgroundVocal");
    }

    private static void testPlainLrcUsesSourceTimings() {
        List<LyricLine> lines = LrcParser.parse("""
                [00:01.00]A very long lyric with many words
                [00:02.00](Short backing line)
                [00:10.00]Next verse
                """);
        check(lines.get(0).endTimestampMs() == 2000L,
                "Plain LRC must not invent an overlap from lyric length");
        check(lines.get(1).endTimestampMs() == 10000L,
                "Plain LRC must not invent a vocal stop or instrumental break");
        check(LrcParser.findActiveLineIndices(lines, 2100L).equals(List.of(1)),
                "Only the timestamped line should remain active");
        check(Math.abs(lines.get(1).progressAt(6000L) - .5f) < .001f,
                "Line-only lyrics should use a steady fallback sweep");
        check(!lines.get(1).isActiveAt(1999L),
                "Scrolling toward the next line must not activate its vocals early");
    }

    private static void testLyricsfile() throws Exception {
        Lyricsfile.Document doc = Lyricsfile.parse("""
                version: '1.0'
                metadata:
                  title: Example
                  artist: Example Artist
                  duration_ms: 5000
                lines:
                  - text: 'Hello world'
                    start_ms: 1000
                    end_ms: 2600
                    words:
                      - text: 'Hello '
                        start_ms: 1000
                        end_ms: 1600
                      - text: world
                        start_ms: 1600
                  - text: 'Second voice'
                    start_ms: 1400
                    end_ms: 1500
                plain: |
                  Hello world
                  Second voice
                """);
        check(doc.lines().size() == 2, "Lyricsfile must preserve simultaneous vocal lines");
        LyricLine first = doc.lines().get(0);
        check(first.hasWordTimings() && first.words().size() == 2, "Lyricsfile word timings missing");
        check(first.words().get(0).endMs() == 1600L, "Explicit word end must be preserved");
        check(first.words().get(1).startChar() == 6 && first.words().get(1).endChar() == 11,
                "Word whitespace must map to the original display text");
        check(first.words().get(1).endMs() == 2600L, "Missing last word end must use its line end");
        check(doc.lines().get(1).endTimestampMs() == 1500L, "Short explicit vocal windows must not be stretched");
        check(LrcParser.findActiveLineIndices(doc.lines(), 1450L).size() == 2, "Timed overlaps must survive parsing");
        check(doc.plain().contains("Second voice"), "YAML multiline plain lyrics must be preserved");
        LyricsEntry entry = new LyricsEntry("Artist", "Title", "Album", 5000L, null,
                doc.plain(), "lrclib", 1234L, false, doc.lines());
        LyricsEntry loaded = LyricsEntry.fromJson(entry.toJson());
        check(loaded.hasSyncedLyrics(), "Lyricsfile-only records must be treated as synchronized");
        check(loaded.timedLines().get(0).words().equals(first.words()), "Cache serialization lost word timing");
        check(loaded.timedLines().get(1).endTimestampMs() == 1500L, "Cache serialization lost explicit line end");
        for (String invalid : List.of(
                "version: '2.0'\nlines: []",
                "version: '1.0'\nversion: '2.0'",
                "!!java.lang.Runtime {}",
                "version: '1.0'\nmetadata: &a {}\nlines: [*a]")) {
            boolean rejected = false;
            try { Lyricsfile.parse(invalid); }
            catch (RuntimeException expected) { rejected = true; }
            check(rejected, "Unsafe or unsupported Lyricsfile must be rejected");
        }
    }

    private static void testContinuousLyricScroll() {
        LyricScrollMotion motion = new LyricScrollMotion(value -> value);
        motion.begin(100, 600, 1000L, 750f);
        check(motion.position(1000L) == 100, "Line change must not teleport the viewport to its target");
        for (long delay : new long[]{0L, 25L, 63L}) {
            float previous = -100f;
            for (long time = 1000L; time <= 1800L; time += 16L) {
                float visibleY = -motion.position(time) + motion.rowOffset(time, delay, 0f);
                check(visibleY <= previous + .001f, "Forward line scroll must not bounce backward");
                check(previous - visibleY <= 11f, "Line advance must not expose a one-frame viewport jump");
                previous = visibleY;
            }
        }
        check(motion.position(1750L) == 600, "Viewport must finish at the requested lyric");
        check(!motion.finished(1750L, 63L), "Trailing rows need time to finish their stagger");
        check(motion.rowOffset(1813L, 63L, 0f) == 0f && motion.finished(1813L, 63L),
                "Staggered rows must settle without residual offsets");

        motion.begin(0, 500, 0L, 750f);
        int current = motion.position(300L);
        float offset = motion.rowOffset(300L, 63L, 0f);
        float before = -current + offset;
        motion.begin(current, 900, 300L, 750f);
        float after = -motion.position(300L) + motion.rowOffset(300L, 63L, offset);
        check(Math.abs(before - after) < .001f, "Retargeting fast lyrics must preserve the displayed position");

        motion.begin(0, 10, 0L, 100f);
        check(Math.abs(-motion.position(25L) + motion.rowOffset(25L, 0L, 0f) + 2.5f) < .001f,
                "Row offsets must compensate integer viewport rounding");
        motion.cancel();
        check(!motion.active(), "Manual scrolling must cancel automatic viewport motion");
    }

    private static void testInterludeInsertion() {
        String lrcWithBreaks = """
                [00:08.00]Opening verse line
                [00:11.00]
                [00:20.00]After instrumental solo
                [00:22.50]Tight consecutive line
                """;
        List<LyricLine> base = LrcParser.parse(lrcWithBreaks);
        check(base.size() == 3, "Empty stop timestamp must not create a blank lyric line, got " + base.size());
        check(base.get(0).endTimestampMs() == 11_000L,
                "Empty stop tag should cap preceding line end at 11000ms, got " + base.get(0).endTimestampMs());

        List<LyricLine> timeline = LrcParser.withInterludes(base);
        // Should have: [0] intro '...', [1] Opening verse, [2] mid '...', [3] After solo, [4] Tight consecutive
        check(timeline.size() == 5, "Expected 5 timeline entries (3 lyrics + 2 interludes), got " + timeline.size());
        check(timeline.get(0).isInterlude(), "Entry 0 should be intro interlude");
        check(timeline.get(0).endTimestampMs() < 8_000L, "Intro interlude must end before first lyric");
        check(timeline.get(2).isInterlude(), "Entry 2 should be mid-song interlude");
        check(timeline.get(2).timestampMs() > 11_000L && timeline.get(2).endTimestampMs() < 20_000L,
                "Mid-song interlude bounds wrong: " + timeline.get(2).timestampMs() + ".." + timeline.get(2).endTimestampMs());
        check(!timeline.get(3).isInterlude() && !timeline.get(4).isInterlude(),
                "No interlude should be inserted between tight consecutive lines");

        // Interlude progress check
        LyricLine intro = timeline.get(0);
        long midIntro = (intro.timestampMs() + intro.endTimestampMs()) / 2L;
        float introProgress = intro.progressAt(midIntro);
        check(introProgress > 0.45f && introProgress < 0.55f,
                "Interlude midpoint progress should be ~0.5, got " + introProgress);

        // Short intro (< 4.5s) should NOT insert an intro interlude
        List<LyricLine> shortIntro = LrcParser.withInterludes(LrcParser.parse("[00:01.20]Immediate start"));
        check(shortIntro.size() == 1 && !shortIntro.get(0).isInterlude(),
                "Short intro must not insert interlude");
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
        check(entry.matchesDuration(250000L), "Small track duration differences should match");
        check(!entry.matchesDuration(300000L), "A different recording must not reuse cached timings");
        check(entry.matchesDuration(0L), "Missing duration should preserve cached lyrics");
    }

    private static void testLyricsCacheDiskLruAndExpiry() throws Exception {
        File tempDir = Files.createTempDirectory("lyrics_cache_test").toFile();
        try {
            LyricsCache cache = new LyricsCache(tempDir);
            check(cache.getCacheInfo().trackCount() == 0, "Initial cache count should be 0");
            check(cache.getCacheInfo().totalBytes() == 0L, "Initial cache bytes should be 0");

            LyricsEntry e1 = new LyricsEntry("Artist1", "Track1", "Album1", 180000L,
                    "[00:01.00]Hello", "Hello", "lrclib", System.currentTimeMillis(), false);
            cache.put(e1);

            LyricsEntry read1 = cache.get("Artist1", "Track1");
            check(read1 != null, "Entry 1 should be cached");
            check("Hello".equals(read1.plainLyrics()), "Entry 1 lyrics mismatch");
            check(cache.getCacheInfo().trackCount() == 1, "Cache count should be 1");
            check(cache.getCacheInfo().totalBytes() > 0, "Cache bytes should be > 0");

            long expiredTime = System.currentTimeMillis() - (LyricsCache.NEGATIVE_TTL_MS + 5000L);
            LyricsEntry expiredNeg = new LyricsEntry("Ghost", "Inexistent", "", 0L,
                    null, null, "none", expiredTime, true);
            cache.put(expiredNeg);

            LyricsEntry readExpired = cache.get("Ghost", "Inexistent");
            check(readExpired == null, "Expired negative entry should return null on read");

            cache.clear();
            check(cache.getCacheInfo().trackCount() == 0, "Cache count after clear should be 0");
            check(cache.get("Artist1", "Track1") == null, "Get after clear should return null");
        } finally {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
            tempDir.delete();
        }
    }

    private static void testLyricsFontPreferenceHelpers() {
        String validSha = "a".repeat(64);
        check(validSha.equals(ExtrasPreferences.cleanFontId(validSha)), "Valid SHA-256 font ID should pass");
        check("".equals(ExtrasPreferences.cleanFontId("../bad.ttf")), "Invalid font ID must be rejected");
        check("".equals(ExtrasPreferences.cleanFontId(null)), "Null font ID must clean to empty");
        check(ExtrasPreferences.clampLyricsWeight(50) == 100, "Weight below 100 must clamp to 100");
        check(ExtrasPreferences.clampLyricsWeight(1200) == 900, "Weight above 900 must clamp to 900");
        check(ExtrasPreferences.clampLyricsWeight(650) == 650, "Weight in range must be preserved");
    }

    private static void testPlaybackPositionMath() {
        check(LyricsController.calculatePosition(null) == 0L, "Null state position should be 0");
    }

    private static void testAppleMusicChoreographyAndHorizontalBounds() {
        // 1. Dynamic line-advance lookahead [200..750ms gap -> 480..750ms lead]
        List<LyricLine> tight = List.of(
                new LyricLine(10_000L, 12_000L, "Fast line one", List.of(), false, false),
                new LyricLine(12_100L, 14_000L, "Fast line two", List.of(), false, false));
        check(LrcParser.computeLineAdvanceLeadMs(tight, 0, false) == 480L,
                "Tight gap (<=200ms) must map to 480ms lookahead");

        List<LyricLine> mid = List.of(
                new LyricLine(10_000L, 12_000L, "Mid line one", List.of(), false, false),
                new LyricLine(12_475L, 14_000L, "Mid line two", List.of(), false, false));
        check(LrcParser.computeLineAdvanceLeadMs(mid, 0, false) == 615L,
                "Midpoint gap (475ms) must map to 615ms lookahead, got "
                        + LrcParser.computeLineAdvanceLeadMs(mid, 0, false));

        List<LyricLine> wide = List.of(
                new LyricLine(10_000L, 12_000L, "Wide line one", List.of(), false, false),
                new LyricLine(13_200L, 15_000L, "Wide line two", List.of(), false, false));
        check(LrcParser.computeLineAdvanceLeadMs(wide, 0, false) == 750L,
                "Wide gap (>=750ms) must cap at 750ms lookahead");
        check(LrcParser.computeLineAdvanceLeadMs(wide, 0, true) == 1300L,
                "Interlude exit flag must return 1300ms anticipation lead");

        // 2. Triangular staggered cascade wave (d <= 0 -> 0; d = 1..4 -> 25, 44, 56, 63ms)
        check(LrcParser.computeCascadeDelayMs(0, 3) == 0L, "Lines above anchor must have 0ms delay");
        check(LrcParser.computeCascadeDelayMs(3, 3) == 0L, "Active anchor line must have 0ms delay");
        check(LrcParser.computeCascadeDelayMs(4, 3) == 25L, "d=1 cascade delay must be 25ms");
        check(LrcParser.computeCascadeDelayMs(5, 3) == 44L, "d=2 cascade delay must be 44ms");
        check(LrcParser.computeCascadeDelayMs(6, 3) == 56L, "d=3 cascade delay must be 56ms");
        check(LrcParser.computeCascadeDelayMs(7, 3) == 63L, "d=4 cascade delay must be 63ms");
        check(LrcParser.computeCascadeDelayMs(15, 3) == 63L, "d>4 cascade delay must clamp at 63ms");

        // 3. Clip-proof horizontal container width bounds
        int available = 1080 - 60 - 60 - 30 - 30; // 900px
        int wAtUnity = LrcParser.computeSafeLineMaxWidthPx(1080, 60, 60, 30, 30, 1.00f);
        check(wAtUnity == available, "At 1.00x active scale, full container inner width must be usable");
        int wAtScaled = LrcParser.computeSafeLineMaxWidthPx(1080, 60, 60, 30, 30, 1.08f);
        check(Math.floor(wAtScaled * 1.08f) <= available && wAtScaled > 0,
                "Scaled lyric width must remain strictly inside container horizontal spacing");

        // 4. Sustained-word emphasis glow vs short-word subtle glow
        String sustainedLrc = "[00:10.00]<00:10.00>I <00:10.25>loooove <00:11.95>you<00:12.30>";
        List<LyricLine> emLines = LrcParser.parse(sustainedLrc);
        check(emLines.size() == 1, "Expected 1 emphasis line");
        LyricLine em = emLines.get(0);
        check(em.emphasisGlowAt(9_500L) == 0f, "Glow before line must be 0");
        float shortWordGlow = em.emphasisGlowAt(10_100L);
        float sustainedWordGlow = em.emphasisGlowAt(10_900L);
        check(sustainedWordGlow > shortWordGlow && sustainedWordGlow > 0.55f,
                "Sustained word must glow stronger than short word: short=" + shortWordGlow
                        + ", sustained=" + sustainedWordGlow);
        check(em.emphasisGlowAt(13_000L) == 0f, "Glow after line must be 0");

        // Standard line-level LRC glow
        LyricLine std = new LyricLine(5_000L, 8_000L, "Standard synced line", List.of(), false, false);
        check(std.emphasisGlowAt(6_500L) > 0.25f, "Active standard line should emit subtle glow");
    }
}
