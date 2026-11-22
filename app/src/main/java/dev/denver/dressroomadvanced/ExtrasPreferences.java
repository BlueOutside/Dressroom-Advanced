package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONObject;

public final class ExtrasPreferences {
    public static final String LYRICS_FONT_FILE = "lyricsFontFile";
    public static final String LYRICS_FONT_FILE_NAME = "lyricsFontFileName";
    public static final String LYRICS_FONT_WEIGHT = "lyricsFontWeight";
    public static final String LYRICS_FONT_AXES = "lyricsFontAxes";
    public static final int DEFAULT_LYRICS_FONT_WEIGHT = 700;

    static final String[] FLAGS = {
        "keepCharging", "fullscreenArt", LiveCoverController.KEY, "keepStretch", "textCard",
        "swipeBlur", AodWidgetAppearance.KEY, WallpaperAod.CLOCK_BLUR, StretchSuggestions.KEY,
        WidgetBackgroundOptions.CAMERA, WidgetBackgroundOptions.PICTURE_FRAME,
        ClockBlurDelays.KEY, NotificationBlurOptions.COLORIZED, NotificationBlurOptions.CUSTOM,
        BlurAppearanceSettings.NEUTRAL_CLOCK, MediaWidgetFade.KEY, AodWidgetAppearance.NEUTRAL,
        NowBarGlass.KEY, ClockInlineSeconds.KEEP_OPEN, MediaClockVisibility.KEY,
        WidgetClickBlock.KEY
    };

    static final String[] DEFAULT_TRUE_FLAGS = {
        ClockInlineSeconds.KEY, LiveCoverController.WIFI_ONLY, "lockscreenLyrics", "lyricsSyncedHighlight"
    };

    private ExtrasPreferences() {}

    static boolean sameFlags(Bundle a, Bundle b) {
        if (a == null || b == null) return false;
        for (String flag : DEFAULT_TRUE_FLAGS)
            if (a.getBoolean(flag, true) != b.getBoolean(flag, true)) return false;
        for (String flag : FLAGS) if (a.getBoolean(flag) != b.getBoolean(flag)) return false;
        return true;
    }

    static boolean sameLyricsFont(Bundle a, Bundle b) {
        if (a == null || b == null) return false;
        String fileA = cleanFontId(a.getString(LYRICS_FONT_FILE));
        String fileB = cleanFontId(b.getString(LYRICS_FONT_FILE));
        if (!fileA.equals(fileB)) return false;
        if (!text(a.getString(LYRICS_FONT_FILE_NAME), 100)
                .equals(text(b.getString(LYRICS_FONT_FILE_NAME), 100))) return false;
        if (clampLyricsWeight(a.getInt(LYRICS_FONT_WEIGHT, DEFAULT_LYRICS_FONT_WEIGHT))
                != clampLyricsWeight(b.getInt(LYRICS_FONT_WEIGHT, DEFAULT_LYRICS_FONT_WEIGHT)))
            return false;
        return FontAxisSettings.key(a.getBundle(LYRICS_FONT_AXES))
                .equals(FontAxisSettings.key(b.getBundle(LYRICS_FONT_AXES)));
    }

    static Bundle clean(Bundle input) {
        Bundle b = new Bundle();
        if (input != null) ScreenIdentity.copy(input, b);
        for (String flag : FLAGS) b.putBoolean(flag, input != null && input.getBoolean(flag));
        for (String flag : DEFAULT_TRUE_FLAGS)
            b.putBoolean(flag, input == null || input.getBoolean(flag, true));
        b.putInt(
                "stackCards",
                Math.max(3, Math.min(8, input == null ? 3 : input.getInt("stackCards", 3))));
        b.putInt(
                "lyricsTextSize",
                Math.max(16, Math.min(38, input == null ? 28 : input.getInt("lyricsTextSize", 28))));
        String fontFile = input == null ? "" : cleanFontId(input.getString(LYRICS_FONT_FILE));
        b.putString(LYRICS_FONT_FILE, fontFile);
        b.putString(
                LYRICS_FONT_FILE_NAME,
                fontFile.isEmpty() || input == null
                        ? ""
                        : text(input.getString(LYRICS_FONT_FILE_NAME), 100));
        b.putInt(
                LYRICS_FONT_WEIGHT,
                clampLyricsWeight(
                        input == null
                                ? DEFAULT_LYRICS_FONT_WEIGHT
                                : input.getInt(LYRICS_FONT_WEIGHT, DEFAULT_LYRICS_FONT_WEIGHT)));
        b.putBundle(
                LYRICS_FONT_AXES,
                fontFile.isEmpty() || input == null
                        ? new Bundle()
                        : FontAxisSettings.clean(input.getBundle(LYRICS_FONT_AXES)));
        b.putInt(
                LiveCoverController.SOURCE,
                LiveCoverController.clampSource(
                        input == null
                                ? LiveCoverController.SOURCE_SPOTIFY_FIRST
                                : input.getInt(
                                        LiveCoverController.SOURCE,
                                        LiveCoverController.SOURCE_SPOTIFY_FIRST)));
        b.putString("title", text(input == null ? null : input.getString("title"), 80));
        b.putString("message", text(input == null ? null : input.getString("message"), 180));
        BlurAppearanceSettings.clean(input, b);
        return b;
    }

    static String cleanFontId(String id) {
        if (id == null) return "";
        String trimmed = id.trim();
        return trimmed.matches("[0-9a-f]{64}") ? trimmed : "";
    }

    static int clampLyricsWeight(int weight) {
        return Math.max(100, Math.min(900, weight));
    }

    static String text(String text, int limit) {
        if (text == null) return "";
        text = text.replace('\u0000', ' ').strip();
        return text.codePointCount(0, text.length()) <= limit
                ? text
                : text.substring(0, text.offsetByCodePoints(0, limit));
    }

    static Bundle read(Context context) {
        Bundle query = new Bundle();
        ScreenIdentity.stamp(query);
        Bundle result =
                context.getContentResolver()
                        .call(SettingsProvider.URI, "readExtras", null, query);
        return clean(result);
    }

    static void commit(Context context, Bundle settings) {
        java.util.ArrayList<Bundle> screens = settings.getParcelableArrayList("screenProfiles", Bundle.class);
        if (screens != null) { for (Bundle screen : screens) commit(context, screen); return; }
        Bundle snapshot = clean(settings);
        ScreenIdentity.stamp(snapshot);
        Bundle result =
                context.getContentResolver()
                        .call(SettingsProvider.URI, "commitExtras", null, snapshot);
        if (result == null || !result.getBoolean("saved"))
            throw new IllegalStateException("Extra settings were not saved");
    }

    static Bundle store(Context context, String method, Bundle input)
            throws org.json.JSONException {
        Bundle sanitizedInput = input;
        if (method.equals("commitExtras") && input != null) {
            String fontFile = cleanFontId(input.getString(LYRICS_FONT_FILE));
            if (!fontFile.isEmpty() && !FontFiles.file(context, fontFile).isFile()) {
                sanitizedInput = new Bundle(input);
                sanitizedInput.putString(LYRICS_FONT_FILE, "");
                sanitizedInput.putString(LYRICS_FONT_FILE_NAME, "");
                sanitizedInput.putBundle(LYRICS_FONT_AXES, new Bundle());
            }
        }
        Bundle result =
                store(context.getSharedPreferences("committed_lockscreen_extras", 0), method, sanitizedInput);
        if (method.equals("commitExtras")) context.getContentResolver().notifyChange(SettingsProvider.URI, null);
        return result;
    }

    static Bundle store(android.content.SharedPreferences prefs, String method, Bundle input)
            throws org.json.JSONException {
        String key = ScreenIdentity.key("options", input == null ? new Bundle() : input);
        if (input != null && ScreenIdentity.value(input) != 0) {
            String old = prefs.getString("options", null);
            if (old != null) {
                android.content.SharedPreferences.Editor seed = prefs.edit();
                for (int screen : new int[] {ScreenIdentity.MAIN, ScreenIdentity.SUB}) {
                    Bundle scope = new Bundle();
                    scope.putInt(ScreenIdentity.FIELD, screen);
                    String sibling = ScreenIdentity.key("options", scope);
                    if (!prefs.contains(sibling)) seed.putString(sibling, old);
                }
                if (!seed.commit()) throw new IllegalStateException("Could not separate Extras settings");
            }
        }
        if (method.equals("commitExtras")) {
            Bundle b = clean(input);
            JSONObject json = new JSONObject();
            for (String flag : FLAGS) json.put(flag, b.getBoolean(flag));
            for (String flag : DEFAULT_TRUE_FLAGS) json.put(flag, b.getBoolean(flag, true));
            json.put("stackCards", b.getInt("stackCards", 3));
            json.put("lyricsTextSize", b.getInt("lyricsTextSize", 28));
            json.put(LYRICS_FONT_FILE, b.getString(LYRICS_FONT_FILE, ""));
            json.put(LYRICS_FONT_FILE_NAME, b.getString(LYRICS_FONT_FILE_NAME, ""));
            json.put(LYRICS_FONT_WEIGHT, b.getInt(LYRICS_FONT_WEIGHT, DEFAULT_LYRICS_FONT_WEIGHT));
            Bundle axes = FontAxisSettings.clean(b.getBundle(LYRICS_FONT_AXES));
            if (!axes.isEmpty()) {
                JSONObject axesJson = new JSONObject();
                for (String tag : axes.keySet()) {
                    axesJson.put(tag, (double) axes.getFloat(tag));
                }
                json.put(LYRICS_FONT_AXES, axesJson);
            }
            json.put(
                    LiveCoverController.SOURCE,
                    b.getInt(LiveCoverController.SOURCE, LiveCoverController.SOURCE_SPOTIFY_FIRST));
            json.put("title", b.getString("title"));
            json.put("message", b.getString("message"));
            BlurAppearanceSettings.write(b, json);
            if (!prefs.edit().putString(key, json.toString()).commit())
                throw new IllegalStateException("Could not save lockscreen extras");
            Bundle result = new Bundle();
            result.putBoolean("saved", true);
            return result;
        }
        JSONObject json = new JSONObject(prefs.getString(key, "{}"));
        Bundle b = new Bundle();
        if (input != null) ScreenIdentity.copy(input, b);
        for (String flag : FLAGS) b.putBoolean(flag, json.optBoolean(flag));
        for (String flag : DEFAULT_TRUE_FLAGS) b.putBoolean(flag, json.optBoolean(flag, true));
        b.putInt("stackCards", json.optInt("stackCards", 3));
        b.putInt("lyricsTextSize", json.optInt("lyricsTextSize", 28));
        b.putString(LYRICS_FONT_FILE, json.optString(LYRICS_FONT_FILE, ""));
        b.putString(LYRICS_FONT_FILE_NAME, json.optString(LYRICS_FONT_FILE_NAME, ""));
        b.putInt(LYRICS_FONT_WEIGHT, json.optInt(LYRICS_FONT_WEIGHT, DEFAULT_LYRICS_FONT_WEIGHT));
        JSONObject axesJson = json.optJSONObject(LYRICS_FONT_AXES);
        if (axesJson != null) {
            Bundle axes = new Bundle();
            java.util.Iterator<String> tags = axesJson.keys();
            while (tags.hasNext()) {
                String tag = tags.next();
                double value = axesJson.optDouble(tag);
                if (FontAxes.validTag(tag)
                        && Double.isFinite(value)
                        && Float.isFinite((float) value)) {
                    axes.putFloat(tag, (float) value);
                }
            }
            b.putBundle(LYRICS_FONT_AXES, FontAxisSettings.clean(axes));
        }
        b.putInt(
                LiveCoverController.SOURCE,
                json.optInt(LiveCoverController.SOURCE, LiveCoverController.SOURCE_SPOTIFY_FIRST));
        b.putString("title", json.optString("title"));
        b.putString("message", json.optString("message"));
        BlurAppearanceSettings.read(json, b);
        return clean(b);
    }
}
