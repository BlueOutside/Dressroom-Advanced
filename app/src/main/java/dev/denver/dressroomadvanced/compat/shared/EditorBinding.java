package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class EditorBinding {
    enum Row {
        TABS,
        LABEL,
        TOGGLE,
        SLIDER
    }

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
    private final Method weatherCapability, managerGetter;
    private final Field managerWrapper;
    private final Field root, dataUseCase, helper, clock, model, activityField;

    String rowTag(Row row) {
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
                        boolean isLegacy = owner.getName().equals(OneUi85Editor.names().controller);
                        if (!isLegacy && !owner.getName().equals(OneUi9Editor.names().controller)) {
                            Class<?> typeData =
                                    Class.forName(
                                            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockTypeData",
                                            false,
                                            loader);
                            try {
                                typeData.getDeclaredMethod("isSupportWeatherInfo");
                            } catch (NoSuchMethodException ignored) {
                                isLegacy = true;
                            }
                        }
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
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasMethod(cls, void.class, ViewGroup.class, data)
                                        && NativeContract.hasMethod(cls, boolean.class),
                        primary.controller,
                        fallback.controller);
        return bindings.get(owner);
    }

    private EditorBinding(ClassLoader loader, Class<?> resolvedController, Names names)
            throws ReflectiveOperationException {
        this.legacy = names.legacy;
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
                                loader,
                                cls ->
                                        NativeContract.hasMethod(
                                                        cls, void.class, ViewGroup.class, data)
                                                && NativeContract.hasMethod(cls, boolean.class),
                                names.controller);
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
                        helperType,
                        names.bindMethod,
                        void.class,
                        legacy ? View.class : clockView);
        Class<?> weatherType =
                NativeContract.findClass(
                        loader,
                        cls ->
                                legacy
                                        ? NativeContract.hasMethod(cls, void.class, boolean.class)
                                        : NativeContract.hasMethod(
                                                cls, void.class, boolean.class, boolean.class),
                        names.weatherType);
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
        if (legacy && names.managerWrapperClass != null) {
            Class<?> manager =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockManager",
                            false,
                            loader);
            Class<?> wrapper =
                    NativeContract.findClass(
                            loader,
                            cls -> NativeContract.hasMethod(cls, manager),
                            names.managerWrapperClass);
            managerWrapper =
                    NativeContract.findField(controller, names.managerWrapperField, wrapper);
            managerGetter =
                    NativeContract.findMethod(wrapper, names.managerGetterMethod, manager);
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

    boolean supportsWeather(Object owner, Object data) throws ReflectiveOperationException {
        Object type = Reflect.call(data, "getClockTypeData");
        if (!legacy) return Boolean.TRUE.equals(weatherCapability.invoke(type));
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
        Object useCase = Reflect.field(owner, legacy ? "a0" : "R");
        return Reflect.call(
                legacy ? Reflect.call(useCase, "b") : Reflect.field(useCase, "a"), "getData");
    }

    Object calculator(Object owner) throws ReflectiveOperationException {
        Object manager = Reflect.call(Reflect.field(owner, legacy ? "j" : "g"), legacy ? "i" : "d");
        return Reflect.call(manager, "getClockPositionCalculator");
    }

    float previewScale(Object owner) throws ReflectiveOperationException {
        Object useCase = Reflect.field(owner, legacy ? "c0" : "S");
        Object data =
                legacy
                        ? Reflect.call(useCase, "a")
                        : Reflect.call(Reflect.field(useCase, "a"), "getData");
        return ((Number) Reflect.call(data, "getScale")).floatValue();
    }

    int orientation(Object owner) throws ReflectiveOperationException {
        Object useCase = Reflect.field(owner, legacy ? "R" : "O");
        Object screen = Reflect.call(Reflect.field(useCase, legacy ? "e" : "d"), "getData");
        return SettingsScope.orientation(
                ((Number) Reflect.call(screen, "getWidth")).intValue(),
                ((Number) Reflect.call(screen, "getHeight")).intValue());
    }

    void restoreWeather(Object owner, Object data, boolean value)
            throws ReflectiveOperationException {
        Reflect.call(data, "setShowWeatherInfo", value);
        if (!legacy) {
            Object assist = Reflect.call(Reflect.field(Reflect.field(owner, "W"), "a"), "getData");
            Reflect.call(assist, "setShowWeatherInfoReservedValue", value);
        }
    }

    void notifyWeather(Object owner) throws ReflectiveOperationException {
        if (legacy) return; // 8.5 reads ClockData directly; it has no reserved-weather coordinator.
        Object repo = Reflect.field(Reflect.field(owner, "W"), "a");
        Reflect.call(repo, "setData", Reflect.call(repo, "getData"), true);
    }

    void position(Object owner, Object draft, boolean resize, float scale, int dx, int dy)
            throws ReflectiveOperationException {
        Object useCase = Reflect.field(owner, legacy ? "R" : "O");
        Object position = Reflect.field(useCase, "b");
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
        else Reflect.call(position, "d", draft, device, screen, false);
        Reflect.call(position, legacy ? "m" : "o", draft, screen, assist, device);
    }
}
