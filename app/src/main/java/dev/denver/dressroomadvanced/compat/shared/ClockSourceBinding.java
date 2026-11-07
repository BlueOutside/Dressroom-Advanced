package dev.denver.dressroomadvanced;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.List;

/** Resolve every source/measurement dependency before installing any hooks. */
final class ClockSourceBinding {
    final Method data, heights, wallpaper;
    final List<Method> callers;

    ClockSourceBinding(ClassLoader loader, ClockBinding clock) throws ReflectiveOperationException {
        Class<?> provider =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                        false,
                        loader);
        Class<?> font =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);
        NativeContract.field(provider, "context", Context.class);
        NativeContract.method(provider, "getConsideredThemeClockType", int.class);
        data = NativeContract.method(provider, "getClockFontData", font);
        heights = NativeContract.method(provider, "getClockFontHeights", List.class);
        wallpaper =
                NativeContract.method(
                        provider, "updateWallpaperColorsItem", void.class, Object.class);
        Method stretch = NativeContract.method(provider, "hasStretchFontHeights", boolean.class);
        var methods = new java.util.ArrayList<Method>();
        methods.add(stretch);
        methods.addAll(
                !clock.legacy
                        ? OneUi9ClockSource.callers(loader)
                        : clock.clockStyle
                                ? OneUi85ClockSource.callers(loader)
                                : OneUi85AodClockSource.callers(loader));
        callers = List.copyOf(methods);
    }
}
