package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

final class ClockBlurDelays {
    static final String KEY = "shortClockBlurDelay";
    private static final Set<String> shortened = Set.of("AlphaChange", "StretchAnimation",
            "MediaExpand", "ShortcutSwiping", "OverlaySwiping");
    private static final Set<Class<?>> installed =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static Context context;

    private ClockBlurDelays() {}

    static synchronized void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        if (module.inEditorProcess()) return;
        String anchor = ": Scheduled deferred update (delay=";
        var bodies = DexSignatures.find(loader, Set.of(anchor)).stream().filter(body ->
                body.anchors().containsKey(anchor) && body.matches(anchor)
                        && body.calls().stream().anyMatch(call ->
                            call.owner().equals("Landroid/os/Handler;")
                            && call.name().equals("postDelayed")
                            && call.parameters().equals(List.of("Ljava/lang/Runnable;", "J"))))
                .toList();
        if (bodies.size() != 1) return;
        Method schedule = bodies.get(0).method(loader);
        Class<?> owner = schedule.getDeclaringClass();
        if (installed.contains(owner)) return;
        Field handler = NativeContract.findField(owner, null, Handler.class);
        Field tag = NativeContract.findField(owner, null, String.class);
        Field callback = NativeContract.findField(owner, null, Runnable.class);
        module.hook(schedule).setId("dressroom-advanced.short-clock-blur-delay")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object debouncer = chain.getThisObject();
                    try {
                        Handler target = (Handler) handler.get(debouncer);
                        if (target == null || Looper.myLooper() != target.getLooper()
                                || !shortened.contains((String) tag.get(debouncer))
                                || !enabled()) return result;
                        Runnable pending = (Runnable) callback.get(debouncer);
                        if (pending != null) {
                            // Keep the native callback and cancellation handle.
                            target.removeCallbacks(pending);
                            target.postDelayed(pending, 16);
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                    return result;
                });
        module.deoptimize(schedule);
        installed.add(owner);
    }

    private static boolean enabled() throws ReflectiveOperationException {
        if (context == null)
            context = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        return context != null && RuntimeExtras.enabled(context, KEY);
    }
}
