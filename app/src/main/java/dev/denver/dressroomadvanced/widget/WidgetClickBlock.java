package dev.denver.dressroomadvanced;

import android.appwidget.AppWidgetHostView;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Blocks lockscreen widget taps and AOD TSP clicks when enabled, while allowing
 * swipe gestures over the widget area to reach the lockscreen and shade.
 */
final class WidgetClickBlock {
    static final String KEY = "disableWidgetClicks";
    static final String KEYGUARD_CONTAINER =
            "com.samsung.android.app.facewidget.complication.KeyguardComplicationWidgetContainer";
    static final String COMPLICATION_CONTAINER =
            "com.samsung.android.app.facewidget.complication.ComplicationContainer";
    private static final String PLUGIN_CONTROLLER =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController";
    private static final String COMPLICATION_MANAGER =
            "com.samsung.android.app.facewidget.complication.ComplicationManager";
    private static final String GRID_ITEM_VIEW =
            "com.samsung.android.app.aodservice.common.grid.view.GridItemView";

    private static final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Class<?>> observedHostViews =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    record Targets(
            Method keyguardDispatchTouch,
            Method keyguardContainsTouchable,
            List<Method> keyguardOnTsp,
            Method complicationContainsTouchable,
            List<Method> complicationOnTsp,
            Method controllerDispatchTsp,
            Method managerInContentBounds,
            Field managerContainer,
            Method gridItemDispatchTouch) {}

    private WidgetClickBlock() {}

    static boolean shouldSuppressTouch(boolean disabled, int actionMasked) {
        return disabled && actionMasked != MotionEvent.ACTION_CANCEL;
    }

    static boolean isLockscreenContainerName(String className) {
        return KEYGUARD_CONTAINER.equals(className) || COMPLICATION_CONTAINER.equals(className);
    }

    static boolean isInLockscreenComplication(View view) {
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (isLockscreenContainerName(current.getClass().getName())) return true;
        }
        return false;
    }

    static Targets resolveTargets(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> keyguardContainer = Class.forName(KEYGUARD_CONTAINER, false, loader);
        Method keyguardDispatchTouch =
                NativeContract.method(
                        keyguardContainer, "dispatchTouchEvent", boolean.class, MotionEvent.class);
        Method keyguardContainsTouchable =
                NativeContract.method(
                        keyguardContainer,
                        "containsTouchableRect",
                        boolean.class,
                        int.class,
                        int.class);
        List<Method> keyguardOnTsp = onTspMethods(keyguardContainer);

        Method complicationContainsTouchable = null;
        List<Method> complicationOnTsp = List.of();
        try {
            Class<?> complicationContainer = Class.forName(COMPLICATION_CONTAINER, false, loader);
            complicationContainsTouchable =
                    NativeContract.method(
                            complicationContainer,
                            "containsTouchableRect",
                            boolean.class,
                            int.class,
                            int.class);
            complicationOnTsp = onTspMethods(complicationContainer);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }

        Method controllerDispatchTsp = null;
        try {
            Class<?> pluginController = Class.forName(PLUGIN_CONTROLLER, false, loader);
            controllerDispatchTsp =
                    NativeContract.method(
                            pluginController,
                            "dispatchTspEventToWidget",
                            void.class,
                            View.class,
                            int.class,
                            int.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }

        Method managerInContentBounds = null;
        Field managerContainer = null;
        try {
            Class<?> complicationManager = Class.forName(COMPLICATION_MANAGER, false, loader);
            managerInContentBounds =
                    NativeContract.method(
                            complicationManager,
                            "isInContentBounds",
                            boolean.class,
                            float.class,
                            float.class);
            managerContainer =
                    NativeContract.assignableField(complicationManager, "container", ViewGroup.class);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
        }

        Method gridItemDispatchTouch = null;
        try {
            Class<?> gridItemView = Class.forName(GRID_ITEM_VIEW, false, loader);
            gridItemDispatchTouch =
                    NativeContract.method(
                            gridItemView, "dispatchTouchEvent", boolean.class, MotionEvent.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }

        return new Targets(
                keyguardDispatchTouch,
                keyguardContainsTouchable,
                keyguardOnTsp,
                complicationContainsTouchable,
                complicationOnTsp,
                controllerDispatchTsp,
                managerInContentBounds,
                managerContainer,
                gridItemDispatchTouch);
    }

    private static List<Method> onTspMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            // Parameter 2 uses an obfuscated AOD TSP event class that differs by release.
            if (!method.isBridge() && method.getName().equals("onTspEvent")) {
                methods.add(method);
            }
        }
        return List.copyOf(methods);
    }

    private static boolean enabled(View view) {
        return view != null
                && view.getContext() != null
                && RuntimeExtras.enabled(view.getContext(), KEY);
    }

    private static boolean suppressTouch(View view, Object motionEventArg) {
        if (!enabled(view)) return false;
        int action =
                motionEventArg instanceof MotionEvent ev
                        ? ev.getActionMasked()
                        : MotionEvent.ACTION_DOWN;
        return shouldSuppressTouch(true, action);
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess() || loader == null || installed.contains(loader)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Targets targets = resolveTargets(loader);
            hookDispatchSuppress(
                    module,
                    hooks,
                    "dressroom-advanced.widget-click-block-dispatch",
                    targets.keyguardDispatchTouch(),
                    false);
            hookContainsTouchable(
                    module,
                    hooks,
                    "dressroom-advanced.widget-click-block-tsp-hit",
                    targets.keyguardContainsTouchable(),
                    targets.keyguardOnTsp());
            hookContainsTouchable(
                    module,
                    hooks,
                    "dressroom-advanced.widget-click-block-container-tsp-hit",
                    targets.complicationContainsTouchable(),
                    targets.complicationOnTsp());
            if (targets.controllerDispatchTsp() != null) {
                hooks.add(
                        module.hook(targets.controllerDispatchTsp())
                                .setId("dressroom-advanced.widget-click-block-tsp-dispatch")
                                .intercept(
                                        chain -> {
                                            if (chain.getArg(0) instanceof View view
                                                    && enabled(view)) {
                                                return null;
                                            }
                                            return chain.proceed();
                                        })
                                ::unhook);
                module.deoptimize(targets.controllerDispatchTsp());
            }
            if (targets.managerInContentBounds() != null && targets.managerContainer() != null) {
                Field containerField = targets.managerContainer();
                hooks.add(
                        module.hook(targets.managerInContentBounds())
                                .setId("dressroom-advanced.widget-click-block-bounds")
                                .intercept(
                                        chain -> {
                                            try {
                                                Object raw = containerField.get(chain.getThisObject());
                                                if (raw instanceof View container
                                                        && enabled(container)) {
                                                    return false;
                                                }
                                            } catch (IllegalAccessException | RuntimeException ignored) {
                                            }
                                            return chain.proceed();
                                        })
                                ::unhook);
                module.deoptimize(targets.managerInContentBounds());
            }
            hookDispatchSuppress(
                    module,
                    hooks,
                    "dressroom-advanced.widget-click-block-grid-item",
                    targets.gridItemDispatchTouch(),
                    true);
            hooks.commit();
            installed.add(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Optional lockscreen widget click block unavailable", error);
        }
    }

    private static void hookDispatchSuppress(
            DressroomModule module,
            HookTransaction hooks,
            String id,
            Method method,
            boolean requireComplicationParent) {
        if (method == null) return;
        hooks.add(
                module.hook(method)
                        .setId(id)
                        .intercept(
                                chain -> {
                                    if (chain.getThisObject() instanceof View view
                                            && (!requireComplicationParent
                                                    || isInLockscreenComplication(view))
                                            && suppressTouch(view, chain.getArg(0))) {
                                        return false;
                                    }
                                    return chain.proceed();
                                })
                        ::unhook);
        module.deoptimize(method);
    }

    private static void hookContainsTouchable(
            DressroomModule module,
            HookTransaction hooks,
            String id,
            Method containsTouchable,
            List<Method> onTsp) {
        if (containsTouchable == null) return;
        hooks.add(
                module.hook(containsTouchable)
                        .setId(id)
                        .intercept(
                                chain -> {
                                    if (enabled((View) chain.getThisObject())) {
                                        return false;
                                    }
                                    return chain.proceed();
                                })
                        ::unhook);
        module.deoptimize(containsTouchable);
        for (Method method : onTsp) {
            module.deoptimize(method);
        }
    }

    static void observeRoot(DressroomModule module, ViewGroup root) {
        if (module.inEditorProcess() || root == null) return;
        if (KEYGUARD_CONTAINER.equals(root.getClass().getName())) return;
        hookHostViews(module, root);
    }

    private static void hookHostViews(DressroomModule module, ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            if (child instanceof AppWidgetHostView) {
                installHostViewHook(module, child.getClass());
            } else if (child instanceof ViewGroup nested) {
                hookHostViews(module, nested);
            }
        }
    }

    private static void installHostViewHook(DressroomModule module, Class<?> hostClass) {
        synchronized (observedHostViews) {
            if (!observedHostViews.add(hostClass)) return;
        }
        try (HookTransaction hooks = new HookTransaction()) {
            try {
                Method dispatch =
                        hostClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
                hookDispatchSuppress(
                        module,
                        hooks,
                        "dressroom-advanced.widget-click-block-host-dispatch",
                        dispatch,
                        true);
                hooks.commit();
                return;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method intercept =
                        hostClass.getDeclaredMethod("onInterceptTouchEvent", MotionEvent.class);
                hooks.add(
                        module.hook(intercept)
                                .setId("dressroom-advanced.widget-click-block-host-intercept")
                                .intercept(
                                        chain -> {
                                            if (chain.getThisObject() instanceof View view
                                                    && isInLockscreenComplication(view)
                                                    && !view.isClickable()
                                                    && !view.isLongClickable()
                                                    && !view.isContextClickable()
                                                    && suppressTouch(view, chain.getArg(0))) {
                                                return true;
                                            }
                                            return chain.proceed();
                                        })
                                ::unhook);
                module.deoptimize(intercept);
                hooks.commit();
            } catch (NoSuchMethodException ignored) {
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }
}
