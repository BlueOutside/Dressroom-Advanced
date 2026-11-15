package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Looper;
import android.view.View;

import java.util.ArrayDeque;

public final class DateRefreshProbe {
    public static final class Clock extends View {
        boolean attached, reject;
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        Clock(Context context) {
            super(context);
        }

        public int getClockType() {
            return 2;
        }

        @Override
        public boolean isAttachedToWindow() {
            return attached;
        }

        @Override
        public boolean post(Runnable task) {
            if (reject) return false;
            queue.add(task);
            return true;
        }
    }

    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        Context context = (Context) type.getMethod("getSystemContext").invoke(thread);
        Clock clock = new Clock(context);
        for (int i = 0; i < 1000; i++) DateEffects.deferUpdate(clock);
        check(clock.queue.isEmpty(), "Detached measurement clock retained callbacks");
        clock.attached = true;
        for (int i = 0; i < 1000; i++) DateEffects.deferUpdate(clock);
        check(clock.queue.size() == 1, "Attached clock queued duplicate callbacks");
        clock.queue.remove().run();
        DateEffects.deferUpdate(clock);
        check(clock.queue.size() == 1, "Completed refresh blocked its next update");
        clock.attached = false;
        clock.queue.remove().run();
        clock.attached = true;
        clock.reject = true;
        DateEffects.deferUpdate(clock);
        check(clock.queue.isEmpty(), "Rejected post was queued");
        clock.reject = false;
        DateEffects.deferUpdate(clock);
        check(clock.queue.size() == 1, "Rejected scheduling left a stale pending flag");
        clock.queue.remove().run();
        System.out.println("Date refresh lifecycle: " + checks + " checks passed");
        System.exit(0);
    }
}
