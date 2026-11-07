package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi85ClockSource {
    private OneUi85ClockSource() {}

    static Method normalizer(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasStaticMethod(cls, List.class, List.class),
                        "aod.QT",
                        "aod.PO");
        return NativeContract.findStaticMethod(owner, "g", List.class, List.class);
    }

    static List<Method> callers(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> model =
                Class.forName(
                        "com.samsung.android.app.facewidget.FaceWidgetViewModel", false, loader);
        Class<?> page =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage",
                        false,
                        loader);
        Class<?> font =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);
        // Clock Style captures stretched height in w(); 9.0 uses y(). Same letter != same job.
        return List.of(
                NativeContract.method(model, "C", boolean.class),
                NativeContract.method(page, "w", boolean.class),
                NativeContract.method(page, "getFullscreenClockFontData", font),
                page.getDeclaredMethod("getClockViewForFullScreen"),
                NativeContract.findMethod(page, "t", void.class, (Class<?>) null, Object.class));
    }
}
