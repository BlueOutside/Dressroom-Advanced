package dev.denver.dressroomadvanced;

import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ClockBlurBinding {
    final Method render, clip, clear, host, origin;
    private final Field owner, clock;

    private static final ClassValue<ClockBlurBinding> cache =
            new ClassValue<>() {
                @Override
                protected ClockBlurBinding computeValue(Class<?> base) {
                    try {
                        return new ClockBlurBinding(ClockBlurRetryBinding.of(base));
                    } catch (ReflectiveOperationException | IOException error) {
                        throw new BindingFailure(error);
                    }
                }
            };

    private static final class BindingFailure extends RuntimeException {
        final Exception cause;

        BindingFailure(Exception cause) {
            this.cause = cause;
        }
    }

    static ClockBlurBinding resolve(Class<?> base) throws ReflectiveOperationException {
        ClockBinding binding = ClockBinding.of(base);
        if (binding.clockStyle) throw new NoSuchMethodException("Clock Style blur is not mapped");
        try {
            return cache.get(ClockBlurRetryBinding.absClockView(base));
        } catch (BindingFailure failure) {
            if (failure.cause instanceof ReflectiveOperationException error) throw error;
            throw new ReflectiveOperationException("Could not read clock blur DEX", failure.cause);
        }
    }

    private ClockBlurBinding(ClockBlurRetryBinding retry)
            throws ReflectiveOperationException, IOException {
        render = retry.render;
        Class<?> strategy = render.getDeclaringClass();
        ClassLoader loader = strategy.getClassLoader();
        clear = resolveClear(strategy);
        host = NativeContract.findMethod(strategy, null, ViewGroup.class);
        origin = NativeContract.findMethod(strategy, null, PointF.class, ViewGroup.class);
        DexSignatures.Body body = DexSignatures.body(render);
        clip = resolveClip(body, loader);
        Field[] ownerPath = retry.task == null
                ? retry.strategyClockPath(body)
                : ClockBlurRetryBinding.ownerClockPath(body, strategy, loader);
        owner = ownerPath[0];
        clock = ownerPath[1];
        if (clock.getType() != Object.class && !View.class.isAssignableFrom(clock.getType()))
            throw new NoSuchFieldException("Native blur owner is not a clock view");
    }

    static Method resolveClear(Class<?> strategy) throws ReflectiveOperationException, IOException {
        List<Method> zeroArg = new ArrayList<>();
        for (Method method : strategy.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.isBridge()
                    || method.getReturnType() != void.class
                    || method.getParameterCount() != 0) continue;
            zeroArg.add(method);
        }
        if (zeroArg.size() == 1) {
            Method method = zeroArg.get(0);
            method.setAccessible(true);
            return method;
        }
        Method found = null;
        for (Method method : zeroArg) {
            boolean clears =
                    DexSignatures.body(method).calls().stream()
                            .anyMatch(
                                    call ->
                                            !call.isStatic()
                                                    && call.owner().equals("Landroid/view/View;")
                                                    && call.name().equals("semSetBlurInfo"));
            if (!clears) continue;
            if (found != null)
                throw new NoSuchMethodException(
                        "Ambiguous clock blur clear: " + strategy.getName());
            found = method;
        }
        if (found == null)
            throw new NoSuchMethodException("Missing clock blur clear: " + strategy.getName());
        found.setAccessible(true);
        return found;
    }

    private static Method resolveClip(DexSignatures.Body renderBody, ClassLoader loader)
            throws ReflectiveOperationException {
        Set<Method> matches = new LinkedHashSet<>();
        List<String> expected =
                List.of("Landroid/view/SemBlurInfo$Builder;", "Landroid/graphics/Path;");
        for (DexSignatures.Call call : renderBody.calls()) {
            if (call.isStatic()
                    && call.result().equals("V")
                    && call.parameters().equals(expected)) {
                matches.add(call.method(loader));
            }
        }
        if (matches.size() != 1)
            throw new NoSuchMethodException(
                    "Ambiguous clock blur clip call site: " + matches.size());
        return matches.iterator().next();
    }

    View clock(Object strategy) throws IllegalAccessException {
        Object holder = owner.get(strategy);
        Object value = holder == null ? null : clock.get(holder);
        return value instanceof View view ? view : null;
    }
}
