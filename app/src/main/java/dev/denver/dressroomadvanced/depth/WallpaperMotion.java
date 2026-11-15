package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.Choreographer;

/** Sample renderer motion only while it changes. Static AOD does not need a frame loop. */
final class WallpaperMotion implements Choreographer.FrameCallback {
    private static WallpaperMotion instance;
    private record SurfaceFrame(Object source, WallpaperMotionPacket.Frame frame) {}
    private static final SurfaceFrame[] surfaces = new SurfaceFrame[WallpaperMotionPacket.SLOTS];
    private final WallpaperMotionChannel channel;
    private final Context context;
    private boolean interactive = true, showMain = true, showSub = true;
    private final WallpaperMotionPacket.Frame[] frames =
            new WallpaperMotionPacket.Frame[WallpaperMotionPacket.SLOTS];
    private boolean scheduled;
    private long lastChange;

    private final java.util.Map<android.view.View, Integer> windows = new java.util.WeakHashMap<>();
    private final java.util.Map<android.view.View, Integer> watched = new java.util.WeakHashMap<>();
    private final android.view.View.OnAttachStateChangeListener attached =
            new android.view.View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(android.view.View view) {
                    send(view, watched.getOrDefault(view, -1), false);
                }

                @Override
                public void onViewDetachedFromWindow(android.view.View view) {}
            };
    private int received;

    private WallpaperMotion(Context context) throws android.system.ErrnoException {
        channel = new WallpaperMotionChannel(this::wake);
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        refreshVisibility(false);
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(android.content.Intent.ACTION_SCREEN_ON);
        filter.addAction(android.content.Intent.ACTION_SCREEN_OFF);
        filter.addAction(android.content.Intent.ACTION_WALLPAPER_CHANGED);
        this.context.registerReceiver(
                new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, android.content.Intent intent) {
                        refreshVisibility(true);
                        for (var root : new java.util.ArrayList<>(windows.keySet()))
                            if (root.isAttachedToWindow()) channel.send(root);
                    }
                },
                filter,
                Context.RECEIVER_NOT_EXPORTED);
        android.database.ContentObserver settings =
                new android.database.ContentObserver(
                        new android.os.Handler(android.os.Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        refreshVisibility(true);
                    }
                };
        this.context
                .getContentResolver()
                .registerContentObserver(
                        android.provider.Settings.System.getUriFor("aod_show_lockscreen_wallpaper"),
                        false,
                        settings);
        this.context
                .getContentResolver()
                .registerContentObserver(
                        android.provider.Settings.System.getUriFor(
                                "aod_show_cover_screen_wallpaper"),
                        false,
                        settings);
    }

    private void refreshVisibility(boolean notify) {
        android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
        boolean awake = power == null || power.isInteractive();
        boolean main =
                android.provider.Settings.System.getInt(
                                context.getContentResolver(), "aod_show_lockscreen_wallpaper", 0)
                        == 1;
        boolean sub =
                android.provider.Settings.System.getInt(
                                context.getContentResolver(),
                                "aod_show_cover_screen_wallpaper",
                                main ? 1 : 0)
                        == 1;
        int changed = 0;
        if (interactive != awake || showMain != main) changed |= ScreenIdentity.MAIN;
        if (interactive != awake || showSub != sub) changed |= ScreenIdentity.SUB;
        interactive = awake;
        showMain = main;
        showSub = sub;
        if (changed != 0 && notify) DepthEffects.wallpaperMotionChanged(changed);
    }

    static boolean visible(int screen) {
        if (instance == null) return true;
        return DepthSourcePolicy.visible(
                instance.interactive,
                screen == ScreenIdentity.SUB ? instance.showSub : instance.showMain);
    }

    static void start(android.view.View view, int wallpaper) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            view.post(() -> start(view, wallpaper));
            return;
        }
        try {
            if (instance == null) instance = new WallpaperMotion(view.getContext());
            if (instance.watched.put(view, wallpaper) == null)
                view.addOnAttachStateChangeListener(instance.attached);
            instance.send(view, wallpaper, false);
        } catch (Exception | LinkageError error) {
            android.util.Log.w(DressroomModule.TAG, "Wallpaper motion reader unavailable", error);
        }
    }

    private void send(android.view.View view, int wallpaper, boolean retry) {
        if (!view.isAttachedToWindow()) return;
        android.view.View root = view.getRootView();
        if (!retry && java.util.Objects.equals(windows.get(root), wallpaper)) return;
        windows.put(root, wallpaper);
        channel.send(root);
        if (!retry) {
            java.lang.ref.WeakReference<android.view.View> reference =
                    new java.lang.ref.WeakReference<>(root);
            root.postDelayed(
                    () -> {
                        android.view.View current = reference.get();
                        if (current != null
                                && current.isAttachedToWindow()
                                && java.util.Objects.equals(windows.get(current), wallpaper))
                            send(current, wallpaper, true);
                    },
                    500);
        }
    }

    private void wake() {
        lastChange = SystemClock.uptimeMillis();
        requestFrame();
    }

    private void requestFrame() {
        if (!scheduled) {
            scheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        scheduled = false;
        int changed = 0;
        for (int i = 0; i < frames.length; i++) {
            WallpaperMotionPacket.Frame next = channel.read(i);
            // A busy writer is not an identity transform. Keep the last complete frame.
            if (next != null && !next.equals(frames[i])) {
                frames[i] = next;
                changed |= screenBit(next.which());
            }
        }
        if (changed != 0) {
            if (++received == 3)
                android.util.Log.i(DressroomModule.TAG, "Live wallpaper motion received");
            lastChange = SystemClock.uptimeMillis();
            DepthEffects.wallpaperMotionChanged(changed);
        }
        if (SystemClock.uptimeMillis() - lastChange < 250) requestFrame();
    }

    // SystemUI's image engine shares this process, so its geometry needs no IPC.
    static void setSurfaceFrame(Object source, int which, WallpaperMotionPacket.Frame frame) {
        int slot = WallpaperMotionPacket.slot(which);
        SurfaceFrame previous = surfaces[slot];
        if (frame == null) {
            if (previous == null || previous.source() != source) return;
            surfaces[slot] = null;
        } else {
            if (previous != null && previous.source() == source && previous.frame().equals(frame))
                return;
            surfaces[slot] = new SurfaceFrame(source, frame);
        }
        if (instance != null) DepthEffects.wallpaperMotionChanged(screenBit(which));
    }

    private static int screenBit(int which) {
        return switch (which & 60) {
            case 0, 4 -> ScreenIdentity.MAIN;
            case 16 -> ScreenIdentity.SUB;
            default -> 0;
        };
    }

    static void transform(Matrix transform, int wallpaper, int screen, float width, float height) {
        int which = ScreenIdentity.wallpaperWhich(screen == 0 ? ScreenIdentity.MAIN : screen, true);
        int slot = WallpaperMotionPacket.slot(which);
        SurfaceFrame surface = surfaces[slot];
        int w = Math.round(width), h = Math.round(height);
        WallpaperMotionPacket.Frame frame = surface == null ? null : surface.frame();
        if (frame == null || !frame.fits(wallpaper, w, h)) {
            frame = instance == null ? null : instance.frames[slot];
            if (frame == null || !frame.fits(wallpaper, w, h)) return;
        }
        Matrix motion = new Matrix();
        motion.setScale(frame.scaleX(), frame.scaleY());
        motion.postTranslate(frame.offsetX() * width, frame.offsetY() * height);
        transform.postConcat(motion);
    }
}
