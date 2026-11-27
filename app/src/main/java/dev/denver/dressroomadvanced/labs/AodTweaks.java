package dev.denver.dressroomadvanced;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure model for the AOD Extras tweaks: option keys, sanitizing, persistence helpers, feature-flag
 * token stripping, clock stretch physics presets, and the StandBy low-light bedside clock styling
 * and hysteresis gate. Nothing here touches Samsung code.
 */
final class AodTweaks {
    /** Lets Dressroom save the subject cutout Samsung rejected, enabling its Erase background. */
    static final String ERASE_UNLOCK = "aodEraseUnlock";
    /** Reports the AOD fullscreen capability on devices whose floating feature says 0. */
    static final String FULLSCREEN_UNLOCK = "aodFullscreenUnlock";
    /** Strips the m55 exclusion token so Settings > AOD > When to show > Auto is exposed. */
    static final String AUTO_AOD_UNLOCK = "aodAutoUnlock";
    /** Enables SystemUI's dormant LowLightClockDreamService while charging in dark rooms. */
    static final String LOW_LIGHT_CLOCK = "lowLightClock";
    /** Shows a formatted date line above the StandBy bedside clock. */
    static final String LOW_LIGHT_SHOW_DATE = "lowLightShowDate";
    /** Smoothly shifts the StandBy bedside clock every minute to protect OLED panels. */
    static final String LOW_LIGHT_BURN_IN = "lowLightBurnIn";

    static final String STRETCH_SPEED = "stretchSpeed";
    static final String STRETCH_BOUNCE = "stretchBounce";
    static final String LOW_LIGHT_LUX = "lowLightLux";
    static final String LOW_LIGHT_THEME = "lowLightTheme";
    static final String LOW_LIGHT_FONT = "lowLightFont";
    static final String LOW_LIGHT_BRIGHTNESS = "lowLightBrightness";

    static final int MAX_SPEED_LEVEL = 3;
    static final int MAX_BOUNCE = 60;
    static final int MIN_LOW_LIGHT_LUX = 1;
    static final int MAX_LOW_LIGHT_LUX = 25;
    static final int DEFAULT_LOW_LIGHT_LUX = 5;
    static final int MIN_LOW_LIGHT_BRIGHTNESS = 0;
    static final int MAX_LOW_LIGHT_BRIGHTNESS = 30;
    static final int DEFAULT_LOW_LIGHT_BRIGHTNESS = 4;

    static final String[] LOW_LIGHT_THEME_LABELS = {
        "StandBy Red", "Warm Amber", "Moonlight Blue", "Emerald", "Soft White"
    };

    /** {primaryClockColor, secondaryDateChargingColor, subtleGlowColor} per theme. */
    private static final int[][] LOW_LIGHT_PALETTES = {
        {0xFFFF3B30, 0xB8FF5A4E, 0x4CFF2D20},
        {0xFFFFA726, 0xB8FFBE55, 0x44FF9100},
        {0xFF7CC8FF, 0xB096D8FF, 0x3E4FC3F7},
        {0xFF34D369, 0xB062E28C, 0x3E00C853},
        {0xFFE5E5EA, 0xA8C7C7CC, 0x2EFFFFFF}
    };

    static final String[] LOW_LIGHT_FONT_LABELS = {
        "StandBy Bold", "Minimal Thin", "Editorial Serif", "Retro Mono", "Custom font"
    };

    static String[] lowLightThemeLabels(android.content.Context context) {
        return new String[] {
            ModuleStrings.getOrFallback(context, R.string.standby_theme_red, LOW_LIGHT_THEME_LABELS[0]),
            ModuleStrings.getOrFallback(context, R.string.standby_theme_amber, LOW_LIGHT_THEME_LABELS[1]),
            ModuleStrings.getOrFallback(context, R.string.standby_theme_blue, LOW_LIGHT_THEME_LABELS[2]),
            ModuleStrings.getOrFallback(context, R.string.standby_theme_emerald, LOW_LIGHT_THEME_LABELS[3]),
            ModuleStrings.getOrFallback(context, R.string.standby_theme_white, LOW_LIGHT_THEME_LABELS[4])
        };
    }

    static String[] lowLightFontLabels(android.content.Context context) {
        return new String[] {
            ModuleStrings.getOrFallback(context, R.string.standby_font_bold, LOW_LIGHT_FONT_LABELS[0]),
            ModuleStrings.getOrFallback(context, R.string.standby_font_thin, LOW_LIGHT_FONT_LABELS[1]),
            ModuleStrings.getOrFallback(context, R.string.standby_font_serif, LOW_LIGHT_FONT_LABELS[2]),
            ModuleStrings.getOrFallback(context, R.string.standby_font_mono, LOW_LIGHT_FONT_LABELS[3]),
            ModuleStrings.getOrFallback(context, R.string.common_custom_font, LOW_LIGHT_FONT_LABELS[4])
        };
    }

    static final String FULLSCREEN_FEATURE = "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN";
    static final String AOD_ITEM_FEATURE = "SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_AOD_ITEM";
    static final String AUTO_AOD_EXCLUDED_TOKEN = "m55";

    static final String[] FLAGS = {
        ERASE_UNLOCK, FULLSCREEN_UNLOCK, AUTO_AOD_UNLOCK, LOW_LIGHT_CLOCK
    };

    static final String[] DEFAULT_TRUE_FLAGS = {
        LOW_LIGHT_SHOW_DATE, LOW_LIGHT_BURN_IN
    };

    /** accelUp, accelDown, maxSpeed, speedScale: Samsung's own AOD test mode presets. */
    private static final float[][] SPEEDS = {
        {0.012f, 0.008f, 0.060f, 1.00f},
        {0.015f, 0.010f, 0.075f, 1.15f},
        {0.020f, 0.014f, 0.100f, 1.35f},
        {0.028f, 0.020f, 0.135f, 1.60f}
    };

    /** The restitution field is stored negated by Samsung's physics loop. */
    private static final float RESTITUTION_SIGN = -1f;

    private AodTweaks() {}

    static int speed(Bundle b) {
        return b == null ? 0 : clampSpeed(b.getInt(STRETCH_SPEED, 0));
    }

    static int bounce(Bundle b) {
        return b == null ? 0 : clampBounce(b.getInt(STRETCH_BOUNCE, 0));
    }

    static int lowLightLux(Bundle b) {
        return b == null
                ? DEFAULT_LOW_LIGHT_LUX
                : clampLowLightLux(b.getInt(LOW_LIGHT_LUX, DEFAULT_LOW_LIGHT_LUX));
    }

    static int lowLightTheme(Bundle b) {
        return b == null ? 0 : clampLowLightTheme(b.getInt(LOW_LIGHT_THEME, 0));
    }

    static int lowLightFont(Bundle b) {
        return b == null ? 0 : clampLowLightFont(b.getInt(LOW_LIGHT_FONT, 0));
    }

    static int lowLightBrightness(Bundle b) {
        return b == null
                ? DEFAULT_LOW_LIGHT_BRIGHTNESS
                : clampLowLightBrightness(
                        b.getInt(LOW_LIGHT_BRIGHTNESS, DEFAULT_LOW_LIGHT_BRIGHTNESS));
    }

    static int clampSpeed(int value) {
        return Math.max(0, Math.min(MAX_SPEED_LEVEL, value));
    }

    static int clampBounce(int value) {
        return Math.max(0, Math.min(MAX_BOUNCE, value));
    }

    static int clampLowLightLux(int value) {
        return Math.max(MIN_LOW_LIGHT_LUX, Math.min(MAX_LOW_LIGHT_LUX, value));
    }

    static int clampLowLightTheme(int value) {
        return Math.max(0, Math.min(LOW_LIGHT_THEME_LABELS.length - 1, value));
    }

    static int clampLowLightFont(int value) {
        return Math.max(0, Math.min(LOW_LIGHT_FONT_LABELS.length - 1, value));
    }

    static int clampLowLightBrightness(int value) {
        return Math.max(MIN_LOW_LIGHT_BRIGHTNESS, Math.min(MAX_LOW_LIGHT_BRIGHTNESS, value));
    }

    static float lowLightBrightnessFloat(int percent) {
        int clamped = clampLowLightBrightness(percent);
        return clamped == 0 ? 0f : clamped / 100f;
    }

    static int[] lowLightPalette(int theme) {
        return LOW_LIGHT_PALETTES[clampLowLightTheme(theme)];
    }

    static float lowLightExitLux(int enterLux) {
        int clamped = clampLowLightLux(enterLux);
        return Math.max(clamped * 2.5f, clamped + 8f);
    }

    /** Smooth zero-mean Lissajous burn-in drift offset within {@code [-maxX, maxX] x [-maxY, maxY]}. */
    static float[] lowLightDriftOffset(int step, float maxX, float maxY) {
        double angle = (step % 12) * (Math.PI / 6.0);
        float dx = (float) (Math.sin(angle) * maxX);
        float dy = (float) (Math.sin(angle * 2.0) * maxY);
        return new float[] {dx, dy};
    }

    static boolean stretchActive(int speed, int bounce) {
        return clampSpeed(speed) != 0 || clampBounce(bounce) != 0;
    }

    /** Returns accelUp, accelDown, maxSpeed, speedScale, restitution for Samsung's config. */
    static float[] stretch(int speed, int bounce) {
        float[] base = SPEEDS[clampSpeed(speed)];
        return new float[] {
            base[0], base[1], base[2], base[3], RESTITUTION_SIGN * clampBounce(bounce) / 100f
        };
    }

    /**
     * Removes a standalone token (or {@code token=value} entry) from Samsung's comma-separated
     * {@code SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_AOD_ITEM} string without disturbing neighbors.
     */
    static String stripFeatureToken(String raw, String token) {
        if (raw == null || raw.isEmpty() || token == null || token.isEmpty()) return raw;
        String[] parts = raw.split(",", -1);
        StringBuilder out = null;
        boolean removed = false;
        for (String part : parts) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            String key = eq > 0 ? trimmed.substring(0, eq).trim() : trimmed;
            if (key.equalsIgnoreCase(token)) {
                removed = true;
                continue;
            }
            if (trimmed.isEmpty() && parts.length > 1) continue;
            if (out == null) out = new StringBuilder(raw.length());
            else out.append(',');
            out.append(trimmed);
        }
        if (!removed) return raw;
        return out == null ? "" : out.toString();
    }

    static void clean(Bundle input, Bundle out) {
        out.putInt(STRETCH_SPEED, speed(input));
        out.putInt(STRETCH_BOUNCE, bounce(input));
        out.putInt(LOW_LIGHT_LUX, lowLightLux(input));
        out.putInt(LOW_LIGHT_THEME, lowLightTheme(input));
        out.putInt(LOW_LIGHT_FONT, lowLightFont(input));
        out.putInt(LOW_LIGHT_BRIGHTNESS, lowLightBrightness(input));
    }

    static void write(Bundle b, JSONObject json) throws JSONException {
        json.put(STRETCH_SPEED, speed(b));
        json.put(STRETCH_BOUNCE, bounce(b));
        json.put(LOW_LIGHT_LUX, lowLightLux(b));
        json.put(LOW_LIGHT_THEME, lowLightTheme(b));
        json.put(LOW_LIGHT_FONT, lowLightFont(b));
        json.put(LOW_LIGHT_BRIGHTNESS, lowLightBrightness(b));
    }

    static void read(JSONObject json, Bundle b) {
        b.putInt(STRETCH_SPEED, clampSpeed(json.optInt(STRETCH_SPEED, 0)));
        b.putInt(STRETCH_BOUNCE, clampBounce(json.optInt(STRETCH_BOUNCE, 0)));
        b.putInt(LOW_LIGHT_LUX, clampLowLightLux(json.optInt(LOW_LIGHT_LUX, DEFAULT_LOW_LIGHT_LUX)));
        b.putInt(LOW_LIGHT_THEME, clampLowLightTheme(json.optInt(LOW_LIGHT_THEME, 0)));
        b.putInt(LOW_LIGHT_FONT, clampLowLightFont(json.optInt(LOW_LIGHT_FONT, 0)));
        b.putInt(
                LOW_LIGHT_BRIGHTNESS,
                clampLowLightBrightness(
                        json.optInt(LOW_LIGHT_BRIGHTNESS, DEFAULT_LOW_LIGHT_BRIGHTNESS)));
    }

    static boolean same(Bundle a, Bundle b) {
        return speed(a) == speed(b)
                && bounce(a) == bounce(b)
                && lowLightLux(a) == lowLightLux(b)
                && lowLightTheme(a) == lowLightTheme(b)
                && lowLightFont(a) == lowLightFont(b)
                && lowLightBrightness(a) == lowLightBrightness(b);
    }

    /**
     * Hysteresis and debounce state machine for ambient-light bedside clock switching.
     * Enters low-light after staying at or below {@code enterLux} for {@code enterDebounceMs}
     * (or immediately when already dreaming), and exits only after staying at or above
     * {@link #lowLightExitLux(int)} for {@code exitDebounceMs}.
     */
    static final class LowLightGate {
        static final long ENTER_DEBOUNCE_MS = 3000L;
        static final long EXIT_DEBOUNCE_MS = 2000L;

        private boolean dark;
        private int pending; // 0 none, 1 entering dark, -1 exiting dark
        private long pendingSinceMs;
        private float lastLux = Float.NaN;

        boolean isDark() {
            return dark;
        }

        void reset() {
            dark = false;
            pending = 0;
            pendingSinceMs = 0L;
            lastLux = Float.NaN;
        }

        /** Updates sensor reading and returns remaining debounce delay in ms, or 0 if settled. */
        long onLux(float lux, int enterLux, boolean dreaming, long nowMs) {
            if (!Float.isFinite(lux) || lux < 0f) return 0L;
            lastLux = lux;
            return evaluate(enterLux, dreaming, nowMs);
        }

        /** Re-evaluates pending transitions at {@code nowMs}. Returns remaining delay or 0. */
        long evaluate(int enterLux, boolean dreaming, long nowMs) {
            if (Float.isNaN(lastLux)) return 0L;
            float enter = clampLowLightLux(enterLux);
            float exit = lowLightExitLux(enterLux);
            if (!dark) {
                if (lastLux <= enter) {
                    if (dreaming) {
                        dark = true;
                        pending = 0;
                        return 0L;
                    }
                    if (pending != 1) {
                        pending = 1;
                        pendingSinceMs = nowMs;
                        return ENTER_DEBOUNCE_MS;
                    }
                    long elapsed = nowMs - pendingSinceMs;
                    if (elapsed >= ENTER_DEBOUNCE_MS) {
                        dark = true;
                        pending = 0;
                        return 0L;
                    }
                    return ENTER_DEBOUNCE_MS - elapsed;
                }
                pending = 0;
                return 0L;
            }
            if (lastLux >= exit) {
                if (pending != -1) {
                    pending = -1;
                    pendingSinceMs = nowMs;
                    return EXIT_DEBOUNCE_MS;
                }
                long elapsed = nowMs - pendingSinceMs;
                if (elapsed >= EXIT_DEBOUNCE_MS) {
                    dark = false;
                    pending = 0;
                    return 0L;
                }
                return EXIT_DEBOUNCE_MS - elapsed;
            }
            pending = 0;
            return 0L;
        }
    }
}
