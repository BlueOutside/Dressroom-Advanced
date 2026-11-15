package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;

import java.util.Objects;

/** Uses Samsung's applied-wallpaper region only when the editor shows that same crop. */
final class InstalledWallpaperMask {
    private static final int SAMPLE = 48;

    record Target(int which, int rotation) {}

    private InstalledWallpaperMask() {}

    static Target target(View image) {
        try {
            if (PlatformGate.oneUiVersion() < 90000 || image.getDisplay() == null) return null;
            return new Target(ScreenIdentity.wallpaperWhich(ScreenIdentity.current(), true),
                    image.getDisplay().getRotation());
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    static Bitmap find(Context context, Target target, LiveSubject capture, Bitmap preview) {
        if (target == null) return null;
        try {
            WallpaperManager manager = context.getSystemService(WallpaperManager.class);
            if (manager == null) return null;
            int user = (Integer) Reflect.call(context, "getUserId");
            int which = target.which();
            int id = manager.getWallpaperId(which);
            if (id < 0) return null;
            String scene = (String) Reflect.call(manager, "semGetWallpaperSceneId", which, user);
            Bundle data = (Bundle) Reflect.call(manager, "semGetWallpaperBackgroundRegion",
                    which, user, scene == null ? "" : scene, target.rotation());
            if (data == null || data.getInt("expand_offset_x") != 0
                    || data.getInt("expand_offset_y") != 0) return null;
            Region background = data.getParcelable("background_region", Region.class);
            if (background == null || !background.isComplex()) return null;
            Rect viewport = background.getBounds();
            if (viewport.left != 0 || viewport.top != 0) return null;
            int width = viewport.width(), height = viewport.height();
            Matrix toViewport = capture.sourceToViewport(width, height);
            if (toViewport == null || !toViewport.rectStaysRect()) return null;
            Region foreground = new Region(viewport);
            if (!foreground.op(background, Region.Op.DIFFERENCE)) return null;
            RectF source = new RectF(0, 0, preview.getWidth(), preview.getHeight());
            toViewport.mapRect(source);
            if (!wholeSubject(viewport, foreground.getBounds(), source)
                    || !sameCrop(manager, which, user, preview, toViewport, width, height)
                    || id != manager.getWallpaperId(which)
                    || !Objects.equals(scene,
                            Reflect.call(manager, "semGetWallpaperSceneId", which, user))) return null;
            Matrix toSource = new Matrix();
            if (!toViewport.invert(toSource)) return null;
            Bitmap mask = Bitmap.createBitmap(preview.getWidth(), preview.getHeight(),
                    Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(mask);
                canvas.concat(toSource);
                canvas.drawPath(foreground.getBoundaryPath(), new Paint(Paint.ANTI_ALIAS_FLAG));
                android.util.Log.i(DressroomModule.TAG, "Using Samsung's applied wallpaper region");
                return mask;
            } catch (RuntimeException error) {
                mask.recycle();
                throw error;
            }
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException
                | LinkageError unavailable) {
            return null;
        }
    }

    private static boolean wholeSubject(Rect viewport, Rect foreground, RectF source) {
        // A subject clipped by the applied crop would be missing pieces when panned.
        return source.left <= 1 && source.top <= 1
                && source.right >= viewport.right - 1 && source.bottom >= viewport.bottom - 1
                && (foreground.left > viewport.left || source.left >= -1)
                && (foreground.top > viewport.top || source.top >= -1)
                && (foreground.right < viewport.right || source.right <= viewport.right + 1)
                && (foreground.bottom < viewport.bottom || source.bottom <= viewport.bottom + 1);
    }

    private static boolean sameCrop(WallpaperManager manager, int which, int user, Bitmap preview,
            Matrix toViewport, int width, int height)
            throws ReflectiveOperationException, java.io.IOException {
        try (ParcelFileDescriptor file = (ParcelFileDescriptor) Reflect.call(
                manager, "semGetScreenshotFileDescriptor", which, user, new Bundle())) {
            if (file == null) return false;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(), null, options);
            if (options.outWidth != width || options.outHeight != height) return false;
            options.inJustDecodeBounds = false;
            options.inSampleSize = Math.max(1, Integer.highestOneBit(
                    Math.min(width, height) / (SAMPLE * 2)));
            Bitmap applied = BitmapFactory.decodeFileDescriptor(file.getFileDescriptor(), null, options);
            if (applied == null) return false;
            try {
                Matrix appliedToSample = new Matrix();
                appliedToSample.setScale((float) SAMPLE / applied.getWidth(),
                        (float) SAMPLE / applied.getHeight());
                Matrix previewToSample = new Matrix(toViewport);
                previewToSample.postScale((float) SAMPLE / width, (float) SAMPLE / height);
                return matches(sample(applied, appliedToSample), sample(preview, previewToSample));
            } finally {
                applied.recycle();
            }
        }
    }

    private static int[] sample(Bitmap source, Matrix mapping) {
        Bitmap sample = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
        try {
            new Canvas(sample).drawBitmap(source, mapping, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels = new int[SAMPLE * SAMPLE];
            sample.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);
            return pixels;
        } finally {
            sample.recycle();
        }
    }

    private static boolean matches(int[] applied, int[] preview) {
        int differences = 0, outliers = 0, texture = 0;
        for (int i = 0; i < applied.length; i++) {
            int error = colorDifference(applied[i], preview[i]);
            differences += error;
            if (error > 20) outliers++;
            if (colorDifference(applied[0], applied[i]) > 32) texture++;
        }
        // Flat samples are too easy to confuse with a different image or crop.
        return differences <= 5 * applied.length && outliers <= applied.length / 20
                && texture >= applied.length / 20;
    }

    private static int colorDifference(int first, int second) {
        int red = Math.abs(((first >>> 16) & 255) - ((second >>> 16) & 255));
        int green = Math.abs(((first >>> 8) & 255) - ((second >>> 8) & 255));
        int blue = Math.abs((first & 255) - (second & 255));
        return Math.max(red, Math.max(green, blue));
    }
}
