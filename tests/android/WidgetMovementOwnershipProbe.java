package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.widget.FrameLayout;

/** Custom placement must not restore an offset from before a native animation. */
public final class WidgetMovementOwnershipProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        try { run(); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Context context = (Context) activityThread.getMethod("getSystemContext")
                .invoke(activityThread.getMethod("systemMain").invoke(null));
        FrameLayout screen = new FrameLayout(context), widgets = new FrameLayout(context);
        screen.addView(widgets);
        screen.layout(0, 0, 1000, 1600);
        widgets.layout(50, 100, 250, 200);
        Bundle settings = new Bundle();
        settings.putInt("anchor", 5);
        settings.putFloat("freeX", .8f);
        settings.putFloat("freeY", .6f);
        widgets.setTranslationX(12);
        widgets.setTranslationY(23);
        WidgetPlacement.apply(widgets, settings, null);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        check(widgets.getTranslationX() != 12, "Custom placement did not run");
        WidgetPlacement.clear(widgets);
        check(widgets.getTranslationX() == 12 && widgets.getTranslationY() == 23,
                "Ordinary clear changed the native baseline");

        WidgetPlacement.apply(widgets, settings, null);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        widgets.setTranslationX(40);
        widgets.setTranslationY(55);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        WidgetPlacement.clear(widgets);
        check(widgets.getTranslationX() == 40 && widgets.getTranslationY() == 55,
                "Clear restored stale offsets after native movement");

        WidgetPlacement.apply(widgets, settings, null);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        widgets.setTranslationX(90);
        WidgetPlacement.clear(widgets);
        check(widgets.getTranslationX() == 90 && widgets.getTranslationY() == 55,
                "Clear overwrote native movement before the next draw");

        WidgetPlacement.apply(widgets, settings, null);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        widgets.setTranslationY(71);
        widgets.getViewTreeObserver().dispatchOnPreDraw();
        WidgetPlacement.clear(widgets);
        check(widgets.getTranslationX() == 90 && widgets.getTranslationY() == 71,
                "One native axis write changed the other axis baseline");
        System.out.println("Widget/native translation ownership: " + checks + " checks passed");
    }
}
