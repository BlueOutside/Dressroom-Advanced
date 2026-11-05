package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class OneUi85WallpaperFadeBinding {
    final Method changed;
    private final Field animator, expanded, host;

    OneUi85WallpaperFadeBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 wallpaper transition");
        ClassLoader loader = base.getClassLoader();
        Class<?> observer = Class.forName(
                "com.samsung.android.app.facewidget.presentation.MediaExpandStateObserver", false, loader);
        Class<?> status = Class.forName("com.samsung.android.app.facewidget.SecKeyguardStatusView", false, loader);
        changed = NativeContract.method(observer, "onChanged", void.class, Object.class);
        animator = NativeContract.field(observer, clock.clockStyle ? "C" : "D", ValueAnimator.class);
        expanded = NativeContract.field(observer, clock.clockStyle ? "E" : "F", boolean.class);
        host = NativeContract.field(observer, clock.clockStyle ? "u" : "v", status);
    }

    void update(Object observer) throws IllegalAccessException {
        OneUi85FullscreenArt.wallpaperFade((View) host.get(observer),
                (ValueAnimator) animator.get(observer), expanded.getBoolean(observer));
    }
}
