package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** The blur host is outside the clock tree, so moving the clock doesn't move its matte. */
final class ClockBlurAlignment {
    private static final Map<View, State> states = new WeakHashMap<>();

    private ClockBlurAlignment() {}

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> clock;
        final WeakReference<Object> manager;
        final Method enabledReader;
        final ViewGeometry geometry = new ViewGeometry();
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        WeakReference<android.animation.Animator> waiting = new WeakReference<>(null);
        final android.animation.AnimatorListenerAdapter settled =
                new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        animation.removeListener(this);
                        if (waiting.get() != animation) return;
                        waiting.clear();
                        request();
                    }
                };

        State(View view, Object owner, Method enabledReader) {
            clock = new WeakReference<>(view);
            manager = new WeakReference<>(owner);
            this.enabledReader = enabledReader;
        }

        public boolean onPreDraw() {
            View view = clock.get();
            if (view == null || !view.isShown() || view.isLayoutRequested()) return true;
            if (!geometry.update(view, null, null) || ClockBlurSync.pending(view)) return true;
            Object owner = manager.get();
            if (!enabled(owner, enabledReader)) return true;
            request();
            return true;
        }

        void request() {
            View view = clock.get();
            if (view == null
                    || !view.isAttachedToWindow()
                    || !view.isShown()
                    || ClockBlurSync.pending(view)) return;
            ClockBlurSync.defer(
                    view,
                    target -> {
                        // Samsung's blockers own AOD, unlock and media transitions.
                        if (!enabled(manager.get(), enabledReader)) return;
                        try {
                            Reflect.call(target, "setClockTextBlur");
                        } catch (ReflectiveOperationException error) {
                            android.util.Log.w(
                                    DressroomModule.TAG,
                                    "Clock blur alignment refresh failed",
                                    error);
                        }
                    });
        }
    }

    private static boolean enabled(Object manager, Method reader) {
        if (manager == null) return false;
        try {
            return Boolean.TRUE.equals(reader.invoke(manager));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static void watch(View clock, Object manager, Method enabledReader) {
        if (enabledReader == null || clock == null || !clock.isAttachedToWindow()) return;
        State state = states.get(clock);
        if (state == null || state.manager.get() != manager) {
            forget(clock);
            state = new State(clock, manager, enabledReader);
            states.put(clock, state);
        }
        ViewTreeObserver observer = clock.getViewTreeObserver();
        if (state.observer.get() == observer) return;
        ViewTreeObserver old = state.observer.get();
        if (old != null && old.isAlive()) old.removeOnPreDrawListener(state);
        state.observer = new WeakReference<>(observer);
        observer.addOnPreDrawListener(state);
    }

    static void forget(View clock) {
        State state = states.remove(clock);
        if (state == null) return;
        android.animation.Animator animator = state.waiting.get();
        if (animator != null) animator.removeListener(state.settled);
        ViewTreeObserver observer = state.observer.get();
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(state);
    }

    static void awaitAnimator(View clock, android.animation.ValueAnimator animator) {
        State state = states.get(clock);
        if (state == null
                || animator == null
                || !animator.isRunning()
                || state.waiting.get() == animator) return;
        android.animation.Animator previous = state.waiting.get();
        if (previous != null) previous.removeListener(state.settled);
        state.waiting = new WeakReference<>(animator);
        animator.addListener(state.settled);
    }
}
