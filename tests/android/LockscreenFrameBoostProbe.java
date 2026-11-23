package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.util.Map;

public final class LockscreenFrameBoostProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object field(String name) throws Exception {
        var field = LockscreenFrameBoost.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    public static void main(String[] args) {
        try {
            run();
            System.out.println("Lockscreen frame boost: " + checks + " checks passed");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Context context = (Context) thread.getMethod("getSystemContext")
                .invoke(thread.getMethod("systemMain").invoke(null));
        Target root = new Target(context), child = new Target(context);
        child.root = root;
        child.token = root.token;
        Map<View, Long> deadlines = (Map<View, Long>) field("deadlines");
        Map<?, ?> roots = (Map<?, ?>) field("roots");
        LockscreenFrameBoost.pulse(child, 500);
        check(child.rate == View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                && root.rate == child.rate, "Animation and root are not both boosted");

        // Existing boost still has more than half a pulse left: it must nevertheless extend.
        long before = SystemClock.uptimeMillis();
        deadlines.put(child, before + 300);
        deadlines.put(root, before + 300);
        LockscreenFrameBoost.pulse(child, 500);
        check(deadlines.get(child) >= before + 500 && deadlines.get(root) >= before + 500,
                "Repeated pulse shortens the requested animation tail");
        check(child.walks == 1, "Repeated pulse walks the same hierarchy");
        check(child.requests == 1 && root.requests == 1, "Repeated pulse resubmits the frame rate");

        Target replacement = new Target(context);
        child.root = replacement; child.token = replacement.token;
        LockscreenFrameBoost.pulse(child, 500);
        check(child.walks == 2 && deadlines.containsKey(replacement),
                "Reattachment uses the previous window root");
        long longer = deadlines.get(child);
        LockscreenFrameBoost.pulse(child, 60);
        check(deadlines.get(child) >= longer, "Short pulse truncates a longer boost");

        var sweep = LockscreenFrameBoost.class.getDeclaredMethod("sweepExpired");
        sweep.setAccessible(true);
        deadlines.replaceAll((view, deadline) -> 0L);
        sweep.invoke(null);
        check(deadlines.isEmpty() && roots.isEmpty(), "Expired boost retains its root cache");
        check(Float.compare(child.rate, View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT) == 0
                && Float.compare(root.rate, child.rate) == 0
                && Float.compare(replacement.rate, child.rate) == 0,
                "Expired animation does not restore adaptive refresh");
        LockscreenFrameBoost.pulse(child, 500);
        child.attached = replacement.attached = false;
        sweep.invoke(null);
        check(deadlines.isEmpty() && roots.isEmpty(), "Detached views retain boosts or cached roots");
    }

    private static final class Target extends View {
        Target root = this;
        IBinder token = new Binder();
        boolean attached = true;
        int walks, requests;
        float rate;

        Target(Context context) { super(context); }
        @Override public boolean isAttachedToWindow() { return attached; }
        @Override public IBinder getWindowToken() { return token; }
        @Override public View getRootView() { walks++; return root; }
        @Override public void setRequestedFrameRate(float rate) { this.rate = rate; requests++; }
    }
}
