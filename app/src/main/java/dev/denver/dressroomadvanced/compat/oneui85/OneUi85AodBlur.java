package dev.denver.dressroomadvanced;

import android.view.View;
import java.lang.reflect.Method;
import java.util.List;

final class OneUi85AodBlur {
    private OneUi85AodBlur() {}

    static ClockBlurBinding resolve(ClassLoader loader) throws ReflectiveOperationException {
        // Lv.b points at a synthetic holder; that holder's b is the AbsClockView.
        return new ClockBlurBinding(loader, "aod.Lv", "aod.dl", "aod.rF", "c", "b");
    }

    static List<Method> submissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> helper = Class.forName("aod.yi", false, loader);
        return List.of(
                Class.forName("aod.rF", false, loader).getDeclaredMethod("d", View.class,
                        Class.forName("android.view.SemBlurInfo"), String.class),
                helper.getDeclaredMethod("b", View.class, Class.forName("android.view.SemBlurInfo"), String.class),
                helper.getDeclaredMethod("d", View.class, float.class, float[].class, Integer.class),
                helper.getDeclaredMethod("e", View.class, int.class, float.class, Integer.class, String.class));
    }
}
