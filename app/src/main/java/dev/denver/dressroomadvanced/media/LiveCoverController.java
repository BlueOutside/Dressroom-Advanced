package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared Style A live album cover coordinator shared by {@link OneUi85FullscreenArt} (One UI 8.5)
 * and {@link MediaFullscreenExtras} (One UI 9). Owns the outline-clipped {@link TextureView},
 * deduplicated async provider resolution, local LRU cache lookup, and power/collapse gating.
 */
final class LiveCoverController implements LiveCoverPlayer.FrameListener {
    static final String KEY = "liveMediaArtwork";
    static final String WIFI_ONLY = "liveMediaWifiOnly";
    static final String SOURCE = "liveMediaSource";
    static final int SOURCE_SPOTIFY_FIRST = 0;
    static final int SOURCE_APPLE_FIRST = 1;
    static final int SOURCE_SPOTIFY_ONLY = 2;
    static final int SOURCE_APPLE_ONLY = 3;

    private static final String CACHE_DIR_NAME = "dressroom_live_covers";
    static final long BLUR_FRAME_INTERVAL_MS = 120L;
    static final int BLUR_FRAME_MAX_SIDE = 240;
    static final long CONTRAST_SAMPLE_INTERVAL_MS = 250L;
    static final int CONTRAST_SAMPLE_MAX_SIDE = 64;
    static final int SNAPSHOT_MAX_SIDE = 1440;

    private static boolean gpuLayerChecked;
    private static Method getTextureLayerMethod;
    private static Method drawTextureLayerMethod;
    private static Class<?> recordingCanvasClass;

    private static final AnimatedCoverProvider SPOTIFY_PROVIDER =
            new SpotifyCanvasAnimatedCoverProvider();
    private static final AnimatedCoverProvider APPLE_PROVIDER =
            new AppleMusicAnimatedCoverProvider();
    private static final ExecutorService resolvePool =
            Executors.newFixedThreadPool(
                    2,
                    runnable -> {
                        Thread t = new Thread(runnable, "DressroomLiveCoverNet");
                        t.setDaemon(true);
                        return t;
                    });
    private static final LatestCoverRequests<AnimatedCoverProvider.AnimatedCoverAsset> requests =
            new LatestCoverRequests<>(resolvePool, 2);

    private static volatile AnimatedCoverCache sharedCache;

    private final Context context;
    private final Context appContext;
    private final PowerManager power;
    private final DisplayManager displays;
    private final FrameLayout surfaceHost;
    private final TextureView textureView;
    private final android.widget.ImageView frozenView;
    private final LiveCoverPlayer player;
    private final Runnable onBlurFrameDue;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable trackChangedListener = this::onActiveTrackChanged;
    private final RectF frameCrop = new RectF();
    private final RectF sampleCrop = new RectF();
    private final RectF contrastBounds = new RectF();
    private final RectF ambientBounds = new RectF();
    private final ValueAnimator fadeToStatic = ValueAnimator.ofFloat(1f, 0f);

    private float outlineCornerRadius = -1f;
    private int layoutLeft = Integer.MIN_VALUE;
    private int layoutTop = Integer.MIN_VALUE;
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    private boolean attached;
    private boolean pausedForPower;
    private boolean activeWanted;
    private boolean closingLive;
    private boolean nativeDozing;
    private float coverOpacity;
    private String activeRequestKey = "";
    private String activeAlbumKey = "";
    private int activeSourceMode = -1;
    private boolean resolving;
    private AnimatedCoverProvider.AnimatedCoverAsset activeAsset;
    private Bitmap lastVideoFrame;
    private Bitmap frameBuffer;
    private Bitmap frozenFrame;
    private Bitmap closingFrame;
    private Bitmap ambientFrame;
    private Bitmap wakeAmbientArt;
    private AnimatedCoverProvider.AnimatedCoverAsset ambientDecodeAsset;
    private String ambientTrackKey = "";
    private Bitmap lastNativeArtwork;
    private boolean ambientArtworkIsLive;
    private boolean ambientAwaitingArtwork;
    private boolean ambientTrackSwitched;
    private float frozenMix = 1f;
    private boolean ambientVisible;
    private final Paint ambientPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private ArtworkBlurFilter.Layers ambientBlur;
    private Bitmap blurredAmbientFrame;
    private final Runnable artworkTimeout = this::revealStaticArtwork;
    private long lastBlurSampleUptimeMs;
    private boolean blurSampleRequested;
    private Bitmap contrastSample;
    private Bitmap contrastBuffer;
    private long lastContrastSampleUptimeMs;
    private boolean contrastSampleRequested;

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) {}
        @Override public void onDisplayRemoved(int id) {}
        @Override public void onDisplayChanged(int id) {
            Display display = surfaceHost.getDisplay();
            if (display != null && display.getDisplayId() == id
                    && (activeWanted || ambientFrame != null)) {
                if (isDozing() || !interactive()) captureAmbientFrame();
                updateAmbientDisplay();
                invalidateCover();
            }
        }
    };

    private final BroadcastReceiver powerReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    wakefulnessChanged(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())
                            || refreshPowerRestriction());
                }
            };

    LiveCoverController(Context context, Runnable onBlurFrameDue) {
        this.context = context;
        this.power = context.getSystemService(PowerManager.class);
        this.displays = context.getSystemService(DisplayManager.class);
        Context app = context.getApplicationContext();
        this.appContext = app != null ? app : context;
        this.onBlurFrameDue = onBlurFrameDue;
        this.player = new LiveCoverPlayer(this);

        this.surfaceHost = new FrameLayout(context);
        this.surfaceHost.setClickable(false);
        this.surfaceHost.setFocusable(false);
        this.surfaceHost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        this.surfaceHost.setClipToOutline(true);
        this.surfaceHost.setVisibility(View.INVISIBLE);
        this.surfaceHost.setOutlineProvider(
                new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
                            outline.setEmpty();
                            return;
                        }
                        float radius = Math.max(0f, outlineCornerRadius);
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });

        this.frozenView = new android.widget.ImageView(context);
        frozenView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        frozenView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surfaceHost.addView(frozenView, new FrameLayout.LayoutParams(-1, -1));
        this.textureView = new TextureView(context);
        this.textureView.setClickable(false);
        this.textureView.setFocusable(false);
        this.textureView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        this.surfaceHost.addView(
                textureView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        this.player.bind(textureView);
        fadeToStatic.setDuration(240);
        fadeToStatic.addUpdateListener(animation -> {
            float mix = (Float) animation.getAnimatedValue();
            if (mix <= 0f) clearFrozenFrame();
            else setFrozenMix(mix);
        });
    }

    FrameLayout hostView() {
        return surfaceHost;
    }

    void onAttached() {
        if (attached) return;
        attached = true;
        MediaCoverWakefulness.add(this);
        if (displays != null) displays.registerDisplayListener(displayListener, main);
        refreshPowerRestriction();
        LiveMediaTracker.addListener(context, trackChangedListener);
        onActiveTrackChanged();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            appContext.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } catch (RuntimeException ignored) {
        }
    }

    void onDetached() {
        if (!attached) return;
        attached = false;
        MediaCoverWakefulness.remove(this);
        if (displays != null) displays.unregisterDisplayListener(displayListener);
        LiveMediaTracker.removeListener(trackChangedListener);
        try {
            appContext.unregisterReceiver(powerReceiver);
        } catch (RuntimeException ignored) {
        }
        ambientFrame = null;
        ambientDecodeAsset = null;
        reset();
        if (ambientBlur != null) ambientBlur.detach();
        ambientBlur = null;
    }

    void update(
            boolean featureEnabled,
            boolean visibleExpanded,
            boolean closing,
            float left,
            float top,
            float right,
            float bottom,
            float cornerRadius,
            float opacity) {
        boolean continueClosing = closing && (activeWanted || closingLive) && player.isVideoVisible()
                && !isDozing() && interactive();
        boolean liveAllowed = featureEnabled
                        && (visibleExpanded || continueClosing)
                        && opacity > 0.01f
                        && RuntimeExtras.enabled(context, KEY);

        if (!liveAllowed) {
            closingLive = false;
            activeWanted = false;
            player.pauseAndHide();
            if (!closing) clearFrozenFrame();
            if (surfaceHost.getVisibility() != View.INVISIBLE) {
                surfaceHost.setVisibility(View.INVISIBLE);
            }
            return;
        }

        int l = Math.round(left);
        int t = Math.round(top);
        int w = Math.max(0, Math.round(right) - l);
        int h = Math.max(0, Math.round(bottom) - t);
        if (w <= 0 || h <= 0) {
            activeWanted = false;
            player.pauseAndHide();
            return;
        }

        applyGeometry(l, t, w, h, cornerRadius);
        if (surfaceHost.getVisibility() != View.VISIBLE) {
            surfaceHost.setVisibility(View.VISIBLE);
        }

        activeWanted = true;
        coverOpacity = opacity;
        frozenView.setAlpha(opacity * frozenMix);
        if (continueClosing) {
            beginCollapse();
            player.updateOpacity(opacity);
            return;
        }
        closingLive = false;
        if (closingFrame != null) {
            if (frozenFrame == null) showFrozenFrame(closingFrame);
            closingFrame = null;
        }
        if (isDozing() || refreshPowerRestriction()) {
            freezeFrame();
            return;
        }
        AnimatedCoverProvider.TrackQuery query = LiveMediaTracker.currentTrack(context, w);
        if (query == null || !query.isValid()) {
            waitForTrack();
            return;
        }

        int sourceMode = currentSourceMode();
        String requestKey = trackRequestKey(query, sourceMode);
        if (!requestKey.equals(activeRequestKey)) {
            startTrack(query, sourceMode);
            return;
        }

        if (activeAsset != null && activeAsset.isPlayableLocalFile()) {
            player.play(activeAsset.cacheKey(), activeAsset.videoFile(), opacity);
        } else {
            player.updateOpacity(opacity);
            if (!resolving && !main.hasCallbacks(artworkTimeout)) revealStaticArtwork();
        }
    }

    static boolean shouldSampleBlurFrame(
            long nowMs, long lastSampleMs, boolean closingLive, boolean pending) {
        return !closingLive
                && !pending
                && (lastSampleMs == 0L || nowMs - lastSampleMs >= BLUR_FRAME_INTERVAL_MS);
    }

    static Bitmap selectClosingFrame(Bitmap latestVideoFrame, Bitmap frozenFrame) {
        if (latestVideoFrame != null && !latestVideoFrame.isRecycled()) return latestVideoFrame;
        if (frozenFrame != null && !frozenFrame.isRecycled()) return frozenFrame;
        return null;
    }

    void beginCollapse() {
        if (closingLive || closingFrame != null) return;
        closingLive = activeWanted && player.isVideoVisible();
        Bitmap frame = closingLive ? selectClosingFrame(lastVideoFrame, frozenFrame) : frozenFrame;
        if (frame == null && closingLive) frame = captureTextureFrame(BLUR_FRAME_MAX_SIDE);
        closingFrame = frame;
        if (closingFrame != null) invalidateCover();
    }

    Bitmap closingStill() {
        return closingFrame;
    }

    void reset() {
        activeWanted = false;
        closingLive = false;
        activeRequestKey = "";
        activeAlbumKey = "";
        activeSourceMode = -1;
        resolving = false;
        activeAsset = null;
        player.resetMedia();
        closingFrame = null;
        clearFrozenFrame();
        releaseAmbient();
        ambientTrackSwitched = false;
        wakeAmbientArt = null;
        if (surfaceHost.getVisibility() != View.INVISIBLE) {
            surfaceHost.setVisibility(View.INVISIBLE);
        }
        lastVideoFrame = frameBuffer = null;
        contrastSample = contrastBuffer = null;
        lastBlurSampleUptimeMs = 0L;
        lastContrastSampleUptimeMs = 0L;
        blurSampleRequested = false;
        contrastSampleRequested = false;
    }

    void releaseAmbient() {
        ambientFrame = lastNativeArtwork = null;
        ambientDecodeAsset = null;
        ambientTrackKey = "";
        ambientArtworkIsLive = ambientAwaitingArtwork = false;
        ambientTrackSwitched = false;
        wakeAmbientArt = null;
        if (ambientBlur != null) ambientBlur.discardDisplayLists();
        blurredAmbientFrame = null;
        updateAmbientDisplay();
    }

    boolean ambientHandoff() {
        return ambientVisible || isDozing() || !interactive()
                || (ambientFrame != null || ambientDecodeAsset != null)
                        && (nativeDozing
                                || MediaCoverWakefulness.sleeping
                                || MediaCoverWakefulness.panelDozing);
    }

    boolean isVideoVisible() {
        return activeWanted && (frozenFrame != null || player.isVideoVisible());
    }

    boolean coversBlurSource() {
        return activeWanted
                && !closingLive
                && frozenFrame == null
                && player.isVideoVisible()
                && player.revealMix() >= 0.995f
                && (canUseGpuBlurLayer()
                        || (lastVideoFrame != null && !lastVideoFrame.isRecycled()));
    }

    void holdVideoRevealDuringPoster(boolean posterMoving) {
        // A video-to-video handoff already has the frozen outgoing video beneath the reveal.
        player.holdReveal(posterMoving && frozenFrame == null);
    }

    boolean retainOnWallpaperAod(Bitmap staticArtwork) {
        if (isDozing() || !interactive()) {
            captureAmbientFrame();
            if (staticArtwork != null && !staticArtwork.isRecycled() && (ambientFrame == null
                    || (ambientAwaitingArtwork && staticArtwork != lastNativeArtwork))) {
                lastNativeArtwork = staticArtwork;
                ambientFrame = staticArtwork;
                if (ambientTrackSwitched) wakeAmbientArt = staticArtwork;
                ambientArtworkIsLive = false;
                ambientAwaitingArtwork = false;
                invalidateCover();
            }
        } else if (staticArtwork != null) {
            lastNativeArtwork = staticArtwork;
        }
        return updateAmbientDisplay();
    }

    private boolean interactive() {
        return power == null || power.isInteractive();
    }

    private boolean isDozing() {
        Display display = surfaceHost.getDisplay();
        if (display == null) return false;
        int state = display.getState();
        return state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND;
    }

    private boolean updateAmbientDisplay() {
        boolean wasVisible = ambientVisible;
        boolean dozing = isDozing();
        if (interactive() && !dozing
                && (wasVisible || !MediaCoverWakefulness.sleeping && !MediaCoverWakefulness.panelDozing)) {
            ambientFrame = null;
            ambientDecodeAsset = null;
            ambientTrackKey = "";
            ambientArtworkIsLive = false;
            ambientAwaitingArtwork = false;
        }
        ambientVisible = dozing && ambientFrame != null && wallpaperAodEnabled();
        if (wasVisible != ambientVisible) {
            android.util.Log.d(DressroomModule.TAG, "Fullscreen media AOD: " + ambientVisible
                    + " liveFrame=" + (ambientDecodeAsset != null) + " displayDoze=" + dozing);
            if (!ambientVisible) {
                if (ambientBlur != null) ambientBlur.discardDisplayLists();
                blurredAmbientFrame = null;
            }
            invalidateCover();
        }
        return ambientVisible;
    }

    private boolean wallpaperAodEnabled() {
        if (!RuntimeExtras.enabled(context, "fullscreenArt")) return false;
        try {
            var resolver = context.getContentResolver();
            int main = android.provider.Settings.System.getInt(resolver, WallpaperAod.MAIN,
                    WallpaperAod.supported() ? 1 : 0);
            int showWallpaper = ScreenIdentity.current() == ScreenIdentity.SUB
                    ? android.provider.Settings.System.getInt(resolver, WallpaperAod.COVER, main) : main;
            return showWallpaper == 1
                    && android.provider.Settings.System.getInt(resolver, "aod_mode", 1) == 1;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    boolean isAmbientVisible() {
        return ambientVisible;
    }

    Bitmap ambientBitmap() {
        return ambientVisible ? ambientFrame : null;
    }

    Bitmap consumeWakeAmbientArt() {
        if (!ambientTrackSwitched) return null;
        Bitmap art = wakeAmbientArt;
        ambientTrackSwitched = false;
        wakeAmbientArt = null;
        return art != null && !art.isRecycled() ? art : null;
    }

    ArtworkBlurFilter.Layers ambientArtworkBlur() {
        return ambientVisible && ambientBlur != null && ambientBlur.hasBlurred() ? ambientBlur : null;
    }

    void nativeDozeChanged(boolean dozing) {
        if (nativeDozing == dozing) return;
        nativeDozing = dozing;
        wakefulnessChanged(dozing);
    }

    void wakefulnessChanged(boolean sleeping) {
        if (sleeping || isDozing() || !interactive()) captureAmbientFrame();
        else if (activeWanted) {
            pausedForPower = false;
            if (activeAsset != null && activeAsset.isPlayableLocalFile()
                    && (ambientTrackKey.isEmpty() || ambientTrackKey.equals(activeRequestKey))
                    && !refreshPowerRestriction())
                playResolvedCover();
            else if (!resolving && !refreshPowerRestriction()) revealStaticArtwork();
        }
        updateAmbientDisplay();
        invalidateCover();
    }

    private void captureAmbientFrame() {
        if (!activeWanted || ambientFrame != null || ambientDecodeAsset != null) return;
        ambientTrackSwitched = false;
        wakeAmbientArt = null;
        ambientTrackKey = activeRequestKey;
        // Capture once at full preview resolution before pausing; fall back to off-thread decode.
        Bitmap frame = captureTextureFrame(SNAPSHOT_MAX_SIDE);
        if (frame != null) {
            showFrozenFrame(frame);
            ambientFrame = frame;
            ambientArtworkIsLive = true;
        }
        AnimatedCoverProvider.AnimatedCoverAsset asset = activeAsset;
        pausedForPower = true;
        player.pauseAndHide();
        if (frame != null || asset == null || !asset.isPlayableLocalFile()) return;
        ambientDecodeAsset = asset;
        player.readPausedFrame(asset.videoFile(), decoded -> {
            if (!attached || ambientDecodeAsset != asset) return;
            showAmbientVideoFrame(decoded);
        });
    }

    private void showAmbientVideoFrame(Bitmap frame) {
        ambientFrame = frame;
        if (ambientTrackSwitched) wakeAmbientArt = frame;
        ambientArtworkIsLive = true;
        ambientAwaitingArtwork = false;
        if (activeWanted) showFrozenFrame(frame);
        updateAmbientDisplay();
        invalidateCover();
    }

    private void freezeFrame() {
        if (pausedForPower) return;
        pausedForPower = true;
        if (frozenFrame == null && activeWanted) {
            Bitmap frame = captureFrame(SNAPSHOT_MAX_SIDE);
            if (frame != null) showFrozenFrame(frame);
        }
        if (frozenFrame != null) {
            fadeToStatic.cancel();
            main.removeCallbacks(artworkTimeout);
            setFrozenMix(1f);
        }
        player.pauseAndHide();
        if (activeWanted) invalidateCover();
    }

    private Bitmap captureFrame(int maxSide) {
        Bitmap frame = captureTextureFrame(maxSide);
        return frame != null ? frame : copyLastVideoFrame();
    }

    private Bitmap captureTextureFrame(int maxSide) {
        if (player.isVideoVisible() && textureView.isAvailable()) {
            // TextureView readback skips the transform used to draw it on screen.
            // Give the readback the decoded aspect ratio and crop it when drawing.
            int width = player.videoWidth(), height = player.videoHeight();
            if (width > 0 && height > 0) {
                float scale = Math.min(1f, maxSide / (float) Math.max(width, height));
                try {
                    Bitmap frame = textureView.getBitmap(
                            Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                    if (hasPixels(frame)) return frame;
                } catch (RuntimeException error) {
                    DressroomModule.note("Live artwork pause frame unavailable", error);
                }
            }
        }
        return null;
    }

    private Bitmap copyLastVideoFrame() {
        return lastVideoFrame == null ? null : lastVideoFrame.copy(Bitmap.Config.ARGB_8888, false);
    }

    private static boolean hasPixels(Bitmap frame) {
        return frame != null && !frame.isRecycled()
                && (frame.getPixel(frame.getWidth() / 2, frame.getHeight() / 2) >>> 24) != 0;
    }

    private void showFrozenFrame(Bitmap frame) {
        fadeToStatic.cancel();
        frozenFrame = frame;
        frozenView.setImageBitmap(frame);
        setFrozenMix(1f);
    }

    private void setFrozenMix(float alpha) {
        frozenMix = alpha;
        frozenView.setAlpha(coverOpacity * alpha);
        if (onBlurFrameDue != null) onBlurFrameDue.run();
    }

    private void clearFrozenFrame() {
        main.removeCallbacks(artworkTimeout);
        fadeToStatic.cancel();
        if (frozenFrame == null) return;
        frozenView.setImageDrawable(null);
        frozenFrame = null;
        frozenMix = 1f;
        if (onBlurFrameDue != null) onBlurFrameDue.run();
    }

    private void invalidateCover() {
        if (surfaceHost.getParent() instanceof View parent) parent.postInvalidateOnAnimation();
        if (onBlurFrameDue != null) onBlurFrameDue.run();
    }

    int ambientDimColor() {
        return ambientArtworkIsLive ? 0x30000000 : 0x60000000;
    }

    void prepareAmbientClock(ViewGroup root, View cover) {
        ambientBounds.set(0, 0, cover.getWidth(), cover.getHeight());
        MediaClockContrast.apply(root, ambientFrame, ambientBounds, cover);
        try {
            MediaStatusBarContrast.applyAmbient(
                    root, ambientFrame, ambientBounds, ambientDimColor());
        } catch (RuntimeException error) {
            MediaStatusBarContrast.clear(root);
        }
    }

    boolean drawAmbient(Canvas canvas, ViewGroup root, View cover) {
        Bitmap frame = ambientFrame;
        if (!ambientVisible || frame == null || frame.isRecycled()) return false;
        ambientBounds.set(0, 0, cover.getWidth(), cover.getHeight());
        int dim = ambientDimColor();
        NativeArtworkReveal.bitmap(canvas, frame, ambientBounds, 1f, 1f, ambientPaint, frameCrop);
        canvas.drawColor(dim);
        if (root != null && canvas.isHardwareAccelerated() && !ambientBounds.isEmpty()) {
            if (ambientBlur == null) ambientBlur = new ArtworkBlurFilter.Layers(cover);
            if (blurredAmbientFrame != frame
                    || ambientBlur.needsRecording(cover.getWidth(), cover.getHeight())) {
                Canvas record = ambientBlur.beginRecording(cover.getWidth(), cover.getHeight());
                NativeArtworkReveal.bitmap(
                        record, frame, ambientBlur.sourceBounds(), 1f, 1f, ambientPaint, frameCrop);
                record.drawColor(dim);
                ambientBlur.endRecording();
                blurredAmbientFrame = frame;
                android.util.Log.d(DressroomModule.TAG, "Fullscreen media AOD frame drawn: "
                        + frame.getWidth() + "x" + frame.getHeight());
            }
            MediaClockContrast.artwork(
                    root, ambientBlur.blurred(), ambientBlur.revision(), ambientBounds, null, 1f);
        }
        return true;
    }

    void hideVideoSurface() {
        if (surfaceHost.getVisibility() != View.INVISIBLE) surfaceHost.setVisibility(View.INVISIBLE);
    }

    private static synchronized boolean resolveGpuLayerBridge() {
        if (!gpuLayerChecked) {
            gpuLayerChecked = true;
            try {
                Class<?> textureLayerClass = Class.forName("android.graphics.TextureLayer");
                Class<?> recordingCanvas = Class.forName("android.graphics.RecordingCanvas");
                Method getter = TextureView.class.getDeclaredMethod("getTextureLayer");
                getter.setAccessible(true);
                Method drawer =
                        recordingCanvas.getDeclaredMethod("drawTextureLayer", textureLayerClass);
                drawer.setAccessible(true);
                recordingCanvasClass = recordingCanvas;
                getTextureLayerMethod = getter;
                drawTextureLayerMethod = drawer;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                recordingCanvasClass = null;
                getTextureLayerMethod = null;
                drawTextureLayerMethod = null;
                DressroomModule.note("Live cover GPU blur bridge unavailable", unavailable);
            }
        }
        return getTextureLayerMethod != null
                && drawTextureLayerMethod != null
                && recordingCanvasClass != null;
    }

    private boolean canUseGpuBlurLayer() {
        return resolveGpuLayerBridge()
                && textureView.isAvailable()
                && textureView.getWidth() > 0
                && textureView.getHeight() > 0;
    }

    private boolean drawGpuVideoFrame(Canvas canvas, RectF drawBounds, float revealMix) {
        if (!canUseGpuBlurLayer()
                || drawBounds == null
                || drawBounds.isEmpty()
                || !recordingCanvasClass.isInstance(canvas)) {
            return false;
        }
        int viewW = textureView.getWidth();
        int viewH = textureView.getHeight();
        if (viewW <= 0 || viewH <= 0) return false;
        Object layer;
        try {
            layer = getTextureLayerMethod.invoke(textureView);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
        if (layer == null) return false;
        int save = canvas.save();
        try {
            if (revealMix < 0.995f) {
                int alpha = Math.max(0, Math.min(255, Math.round(revealMix * 255f)));
                if (alpha <= 0) return true;
                canvas.saveLayerAlpha(drawBounds, alpha);
            }
            canvas.translate(drawBounds.left, drawBounds.top);
            canvas.scale(drawBounds.width() / viewW, drawBounds.height() / viewH);
            drawTextureLayerMethod.invoke(canvas, layer);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        } finally {
            canvas.restoreToCount(save);
        }
    }

    boolean drawBlurFrames(
            android.graphics.Canvas canvas,
            ViewGroup root,
            android.graphics.RectF drawBounds,
            android.graphics.RectF sampleBounds,
            android.graphics.Paint paint) {
        if (closingLive && closingFrame != null) {
            NativeArtworkReveal.bitmap(canvas, closingFrame, drawBounds, 1f, 1f, paint, frameCrop);
            return true;
        }
        if (frozenFrame != null) {
            int alpha = paint.getAlpha();
            paint.setAlpha(Math.round(alpha * frozenMix));
            try {
                NativeArtworkReveal.bitmap(canvas, frozenFrame, drawBounds, 1f, 1f, paint, frameCrop);
            } finally {
                paint.setAlpha(alpha);
            }
        }
        if (!player.isVideoVisible()) return frozenFrame != null && frozenMix >= 0.995f;
        float revealMix = player.revealMix();
        if (drawGpuVideoFrame(canvas, drawBounds, revealMix)) {
            return true;
        }
        Bitmap sample = lastVideoFrame;
        if (sample == null || sample.isRecycled()) return false;
        int alpha = paint.getAlpha();
        paint.setAlpha(Math.round(alpha * revealMix));
        try {
            NativeArtworkReveal.bitmap(canvas, sample, drawBounds, 1f, 1f, paint, frameCrop);
            if (revealMix > .5f) {
                dispatchContrastSample(root, sample, sampleBounds);
            }
            return true;
        } finally {
            paint.setAlpha(alpha);
        }
    }

    private void dispatchContrastSample(ViewGroup root, Bitmap sample, RectF sampleBounds) {
        if (root == null
                || sample == null
                || sample.isRecycled()
                || sampleBounds == null
                || sampleBounds.isEmpty()) {
            return;
        }
        float scale =
                Math.max(
                        sampleBounds.width() / sample.getWidth(),
                        sampleBounds.height() / sample.getHeight());
        float width = sample.getWidth() * scale;
        float height = sample.getHeight() * scale;
        sampleCrop.set(
                sampleBounds.centerX() - width * .5f,
                sampleBounds.centerY() - height * .5f,
                sampleBounds.centerX() + width * .5f,
                sampleBounds.centerY() + height * .5f);
        MediaClockContrast.sampleVideo(root, sample, sampleCrop);
        MediaStatusBarContrast.sampleVideo(root, sample, sampleCrop);
    }

    private boolean shouldSampleContrastFrame(long nowMs) {
        return !contrastSampleRequested
                && (lastContrastSampleUptimeMs == 0L
                        || nowMs - lastContrastSampleUptimeMs >= CONTRAST_SAMPLE_INTERVAL_MS);
    }

    private void readContrastFrame() {
        if (closingLive
                || !player.isVideoVisible()
                || player.revealMix() <= 0.5f
                || !textureView.isAvailable()) {
            return;
        }
        int width = player.videoWidth(), height = player.videoHeight();
        if (width <= 0 || height <= 0) return;
        float scale = Math.min(1f, CONTRAST_SAMPLE_MAX_SIDE / (float) Math.max(width, height));
        int targetW = Math.max(1, Math.round(width * scale));
        int targetH = Math.max(1, Math.round(height * scale));
        if (contrastBuffer == null
                || contrastBuffer == contrastSample
                || contrastBuffer == frozenFrame
                || contrastBuffer == closingFrame
                || contrastBuffer == lastVideoFrame
                || contrastBuffer.isRecycled()
                || contrastBuffer.getWidth() != targetW
                || contrastBuffer.getHeight() != targetH) {
            contrastBuffer = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        }
        try {
            contrastBuffer.eraseColor(0);
            Bitmap frame = textureView.getBitmap(contrastBuffer);
            if (!hasPixels(frame)) return;
            contrastBuffer = contrastSample;
            contrastSample = frame;
            ViewGroup root = surfaceHost.getRootView() instanceof ViewGroup vg ? vg : null;
            if (root != null && layoutWidth > 0 && layoutHeight > 0) {
                contrastBounds.set(
                        layoutLeft,
                        layoutTop,
                        layoutLeft + layoutWidth,
                        layoutTop + layoutHeight);
                dispatchContrastSample(root, frame, contrastBounds);
            }
        } catch (RuntimeException unavailable) {
            // Keep the last good sample if the surface is being torn down.
        }
    }

    private void readVideoFrame() {
        if (closingLive || !player.isVideoVisible() || !textureView.isAvailable()) return;
        int width = player.videoWidth(), height = player.videoHeight();
        if (width <= 0 || height <= 0) return;
        float scale = Math.min(1f, BLUR_FRAME_MAX_SIDE / (float) Math.max(width, height));
        int targetW = Math.max(1, Math.round(width * scale));
        int targetH = Math.max(1, Math.round(height * scale));
        if (frameBuffer == null
                || frameBuffer == frozenFrame
                || frameBuffer == closingFrame
                || frameBuffer.isRecycled()
                || frameBuffer.getWidth() != targetW
                || frameBuffer.getHeight() != targetH) {
            frameBuffer = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        }
        try {
            frameBuffer.eraseColor(0);
            Bitmap frame = textureView.getBitmap(frameBuffer);
            if (!hasPixels(frame)) return;
            frameBuffer = lastVideoFrame;
            lastVideoFrame = frame;
        } catch (RuntimeException unavailable) {
            // Keep the last good frame if the surface is already being torn down.
        }
    }

    @Override
    public void onFirstFrameRevealed() {
        main.removeCallbacks(artworkTimeout);
        fadeToStatic.cancel();
        lastBlurSampleUptimeMs = 0L;
        lastContrastSampleUptimeMs = 0L;
        if (onBlurFrameDue != null) onBlurFrameDue.run();
    }

    @Override
    public void onVideoFrameUpdated() {
        if (onBlurFrameDue == null || !activeWanted || closingLive) return;
        if (player.revealMix() >= 1f) clearFrozenFrame();
        long now = SystemClock.uptimeMillis();
        if (canUseGpuBlurLayer()) {
            onBlurFrameDue.run();
            if (player.revealMix() > 0.5f && shouldSampleContrastFrame(now)) {
                lastContrastSampleUptimeMs = now;
                contrastSampleRequested = true;
                main.post(
                        () -> {
                            contrastSampleRequested = false;
                            if (activeWanted && !closingLive && !isDozing() && interactive()) {
                                readContrastFrame();
                            }
                        });
            }
            return;
        }
        if (shouldSampleBlurFrame(now, lastBlurSampleUptimeMs, false, blurSampleRequested)) {
            lastBlurSampleUptimeMs = now;
            blurSampleRequested = true;
            main.post(
                    () -> {
                        blurSampleRequested = false;
                        if (activeWanted && !closingLive && !isDozing() && interactive()) {
                            readVideoFrame();
                            onBlurFrameDue.run();
                        }
                    });
        } else if (player.revealMix() < 1f
                && lastVideoFrame != null
                && !lastVideoFrame.isRecycled()) {
            onBlurFrameDue.run();
        }
    }

    private void onActiveTrackChanged() {
        if (!attached || closingLive) return;
        int targetW = layoutWidth > 0 ? layoutWidth : MediaArtworkUncap.resolveTargetPx(context);
        AnimatedCoverProvider.TrackQuery query = LiveMediaTracker.latestTrack(targetW);
        if (isDozing() || !interactive()) {
            if (query != null && (ambientFrame != null || ambientDecodeAsset != null)
                    && wallpaperAodEnabled()) changeAmbientTrack(query);
            return;
        }
        if (refreshPowerRestriction()) return;
        int sourceMode = currentSourceMode();
        if (!activeWanted) {
            if (query != null && query.isValid() && RuntimeExtras.enabled(context, KEY)
                    && RuntimeExtras.enabled(context, "fullscreenArt"))
                requestCover(query, sourceMode);
            return;
        }
        if (query == null || !query.isValid()) {
            waitForTrack();
            return;
        }
        String nextKey = trackRequestKey(query, sourceMode);
        if (!nextKey.equals(activeRequestKey)) startTrack(query, sourceMode);
    }

    private void changeAmbientTrack(AnimatedCoverProvider.TrackQuery query) {
        int sourceMode = currentSourceMode();
        String key = trackRequestKey(query, sourceMode);
        if (key.equals(ambientTrackKey)) return;
        ambientTrackKey = key;
        ambientDecodeAsset = null;
        ambientAwaitingArtwork = true;
        clearFrozenFrame();
        closingFrame = null;
        lastVideoFrame = null;
        contrastSample = null;
        lastContrastSampleUptimeMs = 0L;
        activeRequestKey = "";
        activeAlbumKey = "";
        activeSourceMode = -1;
        activeAsset = null;
        resolving = false;
        ambientTrackSwitched = true;
        Bitmap artwork = LiveMediaTracker.artworkFor(query);
        boolean animated = RuntimeExtras.enabled(context, KEY);
        if (artwork != null && (!animated || (artwork.getWidth() >= 600 && artwork.getHeight() >= 600))) {
            ambientFrame = artwork;
            wakeAmbientArt = artwork;
            ambientArtworkIsLive = false;
        }
        invalidateCover();
        if (!animated) return;
        requestCover(query, sourceMode).whenComplete((asset, error) -> main.post(() -> {
            if (!attached || !key.equals(ambientTrackKey) || (interactive() && !isDozing())) return;
            if (asset == null) {
                if (ambientAwaitingArtwork && artwork != null) {
                    ambientFrame = artwork;
                    wakeAmbientArt = artwork;
                    ambientArtworkIsLive = false;
                    invalidateCover();
                }
                return;
            }
            ambientDecodeAsset = asset;
            player.readOpeningFrame(asset, frame -> {
                if (attached && key.equals(ambientTrackKey) && ambientDecodeAsset == asset)
                    showAmbientVideoFrame(frame);
            });
        }));
    }

    private void waitForTrack() {
        if (activeRequestKey.isEmpty()) return;
        Bitmap outgoing = captureFrame(SNAPSHOT_MAX_SIDE);
        if (outgoing != null) showFrozenFrame(outgoing);
        lastVideoFrame = null;
        contrastSample = null;
        lastContrastSampleUptimeMs = 0L;
        activeRequestKey = "";
        activeAlbumKey = "";
        activeSourceMode = -1;
        activeAsset = null;
        resolving = false;
        player.pauseAndHide();
        main.removeCallbacks(artworkTimeout);
        main.postDelayed(artworkTimeout, 1800L);
    }

    private void startTrack(AnimatedCoverProvider.TrackQuery query, int sourceMode) {
        String albumKey = query.normalizedAlbumKey();
        String requestKey = trackRequestKey(query, sourceMode);
        if (sourceMode == activeSourceMode
                && activeAsset != null
                && activeAsset.isPlayableLocalFile()
                && canReuseAlbumAsset(
                        sourceMode, activeAsset.providerId(), activeAlbumKey, albumKey)) {
            // Every track on this album shares the cached motion cover. Keep its
            // decoder and surface running rather than freezing and preparing it again.
            activeRequestKey = requestKey;
            return;
        }
        Bitmap outgoing = captureFrame(SNAPSHOT_MAX_SIDE);
        if (outgoing != null) showFrozenFrame(outgoing);
        lastVideoFrame = null;
        contrastSample = null;
        lastContrastSampleUptimeMs = 0L;
        player.pauseAndHide();
        main.removeCallbacks(artworkTimeout);
        fadeToStatic.cancel();
        activeSourceMode = sourceMode;
        activeRequestKey = requestKey;
        activeAlbumKey = albumKey;
        activeAsset = null;
        resolving = true;
        // A cold network lookup must not leave the previous song's cover up
        // for its entire download. Cached video can still replace it before this fade.
        main.postDelayed(artworkTimeout, 450L);
        resolveAsync(query, sourceMode, requestKey);
    }

    private void playResolvedCover() {
        player.play(activeAsset.cacheKey(), activeAsset.videoFile(), coverOpacity);
        if (frozenFrame != null && !player.isVideoVisible()) {
            main.removeCallbacks(artworkTimeout);
            main.postDelayed(artworkTimeout, 5000L);
        }
    }

    private void revealStaticArtwork() {
        if (frozenFrame == null || fadeToStatic.isStarted()) return;
        if (isDozing() || refreshPowerRestriction()) return;
        fadeToStatic.setFloatValues(frozenMix, 0f);
        fadeToStatic.start();
    }

    private void resolveAsync(
            AnimatedCoverProvider.TrackQuery query, int sourceMode, String requestKey) {
        requestCover(query, sourceMode).whenComplete(
                (asset, err) -> main.post(() -> {
                    if (!attached || !requestKey.equals(activeRequestKey)) return;
                    resolving = false;
                    if (asset != null && asset.isPlayableLocalFile()) {
                        activeAsset = asset;
                        if (activeWanted && !isDozing() && !refreshPowerRestriction())
                            playResolvedCover();
                    } else if (activeWanted) revealStaticArtwork();
                }));
    }

    private int currentSourceMode() {
        return clampSource(RuntimeExtras.get(context).getInt(SOURCE, SOURCE_SPOTIFY_FIRST));
    }

    static int clampSource(int source) {
        return source >= SOURCE_SPOTIFY_FIRST && source <= SOURCE_APPLE_ONLY
                ? source
                : SOURCE_SPOTIFY_FIRST;
    }

    static List<AnimatedCoverProvider> providersForSource(int source) {
        return switch (clampSource(source)) {
            case SOURCE_APPLE_FIRST -> List.of(APPLE_PROVIDER, SPOTIFY_PROVIDER);
            case SOURCE_SPOTIFY_ONLY -> List.of(SPOTIFY_PROVIDER);
            case SOURCE_APPLE_ONLY -> List.of(APPLE_PROVIDER);
            default -> List.of(SPOTIFY_PROVIDER, APPLE_PROVIDER);
        };
    }

    static boolean canReuseAlbumAsset(
            int sourceMode, String activeProviderId, String activeAlbumKey, String nextAlbumKey) {
        if (activeAlbumKey == null
                || activeAlbumKey.isEmpty()
                || nextAlbumKey == null
                || !nextAlbumKey.equals(activeAlbumKey)) {
            return false;
        }
        int mode = clampSource(sourceMode);
        if (mode == SOURCE_APPLE_ONLY) return true;
        return mode == SOURCE_APPLE_FIRST
                && AppleMusicAnimatedCoverProvider.PROVIDER_ID.equals(activeProviderId);
    }

    static String trackRequestKey(AnimatedCoverProvider.TrackQuery query, int sourceMode) {
        return clampSource(sourceMode) + "|" + query.playbackKey();
    }

    static String requestFlightKey(
            AnimatedCoverProvider.TrackQuery query, int sourceMode, boolean allowNet) {
        int mode = clampSource(sourceMode);
        if (mode == SOURCE_APPLE_ONLY) {
            String albumKey = query.normalizedAlbumKey();
            String key = albumKey.isEmpty() ? query.playbackKey() : albumKey;
            return mode + "|" + key + "|" + allowNet;
        }
        return mode + "|" + query.playbackKey() + "|" + allowNet;
    }

    private CompletableFuture<AnimatedCoverProvider.AnimatedCoverAsset> requestCover(
            AnimatedCoverProvider.TrackQuery query, int sourceMode) {
        AnimatedCoverCache cache = obtainCache(context);
        boolean wifiOnly = RuntimeExtras.get(context).getBoolean(WIFI_ONLY, true);
        boolean allowNet = !wifiOnly || isUnmeteredNetwork(context);
        String flightKey = requestFlightKey(query, sourceMode, allowNet);

        return requests.request(
                flightKey, () -> queryProviders(query, sourceMode, allowNet, cache));
    }

    private static AnimatedCoverProvider.AnimatedCoverAsset queryProviders(
            AnimatedCoverProvider.TrackQuery query,
            int sourceMode,
            boolean allowNet,
            AnimatedCoverCache cache) {
        for (AnimatedCoverProvider provider : providersForSource(sourceMode)) {
            AnimatedCoverProvider.AnimatedCoverAsset asset = provider.resolve(query, allowNet, cache);
            if (asset != null && asset.isPlayableLocalFile()) return asset;
        }
        return null;
    }

    private float lyricsBlurAmount;
    private final RenderEffect[] lyricsBlurEffects = new RenderEffect[17];
    private int lyricsBlurWidth, lyricsBlurHeight, appliedLyricsBlurBucket = -1;

    void setLyricsBlurAmount(float amount) {
        if (lyricsBlurAmount == amount) return;
        lyricsBlurAmount = amount;
        updateRealtimeBlur();
    }

    private void updateRealtimeBlur() {
        if (Build.VERSION.SDK_INT < 31) return;
        if (surfaceHost == null) return;
        int bucket = Math.max(0, Math.min(16, Math.round(lyricsBlurAmount * 16f)));
        if (bucket > 0) {
            int w = surfaceHost.getWidth() > 0 ? surfaceHost.getWidth() : layoutWidth;
            int h = surfaceHost.getHeight() > 0 ? surfaceHost.getHeight() : layoutHeight;
            if (w <= 0 || h <= 0) {
                var dm = surfaceHost.getResources().getDisplayMetrics();
                w = dm.widthPixels;
                h = dm.heightPixels;
            }
            if (lyricsBlurWidth != w || lyricsBlurHeight != h) {
                java.util.Arrays.fill(lyricsBlurEffects, null);
                lyricsBlurWidth = w;
                lyricsBlurHeight = h;
                appliedLyricsBlurBucket = -1;
            }
            if (appliedLyricsBlurBucket == bucket) return;
            if (lyricsBlurEffects[bucket] == null)
                lyricsBlurEffects[bucket] = createRealtimeBlur(120f * bucket / 16f, w, h);
            surfaceHost.setRenderEffect(lyricsBlurEffects[bucket]);
        } else if (appliedLyricsBlurBucket != 0) {
            surfaceHost.setRenderEffect(null);
        }
        appliedLyricsBlurBucket = bucket;
    }

    private static RenderEffect createRealtimeBlur(float radius, int width, int height) {
        if (Build.VERSION.SDK_INT < 31) return null;
        try {
            Class<?> type = Class.forName("com.samsung.android.graphics.RenderEffectImageFilter");
            Object samsung = type.getConstructor().newInstance();
            Method setRadius = type.getMethod("setBlurRadius", float.class);
            setRadius.invoke(samsung, radius);
            Method build = type.getMethod("build", int.class, int.class);
            RenderEffect effect = (RenderEffect) build.invoke(samsung, Math.max(1, width), Math.max(1, height));
            if (effect != null) return effect;
        } catch (Throwable ignored) {}
        return RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
    }

    private void applyGeometry(int l, int t, int w, int h, float cornerRadius) {
        boolean radiusChanged = Math.abs(outlineCornerRadius - cornerRadius) > 0.5f;
        if (radiusChanged) {
            outlineCornerRadius = cornerRadius;
            surfaceHost.invalidateOutline();
        }
        if (layoutLeft == l && layoutTop == t && layoutWidth == w && layoutHeight == h) {
            return;
        }
        layoutLeft = l;
        layoutTop = t;
        layoutWidth = w;
        layoutHeight = h;

        ViewGroup.LayoutParams currentLp = surfaceHost.getLayoutParams();
        FrameLayout.LayoutParams lp =
                currentLp instanceof FrameLayout.LayoutParams flp
                        ? flp
                        : new FrameLayout.LayoutParams(w, h);
        lp.width = w;
        lp.height = h;
        lp.leftMargin = l;
        lp.topMargin = t;
        surfaceHost.setLayoutParams(lp);
        if (surfaceHost.getParent() instanceof View parent && parent.getWidth() > 0) {
            surfaceHost.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            surfaceHost.layout(l, t, l + w, t + h);
        }
        if (lyricsBlurAmount > 0f) {
            updateRealtimeBlur();
        }
    }

    private boolean refreshPowerRestriction() {
        boolean restricted = false;
        try {
            if (power != null && (!power.isInteractive() || power.isPowerSaveMode())) {
                restricted = true;
            }
        } catch (RuntimeException ignored) {
        }
        if (!restricted && !isDozing()) pausedForPower = false;
        return restricted;
    }

    private static boolean isUnmeteredNetwork(Context context) {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static AnimatedCoverCache obtainCache(Context context) {
        AnimatedCoverCache existing = sharedCache;
        if (existing != null) return existing;
        synchronized (LiveCoverController.class) {
            if (sharedCache == null) {
                File dir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR_NAME);
                sharedCache = new AnimatedCoverCache(dir);
            }
            return sharedCache;
        }
    }
}
