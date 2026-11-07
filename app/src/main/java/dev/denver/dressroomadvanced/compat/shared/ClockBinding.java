package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class ClockBinding {
    final boolean legacy;
    final boolean aod8957;
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
        aod8957 = font.getName().equals("R0");

        Field idField = null;
        for (String candidate : (aod8957 ? new String[] {"C0"} : new String[] {"w0", "D0", "C0"})) {
            try {
                idField = NativeContract.field(base, candidate, int.class);
                break;
            } catch (NoSuchFieldException ignored) {
            }
        }
        fontId = idField;

        Method renderMethod = null;
        for (String candidate : (aod8957 ? new String[] {"e0"} : new String[] {"X", "b0", "g0", "d0"})) {
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
        clockStyle =
                legacy
                        ? render.getName().equals("X")
                        : (render.getName().equals("d0") || font.getName().equals("S0"));

        Class<?> shadow =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle", false, loader);
        Method refreshMethod = null;
        for (String candidate : (aod8957 ? new String[] {"b0"} : new String[] {"U", "Y", "d0", "a0"})) {
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
        try {
            if (aod8957) {
                balanceMethod = OneUi9Aod8957ClockSource.normalizer(loader, type);
            } else if (legacy) {
                balanceMethod =
                        clockStyle
                                ? OneUi85ClockSource.normalizer(loader)
                                : OneUi85AodClockSource.normalizer(loader);
                heights = 0;
            } else {
                balanceMethod =
                        clockStyle
                                ? OneUi9ClockStyleClockSource.normalizer(loader, type)
                                : OneUi9ClockSource.normalizer(loader, type);
                heights = 2;
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
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
