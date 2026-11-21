package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class OneUi80Tabs {
    private static final String FOREIGN_TAB = "Tab belongs to a different TabLayout.";
    private static final String ARRAY_LIST = "Ljava/util/ArrayList;";

    private OneUi80Tabs() {}

    static NativeTabs.Binding bind(Class<?> strip) throws ReflectiveOperationException {
        String owner = DexSignatures.descriptor(strip);
        ClassLoader loader = strip.getClassLoader();
        try {
            var styles = new ArrayList<Method>();
            var factories = new ArrayList<Method>();
            var inserts = new ArrayList<Method>();
            for (Method m : strip.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 0 && m.getReturnType().getEnclosingClass() == strip
                        && NativeContract.hasMethod(m.getReturnType(), m.getReturnType(), CharSequence.class)
                        && DexSignatures.body(m).writes().stream().anyMatch(write ->
                                write.field().owner().equals(DexSignatures.descriptor(m.getReturnType()))
                                        && write.field().type().equals(owner))) factories.add(m);
                else if (params.length == 3 && params[0].getEnclosingClass() == strip
                        && params[1] == int.class && params[2] == boolean.class
                        && m.getReturnType() == void.class
                        && DexSignatures.body(m, Set.of(FOREIGN_TAB)).anchors()
                                .containsKey(FOREIGN_TAB)) inserts.add(m);
            }
            Method create = unique(factories, "tab factory");
            Method indexed = unique(inserts, "indexed tab insertion");
            Class<?> tab = create.getReturnType();
            String tabOwner = DexSignatures.descriptor(tab);
            var textMethods = new ArrayList<Method>();
            var selectionMethods = new ArrayList<Method>();
            for (Method m : tab.getDeclaredMethods()) {
                if (m.getReturnType() == tab && List.of(m.getParameterTypes()).equals(List.of(CharSequence.class))
                        && DexSignatures.body(m).calls().stream().anyMatch(call ->
                                call.name().equals("setContentDescription"))) textMethods.add(m);
                if (m.getReturnType() == boolean.class && m.getParameterCount() == 0
                        && calls(DexSignatures.body(m), owner, "getSelectedTabPosition", List.of()))
                    selectionMethods.add(m);
            }
            var position = DexSignatures.body(unique(selectionMethods, "tab selection"))
                    .uniqueRead(tabOwner, "I", null).field(loader);
            var listenerSetters = new ArrayList<Method>();
            var append = new ArrayList<Method>();
            for (Method m : strip.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
                Class<?>[] params = m.getParameterTypes();
                if (List.of(params).equals(List.of(tab, boolean.class))
                        && calls(DexSignatures.body(m), owner, indexed.getName(), List.of(tabOwner, "I", "Z")))
                    append.add(m);
                if (m.getName().equals("setOnTabSelectedListener") && params.length == 1
                        && params[0].isInterface() && NativeContract.hasMethod(params[0], void.class, tab))
                    listenerSetters.add(m);
                if (params.length == 0) {
                    var body = DexSignatures.body(m);
                    if (calls(body, owner, create.getName(), List.of())
                            && calls(body, owner, "getSelectedTabPosition", List.of())
                            && body.calls().stream().anyMatch(call -> call.name().equals("getColorStateList")))
                        styles.add(m);
                }
            }
            Method setter = unique(listenerSetters, "tab listener setter");
            Class<?> listener = setter.getParameterTypes()[0];
            Set<Method> registration = new LinkedHashSet<>();
            for (var call : DexSignatures.body(setter).calls()) {
                if (!call.owner().equals(owner) || call.isStatic()
                        || !call.parameters().equals(List.of(DexSignatures.descriptor(listener)))
                        || call.name().equals(setter.getName())) continue;
                Method candidate = call.method(loader);
                if (calls(DexSignatures.body(candidate), ARRAY_LIST, "add", List.of("Ljava/lang/Object;")))
                    registration.add(candidate);
            }
            return new NativeTabs.Binding(create, unique(textMethods, "tab title"),
                    unique(append, "append tab"), unique(List.copyOf(registration), "tab listener registration"),
                    unique(styles, "sub-tab style"), position, listener);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not read legacy tab signatures", error);
        }
    }

    private static boolean calls(DexSignatures.Body body, String owner, String name, List<String> params) {
        return body.calls().stream().anyMatch(call ->
                call.owner().equals(owner) && call.name().equals(name) && call.parameters().equals(params));
    }

    private static Method unique(List<Method> methods, String role) throws NoSuchMethodException {
        if (methods.size() != 1)
            throw new NoSuchMethodException("Expected one One UI 8.0 " + role + ", found " + methods.size());
        Method method = methods.get(0);
        method.setAccessible(true);
        return method;
    }
}
