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

    private record Scope(View clock, PointF origin) {}

    private DepthBlurHooks() {}

    static synchronized boolean ready(ClassLoader loader) {
        return installed.contains(loader);
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
                                                Object host = binding.host.invoke(owner);
                                                PointF point = host instanceof ViewGroup
                                                        ? (PointF) binding.origin.invoke(owner, host) : null;
                                                if (clock != null && point != null
                                                        && Float.isFinite(point.x) && Float.isFinite(point.y))
                                                    current.set(new Scope(clock, point));
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
                    module.hook(clip)
                            .setId("dressroom-advanced.depth-native-blur-path")
                            .intercept(
                                    chain -> {
                                        Scope scope = current.get();
                                        if (scope == null) return chain.proceed();
                                        Path original = (Path) chain.getArg(1), visible = original;
                                        try {
                                            MediaFullscreenExtras.clockPath(scope.clock,
                                                    scope.origin.x, scope.origin.y, original);
                                            Path subject = DepthEffects.nativeSubjectPath(scope.clock,
                                                    scope.origin.x, scope.origin.y);
                                            if (original != null && subject != null) {
                                                visible = DepthGeometry.subtract(original, subject);
                                                if (verified.add(scope.clock))
                                                    android.util.Log.i(DressroomModule.TAG,
                                                            "Depth applied to native blur path: "
                                                                    + scope.clock.getClass().getSimpleName());
                                            }
                                        } catch (RuntimeException error) {
                                            visible = original;
                                            module.report("Native blur depth clip unavailable", error);
                                        }
                                        // Call Samsung once, including when our optional clip fails.
                                        return visible == original ? chain.proceed()
                                                : chain.proceed(new Object[] {chain.getArg(0), visible});
                                    }));
            module.deoptimize(render);
            module.deoptimize(clip);
            Method clear = binding.clear;
            handles.add(
                    module.hook(clear)
                            .setId("dressroom-advanced.media-clock-blur-clear")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        try {
                                            View clock = binding.clock(chain.getThisObject());
                                            if (clock != null)
                                                MediaFullscreenExtras.clockPath(clock, 0, 0, null);
                                        } catch (Exception ignored) {
                                        }
                                        return result;
                                    }));
            module.deoptimize(clear);
            installed.add(loader);
            android.util.Log.i(DressroomModule.TAG, "Native blur subject clipping installed");
        } catch (Exception | LinkageError error) {
            for (io.github.libxposed.api.XposedInterface.HookHandle handle : handles)
                handle.unhook();
            module.report("Native blur depth unavailable", error);
        }
    }
}
