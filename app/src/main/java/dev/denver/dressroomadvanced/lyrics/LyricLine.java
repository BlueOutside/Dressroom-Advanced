package dev.denver.dressroomadvanced.lyrics;

/**
 * Immutable timestamped lyric line parsed from LRC format.
 */
public record LyricLine(long timestampMs, String text) implements Comparable<LyricLine> {
    public LyricLine {
        text = text != null ? text.trim() : "";
        timestampMs = Math.max(0L, timestampMs);
    }

    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(this.timestampMs, other.timestampMs);
    }
}
