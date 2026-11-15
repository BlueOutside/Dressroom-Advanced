package dev.denver.dressroomadvanced;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class WidgetBackgroundOptions {
    static final String CAMERA = "cameraWidgetBlur";
    static final String PICTURE_FRAME = "clearPictureFrameDim";
    private static final String CAMERA_COMPONENT =
            "com.sec.android.app.camera/com.sec.android.app.camera.setting.launcherwidget.CameraWidget";
    private static final String PICTURE_FRAME_PACKAGE = "com.samsung.android.widget.pictureframe";
    private static final Set<Class<?>> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private WidgetBackgroundOptions() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess()) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> decorator = Class.forName(
                    "com.samsung.android.app.facewidget.complication.ComplicationWidgetBackgroundDecorator",
                    false, loader);
            if (installed.contains(decorator)) return;
            Class<?> widget = Class.forName(
                    "com.samsung.android.clockpack.plugins.appwidget.SharedWidgetInfo", false, loader);
            Field context = NativeContract.field(decorator, "context", Context.class);
            Method excluded = NativeContract.method(decorator, "notSupportBlurBg", boolean.class, widget);
            Method dim = NativeContract.method(decorator, "getAODDimColor", Integer.class, String.class);
            Method component = NativeContract.method(widget, "getComponentName", String.class);
            hooks.add(module.hook(excluded).setId("dressroom-advanced.camera-widget-background")
                    .intercept(chain -> {
                        Object nativeResult = chain.proceed();
                        if (!Boolean.TRUE.equals(nativeResult)) return nativeResult;
                        try {
                            Context owner = (Context) context.get(chain.getThisObject());
                            if (owner != null && CAMERA_COMPONENT.equals(component.invoke(chain.getArg(0)))
                                    && RuntimeExtras.enabled(owner, CAMERA)
                                    && BlurAppearance.liveWindowBlurAllowed(owner)) return false;
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                        }
                        return nativeResult;
                    })::unhook);
            hooks.add(module.hook(dim).setId("dressroom-advanced.picture-frame-widget-dim")
                    .intercept(chain -> {
                        if (chain.getArg(0) instanceof String name
                                && (name.equals(PICTURE_FRAME_PACKAGE)
                                    || name.startsWith(PICTURE_FRAME_PACKAGE + "/"))) {
                            try {
                                Context owner = (Context) context.get(chain.getThisObject());
                                if (owner != null && RuntimeExtras.enabled(owner, PICTURE_FRAME)) return null;
                            } catch (ReflectiveOperationException | RuntimeException ignored) {
                            }
                        }
                        return chain.proceed();
                    })::unhook);
            module.deoptimize(excluded);
            module.deoptimize(dim);
            for (Method caller : decorator.getDeclaredMethods())
                if (caller.getName().equals("applyBackground") || caller.getName().equals("getWidgetContainer"))
                    module.deoptimize(caller);
            hooks.commit();
            installed.add(decorator);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.report("Optional camera and picture frame widget controls unavailable", unavailable);
        }
    }
}
