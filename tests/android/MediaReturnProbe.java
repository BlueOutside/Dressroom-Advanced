package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Looper;
import android.view.View;

public final class MediaReturnProbe {
    private static void fullscreen85(Context context) throws Exception {
        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        android.widget.FrameLayout page = new android.widget.FrameLayout(context);
        View frame = new View(context);root.addView(page);page.addView(frame);
        page.setAlpha(.4f);frame.setAlpha(.5f);
        check(Math.abs(OneUi85FullscreenArt.opacity(frame, root) - .2f) < .0001f,
                "8.5 artwork ignores inherited native fade");
        MediaBlurHost.hold(frame);
        check(frame.getAlpha() == 0 && Math.abs(OneUi85FullscreenArt.opacity(frame, root) - .2f) < .0001f,
                "Hiding native artwork also hides its fullscreen copy");
        MediaBlurHost.nativeAlpha(frame, .6f);
        check(Math.abs(OneUi85FullscreenArt.opacity(frame, root) - .24f) < .0001f,
                "Fullscreen fade ignores new native alpha while held");
        frame.setVisibility(View.INVISIBLE);
        check(OneUi85FullscreenArt.opacity(frame, root) == 0, "Hidden page leaves fullscreen art visible");
        frame.setVisibility(View.VISIBLE);MediaBlurHost.release(frame);
        check(frame.getAlpha() == .6f, "Native artwork release restores stale opacity");
        check(OneUi85FullscreenArt.opacity(frame, new View(context)) == 0,
                "Artwork from another root is used");
        var type = Class.forName("dev.denver.dressroomadvanced.OneUi85FullscreenArt$Layer");
        var constructor = type.getDeclaredConstructor(android.view.ViewGroup.class);constructor.setAccessible(true);
        View layer = (View) constructor.newInstance(root);
        android.graphics.drawable.ColorDrawable background = new android.graphics.drawable.ColorDrawable(android.graphics.Color.RED);
        View card = new View(context);card.setBackground(background);
        Reflect.call(layer, "suppressBackground", card);
        check(card.getBackground() instanceof OneUi85FullscreenArt.Blank, "Native framed background stays visible");
        card.getBackground().setAlpha(121);
        int nativeAlpha = background.getAlpha();
        Reflect.call(layer, "restore");
        check(card.getBackground() == background && background.getAlpha() == nativeAlpha,
                "Restoring native card loses background updates");
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888);
        image.eraseColor(android.graphics.Color.BLUE);
        Reflect.set(layer, "bitmap", image);layer.layout(0, 0, 240, 120);
        android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(240, 120, android.graphics.Bitmap.Config.ARGB_8888);
        Reflect.call(layer, "onDraw", new android.graphics.Canvas(output));
        check(output.getPixel(0, 0) == android.graphics.Color.BLUE
                        && output.getPixel(239, 119) == android.graphics.Color.BLUE,
                "Fullscreen artwork retains the native rounded frame");
        check(!image.isRecycled(), "Fullscreen layer recycles Samsung's artwork");
        ValueAnimator fade = ValueAnimator.ofInt(255, 0);fade.setDuration(200);fade.start();
        Reflect.call(layer, "bindFade", fade, false);
        Reflect.call(layer, "update", true);
        check(layer.getVisibility() == View.VISIBLE && Reflect.field(layer, "bitmap") == image,
                "Native source removal drops art before the wallpaper fade");
        fade.setCurrentFraction(.5f);Reflect.call(layer, "update", true);
        check(layer.getAlpha() > .4f && layer.getAlpha() < .6f,
                "Artwork ignores the native wallpaper return progress");
        check(Reflect.field(layer, "bitmap") == image, "Fade loses its retained native texture");
        fade.end();Reflect.call(layer, "update", true);
        check(layer.getVisibility() == View.INVISIBLE && Reflect.field(layer, "bitmap") == null,
                "Completed wallpaper fade retains fullscreen art");
        ValueAnimator opening = ValueAnimator.ofInt(0, 255);opening.setDuration(300);opening.start();
        opening.setCurrentFraction(.5f);Reflect.call(layer, "bindFade", opening, true);
        float openingAlpha = ((Number) Reflect.field(layer, "nativeAlpha")).floatValue();
        check(openingAlpha > .4f && openingAlpha < .6f, "Opening ignores native wallpaper fade");
        Reflect.call(layer, "sample", fade);
        check(((Number) Reflect.field(layer, "nativeAlpha")).floatValue() == openingAlpha,
                "Old closing animator overwrites the reopened scene");
        opening.end();Reflect.call(layer, "bindFade", opening, true);
        check(((Number) Reflect.field(layer, "nativeAlpha")).floatValue() == 1,
                "Already-completed opening loses its final opacity");
        Reflect.call(layer, "bindFade", opening, false);
        check(Boolean.FALSE.equals(Reflect.field(layer, "nativeFade")),
                "Missing replacement wallpaper animator retains stale opacity forever");
        Reflect.call(layer, "bindFade", opening, false);
        check(Boolean.FALSE.equals(Reflect.field(layer, "nativeFade")),
                "Repeated native notification rebinds a stale expanded endpoint");
        ValueAnimator closing = ValueAnimator.ofInt(255, 0);closing.setDuration(200);closing.start();
        Reflect.set(layer, "bitmap", image);Reflect.call(layer, "bindFade", closing, false);
        Reflect.call(layer, "update", false);
        check(layer.getVisibility() == View.INVISIBLE && Reflect.field(layer, "bitmap") == null,
                "Disabling fullscreen does not release its fading texture");
        Reflect.call(layer, "clearFade");
        check(closing.getListeners() == null || closing.getListeners().isEmpty(),
                "Detached fullscreen layer retains native wallpaper listeners");
        closing.end();output.recycle();image.recycle();
    }

    private static final class QueuedView extends View {
        final java.util.ArrayList<Runnable> frames = new java.util.ArrayList<>();
        QueuedView(Context context) { super(context); }
        @Override public void postOnAnimation(Runnable action) { frames.add(action); }
        void frame() {
            var next = new java.util.ArrayList<>(frames);
            frames.clear();
            for (Runnable action : next) action.run();
        }
    }

    private static void mediaDepth(Context context) {
        QueuedView view = new QueuedView(context);
        MediaDepthTransition.update(view, true);
        check(DepthEffects.mediaActive(view), "Expanded player retains wallpaper depth");
        MediaDepthTransition.update(view, false);
        check(DepthEffects.mediaActive(view), "Depth rebuild runs inside the native close call");
        ValueAnimator animation = running();
        int[] ends = {0};
        MediaReturnCompletion.await(view, animation, () -> ends[0]++);
        view.frame();
        check(!DepthEffects.mediaActive(view), "Depth waits until the return animation finishes");
        animation.end();
        check(ends[0] == 1, "Depth restore cancels the clock stretch completion callback");

        MediaDepthTransition.update(view, true);
        MediaDepthTransition.update(view, false);
        MediaDepthTransition.update(view, true);
        view.frame();
        check(DepthEffects.mediaActive(view), "Old return masks a rapidly reopened media clock");

        MediaDepthTransition.update(view, false);
        MediaDepthTransition.screenChanged();
        check(!DepthEffects.mediaActive(view), "Folding retains the old screen's depth return");
        MediaDepthTransition.update(view, true);
        view.frame();
        check(DepthEffects.mediaActive(view), "Old screen callback masks the new media clock");
        MediaDepthTransition.update(view, false);
        view.frame();
    }

    private static void openingDepth(Context context) throws Exception {
        QueuedView view = new QueuedView(context);
        view.layout(0, 0, 100, 200);
        android.graphics.Bitmap mask = android.graphics.Bitmap.createBitmap(16, 16,
                android.graphics.Bitmap.Config.ARGB_8888);
        mask.eraseColor(android.graphics.Color.WHITE);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        mask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes);
        DepthEffects.apply(view, true, bytes.toByteArray());
        ValueAnimator animation = running();
        MediaDepthTransition.update(view, true, ignored -> animation);
        check(!DepthEffects.mediaActive(view), "Opening immediately removed subject depth");
        view.frame();
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(!DepthEffects.mediaActive(view), "Running opening animator lost depth");
        MediaDepthTransition.update(view, true, ignored -> animation);
        animation.end();
        view.setTranslationY(30);
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(!DepthEffects.mediaActive(view), "Page movement after stretch lost depth");
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(!DepthEffects.mediaActive(view), "Opening settled before its final layout frame");
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(DepthEffects.mediaActive(view), "Settled media clock retains wallpaper cuts");
        MediaDepthTransition.restore(view);
        MediaDepthTransition.update(view, true, ignored -> null);
        MediaDepthTransition.update(view, false, ignored -> null);
        view.frame();
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(!DepthEffects.mediaActive(view), "Cancelled opening remasked the closing clock");
        MediaDepthTransition.update(view, true, ignored -> null);
        view.frame();
        MediaDepthTransition.screenChanged();
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(!DepthEffects.mediaActive(view), "Old orientation's opening completion survived");
        DepthEffects.apply(view, false, null);
        mask.recycle();
    }

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

    public static void main(String[] args) {
        try { run(); }
        catch (Throwable error) { error.printStackTrace(System.out);System.exit(1); }
    }

    private static void run() throws Exception {
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
        mediaDepth(context);
        openingDepth(context);
        fullscreen85(context);
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
