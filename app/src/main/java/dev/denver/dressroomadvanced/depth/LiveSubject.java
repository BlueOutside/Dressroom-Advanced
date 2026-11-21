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
                    for (Class<?> owner = type; owner != null; owner = owner.getSuperclass())
                        try {
                            return OneUi80Depth.cropDrawable(owner);
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

    volatile String sourceKey = DepthEffects.currentSourceKey();
    private volatile Object acceptedStamp;

    void adoptSourceKey(String key) {
        if ((sourceKey == null || sourceKey.isEmpty()) && key != null && !key.isEmpty()) {
            sourceKey = key;
        }
    }

    static boolean isSameWallpaper(Object s1, Object s2) {
        if (java.util.Objects.equals(s1, s2)) return true;
        if (s1 instanceof Stamp stamp1 && s2 instanceof Stamp stamp2) {
            if (stamp1.original != null
                    && java.util.Objects.equals(stamp1.original, stamp2.original)) return true;
        }
        return false;
    }

    static boolean samePhotoGeometry(Object s1, Object s2) {
        if (!(s1 instanceof Stamp a) || !(s2 instanceof Stamp b)) return false;
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false;
        if (Math.max(a.width, a.height) < 128 || Math.max(b.width, b.height) < 128) return false;
        long lhs = (long) a.width * b.height;
        long rhs = (long) b.width * a.height;
        return Math.abs(lhs - rhs) * 20L <= Math.max(lhs, rhs);
    }

    private record Stamp(
            Drawable drawable,
            Bitmap bitmap,
            int generation,
            Object original,
            int width,
            int height) {}

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
        Bitmap bitmap =
                plain instanceof BitmapDrawable b
                        ? b.getBitmap()
                        : (inner instanceof BitmapDrawable b ? b.getBitmap() : null);
        Object original = null;
        try {
            original = image.getClass().getMethod("getOriginalDrawable").invoke(image);
        } catch (NoSuchMethodException ignored) {
        }
        int width =
                bitmap != null
                        ? bitmap.getWidth()
                        : (inner != null ? inner.getIntrinsicWidth() : 0);
        int height =
                bitmap != null
                        ? bitmap.getHeight()
                        : (inner != null ? inner.getIntrinsicHeight() : 0);
        return new Stamp(
                inner,
                bitmap,
                bitmap == null ? 0 : bitmap.getGenerationId(),
                original,
                width,
                height);
    }

    static record Capture(LiveSubject subject, Bitmap bitmap) {}

    static record Detail(Bitmap bitmap, Matrix toSource) {}

    Matrix sourceToViewport(int width, int height) {
        if (Math.abs(((double) width * capturedHeight) / ((double) height * capturedWidth) - 1)
                > .01) return null;
        Matrix mapping = new Matrix(capturedToView);
        mapping.postScale((float) width / capturedWidth, (float) height / capturedHeight);
        return mapping;
    }

    Detail detail(Bitmap full) {
        int capW = Math.max(1, capturedWidth), capH = Math.max(1, capturedHeight);
        float ratio = Math.min(1f, Math.min(1440f / capW, 4096f / capH));
        int width = Math.max(1, Math.round(capW * ratio)),
                height = Math.max(1, Math.round(capH * ratio));
        Matrix scale = new Matrix();
        scale.setScale((float) width / capW, (float) height / capH);
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
        Bitmap base = alignedOriginal(image, drawable);
        int sourceWidth = Math.max(1, drawable.getIntrinsicWidth()),
                sourceHeight = Math.max(1, drawable.getIntrinsicHeight());
        Drawable plain = ((android.widget.ImageView) image).getDrawable();
        if (base != null) {
            sourceWidth = base.getWidth();
            sourceHeight = base.getHeight();
        } else if (plain instanceof BitmapDrawable bitmap) {
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
        LiveSubject subject = new LiveSubject(image, drawable, width, height, fingerprint);
        Bitmap owned = base == null ? render(drawable, width, height) : render(base, width, height);
        return new Capture(subject, owned);
    }

    private static Bitmap alignedOriginal(View image, Drawable drawable) {
        if (!(drawable instanceof android.graphics.drawable.LayerDrawable layers)
                || layers.getNumberOfLayers() < 2) return null;
        Drawable base = layers.getDrawable(0);
        if (base == null || !base.getBounds().equals(drawable.getBounds())) return null;
        try {
            Object original = Reflect.call(image, "getOriginalDrawable");
            if (!(original instanceof BitmapDrawable source)) return null;
            Bitmap bitmap = source.getBitmap();
            if (bitmap == null || bitmap.isRecycled()
                    || base.getIntrinsicWidth() <= 0 || base.getIntrinsicHeight() <= 0) return null;
            double originalRatio = bitmap.getWidth() / (double) bitmap.getHeight();
            double layerRatio = base.getIntrinsicWidth() / (double) base.getIntrinsicHeight();
            // The live matte uses the composite bounds. Only substitute the original
            // when the wallpaper layer covers those bounds at the same source aspect.
            return Math.abs(originalRatio / layerRatio - 1) <= .01 ? bitmap : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private static Bitmap render(Bitmap bitmap, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            new Canvas(output).drawBitmap(bitmap, null, new RectF(0, 0, width, height),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            return output;
        } catch (RuntimeException error) {
            output.recycle();
            throw error;
        }
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

    boolean isSameWallpaper(View view) {
        if (view == null) return false;
        if (image.get() == view) {
            try {
                Object current = stamp(view);
                if (source.equals(current)) return true;
                Object accepted = acceptedStamp;
                if (accepted != null && accepted.equals(current)) return true;
                if (isSameWallpaper(source, current)) {
                    acceptedStamp = current;
                    return true;
                }
                String currentKey = DepthEffects.currentSourceKey();
                if (sourceKey != null
                        && !sourceKey.isEmpty()
                        && sourceKey.equals(currentKey)
                        && samePhotoGeometry(source, current)) {
                    acceptedStamp = current;
                    return true;
                }
                String fp = fingerprint(view);
                if (fp != null && fp.equals(this.fingerprint)) {
                    acceptedStamp = current;
                    return true;
                }
                return false;
            } catch (Exception ignored) {
                return false;
            }
        }
        try {
            return source.equals(stamp(view));
        } catch (Exception e) {
            return false;
        }
    }

    Matrix global() {
        View view = image.get();
        if (!EditorWallpaperSource.available(view)) return null;
        try {
            if (!isSameWallpaper(view)) return null;
            Matrix global = new Matrix();
            view.transformMatrixToGlobal(global);
            Matrix matrix =
                    compose(
                            width,
                            height,
                            new RectF(ready(view).getBounds()),
                            ((android.widget.ImageView) view).getImageMatrix(),
                            global);
            return matrix;
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
        // Draw/blur callers also query global(). Only this observer advances its snapshot.
        lastGlobal = current == null ? null : new Matrix(current);
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
        Matrix matrix = global();
        if (view == null || matrix == null || mask == null || mask.isRecycled())
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
                            new RectF(ready(view).getBounds()),
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
