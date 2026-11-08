package dev.denver.dressroomadvanced;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class RuntimeWidgetHooks {
    private final DressroomModule module;
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, State> controllers = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final DeferredRefresh<Object> pending =
            new DeferredRefresh<>(task -> main.post(task), this::refresh);
    private final Set<Class<?>> legacyControllers = Collections.newSetFromMap(new WeakHashMap<>());

    private static final class State {
        WeakReference<ViewGroup> root = new WeakReference<>(null);
        final RootLayoutWatch layoutWatch = new RootLayoutWatch();
        int type = -1, font = -1, orientation = -1;
        boolean loaded;
        Bundle depthProfile;
        Boolean depthEnabled, maskReady;
        String layout = "";
    }

    RuntimeWidgetHooks(DressroomModule module) {
        this.module = module;
    }

    synchronized void installLegacy(ClassLoader loader) throws ReflectiveOperationException {
        if (installed.contains(loader)) return;
        Class<?> type =
                Class.forName(
                        "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController",
                        false,
                        loader);
        NativeContract.assignableField(type, "container", ViewGroup.class);
        NativeContract.field(
                type,
                "complicationGridUIController",
                Class.forName(WidgetEngine.GRID, false, loader));
        java.util.List<Method> updates = legacyUpdates(type);
        Method release = NativeContract.method(type, "release", void.class);
        try (HookTransaction hooks = new HookTransaction()) {
            for (Method method : updates) {
                var update =
                        module.hook(method)
                                .setId("dressroom-advanced.85-widget-runtime-" + method.getName())
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            schedule(
                                                    chain.getThisObject(),
                                                    !method.getName().equals("setDarkAmount"));
                                            return result;
                                        });
                hooks.add(update::unhook);
                module.deoptimize(method);
            }
            var detach =
                    module.hook(release)
                            .setId("dressroom-advanced.85-widget-runtime-release")
                            .intercept(
                                    chain -> {
                                        clear(chain.getThisObject());
                                        return chain.proceed();
                                    });
            hooks.add(detach::unhook);
            module.deoptimize(release);
            Class<?> provider =
                    Class.forName(
                            "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                            false,
                            loader);
            var wallpaper = watchWallpaper(provider);
            hooks.add(wallpaper::unhook);
            for (Method method : provider.getDeclaredMethods()) {
                if (!method.getName().equals("onClockTypeChanged")
                        && !method.getName().equals("refreshClockFontDataInternal")) continue;
                var changed =
                        module.hook(method)
                                .setId("dressroom-advanced.85-widget-clock-profile")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            main.post(() -> refreshAll(true));
                                            return result;
                                        });
                hooks.add(changed::unhook);
                module.deoptimize(method);
            }
            hooks.commit();
            legacyControllers.add(type);
            installed.add(loader);
        }
    }

    static java.util.List<Method> legacyUpdates(Class<?> type) throws ReflectiveOperationException {
        ClassLoader loader = type.getClassLoader();
        return java.util.List.of(
                NativeContract.method(type, "initialize", void.class, ViewGroup.class),
                NativeContract.method(type, "initialize", void.class),
                NativeContract.method(type, "onFinishedWakingUp", void.class),
                NativeContract.method(type, "setDarkAmount", void.class, float.class),
                NativeContract.method(type, "setDozing", void.class, boolean.class),
                NativeContract.method(type, "requestAODState", void.class, boolean.class),
                NativeContract.method(
                        type,
                        "updateWidgetLayout",
                        void.class,
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.WidgetIdList",
                                false,
                                loader),
                        boolean.class),
                NativeContract.method(type, "onCompleteUpdate", void.class, Map.class));
    }

    private void schedule(Object controller, boolean force) {
        WeakReference<Object> reference = new WeakReference<>(controller);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(
                    () -> {
                        Object value = reference.get();
                        if (value != null) schedule(value, force);
                    });
            return;
        }
        pending.offer(controller, force);
    }

    synchronized void install(ClassLoader loader) {
        if (installed.contains(loader)) return;
        ArrayList<io.github.libxposed.api.XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> controller =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController",
                            false,
                            loader);
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isBridge()) continue;
                String name = method.getName();
                if (name.equals("updateClockGravity")) {
                    handles.add(
                            module.hook(method)
                                    .setId("dressroom-advanced.prevent-plugin-gravity-reset")
                                    .intercept(
                                            chain -> {
                                                Object controllerObj = chain.getThisObject();
                                                ViewGroup container =
                                                        (ViewGroup)
                                                                Reflect.field(
                                                                        controllerObj, "container");
                                                if (container != null
                                                        && WidgetPlacement.isCustomPlacement(
                                                                container)) {
                                                    return null;
                                                }
                                                return chain.proceed();
                                            }));
                    module.deoptimize(method);
                    continue;
                }
                if (!((name.equals("initialize")
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0] == ViewGroup.class)
                        || name.equals("setDarkAmount")
                        || name.equals("setDozing")
                        || name.equals("requestAODState")
                        || name.equals("onFinishedWakingUp")
                        || name.equals("refreshComplicationGridAndWidgets")
                        || name.equals("updateWidgetLayout")
                        || name.equals("onCompleteUpdate"))) continue;
                boolean force = !name.equals("setDarkAmount");
                handles.add(
                        module.hook(method)
                                .setId("dressroom-advanced.widget-owner-" + name)
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            refresh(chain.getThisObject(), force);
                                            return result;
                                        }));
                module.deoptimize(method);
            }
            Method release = controller.getDeclaredMethod("release");
            handles.add(
                    module.hook(release)
                            .setId("dressroom-advanced.widget-owner-release")
                            .intercept(
                                    chain -> {
                                        clear(chain.getThisObject());
                                        return chain.proceed();
                                    }));
            module.deoptimize(release);
            Class<?> provider =
                    Class.forName(
                            "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                            false,
                            loader);
            handles.add(watchWallpaper(provider));
            for (Method method : provider.getDeclaredMethods()) {
                if (!method.getName().equals("onClockTypeChanged")
                        && !method.getName().equals("refreshClockFontDataInternal")) continue;
                handles.add(
                        module.hook(method)
                                .setId("dressroom-advanced.widget-selection-" + method.getName())
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            refreshAll(true);
                                            return result;
                                        }));
                module.deoptimize(method);
            }
            try {
                Class<?> containerClass =
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.KeyguardComplicationWidgetContainer",
                                false,
                                loader);
                for (Method m : containerClass.getDeclaredMethods()) {
                    if (m.getName().equals("applyComplicationX")
                            || m.getName().equals("updateClockGravity")) {
                        handles.add(
                                module.hook(m)
                                        .setId(
                                                "dressroom-advanced.prevent-widget-snap-"
                                                        + m.getName())
                                        .intercept(
                                                chain -> {
                                                    View target = (View) chain.getThisObject();
                                                    if (WidgetPlacement.isCustomPlacement(target)) {
                                                        return null;
                                                    }
                                                    return chain.proceed();
                                                }));
                        module.deoptimize(m);
                    }
                }
            } catch (Exception | LinkageError e) {
                module.report("KeyguardComplicationWidgetContainer hook unavailable", e);
            }
            try {
                Class<?> compManagerClass =
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.ComplicationManager",
                                false,
                                loader);
                for (Method m : compManagerClass.getDeclaredMethods()) {
                    if (m.getName().equals("onClockTransitionEnded")
                            || m.getName().equals("onClockTransitionStarted")) {
                        final boolean starting = m.getName().equals("onClockTransitionStarted");
                        handles.add(
                                module.hook(m)
                                        .setId(
                                                "dressroom-advanced.prevent-widget-transition-"
                                                        + m.getName())
                                        .intercept(
                                                chain -> {
                                                    Object manager = chain.getThisObject();
                                                    ViewGroup container =
                                                            (ViewGroup)
                                                                    Reflect.field(
                                                                            manager, "container");
                                                    if (container != null
                                                            && WidgetPlacement.isCustomPlacement(
                                                                    container)) {
                                                        Reflect.call(
                                                                manager,
                                                                "updateContainerAlpha",
                                                                starting);
                                                        return null;
                                                    }
                                                    return chain.proceed();
                                                }));
                        module.deoptimize(m);
                    }
                }
            } catch (Exception | LinkageError e) {
                module.report("ComplicationManager transition hook unavailable", e);
            }
            installed.add(loader);
            android.util.Log.i(
                    DressroomModule.TAG, "Native complication controller owns runtime widgets");
        } catch (Exception | LinkageError error) {
            for (io.github.libxposed.api.XposedInterface.HookHandle handle : handles)
                handle.unhook();
            module.report("Native complication ownership hooks unavailable", error);
        }
    }

    private void refreshAll(boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> refreshAll(force));
            return;
        }
        if (force) module.widgetEngine.invalidate();
        for (Object controller : new ArrayList<>(controllers.keySet())) refresh(controller, force);
    }

    private io.github.libxposed.api.XposedInterface.HookHandle watchWallpaper(Class<?> provider)
            throws ReflectiveOperationException {
        java.lang.reflect.Field context =
                NativeContract.field(provider, "context", android.content.Context.class);
        Method changed =
                NativeContract.method(
                        provider, "updateWallpaperColorsItem", void.class, Object.class);
        var hook =
                module.hook(changed)
                        .setId("dressroom-advanced.widget-wallpaper-identity")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (chain.getArg(0) != null) {
                                        Object owner = chain.getThisObject();
                                        main.post(
                                                () -> {
                                                    try {
                                                        if (module.widgetEngine.wallpaperChanged(
                                                                (android.content.Context)
                                                                        context.get(owner)))
                                                            refreshAll(true);
                                                    } catch (Exception error) {
                                                        module.report(
                                                                "Widget wallpaper refresh"
                                                                        + " unavailable",
                                                                error);
                                                    }
                                                });
                                    }
                                    return result;
                                });
        try {
            module.deoptimize(changed);
            return hook;
        } catch (RuntimeException | Error error) {
            hook.unhook();
            throw error;
        }
    }

    private void clear(Object controller) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> clear(controller));
            return;
        }
        pending.cancel(controller);
        State state = controllers.remove(controller);
        if (state != null) state.layoutWatch.clear();
        ViewGroup root = state == null ? null : state.root.get();
        if (root != null) {
            if (legacyControllers.contains(controller.getClass())) {
                DepthEffects.applyWidgetRoot(root, false, null);
                WidgetPlacement.clear(root);
                return;
            }
            WidgetMediaOcclusion.forget(root);
            DepthEffects.applyWidgetRoot(root, false, null);
            WidgetDepthBlur.blur(root, false);
            WidgetPlacement.clear(root);
        }
    }

    private void refresh(Object controller, boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<Object> reference = new WeakReference<>(controller);
            boolean requested = force;
            main.post(
                    () -> {
                        Object value = reference.get();
                        if (value != null) refresh(value, requested);
                    });
            return;
        }
        try {
            if (legacyControllers.contains(controller.getClass())) {
                refreshLegacy(controller, force);
                return;
            }
            Object container = Reflect.field(controller, "container");
            if (!(container instanceof ViewGroup root)) return;
            Object nativeGrid = Reflect.field(controller, "complicationGridUIController");
            if (!module.widgetEngine.main(Reflect.call(nativeGrid, "getWidgetSizeManager"))) {
                clear(controller);
                return;
            }
            State state = controllers.computeIfAbsent(controller, ignored -> new State());
            int orientation = LayoutOrientation.current(root.getContext());
            if (state.orientation != orientation) {
                state.orientation = orientation;
                force = true;
            }
            if (state.root.get() != root) {
                ViewGroup previous = state.root.get();
                if (previous != null) {
                    WidgetMediaOcclusion.forget(previous);
                    DepthEffects.applyWidgetRoot(previous, false, null);
                    WidgetDepthBlur.blur(previous, false);
                    WidgetPlacement.clear(previous);
                }
                state.root = new WeakReference<>(root);
                state.loaded = false;
                watchLayout(controller, root);
            }
            WidgetMediaOcclusion.register(root);
            if (observing.compareAndSet(false, true)) {
                root.getContext()
                        .getContentResolver()
                        .registerContentObserver(
                                SettingsProvider.URI,
                                false,
                                new ContentObserver(main) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        refreshAll(true);
                                    }
                                });
            }
            Object provider = Reflect.field(controller, "keyguardClockProvider");
            Object item = Reflect.field(provider, "clockItem");
            if (item == null) return;
            int type = ((Number) Reflect.call(item, "getClockType")).intValue();
            Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
            int font = preset instanceof Number ? ((Number) preset).intValue() : 0;
            if (!force && state.loaded && state.type == type && state.font == font) return;
            Bundle query = new Bundle();
            query.putInt("type", type);
            query.putInt("font", font);
            Bundle saved = SettingsBridge.read(root.getContext(), query);
            if (force) module.widgetEngine.invalidate();
            Bundle widgets = module.widgetEngine.settings(root.getContext());
            if (widgets.getBoolean("layout") || state.layout.startsWith("true:")) {
                Object nativeData =
                        Reflect.call(Reflect.field(controller, "aodDataManager"), "get");
                Object align = Reflect.call(Reflect.field(nativeData, "G"), "getValue");
                Object grid = Reflect.field(controller, "complicationGridUIController");
                if (!widgets.getBoolean("layout"))
                    align = WidgetEngine.enumValue(align.getClass(), "Default");
                if (align != Reflect.field(grid, "widgetAlignType")) {
                    Reflect.call(grid, "changeAlignType", align);
                    try {
                        module.widgetEngine.reflow(grid);
                    } catch (Exception e) {
                        module.report("Widget reflow skipped", e);
                    }
                }
            }
            String layout =
                    widgets.getBoolean("layout")
                            + ":"
                            + widgets.getInt("columns")
                            + ":"
                            + widgets.getInt("rows")
                            + ":"
                            + widgets.getBoolean("vertical")
                            + ":"
                            + widgets.getFloat("scale", 1);
            if (!layout.equals(state.layout)) {
                state.layout = layout;
                module.widgetEngine.refreshAll();
            }
            state.type = type;
            state.font = font;
            state.loaded = true;
            boolean enabled = WidgetPreferences.depth(widgets, saved);
            DepthEffects.applyWidgetRoot(root, enabled, WidgetPreferences.mask(widgets, saved));
            WidgetDepthBlur.blur(root, widgets.getBoolean("blurOff"));
            WidgetPlacement.apply(root, widgets, provider);
        } catch (Exception error) {
            module.report("Native widget depth synchronization unavailable", error);
        }
    }

    private void refreshLegacy(Object controller, boolean force)
            throws ReflectiveOperationException {
        Object container = Reflect.field(controller, "container");
        if (!(container instanceof ViewGroup root)) return;
        Object grid = Reflect.field(controller, "complicationGridUIController");
        if (!module.widgetEngine.main(Reflect.call(grid, "getWidgetSizeManager"))) {
            clear(controller);
            return;
        }
        State state = controllers.computeIfAbsent(controller, ignored -> new State());
        int orientation = LayoutOrientation.current(root.getContext());
        if (state.orientation != orientation) {
            state.orientation = orientation;
            force = true;
        }
        if (state.root.get() != root) {
            View previous = state.root.get();
            if (previous != null) {
                DepthEffects.applyWidgetRoot(previous, false, null);
                WidgetPlacement.clear(previous);
            }
            state.root = new WeakReference<>(root);
            state.loaded = false;
            state.type = state.font = -1;
            state.depthProfile = null;
            state.depthEnabled = state.maskReady = null;
            watchLayout(controller, root);
        }
        if (observing.compareAndSet(false, true)) {
            root.getContext()
                    .getContentResolver()
                    .registerContentObserver(
                            SettingsProvider.URI,
                            false,
                            new ContentObserver(main) {
                                @Override
                                public void onChange(boolean selfChange) {
                                    refreshAll(true);
                                }
                            });
        }
        if (force) module.widgetEngine.invalidate();
        Bundle settings = module.widgetEngine.settings(root.getContext());
        boolean alignChanged = false;
        if (settings.getBoolean("layout")) {
            Object align = Reflect.field(grid, "widgetAlignType");
            // On 8.5 phones Samsung leaves the grid alignment unset until it receives
            // an explicit layout. Resolve the enum from its declaration, not that value.
            Object target = WidgetEngine.savedAlignment(grid, settings.getBoolean("vertical"));
            if (align != target) {
                Reflect.call(grid, "changeAlignType", target);
                alignChanged = true;
            }
        }
        String layout = WidgetPreferences.layoutKey(settings);
        if (!state.loaded || alignChanged || !layout.equals(state.layout)) {
            module.widgetEngine.refreshChecked(grid);
            module.widgetEngine.repairPositions(grid);
            state.layout = layout;
            state.loaded = true;
        }
        Object provider = Reflect.field(controller, "keyguardClockProvider");
        Class<?> base =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView",
                        false,
                        grid.getClass().getClassLoader());
        if (DepthBinding.provider(base)) {
            Object item = Reflect.field(provider, "clockItem");
            if (item != null) {
                int type = ((Number) Reflect.call(item, "getClockType")).intValue();
                Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
                int font = preset instanceof Number ? ((Number) preset).intValue() : 0;
                if (force
                        || type != state.type
                        || font != state.font
                        || state.depthProfile == null) {
                    Bundle query = new Bundle();
                    query.putInt("type", type);
                    query.putInt("font", font);
                    state.depthProfile = SettingsBridge.read(root.getContext(), query);
                    state.type = type;
                    state.font = font;
                }
                boolean enabled = WidgetPreferences.depth(settings, state.depthProfile);
                byte[] mask = WidgetPreferences.mask(settings, state.depthProfile);
                DepthEffects.applyWidgetRoot(root, enabled, mask);
                boolean ready = mask != null;
                if (!Boolean.valueOf(enabled).equals(state.depthEnabled)
                        || !Boolean.valueOf(ready).equals(state.maskReady)) {
                    state.depthEnabled = enabled;
                    state.maskReady = ready;
                    android.util.Log.i(
                            DressroomModule.TAG,
                            "8.5 widget depth: enabled="
                                    + enabled
                                    + " mask="
                                    + ready
                                    + " type="
                                    + type
                                    + " font="
                                    + font
                                    + " owner="
                                    + root.getClass().getSimpleName());
                }
            } else {
                // A provider can briefly have no item while replacing the wallpaper/clock.
                state.type = state.font = -1;
                state.depthProfile = null;
                state.depthEnabled = state.maskReady = false;
                DepthEffects.applyWidgetRoot(root, false, null);
            }
        }
        WidgetPlacement.apply(root, settings, provider);
    }

    private void watchLayout(Object controller, ViewGroup root) {
        WeakReference<Object> reference = new WeakReference<>(controller);
        State state = controllers.get(controller);
        state.layoutWatch.bind(
                root,
                (view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                    Object owner = reference.get();
                    State current = owner == null ? null : controllers.get(owner);
                    if (current != null
                            && current.root.get() == view
                            && current.orientation != LayoutOrientation.current(view.getContext()))
                        schedule(owner, true);
                });
    }
}
