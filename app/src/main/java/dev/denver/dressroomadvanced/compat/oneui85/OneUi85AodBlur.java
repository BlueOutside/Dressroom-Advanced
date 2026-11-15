package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi85AodBlur {
    private OneUi85AodBlur() {}

    static List<Method> submissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> info = Class.forName("android.view.SemBlurInfo");
        Class<?> base =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
        Class<?> clipHelper = ClockBlurBinding.resolve(base).clip.getDeclaringClass();
        Class<?> helper = OneUi9AodBlur.blurUtils(loader);
        List<Method> common = OneUi9AodBlur.commonSubmissions(helper);
        return List.of(
                NativeContract.findStaticMethod(
                        clipHelper, null, void.class, View.class, info, String.class),
                NativeContract.findStaticMethod(
                        helper, null, void.class, View.class, info, String.class),
                common.get(0),
                common.get(1));
    }
}
