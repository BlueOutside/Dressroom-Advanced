package dev.denver.dressroomadvanced;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class EditorClockRefresh {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<View, Runnable> queued =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EditorClockRefresh() {}

    static void cancelAll() {
        synchronized (queued) {
            for (Runnable task : queued.values()) main.removeCallbacks(task);
            queued.clear();
        }
    }

    static void cancel(View clock) {
        Runnable old = queued.remove(clock);
        if (old != null) main.removeCallbacks(old);
    }

    static void request(View clock) {
        request(clock, null);
    }

    static void request(View clock, Runnable afterDraw) {
        cancel(clock);
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        if (queued.get(clock) != this) return;
                        if (!clock.isAttachedToWindow()) {
                            queued.remove(clock, this);
                            return;
                        }
                        DateEffects.apply(clock);
                        clock.requestLayout();
                        clock.invalidate();
                        clock.postOnAnimation(
                                () -> {
                                    if (!queued.remove(clock, this) || !clock.isAttachedToWindow()) return;
                                    try {
                                        Reflect.call(clock, "setClockTextBlur");
                                    } catch (Exception error) {
                                        android.util.Log.w(
                                                DressroomModule.TAG,
                                                "Native blur refresh unavailable",
                                                error);
                                    }
                                    if (afterDraw != null && !queued.containsKey(clock))
                                        afterDraw.run();
                                });
                    }
                };
        queued.put(clock, task);
        main.post(task);
    }
}
