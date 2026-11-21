package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class DepthBlurHooks {
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Scope> current = new ThreadLocal<>();
    private static final Set<View> verified = Collections.newSetFromMap(new WeakHashMap<>());

    private static final class Scope {
        final Object strategy;
        final View clock;
        final android.widget.ImageView icon;
        final Object weather;
        PointF origin;

        Scope(Object strategy, View clock, android.widget.ImageView icon, Object weather) {
            this.strategy = strategy;
            this.clock = clock;
            this.icon = icon;
            this.weather = weather;
        }
    }

    private DepthBlurHooks() {}

    static synchronized boolean ready(ClassLoader loader) {
        return installed.contains(loader);
    }

    static void clockBlurSubmitted(View host, Object info) {
        if (info == null || !"lock_clock_blur".equals(host.getTag())) return;
        Scope scope = current.get();
        if (scope != null) ClockBlurMotion.submitted(scope.clock);
    }

    static synchronized void install(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        WidgetDepthBlur.install(module, loader);
        if (installed.contains(loader)) return;
        java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> handles =
                new java.util.ArrayList<>();
        try {
            ClockBlurBinding binding = ClockBlurBinding.resolve(base);
            Method render = binding.render, clip = binding.clip;
            Method glyphOutline = glyphOutline(render, loader);
            handles.add(module.hook(binding.host)
                    .setId("dressroom-advanced.clock-blur-handoff-host")
                    .intercept(chain -> {
                        if (!ClockBlurMotion.hasHandoff()) return chain.proceed();
                        Object strategy = chain.getThisObject();
                        View clock;
                        try {
                            clock = binding.clock(strategy);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            return chain.proceed();
                        }
                        return ClockBlurMotion.outgoing(clock) ? null : chain.proceed();
                    }));
            handles.add(
                    module.hook(render)
                            .setId("dressroom-advanced.depth-blur-owner")
                            .intercept(
                                    chain -> {
                                        Scope previous = current.get();
                                        try {
                                            try {
                                                Object owner = chain.getThisObject();
                                                View clock = binding.clock(owner);
                                                if (clock != null)
                                                    current.set(new Scope(owner, clock,
                                                            (android.widget.ImageView) chain.getArg(0), chain.getArg(1)));
                                                else current.remove();
                                            } catch (Exception error) {
                                                current.remove();
                                                module.report(
                                                        "Blur depth owner unavailable", error);
                                            }
                                            return chain.proceed();
                                        } finally {
                                            if (previous == null) current.remove();
                                            else current.set(previous);
                                        }
                                    }));
            handles.add(
                    module.hook(binding.origin)
                            .setId("dressroom-advanced.depth-blur-host-origin")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        Scope scope = current.get();
                                        // The renderer chooses the editor or lockscreen host
                                        // after reading its options. Use that exact host.
                                        if (scope != null && scope.strategy == chain.getThisObject()
                                                && chain.getArg(0) instanceof ViewGroup host
                                                && result instanceof PointF point
                                                && Float.isFinite(point.x) && Float.isFinite(point.y))
                                            scope.origin = UnlockBlur.hostOrigin(scope.clock, host, point);
                                        return result;
                                    }));
            handles.add(
                    module.hook(clip)
                            .setId("dressroom-advanced.depth-native-blur-path")
                            .intercept(
                                    chain -> {
                                        Scope scope = current.get();
                                        if (scope == null || scope.origin == null) return chain.proceed();
                                        Path supplied = (Path) chain.getArg(1);
                                        Path original = UnlockBlur.liveClockPath(
                                                scope.clock, scope.origin, scope.icon, scope.weather,
                                                supplied, glyphOutline);
                                        Path visible = original;
                                        try {
                                            MediaFullscreenExtras.clockPath(
                                                    scope.clock,
                                                    scope.origin.x,
                                                    scope.origin.y,
                                                    ClockInlineSeconds.withSecondsPath(
                                                            scope.clock, scope.origin, original));
                                            Path subject =
                                                    DepthEffects.nativeSubjectPath(
                                                            scope.clock,
                                                            scope.origin.x,
                                                            scope.origin.y);
                                            if (original != null && subject != null) {
                                                visible = DepthGeometry.subtract(original, subject);
                                                if (verified.add(scope.clock))
                                                    android.util.Log.i(
                                                            DressroomModule.TAG,
                                                            "Depth applied to native blur path: "
                                                                    + scope.clock
                                                                            .getClass()
                                                                            .getSimpleName());
                                            }
                                        } catch (RuntimeException error) {
                                            visible = original;
                                            module.report(
                                                    "Native blur depth clip unavailable", error);
                                        }
                                        // Call Samsung once, including when our optional clip
                                        // fails.
                                        return visible == supplied
                                                ? chain.proceed()
                                                : chain.proceed(
                                                        new Object[] {chain.getArg(0), visible});
                                    }));
            module.deoptimize(render);
            module.deoptimize(binding.origin);
            module.deoptimize(clip);
            module.deoptimize(binding.host);
            Method clear = binding.clear;
            handles.add(
                    module.hook(clear)
                            .setId("dressroom-advanced.media-clock-blur-clear")
                            .intercept(
                                    chain -> {
                                        Object strategy = chain.getThisObject();
                                        View clock;
                                        try {
                                            clock = binding.clock(strategy);
                                        } catch (ReflectiveOperationException | RuntimeException error) {
                                            return chain.proceed();
                                        }
                                        Object result = chain.proceed();
                                        if (clock != null) MediaFullscreenExtras.clockPath(clock, 0, 0, null);
                                        return result;
                                    }));
            module.deoptimize(clear);
            installed.add(loader);
            android.util.Log.i(DressroomModule.TAG, "Native blur subject clipping installed");
        } catch (Exception | LinkageError error) {
            for (io.github.libxposed.api.XposedInterface.HookHandle handle : handles)
                handle.unhook();
            module.report("Native blur depth unavailable", error);
            return;
        }
        if (!module.inEditorProcess()) {
            try {
                ClockBlurMotion.installHandoff(module, ClockBlurRetryBinding.absClockView(base));
            } catch (Exception | LinkageError error) {
                module.report("Optional clock page blur handoff unavailable", error);
            }
        }
    }

    private static Method glyphOutline(Method render, ClassLoader loader) {
        try {
            Set<Method> outlines = new java.util.HashSet<>();
            for (var call : DexSignatures.body(render).calls()) {
                if (call.isStatic() || !call.parameters().isEmpty()
                        || !call.result().equals("Landroid/graphics/Path;")) continue;
                Method method = call.method(loader);
                if (method.getDeclaringClass().isInterface()) outlines.add(method);
            }
            return outlines.size() == 1 ? outlines.iterator().next() : null;
        } catch (ReflectiveOperationException | java.io.IOException ignored) {
            return null;
        }
    }
}
