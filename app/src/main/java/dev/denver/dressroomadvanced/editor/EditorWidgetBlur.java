package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Lets the placed-widget preview use the same native background as the lockscreen. */
final class EditorWidgetBlur {
    private static final String TYPES = "com.samsung.android.clockpack.plugins.briefwidget.";
    private static final String HOST =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";
    private static final Map<View, Boolean> canvasViews =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> canvasRequests =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final boolean CANVAS = canvasSupported();

    private EditorWidgetBlur() {}

    static boolean canvasSupported() {
        try {
            Class<?> core = Class.forName("com.samsung.android.rune.CoreRune");
            return core.getField("GRAPHICS_RENDERER_CANVASBLUR_CAPTURE").getBoolean(null)
                    && core.getField("GRAPHICS_RENDERER_IMAGEFILTER").getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean canvasView(View view) {
        return CANVAS && canvasViews.computeIfAbsent(view, target -> {
            if (!(target.getParent() instanceof View parent)
                    || !parent.getClass().getName().equals(HOST)) return false;
            try {
                return "LockPreview".equals(String.valueOf(
                        Reflect.field(parent, "widgetDisplayType")));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return false;
            }
        });
    }

    static void canvasRequest(Object info) {
        canvasRequests.put(info, true);
    }

    static boolean isCanvasRequest(Object info) {
        return info != null && !canvasRequests.isEmpty() && canvasRequests.containsKey(info);
    }

    static void install(DressroomModule module, Class<?> grid) throws ReflectiveOperationException {
        if (!EditorBlurCapture.available()) return;
        ClassLoader loader = grid.getClassLoader();
        Class<?> display = Class.forName(TYPES + "WidgetDisplayType", false, loader);
        Class<?> background = Class.forName(TYPES + "WidgetBackgroundInfo", false, loader);
        Class<?> dim = Class.forName(TYPES + "WidgetDimInfo", false, loader);
        Class<?> blur = Class.forName(TYPES + "WidgetBlurInfo", false, loader);
        Class<?> item = Class.forName(
                "com.samsung.android.clockpack.plugins.clock.module.ModuleInfo", false, loader);
        Constructor<?> nativeBlur = blur.getConstructor(
                int.class, double.class, float.class, boolean.class, boolean.class, boolean.class);
        Method color = dim.getMethod("getClockColor");
        Method brightness = dim.getMethod("getWallpaperBrightness");
        Method root = grid.getMethod("setRootContainer", ViewGroup.class, String.class, display);
        Method[] entries = {
            grid.getMethod("initModuleInfo", ArrayList.class, background),
            grid.getMethod("addModuleInfo", item, boolean.class, background),
            grid.getMethod("updateWidgetBackground", background)
        };
        Map<Object, WeakReference<View>> editorRoots =
                Collections.synchronizedMap(new WeakHashMap<>());
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(root)
                    .setId("dressroom-advanced.widget-blur-preview-root")
                    .intercept(chain -> {
                        Object owner = chain.getThisObject();
                        if ("DRESSROOM_LOCK_COMPLICATION_CONTAINER".equals(chain.getArg(1))
                                && chain.getArg(0) instanceof View view)
                            editorRoots.put(owner, new WeakReference<>(view));
                        else editorRoots.remove(owner);
                        return chain.proceed();
                    })::unhook);
            module.deoptimize(root);
            for (Method entry : entries) {
                int index = entry.getParameterCount() - 1;
                hooks.add(module.hook(entry)
                        .setId("dressroom-advanced.widget-blur-preview-background")
                        .intercept(chain -> {
                            WeakReference<View> ref = editorRoots.get(chain.getThisObject());
                            View view = ref == null ? null : ref.get();
                            Object info = chain.getArg(index);
                            if (view == null || !dim.isInstance(info)
                                    || !BlurAppearance.liveWindowBlurAllowed(view.getContext()))
                                return chain.proceed();
                            Object replacement;
                            try {
                                replacement = nativeBlur.newInstance(
                                        color.invoke(info), brightness.invoke(info),
                                        0f, false, true, false);
                            } catch (ReflectiveOperationException | RuntimeException error) {
                                DressroomModule.note("Editor widget blur background", error);
                                return chain.proceed();
                            }
                            Object[] args = chain.getArgs().toArray();
                            args[index] = replacement;
                            return chain.proceed(args);
                        })::unhook);
                module.deoptimize(entry);
            }
            hooks.commit();
        }
    }
}
