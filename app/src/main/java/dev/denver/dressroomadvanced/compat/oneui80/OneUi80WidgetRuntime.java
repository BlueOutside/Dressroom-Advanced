package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class OneUi80WidgetRuntime {
    private static final String HOST =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";
    private OneUi80WidgetRuntime() {}

    private static final ClassValue<Grid> grids = new ClassValue<>() {
        @Override
        protected Grid computeValue(Class<?> type) {
            try {
                return bindGrid(type);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("8.0 native grid is unavailable", error);
            }
        }
    };

    record Grid(Method manager, Field id, Method find, Method items, Method arrange) {
        boolean reflow(Object grid) throws ReflectiveOperationException {
            Object key = id.get(grid);
            Object owner = manager.invoke(grid);
            if (key == null || !find.getDeclaringClass().isInstance(owner)) return true;
            Object container = find.invoke(owner, key);
            if (container == null) return true;
            return Boolean.TRUE.equals(arrange.invoke(
                    container, items.invoke(container), Collections.emptySet(), true));
        }
    }

    static Grid grid(Class<?> grid) {
        return grids.get(grid);
    }

    private static Grid bindGrid(Class<?> grid) throws ReflectiveOperationException {
        Method manager = grid.getDeclaredMethod("getGridManager");
        manager.setAccessible(true);
        Method each = NativeContract.method(grid, "forEachWidgetView", void.class, Consumer.class);
        try {
            var body = DexSignatures.body(each);
            ClassLoader loader = grid.getClassLoader();
            Field id = body.uniqueRead(DexSignatures.descriptor(grid), "Ljava/lang/String;", null)
                    .field(loader);
            var matches = new LinkedHashMap<Method, Integer>();
            for (var call : body.calls()) {
                if (call.isStatic() || !call.parameters().equals(List.of("Ljava/lang/String;"))
                        || !call.result().startsWith("L")) continue;
                Method candidate = call.method(loader);
                Class<?> lookupOwner = candidate.getDeclaringClass();
                // The controller returns an interface and casts it before the grid lookup.
                if (lookupOwner.isAssignableFrom(manager.getReturnType())
                        || manager.getReturnType().isAssignableFrom(lookupOwner))
                    matches.putIfAbsent(candidate, call.offset());
            }
            Method find = OneUi95Editor.unique(List.copyOf(matches.keySet()), "8.0 grid lookup");
            int lookupOffset = matches.get(find);
            var itemGetters = new LinkedHashSet<Method>();
            for (var call : body.calls()) {
                if (call.offset() <= lookupOffset || call.isStatic() || !call.parameters().isEmpty()
                        || !call.result().equals("Ljava/util/List;")) continue;
                Method getter = call.method(loader);
                if (find.getReturnType().isAssignableFrom(getter.getDeclaringClass()))
                    itemGetters.add(getter);
            }
            Method items = OneUi95Editor.unique(List.copyOf(itemGetters), "8.0 grid cells");
            Method arrange = NativeContract.findMethod(items.getDeclaringClass(), null, boolean.class,
                    List.class, Set.class, boolean.class);
            return new Grid(manager, id, find, items, arrange);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the 8.0 grid lookup", error);
        }
    }

    static Method addHost(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> grid = Class.forName(WidgetEngine.GRID, false, loader);
        Class<?> entry = Class.forName(
                "com.samsung.android.clockpack.plugins.clock.module.ModuleInfo", false, loader);
        NativeContract.method(entry, "getWidth", int.class);
        NativeContract.method(entry, "getHeight", int.class);
        return NativeContract.method(grid, "addGridItem", void.class,
                entry, View.class, boolean.class);
    }

    static List<Method> updates(Class<?> controller) throws NoSuchMethodException {
        return List.of(
                NativeContract.method(controller, "initialize", void.class, ViewGroup.class),
                NativeContract.method(controller, "initialize", void.class),
                NativeContract.method(controller, "setDarkAmount", void.class, float.class),
                NativeContract.method(controller, "setDozing", void.class, boolean.class),
                NativeContract.method(
                        controller, "updateWidgetLayout", void.class, String.class, boolean.class),
                NativeContract.method(controller, "onCompleteUpdate", void.class, Map.class));
    }

    static List<Method> blurSubmissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> host = Class.forName(HOST, false, loader);
        Method refresh = NativeContract.method(host, "refreshBlur", void.class, float.class);
        try {
            var body = DexSignatures.body(refresh);
            var apply = OneUi95Editor.unique(body.calls().stream()
                    .filter(call -> call.isStatic() && call.result().equals("V")
                            && call.parameters().equals(List.of(
                                    "Landroid/view/View;", "F", "[F", "Ljava/lang/Integer;")))
                    .toList(), "8.0 widget blur submission");
            var callers = new LinkedHashSet<Method>();
            callers.add(refresh);
            callers.add(apply.method(loader));
            Class<?> info = Class.forName("android.view.SemBlurInfo");
            for (var call : DexSignatures.callers(loader, View.class, "semSetBlurInfo", void.class, info)) {
                if (call.owner().equals(apply.owner()) || call.owner().equals(DexSignatures.descriptor(host))
                        || call.owner().startsWith("Lcom/samsung/android/app/facewidget/presentation/NowBar"))
                    callers.add(call.method(loader));
            }
            return List.copyOf(callers);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the 8.0 blur submission", error);
        }
    }
}
