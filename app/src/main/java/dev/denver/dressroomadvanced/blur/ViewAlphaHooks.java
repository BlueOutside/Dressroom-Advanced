package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates a single on-demand {@link View#setAlpha(float)} hook across {@link MediaBlurHost},
 * {@link MediaClockContrast}, and {@link UnlockBlur} so baselines never poison one another and no
 * global View hook remains active when all three are idle.
 */
final class ViewAlphaHooks {
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static final Set<Object> leases =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Method> deoptimized =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static Runnable activeUnhook;

    private ViewAlphaHooks() {}

    static boolean isWriting() {
        return Boolean.TRUE.equals(writing.get());
    }

    static void write(View view, float alpha) {
        Boolean previous = writing.get();
        writing.set(true);
        try {
            view.setAlpha(alpha);
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }

    static void runInternal(Runnable action) {
        Boolean previous = writing.get();
        writing.set(true);
        try {
            action.run();
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }

    static float dispatch(View view, float requested) {
        if (isWriting()) return requested;
        float weather = MediaClockContrast.weatherAlpha(view, requested);
        float afterMedia = MediaBlurHost.nativeAlpha(view, weather);
        return UnlockBlur.interceptClockAlpha(view, weather, afterMedia);
    }

    static synchronized Runnable acquire(DressroomModule module)
            throws ReflectiveOperationException {
        if (module == null) return () -> {};
        Object token = new Object();
        if (activeUnhook == null) {
            Method alpha = View.class.getDeclaredMethod("setAlpha", float.class);
            var handle =
                    module.hook(alpha)
                            .setId("dressroom-advanced.view-alpha-coordinator")
                            .intercept(
                                    chain -> {
                                        if (isWriting()) return chain.proceed();
                                        View view = (View) chain.getThisObject();
                                        if (!MediaBlurHost.hasHeld()
                                                && !UnlockBlur.tracksClockAlpha(view)
                                                && !MediaClockContrast.tracksWeatherAlpha(view))
                                            return chain.proceed();
                                        float requested = ((Number) chain.getArg(0)).floatValue();
                                        float actual = dispatch(view, requested);
                                        return actual == requested
                                                ? chain.proceed()
                                                : chain.proceed(new Object[] {actual});
                                    });
            try {
                if (deoptimized.add(alpha)) module.deoptimize(alpha);
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
