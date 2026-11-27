package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class RuntimeClockData {
    private record Entry(
            int type, int font, String path, int orientation, int screen, List<Float> heights) {}

    private static final Map<Object, Entry> sources =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> measuringCollapsed = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> readingSource = new ThreadLocal<>();

    private RuntimeClockData() {}

    static void invalidate() {
        sources.clear();
    }

    static boolean measuringCollapsed() {
        return Boolean.TRUE.equals(measuringCollapsed.get());
    }

    static List<Float> manualHeights(Context context, Bundle settings) {
        return settings != null
                        && FontAxisSettings.stretchAllowed(context, settings)
                        && settings.getBoolean("manual")
                        && settings.getFloatArray("manualHeights") != null
                ? SavedStretch.heights(settings.getFloatArray("manualHeights"))
                : null;
    }

    private static List<Float> heights(Object provider, Object font)
            throws ReflectiveOperationException {
        Context context = (Context) Reflect.field(provider, "context");
        try {
            if (!ScreenIdentity.supported()
                    && context.getDisplay() != null
                    && context.getDisplay().getDisplayId() != 0) return null;
        } catch (UnsupportedOperationException ignored) {
        } // Package contexts may not carry a Display.

        int type = ((Number) Reflect.call(provider, "getConsideredThemeClockType")).intValue();
        int id = ((Number) Reflect.call(font, "getFontType")).intValue();
        if (type <= 0 || id <= 0) return null;
        String path = "";
        try {
            Object rawPath = Reflect.call(font, "getFontPath");
            if (rawPath instanceof String) path = (String) rawPath;
        } catch (Exception ignored) {
        }
        int orientation = LayoutOrientation.current(context);
        int screen = ScreenIdentity.current();
        Entry entry = sources.get(provider);
        if (entry == null
                || entry.type != type
                || entry.font != id
                || !entry.path.equals(path)
                || entry.orientation != orientation
                || entry.screen != screen) {
            Bundle settings = settings(context, type, id, path);
            List<Float> values = manualHeights(context, settings);
            if (settings == null) return null; // Provider may still be starting after an update.
            entry = new Entry(type, id, path, orientation, screen, values);
            sources.put(provider, entry);
        }
        return entry.heights;
    }

    private static Bundle settings(Context context, int type, int font, String path) {
        Bundle query = new Bundle();
        query.putInt("type", type);
        query.putInt("font", font);
        if (path != null && !path.isEmpty()) query.putString("fontPath", path);
        return SettingsBridge.read(context, query);
    }

    static List<Float> savedHeights(Context context, int type, int font, String path) {
        return context == null || type <= 0 || font <= 0
                ? null : manualHeights(context, settings(context, type, font, path));
    }

    static void install(DressroomModule module, ClassLoader loader, ClockBinding clock)
            throws ReflectiveOperationException {
        ClockSourceBinding binding = new ClockSourceBinding(loader, clock);
        Method data = binding.data, values = binding.heights;
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(data)
                                    .setId("dressroom-advanced.native-clock-source")
                                    .intercept(
                                            chain -> {
                                                if (readingSource.get() != null) return chain.proceed();
                                                readingSource.set(true);
                                                try {
                                                    Object result = chain.proceed();
                                                    if (result == null) return null;
                                                    try {
                                                        return SavedStretch.font(result,
                                                                heights(chain.getThisObject(), result));
                                                    } catch (ReflectiveOperationException
                                                            | RuntimeException ignored) {
                                                        return result;
                                                    }
                                                } finally {
                                                    readingSource.remove();
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(values)
                                    .setId("dressroom-advanced.native-clock-source-heights")
                                    .intercept(
                                            chain -> {
                                                // On 8.0, native getClockFontData calls this getter.
                                                if (readingSource.get() != null) return chain.proceed();
                                                readingSource.set(true);
                                                try {
                                                    Object result = chain.proceed();
                                                    try {
                                                        Object owner = chain.getThisObject();
                                                        Object font = data.invoke(owner);
                                                        List<Float> saved = font == null ? null : heights(owner, font);
                                                        return saved == null ? result : saved;
                                                    } catch (ReflectiveOperationException
                                                            | RuntimeException ignored) {
                                                        return result;
                                                    }
                                                } finally {
                                                    readingSource.remove();
                                                }
                                            })
                            ::unhook);
            Method wallpaper = binding.wallpaper;
            if (wallpaper != null) {
                hooks.add(module.hook(wallpaper)
                                    .setId("dressroom-advanced.native-clock-source-reset")
                                    .intercept(
                                            chain -> {
                                                sources.remove(chain.getThisObject());
                                                return chain.proceed();
                                            })
                                ::unhook);
                module.deoptimize(wallpaper);
            }
            module.deoptimize(data);
            module.deoptimize(values);
            for (Method caller : binding.callers) module.deoptimize(caller);
            hooks.commit();
        }
        android.util.Log.i(
                DressroomModule.TAG, "Native clock font source/measurement sync installed");
        if (!clock.clockStyle && binding.wallpaper == null) {
            installCollapsedMeasurement(module, loader);
            installAmbientHeights(module, loader);
        }
        if (clock.clockStyle && !clock.legacy) {
            try {
                ClockStyleWidgetPosition.install(module, loader);
            } catch (ReflectiveOperationException
                    | java.io.IOException
                    | RuntimeException
                    | LinkageError error) {
                module.report("Clock Style widget position binding unavailable", error);
            }
        }
    }

    private static void installCollapsedMeasurement(DressroomModule module, ClassLoader loader) {
        try {
            Method detachedClock = OneUi95ClockPlacement.detachedClock(loader);
            for (var caller : DexSignatures.callers(detachedClock))
                if (!caller.name().startsWith("<")) module.deoptimize(caller.method(loader));
            module.deoptimize(detachedClock);
            module.hook(detachedClock)
                    .setId("dressroom-advanced.native-collapsed-clock-measurement")
                    .intercept(chain -> {
                        if (chain.getArg(0) != null || measuringCollapsed()) return chain.proceed();
                        // Samsung measures both the saved height and a baseline with no stretch.
                        measuringCollapsed.set(true);
                        try {
                            return chain.proceed();
                        } finally {
                            measuringCollapsed.remove();
                        }
                    });
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            module.report("Optional detached clock measurement unavailable", error);
        }
    }

    private static void installAmbientHeights(DressroomModule module, ClassLoader loader) {
        try {
            OneUi95ClockPlacement binding = OneUi95ClockPlacement.resolve(loader);
            Method configure = binding.configuration();
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(module.hook(configure)
                        .setId("dressroom-advanced.aod-configuration-font-heights")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                binding.apply(chain.getThisObject(), result);
                            } catch (ReflectiveOperationException | RuntimeException error) {
                                DressroomModule.note("AOD saved clock height unavailable", error);
                            }
                            return result;
                        })::unhook);
                module.deoptimize(configure);
                for (var caller : DexSignatures.callers(configure))
                    if (!caller.name().startsWith("<")) module.deoptimize(caller.method(loader));
                hooks.commit();
            }
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            module.report("Optional AOD clock configuration height unavailable", error);
        }
    }
}
