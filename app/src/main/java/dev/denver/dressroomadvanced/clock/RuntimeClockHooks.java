package dev.denver.dressroomadvanced;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import io.github.libxposed.api.XposedInterface;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

final class RuntimeClockHooks {
    private final DressroomModule module;
    private final RuntimeWidgetHooks widgets;
    private HookTransaction installation;
    private final Set<ClassLoader> installing = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final List<WeakReference<View>> clocks = new ArrayList<>();
    private final Set<View> effects =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final java.util.Map<View, Bundle> profiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Boolean> manual = ThreadLocal.withInitial(() -> false);
    private final Set<View> restoringFont =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<View> mediaReturnStarted =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<View> mediaRestorePending =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final java.util.Map<View, android.animation.Animator> mediaPageReturns = new WeakHashMap<>();
    private final ThreadLocal<List<View>> closingMediaClocks = new ThreadLocal<>();
    private final ThreadLocal<Boolean> closingOrphanedMedia = ThreadLocal.withInitial(() -> false);
    private final java.util.Map<View, Integer> settingsRetries =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final java.util.Map<View, Integer> fontRetries =
            Collections.synchronizedMap(new WeakHashMap<>());
    private XposedInterface.HookHandle bootstrap;
    private final Object bootstrapLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DeferredDiscovery<ClassLoader> discovery;
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();

    RuntimeClockHooks(DressroomModule module) {
        this.module = module;
        widgets = new RuntimeWidgetHooks(module);
        ScreenIdentity.observe(() -> main.post(() -> {
            MediaDepthTransition.screenChanged();
            MediaFullscreenExtras.screenChanged();
            for (View clock : new java.util.ArrayList<>(mediaRestorePending)) finishMediaReturn(clock);
            mediaReturnStarted.clear();
            RuntimeClockData.invalidate();
            module.widgetEngine.invalidate();
            refreshAttached();
        }));
        discovery =
                new DeferredDiscovery<>(
                        task -> main.post(task),
                        loader -> {
                            try {
                                android.util.Log.i(
                                        DressroomModule.TAG,
                                        "Deferred provider installation starting");
                                installProvider(
                                        Class.forName(
                                                "com.samsung.android.uniform.widget.clock.AbsClockView",
                                                false,
                                                loader));
                                synchronized (bootstrapLock) {
                                    if (bootstrap != null) {
                                        bootstrap.unhook();
                                        bootstrap = null;
                                    }
                                }
                                return true;
                            } catch (Exception | LinkageError error) {
                                module.report("Deferred provider discovery failed", error);
                                return false;
                            }
                        });
    }

    void install(ClassLoader loader) {
        try {
            try {
                discovery.offer(
                        Class.forName(
                                        "com.samsung.android.uniform.widget.clock.AbsClockView",
                                        false,
                                        loader)
                                .getClassLoader());
                return;
            } catch (ClassNotFoundException ignored) {
            }
            synchronized (bootstrapLock) {
                if (bootstrap != null) return;
                Method load =
                        ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
                bootstrap =
                        module.hook(load)
                                .setId("dressroom-advanced.provider-discovery")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            String name = (String) chain.getArg(0);
                                            if (result instanceof Class<?> loaded
                                                    && ("com.samsung.android.uniform.widget.clock.AbsClockView"
                                                                    .equals(name)
                                                            || "com.samsung.android.uniform.plugins.UfClockProvider"
                                                                    .equals(name))) {
                                                discovery.offer(loaded.getClassLoader());
                                            }
                                            return result;
                                        });
                android.util.Log.i(DressroomModule.TAG, "Waiting for runtime clock provider");
            }
        } catch (Exception | LinkageError e) {
            module.report("Runtime clock hooks unavailable", e);
        }
    }

    private synchronized void installProvider(Class<?> base) throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader) || !installing.add(loader)) return;
        try (HookTransaction transaction = new HookTransaction()) {
            DexSignatures.cachedReflective(() -> {
            installation = transaction;
            ClockBinding binding = ClockBinding.of(base);
            if (!binding.legacy && !binding.clockStyle && ScreenIdentity.supported())
                OneUi9FoldProfiles.install(module, loader);
            DepthEffects.install(module);
            if (binding.early) {
                try {
                    record(OneUi80NowBarText.install(module, loader));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("8.0 custom Now Bar visibility unavailable", error);
                }
                try {
                    record(OneUi80MediaHooks.install(module, base));
                } catch (ReflectiveOperationException | java.io.IOException | RuntimeException
                        | LinkageError error) {
                    module.report("8.0 media clock state unavailable", error);
                }
            } else NowBarGlass.install(module, loader);
            DiyNowBarEffects.install(module, loader, !binding.early);
            ShadowEffects.install(module);
            if (!binding.legacy) {
                if (!binding.clockStyle) {
                    ClockBlurSync.install(module);
                    BlurEffects.install(module, base);
                    BlurEffects.installGlyphSync(module, base);
                    GlyphFontCache.install(module, base);
                    GlyphDrawReuse.install(module, loader);
                }
                try {
                    module.widgetEngine.install(loader);
                } catch (Exception | LinkageError error) {
                    module.report("Widget engine install failed", error);
                }
                try {
                    widgets.install(loader);
                } catch (Exception | LinkageError error) {
                    module.report("Widget hooks install failed", error);
                }
            } else {
                if (!binding.clockStyle && !binding.early) {
                    DepthBlurHooks.install(module, base);
                    GlyphFontCache.install(module, base);
                    GlyphDrawReuse.install(module, loader);
                }
                if (binding.early) try {
                    module.widgetEngine.installEarlyCapacity(loader);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("8.0 widget scaling unavailable", error);
                }
                if (binding.early) WidgetDepthBlur.install(module, loader, true);
                try {
                    if (!binding.early) module.widgetEngine.installLegacyCapacity(loader);
                    widgets.installLegacy(loader, binding.early);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("Optional legacy widget runtime unavailable", error);
                }
            }
            if (!binding.early) {
                try {
                    NowBarStackBinding stack = NowBarStackBinding.of(base);
                    NowBarStackRepair.install(module, stack);
                    NowBarStackLayers.install(module, stack);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("Now Bar stack controls unavailable", error);
                }
            }
            try {
                DateEffects.installProviderSizing(module, base);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                module.report("Clock sizing binding unavailable", error);
            }
            VariableFonts.install(module, base, view -> profiles.get(view));
            Class<?> font =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockFontData",
                            false,
                            loader);
            Method balance = binding.balance;
            if (balance != null) record(
                    module.hook(balance)
                            .setId("dressroom-advanced.runtime-independent-stretch")
                            .intercept(
                                    chain ->
                                            manual.get()
                                                    ? chain.getArg(binding.balanceHeights)
                                                    : chain.proceed()));
            Method fontSetter = base.getDeclaredMethod("setFontType", font);
            record(
                    module.hook(fontSetter)
                            .setId("dressroom-advanced.runtime-font")
                            .intercept(
                                    chain -> {
                                        if (RuntimeClockData.measuringCollapsed())
                                            return chain.proceed();
                                        boolean previous = manual.get();
                                        try {
                                            View view = (View) chain.getThisObject();
                                            Object requestedFont = chain.getArg(0);
                                            ProfileRead loaded = null;
                                            try {
                                                loaded = read(view, identity(view, requestedFont));
                                                Bundle settings = loaded.settings();
                                                if (settings != null)
                                                    profiles.put(
                                                            (View) chain.getThisObject(), settings);
                                                boolean held =
                                                        settings != null
                                                                && FontAxisSettings.nativeStretch(
                                                                        settings)
                                                                && (settings.getBoolean("manual")
                                                                        || settings.getBoolean(
                                                                                "depthEnabled"))
                                                                && settings.getFloatArray(
                                                                                "manualHeights")
                                                                        != null;
                                                manual.set(previous || held);
                                                if (held)
                                                    requestedFont =
                                                            SavedStretch.font(
                                                                    requestedFont,
                                                                    SavedStretch.heights(
                                                                            settings.getFloatArray(
                                                                                    "manualHeights")));
                                            } catch (Exception e) {
                                                module.report(
                                                        "Runtime font settings unavailable", e);
                                            }
                                            VariableFonts.configure(
                                                    module,
                                                    (View) chain.getThisObject(),
                                                    profiles.get((View) chain.getThisObject()));
                                            VariableFonts.forget((View) chain.getThisObject());
                                            Bundle fontProfile =
                                                    profiles.get((View) chain.getThisObject());
                                            if (fontProfile != null
                                                    && DepthBinding.provider(
                                                            (View) chain.getThisObject())) {
                                                View clock = (View) chain.getThisObject();
                                                DepthEffects.apply(
                                                        clock,
                                                        fontProfile.getBoolean("depthEnabled"),
                                                        fontProfile.getByteArray("depthMask"),
                                                        fontProfile.getBoolean("depthOffAod"),
                                                        aodCategory(clock));
                                            }
                                            Object result =
                                                    requestedFont == chain.getArg(0)
                                                            ? chain.proceed()
                                                            : chain.proceed(
                                                                    new Object[] {requestedFont});
                                            apply(view, loaded);
                                            applyBlur((View) chain.getThisObject());
                                            if (binding.clockStyle && !binding.legacy)
                                                DateEffects.prepareMeasurement(
                                                        module, (View) chain.getThisObject());
                                            return result;
                                        } finally {
                                            manual.set(previous);
                                        }
                                    }));
            module.deoptimize(fontSetter);
            Method validator = base.getDeclaredMethod("isStretchable", List.class);
            record(
                    module.hook(validator)
                            .setId("dressroom-advanced.runtime-manual-validation")
                            .intercept(
                                    chain -> {
                                        Bundle profile = profiles.get((View) chain.getThisObject());
                                        if (!manual.get()
                                                && (profile == null
                                                        || (!profile.getBoolean("manual")
                                                                && !profile.getBoolean(
                                                                        "depthEnabled"))
                                                        || profile.getFloatArray("manualHeights")
                                                                == null))
                                            return chain.proceed();
                                        Object values = chain.getArg(0);
                                        if (!(values instanceof List<?> list) || list.isEmpty())
                                            return false;
                                        for (Object value : list)
                                            if (!(value instanceof Number)
                                                    || !Float.isFinite(
                                                            ((Number) value).floatValue())
                                                    || ((Number) value).floatValue() <= 0)
                                                return false;
                                        return true;
                                    }));
            module.deoptimize(validator);
            Method attached = base.getDeclaredMethod("onAttachedToWindow");
            record(
                    module.hook(attached)
                            .setId("dressroom-advanced.runtime-attach")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        View view = (View) chain.getThisObject();
                                        try {
                                            track(view);
                                            if (binding.early) OneUi80MediaHooks.attached(view);
                                            DateEffects.attach(module, view);
                                        } catch (Exception e) {
                                            module.report(
                                                    "Runtime settings observer unavailable", e);
                                        }
                                        apply(view);
                                        syncMediaDepth(view);
                                        if (mediaRestorePending.contains(view))
                                            restoreAfterMedia(view);

                                        return result;
                                    }));
            Class<?> shadow =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ShadowStyle",
                            false,
                            loader);
            Method shadowSetter = base.getDeclaredMethod("setShadowStyle", shadow);
            record(
                    module.hook(shadowSetter)
                            .setId("dressroom-advanced.runtime-shadow")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        apply((View) chain.getThisObject());
                                        return result;
                                    }));
            Method dozing = base.getDeclaredMethod("setDozing", boolean.class);
            record(
                    module.hook(dozing)
                            .setId("dressroom-advanced.runtime-depth-doze")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        View clock = (View) chain.getThisObject();
                                        DepthEffects.setDozing(
                                                clock, Boolean.TRUE.equals(chain.getArg(0)));
                                        MediaClockVisibility.dozing(clock, Boolean.TRUE.equals(chain.getArg(0)));
                                        clock.post(
                                                () -> {
                                                    apply(clock);
                                                    syncMediaDepth(clock);
                                                    if (mediaRestorePending.contains(clock))
                                                        restoreAfterMedia(clock);
                                                });
                                        return result;
                                    }));
            try {
                RuntimeClockData.install(module, loader, binding);
            } catch (Exception | LinkageError error) {
                module.report("Native clock source sync unavailable", error);
            }
            try {
                ClockInlineSeconds.install(module, base);
            } catch (Exception | LinkageError error) {
                module.report("Optional inline clock seconds unavailable", error);
            }
            if (binding.legacy && !binding.early) {
                try {
                    OneUi85MediaHooks.install(module, base);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("8.5 media depth lifecycle unavailable", error);
                }
            }
            if (binding.legacy) {
                try {
                    OneUi85FullscreenArt.install(module, base);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("Legacy fullscreen artwork unavailable", error);
                }
            }
            if (!binding.legacy) {
                Method media = base.getDeclaredMethod("setMediaExpanded", boolean.class);
                record(
                        module.hook(media)
                                .setId("dressroom-advanced.runtime-media-stretch-restore")
                                .intercept(
                                        chain -> {
                                            View clock = (View) chain.getThisObject();
                                            LockscreenFrameBoost.pulse(clock, 750L);
                                            boolean expanding = Boolean.TRUE.equals(chain.getArg(0));

                                            boolean wasExpanded = false;
                                            try {
                                                wasExpanded =
                                                        Boolean.TRUE.equals(
                                                                ClockTransitionBinding.of(clock).mediaExpanded.get(clock));
                                            } catch (Exception ignored) {
                                            }
                                            if (wasExpanded
                                                    && Boolean.FALSE.equals(chain.getArg(0))) {
                                                // Drop compact glyph work; keep the live drawable until the next draw replaces its path.
                                                ClockBlurSync.cancel(clock);
                                            }
                                            if (expanding) {
                                                ClockBlurSync.cancel(clock);
                                                finishMediaReturn(clock);
                                            }
                                            Object result = chain.proceed();
                                            // Both framed and fullscreen players share Samsung's clock state.
                                            MediaClockVisibility.update(clock, expanding);
                                            MediaDepthTransition.update(clock, expanding);
                                            if (wasExpanded
                                                    && Boolean.FALSE.equals(chain.getArg(0))) {
                                                mediaReturnStarted.remove(clock);
                                                mediaRestorePending.add(clock);
                                                AodStretchExtras.mediaReturn(clock, true);
                                                List<View> closed = closingMediaClocks.get();
                                                if (closed != null) closed.add(clock);
                                                clock.postOnAnimation(
                                                        () -> restoreAfterMedia(clock));
                                            }
                                            return result;
                                        }));
                module.deoptimize(media);
                try {
                    String anchor = OneUi95MediaState.PAGE_LOG;
                    String owner = DexSignatures.descriptor(base);
                    Class<?> page = Class.forName(
                            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage", false, loader);
                    String pageType = DexSignatures.descriptor(page);
                    for (var body : DexSignatures.find(loader, Set.of(anchor))) {
                        if (body.calls().stream().anyMatch(call -> call.owner().equals(owner)
                                && call.name().equals(media.getName())
                                && call.parameters().equals(List.of("Z"))
                                && call.result().equals("V"))) {
                            Method observer = body.method(loader);
                            module.deoptimize(observer);
                            var pageField = body.uniqueRead(body.owner(), pageType, anchor).field(loader);
                            var animator = body.uniqueWrite(pageType, "Landroid/animation/ValueAnimator;", null)
                                    .field(loader);
                            var pageClockField = NativeContract.findField(page, null, base);
                            boolean betaMediaObserver = PlatformGate.oneUiVersion() >= 90500
                                    || ClockTransitionBinding.of(base).transition.getParameterCount() == 3;
                            var pageExpanded = betaMediaObserver
                                    ? OneUi95MediaState.pageExpanded(body, pageType, owner, loader)
                                    : null;
                            record(module.hook(observer).setId("dressroom-advanced.media-page-return")
                                    .intercept(chain -> {
                                        if (!Boolean.FALSE.equals(chain.getArg(0))) return chain.proceed();
                                        Object targetPage = pageField.get(chain.getThisObject());
                                        if (pageExpanded != null && (targetPage == null
                                                || !pageExpanded.getBoolean(targetPage))) return chain.proceed();
                                        List<View> previous = closingMediaClocks.get();
                                        List<View> closed = new ArrayList<>();
                                        closingMediaClocks.set(closed);
                                        try {
                                            Object result = chain.proceed();
                                            try {
                                                Object clockPage = targetPage;
                                                // This observer also handles animation-running and
                                                // screen state. Only a real change of page state closes media.
                                                if (pageExpanded != null && pageExpanded.getBoolean(clockPage))
                                                    return result;
                                                if (!closed.isEmpty()) {
                                                    if (clockPage != null && animator.get(clockPage)
                                                            instanceof android.animation.Animator running
                                                            && running.isStarted()) {
                                                        for (View clock : closed) {
                                                            if (mediaRestorePending.contains(clock))
                                                                mediaPageReturns.put(clock, running);
                                                        }
                                                    }
                                                } else if (clockPage != null
                                                        && pageClockField.get(clockPage) instanceof View pageClock
                                                        && pageClock.isAttachedToWindow()) {
                                                    boolean stillExpanded =
                                                            Boolean.TRUE.equals(
                                                                    ClockTransitionBinding.of(pageClock)
                                                                            .mediaExpanded.get(pageClock));
                                                    if (!stillExpanded) {
                                                        MediaClockVisibility.update(pageClock, false);
                                                        mediaRestorePending.add(pageClock);
                                                        AodStretchExtras.mediaReturn(pageClock, true);
                                                        if (animator.get(clockPage)
                                                                instanceof android.animation.Animator running
                                                                && running.isStarted())
                                                            mediaPageReturns.put(pageClock, running);
                                                        MediaDepthTransition.update(pageClock, false);
                                                        pageClock.postOnAnimation(
                                                                () -> restoreAfterMedia(pageClock));
                                                    }
                                                }
                                            } catch (ReflectiveOperationException | RuntimeException error) {
                                                DressroomModule.note("Media return animator unavailable", error);
                                            }
                                            return result;
                                        } finally {
                                            if (previous == null) closingMediaClocks.remove();
                                            else closingMediaClocks.set(previous);
                                        }
                                    }));
                        }
                    }
                } catch (Exception | LinkageError error) {
                    DressroomModule.note("Native media clock observer deoptimization unavailable", error);
                }
                try {
                    Method dozeAmountSetter = base.getDeclaredMethod("setDozeAmount", float.class);
                    record(
                            module.hook(dozeAmountSetter)
                                    .setId("dressroom-advanced.runtime-doze-amount-media-restore")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                View clock = (View) chain.getThisObject();
                                                if (chain.getArg(0) instanceof Number n) {
                                                    float amt = n.floatValue();
                                                    if (amt > 0f && amt < 1f) {
                                                        LockscreenFrameBoost.pulse(clock, 220L);
                                                    }
                                                    if (amt == 0f
                                                            && mediaRestorePending.contains(clock)) {
                                                        clock.postOnAnimation(
                                                                () -> restoreAfterMedia(clock));
                                                    }
                                                }
                                                return result;
                                            }));
                    module.deoptimize(dozeAmountSetter);
                } catch (Exception | LinkageError error) {
                    DressroomModule.note("Doze-amount media restore hook unavailable", error);
                }
                try {
                    installOrphanedMediaCollapse(loader);
                } catch (Exception | LinkageError error) {
                    DressroomModule.note("Orphaned media collapse binding unavailable", error);
                }
                try {
                    AodStretchExtras.install(module, base);
                } catch (Exception | LinkageError error) {
                    module.report("AOD stretch option unavailable", error);
                }
                try {
                    MediaFullscreenExtras.install(module, loader);
                } catch (Exception | LinkageError error) {
                    module.report("Full-screen artwork option unavailable", error);
                }
            }
            android.util.Log.i(
                    DressroomModule.TAG,
                    "Runtime clock provider hooks installed: "
                            + (binding.early ? "One UI 8.0" : binding.legacy ? "8.5 core" : "reference"));
            transaction.commit();
            installed.add(loader);
            });
        } finally {
            installation = null;
            installing.remove(loader);
        }
    }

    private void record(XposedInterface.HookHandle handle) {
        installation.add(handle::unhook);
    }

    private void applyBlur(View view) {
        BlurAppearance.trackClock(view);
        if (!ClockBinding.of(view).legacy) {
            BlurEffects.apply(view);
        }
    }

    private record ProfileIdentity(int type, int font, String path, int orientation, int screen) {}
    private record ProfileRead(ProfileIdentity identity, Bundle settings) {}

    private ProfileIdentity identity(View view, Object font) throws ReflectiveOperationException {
        String category = String.valueOf(Reflect.call(view, "getCategory"));
        if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) return null;
        if (!ScreenIdentity.supported() && view.getDisplay() != null && view.getDisplay().getDisplayId() != 0) return null;
        int type = ((Number) Reflect.call(view, "getClockType")).intValue();
        int fontId = font == null ? ClockBinding.of(view).fontId(view)
                : ((Number) Reflect.call(font, "getFontType")).intValue();
        String path = "";
        if (font != null) {
            try {
                if (Reflect.call(font, "getFontPath") instanceof String value) path = value;
            } catch (Exception ignored) {
            }
        }
        return new ProfileIdentity(type, fontId, path, LayoutOrientation.current(view.getContext()),
                ScreenIdentity.current());
    }

    private ProfileRead read(View view, ProfileIdentity identity) {
        if (identity == null) return new ProfileRead(null, null);
        Bundle query = new Bundle();
        query.putInt("type", identity.type());
        query.putInt("font", identity.font());
        if (!identity.path().isEmpty()) query.putString("fontPath", identity.path());
        query.putInt(LayoutOrientation.FIELD, identity.orientation());
        if (identity.screen() != 0) query.putInt(ScreenIdentity.FIELD, identity.screen());
        return new ProfileRead(identity, SettingsBridge.readRuntimeClock(view.getContext(), query));
    }

    private void restoreAfterMedia(View clock) {
        try {
            if (!mediaRestorePending.contains(clock)) return;
            if (!clock.isAttachedToWindow()) return;
            ClockTransitionBinding transition = ClockTransitionBinding.of(clock);
            Object controller = transition.controller.get(clock);
            if (controller == null
                    || Boolean.TRUE.equals(transition.expanded.get(controller))) {
                finishMediaReturn(clock);
                return;
            }
            MediaDepthTransition.update(clock, false);
            boolean dozing = Boolean.TRUE.equals(transition.dozing.get(controller));
            boolean keepStretch = RuntimeExtras.enabled(clock.getContext(), "keepStretch");
            float dozeAmount = transition.amount.getFloat(clock);
            if ((dozing || dozeAmount > 0f) && !keepStretch) return;
            Bundle profile = profiles.get(clock);
            boolean manualStretch = profile != null && FontAxisSettings.nativeStretch(profile)
                    && (profile.getBoolean("manual") || profile.getBoolean("depthEnabled"));
            if (!manualStretch) {
                if (((!dozing && dozeAmount == 0f) || keepStretch)
                        && transition.axes.get(controller) instanceof List<?> values
                        && values.contains("HEGT")) {
                    android.animation.Animator pageAnim = mediaPageReturns.get(clock);
                    android.animation.Animator axisAnim =
                            (android.animation.Animator) transition.animation.get(controller);
                    if (!dozing && dozeAmount == 0f) {
                        if ((pageAnim != null && pageAnim.isStarted())
                                || (axisAnim != null && axisAnim.isStarted())) {
                            mediaReturnStarted.add(clock);
                        } else if (mediaReturnStarted.add(clock)) {
                            transition.reset(clock);
                            axisAnim =
                                    (android.animation.Animator)
                                            transition.animation.get(controller);
                        }
                    }
                    WeakReference<View> reference = new WeakReference<>(clock);
                    Runnable resume = () -> {
                        View view = reference.get();
                        if (view != null) view.postOnAnimation(() -> restoreAfterMedia(view));
                    };
                    // Samsung's page animator can still submit compact glyphs after the
                    // first return frame. Let it and the font spring finish before drawing
                    // the canonical font, which is also used for the next minute tick.
                    if (MediaReturnCompletion.await(clock, pageAnim, resume)
                            || MediaReturnCompletion.await(clock, axisAnim, resume))
                        return;
                    ClockBinding binding = ClockBinding.of(clock);
                    Object nativeFont = binding.font(clock);
                    if (nativeFont != null) {
                        binding.render(clock, nativeFont);
                        Reflect.call(clock, "refresh");
                        clock.requestLayout();
                    }
                }
                finishMediaReturn(clock);
                return;
            }
            List<Float> heights = SavedStretch.heights(profile.getFloatArray("manualHeights"));
            Object original = ClockBinding.of(clock).font(clock);
            if (original == null || heights == null) {
                finishMediaReturn(clock);
                return;
            }
            android.animation.Animator animation =
                    (android.animation.Animator) transition.animation.get(controller);
            if (!dozing && dozeAmount == 0f) {
                if (animation != null && animation.isStarted()) mediaReturnStarted.add(clock);
                if (mediaReturnStarted.add(clock)) {
                    transition.reset(clock);
                    animation = (android.animation.Animator) transition.animation.get(controller);
                }
            }
            WeakReference<View> reference = new WeakReference<>(clock);
            Runnable resume = () -> {
                View view = reference.get();
                if (view != null) view.postOnAnimation(() -> restoreAfterMedia(view));
            };
            if (MediaReturnCompletion.await(clock, mediaPageReturns.get(clock), resume)
                    || MediaReturnCompletion.await(clock, animation, resume)) return;
            Object restored = SavedStretch.font(original, heights);
            if (restored != original) Reflect.call(clock, "setFontType", restored);
            ClockBinding.of(clock).render(clock, restored);
            Reflect.call(clock, "refresh");
            clock.requestLayout();
            finishMediaReturn(clock);
        } catch (Exception error) {
            finishMediaReturn(clock);
            module.report("Media-exit stretch restore unavailable", error);
        }
    }

    private static void syncMediaDepth(View clock) {
        if (PlatformGate.oneUiVersion() < 90500) return;
        try {
            boolean expanded = ClockTransitionBinding.of(clock).mediaExpanded.getBoolean(clock);
            if (expanded) MediaClockVisibility.update(clock, true);
            if (expanded || DepthEffects.mediaActive(clock)) MediaDepthTransition.update(clock, expanded);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            DressroomModule.note("Media depth state unavailable", unavailable);
        }
    }

    private void installOrphanedMediaCollapse(ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        Class<?> cardVmClass =
                Class.forName(
                        "com.samsung.android.app.facewidget.presentation.NowBarCardViewModel",
                        false,
                        loader);
        Class<?> itemMgrClass =
                Class.forName(
                        "com.samsung.android.app.facewidget.domain.NowBarItemManager",
                        false,
                        loader);
        Class<?> mediaVmClass;
        try {
            mediaVmClass = Class.forName(
                    "com.samsung.android.app.facewidget.presentation.MediaExpandStateViewModel",
                    false, loader);
        } catch (ClassNotFoundException moved) {
            mediaVmClass = OneUi95MediaCollapse.viewModel(loader);
        }
        Class<?> nowBarItemClass =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                        false,
                        loader);
        Class<?> mediaDataClass =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.PluginFaceWidgetMediaData",
                        false,
                        loader);
        String cardVmDesc = DexSignatures.descriptor(cardVmClass);
        String itemMgrDesc = DexSignatures.descriptor(itemMgrClass);
        String mediaVmDesc = DexSignatures.descriptor(mediaVmClass);

        Method closeCard = null;
        for (var body : DexSignatures.find(loader, Set.of("closeExtendNowBarCard"))) {
            if (body.owner().equals(cardVmDesc)
                    && body.parameters().isEmpty()
                    && body.result().equals("V")) {
                closeCard = body.method(loader);
                break;
            }
        }
        Method updateView = null;
        for (var body : DexSignatures.find(loader, Set.of("updateView isCollapsed = "))) {
            if (body.owner().equals(cardVmDesc)
                    && body.parameters().equals(List.of("Z"))
                    && body.result().equals("V")) {
                updateView = body.method(loader);
                break;
            }
        }
        java.lang.reflect.Field mediaExpandedField = null;
        for (var body : DexSignatures.find(loader, Set.of("mediaExpandObserver "))) {
            for (var write : body.writes()) {
                if (write.field().owner().equals(mediaVmDesc)
                        && write.field().type().equals("Z")) {
                    mediaExpandedField = write.field().field(loader);
                    break;
                }
            }
            if (mediaExpandedField != null) break;
        }
        int mediaStyleOrdinal = -1;
        for (var body : DexSignatures.find(loader, Set.of("startMediaPauseTimer"))) {
            if (!body.owner().equals(itemMgrDesc)) continue;
            for (var read : body.reads()) {
                String ownerDesc = read.field().owner();
                if ("MEDIA".equals(read.field().name())
                        && ownerDesc.startsWith("L")
                        && ownerDesc.endsWith(";")) {
                    Class<?> enumType =
                            Class.forName(
                                    ownerDesc.substring(1, ownerDesc.length() - 1).replace('/', '.'),
                                    false,
                                    loader);
                    Object[] constants = enumType.getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if (c instanceof Enum<?> e && "MEDIA".equals(e.name())) {
                                mediaStyleOrdinal = e.ordinal();
                                break;
                            }
                        }
                    }
                    if (mediaStyleOrdinal >= 0) break;
                }
            }
            if (mediaStyleOrdinal >= 0) break;
        }
        if (closeCard == null || updateView == null || mediaExpandedField == null) return;
        java.lang.reflect.Field itemMgrField;
        Method itemMgrGetter;
        try {
            itemMgrField = NativeContract.findField(cardVmClass, null, itemMgrClass);
            itemMgrGetter = null;
        } catch (NoSuchFieldException provided) {
            itemMgrField = null;
            itemMgrGetter = NativeContract.findMethod(cardVmClass, null, itemMgrClass);
        }
        var rawItemsField =
                NativeContract.findField(
                        itemMgrClass, null, java.util.concurrent.CopyOnWriteArrayList.class);
        java.lang.reflect.Field mediaVmField;
        boolean mediaVmProvided;
        try {
            mediaVmField = NativeContract.findField(cardVmClass, null, mediaVmClass);
            mediaVmProvided = false;
        } catch (NoSuchFieldException provided) {
            mediaVmField = OneUi95MediaCollapse.mediaProvider(cardVmClass, mediaVmClass);
            mediaVmProvided = true;
        }
        Method getMediaData =
                NativeContract.method(nowBarItemClass, "getFaceWidgetMediaData", mediaDataClass);
        Method getViewStyle =
                NativeContract.method(nowBarItemClass, "getNowBarViewStyle", int.class);
        Method closeTarget = closeCard;
        var expandedTarget = mediaExpandedField;
        int mediaOrdinal = mediaStyleOrdinal;
        var mediaMember = mediaVmField;
        boolean unwrapMedia = mediaVmProvided;
        var itemMember = itemMgrField;
        var itemGetter = itemMgrGetter;

        module.deoptimize(updateView);
        record(
                module.hook(updateView)
                        .setId("dressroom-advanced.media-orphan-collapse")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (closingOrphanedMedia.get()) return result;
                                    try {
                                        Object cardVm = chain.getThisObject();
                                        Object mediaVm = mediaMember.get(cardVm);
                                        if (unwrapMedia && mediaVm != null)
                                            mediaVm = mediaMember.getType().getMethod("get").invoke(mediaVm);
                                        if (mediaVm == null || !expandedTarget.getBoolean(mediaVm))
                                            return result;
                                        Object itemMgr = itemGetter == null
                                                ? itemMember.get(cardVm) : itemGetter.invoke(cardVm);
                                        if (itemMgr == null) return result;
                                        if (!(rawItemsField.get(itemMgr)
                                                instanceof List<?> rawItems)) return result;
                                        boolean hasMedia = false;
                                        for (Object item : rawItems) {
                                            if (!nowBarItemClass.isInstance(item)) continue;
                                            if (getMediaData.invoke(item) != null
                                                    || (mediaOrdinal >= 0
                                                            && ((Number) getViewStyle.invoke(item))
                                                                            .intValue()
                                                                    == mediaOrdinal)) {
                                                hasMedia = true;
                                                break;
                                            }
                                        }
                                        if (!hasMedia) {
                                            closingOrphanedMedia.set(true);
                                            try {
                                                closeTarget.invoke(cardVm);
                                            } finally {
                                                closingOrphanedMedia.set(false);
                                            }
                                        }
                                    } catch (ReflectiveOperationException
                                            | RuntimeException error) {
                                        DressroomModule.note(
                                                "Orphaned media collapse check skipped", error);
                                    }
                                    return result;
                                }));
    }

    private void finishMediaReturn(View clock) {
        mediaRestorePending.remove(clock);
        mediaPageReturns.remove(clock);
        mediaReturnStarted.remove(clock);
        MediaReturnCompletion.cancel(clock);
        AodStretchExtras.mediaReturn(clock, false);
    }

    private void apply(View view) {
        apply(view, null);
    }

    private void apply(View view, ProfileRead loaded) {
        boolean providerReady = false;
        try {
            Object clockFont = ClockBinding.of(view).font(view);
            ProfileIdentity identity = identity(view, clockFont);
            Bundle settings = loaded != null && java.util.Objects.equals(loaded.identity(), identity)
                    ? loaded.settings() : read(view, identity).settings();
            providerReady = true;
            VariableFonts.configure(module, view, settings);
            boolean waitingForFont = identity != null && identity.font() == 0;
            if (settings != null || !waitingForFont) settingsRetries.remove(view);
            if (settings == null && waitingForFont) {
                Bundle previous = profiles.get(view);
                if (previous != null
                        && previous.getInt("wallpaperId", -1)
                                == SettingsBridge.wallpaperId(view.getContext())
                        && previous.getInt(LayoutOrientation.FIELD)
                                == identity.orientation()
                        && ScreenIdentity.value(previous) == identity.screen())
                    settings = previous;
            }
            if (settings == null) {
                profiles.remove(view);
                DateEffects.bindSettings(view, null);
                if (effects.remove(view)) ShadowEffects.apply(view, false, 0, 0, 0, 0);
                applyDepth(view, null);
                int attempt = settingsRetries.getOrDefault(view, 0);
                if (waitingForFont && attempt < 4 && view.isAttachedToWindow()) {
                    settingsRetries.put(view, attempt + 1);
                    WeakReference<View> reference = new WeakReference<>(view);
                    main.postDelayed(
                            () -> {
                                View clock = reference.get();
                                if (clock != null && clock.isAttachedToWindow()) apply(clock);
                            },
                            250L << attempt);
                }
                return;
            }
            profiles.put(view, settings);
            DateEffects.bindSettings(view, settings);
            if (FontAxisSettings.nativeStretch(settings)
                    && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))
                    && settings.getFloatArray("manualHeights") != null
                    && restoringFont.add(view)) {
                try {
                    Object current = ClockBinding.of(view).font(view);
                    if (current != null) {
                        java.util.List<Float> heights =
                                SavedStretch.heights(settings.getFloatArray("manualHeights"));
                        Object restored = SavedStretch.font(current, heights);
                        if (restored != current) Reflect.call(view, "setFontType", restored);
                        fontRetries.remove(view);
                    } else if (fontRetries.getOrDefault(view, 0) < 4 && view.isAttachedToWindow()) {
                        int attempt = fontRetries.getOrDefault(view, 0);
                        fontRetries.put(view, attempt + 1);
                        WeakReference<View> reference = new WeakReference<>(view);
                        main.postDelayed(
                                () -> {
                                    View clock = reference.get();
                                    if (clock != null && clock.isAttachedToWindow()) apply(clock);
                                },
                                250L << attempt);
                    }
                } catch (Exception error) {
                    module.report("Saved runtime stretch restore unavailable", error);
                } finally {
                    restoringFont.remove(view);
                }
            }
            float scale = ((Number) Reflect.call(view, "getScale")).floatValue();
            if (!Float.isFinite(scale) || scale <= 0) scale = 1f;
            ShadowEffects.apply(
                    view,
                    settings.getBoolean("enabled"),
                    settings.getFloat("radius") / scale,
                    settings.getFloat("dx") / scale,
                    settings.getFloat("dy") / scale,
                    settings.getInt("color"));
            if (DepthBinding.provider(view)) applyDepth(view, settings);
            effects.add(view);
            ClockBinding.of(view).refreshAppearance(view);
            if (VariableFonts.needsRefresh(view, settings)) {
                Object font = ClockBinding.of(view).font(view);
                if (font != null) {
                    ClockBinding.of(view).render(view, font);
                    view.requestLayout();
                }
            }
            view.invalidate();
        } catch (Exception e) {
            module.report("Runtime settings apply deferred", e);
            int attempt = settingsRetries.getOrDefault(view, 0);
            if (!providerReady && attempt < 4 && view.isAttachedToWindow()) {
                settingsRetries.put(view, attempt + 1);
                WeakReference<View> reference = new WeakReference<>(view);
                main.postDelayed(
                        () -> {
                            View clock = reference.get();
                            if (clock != null && clock.isAttachedToWindow()) apply(clock);
                        },
                        250L << attempt);
            }
        }
    }

    private void track(View view) {
        MediaClockContrast.track(view);
        MediaClockVisibility.attach(view);
        clocks.removeIf(reference -> reference.get() == null);
        for (WeakReference<View> ref : clocks) if (ref.get() == view) return;
        clocks.add(new WeakReference<>(view));
        view.addOnLayoutChangeListener(
                (clock, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                    Bundle profile = profiles.get(clock);
                    if (profile == null
                            || profile.getInt(LayoutOrientation.FIELD)
                                    != LayoutOrientation.current(clock.getContext())
                            || ScreenIdentity.value(profile) != ScreenIdentity.current()) {
                        apply(clock);
                        applyBlur(clock);
                        DateEffects.apply(clock);
                        profile = profiles.get(clock);
                    }
                    if (profile != null && DepthBinding.provider(clock)) applyDepth(clock, profile);
                });
        if (!observing.compareAndSet(false, true)) return;
        try {
            view.getContext()
                    .getContentResolver()
                    .registerContentObserver(
                            SettingsProvider.URI,
                            false,
                            new ContentObserver(new Handler(Looper.getMainLooper())) {
                                @Override
                                public void onChange(boolean selfChange) {
                                    RuntimeClockData.invalidate();
                                    refreshAttached();
                                }
                            });
        } catch (RuntimeException error) {
            observing.set(false);
            throw error;
        }
        if (PlatformGate.oneUiVersion() >= 90500) observeWallpaper(view.getContext());
    }

    private void observeWallpaper(android.content.Context context) {
        try {
            var app = context.getApplicationContext();
            (app == null ? context : app).registerReceiver(
                    new android.content.BroadcastReceiver() {
                        @Override
                        public void onReceive(android.content.Context context,
                                android.content.Intent intent) {
                            if (!android.content.Intent.ACTION_WALLPAPER_CHANGED
                                    .equals(intent.getAction())) return;
                            RuntimeClockData.invalidate();
                            widgets.wallpaperReplaced();
                            refreshAttached();
                        }
                    },
                    new android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED),
                    null, main, android.content.Context.RECEIVER_NOT_EXPORTED);
        } catch (RuntimeException unavailable) {
            module.report("Wallpaper profile change watch unavailable", unavailable);
        }
    }

    private void refreshAttached() {
        clocks.removeIf(ref -> ref.get() == null);
        for (WeakReference<View> ref : new ArrayList<>(clocks)) {
            View clock = ref.get();
            if (clock == null || !clock.isAttachedToWindow()) continue;
            apply(clock);
            applyBlur(clock);
            DateEffects.apply(clock);
        }
    }

    private static boolean aodCategory(View view) {
        try {
            return "AOD".equals(String.valueOf(Reflect.call(view, "getCategory")));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void applyDepth(View view, Bundle profile) {
        boolean enabled = profile != null && profile.getBoolean("depthEnabled");
        byte[] mask = profile == null ? null : profile.getByteArray("depthMask");
        boolean hideOnAod = profile != null && profile.getBoolean("depthOffAod");
        boolean aodView = aodCategory(view);
        DepthEffects.apply(view, enabled, mask, hideOnAod, aodView);
        DepthEffects.applyDigits(view, enabled, mask, hideOnAod, aodView);
    }
}
