package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Looper;
import android.view.View;

public final class RootLayoutWatchProbe {
    private static int checks;

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
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
        View first = new View(context), second = new View(context);
        int[] callbacks = {0};
        View.OnLayoutChangeListener listener = (v, l, t, r, b, ol, ot, or, ob) -> callbacks[0]++;
        RootLayoutWatch watch = new RootLayoutWatch();
        watch.bind(first, listener);
        first.layout(0, 0, 10, 10);
        check(callbacks[0] == 1, "Initial root not watched");
        watch.bind(first, listener);
        first.layout(0, 0, 20, 20);
        check(callbacks[0] == 2, "Rebinding pooled root duplicated callbacks");
        watch.bind(second, listener);
        first.layout(0, 0, 30, 30);
        check(callbacks[0] == 2, "Replaced root still watched");
        second.layout(0, 0, 10, 10);
        check(callbacks[0] == 3, "Replacement root not watched");
        watch.clear();
        watch.clear();
        second.layout(0, 0, 20, 20);
        check(callbacks[0] == 3, "Released controller retained a root listener");
        System.out.println("Widget root listener lifecycle: " + checks + " checks passed");
    }
}
