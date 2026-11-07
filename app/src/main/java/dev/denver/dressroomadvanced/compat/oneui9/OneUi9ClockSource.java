package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi9ClockSource {
    private OneUi9ClockSource() {}

    static Method normalizer(ClassLoader loader, Class<?> type)
            throws ReflectiveOperationException {
        return Class.forName("aod.qn1", false, loader)
                .getDeclaredMethod("X", int.class, type, List.class);
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
                NativeContract.method(
                        page,
                        "u",
                        void.class,
                        Class.forName("aod.fb2", false, loader),
                        Object.class));
    }
}
