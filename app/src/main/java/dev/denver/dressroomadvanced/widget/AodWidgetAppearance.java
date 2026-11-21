package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class AodWidgetAppearance {
    static final String KEY = "keepAodWidgetBlur";
    static final String PROFILE = "aodWidgetBlur";
    static final String NEUTRAL = "neutralAodWidgetBlur";
    private static final String WIDGETS = "com.samsung.android.app.facewidget.complication.";
    private static final Map<Class<?>, Binding> installed =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Frame> submitting = new ThreadLocal<>();

    private record Binding(Field wallpaper, Field reduceTransparency, Field darkAmount,
            Field surface, Field expanded, Method refresh) {}

    private record Frame(View owner, float darkAmount, boolean keepLockscreenPreset) {}

    private AodWidgetAppearance() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess() || !WallpaperAod.supported()) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> owner = Class.forName(WIDGETS + "ComplicationWidgetHostViewContainer", false, loader);
            if (installed.containsKey(owner) || !View.class.isAssignableFrom(owner)) return;
            Class<?> preset = Class.forName(WIDGETS + "WidgetBlurType", false, loader);
            Class<?> curves = Class.forName(WIDGETS + "WidgetBlurTypeKt", false, loader);
            Field wallpaper = NativeContract.field(owner, "showWallpaperOnAOD", boolean.class);
            Field reduceTransparency;
            try {
                reduceTransparency = NativeContract.field(owner, "reduceTransparencyEnabled", boolean.class);
            } catch (NoSuchFieldException absent) {
                reduceTransparency = null;
            }
            Method refresh = NativeContract.method(owner, "refreshBlur", void.class, float.class);
            Method darkAmount = NativeContract.method(owner, "setDarkAmount", void.class, float.class);
            Method update = NativeContract.method(owner, "changeBlurBgByDarkAmount", void.class, float.class);
            if (!Modifier.isFinal(refresh.getModifiers())) return;
            List<Method> directional = directionalCurves(refresh, curves, preset);
            for (Method method : directional) {
                hooks.add(module.hook(method).setId("dressroom-advanced.aod-widget-appearance-" + method.getName())
                        .intercept(chain -> {
                            Frame frame = submitting.get();
                            if (frame == null || !frame.keepLockscreenPreset()) return chain.proceed();
                            return chain.proceed(new Object[] {chain.getArg(0), 0f, chain.getArg(2)});
                        })::unhook);
                module.deoptimize(method);
            }
            Binding binding = new Binding(wallpaper, reduceTransparency,
                    NativeContract.field(owner, "darkAmount", float.class),
                    NativeContract.field(owner, "blurView", View.class),
                    NativeContract.field(owner, "musicExpandState", boolean.class), refresh);
            hooks.add(module.hook(refresh).setId("dressroom-advanced.aod-widget-blur")
                    .intercept(chain -> {
                        View view = (View) chain.getThisObject();
                        float amount = (Float) chain.getArg(0);
                        boolean keep = useLockscreenPreset(view, amount, binding);
                        Frame previous = submitting.get();
                        submitting.set(new Frame(view, amount, keep));
                        try {
                            // Directional builds track the actual dark amount to steer their blend.
                            if (keep && directional.isEmpty())
                                return chain.proceed(new Object[] {0f});
                            return chain.proceed();
                        } finally {
                            if (previous == null) submitting.remove();
                            else submitting.set(previous);
                        }
                    })::unhook);
            module.deoptimize(refresh);
            module.deoptimize(update);
            module.deoptimize(darkAmount);
            hooks.commit();
            installed.put(owner, binding);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional wallpaper AOD widget blur unavailable", unavailable);
        }
    }

    static List<Method> directionalCurves(Method refresh, Class<?> curves, Class<?> preset)
            throws ReflectiveOperationException, java.io.IOException {
        if (NativeContract.hasStaticMethod(curves, float[].class, preset, float.class)
                && NativeContract.hasStaticMethod(curves, Integer.class, preset, float.class))
            return List.of();
        return OneUi95Aod.directionalWidgetBlur(refresh, curves, preset);
    }

    private static boolean useLockscreenPreset(View view, float darkAmount, Binding binding) {
        if (darkAmount <= 0f) return false;
        try {
            // Only the curve and dim calculation receive zero. The widget's actual doze state stays native.
            return binding.wallpaper.getBoolean(view)
                    && (binding.reduceTransparency == null || !binding.reduceTransparency.getBoolean(view))
                    && RuntimeExtras.enabled(view.getContext(), KEY)
                    && BlurAppearance.liveWindowBlurAllowed(view.getContext());
        } catch (IllegalAccessException | RuntimeException unavailable) {
            return false;
        }
    }

    static boolean applies(View surface) throws IllegalAccessException {
        if (installed.isEmpty() || !(surface.getParent() instanceof View owner)) return false;
        Binding binding = installed.get(owner.getClass());
        if (binding == null || binding.surface.get(owner) != surface) return false;
        Frame frame = submitting.get();
        float amount = frame != null && frame.owner == owner
                ? frame.darkAmount : binding.darkAmount.getFloat(owner);
        return amount > 0f && binding.wallpaper.getBoolean(owner)
                && (binding.reduceTransparency == null || !binding.reduceTransparency.getBoolean(owner));
    }

    static boolean refresh(View surface) throws ReflectiveOperationException {
        if (!applies(surface) || !(surface.getParent() instanceof View owner)) return false;
        Binding binding = installed.get(owner.getClass());
        if (!binding.expanded.getBoolean(owner))
            binding.refresh.invoke(owner, binding.darkAmount.getFloat(owner));
        return true;
    }
}
