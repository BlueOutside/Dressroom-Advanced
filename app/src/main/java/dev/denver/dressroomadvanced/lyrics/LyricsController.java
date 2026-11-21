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
 * Drives the high-frequency playback position clock (~120ms) only when lyrics are visible.
 */
public final class LyricsController {
    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }
    private static final Map<View, LyricsController> BOUND_CONTROLLERS = new WeakHashMap<>();

    private final WeakReference<ViewGroup> hostContainerRef;
    private final LyricsView lyricsView;
    private final TextView toggleButton;
    private final Context context;

    private boolean isVisible = false;
    private boolean trackingActive = false;
    private String currentTrackKey = "";

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

    public LyricsController(ViewGroup hostContainer) {
        this.hostContainerRef = new WeakReference<>(hostContainer);
        this.context = hostContainer.getContext();

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
        this.toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        this.toggleButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        this.toggleButton.setTextColor(Color.WHITE);
        this.toggleButton.setGravity(Gravity.CENTER);

        int padH = dpToPx(10);
        int padV = dpToPx(5);
        this.toggleButton.setPadding(padH, padV, padH, padV);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.argb(120, 255, 255, 255));
        btnBg.setCornerRadius(dpToPx(14));
        this.toggleButton.setBackground(btnBg);

        this.toggleButton.setOnClickListener(v -> toggleLyricsVisibility());

        // Layout parameters
        FrameLayout.LayoutParams lyricsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int margin = dpToPx(8);
        lyricsLp.setMargins(margin, margin, margin, margin);

        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.setMargins(0, dpToPx(12), dpToPx(12), 0);

        hostContainer.addView(lyricsView, lyricsLp);
        hostContainer.addView(toggleButton, btnLp);

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
        if (container == null) return null;
        LyricsController existing = BOUND_CONTROLLERS.get(container);
        if (existing != null) return existing;

        LyricsController controller = new LyricsController(container);
        BOUND_CONTROLLERS.put(container, controller);
        return controller;
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
        lyricsView.setVisibility(View.VISIBLE);
        lyricsView.animate()
                .alpha(1f)
                .setDuration(220)
                .setListener(null)
                .start();

        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(Color.argb(200, 255, 255, 255));
        activeBg.setCornerRadius(dpToPx(14));
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

        GradientDrawable defaultBg = new GradientDrawable();
        defaultBg.setColor(Color.argb(120, 255, 255, 255));
        defaultBg.setCornerRadius(dpToPx(14));
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
