package dev.denver.dressroomadvanced;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/** The ambient configuration reads the wallpaper's font independently of the keyguard provider. */
record OneUi95ClockPlacement(
        Method configuration, Field context, Field clockInfoCarrier, Field clockInfo,
        Field fontIndex, Field fontPath, Field heights) {
    static Method detachedClock(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        String anchor = "getClockView use cached clock - ";
        String view = "Lcom/samsung/android/uniform/widget/clock/AbsClockView;";
        String font = "Lcom/samsung/android/clockpack/plugins/clock/ClockFontData;";
        return OneUi95Editor.unique(DexSignatures.find(loader, Set.of(anchor)).stream()
                .filter(body -> body.parameters().equals(List.of("Ljava/util/List;", "I"))
                        && body.result().equals(view)
                        && body.calls().stream().anyMatch(call -> call.name().equals("setFontType")
                                && call.owner().equals(view)
                                && call.parameters().equals(List.of(font)))
                        && body.calls().stream().anyMatch(call -> call.name().equals("measure")
                                && call.parameters().equals(List.of("I", "I"))))
                .toList(), "detached clock measurement").method(loader);
    }

    static OneUi95ClockPlacement resolve(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        String owner = "Lcom/samsung/android/app/aodservice/controller/configuration/AODConfigurationController;";
        String config = "Lcom/samsung/android/app/aodservice/content/AODConfiguration;";
        String info = "Lcom/samsung/android/clockpack/plugins/clock/ClockInfo;";
        Set<String> anchors = Set.of("getFontHeights: from WallpaperDataBundle (size=",
                "getFontHeights: from ClockItem (size=");
        var body = OneUi95Editor.unique(DexSignatures.find(loader, anchors).stream()
                .filter(candidate -> candidate.owner().equals(owner)
                        && candidate.result().equals(config)
                        && candidate.parameters().equals(List.of(
                                "Lcom/android/systemui/plugins/aod/PluginAOD$UICallback;"))
                        && candidate.anchors().keySet().containsAll(anchors)).toList(),
                "AOD clock configuration");
        var clockInfo = OneUi95Editor.unique(body.writes().stream()
                .map(DexSignatures.Write::field).distinct().filter(field -> field.type().equals(info))
                .toList(), "AOD clock info");
        String carrier = clockInfo.owner();
        Field clockInfoCarrier = body.uniqueRead(config, carrier, null).field(loader);
        Field heights = body.uniqueWrite(carrier, "Ljava/util/List;", null).field(loader);
        int fontTypeCall = body.calls().stream()
                .filter(call -> call.name().equals("getPresetFontIndex")
                        && call.owner().equals("Lcom/samsung/android/sdk/aodservice/entities/font/Font;"))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("AOD preset font read"))
                .offset();
        int fontPathCall = body.calls().stream()
                .filter(call -> call.name().equals("getFontPath")
                        && call.owner().equals("Lcom/samsung/android/sdk/aodservice/entities/font/Font;"))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("AOD font path read"))
                .offset();
        var fontIndex = OneUi95Editor.unique(body.writes().stream()
                .filter(write -> write.offset() > fontTypeCall && write.offset() < fontPathCall
                        && write.field().owner().equals(carrier) && write.field().type().equals("I"))
                .map(DexSignatures.Write::field).distinct().toList(), "AOD preset font index");
        Field path = body.uniqueWrite(carrier, "Ljava/lang/String;", null).field(loader);
        Field context = body.reads().stream()
                .filter(read -> read.field().owner().equals(owner)
                        && read.field().type().equals("Landroid/content/Context;"))
                .findFirst().orElseThrow(() -> new NoSuchFieldException("AOD configuration context"))
                .field().field(loader);
        return new OneUi95ClockPlacement(body.method(loader), context, clockInfoCarrier,
                clockInfo.field(loader), fontIndex.field(loader), path, heights);
    }

    void apply(Object controller, Object result) throws ReflectiveOperationException {
        Object clock = result == null ? null : clockInfoCarrier.get(result);
        if (clock == null || clockInfo.get(clock) == null) return;
        // Null is Samsung's explicit no-stretch path for this ambient configuration.
        if (!(heights.get(clock) instanceof List<?> nativeHeights) || nativeHeights.isEmpty()) return;
        Context screen = (Context) context.get(controller);
        int type = (Integer) Reflect.call(clockInfo.get(clock), "getClockType");
        List<Float> values = RuntimeClockData.savedHeights(screen, type, fontIndex.getInt(clock),
                (String) fontPath.get(clock));
        if (values != null && !nativeHeights.equals(values)) heights.set(clock, values);
    }
}
