package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import java.lang.reflect.Method;
import java.util.Map;

public final class UnlockBlurProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        try { run(); System.out.println("Unlock blur: " + checks + " checks passed"); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = UnlockBlur.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    @SuppressWarnings("unchecked")
    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Context context = (Context) thread.getMethod("getSystemContext")
                .invoke(thread.getMethod("systemMain").invoke(null));
        check(UnlockBlur.duringSwipe(true, true, .99f), "First drag frame disables blur");
        check(!UnlockBlur.duringSwipe(false, true, .5f), "Screen/media fade mistaken for unlock drag");
        check(!UnlockBlur.duringSwipe(true, false, .5f), "Disabled setting changes native behavior");
        check(UnlockBlur.duringSwipe(true, true, 0), "Native fade endpoint mistaken for completed unlock");
        check(!UnlockBlur.duringSwipe(true, true, 1), "Cancelled drag retains override");
        check(!UnlockBlur.duringSwipe(true, true, Float.NaN), "Invalid progress retains override");
        Bundle b = new Bundle(); b.putBoolean("swipeBlur", true);
        check(ExtrasPreferences.clean(b).getBoolean("swipeBlur"), "Extras drops swipe-blur choice");

        View clock = new AttachedView(context);
        Map<View, Float> clocks = (Map<View, Float>) field("clockAlpha").get(null);
        clocks.put(clock, .8f);
        Method apply = UnlockBlur.class.getDeclaredMethod("applyOpacity"); apply.setAccessible(true);
        Method restore = UnlockBlur.class.getDeclaredMethod("restore"); restore.setAccessible(true);
        field("alpha").setFloat(null, .4f); apply.invoke(null);
        check(Math.abs(clock.getAlpha() - .32f) < .00001f, "Clock blur doesn't follow native fade");
        field("alpha").setFloat(null, .2f); apply.invoke(null);
        check(Math.abs(clock.getAlpha() - .16f) < .00001f, "Clock fade compounds on itself");
        restore.invoke(null);
        check(clock.getAlpha() == .8f, "Clock native opacity not restored");

        GradientDrawable drawable = new GradientDrawable(); drawable.setAlpha(180);
        Class<?> stateType = Class.forName("dev.denver.dressroomadvanced.UnlockBlur$Background");
        var ctor = stateType.getDeclaredConstructor(View.class, int.class); ctor.setAccessible(true);
        View widget = new AttachedView(context); widget.setAlpha(.5f);
        Object state = ctor.newInstance(widget, 180);
        Map<Object, Object> backgrounds = (Map<Object, Object>) field("backgrounds").get(null);
        backgrounds.put(drawable, state);
        Reflect.set(state, "requested", 80); drawable.setAlpha(80); apply.invoke(null);
        check(drawable.getAlpha() == 90, "Moving blurred container does not fade with its content");
        restore.invoke(null);
        check(drawable.getAlpha() == 80, "Background native fade not restored");
        // An idle native alpha write updates requested and baseline in the opacity hook.
        Reflect.set(state, "requested", 0); Reflect.set(state, "baseline", 0);
        drawable.setAlpha(0); apply.invoke(null);
        check(drawable.getAlpha() == 0, "Hidden background resurrected");
        View child = new AttachedView(context);
        android.widget.FrameLayout fadeParent = new android.widget.FrameLayout(context); fadeParent.addView(child);
        child.setAlpha(.5f); fadeParent.setAlpha(.5f);
        check(UnlockBlur.fadedOpacity(200, child) == 50, "Blur ignores ancestor opacity");
        fadeParent.setVisibility(View.INVISIBLE);
        check(UnlockBlur.fadedOpacity(200, child) == 0, "Invisible container retains a blur region");
        check(ClockBlurMotion.allows(java.util.Set.of("STRETCH_ANIMATION"), 0), "Stretch clears live blur");
        check(ClockBlurMotion.allows(java.util.Set.of("STRETCH_ANIMATION", "SCREEN_ANIMATION", "DOZE_ACTIVE"), .5f), "Overlapping screen and stretch motion clears blur");
        check(ClockBlurMotion.allows(java.util.Set.of("MEDIA_EXPAND_ANIMATING", "DASHBOARD_ACTIVE"), 0), "Media return clears moving blur");
        check(!ClockBlurMotion.allows(java.util.Set.of("DASHBOARD_ACTIVE"), 0), "Settled dashboard unexpectedly enables native clock blur");
        check(ClockBlurMotion.allows(java.util.Set.of("DASHBOARD_ACTIVE", "FACE_WIDGET_TRANSITIONING"), 0, true),
                "Seconds-view return keeps the dashboard blur blocker");
        check(!ClockBlurMotion.allows(java.util.Set.of("DASHBOARD_ACTIVE", "CONTAINER_NOT_VISIBLE"), 0, true),
                "Seconds-view return restores blur on a hidden clock");
        check(!ClockBlurMotion.allows(java.util.Set.of("DOZE_ACTIVE"), 1), "Settled AOD retains lockscreen blur");
        check(ClockBlurMotion.allows(java.util.Set.of("DOZE_ACTIVE"), 1, false, true),
                "Wallpaper AOD opt-in does not retain clock blur");
        check(!ClockBlurMotion.allows(
                java.util.Set.of("DOZE_ACTIVE", "PRIMARY_BOUNCER_VISIBLE"), 1, false, true),
                "Wallpaper AOD opt-in overrides the bouncer blocker");
        check(ClockBlurMotion.allows(java.util.Set.of("DOZE_ACTIVE"), .99f), "Wake motion cannot recreate blur after settled AOD");
        for (String reason : java.util.List.of("BLUR_EFFECT_DISABLED", "CONTAINER_NOT_VISIBLE", "PRIMARY_BOUNCER_VISIBLE", "SGG_ACTIVE", "QUICK_PANEL_EXPANDED", "UNKNOWN"))
            check(!ClockBlurMotion.allows(java.util.Set.of("STRETCH_ANIMATION", reason), .5f), "Motion overrides visibility or security blocker: " + reason);
        check(!ClockBlurMotion.allows(java.util.Set.of("SCREEN_ANIMATION"), Float.NaN), "Invalid motion progress enables blur");
        var reveal = android.animation.ValueAnimator.ofFloat(0, 1);
        reveal.setDuration(1000);
        int[] completed = {0};
        reveal.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { completed[0]++; }
        });
        reveal.start();
        ClockBlurAlignment.awaitAnimator(clock, reveal);
        check(reveal.isStarted() && completed[0] == 0,
                "Clock transition committed its native endpoint early");
        ClockBlurContinuity.forget(clock);
        reveal.cancel();
        drawable.setAlpha(180);
        Method begin = UnlockBlur.class.getDeclaredMethod("begin"); begin.setAccessible(true);
        Method visible = UnlockBlur.class.getDeclaredMethod("restoreVisible"); visible.setAccessible(true);
        var coverType = Class.forName("dev.denver.dressroomadvanced.UnlockBlur$CoverState");
        var coverCtor = coverType.getDeclaredConstructor(boolean.class, float.class); coverCtor.setAccessible(true);
        Map<View, Object> covers = (Map<View, Object>) field("coverStates").get(null);
        covers.put(clock, coverCtor.newInstance(true, 1f));
        begin.invoke(null);
        covers.put(clock, coverCtor.newInstance(false, 0f));
        widget.setAlpha(1);
        Reflect.set(state, "requested", 0); drawable.setAlpha(0);
        visible.invoke(null);
        check(drawable.getAlpha() == 180, "Cancelled swipe leaves blur background transparent");
        check(((Number) Reflect.field(state, "requested")).intValue() == 180, "Hidden opacity poisons next gesture");
        check(((AttachedView) clock).coverVisible, "Cancelled swipe leaves widget cover hidden");

        android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
        View digit = new View(context); parent.addView(digit);
        parent.layout(0, 0, 400, 800); digit.layout(-20, 30, 80, 130);
        parent.setPivotX(0); parent.setPivotY(0); parent.setScaleX(2); parent.setScaleY(2);
        android.graphics.Path local = new android.graphics.Path();
        local.addRect(0, 0, 10, 20, android.graphics.Path.Direction.CW);
        android.graphics.Path mapped = new android.graphics.Path();
        UnlockBlur.append(mapped, local, digit, new android.graphics.PointF(10, 15), 1);
        android.graphics.RectF bounds = new android.graphics.RectF(); mapped.computeBounds(bounds, true);
        check(bounds.equals(new android.graphics.RectF(-50, 45, -30, 85)), "Clock matte loses ancestor scale or clipped origin");
        local.computeBounds(bounds, true);
        check(bounds.equals(new android.graphics.RectF(0, 0, 10, 20)), "Live mapping mutates cached glyph path");
        mapped.reset(); UnlockBlur.append(mapped, local, digit, new android.graphics.PointF(10, 15), .5f);
        mapped.computeBounds(bounds, true);
        check(bounds.equals(new android.graphics.RectF(-50, 45, -40, 65)), "Weather path gets the wrong view transform");

        // Re-enable after native opacity changed while the global hooks were absent.
        clock.setAlpha(.6f); drawable.setAlpha(120);
        begin.invoke(null);
        check(clocks.get(clock) == .6f, "Re-enable retains a stale clock opacity");
        check(((Number) Reflect.field(state, "requested")).intValue() == 120,
                "Re-enable retains a stale background opacity");
        field("alpha").setFloat(null, .5f); apply.invoke(null);
        java.util.List<Runnable> unhooks = (java.util.List<Runnable>) field("opacityUnhooks").get(null);
        int[] removed = {0};
        unhooks.add(() -> {
            check(clock.getAlpha() == .6f && drawable.getAlpha() == 120,
                    "Opacity hooks removed before native opacity was restored");
            removed[0]++;
        });
        unhooks.add(() -> removed[0]++);
        field("opacityHooksInstalled").setBoolean(null, true);
        Method uninstall = UnlockBlur.class.getDeclaredMethod("uninstallOpacityHooks");
        uninstall.setAccessible(true); uninstall.invoke(null);
        check(removed[0] == 2 && unhooks.isEmpty() && !field("opacityHooksInstalled").getBoolean(null),
                "Disable leaves global opacity hooks installed");
        check(!field("active").getBoolean(null) && !field("tracking").getBoolean(null),
                "Disable leaves a gesture active");
        uninstall.invoke(null);
        check(removed[0] == 2, "Repeated disable unhooks twice");
        // Verify MediaBlurHost + UnlockBlur compose cleanly without poisoning each other's baselines.
        View sharedHost = new AttachedView(context);
        sharedHost.setAlpha(.8f);
        MediaBlurHost.fade(sharedHost, .25f);
        clocks.put(sharedHost, MediaBlurHost.requestedAlpha(sharedHost));
        check(Math.abs(clocks.get(sharedHost) - .8f) < .00001f,
                "UnlockBlur captured MediaBlurHost's held opacity as native baseline");
        field("active").setBoolean(null, true);
        field("alpha").setFloat(null, .5f);
        apply.invoke(null);
        check(Math.abs(sharedHost.getAlpha() - .1f) < .00001f,
                "UnlockBlur overwrote MediaBlurHost factor during swipe");
        check(Math.abs(MediaBlurHost.requestedAlpha(sharedHost) - .8f) < .00001f,
                "UnlockBlur internal write poisoned MediaBlurHost nativeAlpha");
        float composed = ViewAlphaHooks.dispatch(sharedHost, .6f);
        check(Math.abs(composed - .075f) < .00001f,
                "Shared View.setAlpha dispatch failed to compose media and unlock factors");
        check(Math.abs(clocks.get(sharedHost) - .6f) < .00001f
                        && Math.abs(MediaBlurHost.requestedAlpha(sharedHost) - .6f) < .00001f,
                "Native View.setAlpha write corrupted either baseline");
        MediaBlurHost.release(sharedHost);
        check(Math.abs(sharedHost.getAlpha() - .3f) < .00001f,
                "Releasing MediaBlurHost during active swipe skipped UnlockBlur factor");
        field("active").setBoolean(null, false);
        restore.invoke(null);
        check(Math.abs(sharedHost.getAlpha() - .6f) < .00001f,
                "Completed swipe failed to restore latest native alpha");
        covers.clear();
        clocks.clear(); backgrounds.clear();
    }

    private static final class AttachedView extends View {
        boolean coverVisible;
        AttachedView(Context context) { super(context); }
        @Override public boolean isAttachedToWindow() { return true; }
        public void setCoverWidgetBlurState(boolean visible, float alpha) { coverVisible = visible; }
    }
    enum Reason { STRETCH_ANIMATION, SCREEN_ANIMATION, DOZE_ACTIVE }
}
