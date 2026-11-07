package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.lang.reflect.Method;

/** Fold screen identity is independent of Android display number and orientation. */
final class ScreenIdentity {
    static final String FIELD = "layoutScreen";
    static final int MAIN = 1, SUB = 2;
    private static Method folded;
    private static Object manager;
    private static Object listener;
    private static volatile int screen;
    private static Boolean foldable;
    private static final java.util.List<Runnable> observers = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ScreenIdentity() {}

    static boolean inspectedFold8() {
        return android.os.Build.VERSION.SDK_INT == 37
                && "SM-F971B".equals(android.os.Build.MODEL);
    }

    static synchronized boolean supported() {
        if (foldable != null) return foldable;
        if (inspectedFold8()) return foldable = true;
        if (android.os.Build.VERSION.SDK_INT < 36 || android.os.Build.VERSION.SDK_INT > 37)
            return foldable = false;
        try {
            Class<?> features = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object owner = features.getMethod("getInstance").invoke(null);
            String policy = (String) features.getMethod("getString", String.class)
                    .invoke(owner, "SEC_FLOATING_FEATURE_LOCKSCREEN_CONFIG_SUBDISPLAY_POLICY");
            return foldable = policy != null && policy.contains("LOCKSCREEN");
        } catch (ReflectiveOperationException | RuntimeException ignored) { return foldable = false; }
    }

    static int wallpaperWhich(int screen, boolean lock) {
        return (lock ? 2 : 1) | (screen == SUB ? 16 : screen == MAIN ? 4 : 0);
    }

    static synchronized int current() {
        if (!supported()) return 0;
        try {
            if (folded == null) {
                Class<?> type = Class.forName("com.samsung.android.view.SemWindowManager");
                manager = type.getMethod("getInstance").invoke(null);
                folded = type.getMethod("isFolded");
                screen = Boolean.TRUE.equals(folded.invoke(manager)) ? SUB : MAIN;
                Class<?> callback = Class.forName("com.samsung.android.view.SemWindowManager$FoldStateListener");
                listener = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(),
                        new Class<?>[] {callback}, (p, method, args) -> {
                            if (method.getName().equals("onFoldStateChanged")) {
                                int next = Boolean.TRUE.equals(args[0]) ? SUB : MAIN;
                                if (screen != next) {
                                    screen = next;
                                    for (Runnable observer : observers) observer.run();
                                }
                            } else if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                            else if (method.getName().equals("equals")) return p == args[0];
                            else if (method.getName().equals("toString")) return "DressroomFoldListener";
                            return null;
                        });
                type.getMethod("registerFoldStateListener", callback, android.os.Handler.class)
                        .invoke(manager, listener, new android.os.Handler(android.os.Looper.getMainLooper()));
            }
            return screen;
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Fold screen identity unavailable", error);
        }
    }

    static void observe(Runnable observer) {
        if (supported()) { current(); observers.add(observer); }
    }

    static int value(Bundle data) {
        int value = data.getInt(FIELD);
        return value == MAIN || value == SUB ? value : 0;
    }

    static void stamp(Bundle data) {
        if (!data.containsKey(FIELD)) {
            int screen = current();
            if (screen != 0) data.putInt(FIELD, screen);
        }
    }

    static void copy(Bundle source, Bundle target) {
        if (source.containsKey(FIELD)) target.putInt(FIELD, value(source));
    }

    static String key(String base, Bundle data) {
        return switch (value(data)) {
            case MAIN -> base + "_fold_main";
            case SUB -> base + "_fold_sub";
            default -> base;
        };
    }

    static String draft(Bundle data) {
        return data.getInt(LayoutOrientation.FIELD) + ":" + value(data);
    }
}
