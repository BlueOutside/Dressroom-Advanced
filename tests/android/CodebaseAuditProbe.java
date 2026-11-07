package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Region;

public final class CodebaseAuditProbe {
    static int checks;

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    static Region raster(Path path) {
        Region region = new Region();
        region.setPath(path, new Region(-100, -100, 200, 200));
        return region;
    }

    public static class Scalars {
        public double scalar(double value) {
            return value;
        }

        public byte scalar(byte value) {
            return value;
        }

        public short scalar(short value) {
            return value;
        }

        public char scalar(char value) {
            return value;
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    static void run() throws Exception {
        Path winding = new Path();
        winding.addRect(0, 0, 100, 100, Path.Direction.CW);
        winding.addRect(20, 20, 80, 80, Path.Direction.CW);
        Path corner = new Path();
        corner.addRect(0, 0, 10, 10, Path.Direction.CW);
        check(
                raster(DepthGeometry.subtract(winding, corner)).contains(50, 50),
                "Winding path unexpectedly has a hole");
        Path evenOdd = new Path(winding);
        evenOdd.setFillType(Path.FillType.EVEN_ODD);
        check(
                !raster(DepthGeometry.subtract(evenOdd, corner)).contains(50, 50),
                "Cache reused winding result for an even-odd hole");
        check(
                raster(DepthGeometry.subtract(winding, corner)).contains(50, 50),
                "Switching fill rule poisoned winding cache");
        Scalars scalar = new Scalars();
        check(
                (Double) Reflect.call(scalar, "scalar", 2.5d) == 2.5d,
                "Double primitive dispatch failed");
        check(
                (Byte) Reflect.call(scalar, "scalar", (byte) 3) == 3,
                "Byte primitive dispatch failed");
        check(
                (Short) Reflect.call(scalar, "scalar", (short) 4) == 4,
                "Short primitive dispatch failed");
        check(
                (Character) Reflect.call(scalar, "scalar", 'x') == 'x',
                "Char primitive dispatch failed");
        Bitmap full = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        full.setPixel(1, 1, 0x80ffffff);
        Bitmap result =
                MaskAlpha.refine(
                        full,
                        () -> {
                            throw new IllegalStateException("Synthetic optional-detail failure");
                        });
        check(
                result == full && !full.isRecycled() && (full.getPixel(1, 1) >>> 24) == 128,
                "Detail failure discarded full-image subject");
        check(
                MaskAlpha.refine(full, () -> null) == full,
                "Missing detail discarded full-image subject");
        Bitmap extra = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        extra.setPixel(2, 2, 0xc0ffffff);
        result = MaskAlpha.refine(full, () -> extra);
        check(
                result == full && !full.isRecycled() && extra.isRecycled(),
                "Merged detail ownership incorrect");
        check(
                (full.getPixel(1, 1) >>> 24) == 128 && (full.getPixel(2, 2) >>> 24) == 192,
                "Detail merge lost alpha coverage");
        check(
                MaskAlpha.refine(full, () -> full) == full && !full.isRecycled(),
                "Aliased detail recycled live matte");
        Bitmap detailOnly = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        check(
                MaskAlpha.refine(null, () -> detailOnly) == detailOnly && !detailOnly.isRecycled(),
                "Detail-only subject rejected");
        check(MaskAlpha.refine(null, () -> null) == null, "Empty detections created a subject");
        try {
            MaskAlpha.refine(
                    null,
                    () -> {
                        throw new IllegalStateException("No usable pass");
                    });
            throw new AssertionError("Total failure swallowed");
        } catch (IllegalStateException expected) {
            checks++;
        }
        check(
                MaskAlpha.refine(
                                full,
                                () -> {
                                    throw new NoClassDefFoundError(
                                            "Synthetic optional implementation");
                                })
                        == full,
                "Optional detail linkage failure discarded full-image subject");
        full.recycle();
        detailOnly.recycle();
        System.out.println("Codebase audit: " + checks + " checks passed");
    }
}
