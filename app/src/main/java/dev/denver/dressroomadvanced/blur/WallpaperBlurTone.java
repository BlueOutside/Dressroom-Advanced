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
                    Class.forName("aod.n16", false, loader).getDeclaredMethod("A"),
                    Class.forName("aod.n16", false, loader).getDeclaredMethod("C", Object.class),
                    Class.forName("aod.rv2", false, loader).getDeclaredMethod("w", int.class),
                    Class.forName("aod.l49", false, loader)
                            .getConstructor(double.class, float.class));
        }
    }

    static Object read(Class<?> managerType, View clock) throws ReflectiveOperationException {
        Native nativeApi = readers.get(managerType);
        int color;
        float saturation;
        if (Boolean.TRUE.equals(Reflect.field(clock, "L0"))) {
            color = ((Number) Reflect.field(clock, "t")).intValue();
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            saturation = hsv[1];
        } else {
            float[] hsv =
                    (float[])
                            nativeApi.hsv.invoke(
                                    nativeApi.singleton.invoke(null), Reflect.field(clock, "s"));
            if (hsv == null) return null; // No sample yet: retain Samsung's original decision.
            color = Color.HSVToColor(hsv);
            saturation = hsv[1];
        }
        return nativeApi.info.newInstance(
                ((Number) nativeApi.brightness.invoke(null, color)).doubleValue(), saturation);
    }
}
