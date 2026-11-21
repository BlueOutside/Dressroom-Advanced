package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class OneUi95Tabs {
    static NativeTabs.Binding bind(Class<?> strip) throws ReflectiveOperationException {
        ClassLoader loader = strip.getClassLoader();
        String owner = DexSignatures.descriptor(strip);
        try {
            Method create = OneUi95Editor.unique(Arrays.stream(strip.getDeclaredMethods())
                    .filter(method -> !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 0
                            && method.getReturnType().getPackageName().equals(strip.getPackageName())
                            && NativeContract.hasMethod(method.getReturnType(), void.class,
                                    CharSequence.class)
                            && NativeContract.hasField(method.getReturnType(), strip))
                    .toList(), "TabLayout factory");
            create.setAccessible(true);
            Class<?> tab = create.getReturnType();
            Method text = NativeContract.findMethod(tab, null, void.class, CharSequence.class);
            Class<?> listener = OneUi95Editor.unique(Arrays.stream(strip.getDeclaredMethods())
                    .filter(method -> method.getName().equals("setOnTabSelectedListener")
                            && method.getParameterCount() == 1)
                    .map(method -> method.getParameterTypes()[0])
                    .filter(type -> type.isInterface() && Arrays.stream(type.getDeclaredMethods())
                            .anyMatch(method -> method.getName().equals("onTabSelected")
                                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] {tab})))
                    .toList(), "TabLayout listener");
            Method listenerSetter = NativeContract.method(strip, "setOnTabSelectedListener",
                    void.class, listener);
            Method listen = OneUi95Editor.unique(DexSignatures.body(listenerSetter).calls().stream()
                    .filter(call -> call.owner().equals(owner) && !call.isStatic()
                            && call.result().equals("V")
                            && call.parameters().equals(List.of(DexSignatures.descriptor(listener)))
                            && !call.name().equals(listenerSetter.getName()))
                    .toList(), "native add-tab-listener call").method(loader);

            String insertAnchor = "Tab belongs to a different TabLayout.";
            Method add = null;
            Field position = null;
            for (Method method : strip.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || !Arrays.equals(method.getParameterTypes(), new Class<?>[] {tab, boolean.class}))
                    continue;
                var body = DexSignatures.body(method, Set.of(insertAnchor));
                if (!body.anchors().containsKey(insertAnchor)
                        || body.calls().stream().noneMatch(call -> call.owner().equals("Ljava/util/ArrayList;")
                                && call.name().equals("add")
                                && call.parameters().equals(List.of("I", "Ljava/lang/Object;"))))
                    continue;
                if (add != null) throw new NoSuchMethodException("Multiple tab insertion methods");
                add = method;
                add.setAccessible(true);
                position = body.uniqueWrite(DexSignatures.descriptor(tab), "I", null).field(loader);
            }
            if (add == null) throw new NoSuchMethodException("Missing tab insertion");

            DexSignatures.Body open = OneUi95Editor.openBody(loader, null);
            int before = OneUi95Editor.call(open, owner, "setTabTextColors").offset();
            Method style = OneUi95Editor.unique(open.calls().stream()
                    .filter(call -> call.offset() < before && call.owner().equals(owner)
                            && call.parameters().isEmpty() && call.result().equals("V")
                            && !call.isStatic()).toList(), "native sub-tab style").method(loader);
            return new NativeTabs.Binding(create, text, add, listen, style, position, listener);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not read beta native tab bindings", error);
        }
    }
}
