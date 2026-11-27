package dev.denver.dressroomadvanced;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;

public final class FontAxisSettings {
    static final String KEY_HEIGHT_AXIS = "fontHeightAxis";

    private FontAxisSettings() {}

    static boolean nativeStretch(Bundle profile) {
        return profile == null || profile.getString("fontFile", "").isEmpty();
    }

    static boolean stretchAllowed(android.content.Context context, Bundle profile) {
        if (nativeStretch(profile)) return true;
        if (profile.getBoolean(KEY_HEIGHT_AXIS)) return true;
        return context != null
                && VariableFonts.hasHeightAxis(context, profile.getString("fontFile", ""));
    }

    public static Bundle clean(Bundle input) {
        Bundle result = new Bundle();
        if (input == null) return result;
        ArrayList<String> tags = new ArrayList<>(input.keySet());
        Collections.sort(tags);
        for (String tag : tags) {
            if (result.size() == 64) break;
            if (!FontAxes.validTag(tag)) continue;
            Object raw = input.get(tag);
            if (raw instanceof Number number && Float.isFinite(number.floatValue()))
                result.putFloat(tag, number.floatValue());
        }
        return result;
    }

    static Bundle values(Bundle profile) {
        if (profile == null) return new Bundle();
        if (profile.containsKey("fontAxes")) return clean(profile.getBundle("fontAxes"));
        Bundle result = new Bundle();
        float width = profile.getFloat("fontWidth", Float.NaN);
        if (Float.isFinite(width)) result.putFloat("wdth", width);
        return result;
    }

    public static String key(Bundle values) {
        Bundle clean = clean(values);
        ArrayList<String> tags = new ArrayList<>(clean.keySet());
        Collections.sort(tags);
        StringBuilder key = new StringBuilder();
        for (String tag : tags)
            key.append(tag)
                    .append('=')
                    .append(Float.floatToIntBits(clean.getFloat(tag)))
                    .append(';');
        return key.toString();
    }
}
