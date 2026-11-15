package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class MediaWidgetFade {
    static final String KEY = "fadeWidgetsWhenOpeningMedia";
    private static final String WIDGETS = "com.samsung.android.app.facewidget.complication.";
    private static final Map<View, Fade> fades = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<View> clearing = new ThreadLocal<>();
    private static final Set<Class<?>> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private MediaWidgetFade() {}

    private record Fade(View surface, int alpha, ValueAnimator animator) {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess()) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> owner = Class.forName(WIDGETS + "ComplicationWidgetHostViewContainer", false, loader);
            if (installed.contains(owner)) return;
            Class<?> manager = Class.forName(WIDGETS + "BlurEffectManager", false, loader);
            Method getBlur = NativeContract.method(owner, "getBlurView", View.class);
            Method hostAlpha = NativeContract.method(owner, "updateHostViewAlpha", void.class, float.class);
            Method expand = null;
            for (Method candidate : manager.getDeclaredMethods()) {
                if (candidate.getName().startsWith("setMusicExpandState$lambda$")
                        && candidate.getReturnType() == void.class
                        && Arrays.equals(candidate.getParameterTypes(),
                                new Class<?>[] {boolean.class, manager, View.class})
                        && DexSignatures.body(candidate).calls().stream()
                                .anyMatch(call -> call.name().equals("semSetBlurInfo"))) {
                    expand = candidate;
                    break;
                }
            }
            if (expand == null) return;
            Class<?> observer = Class.forName(WIDGETS + "WidgetObserverManager", false, loader);
            Method applyAlpha = null;
            for (Method candidate : observer.getDeclaredMethods()) {
                if (candidate.getName().startsWith("mediaExpandStateObserver$lambda$")
                        && Arrays.equals(candidate.getParameterTypes(),
                                new Class<?>[] {float.class, owner})) {
                    applyAlpha = candidate;
                    break;
                }
            }
            if (applyAlpha == null) return;
            hooks.add(module.hook(expand).setId("dressroom-advanced.media-widget-blur-handoff")
                    .intercept(chain -> {
                        View widget = (View) chain.getArg(2);
                        if (!owner.isInstance(widget)) return chain.proceed();
                        stop(widget);
                        if (!Boolean.TRUE.equals(chain.getArg(0)) || !widget.isShown()
                                || widget.getAlpha() <= 0f
                                || !RuntimeExtras.enabled(widget.getContext(), KEY)
                                || !BlurAppearance.liveWindowBlurAllowed(widget.getContext()))
                            return chain.proceed();
                        View blur;
                        try {
                            blur = (View) getBlur.invoke(widget);
                        } catch (ReflectiveOperationException unavailable) {
                            return chain.proceed();
                        }
                        if (blur == null || !BlurAppearance.hasWindowDrawable(blur)
                                || blur.getBackground().getAlpha() <= 0) return chain.proceed();
                        int alpha = blur.getBackground().getAlpha();
                        View previous = clearing.get();
                        clearing.set(blur);
                        try {
                            Object result = chain.proceed();
                            if (BlurAppearance.hasWindowDrawable(blur))
                                blur.getBackground().setAlpha(alpha);
                            start(widget, blur, alpha);
                            return result;
                        } finally {
                            if (previous == null) clearing.remove();
                            else clearing.set(previous);
                        }
                    })::unhook);
            hooks.add(module.hook(hostAlpha).setId("dressroom-advanced.media-widget-opening-fade")
                    .intercept(chain -> {
                        View widget = (View) chain.getThisObject();
                        if (fades.containsKey(widget) && ((Number) chain.getArg(0)).floatValue() == 0f)
                            return null;
                        return chain.proceed();
                    })::unhook);
            module.deoptimize(expand);
            module.deoptimize(hostAlpha);
            module.deoptimize(applyAlpha);
            hooks.commit();
            installed.add(owner);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional media widget fade unavailable", unavailable);
        }
    }

    static boolean holdClear(View surface) {
        return clearing.get() == surface;
    }

    private static void start(View widget, View surface, int alpha) {
        float initial = widget.getAlpha();
        ValueAnimator animation = ValueAnimator.ofFloat(1f, 0f);
        Fade fade = new Fade(surface, alpha, animation);
        fades.put(widget, fade);
        animation.setDuration(150);
        animation.addUpdateListener(frame -> {
            if (fades.get(widget) != fade) return;
            float scale = (Float) frame.getAnimatedValue();
            widget.setAlpha(initial * scale);
            if (BlurAppearance.hasWindowDrawable(surface))
                surface.getBackground().setAlpha(Math.round(alpha * scale));
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) {
                if (!fades.remove(widget, fade)) return;
                try {
                    Reflect.call(surface, "semSetBlurInfo", (Object) null);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    DressroomModule.note("Media widget blur fade cleanup", error);
                }
            }
        });
        animation.start();
    }

    private static void stop(View widget) {
        Fade old = fades.remove(widget);
        if (old == null) return;
        old.animator.cancel();
        if (BlurAppearance.hasWindowDrawable(old.surface))
            old.surface.getBackground().setAlpha(old.alpha);
    }
}
