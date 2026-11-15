package dev.denver.dressroomadvanced;

import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Refresh the compositor's live container regions after SystemUI's doze updates. */
final class DozeBlurRegions {
    private static boolean installed, pending, repairOnWake, reported;
    private static Runnable settledWake;

    private static final class Main {
        static final android.os.Handler HANDLER =
                new android.os.Handler(android.os.Looper.getMainLooper());
    }

    private record Binding(
            Field statusBar, Method state, Method dozing, Class<?> blur, Field aggregator,
            Method regionChanged, Field position) {}

    private DozeBlurRegions() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed || android.os.Build.VERSION.SDK_INT < 37) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> host = Class.forName(
                    "com.android.systemui.statusbar.phone.DozeServiceHost", false, loader);
            Field statusBar = host.getDeclaredField("mStatusBarStateController");
            statusBar.setAccessible(true);
            Method dozing = statusBar.getType().getMethod("isDozing");
            Method state = statusBar.getType().getMethod("getState");
            Method tick = NativeContract.method(host, "dozeTimeTick", void.class);
            Method update = NativeContract.method(host, "updateDozing", void.class);
            Class<?> blur = Class.forName(
                    "com.android.internal.graphics.drawable.BackgroundBlurDrawable");
            Field aggregator = blur.getDeclaredField("mAggregator");
            aggregator.setAccessible(true);
            Field position;
            try {
                position = NativeContract.field(blur, "mRect", Rect.class);
            } catch (NoSuchFieldException unsupported) {
                position = null;
            }
            Method regionChanged = NativeContract.method(
                    aggregator.getType(), "onBlurDrawableUpdated", void.class, blur);
            Binding binding = new Binding(statusBar, state, dozing, blur, aggregator,
                    regionChanged, position);
            hooks.add(module.hook(tick).setId("dressroom-advanced.doze-tick-blur-regions")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        schedule(chain.getThisObject(), binding, false);
                        return result;
                    })::unhook);
            hooks.add(module.hook(update).setId("dressroom-advanced.doze-return-blur-regions")
                    .intercept(chain -> {
                        Object hostInstance = chain.getThisObject();
                        boolean before = isDozing(hostInstance, statusBar, dozing);
                        Object result = chain.proceed();
                        boolean after = isDozing(hostInstance, statusBar, dozing);
                        if (before != after) {
                            Runnable updateWake = () -> {
                                if (settledWake != null) Main.HANDLER.removeCallbacks(settledWake);
                                settledWake = null;
                                if (!after) {
                                    schedule(hostInstance, binding, false);
                                    // Doze ends before the lockscreen opacity animation finishes.
                                    settledWake = () -> {
                                        settledWake = null;
                                        schedule(hostInstance, binding, true);
                                    };
                                    Main.HANDLER.postDelayed(settledWake, 500);
                                }
                            };
                            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                updateWake.run();
                            } else {
                                Main.HANDLER.post(updateWake);
                            }
                        }
                        return result;
                    })::unhook);
            module.deoptimize(tick);
            module.deoptimize(update);
            hooks.commit();
            installed = true;
            android.util.Log.i(DressroomModule.TAG, "AOD container blur region refresh installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Optional AOD blur region refresh unavailable", error);
        }
    }

    private static boolean isDozing(Object host, Field statusBar, Method dozing) {
        try {
            return Boolean.TRUE.equals(dozing.invoke(statusBar.get(host)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
            return false;
        }
    }

    private static void schedule(Object host, Binding binding, boolean waking) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return;
        repairOnWake |= waking;
        if (pending) return;
        pending = true;
        Choreographer.getInstance().postFrameCallback(frame -> {
            pending = false;
            boolean repair = repairOnWake;
            repairOnWake = false;
            try {
                // KEYGUARD. A scheduled tick may run after the user has already unlocked.
                if (!Integer.valueOf(1).equals(binding.state.invoke(binding.statusBar.get(host)))) return;
                if (repair) {
                    for (View widget : WidgetDepthBlur.windowSurfaces()) {
                        Drawable background = widget.getBackground();
                        if (background == null
                                || (binding.blur.isInstance(background) && background.getAlpha() == 0))
                            recoverWidget(host, binding, widget);
                    }
                }
                for (View view : BlurAppearance.visibleWindowSurfaces()) {
                    Drawable background = view.getBackground();
                    if (repair && (background == null
                            || (binding.blur.isInstance(background) && background.getAlpha() == 0))) {
                        if (!WidgetDepthBlur.isBackground(view) && settledSurface(host, binding, view))
                            recoverNowBar(view);
                        continue;
                    }
                    if (!binding.blur.isInstance(background) || !background.isVisible()
                            || background.getCallback() != view || background.getAlpha() <= 0
                            || UnlockBlur.fadedOpacity(255, view) <= 0
                            || !BlurAppearance.liveWindowBlurAllowed(view.getContext())) continue;
                    // Ask the framework to send its current region again. Keep the native
                    // drawable, alpha and clip path, even if the tick changed them.
                    binding.regionChanged.invoke(binding.aggregator.get(background), background);
                    view.invalidate();
                    if (repair && binding.position != null
                            && ((Rect) binding.position.get(background)).isEmpty())
                        view.postOnAnimation(() -> repairIfPositionMissing(
                                host, binding, view, background));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                report(error);
            }
        });
    }

    private static void repairIfPositionMissing(
            Object host, Binding binding, View view, Drawable background) {
        try {
            if (!settledSurface(host, binding, view)
                    || view.getBackground() != background || !background.isVisible()
                    || background.getAlpha() <= 0
                    || !((Rect) binding.position.get(background)).isEmpty()) return;
            BlurAppearance.recreateWindowSurface(view, background);
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    private static boolean settledSurface(Object host, Binding binding, View view)
            throws ReflectiveOperationException {
        Object controller = binding.statusBar.get(host);
        return Integer.valueOf(1).equals(binding.state.invoke(controller))
                && Boolean.FALSE.equals(binding.dozing.invoke(controller))
                && view.isAttachedToWindow() && view.isShown()
                && UnlockBlur.fadedOpacity(255, view) == 255
                && view.getGlobalVisibleRect(new Rect())
                && BlurAppearance.liveWindowBlurAllowed(view.getContext());
    }

    private static void recoverWidget(Object host, Binding binding, View view) {
        try {
            if (settledSurface(host, binding, view)) WidgetDepthBlur.recoverWindowSurface(view);
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    private static void recoverNowBar(View view) {
        try {
            recoverTopCard(view);
        } catch (ReflectiveOperationException | RuntimeException error) {
            report(error);
        }
    }

    private static void recoverTopCard(View view) throws ReflectiveOperationException {
        if (!BlurAppearance.hasLiveWindowRequest(view)) return;
        for (View child = view; child.getParent() instanceof View parent; child = parent) {
            if (!parent.getClass().getName().equals(
                    "com.samsung.android.app.facewidget.utils.NowBarCardStackView")) continue;
            NowBarStackBinding.StackState stackState =
                    NowBarStackBinding.StackState.of(parent.getClass());
            if (Reflect.call(parent, "getTopCard") != child
                    || Reflect.call(child, "getBlurContainer") != view
                    || Boolean.TRUE.equals(stackState.expanded.invoke(parent))
                    || Boolean.TRUE.equals(stackState.expanding.get(parent))
                    || stackState.mode == null
                    || !"LOCKSCREEN".equals(String.valueOf(stackState.mode.get(parent)))) return;
            Method opacity =
                    stackState.cardBlurAlpha != null
                            ? stackState.cardBlurAlpha
                            : NowBarStackBinding.cardBlurAlpha(parent.getClass());
            BlurAppearance.restoreMissingWindowSurface(view);
            opacity.invoke(null, parent.getAlpha(), child);
            return;
        }
    }

    private static void report(Exception error) {
        if (reported) return;
        reported = true;
        DressroomModule.note("AOD container blur region refresh", error);
    }
}
