package dev.denver.dressroomadvanced;

import android.graphics.Typeface;
import android.widget.TextView;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

final class GlyphFontCache {
    record Applied(int font, String axes, Object typeface) {
        boolean matches(int nextFont, String nextAxes, Object current) {
            return font == nextFont && Objects.equals(axes, nextAxes) && typeface == current;
        }
    }

    private static final Map<TextView, Applied> applied = new WeakHashMap<>();
    private static final java.util.Set<ClassLoader> installed =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<TextView> applying = new ThreadLocal<>();

    static synchronized void install(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        java.util.ArrayList<io.github.libxposed.api.XposedInterface.HookHandle> handles =
                new java.util.ArrayList<>();
        try {
            Class<?> glyph =
                    Class.forName(
                            "com.samsung.android.uniform.widget.ClockNonePaddingTextView",
                            false,
                            loader);
            java.lang.reflect.Field fixed = glyph.getDeclaredField("c0"),
                    fontType = glyph.getDeclaredField("b0"),
                    variation = glyph.getDeclaredField("d0");
            fixed.setAccessible(true);
            fontType.setAccessible(true);
            variation.setAccessible(true);
            java.lang.reflect.Method fix = glyph.getDeclaredMethod("h", int.class, String.class),
                    update = glyph.getDeclaredMethod("i");
            handles.add(
                    module.hook(update)
                            .setId("dressroom-advanced.font-cache-invalidate")
                            .intercept(
                                    chain -> {
                                        TextView view = (TextView) chain.getThisObject();
                                        if (applying.get() != view) applied.remove(view);
                                        return chain.proceed();
                                    }));
            handles.add(
                    module.hook(fix)
                            .setId("dressroom-advanced.font-identical-request")
                            .intercept(
                                    chain -> {
                                        TextView view = (TextView) chain.getThisObject();
                                        int font = ((Number) chain.getArg(0)).intValue();
                                        String axes = (String) chain.getArg(1);
                                        Applied old = applied.get(view);
                                        if (old != null
                                                && old.matches(font, axes, view.getTypeface())
                                                && fixed.get(view) == null
                                                && fontType.getInt(view) == font
                                                && Objects.equals(axes, variation.get(view)))
                                            return null;
                                        TextView previous = applying.get();
                                        applying.set(view);
                                        try {
                                            Object result = chain.proceed();
                                            Typeface typeface = view.getTypeface();
                                            if (typeface != null)
                                                applied.put(
                                                        view, new Applied(font, axes, typeface));
                                            else applied.remove(view);
                                            return result;
                                        } finally {
                                            if (previous == null) applying.remove();
                                            else applying.set(previous);
                                        }
                                    }));
            module.deoptimize(fix);
            module.deoptimize(update);
            module.deoptimize(
                    base.getDeclaredMethod("B", int.class, String.class, TextView[].class));
            module.deoptimize(base.getDeclaredMethod("C", Typeface.class, TextView[].class));
            module.deoptimize(
                    glyph.getDeclaredMethod(
                            "onConfigurationChanged", android.content.res.Configuration.class));
            installed.add(loader);
            android.util.Log.i(
                    DressroomModule.TAG, "Exact native glyph font request reuse installed");
        } catch (ReflectiveOperationException | LinkageError error) {
            for (var handle : handles) handle.unhook();
            module.report("Optional glyph font reuse unavailable", error);
        }
    }
}
