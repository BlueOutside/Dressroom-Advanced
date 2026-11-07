package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

final class GlyphDrawReuse {
    private static final Map<TextView, GlyphOutlineMemo> memos = new WeakHashMap<>();
    private static final ThreadLocal<Scope> active = new ThreadLocal<>();
    private static final java.util.Set<ClassLoader> installed =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean pathInstalled;

    private static final class Scope {
        Paint paint;
        GlyphOutlineMemo memo;
    }

    private static final ThreadLocal<Scope> scratch = ThreadLocal.withInitial(Scope::new);

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed.contains(loader)) return;
        try {
            if (!pathInstalled) {
                java.lang.reflect.Method path =
                        Paint.class.getDeclaredMethod(
                                "getTextPath",
                                String.class,
                                int.class,
                                int.class,
                                float.class,
                                float.class,
                                Path.class);
                module.hook(path)
                        .setId("dressroom-advanced.glyph-draw-path-reuse")
                        .intercept(
                                chain -> {
                                    Scope scope = active.get();
                                    if (scope == null || scope.paint != chain.getThisObject())
                                        return chain.proceed();
                                    String text = (String) chain.getArg(0);
                                    int start = ((Number) chain.getArg(1)).intValue(),
                                            end = ((Number) chain.getArg(2)).intValue();
                                    float x = ((Number) chain.getArg(3)).floatValue(),
                                            y = ((Number) chain.getArg(4)).floatValue();
                                    Path target = (Path) chain.getArg(5);
                                    if (scope.memo.reuse(
                                            scope.paint, text, start, end, x, y, target))
                                        return null;
                                    Object result = chain.proceed();
                                    scope.memo.capture(scope.paint, text, start, end, x, y, target);
                                    return result;
                                });
                module.deoptimize(path);
                pathInstalled = true;
            }
            Class<?> glyph =
                    Class.forName(
                            "com.samsung.android.uniform.widget.ClockNonePaddingTextView",
                            false,
                            loader);
            java.lang.reflect.Method draw = glyph.getDeclaredMethod("onDraw", Canvas.class);
            module.hook(draw)
                    .setId("dressroom-advanced.glyph-draw-reuse-scope")
                    .intercept(
                            chain -> {
                                TextView view = (TextView) chain.getThisObject();
                                if (view.getPaint().getShader() == null
                                        || MediaClockContrast.overridesDraw(view))
                                    return chain.proceed();
                                Scope previous = active.get(),
                                        scope = previous == null ? scratch.get() : new Scope();
                                scope.paint = view.getPaint();
                                scope.memo =
                                        memos.computeIfAbsent(
                                                view, ignored -> new GlyphOutlineMemo());
                                scope.memo.reset();
                                active.set(scope);
                                try {
                                    return chain.proceed();
                                } finally {
                                    scope.memo.reset();
                                    scope.paint = null;
                                    scope.memo = null;
                                    if (previous == null) active.remove();
                                    else active.set(previous);
                                }
                            });
            module.deoptimize(draw);
            module.deoptimize(glyph.getSuperclass().getDeclaredMethod("onDraw", Canvas.class));
            installed.add(loader);
            android.util.Log.i(
                    DressroomModule.TAG, "Native gradient glyph outline reuse installed");
        } catch (ReflectiveOperationException | LinkageError error) {
            module.report("Optional glyph outline reuse unavailable", error);
        }
    }
}
