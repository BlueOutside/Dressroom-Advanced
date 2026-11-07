package dev.denver.dressroomadvanced;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

final class NativeContract {
    private static final Map<ClassLoader, List<String>> dexClassCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NativeContract() {}

    static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(
                    owner.getName() + "." + name + ": expected " + type.getName());
        field.setAccessible(true);
        return field;
    }

    static Field assignableField(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (!type.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(
                    owner.getName() + "." + name + ": expected " + type.getName());
        field.setAccessible(true);
        return field;
    }

    static Method method(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != result
                || method.isBridge()
                || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(
                    owner.getName() + "." + name + ": expected instance " + result.getName());
        method.setAccessible(true);
        return method;
    }

    static Field findField(Class<?> owner, String preferredName, Class<?> type)
            throws NoSuchFieldException {
        if (preferredName != null) {
            try {
                return assignableField(owner, preferredName, type);
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException(
                owner.getName() + " has no field matching " + type.getName());
    }

    static Method findMethod(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        if (preferredName != null) {
            try {
                if (result != null && !containsNull(parameters)) {
                    return method(owner, preferredName, result, parameters);
                }
                for (Method m : owner.getDeclaredMethods()) {
                    if (m.getName().equals(preferredName)
                            && !Modifier.isStatic(m.getModifiers())
                            && !m.isBridge()
                            && (result == null || result.isAssignableFrom(m.getReturnType()))
                            && matchParameters(m.getParameterTypes(), parameters)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && (result == null || result.isAssignableFrom(m.getReturnType()))
                    && matchParameters(m.getParameterTypes(), parameters)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(
                owner.getName()
                        + " has no method matching "
                        + (result != null ? result.getName() : "any"));
    }

    static Method findStaticMethod(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        if (preferredName != null) {
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getName().equals(preferredName)
                        && Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchParameters(m.getParameterTypes(), parameters)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Method m : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && (result == null || result.isAssignableFrom(m.getReturnType()))
                    && matchParameters(m.getParameterTypes(), parameters)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(
                owner.getName()
                        + " has no static method matching "
                        + (result != null ? result.getName() : "any"));
    }

    static Method findMethodPrefix(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... prefixParameters)
            throws NoSuchMethodException {
        if (preferredName != null) {
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getName().equals(preferredName)
                        && !Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchPrefix(m.getParameterTypes(), prefixParameters)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Method m : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && (result == null || result.isAssignableFrom(m.getReturnType()))
                    && matchPrefix(m.getParameterTypes(), prefixParameters)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(
                owner.getName() + " has no method starting with prefix parameters");
    }

    static boolean hasField(Class<?> owner, Class<?> type) {
        try {
            for (Field f : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType()))
                    return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasMethod(Class<?> owner, Class<?> result, Class<?>... parameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchParameters(m.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasStaticMethod(Class<?> owner, Class<?> result, Class<?>... parameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchParameters(m.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasMethodPrefix(Class<?> owner, Class<?> result, Class<?>... prefixParameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchPrefix(m.getParameterTypes(), prefixParameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasConstructor(Class<?> owner, Class<?>... parameters) {
        try {
            for (Constructor<?> c : owner.getDeclaredConstructors()) {
                if (matchParameters(c.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static Class<?> findClass(
            ClassLoader loader, Predicate<Class<?>> signature, String... candidates)
            throws ClassNotFoundException {
        for (String name : candidates) {
            if (name == null || name.isEmpty()) continue;
            try {
                Class<?> cls = Class.forName(name, false, loader);
                if (signature == null || signature.test(cls)) return cls;
            } catch (Throwable ignored) {
            }
        }
        if (signature != null) {
            for (String name : dexClasses(loader)) {
                try {
                    Class<?> cls = Class.forName(name, false, loader);
                    if (signature.test(cls)) return cls;
                } catch (Throwable ignored) {
                }
            }
        }
        throw new ClassNotFoundException(
                "No class matched signature for candidates: " + String.join(", ", candidates));
    }

    @SuppressWarnings("deprecation")
    private static List<String> dexClasses(ClassLoader loader) {
        List<String> cached = dexClassCache.get(loader);
        if (cached != null) return cached;
        List<String> names = new ArrayList<>();
        try {
            Object pathList = Reflect.field(loader, "pathList");
            Object elements = Reflect.field(pathList, "dexElements");
            int length = java.lang.reflect.Array.getLength(elements);
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(elements, i);
                Object dexFile = Reflect.field(element, "dexFile");
                if (dexFile instanceof dalvik.system.DexFile dex) {
                    Enumeration<String> entries = dex.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement();
                        if (isCandidateClassName(entry)) names.add(entry);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        List<String> result = Collections.unmodifiableList(names);
        dexClassCache.put(loader, result);
        return result;
    }

    private static boolean isCandidateClassName(String name) {
        if (name.startsWith("aod.") || name.startsWith("com.samsung.android.lockscreen.editor."))
            return true;
        int dot = name.indexOf('.');
        return dot > 0 && dot <= 3 && name.lastIndexOf('.') == dot;
    }

    private static boolean containsNull(Class<?>[] array) {
        for (Class<?> item : array) if (item == null) return true;
        return false;
    }

    private static boolean matchParameters(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (expected[i] != null && !expected[i].isAssignableFrom(actual[i])) return false;
        }
        return true;
    }

    private static boolean matchPrefix(Class<?>[] actual, Class<?>[] prefix) {
        if (actual.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != null && !prefix[i].isAssignableFrom(actual[i])) return false;
        }
        return true;
    }
}
