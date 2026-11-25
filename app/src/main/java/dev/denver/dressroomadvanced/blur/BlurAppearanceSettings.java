package dev.denver.dressroomadvanced;

import android.os.Bundle;

import org.json.JSONObject;

final class BlurAppearanceSettings {
    static final String NEUTRAL_CLOCK = "neutralClockBlur";
    static final String[] SURFACES = {"clockBlur", "widgetBlur", "nowBarBlur", AodWidgetAppearance.PROFILE};
    static final String[] OPTIONS = {
        "Custom", "Radius", "Saturation", "Contrast", "Brightness", "Tint", "TintOpacity"
    };

    private BlurAppearanceSettings() {}

    static void clean(Bundle input, Bundle out) {
        for (String surface : SURFACES) {
            out.putBoolean(
                    surface + "Custom", input != null && input.getBoolean(surface + "Custom"));
            boolean aod = surface.equals(AodWidgetAppearance.PROFILE);
            int radius = aod ? 110 : 200;
            out.putInt(surface + "Radius", clamp(input, surface + "Radius", radius, 1, 300));
            out.putInt(surface + "Saturation", clamp(input, surface + "Saturation", 0, -100, 100));
            out.putInt(surface + "Contrast", clamp(input, surface + "Contrast", 0, -100, 100));
            out.putInt(surface + "Brightness", clamp(input, surface + "Brightness", 0, -100, 100));
            out.putInt(surface + "Tint", clamp(input, surface + "Tint", 0, 0, aod ? 3 : 2));
            out.putInt(surface + "TintOpacity", clamp(input, surface + "TintOpacity", 20, 0, 100));
        }
    }

    private static int clamp(Bundle b, String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, b == null ? fallback : b.getInt(key, fallback)));
    }

    static void write(Bundle b, JSONObject json) throws org.json.JSONException {
        for (String surface : SURFACES)
            for (String option : OPTIONS) {
                String key = surface + option;
                json.put(key, option.equals("Custom") ? b.getBoolean(key) : b.getInt(key));
            }
    }

    static void read(JSONObject json, Bundle b) {
        for (String surface : SURFACES)
            for (String option : OPTIONS) {
                String key = surface + option;
                if (!json.has(key)) continue;
                if (option.equals("Custom")) b.putBoolean(key, json.optBoolean(key));
                else b.putInt(key, json.optInt(key));
            }
    }

    static boolean same(Bundle a, Bundle b) {
        for (String surface : SURFACES) if (!same(a, b, surface)) return false;
        return true;
    }

    static boolean same(Bundle a, Bundle b, String surface) {
        if (surface.equals("clockBlur") && a.getBoolean(NEUTRAL_CLOCK) != b.getBoolean(NEUTRAL_CLOCK))
            return false;
        if (surface.equals(AodWidgetAppearance.PROFILE)
                && (a.getBoolean(AodWidgetAppearance.NEUTRAL) != b.getBoolean(AodWidgetAppearance.NEUTRAL)
                    || a.getBoolean(AodWidgetAppearance.KEY) != b.getBoolean(AodWidgetAppearance.KEY)))
            return false;
        for (String option : OPTIONS) {
            String key = surface + option;
            if (option.equals("Custom")) {
                if (a.getBoolean(key) != b.getBoolean(key)) return false;
            } else if (a.getInt(key) != b.getInt(key)) return false;
        }
        return true;
    }
}
