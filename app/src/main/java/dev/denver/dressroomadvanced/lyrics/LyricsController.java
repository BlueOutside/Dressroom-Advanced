package dev.denver.dressroomadvanced.lyrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import dev.denver.dressroomadvanced.MediaClockVisibility;
import dev.denver.dressroomadvanced.MediaFullscreenExtras;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Controller linking SystemUI's active media session with {@link LyricsView}.
 * Positions the lyrics view over the track title and album art area while keeping
 * bottom media controls (seekbar, play/pause, prev/next) unobstructed.
 */
public final class LyricsController {
    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }
    private static final Map<View, LyricsController> BOUND_CONTROLLERS = new WeakHashMap<>();
    private static boolean lyricsActive = false;

    public static boolean isLyricsActive() {
        return lyricsActive;
    }

    private WeakReference<ViewGroup> pageContainerRef;
    private WeakReference<ViewGroup> controlHostRef;
    private WeakReference<View> titleArtistRef;
    private WeakReference<View> albumArtRef;

    private final LyricsView lyricsView;
    private final LyricsToggleButton toggleButton;
    private final Context context;

    private boolean isVisible = false;
    private boolean trackingActive = false;
    private String currentTrackKey = "";

    private final View.OnLayoutChangeListener layoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (isVisible) {
                    View host = controlHostRef.get();
                    if (host == null || !host.isAttachedToWindow() || host.getVisibility() != View.VISIBLE || host.getHeight() <= 0) {
                        android.util.Log.i("DressroomLyrics", "layoutListener: host detached/hidden/zero height -> hideLyrics");
                        hideLyrics();
                        return;
                    }
                    updateLyricsBounds();
                }
            };

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!trackingActive || !isVisible) return;

            MediaController controller = currentMediaController();
            if (controller != null) {
                PlaybackState state = controller.getPlaybackState();
                long pos = calculatePosition(state);
                lyricsView.updatePlaybackPosition(pos);

                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    Main.HANDLER.postDelayed(this, 120L);
                    return;
                }
            }
            // Slower tick if paused
            Main.HANDLER.postDelayed(this, 350L);
        }
    };

    private MediaController currentMediaController() {
        return currentMediaController(context);
    }

    private static MediaController currentMediaController(Context context) {
        try {
            Class<?> trackerClass = Class.forName("dev.denver.dressroomadvanced.LiveMediaTracker");
            java.lang.reflect.Method method = trackerClass.getDeclaredMethod("activeController");
            method.setAccessible(true);
            MediaController ctrl = (MediaController) method.invoke(null);
            if (ctrl != null) return ctrl;
        } catch (Throwable ignored) {}

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

    public LyricsController(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        ViewGroup targetPage = pageContainer != null ? pageContainer : controlHost;
        this.pageContainerRef = new WeakReference<>(targetPage);
        this.controlHostRef = new WeakReference<>(controlHost);
        this.titleArtistRef = new WeakReference<>(titleArtist);
        this.albumArtRef = new WeakReference<>(albumArt);
        this.context = targetPage.getContext();

        android.util.Log.i("DressroomLyrics", "LyricsController init: targetPage="
                + targetPage.getClass().getName()
                + ", controlHost=" + (controlHost != null ? controlHost.getClass().getName() : "null")
                + ", titleArtist=" + (titleArtist != null ? titleArtist.getClass().getName() : "null"));

        this.lyricsView = new LyricsView(context);
        this.lyricsView.setVisibility(View.GONE);
        this.lyricsView.setAlpha(0f);
        this.lyricsView.setOnCloseListener(this::hideLyrics);

        // Seek when a user clicks a line
        this.lyricsView.setOnLyricSeekListener(timestampMs -> {
            MediaController controller = currentMediaController();
            if (controller != null) {
                try {
                    controller.getTransportControls().seekTo(timestampMs);
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
        this.toggleButton.setOnClickListener(v -> {
            android.util.Log.i("DressroomLyrics", "toggleButton clicked!");
            toggleLyricsVisibility();
        });

        if (titleArtist != null) {
            titleArtist.setClickable(true);
            titleArtist.setFocusable(true);
            titleArtist.setOnTouchListener((v, event) -> {
                ViewParent p = v.getParent();
                if (p != null) p.requestDisallowInterceptTouchEvent(true);
                return false;
            });
            titleArtist.setOnClickListener(v -> {
                android.util.Log.i("DressroomLyrics", "titleArtist clicked!");
                toggleLyricsVisibility();
            });
        }

        if (albumArt != null) {
            albumArt.setClickable(true);
            albumArt.setFocusable(true);
            albumArt.setOnTouchListener((v, event) -> {
                ViewParent p = v.getParent();
                if (p != null) p.requestDisallowInterceptTouchEvent(true);
                return false;
            });
            albumArt.setOnClickListener(v -> {
                android.util.Log.i("DressroomLyrics", "albumArt clicked!");
                toggleLyricsVisibility();
            });
        }

        // Register debug broadcast receiver so lyrics can be toggled via ADB
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter("dev.denver.dressroomadvanced.TOGGLE_LYRICS");
            context.registerReceiver(new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, android.content.Intent intent) {
                    toggleLyricsVisibility();
                }
            }, filter, Context.RECEIVER_EXPORTED);
        } catch (Exception ignored) {}

        // Attach toggle button to the top right of controlHost (positioned next to output switcher)
        int btnSize = dpToPx(36);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(btnSize, btnSize);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.setMargins(0, dpToPx(8), dpToPx(56), 0);
        if (toggleButton.getParent() == null) {
            controlHost.addView(toggleButton, btnLp);
        }

        setupControlHost(controlHost);

        targetPage.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                android.util.Log.i("DressroomLyrics", "targetPage detached from window -> hideLyrics");
                hideLyrics();
            }
        });

        attachLyricsView(targetPage);

        targetPage.addOnLayoutChangeListener(layoutListener);
        if (controlHost != targetPage) {
            controlHost.addOnLayoutChangeListener(layoutListener);
        }

        updateLyricsBounds();

        // Listen for media track updates
        try {
            Class<?> trackerClass = Class.forName("dev.denver.dressroomadvanced.LiveMediaTracker");
            java.lang.reflect.Method addListener = trackerClass.getDeclaredMethod("addListener", Context.class, Runnable.class);
            addListener.setAccessible(true);
            addListener.invoke(null, context, (Runnable) this::onTrackChanged);
        } catch (Exception e) {
            android.util.Log.w("LyricsController", "LiveMediaTracker registration failed", e);
        }

        onTrackChanged();
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
            oldParent.removeView(lyricsView);
        }
        FrameLayout.LayoutParams lyricsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lyricsLp.gravity = Gravity.TOP | Gravity.START;
        container.addView(lyricsView, lyricsLp);
        lyricsView.setElevation(80f);
        container.addOnLayoutChangeListener(layoutListener);
        android.util.Log.i("DressroomLyrics", "lyricsView attached to: " + container.getClass().getName());
    }

    private static LyricsController activeInstance;

    public static void hideActiveLyrics() {
        Main.HANDLER.post(() -> {
            if (activeInstance != null && activeInstance.isVisible) {
                android.util.Log.i("DressroomLyrics", "hideActiveLyrics called");
                activeInstance.hideLyrics();
            }
        });
    }

    private void setupControlHost(ViewGroup controlHost) {
        if (controlHost == null) return;
        controlHost.setOnTouchListener((v, event) -> {
            if (lyricsActive) {
                // Consume touches on empty background so Samsung doesn't collapse player while lyrics are active
                return true;
            }
            return false;
        });

        controlHost.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                android.util.Log.i("DressroomLyrics", "controlHost onViewAttachedToWindow");
                ViewGroup bestContainer = findBestContainer(v);
                if (bestContainer != null && bestContainer != pageContainerRef.get()) {
                    android.util.Log.i("DressroomLyrics", "Found better container on attach: " + bestContainer.getClass().getName());
                    pageContainerRef.clear();
                    pageContainerRef = new WeakReference<>(bestContainer);
                    attachLyricsView(bestContainer);
                }
                updateLyricsBounds();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                android.util.Log.i("DressroomLyrics", "controlHost onViewDetachedFromWindow -> hideLyrics");
                hideLyrics();
            }
        });

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
        return bind(container, container, null, null);
    }

    public static synchronized LyricsController bind(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        ViewGroup target = controlHost != null ? controlHost : pageContainer;
        if (target == null) return null;

        if (activeInstance != null) {
            activeInstance.updateTarget(pageContainer, target, titleArtist, albumArt);
            return activeInstance;
        }

        activeInstance = new LyricsController(pageContainer, target, titleArtist, albumArt);
        return activeInstance;
    }

    public void updateTarget(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        if (pageContainer != null) {
            this.pageContainerRef = new WeakReference<>(pageContainer);
            pageContainer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {}

                @Override
                public void onViewDetachedFromWindow(View v) {
                    android.util.Log.i("DressroomLyrics", "pageContainer onViewDetachedFromWindow -> hideLyrics");
                    hideLyrics();
                }
            });
        }
        if (controlHost != null) {
            this.controlHostRef = new WeakReference<>(controlHost);
            setupControlHost(controlHost);
            if (toggleButton.getParent() != controlHost) {
                if (toggleButton.getParent() instanceof ViewGroup p) p.removeView(toggleButton);
                int btnSize = dpToPx(36);
                FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(btnSize, btnSize);
                btnLp.gravity = Gravity.TOP | Gravity.END;
                btnLp.setMargins(0, dpToPx(8), dpToPx(56), 0);
                controlHost.addView(toggleButton, btnLp);
            }
        }
        if (titleArtist != null) this.titleArtistRef = new WeakReference<>(titleArtist);
        if (albumArt != null) this.albumArtRef = new WeakReference<>(albumArt);
        ViewGroup best = findBestContainer(controlHost != null ? controlHost : pageContainer);
        if (best != null && lyricsView.getParent() != best) {
            attachLyricsView(best);
        }
        updateLyricsBounds();
    }

    private void updateLyricsBounds() {
        View controlHost = controlHostRef.get();
        if (controlHost == null) return;
        ViewGroup parent = (ViewGroup) lyricsView.getParent();
        if (parent == null) return;

        int[] hostLoc = new int[2];
        controlHost.getLocationInWindow(hostLoc);
        int hostY = hostLoc[1];
        if (hostY <= 0) {
            if (parent.getHeight() > 0 && controlHost.getHeight() > 0) {
                hostY = parent.getHeight() - controlHost.getHeight();
            } else {
                hostY = 1750;
            }
        }

        int resId = parent.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = resId > 0 ? parent.getResources().getDimensionPixelSize(resId) : dpToPx(38);

        int targetHeight = Math.max(dpToPx(200), hostY - statusBarHeight);
        ViewGroup.LayoutParams lp = lyricsView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams flp) {
            if (flp.width == ViewGroup.LayoutParams.MATCH_PARENT && flp.topMargin == statusBarHeight && flp.height == targetHeight) {
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
            android.util.Log.i("DressroomLyrics", "updateLyricsBounds: top=" + statusBarHeight + ", height=" + targetHeight);
        } else if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
            if (mlp.width == ViewGroup.LayoutParams.MATCH_PARENT && mlp.topMargin == statusBarHeight && mlp.height == targetHeight) {
                return;
            }
            mlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mlp.height = targetHeight;
            mlp.topMargin = statusBarHeight;
            lyricsView.setLayoutParams(mlp);
        }
    }

    private static void hideMatchingViews(ViewGroup root, float targetAlpha) {
        if (root == null) return;
        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = root.getChildAt(i);
            if (child == null) continue;
            String name = "";
            if (child.getId() != View.NO_ID) {
                try {
                    name = child.getResources().getResourceEntryName(child.getId());
                } catch (Exception ignored) {}
            }
            if (name.contains("blur") || name.contains("backdrop")) {
                continue;
            }
            String className = child.getClass().getSimpleName();
            if (name.contains("clock") || name.contains("complication") || name.contains("facewidget_clock")
                    || name.contains("lock_icon") || className.contains("LockIcon")
                    || name.contains("keyguard_indication") || name.contains("carrier")) {
                android.util.Log.i("DressroomLyrics", "hideMatchingViews: " + name + " (" + className + ") -> alpha " + targetAlpha);
                child.animate().cancel();
                child.animate()
                        .alpha(targetAlpha)
                        .setDuration(220)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0.1f, 1f))
                        .start();
            } else if (child instanceof ViewGroup vg) {
                hideMatchingViews(vg, targetAlpha);
            }
        }
    }

    public LyricsView lyricsView() {
        return lyricsView;
    }

    public static void updateAllPreferences(Context context) {
        if (context == null) return;
        try {
            Class<?> runtimeExtrasClass = Class.forName("dev.denver.dressroomadvanced.RuntimeExtras");
            java.lang.reflect.Method enabledMethod = runtimeExtrasClass.getDeclaredMethod("enabled", Context.class, String.class);
            java.lang.reflect.Method getMethod = runtimeExtrasClass.getDeclaredMethod("get", Context.class);
            boolean lyricsEnabled = (Boolean) enabledMethod.invoke(null, context, "lockscreenLyrics");
            boolean autoScroll = (Boolean) enabledMethod.invoke(null, context, "lyricsSyncedHighlight");
            android.os.Bundle b = (android.os.Bundle) getMethod.invoke(null, context);
            int textSize = b != null ? b.getInt("lyricsTextSize", 28) : 28;
            for (LyricsController ctrl : BOUND_CONTROLLERS.values()) {
                if (ctrl != null) {
                    ctrl.setPreferences(lyricsEnabled, autoScroll, textSize);
                }
            }
        } catch (Exception ignored) {}
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

    private long lastToggleMs = 0L;

    public void toggleLyricsVisibility() {
        long now = SystemClock.uptimeMillis();
        if (now - lastToggleMs < 350L) {
            android.util.Log.i("DressroomLyrics", "toggleLyricsVisibility debounced (" + (now - lastToggleMs) + "ms)");
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
        MediaClockVisibility.setLyricsMode(true);
        MediaFullscreenExtras.invalidateAllHosts();

        ViewGroup best = findBestContainer(controlHostRef.get());
        if (best != null && best != lyricsView.getParent()) {
            attachLyricsView(best);
        }

        updateLyricsBounds();
        android.util.Log.i("DressroomLyrics", "showLyrics: lyricsView parent="
                + (lyricsView.getParent() != null ? lyricsView.getParent().getClass().getName() : "null")
                + " bounds=" + lyricsView.getLeft() + "," + lyricsView.getTop() + " " + lyricsView.getWidth() + "x" + lyricsView.getHeight());
        lyricsView.bringToFront();
        lyricsView.animateOpen();

        // Hide clock and complication widgets
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        View control = controlHostRef.get();
        ViewGroup controlRoot = control != null && control.getRootView() instanceof ViewGroup rg ? rg : null;
        hideMatchingViews(shade, 0f);
        if (controlRoot != null && controlRoot != shade) hideMatchingViews(controlRoot, 0f);

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
        if (!isVisible) return;
        isVisible = false;
        lyricsActive = false;
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

        // Restore clock and widgets container
        ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
        View control = controlHostRef.get();
        ViewGroup controlRoot = control != null && control.getRootView() instanceof ViewGroup rg ? rg : null;
        hideMatchingViews(shade, 1f);
        if (controlRoot != null && controlRoot != shade) hideMatchingViews(controlRoot, 1f);

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

    public void onTrackChanged() {
        try {
            MediaController controller = currentMediaController();
            if (controller == null) {
                lyricsView.setLyrics(null);
                currentTrackKey = "";
                return;
            }

            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) {
                lyricsView.setLyrics(null);
                currentTrackKey = "";
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

            LyricsSanitizer.SanitizedTrack sanitized = LyricsSanitizer.sanitize(artist, title);
            String displayTitle = !sanitized.title().isEmpty() ? sanitized.title() : title;
            String displayArtist = !sanitized.artist().isEmpty() ? sanitized.artist() : artist;

            String trackKey = displayArtist + "|" + displayTitle;
            android.util.Log.i("DressroomLyrics", "onTrackChanged: trackKey=" + trackKey + " (raw=" + artist + "|" + title + ")");

            Bitmap cover = null;
            if (metadata != null) {
                cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (cover == null) cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (cover == null && albumArtRef.get() instanceof ImageView iv && iv.getDrawable() instanceof BitmapDrawable bd) {
                cover = bd.getBitmap();
            }
            lyricsView.setTrackInfo(displayTitle, displayArtist, cover);

            if (lyricsActive) {
                ViewGroup shade = MediaFullscreenExtras.getNotificationShadeWindowView();
                View control = controlHostRef.get();
                ViewGroup controlRoot = control != null && control.getRootView() instanceof ViewGroup rg ? rg : null;
                hideMatchingViews(shade, 0f);
                if (controlRoot != null && controlRoot != shade) hideMatchingViews(controlRoot, 0f);
            }

            if (trackKey.equals(currentTrackKey)) return;
            currentTrackKey = trackKey;

            // Reset lyrics view with loading state
            lyricsView.setLyrics(null);

            LyricsBridge.requestLyrics(context, displayArtist, displayTitle, album, durationMs, entry -> {
                android.util.Log.i("DressroomLyrics", "Lyrics received: entry=" + (entry != null ? entry.source() : "null"));
                if (trackKey.equals(currentTrackKey)) {
                    lyricsView.setLyrics(entry);
                    if (isVisible) {
                        MediaController ctrl = currentMediaController();
                        PlaybackState state = ctrl != null ? ctrl.getPlaybackState() : null;
                        lyricsView.updatePlaybackPosition(calculatePosition(state));
                    }
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("DressroomLyrics", "Error in onTrackChanged", t);
        }
    }

    private void startTracking() {
        if (trackingActive) return;
        trackingActive = true;
        Main.HANDLER.removeCallbacks(tickRunnable);
        Main.HANDLER.post(tickRunnable);
    }

    private void stopTracking() {
        trackingActive = false;
        Main.HANDLER.removeCallbacks(tickRunnable);
    }

    public static long calculatePosition(PlaybackState state) {
        if (state == null) return 0L;
        long pos = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long delta = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            float speed = state.getPlaybackSpeed();
            if (speed <= 0.05f) speed = 1.0f;
            pos += (long) (delta * speed);
        }
        return Math.max(0L, pos);
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
