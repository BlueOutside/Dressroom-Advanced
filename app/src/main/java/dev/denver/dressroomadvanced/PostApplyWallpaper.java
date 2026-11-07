package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/** Samsung's save acknowledgement can precede its wallpaper-ID update. */
final class PostApplyWallpaper {
    interface Scheduler { void post(Runnable task, long delay); }
    private final Scheduler scheduler;
    private final LongSupplier time;
    private final IntUnaryOperator wallpaper;
    private final BiConsumer<String, Bundle> write;
    private final Map<String, Request> requests = new HashMap<>();

    PostApplyWallpaper(Scheduler scheduler, LongSupplier time, IntUnaryOperator wallpaper,
            BiConsumer<String, Bundle> write) {
        this.scheduler = scheduler;
        this.time = time;
        this.wallpaper = wallpaper;
        this.write = write;
    }

    synchronized void follow(String method, Bundle committed) {
        if (!(method.equals("commit") || method.equals("commitWidgets"))
                || ScreenIdentity.value(committed) == 0) return;
        Bundle frozen = new Bundle(committed);
        String key = method + ":" + ScreenIdentity.draft(frozen) + ":"
                + frozen.getInt("type", -1) + ":" + frozen.getInt("font", -1);
        Request request = new Request(key, method, frozen, time.getAsLong() + 5000);
        requests.put(key, request);
        scheduler.post(request, 250);
    }

    private final class Request implements Runnable {
        final String key, method;
        final long deadline;
        Bundle saved;
        Request(String key, String method, Bundle saved, long deadline) {
            this.key = key; this.method = method; this.saved = saved; this.deadline = deadline;
        }
        public void run() {
            synchronized (PostApplyWallpaper.this) { check(); }
        }
        private void check() {
            if (requests.get(key) != this) return;
            long now = time.getAsLong();
            if (now > deadline) { requests.remove(key); return; }
            try {
                int id = wallpaper.applyAsInt(ScreenIdentity.value(saved));
                if (id >= 0 && id != saved.getInt("wallpaperId", -1)) {
                    Bundle rebound = new Bundle(saved);
                    rebound.putInt("wallpaperId", id);
                    write.accept(method, rebound);
                    saved = rebound;
                }
            } catch (RuntimeException error) {
                android.util.Log.w(DressroomModule.TAG, "Post-Apply wallpaper rebind deferred", error);
            }
            if (now < deadline) scheduler.post(this, Math.min(250, deadline - now));
            else requests.remove(key);
        }
    }
}
