package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;

final class OneUi85ArtworkBinding {
    final Constructor<?> constructor;

    OneUi85ArtworkBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 artwork binding");
        ClassLoader loader = base.getClassLoader();
        Class<?> page =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage",
                        false,
                        loader);
        Class<?> model =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicViewModel",
                        false,
                        loader);
        Class<?> holder =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasField(type, page)
                                        && NativeContract.hasField(type, model),
                        clock.clockStyle ? "aod.TK" : "aod.YR");
        Constructor<?> found = null;
        for (Constructor<?> candidate : holder.getDeclaredConstructors()) {
            Class<?>[] args = candidate.getParameterTypes();
            if (args.length == 7 && args[0] == Context.class && args[1] == View.class) {
                if (found != null) throw new NoSuchMethodException("Ambiguous native music holder");
                found = candidate;
            }
        }
        if (found == null) throw new NoSuchMethodException("Native music holder changed");
        constructor = found;
    }
}
