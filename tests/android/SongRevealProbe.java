package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

public final class SongRevealProbe {
    static int checks;

    static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    static Bitmap pixels(NativeArtworkReveal reveal, Bitmap image, boolean hardware)
            throws Exception {
        RectF bounds = new RectF(0, 0, 128, 128);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (!hardware) {
            Bitmap result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            reveal.draw(new Canvas(result), image, bounds, paint);
            return result;
        }
        RenderNode node = new RenderNode("Native song reveal regression");
        node.setPosition(0, 0, 128, 128);
        Canvas canvas = node.beginRecording();
        reveal.draw(canvas, image, bounds, paint);
        node.endRecording();
        var create =
                HardwareRenderer.class.getDeclaredMethod(
                        "createHardwareBitmap", RenderNode.class, int.class, int.class);
        create.setAccessible(true);
        Bitmap gpu = (Bitmap) create.invoke(null, node, 128, 128);
        Bitmap result = gpu.copy(Bitmap.Config.ARGB_8888, false);
        gpu.recycle();
        node.discardDisplayList();
        return result;
    }

    static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        Bitmap red = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
                blue = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        red.eraseColor(Color.RED);
        blue.eraseColor(Color.BLUE);
        ImageView image = new ImageView(context);
        NativeArtworkReveal fallback = new NativeArtworkReveal(null, image, null, null);
        fallback.begin(red, blue);
        fallback.progress(0);
        fallback.update();
        Bitmap result = pixels(fallback, blue, false);
        check(
                result.getPixel(64, 64) == Color.RED,
                "New song replaced the origin before progress started");
        result.recycle();
        fallback.progress(.5f);
        result = pixels(fallback, blue, false);
        int mixed = result.getPixel(64, 64);
        check(
                Color.red(mixed) > 100 && Color.blue(mixed) > 100,
                "Fallback snapped instead of following native progress");
        result.recycle();
        fallback.progress(1);
        result = pixels(fallback, blue, false);
        check(
                result.getPixel(64, 64) == Color.BLUE,
                "Completed reveal retained the previous artwork");
        result.recycle();
        check(!red.isRecycled() && !blue.isRecycled(), "Renderer recycled native-owned artwork");
        fallback.begin(red, red);
        fallback.progress(.25f);
        result = pixels(fallback, red, false);
        check(
                result.getPixel(64, 64) == Color.RED,
                "Pending native image bind lost the previous cover");
        result.recycle();
        result = pixels(fallback, blue, false);
        mixed = result.getPixel(64, 64);
        check(
                Color.red(mixed) > 100 && Color.blue(mixed) > 20,
                "Posted image bind bypassed the reveal");
        result.recycle();
        RectF crop = new RectF();
        Bitmap scratch = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        NativeArtworkReveal.bitmap(
                new Canvas(scratch),
                blue,
                new RectF(0, 0, 100, 100),
                1.2f,
                1.1f,
                new Paint(),
                crop);
        check(
                Math.abs(crop.width() - 120) < .001f && Math.abs(crop.height() - 110) < .001f,
                "Native zoom was applied to the frame instead of image pixels");
        scratch.recycle();
        Context aod =
                context.createPackageContext(
                        "com.samsung.android.app.aodservice", Context.CONTEXT_IGNORE_SECURITY);
        RuntimeShader shader = NativeArtworkReveal.load(aod);
        check(shader != null, "Installed native shader did not compile");
        View mask = new View(aod);
        NativeArtworkReveal nativeReveal = new NativeArtworkReveal(mask, image, null, null);
        nativeReveal.begin(red, blue);
        nativeReveal.random(.37f);
        nativeReveal.progress(0);
        nativeReveal.update();
        result = pixels(nativeReveal, blue, true);
        check(result.getPixel(64, 64) == Color.RED, "Native GPU reveal shows incoming art at zero");
        result.recycle();
        nativeReveal.progress(.5f);
        nativeReveal.update();
        result = pixels(nativeReveal, blue, true);
        int affected = 0, origin = 0;
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                int color = result.getPixel(x, y);
                if (Color.blue(color) > 2) affected++;
                if (Color.red(color) > 20) origin++;
            }
        check(
                affected > 100 && origin > 100,
                "Native mask did not mix old and incoming artwork: incoming="
                        + affected
                        + ", origin="
                        + origin
                        + ", center="
                        + Integer.toHexString(result.getPixel(64, 64)));
        result.recycle();
        nativeReveal.progress(1);
        nativeReveal.update();
        result = pixels(nativeReveal, blue, true);
        check(
                result.getPixel(64, 64) == Color.BLUE,
                "Native GPU reveal did not finish on the new song");
        result.recycle();
        nativeReveal.discard();
        check(
                WidgetMediaReveal.visualAlpha(255, .5f) == .5f,
                "Native 255 alpha prevents a gradual widget reveal");
        check(
                WidgetMediaReveal.visualAlpha(.4f, .5f) == .2f,
                "Widget fade lost a native partial opacity");
        check(
                WidgetMediaReveal.visualAlpha(0, .5f) == 0,
                "Widget fade exposed natively hidden contents");
        check(
                WidgetMediaReveal.visualAlpha(255, 1) == 255,
                "Completed fade changed Samsung's native input");
        check(WidgetMediaReveal.visualAlpha(1, 0) == 0, "Widgets appear at the beginning of close");
        red.recycle();
        blue.recycle();
        System.out.println(
                "Native song GPU reveal and widget opacity: " + checks + " checks passed");
    }
}
