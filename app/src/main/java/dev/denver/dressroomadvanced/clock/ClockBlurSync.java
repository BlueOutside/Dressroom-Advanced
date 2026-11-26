package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class ClockBlurSync {
    private static final Map<View, Consumer<View>> pending = new WeakHashMap<>();
    private static final ThreadLocal<View> rebuilding = new ThreadLocal<>();
    private static DressroomModule hookModule;
    private static Runnable drawUnhook;
    private static boolean drawDeoptimized;
    private static boolean installed;

    static synchronized void install(DressroomModule module) throws ReflectiveOperationException {
        if (installed) return;
        hookModule = module;
        installed = true;
        syncHook();
    }

    static synchronized void syncHook() {
        boolean needed =
                installed
                        && hookModule != null
                        && (!pending.isEmpty() || ClockBlurMotion.hasMovingClocks());
        if (needed && drawUnhook == null) {
            try {
                java.lang.reflect.Method draw =
                        ViewGroup.class.getDeclaredMethod("dispatchDraw", Canvas.class);
                var handle =
                        hookModule
                                .hook(draw)
                                .setId("dressroom-advanced.media-clock-glyph-drawn")
                                .intercept(
                                        chain -> {
                                            ClockBlurContinuity.beforeDraw(
                                                    (View) chain.getThisObject());
                                            Object result = chain.proceed();
                                            if (!pending.isEmpty())
                                                drawn((View) chain.getThisObject());
                                            return result;
                                        });
                if (!drawDeoptimized) {
                    hookModule.deoptimize(draw);
                    drawDeoptimized = true;
                }
                drawUnhook = handle::unhook;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                hookModule.report("Clock blur draw synchronization unavailable", error);
            }
        } else if (!needed && drawUnhook != null) {
            Runnable unhook = drawUnhook;
            drawUnhook = null;
            unhook.run();
        }
    }

    static boolean rebuilding(View clock) {
        return rebuilding.get() == clock;
    }

    static boolean pending(View clock) {
        return pending.containsKey(clock);
    }

    static boolean defer(View clock, Consumer<View> work) {
        if (!installed || !clock.isAttachedToWindow()) {
            cancel(clock);
            return false;
        }
        boolean wasEmpty = pending.isEmpty();
        pending.put(clock, work);
        if (wasEmpty) syncHook();
        clock.invalidate();
        return true;
    }

    static void drawn(View clock) {
        Consumer<View> work = pending.get(clock);
        if (work == null) return;
        if (!clock.isAttachedToWindow()) {
            cancel(clock);
            return;
        }
        if (clock.isLayoutRequested()) {
            clock.invalidate();
            return;
        }
        pending.remove(clock);
        View previous = rebuilding.get();
        rebuilding.set(clock);
        try {
            work.accept(clock);
        } catch (RuntimeException ignored) {
        } finally {
            if (previous == null) rebuilding.remove();
            else rebuilding.set(previous);
            if (pending.isEmpty()) syncHook();
        }
    }

    static void cancel(View clock) {
        if (pending.remove(clock) != null && pending.isEmpty()) syncHook();
    }
}
