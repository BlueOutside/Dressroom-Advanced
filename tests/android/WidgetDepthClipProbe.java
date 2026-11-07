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
    public static void main(String[] args) throws Exception {
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
        System.out.println("Widget depth surface outline ownership: " + checks + " checks passed");
        System.exit(0);
    }
}
