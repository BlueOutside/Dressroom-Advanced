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
    private static boolean installed;

    private static final class Target {
        final WeakReference<Object> holder;
        final WeakReference<View> title;
        boolean outside;
        float x, y;

        Target(Object holder, View title) {
            this.holder = new WeakReference<>(holder);
            this.title = new WeakReference<>(title);
        }
    }

    static void bind(DressroomModule module, Object holder, View root)
            throws ReflectiveOperationException {
        targets.put(root, new Target(holder, (View) Reflect.field(holder, "c0")));
        if (installed) return;
        Method dispatch =
                ViewGroup.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        module.hook(dispatch)
                .setId("dressroom-advanced.media-artwork-touch")
                .intercept(
                        chain -> {
                            View rootView = (View) chain.getThisObject();
                            Target target = targets.get(rootView);
                            if (target == null
                                    || !RuntimeExtras.enabled(
                                            rootView.getContext(), "fullscreenArt"))
                                return chain.proceed();
                            MotionEvent event = (MotionEvent) chain.getArg(0);
                            int action = event.getActionMasked();
                            if (action == MotionEvent.ACTION_DOWN) {
                                View title = target.title.get();
                                Rect bounds = new Rect();
                                target.outside =
                                        title == null
                                                || !title.getGlobalVisibleRect(bounds)
                                                || !bounds.contains(
                                                        (int) event.getRawX(),
                                                        (int) event.getRawY());
                                target.x = event.getX();
                                target.y = event.getY();
                            }
                            if (!target.outside) return chain.proceed();
                            if (action == MotionEvent.ACTION_UP) {
                                target.outside = false;
                                float dx = event.getX() - target.x, dy = event.getY() - target.y;
                                int slop =
                                        ViewConfiguration.get(rootView.getContext())
                                                .getScaledTouchSlop();
                                Object holderObject = target.holder.get();
                                if (holderObject != null && dx * dx + dy * dy <= slop * slop)
                                    try {
                                        if (!Boolean.TRUE.equals(Reflect.field(holderObject, "T0")))
                                            Reflect.call(Reflect.field(holderObject, "J"), "m");
                                    } catch (Exception error) {
                                        module.report(
                                                "Native media outside-close unavailable", error);
                                    }
                            } else if (action == MotionEvent.ACTION_CANCEL) target.outside = false;
                            return true;
                        });
        module.deoptimize(dispatch);
        installed = true;
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
