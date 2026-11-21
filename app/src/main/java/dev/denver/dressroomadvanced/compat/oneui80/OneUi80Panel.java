package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.graphics.Region;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

record OneUi80Panel(Method open, Method create, Method bind, Method readRepo,
        Method weather, Method weatherSupported, Field root, Field activity,
        Field dataUseCase, Field helper, Field clock, Field model, Field managerWrapper,
        Stretch stretch) {
    private static final String VIEW = "Landroid/view/View;";
    private static final String OBSERVABLE = "Lcom/samsung/android/lockscreen/editor/item/domain/entity/common/ObservableData;";
    private static final String CATEGORY = "Lcom/samsung/android/clockpack/plugins/Category;";
    private static final String WEATHER = "EnableWeatherInfoUseCase";
    private static final String STRETCH = "ApplyStretchResultUseCase";

    private record Stretch(Field useCase, Field trimmer, Method heights) {
        @SuppressWarnings("unchecked")
        List<Float> heights(Object owner, List<Rect> minimum, List<Rect> target,
                int padding, float scale) throws ReflectiveOperationException {
            return (List<Float>) heights.invoke(trimmer.get(useCase.get(owner)),
                    minimum, target, padding, scale);
        }
    }

    static OneUi80Panel bind(Class<?> controller, Class<?> data, Class<?> clockType)
            throws ReflectiveOperationException {
        ClassLoader loader = controller.getClassLoader();
        String owner = DexSignatures.descriptor(controller);
        String change = "onClockTypeChanged: ";
        String weatherListener = "registerWeatherInfoUpdateListener";
        try {
            var bodies = DexSignatures.find(loader, Set.of("onClick", "panelView",
                    "setClockView: ", change, weatherListener, WEATHER, STRETCH));
            var panel = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.owner().equals(owner) && b.anchors().containsKey("panelView")
                            && b.parameters().isEmpty() && b.result().equals(VIEW)
                            && b.calls().stream().anyMatch(call ->
                                    call.owner().equals("Lcom/google/android/material/tabs/TabLayout;")))
                    .toList(), "8.0 clock panel factory");
            var open = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.owner().equals(owner) && b.anchors().containsKey("onClick")
                            && b.parameters().isEmpty() && b.result().equals("Z")
                            && b.calls().stream().anyMatch(call ->
                                    call.owner().equals(owner) && call.name().equals(panel.name())))
                    .toList(), "8.0 clock panel opener");
            var readRepo = open.calls().stream()
                    .filter(call -> call.parameters().isEmpty() && call.result().equals(OBSERVABLE)
                            && !call.isStatic())
                    .findFirst().orElseThrow(() -> new NoSuchMethodException("8.0 clock repository"));
            Method repo = readRepo.method(loader);
            Field dataUseCase = OneUi95Editor.unique(open.reads().stream()
                    .filter(read -> read.field().owner().equals(owner) && read.offset() < readRepo.offset())
                    .toList(), "8.0 clock repository owner").field().field(loader);
            Method create = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.owner().equals(owner) && b.anchors().containsKey(change)
                            && b.parameters().equals(List.of(DexSignatures.descriptor(ViewGroup.class),
                                    DexSignatures.descriptor(data))) && b.result().equals("V"))
                    .toList(), "8.0 clock preview creation").method(loader);
            Class<?> helper = Class.forName(
                    "com.samsung.android.lockscreen.editor.item.core.util.b", false, loader);
            String helperOwner = DexSignatures.descriptor(helper);
            Method bind = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.owner().equals(helperOwner) && b.anchors().containsKey("setClockView: ")
                            && b.parameters().equals(List.of(VIEW)) && b.result().equals("V"))
                    .toList(), "8.0 clock view binding").method(loader);
            var weatherRegistration = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.owner().equals(owner) && b.anchors().containsKey(weatherListener))
                    .toList(), "8.0 clock weather listener");
            Method weatherSupported = OneUi95Editor.unique(weatherRegistration.calls().stream()
                    .filter(call -> call.parameters().equals(List.of(CATEGORY, "I"))
                            && call.result().equals("Z") && !call.isStatic())
                    .toList(), "8.0 clock weather capability").method(loader);
            Class<?> weatherOwner = OneUi95Editor.unique(bodies.stream()
                    .filter(b -> b.name().equals("<init>") && b.anchors().containsKey(WEATHER))
                    .toList(), "8.0 weather use case").loadOwner(loader);
            Method weather = NativeContract.findMethod(weatherOwner, null, void.class, boolean.class);
            return new OneUi80Panel(open.method(loader), create, bind, repo, weather,
                    weatherSupported,
                    panel.uniqueWrite(owner, VIEW, null).field(loader),
                    NativeContract.findField(controller, null, WeakReference.class),
                    dataUseCase, NativeContract.findField(controller, null, helper),
                    NativeContract.findField(helper, null, clockType),
                    panel.uniqueRead(owner, "Ljavax/inject/Provider;", null).field(loader),
                    NativeContract.findField(controller, null, weatherSupported.getDeclaringClass()),
                    bindStretch(controller, data, bodies));
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the One UI 8.0 clock panel", error);
        }
    }

    private static Stretch bindStretch(Class<?> owner, Class<?> clockData, List<DexSignatures.Body> bodies)
            throws ReflectiveOperationException, IOException {
        ClassLoader loader = owner.getClassLoader();
        Class<?> measured = Class.forName(
                "com.samsung.android.clockpack.plugins.clock.StretchMeasureData", false, loader);
        Class<?> stretchType = OneUi95Editor.unique(bodies.stream()
                .filter(b -> b.name().equals("<init>") && b.anchors().containsKey(STRETCH))
                .toList(), "8.0 stretch result use case").loadOwner(loader);
        Method apply = NativeContract.findMethod(stretchType, null, void.class,
                boolean.class, measured, List.class, Region.class);
        var inputs = List.of(DexSignatures.descriptor(clockData),
                DexSignatures.descriptor(measured), "Ljava/util/List;");
        Class<?> trimmer = OneUi95Editor.unique(DexSignatures.body(apply).calls().stream()
                .filter(call -> !call.isStatic() && call.result().equals("V")
                        && call.parameters().equals(inputs)).toList(),
                "8.0 stretch trimmer").method(loader).getDeclaringClass();
        Method calculate = NativeContract.findMethod(trimmer, null, List.class,
                List.class, List.class, int.class, float.class);
        return new Stretch(NativeContract.findField(owner, null, stretchType),
                NativeContract.findField(stretchType, null, trimmer), calculate);
    }

    List<Float> heights(Object owner, List<Rect> minimum, List<Rect> target, int padding, float scale)
            throws ReflectiveOperationException {
        return stretch.heights(owner, minimum, target, padding, scale);
    }
}
