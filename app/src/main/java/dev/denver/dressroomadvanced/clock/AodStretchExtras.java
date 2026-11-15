package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

final class AodStretchExtras {
    private static final Set<View> mediaReturns =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());

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

    static synchronized void install(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        ClockTransitionBinding binding = ClockTransitionBinding.of(base);
        Method transition = binding.transition;
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
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
                                                    if (controller == null) return chain.proceed();
                                                    expanded =
                                                            Boolean.TRUE.equals(
                                                                    binding.expanded.get(
                                                                            controller));
                                                    dozing =
                                                            Boolean.TRUE.equals(
                                                                    binding.dozing.get(controller));
                                                    doze =
                                                            ((Number) binding.amount.get(clock))
                                                                    .floatValue();
                                                } catch (Exception error) {
                                                    return chain.proceed();
                                                }
                                                if (!preserveHeight(
                                                        RuntimeExtras.enabled(
                                                                clock.getContext(), "keepStretch"),
                                                        expanded,
                                                        dozing,
                                                        doze,
                                                        mediaReturns.contains(clock)))
                                                    return chain.proceed();
                                                Object axis;
                                                ArrayList<Object> remaining = new ArrayList<>();
                                                try {
                                                    axis = binding.axes.get(controller);
                                                    if (!(axis instanceof List<?> list)
                                                            || !list.contains("HEGT"))
                                                        return chain.proceed();
                                                    for (Object value : list)
                                                        if (!"HEGT".equals(value))
                                                            remaining.add(value);
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
                                            })
                            ::unhook);
            module.deoptimize(transition);
            hooks.commit();
            installed.add(loader);
        }
        android.util.Log.i(DressroomModule.TAG, "Native AOD height transition control installed");
    }
}
