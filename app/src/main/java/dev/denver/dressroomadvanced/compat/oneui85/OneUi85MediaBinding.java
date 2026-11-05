package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class OneUi85MediaBinding {
    final Method expanded;
    final List<Method> callers;
    private final Class<?> page;
    private final Field returnAnimation;

    OneUi85MediaBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 media binding");
        ClassLoader loader = base.getClassLoader();
        expanded = NativeContract.method(base, "setMediaExpanded", void.class, boolean.class);
        NativeContract.field(base, "W0", boolean.class);
        page = Class.forName("com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage", false, loader);
        returnAnimation = NativeContract.field(page, clock.clockStyle ? "K0" : "L0", ValueAnimator.class);
        callers = List.of(
                Class.forName(clock.clockStyle ? "aod.WJ" : "aod.ZQ", false, loader)
                        .getDeclaredMethod("onChanged", Object.class),
                page.getDeclaredMethod("d", Class.forName(clock.clockStyle ? "aod.cL" : "aod.iS", false, loader)),
                page.getDeclaredMethod("g", View.class));
    }

    Animator animation(View clock) {
        for (View parent = clock; parent != null;
                parent = parent.getParent() instanceof View up ? up : null) {
            if (page.isInstance(parent)) {
                try { return (Animator) returnAnimation.get(parent); }
                catch (IllegalAccessException error) { throw new IllegalStateException(error); }
            }
        }
        return null;
    }
}
