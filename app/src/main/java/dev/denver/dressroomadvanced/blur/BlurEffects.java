package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class BlurEffects {
    private static final ThreadLocal<Integer> radiusForDraw = new ThreadLocal<>();
    private static final Map<View, Bundle> previews =
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
        if (installed.contains(loader)) return;
        java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> handles =
                new java.util.ArrayList<>();
        boolean addedRadius = false;
        try {
            Class<?> strategy = OneUi9Aod8957Runtime.type("aod.tx0", false, loader);
            Class<?> weather =
                    OneUi9Aod8957Runtime.type(
                            "com.samsung.android.clockpack.plugins.clock.WeatherInfo",
                            false,
                            loader);
            Class<?> options = OneUi9Aod8957Runtime.type("aod.xb0", false, loader);
            Method transition =
                    strategy.getDeclaredMethod("h", int.class, android.widget.ImageView.class);
            handles.add(
                    module.hook(transition)
                            .setId("dressroom-advanced.clock-stable-blur-tick")
                            .intercept(
                                    chain -> {
                                        boolean stable = false;
                                        try {
                                            View clock = clockFromOwner(chain.getThisObject(), "b");
                                            Bundle settings = read(clock);
                                            stable =
                                                    clock != null
                                                            && !DepthEffects.mediaActive(clock)
                                                            && (DepthEffects.hasSubject(clock)
                                                                    || (settings != null
                                                                            && settings.getBoolean(
                                                                                    "forceBlur")));
                                        } catch (Exception ignored) {
                                        }
                                        return stable ? null : chain.proceed();
                                    }));
            module.deoptimize(transition);
            Method draw =
                    strategy.getDeclaredMethod(
                            "b", android.widget.ImageView.class, weather, options);
            handles.add(
                    module.hook(draw)
                            .setId("dressroom-advanced.blur-draw")
                            .intercept(
                                    chain -> {
                                        Integer previous = radiusForDraw.get();
                                        try {
                                            try {
                                                View clock =
                                                        clockFromOwner(chain.getThisObject(), "b");
                                                Bundle settings = read(clock);
                                                if (settings != null)
                                                    radiusForDraw.set(
                                                            clamp(
                                                                    settings.getInt(
                                                                            "blurRadius", 200),
                                                                    1,
                                                                    300));
                                            } catch (Exception ignored) {
                                            }
                                            return chain.proceed();
                                        } finally {
                                            if (previous == null) radiusForDraw.remove();
                                            else radiusForDraw.set(previous);
                                        }
                                    }));
            Class<?> manager = OneUi9Aod8957Runtime.type("aod.cq0", false, loader);
            Method tone = manager.getDeclaredMethod("h");
            handles.add(
                    module.hook(tone)
                            .setId("dressroom-advanced.custom-glass-wallpaper-tone")
                            .intercept(
                                    chain -> {
                                        try {
                                            View clock = clockFromOwner(chain.getThisObject(), "a");
                                            Bundle settings = read(clock);
                                            if (clock != null
                                                    && settings != null
                                                    && settings.getBoolean("forceBlur")
                                                    && !DepthEffects.mediaActive(clock)
                                                    && "Custom"
                                                            .equals(
                                                                    String.valueOf(
                                                                            OneUi9Aod8957Runtime.field(
                                                                                    clock,
                                                                                    "b0")))) {
                                                Object sampled =
                                                        WallpaperBlurTone.read(
                                                                chain.getThisObject().getClass(),
                                                                clock);
                                                if (sampled != null) return sampled;
                                            }
                                        } catch (ReflectiveOperationException
                                                | IllegalStateException ignored) {
                                        }
                                        return chain.proceed();
                                    }));
            module.deoptimize(tone);
            Method collect =
                    manager.getDeclaredMethod("d", ArrayList.class, int.class, float.class);
            handles.add(
                    module.hook(collect)
                            .setId("dressroom-advanced.blur-text-visibility")
                            .intercept(
                                    chain -> {
                                        float nativeValue = ((Number) chain.getArg(2)).floatValue(),
                                                visibility = nativeValue;
                                        try {
                                            View clock = clockFromOwner(chain.getThisObject(), "a");
                                            Bundle settings = read(clock);
                                            if (settings != null) {
                                                boolean custom =
                                                        "Custom"
                                                                .equals(
                                                                        String.valueOf(
                                                                                OneUi9Aod8957Runtime.field(
                                                                                        clock,
                                                                                        "b0")));
                                                double brightness = Double.NaN;
                                                if (custom && settings.getBoolean("forceBlur"))
                                                    brightness =
                                                            ((Number)
                                                                            OneUi9Aod8957Runtime.field(
                                                                                    Reflect.call(
                                                                                            chain
                                                                                                    .getThisObject(),
                                                                                            "h"),
                                                                                    "a"))
                                                                    .doubleValue();
                                                visibility =
                                                        BlurOpacity.resolve(
                                                                settings.getFloat(
                                                                        "blurVisibility", -1),
                                                                settings.getBoolean("forceBlur"),
                                                                custom,
                                                                ((Number)
                                                                                OneUi9Aod8957Runtime.field(
                                                                                        clock,
                                                                                        "l0"))
                                                                        .intValue(),
                                                                brightness,
                                                                nativeValue);
                                            }
                                        } catch (Exception ignored) {
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
            module.deoptimize(manager.getDeclaredMethod("c", int.class, int.class, boolean.class));
            module.deoptimize(base.getDeclaredMethod(OneUi9Aod8957Runtime.matches(loader) ? "C0" : "E0"));
            Method unsupported = base.getDeclaredMethod("y", boolean.class);
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

    static synchronized void installGlyphSync(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        if (glyphSyncInstalled.contains(loader)) return;
        io.github.libxposed.api.XposedInterface.HookHandle handle = null;
        try {
            Class<?> managerType = OneUi9Aod8957Runtime.type("aod.cq0", false, loader);
            Class<?> glyph =
                    OneUi9Aod8957Runtime.type(
                            "com.samsung.android.uniform.widget.NonePaddingTextView",
                            false,
                            loader);
            Method submit = managerType.getDeclaredMethod("c", int.class, int.class, boolean.class);
            handle =
                    module.hook(submit)
                            .setId("dressroom-advanced.native-blur-glyph-ready")
                            .intercept(
                                    chain -> {
                                        Object manager = chain.getThisObject();
                                        try {
                                            View clock = clockFromOwner(manager, "a");
                                            Object strategy = OneUi9Aod8957Runtime.field(manager, "p");
                                            if (clock != null
                                                    && clock.isShown()
                                                    && clock.isAttachedToWindow()
                                                    && !ClockBlurSync.rebuilding(clock)
                                                    && strategy != null
                                                    && OneUi9Aod8957Runtime.field(strategy, "d")
                                                            instanceof java.util.List<?> views
                                                    && (ClockBlurSync.pending(clock)
                                                            || GlyphBlurReadiness.needsDraw(
                                                                    views, glyph))) {
                                                java.lang.ref.WeakReference<Object> reference =
                                                        new java.lang.ref.WeakReference<>(manager);
                                                int color = ((Number) chain.getArg(0)).intValue(),
                                                        retry =
                                                                ((Number) chain.getArg(1))
                                                                        .intValue();
                                                boolean force = (Boolean) chain.getArg(2);
                                                if (ClockBlurSync.defer(
                                                        clock,
                                                        target -> {
                                                            Object current = reference.get();
                                                            if (current == null
                                                                    || !target.isShown()) return;
                                                            try {
                                                                if (OneUi9Aod8957Runtime.field(target, "h1")
                                                                        == current)
                                                                    Reflect.call(
                                                                            current, "c", color,
                                                                            retry, force);
                                                            } catch (Exception error) {
                                                                module.report(
                                                                        "Native blur redraw resume"
                                                                                + " unavailable",
                                                                        error);
                                                            }
                                                        })) return null;
                                            }
                                        } catch (ReflectiveOperationException error) {
                                            module.report(
                                                    "Native blur glyph readiness unavailable",
                                                    error);
                                        }
                                        return chain.proceed();
                                    });
            module.deoptimize(submit);
            module.deoptimize(managerType.getDeclaredMethod("b", Integer.class, boolean.class));
            module.deoptimize(OneUi9Aod8957Runtime.type("aod.yp0", false, loader).getDeclaredMethod("run"));
            module.deoptimize(base.getDeclaredMethod("setClockTextBlur"));
            module.deoptimize(
                    base.getDeclaredMethod(OneUi9Aod8957Runtime.matches(loader) ? "t0" : "x0", int.class, android.widget.TextView[].class));
            glyphSyncInstalled.add(loader);
            Log.i(
                    DressroomModule.TAG,
                    "Native blur submissions synchronized with cached glyph drawing");
        } catch (ReflectiveOperationException | LinkageError error) {
            if (handle != null) handle.unhook();
            module.report("Optional native blur glyph sync unavailable", error);
        }
    }

    static void preview(View clock, boolean enable, int radius, float visibility) {
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

    static void resetPreviews() {
        previews.clear();
        forced.clear();
    }

    static boolean discardOutline(View clock) throws ReflectiveOperationException {
        Object strategy = OneUi9Aod8957Runtime.field(OneUi9Aod8957Runtime.field(clock, "h1"), "p");
        if (strategy == null) return false;
        Reflect.call(strategy, "c");
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
                        if (Boolean.TRUE.equals(OneUi9Aod8957Runtime.field(clock, "s0"))) return;
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

    private static View clockFromOwner(Object owner, String field) throws Exception {
        Object holder = OneUi9Aod8957Runtime.field(owner, field);
        Object clock = OneUi9Aod8957Runtime.field(holder, "o");
        return clock instanceof View ? (View) clock : null;
    }

    private static Bundle read(View clock) {
        if (clock == null) return null;
        Bundle preview = previews.get(clock);
        if (preview != null) return preview;
        try {
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(clock, "getClockType")).intValue());
            query.putInt("font", ((Number) OneUi9Aod8957Runtime.field(clock, "D0")).intValue());
            return SettingsBridge.read(clock.getContext(), query);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
