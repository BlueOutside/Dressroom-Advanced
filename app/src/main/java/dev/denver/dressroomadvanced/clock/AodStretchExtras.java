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
        Method transition = base.getDeclaredMethod(ClockBinding.of(base).fold8 ? "G0" : "K0", float.class, boolean.class);
        module.hook(transition)
                .setId("dressroom-advanced.aod-keep-stretch")
                .intercept(
                        chain -> {
                            View clock = (View) chain.getThisObject();
                            Object controller;
                            boolean expanded, dozing;
                            float doze;
                            try {
                                controller = OneUi9Fold8Runtime.field(clock, "W0");
                                expanded = Boolean.TRUE.equals(OneUi9Fold8Runtime.field(controller, "h"));
                                dozing = Boolean.TRUE.equals(OneUi9Fold8Runtime.field(controller, "d"));
                                doze = ((Number) OneUi9Fold8Runtime.field(clock, "t0")).floatValue();
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
                                axis = OneUi9Fold8Runtime.field(controller, "j");
                                if (!(axis instanceof List<?> list) || !list.contains("HEGT"))
                                    return chain.proceed();
                                for (Object value : list)
                                    if (!"HEGT".equals(value)) remaining.add(value);
                                Reflect.set(controller, "j", remaining);
                            } catch (Exception error) {
                                return chain.proceed();
                            }
                            try {
                                return chain.proceed();
                            } finally {
                                try {
                                    Reflect.set(controller, "j", axis);
                                } catch (Exception ignored) {
                                }
                            }
                        });
        module.deoptimize(transition);
        android.util.Log.i(DressroomModule.TAG, "Native AOD height transition control installed");
    }
}
