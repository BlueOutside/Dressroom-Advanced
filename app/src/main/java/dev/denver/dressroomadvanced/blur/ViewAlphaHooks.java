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
    private static final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<View>>
            afterListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
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
                                        View view = (View) chain.getThisObject();
                                        Object result;
                                        if (isWriting()
                                                || (!MediaBlurHost.hasHeld()
                                                        && !UnlockBlur.tracksClockAlpha(view)
                                                        && !MediaClockContrast.tracksWeatherAlpha(
                                                                view))) {
                                            result = chain.proceed();
                                        } else {
                                            float requested =
                                                    ((Number) chain.getArg(0)).floatValue();
                                            float actual = dispatch(view, requested);
                                            result =
                                                    actual == requested
                                                            ? chain.proceed()
                                                            : chain.proceed(new Object[] {actual});
                                        }
                                        if (!afterListeners.isEmpty()) {
                                            for (var listener : afterListeners)
                                                listener.accept(view);
                                        }
                                        return result;
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

    static synchronized Runnable acquireAfter(
            DressroomModule module, java.util.function.Consumer<View> listener)
            throws ReflectiveOperationException {
        if (module == null || listener == null) return () -> {};
        afterListeners.add(listener);
        Runnable lease;
        try {
            lease = acquire(module);
        } catch (ReflectiveOperationException | RuntimeException | Error error) {
            afterListeners.remove(listener);
            throw error;
        }
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                afterListeners.remove(listener);
                lease.run();
            }
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
