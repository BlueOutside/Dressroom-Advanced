package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Restore Samsung's own resting offsets, after its animations and gestures have finished. */
final class NowBarStackRepair {
    private final Method depth, offset, expanded, setting;
    private final Field dragging, expanding, animator;
    private final Set<ViewGroup> queued = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Animator> waiting = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Animator> cancelled = Collections.newSetFromMap(new WeakHashMap<>());

    private NowBarStackRepair(Class<?> stack, ClassLoader loader) throws ReflectiveOperationException {
        depth = NativeContract.method(stack, "e", int.class, View.class, boolean.class);
        offset = NativeContract.method(stack, "g", float.class, int.class);
        expanded = NativeContract.method(stack, "k", boolean.class);
        setting = NativeContract.method(stack, "n", boolean.class);
        dragging = NativeContract.field(stack, "E", boolean.class);
        expanding = NativeContract.field(stack, "U", boolean.class);
        animator = NativeContract.field(Class.forName("aod.qx4", false, loader), "f", Animator.class);
    }

    static void install(DressroomModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> stack = Class.forName("com.samsung.android.app.facewidget.utils.NowBarCardStackView", false, loader);
        NowBarStackRepair repair = new NowBarStackRepair(stack, loader);
        Class<?> listener = Class.forName("aod.zj0", false, loader);
        Field card = NativeContract.field(listener, "o", View.class);
        Method end = listener.getDeclaredMethod("onAnimationEnd", Animator.class);
        Method cancel = listener.getDeclaredMethod("onAnimationCancel", Animator.class);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(cancel).setId("dressroom-advanced.stack-cancel").intercept(chain -> {
                repair.cancelled.add((Animator) chain.getArg(0));return chain.proceed();
            })::unhook);
            hooks.add(module.hook(end).setId("dressroom-advanced.stack-settle").intercept(chain -> {
                // Android calls onAnimationEnd after cancellation too. That old target must
                // not overwrite the card's next stack position.
                Object result = repair.cancelled.remove((Animator) chain.getArg(0)) ? null : chain.proceed();
                View view = (View) card.get(chain.getThisObject());
                if (view.getParent() instanceof ViewGroup parent && stack.isInstance(parent)) repair.schedule(parent);
                return result;
            })::unhook);
            Method refresh = stack.getDeclaredMethod("q", CopyOnWriteArrayList.class);
            Method touch = stack.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            Method attach = stack.getDeclaredMethod("onAttachedToWindow");
            for (Method method : new Method[] {refresh, touch, attach}) {
                hooks.add(module.hook(method).setId("dressroom-advanced.stack-reconcile-" + method.getName()).intercept(chain -> {
                    Object result = chain.proceed();
                    if (method != touch || ((MotionEvent) chain.getArg(0)).getActionMasked() == MotionEvent.ACTION_UP
                            || ((MotionEvent) chain.getArg(0)).getActionMasked() == MotionEvent.ACTION_CANCEL)
                        repair.schedule((ViewGroup) chain.getThisObject());
                    return result;
                })::unhook);
                module.deoptimize(method);
            }
            module.deoptimize(end);module.deoptimize(cancel);
            hooks.commit();
        }
        android.util.Log.i(DressroomModule.TAG, "Native Now Bar stack settling repair installed");
    }

    private void schedule(ViewGroup stack) {
        if (!queued.add(stack)) return;
        WeakReference<ViewGroup> reference = new WeakReference<>(stack);
        stack.postOnAnimation(() -> {
            ViewGroup view = reference.get();
            if (view != null) { queued.remove(view);settle(view); }
        });
    }

    private void settle(ViewGroup stack) {
        try {
            if (!stack.isAttachedToWindow() || stack.getVisibility() != View.VISIBLE
                    || dragging.getBoolean(stack) || expanding.getBoolean(stack)
                    || Boolean.TRUE.equals(expanded.invoke(stack)) || Boolean.TRUE.equals(setting.invoke(stack))) return;
            boolean moving = false;
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                Object data = Reflect.call(child, "getShowHideAnimationData");
                Animator active = data == null ? null : (Animator) animator.get(data);
                if (active != null && active.isStarted()) {
                    moving = true;
                    if (waiting.add(active)) {
                        WeakReference<ViewGroup> reference = new WeakReference<>(stack);
                        active.addListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator ended) {
                                ended.removeListener(this);waiting.remove(ended);
                                ViewGroup owner = reference.get();if (owner != null) schedule(owner);
                            }
                        });
                    }
                }
            }
            if (moving) return;
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0) continue;
                int rank = ((Number) depth.invoke(stack, child, false)).intValue();
                if (rank < 0 || rank > 2) continue;
                float target = ((Number) offset.invoke(stack, rank)).floatValue();
                if (Float.isFinite(target) && Math.abs(child.getTranslationY() - target) > .5f)
                    child.setTranslationY(target);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Now Bar stack settle unavailable", error);
        }
    }
}
