package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class MediaInteraction {
    private static final Map<View, Target> targets = new WeakHashMap<>();
    private static final Map<View, Boolean> rejected = new WeakHashMap<>();
    private static final Set<Class<?>> stacks = Collections.newSetFromMap(new WeakHashMap<>());
    private static DressroomModule hookModule;
    private static Runnable touchUnhook;
    private static boolean touchDeoptimized;
    private static boolean touchActive;

    private static final class Target {
        final WeakReference<Object> holder;
        final WeakReference<View> title;
        final OneUi9MediaBinding.HolderContract contract;
        boolean outside, moved;
        float x, y;

        Target(Object holder, View title, OneUi9MediaBinding.HolderContract contract) {
            this.holder = new WeakReference<>(holder);
            this.title = new WeakReference<>(title);
            this.contract = contract;
        }
    }

    static synchronized void bind(DressroomModule module, Object holder, View root)
            throws ReflectiveOperationException {
        OneUi9MediaBinding.HolderContract contract =
                OneUi9MediaBinding.holderContract(holder.getClass().getClassLoader());
        targets.put(root, new Target(holder, (View) contract.titleArtist().get(holder), contract));
        hookModule = module;
        syncTouchHook();
    }

    static synchronized void setActive(boolean active) {
        if (touchActive == active) return;
        touchActive = active;
        syncTouchHook();
    }

    private static synchronized void syncTouchHook() {
        boolean needed = touchActive && !targets.isEmpty() && hookModule != null;
        if (needed && touchUnhook == null) {
            DressroomModule module = hookModule;
            try {
                Method dispatch =
                        ViewGroup.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
                var handle =
                        module.hook(dispatch)
                                .setId("dressroom-advanced.media-artwork-touch")
                                .intercept(
                                        chain -> {
                                            View rootView = (View) chain.getThisObject();
                                            Target target = targets.get(rootView);
                                            if (target == null) return chain.proceed();
                                            MotionEvent event = (MotionEvent) chain.getArg(0);
                                            int action = event.getActionMasked();
                                            if (action == MotionEvent.ACTION_DOWN) {
                                                target.outside = false;
                                                if (!RuntimeExtras.enabled(
                                                        rootView.getContext(), "fullscreenArt"))
                                                    return chain.proceed();
                                                View title = target.title.get();
                                                Rect bounds = new Rect();
                                                target.outside =
                                                        title == null
                                                                || !title.getGlobalVisibleRect(
                                                                        bounds)
                                                                || !bounds.contains(
                                                                        (int) event.getRawX(),
                                                                        (int) event.getRawY());
                                                target.x = event.getRawX();
                                                target.y = event.getRawY();
                                                target.moved = false;
                                            }
                                            if (!target.outside) return chain.proceed();
                                            if (action == MotionEvent.ACTION_MOVE
                                                    || action == MotionEvent.ACTION_UP) {
                                                float dx = event.getRawX() - target.x;
                                                float dy = event.getRawY() - target.y;
                                                int slop =
                                                        ViewConfiguration.get(rootView.getContext())
                                                                .getScaledTouchSlop();
                                                target.moved |= dx * dx + dy * dy > slop * slop;
                                            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                                                target.moved = true;
                                            }
                                            if (action == MotionEvent.ACTION_UP) {
                                                target.outside = false;
                                                Object holderObject = target.holder.get();
                                                if (holderObject != null
                                                        && !target.moved
                                                        && RuntimeExtras.enabled(
                                                                rootView.getContext(),
                                                                "fullscreenArt"))
                                                    try {
                                                        if (!Boolean.TRUE.equals(
                                                                target.contract
                                                                        .showing()
                                                                        .get(holderObject))) {
                                                            target.contract
                                                                    .viewModelExpand()
                                                                    .invoke(
                                                                            target.contract
                                                                                    .viewModel()
                                                                                    .get(
                                                                                            holderObject));
                                                        }
                                                    } catch (Exception error) {
                                                        module.report(
                                                                "Native media outside-close"
                                                                        + " unavailable",
                                                                error);
                                                    }
                                            } else if (action == MotionEvent.ACTION_CANCEL)
                                                target.outside = false;
                                            return true;
                                        });
                if (!touchDeoptimized) {
                    module.deoptimize(dispatch);
                    touchDeoptimized = true;
                }
                touchUnhook = handle::unhook;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                module.report("Media touch hook unavailable", error);
            }
        } else if (!needed && touchUnhook != null) {
            Runnable unhook = touchUnhook;
            touchUnhook = null;
            unhook.run();
        }
    }

    static void installStack(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> stack =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.NowBarCardStackView",
                        false,
                        loader);
        if (stacks.contains(stack)) return;
        Method bounds = OneUi9MediaBinding.pillBounds(loader);
        module.hook(bounds)
                .setId("dressroom-advanced.native-pill-content-bounds")
                .intercept(
                        chain -> {
                            if (!stack.isInstance(chain.getArg(0))) return chain.proceed();
                            try {
                                Boolean inside =
                                        PillTouchBounds.contains(
                                                (ViewGroup) chain.getArg(0),
                                                ((Number) chain.getArg(1)).intValue(),
                                                ((Number) chain.getArg(2)).intValue());
                                if (inside != null) return inside;
                            } catch (ReflectiveOperationException ignored) {
                            }
                            return chain.proceed();
                        });
        module.deoptimize(bounds);
        module.deoptimize(
                Class.forName(
                                "com.samsung.android.app.facewidget.FaceWidgetViewModel",
                                false,
                                loader)
                        .getDeclaredMethod("isInContentBounds", float.class, float.class));
        Method dispatch = stack.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        module.hook(dispatch)
                .setId("dressroom-advanced.native-pill-touch-bounds")
                .intercept(
                        chain -> {
                            ViewGroup root = (ViewGroup) chain.getThisObject();
                            MotionEvent event = (MotionEvent) chain.getArg(0);
                            int action = event.getActionMasked();
                            if (action == MotionEvent.ACTION_DOWN) {
                                rejected.remove(root);
                                try {
                                    if (Boolean.FALSE.equals(
                                            PillTouchBounds.contains(
                                                    root,
                                                    (int) event.getRawX(),
                                                    (int) event.getRawY())))
                                        rejected.put(root, true);
                                } catch (Exception ignored) {
                                }
                            }
                            boolean ignore = Boolean.TRUE.equals(rejected.get(root));
                            if (action == MotionEvent.ACTION_UP
                                    || action == MotionEvent.ACTION_CANCEL) rejected.remove(root);
                            return ignore ? false : chain.proceed();
                        });
        module.deoptimize(dispatch);
        stacks.add(stack);
    }
}
