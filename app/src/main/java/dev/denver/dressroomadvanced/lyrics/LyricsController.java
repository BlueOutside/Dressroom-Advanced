package dev.denver.dressroomadvanced.lyrics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
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

    private final WeakReference<ViewGroup> pageContainerRef;
    private final WeakReference<ViewGroup> controlHostRef;
    private final WeakReference<View> titleArtistRef;
    private final WeakReference<View> albumArtRef;

    private final LyricsView lyricsView;
    private final TextView toggleButton;
    private final Context context;

    private boolean isVisible = false;
    private boolean trackingActive = false;
    private String currentTrackKey = "";

    private final View.OnLayoutChangeListener layoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateLyricsBounds();

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

    private static MediaController currentMediaController() {
        try {
            Class<?> trackerClass = Class.forName("dev.denver.dressroomadvanced.LiveMediaTracker");
            java.lang.reflect.Method method = trackerClass.getDeclaredMethod("activeController");
            method.setAccessible(true);
            return (MediaController) method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public LyricsController(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        ViewGroup targetPage = pageContainer != null ? pageContainer : controlHost;
        this.pageContainerRef = new WeakReference<>(targetPage);
        this.controlHostRef = new WeakReference<>(controlHost);
        this.titleArtistRef = new WeakReference<>(titleArtist);
        this.albumArtRef = new WeakReference<>(albumArt);
        this.context = targetPage.getContext();

        this.lyricsView = new LyricsView(context);
        this.lyricsView.setVisibility(View.GONE);
        this.lyricsView.setAlpha(0f);

        // Seek when a user clicks a line
        this.lyricsView.setOnLyricSeekListener(timestampMs -> {
            MediaController controller = currentMediaController();
            if (controller != null) {
                try {
                    controller.getTransportControls().seekTo(timestampMs);
                } catch (Exception ignored) {}
            }
        });

        // Toggle button: subtle pill with text "LYRICS"
        this.toggleButton = new TextView(context);
        this.toggleButton.setText("LYRICS");
        this.toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        this.toggleButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        this.toggleButton.setTextColor(Color.WHITE);
        this.toggleButton.setGravity(Gravity.CENTER);

        int padH = dpToPx(8);
        int padV = dpToPx(3);
        this.toggleButton.setPadding(padH, padV, padH, padV);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.argb(120, 255, 255, 255));
        btnBg.setCornerRadius(dpToPx(12));
        this.toggleButton.setBackground(btnBg);

        this.toggleButton.setOnClickListener(v -> toggleLyricsVisibility());

        // Attach toggle button to the top right of controlHost
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.setMargins(0, dpToPx(10), dpToPx(16), 0);
        controlHost.addView(toggleButton, btnLp);

        // Attach lyrics view to pageContainer (covering album art + titleArtist)
        FrameLayout.LayoutParams lyricsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int margin = dpToPx(6);
        lyricsLp.setMargins(margin, margin, margin, margin);
        targetPage.addView(lyricsView, lyricsLp);

        targetPage.addOnLayoutChangeListener(layoutListener);
        if (controlHost != null && controlHost != targetPage) {
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

    public static LyricsController bind(ViewGroup container) {
        return bind(container, container, null, null);
    }

    public static LyricsController bind(ViewGroup pageContainer, ViewGroup controlHost, View titleArtist, View albumArt) {
        ViewGroup target = controlHost != null ? controlHost : pageContainer;
        if (target == null) return null;
        LyricsController existing = BOUND_CONTROLLERS.get(target);
        if (existing != null) return existing;

        LyricsController controller = new LyricsController(pageContainer, controlHost != null ? controlHost : pageContainer, titleArtist, albumArt);
        BOUND_CONTROLLERS.put(target, controller);
        return controller;
    }

    private void updateLyricsBounds() {
        ViewGroup page = pageContainerRef.get();
        if (page == null) return;
        View controlHost = controlHostRef.get();
        View albumArt = albumArtRef.get();

        int top = 0;
        if (albumArt != null && albumArt.getParent() == page) {
            top = albumArt.getTop();
        } else if (albumArt != null) {
            int[] artLoc = new int[2];
            int[] pageLoc = new int[2];
            albumArt.getLocationInWindow(artLoc);
            page.getLocationInWindow(pageLoc);
            top = Math.max(0, artLoc[1] - pageLoc[1]);
        }

        int bottom = page.getHeight();
        if (controlHost != null && controlHost.getParent() == page) {
            bottom = controlHost.getTop();
        } else if (controlHost != null) {
            int[] ctrlLoc = new int[2];
            int[] pageLoc = new int[2];
            controlHost.getLocationInWindow(ctrlLoc);
            page.getLocationInWindow(pageLoc);
            int relCtrlTop = ctrlLoc[1] - pageLoc[1];
            if (relCtrlTop > top) {
                bottom = relCtrlTop;
            }
        }

        int h = bottom - top;
        if (h > dpToPx(80)) {
            ViewGroup.LayoutParams lp = lyricsView.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
                if (mlp.height != h || mlp.topMargin != top) {
                    mlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    mlp.height = h;
                    mlp.topMargin = top;
                    if (mlp instanceof FrameLayout.LayoutParams flp) {
                        flp.gravity = Gravity.TOP;
                    }
                    lyricsView.setLayoutParams(mlp);
                }
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
            int textSize = b != null ? b.getInt("lyricsTextSize", 16) : 16;
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

    public void toggleLyricsVisibility() {
        if (isVisible) {
            hideLyrics();
        } else {
            showLyrics();
        }
    }

    public void showLyrics() {
        if (isVisible) return;
        isVisible = true;
        updateLyricsBounds();
        lyricsView.bringToFront();
        lyricsView.setVisibility(View.VISIBLE);
        lyricsView.animate()
                .alpha(1f)
                .setDuration(220)
                .setListener(null)
                .start();

        View titleArtist = titleArtistRef.get();
        if (titleArtist != null) {
            titleArtist.animate().alpha(0f).setDuration(180).start();
        }
        View albumArt = albumArtRef.get();
        if (albumArt != null) {
            albumArt.animate().alpha(0.12f).setDuration(180).start();
        }

        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(Color.argb(200, 255, 255, 255));
        activeBg.setCornerRadius(dpToPx(12));
        toggleButton.setBackground(activeBg);
        toggleButton.setTextColor(Color.BLACK);

        startTracking();
    }

    public void hideLyrics() {
        if (!isVisible) return;
        isVisible = false;
        lyricsView.animate()
                .alpha(0f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        lyricsView.setVisibility(View.GONE);
                    }
                })
                .start();

        View titleArtist = titleArtistRef.get();
        if (titleArtist != null) {
            titleArtist.animate().alpha(1f).setDuration(200).start();
        }
        View albumArt = albumArtRef.get();
        if (albumArt != null) {
            albumArt.animate().alpha(1f).setDuration(200).start();
        }

        GradientDrawable defaultBg = new GradientDrawable();
        defaultBg.setColor(Color.argb(120, 255, 255, 255));
        defaultBg.setCornerRadius(dpToPx(12));
        toggleButton.setBackground(defaultBg);
        toggleButton.setTextColor(Color.WHITE);

        stopTracking();
    }

    public void onTrackChanged() {
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

        String trackKey = artist + "|" + title;
        if (trackKey.equals(currentTrackKey)) return;
        currentTrackKey = trackKey;

        // Reset lyrics view with loading state
        lyricsView.setLyrics(null);

        LyricsBridge.requestLyrics(context, artist, title, album, durationMs, entry -> {
            if (trackKey.equals(currentTrackKey)) {
                lyricsView.setLyrics(entry);
                if (isVisible) {
                    PlaybackState state = controller.getPlaybackState();
                    lyricsView.updatePlaybackPosition(calculatePosition(state));
                }
            }
        });
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
