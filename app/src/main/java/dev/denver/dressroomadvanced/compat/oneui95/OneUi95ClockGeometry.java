package dev.denver.dressroomadvanced;

import android.graphics.Rect;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

final class OneUi95ClockGeometry {
    private static final String ENTITY = "com.samsung.android.lockscreen.editor.item.domain.entity.";
    private final Field useCase, position, screenRepo, deviceRepo, assistRepo, previewRepo;
    private final Method resize, move, mapPreview, constrain, updateBounds;

    OneUi95ClockGeometry(Class<?> controller, Class<?> data) throws ReflectiveOperationException {
        ClassLoader loader = controller.getClassLoader();
        Class<?> screen = Class.forName(ENTITY + "system.ScreenSize", false, loader);
        Class<?> device = Class.forName(ENTITY + "system.DeviceType", false, loader);
        Class<?> assist = Class.forName(ENTITY + "item.clock.ClockAssistData", false, loader);
        Class<?> preview = Class.forName(ENTITY + "system.PreviewScale", false, loader);
        try {
            Class<?> refreshOwner = OneUi95Editor.unique(
                    DexSignatures.find(loader, Set.of("RefreshClockPositionUseCase")).stream()
                            .filter(body -> body.name().equals("getTag") && body.parameters().isEmpty()
                                    && body.result().equals("Ljava/lang/String;"))
                            .toList(), "clock position use case").loadOwner(loader);
            useCase = NativeContract.findField(controller, null, refreshOwner);
            Method refresh = NativeContract.findMethod(refreshOwner, null, void.class);
            DexSignatures.Body body = DexSignatures.body(refresh);
            String owner = DexSignatures.descriptor(refreshOwner);
            String dataType = DexSignatures.descriptor(data);
            String screenType = DexSignatures.descriptor(screen);
            String deviceType = DexSignatures.descriptor(device);
            String assistType = DexSignatures.descriptor(assist);
            String previewType = DexSignatures.descriptor(preview);

            DexSignatures.Call apply = signature(body,
                    List.of(dataType, deviceType, screenType, "Z"), "clock bounds constraint");
            DexSignatures.Call map = signature(body, List.of(dataType, previewType, screenType),
                    "clock preview position");
            DexSignatures.Call bounds = signature(body,
                    List.of(dataType, screenType, assistType, deviceType), "clock visibility bounds");
            int firstPosition = OneUi95Editor.call(body, dataType, "getScale").offset();
            int forceReset = OneUi95Editor.call(body,
                    "Lcom/samsung/android/lockscreen/editor/item/domain/entity/item/clock/State;",
                    "isForceReset").offset();
            List<DexSignatures.Call> sizeInputs = dataReads(body, forceReset, firstPosition);
            if (sizeInputs.size() != 2) throw new NoSuchMethodException("Clock geometry inputs");
            screenRepo = fieldBefore(body, owner, sizeInputs.get(0).offset(), loader);
            deviceRepo = fieldBefore(body, owner, sizeInputs.get(1).offset(), loader);
            assistRepo = fieldBefore(body, owner, OneUi95Editor.unique(dataReads(body, 0,
                    OneUi95Editor.call(body, assistType, "getOrientation").offset()),
                    "clock assist repository read").offset(), loader);
            List<DexSignatures.Call> previewInputs = dataReads(body, apply.offset(), map.offset());
            if (previewInputs.size() != 2) throw new NoSuchMethodException("Clock preview inputs");
            previewRepo = fieldBefore(body, owner, previewInputs.get(0).offset(), loader);
            if (screenRepo.equals(previewRepo))
                throw new NoSuchFieldException("Clock preview scale was resolved to screen size");
            constrain = apply.method(loader);
            mapPreview = map.method(loader);
            updateBounds = bounds.method(loader);
            Class<?> positionType = constrain.getDeclaringClass();
            if (mapPreview.getDeclaringClass() != positionType
                    || updateBounds.getDeclaringClass() != positionType)
                throw new NoSuchMethodException("Inconsistent clock geometry calculator");
            position = NativeContract.findField(refreshOwner, null, positionType);
            resize = NativeContract.findMethod(positionType, null, void.class,
                    data, screen, device, boolean.class, boolean.class, float.class,
                    boolean.class, Rect.class);
            move = NativeContract.findMethod(positionType, null, void.class,
                    data, screen, int.class, int.class, assist, device);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Clock geometry DEX unavailable", invalidDex);
        }
    }

    private static DexSignatures.Call signature(
            DexSignatures.Body body, List<String> parameters, String role)
            throws NoSuchMethodException {
        return OneUi95Editor.unique(body.calls().stream()
                .filter(call -> !call.isStatic() && call.result().equals("V")
                        && call.parameters().equals(parameters)).toList(), role);
    }

    private static List<DexSignatures.Call> dataReads(DexSignatures.Body body, int after, int before) {
        return body.calls().stream().filter(call -> call.offset() > after && call.offset() < before
                && call.name().equals("getData") && call.parameters().isEmpty()
                && call.result().equals("Ljava/lang/Object;")).toList();
    }

    private static Field fieldBefore(DexSignatures.Body body, String owner, int offset,
            ClassLoader loader) throws ReflectiveOperationException {
        return body.reads().stream().filter(read -> read.offset() < offset
                        && read.field().owner().equals(owner))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new NoSuchFieldException("Clock geometry repository"))
                .field().field(loader);
    }

    int orientation(Object controller) throws ReflectiveOperationException {
        Object screen = getData(screenRepo, useCase.get(controller));
        return SettingsScope.orientation(((Number) Reflect.call(screen, "getWidth")).intValue(),
                ((Number) Reflect.call(screen, "getHeight")).intValue());
    }

    void restoreWeather(Object controller, boolean show) throws ReflectiveOperationException {
        Reflect.call(getData(assistRepo, useCase.get(controller)), "setShowWeatherInfoReservedValue", show);
    }

    void notifyWeather(Object controller) throws ReflectiveOperationException {
        Object repo = assistRepo.get(useCase.get(controller));
        Reflect.call(repo, "setData", Reflect.call(repo, "getData"), true);
    }

    Object positioner(Object controller) throws ReflectiveOperationException {
        return position.get(useCase.get(controller));
    }

    void apply(Object controller, Object draft, boolean resizing, float scale, int dx, int dy)
            throws ReflectiveOperationException {
        Object settings = useCase.get(controller);
        Object calculator = position.get(settings);
        Object screen = getData(screenRepo, settings);
        Object device = getData(deviceRepo, settings);
        Object assist = getData(assistRepo, settings);
        Object preview = getData(previewRepo, settings);
        if (resizing) resize.invoke(calculator, draft, screen, device, true, false, scale, false, null);
        else move.invoke(calculator, draft, screen, dx, dy, assist, device);
        mapPreview.invoke(calculator, draft, preview, screen);
        constrain.invoke(calculator, draft, device, screen, false);
        updateBounds.invoke(calculator, draft, screen, assist, device);
    }

    private static Object getData(Field repository, Object useCase)
            throws ReflectiveOperationException {
        return Reflect.call(repository.get(useCase), "getData");
    }
}
