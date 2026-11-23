package dev.denver.dressroomadvanced.lyrics;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import dev.denver.dressroomadvanced.ExtrasPreferences;
import dev.denver.dressroomadvanced.LiveMediaTracker;
import dev.denver.dressroomadvanced.MediaClockVisibility;
import dev.denver.dressroomadvanced.MediaCoverWakefulness;
import dev.denver.dressroomadvanced.MediaFullscreenExtras;
import dev.denver.dressroomadvanced.OneUi85FullscreenArt;
import dev.denver.dressroomadvanced.RuntimeExtras;
import dev.denver.dressroomadvanced.VariableFonts;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Controller linking SystemUI's active media session with {@link LyricsView}.
 * Positions the lyrics view over the upper lockscreen area while keeping
 * bottom media controls (seekbar, play/pause, prev/next) unobstructed.
 */
public final class LyricsController {
    private static final String TAG = "DressroomLyrics";
    private static final String STATUS_CLOCK_TAG = "dressroom_lyrics_status_clock";
    private static final long BINDER_RESYNC_INTERVAL_MS = 1500L;

    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }

    private static final Map<View, LyricsController> BOUND_CONTROLLERS = new WeakHashMap<>();
    private static volatile boolean lyricsActive = false;
    private static boolean extrasListenerInstalled = false;
    private static LyricsController activeInstance;

    // Status bar replacement clock state (shared per SystemUI shade window)
    private static WeakReference<TextClock> statusClockRef = new WeakReference<>(null);
    private static WeakReference<View> hiddenCarrierSlotRef = new WeakReference<>(null);
    private static int savedCarrierSlotVisibility = -1;
    private static int statusClockAnimGen = 0;

    public static boolean isLyricsActive() {
        return lyricsActive;
    }

    private WeakReference<ViewGroup> pageContainerRef;
    private WeakReference<ViewGroup> controlHostRef;
    private WeakReference<View> titleArtistRef;
    private WeakReference<View> albumArtRef;
    private Supplier<Bitmap> artworkSupplier;

    private final LyricsView lyricsView;
    private final LyricsToggleButton toggleButton;
    private final Context context;

    private final Map<View, Float> hiddenByLyrics = new WeakHashMap<>();
    private float savedAlbumArtAlpha = -1f;
    private boolean albumArtHiddenForLocalBackdrop = false;

    private boolean isVisible = false;
    private boolean trackingActive = false;
    private String currentTrackKey = "";
    private long trackGeneration;
    private long pendingSeekPositionMs = -1L;
    private long pendingSeekElapsedMs;
    private WeakReference<ViewGroup> boundPage = new WeakReference<>(null);
    private WeakReference<ViewGroup> boundControls = new WeakReference<>(null);
    private long lastToggleMs = 0L;

    // VSync playback tracking state
    private MediaController observedController;
    private PlaybackState cachedPlaybackState;
    private long lastBinderSyncUptimeMs = 0L;
    private long lastFrameNanos = 0L;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state == null) return;
            cachedPlaybackState = state;
            lastBinderSyncUptimeMs = SystemClock.uptimeMillis();
            if (pendingSeekPositionMs >= 0L && state != null
                    && Math.abs(calculatePosition(state) - playbackPosition()) < 500L) {
                pendingSeekPositionMs = -1L;
            }
            if (isVisible) {
                boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
                lyricsView.updatePlaybackPosition(playbackPosition(), playing);
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            onTrackChanged();
        }

        @Override
        public void onSessionDestroyed() {
            detachMediaCallback();
            onTrackChanged();
        }
    };

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!trackingActive || !isVisible) return;

            long nowUptimeMs = SystemClock.uptimeMillis();
            float dtSec = lastFrameNanos > 0L
                    ? Math.max(0.001f, Math.min(0.05f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f))
                    : 0.016f;
            lastFrameNanos = frameTimeNanos;

            if ((nowUptimeMs - lastBinderSyncUptimeMs) >= BINDER_RESYNC_INTERVAL_MS) {
                syncMediaController(nowUptimeMs);
                syncStatusBarClockColor();
            }

            long posMs = playbackPosition();
            boolean playing = cachedPlaybackState != null
                    && cachedPlaybackState.getState() == PlaybackState.STATE_PLAYING;
            lyricsView.stepFrame(posMs, dtSec, nowUptimeMs, playing);

            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final View.OnLayoutChangeListener layoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (isVisible) {
                    View host = controlHostRef.get();
                    if (host == null || !host.isAttachedToWindow()
                            || host.getVisibility() != View.VISIBLE) {
                        Main.HANDLER.post(this::hideIfHostGone);
                        return;
                    }
                    updateLyricsBounds();
                }
            };

    private final Runnable liveTrackListener = this::onTrackChanged;

    private final View.OnAttachStateChangeListener pageAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) {
                    Main.HANDLER.post(LyricsController.this::hideIfHostGone);
                }
            };

    private final View.OnAttachStateChangeListener controlsAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    if (v != controlHostRef.get()) return;
                    requestHostAttachment();
                }
                @Override public void onViewDetachedFromWindow(View v) {
                    Main.HANDLER.post(LyricsController.this::hideIfHostGone);
                }
            };

    private final Runnable attachToCurrentHost = this::attachToCurrentHost;

    private void requestHostAttachment() {
        // ViewGroup snapshots its children before dispatching attachment. Moving the
        // lyrics out of an ancestor here can leave a null slot in that traversal.
        Main.HANDLER.removeCallbacks(attachToCurrentHost);
        Main.HANDLER.post(attachToCurrentHost);
    }

    private void attachToCurrentHost() {
        // Resolve after dispatch: recycled pages may have retargeted us in the meantime.
        ViewGroup controls = controlHostRef.get();
        if (controls == null || !controls.isAttachedToWindow()) return;
        ViewGroup best = findBestContainer(controls);
        if (best == null) best = pageContainerRef.get();
        if (best == null || !best.isAttachedToWindow()
                || best.getRootView() != controls.getRootView()) return;
        attachToggleButton(controls);
        attachLyricsView(best);
        updateLyricsBounds();
    }

    private void hideIfHostGone() {
        View host = controlHostRef.get();
        if (isVisible && (host == null || !host.isAttachedToWindow()
                || host.getVisibility() != View.VISIBLE)) hideLyrics();
    }

    private void bindPage(ViewGroup page) {
        ViewGroup old = boundPage.get();
        if (old == page) return;
        if (old != null) old.removeOnAttachStateChangeListener(pageAttachListener);
        boundPage = new WeakReference<>(page);
        if (page != null) page.addOnAttachStateChangeListener(pageAttachListener);
    }

    public LyricsController(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        this(pageContainer, controlHost, titleArtist, albumArt, null);
    }

    public LyricsController(
            ViewGroup pageContainer,
            ViewGroup controlHost,
            View titleArtist,
            View albumArt,
            Supplier<Bitmap> artworkSupplier) {
        ViewGroup targetPage = pageContainer != null ? pageContainer : controlHost;
        this.pageContainerRef = new WeakReference<>(targetPage);
        this.controlHostRef = new WeakReference<>(controlHost);
        this.titleArtistRef = new WeakReference<>(titleArtist);
        this.albumArtRef = new WeakReference<>(albumArt);
        this.artworkSupplier = artworkSupplier;
        this.context = targetPage.getContext();

        android.util.Log.i(TAG, "LyricsController init: targetPage="
                + targetPage.getClass().getName()
                + ", controlHost=" + (controlHost != null ? controlHost.getClass().getName() : "null")
                + ", titleArtist=" + (titleArtist != null ? titleArtist.getClass().getName() : "null"));

        this.lyricsView = new LyricsView(context);
        this.lyricsView.setVisibility(View.GONE);
        this.lyricsView.setAlpha(0f);
        this.lyricsView.setOnCloseListener(this::hideLyrics);

        this.lyricsView.setOnLyricSeekListener(timestampMs -> {
            MediaController controller = currentMediaController();
            if (controller != null) {
                try {
                    controller.getTransportControls().seekTo(timestampMs);
                    pendingSeekPositionMs = timestampMs;
                    pendingSeekElapsedMs = SystemClock.elapsedRealtime();
                    boolean playing = cachedPlaybackState != null
                            && cachedPlaybackState.getState() == PlaybackState.STATE_PLAYING;
                    lyricsView.updatePlaybackPosition(timestampMs, playing);
                } catch (Exception ignored) {}
            }
        });

        // Toggle button: Apple Music quotation speech bubble icon
        this.toggleButton = new LyricsToggleButton(context);
        this.toggleButton.setClickable(true);
        this.toggleButton.setFocusable(true);
        this.toggleButton.setElevation(100f);
        this.toggleButton.setOnTouchListener((v, event) -> {
            ViewParent p = v.getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        this.toggleButton.setOnClickListener(v -> toggleLyricsVisibility());

        setupControlHost(controlHost);

        bindPage(targetPage);

        requestHostAttachment();

        updateLyricsBounds();
        applyPreferences(RuntimeExtras.get(context));

        LiveMediaTracker.addListener(context, liveTrackListener);
        ensureExtrasListener(context);
        onTrackChanged();
    }

    private static synchronized void ensureExtrasListener(Context context) {
        if (extrasListenerInstalled || context == null) return;
        extrasListenerInstalled = true;
        Context app = context.getApplicationContext();
        Context holderCtx = app != null ? app : context;
        RuntimeExtras.listen(() -> Main.HANDLER.post(() -> updateAllPreferences(holderCtx)));
    }

    private void attachToggleButton(ViewGroup controlHost) {
        if (controlHost == null) return;
        if (toggleButton.getParent() == controlHost) return;
        if (toggleButton.getParent() instanceof ViewGroup oldParent) {
            oldParent.removeView(toggleButton);
        }
        int btnSize = dpToPx(36);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(btnSize, btnSize);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.setMargins(0, dpToPx(8), dpToPx(56), 0);
        controlHost.addView(toggleButton, btnLp);
    }

    private MediaController currentMediaController() {
        return currentMediaController(context);
    }

    private static MediaController currentMediaController(Context context) {
        MediaController ctrl = LiveMediaTracker.activeController();
        if (ctrl != null) return ctrl;

        if (context != null) {
            try {
                MediaSessionManager mgr = context.getSystemService(MediaSessionManager.class);
                if (mgr != null) {
                    List<MediaController> list = mgr.getActiveSessions(null);
                    if (list != null && !list.isEmpty()) {
                        for (MediaController mc : list) {
                            PlaybackState ps = mc.getPlaybackState();
                            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                                return mc;
                            }
                        }
                        return list.get(0);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private void syncMediaController(long nowUptimeMs) {
        MediaController ctrl = currentMediaController();
        lastBinderSyncUptimeMs = nowUptimeMs;
        // A session/metadata handoff is not a seek to the start of the song.
        if (ctrl == null) return;
        boolean sameSession = ctrl != null && observedController != null
                && ctrl.getSessionToken().equals(observedController.getSessionToken());
        if (ctrl != observedController && !sameSession) {
            detachMediaCallback();
            observedController = ctrl;
            if (ctrl != null) {
                try {
                    ctrl.registerCallback(mediaCallback, Main.HANDLER);
                } catch (RuntimeException ignored) {}
            }
        }
        PlaybackState state = observedController != null ? observedController.getPlaybackState() : null;
        if (state != null) cachedPlaybackState = state;
    }

    private void detachMediaCallback() {
        if (observedController != null) {
            try {
                observedController.unregisterCallback(mediaCallback);
            } catch (RuntimeException ignored) {}
            observedController = null;
        }
    }

    private ViewGroup findBestContainer(View child) {
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        if (shade != null) return shade;

        View root = child != null ? child.getRootView() : null;
        if (root instanceof ViewGroup rootVg) {
            if (root.getClass().getName().contains("NotificationShadeWindowView")) {
                return rootVg;
            }
            int nowBarId = root.getResources().getIdentifier("now_bar_rootview", "id", "com.android.systemui");
            if (nowBarId != 0) {
                View nb = root.findViewById(nowBarId);
                if (nb instanceof ViewGroup vg) return vg;
            }
        }
        for (ViewParent p = child != null ? child.getParent() : null; p instanceof View pv; p = pv.getParent()) {
            if (pv.getClass().getName().contains("NotificationShadeWindowView") && pv instanceof ViewGroup vg) {
                return vg;
            }
            if (pv.getClass().getName().contains("FaceWidgetMusicPage") && pv instanceof ViewGroup vg) {
                return vg;
            }
        }
        for (ViewParent p = child != null ? child.getParent() : null; p instanceof View pv; p = pv.getParent()) {
            if (pv.getId() != View.NO_ID) {
                try {
                    String name = pv.getResources().getResourceEntryName(pv.getId());
                    if ("facewidget_music_page_container".equals(name) && pv instanceof ViewGroup vg) {
                        return vg;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void attachLyricsView(ViewGroup container) {
        if (container == null) return;
        if (lyricsView.getParent() == container) return;
        if (lyricsView.getParent() instanceof ViewGroup oldParent) {
            oldParent.removeOnLayoutChangeListener(layoutListener);
            oldParent.removeView(lyricsView);
        }
        FrameLayout.LayoutParams lyricsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lyricsLp.gravity = Gravity.TOP | Gravity.START;
        container.addView(lyricsView, lyricsLp);
        lyricsView.setElevation(80f);
        container.addOnLayoutChangeListener(layoutListener);
    }

    public static void hideActiveLyrics() {
        Main.HANDLER.post(() -> {
            if (activeInstance != null && activeInstance.isVisible) {
                activeInstance.hideLyrics();
            }
        });
    }

    /**
     * Pushes newly loaded or upgraded system artwork from {@link MediaFullscreenExtras} or
     * {@link OneUi85FullscreenArt} straight into the lyrics header thumbnail and local backdrop.
     */
    public static void notifyArtworkUpdated(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        Main.HANDLER.post(() -> {
            if (activeInstance != null) {
                activeInstance.lyricsView.setArtwork(bitmap);
            }
            for (LyricsController ctrl : BOUND_CONTROLLERS.values()) {
                if (ctrl != null && ctrl != activeInstance) {
                    ctrl.lyricsView.setArtwork(bitmap);
                }
            }
        });
    }

    private void setupControlHost(ViewGroup controlHost) {
        if (controlHost == null) return;
        ViewGroup old = boundControls.get();
        if (old == controlHost) return;
        if (old != null) {
            old.removeOnAttachStateChangeListener(controlsAttachListener);
            old.removeOnLayoutChangeListener(layoutListener);
        }
        boundControls = new WeakReference<>(controlHost);
        controlHost.setOnTouchListener((v, event) -> lyricsActive);
        controlHost.addOnAttachStateChangeListener(controlsAttachListener);
        controlHost.addOnLayoutChangeListener(layoutListener);

        try {
            int devId = controlHost.getResources().getIdentifier(
                    "facewidget_music_page_device", "id", "com.samsung.android.app.aodservice");
            if (devId != 0) {
                View devView = controlHost.findViewById(devId);
                if (devView != null) {
                    devView.setOnTouchListener((v, event) -> lyricsActive);
                }
            }
            int outId = controlHost.getResources().getIdentifier(
                    "facewidget_music_icon_device_media_output_container", "id", "com.samsung.android.app.aodservice");
            if (outId != 0) {
                View outView = controlHost.findViewById(outId);
                if (outView != null) {
                    outView.setOnTouchListener((v, event) -> lyricsActive);
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized LyricsController bind(ViewGroup container) {
        return bind(container, container, null, null, null);
    }

    public static synchronized LyricsController bind(
            ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        return bind(pageContainer, controlHost, titleArtist, albumArt, null);
    }

    public static synchronized LyricsController bind(
            ViewGroup pageContainer,
            ViewGroup controlHost,
            View titleArtist,
            View albumArt,
            Supplier<Bitmap> artworkSupplier) {
        ViewGroup target = controlHost != null ? controlHost : pageContainer;
        if (target == null) return null;

        if (activeInstance != null) {
            activeInstance.updateTarget(pageContainer, target, titleArtist, albumArt, artworkSupplier);
            BOUND_CONTROLLERS.put(target, activeInstance);
            return activeInstance;
        }

        activeInstance = new LyricsController(pageContainer, target, titleArtist, albumArt, artworkSupplier);
        BOUND_CONTROLLERS.put(target, activeInstance);
        return activeInstance;
    }

    public void updateTarget(
            ViewGroup pageContainer,
            ViewGroup controlHost,
            View titleArtist,
            View albumArt,
            Supplier<Bitmap> artworkSupplier) {
        if (pageContainer != null) {
            this.pageContainerRef = new WeakReference<>(pageContainer);
            bindPage(pageContainer);
        }
        if (controlHost != null) {
            this.controlHostRef = new WeakReference<>(controlHost);
            setupControlHost(controlHost);
        }
        if (titleArtist != null) this.titleArtistRef = new WeakReference<>(titleArtist);
        if (albumArt != null) this.albumArtRef = new WeakReference<>(albumArt);
        if (artworkSupplier != null) this.artworkSupplier = artworkSupplier;

        requestHostAttachment();
        applyPreferences(RuntimeExtras.get(context));
        refreshCoverOnly();
    }

    private void updateLyricsBounds() {
        View controlHost = controlHostRef.get();
        if (controlHost == null) return;
        ViewGroup parent = (ViewGroup) lyricsView.getParent();
        if (parent == null) return;

        int[] hostLoc = new int[2];
        controlHost.getLocationInWindow(hostLoc);
        int[] parentLoc = new int[2];
        parent.getLocationInWindow(parentLoc);
        int hostY = hostLoc[1] - parentLoc[1];
        if (hostY <= 0) {
            if (parent.getHeight() > 0 && controlHost.getHeight() > 0) {
                hostY = parent.getHeight() - controlHost.getHeight();
            } else {
                hostY = 1750;
            }
        }

        int resId = parent.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = resId > 0 ? parent.getResources().getDimensionPixelSize(resId) : dpToPx(38);
        lyricsView.setStatusBarInset(0);

        int targetHeight = Math.max(dpToPx(200), hostY - statusBarHeight);
        ViewGroup.LayoutParams lp = lyricsView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams flp) {
            if (flp.width == ViewGroup.LayoutParams.MATCH_PARENT
                    && flp.topMargin == statusBarHeight
                    && flp.height == targetHeight) {
                return;
            }
            flp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.gravity = Gravity.TOP | Gravity.START;
            flp.topMargin = statusBarHeight;
            flp.height = targetHeight;
            flp.leftMargin = 0;
            flp.rightMargin = 0;
            flp.bottomMargin = 0;
            lyricsView.setLayoutParams(flp);
            lyricsView.bringToFront();
        } else if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
            if (mlp.width == ViewGroup.LayoutParams.MATCH_PARENT
                    && mlp.topMargin == statusBarHeight
                    && mlp.height == targetHeight) {
                return;
            }
            mlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mlp.height = targetHeight;
            mlp.topMargin = statusBarHeight;
            lyricsView.setLayoutParams(mlp);
        }
    }

    /**
     * Hides lockscreen clock/complication/indication views while lyrics are active,
     * skipping the status bar ({@code keyguard_header}) entirely and saving original alphas
     * so only views hidden by lyrics are restored on close.
     */
    private void hideLockscreenElements(ViewGroup root) {
        if (root == null) return;
        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = root.getChildAt(i);
            if (child == null || child == lyricsView) continue;
            if (STATUS_CLOCK_TAG.equals(child.getTag())) continue;

            String name = resourceEntryName(child);
            if ("keyguard_header".equals(name)
                    || "keyguard_status_bar_container".equals(name)
                    || "keyguard_left_container".equals(name)
                    || "keyguard_carrier_text_nio_container".equals(name)
                    || name.contains("blur")
                    || name.contains("backdrop")
                    || name.contains("lyrics_status_clock")) {
                continue;
            }

            String className = child.getClass().getSimpleName();
            if ("KeyguardStatusBarView".equals(className)) continue;

            if (name.contains("clock")
                    || name.contains("complication")
                    || name.contains("facewidget_clock")
                    || name.contains("lock_icon")
                    || className.contains("LockIcon")
                    || name.contains("keyguard_indication")) {
                float currentAlpha = child.getAlpha();
                if (!hiddenByLyrics.containsKey(child) && currentAlpha <= 0.01f) {
                    continue;
                }
                if (hiddenByLyrics.containsKey(child)) continue;
                hiddenByLyrics.put(child, currentAlpha);
                child.animate().cancel();
                child.animate()
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                        .start();
            } else if (child instanceof ViewGroup vg) {
                hideLockscreenElements(vg);
            }
        }
    }

    private void restoreLockscreenElements() {
        if (hiddenByLyrics.isEmpty()) return;
        for (Map.Entry<View, Float> entry : hiddenByLyrics.entrySet()) {
            View child = entry.getKey();
            Float originalAlpha = entry.getValue();
            if (child == null || originalAlpha == null) continue;
            child.animate().cancel();
            child.animate()
                    .alpha(originalAlpha)
                    .setDuration(220)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }
        hiddenByLyrics.clear();
    }

    /**
     * Swaps the lockscreen status bar carrier label for a native Samsung-styled status bar clock
     * using exact {@code QSClockIndicatorView} metrics and spacing while preserving notification
     * icons in {@code keyguard_notification_icon_container}.
     */
    private static void updateStatusBarClock(boolean show) {
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        if (shade == null && activeInstance != null) {
            View control = activeInstance.controlHostRef.get();
            if (control != null && control.getRootView() instanceof ViewGroup rg) {
                shade = rg;
            }
        }
        if (shade == null) return;

        ViewGroup keyguardHeader = findViewGroupByEntryName(shade, "keyguard_header");
        ViewGroup searchRoot = keyguardHeader != null ? keyguardHeader : shade;
        TextView carrier = findCarrierTextView(searchRoot);
        ViewGroup nioContainer = findViewGroupByEntryName(searchRoot, "keyguard_carrier_text_nio_container");
        View carrierSlot = resolveCarrierSlot(carrier, nioContainer);

        TextClock clock = statusClockRef.get();
        if (clock == null || !clock.isAttachedToWindow()) {
            clock = findViewByTagRecursively(searchRoot, STATUS_CLOCK_TAG);
            if (clock != null) statusClockRef = new WeakReference<>(clock);
        }

        if (clock == null && show) {
            ViewGroup targetContainer = resolveClockTargetContainer(searchRoot, nioContainer, carrier, carrierSlot);
            if (targetContainer != null) {
                clock = createNativeStatusBarClock(targetContainer, carrier, carrierSlot);
                attachClockToContainer(targetContainer, clock, carrier, carrierSlot);
                statusClockRef = new WeakReference<>(clock);
            }
        } else if (clock != null && show) {
            // Re-sync margins/padding/typography in case DPI, orientation, or carrier layout changed
            styleStatusBarClock(clock, carrier);
            if (clock.getParent() instanceof ViewGroup parent) {
                updateClockLayoutParams(parent, clock, carrier, carrierSlot);
            }
        }

        final int animGen = ++statusClockAnimGen;

        if (show) {
            if (carrierSlot != null) {
                View previousSlot = hiddenCarrierSlotRef.get();
                if (previousSlot != carrierSlot || savedCarrierSlotVisibility < 0) {
                    savedCarrierSlotVisibility = carrierSlot.getVisibility();
                    hiddenCarrierSlotRef = new WeakReference<>(carrierSlot);
                }
                carrierSlot.animate().cancel();
                boolean inLinear = carrierSlot.getParent() instanceof LinearLayout;
                carrierSlot.setVisibility(inLinear ? View.GONE : View.INVISIBLE);
            }
            if (clock != null) {
                clock.animate().cancel();
                applyStatusClockColor(clock, carrier);
                clock.setVisibility(View.VISIBLE);
                clock.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                        .start();
            }
        } else {
            final View slotToRestore = hiddenCarrierSlotRef.get() != null ? hiddenCarrierSlotRef.get() : carrierSlot;
            final int restoreVis = savedCarrierSlotVisibility >= 0 ? savedCarrierSlotVisibility : View.VISIBLE;
            if (clock != null) {
                final TextClock c = clock;
                c.animate().cancel();
                c.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                        .withEndAction(() -> {
                            if (statusClockAnimGen != animGen) return;
                            c.setVisibility(View.GONE);
                            restoreCarrierSlot(slotToRestore, restoreVis);
                        })
                        .start();
            } else {
                restoreCarrierSlot(slotToRestore, restoreVis);
            }
        }
    }

    private static void restoreCarrierSlot(View slot, int visibility) {
        hiddenCarrierSlotRef.clear();
        savedCarrierSlotVisibility = -1;
        if (slot == null) return;
        slot.animate().cancel();
        slot.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            slot.setAlpha(0f);
            slot.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }
    }

    private static void syncStatusBarClockColor() {
        TextClock clock = statusClockRef.get();
        if (clock == null || clock.getVisibility() != View.VISIBLE) return;
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        if (shade == null) return;
        TextView carrier = findCarrierTextView(shade);
        applyStatusClockColor(clock, carrier);
    }

    private static void applyStatusClockColor(TextClock clock, TextView carrier) {
        if (clock == null) return;
        int color = 0xEDFFFFFF;
        if (carrier != null && carrier.getCurrentTextColor() != 0) {
            color = carrier.getCurrentTextColor();
        }
        if (clock.getCurrentTextColor() != color) {
            clock.setTextColor(color);
        }
    }

    private static View resolveCarrierSlot(TextView carrier, ViewGroup nioContainer) {
        if (carrier == null) return null;
        if (nioContainer != null) {
            for (View cur = carrier; cur != null && cur != nioContainer; ) {
                ViewParent p = cur.getParent();
                if (p == nioContainer) return cur;
                cur = p instanceof View pv ? pv : null;
            }
        }
        return carrier;
    }

    private static ViewGroup resolveClockTargetContainer(
            ViewGroup searchRoot, ViewGroup nioContainer, TextView carrier, View carrierSlot) {
        if (nioContainer instanceof LinearLayout && carrierSlot != null && carrierSlot.getParent() == nioContainer) {
            return nioContainer;
        }
        if (carrier != null && carrier.getParent() instanceof ViewGroup cp) {
            return cp;
        }
        ViewGroup left = findViewGroupByEntryName(searchRoot, "keyguard_left_container");
        if (left != null) return left;
        ViewGroup bar = findViewGroupByEntryName(searchRoot, "keyguard_status_bar_container");
        return bar != null ? bar : searchRoot;
    }

    private static TextClock createNativeStatusBarClock(
            ViewGroup container, TextView carrier, View carrierSlot) {
        TextClock clock = new TextClock(container.getContext());
        clock.setTag(STATUS_CLOCK_TAG);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        clock.setShadowLayer(0f, 0f, 0f, 0);
        styleStatusBarClock(clock, carrier);
        clock.setAlpha(0f);
        clock.setVisibility(View.GONE);
        return clock;
    }

    private static void styleStatusBarClock(TextClock clock, TextView carrier) {
        Context ctx = clock.getContext();
        Resources res = ctx.getResources();

        // Match Samsung's real QSClockIndicatorView (status_bar.xml): fontFamily="sec", weight=600
        Typeface secSemiBold = Typeface.create(Typeface.create("sec", Typeface.NORMAL), 600, false);
        clock.setTypeface(secSemiBold != null ? secSemiBold : Typeface.create("sans-serif-medium", Typeface.NORMAL));

        float sizePx = 0f;
        int clockSizeId = res.getIdentifier("status_bar_clock_size", "dimen", "com.android.systemui");
        if (clockSizeId != 0) {
            try {
                sizePx = res.getDimension(clockSizeId);
            } catch (Resources.NotFoundException ignored) {}
        }
        if (sizePx <= 0f && carrier != null && carrier.getTextSize() > 0f) {
            sizePx = carrier.getTextSize();
        }
        if (sizePx > 0f) {
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        } else {
            clock.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        }

        if (carrier != null) {
            clock.setIncludeFontPadding(carrier.getIncludeFontPadding());
            clock.setPaddingRelative(
                    carrier.getPaddingStart(),
                    carrier.getPaddingTop(),
                    0,
                    carrier.getPaddingBottom());
        } else {
            clock.setIncludeFontPadding(false);
            int topPadId = res.getIdentifier("status_bar_padding_top", "dimen", "com.android.systemui");
            int topPad = 0;
            if (topPadId != 0) {
                try {
                    topPad = res.getDimensionPixelSize(topPadId);
                } catch (Resources.NotFoundException ignored) {}
            }
            clock.setPaddingRelative(0, topPad, 0, 0);
        }
        applyStatusClockColor(clock, carrier);
    }

    private static void attachClockToContainer(
            ViewGroup container, TextClock clock, TextView carrier, View carrierSlot) {
        if (container instanceof LinearLayout ll) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            applyClockMargins(container.getContext(), lp, carrier, carrierSlot, true);
            int insertIndex = 0;
            if (carrierSlot != null && carrierSlot.getParent() == ll) {
                insertIndex = Math.max(0, ll.indexOfChild(carrierSlot));
            }
            ll.addView(clock, insertIndex, lp);
        } else if (container instanceof RelativeLayout rl) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            applyClockMargins(container.getContext(), lp, carrier, carrierSlot, false);
            rl.addView(clock, lp);
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            applyClockMargins(container.getContext(), lp, carrier, carrierSlot, false);
            container.addView(clock, lp);
        }
    }

    private static void updateClockLayoutParams(
            ViewGroup container, TextClock clock, TextView carrier, View carrierSlot) {
        if (clock.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
            applyClockMargins(container.getContext(), mlp, carrier, carrierSlot, container instanceof LinearLayout);
            clock.setLayoutParams(mlp);
        }
    }

    private static void applyClockMargins(
            Context ctx,
            ViewGroup.MarginLayoutParams lp,
            TextView carrier,
            View carrierSlot,
            boolean hasFollowingIcons) {
        int startMargin = 0;
        int topMargin = 0;
        int bottomMargin = 0;

        if (carrier != null && carrier.getLayoutParams() instanceof ViewGroup.MarginLayoutParams cmp) {
            startMargin += cmp.getMarginStart();
            topMargin += cmp.topMargin;
            bottomMargin += cmp.bottomMargin;
        }
        if (carrierSlot != null && carrierSlot != carrier
                && carrierSlot.getLayoutParams() instanceof ViewGroup.MarginLayoutParams smp) {
            startMargin += smp.getMarginStart();
            topMargin += smp.topMargin;
            bottomMargin += smp.bottomMargin;
        }

        int endMargin = 0;
        if (hasFollowingIcons) {
            Resources res = ctx.getResources();
            int clockEndId = res.getIdentifier("status_bar_left_clock_end_padding", "dimen", "com.android.systemui");
            int nioMarginId = res.getIdentifier("keyguard_carrier_text_nio_default_margin", "dimen", "com.android.systemui");
            int clockEndPx = clockEndId != 0 ? safeDimenPx(res, clockEndId, 0) : 0;
            int nioMarginPx = nioMarginId != 0 ? safeDimenPx(res, nioMarginId, 0) : 0;
            endMargin = (clockEndPx + nioMarginPx) > 0
                    ? (clockEndPx + nioMarginPx)
                    : Math.round(TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 6f, res.getDisplayMetrics()));
        }

        lp.setMarginStart(startMargin);
        lp.setMarginEnd(endMargin);
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
    }

    private static int safeDimenPx(Resources res, int id, int fallback) {
        try {
            return res.getDimensionPixelSize(id);
        } catch (Resources.NotFoundException ignored) {
            return fallback;
        }
    }

    private static ViewGroup findViewGroupByEntryName(ViewGroup root, String targetName) {
        View found = findViewByResourceEntryName(root, targetName);
        return found instanceof ViewGroup vg ? vg : null;
    }

    private static TextView findCarrierTextView(ViewGroup root) {
        if (root == null) return null;
        View found = findViewByResourceEntryName(root, "keyguard_carrier_text");
        if (found instanceof TextView tv) return tv;
        View parent = root.getRootView();
        if (parent instanceof ViewGroup vg && vg != root) {
            found = findViewByResourceEntryName(vg, "keyguard_carrier_text");
            if (found instanceof TextView tv) return tv;
        }
        return null;
    }

    private static View findViewByResourceEntryName(View root, String targetName) {
        if (root == null) return null;
        if (targetName.equals(resourceEntryName(root))) return root;
        if (root instanceof ViewGroup vg) {
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = findViewByResourceEntryName(vg.getChildAt(i), targetName);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Exception ignored) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T findViewByTagRecursively(View root, Object tag) {
        if (root == null || tag == null) return null;
        if (tag.equals(root.getTag())) return (T) root;
        if (root instanceof ViewGroup vg) {
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                T found = findViewByTagRecursively(vg.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    public LyricsView lyricsView() {
        return lyricsView;
    }

    public static void updateAllPreferences(Context context) {
        if (context == null) return;
        try {
            Bundle extras = RuntimeExtras.get(context);
            if (activeInstance != null) {
                activeInstance.applyPreferences(extras);
            }
            for (LyricsController ctrl : BOUND_CONTROLLERS.values()) {
                if (ctrl != null && ctrl != activeInstance) {
                    ctrl.applyPreferences(extras);
                }
            }
        } catch (Exception ignored) {}
    }

    public void applyPreferences(Bundle extras) {
        if (extras == null) return;
        boolean enabled = extras.getBoolean("lockscreenLyrics", true);
        boolean autoScroll = extras.getBoolean("lyricsSyncedHighlight", true);
        int textSize = extras.getInt("lyricsTextSize", 28);
        setPreferences(enabled, autoScroll, textSize);

        String fontFile = extras.getString(ExtrasPreferences.LYRICS_FONT_FILE, "");
        Bundle fontAxes = extras.getBundle(ExtrasPreferences.LYRICS_FONT_AXES);
        int fontWeight = extras.getInt(
                ExtrasPreferences.LYRICS_FONT_WEIGHT,
                ExtrasPreferences.DEFAULT_LYRICS_FONT_WEIGHT);

        Typeface primary = VariableFonts.lyricsTypeface(context, fontFile, fontAxes, fontWeight);
        Typeface secondary = buildSecondaryTypeface(fontFile, fontAxes, fontWeight, primary);
        lyricsView.setLyricsTypeface(primary, secondary);
    }

    private Typeface buildSecondaryTypeface(
            String fontFile, Bundle fontAxes, int fontWeight, Typeface primary) {
        if (fontFile != null && !fontFile.isEmpty()) {
            if (fontAxes != null && fontAxes.containsKey("wght")) {
                Bundle lighter = new Bundle(fontAxes);
                lighter.putFloat("wght", Math.max(200f, fontAxes.getFloat("wght") - 150f));
                Typeface tf = VariableFonts.importedTypeface(context, fontFile, lighter);
                if (tf != null) return tf;
            }
            return primary;
        }
        int secondaryWeight = Math.max(300, fontWeight - 150);
        return VariableFonts.lyricsTypeface(context, "", null, secondaryWeight);
    }

    public void setPreferences(boolean enabled, boolean autoScroll, int textSizeSp) {
        if (!enabled) {
            toggleButton.setVisibility(View.GONE);
            hideLyrics();
        } else {
            toggleButton.setVisibility(View.VISIBLE);
        }
        lyricsView.setAutoScrollEnabled(autoScroll);
        lyricsView.setTextSizeSp(textSizeSp);
    }

    public void toggleLyricsVisibility() {
        long now = SystemClock.uptimeMillis();
        if (now - lastToggleMs < 320L) {
            return;
        }
        lastToggleMs = now;
        if (isVisible) {
            hideLyrics();
        } else {
            showLyrics();
        }
    }

    public void showLyrics() {
        if (isVisible) return;
        isVisible = true;
        lyricsActive = true;
        MediaCoverWakefulness.setLyricsBlur(true);
        MediaClockVisibility.setLyricsMode(true);
        MediaFullscreenExtras.invalidateAllHosts();

        // Check if Fullscreen Album Cover is providing the blurred backdrop; otherwise use local blurred cover
        boolean hasFullscreenBackdrop = MediaFullscreenExtras.isFullscreenBackdropActive()
                || OneUi85FullscreenArt.isFullscreenBackdropActive();
        lyricsView.setUseLocalBackdrop(!hasFullscreenBackdrop);
        if (!hasFullscreenBackdrop) {
            View art = albumArtRef.get();
            if (art != null) {
                savedAlbumArtAlpha = art.getAlpha();
                albumArtHiddenForLocalBackdrop = true;
                art.animate().cancel();
                art.animate().alpha(0f).setDuration(200).start();
            }
        }

        ViewGroup best = findBestContainer(controlHostRef.get());
        if (best != null && best != lyricsView.getParent()) {
            attachLyricsView(best);
        }

        // Refresh cover and metadata immediately so top cover art is guaranteed up to date
        refreshCoverOnly();
        if (currentTrackKey.isEmpty()) {
            onTrackChanged();
        }

        updateLyricsBounds();
        lyricsView.bringToFront();
        lyricsView.animateOpen();

        // Hide lockscreen clock and complications (preserving status bar & notification icons)
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        View control = controlHostRef.get();
        ViewGroup controlRoot = control != null && control.getRootView() instanceof ViewGroup rg ? rg : null;
        hideLockscreenElements(shade);
        if (controlRoot != null && controlRoot != shade) hideLockscreenElements(controlRoot);
        updateStatusBarClock(true);

        View titleArtist = titleArtistRef.get();
        if (titleArtist != null) {
            titleArtist.animate().cancel();
            titleArtist.animate()
                    .alpha(0f)
                    .translationY(dpToPx(12))
                    .setDuration(220)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }

        toggleButton.bringToFront();
        toggleButton.setActive(true);

        startTracking();
    }

    public void hideLyrics() {
        lyricsView.releaseScreenPin();
        if (!isVisible) return;
        isVisible = false;
        lyricsActive = false;
        MediaCoverWakefulness.setLyricsBlur(false);
        MediaClockVisibility.setLyricsMode(false);
        MediaFullscreenExtras.invalidateAllHosts();

        View host = controlHostRef.get();
        boolean hostMissing = host == null || !host.isAttachedToWindow() || host.getVisibility() != View.VISIBLE;

        if (hostMissing) {
            lyricsView.animate().cancel();
            lyricsView.setVisibility(View.GONE);
            lyricsView.setAlpha(0f);
        } else {
            lyricsView.animateClose(null);
        }

        if (albumArtHiddenForLocalBackdrop) {
            albumArtHiddenForLocalBackdrop = false;
            View art = albumArtRef.get();
            if (art != null) {
                art.animate().cancel();
                float restoreAlpha = savedAlbumArtAlpha >= 0f ? savedAlbumArtAlpha : 1f;
                art.animate().alpha(restoreAlpha).setDuration(220).start();
            }
            savedAlbumArtAlpha = -1f;
        }

        restoreLockscreenElements();
        updateStatusBarClock(false);

        View titleArtist = titleArtistRef.get();
        if (titleArtist != null) {
            titleArtist.animate().cancel();
            titleArtist.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                    .start();
        }

        toggleButton.setActive(false);

        stopTracking();
    }

    private void refreshCoverOnly() {
        MediaController controller = currentMediaController();
        MediaMetadata metadata = controller != null ? controller.getMetadata() : null;
        Bitmap cover = resolveCoverBitmap(metadata);
        if (cover != null && !cover.isRecycled()) {
            lyricsView.setArtwork(cover);
        }
    }

    private Bitmap resolveCoverBitmap(MediaMetadata metadata) {
        // 1. Direct system artwork supplier from One UI 9 / 8.5 media holder (uncapped bitmap!)
        if (artworkSupplier != null) {
            try {
                Bitmap supplied = artworkSupplier.get();
                if (supplied != null && !supplied.isRecycled()) return supplied;
            } catch (RuntimeException ignored) {}
        }

        // 2. Active fullscreen artwork backdrop bitmap (system-provided, high-res)
        Bitmap backdropBmp = MediaFullscreenExtras.currentArtworkBitmap();
        if (backdropBmp != null && !backdropBmp.isRecycled()) return backdropBmp;

        Bitmap legacyBackdropBmp = OneUi85FullscreenArt.currentArtworkBitmap();
        if (legacyBackdropBmp != null && !legacyBackdropBmp.isRecycled()) return legacyBackdropBmp;

        // 3. System media card ImageView drawable (handles FrameLayout containers & wrapped drawables)
        Bitmap viewBmp = extractBitmapFromView(albumArtRef.get());
        if (viewBmp != null && !viewBmp.isRecycled()) return viewBmp;

        // 4. MediaMetadata inline bitmaps (highest resolution available)
        if (metadata != null) {
            Bitmap best = null;
            for (String key : new String[] {
                    MediaMetadata.METADATA_KEY_ART,
                    MediaMetadata.METADATA_KEY_ALBUM_ART,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                Bitmap candidate = metadata.getBitmap(key);
                if (candidate != null && !candidate.isRecycled()
                        && (best == null || candidate.getWidth() > best.getWidth())) {
                    best = candidate;
                }
            }
            if (best != null) return best;
        }

        return null;
    }

    private static Bitmap extractBitmapFromView(View view) {
        if (view == null) return null;
        if (view instanceof ImageView iv) {
            return extractBitmapFromDrawable(iv.getDrawable());
        }
        if (view instanceof ViewGroup vg) {
            // Prefer Samsung's exact artwork ImageView first
            View bg = findViewByResourceEntryName(vg, "facewidget_album_art_background");
            if (bg instanceof ImageView iv) {
                Bitmap b = extractBitmapFromDrawable(iv.getDrawable());
                if (b != null) return b;
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                Bitmap b = extractBitmapFromView(vg.getChildAt(i));
                if (b != null) return b;
            }
        }
        return null;
    }

    private static Bitmap extractBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable bd) {
            Bitmap b = bd.getBitmap();
            return (b != null && !b.isRecycled()) ? b : null;
        }
        if (drawable instanceof LayerDrawable ld) {
            for (int i = ld.getNumberOfLayers() - 1; i >= 0; i--) {
                Bitmap b = extractBitmapFromDrawable(ld.getDrawable(i));
                if (b != null) return b;
            }
            return null;
        }
        // Fallback: render other drawable types (e.g. RoundedBitmapDrawable) into a thumbnail bitmap
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        int side = Math.min(256, Math.max(w, h));
        int tw = Math.max(1, Math.round(side * (w / (float) Math.max(w, h))));
        int th = Math.max(1, Math.round(side * (h / (float) Math.max(w, h))));
        try {
            Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            android.graphics.Rect oldBounds = new android.graphics.Rect(drawable.getBounds());
            int oldAlpha = drawable.getAlpha();
            drawable.setBounds(0, 0, tw, th);
            drawable.setAlpha(255);
            drawable.draw(canvas);
            drawable.setAlpha(oldAlpha);
            drawable.setBounds(oldBounds);
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void onTrackChanged() {
        try {
            MediaController controller = currentMediaController();
            if (controller == null) {
                return;
            }

            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) {
                return;
            }

            String artist = firstNonEmpty(
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
            String title = firstNonEmpty(
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            long durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (title == null || title.isBlank()) return;

            LyricsSanitizer.SanitizedTrack sanitized = LyricsSanitizer.sanitize(artist, title);
            String displayTitle = !sanitized.title().isEmpty() ? sanitized.title() : title;
            String displayArtist = !sanitized.artist().isEmpty() ? sanitized.artist() : artist;

            String trackKey = displayArtist + "|" + displayTitle + "|" + album;
            boolean isNewTrack = !trackKey.equals(currentTrackKey);

            Bitmap cover = resolveCoverBitmap(metadata);
            lyricsView.setTrackInfo(displayTitle, displayArtist, cover, isNewTrack);

            if (lyricsActive) {
                ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
                View control = controlHostRef.get();
                ViewGroup controlRoot = control != null && control.getRootView() instanceof ViewGroup rg ? rg : null;
                hideLockscreenElements(shade);
                if (controlRoot != null && controlRoot != shade) hideLockscreenElements(controlRoot);
                updateStatusBarClock(true);
            }

            if (!isNewTrack) return;
            currentTrackKey = trackKey;
            long requestGeneration = ++trackGeneration;
            pendingSeekPositionMs = -1L;
            cachedPlaybackState = controller.getPlaybackState();
            lastBinderSyncUptimeMs = SystemClock.uptimeMillis();

            // Reset lyrics view with loading state
            lyricsView.setLyrics(null);

            LyricsBridge.requestLyrics(context, displayArtist, displayTitle, album, durationMs, entry -> {
                if (requestGeneration == trackGeneration && trackKey.equals(currentTrackKey)) {
                    lyricsView.setLyrics(entry);
                    refreshCoverOnly();
                    if (isVisible) {
                        syncMediaController(SystemClock.uptimeMillis());
                        boolean playing = cachedPlaybackState != null
                                && cachedPlaybackState.getState() == PlaybackState.STATE_PLAYING;
                        lyricsView.updatePlaybackPosition(playbackPosition(), playing);
                    }
                }
            });
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Error in onTrackChanged", t);
        }
    }

    private void startTracking() {
        if (trackingActive) return;
        trackingActive = true;
        lastFrameNanos = 0L;
        syncMediaController(SystemClock.uptimeMillis());
        boolean playing = cachedPlaybackState != null
                && cachedPlaybackState.getState() == PlaybackState.STATE_PLAYING;
        lyricsView.updatePlaybackPosition(playbackPosition(), playing);
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopTracking() {
        trackingActive = false;
        lastFrameNanos = 0L;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        detachMediaCallback();
        cachedPlaybackState = null;
    }

    public static long calculatePosition(PlaybackState state) {
        if (state == null) return 0L;
        long pos = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long lastUpdate = state.getLastPositionUpdateTime();
            if (lastUpdate > 0L) {
                long delta = Math.max(0L, SystemClock.elapsedRealtime() - lastUpdate);
                float speed = state.getPlaybackSpeed();
                if (!Float.isFinite(speed) || speed <= 0.05f) speed = 1.0f;
                pos += Math.round(delta * speed);
            }
        }
        return Math.max(0L, pos);
    }

    private long playbackPosition() {
        long now = SystemClock.elapsedRealtime();
        if (pendingSeekPositionMs >= 0L && now - pendingSeekElapsedMs < 1500L) {
            float speed = cachedPlaybackState != null
                    && cachedPlaybackState.getState() == PlaybackState.STATE_PLAYING
                    ? cachedPlaybackState.getPlaybackSpeed() : 0f;
            if (!Float.isFinite(speed)) speed = 0f;
            return Math.max(0L, pendingSeekPositionMs
                    + Math.round((now - pendingSeekElapsedMs) * speed));
        }
        pendingSeekPositionMs = -1L;
        return calculatePosition(cachedPlaybackState);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
