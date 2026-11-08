package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class AodStretchExtras {
    private static final java.util.Set<View> mediaReturns =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private AodStretchExtras() {}

    static void mediaReturn(View clock, boolean active) {
        if (active) mediaReturns.add(clock);
        else mediaReturns.remove(clock);
    }

    static boolean preserveHeight(
            boolean enabled,
            boolean mediaExpanded,
            boolean dozing,
            float dozeAmount,
            boolean returning) {
        return enabled && !mediaExpanded && (dozing || dozeAmount > 0f || !returning);
    }

    static void install(DressroomModule module, Class<?> base) throws ReflectiveOperationException {
        ClockTransitionBinding binding = ClockTransitionBinding.of(base);
        Method transition = binding.transition;
        module.hook(transition)
                .setId("dressroom-advanced.aod-keep-stretch")
                .intercept(
                        chain -> {
                            View clock = (View) chain.getThisObject();
                            Object controller;
                            boolean expanded, dozing;
                            float doze;
                            try {
                                controller = binding.controller.get(clock);
                                expanded = Boolean.TRUE.equals(binding.expanded.get(controller));
                                dozing = Boolean.TRUE.equals(binding.dozing.get(controller));
                                doze = ((Number) binding.amount.get(clock)).floatValue();
                            } catch (Exception error) {
                                return chain.proceed();
                            }
                            if (!preserveHeight(
                                    RuntimeExtras.enabled(clock.getContext(), "keepStretch"),
                                    expanded,
                                    dozing,
                                    doze,
                                    mediaReturns.contains(clock))) return chain.proceed();
                            Object axis;
                            ArrayList<Object> remaining = new ArrayList<>();
                            try {
                                axis = binding.axes.get(controller);
                                if (!(axis instanceof List<?> list) || !list.contains("HEGT"))
                                    return chain.proceed();
                                for (Object value : list)
                                    if (!"HEGT".equals(value)) remaining.add(value);
                                binding.axes.set(controller, remaining);
                            } catch (Exception error) {
                                return chain.proceed();
                            }
                            try {
                                return chain.proceed();
                            } finally {
                                try {
                                    binding.axes.set(controller, axis);
                                } catch (Exception ignored) {
                                }
                            }
                        });
        module.deoptimize(transition);
        android.util.Log.i(DressroomModule.TAG, "Native AOD height transition control installed");
    }
}
