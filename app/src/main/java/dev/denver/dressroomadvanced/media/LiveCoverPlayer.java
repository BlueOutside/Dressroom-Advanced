package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Isolated {@link MediaPlayer} + {@link TextureView} controller that executes all binder/codec setup
 * on a dedicated daemon {@link HandlerThread} and keeps {@code TextureView} hidden at
 * {@code alpha = 0f} until the first real decoded video frame reaches the surface.
 */
final class LiveCoverPlayer implements TextureView.SurfaceTextureListener {
    private static final long REVEAL_DURATION_MS = 260L;

    interface FrameListener {
        void onFirstFrameRevealed();

        void onVideoFrameUpdated();
    }

    private static HandlerThread sharedThread;
    private static Handler sharedWorker;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Matrix transformMatrix = new Matrix();
    private final AtomicLong generation = new AtomicLong();

    private TextureView textureView;
    private SurfaceTexture surfaceTexture;
    private FrameListener frameListener;

    private String currentKey = "";
    private File currentFile;
    private volatile boolean desiredPlaying;
    private boolean prepared;
    private boolean firstFrameRendered;
    private boolean holdingPausedFrame;
    private boolean revealHeld;
    private long hiddenFrameTimestamp;
    private float targetOpacity = 1f;
    private float revealMix;
    private final ValueAnimator reveal = ValueAnimator.ofFloat(0f, 1f);
    private volatile int resumePositionMs;
    private int viewWidth;
    private int viewHeight;
    private int videoWidth;
    private int videoHeight;

    // Owned exclusively by workerHandler thread.
    private MediaPlayer workerPlayer;
    private Surface workerSurface;
    private boolean workerPrepared;
    private boolean workerPlaying;

    LiveCoverPlayer(FrameListener frameListener) {
        this.frameListener = frameListener;
        reveal.setDuration(REVEAL_DURATION_MS);
        reveal.addUpdateListener(animation -> {
            revealMix = animation.getAnimatedFraction();
            applyOpacity();
            if (this.frameListener != null) this.frameListener.onVideoFrameUpdated();
        });
    }

    private static synchronized Handler worker() {
        if (sharedWorker == null) {
            sharedThread = new HandlerThread("DressroomLiveCover");
            sharedThread.setDaemon(true);
            sharedThread.start();
            sharedWorker = new Handler(sharedThread.getLooper());
        }
        return sharedWorker;
    }

    void bind(TextureView view) {
        if (textureView == view) return;
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
        }
        textureView = view;
        if (view != null) {
            view.setOpaque(false);
            view.setAlpha(0f);
            view.setSurfaceTextureListener(this);
            if (view.isAvailable() && view.getSurfaceTexture() != null) {
                onSurfaceTextureAvailable(
                        view.getSurfaceTexture(), view.getWidth(), view.getHeight());
            }
        }
    }

    boolean isVideoVisible() {
        return ((desiredPlaying && firstFrameRendered) || holdingPausedFrame)
                && textureView != null
                && textureView.getAlpha() > 0.01f;
    }

    boolean isHoldingPausedFrame() {
        return holdingPausedFrame;
    }

    float revealMix() {
        if (holdingPausedFrame) return 1f;
        return desiredPlaying && firstFrameRendered ? revealMix : 0f;
    }

    int videoWidth() {
        return videoWidth;
    }

    int videoHeight() {
        return videoHeight;
    }

    void play(String assetKey, File mp4File, float opacity) {
        if (assetKey == null || assetKey.isEmpty() || mp4File == null || !mp4File.isFile()) {
            pauseAndHide();
            return;
        }
        targetOpacity = Math.max(0f, Math.min(1f, opacity));
        boolean sameAsset = assetKey.equals(currentKey) && mp4File.equals(currentFile);
        if (sameAsset && desiredPlaying) {
            applyOpacity();
            return;
        }

        desiredPlaying = true;
        if (sameAsset && prepared) {
            if (holdingPausedFrame) {
                holdingPausedFrame = false;
                firstFrameRendered = true;
                revealMix = 1f;
                applyOpacity();
            }
            long seq = generation.get();
            SurfaceTexture st = surfaceTexture;
            worker().post(() -> resumeOnWorker(seq, st));
            return;
        }

        if (!sameAsset) resumePositionMs = 0;
        currentKey = assetKey;
        currentFile = mp4File;
        prepared = false;
        firstFrameRendered = false;
        holdingPausedFrame = false;
        videoWidth = 0;
        videoHeight = 0;
        hideTextureImmediately();

        long seq = generation.incrementAndGet();
        SurfaceTexture st = surfaceTexture;
        if (st != null) {
            worker().post(() -> prepareOnWorker(seq, mp4File, st));
        }
    }

    void updateOpacity(float opacity) {
        targetOpacity = Math.max(0f, Math.min(1f, opacity));
        applyOpacity();
    }

    void holdReveal(boolean held) {
        if (revealHeld == held) return;
        revealHeld = held;
        if (!held && desiredPlaying && firstFrameRendered && revealMix == 0f && !reveal.isStarted())
            reveal.start();
    }

    void pauseAndHide() {
        boolean wasHolding = holdingPausedFrame;
        if (!desiredPlaying && !wasHolding) return;
        desiredPlaying = false;
        firstFrameRendered = false;
        holdingPausedFrame = false;
        hideTextureImmediately();
        if (!wasHolding) {
            long seq = generation.get();
            worker().post(() -> pauseOnWorker(seq));
        }
    }

    /**
     * Pauses decoding on the worker thread while leaving the last rendered GPU video frame visible
     * on {@link TextureView}, preventing both main-thread {@code getBitmap()} stalls and flashing
     * static artwork while the off-thread AOD still decodes.
     */
    void pauseKeepingFrame() {
        if (!desiredPlaying) return;
        boolean visible = firstFrameRendered && textureView != null && textureView.getAlpha() > 0.01f;
        desiredPlaying = false;
        firstFrameRendered = false;
        if (visible) {
            reveal.cancel();
            revealMix = 1f;
            holdingPausedFrame = true;
            if (textureView.getAlpha() != targetOpacity) textureView.setAlpha(targetOpacity);
            hiddenFrameTimestamp =
                    surfaceTexture == null ? Long.MIN_VALUE : surfaceTexture.getTimestamp();
        } else {
            holdingPausedFrame = false;
            hideTextureImmediately();
        }
        long seq = generation.get();
        worker().post(() -> pauseOnWorker(seq));
    }

    void hidePausedFrame() {
        if (!holdingPausedFrame) return;
        holdingPausedFrame = false;
        hideTextureImmediately();
    }

    void readPausedFrame(File file, Consumer<Bitmap> ready) {
        readStill(file, videoWidth, videoHeight, () -> resumePositionMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST, ready);
    }

    void readOpeningFrame(AnimatedCoverProvider.AnimatedCoverAsset asset, Consumer<Bitmap> ready) {
        readStill(asset.videoFile(), asset.videoWidth(), asset.videoHeight(), () -> 0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, ready);
    }

    private void readStill(File file, int width, int height, LongSupplier position,
            int option, Consumer<Bitmap> ready) {
        worker().post(() -> {
            Bitmap frame = null;
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(file.getAbsolutePath());
                float scale = Math.min(1f, 1440f / Math.max(width, height));
                frame = width > 0 && height > 0
                        ? retriever.getScaledFrameAtTime(position.getAsLong(), option,
                                Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)))
                        : retriever.getFrameAtTime(position.getAsLong(), option);
            } catch (IOException | RuntimeException error) {
                DressroomModule.note("Live artwork doze frame unavailable", error);
            }
            Bitmap result = frame;
            main.post(() -> ready.accept(result));
        });
    }

    void resetMedia() {
        desiredPlaying = false;
        prepared = false;
        firstFrameRendered = false;
        currentKey = "";
        currentFile = null;
        resumePositionMs = 0;
        videoWidth = 0;
        videoHeight = 0;
        hideTextureImmediately();
        generation.incrementAndGet();
        worker().post(this::releaseOnWorker);
    }

    void release() {
        resetMedia();
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView = null;
        }
        surfaceTexture = null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        surfaceTexture = st;
        viewWidth = width;
        viewHeight = height;
        applyCenterCropTransform();
        if (desiredPlaying && currentFile != null && currentFile.isFile()) {
            long seq = generation.incrementAndGet();
            firstFrameRendered = false;
            prepared = false;
            hideTextureImmediately();
            File file = currentFile;
            worker().post(() -> prepareOnWorker(seq, file, st));
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        applyCenterCropTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (surfaceTexture == st) surfaceTexture = null;
        desiredPlaying = false;
        prepared = false;
        firstFrameRendered = false;
        hideTextureImmediately();
        generation.incrementAndGet();
        worker().post(this::releaseOnWorker);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
        if (st != surfaceTexture || !desiredPlaying) return;
        if (!firstFrameRendered && prepared && videoWidth > 0 && videoHeight > 0
                && st.getTimestamp() != hiddenFrameTimestamp) {
            markFirstFrameAndReveal(generation.get());
            return;
        }
        if (firstFrameRendered && frameListener != null) {
            frameListener.onVideoFrameUpdated();
        }
    }

    private void hideTextureImmediately() {
        reveal.cancel();
        revealMix = 0f;
        holdingPausedFrame = false;
        hiddenFrameTimestamp = surfaceTexture == null ? Long.MIN_VALUE : surfaceTexture.getTimestamp();
        if (textureView != null) {
            if (textureView.getAlpha() != 0f) textureView.setAlpha(0f);
        }
    }

    private void applyOpacity() {
        if (textureView == null) return;
        if (holdingPausedFrame) {
            textureView.setAlpha(targetOpacity);
        } else if (desiredPlaying && firstFrameRendered) {
            textureView.setAlpha(targetOpacity * revealMix);
        }
    }

    private void markFirstFrameAndReveal(long seq) {
        if (seq != generation.get() || !desiredPlaying) return;
        boolean newlyRevealed = !firstFrameRendered;
        firstFrameRendered = true;
        if (newlyRevealed) {
            applyCenterCropTransform();
            if (!revealHeld) reveal.start();
            android.util.Log.i(
                    DressroomModule.TAG,
                    "Live cover video frame visible ("
                            + videoWidth
                            + "x"
                            + videoHeight
                            + ") for "
                            + currentKey);
            if (frameListener != null) frameListener.onFirstFrameRevealed();
        }
    }

    private void applyCenterCropTransform() {
        if (textureView == null || videoWidth <= 0 || videoHeight <= 0) return;
        int w = viewWidth > 0 ? viewWidth : textureView.getWidth();
        int h = viewHeight > 0 ? viewHeight : textureView.getHeight();
        if (w <= 0 || h <= 0) return;
        float[] t = AppleHlsResolver.computeCenterCropScale(videoWidth, videoHeight, w, h);
        transformMatrix.reset();
        transformMatrix.setScale(t[0], t[1], t[2], t[3]);
        textureView.setTransform(transformMatrix);
    }

    private void prepareOnWorker(long seq, File file, SurfaceTexture st) {
        if (seq != generation.get() || st == null) return;
        releaseOnWorker();
        if (!file.isFile()) return;

        MediaPlayer mp = new MediaPlayer();
        Surface surface;
        try {
            surface = new Surface(st);
        } catch (RuntimeException error) {
            mp.release();
            return;
        }
        workerPlayer = mp;
        workerSurface = surface;
        workerPrepared = false;
        workerPlaying = false;

        try {
            mp.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build());
            mp.setSurface(surface);
            mp.setDataSource(file.getAbsolutePath());
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            // TextureView applies the crop. Keep the producer buffer in video proportions.
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            mp.setOnVideoSizeChangedListener(
                    (player, w, h) ->
                            main.post(
                                    () -> {
                                        if (seq != generation.get()) return;
                                        videoWidth = w;
                                        videoHeight = h;
                                        applyCenterCropTransform();
                                    }));
            mp.setOnPreparedListener(
                    player -> {
                        if (seq != generation.get() || workerPlayer != player) return;
                        workerPrepared = true;
                        int position = resumePositionMs;
                        if (position > 0) {
                            try {
                                player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                            } catch (IllegalStateException error) {
                                DressroomModule.note("Live artwork resume position unavailable", error);
                            }
                        }
                        main.post(
                                () -> {
                                    if (seq != generation.get()) return;
                                    prepared = true;
                                });
                        if (desiredPlaying) {
                            try {
                                player.start();
                                workerPlaying = true;
                            } catch (IllegalStateException ignored) {
                            }
                        }
                    });
            mp.setOnErrorListener(
                    (player, what, extra) -> {
                        android.util.Log.w(
                                DressroomModule.TAG,
                                "Live cover MediaPlayer error what=" + what + " extra=" + extra);
                        releaseOnWorker();
                        main.post(
                                () -> {
                                    if (seq != generation.get()) return;
                                    prepared = false;
                                    firstFrameRendered = false;
                                    hideTextureImmediately();
                                });
                        return true;
                    });
            mp.prepareAsync();
        } catch (IOException | RuntimeException error) {
            releaseOnWorker();
            DressroomModule.note("Live cover media prepare failed", error);
        }
    }

    private void resumeOnWorker(long seq, SurfaceTexture st) {
        if (seq != generation.get() || workerPlayer == null || !workerPrepared) return;
        try {
            if ((workerSurface == null || !workerSurface.isValid()) && st != null) {
                if (workerSurface != null) workerSurface.release();
                workerSurface = new Surface(st);
                workerPlayer.setSurface(workerSurface);
            }
            if (!workerPlaying) {
                workerPlayer.start();
                workerPlaying = true;
            }
        } catch (IllegalStateException | IllegalArgumentException error) {
            releaseOnWorker();
        }
    }

    private void pauseOnWorker(long seq) {
        if (seq != generation.get() || workerPlayer == null || !workerPrepared) return;
        try {
            if (workerPlaying) {
                if (workerPlayer.isPlaying()) workerPlayer.pause();
                int pos = workerPlayer.getCurrentPosition();
                int duration = workerPlayer.getDuration();
                if (duration > 0 && pos >= duration) pos = pos % duration;
                if (pos >= 0) resumePositionMs = pos;
            }
            workerPlaying = false;
        } catch (IllegalStateException ignored) {
            workerPlaying = false;
        }
    }

    private void releaseOnWorker() {
        workerPrepared = false;
        workerPlaying = false;
        if (workerPlayer != null) {
            try {
                workerPlayer.reset();
            } catch (RuntimeException ignored) {
            }
            try {
                workerPlayer.release();
            } catch (RuntimeException ignored) {
            }
            workerPlayer = null;
        }
        if (workerSurface != null) {
            try {
                workerSurface.release();
            } catch (RuntimeException ignored) {
            }
            workerSurface = null;
        }
    }
}
