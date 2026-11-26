package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class BlurEffects {
    private static final ThreadLocal<Integer> radiusForDraw = new ThreadLocal<>();
    private static final Map<View, Bundle> previews =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Bundle> runtimeSettings =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> forced = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> glyphSyncInstalled =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean radiusHooked;

    private BlurEffects() {}

    static synchronized void install(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        DepthBlurHooks.install(module, base);
        StretchSuggestions.install(module, base);
        if (installed.contains(loader)) return;
        java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> handles =
                new java.util.ArrayList<>();
        boolean addedRadius = false;
        try {
            ClockBlurBinding blur = ClockBlurBinding.resolve(base);
            Method draw = blur.render;
            handles.add(
                    module.hook(draw)
                            .setId("dressroom-advanced.blur-draw")
                            .intercept(
                                    chain -> {
                                        Integer previous = radiusForDraw.get();
                                        try {
                                            try {
                                                View clock = blur.clock(chain.getThisObject());
                                                Bundle settings = read(clock);
                                                int radius = settings == null
                                                        ? 200 : settings.getInt("blurRadius", 200);
                                                if (radius != 200)
                                                    radiusForDraw.set(clamp(radius, 1, 300));
                                                else radiusForDraw.remove();
                                            } catch (Exception ignored) {
                                            }
                                            return chain.proceed();
                                        } finally {
                                            if (previous == null) radiusForDraw.remove();
                                            else radiusForDraw.set(previous);
                                        }
                                    }));
            ClockBlurRetryBinding retry = ClockBlurRetryBinding.of(base);
            Class<?> manager = retry.manager.getType();
            Method collect = retry.collect;
            Method submit = retry.submit;
            java.lang.reflect.Field targetAlpha = targetAlpha(manager, collect, submit);
            handles.add(
                    module.hook(collect)
                            .setId("dressroom-advanced.blur-text-visibility")
                            .intercept(
                                    chain -> {
                                        float nativeValue = ((Number) chain.getArg(2)).floatValue(),
                                                visibility = nativeValue;
                                        boolean explicit = false;
                                        try {
                                            View clock = retry.clock(chain.getThisObject());
                                            Bundle settings = read(clock);
                                            if (settings != null) {
                                                float requested = settings.getFloat("blurVisibility", -1);
                                                explicit = Float.isFinite(requested) && requested >= 0f;
                                                visibility = BlurOpacity.resolve(requested, nativeValue);
                                            }
                                        } catch (Exception ignored) {
                                        }
                                        if (targetAlpha != null && explicit) {
                                            Object result = chain.proceed();
                                            try {
                                                targetAlpha.setInt(chain.getThisObject(),
                                                        Math.round(visibility * 255f));
                                            } catch (IllegalAccessException ignored) {
                                            }
                                            return result;
                                        }
                                        return visibility == nativeValue
                                                ? chain.proceed()
                                                : chain.proceed(
                                                        new Object[] {
                                                            chain.getArg(0),
                                                            chain.getArg(1),
                                                            Math.max(0f, Math.min(1f, visibility))
                                                        });
                                    }));
            module.deoptimize(collect);
            module.deoptimize(submit);
            module.deoptimize(ClockGlassBinding.of(base).refresh);
            Method unsupported = resolveUnsupported(base);
            handles.add(
                    module.hook(unsupported)
                            .setId("dressroom-advanced.force-blur-capability")
                            .intercept(
                                    chain -> {
                                        Bundle settings = read((View) chain.getThisObject());
                                        return settings != null && settings.getBoolean("forceBlur")
                                                ? null
                                                : chain.proceed();
                                    }));
            if (!radiusHooked) {
                Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
                Method setRadius = builder.getDeclaredMethod("setRadius", int.class);
                handles.add(
                        module.hook(setRadius)
                                .setId("dressroom-advanced.blur-radius")
                                .intercept(
                                        chain -> {
                                            Integer radius = radiusForDraw.get();
                                            return radius == null
                                                    ? chain.proceed()
                                                    : chain.proceed(new Object[] {radius});
                                        }));
                radiusHooked = true;
                addedRadius = true;
            }
            installed.add(loader);
            Log.i(DressroomModule.TAG, "Provider blur controls installed");
        } catch (Exception | LinkageError e) {
            for (io.github.libxposed.api.XposedInterface.HookHandle handle : handles)
                handle.unhook();
            if (addedRadius) radiusHooked = false;
            module.report("Optional provider blur controls unavailable", e);
        }
    }

    private static Method resolveUnsupported(Class<?> clockBase)
            throws ReflectiveOperationException, IOException {
        Class<?> abs = ClockBlurRetryBinding.absClockView(clockBase);
        String absDesc = DexSignatures.descriptor(abs);
        List<Method> matches = new ArrayList<>();
        for (DexSignatures.Body body :
                DexSignatures.find(abs.getClassLoader(), Set.of("Blur request - disableBlur"))) {
            if ((body.access() & 8) == 0
                    && body.owner().equals(absDesc)
                    && body.result().equals("V")
                    && body.parameters().equals(List.of("Z"))) {
                matches.add(body.method(abs.getClassLoader()));
            }
        }
        if (matches.size() != 1)
            throw new NoSuchMethodException(
                    "Ambiguous disableBlur method on " + abs.getName() + ": " + matches.size());
        return matches.get(0);
    }

    private static java.lang.reflect.Field targetAlpha(Class<?> manager, Method collect, Method submit) {
        try {
            String owner = DexSignatures.descriptor(manager);
            var field = DexSignatures.body(submit).uniqueRead(owner, "I", null);
            if (DexSignatures.body(collect).reads().stream().noneMatch(read -> read.field().equals(field)))
                return null;
            return field.field(manager.getClassLoader());
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException unavailable) {
            return null;
        }
    }

    static synchronized void installGlyphSync(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        if (glyphSyncInstalled.contains(loader)) return;
        try {
            ClockBlurRetries.install(module, base);
            glyphSyncInstalled.add(loader);
            Log.i(DressroomModule.TAG, "Native blur glyph sync and retry lifecycle installed");
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError error) {
            module.report("Optional native blur glyph sync unavailable", error);
        }
    }

    static void preview(View clock, boolean enable, int radius, float visibility) {
        BlurAppearance.trackClock(clock);
        Bundle value = new Bundle();
        value.putBoolean("forceBlur", enable);
        value.putInt("blurRadius", radius);
        value.putFloat("blurVisibility", visibility);
        previews.put(clock, value);
        if (!"com.samsung.android.app.dressroom".equals(clock.getContext().getOpPackageName()))
            apply(clock);
    }

    static void clearPreview(View clock) {
        previews.remove(clock);
    }

    static void bindSettings(View clock, Bundle settings) {
        if (clock == null) return;
        if (settings == null) runtimeSettings.remove(clock);
        else runtimeSettings.put(clock, settings);
    }

    static void resetPreviews() {
        previews.clear();
        runtimeSettings.clear();
        forced.clear();
    }

    static boolean discardOutline(View clock) throws ReflectiveOperationException {
        ClockGlassBinding glass = ClockGlassBinding.of(clock);
        Object manager = glass.manager.get(clock);
        Object strategy = manager == null ? null : glass.strategy.get(manager);
        if (strategy == null) return false;
        ClockBlurBinding.resolve(clock.getClass()).clear.invoke(strategy);
        return true;
    }

    static boolean discardStrategy(Object strategy) throws ReflectiveOperationException {
        if (strategy == null) return false;
        try {
            ClockBlurBinding.resolveClear(strategy.getClass()).invoke(strategy);
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read clock blur strategy DEX", invalidDex);
        }
        return true;
    }

    static void apply(View clock) {
        Bundle settings = read(clock);
        boolean enable = settings != null && settings.getBoolean("forceBlur");
        if (!enable) {
            if (forced.remove(clock))
                clock.post(
                        () -> {
                            try {
                                Reflect.call(clock, "setBlurEffectEnabled", false);
                            } catch (Exception e) {
                                Log.w(DressroomModule.TAG, "Could not clear forced blur", e);
                            }
                        });
            return;
        }
        forced.add(clock);
        clock.post(
                () -> {
                    try {
                        if (!clock.isAttachedToWindow() || !clock.isShown()) return;
                        if (Boolean.TRUE.equals(ClockGlassBinding.of(clock).dozing.get(clock))) return;
                        Reflect.call(clock, "setBlurEffectEnabled", true);
                        float scale = ((Number) Reflect.call(clock, "getScale")).floatValue();
                        Reflect.call(
                                clock, "addBlur", Float.isFinite(scale) && scale > 0f ? scale : 1f);
                        Reflect.call(clock, "setClockTextBlur");
                    } catch (Exception e) {
                        Log.w(DressroomModule.TAG, "Forced blur unavailable for clock", e);
                    }
                });
    }

    private static Bundle read(View clock) {
        if (clock == null) return null;
        Bundle preview = previews.get(clock);
        if (preview != null) return preview;
        Bundle bound = runtimeSettings.get(clock);
        if (bound != null) return bound;
        try {
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(clock, "getClockType")).intValue());
            query.putInt("font", ((Number) ClockBinding.of(clock).fontId(clock)).intValue());
            Bundle loaded = SettingsBridge.readRuntimeClock(clock.getContext(), query);
            if (loaded != null) runtimeSettings.put(clock, loaded);
            return loaded;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
