package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Reusing a visible window blur doesn't need another native zero-to-one fade. */
final class ClockBlurContinuity {
    private ClockBlurContinuity() {}

    static void install(DressroomModule module, ClockBlurRetryBinding binding)
            throws ReflectiveOperationException, java.io.IOException {
        Class<?> manager = binding.submit.getDeclaringClass();
        Map<Method, Field> fades = new java.util.LinkedHashMap<>();
        for (Method method : manager.getDeclaredMethods()) {
            if (method.getReturnType() != void.class || method.getParameterCount() != 0) continue;
            var body = DexSignatures.body(method);
            boolean animates =
                    body.calls().stream()
                            .anyMatch(
                                    call ->
                                            call.owner().equals("Landroid/animation/ValueAnimator;")
                                                    && call.name().equals("ofFloat"));
            boolean submitted = DexSignatures.body(binding.submit).calls().stream()
                    .anyMatch(call -> call.owner().equals(DexSignatures.descriptor(manager))
                            && call.name().equals(method.getName()) && call.parameters().isEmpty());
            if (animates && submitted) {
                Field animator = body.uniqueRead(DexSignatures.descriptor(manager),
                        "Landroid/animation/ValueAnimator;", null).field(manager.getClassLoader());
                fades.put(method, animator);
            }
        }
        if (fades.isEmpty() || fades.size() > 2) throw new NoSuchMethodException("Ambiguous blur fade entries");
        if (binding.animator == null) throw new NoSuchFieldException("Native blur reveal animator");
        Class<?> strategy = binding.strategy.getType();
        Method transition =
                NativeContract.findMethod(
                        strategy, null, void.class, int.class, android.widget.ImageView.class);
        Field transitionAnimator =
                NativeContract.findField(strategy, null, android.animation.ValueAnimator.class);
        try (HookTransaction hooks = new HookTransaction()) {
            for (Map.Entry<Method, Field> entry : fades.entrySet()) {
                Method fade = entry.getKey();
                Field animator = entry.getValue();
            hooks.add(
                    module.hook(fade)
                                    .setId("dressroom-advanced.clock-continuous-blur")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                try {
                                                    settle(
                                                            (android.animation.ValueAnimator)
                                                                    animator.get(
                                                                            chain.getThisObject()));
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    DressroomModule.note(
                                                            "Native clock reveal completion",
                                                            error);
                                                }
                                                return result;
                                            })
                            ::unhook);
                module.deoptimize(fade);
            }
            hooks.add(
                    module.hook(transition)
                                    .setId("dressroom-advanced.clock-blur-transition-final-state")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                try {
                                                    settle(
                                                            (android.animation.ValueAnimator)
                                                                    transitionAnimator.get(
                                                                            chain.getThisObject()));
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    DressroomModule.note(
                                                            "Native clock transition completion",
                                                            error);
                                                }
                                                return result;
                                            })
                            ::unhook);
            module.deoptimize(transition);
            hooks.commit();
        }
        try {
            ClockBlurMotion.install(module, binding);
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException error) {
            module.report("Optional moving clock blur unavailable", error);
        }
        android.util.Log.i(DressroomModule.TAG, "Continuous native clock blur installed");
    }

    static void settle(android.animation.ValueAnimator animation) {
        if (animation != null && animation.isStarted()) animation.end();
    }

    static void beforeDraw(View clock) {
        ClockBlurMotion.beforeDraw(clock);
    }

    static boolean retaining(View clock) {
        return ClockBlurMotion.retaining(clock);
    }

    static void forget(View clock) {
        ClockBlurMotion.forget(clock);
    }
}
