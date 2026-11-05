package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Looper;
import android.view.View;

public final class MediaReturnProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static ValueAnimator running() {
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        a.setDuration(10000);
        a.start();
        return a;
    }

    public static final class Holder {
        public android.graphics.Bitmap G0;
        public View t0, o0;

        Holder(Context context) {
            t0 = new View(context);
            o0 = new View(context);
        }
    }

    private static void returnLayer(Context context) throws Exception {
        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        root.layout(0, 0, 100, 200);
        Class<?> type =
                Class.forName("dev.denver.dressroomadvanced.MediaFullscreenExtras$Backdrop");
        var ctor = type.getDeclaredConstructor(android.view.ViewGroup.class);
        ctor.setAccessible(true);
        Object backdrop = ctor.newInstance(root);
        android.widget.ImageView image = new android.widget.ImageView(context);
        Holder holder = new Holder(context);
        Class<?> art = Class.forName("dev.denver.dressroomadvanced.MediaFullscreenExtras$Art");
        var artCtor =
                art.getDeclaredConstructor(
                        View.class, android.widget.ImageView.class, Object.class);
        artCtor.setAccessible(true);
        Object state = artCtor.newInstance(image, image, holder);
        Reflect.set(state, "small", new java.lang.ref.WeakReference<>(image));
        Reflect.set(backdrop, "current", state);
        Reflect.set(backdrop, "showing", true);
        var bitmap =
                android.graphics.Bitmap.createBitmap(
                        100, 200, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xffff0000);
        Reflect.set(backdrop, "bitmap", bitmap);
        ((android.graphics.RectF) Reflect.field(backdrop, "destination")).set(0, 0, 100, 200);

        View layer = (View) Reflect.field(backdrop, "returnLayer");
        layer.layout(0, 0, 100, 200);
        layer.setVisibility(View.VISIBLE);
        ((android.graphics.RectF) Reflect.field(backdrop, "returnBounds")).set(0, 100, 100, 200);
        ((android.graphics.RectF) Reflect.field(backdrop, "returnExclusion")).set(0, 100, 100, 140);
        var pixels =
                android.graphics.Bitmap.createBitmap(
                        100, 200, android.graphics.Bitmap.Config.ARGB_8888);
        var canvas = new android.graphics.Canvas(pixels);
        canvas.drawColor(0xff0000ff);
        layer.draw(canvas); // The real return layer is above native controls in the root overlay.
        check(pixels.getPixel(30, 160) == 0xffff0000, "Artwork no longer bridges above the pill");
        check(
                pixels.getPixel(60, 160) == 0xffff0000,
                "Moving artwork was prematurely cropped to a thumbnail");
        check(
                pixels.getPixel(30, 120) == 0xff0000ff,
                "Foreground artwork paints over native controls");
        check(pixels.getPixel(30, 80) == 0xff0000ff, "Bridge escaped the native pill region");
        ((android.graphics.RectF) Reflect.field(backdrop, "returnExclusion")).setEmpty();
        layer.draw(canvas);
        check(
                pixels.getPixel(30, 120) == 0xffff0000,
                "Invisible controls still cut a hole in the bridge");
        bitmap.recycle();
        pixels.recycle();
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        View clock = new View(context);
        check(
                !AodStretchExtras.preserveHeight(false, false, true, 1, true),
                "Disabled option changed native doze");
        check(
                !AodStretchExtras.preserveHeight(true, true, false, 0, false),
                "Expanded media height was held");
        check(
                AodStretchExtras.preserveHeight(true, false, true, 1, true),
                "AOD lost kept height during media return");
        check(
                AodStretchExtras.preserveHeight(true, false, false, .5f, true),
                "Wake transition lost kept height");
        check(
                !AodStretchExtras.preserveHeight(true, false, false, 0, true),
                "Awake media-return spring suppressed");
        check(
                AodStretchExtras.preserveHeight(true, false, false, 0, false),
                "Ordinary awake update restarts height spring");
        android.os.Bundle saved = new android.os.Bundle();
        saved.putBoolean("manual", true);
        saved.putFloatArray("manualHeights", new float[] {2.4f, 1.2f});
        check(
                RuntimeClockData.manualHeights(saved).equals(java.util.List.of(2.4f, 1.2f)),
                "Native source lost manual heights");
        saved.putBoolean("manual", false);
        check(
                RuntimeClockData.manualHeights(saved) == null,
                "Native source changed an unmodified clock");
        saved.putBoolean("depthEnabled", true);
        check(RuntimeClockData.manualHeights(saved) != null, "Depth-owned manual heights omitted");
        saved.putFloatArray("manualHeights", new float[] {Float.NaN});
        check(
                RuntimeClockData.manualHeights(saved) == null,
                "Invalid native source heights accepted");
        int[] ends = {0};
        ValueAnimator first = running();
        check(
                MediaReturnCompletion.await(clock, first, () -> ends[0]++),
                "Native animator not awaited");
        check(ends[0] == 0 && first.isStarted(), "Await snapped or cancelled animation");
        check(
                MediaReturnCompletion.await(clock, first, () -> ends[0] += 100),
                "Repeated await failed");
        first.end();
        check(ends[0] == 1, "Completion duplicated or replaced while same animator ran");
        check(
                first.getListeners() == null || first.getListeners().isEmpty(),
                "End listener leaked");
        first = running();
        ValueAnimator second = running();
        MediaReturnCompletion.await(clock, first, () -> ends[0] += 100);
        MediaReturnCompletion.await(clock, second, () -> ends[0]++);
        first.end();
        check(ends[0] == 1, "Replaced animator completed stale restore");
        second.end();
        check(ends[0] == 2, "Replacement animator failed restore");
        first = running();
        MediaReturnCompletion.await(clock, first, () -> ends[0] += 100);
        MediaReturnCompletion.cancel(clock);
        check(first.isStarted(), "Listener cleanup cancelled Samsung animator");
        first.end();
        check(ends[0] == 2, "Reopened media ran stale restore");
        first = running();
        MediaReturnCompletion.await(clock, first, () -> ends[0]++);
        first.cancel();
        check(ends[0] == 3, "Native cancellation lost settle callback");
        check(
                !MediaReturnCompletion.await(clock, ValueAnimator.ofFloat(0, 1), () -> ends[0]++),
                "Unstarted animation blocked fallback");
        check(MediaFullscreenExtras.compactReveal(1) == 0, "Shortcuts appear at collapse start");
        check(
                MediaFullscreenExtras.compactReveal(.2f) == 0,
                "Compact title appears before the cover clears it");
        check(
                Math.abs(MediaFullscreenExtras.compactReveal(.1f) - .5f) < .00001f,
                "Compact reveal is not continuous");
        check(
                MediaFullscreenExtras.compactReveal(0) == 1,
                "Compact content does not finish visible");
        returnLayer(context);
        android.widget.FrameLayout outer = new android.widget.FrameLayout(context);
        View child =
                new View(context) {
                    @Override
                    public boolean isShown() {
                        return true;
                    }
                };
        outer.addView(child);
        check(
                MediaFullscreenExtras.visibleControls(child),
                "Opaque controls were excluded from visibility");
        outer.setAlpha(0);
        check(
                !MediaFullscreenExtras.visibleControls(child),
                "Invisible ancestor leaves a foreground hole over the pill");
        outer.setAlpha(.05f);
        child.setAlpha(.1f);
        check(
                !MediaFullscreenExtras.visibleControls(child),
                "Combined inherited opacity was ignored");
        outer.setAlpha(1);
        child.setAlpha(1);
        check(
                MediaFullscreenExtras.visibleControls(child),
                "Controls did not restore after a parent fade");
        System.out.println("Media return/native animator contracts: " + checks + " checks passed");
        System.exit(0);
    }
}
