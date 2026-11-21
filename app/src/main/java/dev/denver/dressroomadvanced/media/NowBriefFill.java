package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

final class NowBriefFill {
    private static final String GENUI = "com.samsung.android.genui.";
    private static final String PILL = "key_secure_suggestion_container";
    private static final Set<View> blurViews = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> inUpdateBlur =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final View.OnAttachStateChangeListener ATTACH_REPLAY =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    BlurAppearance.recheckDetachedGenUi(view);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {}
            };

    @FunctionalInterface
    interface BlurCall<T> {
        T run() throws Throwable;
    }

    private NowBriefFill() {}

    static void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> style = Class.forName(GENUI + "model.style.BaseStyle", false, loader);
        installStyleBox(module, loader, style);
        Class<?> node = Class.forName(GENUI + "model.GenUINode", false, loader);
        Class<?> kit;
        try {
            kit = Class.forName(GENUI + "kit.style.GenUIStyleKit", false, loader);
        } catch (ClassNotFoundException replaced) {
            installBetaFill(module, new OneUi95BriefFill(loader, node, style));
            return;
        }
        Class<?> linear = Class.forName(GENUI + "model.GenUINode$Linear", false, loader);
        Class<?> modifier = Class.forName(GENUI + "model.GenUIModifier", false, loader);
        Class<?> blur = Class.forName(GENUI + "model.style.BlurStyle", false, loader);
        Method update = NativeContract.method(kit, "updateNodeStyle", node, node, modifier, Boolean.class);
        Method copyStyle = copyDefault(style, 32);
        Method copyLinear = copyDefault(linear, 10);
        if (copyStyle.getParameterTypes()[9] != List.class
                || copyStyle.getParameterTypes()[10] != List.class
                || copyStyle.getParameterTypes()[30] != blur
                || copyLinear.getParameterTypes()[8] != style)
            throw new NoSuchMethodException("Now Brief GenUI background copy contract");
        Method getKey = linear.getMethod("getKey");
        Method getStyle = linear.getMethod("getStyle");
        Method getType = modifier.getMethod("getType");
        Method getBlur = style.getMethod("getBlurStyle");
        Method getDoze = modifier.getMethod("getDoze");
        Method current = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
        module.hook(update).setId("dressroom-advanced.now-brief-clear-pill-fill")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!linear.isInstance(result)) return result;
                    try {
                        if (!PILL.equals(getKey.invoke(result))) return result;
                        Object options = chain.getArg(1);
                        if (!(getType.invoke(options) instanceof Enum<?> type)
                                || !type.name().equals("SECURE_SUGGESTION_COLLAPSE")) return result;
                        Object renderedStyle = getStyle.invoke(result);
                        if (getBlur.invoke(renderedStyle) == null) return result;
                        Context context = (Context) current.invoke(null);
                        float dark = Boolean.TRUE.equals(getDoze.invoke(options)) ? 1f : 0f;
                        if (context == null || !NowBarGlass.glassAllowed(context, dark)) return result;

                        // The Compose fill and the wallpaper blur are separate GenUI layers.
                        // Clear both theme fills in the rendered copy; keep the server model intact.
                        Object clear = copy(copyStyle, renderedStyle, 32, ~((1 << 8) | (1 << 9)), -1, null);
                        return copy(copyLinear, result, 10, 1023 ^ (1 << 7), 8, clear);
                    } catch (ReflectiveOperationException | RuntimeException unavailable) {
                        return result;
                    }
                });
        module.deoptimize(update);
    }

    private static void installBetaFill(DressroomModule module, OneUi95BriefFill binding)
            throws ReflectiveOperationException {
        Method current = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
        module.hook(binding.update).setId("dressroom-advanced.now-brief-clear-pill-fill")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object modifier = chain.getArg(1);
                        if (!binding.isPill(result, modifier)) return result;
                        Context context = (Context) current.invoke(null);
                        if (context != null && NowBarGlass.glassAllowed(context, binding.darkAmount(modifier)))
                            return binding.clear(result);
                    } catch (ReflectiveOperationException | RuntimeException unavailable) {
                        DressroomModule.note("Now Brief beta pill fill unavailable", unavailable);
                    }
                    return result;
                });
        module.deoptimize(binding.update);
    }

    static Method resolveStyleBoxUpdateBlur(ClassLoader loader, Class<?> style)
            throws ReflectiveOperationException {
        try {
            Class<?> styleBox = Class.forName(GENUI + "kit.nodes.StyleBox", false, loader);
            return NativeContract.method(
                styleBox,
                "updateBlur",
                void.class,
                View.class,
                Context.class,
                style,
                boolean.class,
                    Float.class);
        } catch (ClassNotFoundException | NoSuchMethodException changed) {
            try {
                return OneUi95Aod.briefBlurSubmission(loader, style);
            } catch (ReflectiveOperationException unavailable) {
                changed.addSuppressed(unavailable);
                throw changed;
            }
        }
    }

    private static void installStyleBox(DressroomModule module, ClassLoader loader, Class<?> style) {
        try (HookTransaction hooks = new HookTransaction()) {
            Method updateBlur = resolveStyleBoxUpdateBlur(loader, style);
            hooks.add(
                    module.hook(updateBlur)
                            .setId("dressroom-advanced.now-brief-track-stylebox-blur")
                            .intercept(chain -> {
                                View view = (View) chain.getArg(0);
                                if (!Boolean.TRUE.equals(chain.getArg(3))) {
                                    forgetBlurView(view);
                                    return chain.proceed();
                                }
                                return withUpdateBlur(view, chain::proceed);
                            })
                            ::unhook);
            module.deoptimize(updateBlur);
            for (var caller : DexSignatures.callers(updateBlur)) {
                if (!caller.name().equals("<init>")) module.deoptimize(caller.method(loader));
            }
            hooks.commit();
        } catch (ReflectiveOperationException
                | java.io.IOException
                | RuntimeException
                | LinkageError unavailable) {
            module.report("Optional Now Brief GenUI StyleBox blur tracking unavailable", unavailable);
        }
    }

    /** Records a GenUI blur FrameLayout so pre-attach submissions resolve and replay on attach. */
    static void trackBlurView(View view) {
        if (view == null) return;
        boolean added;
        synchronized (blurViews) {
            added = blurViews.add(view);
        }
        if (added) view.addOnAttachStateChangeListener(ATTACH_REPLAY);
    }

    private static void forgetBlurView(View view) {
        if (view == null) return;
        boolean removed;
        synchronized (blurViews) {
            removed = blurViews.remove(view);
        }
        if (!removed) return;
        view.removeOnAttachStateChangeListener(ATTACH_REPLAY);
        BlurAppearance.forgetNowBarRequest(view);
    }

    /** Marks the calling thread as executing StyleBox.updateBlur around {@code action}. */
    static <T> T withUpdateBlur(View view, BlurCall<T> action) throws Throwable {
        trackBlurView(view);
        Boolean previous = inUpdateBlur.get();
        inUpdateBlur.set(Boolean.TRUE);
        try {
            return action.run();
        } finally {
            if (Boolean.TRUE.equals(previous)) inUpdateBlur.set(Boolean.TRUE);
            else inUpdateBlur.remove();
        }
    }

    static boolean isBriefBlurView(View view) {
        if (Boolean.TRUE.equals(inUpdateBlur.get())) return true;
        if (view == null) return false;
        synchronized (blurViews) {
            return blurViews.contains(view);
        }
    }

    /** Replays live blur submissions on attached GenUI Now Brief views after glass settings change. */
    static void refreshAttachedBlurViews() {
        List<View> snapshot;
        synchronized (blurViews) {
            if (blurViews.isEmpty()) return;
            snapshot = new ArrayList<>(blurViews);
        }
        for (View view : snapshot) {
            if (view == null || !view.isAttachedToWindow() || !BlurAppearance.isNowBarCard(view))
                continue;
            try {
                BlurAppearance.refreshNativeNowBar(view);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                DressroomModule.note("Now Brief blur view refresh", unavailable);
            }
        }
    }

    private static Method copyDefault(Class<?> owner, int fields) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals("copy$default") || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != owner || method.getParameterCount() != fields + 3)
                continue;
            Class<?>[] types = method.getParameterTypes();
            if (types[0] != owner || types[fields + 1] != int.class || types[fields + 2] != Object.class)
                continue;
            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(owner.getName() + " copy");
    }

    private static Object copy(Method method, Object original, int fields, int mask,
            int replacement, Object value) throws ReflectiveOperationException {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        args[0] = original;
        for (int i = 1; i <= fields; i++) {
            if (types[i] == int.class) args[i] = 0;
            else if (types[i] == float.class) args[i] = 0f;
            else if (types[i] == boolean.class) args[i] = false;
        }
        if (replacement >= 0) args[replacement] = value;
        args[fields + 1] = mask;
        return method.invoke(null, args);
    }
}
