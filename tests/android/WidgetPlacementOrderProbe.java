package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** A free-position frame must not submit blur geometry from before its own translation. */
public final class WidgetPlacementOrderProbe {
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
        FrameLayout screen = new FrameLayout(c), widgets = new FrameLayout(c);
        View blur = new View(c); blur.setTag("BlurView");
        screen.addView(widgets); widgets.addView(blur);
        screen.layout(0, 0, 1000, 1600); widgets.layout(50, 100, 250, 700); blur.layout(0, 0, 200, 100);
        Bitmap mask = Bitmap.createBitmap(10, 16, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(); paint.setColor(Color.WHITE);
        new Canvas(mask).drawRect(0, 0, 5, 16, paint);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); mask.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        // Reproduce runtime registration order: depth first, placement second.
        DepthEffects.applyWidgetRoot(widgets, true, bytes.toByteArray());
        Bundle b = new Bundle(); b.putInt("anchor", 5); b.putBoolean("vertical", true);
        b.putFloat("freeX", .9f); b.putFloat("freeY", .8f);
        WidgetPlacement.apply(widgets, b, null);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        if (widgets.getTranslationX() == 0) throw new AssertionError("Placement did not execute");
        var field = WidgetDepthBlur.class.getDeclaredField("geometry"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<View, ViewGeometry> geometry = (Map<View, ViewGeometry>) field.get(null);
        ViewGeometry sampled = geometry.get(blur);
        if (sampled == null || sampled.update(blur, DepthEffects.subjectIdentity(blur), WidgetMediaOcclusion.source(blur)))
            throw new AssertionError("Blur sampled the previous widget position");
        float x = widgets.getTranslationX(), y = widgets.getTranslationY();
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        if (x != widgets.getTranslationX() || y != widgets.getTranslationY())
            throw new AssertionError("Stationary placement oscillates");
        WidgetPlacement.clear(widgets);
        if (widgets.getTranslationX() != 0 || widgets.getTranslationY() != 0)
            throw new AssertionError("Native translation was not restored");
        DepthEffects.applyWidgetRoot(widgets, false, null);
        System.out.println("Free vertical placement/depth draw ordering: 4 checks passed");
        System.exit(0);
    }
}
