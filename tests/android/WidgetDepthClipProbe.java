package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;

/** Regression: depth content clips must not replace Samsung blur surface outlines. */
public final class WidgetDepthClipProbe {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static final class Content extends View {
        int outlines;
        Content(Context c) { super(c); }
        @Override public void invalidateOutline() { outlines++; super.invalidateOutline(); }
    }
    public static void main(String[] args) {
        try { run(); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context c = (Context) at.getMethod("getSystemContext").invoke(thread);
        FrameLayout root = new FrameLayout(c);
        View blur = new View(c);
        blur.setTag("BlurView");
        Content content = new Content(c);
        root.addView(blur); root.addView(content);
        root.layout(0, 0, 200, 200);
        blur.layout(0, 0, 200, 200); content.layout(0, 0, 200, 200);
        ViewOutlineProvider rounded = new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) { out.setRoundRect(0, 0, 200, 200, 20); }
        };
        blur.setOutlineProvider(rounded); blur.setClipToOutline(true);
        ViewOutlineProvider originalRoot = root.getOutlineProvider(), originalContent = content.getOutlineProvider();
        Bitmap mask = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        new Canvas(mask).drawRect(0, 0, 10, 20, new Paint(0) {{ setColor(Color.WHITE); }});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        mask.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        DepthEffects.apply(root, true, bytes.toByteArray());
        WidgetDepthClip.container(root, true);
        check(root.getOutlineProvider() == originalRoot, "Widget root now clips the blur subtree");
        check(blur.getOutlineProvider() != rounded && blur.getClipToOutline(), "Background depth outline missing");
        check(content.getOutlineProvider() != originalContent && content.getClipToOutline(), "Content depth missing");
        int first = content.outlines;
        WidgetDepthClip.container(root, true);
        check(content.outlines == first, "Stationary widget invalidates its outline every frame");
        content.setTranslationX(12);
        WidgetDepthClip.container(root, true);
        check(content.outlines > first, "Moving widget retained a stale subject clip");
        WidgetDepthClip.container(root, false);
        check(content.getOutlineProvider() == originalContent && !content.getClipToOutline(), "Depth Off did not restore content");
        check(blur.getOutlineProvider() == rounded, "Depth Off altered blur surface");
        Content smooth = new Content(c);
        root.addView(smooth);
        smooth.layout(0, 0, 200, 200);
        Path nativeShape = new Path();
        nativeShape.addOval(0, 0, 200, 200, Path.Direction.CW);
        float[] before = nativeShape.approximate(.1f);
        ViewOutlineProvider nativeSmooth = new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) {
                out.setPath(nativeShape);
                out.setAlpha(.35f);
            }
        };
        smooth.setOutlineProvider(nativeSmooth);
        smooth.setClipToOutline(true);
        WidgetContentClip.apply(smooth, true);
        // Test local composition independently of app_process's missing display
        // metrics. Provider ownership and restoration are exercised above/below.
        Path subject = new Path();
        subject.addRect(0, 0, 100, 200, Path.Direction.CW);
        Outline clipped = new Outline();
        Outline nativeOutline = new Outline();
        nativeSmooth.getOutline(smooth, nativeOutline);
        WidgetContentClip.composeOutline(smooth, clipped, nativeOutline, true, subject, null);
        Path visible = (Path) Reflect.field(clipped, "mPath");
        Region visibleRegion = new Region();
        visibleRegion.setPath(visible, new Region(0, 0, 200, 200));
        check(!visibleRegion.contains(190, 10), "Native path corner became a rectangle");
        check(visibleRegion.contains(150, 100), "Uncovered widget content disappeared");
        check(!visibleRegion.contains(30, 100), "Subject cut was lost while retaining native path");
        check(clipped.getAlpha() == .35f, "Native shadow alpha changed under depth");
        check(java.util.Arrays.equals(before, nativeShape.approximate(.1f)),
                "Depth subtraction mutated Samsung's outline path");
        Path cover = new Path();
        cover.addRect(100, 0, 200, 200, Path.Direction.CW);
        WidgetContentClip.composeOutline(smooth, clipped, nativeOutline, true, null, cover);
        visibleRegion.setPath((Path) Reflect.field(clipped, "mPath"), new Region(0, 0, 200, 200));
        check(visibleRegion.contains(50, 100), "Media cover hid uncovered widget content");
        check(!visibleRegion.contains(150, 100), "Media cover failed to clip the widget");
        check(!visibleRegion.contains(10, 10), "Media cover lost native smooth corners");
        WidgetContentClip.composeOutline(smooth, clipped, nativeOutline, false, subject, null);
        visibleRegion.setPath((Path) Reflect.field(clipped, "mPath"), new Region(0, 0, 200, 200));
        check(visibleRegion.contains(190, 10), "A disabled native content clip was forced on");
        WidgetContentClip.apply(smooth, false);
        check(smooth.getOutlineProvider() == nativeSmooth && smooth.getClipToOutline(),
                "Native path provider was not restored");

        WidgetContentClip.apply(smooth, true);
        // Host layout and RemoteViews updates can replace the provider while
        // our clip is active. Disable must restore the latest native provider.
        smooth.setOutlineProvider(rounded);
        smooth.setClipToOutline(true);
        WidgetContentClip.apply(smooth, true);
        WidgetContentClip.apply(smooth, false);
        check(smooth.getOutlineProvider() == rounded && smooth.getClipToOutline(),
                "A layout replacement restored an obsolete outline provider");
        Outline restored = new Outline();
        smooth.getOutlineProvider().getOutline(smooth, restored);
        Rect bounds = new Rect();
        check(restored.getRect(bounds) && bounds.equals(new Rect(0, 0, 200, 200))
                        && restored.getRadius() == 20,
                "Restored widget corner geometry changed");
        System.out.println("Widget depth surface outline ownership: " + checks + " checks passed");
        System.exit(0);
    }
}
