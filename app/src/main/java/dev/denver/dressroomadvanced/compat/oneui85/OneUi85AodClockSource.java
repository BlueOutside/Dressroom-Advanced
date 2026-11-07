package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi85AodClockSource {
    private OneUi85AodClockSource() {}

    static Method normalizer(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasStaticMethod(cls, List.class, List.class),
                        "aod.PO",
                        "aod.QT");
        return NativeContract.findStaticMethod(owner, "k", List.class, List.class);
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
        return List.of(
                NativeContract.method(model, "y", boolean.class),
                NativeContract.method(page, "w", boolean.class),
                NativeContract.method(page, "getFullscreenClockFontData", font),
                page.getDeclaredMethod("getClockViewForFullScreen"),
                NativeContract.findMethod(page, "t", void.class, (Class<?>) null, Object.class));
    }
}
