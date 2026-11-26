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
        boolean legacyHostBounds;
        final RectF lastClockRect = new RectF();
        final Matrix parentGlobal = new Matrix();
        final Matrix inverse = new Matrix();
        final Matrix scratchMatrix = new Matrix();
        final RectF screenRect = new RectF();
        final RectF currentRect = new RectF();
        final RectF clockRect = new RectF();
        final RectF hostRect = new RectF();
        final float[] unitVec = new float[4];
        final float[] targetPos = new float[2];
        WeakReference<View> contentChild = new WeakReference<>(null);
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
                // A ROM version can be newer than its installed clock provider.
                // Page binding is optional; do not abandon docking when it is absent.
                if (time == null && useNativePage) time = findClock(window);
                clock = new WeakReference<>(time);
            }
            if (time == null && lastClockRect.isEmpty() && settings.getInt("anchor") != 5) return true;
            parentGlobal.reset();
            parent.transformMatrixToGlobal(parentGlobal);
            if (!parentGlobal.invert(inverse)) return true;
            rect(window, inverse, scratchMatrix, screenRect);
            // The runtime root can fill the screen even when its widget grid is narrower.
            // Dock the visible group, not that full-width wrapper.
            placementBounds(
                    view, inverse, legacyHostBounds, scratchMatrix, hostRect, this, currentRect);
            if (time != null) {
                rect(time, inverse, scratchMatrix, clockRect);
                clockRect.offset(-screenRect.left, -screenRect.top);
                lastClockRect.set(clockRect);
            } else if (!lastClockRect.isEmpty()) {
                clockRect.set(lastClockRect);
            } else {
                clockRect.setEmpty();
            }
            float density = view.getResources().getDisplayMetrics().density;
            unitVec[0] = density;
            unitVec[1] = 0;
            unitVec[2] = 0;
            unitVec[3] = density;
            inverse.mapVectors(unitVec);
            float unitScale = (float) Math.hypot(unitVec[0], unitVec[1]);
            float gap = settings.getFloat("gap", 8) * unitScale;
            float[] target =
                    WidgetLayout.placeInto(
                            targetPos,
                            settings.getInt("anchor"),
                            clockRect.left,
                            clockRect.top,
                            clockRect.right,
                            clockRect.bottom,
                            currentRect.width(),
                            currentRect.height(),
                            screenRect.width(),
                            screenRect.height(),
                            gap,
                            settings.getFloat("freeX", .5f),
                            settings.getFloat("freeY", .4f),
                            settings.getFloat("dockOffset") * unitScale);
            float baseLeft = currentRect.left - view.getTranslationX();
            float baseTop = currentRect.top - view.getTranslationY();
            float targetTx = target[0] + screenRect.left - baseLeft;
            float targetTy = target[1] + screenRect.top - baseTop;
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
        apply(root, settings, provider, false);
    }

    static void disableAncestorClipping(View root) {
        android.view.ViewParent cursor = root instanceof ViewGroup ? (ViewGroup) root : root.getParent();
        for (int depth = 0; depth < 5 && cursor instanceof ViewGroup group; depth++) {
            group.setClipChildren(false);
            group.setClipToPadding(false);
            cursor = group.getParent();
        }
    }

    static void apply(View root, Bundle settings, Object provider, boolean legacyHostBounds) {
        if (root != null) disableAncestorClipping(root);
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
        state.legacyHostBounds = legacyHostBounds;
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
        if (old != observer || (old != null && !old.isAlive())) {
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
        if (root == null || states.isEmpty()) return false;
        for (View current = root; current != null; ) {
            if (hasActivePlacement(states.get(current))) return true;
            Object parent = current.getParent();
            current = parent instanceof View view ? view : null;
        }
        synchronized (states) {
            for (var entry : states.entrySet()) {
                if (!hasActivePlacement(entry.getValue())) continue;
                for (View current = entry.getKey(); current != null; ) {
                    if (current == root) return true;
                    Object parent = current.getParent();
                    current = parent instanceof View view ? view : null;
                }
            }
        }
        return false;
    }

    private static boolean hasActivePlacement(State state) {
        return state != null && state.settings != null && state.settings.getInt("anchor") != 0;
    }

    private static RectF rect(View view, Matrix inverse) {
        RectF out = new RectF();
        rect(view, inverse, new Matrix(), out);
        return out;
    }

    private static void rect(View view, Matrix inverse, Matrix scratch, RectF out) {
        scratch.reset();
        view.transformMatrixToGlobal(scratch);
        out.set(0, 0, view.getWidth(), view.getHeight());
        scratch.mapRect(out);
        inverse.mapRect(out);
    }

    private static void contentRect(View view, Matrix inverse, Matrix scratch, RectF out) {
        scratch.reset();
        view.transformMatrixToGlobal(scratch);
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getWidth() - view.getPaddingRight();
        int bottom = view.getHeight() - view.getPaddingBottom();
        if (right <= left) {
            left = 0;
            right = view.getWidth();
        }
        if (bottom <= top) {
            top = 0;
            bottom = view.getHeight();
        }
        out.set(left, top, right, bottom);
        scratch.mapRect(out);
        inverse.mapRect(out);
    }

    private static View placementContent(View root, State state) {
        if (state != null) {
            View cached = state.contentChild.get();
            if (cached != null
                    && cached.getParent() == root
                    && cached.getWidth() > 0
                    && cached.getHeight() > 0) return cached;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                String name = child.getClass().getName();
                if ((name.equals(
                                        "com.samsung.android.app.facewidget.complication.KeyguardComplicationWidgetContainer")
                                || name.equals(
                                        "com.samsung.android.app.aodservice.common.grid.view.GridContainerView"))
                        && child.getWidth() > 0
                        && child.getHeight() > 0) {
                    if (state != null) state.contentChild = new WeakReference<>(child);
                    return child;
                }
            }
        }
        return root;
    }

    static RectF placementBounds(View root, Matrix inverse, boolean legacyHostBounds) {
        RectF out = new RectF();
        placementBounds(root, inverse, legacyHostBounds, new Matrix(), new RectF(), null, out);
        return out;
    }

    private static void placementBounds(
            View root,
            Matrix inverse,
            boolean legacyHostBounds,
            Matrix scratch,
            RectF hostRect,
            State state,
            RectF out) {
        if (legacyHostBounds) {
            out.setEmpty();
            collectHostBounds(root, inverse, scratch, hostRect, out, WidgetPlacement::isWidgetHost);
            if (!out.isEmpty()) return;
        }
        contentRect(placementContent(root, state), inverse, scratch, out);
    }

    static RectF occupiedBounds(View root, Matrix inverse, java.util.function.Predicate<View> host) {
        RectF occupied = new RectF();
        collectHostBounds(root, inverse, new Matrix(), new RectF(), occupied, host);
        return occupied;
    }

    private static boolean isWidgetHost(View root) {
        for (Class<?> type = root.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer"))
                return true;
        return false;
    }

    private static void collectHostBounds(
            View root,
            Matrix inverse,
            Matrix scratch,
            RectF hostRect,
            RectF occupied,
            java.util.function.Predicate<View> host) {
        if (root.getVisibility() != View.VISIBLE || root.getAlpha() <= 0f) return;
        if (host.test(root)) {
            if (root.getWidth() > 0 && root.getHeight() > 0) {
                rect(root, inverse, scratch, hostRect);
                occupied.union(hostRect);
            }
            return;
        }
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                collectHostBounds(group.getChildAt(i), inverse, scratch, hostRect, occupied, host);
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
