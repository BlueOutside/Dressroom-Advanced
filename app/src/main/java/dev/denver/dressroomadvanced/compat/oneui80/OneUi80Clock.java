package dev.denver.dressroomadvanced;

import java.util.List;

final class OneUi80Clock {
    private OneUi80Clock() {}

    static boolean acceptsNativeHeights(Class<?> base, Class<?> font) {
        try {
            NativeContract.method(base, "setDozeAmount", void.class, float.class);
            NativeContract.method(base, "startSpringAnimation", void.class, font);
            NativeContract.method(font, "getFontHeights", List.class);
            try {
                base.getDeclaredMethod("setMediaExpanded", boolean.class);
                return false;
            } catch (NoSuchMethodException earlyClock) {
                return true;
            }
        } catch (NoSuchMethodException unavailable) {
            return false;
        }
    }
}
