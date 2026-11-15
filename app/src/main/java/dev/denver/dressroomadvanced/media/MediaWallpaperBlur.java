package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** The legacy wallpaper blur must not sample the replacement album artwork. */
final class MediaWallpaperBlur {
    static final class Hold {
        Object nativeInfo;
        boolean held;

        Object request(Object info) {
            nativeInfo = info;
            return held ? null : info;
        }

        boolean hold() {
            if (held) return false;
            held = true;
            return true;
        }

        boolean release() {
            if (!held) return false;
            held = false;
            return true;
        }
    }

    private static final Map<View, Hold> states = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> hosts = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static Method setter;

    static synchronized void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> host = Class.forName(
                "com.samsung.android.app.facewidget.SecKeyguardStatusView", false, loader);
        if (setter == null) {
            Method method = View.class.getDeclaredMethod(
                    "semSetBlurInfo", Class.forName("android.view.SemBlurInfo"));
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(module.hook(method)
                    .setId("dressroom-advanced.85-artwork-wallpaper-blur")
                    .intercept(chain -> {
                        View view = (View) chain.getThisObject();
                        if (Boolean.TRUE.equals(writing.get()) || !hosts.contains(view.getClass()))
                            return chain.proceed();
                        Object requested = chain.getArg(0);
                        Object displayed = states.computeIfAbsent(view, ignored -> new Hold())
                                .request(requested);
                        return displayed == requested ? chain.proceed()
                                : chain.proceed(new Object[] {displayed});
                    })::unhook);
                module.deoptimize(method);
                setter = method;
                hooks.commit();
            }
        }
        hosts.add(host);
    }

    static void hold(View view) {
        if (view == null || setter == null || !hosts.contains(view.getClass())) return;
        Hold state = states.computeIfAbsent(view, ignored -> new Hold());
        if (state.hold() && state.nativeInfo != null) write(view, null);
    }

    static void release(View view, float alpha) {
        Hold state = states.get(view);
        if (state == null || !state.release() || state.nativeInfo == null) return;
        write(view, state.nativeInfo);
        // Native fade callbacks cannot write an absent blur drawable while held.
        // Restore its current fade position, not a fully opaque background.
        if (view.getBackground() != null)
            view.getBackground().setAlpha(Math.round(Math.max(0, Math.min(1, alpha)) * 255));
    }

    private static void write(View view, Object info) {
        Boolean previous = writing.get();
        writing.set(true);
        try {
            setter.invoke(view, info);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Legacy artwork wallpaper blur unavailable", error);
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }
}
