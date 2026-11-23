package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Keep existing live blur during the unlock drag without changing the content animation. */
final class UnlockBlur {
    private static final String CLOCK_LISTENER =
            "com.samsung.android.app.facewidget.pages.clock.blur.ClockBlurController$";
    private static final Map<View, Float> clockAlpha =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable, Background> backgrounds =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, WeakReference<Drawable>> heldClears =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record CoverState(boolean visible, float alpha) {}

    private static final Map<View, CoverState> coverStates =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, CoverState> coverBeforeDrag =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, WeakReference<java.util.List<?>>> glyphs =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ClassLoader> providers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Object> listeners =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Class<?>> hookedListeners =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> dragEvent = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> dragNotified = new ThreadLocal<>();
    private static Context context;

    private static final class Main {
        static final android.os.Handler HANDLER =
                new android.os.Handler(android.os.Looper.getMainLooper());
    }
    private static boolean installed, tracking, active;
    private static float alpha = 1;
    private static long gestureDownTime;

    private static final class Background {
        final WeakReference<View> view;
        int baseline, requested;

        Background(View owner, int value) {
            view = new WeakReference<>(owner);
            baseline = requested = value;
        }
    }

    private UnlockBlur() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed || android.os.Build.VERSION.SDK_INT < 37) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> wrapper =
                    Class.forName(
                            "com.android.systemui.facewidget.plugin.KeyguardStatusViewAlphaChangeControllerWrapper",
                            false,
                            loader);
            Class<?> callback =
                    Class.forName(
                            "com.android.systemui.plugins.keyguardstatusview.PluginKeyguardStatusViewAlphaChangeListener",
                            false,
                            loader);
            Method register = wrapper.getDeclaredMethod("registerListener", callback);
            hooks.add(
                    module.hook(register)
                                    .setId("dressroom-advanced.unlock-blur-listener")
                                    .intercept(
                                            chain -> {
                                                Object listener = chain.getArg(0);
                                                if (listener != null
                                                        && listener.getClass()
                                                                .getName()
                                                                .startsWith(CLOCK_LISTENER))
                                                    hookClock(module, listener);
                                                return chain.proceed();
                                            })
                            ::unhook);
            Method update = wrapper.getDeclaredMethod("updateAlpha", float.class);
            hooks.add(
                    module.hook(update)
                                    .setId("dressroom-advanced.unlock-blur-progress")
                                    .intercept(
                                            chain -> {
                                                if (Boolean.TRUE.equals(dragEvent.get())
                                                        && enabled()) {
                                                    tracking = true;
                                                    dragNotified.set(true);
                                                }
                                                update(((Number) chain.getArg(0)).floatValue());
                                                Object result = chain.proceed();
                                                // The widget and Now Bar listeners set their
                                                // own alpha during dispatch.
                                                if (active) applyOpacity();
                                                return result;
                                            })
                            ::unhook);
            Class<?> move =
                    Class.forName(
                            "com.android.systemui.keyguard.animator.ActionMoveHandler",
                            false,
                            loader);
            Method gesture = move.getDeclaredMethod("handleMotionEvent", MotionEvent.class);
            hooks.add(
                    module.hook(gesture)
                                    .setId("dressroom-advanced.unlock-blur-drag")
                                    .intercept(
                                            chain -> {
                                                if (((MotionEvent) chain.getArg(0))
                                                                .getActionMasked()
                                                        != MotionEvent.ACTION_MOVE)
                                                    return chain.proceed();
                                                gestureDownTime =
                                                        ((MotionEvent) chain.getArg(0)).getDownTime();
                                                Boolean previous = dragEvent.get();
                                                boolean began = !active && enabled();
                                                if (began) begin();
                                                dragEvent.set(true);
                                                dragNotified.set(false);
                                                try {
                                                    return chain.proceed();
                                                } finally {
                                                    if (began
                                                            && !Boolean.TRUE.equals(
                                                                    dragNotified.get())) {
                                                        active = tracking = false;
                                                        restore();
                                                        releaseContainerClears();
                                                    }
                                                    dragNotified.remove();
                                                    if (previous == null) dragEvent.remove();
                                                    else dragEvent.set(previous);
                                                }
                                            })
                            ::unhook);
            Class<?> animator =
                    Class.forName(
                            "com.android.systemui.keyguard.animator.KeyguardTouchAnimator",
                            false,
                            loader);
            Method touch = animator.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            hooks.add(
                    module.hook(touch)
                                    .setId("dressroom-advanced.unlock-blur-gesture-end")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                MotionEvent event = (MotionEvent) chain.getArg(0);
                                                int action = event.getActionMasked();
                                                if (action == MotionEvent.ACTION_DOWN) {
                                                    gestureDownTime = event.getDownTime();
                                                    enabledKnown = false;
                                                }
                                                if ((action == MotionEvent.ACTION_UP
                                                        || action == MotionEvent.ACTION_CANCEL)) {
                                                    boolean unlocked = false;
                                                    try {
                                                        unlocked =
                                                                Boolean.TRUE.equals(
                                                                        Reflect.field(
                                                                                chain
                                                                                        .getThisObject(),
                                                                                "isUnlockExecuted"));
                                                    } catch (ReflectiveOperationException ignored) {
                                                    }
                                                    if (unlocked) stop();
                                                    else if (!active) tracking = false;
                                                    else finishCancelledGesture(event.getDownTime());
                                                }
                                                return result;
                                            })
                            ::unhook);
            for (Method method : new Method[] {register, update, gesture, touch})
                module.deoptimize(method);
            hooks.commit();
            installed = true;
            hookModule = module;
            // View.setAlpha and BackgroundBlurDrawable.setAlpha run for every view in
            // SystemUI. Only intercept them while the swipe-blur setting is on.
            refreshOpacityHooks();
            RuntimeExtras.listen(UnlockBlur::refreshOpacityHooks);
            android.util.Log.i(DressroomModule.TAG, "Live unlock blur hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Optional live unlock blur unavailable", error);
        }
    }

    private static DressroomModule hookModule;
    private static boolean opacityHooksInstalled;
    private static final List<Runnable> opacityUnhooks = new ArrayList<>(2);

    private static void refreshOpacityHooks() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            Main.HANDLER.removeCallbacks(REFRESH_OPACITY_HOOKS);
            Main.HANDLER.post(REFRESH_OPACITY_HOOKS);
            return;
        }
        enabledKnown = false;
        if (settingOnOrUnknown()) {
            if (hookModule != null) installOpacityHooks(hookModule);
            if (!enabled()) stop();
        } else {
            uninstallOpacityHooks();
        }
    }

    private static final Runnable REFRESH_OPACITY_HOOKS = UnlockBlur::refreshOpacityHooks;

    private static synchronized void uninstallOpacityHooks() {
        // Restore while interception is still active, with the writing guard set by stop().
        stop();
        Throwable failure = null;
        for (int i = opacityUnhooks.size() - 1; i >= 0; i--) {
            try {
                opacityUnhooks.get(i).run();
                opacityUnhooks.remove(i);
            } catch (RuntimeException | LinkageError error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (opacityUnhooks.isEmpty() && opacityHooksInstalled) {
            opacityHooksInstalled = false;
            android.util.Log.i(DressroomModule.TAG, "Live unlock blur opacity hooks removed");
        }
        if (failure != null && hookModule != null)
            hookModule.report("Live unlock blur opacity cleanup failed", failure);
    }

    private static boolean settingOnOrUnknown() {
        try {
            if (context == null)
                context = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
            return context == null || RuntimeExtras.enabled(context, "swipeBlur");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return true;
        }
    }

    private static synchronized void installOpacityHooks(DressroomModule module) {
        if (opacityHooksInstalled) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Method viewAlpha = View.class.getDeclaredMethod("setAlpha", float.class);
            Runnable viewUnhook =
                    module.hook(viewAlpha)
                                    .setId("dressroom-advanced.unlock-blur-clock-opacity")
                                    .intercept(
                                            chain -> {
                                                View view = (View) chain.getThisObject();
                                                if (clockAlpha.containsKey(view)
                                                        && !Boolean.TRUE.equals(writing.get())) {
                                                    float requested =
                                                            ((Number) chain.getArg(0)).floatValue();
                                                    clockAlpha.put(view, requested);
                                                    if (active)
                                                        return chain.proceed(
                                                                new Object[] {requested * alpha});
                                                }
                                                return chain.proceed();
                                            })
                            ::unhook;
            hooks.add(viewUnhook);
            Class<?> blur =
                    Class.forName("com.android.internal.graphics.drawable.BackgroundBlurDrawable");
            Method opacity = blur.getDeclaredMethod("setAlpha", int.class);
            Runnable backgroundUnhook =
                    module.hook(opacity)
                                    .setId("dressroom-advanced.unlock-blur-background-opacity")
                                    .intercept(
                                            chain -> {
                                                Background state =
                                                        backgrounds.get(chain.getThisObject());
                                                if (state == null
                                                        || Boolean.TRUE.equals(writing.get()))
                                                    return chain.proceed();
                                                int requested =
                                                        ((Number) chain.getArg(0)).intValue();
                                                state.requested = requested;
                                                if (!active) state.baseline = requested;
                                                else if (state.baseline > 0 && state.view.get() != null)
                                                    return chain.proceed(
                                                            new Object[] {
                                                                fadedOpacity(
                                                                        state.baseline,
                                                                        state.view.get())
                                                            });
                                                return chain.proceed();
                                            })
                            ::unhook;
            hooks.add(backgroundUnhook);
            module.deoptimize(viewAlpha);
            module.deoptimize(opacity);
            hooks.commit();
            opacityUnhooks.add(viewUnhook);
            opacityUnhooks.add(backgroundUnhook);
            opacityHooksInstalled = true;
            android.util.Log.i(DressroomModule.TAG, "Live unlock blur opacity hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Optional live unlock blur opacity unavailable", error);
        }
    }

    private static synchronized void hookClock(DressroomModule module, Object listener) {
        Class<?> type = listener.getClass();
        listeners.add(listener);
        if (hookedListeners.contains(type)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Method callback = type.getMethod("onKeyguardStatusViewAlphaChanged", float.class);
            hooks.add(
                    module.hook(callback)
                                    .setId("dressroom-advanced.unlock-blur-clock-gate")
                                    .intercept(
                                            chain ->
                                                    active && !Boolean.TRUE.equals(writing.get())
                                                            ? chain.proceed(new Object[] {1f})
                                                            : chain.proceed())
                            ::unhook);
            module.deoptimize(callback);
            hooks.commit();
            hookedListeners.add(type);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Unlock clock blur listener unavailable", error);
        }
    }

    // enabled() runs on every drag event and reads several system settings, so
    // keep the answer briefly. Setting changes and each new touch invalidate it.
    private static final long ENABLED_TTL_MS = 500L;
    private static volatile boolean enabledKnown, enabledValue;
    private static volatile long enabledAt;

    private static boolean enabled() {
        long now = android.os.SystemClock.uptimeMillis();
        if (enabledKnown && now - enabledAt < ENABLED_TTL_MS) return enabledValue;
        boolean value = computeEnabled();
        enabledValue = value;
        enabledAt = now;
        enabledKnown = true;
        return value;
    }

    private static boolean computeEnabled() {
        try {
            if (context == null)
                try {
                    context =
                            (Context)
                                    Class.forName("android.app.ActivityThread")
                                            .getMethod("currentApplication")
                                            .invoke(null);
                } catch (ReflectiveOperationException ignored) {
                }
            return context != null
                    && RuntimeExtras.enabled(context, "swipeBlur")
                    && BlurAppearance.available(context, "clockBlur");
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    static boolean duringSwipe(boolean tracking, boolean enabled, float progress) {
        return tracking && enabled && Float.isFinite(progress) && progress >= 0 && progress < 1;
    }

    private static void begin() {
        active = tracking = true;
        alpha = 1;
        coverBeforeDrag.clear();
        synchronized (coverStates) {
            coverBeforeDrag.putAll(coverStates);
        }
        for (var entry : snapshot(backgrounds.entrySet(), backgrounds)) {
            Drawable drawable = entry.getKey();
            if (drawable != null) {
                Background state = entry.getValue();
                state.baseline = state.requested = drawable.getAlpha();
            }
        }
        for (var entry : snapshot(clockAlpha.entrySet(), clockAlpha)) {
            View view = entry.getKey();
            if (view != null) clockAlpha.put(view, view.getAlpha());
        }
    }

    private static void update(float value) {
        alpha = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 1;
        boolean next = duringSwipe(tracking, enabled(), alpha);
        if (!next && active) restoreVisible();
        active = next;
        if (!active && alpha == 1) tracking = false;
    }

    private static void stop() {
        if (!active && !tracking) return;
        active = tracking = false;
        restore();
        releaseContainerClears();
        writing.set(true);
        try {
            for (Object listener : snapshot(listeners, listeners))
                try {
                    Reflect.call(listener, "onKeyguardStatusViewAlphaChanged", alpha);
                } catch (ReflectiveOperationException ignored) {
                }
        } finally {
            writing.remove();
        }
    }

    private static void finishCancelledGesture(long downTime) {
        // Some exits never send the final native alpha of one.
        Main.HANDLER.postDelayed(() -> {
            if (active && tracking && gestureDownTime == downTime) {
                alpha = 1;
                restoreVisible();
                stop();
            }
        }, 500);
    }

    private static void applyOpacity() {
        writing.set(true);
        try {
            for (var entry : snapshot(clockAlpha.entrySet(), clockAlpha)) {
                View view = entry.getKey();
                if (view != null && view.isAttachedToWindow()) {
                    LockscreenFrameBoost.pulse(view, 220L);
                    view.setAlpha(entry.getValue() * alpha);
                }
            }
            for (var entry : snapshot(backgrounds.entrySet(), backgrounds)) {
                Background state = entry.getValue();
                View view = state.view.get();
                if (view != null && view.isAttachedToWindow() && state.baseline > 0) {
                    LockscreenFrameBoost.pulse(view, 220L);
                    entry.getKey().setAlpha(fadedOpacity(state.baseline, view));
                }
            }
        } finally {
            writing.remove();
        }
    }

    private static void restore() {
        writing.set(true);
        try {
            for (var entry : snapshot(clockAlpha.entrySet(), clockAlpha)) {
                View view = entry.getKey();
                if (view != null) view.setAlpha(entry.getValue());
            }
            for (var entry : snapshot(backgrounds.entrySet(), backgrounds)) {
                Drawable drawable = entry.getKey();
                if (drawable != null) drawable.setAlpha(entry.getValue().requested);
            }
            for (var entry : snapshot(coverStates.entrySet(), coverStates)) {
                View view = entry.getKey();
                if (view != null) setCover(view, entry.getValue());
            }
        } finally {
            writing.remove();
        }
    }

    private static void restoreVisible() {
        heldClears.clear();
        restore();
        writing.set(true);
        try {
            for (var entry : snapshot(coverBeforeDrag.entrySet(), coverBeforeDrag)) {
                View view = entry.getKey();
                if (view != null) {
                    coverStates.put(view, entry.getValue());
                    setCover(view, entry.getValue());
                }
            }
            for (var entry : snapshot(backgrounds.entrySet(), backgrounds)) {
                Drawable drawable = entry.getKey();
                Background state = entry.getValue();
                if (drawable != null) drawable.setAlpha(state.baseline);
                state.requested = state.baseline;
            }
        } finally {
            writing.remove();
            coverBeforeDrag.clear();
        }
    }

    static boolean holdContainerClear(View view) {
        if (!active || WidgetDepthBlur.isClockBlurHost(view) || !view.isShown()) return false;
        Drawable drawable = view.getBackground();
        Background state = backgrounds.get(drawable);
        if (state == null || state.view.get() != view || state.baseline == 0) return false;
        heldClears.put(view, new WeakReference<>(drawable));
        return true;
    }

    private static void releaseContainerClears() {
        var pending = snapshot(heldClears.entrySet(), heldClears);
        heldClears.clear();
        for (var entry : pending) {
            View view = entry.getKey();
            Drawable drawable = entry.getValue().get();
            if (view == null || drawable == null || view.getBackground() != drawable) continue;
            BlurAppearance.clearWindowSurface(view);
        }
    }

    private static <T> List<T> snapshot(Collection<T> items, Object lock) {
        synchronized (lock) {
            return new ArrayList<>(items);
        }
    }

    private static void setCover(View view, CoverState state) {
        try {
            Reflect.call(view, "setCoverWidgetBlurState", state.visible, state.alpha);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    static synchronized void installProvider(DressroomModule module, ClassLoader loader) {
        if (android.os.Build.VERSION.SDK_INT < 37 || providers.contains(loader)) return;
        try {
            Class<?> container =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer",
                            false,
                            loader);
            Method state;
            try {
                state =
                        container.getDeclaredMethod(
                                "setCoverWidgetBlurState", boolean.class, float.class);
            } catch (NoSuchMethodException unsupported) {
                providers.add(loader);
                return;
            }
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(
                        module.hook(state)
                                        .setId("dressroom-advanced.unlock-blur-widget-state")
                                        .intercept(
                                                chain -> {
                                                    View view = (View) chain.getThisObject();
                                                    if (Boolean.TRUE.equals(writing.get()))
                                                        return chain.proceed();
                                                    CoverState requested =
                                                            new CoverState(
                                                                    (Boolean) chain.getArg(0),
                                                                    ((Number) chain.getArg(1))
                                                                            .floatValue());
                                                    coverStates.put(view, requested);
                                                    CoverState before = coverBeforeDrag.get(view);
                                                    if (active && before != null && before.visible)
                                                        return chain.proceed(
                                                                new Object[] {
                                                                    before.visible, before.alpha
                                                                });
                                                    return chain.proceed();
                                                })
                                ::unhook);
                module.deoptimize(state);
                hooks.commit();
            }
            providers.add(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Optional unlock widget blur state unavailable", error);
        }
    }

    static void glyphs(View clock, java.util.List<?> views) {
        WeakReference<java.util.List<?>> old = glyphs.get(clock);
        if (old == null || old.get() != views) glyphs.put(clock, new WeakReference<>(views));
    }

    static PointF hostOrigin(View host, PointF nativeOrigin) {
        if (!active) return nativeOrigin;
        return globalOrigin(host);
    }

    static PointF hostOrigin(View clock, View host, PointF nativeOrigin) {
        return active || ClockBlurMotion.moving(clock) || ClockInlineSeconds.active(clock)
                ? globalOrigin(host)
                : nativeOrigin;
    }

    private static PointF globalOrigin(View host) {
        Matrix matrix = new Matrix();
        host.transformMatrixToGlobal(matrix);
        float[] point = {0, 0};
        matrix.mapPoints(point);
        return new PointF(point[0], point[1]);
    }

    static Path liveClockPath(
            View clock, PointF origin, ImageView icon, Object weather, Path nativePath,
            Method glyphOutline) {
        if (!active && !ClockBlurMotion.moving(clock) && !ClockInlineSeconds.active(clock))
            return nativePath;
        WeakReference<java.util.List<?>> reference = glyphs.get(clock);
        java.util.List<?> views = reference == null ? null : reference.get();
        if (views == null) return nativePath;
        try {
            Path result = new Path();
            for (Object value : views) {
                if (!(value instanceof TextView text) || !text.isShown()) continue;
                if (glyphOutline != null && !glyphOutline.getDeclaringClass().isInstance(text))
                    continue;
                Object local = glyphOutline == null
                        ? Reflect.call(text, "getPathForBlur") : glyphOutline.invoke(text);
                if (!(local instanceof Path path)) return nativePath;
                append(result, path, text, origin, 1);
            }
            if (icon != null && icon.isShown() && weather != null) {
                Object paths = Reflect.call(weather, "getIconPathForBlur");
                if (paths instanceof java.util.List<?> list)
                    for (Object item : list)
                        if (item instanceof Path path)
                            append(result, path, icon, origin, icon.getWidth() / 70f);
            }
            return result.isEmpty() ? nativePath : result;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return nativePath;
        }
    }

    static void append(Path result, Path local, View view, PointF origin, float scale) {
        Matrix global = new Matrix();
        view.transformMatrixToGlobal(global);
        global.preScale(scale, scale);
        global.postTranslate(-origin.x, -origin.y);
        result.addPath(local, global);
    }

    static void submitted(View view, boolean present, String surface) {
        if (!installed) return;
        heldClears.remove(view);
        if ("clockBlur".equals(surface)) {
            // A native clear can be immediately followed by a rebuild on the
            // same host. Retain its unfaded alpha across that handoff.
            if (!present) return;
            clockAlpha.putIfAbsent(view, view.getAlpha());
        }
        if (!present) return;
        Drawable background = view.getBackground();
        if (background == null
                || !background
                        .getClass()
                        .getName()
                        .equals("com.android.internal.graphics.drawable.BackgroundBlurDrawable"))
            return;
        if (surface != null && !backgrounds.containsKey(background))
            backgrounds.put(background, new Background(view, background.getAlpha()));
    }

    static int fadedOpacity(int baseline, View view) {
        float opacity = 1;
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) return 0;
            float value = current.getAlpha();
            if (!Float.isFinite(value)) return 0;
            opacity *= Math.max(0, Math.min(1, value));
        }
        return Math.round(baseline * opacity);
    }
}
