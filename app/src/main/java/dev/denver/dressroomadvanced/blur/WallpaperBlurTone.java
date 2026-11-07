package dev.denver.dressroomadvanced;

import android.graphics.Color;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class WallpaperBlurTone {
    private static final ClassValue<Native> readers =
            new ClassValue<>() {
                @Override
                protected Native computeValue(Class<?> manager) {
                    try {
                        return new Native(manager.getClassLoader());
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                }
            };

    private record Native(Method singleton, Method hsv, Method brightness, Constructor<?> info) {
        Native(ClassLoader loader) throws ReflectiveOperationException {
            this(
                    OneUi9Aod8957Runtime.type("aod.n16", false, loader).getDeclaredMethod("A"),
                    OneUi9Aod8957Runtime.type("aod.n16", false, loader).getDeclaredMethod("C", Object.class),
                    OneUi9Aod8957Runtime.type("aod.rv2", false, loader).getDeclaredMethod(OneUi9Aod8957Runtime.matches(loader) ? "O" : "w", int.class),
                    OneUi9Aod8957Runtime.type("aod.l49", false, loader)
                            .getConstructor(double.class, float.class));
        }
    }

    static Object read(Class<?> managerType, View clock) throws ReflectiveOperationException {
        Native nativeApi = readers.get(managerType);
        int color;
        float saturation;
        if (Boolean.TRUE.equals(OneUi9Aod8957Runtime.field(clock, "L0"))) {
            color = ((Number) OneUi9Aod8957Runtime.field(clock, "t")).intValue();
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            saturation = hsv[1];
        } else {
            float[] hsv =
                    (float[])
                            nativeApi.hsv.invoke(
                                    nativeApi.singleton.invoke(null), OneUi9Aod8957Runtime.field(clock, "s"));
            if (hsv == null) return null; // No sample yet: retain Samsung's original decision.
            color = Color.HSVToColor(hsv);
            saturation = hsv[1];
        }
        return nativeApi.info.newInstance(
                ((Number) nativeApi.brightness.invoke(null, color)).doubleValue(), saturation);
    }
}
