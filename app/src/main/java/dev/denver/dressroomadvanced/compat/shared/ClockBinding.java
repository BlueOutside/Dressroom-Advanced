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
        boolean old;
        try {
            NativeContract.field(base, "L0", type);
            old = true;
        } catch (NoSuchFieldException ignored) {
            old = false;
        }
        legacy = old;
        boolean tablet = false;
        if (legacy) {
            try {
                NativeContract.method(base, "X", void.class, type);
                tablet = true;
            } catch (NoSuchMethodException ignored) {
            }
        }
        clockStyle = tablet;
        font = NativeContract.field(base, legacy ? "L0" : "T0", type);
        fontId = NativeContract.field(base, legacy ? "w0" : "D0", int.class);
        render =
                NativeContract.method(
                        base, clockStyle ? "X" : legacy ? "b0" : "g0", void.class, type);
        Class<?> shadow =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle", false, loader);
        refreshAppearance =
                NativeContract.method(
                        base, clockStyle ? "U" : legacy ? "Y" : "d0", void.class, shadow);
        if (legacy) {
            balance =
                    Class.forName(clockStyle ? "aod.QT" : "aod.PO", false, loader)
                            .getDeclaredMethod(clockStyle ? "g" : "k", List.class);
            balanceHeights = 0;
        } else {
            balance =
                    Class.forName("aod.qn1", false, loader)
                            .getDeclaredMethod("X", int.class, type, List.class);
            balanceHeights = 2;
        }
        if (!java.lang.reflect.Modifier.isStatic(balance.getModifiers())
                || !List.class.isAssignableFrom(balance.getReturnType()))
            throw new NoSuchMethodException("Clock height normalizer changed");
        balance.setAccessible(true);
    }

    int fontId(Object clock) throws IllegalAccessException {
        return fontId.getInt(clock);
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
