package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Replaces Samsung's full-screen seconds dashboard on digital lockscreen clocks with an inline
 * ':SS' reveal that smoothly shifts/resizes the live clock, live-blurs ':SS' characters with
 * SemBlurInfo, carries across full media player expand/collapse, and fades out on AOD/shade.
 */
final class ClockInlineSeconds {
    static final String KEY = "inlineSeconds";
    static final String KEEP_OPEN = "keepSecondsOpen";
    static final String BLUR_TAG = "dressroom_inline_sec_blur";

    private static final long OPEN_DURATION_MS = 420L;
    private static final long CLOSE_DURATION_MS = 320L;
    private static final long HANDOFF_CLOSE_MS = 260L;
    private static final long MEDIA_FOLLOW_MS = 950L;
    private static final long AUTO_CLOSE_MS = 10_000L;
    private static final TimeInterpolator OPEN_EASE =
            new PathInterpolator(0.20f, 0.90f, 0.25f, 1.0f);
    private static final TimeInterpolator CLOSE_EASE =
            new PathInterpolator(0.30f, 0.0f, 0.20f, 1.0f);
    private static final Canvas PRIME_CANVAS = new Canvas();

    private static final Set<String> ELIGIBLE_CLOCKS =
            Set.of(
                    "com.samsung.android.uniform.widget.clock.DigitalHorizontalClockView",
                    "com.samsung.android.uniform.widget.clock.DigitalHorizontalSmallClockView",
                    "com.samsung.android.uniform.widget.clock.DigitalHorizontalSubClockView",
                    "com.samsung.android.uniform.widget.clock.DigitalHorizontalTypoClockView",
                    "com.samsung.android.uniform.widget.clock.CalendarClockView",
                    "com.samsung.android.uniform.widget.clock.TodaysEventClockView",
                    "com.samsung.android.uniform.widget.clock.DigitalVerticalClockView");
    private static final String VERTICAL_CLOCK =
            "com.samsung.android.uniform.widget.clock.DigitalVerticalClockView";
    private static final Set<String> CLOSE_BLOCKERS =
            Set.of(
                    "QUICK_PANEL_EXPANDED",
                    "PRIMARY_BOUNCER_VISIBLE",
                    "DOZE_ACTIVE",
                    "EDIT_MODE_ANIMATING",
                    "PINCH_ZOOM_ACTIVE",
                    "OVERLAY_SWIPING",
                    "SHORTCUT_PREVIEW_SWIPING");

    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, ClockSecondsBinding> pages =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Session> sessions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> pendingTransfers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<View, Set<String>> clockBlockers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> updatingClockBlur = new ThreadLocal<>();
    private static final ThreadLocal<View> renderingClockFont = new ThreadLocal<>();
    private static final ThreadLocal<View> syncedClockInHook = new ThreadLocal<>();
    private static final ThreadLocal<View> collectedSecondsBlur = new ThreadLocal<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean pinnedByUser;
    private static volatile boolean sleeping;
    private static volatile float lastDarkAmount;
    private static volatile boolean keyguardVisible = true;
    private static volatile int wakeRestoreGeneration;
    private static volatile Method cachedGetClockGravity;
    private static volatile Method cachedIsShowDateFirst;
    private static boolean extrasObserved;

    private ClockInlineSeconds() {}

    static boolean enabled(Context context) {
        return RuntimeExtras.get(context).getBoolean(KEY, true);
    }

    static boolean keepOpen(Context context) {
        return RuntimeExtras.enabled(context, KEEP_OPEN);
    }

    static boolean active(View clock) {
        if (clock == null || sessions.isEmpty()) return false;
        Session session = sessions.get(clock);
        return session != null && session.progress > 0f && !session.ghostActive;
    }

    static void onMediaChanged(View clock) {
        if (!active(clock)) return;
        Session session = sessions.get(clock);
        if (session != null) session.startMediaFollowSync();
    }

    static Path withSecondsPath(View clock, PointF origin, Path basePath) {
        if (clock == null || origin == null || sessions.isEmpty()) return basePath;
        Session session = sessions.get(clock);
        if (session == null || session.progress <= 0.02f || session.ghostActive) return basePath;
        Path combined = basePath == null ? new Path() : new Path(basePath);
        return session.appendSecondsToPath(combined, origin) ? combined : basePath;
    }

    static void onBlockerState(View clock, Set<String> reasons) {
        if (clock == null || reasons == null) return;
        clockBlockers.put(clock, Set.copyOf(reasons));
        Session session = sessions.isEmpty() ? null : sessions.get(clock);
        for (String reason : reasons) {
            if (CLOSE_BLOCKERS.contains(reason)) {
                if (session != null) session.close(false);
                return;
            }
        }
        if (session != null && session.progress > 0f && !session.ghostActive) {
            if (reasons.contains("MEDIA_EXPAND_ANIMATING")) {
                session.startMediaFollowSync();
            } else if (reasons.contains("MEDIA_EXPANDED_IN_LANDSCAPE") || reasons.isEmpty()) {
                session.schedulePreDrawRefresh();
            }
        } else if (pinnedByUser && !sleeping && keyguardVisible && lastDarkAmount <= 0.01f) {
            scheduleWakeRestore();
        }
    }

    static synchronized void install(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException, IOException {
        if (module.inEditorProcess()) return;
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;

        ClockSecondsBinding binding = ClockSecondsBinding.resolve(base);
        ClockBinding clockBinding = ClockBinding.of(binding.absClockBase);
        Method setDozing = binding.absClockBase.getDeclaredMethod("setDozing", boolean.class);
        Method setMediaExpanded = clockBinding.early ? null
                : binding.absClockBase.getDeclaredMethod("setMediaExpanded", boolean.class);
        Method onDetached = binding.absClockBase.getDeclaredMethod("onDetachedFromWindow");
        Class<?> shadowClass =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle", false, loader);
        Method setShadow = binding.absClockBase.getDeclaredMethod("setShadowStyle", shadowClass);
        Method getClockGravity = clockBinding.early ? null
                : binding.absClockBase.getMethod("getClockGravity");
        Method isShowDateFirst = binding.absClockBase.getMethod("isShowDateInfoFirst");
        cachedGetClockGravity = getClockGravity;
        cachedIsShowDateFirst = isShowDateFirst;
        Method startedWaking = null;
        Method finishedWaking = null;
        try {
            startedWaking = binding.sleep.getDeclaringClass().getMethod("onStartedWakingUp");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            finishedWaking = binding.sleep.getDeclaringClass().getMethod("onFinishedWakingUp");
        } catch (NoSuchMethodException ignored) {
        }

        FontBinding fontBinding = null;
        try {
            fontBinding = FontBinding.of(binding.absClockBase);
        } catch (RuntimeException ignored) {
        }
        Method fontAxesTransition = null;
        try {
            fontAxesTransition = ClockTransitionBinding.of(binding.absClockBase).transition;
        } catch (RuntimeException ignored) {
        }
        Method setClockTextBlur = null;
        try {
            setClockTextBlur = binding.absClockBase.getMethod("setClockTextBlur");
        } catch (NoSuchMethodException ignored) {
        }
        Method removeBlur = null;
        try {
            removeBlur = binding.absClockBase.getDeclaredMethod("removeBlur");
        } catch (NoSuchMethodException ignored) {
        }
        Method setBlurEnabled = null;
        try {
            setBlurEnabled =
                    binding.absClockBase.getDeclaredMethod("setBlurEffectEnabled", boolean.class);
        } catch (NoSuchMethodException ignored) {
        }
        Method setClockGravity = null;
        try {
            setClockGravity = binding.absClockBase.getMethod("setClockGravity", int.class);
        } catch (NoSuchMethodException ignored) {
        }
        Method setScaleClock = null;
        try {
            setScaleClock =
                    binding.absClockBase.getMethod("setScaleClock", float.class, int.class);
        } catch (NoSuchMethodException ignored) {
        }

        List<Method> baseColorBatchSetters = new ArrayList<>();
        for (Method m : binding.absClockBase.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    || m.getReturnType() != void.class
                    || m.getParameterCount() != 2) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params[1] == TextView[].class
                    && (params[0] == int.class
                            || "PaletteItem".equals(params[0].getSimpleName()))) {
                m.setAccessible(true);
                baseColorBatchSetters.add(m);
            }
        }

        List<Method> axesTransitions =
                collectHierarchyOverrides(loader, binding.absClockBase, fontAxesTransition);
        List<Method> textBlurMethods =
                collectHierarchyOverrides(loader, binding.absClockBase, setClockTextBlur);
        Set<Method> colorSetterSet = Collections.newSetFromMap(new java.util.LinkedHashMap<>());
        for (Method baseSetter : baseColorBatchSetters) {
            colorSetterSet.addAll(
                    collectHierarchyOverrides(loader, binding.absClockBase, baseSetter));
        }
        List<Method> colorBatchSetters = new ArrayList<>(colorSetterSet);

        ThreadLocal<Boolean> inAxesHook = new ThreadLocal<>();
        ThreadLocal<Boolean> inTextBlurHook = new ThreadLocal<>();
        ThreadLocal<Boolean> inColorBatchHook = new ThreadLocal<>();

        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(binding.bindClock)
                                    .setId("dressroom-advanced.inline-seconds-bind-page")
                                    .intercept(
                                            chain -> {
                                                View page = (View) chain.getThisObject();
                                                View prevClock = readClock(page, binding);
                                                captureLastGlobal(prevClock);
                                                Session prevSession =
                                                        prevClock == null || sessions.isEmpty()
                                                                ? null
                                                                : sessions.get(prevClock);
                                                boolean wasOpen =
                                                        prevSession != null
                                                                && prevSession.targetOpen
                                                                && !prevSession.ghostActive;
                                                long remainingMs =
                                                        wasOpen
                                                                ? prevSession.remainingAutoCloseMs()
                                                                : AUTO_CLOSE_MS;

                                                Object result = chain.proceed();
                                                pages.put(page, binding);
                                                View nextClock = readClock(page, binding);
                                                if (prevClock != null && prevClock != nextClock) {
                                                    String state = readPageState(page, binding);
                                                    boolean wantCarry =
                                                            wasOpen
                                                                    || (pinnedByUser
                                                                            && !sleeping
                                                                            && keyguardVisible
                                                                            && lastDarkAmount
                                                                                    <= 0.01f
                                                                            && nextClock != null
                                                                            && keepOpen(
                                                                                    nextClock
                                                                                            .getContext()));
                                                    boolean canCarry =
                                                            wantCarry
                                                                    && nextClock != null
                                                                    && ("SMALL".equals(state)
                                                                            || "EXPAND"
                                                                                    .equals(state))
                                                                    && enabled(
                                                                            nextClock.getContext())
                                                                    && ELIGIBLE_CLOCKS.contains(
                                                                            nextClock
                                                                                    .getClass()
                                                                                    .getName());
                                                    closeClock(prevClock, false);
                                                    if (canCarry) {
                                                        scheduleTransferredOpen(
                                                                page,
                                                                nextClock,
                                                                binding,
                                                                getClockGravity,
                                                                isShowDateFirst,
                                                                remainingMs);
                                                    }
                                                } else if (prevClock == null
                                                        && nextClock != null
                                                        && pinnedByUser
                                                        && !sleeping
                                                        && keyguardVisible
                                                        && lastDarkAmount <= 0.01f) {
                                                    scheduleWakeRestore();
                                                }
                                                return result;
                                            })
                            ::unhook);

            if (binding.pageTransition != null) {
                hooks.add(
                        module.hook(binding.pageTransition.attach())
                                        .setId("dressroom-advanced.inline-seconds-page-transition")
                                        .intercept(
                                                chain -> {
                                                    View srcClock =
                                                            binding.pageTransition.clock(
                                                                    chain.getThisObject(),
                                                                    binding.pageTransition.source(),
                                                                    binding.absClockBase);
                                                    captureLastGlobal(srcClock);
                                                    Object result = chain.proceed();
                                                    if (srcClock != null) {
                                                        closeClock(srcClock, false);
                                                    }
                                                    return result;
                                                })
                                ::unhook);
            }

            hooks.add(
                    module.hook(binding.timeChanged)
                                    .setId("dressroom-advanced.inline-seconds-time-changed")
                                    .intercept(
                                            chain -> {
                                                View clock = (View) chain.getThisObject();
                                                Session s =
                                                        sessions.isEmpty()
                                                                ? null
                                                                : sessions.get(clock);
                                                if (s != null && s.progress > 0f && !s.ghostActive) {
                                                    s.reapplyTransforms();
                                                }
                                                Object result = chain.proceed();
                                                if (s != null && s.progress > 0f && !s.ghostActive) {
                                                    s.onClockTimeChanged();
                                                }
                                                return result;
                                            })
                            ::unhook);

            if (clockBinding.early) {
                OneUi80ClockSeconds.dashboard(loader).install(module, hooks,
                        root -> handleEarlyTap(root, binding, isShowDateFirst));
            } else hooks.add(
                    module.hook(binding.tap)
                                    .setId("dressroom-advanced.inline-seconds-tap")
                                    .intercept(
                                            chain -> {
                                                int x = ((Number) chain.getArg(0)).intValue();
                                                int y = ((Number) chain.getArg(1)).intValue();
                                                TapOutcome outcome =
                                                        handleTap(
                                                                x,
                                                                y,
                                                                null,
                                                                binding,
                                                                getClockGravity,
                                                                isShowDateFirst);
                                                return outcome == TapOutcome.CONSUME
                                                        ? null
                                                        : chain.proceed();
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(binding.sleep)
                                    .setId("dressroom-advanced.inline-seconds-sleep")
                                    .intercept(
                                            chain -> {
                                                sleeping = true;
                                                wakeRestoreGeneration++;
                                                pendingTransfers.clear();
                                                closeAll(false);
                                                return chain.proceed();
                                            })
                            ::unhook);

            if (startedWaking != null) {
                hooks.add(
                        module.hook(startedWaking)
                                        .setId("dressroom-advanced.inline-seconds-wake-start")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    sleeping = false;
                                                    if (pinnedByUser) scheduleWakeRestore();
                                                    return result;
                                                })
                                ::unhook);
            }

            if (finishedWaking != null) {
                hooks.add(
                        module.hook(finishedWaking)
                                        .setId("dressroom-advanced.inline-seconds-wake-end")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    sleeping = false;
                                                    if (pinnedByUser) scheduleWakeRestore();
                                                    return result;
                                                })
                                ::unhook);
            }

            hooks.add(
                    module.hook(binding.darkAmount)
                                    .setId("dressroom-advanced.inline-seconds-dark-amount")
                                    .intercept(
                                            chain -> {
                                                float dark =
                                                        ((Number) chain.getArg(0)).floatValue();
                                                Object result = chain.proceed();
                                                if (Float.isFinite(dark)) {
                                                    lastDarkAmount = dark;
                                                    if (dark > 0.01f) {
                                                        wakeRestoreGeneration++;
                                                        pendingTransfers.clear();
                                                        closeAll(false);
                                                    } else if (pinnedByUser && !sleeping) {
                                                        scheduleWakeRestore();
                                                    }
                                                }
                                                return result;
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(binding.visibilityChanged)
                                    .setId("dressroom-advanced.inline-seconds-keyguard-vis")
                                    .intercept(
                                            chain -> {
                                                int vis = ((Number) chain.getArg(0)).intValue();
                                                Object result = chain.proceed();
                                                keyguardVisible = (vis == View.VISIBLE);
                                                if (!keyguardVisible) {
                                                    wakeRestoreGeneration++;
                                                    pendingTransfers.clear();
                                                    closeAll(true);
                                                } else if (pinnedByUser
                                                        && !sleeping
                                                        && lastDarkAmount <= 0.01f) {
                                                    scheduleWakeRestore();
                                                }
                                                return result;
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(binding.folderChanged)
                                    .setId("dressroom-advanced.inline-seconds-folder")
                                    .intercept(
                                            chain -> {
                                                pendingTransfers.clear();
                                                closeAll(true);
                                                Object result = chain.proceed();
                                                if (pinnedByUser
                                                        && !sleeping
                                                        && keyguardVisible
                                                        && lastDarkAmount <= 0.01f) {
                                                    scheduleWakeRestore();
                                                }
                                                return result;
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(setDozing)
                                    .setId("dressroom-advanced.inline-seconds-doze")
                                    .intercept(
                                            chain -> {
                                                boolean isDozing =
                                                        Boolean.TRUE.equals(chain.getArg(0));
                                                if (isDozing) {
                                                    View clock = (View) chain.getThisObject();
                                                    pendingTransfers.remove(clock);
                                                    closeClock(clock, false);
                                                }
                                                Object result = chain.proceed();
                                                if (!isDozing && pinnedByUser) {
                                                    sleeping = false;
                                                    scheduleWakeRestore();
                                                }
                                                return result;
                                            })
                            ::unhook);

            if (setMediaExpanded != null) hooks.add(
                    module.hook(setMediaExpanded)
                                    .setId("dressroom-advanced.inline-seconds-media")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                onMediaChanged((View) chain.getThisObject());
                                                return result;
                                            })
                            ::unhook);

            if (fontBinding != null) {
                hooks.add(
                        module.hook(fontBinding.fixed)
                                        .setId("dressroom-advanced.inline-seconds-fixed-font")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    if (chain.getThisObject() instanceof View clock
                                                            && renderingClockFont.get() != clock) {
                                                        Session s =
                                                                sessions.isEmpty()
                                                                        ? null
                                                                        : sessions.get(clock);
                                                        if (s != null
                                                                && s.progress > 0f
                                                                && !s.ghostActive) {
                                                            s.schedulePreDrawFontSync();
                                                        }
                                                    }
                                                    return result;
                                                })
                                ::unhook);
                hooks.add(
                        module.hook(fontBinding.variable)
                                        .setId("dressroom-advanced.inline-seconds-variable-font")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    if (chain.getThisObject() instanceof View clock
                                                            && renderingClockFont.get() != clock) {
                                                        Session s =
                                                                sessions.isEmpty()
                                                                        ? null
                                                                        : sessions.get(clock);
                                                        if (s != null
                                                                && s.progress > 0f
                                                                && !s.ghostActive) {
                                                            s.schedulePreDrawFontSync();
                                                        }
                                                    }
                                                    return result;
                                                })
                                ::unhook);
            }

            for (int i = 0; i < axesTransitions.size(); i++) {
                Method axesMethod = axesTransitions.get(i);
                hooks.add(
                        module.hook(axesMethod)
                                        .setId(
                                                "dressroom-advanced.inline-seconds-axes-transition-"
                                                        + i)
                                        .intercept(
                                                chain -> {
                                                    if (Boolean.TRUE.equals(inAxesHook.get())) {
                                                        return chain.proceed();
                                                    }
                                                    inAxesHook.set(Boolean.TRUE);
                                                    View prevSynced = syncedClockInHook.get();
                                                    syncedClockInHook.remove();
                                                    try {
                                                        Object result = chain.proceed();
                                                        View clock = (View) chain.getThisObject();
                                                        if (syncedClockInHook.get() != clock) {
                                                            Session s =
                                                                    sessions.isEmpty()
                                                                            ? null
                                                                            : sessions.get(clock);
                                                            if (s != null
                                                                    && s.progress > 0f
                                                                    && !s.ghostActive) {
                                                                s.syncDuringClockMotion();
                                                            }
                                                        }
                                                        return result;
                                                    } finally {
                                                        if (prevSynced == null) {
                                                            syncedClockInHook.remove();
                                                        } else {
                                                            syncedClockInHook.set(prevSynced);
                                                        }
                                                        inAxesHook.remove();
                                                    }
                                                })
                                ::unhook);
            }

            for (int i = 0; i < textBlurMethods.size(); i++) {
                Method blurMethod = textBlurMethods.get(i);
                hooks.add(
                        module.hook(blurMethod)
                                        .setId(
                                                "dressroom-advanced.inline-seconds-text-blur-sync-"
                                                        + i)
                                        .intercept(
                                                chain -> {
                                                    if (Boolean.TRUE.equals(
                                                            inTextBlurHook.get())) {
                                                        return chain.proceed();
                                                    }
                                                    inTextBlurHook.set(Boolean.TRUE);
                                                    View prevCollected = collectedSecondsBlur.get();
                                                    collectedSecondsBlur.remove();
                                                    try {
                                                        Object result = chain.proceed();
                                                        if (Boolean.TRUE.equals(
                                                                updatingClockBlur.get())) {
                                                            return result;
                                                        }
                                                        View clock = (View) chain.getThisObject();
                                                        if (collectedSecondsBlur.get() == clock) {
                                                            return result;
                                                        }
                                                        Session s =
                                                                sessions.isEmpty()
                                                                        ? null
                                                                        : sessions.get(clock);
                                                        if (s != null
                                                                && s.progress > 0f
                                                                && !s.ghostActive) {
                                                            s.syncColorsFromRef();
                                                            if (s.isClockGlassBlurActive()) {
                                                                s.updateSecondsSemBlur(
                                                                        ClockSecondsLayout
                                                                                .revealAlpha(
                                                                                        s.progress));
                                                            } else {
                                                                s.clearSecondsSemBlur(false);
                                                            }
                                                        }
                                                        return result;
                                                    } finally {
                                                        if (prevCollected == null) {
                                                            collectedSecondsBlur.remove();
                                                        } else {
                                                            collectedSecondsBlur.set(prevCollected);
                                                        }
                                                        inTextBlurHook.remove();
                                                    }
                                                })
                                ::unhook);
            }

            if (removeBlur != null) {
                hooks.add(
                        module.hook(removeBlur)
                                        .setId("dressroom-advanced.inline-seconds-remove-blur")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    View clock = (View) chain.getThisObject();
                                                    Session s =
                                                            sessions.isEmpty()
                                                                    ? null
                                                                    : sessions.get(clock);
                                                    if (s != null
                                                            && s.progress > 0f
                                                            && !s.ghostActive) {
                                                        s.onClockBlurModeChanged();
                                                    }
                                                    return result;
                                                })
                                ::unhook);
            }

            if (setBlurEnabled != null) {
                hooks.add(
                        module.hook(setBlurEnabled)
                                        .setId("dressroom-advanced.inline-seconds-blur-enabled")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    View clock = (View) chain.getThisObject();
                                                    Session s =
                                                            sessions.isEmpty()
                                                                    ? null
                                                                    : sessions.get(clock);
                                                    if (s != null
                                                            && s.progress > 0f
                                                            && !s.ghostActive) {
                                                        s.onClockBlurModeChanged();
                                                    }
                                                    return result;
                                                })
                                ::unhook);
            }

            for (int i = 0; i < colorBatchSetters.size(); i++) {
                Method colorSetter = colorBatchSetters.get(i);
                hooks.add(
                        module.hook(colorSetter)
                                        .setId("dressroom-advanced.inline-seconds-color-" + i)
                                        .intercept(
                                                chain -> {
                                                    if (Boolean.TRUE.equals(
                                                            inColorBatchHook.get())) {
                                                        return chain.proceed();
                                                    }
                                                    inColorBatchHook.set(Boolean.TRUE);
                                                    try {
                                                        Object result = chain.proceed();
                                                        if (chain.getThisObject()
                                                                instanceof View clock) {
                                                            Session s =
                                                                    sessions.isEmpty()
                                                                            ? null
                                                                            : sessions.get(clock);
                                                            if (s != null
                                                                    && s.progress > 0f
                                                                    && !s.ghostActive) {
                                                                s.syncColorsFromRef();
                                                            }
                                                        }
                                                        return result;
                                                    } finally {
                                                        inColorBatchHook.remove();
                                                    }
                                                })
                                ::unhook);
            }

            if (setClockGravity != null) {
                hooks.add(
                        module.hook(setClockGravity)
                                        .setId("dressroom-advanced.inline-seconds-gravity-sync")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    syncActive((View) chain.getThisObject());
                                                    return result;
                                                })
                                ::unhook);
            }

            if (setScaleClock != null) {
                hooks.add(
                        module.hook(setScaleClock)
                                        .setId("dressroom-advanced.inline-seconds-scale-sync")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    syncActive((View) chain.getThisObject());
                                                    return result;
                                                })
                                ::unhook);
            }

            hooks.add(
                    module.hook(onDetached)
                                    .setId("dressroom-advanced.inline-seconds-detach")
                                    .intercept(
                                            chain -> {
                                                View clock = (View) chain.getThisObject();
                                                pendingTransfers.remove(clock);
                                                Session s =
                                                        sessions.isEmpty()
                                                                ? null
                                                                : sessions.get(clock);
                                                if (s != null) {
                                                    s.close(!s.canHandoffToPage());
                                                }
                                                return chain.proceed();
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(setShadow)
                                    .setId("dressroom-advanced.inline-seconds-shadow-sync")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                View clock = (View) chain.getThisObject();
                                                Session s =
                                                        sessions.isEmpty()
                                                                ? null
                                                                : sessions.get(clock);
                                                if (s != null && s.progress > 0f && !s.ghostActive) {
                                                    s.syncShadowsFromRef();
                                                    s.schedulePreDrawRefresh();
                                                }
                                                return result;
                                            })
                            ::unhook);

            hooks.add(
                    module.hook(clockBinding.render)
                                    .setId("dressroom-advanced.inline-seconds-font-sync")
                                    .intercept(
                                            chain -> {
                                                View clock =
                                                        chain.getThisObject() instanceof View v
                                                                ? v
                                                                : null;
                                                View prev = renderingClockFont.get();
                                                if (clock != null) renderingClockFont.set(clock);
                                                Object result;
                                                try {
                                                    result = chain.proceed();
                                                } finally {
                                                    if (prev == null) renderingClockFont.remove();
                                                    else renderingClockFont.set(prev);
                                                }
                                                if (clock != null) {
                                                    Session s =
                                                            sessions.isEmpty()
                                                                    ? null
                                                                    : sessions.get(clock);
                                                    if (s != null
                                                            && s.progress > 0f
                                                            && !s.ghostActive) {
                                                        s.onClockFontUpdated();
                                                        if (Boolean.TRUE.equals(inAxesHook.get())) {
                                                            syncedClockInHook.set(clock);
                                                        }
                                                    }
                                                }
                                                return result;
                                            })
                            ::unhook);

            if (binding.blurRetry != null) {
                hooks.add(
                        module.hook(binding.blurRetry.collect)
                                        .setId("dressroom-advanced.inline-seconds-blur-collect")
                                        .intercept(
                                                chain -> {
                                                    Object result = chain.proceed();
                                                    try {
                                                        View clock =
                                                                binding.blurRetry.clock(
                                                                        chain.getThisObject());
                                                        Session s =
                                                                clock == null
                                                                        ? null
                                                                        : sessions.get(clock);
                                                        if (s != null
                                                                && s.progress > 0f
                                                                && !s.ghostActive) {
                                                            s.syncColorsFromRef();
                                                            s.updateSecondsSemBlur(
                                                                    ClockSecondsLayout.revealAlpha(
                                                                            s.progress));
                                                            collectedSecondsBlur.set(clock);
                                                        }
                                                    } catch (ReflectiveOperationException
                                                            | RuntimeException ignored) {
                                                    }
                                                    return result;
                                                })
                                ::unhook);

                if (binding.applyAnimatedAlpha != null) {
                    hooks.add(
                            module.hook(binding.applyAnimatedAlpha)
                                            .setId(
                                                    "dressroom-advanced.inline-seconds-blur-alpha-anim")
                                            .intercept(
                                                    chain -> {
                                                        Object result = chain.proceed();
                                                        try {
                                                            View clock =
                                                                    binding.blurRetry.clock(
                                                                            chain.getThisObject());
                                                            Session s =
                                                                    clock == null
                                                                            ? null
                                                                            : sessions.get(clock);
                                                            if (s != null
                                                                    && s.progress > 0f
                                                                    && !s.ghostActive) {
                                                                s.syncColorsFromRef();
                                                            }
                                                        } catch (ReflectiveOperationException
                                                                | RuntimeException ignored) {
                                                        }
                                                        return result;
                                                    })
                                    ::unhook);
                }
            }

            module.deoptimize(binding.bindClock);
            module.deoptimize(binding.timeChanged);
            if (!clockBinding.early) module.deoptimize(binding.tap);
            module.deoptimize(setDozing);
            if (setMediaExpanded != null) module.deoptimize(setMediaExpanded);
            module.deoptimize(setShadow);
            module.deoptimize(clockBinding.render);
            if (binding.pageTransition != null) {
                module.deoptimize(binding.pageTransition.attach());
            }
            if (fontBinding != null) {
                module.deoptimize(fontBinding.fixed);
                module.deoptimize(fontBinding.variable);
            }
            for (Method axesMethod : axesTransitions) {
                module.deoptimize(axesMethod);
            }
            for (Method blurMethod : textBlurMethods) {
                module.deoptimize(blurMethod);
            }
            for (Method colorSetter : colorBatchSetters) {
                module.deoptimize(colorSetter);
            }
            if (removeBlur != null) {
                module.deoptimize(removeBlur);
            }
            if (setBlurEnabled != null) {
                module.deoptimize(setBlurEnabled);
            }
            if (binding.applyAnimatedAlpha != null) {
                module.deoptimize(binding.applyAnimatedAlpha);
            }
            hooks.commit();
        }

        installed.add(loader);
        observeExtras();
        android.util.Log.i(DressroomModule.TAG, "Inline clock seconds installed");
    }

    private static List<Method> collectHierarchyOverrides(
            ClassLoader loader, Class<?> baseClass, Method rootMethod) {
        if (rootMethod == null) return List.of();
        Set<Method> found = Collections.newSetFromMap(new java.util.LinkedHashMap<>());
        found.add(rootMethod);
        String name = rootMethod.getName();
        Class<?>[] params = rootMethod.getParameterTypes();
        for (String className : ELIGIBLE_CLOCKS) {
            try {
                Class<?> sub = Class.forName(className, false, loader);
                for (Class<?> cur = sub;
                        cur != null && baseClass.isAssignableFrom(cur);
                        cur = cur.getSuperclass()) {
                    try {
                        Method declared = cur.getDeclaredMethod(name, params);
                        declared.setAccessible(true);
                        found.add(declared);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return new ArrayList<>(found);
    }

    private static synchronized void observeExtras() {
        if (extrasObserved) return;
        extrasObserved = true;
        RuntimeExtras.listen(
                () ->
                        main.post(
                                () -> {
                                    boolean anyOpen = false;
                                    Context sampleCtx = null;
                                    for (Session session : snapshotSessions()) {
                                        Context ctx = session.clock.getContext();
                                        sampleCtx = ctx;
                                        if (!enabled(ctx)) {
                                            pinnedByUser = false;
                                            session.close(false);
                                        } else {
                                            if (session.isOpenOrOpening()) anyOpen = true;
                                            session.rescheduleAutoClose(AUTO_CLOSE_MS);
                                        }
                                    }
                                    if (sampleCtx != null) {
                                        if (!enabled(sampleCtx) || (!keepOpen(sampleCtx) && !anyOpen)) {
                                            pinnedByUser = false;
                                        } else if (keepOpen(sampleCtx) && anyOpen) {
                                            pinnedByUser = true;
                                        }
                                    }
                                }));
    }

    private static void scheduleWakeRestore() {
        final int gen = ++wakeRestoreGeneration;
        Runnable attempt =
                new Runnable() {
                    int tries;

                    @Override
                    public void run() {
                        if (gen != wakeRestoreGeneration
                                || !pinnedByUser
                                || sleeping
                                || !keyguardVisible
                                || lastDarkAmount > 0.01f) {
                            return;
                        }
                        Method gravityMethod = cachedGetClockGravity;
                        Method dateFirstMethod = cachedIsShowDateFirst;
                        if (dateFirstMethod == null) return;

                        View activePage = null;
                        ClockSecondsBinding binding = null;
                        View activeClock = null;
                        synchronized (pages) {
                            for (Map.Entry<View, ClockSecondsBinding> entry : pages.entrySet()) {
                                View page = entry.getKey();
                                ClockSecondsBinding b = entry.getValue();
                                if (page == null
                                        || !page.isAttachedToWindow()
                                        || !page.isShown()) continue;
                                String state = readPageState(page, b);
                                if (!"SMALL".equals(state) && !"EXPAND".equals(state)) continue;
                                View clock = readClock(page, b);
                                if (clock == null
                                        || !clock.isAttachedToWindow()
                                        || !clock.isShown()) continue;
                                activePage = page;
                                binding = b;
                                activeClock = clock;
                                break;
                            }
                        }
                        if (activePage == null || activeClock == null) {
                            if (++tries < 18) main.postDelayed(this, 35L);
                            return;
                        }
                        Context ctx = activeClock.getContext();
                        if (!enabled(ctx) || !keepOpen(ctx)) {
                            pinnedByUser = false;
                            return;
                        }
                        if (!ELIGIBLE_CLOCKS.contains(activeClock.getClass().getName())) {
                            return;
                        }
                        Set<String> blockers = clockBlockers.get(activeClock);
                        if (blockers != null) {
                            for (String r : blockers) {
                                if (CLOSE_BLOCKERS.contains(r)) {
                                    if (++tries < 18) main.postDelayed(this, 35L);
                                    return;
                                }
                            }
                        }
                        Session existing = sessions.get(activeClock);
                        if (existing != null && existing.isOpenOrOpening()) return;
                        if (pendingTransfers.contains(activeClock)) return;
                        scheduleTransferredOpen(
                                activePage,
                                activeClock,
                                binding,
                                gravityMethod,
                                dateFirstMethod,
                                AUTO_CLOSE_MS);
                    }
                };
        main.post(attempt);
    }

    private static void scheduleTransferredOpen(
            View page,
            View nextClock,
            ClockSecondsBinding binding,
            Method getClockGravity,
            Method isShowDateFirst,
            long remainingMs) {
        pendingTransfers.add(nextClock);
        Runnable attempt =
                new Runnable() {
                    int tries;

                    @Override
                    public void run() {
                        if (!pendingTransfers.contains(nextClock)) return;
                        if (readClock(page, binding) != nextClock) {
                            pendingTransfers.remove(nextClock);
                            return;
                        }
                        String state = readPageState(page, binding);
                        if (!"SMALL".equals(state) && !"EXPAND".equals(state)) {
                            pendingTransfers.remove(nextClock);
                            return;
                        }
                        if (!nextClock.isAttachedToWindow() || !nextClock.isShown()) {
                            if (++tries < 20) main.postDelayed(this, 25L);
                            else pendingTransfers.remove(nextClock);
                            return;
                        }
                        Target target = resolveTarget(nextClock, binding);
                        if (target == null) {
                            if (++tries < 20) main.postDelayed(this, 25L);
                            else pendingTransfers.remove(nextClock);
                            return;
                        }
                        pendingTransfers.remove(nextClock);
                        try {
                            Session existing = sessions.get(nextClock);
                            if (existing == null
                                    || existing.ghostActive
                                    || !existing.matches(target)) {
                                if (existing != null) existing.close(true);
                                existing =
                                        new Session(
                                                page,
                                                nextClock,
                                                binding,
                                                target,
                                                getClockGravity,
                                                isShowDateFirst);
                                sessions.put(nextClock, existing);
                            }
                            existing.openWithAutoClose(remainingMs);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            DressroomModule.note("Inline clock seconds transfer", error);
                        }
                    }
                };
        nextClock.post(attempt);
    }

    private enum TapOutcome {
        PROCEED,
        CONSUME
    }

    private static boolean handleEarlyTap(View root, ClockSecondsBinding binding, Method dateFirst) {
        View page = visibleClockPage(root, binding);
        if (page == null) return false;
        // Deferred provider discovery can run after the native clock page was already bound.
        pages.put(page, binding);
        return handleTap(0, 0, page, binding, null, dateFirst) == TapOutcome.CONSUME;
    }

    private static View visibleClockPage(View view, ClockSecondsBinding binding) {
        if (!view.isShown() || !view.isAttachedToWindow()) return null;
        if (binding.clockPageClass.isInstance(view)) {
            String state = readPageState(view, binding);
            View clock = readClock(view, binding);
            return ("SMALL".equals(state) || "EXPAND".equals(state))
                    && clock != null && clock.isShown() ? view : null;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View page = visibleClockPage(group.getChildAt(i), binding);
                if (page != null) return page;
            }
        }
        return null;
    }

    private static TapOutcome handleTap(
            int x,
            int y,
            View nativePage,
            ClockSecondsBinding fallbackBinding,
            Method getClockGravity,
            Method isShowDateFirst) {
        View activePage = null;
        ClockSecondsBinding binding = fallbackBinding;
        View activeClock = null;
        synchronized (pages) {
            for (Map.Entry<View, ClockSecondsBinding> entry : pages.entrySet()) {
                View page = entry.getKey();
                ClockSecondsBinding b = entry.getValue();
                if (nativePage != null && page != nativePage) continue;
                if (page == null || !page.isAttachedToWindow() || !page.isShown()) continue;
                String state = readPageState(page, b);
                if (!"SMALL".equals(state) && !"EXPAND".equals(state)) continue;
                View clock = readClock(page, b);
                if (clock == null || !clock.isAttachedToWindow() || !clock.isShown()) continue;
                activePage = page;
                binding = b;
                activeClock = clock;
                break;
            }
        }
        if (activePage == null || activeClock == null) return TapOutcome.PROCEED;

        Session existing = sessions.get(activeClock);
        Target target = resolveTarget(activeClock, binding);
        Rect nativeRect = nativePage != null ? null : readContentsRect(activePage, binding);
        boolean hitNative = nativePage != null || nativeRect != null && nativeRect.contains(x, y);
        boolean hitExpanded =
                existing != null
                        && existing.progress > 0f
                        && !existing.ghostActive
                        && existing.containsScreenPoint(x, y);
        boolean hitTimeGroup =
                target != null && Session.viewContainsScreenPoint(target.timeGroup, x, y);

        if (!hitNative && !hitExpanded && !hitTimeGroup) {
            if (existing != null
                    && existing.isOpenOrOpening()
                    && !keepOpen(activeClock.getContext())) {
                pinnedByUser = false;
                existing.close(false);
            }
            return TapOutcome.PROCEED;
        }

        if (!enabled(activeClock.getContext())
                || !ELIGIBLE_CLOCKS.contains(activeClock.getClass().getName())) {
            pinnedByUser = false;
            if (existing != null) existing.close(true);
            return TapOutcome.PROCEED;
        }

        if (target == null) {
            if (existing != null) existing.close(true);
            return TapOutcome.PROCEED;
        }

        try {
            Session session = existing;
            if (session == null || session.ghostActive || !session.matches(target)) {
                if (session != null) session.close(true);
                session =
                        new Session(
                                activePage,
                                activeClock,
                                binding,
                                target,
                                getClockGravity,
                                isShowDateFirst);
                sessions.put(activeClock, session);
            }
            if (session.isOpenOrOpening()) {
                pinnedByUser = false;
                session.close(false);
            } else {
                pinnedByUser = true;
                session.open();
            }
            return TapOutcome.CONSUME;
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Inline clock seconds fallback", error);
            if (existing != null) existing.close(true);
            return TapOutcome.PROCEED;
        }
    }

    private static View readClock(View page, ClockSecondsBinding binding) {
        try {
            Object v = binding.clockView.get(page);
            return v instanceof View view ? view : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static String readPageState(View page, ClockSecondsBinding binding) {
        try {
            Object state = binding.pageState.get(page);
            return state instanceof Enum<?> e ? e.name() : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Rect readContentsRect(View page, ClockSecondsBinding binding) {
        try {
            Object r = binding.contentsPosition.invoke(page);
            return r instanceof Rect rect ? rect : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void captureLastGlobal(View clock) {
        if (clock == null || sessions.isEmpty()) return;
        Session session = sessions.get(clock);
        if (session != null) session.captureLastGlobalIfShown();
    }

    private static void syncActive(View clock) {
        if (clock == null || sessions.isEmpty()) return;
        Session session = sessions.get(clock);
        if (session != null && session.progress > 0f && !session.ghostActive) {
            session.schedulePreDrawRefresh();
        }
    }

    private static void closeClock(View clock, boolean immediate) {
        if (clock == null || sessions.isEmpty()) return;
        Session session = sessions.get(clock);
        if (session != null) session.close(immediate);
    }

    private static void closeAll(boolean immediate) {
        for (Session session : snapshotSessions()) {
            session.close(immediate);
        }
    }

    private static List<Session> snapshotSessions() {
        synchronized (sessions) {
            return new ArrayList<>(sessions.values());
        }
    }

    private record Target(
            ViewGroup stage,
            ViewGroup timeGroup,
            TextView refDigit,
            TextView refColon,
            View mmAnchor,
            boolean vertical,
            boolean stretched) {}

    private static Target resolveTarget(View clock, ClockSecondsBinding binding) {
        ClockSecondsBinding.Ids ids = binding.ids(clock);
        if (ids.timeAreaContainer() == 0) return null;
        View containerView = clock.findViewById(ids.timeAreaContainer());
        if (!(containerView instanceof ViewGroup container) || !container.isShown()) return null;

        boolean vertical = VERTICAL_CLOCK.equals(clock.getClass().getName());
        if (vertical) {
            ViewGroup stage =
                    container.getChildCount() > 0
                                    && container.getChildAt(0) instanceof FrameLayout frame
                            ? frame
                            : container;
            ViewGroup stretchGroup = findGroup(stage, ids.stretchTimeArea());
            if (shown(stretchGroup)) {
                TextView ref =
                        firstShownText(
                                stretchGroup, ids.stretchMinute1st(), ids.stretchMinute10th());
                if (ref != null && stretchGroup.getWidth() > 0 && stretchGroup.getHeight() > 0) {
                    return new Target(stage, stretchGroup, ref, null, ref, true, true);
                }
            }
            ViewGroup normalGroup = findGroup(stage, ids.timeArea());
            if (shown(normalGroup)) {
                TextView ref =
                        firstShownText(
                                normalGroup, ids.minute1st(), ids.minute10th(), ids.minute());
                if (ref != null && normalGroup.getWidth() > 0 && normalGroup.getHeight() > 0) {
                    return new Target(stage, normalGroup, ref, null, ref, true, false);
                }
            }
            return null;
        }

        ViewGroup stage = container;
        ViewGroup stretchGroup = findGroup(stage, ids.stretchTimeArea());
        if (shown(stretchGroup)) {
            TextView ref =
                    firstShownText(stretchGroup, ids.stretchMinute1st(), ids.stretchMinute10th());
            TextView colon = firstShownText(stretchGroup, ids.stretchColon());
            if (ref != null && stretchGroup.getWidth() > 0 && stretchGroup.getHeight() > 0) {
                return new Target(stage, stretchGroup, ref, colon, stretchGroup, false, true);
            }
        }
        ViewGroup normalGroup = findGroup(stage, ids.timeArea());
        if (shown(normalGroup)) {
            TextView ref =
                    firstShownText(
                            normalGroup,
                            ids.minute1st(),
                            ids.minute10th(),
                            ids.minute(),
                            ids.hourMin());
            TextView colon = firstShownText(normalGroup, ids.colon());
            if (ref != null && normalGroup.getWidth() > 0 && normalGroup.getHeight() > 0) {
                return new Target(stage, normalGroup, ref, colon, normalGroup, false, false);
            }
        }
        ViewGroup hmGroup = findGroup(stage, ids.timeAreaHourMin());
        if (shown(hmGroup)) {
            TextView ref = firstShownText(hmGroup, ids.hourMin());
            if (ref != null && hmGroup.getWidth() > 0 && hmGroup.getHeight() > 0) {
                return new Target(stage, hmGroup, ref, null, hmGroup, false, false);
            }
        }
        return null;
    }

    private static ViewGroup findGroup(View root, int id) {
        if (root == null || id == 0) return null;
        View v = root.findViewById(id);
        return v instanceof ViewGroup group ? group : null;
    }

    private static boolean shown(View v) {
        return v != null && v.getVisibility() == View.VISIBLE && v.isShown();
    }

    private static TextView firstShownText(View root, int... ids) {
        for (int id : ids) {
            if (id == 0) continue;
            View v = root.findViewById(id);
            if (v instanceof TextView tv && tv.getVisibility() == View.VISIBLE) return tv;
        }
        return null;
    }

    /**
     * 1x1 footprint host inside the clock's FrameLayout stage so adding ':SS' never changes the
     * clock's measured width or height by even a single pixel.
     */
    private static final class ZeroFootprintHost extends FrameLayout {
        final LinearLayout row;

        ZeroFootprintHost(Context context, LinearLayout row) {
            super(context);
            this.row = row;
            setClipChildren(false);
            setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            row.measure(unspec, unspec);
            setMeasuredDimension(1, 1);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        }
    }

    private record SavedClip(ViewGroup group, boolean clipChildren, boolean clipToPadding) {}

    private static final class Session {
        final View page;
        final View clock;
        final ClockSecondsBinding binding;
        final Method getClockGravity;
        final Method isShowDateFirst;
        Target target;

        final ZeroFootprintHost host;
        final LinearLayout secRow;
        final TextView colonView;
        final TextView sec10View;
        final TextView sec1View;
        final float density;
        final int padX;
        final int padY;
        final float maxSigmaX;
        final float glassMaxSigmaX;
        final float maxSigmaY;
        final float edgeMarginPx;
        final float maxSlidePx;
        final float verticalGapPx;

        final List<SavedClip> savedClips = new ArrayList<>();
        final Matrix lastSecGlobal = new Matrix();
        final View.OnLayoutChangeListener layoutWatcher;
        final Runnable tickTask = this::onSecondTick;
        final Runnable autoCloseTask =
                () -> {
                    pinnedByUser = false;
                    close(false);
                };

        View secBlurSurface;
        Object fallbackBlurTemplate;
        ValueAnimator animator;
        ValueAnimator ghostAnimator;
        ValueAnimator mediaFollowAnimator;
        ClockSecondsLayout.Plan plan;
        Typeface lastSyncedTypeface;
        Object lastSyncedFixedTypeface;
        int lastSyncedFixedType = Integer.MIN_VALUE;
        float lastSyncedTextSize = -1f;
        String lastSyncedAxes;
        String lastTimeGroupSignature;
        boolean hasInitialSecondsText;
        boolean fontPreDrawPending;
        float lastSigmaX;
        float lastSigmaY;
        ColorStateList lastRefColors;
        boolean lastRefPathMode;
        int lastRefPathColor;
        Shader lastRefShader;
        boolean lastContrastOverride;
        boolean colorsInitialized;
        float lastShadowRadius = Float.NaN;
        float lastShadowDx;
        float lastShadowDy;
        int lastShadowColor;
        boolean lastShadowContrast;
        float lastPlanHmL = Float.NaN;
        float lastPlanHmT;
        float lastPlanHmR;
        float lastPlanHmB;
        float lastPlanMmR;
        float lastPlanWinL;
        float lastPlanWinR;
        float lastPlanRootW;
        int lastPlanGravity;
        boolean lastPlanDateAbove;
        long autoCloseDeadlineUptimeMs;
        boolean basePivotSet;
        float basePivotX;
        float basePivotY;
        float baseScaleX = 1f;
        float baseScaleY = 1f;
        float baseTx;
        float baseTy;
        float secBaseX;
        float secBaseY;
        float progress;
        boolean targetOpen;
        boolean hasLastGlobal;
        boolean ghostActive;
        boolean preDrawPending;

        Session(
                View page,
                View clock,
                ClockSecondsBinding binding,
                Target target,
                Method getClockGravity,
                Method isShowDateFirst)
                throws ReflectiveOperationException {
            this.page = page;
            this.clock = clock;
            this.binding = binding;
            this.target = target;
            this.getClockGravity = getClockGravity;
            this.isShowDateFirst = isShowDateFirst;

            Context ctx = clock.getContext();
            this.density = ctx.getResources().getDisplayMetrics().density;
            this.maxSigmaX = 18f * density;
            this.glassMaxSigmaX = 10f * density;
            this.maxSigmaY = 3.2f * density;
            this.padX = Math.round(30f * density);
            this.padY = Math.round(10f * density);
            this.edgeMarginPx = 16f * density;
            this.maxSlidePx = 26f * density;
            this.verticalGapPx = 6f * density;

            boolean useGlyphClass = binding.glyphTextClass.isInstance(target.refDigit);
            this.colonView = createText(ctx, useGlyphClass);
            this.sec10View = createText(ctx, useGlyphClass);
            this.sec1View = createText(ctx, useGlyphClass);

            this.secRow = new LinearLayout(ctx);
            this.secRow.setOrientation(LinearLayout.HORIZONTAL);
            this.secRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            this.secRow.setGravity(Gravity.TOP);
            this.secRow.setClipChildren(false);
            this.secRow.setClipToPadding(false);
            this.secRow.setPadding(padX, padY, padX, padY);
            this.secRow.addView(
                    colonView,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            this.secRow.addView(
                    sec10View,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            this.secRow.addView(
                    sec1View,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));

            this.host = new ZeroFootprintHost(ctx, secRow);
            this.host.setVisibility(View.GONE);
            this.layoutWatcher =
                    (v, l, t, r, b, ol, ot, or, ob) -> {
                        if (progress > 0f
                                && !ghostActive
                                && (r - l != or - ol || b - t != ob - ot || l != ol || t != ot)) {
                            schedulePreDrawRefresh();
                        }
                    };
        }

        private TextView createText(Context context, boolean glyph)
                throws ReflectiveOperationException {
            TextView tv = glyph ? binding.newGlyphView(context) : new TextView(context);
            tv.setIncludeFontPadding(false);
            tv.setSingleLine(true);
            tv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            return tv;
        }

        boolean matches(Target next) {
            return target.stage == next.stage
                    && target.timeGroup == next.timeGroup
                    && target.vertical == next.vertical
                    && binding.glyphTextClass.isInstance(target.refDigit)
                            == binding.glyphTextClass.isInstance(next.refDigit);
        }

        boolean isOpenOrOpening() {
            return targetOpen && !ghostActive;
        }

        long remainingAutoCloseMs() {
            if (autoCloseDeadlineUptimeMs <= 0L) return AUTO_CLOSE_MS;
            return Math.max(1_500L, autoCloseDeadlineUptimeMs - SystemClock.uptimeMillis());
        }

        boolean canHandoffToPage() {
            return progress > 0.02f
                    && hasLastGlobal
                    && !ghostActive
                    && page instanceof ViewGroup pageGroup
                    && pageGroup.isAttachedToWindow()
                    && pageGroup.isShown();
        }

        void captureLastGlobalIfShown() {
            if (progress > 0f && !ghostActive && secRow.isShown() && secRow.getWidth() > 0) {
                secRow.transformMatrixToGlobal(lastSecGlobal);
                hasLastGlobal = true;
            }
        }

        void open() {
            openWithAutoClose(AUTO_CLOSE_MS);
        }

        void openWithAutoClose(long delayMs) {
            Target fresh = resolveTarget(clock, binding);
            if (fresh == null) return;
            target = fresh;
            if (host.getParent() == null) {
                relaxAncestorClipping(target.stage, page);
                captureBaseline();
                target.stage.addView(
                        host,
                        new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.LEFT));
                target.timeGroup.addOnLayoutChangeListener(layoutWatcher);
            }
            host.setVisibility(View.VISIBLE);
            hasInitialSecondsText = false;
            colorsInitialized = false;
            lastShadowRadius = Float.NaN;
            lastPlanHmL = Float.NaN;
            syncStyleAndText(true);
            measureSecondsAndBuildPlan(true);
            lastTimeGroupSignature = computeTimeGroupSignature(target.timeGroup);

            targetOpen = true;
            scheduleSecondTicks();
            rescheduleAutoClose(delayMs);
            animateTo(1f, OPEN_DURATION_MS, OPEN_EASE);
        }

        void close(boolean immediate) {
            main.removeCallbacks(autoCloseTask);
            autoCloseDeadlineUptimeMs = 0L;
            cancelMediaFollow();
            if (ghostActive) {
                if (immediate) finishGhost();
                return;
            }
            targetOpen = false;
            if (immediate) {
                cancelAnimator();
                main.removeCallbacks(tickTask);
                applyFrame(0f, false);
                detachFromClock();
                clearSecondsSemBlur(true);
                sessions.remove(clock, this);
                return;
            }
            if (!clock.isAttachedToWindow() || !clock.isShown()) {
                if (canHandoffToPage()) {
                    handoffFadeOut((ViewGroup) page);
                } else {
                    close(true);
                }
                return;
            }
            if (animator != null && animator.isRunning()) {
                return;
            }
            animateTo(0f, CLOSE_DURATION_MS, CLOSE_EASE);
        }

        void rescheduleAutoClose(long delayMs) {
            main.removeCallbacks(autoCloseTask);
            if (targetOpen && !ghostActive && !keepOpen(clock.getContext())) {
                long clamped = Math.max(500L, delayMs);
                autoCloseDeadlineUptimeMs = SystemClock.uptimeMillis() + clamped;
                main.postDelayed(autoCloseTask, clamped);
            } else {
                autoCloseDeadlineUptimeMs = 0L;
            }
        }

        void startMediaFollowSync() {
            if (ghostActive || progress <= 0f) return;
            syncDuringClockMotion();
            cancelMediaFollow();
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(MEDIA_FOLLOW_MS);
            va.addUpdateListener(
                    anim -> {
                        if (ghostActive || progress <= 0f || !clock.isAttachedToWindow()) {
                            cancelMediaFollow();
                            return;
                        }
                        if (clock.isShown()) {
                            syncDuringClockMotion();
                        }
                    });
            va.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mediaFollowAnimator == animation) mediaFollowAnimator = null;
                        }
                    });
            mediaFollowAnimator = va;
            va.start();
        }

        private void cancelMediaFollow() {
            if (mediaFollowAnimator != null) {
                ValueAnimator running = mediaFollowAnimator;
                mediaFollowAnimator = null;
                if (running.isRunning()) running.cancel();
            }
        }

        void onClockFontUpdated() {
            if (ghostActive || progress <= 0f || host.getParent() == null) return;
            if (!clock.isAttachedToWindow() || !clock.isShown()) return;
            LockscreenFrameBoost.pulse(clock);
            LockscreenFrameBoost.pulse(host);
            if (ensureTarget() == null) return;
            if (didFontSignatureChange(target.refDigit) || !hasInitialSecondsText) {
                syncStyleAndText(true);
                measureSecondsAndBuildPlan(true);
            } else {
                syncColorsFromRef();
                syncShadowsFromRef();
                measureSecondsAndBuildPlanIfMoved();
            }
            applyFrame(progress, false);
        }

        void schedulePreDrawFontSync() {
            if (fontPreDrawPending || preDrawPending || ghostActive || !clock.isAttachedToWindow()) {
                return;
            }
            ViewTreeObserver vto = clock.getViewTreeObserver();
            if (vto == null || !vto.isAlive()) {
                clock.post(this::onClockFontUpdated);
                return;
            }
            fontPreDrawPending = true;
            vto.addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            fontPreDrawPending = false;
                            ViewTreeObserver current = clock.getViewTreeObserver();
                            if (current != null && current.isAlive()) {
                                current.removeOnPreDrawListener(this);
                            }
                            onClockFontUpdated();
                            return true;
                        }
                    });
        }

        void onClockBlurModeChanged() {
            if (ghostActive || progress <= 0f || host.getParent() == null) return;
            colorsInitialized = false;
            syncColorsFromRef();
            if (isClockGlassBlurActive()) {
                updateSecondsSemBlur(ClockSecondsLayout.revealAlpha(progress));
            } else {
                clearSecondsSemBlur(false);
            }
        }

        void syncDuringClockMotion() {
            if (ghostActive || progress <= 0f || host.getParent() == null) return;
            if (!clock.isAttachedToWindow() || !clock.isShown()) return;
            LockscreenFrameBoost.pulse(clock);
            LockscreenFrameBoost.pulse(host);
            if (ensureTarget() == null) return;
            if (didFontSignatureChange(target.refDigit) || !hasInitialSecondsText) {
                syncStyleAndText(true);
                measureSecondsAndBuildPlan(true);
                applyFrame(progress, false);
            } else {
                syncColorsFromRef();
                syncShadowsFromRef();
                if (measureSecondsAndBuildPlanIfMoved()) {
                    applyFrame(progress, false);
                }
            }
        }

        private Target ensureTarget() {
            if (target != null
                    && target.stage.isAttachedToWindow()
                    && shown(target.timeGroup)
                    && target.refDigit.getVisibility() == View.VISIBLE
                    && target.timeGroup.getWidth() > 0
                    && target.timeGroup.getHeight() > 0) {
                return target;
            }
            Target fresh = resolveTarget(clock, binding);
            if (fresh != null) updateTargetIfChanged(fresh);
            return fresh;
        }

        private void updateTargetIfChanged(Target fresh) {
            if (fresh.timeGroup != target.timeGroup) {
                restoreTimeGroupBaseline();
                target.timeGroup.removeOnLayoutChangeListener(layoutWatcher);
                target = fresh;
                captureBaseline();
                target.timeGroup.addOnLayoutChangeListener(layoutWatcher);
                lastPlanHmL = Float.NaN;
                lastTimeGroupSignature = null;
                colorsInitialized = false;
                lastShadowRadius = Float.NaN;
            } else if (fresh.refDigit != target.refDigit) {
                target = fresh;
                colorsInitialized = false;
                lastShadowRadius = Float.NaN;
            } else {
                target = fresh;
            }
        }

        private boolean didFontSignatureChange(TextView ref) {
            if (ref == null) return false;
            String axes = readRefAxes(ref);
            return ref.getTypeface() != lastSyncedTypeface
                    || readFixedTypeface(ref) != lastSyncedFixedTypeface
                    || readFixedFontType(ref) != lastSyncedFixedType
                    || Float.compare(ref.getTextSize(), lastSyncedTextSize) != 0
                    || !Objects.equals(axes, lastSyncedAxes);
        }

        private Object readFixedTypeface(TextView tv) {
            try {
                if (binding.glyphTextClass.isInstance(tv)) {
                    return binding.glyphFont.fixed().get(tv);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            return null;
        }

        private int readFixedFontType(TextView tv) {
            try {
                if (binding.glyphTextClass.isInstance(tv)) {
                    return binding.glyphFont.fontType().getInt(tv);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            return 0;
        }

        private String readRefAxes(TextView ref) {
            try {
                if (binding.glyphTextClass.isInstance(ref)) {
                    return (String) binding.glyphFont.variation().get(ref);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
            return Build.VERSION.SDK_INT >= 26 ? ref.getFontVariationSettings() : null;
        }

        void reapplyTransforms() {
            if (plan == null || ghostActive || progress <= 0f) return;
            target.timeGroup.setPivotX(0f);
            target.timeGroup.setPivotY(0f);
            float s = ClockSecondsLayout.scaleAt(plan, progress);
            target.timeGroup.setScaleX(baseScaleX * s);
            target.timeGroup.setScaleY(baseScaleY * s);
            target.timeGroup.setTranslationX(
                    ClockSecondsLayout.timeGroupTx(
                            plan, progress, target.timeGroup.getLeft(), baseTx));
            target.timeGroup.setTranslationY(
                    ClockSecondsLayout.timeGroupTy(
                            plan, progress, target.timeGroup.getTop(), baseTy));
        }

        void onClockTimeChanged() {
            if (ghostActive || progress <= 0f) return;
            reapplyTransforms();
            syncColorsFromRef();
            if (ensureTarget() == null) {
                schedulePreDrawRefresh();
                return;
            }
            String sig = computeTimeGroupSignature(target.timeGroup);
            if (!Objects.equals(sig, lastTimeGroupSignature)) {
                lastTimeGroupSignature = sig;
                schedulePreDrawRefresh();
            }
        }

        private static String computeTimeGroupSignature(ViewGroup group) {
            if (group == null) return "";
            StringBuilder sb = new StringBuilder(16);
            appendVisibleText(group, sb);
            return sb.toString();
        }

        private static void appendVisibleText(ViewGroup group, StringBuilder sb) {
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                if (child instanceof TextView tv) {
                    CharSequence t = tv.getText();
                    if (t != null) sb.append(t);
                    sb.append('|');
                } else if (child instanceof ViewGroup nested) {
                    appendVisibleText(nested, sb);
                }
            }
        }

        void schedulePreDrawRefresh() {
            if (preDrawPending || ghostActive || !clock.isAttachedToWindow()) return;
            ViewTreeObserver vto = clock.getViewTreeObserver();
            if (vto == null || !vto.isAlive()) {
                clock.post(this::refreshAppearanceAndGeometry);
                return;
            }
            preDrawPending = true;
            vto.addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            preDrawPending = false;
                            ViewTreeObserver current = clock.getViewTreeObserver();
                            if (current != null && current.isAlive()) {
                                current.removeOnPreDrawListener(this);
                            }
                            refreshAppearanceAndGeometry();
                            return true;
                        }
                    });
            clock.invalidate();
        }

        void refreshAppearanceAndGeometry() {
            if (progress <= 0f || ghostActive || host.getParent() == null) return;
            if (!clock.isAttachedToWindow() || !clock.isShown()) {
                close(false);
                return;
            }
            Target fresh = resolveTarget(clock, binding);
            if (fresh == null) {
                close(false);
                return;
            }
            updateTargetIfChanged(fresh);
            primeGroupGlyphs(target.timeGroup);
            syncStyleAndText(true);
            measureSecondsAndBuildPlan(true);
            lastTimeGroupSignature = computeTimeGroupSignature(target.timeGroup);
            applyFrame(progress, false);
        }

        private void primeGroupGlyphs(ViewGroup group) {
            if (group == null || binding.glyphPath == null) return;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                if (binding.glyphTextClass.isInstance(child)) {
                    try {
                        if (binding.glyphPath.invoke(child) == null) {
                            child.draw(PRIME_CANVAS);
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                } else if (child instanceof ViewGroup nested) {
                    primeGroupGlyphs(nested);
                }
            }
        }

        private void primeSecondsGlyphs() {
            if (!binding.glyphTextClass.isInstance(colonView)) return;
            colonView.draw(PRIME_CANVAS);
            sec10View.draw(PRIME_CANVAS);
            sec1View.draw(PRIME_CANVAS);
        }

        void syncColorsFromRef() {
            TextView ref = target.refDigit;
            if (ref == null) return;
            ColorStateList refColors = ref.getTextColors();
            boolean refPathMode = false;
            int refPathColor = ref.getCurrentTextColor();
            try {
                if (binding.pathMode != null
                        && binding.pathTextColor != null
                        && binding.pathMode.getDeclaringClass().isInstance(ref)) {
                    refPathMode = binding.pathMode.getBoolean(ref);
                    refPathColor = binding.pathTextColor.getInt(ref);
                }
            } catch (IllegalAccessException ignored) {
            }
            Shader refShader = refPathMode ? null : ref.getPaint().getShader();
            boolean anyContrast =
                    MediaClockContrast.overridesDraw(colonView)
                            || MediaClockContrast.overridesDraw(sec10View)
                            || MediaClockContrast.overridesDraw(sec1View);
            if (colorsInitialized
                    && !anyContrast
                    && !lastContrastOverride
                    && refColors == lastRefColors
                    && refPathMode == lastRefPathMode
                    && refPathColor == lastRefPathColor
                    && refShader == lastRefShader) {
                return;
            }
            ColorStateList effectiveColors = refColors;
            if (refPathMode) {
                int pathRgb = refPathColor & 0x00ffffff;
                if (effectiveColors == null
                        || (effectiveColors.getDefaultColor() & 0x00ffffff) != pathRgb) {
                    effectiveColors = ColorStateList.valueOf(0xff000000 | pathRgb);
                }
            }

            for (TextView dest : new TextView[] {colonView, sec10View, sec1View}) {
                boolean contrastActive = MediaClockContrast.overridesDraw(dest);
                try {
                    if (binding.pathMode != null
                            && binding.pathTextColor != null
                            && binding.pathMode.getDeclaringClass().isInstance(dest)) {
                        if (!contrastActive && effectiveColors != null) {
                            binding.pathMode.setBoolean(dest, false);
                            dest.setTextColor(effectiveColors);
                        }
                        binding.pathMode.setBoolean(dest, refPathMode);
                        binding.pathTextColor.setInt(dest, refPathColor);
                    } else if (!contrastActive && effectiveColors != null) {
                        dest.setTextColor(effectiveColors);
                    }
                } catch (IllegalAccessException ignored) {
                    if (!contrastActive && effectiveColors != null) {
                        dest.setTextColor(effectiveColors);
                    }
                }
                if (!contrastActive) {
                    dest.getPaint().setShader(refShader);
                }
                dest.invalidate();
            }
            lastRefColors = refColors;
            lastRefPathMode = refPathMode;
            lastRefPathColor = refPathColor;
            lastRefShader = refShader;
            lastContrastOverride = anyContrast;
            colorsInitialized = true;
        }

        void syncShadowsFromRef() {
            TextView ref = target.refDigit;
            if (ref == null) return;
            float radius = ref.getShadowRadius();
            float dx = ref.getShadowDx();
            float dy = ref.getShadowDy();
            int color = ref.getShadowColor();
            boolean anyContrast =
                    MediaClockContrast.overridesDraw(colonView)
                            || MediaClockContrast.overridesDraw(sec10View)
                            || MediaClockContrast.overridesDraw(sec1View);
            if (!anyContrast
                    && !lastShadowContrast
                    && Float.compare(radius, lastShadowRadius) == 0
                    && Float.compare(dx, lastShadowDx) == 0
                    && Float.compare(dy, lastShadowDy) == 0
                    && color == lastShadowColor) {
                return;
            }
            for (TextView dest : new TextView[] {colonView, sec10View, sec1View}) {
                if (!MediaClockContrast.overridesDraw(dest)) {
                    dest.setShadowLayer(radius, dx, dy, color);
                }
            }
            lastShadowRadius = radius;
            lastShadowDx = dx;
            lastShadowDy = dy;
            lastShadowColor = color;
            lastShadowContrast = anyContrast;
        }

        private void captureBaseline() {
            if (Build.VERSION.SDK_INT >= 28) {
                basePivotSet = target.timeGroup.isPivotSet();
            }
            basePivotX = target.timeGroup.getPivotX();
            basePivotY = target.timeGroup.getPivotY();
            baseScaleX = target.timeGroup.getScaleX();
            baseScaleY = target.timeGroup.getScaleY();
            baseTx = target.timeGroup.getTranslationX();
            baseTy = target.timeGroup.getTranslationY();
        }

        private void restoreTimeGroupBaseline() {
            target.timeGroup.setScaleX(baseScaleX);
            target.timeGroup.setScaleY(baseScaleY);
            target.timeGroup.setTranslationX(baseTx);
            target.timeGroup.setTranslationY(baseTy);
            if (Build.VERSION.SDK_INT >= 28 && !basePivotSet) {
                target.timeGroup.resetPivot();
            } else {
                target.timeGroup.setPivotX(basePivotX);
                target.timeGroup.setPivotY(basePivotY);
            }
        }

        private void relaxAncestorClipping(View start, View stopInclusive) {
            savedClips.clear();
            Set<ViewGroup> seen = Collections.newSetFromMap(new WeakHashMap<>());
            View current = start;
            while (current instanceof ViewGroup group && seen.add(group)) {
                savedClips.add(
                        new SavedClip(group, group.getClipChildren(), group.getClipToPadding()));
                group.setClipChildren(false);
                group.setClipToPadding(false);
                if (current == stopInclusive) break;
                current = current.getParent() instanceof View pv ? pv : null;
            }
        }

        private void restoreAncestorClipping() {
            for (SavedClip saved : savedClips) {
                saved.group.setClipChildren(saved.clipChildren);
                saved.group.setClipToPadding(saved.clipToPadding);
            }
            savedClips.clear();
        }

        private void detachFromClock() {
            cancelMediaFollow();
            secRow.setRenderEffect(null);
            lastSigmaX = 0f;
            lastSigmaY = 0f;
            target.timeGroup.removeOnLayoutChangeListener(layoutWatcher);
            restoreTimeGroupBaseline();
            if (host.getParent() instanceof ViewGroup parent) {
                parent.removeView(host);
            }
            host.setVisibility(View.GONE);
            restoreAncestorClipping();
            updateClockGlassBlurNow();
        }

        private void handoffFadeOut(ViewGroup pageGroup) {
            cancelAnimator();
            cancelMediaFollow();
            main.removeCallbacks(tickTask);
            sessions.remove(clock, this);

            float startProgress = progress;
            float startAlpha = ClockSecondsLayout.revealAlpha(startProgress);
            boolean glassActive = isClockGlassBlurActive();

            detachFromClock();
            if (startAlpha <= 0.02f || secRow.getMeasuredWidth() <= 0) {
                clearSecondsSemBlur(true);
                return;
            }

            ghostActive = true;
            relaxAncestorClipping(pageGroup, pageGroup);
            host.setScaleX(1f);
            host.setScaleY(1f);
            host.setTranslationX(0f);
            host.setTranslationY(0f);
            host.setVisibility(View.VISIBLE);
            pageGroup.addView(
                    host, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.LEFT));
            host.measure(
                    View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY));
            host.layout(0, 0, 1, 1);

            Matrix hostGlobal = new Matrix();
            host.transformMatrixToGlobal(hostGlobal);
            Matrix hostInv = new Matrix();
            if (!hostGlobal.invert(hostInv)) {
                finishGhost();
                return;
            }

            float[] pts = {0f, 0f, 1f, 0f, 0f, 1f};
            lastSecGlobal.mapPoints(pts);
            hostInv.mapPoints(pts);
            float startX = pts[0];
            float startY = pts[1];
            float startSx =
                    Math.max(0.1f, (float) Math.hypot(pts[2] - pts[0], pts[3] - pts[1]));
            float startSy =
                    Math.max(0.1f, (float) Math.hypot(pts[4] - pts[0], pts[5] - pts[1]));
            float rowH = Math.max(1f, secRow.getMeasuredHeight());
            float peakSigmaX = glassActive ? glassMaxSigmaX : maxSigmaX;

            secRow.setPivotX(0f);
            secRow.setPivotY(0f);
            LockscreenFrameBoost.pulse(host, HANDOFF_CLOSE_MS + 80L);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(HANDOFF_CLOSE_MS);
            va.setInterpolator(CLOSE_EASE);
            va.addUpdateListener(
                    anim -> {
                        if (!pageGroup.isAttachedToWindow() || !pageGroup.isShown()) {
                            finishGhost();
                            return;
                        }
                        LockscreenFrameBoost.pulse(host);
                        float t = ((Number) anim.getAnimatedValue()).floatValue();
                        float shrink = 1f - 0.10f * t;
                        secRow.setScaleX(startSx * shrink);
                        secRow.setScaleY(startSy * shrink);
                        secRow.setTranslationX(startX - 12f * density * t);
                        secRow.setTranslationY(startY + 0.05f * t * rowH * startSy);
                        float fade = (1f - t) * (1f - t);
                        float alphaNow = startAlpha * fade;
                        secRow.setAlpha(alphaNow);

                        float arc = (float) Math.sin(Math.PI * Math.min(1f, t * 1.05f));
                        float sx = peakSigmaX * 0.65f * arc;
                        float sy = maxSigmaY * 0.65f * arc;
                        applyMotionRenderEffect(sx > 0.25f ? sx : 0f, sx > 0.25f ? sy : 0f);
                        if (glassActive) {
                            updateSecondsSemBlur(alphaNow);
                        }
                    });
            va.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishGhost();
                        }
                    });
            ghostAnimator = va;
            va.start();
        }

        private void finishGhost() {
            if (!ghostActive && ghostAnimator == null) return;
            ghostActive = false;
            if (ghostAnimator != null) {
                ValueAnimator running = ghostAnimator;
                ghostAnimator = null;
                if (running.isRunning()) running.cancel();
            }
            secRow.setRenderEffect(null);
            lastSigmaX = 0f;
            lastSigmaY = 0f;
            if (host.getParent() instanceof ViewGroup parent) {
                parent.removeView(host);
            }
            host.setVisibility(View.GONE);
            restoreAncestorClipping();
            clearSecondsSemBlur(true);
        }

        private void animateTo(float end, long fullDuration, TimeInterpolator interpolator) {
            cancelAnimator();
            float start = progress;
            float distance = Math.abs(end - start);
            if (distance < 0.002f) {
                applyFrame(end, true);
                if (end == 0f) finishClosed();
                return;
            }
            long duration = Math.max(90L, Math.round(fullDuration * distance));
            LockscreenFrameBoost.pulse(clock, duration + 80L);
            LockscreenFrameBoost.pulse(host, duration + 80L);
            ValueAnimator va = ValueAnimator.ofFloat(start, end);
            va.setDuration(duration);
            va.setInterpolator(interpolator);
            va.addUpdateListener(
                    anim -> {
                        LockscreenFrameBoost.pulse(clock);
                        LockscreenFrameBoost.pulse(host);
                        applyFrame(((Number) anim.getAnimatedValue()).floatValue(), false);
                    });
            va.addListener(
                    new AnimatorListenerAdapter() {
                        private boolean canceled;

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            canceled = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animator == animation) animator = null;
                            if (!canceled) {
                                if (!targetOpen && progress <= 0.005f) {
                                    finishClosed();
                                } else if (progress > 0f && isClockGlassBlurActive()) {
                                    schedulePostDrawGlassBlur();
                                }
                            }
                        }
                    });
            animator = va;
            va.start();
        }

        private void finishClosed() {
            main.removeCallbacks(tickTask);
            applyFrame(0f, false);
            detachFromClock();
            clearSecondsSemBlur(true);
            sessions.remove(clock, this);
        }

        private void cancelAnimator() {
            if (animator != null) {
                ValueAnimator running = animator;
                animator = null;
                running.cancel();
            }
        }

        private void scheduleSecondTicks() {
            main.removeCallbacks(tickTask);
            long now = System.currentTimeMillis();
            long delay = 1000L - (now % 1000L) + 8L;
            main.postDelayed(tickTask, delay);
        }

        private void onSecondTick() {
            if (ghostActive || (progress <= 0f && !targetOpen)) return;
            if (!clock.isAttachedToWindow() || !clock.isShown()) {
                close(false);
                return;
            }
            if (updateSecondsText(false)) {
                int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                secRow.measure(unspec, unspec);
                secRow.layout(0, 0, secRow.getMeasuredWidth(), secRow.getMeasuredHeight());
                primeSecondsGlyphs();
                if (isClockGlassBlurActive()) {
                    updateClockGlassBlurNow();
                    updateSecondsSemBlur(ClockSecondsLayout.revealAlpha(progress));
                }
                if (DepthEffects.hasVisibleSubject(clock)) clock.invalidate();
            }
            if (targetOpen || progress > 0f) scheduleSecondTicks();
        }

        private boolean updateSecondsText(boolean force) {
            String colonStr = ":";
            if (target.refColon != null) {
                CharSequence existing = target.refColon.getText();
                if (existing != null && existing.length() > 0) colonStr = existing.toString();
            }
            int sec = Calendar.getInstance().get(Calendar.SECOND);
            String formatted = ClockSecondsLayout.twoDigits(sec, Locale.getDefault());
            String d0 = formatted.substring(0, 1);
            String d1 =
                    formatted.length() > 1
                            ? formatted.substring(1, 2)
                            : formatted;
            boolean changed = false;
            if (force || !Objects.equals(colonStr, String.valueOf(colonView.getText()))) {
                colonView.setText(colonStr);
                changed = true;
            }
            if (force || !Objects.equals(d0, String.valueOf(sec10View.getText()))) {
                sec10View.setText(d0);
                changed = true;
            }
            if (force || !Objects.equals(d1, String.valueOf(sec1View.getText()))) {
                sec1View.setText(d1);
                changed = true;
            }
            return changed;
        }

        private void syncStyleAndText(boolean syncColorsAndShadows) {
            TextView ref = target.refDigit;
            TextView colonRef = target.refColon != null ? target.refColon : ref;
            float digitSize =
                    target.vertical
                            ? Math.max(22f * density, ref.getTextSize() * 0.44f)
                            : ref.getTextSize();
            int bottomPad = target.vertical ? 0 : ref.getPaddingBottom();

            boolean colonStyleChanged =
                    copyGlyphFontAndSize(
                            colonRef, colonView, digitSize, bottomPad, target.vertical);
            boolean s10StyleChanged =
                    copyGlyphFontAndSize(ref, sec10View, digitSize, bottomPad, target.vertical);
            boolean s1StyleChanged =
                    copyGlyphFontAndSize(ref, sec1View, digitSize, bottomPad, target.vertical);
            lastSyncedTypeface = ref.getTypeface();
            lastSyncedFixedTypeface = readFixedTypeface(ref);
            lastSyncedFixedType = readFixedFontType(ref);
            lastSyncedTextSize = ref.getTextSize();
            lastSyncedAxes = readRefAxes(ref);

            if (syncColorsAndShadows) {
                syncColorsFromRef();
                syncShadowsFromRef();
            }
            boolean forceInitial = !hasInitialSecondsText;
            hasInitialSecondsText = true;
            updateSecondsTextWithStyle(
                    forceInitial, colonStyleChanged, s10StyleChanged, s1StyleChanged);
        }

        private void updateSecondsTextWithStyle(
                boolean force, boolean colonDirty, boolean s10Dirty, boolean s1Dirty) {
            String colonStr = ":";
            if (target.refColon != null) {
                CharSequence existing = target.refColon.getText();
                if (existing != null && existing.length() > 0) colonStr = existing.toString();
            }
            int sec = Calendar.getInstance().get(Calendar.SECOND);
            String formatted = ClockSecondsLayout.twoDigits(sec, Locale.getDefault());
            String d0 = formatted.substring(0, 1);
            String d1 = formatted.length() > 1 ? formatted.substring(1, 2) : formatted;
            if (force || colonDirty || !Objects.equals(colonStr, String.valueOf(colonView.getText()))) {
                colonView.setText(colonStr);
            }
            if (force || s10Dirty || !Objects.equals(d0, String.valueOf(sec10View.getText()))) {
                sec10View.setText(d0);
            }
            if (force || s1Dirty || !Objects.equals(d1, String.valueOf(sec1View.getText()))) {
                sec1View.setText(d1);
            }
        }

        private boolean copyGlyphFontAndSize(
                TextView source,
                TextView dest,
                float textSizePx,
                int bottomPad,
                boolean compactVertical) {
            int padL = source.getPaddingLeft();
            int padT = source.getPaddingTop();
            int padR = source.getPaddingRight();
            boolean padChanged =
                    dest.getPaddingLeft() != padL
                            || dest.getPaddingTop() != padT
                            || dest.getPaddingRight() != padR
                            || dest.getPaddingBottom() != bottomPad;
            if (padChanged) {
                dest.setPadding(padL, padT, padR, bottomPad);
            }
            Typeface prevTf = dest.getTypeface();
            Object prevFixedTf = readFixedTypeface(dest);
            int prevFixedType = readFixedFontType(dest);
            String prevAxes = readRefAxes(dest);
            float prevSize = dest.getTextSize();

            try {
                if (binding.glyphTextClass.isInstance(source)
                        && binding.glyphTextClass.isInstance(dest)) {
                    int orig = binding.originalFontType.getInt(source);
                    if (binding.originalFontType.getInt(dest) != orig) {
                        binding.originalFontType.setInt(dest, orig);
                    }
                    Object fixedTypeface = binding.glyphFont.fixed().get(source);
                    int fixedType = binding.glyphFont.fontType().getInt(source);
                    String axes = (String) binding.glyphFont.variation().get(source);
                    if (fixedTypeface != null) {
                        if (binding.glyphFont.fixed().get(dest) != fixedTypeface) {
                            binding.glyphFont.fixed().set(dest, fixedTypeface);
                            binding.glyphFont.update().invoke(dest);
                        }
                    } else if (fixedType > 0) {
                        int mappedType =
                                compactVertical
                                        ? ClockSecondsLayout.compactVerticalFontType(fixedType)
                                        : fixedType;
                        String mappedAxes =
                                compactVertical ? ClockSecondsLayout.stripStretchAxis(axes) : axes;
                        binding.glyphFont.fix().invoke(dest, mappedType, mappedAxes);
                    } else if (dest.getTypeface() != source.getTypeface()) {
                        dest.setTypeface(source.getTypeface());
                        binding.glyphFont.update().invoke(dest);
                    }
                } else {
                    if (dest.getTypeface() != source.getTypeface()) {
                        dest.setTypeface(source.getTypeface());
                    }
                    if (Build.VERSION.SDK_INT >= 26) {
                        String srcVar = source.getFontVariationSettings();
                        if (!Objects.equals(dest.getFontVariationSettings(), srcVar)) {
                            dest.setFontVariationSettings(srcVar);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                dest.setTypeface(source.getTypeface());
            }

            if (Float.compare(dest.getTextSize(), textSizePx) != 0) {
                dest.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
            }
            return padChanged
                    || dest.getTypeface() != prevTf
                    || readFixedTypeface(dest) != prevFixedTf
                    || readFixedFontType(dest) != prevFixedType
                    || !Objects.equals(readRefAxes(dest), prevAxes)
                    || Float.compare(dest.getTextSize(), prevSize) != 0;
        }

        private void measureSecondsAndBuildPlan(boolean secDirty) {
            int rowVGravity = Gravity.TOP;
            if (!target.vertical && target.timeGroup instanceof LinearLayout groupLayout) {
                int vg = groupLayout.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
                if (vg == Gravity.BOTTOM || vg == Gravity.CENTER_VERTICAL || vg == Gravity.TOP) {
                    rowVGravity = vg;
                }
            }
            if ((secRow.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != rowVGravity) {
                secRow.setGravity(rowVGravity);
                secDirty = true;
            }
            if (secDirty || secRow.getMeasuredWidth() <= 0 || secRow.isLayoutRequested()) {
                int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                secRow.measure(unspec, unspec);
                secRow.layout(0, 0, secRow.getMeasuredWidth(), secRow.getMeasuredHeight());
                if (isClockGlassBlurActive() || DepthEffects.hasVisibleSubject(clock)) {
                    primeSecondsGlyphs();
                }
            }
            rebuildPlanFromCurrentGeometry();
        }

        private boolean measureSecondsAndBuildPlanIfMoved() {
            if (secRow.getMeasuredWidth() <= 0 || secRow.isLayoutRequested()) {
                measureSecondsAndBuildPlan(true);
                return true;
            }
            RectF hmStage = unscaledBoundsInStage(target.timeGroup, target.stage);
            RectF mmStage = unscaledBoundsInStage(target.mmAnchor, target.stage);
            Matrix stageGlobal = new Matrix();
            target.stage.transformMatrixToGlobal(stageGlobal);
            float[] pts = {hmStage.left, hmStage.top, hmStage.right, hmStage.bottom};
            stageGlobal.mapPoints(pts);
            float hmWinLeft = Math.min(pts[0], pts[2]);
            float hmWinRight = Math.max(pts[0], pts[2]);
            View root = clock.getRootView();
            float rootWidth =
                    root != null && root.getWidth() > 0
                            ? root.getWidth()
                            : clock.getResources().getDisplayMetrics().widthPixels;
            int gravity = resolveClockHorizontalGravity();
            boolean dateAbove = resolveClockDateAbove();
            if (plan != null
                    && nearPx(hmStage.left, lastPlanHmL)
                    && nearPx(hmStage.top, lastPlanHmT)
                    && nearPx(hmStage.right, lastPlanHmR)
                    && nearPx(hmStage.bottom, lastPlanHmB)
                    && nearPx(mmStage.right, lastPlanMmR)
                    && nearPx(hmWinLeft, lastPlanWinL)
                    && nearPx(hmWinRight, lastPlanWinR)
                    && nearPx(rootWidth, lastPlanRootW)
                    && gravity == lastPlanGravity
                    && dateAbove == lastPlanDateAbove) {
                return false;
            }
            rebuildPlanFromGeometry(
                    hmStage, mmStage, hmWinLeft, hmWinRight, rootWidth, gravity, dateAbove);
            return true;
        }

        private static boolean nearPx(float a, float b) {
            return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) <= 0.05f;
        }

        private int resolveClockHorizontalGravity() {
            int gravity = Gravity.CENTER_HORIZONTAL;
            try {
                int rawGravity = getClockGravity == null ? OneUi80ClockSeconds.gravity(clock)
                        : ((Number) getClockGravity.invoke(clock)).intValue();
                int abs =
                        Gravity.getAbsoluteGravity(
                                rawGravity,
                                clock.getResources().getConfiguration().getLayoutDirection());
                int horiz = abs & Gravity.HORIZONTAL_GRAVITY_MASK;
                if (horiz == Gravity.LEFT || horiz == Gravity.RIGHT) gravity = horiz;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            return gravity;
        }

        private boolean resolveClockDateAbove() {
            try {
                return Boolean.TRUE.equals(isShowDateFirst.invoke(clock));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }

        private void rebuildPlanFromCurrentGeometry() {
            RectF hmStage = unscaledBoundsInStage(target.timeGroup, target.stage);
            RectF mmStage = unscaledBoundsInStage(target.mmAnchor, target.stage);
            Matrix stageGlobal = new Matrix();
            target.stage.transformMatrixToGlobal(stageGlobal);
            float[] pts = {hmStage.left, hmStage.top, hmStage.right, hmStage.bottom};
            stageGlobal.mapPoints(pts);
            float hmWinLeft = Math.min(pts[0], pts[2]);
            float hmWinRight = Math.max(pts[0], pts[2]);
            View root = clock.getRootView();
            float rootWidth =
                    root != null && root.getWidth() > 0
                            ? root.getWidth()
                            : clock.getResources().getDisplayMetrics().widthPixels;
            rebuildPlanFromGeometry(
                    hmStage,
                    mmStage,
                    hmWinLeft,
                    hmWinRight,
                    rootWidth,
                    resolveClockHorizontalGravity(),
                    resolveClockDateAbove());
        }

        private void rebuildPlanFromGeometry(
                RectF hmStage,
                RectF mmStage,
                float hmWinLeft,
                float hmWinRight,
                float rootWidth,
                int gravity,
                boolean dateAbove) {
            float secContentW = Math.max(1f, secRow.getMeasuredWidth() - 2f * padX);
            float secContentH = Math.max(1f, secRow.getMeasuredHeight() - 2f * padY);
            RectF refStage = unscaledBoundsInStage(target.refDigit, target.stage);

            float gap = target.vertical ? verticalGapPx : 0f;
            secBaseX = mmStage.right + gap - padX;
            if (target.vertical) {
                float refInkBottom = refStage.bottom - target.refDigit.getPaddingBottom();
                secBaseY = refInkBottom - secContentH - padY;
            } else {
                int digitTopInRow = sec10View.getMeasuredHeight() > 0 ? sec10View.getTop() : padY;
                secBaseY = refStage.top - digitTopInRow;
            }

            float hmLocalW = Math.max(1f, hmStage.width());
            float parentScale = Math.max(0.1f, (hmWinRight - hmWinLeft) / hmLocalW);

            lastPlanHmL = hmStage.left;
            lastPlanHmT = hmStage.top;
            lastPlanHmR = hmStage.right;
            lastPlanHmB = hmStage.bottom;
            lastPlanMmR = mmStage.right;
            lastPlanWinL = hmWinLeft;
            lastPlanWinR = hmWinRight;
            lastPlanRootW = rootWidth;
            lastPlanGravity = gravity;
            lastPlanDateAbove = dateAbove;

            float clipLeft = Float.NEGATIVE_INFINITY;
            float clipRight = Float.POSITIVE_INFINITY;
            Rect visible = new Rect();
            if (target.stage.getGlobalVisibleRect(visible) && visible.width() > 0) {
                float inset = Math.min(2f * clock.getResources().getDisplayMetrics().density,
                        visible.width() / 4f);
                clipLeft = visible.left + inset;
                clipRight = visible.right - inset;
            }
            plan =
                    ClockSecondsLayout.computePlan(
                            hmStage.left,
                            hmStage.top,
                            hmStage.right,
                            hmStage.bottom,
                            mmStage.right,
                            gap,
                            secContentW,
                            parentScale,
                            hmWinLeft,
                            hmWinRight,
                            rootWidth,
                            edgeMarginPx,
                            gravity,
                            dateAbove,
                            maxSlidePx,
                            clipLeft,
                            clipRight);
        }

        private static RectF unscaledBoundsInStage(View child, ViewGroup stage) {
            float x = 0f;
            float y = 0f;
            View cur = child;
            while (cur != null && cur != stage) {
                x += cur.getLeft();
                y += cur.getTop();
                cur = cur.getParent() instanceof View pv ? pv : null;
            }
            return new RectF(x, y, x + child.getWidth(), y + child.getHeight());
        }

        private void applyMotionRenderEffect(float sigmaX, float sigmaY) {
            float qx = sigmaX > 0f ? Math.round(sigmaX * 20f) * 0.05f : 0f;
            float qy = sigmaY > 0f ? Math.round(sigmaY * 20f) * 0.05f : 0f;
            if (Float.compare(qx, lastSigmaX) == 0 && Float.compare(qy, lastSigmaY) == 0) {
                return;
            }
            lastSigmaX = qx;
            lastSigmaY = qy;
            if (qx > 0f || qy > 0f) {
                secRow.setRenderEffect(
                        RenderEffect.createBlurEffect(
                                Math.max(0.1f, qx), Math.max(0.1f, qy), Shader.TileMode.DECAL));
            } else {
                secRow.setRenderEffect(null);
            }
        }

        private void schedulePostDrawGlassBlur() {
            if (!clock.isAttachedToWindow() || !clock.isShown()) return;
            if (ClockBlurSync.pending(clock) || ClockBlurSync.rebuilding(clock)) {
                return;
            }
            if (!ClockBlurSync.defer(
                    clock,
                    v -> {
                        if (progress > 0f && !ghostActive && isClockGlassBlurActive()) {
                            updateClockGlassBlurNow();
                            updateSecondsSemBlur(ClockSecondsLayout.revealAlpha(progress));
                        }
                    })) {
                updateClockGlassBlurNow();
                updateSecondsSemBlur(ClockSecondsLayout.revealAlpha(progress));
            }
        }

        private void applyFrame(float nextProgress, boolean immediateBlur) {
            progress = Math.max(0f, Math.min(1f, nextProgress));
            if (plan == null || ghostActive) return;

            target.timeGroup.setPivotX(0f);
            target.timeGroup.setPivotY(0f);
            host.setPivotX(0f);
            host.setPivotY(0f);

            float s = ClockSecondsLayout.scaleAt(plan, progress);
            target.timeGroup.setScaleX(baseScaleX * s);
            target.timeGroup.setScaleY(baseScaleY * s);
            target.timeGroup.setTranslationX(
                    ClockSecondsLayout.timeGroupTx(
                            plan, progress, target.timeGroup.getLeft(), baseTx));
            target.timeGroup.setTranslationY(
                    ClockSecondsLayout.timeGroupTy(
                            plan, progress, target.timeGroup.getTop(), baseTy));

            host.setScaleX(s);
            host.setScaleY(s);
            host.setTranslationX(ClockSecondsLayout.hostTx(plan, progress));
            host.setTranslationY(ClockSecondsLayout.hostTy(plan, progress));

            float slide = ClockSecondsLayout.slideOffset(plan, progress);
            float localScale = 0.88f + 0.12f * progress;
            float revealAlpha = ClockSecondsLayout.revealAlpha(progress);
            secRow.setPivotX(padX);
            secRow.setPivotY(padY + 0.5f * Math.max(1f, secRow.getHeight() - 2f * padY));
            secRow.setScaleX(localScale);
            secRow.setScaleY(localScale);
            secRow.setTranslationX(secBaseX - slide);
            secRow.setTranslationY(secBaseY);
            secRow.setAlpha(revealAlpha);

            boolean glassActive = isClockGlassBlurActive();
            float peakSigmaX = glassActive ? glassMaxSigmaX : maxSigmaX;
            float sigmaX = ClockSecondsLayout.motionSigmaX(progress, peakSigmaX);
            float sigmaY = ClockSecondsLayout.motionSigmaY(progress, maxSigmaY);
            applyMotionRenderEffect(sigmaX, sigmaY);

            if (progress > 0f) {
                captureLastGlobalIfShown();
                if (glassActive) {
                    if (immediateBlur) {
                        updateClockGlassBlurNow();
                        updateSecondsSemBlur(revealAlpha);
                    } else {
                        schedulePostDrawGlassBlur();
                    }
                } else {
                    clearSecondsSemBlur(false);
                }
                if (DepthEffects.hasVisibleSubject(clock)) clock.invalidate();
            } else {
                clearSecondsSemBlur(false);
            }
        }

        boolean isClockGlassBlurActive() {
            ClockBlurRetryBinding retry = binding.blurRetry;
            if (retry == null
                    || binding.clockBlur == null
                    || binding.semBlurCopies == null
                    || binding.semSetBlurInfo == null
                    || binding.glyphPath == null
                    || !binding.glyphTextClass.isInstance(colonView)) {
                return false;
            }
            try {
                Object manager = retry.manager.get(clock);
                return manager != null
                        && retry.enabled != null
                        && Boolean.TRUE.equals(retry.enabled.invoke(manager));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }

        private void updateClockGlassBlurNow() {
            if (binding.blurRetry == null || !clock.isAttachedToWindow() || !clock.isShown()) {
                return;
            }
            Boolean prev = updatingClockBlur.get();
            updatingClockBlur.set(Boolean.TRUE);
            try {
                Object manager = binding.blurRetry.manager.get(clock);
                if (manager != null
                        && binding.blurRetry.enabled != null
                        && Boolean.TRUE.equals(binding.blurRetry.enabled.invoke(manager))) {
                    Reflect.call(clock, "setClockTextBlur");
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            } finally {
                if (prev == null) updatingClockBlur.remove();
                else updatingClockBlur.set(prev);
            }
        }

        private ViewGroup resolveClockBlurHost() {
            if (binding.blurRetry == null || binding.clockBlur == null) return null;
            try {
                Object manager = binding.blurRetry.manager.get(clock);
                Object strategy = manager == null ? null : binding.blurRetry.strategy.get(manager);
                if (strategy == null) return null;
                Object hostObj = binding.clockBlur.host.invoke(strategy);
                return hostObj instanceof ViewGroup group ? group : null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        private View ensureSecondsBlurSurface(ViewGroup clockBlurHost) {
            int w = clockBlurHost.getWidth();
            int h = clockBlurHost.getHeight();
            if (w <= 0 || h <= 0) return null;
            if (secBlurSurface == null) {
                secBlurSurface = new View(clockBlurHost.getContext());
                secBlurSurface.setTag(BLUR_TAG);
                secBlurSurface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            if (secBlurSurface.getParent() != clockBlurHost) {
                if (secBlurSurface.getParent() instanceof ViewGroup oldParent) {
                    oldParent.removeView(secBlurSurface);
                }
                clockBlurHost.addView(
                        secBlurSurface,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (secBlurSurface.getWidth() != w || secBlurSurface.getHeight() != h) {
                secBlurSurface.measure(
                        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                secBlurSurface.layout(0, 0, w, h);
            }
            return secBlurSurface;
        }

        private Object resolveBlurTemplate(ViewGroup clockBlurHost)
                throws ReflectiveOperationException {
            Object template = BlurAppearance.nativeRequest(clockBlurHost);
            if (template != null) return template;
            if (fallbackBlurTemplate == null
                    && binding.semBlurBuilderCtor != null
                    && binding.semBlurSetRadius != null
                    && binding.semBlurBuild != null) {
                Object builder = binding.semBlurBuilderCtor.newInstance(0);
                binding.semBlurSetRadius.invoke(builder, 200);
                fallbackBlurTemplate = binding.semBlurBuild.invoke(builder);
            }
            return fallbackBlurTemplate;
        }

        boolean appendSecondsToPath(Path out, PointF origin) {
            if (binding.glyphPath == null || !binding.glyphTextClass.isInstance(colonView)) {
                return false;
            }
            boolean added = false;
            try {
                for (TextView tv : new TextView[] {colonView, sec10View, sec1View}) {
                    if (!tv.isShown()) continue;
                    Object rawPath = binding.glyphPath.invoke(tv);
                    if (!(rawPath instanceof Path)) {
                        tv.draw(PRIME_CANVAS);
                        rawPath = binding.glyphPath.invoke(tv);
                    }
                    if (rawPath instanceof Path localPath && !localPath.isEmpty()) {
                        UnlockBlur.append(out, localPath, tv, origin, 1f);
                        added = true;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
            return added;
        }

        void updateSecondsSemBlur(float alphaFactor) {
            if (alphaFactor <= 0.004f) {
                clearSecondsSemBlur(false);
                return;
            }
            ViewGroup clockBlurHost = resolveClockBlurHost();
            if (clockBlurHost == null
                    || !clockBlurHost.isAttachedToWindow()
                    || clockBlurHost.getVisibility() != View.VISIBLE) {
                clearSecondsSemBlur(false);
                return;
            }
            View surface = ensureSecondsBlurSurface(clockBlurHost);
            if (surface == null) return;

            try {
                Object template = resolveBlurTemplate(clockBlurHost);
                if (template == null) return;

                Matrix surfaceGlobal = new Matrix();
                surface.transformMatrixToGlobal(surfaceGlobal);
                float[] originPt = {0f, 0f};
                surfaceGlobal.mapPoints(originPt);
                PointF origin = new PointF(originPt[0], originPt[1]);

                Path secPath = new Path();
                if (!appendSecondsToPath(secPath, origin) || secPath.isEmpty()) {
                    clearSecondsSemBlur(false);
                    return;
                }
                Path subject = DepthEffects.nativeSubjectPath(clock, origin.x, origin.y);
                if (subject != null) {
                    secPath = DepthGeometry.subtract(secPath, subject);
                }
                Object submission = binding.semBlurCopies.submission(template, secPath);
                binding.semSetBlurInfo.invoke(surface, submission);
                Drawable bg = surface.getBackground();
                if (bg != null) {
                    float factor = Math.max(0f, Math.min(1f, alphaFactor));
                    bg.setAlpha(Math.round(255f * factor));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                DressroomModule.note("Inline seconds SemBlurInfo update", error);
            }
        }

        void clearSecondsSemBlur(boolean detachView) {
            if (secBlurSurface == null) return;
            try {
                if (binding.semSetBlurInfo != null) {
                    binding.semSetBlurInfo.invoke(secBlurSurface, (Object) null);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            if (detachView) {
                if (secBlurSurface.getParent() instanceof ViewGroup parent) {
                    parent.removeView(secBlurSurface);
                }
                secBlurSurface = null;
            }
        }

        boolean containsScreenPoint(int x, int y) {
            return viewContainsScreenPoint(target.timeGroup, x, y)
                    || viewContainsScreenPoint(secRow, x, y);
        }

        static boolean viewContainsScreenPoint(View v, int x, int y) {
            if (v == null || !v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0) return false;
            Matrix m = new Matrix();
            v.transformMatrixToGlobal(m);
            RectF rect = new RectF(0, 0, v.getWidth(), v.getHeight());
            m.mapRect(rect);
            return rect.contains(x, y);
        }
    }
}
