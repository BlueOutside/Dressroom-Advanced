package dev.denver.nativeglyphprobe;

import android.content.Context;
import android.graphics.*;
import android.widget.TextView;

import java.lang.reflect.*;

public class NativeGlyphCache {
    static void test(Context system) throws Exception {
        Context c =
                system.createPackageContext(
                        "com.samsung.android.app.aodservice",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        Class<?> glyph =
                Class.forName(
                        "com.samsung.android.uniform.widget.ClockNonePaddingTextView",
                        true,
                        c.getClassLoader());
        TextView v =
                (TextView)
                        glyph.getConstructor(Context.class, android.util.AttributeSet.class)
                                .newInstance(c, null);
        Class<?> base = glyph.getSuperclass();
        Field mode = base.getDeclaredField("S");
        mode.setAccessible(true);
        mode.setBoolean(v, true);
        Field ready = base.getDeclaredField("R");
        ready.setAccessible(true);
        Method get = glyph.getDeclaredMethod("getPathForBlur");
        get.setAccessible(true);
        v.setLayoutParams(new android.view.ViewGroup.LayoutParams(900, 500));
        v.setTypeface(Typeface.DEFAULT);
        v.setTextSize(0, 80);
        v.setText("21:13");
        v.setTextColor(Color.WHITE);
        int
                w =
                        android.view.View.MeasureSpec.makeMeasureSpec(
                                900, android.view.View.MeasureSpec.EXACTLY),
                h =
                        android.view.View.MeasureSpec.makeMeasureSpec(
                                500, android.view.View.MeasureSpec.EXACTLY);
        v.measure(w, h);
        v.layout(0, 0, 900, 500);
        Bitmap image = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        v.draw(canvas);
        RectF before = bounds((Path) get.invoke(v));
        System.out.println("BEFORE ready=" + ready.getBoolean(v) + " path=" + before);
        v.setTextSize(0, 160);
        RectF stale = bounds((Path) get.invoke(v));
        System.out.println(
                "AFTER_SIZE ready="
                        + ready.getBoolean(v)
                        + " dirty="
                        + v.isDirty()
                        + " layout="
                        + v.isLayoutRequested()
                        + " path="
                        + stale);
        if (!before.equals(stale) || !ready.getBoolean(v) || !v.isDirty() || !v.isLayoutRequested())
            throw new AssertionError("Native stale-ready cache did not reproduce");
        Class<?> guard = Class.forName("dev.denver.dressroomadvanced.GlyphBlurReadiness");
        Method needs = guard.getDeclaredMethod("stale", android.view.View.class, Class.class);
        needs.setAccessible(true);
        if (!Boolean.TRUE.equals(needs.invoke(null, v, base)))
            throw new AssertionError("Guard missed the real native stale-ready cache");
        mode.setBoolean(v, false);
        if (!Boolean.FALSE.equals(needs.invoke(null, v, base)))
            throw new AssertionError("Guard deferred uncached native text");
        mode.setBoolean(v, true);
        v.measure(w, h);
        v.layout(0, 0, 900, 500);
        v.draw(canvas);
        RectF fresh = bounds((Path) get.invoke(v));
        System.out.println("AFTER_DRAW ready=" + ready.getBoolean(v) + " path=" + fresh);
        if (fresh.height() < before.height() * 1.8f)
            throw new AssertionError("Native glyph did not resize");
        System.out.println("NATIVE_GLYPH_CHECKS=4 passed");
        Class<?> memoType = Class.forName("dev.denver.dressroomadvanced.GlyphOutlineMemo");
        Constructor<?> memoConstructor = memoType.getDeclaredConstructor();
        memoConstructor.setAccessible(true);
        Object memo = memoConstructor.newInstance();
        Method capture =
                memoType.getDeclaredMethod(
                        "capture",
                        Paint.class,
                        String.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        Path.class);
        capture.setAccessible(true);
        Method reuse =
                memoType.getDeclaredMethod(
                        "reuse",
                        Paint.class,
                        String.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        Path.class);
        reuse.setAccessible(true);
        Paint p = new Paint();
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(160);
        Path first = new Path();
        p.getTextPath("01:34", 0, 5, 0, 0, first);
        capture.invoke(memo, p, "01:34", 0, 5, 0f, 0f, first);
        p.setShader(
                new LinearGradient(0, 0, 100, 100, Color.RED, Color.BLUE, Shader.TileMode.CLAMP));
        p.setShadowLayer(12, 2, 3, Color.BLACK);
        p.setColor(Color.GREEN);
        Path reused = new Path();
        if (!Boolean.TRUE.equals(reuse.invoke(memo, p, "01:34", 0, 5, 0f, 0f, reused)))
            throw new AssertionError("Draw effects blocked native geometry reuse");
        Path expected = new Path();
        p.getTextPath("01:34", 0, 5, 0, 0, expected);
        if (!java.util.Arrays.equals(reused.approximate(.1f), expected.approximate(.1f)))
            throw new AssertionError("Native outline reuse changed actual glyph geometry");
        p.setTextSize(161);
        if (!Boolean.FALSE.equals(reuse.invoke(memo, p, "01:34", 0, 5, 0f, 0f, reused)))
            throw new AssertionError("Changed native size reused an old outline");
        p.setTextSize(160);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        if (!Boolean.FALSE.equals(reuse.invoke(memo, p, "01:34", 0, 5, 0f, 0f, reused)))
            throw new AssertionError("Changed native weight reused an old outline");
        System.out.println("NATIVE_OUTLINE_REUSE_CHECKS=4 passed");
        image.recycle();
    }

    static RectF bounds(Path p) {
        if (p == null) return null;
        RectF r = new RectF();
        p.computeBounds(r, true);
        return r;
    }
}
