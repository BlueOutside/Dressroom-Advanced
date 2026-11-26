package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class WidgetEngine {
    static final String GRID =
            "com.samsung.android.app.facewidget.complication.ComplicationGridUIController";
    static final String SIZE =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetSizeManager";
    private final DressroomModule module;
    private int runtimeScreen;
    private final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Object> grids =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final ThreadLocal<Boolean> nativeMetrics = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Object> nativeArrangement = new ThreadLocal<>();
    private Bundle runtime;
    private int runtimeOrientation;
    private int runtimeWallpaper = -1;
    private Context context;
    private boolean refreshing;

    private record GridRefresh(Method method, boolean forceArgument) {}

    private static final ClassValue<GridRefresh> refreshBindings =
            new ClassValue<>() {
                @Override
                protected GridRefresh computeValue(Class<?> type) {
                    try {
                        try {
                            return new GridRefresh(
                                    NativeContract.method(
                                            type,
                                            "reconfigureGridLayout",
                                            void.class,
                                            boolean.class),
                                    true);
                        } catch (NoSuchMethodException ignored) {
                            return new GridRefresh(
                                    NativeContract.method(
                                            type, "reconfigureGridLayout", void.class),
                                    false);
                        }
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported native grid refresh", error);
                    }
                }
            };
    private final Set<ClassLoader> legacyProviders =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<ClassLoader> scaleInstalled =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<ClassLoader> earlyScale =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<ClassLoader> earlyGrid =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final java.util.Map<Object, Boolean> mainDisplays =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Map<Object, Context> managerContexts =
            Collections.synchronizedMap(new WeakHashMap<>());

    WidgetEngine(DressroomModule module) {
        this.module = module;
    }

    Bundle settings(Context c) {
        if (module.editorWidgets != null) return module.editorWidgets.settings(c);
        int orientation = LayoutOrientation.current(c);
        int screen = ScreenIdentity.current();
        if (runtime == null || runtimeOrientation != orientation || runtimeScreen != screen) {
            runtime = WidgetPreferences.read(c, "");
            runtimeOrientation = orientation;
            runtimeScreen = screen;
            runtimeWallpaper = runtime.getInt("wallpaperId", -1);
        }
        return runtime;
    }

    void invalidate() {
        runtime = null;
    }

    boolean wallpaperChanged(Context c) {
        if (runtime == null || SettingsBridge.wallpaperId(c) == runtimeWallpaper) return false;
        invalidate();
        return true;
    }

    boolean main(Object manager) {
        if (manager == null) return false;
        try {
            Object display = Reflect.call(manager, "getWidgetDisplayType");
            if (display == null) return false;
            Boolean cached = mainDisplays.get(display);
            if (cached != null) return cached;
            boolean isMain =
                    !(Boolean) Reflect.call(display, "isCover")
                            && !(Boolean) Reflect.call(display, "isInformative");
            mainDisplays.put(display, isMain);
            return isMain;
        } catch (Exception e) {
            return false;
        }
    }

    private Bundle config(Object manager) throws ReflectiveOperationException {
        Context c = managerContexts.get(manager);
        if (c == null) {
            c =
                    (Context)
                            RuntimeWidgetHooks.resolveControllerMember(
                                    manager, "context", "contextProvider");
            if (c != null) managerContexts.put(manager, c);
        }
        return settings(c);
    }

    synchronized boolean installLegacyCapacity(ClassLoader loader)
            throws ReflectiveOperationException {
        legacyProviders.add(loader);
        if (installed.contains(loader)) return true;
        if (!scaleInstalled.contains(loader)) install(loader);
        if (installed.contains(loader)) return true;
        installScale(loader, false);
        return false;
    }

    boolean earlyScaling(Object grid) {
        return earlyScale.contains(grid.getClass().getClassLoader());
    }

    boolean earlyCapacity(Object grid) {
        return earlyGrid.contains(grid.getClass().getClassLoader());
    }

    synchronized boolean installEarlyCapacity(ClassLoader loader) throws ReflectiveOperationException {
        legacyProviders.add(loader);
        if (!installed.contains(loader) && !scaleInstalled.contains(loader)) install(loader, true);
        if (!installed.contains(loader)) installScale(loader, true);
        return earlyGrid.contains(loader);
    }

    synchronized void installScale(ClassLoader loader, boolean early)
            throws ReflectiveOperationException {
        if (scaleInstalled.contains(loader) || installed.contains(loader)) return;
        Class<?> manager = Class.forName(SIZE, false, loader);
        NativeContract.field(manager, "_scaleFactor", float.class);
        Class<?> display =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetDisplayType",
                        false,
                        loader);
        Method init = NativeContract.method(manager, "init", void.class, display);
        Method padding = NativeContract.method(manager, "getPadding", float.class);
        Class<?> grid = Class.forName(GRID, false, loader);
        Method bind =
                NativeContract.method(
                        grid,
                        "setRootContainer",
                        void.class,
                        android.view.ViewGroup.class,
                        String.class,
                        display);
        refreshBindings.get(grid);
        validateLegacyGrid(grid);
        Method earlyHost = early ? OneUi80WidgetRuntime.addHost(loader) : null;
        try (HookTransaction hooks = new HookTransaction()) {
            var scale =
                    module.hook(init)
                            .setId("dressroom-advanced.85-widget-scale")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        Object instance = chain.getThisObject();
                                        if (main(instance)) {
                                            float nativeScale =
                                                    ((Number)
                                                                    Reflect.field(
                                                                            instance,
                                                                            "_scaleFactor"))
                                                            .floatValue();
                                            Reflect.set(
                                                    instance,
                                                    "_scaleFactor",
                                                    nativeScale
                                                            * config(instance)
                                                                    .getFloat("scale", 1));
                                        }
                                        return result;
                                    });
            hooks.add(scale::unhook);
            module.deoptimize(init);
            var pad =
                    module.hook(padding)
                            .setId("dressroom-advanced.85-widget-padding-scale")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        Object instance = chain.getThisObject();
                                        if (!nativeMetrics.get()
                                                && !WidgetHostSizing.unscaled.get()
                                                && result instanceof Number value
                                                && main(instance)) {
                                            float zoom = config(instance).getFloat("scale", 1f);
                                            if (zoom != 1f) return value.floatValue() * zoom;
                                        }
                                        return result;
                                    });
            hooks.add(pad::unhook);
            module.deoptimize(padding);
            var root =
                    module.hook(bind)
                            .setId("dressroom-advanced.85-widget-owner")
                            .intercept(
                                    chain -> {
                                        grids.add(chain.getThisObject());
                                        if (chain.getArg(0) instanceof View view)
                                            context = view.getContext().getApplicationContext();
                                        return chain.proceed();
                                    });
            hooks.add(root::unhook);
            module.deoptimize(bind);
            installHostSizing(loader, earlyHost, handle -> hooks.add(handle::unhook));
            hooks.commit();
            scaleInstalled.add(loader);
            if (early) earlyScale.add(loader);
        }
    }

    synchronized void install(ClassLoader loader) {
        install(loader, false);
    }

    private void install(ClassLoader loader, boolean early) {
        if (installed.contains(loader)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> size = Class.forName(SIZE, false, loader);
            Class<?> alignType =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                            false,
                            loader);
            for (String name : new String[] {"getColumn", "getRow", "isBiggerSize"}) {
                Method m = size.getDeclaredMethod(name);
                hooks.add(
                        module.hook(m)
                                .setId("dressroom-advanced.widget-" + name)
                                .intercept(
                                        chain -> {
                                            if (!nativeMetrics.get()
                                                    && main(chain.getThisObject())) {
                                                Bundle b = config(chain.getThisObject());
                                                if (b.getBoolean("layout")) {
                                                    if (name.equals("isBiggerSize")) return true;
                                                    boolean vertical =
                                                            isVertical(
                                                                    Reflect.field(
                                                                            chain.getThisObject(),
                                                                            "widgetAlignType"));
                                                    int[] dimensions =
                                                            WidgetLayout.dimensions(
                                                                    b.getInt("columns"),
                                                                    b.getInt("rows"),
                                                                    b.getBoolean("vertical"),
                                                                    vertical);
                                                    return dimensions[
                                                            name.equals("getColumn") ? 0 : 1];
                                                }
                                            }
                                            return chain.proceed();
                                        })
                                ::unhook);
                module.deoptimize(m);
            }
            Method padding = NativeContract.method(size, "getPadding", float.class);
            hooks.add(
                    module.hook(padding)
                            .setId("dressroom-advanced.widget-padding-scale")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        Object instance = chain.getThisObject();
                                        if (!nativeMetrics.get()
                                                && !WidgetHostSizing.unscaled.get()
                                                && result instanceof Number value
                                                && main(instance)) {
                                            float zoom = config(instance).getFloat("scale", 1f);
                                            if (zoom != 1f) return value.floatValue() * zoom;
                                        }
                                        return result;
                                    })
                            ::unhook);
            module.deoptimize(padding);
            for (Method m : size.getDeclaredMethods())
                if (m.getName().equals("init") && m.getParameterCount() == 1) {
                    hooks.add(
                            module.hook(m)
                                    .setId("dressroom-advanced.widget-cell-scale")
                                    .intercept(
                                            chain -> {
                                                boolean old = nativeMetrics.get();
                                                nativeMetrics.set(true);
                                                Object manager = chain.getThisObject();
                                                Object align = null;
                                                boolean restoreAlign = false;
                                                try {
                                                    if (main(manager)
                                                            && config(manager)
                                                                    .getBoolean("layout")) {
                                                        align =
                                                                Reflect.field(
                                                                        manager, "widgetAlignType");
                                                        restoreAlign = true;
                                                        Reflect.set(
                                                                manager,
                                                                "widgetAlignType",
                                                                enumValue(alignType, "Default"));
                                                    }
                                                    Object result = chain.proceed();
                                                    if (main(chain.getThisObject())) {
                                                        Bundle b = config(chain.getThisObject());
                                                        float base =
                                                                ((Number)
                                                                                Reflect.field(
                                                                                        chain
                                                                                                .getThisObject(),
                                                                                        "_scaleFactor"))
                                                                        .floatValue();
                                                        Reflect.set(
                                                                chain.getThisObject(),
                                                                "_scaleFactor",
                                                                base * b.getFloat("scale", 1));
                                                    }
                                                    return result;
                                                } finally {
                                                    if (restoreAlign)
                                                        Reflect.set(
                                                                manager, "widgetAlignType", align);
                                                    nativeMetrics.set(old);
                                                }
                                            })
                                    ::unhook);
                    module.deoptimize(m);
                }
            Class<?> grid = Class.forName(GRID, false, loader);
            OneUi80WidgetRuntime.grid(grid);
            Method largeScreen = NativeContract.method(grid, "isLargeScreenWidget", boolean.class);
            hooks.add(
                    module.hook(largeScreen)
                            .setId("dressroom-advanced.widget-large-screen-grid")
                            .intercept(
                                    chain -> {
                                        Object manager =
                                                Reflect.call(
                                                        chain.getThisObject(),
                                                        "getWidgetSizeManager");
                                        if (!nativeMetrics.get()
                                                && main(manager)
                                                && config(manager).getBoolean("layout")) {
                                            return true;
                                        }
                                        return chain.proceed();
                                    })
                            ::unhook);
            module.deoptimize(largeScreen);
            if (!early && module.editorWidgets == null) {
                boolean legacy;
                try {
                    legacy =
                            ClockBinding.of(
                                            Class.forName(
                                                    "com.samsung.android.uniform.widget.clock.AbsClockView",
                                                    false,
                                                    loader))
                                    .legacy;
                } catch (ReflectiveOperationException | RuntimeException unavailable) {
                    legacy = false;
                }
                final boolean legacyOrientation = legacy;
                Class<?> viewModel =
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.KeyguardComplicationViewModel",
                                false,
                                loader);
                Method update =
                        viewModel.getDeclaredMethod("updateGridLayout", alignType, boolean.class);
                hooks.add(
                        module.hook(update)
                                .setId("dressroom-advanced.native-widget-arrangement-source")
                                .intercept(
                                        chain -> {
                                            Object previous = nativeArrangement.get();
                                            nativeArrangement.set(chain.getArg(0));
                                            try {
                                                return chain.proceed();
                                            } finally {
                                                if (previous == null) nativeArrangement.remove();
                                                else nativeArrangement.set(previous);
                                            }
                                        })
                                ::unhook);
                module.deoptimize(update);
                Class<?> layouts =
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.WidgetLayoutManager",
                                false,
                                loader);
                Method refresh = layouts.getDeclaredMethod("refreshGridLayout", alignType);
                hooks.add(
                        module.hook(refresh)
                                .setId("dressroom-advanced.runtime-widget-arrangement-capability")
                                .intercept(
                                        chain -> {
                                            Object controller =
                                                    RuntimeWidgetHooks.widgetMember(
                                                            chain.getThisObject(),
                                                            "complicationGridUIController");
                                            Object manager =
                                                    Reflect.call(
                                                            controller, "getWidgetSizeManager");
                                            if (!main(manager)
                                                    || !config(manager).getBoolean("layout"))
                                                return chain.proceed();
                                            Object requested = nativeArrangement.get();
                                            Object targetAlign =
                                                    requested == null ? chain.getArg(0) : requested;
                                            if (legacyOrientation)
                                                targetAlign =
                                                        enumValue(
                                                                alignType,
                                                                config(manager)
                                                                                .getBoolean(
                                                                                        "vertical")
                                                                        ? "Vertical"
                                                                        : "Default");
                                            Reflect.call(
                                                    controller, "changeAlignType", targetAlign);
                                            reflow(controller);
                                            return null;
                                        })
                                ::unhook);
                module.deoptimize(refresh);
            }
            Method rotate = grid.getDeclaredMethod("onclickChangeWidgetAlign", alignType);
            hooks.add(
                    module.hook(rotate)
                            .setId("dressroom-advanced.native-widget-reflow")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        Object manager =
                                                Reflect.call(
                                                        chain.getThisObject(),
                                                        "getWidgetSizeManager");
                                        if (main(manager) && config(manager).getBoolean("layout"))
                                            reflow(chain.getThisObject());
                                        return result;
                                    })
                            ::unhook);
            module.deoptimize(rotate);
            for (Method m : grid.getDeclaredMethods())
                if (java.util.Set.of(
                                "getLockWidgetGridSize",
                                "updateGridRule",
                                "initDynamicGridLayout",
                                "updateAlignChangeLayout",
                                "updateRootContainerSize",
                                "checkAllowPosition",
                                "animateOffsetX",
                                "registerDropListener",
                                "sendAppWidgetOptions")
                        .contains(m.getName())) module.deoptimize(m);
            if (!early) {
                Class<?> containers = Class.forName(
                        "com.samsung.android.app.facewidget.complication.ComplicationContainerManager",
                        false, loader);
                for (Method m : containers.getDeclaredMethods())
                    if (m.getName().equals("getContainerWidth")
                            || m.getName().equals("getContainerHeight")) module.deoptimize(m);
            }
            for (Method m : grid.getDeclaredMethods())
                if (m.getName().equals("setRootContainer") && m.getParameterCount() == 3) {
                    hooks.add(
                            module.hook(m)
                                    .setId("dressroom-advanced.widget-grid-owner")
                                    .intercept(
                                            chain -> {
                                                grids.add(chain.getThisObject());
                                                if (chain.getArg(0) instanceof View view) {
                                                    context =
                                                            view.getContext()
                                                                    .getApplicationContext();
                                                    settings(context);
                                                }
                                                Object result = chain.proceed();
                                                return result;
                                            })
                                    ::unhook);
                    module.deoptimize(m);
                }
            installHostSizing(loader, early ? OneUi80WidgetRuntime.addHost(loader) : null,
                    handle -> hooks.add(handle::unhook));
            if (early) validateLegacyGrid(grid);
            if (early) OneUi80WidgetRuntime.grid(grid);
            hooks.commit();
            installed.add(loader);
            if (early) {
                earlyScale.add(loader);
                earlyGrid.add(loader);
            }
            if (!early) WidgetDepthBlur.install(module, loader);
            if (!early && module.inEditorProcess()) {
                try {
                    EditorWidgetBlur.install(module, grid);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("Optional editor widget blur unavailable", error);
                }
            }
            android.util.Log.i(
                    DressroomModule.TAG,
                    "Widget grid capacity, native rules and cell metrics hooks installed");
        } catch (Exception | LinkageError e) {
            module.report("Widget grid hooks unavailable", e);
        }
    }

    private void installHostSizing(
            ClassLoader loader,
            Method earlyHost,
            java.util.function.Consumer<io.github.libxposed.api.XposedInterface.HookHandle> own)
            throws ReflectiveOperationException {
        WidgetHostSizing.validateResize(loader);
        if (earlyHost == null) {
            installFactoryHostSizing(loader, own);
        } else {
            own.accept(module.hook(earlyHost)
                    .setId("dressroom-advanced.80-widget-host-scale")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object manager = Reflect.call(chain.getThisObject(), "getWidgetSizeManager");
                            if (!main(manager)) return result;
                            float zoom = config(manager).getFloat("scale", 1f);
                            if (zoom == 1f) return result;
                            Object info = chain.getArg(0);
                            int columns = ((Number) Reflect.call(info, "getWidth")).intValue();
                            int rows = ((Number) Reflect.call(info, "getHeight")).intValue();
                            for (var host : Set.copyOf(WidgetHostSizing.inView((View) chain.getArg(1)).values()))
                                WidgetHostSizing.refresh(host, columns, rows, manager, zoom, false, true);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            module.report("8.0 widget host scaling unavailable", error);
                        }
                        return result;
                    }));
            module.deoptimize(earlyHost);
        }
        installWidgetOptionSizing(loader, own);
    }

    private void installFactoryHostSizing(ClassLoader loader,
            java.util.function.Consumer<io.github.libxposed.api.XposedInterface.HookHandle> own)
            throws ReflectiveOperationException {
        Method create = WidgetHostSizing.creation(loader);
        var created =
                module.hook(create)
                        .setId("dressroom-advanced.widget-host-identity")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    try {
                                        WidgetHostSizing.created(result);
                                        if (result != null) {
                                            Object manager =
                                                    Reflect.field(
                                                            chain.getThisObject(),
                                                            "widgetSizeManager");
                                            if (main(manager)) {
                                                Object info = Reflect.call(result, "getWidgetInfo");
                                                var hosts =
                                                        WidgetHostSizing.inView(
                                                                (View)
                                                                        Reflect.call(
                                                                                result,
                                                                                "getWidgetView"));
                                                for (var host :
                                                        new java.util.HashSet<>(hosts.values()))
                                                    WidgetHostSizing.refresh(
                                                            host,
                                                            ((Number)
                                                                            Reflect.call(
                                                                                    info,
                                                                                    "getWidth"))
                                                                    .intValue(),
                                                            ((Number)
                                                                            Reflect.call(
                                                                                    info,
                                                                                    "getHeight"))
                                                                    .intValue(),
                                                            manager,
                                                            config(manager).getFloat("scale", 1),
                                                            config(manager).getBoolean("layout"));
                                            }
                                        }
                                    } catch (Exception error) {
                                        module.report("Widget host scaling unavailable", error);
                                    }
                                    return result;
                                });
        own.accept(created);
        module.deoptimize(create);
    }

    private void installWidgetOptionSizing(ClassLoader loader,
            java.util.function.Consumer<io.github.libxposed.api.XposedInterface.HookHandle> own)
            throws ReflectiveOperationException {
        Class<?> grid = Class.forName(GRID, false, loader);
        Method optionsMethod =
                NativeContract.method(
                        grid, "sendAppWidgetOptions", void.class, int.class, int.class, int.class);
        var options =
                module.hook(optionsMethod)
                        .setId("dressroom-advanced.widget-logical-viewport")
                        .intercept(
                                chain -> {
                                    Object sizes =
                                            Reflect.call(
                                                    chain.getThisObject(), "getWidgetSizeManager");
                                    float zoom =
                                            main(sizes) ? config(sizes).getFloat("scale", 1) : 1;
                                    if (zoom == 1) return chain.proceed();
                                    float scaled =
                                            ((Number) Reflect.field(sizes, "_scaleFactor"))
                                                    .floatValue();
                                    boolean wasUnscaled = WidgetHostSizing.unscaled.get();
                                    try {
                                        WidgetHostSizing.unscaled.set(true);
                                        Reflect.set(sizes, "_scaleFactor", scaled / zoom);
                                        return chain.proceed();
                                    } finally {
                                        Reflect.set(sizes, "_scaleFactor", scaled);
                                        WidgetHostSizing.unscaled.set(wasUnscaled);
                                    }
                                });
        own.accept(options);
        module.deoptimize(optionsMethod);
    }

    boolean fits(Object grid, Bundle candidate) throws ReflectiveOperationException {
        Object list = Reflect.call(grid, "getModuleInfo");
        if (!(list instanceof java.util.List<?> modules)) return false;
        int[][] spans = new int[modules.size()][2];
        for (int i = 0; i < modules.size(); i++) {
            spans[i][0] = ((Number) Reflect.call(modules.get(i), "getWidth")).intValue();
            spans[i][1] = ((Number) Reflect.call(modules.get(i), "getHeight")).intValue();
        }
        return WidgetLayout.fits(candidate.getInt("columns"), candidate.getInt("rows"), spans);
    }

    void refreshAll() {
        if (refreshing) return;
        refreshing = true;
        try {
            java.util.List<Object> current;
            synchronized (grids) {
                current = new java.util.ArrayList<>(grids);
            }
            for (Object grid : current) refresh(grid);
        } finally {
            refreshing = false;
        }
    }

    void refresh(Object grid) {
        try {
            refreshChecked(grid);
        } catch (Exception e) {
            module.report("Widget grid refresh failed", e);
        }
    }

    void refreshChecked(Object grid) throws ReflectiveOperationException {
        Object manager = Reflect.call(grid, "getWidgetSizeManager");
        if (!main(manager)) return;
        Reflect.call(manager, "init", Reflect.call(manager, "getWidgetDisplayType"));
        GridRefresh refresh = refreshBindings.get(grid.getClass());
        if (refresh.forceArgument()) {
            refresh.method().invoke(grid, true);
        } else {
            refresh.method().invoke(grid);
        }
        notifyLegacyProviders(grid);
    }

    static void validateLegacyGrid(Class<?> grid) throws ReflectiveOperationException {
        NativeContract.method(
                grid, "forEachWidgetView", void.class, java.util.function.Consumer.class);
        NativeContract.method(
                grid, "sendAppWidgetOptions", void.class, int.class, int.class, int.class);
    }

    private void notifyLegacyProviders(Object grid) throws ReflectiveOperationException {
        var attached = WidgetHostSizing.attached(grid);
        Object current = Reflect.call(grid, "getModuleInfo");
        if (!(current instanceof java.util.List<?> items)) return;
        for (Object item : items) {
            String uri = (String) Reflect.call(item, "getUri");
            int separator = uri == null ? -1 : uri.indexOf(';');
            if (separator <= 0) continue;
            int id;
            try {
                id = Integer.parseInt(uri.substring(0, separator));
            } catch (NumberFormatException ignored) {
                continue;
            }
            var host = attached.get(id);
            if (host == null) continue;
            int columns = ((Number) Reflect.call(item, "getWidth")).intValue();
            int rows = ((Number) Reflect.call(item, "getHeight")).intValue();
            Object manager = Reflect.call(grid, "getWidgetSizeManager");
            boolean early = earlyScaling(grid);
            WidgetHostSizing.refresh(
                    host,
                    columns,
                    rows,
                    manager,
                    config(manager).getFloat("scale", 1),
                    config(manager).getBoolean("layout"),
                    early);
            Reflect.call(grid, "sendAppWidgetOptions", id, columns, rows);
        }
    }

    boolean repairPositions(Object grid) throws ReflectiveOperationException {
        Object manager = Reflect.call(grid, "getWidgetSizeManager");
        if (!main(manager) || !config(manager).getBoolean("layout")) return false;
        Object current = Reflect.call(grid, "getModuleInfo");
        if (!(current instanceof java.util.List<?> items)) return false;
        boolean vertical = isVertical(Reflect.field(grid, "widgetAlignType"));
        int[][] cells = new int[items.size()][4];
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            cells[i][0] =
                    ((Number) Reflect.call(item, vertical ? "getSecondaryX" : "getX")).intValue();
            cells[i][1] =
                    ((Number) Reflect.call(item, vertical ? "getSecondaryY" : "getY")).intValue();
            cells[i][2] = ((Number) Reflect.call(item, "getWidth")).intValue();
            cells[i][3] = ((Number) Reflect.call(item, "getHeight")).intValue();
        }
        if (WidgetLayout.positionsValid(
                ((Number) Reflect.call(manager, "getColumn")).intValue(),
                ((Number) Reflect.call(manager, "getRow")).intValue(),
                cells)) return false;
        reflow(grid);
        return true;
    }

    void reflow(Object grid) throws ReflectiveOperationException {
        if (!OneUi80WidgetRuntime.grid(grid.getClass()).reflow(grid))
            throw new IllegalStateException(
                    "Samsung could not fit these widgets. Increase the grid size.");
    }

    static boolean isVertical(Object align) throws ReflectiveOperationException {
        return align != null && Boolean.TRUE.equals(Reflect.call(align, "isVertical"));
    }

    static Object savedAlignment(Object grid, boolean vertical)
            throws ReflectiveOperationException {
        Class<?> type =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                        false,
                        grid.getClass().getClassLoader());
        NativeContract.field(grid.getClass(), "widgetAlignType", type);
        return enumValue(type, vertical ? "Vertical" : "Default");
    }

    static Object enumValue(Class<?> type, String name) {
        for (Object value : type.getEnumConstants())
            if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException("Missing native enum " + name);
    }
}
