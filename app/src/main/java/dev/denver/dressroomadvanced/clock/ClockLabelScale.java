package dev.denver.dressroomadvanced;

final class ClockLabelScale {
    static final int MIN = 50;
    static final int DEFAULT = 100;
    static final int MAX = 200;
    static final String DATE = "dateSizePercent";
    static final String WEATHER = "weatherSizePercent";
    static final String REFERENCE = "labelReferenceScale";

    private ClockLabelScale() {}

    static int clamp(int percent) {
        return Math.max(MIN, Math.min(MAX, percent));
    }

    static float reference(float value, int date, int weather) {
        if (Float.isFinite(value) && value > 0f) return value;
        return date == DEFAULT && weather == DEFAULT ? 0f : 1f;
    }

    static float labelFactor(float reference, float viewScale, float nativeScale) {
        return reference / zoom(viewScale) / zoom(nativeScale);
    }

    static float zoom(float value) {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }
}
