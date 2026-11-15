package dev.denver.dressroomadvanced;

import android.content.Context;
import android.provider.Settings;

final class WallpaperAod {
    static final String CLOCK_BLUR = "keepAodClockBlur";
    static final String MAIN = "aod_show_lockscreen_wallpaper";
    static final String COVER = "aod_show_cover_screen_wallpaper";
    private static Boolean supported;

    private WallpaperAod() {}

    static synchronized boolean supported() {
        if (supported != null) return supported;
        int version = PlatformGate.oneUiVersion();
        if (version < 90000 || version >= 100000) return supported = false;
        try {
            Class<?> feature = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object instance = feature.getMethod("getInstance").invoke(null);
            return supported = (Integer) Reflect.call(
                    instance, "getInt", "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN") > 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return supported = false;
        }
    }

    static boolean shown(Context context) {
        if (!supported()) return false;
        var resolver = context.getContentResolver();
        int main = Settings.System.getInt(resolver, MAIN, 0);
        return (ScreenIdentity.current() == ScreenIdentity.SUB
                ? Settings.System.getInt(resolver, COVER, main) : main) == 1;
    }
}
