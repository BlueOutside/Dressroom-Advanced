package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi9ClockStyleClockSource {
    private OneUi9ClockStyleClockSource() {}

    static Method normalizer(ClassLoader loader, Class<?> type)
            throws ReflectiveOperationException {
        return Class.forName("aod.uq6", false, loader)
                .getDeclaredMethod("T", int.class, type, List.class);
    }

    static List<Method> callers(ClassLoader loader) {
        return List.of();
    }
}
