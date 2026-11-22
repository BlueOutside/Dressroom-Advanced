package dev.denver.dressroomadvanced.lyrics;

import java.util.Collections;
import java.util.List;

/**
 * Immutable timestamped lyric line supporting line-level LRC, enhanced-LRC
 * word/syllable timings, concurrent overlapping vocal windows, and synthesized
 * instrumental interlude ({@code ...}) entries.
 */
public final class LyricLine implements Comparable<LyricLine> {

    /** Immutable word or syllable timing span mapped onto {@link LyricLine#text()}. */
    public record Word(long startMs, long endMs, int startChar, int endChar, String text) {
        public Word {
            startMs = Math.max(0L, startMs);
            endMs = Math.max(startMs, endMs);
            startChar = Math.max(0, startChar);
            endChar = Math.max(startChar, endChar);
            text = text != null ? text : "";
        }
    }

    private final long timestampMs;
    private final long endTimestampMs;
    private final String text;
    private final List<Word> words;
    private final boolean interlude;
    private final boolean backgroundVocal;

    public LyricLine(long timestampMs, String text) {
        this(timestampMs, Math.max(0L, timestampMs) + estimateVocalDurationMs(text), text, List.of(), false, false);
    }

    public LyricLine(
            long timestampMs,
            long endTimestampMs,
            String text,
            List<Word> words,
            boolean interlude,
            boolean backgroundVocal) {
        this.timestampMs = Math.max(0L, timestampMs);
        this.text = text != null ? text.trim() : "";
        long minEnd = this.timestampMs + 1L;
        this.endTimestampMs = Math.max(minEnd, endTimestampMs);
        this.words = (words == null || words.isEmpty())
                ? Collections.emptyList()
                : List.copyOf(words);
        this.interlude = interlude;
        this.backgroundVocal = backgroundVocal;
    }

    public static LyricLine interlude(long startMs, long endMs) {
        return new LyricLine(startMs, Math.max(startMs + 500L, endMs), "...", List.of(), true, false);
    }

    public long timestampMs() {
        return timestampMs;
    }

    public long endTimestampMs() {
        return endTimestampMs;
    }

    public String text() {
        return text;
    }

    public List<Word> words() {
        return words;
    }

    public boolean hasWordTimings() {
        return !words.isEmpty();
    }

    public boolean isInterlude() {
        return interlude;
    }

    public boolean isBackgroundVocal() {
        return backgroundVocal;
    }

    public long durationMs() {
        return Math.max(1L, endTimestampMs - timestampMs);
    }

    /** Returns true while this line (or interlude) should be actively animated at {@code positionMs}. */
    public boolean isActiveAt(long positionMs) {
        return positionMs >= timestampMs && positionMs < endTimestampMs;
    }

    /**
     * Computes normalized fill progress in {@code [0, 1]} at {@code positionMs},
     * representing character position across {@link #text()} (or time progress for interludes).
     */
    public float progressAt(long positionMs) {
        if (positionMs <= timestampMs) return 0f;
        if (positionMs >= endTimestampMs) return 1f;

        if (interlude || text.isEmpty()) {
            return clamp01((float) (positionMs - timestampMs) / (float) durationMs());
        }

        int totalChars = text.length();
        if (!words.isEmpty()) {
            return wordProgressAt(positionMs, totalChars);
        }

        // Line-only timestamps cannot reveal syllable pacing. Use a steady sweep.
        return clamp01((float) (positionMs - timestampMs) / (float) durationMs());
    }

    /**
     * Computes Apple Music style luminous emphasis glow intensity in {@code [0, 1]} at {@code positionMs}.
     * Sustained word-timed syllables ({@code >= 650ms}) swell smoothly up to peak glow proportional
     * to note duration, while normal active vocals maintain a subtle crisp presence.
     */
    public float emphasisGlowAt(long positionMs) {
        if (interlude || text.isEmpty() || positionMs < timestampMs || positionMs > endTimestampMs) {
            return 0f;
        }
        if (!words.isEmpty()) {
            for (int i = 0; i < words.size(); i++) {
                Word w = words.get(i);
                if (positionMs >= w.startMs() && positionMs <= w.endMs()) {
                    long dur = Math.max(1L, w.endMs() - w.startMs());
                    if (dur < 650L) {
                        return 0.28f;
                    }
                    // Duration factor in [1.0, 2.0] matching Apple Music's sustained-word emphasis window
                    float durNorm = clamp01((Math.min(2000f, (float) dur) - 650f) / 1350f);
                    float peak = 0.45f + 0.55f * durNorm;
                    float t = clamp01((float) (positionMs - w.startMs()) / (float) dur);
                    // Smooth rise over first 35%, gentle sustain and ease-out over remaining 65%
                    float env = t < 0.35f
                            ? smoothStep(t / 0.35f)
                            : 1f - 0.55f * smoothStep((t - 0.35f) / 0.65f);
                    return clamp01(0.28f + (peak - 0.28f) * env);
                }
            }
            return 0.22f;
        }
        // Line-level LRC: clean soft luminous glow during active sweep, easing down near line end
        float p = clamp01((float) (positionMs - timestampMs) / (float) durationMs());
        float edge = p < 0.12f ? smoothStep(p / 0.12f)
                : (p > 0.88f ? 1f - 0.5f * smoothStep((p - 0.88f) / 0.12f) : 1f);
        return 0.36f * edge;
    }

    private static float smoothStep(float t) {
        float c = clamp01(t);
        return c * c * (3f - 2f * c);
    }

    private float wordProgressAt(long positionMs, int totalChars) {
        Word first = words.get(0);
        if (positionMs <= first.startMs()) {
            return 0f;
        }
        Word last = words.get(words.size() - 1);
        if (positionMs >= last.endMs()) {
            return 1f;
        }

        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            if (positionMs < w.startMs()) {
                return clamp01((float) w.startChar() / (float) totalChars);
            }
            if (positionMs <= w.endMs()) {
                long span = Math.max(1L, w.endMs() - w.startMs());
                float local = clamp01((float) (positionMs - w.startMs()) / (float) span);
                float charPos = w.startChar() + local * (w.endChar() - w.startChar());
                return clamp01(charPos / (float) totalChars);
            }
        }
        return 1f;
    }

    /** Estimates natural vocal delivery duration for line-only LRC timing and interlude detection. */
    public static long estimateVocalDurationMs(String text) {
        if (text == null || text.trim().isEmpty()) return 1600L;
        String t = text.trim();
        float units = 0f;
        int wordCount = 1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                units += 0.9f;
                wordCount++;
            } else if (c == ',' || c == ';' || c == ':' || c == '-') {
                units += 2.2f;
            } else if (c == '.' || c == '!' || c == '?') {
                units += 2.8f;
            } else if (isCjk(c)) {
                units += 3.2f;
            } else {
                units += 1.0f;
            }
        }
        long ms = Math.round(units * 72f + wordCount * 55f);
        return Math.max(1100L, Math.min(7200L, ms));
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public int compareTo(LyricLine other) {
        int cmp = Long.compare(this.timestampMs, other.timestampMs);
        if (cmp != 0) return cmp;
        // Interludes sort before lines at the exact same timestamp; preserve stability otherwise
        return Boolean.compare(other.interlude, this.interlude);
    }
}
