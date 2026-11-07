package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class ClockBinding {
    final boolean legacy;
    final boolean clockStyle;
    final Method balance, render;
    final int balanceHeights;
    private final Field font, fontId;
    private final Method refreshAppearance;
    private static final ClassValue<ClockBinding> bindings =
            new ClassValue<>() {
                @Override
                protected ClockBinding computeValue(Class<?> clock) {
                    Class<?> base = clock;
                    while (base != null
                            && !base.getName()
                                    .equals(
                                            "com.samsung.android.uniform.widget.clock.AbsClockView"))
                        base = base.getSuperclass();
                    if (base == null)
                        throw new IllegalStateException(
                                "Unknown clock provider: " + clock.getName());
                    try {
                        return new ClockBinding(base);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported clock provider", error);
                    }
                }
            };

    static ClockBinding of(Object clock) {
        return bindings.get(clock instanceof Class<?> ? (Class<?>) clock : clock.getClass());
    }

    private ClockBinding(Class<?> base) throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        Class<?> type =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);

        Field fontField = null;
        for (String candidate : new String[] {"L0", "T0", "S0"}) {
            try {
                fontField = NativeContract.field(base, candidate, type);
                break;
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (fontField == null) {
            for (Field f : base.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    fontField = f;
                    break;
                }
            }
        }
        if (fontField == null)
            throw new NoSuchFieldException("ClockFontData field not found in " + base.getName());
        font = fontField;
        legacy = font.getName().equals("L0");

        Field idField = null;
        for (String candidate : new String[] {"w0", "D0", "C0"}) {
            try {
                idField = NativeContract.field(base, candidate, int.class);
                break;
            } catch (NoSuchFieldException ignored) {
            }
        }
        fontId = idField;

        Method renderMethod = null;
        for (String candidate : new String[] {"X", "b0", "g0", "d0"}) {
            try {
                renderMethod = NativeContract.method(base, candidate, void.class, type);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (renderMethod == null) {
            for (Method m : base.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getReturnType() == void.class
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(type)
                        && !m.getName().equals("setFontType")
                        && !m.getName().equals("startSpringAnimation")) {
                    m.setAccessible(true);
                    renderMethod = m;
                    break;
                }
            }
        }
        if (renderMethod == null)
            throw new NoSuchMethodException(
                    "Clock render method not found in " + base.getName());
        render = renderMethod;
        clockStyle = legacy && render.getName().equals("X");

        Class<?> shadow =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle", false, loader);
        Method refreshMethod = null;
        for (String candidate : new String[] {"U", "Y", "d0", "a0"}) {
            try {
                refreshMethod = NativeContract.method(base, candidate, void.class, shadow);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (refreshMethod == null) {
            for (Method m : base.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getReturnType() == void.class
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(shadow)
                        && !m.getName().equals("setShadowStyle")) {
                    m.setAccessible(true);
                    refreshMethod = m;
                    break;
                }
            }
        }
        if (refreshMethod == null)
            throw new NoSuchMethodException(
                    "Clock shadow refresh method not found in " + base.getName());
        refreshAppearance = refreshMethod;

        Method balanceMethod = null;
        int heights = 2;
        if (legacy) {
            try {
                balanceMethod =
                        Class.forName(clockStyle ? "aod.QT" : "aod.PO", false, loader)
                                .getDeclaredMethod(clockStyle ? "g" : "k", List.class);
                heights = 0;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }
        }
        if (balanceMethod == null) {
            for (String[] candidate : new String[][] {
                    {"aod.qn1", "X"},
                    {"aod.uq6", "T"}
            }) {
                try {
                    balanceMethod =
                            Class.forName(candidate[0], false, loader)
                                    .getDeclaredMethod(candidate[1], int.class, type, List.class);
                    heights = 2;
                    break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                }
            }
        }
        if (balanceMethod == null && !legacy) {
            for (String[] candidate : new String[][] {
                    {"aod.PO", "k"},
                    {"aod.QT", "g"}
            }) {
                try {
                    balanceMethod =
                            Class.forName(candidate[0], false, loader)
                                    .getDeclaredMethod(candidate[1], List.class);
                    heights = 0;
                    break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                }
            }
        }
        if (balanceMethod == null
                || !java.lang.reflect.Modifier.isStatic(balanceMethod.getModifiers())
                || !List.class.isAssignableFrom(balanceMethod.getReturnType()))
            throw new NoSuchMethodException("Clock height normalizer changed");
        balanceMethod.setAccessible(true);
        balance = balanceMethod;
        balanceHeights = heights;
    }

    int fontId(Object clock) throws IllegalAccessException {
        if (fontId != null) return fontId.getInt(clock);
        Object f = font(clock);
        if (f != null) {
            try {
                return ((Number) f.getClass().getMethod("getFontType").invoke(f)).intValue();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    Object font(Object clock) throws IllegalAccessException {
        return font.get(clock);
    }

    void render(Object clock, Object value) throws ReflectiveOperationException {
        render.invoke(clock, value);
    }

    void refreshAppearance(Object clock) throws ReflectiveOperationException {
        refreshAppearance.invoke(clock, new Object[] {null});
    }
}
