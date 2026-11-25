package dev.denver.dressroomadvanced;

import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates a single {@link TextView#getCurrentTextColor()} hook across {@link ClockBlurColors}
 * and {@link MediaClockContrast}.
 */
final class TextColorHooks {
    private static final Set<Object> leases =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Method> deoptimized =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static Runnable activeUnhook;

    private TextColorHooks() {}

    static Integer read(TextView view) {
        Integer scoped = ClockBlurColors.read(view);
        if (scoped != null) return scoped;
        return MediaClockContrast.readDrawingColor(view);
    }

    static synchronized Runnable acquire(DressroomModule module)
            throws ReflectiveOperationException {
        if (module == null) return () -> {};
        Object token = new Object();
        if (activeUnhook == null) {
            Method getter = TextView.class.getDeclaredMethod("getCurrentTextColor");
            var handle =
                    module.hook(getter)
                            .setId("dressroom-advanced.text-current-color")
                            .intercept(
                                    chain -> {
                                        Integer color = read((TextView) chain.getThisObject());
                                        return color == null ? chain.proceed() : color;
                                    });
            try {
                if (deoptimized.add(getter)) module.deoptimize(getter);
            } catch (RuntimeException | Error error) {
                handle.unhook();
                throw error;
            }
            activeUnhook = handle::unhook;
        }
        leases.add(token);
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) release(token);
        };
    }

    private static synchronized void release(Object token) {
        leases.remove(token);
        if (leases.isEmpty() && activeUnhook != null) {
            Runnable unhook = activeUnhook;
            activeUnhook = null;
            unhook.run();
        }
    }
}
