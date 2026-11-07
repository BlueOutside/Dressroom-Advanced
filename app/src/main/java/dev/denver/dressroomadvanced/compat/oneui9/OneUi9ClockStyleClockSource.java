package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi9ClockStyleClockSource {
    private OneUi9ClockStyleClockSource() {}

    static Method normalizer(ClassLoader loader, Class<?> type)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasStaticMethod(
                                        cls, List.class, int.class, type, List.class),
                        "aod.uq6",
                        "aod.qn1");
        return NativeContract.findStaticMethod(
                owner, "T", List.class, int.class, type, List.class);
    }

    static List<Method> callers(ClassLoader loader) {
        return List.of();
    }
}
