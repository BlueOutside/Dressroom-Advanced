package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native glass owners are found by contract; scalar hints remain type checked. */
final class ClockGlassBinding {
    final Field manager, dozing, strategy;
    final Method refresh;
    private static final ClassValue<ClockGlassBinding> bindings = new ClassValue<>() {
        @Override protected ClockGlassBinding computeValue(Class<?> type) {
            try { return new ClockGlassBinding(type); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Clock glass ABI unavailable", error); }
        }
    };
    static ClockGlassBinding of(Object clock) { return bindings.get(clock.getClass()); }
    static ClockGlassBinding of(Class<?> clock) { return bindings.get(clock); }
    private ClockGlassBinding(Class<?> clockBase) throws ReflectiveOperationException {
        Class<?> base = ClockBlurRetryBinding.absClockView(clockBase);
        ClockBlurRetryBinding retry = ClockBlurRetryBinding.of(base);
        manager = retry.manager;
        strategy = retry.strategy;
        try {
            String absDesc = DexSignatures.descriptor(base);
            dozing =
                    DexSignatures.body(base.getDeclaredMethod("setDozing", boolean.class))
                            .uniqueRead(absDesc, "Z", null)
                            .field(base.getClassLoader());
            refresh = resolveRefresh(base, absDesc);
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException("Could not inspect clock glass DEX", invalidDex);
        }
    }

    private static Method resolveRefresh(Class<?> base, String absDesc)
            throws ReflectiveOperationException, java.io.IOException {
        java.util.Set<Method> inRemove = selfVoidCalls(base, absDesc, "removeBlur");
        java.util.Set<Method> inEnable =
                selfVoidCalls(base, absDesc, "setBlurEffectEnabled", boolean.class);
        inRemove.retainAll(inEnable);
        if (inRemove.size() != 1)
            throw new NoSuchMethodException("Ambiguous clock glass refresh: " + inRemove.size());
        return inRemove.iterator().next();
    }

    private static java.util.Set<Method> selfVoidCalls(
            Class<?> base, String absDesc, String name, Class<?>... params)
            throws ReflectiveOperationException, java.io.IOException {
        ClassLoader loader = base.getClassLoader();
        java.util.Set<Method> calls = new java.util.LinkedHashSet<>();
        for (DexSignatures.Call call :
                DexSignatures.body(base.getDeclaredMethod(name, params)).calls()) {
            if (!call.isStatic()
                    && call.owner().equals(absDesc)
                    && call.result().equals("V")
                    && call.parameters().isEmpty()) {
                calls.add(call.method(loader));
            }
        }
        return calls;
    }
}
