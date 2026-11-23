package dev.denver.dressroomadvanced;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pins active lockscreen animation views and their window root to
 * {@link View#REQUESTED_FRAME_RATE_CATEGORY_HIGH} (120Hz on LTPO panels) on Android 15+ while
 * motion is in flight, releasing back to {@link View#REQUESTED_FRAME_RATE_CATEGORY_DEFAULT} once
 * motion settles so idle lockscreen and AOD VRR power savings remain untouched.
 */
final class LockscreenFrameBoost {
    static final long DEFAULT_PULSE_MS = 180L;
    private static final long SWEEP_GRACE_MS = 32L;
    private static final long MIN_SWEEP_DELAY_MS = 48L;

    private static final WeakHashMap<View, Long> deadlines = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<View>> roots = new WeakHashMap<>();
    private static Handler mainHandler;
    private static boolean sweepScheduled;

    private static final Runnable SWEEP = LockscreenFrameBoost::sweepExpired;

    private LockscreenFrameBoost() {}

    static void pulse(View view) {
        pulse(view, DEFAULT_PULSE_MS);
    }

    static void pulse(View view, long durationMs) {
        if (Build.VERSION.SDK_INT < 35 || view == null) return;
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null || Looper.myLooper() != mainLooper) return;
        if (!view.isAttachedToWindow()) return;

        long now = SystemClock.uptimeMillis();
        long deadline = now + Math.max(60L, durationMs);
        boostTarget(view, deadline);
        // Cache only the hierarchy walk; every pulse still extends both deadlines.
        WeakReference<View> cached = roots.get(view);
        View root = cached == null ? null : cached.get();
        android.os.IBinder token = view.getWindowToken();
        if (root == null || token == null || !root.isAttachedToWindow()
                || root.getWindowToken() != token) {
            root = view.getRootView();
            if (root != null) roots.put(view, new WeakReference<>(root));
        }
        if (root != null && root != view && root.isAttachedToWindow()) {
            boostTarget(root, deadline);
        }
        scheduleSweep(Math.max(MIN_SWEEP_DELAY_MS, durationMs + SWEEP_GRACE_MS));
    }

    private static void boostTarget(View target, long deadline) {
        Long existing = deadlines.get(target);
        if (existing == null) {
            deadlines.put(target, deadline);
            target.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
        } else if (deadline > existing) {
            deadlines.put(target, deadline);
        }
    }

    private static void scheduleSweep(long delayMs) {
        if (sweepScheduled) return;
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        sweepScheduled = true;
        mainHandler.postDelayed(SWEEP, delayMs);
    }

    private static void sweepExpired() {
        sweepScheduled = false;
        if (Build.VERSION.SDK_INT < 35 || deadlines.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        long earliestRemaining = Long.MAX_VALUE;
        Iterator<Map.Entry<View, Long>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<View, Long> entry = it.next();
            View target = entry.getKey();
            long deadline = entry.getValue();
            if (target == null || !target.isAttachedToWindow() || now >= deadline) {
                it.remove();
                if (target != null) {
                    roots.remove(target);
                    target.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
                }
            } else {
                long remaining = deadline - now + SWEEP_GRACE_MS;
                if (remaining < earliestRemaining) {
                    earliestRemaining = remaining;
                }
            }
        }
        if (!deadlines.isEmpty()) {
            scheduleSweep(Math.max(MIN_SWEEP_DELAY_MS, earliestRemaining));
        }
    }
}
