package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** Keep geometry fresh without committing native animation endpoints early. */
final class ClockBlurContinuity {
    private ClockBlurContinuity() {}

    private static final String DASHBOARD_FADE = "startDashBoardBlurOnAnimation: Starting blur animation";
    private record Fade(Field animator, boolean dashboard) {}

    static void install(DressroomModule module, ClockBlurRetryBinding binding)
            throws ReflectiveOperationException, java.io.IOException {
        Class<?> manager = binding.submit.getDeclaringClass();
        Map<Method, Fade> fades = new java.util.LinkedHashMap<>();
        var submitCalls = DexSignatures.body(binding.submit).calls();
        for (Method method : manager.getDeclaredMethods()) {
            if (method.getReturnType() != void.class || method.getParameterCount() != 0) continue;
            var body = DexSignatures.body(method, Set.of(DASHBOARD_FADE));
            boolean animates =
                    body.calls().stream()
                            .anyMatch(
                                    call ->
                                            call.owner().equals("Landroid/animation/ValueAnimator;")
                                                    && call.name().equals("ofFloat"));
            boolean submitted = submitCalls.stream()
                    .anyMatch(call -> call.owner().equals(DexSignatures.descriptor(manager))
                            && call.name().equals(method.getName()) && call.parameters().isEmpty());
            if (animates && submitted) {
                Field animator = body.uniqueRead(DexSignatures.descriptor(manager),
                        "Landroid/animation/ValueAnimator;", null).field(manager.getClassLoader());
                fades.put(method, new Fade(animator, body.anchors().containsKey(DASHBOARD_FADE)));
            }
        }
        if (fades.isEmpty() || fades.size() > 2) throw new NoSuchMethodException("Ambiguous blur fade entries");
        Field dashboardAnimator = fades.values().stream().filter(Fade::dashboard)
                .map(Fade::animator).findFirst().orElse(null);
        if (dashboardAnimator != null) {
            try (HookTransaction alpha = new HookTransaction()) {
                installDashboardAlpha(module, manager, submitCalls, dashboardAnimator, binding, alpha);
                alpha.commit();
            } catch (ReflectiveOperationException | java.io.IOException | RuntimeException error) {
                module.report("Optional seconds-view live fade alpha unavailable", error);
            }
        }
        try (HookTransaction hooks = new HookTransaction()) {
            for (Map.Entry<Method, Fade> entry : fades.entrySet()) {
                Method fade = entry.getKey();
                Fade state = entry.getValue();
                hooks.add(module.hook(fade).setId("dressroom-advanced.clock-continuous-blur")
                        .intercept(chain -> {
                            Object source = chain.getThisObject();
                            if (state.dashboard) {
                                try {
                                    if (ClockBlurMotion.returning(binding.clock(source))
                                            && state.animator.get(source) instanceof ValueAnimator previous
                                            && previous.isStarted()) return null;
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    DressroomModule.note("Seconds-view blur fade state", error);
                                }
                            }
                            Object result = chain.proceed();
                            try {
                                ClockBlurAlignment.awaitAnimator(binding.clock(source),
                                        (ValueAnimator) state.animator.get(source));
                            } catch (ReflectiveOperationException | RuntimeException error) {
                                DressroomModule.note("Native clock fade alignment", error);
                            }
                            return result;
                        })::unhook);
                module.deoptimize(fade);
            }
            hooks.commit();
        }
        try {
            ClockBlurColors.install(module, binding);
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException | LinkageError error) {
            module.report("Optional native clock path-color correction unavailable", error);
        }
        try {
            ClockBlurMotion.install(module, binding);
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException error) {
            module.report("Optional moving clock blur unavailable", error);
        }
        android.util.Log.i(DressroomModule.TAG, "Continuous native clock blur installed");
    }

    private static void installDashboardAlpha(DressroomModule module, Class<?> manager,
            java.util.List<DexSignatures.Call> calls, Field animator, ClockBlurRetryBinding binding,
            HookTransaction hooks) throws ReflectiveOperationException, java.io.IOException {
        String owner = DexSignatures.descriptor(manager);
        Set<Method> methods = new java.util.HashSet<>();
        for (var call : calls) {
            if (!call.isStatic() && call.owner().equals(owner) && call.result().equals("V")
                    && call.parameters().equals(java.util.List.of("I"))) {
                Method candidate = call.method(manager.getClassLoader());
                if (DexSignatures.body(candidate).calls().stream().anyMatch(bodyCall ->
                        bodyCall.owner().equals("Landroid/widget/TextView;")
                                && bodyCall.name().equals("setTextColor")
                                && bodyCall.parameters().equals(java.util.List.of("I"))))
                    methods.add(candidate);
            }
        }
        if (methods.size() != 1) return;
        Method alpha = methods.iterator().next();
        hooks.add(module.hook(alpha).setId("dressroom-advanced.seconds-return-blur-alpha")
                .intercept(chain -> {
                    if (!ClockBlurMotion.hasReturningClock()) return chain.proceed();
                    Integer value = null;
                    try {
                        Object source = chain.getThisObject();
                        if (ClockBlurMotion.returning(binding.clock(source))
                                && animator.get(source) instanceof ValueAnimator fade
                                && fade.isRunning() && fade.getAnimatedValue() instanceof Number current)
                            value = current.intValue();
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("Seconds-view moving blur alpha", error);
                    }
                    return value == null || value.equals(chain.getArg(0)) ? chain.proceed()
                            : chain.proceed(new Object[] {value});
                })::unhook);
        module.deoptimize(alpha);
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
