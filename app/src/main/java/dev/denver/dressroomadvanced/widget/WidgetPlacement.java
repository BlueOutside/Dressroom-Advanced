package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

final class WidgetPlacement {
    private static final WeakHashMap<View, State> states = new WeakHashMap<>();

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> root;
        WeakReference<View> clock = new WeakReference<>(null);
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        Bundle settings;
        float deltaX, deltaY, appliedX, appliedY;

        State(View root) {
            this.root = new WeakReference<>(root);
        }

        @Override
        public boolean onPreDraw() {
            View view = root.get();
            if (view == null
                    || settings == null
                    || settings.getInt("anchor") == 0
                    || !(view.getParent() instanceof View parent)) return true;
            View time = clock.get();
            if (time == null || !time.isAttachedToWindow() || time.getWidth() == 0) {
                time = findClock(view.getRootView());
                clock = new WeakReference<>(time);
            }
            if (time == null && settings.getInt("anchor") != 5) return true;
            Matrix parentGlobal = new Matrix();
            parent.transformMatrixToGlobal(parentGlobal);
            Matrix inverse = new Matrix();
            if (!parentGlobal.invert(inverse)) return true;
            RectF screen = rect(view.getRootView(), inverse);
            RectF current = rect(view, inverse);
            RectF clockRect = time == null ? new RectF() : rect(time, inverse);
            clockRect.offset(-screen.left, -screen.top);
            float density = view.getResources().getDisplayMetrics().density;
            float[] unit = {density, 0, 0, density};
            inverse.mapVectors(unit);
            float gap = settings.getFloat("gap", 8) * (float) Math.hypot(unit[0], unit[1]);
            float[] target =
                    WidgetLayout.place(
                            settings.getInt("anchor"),
                            clockRect.left,
                            clockRect.top,
                            clockRect.right,
                            clockRect.bottom,
                            current.width(),
                            current.height(),
                            screen.width(),
                            screen.height(),
                            gap,
                            settings.getFloat("freeX", .5f),
                            settings.getFloat("freeY", .4f),
                            settings.getFloat("dockOffset") * (float) Math.hypot(unit[0], unit[1]));
            float dx = target[0] + screen.left - current.left,
                    dy = target[1] + screen.top - current.top;
            float tx = view.getTranslationX(), ty = view.getTranslationY();
            if (Math.abs(dx) > .25f || Math.abs(dy) > .25f) {
                if (Math.abs(tx - appliedX) > .01f) deltaX = 0;
                if (Math.abs(ty - appliedY) > .01f) deltaY = 0;
                deltaX += dx;
                deltaY += dy;
                appliedX = tx + dx;
                appliedY = ty + dy;
                view.setTranslationX(appliedX);
                view.setTranslationY(appliedY);
                view.invalidate();
            }
            return true;
        }

        void restore(View view) {
            if (Math.abs(view.getTranslationX() - appliedX) < .01f)
                view.setTranslationX(view.getTranslationX() - deltaX);
            if (Math.abs(view.getTranslationY() - appliedY) < .01f)
                view.setTranslationY(view.getTranslationY() - deltaY);
        }
    }

    static void apply(View root, Bundle settings, Object provider) {
        if (settings == null || settings.getInt("anchor") == 0) {
            clear(root);
            return;
        }
        State state = states.computeIfAbsent(root, State::new);
        state.settings = new Bundle(settings);
        if (provider != null)
            try {
                Object clock = Reflect.field(provider, "clockView");
                if (clock instanceof View view) state.clock = new WeakReference<>(view);
            } catch (ReflectiveOperationException ignored) {
            }
        ViewTreeObserver observer = root.getViewTreeObserver(), old = state.observer.get();
        if (old != observer) {
            if (old != null && old.isAlive()) old.removeOnPreDrawListener(state);
            state.observer = new WeakReference<>(observer);
            observer.addOnPreDrawListener(state);
        }
        root.invalidate();
    }

    static void clear(View root) {
        State state = states.remove(root);
        if (state == null) return;
        ViewTreeObserver observer = state.observer.get();
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(state);
        state.restore(root);
    }

    private static RectF rect(View view, Matrix inverse) {
        Matrix transform = new Matrix();
        view.transformMatrixToGlobal(transform);
        RectF rect = new RectF(0, 0, view.getWidth(), view.getHeight());
        transform.mapRect(rect);
        inverse.mapRect(rect);
        return rect;
    }

    private static View findClock(View root) {
        View best = null;
        for (Class<?> type = root.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")
                    && root.getVisibility() == View.VISIBLE) return root;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findClock(group.getChildAt(i));
                if (candidate != null
                        && (best == null
                                || candidate.getWidth() * candidate.getHeight()
                                        > best.getWidth() * best.getHeight())) best = candidate;
            }
        return best;
    }
}
