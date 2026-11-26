package dev.denver.dressroomadvanced;

import android.graphics.Typeface;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

final class GlyphFontCache {
    private static final String GLYPH_TEXT_VIEW =
            "com.samsung.android.uniform.widget.ClockNonePaddingTextView";

    record Applied(int font, String axes, Object typeface) {
        boolean matches(int nextFont, String nextAxes, Object current) {
            return font == nextFont && Objects.equals(axes, nextAxes) && typeface == current;
        }
    }

    record Resolved(
            Method fix,
            Method update,
            Field fontType,
            Field fixed,
            Field variation,
            Method applyFont,
            Method applyTypeface,
            Method configChanged) {}

    private static final Map<TextView, Applied> applied =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<TextView> applying = new ThreadLocal<>();

    static Resolved resolve(Class<?> base) throws ReflectiveOperationException, java.io.IOException {
        ClassLoader loader = base.getClassLoader();
        Class<?> glyph = Class.forName(GLYPH_TEXT_VIEW, false, loader);
        String glyphDesc = DexSignatures.descriptor(glyph);
        String baseDesc = DexSignatures.descriptor(base);

        List<DexSignatures.Body> glyphBodies =
                DexSignatures.findInClass(
                        glyph, Set.of("fixFontType: [", "updateTypeface() ["));
        DexSignatures.Body fixBody =
                uniqueMethod(
                        glyphBodies.stream()
                                .filter(body -> body.anchors().containsKey("fixFontType: ["))
                                .toList(),
                        glyphDesc,
                        "V",
                        "glyph fixFontType",
                        "I",
                        "Ljava/lang/String;");
        DexSignatures.Body updateBody =
                uniqueMethod(
                        glyphBodies.stream()
                                .filter(body -> body.anchors().containsKey("updateTypeface() ["))
                                .toList(),
                        glyphDesc,
                        "V",
                        "glyph updateTypeface");
        boolean callsUpdate =
                fixBody.calls().stream()
                        .anyMatch(
                                call ->
                                        !call.isStatic()
                                                && call.owner().equals(glyphDesc)
                                                && call.name().equals(updateBody.name())
                                                && call.parameters().isEmpty()
                                                && call.result().equals("V"));
        if (!callsUpdate)
            throw new NoSuchMethodException("Glyph font fix does not invoke updateTypeface");

        Method fix = fixBody.method(loader);
        Method update = updateBody.method(loader);
        Field fontType = fixBody.uniqueRead(glyphDesc, "I", null).field(loader);
        Field fixed =
                updateBody.uniqueRead(glyphDesc, "Landroid/graphics/Typeface;", null).field(loader);
        Field variation =
                updateBody.uniqueRead(glyphDesc, "Ljava/lang/String;", null).field(loader);
        Method applyFont =
                uniqueMethod(
                                DexSignatures.callers(fix),
                                baseDesc,
                                "V",
                                "clock applyFont",
                                "I",
                                "Ljava/lang/String;",
                                "[Landroid/widget/TextView;")
                        .method(loader);
        Method applyTypeface =
                uniqueMethod(
                                DexSignatures.findInClass(base, Set.of("fixTypeface: [")),
                                baseDesc,
                                "V",
                                "clock applyTypeface",
                                "Landroid/graphics/Typeface;",
                                "[Landroid/widget/TextView;")
                        .method(loader);
        Method configChanged =
                glyph.getDeclaredMethod(
                        "onConfigurationChanged", android.content.res.Configuration.class);
        return new Resolved(
                fix, update, fontType, fixed, variation, applyFont, applyTypeface, configChanged);
    }

    private static Field tryResolveBaseTypeface(Class<?> glyph) {
        Class<?> superClass = glyph.getSuperclass();
        if (superClass == null || superClass == TextView.class) return null;
        Field match = null;
        for (Field field : superClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != Typeface.class)
                continue;
            if (match != null) return null;
            field.setAccessible(true);
            match = field;
        }
        return match;
    }

    private static Object effectiveTypeface(TextView view, Field baseTypeface) {
        if (baseTypeface != null) {
            try {
                Object base = baseTypeface.get(view);
                if (base != null) return base;
            } catch (IllegalAccessException ignored) {
            }
        }
        return view.getTypeface();
    }

    private static DexSignatures.Body uniqueMethod(
            List<DexSignatures.Body> bodies,
            String owner,
            String result,
            String label,
            String... parameters)
            throws NoSuchMethodException {
        List<String> expected = List.of(parameters);
        DexSignatures.Body match = null;
        for (DexSignatures.Body body : bodies) {
            if ((body.access() & Modifier.STATIC) != 0
                    || !body.owner().equals(owner)
                    || !body.parameters().equals(expected)
                    || !body.result().equals(result)) continue;
            if (match != null)
                throw new NoSuchMethodException("Ambiguous " + label + " in " + owner);
            match = body;
        }
        if (match == null) throw new NoSuchMethodException("Missing " + label + " in " + owner);
        return match;
    }

    static synchronized void install(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Resolved resolved = resolve(base);
            Class<?> glyph = Class.forName(GLYPH_TEXT_VIEW, false, loader);
            Field baseTypeface = tryResolveBaseTypeface(glyph);
            var invalidate =
                    module.hook(resolved.update())
                            .setId("dressroom-advanced.font-cache-invalidate")
                            .intercept(
                                    chain -> {
                                        TextView view = (TextView) chain.getThisObject();
                                        if (applying.get() != view) applied.remove(view);
                                        return chain.proceed();
                                    });
            hooks.add(invalidate::unhook);
            var reuse =
                    module.hook(resolved.fix())
                            .setId("dressroom-advanced.font-identical-request")
                            .intercept(
                                    chain -> {
                                        TextView view = (TextView) chain.getThisObject();
                                        LockscreenFrameBoost.pulse(view);
                                        int font = ((Number) chain.getArg(0)).intValue();
                                        String axes = (String) chain.getArg(1);
                                        Object currentTf = effectiveTypeface(view, baseTypeface);
                                        Applied old = applied.get(view);
                                        if (old != null
                                                && old.matches(font, axes, currentTf)
                                                && resolved.fixed().get(view) == null
                                                && resolved.fontType().getInt(view) == font
                                                && Objects.equals(
                                                        axes, resolved.variation().get(view)))
                                            return null;
                                        TextView previous = applying.get();
                                        applying.set(view);
                                        try {
                                            Object result = chain.proceed();
                                            Object updatedTf =
                                                    effectiveTypeface(view, baseTypeface);
                                            if (updatedTf != null)
                                                applied.put(
                                                        view, new Applied(font, axes, updatedTf));
                                            else applied.remove(view);
                                            return result;
                                        } finally {
                                            if (previous == null) applying.remove();
                                            else applying.set(previous);
                                        }
                                    });
            hooks.add(reuse::unhook);
            module.deoptimize(resolved.fix());
            module.deoptimize(resolved.update());
            module.deoptimize(resolved.applyFont());
            module.deoptimize(resolved.applyTypeface());
            module.deoptimize(resolved.configChanged());
            installed.add(loader);
            hooks.commit();
            android.util.Log.i(
                    DressroomModule.TAG, "Exact native glyph font request reuse installed");
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError error) {
            module.report("Optional glyph font reuse unavailable", error);
        }
    }
}
