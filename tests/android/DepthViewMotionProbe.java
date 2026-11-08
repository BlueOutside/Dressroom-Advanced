package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

public final class DepthViewMotionProbe {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static final class Child extends View {
        int redraws;
        Child(Context context) { super(context); }
        @Override public void invalidate() { redraws++; super.invalidate(); }
    }
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context context = (Context) at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        FrameLayout parent = new FrameLayout(context), owner = new FrameLayout(context);
        Child child = new Child(context);
        parent.addView(owner); owner.addView(child);
        parent.layout(0, 0, 200, 400); owner.layout(0, 0, 100, 200); child.layout(0, 0, 80, 80);
        android.graphics.Bitmap mask = android.graphics.Bitmap.createBitmap(16, 16,
                android.graphics.Bitmap.Config.ARGB_8888);
        mask.eraseColor(android.graphics.Color.WHITE);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        mask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes);
        DepthEffects.apply(owner, true, bytes.toByteArray());
        mask.recycle();
        DepthViewMotion.watch(owner, true);
        owner.getViewTreeObserver().dispatchOnPreDraw();
        child.redraws = 0;
        parent.setTranslationY(30);
        child.redraws = 0;
        owner.getViewTreeObserver().dispatchOnPreDraw();
        check(child.redraws > 0, "Parent swipe reused a stale child display list");
        child.redraws = 0;
        owner.getViewTreeObserver().dispatchOnPreDraw();
        check(child.redraws == 0, "Stationary owner redraws its children every frame");
        owner.setScaleY(.8f);
        child.redraws = 0;
        owner.getViewTreeObserver().dispatchOnPreDraw();
        check(child.redraws > 0, "Scaling reused a stale cut");
        DepthEffects.media(owner, true);
        parent.setTranslationY(60);
        child.redraws = 0;
        owner.getViewTreeObserver().dispatchOnPreDraw();
        check(child.redraws == 0, "Settled media clock still rebuilds its wallpaper cut");
        DepthEffects.media(owner, false);
        DepthViewMotion.watch(owner, false);
        parent.setTranslationY(80);
        child.redraws = 0;
        owner.getViewTreeObserver().dispatchOnPreDraw();
        check(child.redraws == 0, "Disabled motion watcher remains installed");
        DepthEffects.apply(owner, false, null);
        System.out.println("Live depth view motion: " + checks + " checks passed");
        System.exit(0);
    }
}
