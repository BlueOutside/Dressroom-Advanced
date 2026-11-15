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

    private final int width, height;
    private final int[] pixels;

    private ArtworkSamples(Bitmap image) {
        width = image.getWidth();
        height = image.getHeight();
        pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
    }

    static ArtworkSamples read(Bitmap image) {
        return new ArtworkSamples(image);
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
