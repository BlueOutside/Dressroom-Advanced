package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

final class OneUi80Placement {
    private static final String ENTITY = "com.samsung.android.lockscreen.editor.item.domain.entity.";
    private final Field useCase, calculator, screen, device, assist, preview;
    private final Method resize, move, constrain, mapPreview, bounds;

    OneUi80Placement(Class<?> controller, Class<?> data) throws ReflectiveOperationException {
        ClassLoader loader = controller.getClassLoader();
        Class<?> screenType = Class.forName(ENTITY + "system.ScreenSize", false, loader);
        Class<?> deviceType = Class.forName(ENTITY + "system.DeviceType", false, loader);
        Class<?> assistType = Class.forName(ENTITY + "item.clock.ClockAssistData", false, loader);
        Class<?> previewType = Class.forName(ENTITY + "system.PreviewScale", false, loader);
        String tag = "ChangeClockPositionInfoUseCase";
        try {
            Class<?> owner = OneUi95Editor.unique(DexSignatures.find(loader, Set.of(tag)).stream()
                    .filter(body -> body.name().equals("<init>") && body.anchors().containsKey(tag))
                    .toList(), "8.0 clock position use case").loadOwner(loader);
            useCase = NativeContract.findField(controller, null, owner);
            var body = DexSignatures.body(NativeContract.findMethod(owner, null, void.class,
                    List.class, boolean.class));
            var apply = call(body, 0, data, screenType, deviceType, boolean.class);
            var map = call(body, apply.offset(), data, previewType, screenType);
            var limit = call(body, 0, data, screenType, assistType, deviceType);
            constrain = apply.method(loader);
            mapPreview = map.method(loader);
            bounds = limit.method(loader);
            Class<?> position = constrain.getDeclaringClass();
            if (mapPreview.getDeclaringClass() != position || bounds.getDeclaringClass() != position)
                throw new NoSuchMethodException("Inconsistent 8.0 clock position repositories");
            calculator = NativeContract.findField(owner, null, position);
            resize = NativeContract.findMethod(position, null, void.class,
                    data, screenType, deviceType, boolean.class, boolean.class, float.class,
                    boolean.class);
            move = NativeContract.findMethod(position, null, void.class,
                    data, screenType, int.class, int.class, assistType, deviceType);
            String target = DexSignatures.descriptor(owner);
            List<DexSignatures.Read> mapReads = reads(body, target, apply.offset(), map.offset());
            List<DexSignatures.Read> beforeBounds = reads(body, target, 0, limit.offset());
            if (beforeBounds.size() < 4)
                throw new NoSuchFieldException("8.0 clock bounds inputs");
            List<DexSignatures.Read> boundReads = beforeBounds.subList(
                    beforeBounds.size() - 4, beforeBounds.size());
            if (mapReads.size() != 3
                    || !mapReads.get(0).field().equals(boundReads.get(0).field())
                    || !mapReads.get(2).field().equals(boundReads.get(1).field()))
                throw new NoSuchFieldException("8.0 clock position repository inputs");
            preview = mapReads.get(1).field().field(loader);
            screen = boundReads.get(1).field().field(loader);
            assist = boundReads.get(2).field().field(loader);
            device = boundReads.get(3).field().field(loader);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not read the 8.0 clock geometry", error);
        }
    }

    private static DexSignatures.Call call(DexSignatures.Body body, int after, Class<?>... params)
            throws NoSuchMethodException {
        var types = java.util.Arrays.stream(params).map(DexSignatures::descriptor).toList();
        return OneUi95Editor.unique(body.calls().stream().filter(c -> c.offset() > after && !c.isStatic()
                && c.result().equals("V") && c.parameters().equals(types)).toList(),
                "8.0 geometry " + types);
    }

    private static List<DexSignatures.Read> reads(DexSignatures.Body body, String owner,
            int after, int before) {
        return body.reads().stream().filter(r -> r.field().owner().equals(owner)
                && r.offset() > after && r.offset() < before).toList();
    }

    int orientation(Object controller) throws ReflectiveOperationException {
        Object display = data(screen, useCase.get(controller));
        return SettingsScope.orientation((Integer) Reflect.call(display, "getWidth"),
                (Integer) Reflect.call(display, "getHeight"));
    }

    float previewScale(Object controller) throws ReflectiveOperationException {
        return ((Number) Reflect.call(data(preview, useCase.get(controller)), "getScale")).floatValue();
    }

    void apply(Object controller, Object draft, boolean resizing, float scale, int dx, int dy)
            throws ReflectiveOperationException {
        Object settings = useCase.get(controller);
        Object position = calculator.get(settings);
        Object display = data(screen, settings);
        Object deviceType = data(device, settings);
        Object clockAssist = data(assist, settings);
        if (resizing) resize.invoke(position, draft, display, deviceType, true, false, scale, false);
        else move.invoke(position, draft, display, dx, dy, clockAssist, deviceType);
        bounds.invoke(position, draft, display, clockAssist, deviceType);
        constrain.invoke(position, draft, display, deviceType, false);
        mapPreview.invoke(position, draft, data(preview, settings), display);
    }

    private static Object data(Field repo, Object owner) throws ReflectiveOperationException {
        return Reflect.call(repo.get(owner), "getData");
    }
}
