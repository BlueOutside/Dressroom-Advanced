package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.RenderEffect;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

public final class OptimizationProbe {
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

    static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        FrameLayout parent = new FrameLayout(context);
        View view = new View(context);
        parent.addView(view);
        parent.layout(0, 0, 200, 200);
        view.layout(0, 0, 80, 60);
        ViewGeometry geometry = new ViewGeometry();
        Object subject = new Object(), cover = new Object();
        check(geometry.update(view, subject, cover), "Initial geometry omitted");
        boolean stable = true;
        for (int i = 0; i < 100; i++) stable &= !geometry.update(view, subject, cover);
        check(stable, "Stable matrix accumulated a transform");
        view.setTranslationX(9);
        check(geometry.update(view, subject, cover), "Translation ignored");
        check(!geometry.update(view, subject, cover), "Translation keeps invalidating");
        parent.setTranslationY(13);
        check(geometry.update(view, subject, cover), "Ancestor movement ignored");
        parent.setScrollX(4);
        check(geometry.update(view, subject, cover), "Ancestor scroll ignored");
        view.setScaleX(.5f);
        check(geometry.update(view, subject, cover), "Scale ignored");
        view.setRotation(23);
        check(geometry.update(view, subject, cover), "Rotation ignored");
        view.layout(0, 0, 81, 60);
        check(geometry.update(view, subject, cover), "Resize ignored");
        check(geometry.update(view, new Object(), cover), "New subject ignored");
        check(geometry.update(view, subject, new Object()), "New media cover ignored");
        check(geometry.update(view, null, null), "Released masks ignored");
        check(!geometry.update(view, null, null), "Released masks keep invalidating");

        ShadowEffectCache cache = new ShadowEffectCache();
        RenderEffect first = cache.get(8, 2, 3, 0xff000000, null);
        check(cache.get(8, 2, 3, 0xff000000, null) == first, "Identical shadow graph rebuilt");
        RenderEffect next = cache.get(10, 2, 3, 0xff000000, null);
        check(next != first, "Radius edit ignored");
        check(cache.get(10, 2, 3, 0xff000000, null) == next, "New radius is not cached");
        first = next;
        next = cache.get(10, 4, 3, 0xff000000, null);
        check(next != first, "X edit ignored");
        first = next;
        next = cache.get(10, 4, 5, 0xff000000, null);
        check(next != first, "Y edit ignored");
        first = next;
        next = cache.get(10, 4, 5, 0xff123456, null);
        check(next != first, "Color edit ignored");
        RenderEffect nativeEffect = RenderEffect.createOffsetEffect(1, 2);
        first = next;
        next = cache.get(10, 4, 5, 0xff123456, nativeEffect);
        check(next != first, "Native effect edit ignored");
        check(
                cache.get(10, 4, 5, 0xff123456, nativeEffect) == next,
                "Native effect identity not cached");
        check(cache.get(10, 4, 5, 0xff123456, null) != next, "Native effect removal ignored");
        check(cache.get(0, 0, 0, 0xff000000, null) != null, "Zero-radius shadow unavailable");
        EffectView target = new EffectView(context);
        ShadowEffects.apply(target, true, 8, 2, 3, 0xff000000);
        first = target.effect;
        ShadowEffects.apply(target, true, 8, 2, 3, 0xff000000);
        check(target.writes == 1 && target.effect == first, "Unchanged shadow resubmitted");
        ShadowEffects.apply(target, false, 0, 0, 0, 0);
        check(target.effect == null, "Disabled shadow retained graph");
        ShadowEffects.apply(target, true, 8, 2, 3, 0xff000000);
        check(target.effect != first, "Disabled shadow cache retained");
        System.out.println("Optimization contracts: " + checks + " checks passed");
    }

    static final class EffectView extends View {
        RenderEffect effect;
        int writes;

        EffectView(Context context) {
            super(context);
        }

        @Override
        public void setRenderEffect(RenderEffect value) {
            effect = value;
            writes++;
            super.setRenderEffect(value);
        }
    }
}
