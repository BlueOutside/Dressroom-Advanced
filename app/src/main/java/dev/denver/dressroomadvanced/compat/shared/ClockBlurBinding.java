package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ClockBlurBinding {
    final Method render, clip, clear, host, origin;
    private final Field owner, clock;

    static ClockBlurBinding resolve(Class<?> base) throws ReflectiveOperationException {
        ClockBinding binding = ClockBinding.of(base);
        if (binding.clockStyle) throw new NoSuchMethodException("Clock Style blur is not mapped");
        ClassLoader loader = base.getClassLoader();
        if (!binding.legacy)
            return new ClockBlurBinding(loader, "aod.tx0", "aod.xb0", "aod.e70", "a", "o");
        return OneUi85AodBlur.resolve(loader);
    }

    ClockBlurBinding(
            ClassLoader loader,
            String strategyName,
            String optionsName,
            String helperName,
            String clipName,
            String clockField)
            throws ReflectiveOperationException {
        Class<?> strategy = Class.forName(strategyName, false, loader);
        Class<?> options = Class.forName(optionsName, false, loader);
        Class<?> weather =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader);
        render = strategy.getDeclaredMethod("b", android.widget.ImageView.class, weather, options);
        clear = NativeContract.method(strategy, "c", void.class);
        host = NativeContract.method(strategy, "d", ViewGroup.class);
        origin = NativeContract.method(strategy, "e", PointF.class, ViewGroup.class);
        clip =
                Class.forName(helperName, false, loader)
                        .getDeclaredMethod(
                                clipName,
                                Class.forName("android.view.SemBlurInfo$Builder"),
                                Path.class);
        if (!Modifier.isStatic(clip.getModifiers()) || clip.getReturnType() != void.class)
            throw new NoSuchMethodException("Native blur clip signature changed");
        owner = strategy.getDeclaredField("b");
        owner.setAccessible(true);
        clock = owner.getType().getDeclaredField(clockField);
        clock.setAccessible(true);
        if (clock.getType() != Object.class && !View.class.isAssignableFrom(clock.getType()))
            throw new NoSuchFieldException("Native blur owner is not a clock view");
    }

    View clock(Object strategy) throws IllegalAccessException {
        Object value = clock.get(owner.get(strategy));
        return value instanceof View view ? view : null;
    }
}
