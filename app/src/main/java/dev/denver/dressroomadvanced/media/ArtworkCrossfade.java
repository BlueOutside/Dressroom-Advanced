package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Used when the native song animation has no usable reveal mask. */
final class ArtworkCrossfade {
    private final Runnable redraw;
    private final RectF crop = new RectF();
    private Bitmap artwork, outgoing;
    private final ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
    private final NativeArtworkReveal mask;

    ArtworkCrossfade(View owner, Runnable redraw) {
        this.redraw = redraw;
        mask = new NativeArtworkReveal(owner, null, null, null);
        fade.setDuration(320);
        fade.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            mask.fallbackProgress(progress);
            if (progress >= 1f) outgoing = null;
            this.redraw.run();
        });
    }

    void update(Bitmap next, boolean visible) {
        if (next == artwork) return;
        fade.cancel();
        outgoing = visible ? artwork : null;
        artwork = next;
        if (running() && ValueAnimator.areAnimatorsEnabled()) {
            mask.begin(outgoing, next);
            fade.start();
        } else {
            outgoing = null;
            mask.progress(1f);
        }
    }

    boolean running() {
        return outgoing != null && !outgoing.isRecycled();
    }

    Bitmap target() {
        return artwork;
    }

    float fraction() {
        return running() ? fade.getAnimatedFraction() : 1f;
    }

    /** Stops any in-flight fallback crossfade and settles on {@code next} without starting a new fade. */
    void cancelTo(Bitmap next) {
        fade.cancel();
        outgoing = null;
        artwork = next;
        mask.progress(1f);
    }

    void draw(Canvas canvas, Bitmap next, RectF destination, Paint paint) {
        draw(canvas, next, destination, paint, true);
    }

    /** Draws the crossfade without invoking the shared hardware reveal node (safe for offscreen blur passes). */
    void drawSimple(Canvas canvas, Bitmap next, RectF destination, Paint paint) {
        draw(canvas, next, destination, paint, false);
    }

    private void draw(Canvas canvas, Bitmap next, RectF destination, Paint paint, boolean allowMask) {
        if (!running()) {
            NativeArtworkReveal.bitmap(canvas, next, destination, 1f, 1f, paint, crop);
            return;
        }
        float fraction = fade.getAnimatedFraction();
        int alpha = paint.getAlpha();
        // Composite the blend first so a partially visible player keeps a constant opacity.
        int layer = alpha == 255 ? canvas.save()
                : canvas.saveLayerAlpha(destination, alpha);
        try {
            paint.setAlpha(255);
            if (allowMask && mask.hasActiveMask(next) && canvas.isHardwareAccelerated()) {
                mask.draw(canvas, next, destination, paint);
                return;
            }
            NativeArtworkReveal.bitmap(canvas, outgoing, destination, 1f, 1f, paint, crop);
            paint.setAlpha(Math.round(fraction * 255));
            float scale = 1f + (1f - fraction) * .035f;
            NativeArtworkReveal.bitmap(canvas, next, destination, scale, scale, paint, crop);
        } finally {
            paint.setAlpha(alpha);
            canvas.restoreToCount(layer);
        }
    }

    void clear() {
        fade.cancel();
        artwork = outgoing = null;
        mask.progress(1f);
        mask.discard();
    }
}
