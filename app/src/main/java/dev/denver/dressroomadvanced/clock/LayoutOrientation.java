package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class LayoutOrientation {
    static final String FIELD = "layoutOrientation";

    private LayoutOrientation() {}

    static int current(Context context) {
        return SettingsScope.orientation(context.getResources().getConfiguration().orientation);
    }

    static void stamp(Context context, Bundle data) {
        if (!data.containsKey(FIELD)) data.putInt(FIELD, current(context));
    }

    static void copy(Bundle source, Bundle target) {
        if (source.containsKey(FIELD))
            target.putInt(FIELD, SettingsScope.orientation(source.getInt(FIELD)));
    }

    static String key(String base, Bundle data) {
        return SettingsScope.oriented(base, data.getInt(FIELD));
    }

    static String read(SharedPreferences prefs, String base, Bundle data) {
        int orientation = SettingsScope.orientation(data.getInt(FIELD));
        String key = SettingsScope.oriented(base, orientation);
        String saved = prefs.getString(key, null);
        if (saved != null || orientation == 0) return saved;
        String old = prefs.getString(base, null);
        String owner = "layout_owner_" + base;
        if (old == null) return null;
        int originalOrientation = prefs.getInt(owner, orientation);
        if (originalOrientation != orientation) {
            try {
                org.json.JSONObject inherited = new org.json.JSONObject(old);
                inherited.remove("depthMask");
                inherited.remove("depthMaskRef");
                inherited.put(FIELD, orientation);
                old = inherited.toString();
            } catch (org.json.JSONException error) {
                throw new IllegalArgumentException("Invalid legacy layout profile", error);
            }
        }
        if (!prefs.edit().putString(key, old).putInt(owner, originalOrientation).commit())
            throw new IllegalStateException("Could not migrate layout settings");
        return old;
    }
}
