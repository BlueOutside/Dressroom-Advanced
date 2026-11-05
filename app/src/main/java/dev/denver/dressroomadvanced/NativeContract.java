package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class NativeContract {
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
}
