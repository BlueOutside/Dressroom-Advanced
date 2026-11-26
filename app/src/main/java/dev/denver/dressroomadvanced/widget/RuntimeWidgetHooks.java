package dev.denver.dressroomadvanced;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class RuntimeWidgetHooks {
    private final DressroomModule module;
    private final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<Object, State> controllers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final DeferredRefresh<Object> pending =
            new DeferredRefresh<>(task -> main.post(task), this::refresh);
    private final Set<Class<?>> legacyControllers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Class<?>> earlyControllers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Class<?>, Field> alignFields =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        WeakReference<ViewGroup> root = new WeakReference<>(null);
        final RootLayoutWatch layoutWatch = new RootLayoutWatch();
        int type = -1, font = -1, orientation = -1, screen = -1;
        boolean loaded;
        boolean dozing, aodRequested;
        Bundle depthProfile;
        Boolean depthEnabled, maskReady;
        String fontPath = "";
        String layout = "";
        float scale = 1f;
    }

    RuntimeWidgetHooks(DressroomModule module) {
        this.module = module;
        ScreenIdentity.observe(() -> main.post(() -> refreshAll(true)));
    }

    synchronized void installLegacy(ClassLoader loader, boolean early) throws ReflectiveOperationException {
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
        java.util.List<Method> updates = early ? OneUi80WidgetRuntime.updates(type) : legacyUpdates(type);
        Method release = NativeContract.method(type, "release", void.class);
        try (HookTransaction hooks = new HookTransaction()) {
            for (Method method : updates) {
                String name = method.getName();
                boolean hasArg = method.getParameterCount() > 0;
                var update =
                        module.hook(method)
                                .setId("dressroom-advanced.85-widget-runtime-" + name)
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            trackAodState(
                                                    chain.getThisObject(),
                                                    name,
                                                    hasArg ? chain.getArg(0) : null);
                                            schedule(
                                                    chain.getThisObject(),
                                                    !name.equals("setDarkAmount"));
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
            installPlacementGuards(loader, type, hooks);
            hooks.commit();
            legacyControllers.add(type);
            if (early) earlyControllers.add(type);
            installed.add(loader);
            WidgetClickBlock.install(module, loader);
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
        if (!needsRefresh(controller, force)) return;
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

    private boolean needsRefresh(Object controller, boolean force) {
        if (force || controller == null) return true;
        State state = controllers.get(controller);
        if (state == null || !state.loaded) return true;
        ViewGroup root = state.root.get();
        if (root == null) return true;
        try {
            if (Reflect.field(controller, "container") != root) return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return true;
        }
        return state.orientation != LayoutOrientation.current(root.getContext())
                || state.screen != ScreenIdentity.current();
    }

    synchronized void install(ClassLoader loader) {
        if (installed.contains(loader)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> controller =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController",
                            false,
                            loader);
            boolean tracksCompletion = PlatformGate.oneUiVersion() >= 90500
                    && installLayoutCompletion(controller, hooks);
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isBridge()) continue;
                String name = method.getName();
                if (tracksCompletion && (name.equals("refreshComplicationGridAndWidgets")
                        || name.equals("updateWidgetLayout"))) continue;
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
                boolean hasArg = method.getParameterCount() > 0;
                hooks.add(
                        module.hook(method)
                                .setId("dressroom-advanced.widget-owner-" + name)
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            trackAodState(
                                                    chain.getThisObject(),
                                                    name,
                                                    hasArg ? chain.getArg(0) : null);
                                            refresh(chain.getThisObject(), force);
                                            return result;
                                        })
                                ::unhook);
                module.deoptimize(method);
            }
            Method release = controller.getDeclaredMethod("release");
            hooks.add(
                    module.hook(release)
                            .setId("dressroom-advanced.widget-owner-release")
                            .intercept(
                                    chain -> {
                                        clear(chain.getThisObject());
                                        return chain.proceed();
                                    })
                            ::unhook);
            module.deoptimize(release);
            Class<?> provider =
                    Class.forName(
                            "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                            false,
                            loader);
            try {
                hooks.add(watchWallpaper(provider)::unhook);
            } catch (Exception | LinkageError unavailable) {
                module.report("Widget wallpaper identity watch unavailable", unavailable);
            }
            for (Method method : provider.getDeclaredMethods()) {
                if (!method.getName().equals("onClockTypeChanged")
                        && !method.getName().equals("refreshClockFontDataInternal")) continue;
                hooks.add(
                        module.hook(method)
                                .setId("dressroom-advanced.widget-selection-" + method.getName())
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            refreshAll(true);
                                            return result;
                                        })
                                ::unhook);
                module.deoptimize(method);
            }
            installPlacementGuards(loader, controller, hooks);
            hooks.commit();
            installed.add(loader);
            WidgetClickBlock.install(module, loader);
            android.util.Log.i(
                    DressroomModule.TAG, "Native complication controller owns runtime widgets");
        } catch (Exception | LinkageError error) {
            module.report("Native complication ownership hooks unavailable", error);
        }
    }

    private void installPlacementGuards(
            ClassLoader loader, Class<?> controller, HookTransaction hooks) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.isBridge() || !method.getName().equals("updateClockGravity")) continue;
            hooks.add(
                    module.hook(method)
                            .setId("dressroom-advanced.prevent-plugin-gravity-reset")
                            .intercept(
                                    chain -> {
                                        Object controllerObj = chain.getThisObject();
                                        ViewGroup container =
                                                (ViewGroup)
                                                        Reflect.field(controllerObj, "container");
                                        if (container != null
                                                && WidgetPlacement.isCustomPlacement(container)) {
                                            return null;
                                        }
                                        return chain.proceed();
                                    })
                            ::unhook);
            module.deoptimize(method);
        }
        try (HookTransaction optional = new HookTransaction()) {
            Class<?> containerClass =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.KeyguardComplicationWidgetContainer",
                            false,
                            loader);
            for (Method m : containerClass.getDeclaredMethods()) {
                if (m.getName().equals("applyComplicationX")
                        || m.getName().equals("updateClockGravity")) {
                    optional.add(
                            module.hook(m)
                                    .setId("dressroom-advanced.prevent-widget-snap-" + m.getName())
                                    .intercept(
                                            chain -> {
                                                View target = (View) chain.getThisObject();
                                                if (WidgetPlacement.isCustomPlacement(target)) {
                                                    return null;
                                                }
                                                return chain.proceed();
                                            })
                                    ::unhook);
                    module.deoptimize(m);
                }
            }
            optional.commitTo(hooks);
        } catch (Exception | LinkageError e) {
            module.report("KeyguardComplicationWidgetContainer hook unavailable", e);
        }
        try (HookTransaction optional = new HookTransaction()) {
            Class<?> compManagerClass =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationManager",
                            false,
                            loader);
            for (Method m : compManagerClass.getDeclaredMethods()) {
                if (m.getName().equals("onClockTransitionEnded")
                        || m.getName().equals("onClockTransitionStarted")) {
                    final boolean starting = m.getName().equals("onClockTransitionStarted");
                    optional.add(
                            module.hook(m)
                                    .setId(
                                            "dressroom-advanced.prevent-widget-transition-"
                                                    + m.getName())
                                    .intercept(
                                            chain -> {
                                                Object manager = chain.getThisObject();
                                                ViewGroup container =
                                                        (ViewGroup)
                                                                Reflect.field(manager, "container");
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
                                            })
                                    ::unhook);
                    module.deoptimize(m);
                }
            }
            optional.commitTo(hooks);
        } catch (Exception | LinkageError e) {
            module.report("ComplicationManager transition hook unavailable", e);
        }
    }

    private static void syncParentHeight(Object controller, ViewGroup root) {
        if (!(root.getParent() instanceof ViewGroup parent)) return;
        ViewGroup.LayoutParams params = parent.getLayoutParams();
        if (params == null || params.height <= 0) return;
        try {
            int height =
                    ((Number) Reflect.call(controller, "getHeight", root.getContext())).intValue();
            if (height > 0 && params.height != height) {
                params.height = height;
                parent.setLayoutParams(params);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean installLayoutCompletion(Class<?> controller, HookTransaction hooks) {
        try (HookTransaction optional = new HookTransaction()) {
            var layout = OneUi95WidgetRuntime.layout(controller);
            for (Method method : List.of(layout.applyLayout(), layout.refresh())) {
                optional.add(module.hook(method)
                        .setId("dressroom-advanced.widget-layout-completed-" + method.getName())
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            // The coroutine is called again when layout resumes. Only Unit
                            // signals that the replacement widget tree is ready.
                            if (layout.unit().isInstance(result)) schedule(chain.getThisObject(), true);
                            return result;
                        })::unhook);
                module.deoptimize(method);
            }
            for (Method caller : layout.callers()) module.deoptimize(caller);
            optional.commitTo(hooks);
            return true;
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            module.report("Optional widget layout completion unavailable", error);
            return false;
        }
    }

    void wallpaperReplaced() {
        refreshAll(true);
    }

    private void refreshAll(boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> refreshAll(force));
            return;
        }
        if (force) module.widgetEngine.invalidate();
        List<Object> current;
        synchronized (controllers) {
            current = new ArrayList<>(controllers.keySet());
        }
        for (Object controller : current) refresh(controller, force);
    }

    private io.github.libxposed.api.XposedInterface.HookHandle watchWallpaper(Class<?> provider)
            throws ReflectiveOperationException {
        java.lang.reflect.Field context =
                NativeContract.field(provider, "context", android.content.Context.class);
        Method changed;
        int argIndex;
        try {
            changed =
                    NativeContract.method(
                            provider, "updateWallpaperColorsItem", void.class, Object.class);
            argIndex = 0;
        } catch (NoSuchMethodException removedInBeta) {
            Class<?> localColor =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.WallpaperLocalColor",
                            false,
                            provider.getClassLoader());
            changed =
                    NativeContract.method(
                            provider, "updateClockColorData", void.class, int.class, localColor);
            argIndex = 1;
        }
        final int valueArg = argIndex;
        var hook =
                module.hook(changed)
                        .setId("dressroom-advanced.widget-wallpaper-identity")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (chain.getArg(valueArg) != null) {
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

    static Object resolveControllerMember(
            Object controller, String directName, String providerName)
            throws ReflectiveOperationException {
        try {
            return widgetMember(controller, directName);
        } catch (NoSuchFieldException migrated) {
            Object provider = Reflect.field(controller, providerName);
            return provider == null ? null : Reflect.call(provider, "get");
        }
    }

    static Object widgetMember(Object owner, String name) throws ReflectiveOperationException {
        var field = Reflect.findField(owner.getClass(), name);
        Object value = field.get(owner);
        return value != null && field.getType().getName().equals("javax.inject.Provider")
                ? field.getType().getMethod("get").invoke(value) : value;
    }

    static Object resolveControllerProvider(
            Object controller, String directName, String providerName)
            throws ReflectiveOperationException {
        Object provider;
        try {
            provider = Reflect.field(controller, directName);
        } catch (NoSuchFieldException migrated) {
            provider = Reflect.field(controller, providerName);
        }
        return provider == null ? null : Reflect.call(provider, "get");
    }

    private void trackAodState(Object controller, String method, Object arg) {
        if (controller == null) return;
        State state = controllers.computeIfAbsent(controller, ignored -> new State());
        boolean changed = false;
        if (method.equals("setDozing") && arg instanceof Boolean value) {
            changed = state.dozing != value;
            state.dozing = value;
        } else if (method.equals("requestAODState") && arg instanceof Boolean value) {
            changed = state.aodRequested != value;
            state.aodRequested = value;
        } else if (method.equals("onFinishedWakingUp")) {
            changed = state.dozing || state.aodRequested;
            state.dozing = false;
            state.aodRequested = false;
        }
        if (changed) {
            ViewGroup root = state.root.get();
            if (root != null) {
                boolean aod = state.dozing || state.aodRequested;
                if (Looper.myLooper() == Looper.getMainLooper()) DepthEffects.setDozing(root, aod);
                else main.post(() -> DepthEffects.setDozing(root, aod));
            }
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
            DepthEffects.setDozing(root, false);
            WidgetMediaOcclusion.forget(root);
            DepthEffects.applyWidgetRoot(root, false, null);
            WidgetDepthBlur.blur(root, false);
            WidgetPlacement.clear(root);
        }
    }

    private void refresh(Object controller, boolean force) {
        if (!needsRefresh(controller, force)) return;
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
            Object nativeGrid =
                    resolveControllerMember(
                            controller,
                            "complicationGridUIController",
                            "complicationGridUIControllerProvider");
            if (nativeGrid == null) return;
            if (!module.widgetEngine.main(Reflect.call(nativeGrid, "getWidgetSizeManager"))) {
                clear(controller);
                return;
            }
            WidgetClickBlock.observeRoot(module, root);
            State state = controllers.computeIfAbsent(controller, ignored -> new State());
            int orientation = LayoutOrientation.current(root.getContext());
            int screen = ScreenIdentity.current();
            if (state.orientation != orientation || state.screen != screen) {
                state.screen = screen;
                state.orientation = orientation;
                force = true;
            }
            if (state.root.get() != root) {
                ViewGroup previous = state.root.get();
                if (previous != null) {
                    DepthEffects.setDozing(previous, false);
                    WidgetMediaOcclusion.forget(previous);
                    DepthEffects.applyWidgetRoot(previous, false, null);
                    WidgetDepthBlur.blur(previous, false);
                    WidgetPlacement.clear(previous);
                }
                state.root = new WeakReference<>(root);
                state.loaded = false;
                state.type = state.font = -1;
                state.fontPath = "";
                state.depthProfile = null;
                watchLayout(controller, root);
            }
            WidgetMediaOcclusion.register(root);
            if (observing.compareAndSet(false, true)) {
                try {
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
                } catch (RuntimeException error) {
                    observing.set(false);
                    throw error;
                }
            }
            if (force) module.widgetEngine.invalidate();
            Bundle widgets = module.widgetEngine.settings(root.getContext());
            boolean alignChanged = false;
            if (widgets.getBoolean("layout") || state.layout.startsWith("true:")) {
                Object nativeData =
                        resolveControllerProvider(
                                controller, "aodDataManager", "aodDataManagerProvider");
                if (nativeData != null) {
                    Object align =
                            nativeAlignment(nativeData, nativeData.getClass().getClassLoader());
                    Object grid = nativeGrid;
                    if (!widgets.getBoolean("layout"))
                        align = WidgetEngine.enumValue(align.getClass(), "Default");
                    if (align != Reflect.field(grid, "widgetAlignType")) {
                        Reflect.call(grid, "changeAlignType", align);
                        alignChanged = true;
                        try {
                            module.widgetEngine.reflow(grid);
                        } catch (Exception e) {
                            module.report("Widget reflow skipped", e);
                        }
                    }
                }
            }
            String layout = WidgetPreferences.layoutKey(widgets);
            if (!state.loaded || alignChanged || !layout.equals(state.layout)) {
                state.layout = layout;
                module.widgetEngine.refreshChecked(nativeGrid);
                module.widgetEngine.repairPositions(nativeGrid);
            }
            syncParentHeight(controller, root);
            Object provider =
                    resolveControllerMember(
                            controller, "keyguardClockProvider", "keyguardClockProviderProvider");
            Object item = provider == null ? null : Reflect.field(provider, "clockItem");
            if (item != null) {
                int type = ((Number) Reflect.call(item, "getClockType")).intValue();
                Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
                int font = preset instanceof Number ? ((Number) preset).intValue() : 0;
                String path = "";
                try {
                    if (Reflect.call(provider, "getClockFontPath") instanceof String fontPath
                            && !fontPath.isEmpty()) {
                        path = fontPath;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
                if (force
                        || !state.loaded
                        || state.type != type
                        || state.font != font
                        || !path.equals(state.fontPath)
                        || state.depthProfile == null) {
                    Bundle query = new Bundle();
                    query.putInt("type", type);
                    query.putInt("font", font);
                    if (!path.isEmpty()) query.putString("fontPath", path);
                    state.depthProfile = SettingsBridge.readRuntimeClock(root.getContext(), query);
                    state.type = type;
                    state.font = font;
                    state.fontPath = path;
                }
                state.loaded = true;
                boolean enabled = WidgetPreferences.depth(widgets, state.depthProfile);
                boolean hideOnAod = WidgetPreferences.depthOffAod(widgets, state.depthProfile);
                DepthEffects.applyWidgetRoot(
                        root, enabled, WidgetPreferences.mask(widgets, state.depthProfile), hideOnAod);
            } else {
                state.type = state.font = -1;
                state.fontPath = "";
                state.depthProfile = null;
                DepthEffects.applyWidgetRoot(root, false, null);
            }
            DepthEffects.setDozing(root, state.dozing || state.aodRequested);
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
        WidgetClickBlock.observeRoot(module, root);
        State state = controllers.computeIfAbsent(controller, ignored -> new State());
        int orientation = LayoutOrientation.current(root.getContext());
        int screen = ScreenIdentity.current();
        if (state.orientation != orientation || state.screen != screen) {
            state.screen = screen;
            state.orientation = orientation;
            force = true;
        }
        if (state.root.get() != root) {
            View previous = state.root.get();
            if (previous != null) {
                DepthEffects.setDozing(previous, false);
                WidgetMediaOcclusion.forget(previous);
                DepthEffects.applyWidgetRoot(previous, false, null);
                WidgetDepthBlur.blur(previous, false);
                WidgetPlacement.clear(previous);
            }
            state.root = new WeakReference<>(root);
            state.loaded = false;
            state.type = state.font = -1;
            state.depthProfile = null;
            state.depthEnabled = state.maskReady = null;
            state.scale = 1f;
            watchLayout(controller, root);
        }
        WidgetMediaOcclusion.register(root);
        if (observing.compareAndSet(false, true)) {
            try {
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
            } catch (RuntimeException error) {
                observing.set(false);
                throw error;
            }
        }
        if (force) module.widgetEngine.invalidate();
        Bundle settings = module.widgetEngine.settings(root.getContext());
        if (earlyControllers.contains(controller.getClass()) && !module.widgetEngine.earlyCapacity(grid)) {
            float scale = settings.getFloat("scale", 1f);
            if (state.scale != scale && module.widgetEngine.earlyScaling(grid)) {
                float previousScale = state.scale;
                state.scale = scale;
                try {
                    module.widgetEngine.refreshChecked(grid);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    state.scale = previousScale;
                    module.report("8.0 saved widget scale unavailable", error);
                }
            }
        } else {
            boolean alignChanged = false;
            if (settings.getBoolean("layout") || state.layout.startsWith("true:")) {
                Object align = Reflect.field(grid, "widgetAlignType");
                // The grid alignment can be null until Samsung receives an explicit layout.
                Object target =
                        WidgetEngine.savedAlignment(
                                grid,
                                settings.getBoolean("layout") && settings.getBoolean("vertical"));
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
            }
        }
        syncParentHeight(controller, root);
        Object provider = Reflect.field(controller, "keyguardClockProvider");
        Class<?> base =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView",
                        false,
                        grid.getClass().getClassLoader());
        boolean depthLoaded = true;
        if (DepthBinding.provider(base)) {
            boolean early = earlyControllers.contains(controller.getClass());
            Object item = early ? null : Reflect.field(provider, "clockItem");
            depthLoaded = early || item != null;
            if (depthLoaded) {
                Bundle query = early ? OneUi80WidgetRuntime.clockIdentity(provider) : new Bundle();
                if (!early) {
                    query.putInt("type", ((Number) Reflect.call(item, "getClockType")).intValue());
                    Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
                    query.putInt("font", preset instanceof Number ? ((Number) preset).intValue() : 0);
                    try {
                        if (Reflect.call(provider, "getClockFontPath") instanceof String fontPath
                                && !fontPath.isEmpty()) {
                            query.putString("fontPath", fontPath);
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
                int type = query.getInt("type"), font = query.getInt("font");
                String path = query.getString("fontPath", "");
                if (force
                        || type != state.type
                        || font != state.font
                        || !path.equals(state.fontPath)
                        || state.depthProfile == null) {
                    // Read the committed profile with the runtime clock's identity.
                    state.depthProfile = SettingsBridge.readRuntimeClock(root.getContext(), query);
                    state.type = type;
                    state.font = font;
                    state.fontPath = path;
                }
                boolean enabled = WidgetPreferences.depth(settings, state.depthProfile);
                boolean hideOnAod = WidgetPreferences.depthOffAod(settings, state.depthProfile);
                byte[] mask = WidgetPreferences.mask(settings, state.depthProfile);
                DepthEffects.applyWidgetRoot(root, enabled, mask, hideOnAod);
                DepthEffects.setDozing(root, state.dozing || state.aodRequested);
                boolean ready = mask != null;
                if (!Boolean.valueOf(enabled).equals(state.depthEnabled)
                        || !Boolean.valueOf(ready).equals(state.maskReady)) {
                    state.depthEnabled = enabled;
                    state.maskReady = ready;
                    android.util.Log.i(
                            DressroomModule.TAG,
                            "Legacy widget depth: enabled="
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
        state.loaded = depthLoaded;
        WidgetDepthBlur.blur(root, settings.getBoolean("blurOff"));
        WidgetPlacement.apply(root, settings, provider, earlyControllers.contains(controller.getClass()));
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
                            && (current.orientation != LayoutOrientation.current(view.getContext())
                                    || current.screen != ScreenIdentity.current()))
                        schedule(owner, true);
                });
    }

    static Object nativeAlignment(Object nativeData, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> type = nativeData.getClass();
        Field cached = alignFields.get(type);
        if (cached != null) {
            Method getter = zeroArgMethod(cached.get(nativeData), "getValue");
            if (getter != null) return getter.invoke(cached.get(nativeData));
        }
        Class<?> alignType =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                        false,
                        loader);
        Field match = null;
        Object matchValue = null;
        for (Class<?> cursor = type;
                cursor != null && cursor != Object.class;
                cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object holder = field.get(nativeData);
                Method getter = zeroArgMethod(holder, "getValue");
                if (getter == null) continue;
                Object value = getter.invoke(holder);
                if (!alignType.isInstance(value)) continue;
                if (match != null) {
                    throw new NoSuchFieldException(
                            "Ambiguous WidgetAlignType flow field on "
                                    + type.getName()
                                    + ": "
                                    + match.getName()
                                    + ", "
                                    + field.getName());
                }
                match = field;
                matchValue = value;
            }
        }
        if (match == null) {
            throw new NoSuchFieldException("No WidgetAlignType flow field on " + type.getName());
        }
        alignFields.put(type, match);
        return matchValue;
    }

    private static Method zeroArgMethod(Object target, String name) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
