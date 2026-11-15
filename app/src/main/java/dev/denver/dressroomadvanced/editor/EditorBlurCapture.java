package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Samsung's canvas blur normally records the whole window, including foreground content. */
final class EditorBlurCapture {
    private static final Map<View, WeakReference<View>> wallpapers = new WeakHashMap<>();
    private static final java.util.Set<View> targets =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<View> capturing = new ThreadLocal<>();
    private static boolean installed;
    private static boolean reported;

    private EditorBlurCapture() {}

    static void install(DressroomModule module) throws ReflectiveOperationException {
        if (installed || !EditorWidgetBlur.canvasSupported()) return;
        Method blur = View.class.getDeclaredMethod("drawBlurEffect", Canvas.class);
        Method draw = View.class.getDeclaredMethod("draw", Canvas.class);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(blur)
                    .setId("dressroom-advanced.editor-canvas-blur-capture")
                    .intercept(chain -> {
                        if (targets.isEmpty()) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        if (!targets.contains(view)) return chain.proceed();
                        View image = wallpaper(view);
                        if (image == null) return null;
                        View previous = capturing.get();
                        capturing.set(image);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) capturing.remove();
                            else capturing.set(previous);
                        }
                    })::unhook);
            hooks.add(module.hook(draw)
                    .setId("dressroom-advanced.editor-canvas-blur-wallpaper")
                    .intercept(chain -> {
                        View image = capturing.get();
                        if (image == null || chain.getThisObject() != image.getRootView()
                                || ((Canvas) chain.getArg(0)).isHardwareAccelerated())
                            return chain.proceed();
                        try {
                            drawWallpaper(image, (Canvas) chain.getArg(0));
                        } catch (RuntimeException error) {
                            if (!reported) {
                                reported = true;
                                DressroomModule.note("Editor wallpaper blur capture", error);
                            }
                        }
                        return null;
                    })::unhook);
            module.deoptimize(blur);
            module.deoptimize(draw);
            hooks.commit();
            installed = true;
        }
    }

    static boolean available() {
        return installed;
    }

    static boolean prepare(View view) {
        if (!installed) return false;
        View image = wallpaper(view);
        if (image == null) return false;
        targets.add(view);
        return true;
    }

    static void clear(View view) {
        targets.remove(view);
    }

    private static View wallpaper(View target) {
        View root = target.getRootView();
        WeakReference<View> ref = wallpapers.get(root);
        View image = ref == null ? null : ref.get();
        if (!ready(image, root)) {
            image = DepthEffects.findCropView(root);
            if (!ready(image, root)) return null;
            wallpapers.put(root, new WeakReference<>(image));
        }
        return image;
    }

    private static boolean ready(View image, View root) {
        return image != null && image.isShown() && image.getRootView() == root
                && image.getWidth() > 0 && image.getHeight() > 0;
    }

    private static void drawWallpaper(View image, Canvas canvas) {
        Matrix rootToWindow = new Matrix(), windowToRoot = new Matrix();
        image.getRootView().transformMatrixToGlobal(rootToWindow);
        if (!rootToWindow.invert(windowToRoot)) return;
        Matrix imageToWindow = new Matrix(), imageToRoot = new Matrix();
        image.transformMatrixToGlobal(imageToWindow);
        imageToRoot.setConcat(windowToRoot, imageToWindow);
        int saved = canvas.save();
        try {
            canvas.concat(imageToRoot);
            canvas.clipRect(0, 0, image.getWidth(), image.getHeight());
            image.draw(canvas);
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
