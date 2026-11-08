package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi85MediaBinding {
    final Method expanded;
    final List<Method> callers;

    OneUi85MediaBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 media binding");
        ClassLoader loader = base.getClassLoader();
        expanded = NativeContract.method(base, "setMediaExpanded", void.class, boolean.class);
        // These are deoptimized, never hooked or invoked. Follow actual callers of
        // the public setter instead of selecting one of many Object observers.
        var resolved = new java.util.LinkedHashSet<Method>();
        try {
            for (DexSignatures.Body body : DexSignatures.callers(expanded))
                resolved.add(body.method(loader));
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read media expansion callers", invalidDex);
        }
        if (resolved.isEmpty())
            throw new NoSuchMethodException("Native media expansion callers missing");
        callers = List.copyOf(resolved);
    }
}
