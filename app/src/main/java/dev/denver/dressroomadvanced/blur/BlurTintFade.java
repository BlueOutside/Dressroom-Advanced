package dev.denver.dressroomadvanced;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Tint belongs to the existing blur drawable, separate from its radius, clip and opacity. */
final class BlurTintFade {
    private static final long DURATION_MS = 180;
    private static final long NATIVE_FRAME_WINDOW_MS = 40;
    private static final Map<View, State> states = new WeakHashMap<>();
    private static final ArgbEvaluator colors = new ArgbEvaluator();
    private static Method setColor;

    private BlurTintFade() {}

    static void install() {
        try {
            Class<?> blur = Class.forName("com.android.internal.graphics.drawable.BackgroundBlurDrawable");
            setColor = blur.getMethod("setColor", int.class);
            if (setColor.getReturnType() != void.class) setColor = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            DressroomModule.note("Optional window blur tint fade", unavailable);
        }
    }

    static void submitted(View view, int tint) {
        if (setColor == null) return;
        Drawable background = view.getBackground();
        if (background == null || !setColor.getDeclaringClass().isInstance(background)) {
            clear(view);
            return;
        }
        State state = states.get(view);
        if (state == null || state.drawable.get() != background) {
            clear(view);
            states.put(view, new State(view, background, tint));
        } else {
            state.update(background, tint);
        }
    }

    static void clear(View view) {
        State state = states.remove(view);
        if (state != null) state.stop();
    }

    private static final class State {
        final WeakReference<View> view;
        final WeakReference<Drawable> drawable;
        ValueAnimator animator;
        int from, displayed, target;
        long lastTintChange = -1;

        State(View view, Drawable drawable, int color) {
            this.view = new WeakReference<>(view);
            this.drawable = new WeakReference<>(drawable);
            displayed = target = color;
        }

        void update(Drawable background, int color) {
            View surface = view.get();
            if (surface == null || !surface.isAttachedToWindow() || !surface.isShown()
                    || !ValueAnimator.areAnimatorsEnabled()) {
                stop();
                displayed = target = color;
                return;
            }
            if (target == color) {
                // Native layout and depth updates may resubmit the destination tint mid-fade.
                if (displayed != target) write(background, displayed);
                return;
            }
            long now = SystemClock.uptimeMillis();
            boolean nativeAnimation = lastTintChange >= 0
                    && now - lastTintChange <= NATIVE_FRAME_WINDOW_MS;
            lastTintChange = now;
            stop();
            if (nativeAnimation) {
                // Native already submitted this frame. Starting another fade would chase it.
                displayed = target = color;
                return;
            }
            from = displayed;
            target = color;
            if (from == target || ((from | target) >>> 24) == 0) {
                displayed = target;
                return;
            }
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(DURATION_MS);
                animator.addUpdateListener(animation -> frame(animation.getAnimatedFraction()));
            }
            if (write(background, from)) animator.start();
        }

        void frame(float progress) {
            Drawable background = drawable.get();
            View surface = view.get();
            if (background == null || surface == null || !surface.isAttachedToWindow()
                    || surface.getBackground() != background) {
                stop();
                return;
            }
            // A transparent tint has no visible hue. Fade the visible color in or out directly.
            int start = from >>> 24 == 0 ? target & 0x00ffffff : from;
            int end = target >>> 24 == 0 ? from & 0x00ffffff : target;
            boolean shown = surface.isShown();
            int color = progress >= 1f || !shown ? target
                    : (Integer) colors.evaluate(progress, start, end);
            if (displayed != color) write(background, color);
            if (!shown) stop();
        }

        boolean write(Drawable background, int color) {
            try {
                setColor.invoke(background, color);
                background.invalidateSelf();
                displayed = color;
                return true;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                stop();
                displayed = target;
                DressroomModule.note("Window blur tint fade", unavailable);
                return false;
            }
        }

        void stop() {
            if (animator != null && animator.isStarted()) animator.cancel();
        }
    }
}
