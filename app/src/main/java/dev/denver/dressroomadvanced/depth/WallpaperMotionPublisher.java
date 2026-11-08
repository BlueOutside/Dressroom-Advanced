package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.util.Map;
import java.util.WeakHashMap;

/** Observe Samsung's own bitmap transform. Nothing here changes the wallpaper renderer. */
final class WallpaperMotionPublisher {
    private static final Map<ClassLoader, WallpaperMotionPublisher> installed = new WeakHashMap<>();
    private final ThreadLocal<Pass> drawing = new ThreadLocal<>();
    private final Map<Object, Identity> engines =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final WallpaperMotionPacket.Frame[] latest =
            new WallpaperMotionPacket.Frame[WallpaperMotionPacket.SLOTS];
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.LinkedHashMap<String, WallpaperMotionChannel.Sink> clients =
            new java.util.LinkedHashMap<>();
    private boolean signalled, connected;
    private final java.util.Set<Class<?>> drawableTypes = new java.util.HashSet<>();
    private final java.util.Set<java.lang.reflect.Method> drawableMethods =
            new java.util.HashSet<>();
    private final Runnable settled =
            () -> {
                signalled = false;
                signal();
            };

    private record Identity(Context context, int which, int wallpaper) {}

    private static final class Pass {
        final Identity identity;
        Canvas bitmapCanvas;
        WallpaperMotionPacket.Frame frame;

        Pass(Identity identity) {
            this.identity = identity;
        }
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed.containsKey(loader)) return;
        WallpaperMotionPublisher publisher = new WallpaperMotionPublisher();
        try (HookTransaction hooks = new HookTransaction()) {
            WallpaperMotionBinding binding = new WallpaperMotionBinding(loader);
            hooks.add(
                    module.hook(binding.engine)
                                    .setId("dressroom-advanced.wallpaper-frame")
                                    .intercept(
                                            chain -> {
                                                Pass previous = publisher.drawing.get();
                                                Pass pass = null;
                                                try {
                                                    Object engine = chain.getThisObject();
                                                    Identity identity =
                                                            publisher.engines.get(engine);
                                                    int flags = (int) binding.which.invoke(engine);
                                                    int which =
                                                            (flags & 2) == 0
                                                                    ? flags
                                                                    : (flags & ~3) | 2;
                                                    if (identity == null
                                                            || identity.which != which) {
                                                        if ((which & 2) != 0) {
                                                            Context context =
                                                                    (Context)
                                                                            binding.context.invoke(
                                                                                    engine);
                                                            int source =
                                                                    (int)
                                                                            binding.sourceWhich
                                                                                    .invoke(engine);
                                                            int wallpaper =
                                                                    WallpaperManager.getInstance(
                                                                                    context)
                                                                            .getWallpaperId(source);
                                                            identity =
                                                                    new Identity(
                                                                            context, which,
                                                                            wallpaper);
                                                            android.util.Log.i(
                                                                    DressroomModule.TAG,
                                                                    "Wallpaper motion source:"
                                                                            + " which="
                                                                            + which
                                                                            + ", id="
                                                                            + wallpaper);
                                                        } else {
                                                            identity =
                                                                    new Identity(null, which, -1);
                                                        }
                                                        publisher.engines.put(engine, identity);
                                                    }
                                                    if (identity != null
                                                            && (identity.which & 3) == 2)
                                                        pass = new Pass(identity);
                                                } catch (Exception error) {
                                                    module.report(
                                                            "Wallpaper motion identity unavailable",
                                                            error);
                                                }
                                                if (pass == null) publisher.drawing.remove();
                                                else publisher.drawing.set(pass);
                                                try {
                                                    return chain.proceed();
                                                } finally {
                                                    if (previous == null)
                                                        publisher.drawing.remove();
                                                    else publisher.drawing.set(previous);
                                                    if (pass != null && pass.frame != null) {
                                                        try {
                                                            publisher.publish(pass.frame);
                                                        } catch (RuntimeException
                                                                | LinkageError error) {
                                                            module.report(
                                                                    "Wallpaper motion publish"
                                                                            + " unavailable",
                                                                    error);
                                                        }
                                                    }
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(binding.bitmap)
                                    .setId("dressroom-advanced.wallpaper-bitmap")
                                    .intercept(
                                            chain -> {
                                                Pass pass = publisher.drawing.get();
                                                if (pass == null || pass.frame != null)
                                                    return chain.proceed();
                                                try {
                                                    publisher.captureDrawable(
                                                            module,
                                                            (Drawable)
                                                                    binding.drawable.get(
                                                                            chain.getThisObject()));
                                                } catch (Exception error) {
                                                    module.report(
                                                            "Wallpaper drawable observation"
                                                                    + " unavailable",
                                                            error);
                                                }
                                                Canvas previous = pass.bitmapCanvas;
                                                pass.bitmapCanvas = (Canvas) chain.getArg(0);
                                                try {
                                                    return chain.proceed();
                                                } finally {
                                                    pass.bitmapCanvas = previous;
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(binding.command)
                                    .setId("dressroom-advanced.wallpaper-channel")
                                    .intercept(
                                            chain -> {
                                                if (!WallpaperMotionChannel.COMMAND.equals(
                                                        chain.getArg(0))) return chain.proceed();
                                                try {
                                                    android.os.Bundle data =
                                                            (android.os.Bundle) chain.getArg(4);
                                                    if (data != null)
                                                        publisher.accept(
                                                                new WallpaperMotionChannel.Sink(
                                                                        data));
                                                } catch (Exception | LinkageError error) {
                                                    module.report(
                                                            "Wallpaper motion handshake"
                                                                    + " unavailable",
                                                            error);
                                                }
                                                return android.os.Bundle.EMPTY;
                                            })
                            ::unhook);
            module.deoptimize(binding.command);
            module.deoptimize(binding.engine);
            module.deoptimize(binding.bitmap);
            hooks.commit();
            installed.put(loader, publisher);
            android.util.Log.i(DressroomModule.TAG, "Native wallpaper motion tracking installed");
        } catch (Exception | LinkageError error) {
            module.report("Wallpaper motion unavailable; using saved depth mapping", error);
        }
    }

    private synchronized void captureDrawable(DressroomModule module, Drawable drawable)
            throws ReflectiveOperationException {
        if (drawable == null || !drawableTypes.add(drawable.getClass())) return;
        java.lang.reflect.Method draw = drawable.getClass().getMethod("draw", Canvas.class);
        if (!drawableMethods.add(draw)) return;
        module.hook(draw)
                .setId("dressroom-advanced.wallpaper-matrix")
                .intercept(
                        chain -> {
                            Pass pass = drawing.get();
                            Canvas canvas = (Canvas) chain.getArg(0);
                            if (pass != null && pass.frame == null && pass.bitmapCanvas == canvas) {
                                try {
                                    Matrix matrix = new Matrix();
                                    canvas.getMatrix(matrix);
                                    float[] values = new float[9];
                                    matrix.getValues(values);
                                    if (values[1] == 0
                                            && values[3] == 0
                                            && values[6] == 0
                                            && values[7] == 0
                                            && values[8] == 1
                                            && canvas.getWidth() > 0
                                            && canvas.getHeight() > 0) {
                                        Identity identity = pass.identity;
                                        var frame =
                                                new WallpaperMotionPacket.Frame(
                                                        0,
                                                        identity.wallpaper,
                                                        identity.which,
                                                        canvas.getWidth(),
                                                        canvas.getHeight(),
                                                        values[0],
                                                        values[4],
                                                        values[2] / canvas.getWidth(),
                                                        values[5] / canvas.getHeight());
                                        if (frame.valid()) pass.frame = frame;
                                    }
                                } catch (RuntimeException | LinkageError error) {
                                    module.report("Wallpaper canvas transform unavailable", error);
                                }
                            }
                            return chain.proceed();
                        });
        module.deoptimize(draw);
    }

    private synchronized void accept(WallpaperMotionChannel.Sink sink) {
        WallpaperMotionChannel.Sink previous = clients.remove(sink.key);
        if (previous != null) previous.close();
        if (clients.size() >= 4) {
            String oldest = clients.keySet().iterator().next();
            clients.remove(oldest).close();
        }
        clients.put(sink.key, sink);
        for (var frame : latest) if (frame != null) sink.write(frame);
        sink.signal();
    }

    private synchronized void signal() {
        for (var client : clients.values()) client.signal();
    }

    private synchronized void publish(WallpaperMotionPacket.Frame frame) {
        latest[WallpaperMotionPacket.slot(frame.which())] = frame;
        boolean changed = false;
        for (var client : clients.values()) changed |= client.write(frame);
        if (!changed) return;
        if (!connected) {
            connected = true;
            android.util.Log.i(
                    DressroomModule.TAG,
                    "Wallpaper motion connected: " + frame.width() + "x" + frame.height());
        }
        // Wake once at the beginning and once after the last changing frame.
        main.post(
                () -> {
                    if (!signalled) {
                        signalled = true;
                        signal();
                    }
                    main.removeCallbacks(settled);
                    main.postDelayed(settled, 100);
                });
    }
}
