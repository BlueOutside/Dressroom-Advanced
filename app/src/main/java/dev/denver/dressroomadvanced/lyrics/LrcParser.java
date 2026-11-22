package dev.denver.dressroomadvanced.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust LRC timestamp parser with support for multi-tag lines and subsecond fractions.
 */
public final class LrcParser {
    private static final Pattern TIME_TAG_PATTERN =
            Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern INLINE_TAG_PATTERN =
            Pattern.compile("<[^>]+>");
    private static final Pattern OFFSET_TAG_PATTERN =
            Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {}

    /**
     * Parses standard or multi-timestamp LRC content into a chronological list of {@link LyricLine}.
     */
    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> result = new ArrayList<>();
        String[] lines = lrcContent.split("\r?\n");
        long globalOffsetMs = 0L;

        // First pass: look for offset tags if any
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            Matcher offsetMatcher = OFFSET_TAG_PATTERN.matcher(trimmed);
            if (offsetMatcher.find()) {
                try {
                    globalOffsetMs = Long.parseLong(offsetMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = TIME_TAG_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastTagEnd = 0;

            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fracStr = matcher.group(3);
                long millis = 0L;
                if (fracStr != null && !fracStr.isEmpty()) {
                    long rawFrac = Long.parseLong(fracStr);
                    if (fracStr.length() == 1) millis = rawFrac * 100;
                    else if (fracStr.length() == 2) millis = rawFrac * 10;
                    else millis = rawFrac;
                }
                long timeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis + globalOffsetMs;
                timestamps.add(Math.max(0L, timeMs));
                lastTagEnd = matcher.end();
            }

            if (!timestamps.isEmpty()) {
                String lyricText = trimmed.substring(lastTagEnd).trim();
                // Strip enhanced-LRC word timestamps like <01:51.01>
                lyricText = INLINE_TAG_PATTERN.matcher(lyricText).replaceAll("").trim();
                lyricText = lyricText.replaceAll("\\s+", " ").trim();
                if (!lyricText.isEmpty()) {
                    for (long timestamp : timestamps) {
                        result.add(new LyricLine(timestamp, lyricText));
                    }
                }
            }
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Finds the index of the active lyric line for the given track position.
     * Returns -1 if lines are empty or the position is before the first lyric line.
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
}
