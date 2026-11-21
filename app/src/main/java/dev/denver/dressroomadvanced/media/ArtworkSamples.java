package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

final class ArtworkSamples {
    private static final double[] linear = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255d;
            linear[i] = v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
        }
    }

    private static final int MAX_SAMPLE_DIM = 96;

    private final int width, height;
    private final int[] pixels;

    private ArtworkSamples(Bitmap image) {
        Bitmap readable = readableSampleBitmap(image);
        if (readable == null) {
            width = 0;
            height = 0;
            pixels = new int[0];
            return;
        }
        try {
            width = readable.getWidth();
            height = readable.getHeight();
            pixels = new int[width * height];
            readable.getPixels(pixels, 0, width, 0, 0, width, height);
        } finally {
            if (readable != image) {
                readable.recycle();
            }
        }
    }

    private static Bitmap readableSampleBitmap(Bitmap image) {
        if (image == null || image.isRecycled()) return null;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) return null;
        Bitmap source = image;
        boolean copiedHardware = false;
        try {
            if (source.getConfig() == Bitmap.Config.HARDWARE) {
                source = source.copy(Bitmap.Config.ARGB_8888, false);
                if (source == null) return null;
                copiedHardware = true;
            }
            int maxSide = Math.max(w, h);
            if (maxSide <= MAX_SAMPLE_DIM) {
                Bitmap result = source;
                copiedHardware = false;
                return result;
            }
            float scale = MAX_SAMPLE_DIM / (float) maxSide;
            int targetW = Math.max(1, Math.round(w * scale));
            int targetH = Math.max(1, Math.round(h * scale));
            return Bitmap.createScaledBitmap(source, targetW, targetH, true);
        } finally {
            if (copiedHardware && source != null && source != image) {
                source.recycle();
            }
        }
    }

    static ArtworkSamples read(Bitmap image) {
        return new ArtworkSamples(image);
    }

    static double luminance(int color) {
        return .2126 * linear[(color >> 16) & 255]
                + .7152 * linear[(color >> 8) & 255]
                + .0722 * linear[color & 255];
    }

    record Stats(double mean, double min, double max) {
        double spread() {
            return Math.max(0, max - min);
        }
    }

    Stats analyze(RectF cover, Rect bounds) {
        if (width <= 0 || height <= 0 || pixels.length == 0
                || cover.width() <= 0f || cover.height() <= 0f) {
            return new Stats(0, 0, 0);
        }
        double sum = 0;
        double min = 1;
        double max = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                int px =
                        Math.max(
                                0,
                                Math.min(
                                        width - 1,
                                        (int)
                                                ((bounds.left
                                                                + bounds.width() * (x + .5f) / 8
                                                                - cover.left)
                                                        / cover.width()
                                                        * width)));
                int py =
                        Math.max(
                                0,
                                Math.min(
                                        height - 1,
                                        (int)
                                                ((bounds.top
                                                                + bounds.height() * (y + .5f) / 4
                                                                - cover.top)
                                                        / cover.height()
                                                        * height)));
                int color = pixels[py * width + px];
                double lum =
                        .2126 * linear[(color >> 16) & 255]
                                + .7152 * linear[(color >> 8) & 255]
                                + .0722 * linear[color & 255];
                sum += lum;
                if (lum < min) min = lum;
                if (lum > max) max = lum;
            }
        }
        return new Stats(sum / 32, min, max);
    }

    double sample(RectF cover, Rect bounds) {
        double sum = 0;
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 8; x++) {
                int px =
                        Math.max(
                                0,
                                Math.min(
                                        width - 1,
                                        (int)
                                                ((bounds.left
                                                                + bounds.width() * (x + .5f) / 8
                                                                - cover.left)
                                                        / cover.width()
                                                        * width)));
                int py =
                        Math.max(
                                0,
                                Math.min(
                                        height - 1,
                                        (int)
                                                ((bounds.top
                                                                + bounds.height() * (y + .5f) / 4
                                                                - cover.top)
                                                        / cover.height()
                                                        * height)));
                int color = pixels[py * width + px];
                sum =
                        .2126 * linear[(color >> 16) & 255]
                                + .7152 * linear[(color >> 8) & 255]
                                + .0722 * linear[color & 255]
                                + sum;
            }
        return sum / 32;
    }
}
