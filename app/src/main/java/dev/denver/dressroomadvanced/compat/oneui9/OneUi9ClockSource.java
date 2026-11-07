package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi9ClockSource {
    private OneUi9ClockSource() {}

    static Method normalizer(ClassLoader loader, Class<?> type)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasStaticMethod(
                                        cls, List.class, int.class, type, List.class),
                        "aod.qn1",
                        "aod.uq6");
        return NativeContract.findStaticMethod(
                owner, "X", List.class, int.class, type, List.class);
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
        return List.of(
                model.getDeclaredMethod("C"),
                page.getDeclaredMethod("y"),
                page.getDeclaredMethod("C"),
                page.getDeclaredMethod("getFullscreenClockFontData"),
                page.getDeclaredMethod("getClockViewForFullScreen"),
                NativeContract.findMethod(page, "u", void.class, (Class<?>) null, Object.class));
    }
}
