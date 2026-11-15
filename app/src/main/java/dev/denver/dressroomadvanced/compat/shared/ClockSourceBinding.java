package dev.denver.dressroomadvanced;

import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolve every source/measurement dependency before installing any hooks. */
final class ClockSourceBinding {
    private static final String NORMALIZER_ANCHOR =
            "balanceVerticalFontHeights: already balanced, left=";
    final Method data, heights, wallpaper;
    final List<Method> callers;

    ClockSourceBinding(ClassLoader loader, ClockBinding clock) throws ReflectiveOperationException {
        Class<?> provider =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                        false,
                        loader);
        Class<?> font =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);
        NativeContract.field(provider, "context", Context.class);
        NativeContract.method(provider, "getConsideredThemeClockType", int.class);
        data = NativeContract.method(provider, "getClockFontData", font);
        heights = NativeContract.method(provider, "getClockFontHeights", List.class);
        wallpaper =
                NativeContract.method(
                        provider, "updateWallpaperColorsItem", void.class, Object.class);
        Method stretch = NativeContract.method(provider, "hasStretchFontHeights", boolean.class);
        var methods = new java.util.ArrayList<Method>();
        methods.add(stretch);
        try {
            Class<?> model =
                    Class.forName(
                            "com.samsung.android.app.facewidget.FaceWidgetViewModel", false, loader);
            Class<?> view =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
            addCallers(methods, model, font, view, false);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Class<?> page =
                    Class.forName(
                            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage",
                            false,
                            loader);
            Class<?> view =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
            addCallers(methods, page, font, view, true);
        } catch (ReflectiveOperationException ignored) {
        }
        // Deoptimize provider callers (e.g. measureClockSize) so ART does not inline native font
        // getters and measure native heights while the view uses ours.
        try {
            String owner = DexSignatures.descriptor(provider);
            addProviderCallers(methods, provider, owner, loader, data);
            addProviderCallers(methods, provider, owner, loader, heights);
        } catch (java.io.IOException | ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w(
                    DressroomModule.TAG, "Provider measurement callers unavailable", error);
        }
        callers = List.copyOf(methods);
    }

    private static void addProviderCallers(
            List<Method> methods,
            Class<?> provider,
            String owner,
            ClassLoader loader,
            Method target)
            throws java.io.IOException, ReflectiveOperationException {
        for (DexSignatures.Body caller : DexSignatures.callers(target)) {
            if (!caller.owner().equals(owner)) continue;
            Method method = caller.method(loader);
            if (method.getDeclaringClass() == provider
                    && !Modifier.isAbstract(method.getModifiers())
                    && !method.isBridge()
                    && !method.isSynthetic()
                    && !methods.contains(method)) {
                methods.add(method);
            }
        }
    }

    static Method normalizer(ClassLoader loader, Class<?> font, boolean clockStyle)
            throws ReflectiveOperationException {
        Class<?> base =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
        Method setter = NativeContract.method(base, "setFontType", void.class, font);
        String fontDesc = DexSignatures.descriptor(font);
        List<String> modernArgs = List.of("I", fontDesc, "Ljava/util/List;");
        List<String> legacyArgs = List.of("Ljava/util/List;");
        Set<Method> candidates = new LinkedHashSet<>();
        try {
            for (DexSignatures.Call call : DexSignatures.body(setter).calls()) {
                if (!call.isStatic() || !call.result().equals("Ljava/util/List;")) continue;
                List<String> args = call.parameters();
                if (!args.equals(modernArgs) && !args.equals(legacyArgs)) continue;
                Method candidate = call.method(loader);
                if (DexSignatures.body(candidate, Set.of(NORMALIZER_ANCHOR))
                        .anchors()
                        .containsKey(NORMALIZER_ANCHOR)) {
                    candidates.add(candidate);
                }
            }
        } catch (java.io.IOException invalidDex) {
            throw (NoSuchMethodException)
                    new NoSuchMethodException("Could not read clock height call site")
                            .initCause(invalidDex);
        }
        if (candidates.size() != 1)
            throw new NoSuchMethodException(
                    "Expected unique clock height normalizer, found " + candidates.size());
        return candidates.iterator().next();
    }

    private static void addCallers(
            List<Method> result, Class<?> owner, Class<?> font, Class<?> view, boolean page) {
        // Several zero-argument boolean methods share the stretch-measurement signature.
        // Deoptimize that small superset; never hook or invoke an arbitrary match.
        for (Method method : owner.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.isBridge())
                continue;
            Class<?>[] args = method.getParameterTypes();
            Class<?> returns = method.getReturnType();
            if ((args.length == 0
                            && (returns == boolean.class
                                    || (page
                                            && (returns == font
                                                    || view.isAssignableFrom(returns)))))
                    || (page
                            && returns == void.class
                            && args.length == 2
                            && !args[0].isPrimitive()
                            && args[1] == Object.class)) {
                result.add(method);
            }
        }
    }
}
