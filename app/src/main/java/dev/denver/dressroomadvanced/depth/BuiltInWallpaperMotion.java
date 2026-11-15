package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Observe the built-in image engine's surface zoom, which never reaches a Canvas hook. */
final class BuiltInWallpaperMotion {
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final class Source {
        final int which, wallpaper;
        long update;

        Source(int which, int wallpaper) {
            this.which = which;
            this.wallpaper = wallpaper;
        }
    }

    private final Map<Object, Source> animators = new WeakHashMap<>();
    private final Source[] active = new Source[WallpaperMotionPacket.SLOTS];
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed.contains(loader)) return;
        try {
            Class<?> image = Class.forName(
                    "com.android.systemui.wallpaper.engines.image.ImageEngine", false, loader);
            Class<?> animator = Class.forName(
                    "com.android.systemui.wallpaper.engines.WallpaperAnimator", false, loader);
            Method scale = NativeContract.findMethod(animator, "scaleSurface", void.class, Rect.class);
            var body = DexSignatures.body(scale);
            String transaction = "Landroid/view/SurfaceControl$Transaction;";
            if (body.calls().stream().noneMatch(call ->
                    call.owner().equals(transaction) && call.name().equals("setGeometry")
                            && call.parameters().equals(java.util.List.of(
                                    "Landroid/view/SurfaceControl;", "Landroid/graphics/Rect;",
                                    "Landroid/graphics/Rect;", "I")))
                    || body.calls().stream().noneMatch(call ->
                            call.owner().equals(transaction) && call.name().equals("apply")))
                throw new NoSuchMethodException("Image wallpaper surface geometry");
            Field imageAnimator = NativeContract.findField(image, null, animator);
            Field surfaceHolder = NativeContract.findField(animator, null, SurfaceHolder.class);
            Field control = NativeContract.findField(animator, null, SurfaceControl.class);
            Method which = image.getMethod("getWhich");
            Method sourceWhich = image.getMethod("getSourceWhich");
            Method context = image.getMethod("getAppContext");
            Method user = image.getMethod("getCurrentUserId");
            int processUser = (int) android.os.UserHandle.class.getMethod("getIdentifier")
                    .invoke(android.os.Process.myUserHandle());
            Method redraw = image.getDeclaredMethod("onSurfaceRedrawNeeded", SurfaceHolder.class);
            Method whichChanged = image.getDeclaredMethod("onWhichChanged", int.class);
            Method surfaceDestroyed = image.getDeclaredMethod("onSurfaceDestroyed", SurfaceHolder.class);
            Method destroy = image.getDeclaredMethod("onDestroy");
            BuiltInWallpaperMotion observer = new BuiltInWallpaperMotion();
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(module.hook(scale).setId("dressroom-advanced.image-wallpaper-geometry")
                        .intercept(chain -> {
                            Object owner = chain.getThisObject();
                            Source source = observer.source(owner);
                            WallpaperMotionPacket.Frame frame = null;
                            if (source != null && chain.getArg(0) instanceof Rect destination) {
                                try {
                                    SurfaceHolder holder = (SurfaceHolder) surfaceHolder.get(owner);
                                    SurfaceControl surfaceControl = (SurfaceControl) control.get(owner);
                                    Surface surface = holder == null ? null : holder.getSurface();
                                    if (surface != null && surface.isValid() && surfaceControl != null
                                            && surfaceControl.isValid()) {
                                        Rect frameRect = holder.getSurfaceFrame();
                                        frame = WallpaperMotionPacket.Frame.fromSurface(
                                                source.wallpaper, source.which,
                                                frameRect.width(), frameRect.height(),
                                                destination.width(), destination.height(),
                                                destination.left - frameRect.left,
                                                destination.top - frameRect.top);
                                    }
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    DressroomModule.note("Image wallpaper geometry", error);
                                }
                            }
                            Object result = chain.proceed();
                            if (source != null) observer.publish(source, frame);
                            return result;
                        })::unhook);
                for (Method event : new Method[] {redraw, whichChanged}) {
                    hooks.add(module.hook(event).setId("dressroom-advanced.image-wallpaper-source")
                            .intercept(chain -> {
                                Object engine = chain.getThisObject();
                                Object owner = null;
                                Source next = null;
                                try {
                                    owner = imageAnimator.get(engine);
                                    int flags = (int) which.invoke(engine);
                                    int mode = flags & 60;
                                    boolean matches = event == redraw
                                            ? chain.getArg(0) instanceof SurfaceHolder holder
                                                    && holder.getSurface() != null
                                                    && holder.getSurface().isValid()
                                            : (int) chain.getArg(0) == flags;
                                    if (matches && (flags & 2) != 0 && (flags & ~63) == 0
                                            && (mode == 0 || mode == 4 || mode == 16)
                                            && (int) user.invoke(engine) == processUser) {
                                        Context app = (Context) context.invoke(engine);
                                        int id = WallpaperManager.getInstance(app)
                                                .getWallpaperId((int) sourceWhich.invoke(engine));
                                        if (id >= 0) next = new Source((flags & ~3) | 2, id);
                                    }
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    DressroomModule.note("Image wallpaper source", error);
                                }
                                if (owner != null) observer.replace(owner, next);
                                return chain.proceed();
                            })::unhook);
                }
                for (Method event : new Method[] {surfaceDestroyed, destroy}) {
                    hooks.add(module.hook(event).setId("dressroom-advanced.image-wallpaper-release")
                            .intercept(chain -> {
                                try {
                                    Object owner = imageAnimator.get(chain.getThisObject());
                                    if (owner != null) observer.replace(owner, null);
                                } catch (IllegalAccessException error) {
                                    DressroomModule.note("Image wallpaper release", error);
                                }
                                return chain.proceed();
                            })::unhook);
                }
                module.deoptimize(scale);
                for (var caller : DexSignatures.callers(scale)) module.deoptimize(caller.method(loader));
                hooks.commit();
            }
            installed.add(loader);
        } catch (Exception | LinkageError error) {
            DressroomModule.note("Optional built-in wallpaper motion unavailable", error);
        }
    }

    private synchronized Source source(Object animator) {
        Source source = animators.get(animator);
        return source != null && active[WallpaperMotionPacket.slot(source.which)] == source
                ? source : null;
    }

    private synchronized void replace(Object animator, Source next) {
        Source previous = animators.remove(animator);
        if (previous != null) {
            int slot = WallpaperMotionPacket.slot(previous.which);
            if (active[slot] == previous) {
                active[slot] = null;
                clear(previous);
            }
        }
        if (next != null) {
            int slot = WallpaperMotionPacket.slot(next.which);
            if (active[slot] != null) clear(active[slot]);
            active[slot] = next;
            animators.put(animator, next);
        }
    }

    private void clear(Source source) {
        main.post(() -> WallpaperMotion.setSurfaceFrame(source, source.which, null));
    }

    private void publish(Source source, WallpaperMotionPacket.Frame frame) {
        int slot = WallpaperMotionPacket.slot(source.which);
        long update;
        synchronized (this) {
            if (active[slot] != source) return;
            update = ++source.update;
        }
        Runnable apply = () -> {
            synchronized (this) {
                if (active[slot] != source || source.update != update) return;
            }
            WallpaperMotion.setSurfaceFrame(source, source.which, frame);
        };
        if (Looper.myLooper() == main.getLooper()) apply.run();
        else main.post(apply);
    }
}
