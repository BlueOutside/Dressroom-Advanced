package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONObject;

final class ExtrasPreferences {
    static final String[] FLAGS = {"keepCharging", "fullscreenArt", "keepStretch", "textCard"};

    private ExtrasPreferences() {}

    static Bundle clean(Bundle input) {
        Bundle b = new Bundle();
        for (String flag : FLAGS) b.putBoolean(flag, input != null && input.getBoolean(flag));
        b.putInt("stackCards", Math.max(3, Math.min(8, input == null ? 3 : input.getInt("stackCards", 3))));
        b.putString("title", text(input == null ? null : input.getString("title"), 80));
        b.putString("message", text(input == null ? null : input.getString("message"), 180));
        return b;
    }

    static String text(String text, int limit) {
        if (text == null) return "";
        text = text.replace('\u0000', ' ').strip();
        return text.codePointCount(0, text.length()) <= limit
                ? text
                : text.substring(0, text.offsetByCodePoints(0, limit));
    }

    static Bundle read(Context context) {
        Bundle result =
                context.getContentResolver()
                        .call(SettingsProvider.URI, "readExtras", null, new Bundle());
        return clean(result);
    }

    static void commit(Context context, Bundle settings) {
        Bundle result =
                context.getContentResolver()
                        .call(SettingsProvider.URI, "commitExtras", null, clean(settings));
        if (result == null || !result.getBoolean("saved"))
            throw new IllegalStateException("Extra settings were not saved");
    }

    static Bundle store(Context context, String method, Bundle input)
            throws org.json.JSONException {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("committed_lockscreen_extras", 0);
        if (method.equals("commitExtras")) {
            Bundle b = clean(input);
            JSONObject json = new JSONObject();
            for (String flag : FLAGS) json.put(flag, b.getBoolean(flag));
            json.put("stackCards", b.getInt("stackCards", 3));
            json.put("title", b.getString("title"));
            json.put("message", b.getString("message"));
            if (!prefs.edit().putString("options", json.toString()).commit())
                throw new IllegalStateException("Could not save lockscreen extras");
            context.getContentResolver().notifyChange(SettingsProvider.URI, null);
            Bundle result = new Bundle();
            result.putBoolean("saved", true);
            return result;
        }
        JSONObject json = new JSONObject(prefs.getString("options", "{}"));
        Bundle b = new Bundle();
        for (String flag : FLAGS) b.putBoolean(flag, json.optBoolean(flag));
        b.putInt("stackCards", json.optInt("stackCards", 3));
        b.putString("title", json.optString("title"));
        b.putString("message", json.optString("message"));
        return clean(b);
    }
}
