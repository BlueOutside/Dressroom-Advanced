package dev.denver.dressroomadvanced;

import android.view.View;
import android.widget.FrameLayout;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class OneUi95Editor {
    private static final String ENTITY =
            "com.samsung.android.lockscreen.editor.item.domain.entity.";
    private static final String TAB_LAYOUT = "Lcom/google/android/material/tabs/TabLayout;";
    private static final String CLICK = "onClick";
    private static final String CLOCK_CHANGE = "onClockTypeChanged: ";
    private static final String WEATHER = "EnableWeatherInfoUseCase";

    record ClockPanel(
            Method open, Method create, Method bind, Method repo, Method weather,
            Field root, Field dataUseCase, Field helper, Field clock, Field model, Field activity,
            Field scaleUseCase, Field scaleRepo, Field managerWrapper, Method manager,
            Field focusedSceneUseCase, Method focusedSceneRepo) {}

    static String rowTag(EditorBinding.Row row) {
        return switch (row) {
            case TABS -> "binding_1";
            case LABEL -> "binding_13";
            case TOGGLE -> "binding_12";
            case SLIDER -> "binding_6";
        };
    }

    static void editing(Object model, boolean changing) throws ReflectiveOperationException {
        Class<?> type = model.getClass();
        NativeContract.findStaticMethod(type, null, void.class, type, boolean.class)
                .invoke(null, model, changing);
    }

    static void weather(ClockPanel panel, Object model, boolean show)
            throws ReflectiveOperationException {
        weather(panel, model, show, true);
    }

    static void weather(ClockPanel panel, Object model, boolean show, boolean reserved)
            throws ReflectiveOperationException {
        Field useCase = NativeContract.findField(model.getClass(), null,
                panel.weather().getDeclaringClass());
        panel.weather().invoke(useCase.get(model), show, reserved);
    }

    static Class<?> resolve(ClassLoader loader, Class<?> clockData)
            throws ReflectiveOperationException {
        try {
            return unique(DexSignatures.find(loader, Set.of(CLOCK_CHANGE)).stream()
                    .filter(body -> body.parameters().equals(List.of(
                            "Landroid/widget/FrameLayout;", DexSignatures.descriptor(clockData)))
                            && body.result().equals("V") && (body.access() & 8) == 0).toList(),
                    "clock preview creator").loadOwner(loader);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not read clock preview creator", error);
        }
    }

    static ClockPanel bind(Class<?> owner, Class<?> clockData, Class<?> clockView)
            throws ReflectiveOperationException {
        ClassLoader loader = owner.getClassLoader();
        String controller = DexSignatures.descriptor(owner);
        try {
            Method create = NativeContract.findMethod(owner, null, void.class,
                    FrameLayout.class, clockData);
            DexSignatures.Body click = openBody(loader, controller);
            Method open = click.method(loader);
            Field root = panelRoot(click, loader, controller);
            Field activity = NativeContract.findField(owner, null, WeakReference.class);

            Class<?> observable = Class.forName(ENTITY + "common.ObservableData", false, loader);
            int firstData = click.calls().stream()
                    .filter(call -> call.name().equals("getData") && call.parameters().isEmpty()
                            && call.result().equals("Ljava/lang/Object;"))
                    .findFirst().orElseThrow(() -> new NoSuchMethodException("Clock data read"))
                    .offset();
            Field dataUseCase = uniqueField(click, controller, firstData,
                    loader, field -> NativeContract.hasMethod(field.getType(), observable),
                    "clock repository use case");
            Method repo = NativeContract.findMethod(dataUseCase.getType(), null, observable);
            Field model = uniqueField(click, controller,
                    call(click, "Landroid/view/LayoutInflater;", "from").offset(),
                    loader, field -> NativeContract.hasMethod(field.getType(), Object.class)
                            && hasGet(field.getType()), "clock panel model");

            DexSignatures.Body creation = DexSignatures.body(create);
            Method bind = unique(creation.calls().stream()
                    .filter(call -> call.parameters().equals(List.of("Landroid/view/View;"))
                            && call.result().equals("V")
                            && !call.isStatic()
                            && !call.owner().startsWith("Landroid/"))
                    .filter(call -> writesClock(call, loader, clockView))
                    .map(call -> new DexSignatures.Call(0, call.owner(), call.name(),
                            call.parameters(), call.result(), false))
                    .distinct().toList(), "clock view setter").method(loader);
            Field helper = NativeContract.findField(owner, null, bind.getDeclaringClass());
            Field clock = NativeContract.findField(bind.getDeclaringClass(), null, clockView);

            Class<?> managerType = Class.forName(
                    "com.samsung.android.clockpack.plugins.clock.ClockManager", false, loader);
            Field wrapper = uniqueField(creation, controller, Integer.MAX_VALUE, loader,
                    field -> NativeContract.hasMethod(field.getType(), managerType),
                    "clock manager wrapper");
            Method manager = NativeContract.findMethod(wrapper.getType(), null, managerType);

            DexSignatures.Call scale = call(click,
                    "Lcom/samsung/android/lockscreen/editor/item/domain/entity/system/PreviewScale;",
                    "getScale");
            Field scaleUseCase = null, scaleRepo = null;
            List<DexSignatures.Read> reads = click.reads();
            for (int i = 1; i < reads.size() && reads.get(i).offset() < scale.offset(); i++) {
                var nested = reads.get(i).field();
                var outer = reads.get(i - 1).field();
                if (!outer.owner().equals(controller) || !outer.type().equals(nested.owner())
                        || !observable.isAssignableFrom(
                                nested.field(loader).getType())) continue;
                scaleUseCase = outer.field(loader);
                scaleRepo = nested.field(loader);
            }
            if (scaleUseCase == null) throw new NoSuchFieldException("Missing preview scale");

            Class<?> useCase = unique(DexSignatures.find(loader, Set.of(WEATHER)).stream()
                    .filter(body -> body.name().equals("getTag") && body.parameters().isEmpty())
                    .toList(), "weather use case").loadOwner(loader);
            Method weather = NativeContract.findMethod(useCase, null, void.class,
                    boolean.class, boolean.class);
            Field focusedSceneUseCase = null;
            Method focusedSceneRepo = null;
            try {
                Class<?> sceneType = Class.forName(
                        ENTITY + "item.clock.wallpaperbundle.WallpaperScene", false, loader);
                String sceneDesc = DexSignatures.descriptor(sceneType);
                List<Field> sceneFields = new ArrayList<>();
                for (Field candidate : owner.getDeclaredFields()) {
                    if (Modifier.isStatic(candidate.getModifiers())
                            || !NativeContract.hasMethod(candidate.getType(), observable)) continue;
                    if (declaresSceneRepo(candidate.getType(), observable, sceneDesc)) {
                        candidate.setAccessible(true);
                        sceneFields.add(candidate);
                    }
                }
                if (sceneFields.size() == 1) {
                    focusedSceneUseCase = sceneFields.get(0);
                    focusedSceneRepo = NativeContract.findMethod(
                            focusedSceneUseCase.getType(), null, observable);
                }
            } catch (ReflectiveOperationException | IOException ignored) {
            }
            return new ClockPanel(open, create, bind, repo, weather, root, dataUseCase, helper,
                    clock, model, activity, scaleUseCase, scaleRepo, wrapper, manager,
                    focusedSceneUseCase, focusedSceneRepo);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not read beta clock panel bindings", error);
        }
    }

    private static boolean declaresSceneRepo(
            Class<?> useCaseType, Class<?> observable, String sceneDesc)
            throws ReflectiveOperationException, IOException {
        for (Field repoField : useCaseType.getDeclaredFields()) {
            if (Modifier.isStatic(repoField.getModifiers())
                    || !observable.isAssignableFrom(repoField.getType())) continue;
            for (Method m : repoField.getType().getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
                for (DexSignatures.Call call : DexSignatures.body(m).calls()) {
                    if (call.owner().equals(sceneDesc) && call.name().equals("<init>")) return true;
                }
            }
        }
        return false;
    }

    static DexSignatures.Body openBody(ClassLoader loader, String controller)
            throws IOException, ReflectiveOperationException {
        return unique(DexSignatures.find(loader, Set.of(CLICK, "getRoot(...)")).stream()
                .filter(body -> (controller == null || body.owner().equals(controller))
                        && body.parameters().isEmpty() && body.result().equals("Z")
                        && (body.access() & 8) == 0 && body.anchors().containsKey(CLICK)
                        && body.anchors().containsKey("getRoot(...)")
                        && body.calls().stream().anyMatch(call -> call.owner().equals(TAB_LAYOUT)
                                && call.name().equals("setTabTextColors"))).toList(),
                "clock panel opener");
    }

    private static Field panelRoot(DexSignatures.Body body, ClassLoader loader, String owner)
            throws ReflectiveOperationException {
        int after = call(body, TAB_LAYOUT, "setSelectedTabIndicatorColor").offset();
        return body.writes().stream()
                .filter(write -> write.offset() > after && write.field().owner().equals(owner)
                        && write.field().type().equals("Landroid/view/View;"))
                .findFirst().orElseThrow(() -> new NoSuchFieldException("Missing clock panel root"))
                .field().field(loader);
    }

    private static boolean hasGet(Class<?> type) {
        try {
            return type.getMethod("get").getReturnType() == Object.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean writesClock(
            DexSignatures.Call call, ClassLoader loader, Class<?> clockView) {
        try {
            return DexSignatures.body(call.method(loader), Set.of("setClockView: "))
                    .anchors().containsKey("setClockView: ")
                    && NativeContract.hasField(call.method(loader).getDeclaringClass(), clockView);
        } catch (ReflectiveOperationException | IOException ignored) {
            return false;
        }
    }

    private static Field uniqueField(DexSignatures.Body body, String owner, int limit,
            ClassLoader loader, java.util.function.Predicate<Field> match, String role)
            throws ReflectiveOperationException {
        Set<Field> fields = new LinkedHashSet<>();
        for (var read : body.reads()) {
            if (read.offset() >= limit || !read.field().owner().equals(owner)) continue;
            Field field = read.field().field(loader);
            if (match.test(field)) fields.add(field);
        }
        return unique(List.copyOf(fields), role);
    }

    static DexSignatures.Call call(DexSignatures.Body body, String owner, String name)
            throws NoSuchMethodException {
        return body.calls().stream().filter(call -> call.owner().equals(owner)
                && call.name().equals(name)).findFirst()
                .orElseThrow(() -> new NoSuchMethodException(owner + "." + name));
    }

    static <T> T unique(List<T> matches, String role) throws NoSuchMethodException {
        if (matches.size() != 1)
            throw new NoSuchMethodException("Expected one " + role + ", found " + matches.size());
        return matches.get(0);
    }
}
