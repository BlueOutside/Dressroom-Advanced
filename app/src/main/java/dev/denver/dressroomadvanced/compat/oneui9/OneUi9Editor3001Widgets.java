package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

/** Preflight the editor contracts before adding any Dressroom 3.0.01.20 widget hooks. */
final class OneUi9Editor3001Widgets {
    private static final String ENTITIES = "com.samsung.android.lockscreen.editor.item.domain.entity.";
    private OneUi9Editor3001Widgets() {}

    private static Class<?> entity(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(ENTITIES + name, false, loader);
    }

    static void validate(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> preview = Class.forName("lp.l", false, loader);
        NativeContract.method(preview, "b", View.class);
        NativeContract.field(preview, "F", entity(loader, "item.widget.WidgetData"));
        NativeContract.assignableField(preview, "G", ViewGroup.class);
        NativeContract.assignableField(preview, "K", View.class);
        NativeContract.method(Class.forName("v8.y", false, loader), "l", void.class);
        Class.forName("k6.m", false, loader).getDeclaredConstructor(int.class, int.class, Runnable.class, String.class);
        NativeContract.method(Class.forName("pc.h", false, loader), "d",
                Class.forName("com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController", false, loader));
        NativeContract.method(Class.forName("sd.p", false, loader), "b", entity(loader, "item.widget.WidgetData"),
                entity(loader, "profile.ProfileData"), entity(loader, "preview.UIMode"));
        NativeContract.method(Class.forName("sd.g", false, loader), "a", void.class,
                entity(loader, "item.widget.WidgetGridData"),
                Class.forName("com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType", false, loader), boolean.class);
        NativeContract.method(Class.forName("sd.n", false, loader), "a", void.class,
                entity(loader, "item.widget.WidgetGridData"), entity(loader, "item.widget.WidgetSizeData"),
                entity(loader, "system.PreviewScale"), boolean.class, entity(loader, "item.widget.WidgetAssistData"),
                entity(loader, "system.ScreenSize"), entity(loader, "system.DeviceType"),
                entity(loader, "preview.UIMode"), entity(loader, "item.common.ModuleRectInfo"));
        NativeContract.method(Class.forName("sd.l", false, loader), "f", void.class,
                entity(loader, "item.widget.WidgetData"), entity(loader, "item.widget.WidgetAssistData"),
                entity(loader, "item.clock.ClockAssistData"), entity(loader, "preview.UIMode"), boolean.class,
                entity(loader, "system.ScreenSize"), entity(loader, "item.common.ModuleRectInfo"));
        Class.forName("u2.a", false, loader).getDeclaredMethod("V", View.class, int.class, int.class,
                float.class, int.class, int.class, boolean.class);
        NativeContract.method(Class.forName("cg.n", false, loader), "c", void.class, Class.forName("gf.c", false, loader));
        NativeContract.method(Class.forName("cg.u", false, loader), "a", boolean.class,
                Class.forName("com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType", false, loader));
        NativeContract.method(Class.forName("yc.b", false, loader), "c", void.class, entity(loader, "item.widget.WidgetData"));
    }
}
