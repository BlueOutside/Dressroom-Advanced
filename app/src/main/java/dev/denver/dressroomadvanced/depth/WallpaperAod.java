package dev.denver.dressroomadvanced;

import android.content.Context;
import android.provider.Settings;

final class WallpaperAod {
    static final String CLOCK_BLUR = "keepAodClockBlur";
    static final String MAIN = "aod_show_lockscreen_wallpaper";
    static final String COVER = "aod_show_cover_screen_wallpaper";
    private static Boolean fullscreen;
    private static volatile Integer cachedMain, cachedCover;
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();

    private WallpaperAod() {}

    private static void ensureObserved(android.content.ContentResolver resolver) {
        if (resolver == null || observing.get()) return;
        android.os.Looper looper = android.os.Looper.getMainLooper();
        if (looper == null || !observing.compareAndSet(false, true)) return;
        try {
            android.database.ContentObserver observer =
                    new android.database.ContentObserver(new android.os.Handler(looper)) {
                        @Override
                        public void onChange(boolean selfChange) {
                            cachedMain = null;
                            cachedCover = null;
                        }
                    };
            android.net.Uri mainUri = Settings.System.getUriFor(MAIN);
            android.net.Uri coverUri = Settings.System.getUriFor(COVER);
            if (mainUri == null || coverUri == null) {
                observing.set(false);
                return;
            }
            resolver.registerContentObserver(mainUri, false, observer);
            resolver.registerContentObserver(coverUri, false, observer);
        } catch (RuntimeException ignored) {
            observing.set(false);
        }
    }

    static boolean supported() {
        int version = PlatformGate.oneUiVersion();
        return version >= 90000 && version < 100000 && fullscreenSupported();
    }

    static synchronized boolean fullscreenSupported() {
        if (fullscreen != null) return fullscreen;
        try {
            Class<?> feature = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object instance = feature.getMethod("getInstance").invoke(null);
            return fullscreen = (Integer) Reflect.call(
                    instance, "getInt", "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN") > 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return fullscreen = false;
        }
    }

    static boolean shown(Context context) {
        if (!supported()) return false;
        var resolver = context.getContentResolver();
        ensureObserved(resolver);
        Integer mainVal = observing.get() ? cachedMain : null;
        if (mainVal == null) {
            mainVal = Settings.System.getInt(resolver, MAIN, 0);
            if (observing.get()) cachedMain = mainVal;
        }
        int main = mainVal;
        if (ScreenIdentity.current() != ScreenIdentity.SUB) return main == 1;
        Integer coverVal = observing.get() ? cachedCover : null;
        if (coverVal == null) {
            coverVal = Settings.System.getInt(resolver, COVER, main);
            if (observing.get()) cachedCover = coverVal;
        }
        return coverVal == 1;
    }
}
