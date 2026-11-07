package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.List;

/** AOD 8.9.57.1 interface; keep the reference AOD adapter separate. */
final class OneUi9Aod8957ClockSource {
    private OneUi9Aod8957ClockSource() {}

    static List<Method> callers(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> model = Class.forName("com.samsung.android.app.facewidget.FaceWidgetViewModel", false, loader);
        Class<?> page = Class.forName("com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage", false, loader);
        return List.of(model.getDeclaredMethod("C"), page.getDeclaredMethod("z"),
                page.getDeclaredMethod("getFullscreenClockFontData"),
                page.getDeclaredMethod("getClockViewForFullScreen"));
    }

    static Method normalizer(ClassLoader loader, Class<?> type)
            throws ReflectiveOperationException {
        return Class.forName("aod.an1", false, loader)
                .getDeclaredMethod("c0", int.class, type, List.class);
    }
}
