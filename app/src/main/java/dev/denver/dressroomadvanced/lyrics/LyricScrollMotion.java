package dev.denver.dressroomadvanced.lyrics;

import java.util.function.DoubleUnaryOperator;

/** A continuous viewport scroll, with row delays expressed relative to that viewport. */
public final class LyricScrollMotion {
    private final DoubleUnaryOperator easing;
    private int from, target;
    private long started;
    private float duration = 1f;
    private boolean active;

    public LyricScrollMotion(DoubleUnaryOperator easing) {
        this.easing = easing;
    }

    public void begin(int current, int target, long now, float durationMs) {
        from = current;
        this.target = target;
        started = now;
        duration = Math.max(1f, durationMs);
        active = true;
    }

    public boolean active() { return active; }
    public void cancel() { active = false; }

    private float progress(long now, long delay) {
        float t = Math.max(0f, Math.min(1f, (now - started - delay) / duration));
        return (float) easing.applyAsDouble(t);
    }

    public int position(long now) {
        return from + Math.round((target - from) * progress(now, 0L));
    }

    public float rowOffset(long now, long delay, float existingOffset) {
        float global = progress(now, 0L);
        float delayed = progress(now, delay);
        return existingOffset * (1f - global) + position(now) - from - (target - from) * delayed;
    }

    public boolean finished(long now, long longestDelay) {
        return now - started >= duration + longestDelay;
    }
}
