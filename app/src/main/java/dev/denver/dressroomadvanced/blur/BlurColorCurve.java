package dev.denver.dressroomadvanced;

/** Adjusts Samsung's saturation, curve bias and output luminance without changing input bounds. */
final class BlurColorCurve {
    private BlurColorCurve() {}

    static void adjust(float[] values, int saturation, int contrast, int brightness) {
        values[0] = clamp(values[0] + saturation / 100f, -1f, 1f);
        values[1] = clamp(values[1] + contrast, -100f, 100f);
        float offset = brightness * 2.55f;
        values[4] = clamp(values[4] + offset, 0f, 255f);
        values[5] = clamp(values[5] + offset, 0f, 255f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
