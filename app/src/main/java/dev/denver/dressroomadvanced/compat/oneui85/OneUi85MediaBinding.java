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
        page =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage",
                        false,
                        loader);
        returnAnimation =
                NativeContract.findField(page, clock.clockStyle ? "K0" : "L0", ValueAnimator.class);
        // These are deoptimized, never hooked or invoked. Follow actual callers of
        // the public setter instead of selecting one of many Object observers.
        var resolved = new java.util.LinkedHashSet<Method>();
        try {
            for (DexSignatures.Body body : DexSignatures.callers(expanded))
                resolved.add(body.method(loader));
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read media expansion callers", invalidDex);
        }
        if (resolved.isEmpty())
            throw new NoSuchMethodException("Native media expansion callers missing");
        callers = List.copyOf(resolved);
    }

    Animator animation(View clock) {
        for (View parent = clock;
                parent != null;
                parent = parent.getParent() instanceof View up ? up : null) {
            if (page.isInstance(parent)) {
                try {
                    return (Animator) returnAnimation.get(parent);
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
        return null;
    }
}
