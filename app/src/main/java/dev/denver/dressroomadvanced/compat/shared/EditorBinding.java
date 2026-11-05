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

    static final long REFERENCE = 301038000L, ONE_UI_85 = 296051000L, ONE_UI_85_TABLET = 296050000L;
    final boolean legacy;
    final Class<?> controller;
    final Method open, create, weather, bind;
    private final Method weatherCapability, managerGetter;
    private final Field managerWrapper;
    private final Field root, dataUseCase, helper, clock, model;

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
                        return new EditorBinding(
                                owner.getClassLoader(), owner.getName().equals("Pa.w0"));
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
        if (version != REFERENCE && version != ONE_UI_85 && version != ONE_UI_85_TABLET)
            throw new ClassNotFoundException(
                    "Dressroom version " + version + " has no inspected editor binding");
        return bindings.get(Class.forName(version == REFERENCE ? "vo.x" : "Pa.w0", false, loader));
    }

    private EditorBinding(ClassLoader loader, boolean legacy) throws ReflectiveOperationException {
        this.legacy = legacy;
        NativeTabs.forType(
                Class.forName("com.google.android.material.tabs.TabLayout", false, loader));
        controller = Class.forName(legacy ? "Pa.w0" : "vo.x", false, loader);
        Class<?> data =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData",
                        false,
                        loader);
        Class<?> clockView =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
        Class<?> useCase = Class.forName(legacy ? "b7.Z" : "mf.o0", false, loader);
        Class<?> helperType =
                Class.forName(
                        legacy ? "com.samsung.android.lockscreen.editor.item.core.util.b" : "oc.a",
                        false,
                        loader);
        open = NativeContract.method(controller, "a", boolean.class);
        root = NativeContract.assignableField(controller, legacy ? "h0" : "Y", View.class);
        dataUseCase = NativeContract.field(controller, legacy ? "c" : "b", useCase);
        helper = NativeContract.field(controller, legacy ? "z" : "w", helperType);
        clock = NativeContract.field(helperType, legacy ? "b" : "a", clockView);
        model = controller.getDeclaredField(legacy ? "e" : "d");
        model.setAccessible(true);
        model.getType().getMethod("get");
        create =
                NativeContract.method(
                        controller, legacy ? "s1" : "e", void.class, ViewGroup.class, data);
        bind =
                NativeContract.method(
                        helperType,
                        legacy ? "w" : "e",
                        void.class,
                        legacy ? View.class : clockView);
        Class<?> weatherType = Class.forName(legacy ? "b7.J" : "mf.i0", false, loader);
        weather =
                legacy
                        ? NativeContract.method(weatherType, "a", void.class, boolean.class)
                        : NativeContract.method(
                                weatherType, "a", void.class, boolean.class, boolean.class);
        Class<?> typeData =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockTypeData",
                        false,
                        loader);
        if (legacy) {
            Class<?> manager =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockManager",
                            false,
                            loader);
            Class<?> wrapper = Class.forName("O5.a", false, loader);
            managerWrapper = NativeContract.field(controller, "j", wrapper);
            managerGetter = NativeContract.method(wrapper, "i", manager);
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
        return ((java.lang.ref.WeakReference<?>) Reflect.field(owner, legacy ? "p0" : "f0")).get();
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
