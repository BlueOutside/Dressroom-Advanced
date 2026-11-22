package dev.denver.dressroomadvanced.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC parser supporting standard multi-tag LRC, enhanced-LRC inline word/syllable
 * timestamps ({@code <mm:ss.xx>}), empty stop-marker timestamps, concurrent/overlapping
 * vocal lines (duets & backing vocals), and synthesized instrumental interlude
 * ({@code ...}) entries.
 */
public final class LrcParser {
    private static final Pattern TIME_TAG_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern INLINE_TIME_TAG_PATTERN =
            Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern ANY_INLINE_TAG_PATTERN =
            Pattern.compile("<[^>]+>");
    private static final Pattern OFFSET_TAG_PATTERN =
            Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    /** Minimum intro duration before the first lyric line to insert a leading {@code ...} break. */
    public static final long MIN_INTRO_INTERLUDE_MS = 4500L;
    /** Minimum quiet gap between finished singing and next line to insert a mid-song {@code ...}. */
    public static final long MIN_GAP_INTERLUDE_MS = 3800L;
    /** Minimum visible duration of a synthesized {@code ...} interlude indicator. */
    public static final long MIN_INTERLUDE_VISIBLE_MS = 2800L;

    /** Default scroll/focus anticipation lead matching Apple Music's line offset (-500ms). */
    public static final long DEFAULT_LINE_ADVANCE_LEAD_MS = 500L;
    /** Anticipation lead while exiting an instrumental {@code ...} break (-1300ms). */
    public static final long INTERLUDE_EXIT_LEAD_MS = 1300L;
    /** Karaoke word-sweep audio/display pipeline lead (-100ms). */
    public static final long WORD_SWEEP_LEAD_MS = 100L;
    /** Base stagger quantum (ms) for Apple Music's decelerating triangular cascade wave. */
    public static final int CASCADE_BASE_DELAY_MS = 25;

    private static final long INTERLUDE_LEAD_IN_MS = 280L;
    private static final long INTERLUDE_LEAD_OUT_MS = 220L;

    private record RawEntry(
            long timestampMs,
            long explicitEndMs,
            String text,
            List<LyricLine.Word> words,
            boolean parenthetical,
            int order) {}

    private LrcParser() {}

    /**
     * Parses standard, multi-timestamp, or enhanced word-timed LRC content into a chronological
     * list of {@link LyricLine} with resolved vocal windows and concurrent overlap support.
     */
    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] rawLines = lrcContent.split("\r?\n");
        long globalOffsetMs = 0L;

        for (String rawLine : rawLines) {
            Matcher offsetMatcher = OFFSET_TAG_PATTERN.matcher(rawLine.trim());
            if (offsetMatcher.find()) {
                try {
                    globalOffsetMs = Long.parseLong(offsetMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }

        List<RawEntry> entries = new ArrayList<>();
        List<Long> stopTimestamps = new ArrayList<>();
        int order = 0;

        for (String rawLine : rawLines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = TIME_TAG_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastTagEnd = 0;

            while (matcher.find()) {
                long timeMs = parseTimeGroup(matcher.group(1), matcher.group(2), matcher.group(3), globalOffsetMs);
                timestamps.add(timeMs);
                lastTagEnd = matcher.end();
            }

            if (timestamps.isEmpty()) continue;

            String rawBody = trimmed.substring(lastTagEnd).trim();
            if (rawBody.isEmpty()) {
                // Empty timestamp tag marks the end of the preceding vocal line (break / interlude start)
                stopTimestamps.addAll(timestamps);
                continue;
            }

            long baseTs = timestamps.get(0);
            ParsedBody parsed = parseEnhancedBody(rawBody, baseTs, globalOffsetMs);
            if (parsed.text.isEmpty()) {
                stopTimestamps.addAll(timestamps);
                continue;
            }

            boolean parenthetical = isParentheticalLine(parsed.text);
            for (long ts : timestamps) {
                long delta = ts - baseTs;
                List<LyricLine.Word> shiftedWords = shiftWords(parsed.words, delta);
                long shiftedEnd = parsed.explicitEndMs > 0L ? Math.max(ts + 250L, parsed.explicitEndMs + delta) : -1L;
                entries.add(new RawEntry(ts, shiftedEnd, parsed.text, shiftedWords, parenthetical, order++));
            }
        }

        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        entries.sort(Comparator.comparingLong(RawEntry::timestampMs).thenComparingInt(RawEntry::order));
        Collections.sort(stopTimestamps);

        return resolveTimings(entries, stopTimestamps);
    }

    /**
     * Returns a display timeline with synthesized {@code ...} instrumental interlude entries
     * inserted before long intros and during long quiet gaps between lyric lines.
     */
    public static List<LyricLine> withInterludes(List<LyricLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> timeline = new ArrayList<>(lines.size() + 6);

        // Leading intro interlude if the first lyric enters late enough
        LyricLine first = lines.get(0);
        if (!first.isInterlude() && first.timestampMs() >= MIN_INTRO_INTERLUDE_MS) {
            long start = Math.min(500L, first.timestampMs() / 6L);
            long end = first.timestampMs() - INTERLUDE_LEAD_OUT_MS;
            if (end - start >= MIN_INTERLUDE_VISIBLE_MS) {
                timeline.add(LyricLine.interlude(start, end));
            }
        }

        long maxEndSoFar = 0L;
        for (int i = 0; i < lines.size(); i++) {
            LyricLine current = lines.get(i);
            if (i > 0 && !current.isInterlude()) {
                long gap = current.timestampMs() - maxEndSoFar;
                if (gap >= MIN_GAP_INTERLUDE_MS) {
                    long interludeStart = maxEndSoFar + INTERLUDE_LEAD_IN_MS;
                    long interludeEnd = current.timestampMs() - INTERLUDE_LEAD_OUT_MS;
                    if (interludeEnd - interludeStart >= MIN_INTERLUDE_VISIBLE_MS) {
                        timeline.add(LyricLine.interlude(interludeStart, interludeEnd));
                    }
                }
            }
            timeline.add(current);
            if (current.endTimestampMs() > maxEndSoFar) {
                maxEndSoFar = current.endTimestampMs();
            }
        }

        return timeline;
    }

    /**
     * Finds the primary (latest-started) active lyric line index for {@code positionMs}.
     * Preserves standard binary-search contract: returns {@code -1} before the first line.
     */
    public static int findActiveLineIndex(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        if (positionMs < lines.get(0).timestampMs()) return -1;

        int low = 0;
        int high = lines.size() - 1;
        int best = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long lineTime = lines.get(mid).timestampMs();
            if (lineTime <= positionMs) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return best;
    }

    /**
     * Returns all concurrently active line indices at {@code positionMs}, supporting simultaneous
     * duets, harmonies, overlapping backing vocals, and interludes.
     */
    public static List<Integer> findActiveLineIndices(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return Collections.emptyList();
        List<Integer> active = new ArrayList<>(3);
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (line.timestampMs() > positionMs) {
                break;
            }
            if (line.isActiveAt(positionMs)) {
                active.add(i);
            }
        }
        return active;
    }

    /**
     * Computes Apple Music's dynamic line-advance lookahead lead (in milliseconds) for smooth
     * scroll anticipation between consecutive lyric lines.
     *
     * <p>Maps the inter-line quiet gap {@code [200ms, 750ms]} linearly onto {@code [480ms, 750ms]}
     * so rapid lyrics stay snappy while spaced phrases glide into position naturally.
     */
    public static long computeLineAdvanceLeadMs(
            List<LyricLine> lines, int currentIndex, boolean exitingInterlude) {
        if (exitingInterlude) {
            return INTERLUDE_EXIT_LEAD_MS;
        }
        if (lines == null || currentIndex < 0 || currentIndex + 1 >= lines.size()) {
            return DEFAULT_LINE_ADVANCE_LEAD_MS;
        }
        LyricLine cur = lines.get(currentIndex);
        LyricLine next = lines.get(currentIndex + 1);
        if (cur.isInterlude()) {
            // Short interludes scale their exit lead down slightly so dots stay visible
            return Math.min(INTERLUDE_EXIT_LEAD_MS, Math.max(500L, Math.round(cur.durationMs() * 0.36f)));
        }
        if (next.isInterlude()) {
            return DEFAULT_LINE_ADVANCE_LEAD_MS;
        }
        long gapMs = next.timestampMs() - cur.endTimestampMs();
        long clamped = Math.max(200L, Math.min(750L, gapMs));
        float fraction = (clamped - 200f) / 550f;
        return Math.round(480f + (750f - 480f) * fraction);
    }

    /**
     * Computes Apple Music's decelerating triangular cascade start delay (ms) for row {@code rowIndex}
     * relative to {@code anchorIndex}. Rows at or above the active anchor move immediately (0ms);
     * rows below follow in a smooth diminishing wave ({@code d = 1..4 -> 25, 44, 56, 63ms}).
     */
    public static long computeCascadeDelayMs(int rowIndex, int anchorIndex) {
        if (anchorIndex < 0 || rowIndex <= anchorIndex) {
            return 0L;
        }
        int d = Math.min(rowIndex - anchorIndex, 4);
        double wave = (5.0 * d) - ((d * (d + 1.0)) / 2.0);
        return Math.round((CASCADE_BASE_DELAY_MS * 0.25) * wave);
    }

    /**
     * Computes the maximum horizontal text layout width (px) guaranteed to remain strictly inside
     * its horizontal container spacing even at {@code maxActiveScale}.
     */
    public static int computeSafeLineMaxWidthPx(
            int containerWidthPx,
            int outerMarginStartPx,
            int outerMarginEndPx,
            int innerPadStartPx,
            int innerPadEndPx,
            float maxActiveScale) {
        int available = Math.max(
                0,
                containerWidthPx
                        - Math.max(0, outerMarginStartPx)
                        - Math.max(0, outerMarginEndPx)
                        - Math.max(0, innerPadStartPx)
                        - Math.max(0, innerPadEndPx));
        float scale = Math.max(1.0f, Float.isFinite(maxActiveScale) ? maxActiveScale : 1.0f);
        return Math.max(0, (int) Math.floor(available / scale));
    }

    private static List<LyricLine> resolveTimings(List<RawEntry> entries, List<Long> stopTimestamps) {
        int n = entries.size();
        List<LyricLine> resolved = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            RawEntry entry = entries.get(i);
            long startMs = entry.timestampMs();

            // Find next entry with a strictly later start timestamp (so same-timestamp duets share window)
            int nextDistinctIdx = -1;
            for (int j = i + 1; j < n; j++) {
                if (entries.get(j).timestampMs() > startMs) {
                    nextDistinctIdx = j;
                    break;
                }
            }
            long nextDistinctStart = nextDistinctIdx >= 0 ? entries.get(nextDistinctIdx).timestampMs() : -1L;

            // Check if an explicit stop timestamp sits between startMs and nextDistinctStart
            long stopMarker = findFirstStopBetween(stopTimestamps, startMs, nextDistinctStart);

            long endMs;
            if (entry.explicitEndMs() > startMs) {
                endMs = entry.explicitEndMs();
            } else {
                long estimated = LyricLine.estimateVocalDurationMs(entry.text());
                if (stopMarker > startMs) {
                    endMs = stopMarker;
                } else if (nextDistinctStart > startMs) {
                    // Plain LRC provides line starts, not vocal durations. Do not invent
                    // overlaps or instrumental breaks from the number of characters.
                    endMs = nextDistinctStart;
                } else {
                    // Final line in the song
                    endMs = startMs + Math.max(estimated, 3200L);
                }
            }

            // Determine whether this entry should style as a secondary/backing vocal
            boolean hasLeadSibling = false;
            if (entry.parenthetical()) {
                if (i > 0 && !entries.get(i - 1).parenthetical()
                        && startMs - entries.get(i - 1).timestampMs() <= 3500L) {
                    hasLeadSibling = true;
                } else if (i + 1 < n && !entries.get(i + 1).parenthetical()
                        && entries.get(i + 1).timestampMs() - startMs <= 1500L) {
                    hasLeadSibling = true;
                }
            }

            // Finalize word end timestamps against the resolved line endMs
            List<LyricLine.Word> finalWords = finalizeWords(entry.words(), endMs);
            if (!finalWords.isEmpty()) {
                long lastWordEnd = finalWords.get(finalWords.size() - 1).endMs();
                if (lastWordEnd > endMs) {
                    endMs = lastWordEnd;
                }
            }

            resolved.add(new LyricLine(startMs, endMs, entry.text(), finalWords, false, hasLeadSibling));
        }

        return resolved;
    }

    private static long findFirstStopBetween(List<Long> stops, long startMs, long nextStartMs) {
        if (stops.isEmpty()) return -1L;
        for (int i = 0; i < stops.size(); i++) {
            long s = stops.get(i);
            if (s <= startMs + 200L) continue;
            if (nextStartMs > 0L && s >= nextStartMs) break;
            return s;
        }
        return -1L;
    }

    private record ParsedBody(String text, List<LyricLine.Word> words, long explicitEndMs) {}

    private record WordDraft(long startMs, int rawStart, int rawEnd, String seg) {}

    private static ParsedBody parseEnhancedBody(String rawBody, long lineStartMs, long offsetMs) {
        Matcher m = INLINE_TIME_TAG_PATTERN.matcher(rawBody);
        if (!m.find()) {
            String plain = ANY_INLINE_TAG_PATTERN.matcher(rawBody).replaceAll("");
            plain = collapseSpaces(plain);
            return new ParsedBody(plain, List.of(), -1L);
        }

        // Collect (timestamp, textSegment) spans across the line
        List<Long> segTimes = new ArrayList<>();
        List<String> segTexts = new ArrayList<>();

        int pos = 0;
        long currentStart = lineStartMs;
        m.reset();
        while (m.find()) {
            if (m.start() > pos) {
                String before = ANY_INLINE_TAG_PATTERN.matcher(rawBody.substring(pos, m.start())).replaceAll("");
                if (!before.isEmpty()) {
                    segTimes.add(currentStart);
                    segTexts.add(before);
                }
            }
            currentStart = parseTimeGroup(m.group(1), m.group(2), m.group(3), offsetMs);
            pos = m.end();
        }
        long explicitEndMs = -1L;
        if (pos < rawBody.length()) {
            String tail = ANY_INLINE_TAG_PATTERN.matcher(rawBody.substring(pos)).replaceAll("");
            if (!tail.isEmpty()) {
                segTimes.add(currentStart);
                segTexts.add(tail);
            } else {
                explicitEndMs = currentStart;
            }
        } else {
            // Trailing <mm:ss.xx> tag at the end of the line marks exact finish time
            explicitEndMs = currentStart;
        }

        // Build clean single-spaced text while preserving exact character ranges per timed segment
        StringBuilder clean = new StringBuilder(rawBody.length());
        List<WordDraft> drafts = new ArrayList<>(segTexts.size());
        boolean prevSpace = true; // strips leading whitespace

        for (int i = 0; i < segTexts.size(); i++) {
            String rawSeg = segTexts.get(i);
            long segTime = segTimes.get(i);
            int startIdx = clean.length();
            for (int cIdx = 0; cIdx < rawSeg.length(); cIdx++) {
                char c = rawSeg.charAt(cIdx);
                if (Character.isWhitespace(c)) {
                    if (!prevSpace) {
                        clean.append(' ');
                        prevSpace = true;
                    }
                } else {
                    clean.append(c);
                    prevSpace = false;
                }
            }
            int endIdx = clean.length();
            if (endIdx > startIdx) {
                // Trim leading/trailing space from the word's own highlight range
                int wStart = startIdx;
                int wEnd = endIdx;
                while (wStart < wEnd && clean.charAt(wStart) == ' ') wStart++;
                while (wEnd > wStart && clean.charAt(wEnd - 1) == ' ') wEnd--;
                if (wEnd > wStart) {
                    drafts.add(new WordDraft(segTime, wStart, wEnd, clean.substring(wStart, wEnd)));
                }
            }
        }

        // Trim trailing space from clean text
        int finalLen = clean.length();
        while (finalLen > 0 && clean.charAt(finalLen - 1) == ' ') {
            finalLen--;
        }
        String finalText = clean.substring(0, finalLen);
        if (finalText.isEmpty() || drafts.isEmpty()) {
            return new ParsedBody(finalText, List.of(), explicitEndMs);
        }

        List<LyricLine.Word> words = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            WordDraft d = drafts.get(i);
            long endMs;
            if (i + 1 < drafts.size()) {
                endMs = Math.max(d.startMs() + 60L, drafts.get(i + 1).startMs());
            } else if (explicitEndMs > d.startMs()) {
                endMs = explicitEndMs;
            } else {
                long estWord = Math.max(240L, Math.min(1200L, d.seg().length() * 90L));
                endMs = d.startMs() + estWord;
            }
            int clampedStart = Math.min(d.rawStart(), finalLen);
            int clampedEnd = Math.min(d.rawEnd(), finalLen);
            if (clampedEnd > clampedStart) {
                words.add(new LyricLine.Word(d.startMs(), endMs, clampedStart, clampedEnd, d.seg()));
            }
        }

        return new ParsedBody(finalText, words, explicitEndMs);
    }

    private static List<LyricLine.Word> shiftWords(List<LyricLine.Word> words, long deltaMs) {
        if (words.isEmpty() || deltaMs == 0L) return words;
        List<LyricLine.Word> shifted = new ArrayList<>(words.size());
        for (LyricLine.Word w : words) {
            shifted.add(new LyricLine.Word(
                    Math.max(0L, w.startMs() + deltaMs),
                    Math.max(0L, w.endMs() + deltaMs),
                    w.startChar(),
                    w.endChar(),
                    w.text()));
        }
        return shifted;
    }

    private static List<LyricLine.Word> finalizeWords(List<LyricLine.Word> words, long lineEndMs) {
        if (words.isEmpty()) return List.of();
        int lastIdx = words.size() - 1;
        LyricLine.Word last = words.get(lastIdx);
        if (last.endMs() > lineEndMs && lineEndMs > last.startMs() + 80L) {
            List<LyricLine.Word> adjusted = new ArrayList<>(words);
            adjusted.set(lastIdx, new LyricLine.Word(
                    last.startMs(), lineEndMs, last.startChar(), last.endChar(), last.text()));
            return adjusted;
        }
        return words;
    }

    private static long parseTimeGroup(String minStr, String secStr, String fracStr, long offsetMs) {
        long minutes = Long.parseLong(minStr);
        long seconds = Long.parseLong(secStr);
        long millis = 0L;
        if (fracStr != null && !fracStr.isEmpty()) {
            long rawFrac = Long.parseLong(fracStr);
            if (fracStr.length() == 1) millis = rawFrac * 100L;
            else if (fracStr.length() == 2) millis = rawFrac * 10L;
            else millis = rawFrac;
        }
        return Math.max(0L, (minutes * 60_000L) + (seconds * 1_000L) + millis + offsetMs);
    }

    private static String collapseSpaces(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private static boolean isParentheticalLine(String text) {
        if (text == null || text.length() < 3) return false;
        return (text.startsWith("(") && text.endsWith(")"))
                || (text.startsWith("\uFF08") && text.endsWith("\uFF09"));
    }
}
