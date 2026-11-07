package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public final class EditorScrollProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static void layout(FrameLayout root, int height) {
        for (int i = 0; i < 3; i++) {
            root.measure(
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 400, height);
        }
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

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        FrameLayout root = new FrameLayout(context);
        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        root.addView(sheet, new FrameLayout.LayoutParams(400, 300));
        sheet.addView(new View(context), new LinearLayout.LayoutParams(-1, 40));
        EditorScrollView scroll = new EditorScrollView(context);
        scroll.setSmoothScrollingEnabled(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 260));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int base = Math.round(16 * context.getResources().getDisplayMetrics().density);
        content.setPadding(0, 0, 0, base);
        scroll.addView(content);
        content.addView(new View(context), new LinearLayout.LayoutParams(-1, 700));
        View last = new View(context);
        content.addView(last, new LinearLayout.LayoutParams(-1, 40));
        layout(root, 200);
        Rect visible = new Rect();
        check(scroll.getLocalVisibleRect(visible), "Viewport not visible");
        check(visible.height() == 160, "Fixture must clip 100 pixels of the measured viewport");
        check(scroll.getPaddingBottom() == 100, "Scroll range omitted parent clipping");
        scroll.fullScroll(View.FOCUS_DOWN);
        Rect row = new Rect();
        boolean shown = last.getGlobalVisibleRect(row);
        check(
                shown && row.height() == 40,
                "Last row remains clipped at maximum scroll: row="
                        + row
                        + " scroll="
                        + scroll.getScrollY()
                        + " content="
                        + content.getHeight()
                        + " padding="
                        + content.getPaddingBottom()
                        + " last="
                        + last.getTop()
                        + "/"
                        + last.getBottom());
        check(row.bottom <= 200 - base, "Last row has no visible bottom spacing");
        scroll.requestLayout();
        layout(root, 200);
        check(scroll.getPaddingBottom() == 100, "Scrolling changed the clipping inset");
        scroll.requestLayout();
        layout(root, 400);
        check(
                scroll.getPaddingBottom() == 0,
                "Extra scroll range remained after the parent expanded");
        scroll.requestLayout();
        layout(root, 200);
        check(scroll.getPaddingBottom() == 100, "Repeated sheet resizing accumulated padding");
        System.out.println("Editor clipped viewport: " + checks + " checks passed");
    }
}
