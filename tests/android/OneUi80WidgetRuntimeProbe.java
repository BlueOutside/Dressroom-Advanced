package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

public final class OneUi80WidgetRuntimeProbe {
    private static int checks;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
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
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context context = (Context) at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        FrameLayout screen = new FrameLayout(context), wrapper = new FrameLayout(context);
        View first = new View(context), second = new View(context), decoration = new View(context);
        first.setTag(true); second.setTag(true);
        screen.addView(wrapper); wrapper.addView(first); wrapper.addView(second); wrapper.addView(decoration);
        screen.layout(0, 0, 1000, 1600); wrapper.layout(0, 0, 1000, 600);
        first.layout(100, 40, 300, 140); second.layout(400, 40, 600, 140);
        decoration.layout(0, 0, 1000, 600);
        var hosts = (java.util.function.Predicate<View>) view -> Boolean.TRUE.equals(view.getTag());
        RectF bounds = WidgetPlacement.occupiedBounds(wrapper, new Matrix(), hosts);
        check(bounds.equals(new RectF(100, 40, 600, 140)), "Full-width wrapper/decoration changed widget bounds");
        wrapper.setTranslationX(30); wrapper.setTranslationY(60);
        bounds = WidgetPlacement.occupiedBounds(wrapper, new Matrix(), hosts);
        check(bounds.equals(new RectF(130, 100, 630, 200)), "Native group translation was lost");
        wrapper.setPivotX(0); wrapper.setPivotY(0); wrapper.setScaleX(.5f);
        bounds = WidgetPlacement.occupiedBounds(wrapper, new Matrix(), hosts);
        check(Math.abs(bounds.width()-250) < .01f, "Scaled widget width used wrapper width");
        second.setVisibility(View.GONE);
        bounds = WidgetPlacement.occupiedBounds(wrapper, new Matrix(), hosts);
        check(Math.abs(bounds.width()-100) < .01f, "Hidden widget displaced the group");
        first.setAlpha(0);
        check(WidgetPlacement.occupiedBounds(wrapper, new Matrix(), hosts).isEmpty(), "Hidden group retained visible bounds");
        Provider provider = new Provider();
        Bundle identity = OneUi80WidgetRuntime.clockIdentity(provider);
        check(identity.getInt("type") == 3 && identity.getInt("font") == 803,
                "Widget profile used the stale serialized preset instead of the renderer font");
        check("/fonts/custom.ttf".equals(identity.getString("fontPath")), "File-font identity was discarded");
        provider.font = new Font(804, "");
        check(OneUi80WidgetRuntime.clockIdentity(provider).getInt("font") == 804,
                "Font-flow replacement retained the old profile identity");
        provider.font = null;
        check(OneUi80WidgetRuntime.clockIdentity(provider).getInt("font") == 0,
                "Unset runtime font failed its default identity");
        System.out.println("8.0 widget geometry/profile: " + checks + " checks passed");
    }
    public static final class Provider {
        public final int stalePreset = 0;
        public Font font = new Font(803, "/fonts/custom.ttf");
        public int getClockTypeInternal() { return 3; }
        public Font getClockFontData() { return font; }
    }
    public record Font(int value, String path) {
        public int getFontType() { return value; }
        public String getFontPath() { return path; }
    }
}
