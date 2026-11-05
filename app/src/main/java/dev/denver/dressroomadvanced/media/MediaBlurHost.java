package dev.denver.dressroomadvanced;

import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

final class MediaBlurHost {
    private static final class Opacity {
        float nativeAlpha, factor;

        Opacity(float alpha) {
            nativeAlpha = alpha;
        }
    }

    private static final Map<View, Opacity> held = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static boolean installed;

    static synchronized void install(DressroomModule module) throws ReflectiveOperationException {
        if (installed) return;
        java.lang.reflect.Method alpha = View.class.getDeclaredMethod("setAlpha", float.class);
        module.hook(alpha)
                .setId("dressroom-advanced.live-clock-blur-host")
                .intercept(
                        chain -> {
                            View view = (View) chain.getThisObject();
                            float requested = ((Number) chain.getArg(0)).floatValue();
                            float actual = nativeAlpha(view, requested);
                            return actual == requested
                                    ? chain.proceed()
                                    : chain.proceed(new Object[] {actual});
                        });
        module.deoptimize(alpha);
        installed = true;
    }

    static float nativeAlpha(View view, float value) {
        Opacity state = held.get(view);
        if (!Boolean.TRUE.equals(writing.get()) && state != null) {
            state.nativeAlpha = value;
            return value * state.factor;
        }
        return value;
    }

    static float requestedAlpha(View view) {
        Opacity state = held.get(view);
        return state == null ? view.getAlpha() : state.nativeAlpha;
    }

    static void hold(View view) {
        fade(view, 0f);
    }

    static void fade(View view, float factor) {
        Opacity state = held.get(view);
        if (state == null) {
            state = new Opacity(view.getAlpha());
            held.put(view, state);
        }
        state.factor = Math.max(0f, Math.min(1f, factor));
        float alpha = state.nativeAlpha * state.factor;
        if (view.getAlpha() != alpha) write(view, alpha);
    }

    static void release(View view) {
        Opacity state = held.remove(view);
        if (state != null) write(view, state.nativeAlpha);
    }

    private static void write(View view, float alpha) {
        Boolean previous = writing.get();
        writing.set(true);
        try {
            view.setAlpha(alpha);
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }
}
