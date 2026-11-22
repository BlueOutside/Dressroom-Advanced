package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.util.Range;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class EditorBinding {
    enum Row {
        TABS,
        LABEL,
        TOGGLE,
        SLIDER
    }

    record ClockBoxMethods(Method limit, Method scale, Method valid, Method mapPreview) {}

    record Names(
            boolean legacy,
            String controller,
            String useCase,
            String helperType,
            String rootField,
            String dataUseCaseField,
            String helperField,
            String clockField,
            String modelField,
            String createMethod,
            String bindMethod,
            String weatherType,
            String managerWrapperClass,
            String managerWrapperField,
            String managerGetterMethod) {}

    static final long REFERENCE = 301038000L, ONE_UI_85 = 296051000L, ONE_UI_85_TABLET = 296050000L;
    final boolean legacy;
    final Class<?> controller;
    final Method open, create, weather, bind;
    final OneUi95Editor.ClockPanel beta;
    final OneUi95ClockGeometry betaGeometry;
    final OneUi80Panel early;
    private final OneUi80Placement earlyPlacement;
    private final Method weatherCapability, managerGetter;
    private final Field managerWrapper;
    private final Field root, dataUseCase, helper, clock, model, activityField;

    String rowTag(Row row) {
        if (beta != null) return OneUi95Editor.rowTag(row);
        return switch (row) {
            case TABS -> "binding_1";
            case LABEL -> legacy ? "binding_8" : "binding_10";
            case TOGGLE -> legacy ? "binding_7" : "binding_9";
            case SLIDER -> legacy ? "binding_4" : "binding_5";
        };
    }

    private static final ClassValue<EditorBinding> bindings =
            new ClassValue<>() {
                @Override
                protected EditorBinding computeValue(Class<?> owner) {
                    try {
                        ClassLoader loader = owner.getClassLoader();
                        Class<?> clockView = Class.forName(
                                "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
                        // Weather capability moved independently of the editor ABI. Inspect the
                        // helper's bind operation instead of treating its absence as an 8.5 signal.
                        Class<?> data = Class.forName(
                                "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData",
                                false, loader);
                        if (NativeTabs.forType(Class.forName(
                                "com.google.android.material.tabs.TabLayout", false, loader)).early)
                            return new EditorBinding(owner, OneUi80Panel.bind(owner, data, clockView));
                        if (!controllerShape(owner, data)
                                && NativeContract.hasMethod(owner, void.class,
                                        android.widget.FrameLayout.class, data)) {
                            return new EditorBinding(owner, data, clockView);
                        }
                        boolean modern = false;
                        for (Field field : owner.getDeclaredFields()) {
                            Class<?> helper = field.getType();
                            if (NativeContract.hasField(helper, clockView)
                                    && NativeContract.hasMethod(helper, void.class, clockView)) modern = true;
                        }
                        boolean isLegacy = !modern;
                        Names names = isLegacy ? OneUi85Editor.names() : OneUi9Editor.names();
                        return new EditorBinding(loader, owner, names);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported editor ABI", error);
                    }
                }
            };

    static EditorBinding of(Object owner) {
        return bindings.get(owner.getClass());
    }

    static EditorBinding resolve(ClassLoader loader, long version)
            throws ReflectiveOperationException {
        boolean preferLegacy =
                version == ONE_UI_85
                        || version == ONE_UI_85_TABLET
                        || (version > 0 && version < 300000000L);
        Names primary = preferLegacy ? OneUi85Editor.names() : OneUi9Editor.names();
        Names fallback = preferLegacy ? OneUi9Editor.names() : OneUi85Editor.names();
        Class<?> data =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData",
                        false,
                        loader);
        Class<?> owner;
        try {
            owner = NativeContract.findClass(
                        loader,
                        cls -> controllerShape(cls, data),
                        primary.controller,
                        fallback.controller, "so.x");
        } catch (ClassNotFoundException missing) {
            if (preferLegacy) throw missing;
            owner = OneUi95Editor.resolve(loader, data);
        }
        return bindings.get(owner);
    }

    private static boolean controllerShape(Class<?> type, Class<?> data) {
        // The preview helper also creates clock views. Only the editor controller owns
        // the weak Activity reference used by the panel lifecycle.
        return NativeContract.hasMethod(type, void.class, ViewGroup.class, data)
                && NativeContract.hasMethod(type, boolean.class)
                && NativeContract.hasField(type, java.lang.ref.WeakReference.class);
    }

    private EditorBinding(ClassLoader loader, Class<?> resolvedController, Names names)
            throws ReflectiveOperationException {
        this.legacy = names.legacy;
        early = null;
        earlyPlacement = null;
        beta = null;
        betaGeometry = null;
        NativeTabs.forType(
                Class.forName("com.google.android.material.tabs.TabLayout", false, loader));
        Class<?> data =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData",
                        false,
                        loader);
        Class<?> clockView =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
        controller =
                resolvedController != null
                        ? resolvedController
                        : NativeContract.findClass(
                                loader, cls -> controllerShape(cls, data), names.controller);
        Class<?> helperType = null;
        try {
            Class<?> candidate = Class.forName(names.helperType, false, loader);
            if (NativeContract.hasField(candidate, clockView)) helperType = candidate;
        } catch (ClassNotFoundException ignored) {
        }
        if (helperType == null) {
            for (Field f : controller.getDeclaredFields()) {
                if (NativeContract.hasField(f.getType(), clockView)) {
                    helperType = f.getType();
                    break;
                }
            }
        }
        if (helperType == null)
            throw new ClassNotFoundException("Clock helper type not found in " + controller);

        Class<?> useCase = null;
        try {
            useCase = Class.forName(names.useCase, false, loader);
        } catch (ClassNotFoundException ignored) {
            Field f = controller.getDeclaredField(names.dataUseCaseField);
            useCase = f.getType();
        }
        if (!NativeContract.hasField(controller, useCase)) {
            // The observable use case is constructor-injected; its declared getter
            // returns the same stable ObservableData contract on both modern editors.
            Field injected = controller.getDeclaredField(names.dataUseCaseField);
            Class<?> observable = Class.forName(
                    "com.samsung.android.lockscreen.editor.item.domain.entity.common.ObservableData", false, loader);
            NativeContract.findMethod(injected.getType(), "a", observable);
            useCase = injected.getType();
        }
        open = NativeContract.findMethod(controller, "a", boolean.class);
        root = NativeContract.findField(controller, names.rootField, View.class);
        dataUseCase = NativeContract.findField(controller, names.dataUseCaseField, useCase);
        helper = NativeContract.findField(controller, names.helperField, helperType);
        clock = NativeContract.findField(helperType, names.clockField, clockView);
        activityField =
                NativeContract.findField(
                        controller, legacy ? "p0" : "f0", java.lang.ref.WeakReference.class);
        Field modelField = null;
        try {
            modelField = controller.getDeclaredField(names.modelField);
            modelField.getType().getMethod("get");
        } catch (ReflectiveOperationException ignored) {
            for (Field f : controller.getDeclaredFields()) {
                try {
                    f.getType().getMethod("get");
                    modelField = f;
                    break;
                } catch (NoSuchMethodException ignored2) {
                }
            }
        }
        if (modelField == null)
            throw new NoSuchFieldException("Model field not found in " + controller.getName());
        modelField.setAccessible(true);
        model = modelField;
        create =
                NativeContract.findMethod(
                        controller, names.createMethod, void.class, ViewGroup.class, data);
        bind =
                NativeContract.findMethod(
                        helperType, names.bindMethod, void.class, legacy ? View.class : clockView);
        java.util.function.Predicate<Class<?>> weatherShape =
                cls ->
                        legacy
                                ? NativeContract.hasMethod(cls, void.class, boolean.class)
                                : NativeContract.hasMethod(
                                        cls, void.class, boolean.class, boolean.class);
        Class<?> weatherType = NativeContract.knownClass(loader, weatherShape, names.weatherType, "mf.f0");
        if (weatherType == null) {
            // A boolean setter is far too common to identify this use case by type alone.
            java.util.Set<Class<?>> matches = new java.util.LinkedHashSet<>();
            try {
                for (DexSignatures.Body body :
                        DexSignatures.find(loader, java.util.Set.of("EnableWeatherInfoUseCase"))) {
                    Class<?> candidate =
                            Class.forName(
                                    body.owner()
                                            .substring(1, body.owner().length() - 1)
                                            .replace('/', '.'),
                                    false,
                                    loader);
                    if (weatherShape.test(candidate)) matches.add(candidate);
                }
            } catch (java.io.IOException invalidDex) {
                throw new ReflectiveOperationException(
                        "Could not read weather use-case signature", invalidDex);
            }
            if (matches.size() != 1)
                throw new ClassNotFoundException("Ambiguous weather use case: " + matches.size());
            weatherType = matches.iterator().next();
        }
        weather =
                legacy
                        ? NativeContract.findMethod(weatherType, "a", void.class, boolean.class)
                        : NativeContract.findMethod(
                                weatherType, "a", void.class, boolean.class, boolean.class);
        Class<?> typeData =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockTypeData",
                        false,
                        loader);
        if (!java.util.Arrays.stream(typeData.getDeclaredMethods()).anyMatch(m -> m.getName().equals("isSupportWeatherInfo") && m.getReturnType() == boolean.class && m.getParameterCount() == 0)) {
            Class<?> manager =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockManager",
                            false,
                            loader);
            Class<?> wrapper =
                    NativeContract.findClass(
                            loader,
                            cls -> NativeContract.hasMethod(cls, manager),
                            names.managerWrapperClass, "pc.b");
            managerWrapper =
                    NativeContract.findField(controller, names.managerWrapperField == null ? "g" : names.managerWrapperField, wrapper);
            managerGetter = NativeContract.findMethod(wrapper, names.managerGetterMethod, manager);
            weatherCapability =
                    NativeContract.method(
                            manager,
                            "isSupportWeatherInfo",
                            boolean.class,
                            Class.forName(
                                    "com.samsung.android.clockpack.plugins.Category",
                                    false,
                                    loader),
                            int.class);
        } else {
            managerWrapper = null;
            managerGetter = null;
            weatherCapability =
                    NativeContract.method(typeData, "isSupportWeatherInfo", boolean.class);
        }
    }

    private EditorBinding(Class<?> owner, Class<?> clockData, Class<?> clockView)
            throws ReflectiveOperationException {
        legacy = false;
        early = null;
        earlyPlacement = null;
        controller = owner;
        ClassLoader loader = owner.getClassLoader();
        NativeTabs.forType(Class.forName("com.google.android.material.tabs.TabLayout", false, loader));
        beta = OneUi95Editor.bind(owner, clockData, clockView);
        OneUi95ClockGeometry geometry;
        try {
            geometry = new OneUi95ClockGeometry(owner, clockData);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            geometry = null;
            DressroomModule.note("Optional beta clock geometry unavailable", unavailable);
        }
        betaGeometry = geometry;
        open = beta.open();
        create = beta.create();
        weather = beta.weather();
        bind = beta.bind();
        root = beta.root();
        dataUseCase = beta.dataUseCase();
        helper = beta.helper();
        clock = beta.clock();
        model = beta.model();
        activityField = beta.activity();
        managerWrapper = null;
        managerGetter = null;
        Class<?> type = Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockTypeData",
                false, loader);
        weatherCapability = NativeContract.method(type, "isSupportWeatherInfo", boolean.class);
    }

    private EditorBinding(Class<?> owner, OneUi80Panel early) {
        legacy = true;
        this.early = early;
        OneUi80Placement placement;
        try {
            placement = new OneUi80Placement(owner, early.create().getParameterTypes()[1]);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            placement = null;
            DressroomModule.note("Optional 8.0 clock geometry unavailable", unavailable);
        }
        earlyPlacement = placement;
        beta = null;
        betaGeometry = null;
        controller = owner;
        open = early.open();
        create = early.create();
        weather = early.weather();
        bind = early.bind();
        root = early.root();
        dataUseCase = early.dataUseCase();
        helper = early.helper();
        clock = early.clock();
        model = early.model();
        activityField = early.activity();
        managerWrapper = early.managerWrapper();
        managerGetter = null;
        weatherCapability = early.weatherSupported();
    }

    boolean supportsWeather(Object owner, Object data) throws ReflectiveOperationException {
        Object type = Reflect.call(data, "getClockTypeData");
        if (early != null)
            return Boolean.TRUE.equals(weatherCapability.invoke(managerWrapper.get(owner),
                    Reflect.call(type, "getClockCategory"), Reflect.call(data, "getClockType")));
        if (managerWrapper == null) return Boolean.TRUE.equals(weatherCapability.invoke(type));
        if (!Boolean.TRUE.equals(Reflect.call(data, "getEnable"))) return false;
        Object manager = managerGetter.invoke(managerWrapper.get(owner));
        return Boolean.TRUE.equals(
                weatherCapability.invoke(
                        manager,
                        Reflect.call(type, "getClockCategory"),
                        ((Number) Reflect.call(data, "getClockType")).intValue()));
    }

    Object root(Object owner) throws ReflectiveOperationException {
        return root.get(owner);
    }

    Object repo(Object owner) throws ReflectiveOperationException {
        if (early != null) return early.readRepo().invoke(dataUseCase.get(owner));
        if (beta != null) return beta.repo().invoke(dataUseCase.get(owner));
        return Reflect.field(dataUseCase.get(owner), "a");
    }

    Object data(Object owner) throws ReflectiveOperationException {
        return Reflect.call(repo(owner), "getData");
    }

    Object renderer(Object owner) throws ReflectiveOperationException {
        return clock.get(helper.get(owner));
    }

    Object model(Object owner) throws ReflectiveOperationException {
        return Reflect.call(model.get(owner), "get");
    }

    Object activity(Object owner) throws ReflectiveOperationException {
        return ((java.lang.ref.WeakReference<?>) activityField.get(owner)).get();
    }

    Object focusedScene(Object owner) throws ReflectiveOperationException {
        if (early != null) return null;
        if (beta != null) {
            if (beta.focusedSceneUseCase() == null || beta.focusedSceneRepo() == null) return null;
            Object useCase = beta.focusedSceneUseCase().get(owner);
            if (useCase == null) return null;
            Object repo = beta.focusedSceneRepo().invoke(useCase);
            return repo == null ? null : Reflect.call(repo, "getData");
        }
        Object useCase = Reflect.field(owner, legacy ? "a0" : "R");
        return Reflect.call(
                legacy ? Reflect.call(useCase, "b") : Reflect.field(useCase, "a"), "getData");
    }

    private Object calculator(Object owner) throws ReflectiveOperationException {
        if (beta != null) return Reflect.call(
                beta.manager().invoke(beta.managerWrapper().get(owner)), "getClockPositionCalculator");
        Object manager = Reflect.call(Reflect.field(owner, legacy ? "j" : "g"), legacy ? "i" : "d");
        return Reflect.call(manager, "getClockPositionCalculator");
    }

    @SuppressWarnings("unchecked")
    List<Float> stretchHeights(Object owner, List<Rect> minimum, List<Rect> target,
            int padding, float scale) throws ReflectiveOperationException {
        if (early != null) return early.heights(owner, minimum, target, padding, scale);
        Object calculator = calculator(owner);
        if (calculator == null) throw new NoSuchMethodException("No stretch calculator for this clock");
        return (List<Float>) Reflect.call(calculator, "calculateStretchHeightList",
                minimum, target, padding, scale);
    }

    float previewScale(Object owner) throws ReflectiveOperationException {
        if (earlyPlacement != null) return earlyPlacement.previewScale(owner);
        if (beta != null) {
            Object repo = beta.scaleRepo().get(beta.scaleUseCase().get(owner));
            return ((Number) Reflect.call(Reflect.call(repo, "getData"), "getScale")).floatValue();
        }
        Object useCase = Reflect.field(owner, legacy ? "c0" : "S");
        Object data =
                legacy
                        ? Reflect.call(useCase, "a")
                        : Reflect.call(Reflect.field(useCase, "a"), "getData");
        return ((Number) Reflect.call(data, "getScale")).floatValue();
    }

    int orientation(Object owner) throws ReflectiveOperationException {
        if (earlyPlacement != null) return earlyPlacement.orientation(owner);
        if (betaGeometry != null) return betaGeometry.orientation(owner);
        if (beta != null || early != null) {
            Object context = activity(owner);
            return context instanceof android.content.Context c ? LayoutOrientation.current(c) : 0;
        }
        Object useCase = Reflect.field(owner, legacy ? "R" : "O");
        Object screen = Reflect.call(Reflect.field(useCase, legacy ? "e" : "d"), "getData");
        return SettingsScope.orientation(
                ((Number) Reflect.call(screen, "getWidth")).intValue(),
                ((Number) Reflect.call(screen, "getHeight")).intValue());
    }

    void restoreWeather(Object owner, Object data, boolean value)
            throws ReflectiveOperationException {
        Reflect.call(data, "setShowWeatherInfo", value);
        if (betaGeometry != null) {
            betaGeometry.restoreWeather(owner, value);
        } else if (!legacy && beta == null) {
            Object assist = Reflect.call(Reflect.field(Reflect.field(owner, "W"), "a"), "getData");
            Reflect.call(assist, "setShowWeatherInfoReservedValue", value);
        }
    }

    void notifyWeather(Object owner) throws ReflectiveOperationException {
        if (betaGeometry != null) {
            betaGeometry.notifyWeather(owner);
            return;
        }
        if (legacy || beta != null) return;
        Object repo = Reflect.field(Reflect.field(owner, "W"), "a");
        Reflect.call(repo, "setData", Reflect.call(repo, "getData"), true);
    }

    Object positioner(Object owner) throws ReflectiveOperationException {
        if (beta != null) return betaGeometry == null ? null : betaGeometry.positioner(owner);
        return Reflect.field(Reflect.field(owner, legacy ? "R" : "O"), "b");
    }

    static ClockBoxMethods clockBoxMethods(Class<?> owner, Class<?> data)
            throws ReflectiveOperationException {
        ClassLoader loader = owner.getClassLoader();
        String system = "com.samsung.android.lockscreen.editor.item.domain.entity.system.";
        Class<?> previewScale = Class.forName(system + "PreviewScale", false, loader);
        Class<?> screenSize = Class.forName(system + "ScreenSize", false, loader);
        return clockBoxMethods(owner, data, previewScale, screenSize);
    }

    static ClockBoxMethods clockBoxMethods(
            Class<?> owner, Class<?> data, Class<?> previewScale, Class<?> screenSize)
            throws NoSuchMethodException {
        Class<?> constraints = data.getMethod("getSizeConstraints").getReturnType();
        return new ClockBoxMethods(
                NativeContract.findMethod(owner, null, Range.class,
                        int.class, int.class, Rect.class, constraints),
                NativeContract.findMethod(owner, null, void.class, data, float.class),
                NativeContract.findMethod(owner, null, boolean.class, data, screenSize),
                NativeContract.findMethod(owner, null, void.class, data, previewScale, screenSize));
    }

    void position(Object owner, Object draft, boolean resize, float scale, int dx, int dy)
            throws ReflectiveOperationException {
        if (earlyPlacement != null) {
            earlyPlacement.apply(owner, draft, resize, scale, dx, dy);
            return;
        }
        if (betaGeometry != null) {
            betaGeometry.apply(owner, draft, resize, scale, dx, dy);
            return;
        }
        if (beta != null || early != null) throw new NoSuchMethodException("Clock positioning unavailable");
        Object useCase = Reflect.field(owner, legacy ? "R" : "O");
        Object position = positioner(owner);
        Object screen = Reflect.call(Reflect.field(useCase, legacy ? "e" : "d"), "getData");
        Object device = Reflect.call(Reflect.field(useCase, legacy ? "h" : "e"), "getData");
        Object assist = Reflect.call(Reflect.field(useCase, legacy ? "c" : "j"), "getData");
        Object preview = Reflect.call(Reflect.field(useCase, legacy ? "i" : "h"), "getData");
        if (resize)
            Reflect.call(
                    position,
                    legacy ? "k" : "n",
                    draft,
                    screen,
                    device,
                    true,
                    false,
                    scale,
                    false,
                    null);
        else Reflect.call(position, legacy ? "h" : "f", draft, screen, dx, dy, assist, device);
        Reflect.call(position, legacy ? "a" : "l", draft, preview, screen);
        if (legacy) Reflect.call(position, "f", draft, screen, device, false);
        else {
            Method placement;
            try {
                placement = NativeContract.method(position.getClass(), "d", void.class,
                        draft.getClass(), device.getClass(), screen.getClass(), boolean.class);
                placement.invoke(position, draft, device, screen, false);
            } catch (NoSuchMethodException reordered) {
                placement = NativeContract.findMethod(position.getClass(), "d", void.class,
                        draft.getClass(), screen.getClass(), device.getClass(), boolean.class);
                placement.invoke(position, draft, screen, device, false);
            }
        }
        Reflect.call(position, legacy ? "m" : "o", draft, screen, assist, device);
    }

    boolean supportsPosition() {
        return early != null ? earlyPlacement != null : beta == null || betaGeometry != null;
    }

    float minimumScale(Object data) throws ReflectiveOperationException {
        Object bounds = early == null ? data : Reflect.call(data, "getSizeConstraints");
        return ((Number) Reflect.call(bounds, "getMinScale")).floatValue();
    }
}
