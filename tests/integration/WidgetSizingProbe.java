package dev.denver.dressroomadvanced;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.util.function.Consumer;

/** Regression for Samsung's outer-container callback and temporary preview host IDs. */
public final class WidgetSizingProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public record Result(int getDbWidgetId, View getWidgetView) {}
    public record Grid(View child) {
        public void forEachWidgetView(Consumer<View> consumer) { consumer.accept(child); }
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> activity = Class.forName("android.app.ActivityThread");
        Context context = (Context) activity.getMethod("getSystemContext").invoke(activity.getMethod("systemMain").invoke(null));
        FrameLayout outer = new FrameLayout(context), background = new FrameLayout(context);
        AppWidgetHostView host = new AppWidgetHostView(context); host.setAppWidget(91, null);
        outer.addView(background); background.addView(host);
        var found = WidgetHostSizing.attached(new Grid(outer));
        check(found.get(91) == host, "Nested host skipped by outer-container callback");
        check(found.size() == 1, "Host traversal duplicated native identity");
        WidgetHostSizing.created(new Result(12, outer));
        found = WidgetHostSizing.attached(new Grid(outer));
        check(found.get(12) == host, "Saved widget ID does not resolve to temporary preview host");
        check(found.get(91) == host, "Actual host ID was lost while tracking saved identity");
        check(host.getAppWidgetId() == 91 && host.getParent() == background, "Discovery changed host ownership");
        check(WidgetHostSizing.inView(new FrameLayout(context)).isEmpty(), "Empty preview allocated a host");
        System.out.println("Widget nested-host discovery and preview identity: " + checks + " checks passed");
        System.exit(0);
    }
}
