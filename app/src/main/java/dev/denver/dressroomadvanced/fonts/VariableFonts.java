package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.fonts.FontVariationAxis;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class VariableFonts {
    interface Profile {
        Bundle settings(View view);
    }

    static final class Axes {
        final List<FontAxes.Axis> values;

        Axes(List<FontAxes.Axis> values) {
            this.values = values;
        }

        boolean hasAny() {
            return !values.isEmpty();
        }
    }

    private static final class Source {
        final String key;
        final File file;
        final AssetManager assets;
        final String asset;
        final Axes axes;
        final android.net.Uri uri;

        Source(String key, File file, AssetManager assets, String asset, Axes axes) {
            this(key, file, assets, asset, axes, null);
        }

        Source(
                String key,
                File file,
                AssetManager assets,
                String asset,
                Axes axes,
                android.net.Uri uri) {
            this.uri = uri;
            this.key = key;
            this.file = file;
            this.assets = assets;
            this.asset = asset;
            this.axes = axes;
        }
    }

    private static final Map<String, Source> sources =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(8, .75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Source> eldest) {
                            return size() > 6;
                        }
                    });
    private static final Map<String, Typeface> typefaces =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(16, .75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Typeface> eldest) {
                            return size() > 24;
                        }
                    });
    private static final Map<View, String> applied =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> importedClocks =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<Method> renderers = new java.util.HashSet<>();
    private static final Map<View, Bundle> configured =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, List<java.lang.ref.WeakReference<TextView>>> targets =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VariableFonts() {}

    static synchronized void install(DressroomModule module, Class<?> base, Profile profile) {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            FontBinding binding = FontBinding.of(base);
            Method apply = binding.fixed;
            hooks.add(
                    module.hook(apply)
                                    .setId("dressroom-advanced.variable-font-typeface")
                                    .intercept(
                                            chain -> {
                                                Object owner = chain.getThisObject();
                                                if (!(owner instanceof View))
                                                    return chain.proceed();
                                                View clock = (View) owner;
                                                Object font = font(clock);
                                                if (font == null) return chain.proceed();
                                                Bundle settings = settings(clock, profile);
                                                Bundle values = FontAxisSettings.values(settings);
                                                Typeface replacement =
                                                        typeface(
                                                                clock.getContext(),
                                                                font,
                                                                values,
                                                                fileId(settings));
                                                applied.put(clock, profileKey(settings));
                                                if (replacement == null) return chain.proceed();
                                                TextView[] views = (TextView[]) chain.getArg(1);
                                                remember(clock, views);
                                                Object result =
                                                        chain.proceed(
                                                                new Object[] {
                                                                    replacement, chain.getArg(1)
                                                                });
                                                ensureTypeface(views, replacement);
                                                return result;
                                            })
                            ::unhook);
            module.deoptimize(apply);
            Method variable = binding.variable;
            hooks.add(
                    module.hook(variable)
                                    .setId("dressroom-advanced.imported-font-typeface")
                                    .intercept(
                                            chain -> {
                                                if (!(chain.getThisObject() instanceof View clock)
                                                        || !importedClocks.containsKey(clock))
                                                    return chain.proceed();
                                                try {
                                                    Bundle settings = settings(clock, profile);
                                                    if (!fileId(settings).isEmpty()) {
                                                        Typeface replacement =
                                                                typeface(
                                                                        clock.getContext(),
                                                                        font(clock),
                                                                        FontAxisSettings.values(
                                                                                settings),
                                                                        fileId(settings));
                                                        if (replacement != null) {
                                                            TextView[] views =
                                                                    (TextView[]) chain.getArg(2);
                                                            // This call may bypass the fixed-font
                                                            // hook. Keep the live targets here too.
                                                            remember(clock, views);
                                                            apply.invoke(clock, replacement, views);
                                                            ensureTypeface(views, replacement);
                                                            applied.put(
                                                                    clock, profileKey(settings));
                                                            return null;
                                                        }
                                                    }
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException
                                                        | LinkageError error) {
                                                    android.util.Log.w(
                                                            DressroomModule.TAG,
                                                            "Imported font unavailable; using"
                                                                    + " native font",
                                                            error);
                                                }
                                                return chain.proceed();
                                            })
                            ::unhook);
            module.deoptimize(variable);
            hooks.commit();
            installed.add(loader);
            android.util.Log.i(DressroomModule.TAG, "Variable font typeface support installed");
        } catch (RuntimeException | LinkageError error) {
            module.report("Variable font support unavailable", error);
        }
    }

    static Axes axes(Context context, String path, int type) {
        if (!eligible(type, path)) return null;
        Source source = source(context, path, type);
        return source == null ? null : source.axes;
    }

    static synchronized void configure(DressroomModule module, View clock, Bundle settings) {
        configure(clock, settings);
        if (FontAxisSettings.nativeStretch(settings)) return;
        try {
            FontBinding binding = FontBinding.of(clock);
            Method render = binding.renderer(clock.getClass());
            if (renderers.contains(render)) return;
            module.hook(render)
                    .setId("dressroom-advanced.imported-font-layout")
                    .intercept(
                            chain -> {
                                if (!(chain.getThisObject() instanceof View owner)
                                        || FontAxisSettings.nativeStretch(configured.get(owner)))
                                    return chain.proceed();
                                Object original = chain.getArg(0);
                                // Keep the saved font identity on the clock; only the drawing
                                // request uses the static path.
                                Object plain = binding.plain(original);
                                return chain.proceed(new Object[] {plain});
                            });
            module.deoptimize(render);
            renderers.add(render);
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.report("Imported font layout unavailable", error);
        }
    }

    static void configure(View clock, Bundle settings) {
        if (settings == null) configured.remove(clock);
        else configured.put(clock, settings);
        if (fileId(settings).isEmpty()) importedClocks.remove(clock);
        else importedClocks.put(clock, true);
    }

    private static Bundle settings(View clock, Profile fallback) {
        Bundle settings = configured.get(clock);
        return settings != null || fallback == null ? settings : fallback.settings(clock);
    }

    static String fileId(Bundle profile) {
        return profile == null ? "" : profile.getString("fontFile", "");
    }

    private static String profileKey(Bundle profile) {
        return fileId(profile) + "|" + FontAxisSettings.key(FontAxisSettings.values(profile));
    }

    static Axes importedAxes(Context context, String id) {
        Source source = imported(context, id);
        return source == null ? null : source.axes;
    }

    private static Source imported(Context context, String id) {
        if (id == null || id.isEmpty()) return null;
        String key = "import|" + id;
        Source cached = sources.get(key);
        if (cached != null) return cached;
        try {
            android.net.Uri uri = FontFiles.uri(id);
            byte[] bytes = read(context.getContentResolver().openInputStream(uri));
            if (bytes == null) return null;
            Source source = new Source(key, null, null, null, parse(bytes), uri);
            sources.put(key, source);
            return source;
        } catch (RuntimeException | java.io.IOException error) {
            return null;
        }
    }

    static Typeface typeface(Context context, Object font, Bundle values, String imported) {
        if (font == null) return null;
        try {
            int type = ((Number) Reflect.call(font, "getFontType")).intValue();
            Object rawPath = Reflect.call(font, "getFontPath");
            String path = rawPath instanceof String ? (String) rawPath : "";
            Object rawWeight = Reflect.call(font, "getFontWeight");
            Integer weight = null;
            if (imported.isEmpty() && rawWeight instanceof Number) {
                float value = ((Number) rawWeight).floatValue();
                if (Float.isFinite(value) && value > 0) weight = Math.round(value);
            }
            if (imported.isEmpty() && !eligible(type, path)) return null;
            Source source =
                    imported.isEmpty() ? source(context, path, type) : imported(context, imported);
            if (source == null || (source.axes == null && imported.isEmpty())) return null;
            List<FontVariationAxis> variations = new ArrayList<>();
            StringBuilder keyParts = new StringBuilder(source.key);
            for (FontAxes.Axis axis :
                    source.axes == null ? List.<FontAxes.Axis>of() : source.axes.values) {
                float value =
                        values != null && values.containsKey(axis.tag())
                                ? values.getFloat(axis.tag())
                                : axis.tag().equals("wght") && weight != null
                                        ? weight
                                        : axis.defaultValue();
                if (!Float.isFinite(value)) value = axis.defaultValue();
                value = axis.clamp(value);
                variations.add(new FontVariationAxis(axis.tag(), value));
                keyParts.append('|')
                        .append(axis.tag())
                        .append('=')
                        .append(Float.floatToIntBits(value));
            }
            if (variations.isEmpty() && imported.isEmpty()) return null;
            String key = keyParts.toString();
            Typeface cached = typefaces.get(key);
            if (cached != null) return cached;
            Typeface result;
            if (source.uri != null) {
                try (android.os.ParcelFileDescriptor fd =
                        context.getContentResolver().openFileDescriptor(source.uri, "r")) {
                    if (fd == null) return null;
                    Typeface.Builder builder = new Typeface.Builder(fd.getFileDescriptor());
                    if (!variations.isEmpty())
                        builder.setFontVariationSettings(
                                variations.toArray(new FontVariationAxis[0]));
                    result = builder.build();
                }
            } else if (source.asset != null) {
                Typeface.Builder builder = new Typeface.Builder(source.assets, source.asset);
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                result = builder.build();
            } else if (source.file != null) {
                Typeface.Builder builder = new Typeface.Builder(source.file);
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                result = builder.build();
            } else return null;
            if (result != null) typefaces.put(key, result);
            return result;
        } catch (Throwable error) {
            return null;
        }
    }

    static void preview(View clock, Bundle settings) {
        if (clock == null) return;
        Object font = font(clock);
        Bundle values = FontAxisSettings.values(settings);
        configure(clock, settings);
        Typeface replacement = typeface(clock.getContext(), font, values, fileId(settings));
        if (replacement == null) return;
        try {
            List<TextView> current = new ArrayList<>();
            List<java.lang.ref.WeakReference<TextView>> remembered = targets.get(clock);
            if (remembered != null)
                for (java.lang.ref.WeakReference<TextView> reference : remembered) {
                    TextView view = reference.get();
                    if (view != null) current.add(view);
                }
            // Go through the native setter so its fixed-typeface cache follows the preview.
            if (current.isEmpty()) ClockBinding.of(clock).render(clock, font);
            else {
                TextView[] views = current.toArray(new TextView[0]);
                FontBinding.of(clock).apply(clock, replacement, views);
                ensureTypeface(views, replacement);
            }
            applied.put(clock, profileKey(settings));
            clock.requestLayout();
            clock.invalidate();
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Variable font preview unavailable", error);
        }
    }

    private static void ensureTypeface(TextView[] views, Typeface typeface) {
        if (views == null) return;
        for (TextView view : views) {
            if (view != null && view.getTypeface() != typeface) view.setTypeface(typeface);
        }
    }

    private static void remember(View clock, TextView[] views) {
        List<java.lang.ref.WeakReference<TextView>> current = targets.get(clock);
        List<TextView> unique = new ArrayList<>();
        if (current != null)
            for (java.lang.ref.WeakReference<TextView> reference : current) {
                TextView view = reference.get();
                if (view != null && view.isAttachedToWindow() && !unique.contains(view))
                    unique.add(view);
            }
        if (views != null)
            for (TextView view : views)
                if (view != null && !unique.contains(view) && unique.size() < 64) unique.add(view);
        List<java.lang.ref.WeakReference<TextView>> remembered = new ArrayList<>();
        for (TextView view : unique) remembered.add(new java.lang.ref.WeakReference<>(view));
        targets.put(clock, remembered);
    }

    static void forget(View clock) {
        if (clock != null) {
            applied.remove(clock);
            targets.remove(clock);
        }
    }

    static boolean needsRefresh(View clock, Bundle profile) {
        Bundle values = FontAxisSettings.values(profile);
        String previous = applied.get(clock);
        return previous == null
                ? !values.isEmpty() || !fileId(profile).isEmpty()
                : !previous.equals(profileKey(profile));
    }

    private static Object font(View clock) {
        try {
            return ClockBinding.of(clock).font(clock);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static Source source(Context context, String path, int type) {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty() && (type == 700 || type == 0)) value = themeFont(context);
        if (value.isEmpty()) return null;
        String key = type + "|" + value;
        Source cached = sources.get(key);
        if (cached != null) return cached;
        Source result = resolve(context, value);
        if (result != null) sources.put(key, result);
        return result;
    }

    private static Source resolve(Context context, String value) {
        File file = new File(value);
        if (value.endsWith(".apk")) {
            try {
                PackageInfo info = context.getPackageManager().getPackageArchiveInfo(value, 0);
                if (info != null) return packageSource(context, info.packageName);
            } catch (Throwable ignored) {
            }
            return null;
        }
        if (isFile(value)) {
            String key =
                    "file|"
                            + file.getAbsolutePath()
                            + "|"
                            + file.length()
                            + "|"
                            + file.lastModified();
            Source cached = sources.get(key);
            if (cached != null) return cached;
            byte[] data = read(file);
            return data == null ? null : new Source(key, file, null, null, parse(data));
        }
        if (value.indexOf('/') < 0 && value.indexOf(':') < 0 && value.contains("."))
            return packageSource(context, value);
        return null;
    }

    private static Source packageSource(Context context, String packageName) {
        try {
            Resources resources =
                    context.getPackageManager().getResourcesForApplication(packageName);
            AssetManager assets = resources.getAssets();
            List<String> candidates = new ArrayList<>();
            collect(assets, "fonts", candidates, 0);
            if (candidates.isEmpty()) collect(assets, "", candidates, 0);
            Source fallback = null;
            for (String asset : candidates) {
                byte[] data = read(assets.open(asset));
                if (data == null) continue;
                String key = "asset|" + packageName + "|" + asset + "|" + data.length;
                Axes axes = parse(data);
                Source source = new Source(key, null, assets, asset, axes);
                if (axes != null && axes.hasAny()) return source;
                if (fallback == null) fallback = source;
            }
            return fallback;
        } catch (Throwable error) {
            return null;
        }
    }

    private static void collect(
            AssetManager assets, String directory, List<String> candidates, int depth) {
        try {
            String[] names = assets.list(directory);
            if (names == null) return;
            for (String name : names) {
                String path = directory.isEmpty() ? name : directory + "/" + name;
                if (isFontName(name)) candidates.add(path);
                else if (depth < 2) collect(assets, path, candidates, depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isFontName(String name) {
        String value = name.toLowerCase(java.util.Locale.ROOT);
        return value.endsWith(".ttf") || value.endsWith(".otf") || value.endsWith(".ttc");
    }

    private static boolean eligible(int type, String path) {
        return type == 700 || (type == 0 && isFile(path));
    }

    private static boolean isFile(String path) {
        if (path == null || path.isEmpty()) return false;
        try {
            return new File(path).isFile();
        } catch (Throwable error) {
            return false;
        }
    }

    private static String themeFont(Context context) {
        try {
            String path =
                    Settings.System.getString(context.getContentResolver(), "theme_font_clock");
            if (path == null || path.isEmpty())
                path = Settings.System.getString(context.getContentResolver(), "theme_font");
            return path == null ? "" : path;
        } catch (Throwable error) {
            return "";
        }
    }

    private static byte[] read(File file) {
        try (InputStream input = new FileInputStream(file)) {
            return read(input);
        } catch (Throwable error) {
            return null;
        }
    }

    private static byte[] read(InputStream input) {
        try (InputStream source = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (output.size() > 64 * 1024 * 1024 - count) return null;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Throwable error) {
            return null;
        }
    }

    private static Axes parse(byte[] data) {
        List<FontAxes.Axis> values = FontAxes.parse(data);
        return values.isEmpty() ? null : new Axes(values);
    }
}
