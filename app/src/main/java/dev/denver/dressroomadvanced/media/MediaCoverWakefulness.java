package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class MediaCoverWakefulness {
    private static final Set<LiveCoverController> covers =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean installed;
    private static int wakefulness = -1;
    static boolean sleeping;
    static boolean panelDozing;
    private static final class WakePills {
        static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
        static final Runnable RESTORE = () -> {
            if (wakefulness == 2) BlurAppearance.rebindAwakePills();
        };
    }

    private MediaCoverWakefulness() {}

    static void add(LiveCoverController cover) {
        covers.add(cover);
    }

    static void remove(LiveCoverController cover) {
        covers.remove(cover);
    }

    static void install(DressroomModule module, ClassLoader loader) {
        if (installed) return;
        installPanelDoze(module, loader);
        try {
            Class<?> lifecycle = Class.forName(
                    "com.android.systemui.keyguard.WakefulnessLifecycle", false, loader);
            Method current = NativeContract.method(lifecycle, "getWakefulness", int.class);
            Method dispatch = NativeContract.method(lifecycle.getSuperclass(), "dispatch",
                    void.class, Consumer.class);
            module.hook(dispatch).setId("dressroom-advanced.media-cover-wakefulness")
                    .intercept(chain -> {
                        boolean restorePills = false;
                        if (lifecycle.isInstance(chain.getThisObject())) {
                            try {
                                int state = (Integer) current.invoke(chain.getThisObject());
                                if (state != wakefulness && PlatformGate.oneUiVersion() >= 90500) {
                                    WakePills.MAIN.removeCallbacks(WakePills.RESTORE);
                                    if (state == 3) BlurAppearance.captureAwakePills();
                                    restorePills = state == 2 && wakefulness >= 0;
                                }
                                wakefulness = state;
                                // WakefulnessLifecycle assigns its state before notifying SystemUI.
                                // 0 is asleep, 3 is going to sleep.
                                boolean next = state == 0 || state == 3;
                                if (next != sleeping) {
                                    sleeping = next;
                                    for (var cover : new ArrayList<>(covers))
                                        cover.wakefulnessChanged(next);
                                }
                            } catch (ReflectiveOperationException | RuntimeException error) {
                                DressroomModule.note("Media cover wakefulness update", error);
                            }
                        }
                        Object result = chain.proceed();
                        if (restorePills) schedulePillRestore();
                        return result;
                    });
            module.deoptimize(dispatch);
            installed = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            DressroomModule.note("Media cover wakefulness binding", unavailable);
        }
    }

    private static void installPanelDoze(DressroomModule module, ClassLoader loader) {
        try {
            Class<?> panel = Class.forName(
                    "com.android.systemui.shade.NotificationPanelViewController", false, loader);
            Method setDozing = NativeContract.method(panel, "setDozing",
                    void.class, boolean.class, boolean.class);
            module.hook(setDozing).setId("dressroom-advanced.media-cover-panel-doze")
                    .intercept(chain -> {
                        boolean dozing = Boolean.TRUE.equals(chain.getArg(0));
                        boolean wasDozing = panelDozing;
                        if (panelDozing != dozing) {
                            panelDozing = dozing;
                            for (var cover : new ArrayList<>(covers)) {
                                try {
                                    cover.wakefulnessChanged(dozing);
                                } catch (RuntimeException error) {
                                    DressroomModule.note("Media artwork panel doze", error);
                                }
                            }
                        }
                        Object result = chain.proceed();
                        if (wasDozing && !dozing && wakefulness == 2
                                && PlatformGate.oneUiVersion() >= 90500) schedulePillRestore();
                        return result;
                    });
            module.deoptimize(setDozing);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            DressroomModule.note("Optional media cover panel doze", unavailable);
        }
    }

    private static void schedulePillRestore() {
        WakePills.MAIN.removeCallbacks(WakePills.RESTORE);
        // Let the native tint fade finish before rebinding the hardware region.
        WakePills.MAIN.postDelayed(WakePills.RESTORE, 420L);
    }
}
