package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VariableFonts {
    interface Profile {
        Bundle settings(View view);
    }

    static final class Axes {
        final List<FontAxes.Axis> values;
        final FontAxes.Axis heightAxis;

        Axes(List<FontAxes.Axis> values) {
            this.values = values;
            this.heightAxis = FontAxes.heightAxis(values);
        }

        boolean hasAny() {
            return !values.isEmpty();
        }

        FontAxes.Axis heightAxis() {
            return heightAxis;
        }
    }

    private static final class Source {
        final String key;
        final File file;
        final AssetManager assets;
        final String asset;
        final Axes axes;
        final android.net.Uri uri;
        final Font baseFont;

        Source(String key, File file, AssetManager assets, String asset, Axes axes, Font baseFont) {
            this(key, file, assets, asset, axes, null, baseFont);
        }

        Source(
                String key,
                File file,
                AssetManager assets,
                String asset,
                Axes axes,
                android.net.Uri uri,
                Font baseFont) {
            this.uri = uri;
            this.key = key;
            this.file = file;
            this.assets = assets;
            this.asset = asset;
            this.axes = axes;
            this.baseFont = baseFont;
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
                    new LinkedHashMap<>(64, .75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Typeface> eldest) {
                            return size() > 128;
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
    private static final Map<TextView, Typeface> measuredTypefaces =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView, Float> digitStretchHegt =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> applyingDirect = new ThreadLocal<>();
    private static final ClassValue<Method> variableClockFontSetter =
            new ClassValue<>() {
                @Override
                protected Method computeValue(Class<?> type) {
                    try {
                        Method setter = type.getMethod("setVariableClockFont", boolean.class);
                        setter.setAccessible(true);
                        return setter;
                    } catch (NoSuchMethodException | SecurityException ignored) {
                        return null;
                    }
                }
            };

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
                                                if (Boolean.TRUE.equals(applyingDirect.get()))
                                                    return chain.proceed();
                                                Object owner = chain.getThisObject();
                                                if (!(owner instanceof View))
                                                    return chain.proceed();
                                                View clock = (View) owner;
                                                Object font = font(clock);
                                                if (font == null) return chain.proceed();
                                                Bundle settings = settings(clock, profile);
                                                Bundle values = FontAxisSettings.values(settings);
                                                String imported = fileId(settings);
                                                Typeface replacement =
                                                        typeface(
                                                                clock.getContext(),
                                                                font,
                                                                values,
                                                                imported);
                                                if (replacement == null) {
                                                    if (values.isEmpty() && imported.isEmpty())
                                                        applied.put(clock, profileKey(settings));
                                                    return chain.proceed();
                                                }
                                                TextView[] views = (TextView[]) chain.getArg(1);
                                                remember(clock, views);
                                                Object result =
                                                        chain.proceed(
                                                                new Object[] {
                                                                    replacement, chain.getArg(1)
                                                                });
                                                ensureTypeface(
                                                        views,
                                                        replacement,
                                                        hasVariableAxes(
                                                                clock.getContext(), font, imported));
                                                applied.put(clock, profileKey(settings));
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
                                                    String imported = fileId(settings);
                                                    if (!imported.isEmpty()) {
                                                        String nativeVariation =
                                                                chain.getArg(1) instanceof String s
                                                                        ? s
                                                                        : null;
                                                        TextView[] views =
                                                                (TextView[]) chain.getArg(2);
                                                        float nativeHegt =
                                                                FontAxes.parseVariationAxis(
                                                                        nativeVariation, "HEGT");
                                                        if (views != null) {
                                                            for (TextView v : views) {
                                                                if (v == null) continue;
                                                                if (Float.isFinite(nativeHegt)) {
                                                                    digitStretchHegt.put(
                                                                            v, nativeHegt);
                                                                } else {
                                                                    digitStretchHegt.remove(v);
                                                                }
                                                            }
                                                        }
                                                        Object clockFont = font(clock);
                                                        boolean manualStretch =
                                                                settings != null
                                                                        && settings.getBoolean(
                                                                                "manual");
                                                        Typeface replacement =
                                                                typeface(
                                                                        clock.getContext(),
                                                                        clockFont,
                                                                        FontAxisSettings.values(
                                                                                settings),
                                                                        imported,
                                                                        nativeHegt,
                                                                        isVerticalClock(clock),
                                                                        manualStretch);
                                                        if (replacement != null) {
                                                            // This call bypasses the fixed-font
                                                            // hook. Keep the live targets here too.
                                                            remember(clock, views);
                                                            applyDirect(
                                                                    apply,
                                                                    clock,
                                                                    replacement,
                                                                    views);
                                                            ensureTypeface(
                                                                    views,
                                                                    replacement,
                                                                    hasVariableAxes(
                                                                            clock.getContext(),
                                                                            clockFont,
                                                                            imported));
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

    static boolean hasHeightAxis(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return false;
        Source source = imported(context, id);
        return source != null && source.axes != null && source.axes.heightAxis() != null;
    }

    static synchronized void configure(DressroomModule module, View clock, Bundle settings) {
        configure(clock, settings);
        if (FontAxisSettings.stretchAllowed(clock.getContext(), settings)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            FontBinding binding = FontBinding.of(clock);
            Method render = binding.renderer(clock.getClass());
            if (renderers.contains(render)) return;
            hooks.add(
                    module.hook(render)
                                    .setId("dressroom-advanced.imported-font-layout")
                                    .intercept(
                                            chain -> {
                                                if (!(chain.getThisObject() instanceof View owner))
                                                    return chain.proceed();
                                                Bundle ownerSettings = configured.get(owner);
                                                if (FontAxisSettings.stretchAllowed(
                                                        owner.getContext(), ownerSettings))
                                                    return chain.proceed();
                                                Object original = chain.getArg(0);
                                                if (original == null) return chain.proceed();
                                                // Keep the saved font identity on the clock; only
                                                // the drawing request uses the static path.
                                                Object plain = binding.plain(original);
                                                return chain.proceed(new Object[] {plain});
                                            })
                            ::unhook);
            module.deoptimize(render);
            hooks.commit();
            renderers.add(render);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
        return fileId(profile)
                + "|"
                + (profile != null && profile.getBoolean("manual"))
                + "|"
                + FontAxisSettings.key(FontAxisSettings.values(profile));
    }

    static Axes importedAxes(Context context, String id) {
        Source source = imported(context, id);
        return source == null ? null : source.axes;
    }

    /**
     * Loads an imported custom font (via {@link FontFiles}) with optional variable font axis
     * coordinates, caching the resulting {@link Typeface}. Safe to call from SystemUI or the editor.
     */
    public static Typeface importedTypeface(Context context, String id, Bundle values) {
        if (context == null || id == null || id.isEmpty()) return null;
        try {
            Source source = imported(context, id);
            if (source == null || source.uri == null) return null;
            List<FontVariationAxis> variations = new ArrayList<>();
            StringBuilder keyParts = new StringBuilder(source.key);
            for (FontAxes.Axis axis :
                    source.axes == null ? List.<FontAxes.Axis>of() : source.axes.values) {
                float value =
                        values != null && values.containsKey(axis.tag())
                                ? values.getFloat(axis.tag())
                                : axis.defaultValue();
                if (!Float.isFinite(value)) value = axis.defaultValue();
                value = axis.clamp(value);
                variations.add(new FontVariationAxis(axis.tag(), value));
                keyParts.append('|')
                        .append(axis.tag())
                        .append('=')
                        .append(Float.floatToIntBits(value));
            }
            String key = keyParts.toString();
            Typeface cached = typefaces.get(key);
            if (cached != null) return cached;
            Typeface result = buildTypeface(context, source, variations);
            if (result != null) typefaces.put(key, result);
            return result;
        } catch (Throwable error) {
            return null;
        }
    }

    /**
     * Resolves the lyrics {@link Typeface}: imported custom font (with variable axes) when
     * configured, or the system sans-serif font at the requested weight otherwise.
     */
    public static Typeface lyricsTypeface(
            Context context, String fontFile, Bundle fontAxes, int defaultWeight) {
        if (fontFile != null && !fontFile.isEmpty()) {
            Typeface custom = importedTypeface(context, fontFile, fontAxes);
            if (custom != null) return custom;
        }
        int clampedWeight = Math.max(100, Math.min(900, defaultWeight));
        String key = "lyrics-default|wght=" + clampedWeight;
        Typeface cached = typefaces.get(key);
        if (cached != null) return cached;
        Typeface result = Typeface.create(Typeface.DEFAULT, clampedWeight, false);
        if (result != null) typefaces.put(key, result);
        return result != null ? result : Typeface.DEFAULT_BOLD;
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
            Source source =
                    new Source(key, null, null, null, parse(bytes), uri, createBaseFont(bytes));
            sources.put(key, source);
            return source;
        } catch (RuntimeException | java.io.IOException error) {
            return null;
        }
    }

    static Typeface typeface(Context context, Object font, Bundle values, String imported) {
        return typeface(context, font, values, imported, Float.NaN, false, false);
    }

    static Typeface compactTypeface(View clock, Typeface fallback) {
        if (clock == null) return fallback;
        try {
            Bundle settings = configured.get(clock);
            String imported = fileId(settings);
            if (imported.isEmpty()) return fallback;
            Source source = imported(clock.getContext(), imported);
            if (source == null || source.axes == null || source.axes.heightAxis() == null) {
                return fallback;
            }
            Bundle values = FontAxisSettings.values(settings);
            boolean manualStretch = settings != null && settings.getBoolean("manual");
            Object clockFont = font(clock);
            if (!manualStretch
                    && (hasCustomAxisValue(source.axes.heightAxis(), values)
                            || !hasActiveDynamicStretch(clockFont, Float.NaN))) {
                return fallback;
            }
            Typeface compact =
                    typeface(
                            clock.getContext(),
                            clockFont,
                            values,
                            imported,
                            FontAxes.SAMSUNG_MIN_HEGT,
                            isVerticalClock(clock),
                            true);
            return compact != null ? compact : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static Typeface typeface(
            Context context,
            Object font,
            Bundle values,
            String imported,
            float nativeHegt,
            boolean vertical,
            boolean manualStretch) {
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
            FontAxes.Axis heightAxis =
                    !imported.isEmpty() && source.axes != null ? source.axes.heightAxis() : null;
            boolean driveHeight =
                    heightAxis != null
                            && Float.isFinite(nativeHegt)
                            && (manualStretch
                                    || (!hasCustomAxisValue(heightAxis, values)
                                            && hasActiveDynamicStretch(font, nativeHegt)));
            float mappedHeight =
                    driveHeight ? FontAxes.mapStretchAxis(heightAxis, nativeHegt, vertical) : Float.NaN;
            List<FontVariationAxis> variations = new ArrayList<>();
            StringBuilder keyParts = new StringBuilder(source.key);
            for (FontAxes.Axis axis :
                    source.axes == null ? List.<FontAxes.Axis>of() : source.axes.values) {
                float value;
                if (driveHeight && axis == heightAxis) {
                    value = mappedHeight;
                } else {
                    value =
                            values != null && values.containsKey(axis.tag())
                                    ? values.getFloat(axis.tag())
                                    : axis.tag().equals("wght") && weight != null
                                            ? weight
                                            : axis.defaultValue();
                }
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
            Typeface result = buildTypeface(context, source, variations);
            if (result != null) typefaces.put(key, result);
            return result;
        } catch (Throwable error) {
            return null;
        }
    }

    private static Typeface buildTypeface(
            Context context, Source source, List<FontVariationAxis> variations) throws Exception {
        if (source.baseFont != null) {
            try {
                Font.Builder builder = new Font.Builder(source.baseFont);
                if (!variations.isEmpty()) {
                    builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                }
                Font built = builder.build();
                return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(built).build())
                        .setSystemFallback("sans-serif")
                        .build();
            } catch (Throwable ignored) {
            }
        }
        if (source.uri != null) {
            try (android.os.ParcelFileDescriptor fd =
                    context.getContentResolver().openFileDescriptor(source.uri, "r")) {
                if (fd == null) return null;
                Typeface.Builder builder = new Typeface.Builder(fd.getFileDescriptor());
                if (!variations.isEmpty())
                    builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
                return builder.build();
            }
        }
        if (source.asset != null) {
            Typeface.Builder builder = new Typeface.Builder(source.assets, source.asset);
            if (!variations.isEmpty())
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
            return builder.build();
        }
        if (source.file != null) {
            Typeface.Builder builder = new Typeface.Builder(source.file);
            if (!variations.isEmpty())
                builder.setFontVariationSettings(variations.toArray(new FontVariationAxis[0]));
            return builder.build();
        }
        return null;
    }

    private static Font createBaseFont(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        try {
            ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length);
            direct.put(bytes);
            direct.flip();
            return new Font.Builder(direct).build();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void preview(View clock, Bundle settings) {
        if (clock == null) return;
        Object font = font(clock);
        Bundle values = FontAxisSettings.values(settings);
        String imported = fileId(settings);
        configure(clock, settings);
        try {
            List<TextView> current = new ArrayList<>();
            List<java.lang.ref.WeakReference<TextView>> remembered = targets.get(clock);
            if (remembered != null)
                for (java.lang.ref.WeakReference<TextView> reference : remembered) {
                    TextView view = reference.get();
                    if (view != null) current.add(view);
                }
            if (current.isEmpty()) {
                if (typeface(clock.getContext(), font, values, imported) == null) return;
                ClockBinding.of(clock).render(clock, font);
            } else {
                boolean variable = hasVariableAxes(clock.getContext(), font, imported);
                boolean manualStretch = settings != null && settings.getBoolean("manual");
                Source importedSource = imported.isEmpty() ? null : imported(clock.getContext(), imported);
                FontAxes.Axis heightAxis =
                        importedSource != null && importedSource.axes != null
                                ? importedSource.axes.heightAxis()
                                : null;
                boolean perDigitStretch =
                        heightAxis != null
                                && (manualStretch
                                        || (!hasCustomAxisValue(heightAxis, values)
                                                && hasActiveDynamicStretch(font, Float.NaN)));
                FontBinding fontBinding = FontBinding.of(clock);
                if (perDigitStretch) {
                    boolean vertical = isVerticalClock(clock);
                    boolean anyApplied = false;
                    for (TextView digit : current) {
                        Float hegt = digitStretchHegt.get(digit);
                        Typeface tf =
                                typeface(
                                        clock.getContext(),
                                        font,
                                        values,
                                        imported,
                                        hegt == null ? Float.NaN : hegt,
                                        vertical,
                                        manualStretch);
                        if (tf == null) continue;
                        TextView[] one = new TextView[] {digit};
                        applyDirect(fontBinding.fixed, clock, tf, one);
                        ensureTypeface(one, tf, variable);
                        anyApplied = true;
                    }
                    if (!anyApplied) return;
                } else {
                    Typeface replacement = typeface(clock.getContext(), font, values, imported);
                    if (replacement == null) return;
                    TextView[] views = current.toArray(new TextView[0]);
                    applyDirect(fontBinding.fixed, clock, replacement, views);
                    ensureTypeface(views, replacement, variable);
                }
            }
            applied.put(clock, profileKey(settings));
            clock.requestLayout();
            clock.invalidate();
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Variable font preview unavailable", error);
        }
    }

    private static void applyDirect(
            Method fixed, View clock, Typeface typeface, TextView[] views)
            throws ReflectiveOperationException {
        applyingDirect.set(true);
        try {
            fixed.invoke(clock, typeface, views);
        } finally {
            applyingDirect.remove();
        }
    }

    private static boolean hasVariableAxes(Context context, Object font, String imported) {
        if (!imported.isEmpty()) {
            Source source = imported(context, imported);
            return source != null && source.axes != null && source.axes.hasAny();
        }
        return true;
    }

    private static boolean isVerticalClock(View clock) {
        if (clock == null) return false;
        if (clock.getClass().getName().contains("Vertical")) return true;
        try {
            return ((Number) Reflect.call(clock, "getClockType")).intValue() == 1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasCustomAxisValue(FontAxes.Axis axis, Bundle values) {
        if (axis == null || values == null || !values.containsKey(axis.tag())) return false;
        float value = values.getFloat(axis.tag());
        return Float.isFinite(value) && Math.abs(value - axis.defaultValue()) > 0.01f;
    }

    private static boolean hasActiveDynamicStretch(Object fontData, float nativeHegt) {
        if (Float.isFinite(nativeHegt) && nativeHegt > FontAxes.SAMSUNG_MIN_HEGT + 0.5f) {
            return true;
        }
        if (fontData == null) return false;
        try {
            Object raw = Reflect.call(fontData, "getFontHeights");
            if (raw instanceof List<?> heights) {
                for (Object item : heights) {
                    if (item instanceof Number n
                            && Float.isFinite(n.floatValue())
                            && n.floatValue() > 1.002f) {
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    private static void ensureTypeface(TextView[] views, Typeface typeface, boolean variableFont) {
        if (views == null) return;
        for (TextView view : views) {
            if (view == null) continue;
            boolean isClockGlyph =
                    view.getClass()
                            .getName()
                            .equals("com.samsung.android.uniform.widget.ClockNonePaddingTextView");
            if (isClockGlyph && variableFont) {
                // Prevents NonePaddingTextView from allocating a full-size ARGB_8888 Bitmap and
                // running a per-pixel scanline trim loop on every axis tick, and keeps digit
                // baseline alignment locked to "0123456789".
                Method setter = variableClockFontSetter.get(view.getClass());
                if (setter != null) {
                    try {
                        setter.invoke(view, true);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }
            }
            boolean changed = view.getTypeface() != typeface;
            if (changed) view.setTypeface(typeface);
            if (isClockGlyph && (changed || measuredTypefaces.get(view) != typeface)) {
                // Samsung's setTypeface leaves the trimmed digit bounds cached.
                // Re-setting the same text runs its native glyph measurement.
                view.setText(view.getText());
                measuredTypefaces.put(view, typeface);
            }
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
            List<java.lang.ref.WeakReference<TextView>> remembered = targets.remove(clock);
            if (remembered != null) {
                for (java.lang.ref.WeakReference<TextView> ref : remembered) {
                    TextView view = ref.get();
                    if (view != null) digitStretchHegt.remove(view);
                }
            }
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
            return data == null
                    ? null
                    : new Source(key, file, null, null, parse(data), createBaseFont(data));
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
                Source source =
                        new Source(key, null, assets, asset, axes, createBaseFont(data));
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
