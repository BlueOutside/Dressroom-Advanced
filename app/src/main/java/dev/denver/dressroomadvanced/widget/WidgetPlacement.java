package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class WidgetPlacement {
    private static final Map<View, State> states =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> root;
        WeakReference<View> clock = new WeakReference<>(null);
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        View.OnLayoutChangeListener layoutListener;
        Bundle settings;
        boolean useNativePage;
        final RectF lastClockRect = new RectF();
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
            View window = view.getRootView();
            View time = clock.get();
            if (time == null || !time.isAttachedToWindow() || time.getRootView() != window
                    || time.getWidth() == 0 || time.getHeight() == 0
                    || (useNativePage && !shown(time))) {
                time = useNativePage ? findPageClock(window) : findClock(window);
                clock = new WeakReference<>(time);
            }
            if (time == null && lastClockRect.isEmpty() && settings.getInt("anchor") != 5) return true;
            Matrix parentGlobal = new Matrix();
            parent.transformMatrixToGlobal(parentGlobal);
            Matrix inverse = new Matrix();
            if (!parentGlobal.invert(inverse)) return true;
            RectF screen = rect(window, inverse);
            RectF current = rect(view, inverse);
            RectF clockRect;
            if (time != null) {
                clockRect = rect(time, inverse);
                clockRect.offset(-screen.left, -screen.top);
                lastClockRect.set(clockRect);
            } else if (!lastClockRect.isEmpty()) {
                clockRect = new RectF(lastClockRect);
            } else {
                clockRect = new RectF();
            }
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
            float baseLeft = current.left - view.getTranslationX();
            float baseTop = current.top - view.getTranslationY();
            float targetTx = target[0] + screen.left - baseLeft;
            float targetTy = target[1] + screen.top - baseTop;
            float tx = view.getTranslationX(), ty = view.getTranslationY();
            // A native animation can replace either translation between frames.
            // Our previous delta no longer belongs to an axis it has overwritten.
            if (Math.abs(tx - appliedX) > .01f) deltaX = 0;
            if (Math.abs(ty - appliedY) > .01f) deltaY = 0;
            if (Math.abs(tx - targetTx) > .25f || Math.abs(ty - targetTy) > .25f) {
                LockscreenFrameBoost.pulse(view, 220L);
                deltaX = targetTx - (tx - deltaX);
                deltaY = targetTy - (ty - deltaY);
                appliedX = targetTx;
                appliedY = targetTy;
                view.setTranslationX(targetTx);
                view.setTranslationY(targetTy);
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
        if (settings == null) {
            clear(root);
            return;
        }
        boolean foldable = ScreenIdentity.supported();
        int anchor = WidgetLayout.effectiveAnchor(settings.getInt("anchor"), settings.getBoolean("layout"),
                settings.getBoolean("vertical"), foldable);
        if (anchor == 0) {
            clear(root);
            return;
        }
        State state = states.computeIfAbsent(root, State::new);
        state.settings = new Bundle(settings);
        state.settings.putInt("anchor", anchor);
        state.useNativePage = provider != null && PlatformGate.oneUiVersion() >= 90500;
        if (state.useNativePage) state.clock.clear();
        if (provider != null && !state.useNativePage)
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
        if (state.layoutListener == null) {
            state.layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> state.onPreDraw();
            root.addOnLayoutChangeListener(state.layoutListener);
        }
        WidgetDepthBlur.afterPlacement(root);
        EditorBlurCapture.afterPlacement(root);
        // Non-foldables use the canvas-motion watcher instead of the separate
        // outline watcher. Both must sample after this frame's placement.
        DepthViewMotion.afterPlacement(root);
        root.invalidate();
    }

    static void clear(View root) {
        State state = states.remove(root);
        if (state == null) return;
        ViewTreeObserver observer = state.observer.get();
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(state);
        if (state.layoutListener != null) {
            root.removeOnLayoutChangeListener(state.layoutListener);
            state.layoutListener = null;
        }
        state.restore(root);
    }

    static boolean isCustomPlacement(View root) {
        if (root == null) return false;
        for (View current = root; current != null; ) {
            State state = states.get(current);
            if (state != null && state.settings != null && state.settings.getInt("anchor") != 0)
                return true;
            Object parent = current.getParent();
            current = parent instanceof View view ? view : null;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (isCustomPlacement(group.getChildAt(i))) return true;
            }
        }
        return false;
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
        View visible = findClock(root, false);
        return visible != null ? visible : findClock(root, true);
    }

    private static View findPageClock(View root) {
        if (!shown(root)) return null;
        for (Class<?> type = root.getClass(); type != null; type = type.getSuperclass()) {
            if (!type.getName().equals(OneUi95WidgetRuntime.CLOCK_PAGE)) continue;
            try {
                Object clock = OneUi95WidgetRuntime.currentClock(type).invoke(root);
                if (clock instanceof View view && view.isAttachedToWindow() && shown(view)
                        && view.getWidth() > 0 && view.getHeight() > 0) return view;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                DressroomModule.note("Widget placement clock page unavailable", unavailable);
            }
            return null;
        }
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View clock = findPageClock(group.getChildAt(i));
                if (clock != null) return clock;
            }
        return null;
    }

    private static boolean shown(View view) {
        return view.isShown() && view.getAlpha() > 0f;
    }

    private static View findClock(View root, boolean allowHidden) {
        if (root == null) return null;
        for (Class<?> type = root.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")) {
                if (root.getWidth() > 0 && root.getHeight() > 0
                        && (allowHidden || root.isShown())) return root;
            }
        View best = null;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findClock(group.getChildAt(i), allowHidden);
                if (candidate != null
                        && (best == null
                                || candidate.getWidth() * candidate.getHeight()
                                        > best.getWidth() * best.getHeight())) best = candidate;
            }
        return best;
    }
}
