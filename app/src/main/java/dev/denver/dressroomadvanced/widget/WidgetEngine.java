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
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Object> grids = Collections.newSetFromMap(new WeakHashMap<>());
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
    private final Set<ClassLoader> legacyProviders = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> scaleInstalled = Collections.newSetFromMap(new WeakHashMap<>());

    WidgetEngine(DressroomModule module) {
        this.module = module;
    }

    Bundle settings(Context c) {
        if (module.editorWidgets != null) return module.editorWidgets.settings(c);
        int orientation = LayoutOrientation.current(c);
        if (runtime == null || runtimeOrientation != orientation) {
            runtime = WidgetPreferences.read(c, "");
            runtimeOrientation = orientation;
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
        try {
            Object display = Reflect.call(manager, "getWidgetDisplayType");
            return !(Boolean) Reflect.call(display, "isCover")
                    && !(Boolean) Reflect.call(display, "isInformative");
        } catch (Exception e) {
            return false;
        }
    }

    private Bundle config(Object manager) throws ReflectiveOperationException {
        return settings((Context) Reflect.field(manager, "context"));
    }

    synchronized boolean installLegacyCapacity(ClassLoader loader)
            throws ReflectiveOperationException {
        legacyProviders.add(loader);
        if (installed.contains(loader)) return true;
        if (!scaleInstalled.contains(loader)) install(loader);
        if (installed.contains(loader)) return true;
        installScale(loader);
        return false;
    }

    synchronized void installScale(ClassLoader loader) throws ReflectiveOperationException {
        if (scaleInstalled.contains(loader) || installed.contains(loader)) return;
        Class<?> manager = Class.forName(SIZE, false, loader);
        NativeContract.field(manager, "_scaleFactor", float.class);
        Class<?> display =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetDisplayType",
                        false,
                        loader);
        Method init = NativeContract.method(manager, "init", void.class, display);
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
        WidgetHostSizing.validate(loader);
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
            installHostSizing(loader, handle -> hooks.add(handle::unhook));
            hooks.commit();
            scaleInstalled.add(loader);
        }
    }

    synchronized void install(ClassLoader loader) {
        if (installed.contains(loader)) return;
        java.util.ArrayList<io.github.libxposed.api.XposedInterface.HookHandle> handles =
                new java.util.ArrayList<>();
        try {
            Class<?> size = Class.forName(SIZE, false, loader);
            installed.add(loader);
            for (String name : new String[] {"getColumn", "getRow", "isBiggerSize"}) {
                Method m = size.getDeclaredMethod(name);
                handles.add(
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
                                                            (Boolean)
                                                                    Reflect.call(
                                                                            Reflect.field(
                                                                                    chain
                                                                                            .getThisObject(),
                                                                                    "widgetAlignType"),
                                                                            "isVertical");
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
                                        }));
                module.deoptimize(m);
            }
            for (Method m : size.getDeclaredMethods())
                if (m.getName().equals("init") && m.getParameterCount() == 1) {
                    handles.add(
                            module.hook(m)
                                    .setId("dressroom-advanced.widget-cell-scale")
                                    .intercept(
                                            chain -> {
                                                boolean old = nativeMetrics.get();
                                                nativeMetrics.set(true);
                                                Object manager = chain.getThisObject();
                                                Object align = null;
                                                try {
                                                    if (main(manager)
                                                            && config(manager)
                                                                    .getBoolean("layout")) {
                                                        align =
                                                                Reflect.field(
                                                                        manager, "widgetAlignType");
                                                        Reflect.set(
                                                                manager,
                                                                "widgetAlignType",
                                                                enumValue(
                                                                        align.getClass(),
                                                                        "Default"));
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
                                                    if (align != null)
                                                        Reflect.set(
                                                                manager, "widgetAlignType", align);
                                                    nativeMetrics.set(old);
                                                }
                                            }));
                    module.deoptimize(m);
                }
            Class<?> grid = Class.forName(GRID, false, loader);
            Class<?> alignType =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                            false,
                            loader);
            if (module.editorWidgets == null) {
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
                handles.add(
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
                                        }));
                module.deoptimize(update);
                Class<?> layouts =
                        Class.forName(
                                "com.samsung.android.app.facewidget.complication.WidgetLayoutManager",
                                false,
                                loader);
                Method refresh = layouts.getDeclaredMethod("refreshGridLayout", alignType);
                handles.add(
                        module.hook(refresh)
                                .setId("dressroom-advanced.runtime-widget-arrangement-capability")
                                .intercept(
                                        chain -> {
                                            Object controller =
                                                    Reflect.field(
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
                                        }));
                module.deoptimize(refresh);
            }
            Method rotate = grid.getDeclaredMethod("onclickChangeWidgetAlign", alignType);
            handles.add(
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
                                    }));
            module.deoptimize(rotate);
            for (Method m : grid.getDeclaredMethods())
                if (java.util.Set.of(
                                "getLockWidgetGridSize",
                                "updateGridRule",
                                "initDynamicGridLayout",
                                "updateAlignChangeLayout",
                                "updateRootContainerSize",
                                "checkAllowPosition",
                                "sendAppWidgetOptions")
                        .contains(m.getName())) module.deoptimize(m);
            Class<?> containers =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationContainerManager",
                            false,
                            loader);
            for (Method m : containers.getDeclaredMethods())
                if (m.getName().equals("getContainerWidth")
                        || m.getName().equals("getContainerHeight")) module.deoptimize(m);
            for (Method m : grid.getDeclaredMethods())
                if (m.getName().equals("setRootContainer") && m.getParameterCount() == 3) {
                    handles.add(
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
                                            }));
                    module.deoptimize(m);
                }
            installHostSizing(loader, handles::add);
            WidgetDepthBlur.install(module, loader);
            android.util.Log.i(
                    DressroomModule.TAG,
                    "Widget grid capacity, native rules and cell metrics hooks installed");
        } catch (Exception | LinkageError e) {
            installed.remove(loader);
            for (var handle : handles) handle.unhook();
            module.report("Widget grid hooks unavailable", e);
        }
    }

    private void installHostSizing(
            ClassLoader loader,
            java.util.function.Consumer<io.github.libxposed.api.XposedInterface.HookHandle> own)
            throws ReflectiveOperationException {
        WidgetHostSizing.validate(loader);
        Method create = WidgetHostSizing.creation(loader);
        var created =
                module.hook(create)
                        .setId("dressroom-advanced.widget-host-identity")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    WidgetHostSizing.created(result);
                                    try {
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
                                                            legacyProviders.contains(
                                                                            manager.getClass()
                                                                                    .getClassLoader())
                                                                    && config(manager)
                                                                            .getBoolean("layout"));
                                            }
                                        }
                                    } catch (Exception error) {
                                        module.report("Widget host scaling unavailable", error);
                                    }
                                    return result;
                                });
        own.accept(created);
        module.deoptimize(create);
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
                                    try {
                                        Reflect.set(sizes, "_scaleFactor", scaled / zoom);
                                        return chain.proceed();
                                    } finally {
                                        Reflect.set(sizes, "_scaleFactor", scaled);
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
            for (Object grid : new java.util.ArrayList<>(grids)) refresh(grid);
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
        GridRefresh refresh = refreshBindings.get(grid.getClass());
        if (refresh.forceArgument()) {
            refresh.method().invoke(grid, true);
            notifyLegacyProviders(grid);
        } else {
            Reflect.call(manager, "init", Reflect.call(manager, "getWidgetDisplayType"));
            refresh.method().invoke(grid);
            notifyLegacyProviders(grid);
        }
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
            WidgetHostSizing.refresh(
                    host,
                    columns,
                    rows,
                    manager,
                    config(manager).getFloat("scale", 1),
                    legacyProviders.contains(manager.getClass().getClassLoader())
                            && config(manager).getBoolean("layout"));
            Reflect.call(grid, "sendAppWidgetOptions", id, columns, rows);
        }
    }

    boolean repairPositions(Object grid) throws ReflectiveOperationException {
        Object manager = Reflect.call(grid, "getWidgetSizeManager");
        if (!main(manager) || !config(manager).getBoolean("layout")) return false;
        Object current = Reflect.call(grid, "getModuleInfo");
        if (!(current instanceof java.util.List<?> items)) return false;
        boolean vertical =
                Boolean.TRUE.equals(
                        Reflect.call(Reflect.field(grid, "widgetAlignType"), "isVertical"));
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
        Object containers = Reflect.field(grid, "containerManager");
        Object id = Reflect.call(containers, "getContainerId");
        if (id == null) return;
        Object manager = Reflect.call(grid, "getGridManager");
        Object container = Reflect.call(manager, "b", id);
        if (container == null) return;
        Object items = Reflect.call(container, "f");
        Method rearrange =
                NativeContract.findMethod(
                        container.getClass(),
                        "o",
                        boolean.class,
                        java.util.List.class,
                        java.util.Set.class,
                        boolean.class);
        if (!Boolean.TRUE.equals(rearrange.invoke(container, items, Collections.emptySet(), true)))
            throw new IllegalStateException(
                    "Samsung could not fit these widgets. Increase the grid size.");
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
