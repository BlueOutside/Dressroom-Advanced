package dev.denver.dressroomadvanced;

/** Inspected AOD 8.9.57.1 names; existing providers retain their original lookups. */
final class OneUi9Aod8957Runtime {
    private OneUi9Aod8957Runtime() {}

    private static final java.util.Map<ClassLoader, Boolean> providers =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final ClassValue<Boolean> clockViews = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> base = type; base != null; base = base.getSuperclass())
                if (base.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")) return true;
            return false;
        }
    };

    static boolean matches(ClassLoader loader) {
        Boolean known = providers.get(loader);
        if (known != null) return known;
        boolean match = discover(loader);
        providers.put(loader, match);
        return match;
    }

    private static boolean discover(ClassLoader loader) {
        try {
            Class<?> base = Class.forName("com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
            return ClockBinding.of(base).aod8957;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static Class<?> type(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        if (name.startsWith("aod.") && matches(loader)) name = switch (name) {
            case "aod.tx0" -> "aod.jx0";
            case "aod.xb0" -> "aod.qb0";
            case "aod.cq0" -> "aod.sp0";
            case "aod.yp0" -> "aod.op0";
            case "aod.e70" -> "aod.x60";
            case "aod.n16" -> "aod.i16";
            case "aod.rv2" -> "aod.ev2";
            case "aod.l49" -> "aod.q39";
            case "aod.fa2" -> "aod.u92";
            case "aod.ag4" -> "aod.wf4";
            case "aod.o8" -> "aod.k8";
            case "aod.n82" -> "aod.a82";
            case "aod.di1" -> "aod.dp1";
            default -> name;
        };
        return Class.forName(name, initialize, loader);
    }

    static Object field(Object owner, String name) throws ReflectiveOperationException {
        if (owner != null && clockViews.get(owner.getClass()) && matches(owner.getClass().getClassLoader())) {
            name = switch (name) {
                case "h1" -> "f1";
                case "b0" -> "a0";
                case "l0" -> "k0";
                case "L0" -> "J0";
                case "W0" -> "U0";
                case "t0" -> "s0";
                case "d1" -> "b1";
                default -> name;
            };
        }
        return Reflect.field(owner, name);
    }
}
