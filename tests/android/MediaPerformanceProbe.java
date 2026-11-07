package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

public final class MediaPerformanceProbe {
    static int checks;
    static volatile double sink;

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    static void run() {
        Bitmap image = Bitmap.createBitmap(96, 73, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 73; y++)
            for (int x = 0; x < 96; x++)
                image.setPixel(x, y, 0xff000000 | ((x * 7919 + y * 104729) & 0xffffff));
        ArtworkSamples cache = ArtworkSamples.read(image);
        for (int i = 0; i < 128; i++) {
            RectF cover = new RectF(-i * 2.3f, i * .7f, 500 + i * 3.1f, 900 - i * .2f);
            Rect bounds = new Rect(i * 7 - 300, i * 3 - 100, i * 7 + 80, i * 3 + 130);
            check(
                    Double.doubleToLongBits(cache.sample(cover, bounds))
                            == Double.doubleToLongBits(reference(image, cover, bounds)),
                    "Contrast changed at crop " + i);
        }
        RectF cover = new RectF(0, 0, 96, 73);
        Rect bounds = new Rect(5, 9, 85, 60);
        double retained = cache.sample(cover, bounds);
        int generation = image.getGenerationId();
        image.eraseColor(0xffffffff);
        check(
                cache.sample(cover, bounds) == retained,
                "Cached pixels alias the changing native artwork");
        check(image.getGenerationId() != generation, "Artwork mutation did not change generation");
        cache = ArtworkSamples.read(image);
        check(cache.sample(cover, bounds) == 1d, "New artwork snapshot is stale");
        Bitmap tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        tiny.eraseColor(0xff123456);
        check(
                ArtworkSamples.read(tiny).sample(cover, new Rect(-100, -100, 300, 300))
                        == reference(tiny, cover, new Rect(-100, -100, 300, 300)),
                "Clamped single-pixel sample changed");
        tiny.recycle();
        Object face = new Object();
        GlyphFontCache.Applied font =
                new GlyphFontCache.Applied(801, "'HEGT' 2.3456, 'wght' 300", face);
        check(
                font.matches(801, new String("'HEGT' 2.3456, 'wght' 300"), face),
                "Equivalent native request was not reusable");
        check(!font.matches(804, font.axes(), face), "Changed native font was suppressed");
        check(
                !font.matches(801, "'HEGT' 2.3457, 'wght' 300", face),
                "A small stretch change was rounded away");
        check(
                !font.matches(801, "'HEGT' 2.3456, 'wght' 301", face),
                "Weight animation was suppressed");
        check(
                !font.matches(801, font.axes(), new Object()),
                "External typeface update was suppressed");
        check(!font.matches(801, null, face), "Clearing variation settings was suppressed");
        check(
                new GlyphFontCache.Applied(0, null, face).matches(0, null, face),
                "Null native axes not handled");
        image.eraseColor(0xff267fb3);
        cache = ArtworkSamples.read(image);
        for (int i = 0; i < 3000; i++) {
            sink = reference(image, cover, bounds);
            sink = cache.sample(cover, bounds);
        }
        long start = System.nanoTime();
        for (int i = 0; i < 20000; i++) sink = reference(image, cover, bounds);
        long oldTime = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 20000; i++) sink = cache.sample(cover, bounds);
        long newTime = System.nanoTime() - start;
        System.out.println(
                "Contrast sampling 20000 calls: bitmap/pow="
                        + oldTime / 1e6
                        + "ms array/LUT="
                        + newTime / 1e6
                        + "ms (isolated, not FPS)");
        image.recycle();
        System.out.println("Media performance/visual parity: " + checks + " checks passed");
    }

    static double reference(Bitmap image, RectF cover, Rect bounds) {
        double sum = 0;
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 8; x++) {
                int px =
                        Math.max(
                                0,
                                Math.min(
                                        image.getWidth() - 1,
                                        (int)
                                                ((bounds.left
                                                                + bounds.width() * (x + .5f) / 8
                                                                - cover.left)
                                                        / cover.width()
                                                        * image.getWidth())));
                int py =
                        Math.max(
                                0,
                                Math.min(
                                        image.getHeight() - 1,
                                        (int)
                                                ((bounds.top
                                                                + bounds.height() * (y + .5f) / 4
                                                                - cover.top)
                                                        / cover.height()
                                                        * image.getHeight())));
                int color = image.getPixel(px, py);
                sum =
                        .2126 * linear((color >> 16) & 255)
                                + .7152 * linear((color >> 8) & 255)
                                + .0722 * linear(color & 255)
                                + sum;
            }
        return sum / 32;
    }

    static double linear(int value) {
        double v = value / 255d;
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
