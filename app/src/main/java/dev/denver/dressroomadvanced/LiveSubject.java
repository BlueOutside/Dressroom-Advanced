package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

final class LiveSubject {
    private static final ClassValue<java.lang.reflect.Method> innerGetter =
            new ClassValue<>() {
                @Override
                protected java.lang.reflect.Method computeValue(Class<?> type) {
                    for (Class<?> owner = type; owner != null; owner = owner.getSuperclass())
                        try {
                            var method = owner.getDeclaredMethod("getDrawableInner");
                            method.setAccessible(true);
                            return method;
                        } catch (NoSuchMethodException ignored) {
                        }
                    throw new IllegalArgumentException(
                            "Wallpaper drawable accessor unavailable: " + type.getName());
                }
            };

    private static Drawable inner(View view) throws Exception {
        return (Drawable) innerGetter.get(view.getClass()).invoke(view);
    }

    final WeakReference<View> image;
    private final Drawable drawable;
    private final Object source;
    private final Matrix capturedToView;
    private final int capturedWidth, capturedHeight;
    final int width, height;
    final String fingerprint;
    byte[] png, baked;
    private Matrix lastGlobal;
    private android.view.ViewTreeObserver observer;
    private android.view.ViewTreeObserver.OnPreDrawListener listener;

    private record Stamp(Drawable drawable, Bitmap bitmap, int generation) {}

    private LiveSubject(View image, Drawable drawable, int width, int height, String fingerprint)
            throws Exception {
        this.image = new WeakReference<>(image);
        this.drawable = drawable;
        this.width = width;
        this.height = height;
        this.fingerprint = fingerprint;
        source = stamp(image);
        capturedWidth = image.getWidth();
        capturedHeight = image.getHeight();
        capturedToView =
                compose(
                        width,
                        height,
                        new RectF(drawable.getBounds()),
                        ((android.widget.ImageView) image).getImageMatrix(),
                        new Matrix());
    }

    static Object stamp(View image) throws Exception {
        Drawable inner = inner(image);
        Drawable plain = ((android.widget.ImageView) image).getDrawable();
        Bitmap bitmap = plain instanceof BitmapDrawable b ? b.getBitmap() : null;
        return new Stamp(inner, bitmap, bitmap == null ? 0 : bitmap.getGenerationId());
    }

    static record Capture(LiveSubject subject, Bitmap bitmap) {}

    static record Detail(Bitmap bitmap, Matrix toSource) {}

    Detail detail(Bitmap full) {
        float ratio = Math.min(1f, Math.min(1440f / capturedWidth, 4096f / capturedHeight));
        int width = Math.max(1, Math.round(capturedWidth * ratio)),
                height = Math.max(1, Math.round(capturedHeight * ratio));
        Matrix scale = new Matrix();
        scale.setScale((float) width / capturedWidth, (float) height / capturedHeight);
        Matrix toDetail = new Matrix();
        toDetail.setConcat(scale, capturedToView);
        Matrix inverse = new Matrix();
        if (!toDetail.invert(inverse)) throw new IllegalStateException("Invalid detail crop");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap)
                .drawBitmap(
                        full,
                        toDetail,
                        new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return new Detail(bitmap, inverse);
    }

    static Capture capture(View image) throws Exception {
        Drawable drawable = ready(image);
        int sourceWidth = Math.max(1, drawable.getIntrinsicWidth()),
                sourceHeight = Math.max(1, drawable.getIntrinsicHeight());
        Drawable plain = ((android.widget.ImageView) image).getDrawable();
        if (plain instanceof BitmapDrawable bitmap) {
            sourceWidth = bitmap.getBitmap().getWidth();
            sourceHeight = bitmap.getBitmap().getHeight();
        }
        if (sourceWidth <= 1 || sourceHeight <= 1) {
            sourceWidth = drawable.getBounds().width();
            sourceHeight = drawable.getBounds().height();
        }
        double ratio =
                Math.min(
                        1,
                        Math.min(
                                4096d / Math.max(sourceWidth, sourceHeight),
                                Math.sqrt(4_500_000d / ((double) sourceWidth * sourceHeight))));
        int width = Math.max(1, (int) Math.round(sourceWidth * ratio)),
                height = Math.max(1, (int) Math.round(sourceHeight * ratio));
        String fingerprint = fingerprint(image);
        Bitmap owned = render(drawable, width, height);
        return new Capture(new LiveSubject(image, drawable, width, height, fingerprint), owned);
    }

    static LiveSubject bind(View image, byte[] png, String fingerprint, byte[] baked)
            throws Exception {
        android.graphics.BitmapFactory.Options options =
                new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length, options);
        if (options.outWidth <= 0
                || options.outHeight <= 0
                || (long) options.outWidth * options.outHeight > 17_000_000)
            throw new IllegalArgumentException("Invalid source matte");
        LiveSubject subject =
                new LiveSubject(
                        image, ready(image), options.outWidth, options.outHeight, fingerprint);
        subject.png = png;
        subject.baked = baked;
        return subject;
    }

    private static Drawable ready(View image) throws Exception {
        if (image.getWidth() <= 0 || image.getHeight() <= 0)
            throw new IllegalStateException("Wallpaper preview has not been measured");
        Drawable drawable = inner(image);
        if (drawable == null || drawable.getBounds().isEmpty())
            throw new IllegalStateException("Wallpaper image is not ready");
        return drawable;
    }

    static String fingerprint(View image) throws Exception {
        Bitmap sample;
        Bitmap original = null;
        try {
            Object drawable = image.getClass().getMethod("getOriginalDrawable").invoke(image);
            if (drawable instanceof BitmapDrawable bitmap) original = bitmap.getBitmap();
        } catch (NoSuchMethodException ignored) {
        }
        if (original != null && !original.isRecycled()) {
            sample = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            new Canvas(sample)
                    .drawBitmap(
                            original,
                            null,
                            new RectF(0, 0, 64, 64),
                            new Paint(Paint.FILTER_BITMAP_FLAG));
        } else sample = render(ready(image), 64, 64);
        try {
            int[] pixels = new int[4096];
            sample.getPixels(pixels, 0, 64, 0, 0, 64, 64);
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(pixels.length * 4);
            bytes.asIntBuffer().put(pixels);
            return DepthStorage.hash(bytes.array());
        } finally {
            sample.recycle();
        }
    }

    private static Bitmap render(Drawable drawable, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Matrix matrix = new Matrix();
            matrix.setRectToRect(
                    new RectF(drawable.getBounds()),
                    new RectF(0, 0, width, height),
                    Matrix.ScaleToFit.FILL);
            Canvas canvas = new Canvas(output);
            canvas.concat(matrix);
            drawable.draw(canvas);
            return output;
        } catch (RuntimeException error) {
            output.recycle();
            throw error;
        }
    }

    static Matrix compose(int width, int height, RectF bounds, Matrix crop, Matrix global) {
        Matrix source = new Matrix();
        source.setRectToRect(new RectF(0, 0, width, height), bounds, Matrix.ScaleToFit.FILL);
        Matrix local = new Matrix();
        local.setConcat(crop, source);
        Matrix result = new Matrix();
        result.setConcat(global, local);
        return result;
    }

    Matrix global() {
        View view = image.get();
        if (!EditorWallpaperSource.available(view)) return null;
        try {
            if (!source.equals(stamp(view))) return null;
            Matrix global = new Matrix();
            view.transformMatrixToGlobal(global);
            return compose(
                    width,
                    height,
                    new RectF(drawable.getBounds()),
                    ((android.widget.ImageView) view).getImageMatrix(),
                    global);
        } catch (Exception error) {
            return null;
        }
    }

    boolean matches(byte[] bytes) {
        return bytes != null
                && (java.util.Arrays.equals(png, bytes)
                        || (baked != null && java.util.Arrays.equals(baked, bytes)));
    }

    boolean changed() {
        Matrix current = global();
        if (java.util.Objects.equals(current, lastGlobal)) return false;
        lastGlobal = current;
        return true;
    }

    void watch(Runnable changed) {
        stop();
        View view = image.get();
        if (view == null) return;
        observer = view.getViewTreeObserver();
        listener =
                () -> {
                    changed.run();
                    return true;
                };
        observer.addOnPreDrawListener(listener);
    }

    void stop() {
        if (observer != null && observer.isAlive() && listener != null)
            observer.removeOnPreDrawListener(listener);
        observer = null;
        listener = null;
    }

    byte[] bake(Bitmap mask) throws Exception {
        View view = image.get();
        if (view == null || global() == null || mask == null || mask.isRecycled())
            throw new IllegalStateException("Wallpaper source changed during detection");
        int width = Math.min(1440, view.getWidth()),
                height =
                        Math.max(1, Math.round(view.getHeight() * (float) width / view.getWidth()));
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Matrix scale = new Matrix();
            scale.setScale((float) width / view.getWidth(), (float) height / view.getHeight());
            Matrix map =
                    compose(
                            this.width,
                            this.height,
                            new RectF(drawable.getBounds()),
                            ((android.widget.ImageView) view).getImageMatrix(),
                            scale);
            new Canvas(output)
                    .drawBitmap(
                            mask, map, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            baked = bytes.toByteArray();
            return baked;
        } finally {
            output.recycle();
        }
    }
}
