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
    private final Set<View> effects = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<View, Bundle> profiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Boolean> manual = ThreadLocal.withInitial(() -> false);
    private final Set<View> restoringFont = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mediaReturnStarted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mediaRestorePending = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<View, Integer> settingsRetries = new WeakHashMap<>();
    private final java.util.Map<View, Integer> fontRetries = new WeakHashMap<>();
    private XposedInterface.HookHandle bootstrap;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DeferredDiscovery<ClassLoader> discovery;
    private boolean observing;

    RuntimeClockHooks(DressroomModule module) {
        this.module = module;
        widgets = new RuntimeWidgetHooks(module);
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
                                if (bootstrap != null) {
                                    bootstrap.unhook();
                                    bootstrap = null;
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
            Method load =
                    ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
            bootstrap =
                    module.hook(load)
                            .setId("dressroom-advanced.provider-discovery")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        String name = (String) chain.getArg(0);
                                        if (name.equals(
                                                        "com.samsung.android.uniform.widget.clock.AbsClockView")
                                                || name.equals(
                                                        "com.samsung.android.uniform.plugins.UfClockProvider")) {
                                            discovery.offer(((Class<?>) result).getClassLoader());
                                        }
                                        return result;
                                    });
            android.util.Log.i(DressroomModule.TAG, "Waiting for runtime clock provider");
        } catch (Exception | LinkageError e) {
            module.report("Runtime clock hooks unavailable", e);
        }
    }

    private synchronized void installProvider(Class<?> base) throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader) || !installing.add(loader)) return;
        try (HookTransaction transaction = new HookTransaction()) {
            installation = transaction;
            ClockBinding binding = ClockBinding.of(base);
            DepthEffects.install(module);
            ShadowEffects.install(module);
            if (!binding.legacy) {
                ClockBlurSync.install(module);
                BlurEffects.install(module, base);
                BlurEffects.installGlyphSync(module, base);
                GlyphFontCache.install(module, base);
                GlyphDrawReuse.install(module, loader);
                module.widgetEngine.install(loader);
                widgets.install(loader);
            } else {
                if (!binding.clockStyle) DepthBlurHooks.install(module, base);
                try {
                    module.widgetEngine.installScale(loader);
                    widgets.installLegacy(loader);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("Optional 8.5 widget runtime unavailable", error);
                }
            }
            Class<?> font =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockFontData",
                            false,
                            loader);
            Method balance = binding.balance;
            record(
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
                                        boolean previous = manual.get();
                                        try {
                                            Object requestedFont = chain.getArg(0);
                                            try {
                                                Bundle settings =
                                                        read(
                                                                (View) chain.getThisObject(),
                                                                ((Number)
                                                                                Reflect.call(
                                                                                        chain
                                                                                                .getArg(
                                                                                                        0),
                                                                                        "getFontType"))
                                                                        .intValue());
                                                if (settings != null)
                                                    profiles.put(
                                                            (View) chain.getThisObject(), settings);
                                                boolean held =
                                                        settings != null
                                                                && (settings.getBoolean("manual")
                                                                        || settings.getBoolean(
                                                                                "depthEnabled"));
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
                                            Object result =
                                                    requestedFont == chain.getArg(0)
                                                            ? chain.proceed()
                                                            : chain.proceed(
                                                                    new Object[] {requestedFont});
                                            apply((View) chain.getThisObject());
                                            applyBlur((View) chain.getThisObject());
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
                                                                        "depthEnabled"))))
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
                                            DateEffects.attach(module, view);
                                        } catch (Exception e) {
                                            module.report(
                                                    "Runtime settings observer unavailable", e);
                                        }
                                        apply(view);
                                        applyBlur(view);
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
                                        clock.post(
                                                () -> {
                                                    apply(clock);
                                                    applyBlur(clock);
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
            if (binding.legacy) {
                try {
                    OneUi85MediaHooks.install(module, base);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    module.report("8.5 media depth lifecycle unavailable", error);
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

                                            boolean wasExpanded = false;
                                            try {
                                                wasExpanded =
                                                        Boolean.TRUE.equals(
                                                                Reflect.field(clock, "d1"));
                                            } catch (Exception ignored) {
                                            }
                                            if (wasExpanded
                                                    && Boolean.FALSE.equals(chain.getArg(0))) {
                                                Bundle profile = profiles.get(clock);
                                                if (profile != null
                                                        && (profile.getBoolean("manual")
                                                                || profile.getBoolean(
                                                                        "depthEnabled"))
                                                        && profile.getFloatArray("manualHeights")
                                                                != null)
                                                    try {
                                                        BlurEffects.discardOutline(clock);
                                                    } catch (Exception error) {
                                                        module.report(
                                                                "Media compact blur cleanup"
                                                                        + " unavailable",
                                                                error);
                                                    }
                                            }
                                            if (Boolean.TRUE.equals(chain.getArg(0))) {
                                                ClockBlurSync.cancel(clock);
                                                finishMediaReturn(clock);
                                            }
                                            Object result = chain.proceed();
                                            if (wasExpanded
                                                    && Boolean.FALSE.equals(chain.getArg(0))) {
                                                mediaReturnStarted.remove(clock);
                                                mediaRestorePending.add(clock);
                                                AodStretchExtras.mediaReturn(clock, true);
                                                clock.postOnAnimation(
                                                        () -> restoreAfterMedia(clock));
                                            }
                                            return result;
                                        }));
                module.deoptimize(media);
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
                            + (binding.legacy ? "8.5 core" : "reference"));
            transaction.commit();
            installed.add(loader);
        } finally {
            installation = null;
            installing.remove(loader);
        }
    }

    private void record(XposedInterface.HookHandle handle) {
        installation.add(handle::unhook);
    }

    private void applyBlur(View view) {
        if (!ClockBinding.of(view).legacy) BlurEffects.apply(view);
    }

    private Bundle read(View view, int font) {
        try {
            String category = String.valueOf(Reflect.call(view, "getCategory"));
            if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) return null;
            if (view.getDisplay() != null && view.getDisplay().getDisplayId() != 0) return null;
            if (SettingsBridge.wallpaperId(view.getContext()) < 0)
                throw new IllegalStateException("Wallpaper identity is not ready");
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(view, "getClockType")).intValue());
            query.putInt("font", font);
            return SettingsBridge.read(view.getContext(), query);
        } catch (Exception e) {
            throw new IllegalStateException("Runtime settings temporarily unavailable", e);
        }
    }

    private void restoreAfterMedia(View clock) {
        try {
            if (!mediaRestorePending.contains(clock)) return;
            if (!clock.isAttachedToWindow()) return;
            Object controller = Reflect.field(clock, "W0");
            if (Boolean.TRUE.equals(Reflect.field(controller, "h"))) {
                finishMediaReturn(clock);
                return;
            }
            boolean dozing = Boolean.TRUE.equals(Reflect.field(controller, "d"));
            if (dozing && !RuntimeExtras.enabled(clock.getContext(), "keepStretch")) return;
            Bundle profile = profiles.get(clock);
            if (profile == null
                    || (!profile.getBoolean("manual") && !profile.getBoolean("depthEnabled"))) {
                finishMediaReturn(clock);
                return;
            }
            List<Float> heights = SavedStretch.heights(profile.getFloatArray("manualHeights"));
            Object original = Reflect.field(clock, "T0");
            if (original == null || heights == null) {
                finishMediaReturn(clock);
                return;
            }
            Object restored = SavedStretch.font(original, heights);
            if (restored != original) Reflect.call(clock, "setFontType", restored);
            if (!dozing) {
                android.animation.Animator animation =
                        (android.animation.Animator) Reflect.field(controller, "i");
                if (animation != null && animation.isStarted()) mediaReturnStarted.add(clock);
                if (mediaReturnStarted.add(clock)) {
                    Reflect.call(clock, "K0", 0f, false);
                    animation = (android.animation.Animator) Reflect.field(controller, "i");
                }
                WeakReference<View> reference = new WeakReference<>(clock);
                if (MediaReturnCompletion.await(
                        clock,
                        animation,
                        () -> {
                            View view = reference.get();
                            if (view != null) view.postOnAnimation(() -> restoreAfterMedia(view));
                        })) return;
            }
            Reflect.call(clock, "g0", restored);
            Reflect.call(clock, "refresh");
            clock.requestLayout();
            finishMediaReturn(clock);
        } catch (Exception error) {
            finishMediaReturn(clock);
            module.report("Manual media-exit restore unavailable", error);
        }
    }

    private void finishMediaReturn(View clock) {
        mediaRestorePending.remove(clock);
        mediaReturnStarted.remove(clock);
        MediaReturnCompletion.cancel(clock);
        AodStretchExtras.mediaReturn(clock, false);
    }

    private void apply(View view) {
        boolean providerReady = false;
        try {
            Bundle settings = read(view, ClockBinding.of(view).fontId(view));
            providerReady = true;
            if (settings != null) settingsRetries.remove(view);
            if (settings == null && ClockBinding.of(view).fontId(view) == 0) {
                Bundle previous = profiles.get(view);
                if (previous != null
                        && previous.getInt("wallpaperId", -1)
                                == SettingsBridge.wallpaperId(view.getContext())
                        && previous.getInt(LayoutOrientation.FIELD)
                                == LayoutOrientation.current(view.getContext()))
                    settings = previous;
            }
            if (settings == null) {
                profiles.remove(view);
                if (effects.remove(view)) ShadowEffects.apply(view, false, 0, 0, 0, 0);
                DepthEffects.apply(view, false, null);
                DepthEffects.applyDigits(view, false, null);
                int attempt = settingsRetries.getOrDefault(view, 0);
                if (attempt < 4 && view.isAttachedToWindow()) {
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
            if ((settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))
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
            if (DepthBinding.provider(view)) {
                DepthEffects.apply(
                        view,
                        settings.getBoolean("depthEnabled"),
                        settings.getByteArray("depthMask"));
                DepthEffects.applyDigits(
                        view,
                        settings.getBoolean("depthEnabled"),
                        settings.getByteArray("depthMask"));
            }
            effects.add(view);
            ClockBinding.of(view).refreshAppearance(view);
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
        if (!ClockBinding.of(view).legacy) MediaClockContrast.track(view);
        clocks.removeIf(reference -> reference.get() == null);
        for (WeakReference<View> ref : clocks) if (ref.get() == view) return;
        clocks.add(new WeakReference<>(view));
        view.addOnLayoutChangeListener(
                (clock, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                    Bundle profile = profiles.get(clock);
                    if (profile == null
                            || profile.getInt(LayoutOrientation.FIELD)
                                    != LayoutOrientation.current(clock.getContext())) {
                        apply(clock);
                        applyBlur(clock);
                        DateEffects.apply(clock);
                        profile = profiles.get(clock);
                    }
                    if (profile != null && DepthBinding.provider(clock)) {
                        DepthEffects.applyDigits(
                                clock,
                                profile.getBoolean("depthEnabled"),
                                profile.getByteArray("depthMask"));
                    }
                });
        if (observing) return;
        observing = true;
        view.getContext()
                .getContentResolver()
                .registerContentObserver(
                        SettingsProvider.URI,
                        false,
                        new ContentObserver(new Handler(Looper.getMainLooper())) {
                            @Override
                            public void onChange(boolean selfChange) {
                                RuntimeClockData.invalidate();
                                clocks.removeIf(ref -> ref.get() == null);
                                for (WeakReference<View> ref : clocks) {
                                    View clock = ref.get();
                                    if (clock != null && clock.isAttachedToWindow()) {
                                        apply(clock);
                                        applyBlur(clock);
                                        DateEffects.apply(clock);
                                    }
                                }
                            }
                        });
    }
}
