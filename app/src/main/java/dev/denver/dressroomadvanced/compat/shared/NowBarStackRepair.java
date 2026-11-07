package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/** Refresh fixes for the supported native stacks. Samsung still owns card order and animations. */
final class NowBarStackRepair {
    private final Method data, item, key, blur, expanded, normalContent;
    private final Field state, animator, dragging, reordering, dismissing, expanding;

    private NowBarStackRepair(NowBarStackBinding binding) {
        data = binding.data;
        item = binding.item;
        key = binding.key;
        blur = binding.blur;
        expanded = binding.expanded;
        normalContent = binding.content;
        state = binding.state;
        animator = binding.animator;
        dragging = binding.dragging;
        reordering = binding.reordering;
        dismissing = binding.dismissing;
        expanding = binding.expanding;
    }

    static void install(DressroomModule module, NowBarStackBinding binding)
            throws ReflectiveOperationException {
        Class<?> stack = binding.stack;
        NowBarStackRepair repair = new NowBarStackRepair(binding);
        Method refresh = binding.refresh;
        Method animate = binding.animate;
        Method layout =
                FrameLayout.class.getDeclaredMethod(
                        "onLayout", boolean.class, int.class, int.class, int.class, int.class);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(refresh)
                                    .setId("dressroom-advanced.stack-refresh")
                                    .intercept(
                                            chain -> {
                                                try {
                                                    repair.beforeRefresh(
                                                            (ViewGroup) chain.getThisObject(),
                                                            (CopyOnWriteArrayList<?>)
                                                                    chain.getArg(0));
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    module.report(
                                                            "Now Bar refresh repair unavailable",
                                                            error);
                                                }
                                                Object result = chain.proceed();
                                                repair.updatePivotsSafely(
                                                        module, (ViewGroup) chain.getThisObject());
                                                return result;
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(layout)
                                    .setId("dressroom-advanced.stack-pill-pivot")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                if (stack.isInstance(chain.getThisObject()))
                                                    repair.updatePivotsSafely(
                                                            module,
                                                            (ViewGroup) chain.getThisObject());
                                                return result;
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(animate)
                                    .setId("dressroom-advanced.stack-move-opacity")
                                    .intercept(
                                            chain -> {
                                                View view = (View) chain.getArg(0);
                                                boolean interruptedShow = false;
                                                try {
                                                    Object previous = repair.data.invoke(view);
                                                    interruptedShow =
                                                            repair.isMove(chain.getArg(1))
                                                                    && previous != null
                                                                    && repair.name(previous)
                                                                            .startsWith("SHOW_");
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    module.report(
                                                            "Now Bar appearance state unavailable",
                                                            error);
                                                }
                                                Object result = chain.proceed();
                                                if (interruptedShow) {
                                                    try {
                                                        // MOVE_OTHER animates scale/Y only.
                                                        // Cancelling a SHOW fade otherwise
                                                        // leaves the card (and sometimes its blur
                                                        // drawable) partly invisible.
                                                        view.setAlpha(1f);
                                                        View background =
                                                                (View) repair.blur.invoke(view);
                                                        Drawable drawable =
                                                                background == null
                                                                        ? null
                                                                        : background
                                                                                .getBackground();
                                                        if (drawable != null)
                                                            drawable.setAlpha(255);
                                                    } catch (ReflectiveOperationException
                                                            | RuntimeException error) {
                                                        module.report(
                                                                "Now Bar appearance completion"
                                                                        + " unavailable",
                                                                error);
                                                    }
                                                }
                                                return result;
                                            })
                            ::unhook);
            module.deoptimize(refresh);
            module.deoptimize(animate);
            module.deoptimize(layout);
            hooks.commit();
        }
        android.util.Log.i(
                DressroomModule.TAG, "Native Now Bar refresh and pill-pivot repair installed");
    }

    private void updatePivotsSafely(DressroomModule module, ViewGroup stack) {
        try {
            if (expanding.getBoolean(stack) || Boolean.TRUE.equals(expanded.invoke(stack))) return;
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                View content = (View) normalContent.invoke(child);
                if (content == null
                        || content.getParent() != child
                        || content.getWidth() <= 0
                        || content.getHeight() <= 0) continue;
                // Some cards retain a tall, invisible expanded container. Scaling around
                // that wrapper's center pulls the actual pill into the card above it.
                float x = content.getLeft() + content.getWidth() * .5f;
                float y = content.getTop() + content.getHeight() * .5f;
                if (Math.abs(child.getPivotX() - x) > .01f) child.setPivotX(x);
                if (Math.abs(child.getPivotY() - y) > .01f) child.setPivotY(y);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.report("Now Bar pill pivot unavailable", error);
        }
    }

    private String name(Object model) throws IllegalAccessException {
        return ((Enum<?>) state.get(model)).name();
    }

    private boolean isMove(Object model) throws IllegalAccessException {
        String value = name(model);
        return value.equals("MOVE_OTHER") || value.equals("MOVE_OTHER_DELAY");
    }

    private void beforeRefresh(ViewGroup stack, CopyOnWriteArrayList<?> incoming)
            throws ReflectiveOperationException {
        if (dragging.getBoolean(stack)
                || reordering.getBoolean(stack)
                || dismissing.getBoolean(stack)
                || expanding.getBoolean(stack)
                || Boolean.TRUE.equals(expanded.invoke(stack))) return;
        HashSet<Object> retained = new HashSet<>();
        for (Object value : incoming) if (value != null) retained.add(key.invoke(null, value));
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            Object previous = data.invoke(child);
            if (previous == null || !isMove(previous)) continue;
            Object value = item.invoke(child);
            if (value == null || !retained.contains(key.invoke(null, value))) continue;
            Animator active = (Animator) animator.get(previous);
            if (active == null || !active.isStarted()) continue;
            float x = child.getScaleX(), y = child.getScaleY(), offset = child.getTranslationY();
            // The same-depth refresh writes its resting pose without stopping this animator.
            // Cancel before Samsung refreshes it, preserving the start pose for a changed slot.
            active.cancel();
            child.setScaleX(x);
            child.setScaleY(y);
            child.setTranslationY(offset);
            animator.set(previous, null);
        }
    }
}
