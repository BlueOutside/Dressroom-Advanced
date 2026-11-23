package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Custom label sizes do not own date visibility or the saved clock position. */
final class ClockLabelSizing {
    private record Profile(int date, int weather, float reference) {}
    private record Bounds(float scale, int width, int height) {}
    private static final Map<View, Profile> profiles = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Bounds> editorBounds = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> providerWidths = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, ClockLabelLayout> labelLayouts = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Method> hooked = new java.util.HashSet<>();

    private ClockLabelSizing() {}

    static void bind(View clock, Bundle settings) {
        int date = settings == null ? ClockLabelScale.DEFAULT
                : ClockLabelScale.clamp(settings.getInt(ClockLabelScale.DATE, ClockLabelScale.DEFAULT));
        int weather = settings == null ? ClockLabelScale.DEFAULT
                : ClockLabelScale.clamp(settings.getInt(ClockLabelScale.WEATHER, ClockLabelScale.DEFAULT));
        bind(clock, date, weather, ClockLabelScale.reference(
                settings == null ? 0f : settings.getFloat(ClockLabelScale.REFERENCE), date, weather));
    }

    static void bind(View clock, int date, int weather, float reference) {
        if (!(Float.isFinite(reference) && reference > 0f)) {
            profiles.remove(clock);
            restore(clock);
            return;
        }
        profiles.put(clock, new Profile(date, weather, reference));
        apply(clock);
    }

    static void reset() {
        synchronized (labelLayouts) {
            labelLayouts.values().forEach(ClockLabelLayout::restore);
            labelLayouts.clear();
        }
        profiles.clear();
        editorBounds.clear();
    }

    static void clear(View clock) {
        profiles.remove(clock);
        editorBounds.remove(clock);
        restore(clock);
    }

    private static void restore(View clock) {
        ClockLabelLayout previous = labelLayouts.remove(clock);
        if (previous != null) previous.restore();
    }

    static ClockLabelFit.Size stockSize(View clock) {
        ClockLabelLayout layout = labelLayouts.get(clock);
        return layout == null ? null : layout.stock();
    }

    static void editorBounds(View clock, Rect area, float scale, int maxHeight) {
        if (area.isEmpty()) return;
        int height = maxHeight > 0 ? Math.min(maxHeight, area.height()) : area.height();
        Bounds next = new Bounds(ClockLabelScale.zoom(scale), area.width(), height);
        if (!next.equals(editorBounds.put(clock, next))) apply(clock);
    }

    static synchronized void install(DressroomModule module, Class<?> renderer)
            throws ReflectiveOperationException {
        Method width = renderer.getMethod("setClockMaxWidth", int.class);
        if (!hooked.contains(width)) {
            module.hook(width).setId("dressroom-advanced.date-width").intercept(chain -> {
                View clock = (View) chain.getThisObject();
                providerWidths.put(clock, (Integer) chain.getArg(0));
                Object result = chain.proceed();
                apply(clock);
                return result;
            });
            hooked.add(width);
            module.deoptimize(width);
        }
        Method scale = renderer.getMethod("setScale", float.class);
        if (hooked.contains(scale)) return;
        module.hook(scale).setId("dressroom-advanced.label-native-scale").intercept(chain -> {
            View clock = (View) chain.getThisObject();
            ClockLabelLayout layout = labelLayouts.get(clock);
            if (layout != null) layout.restore();
            Object result = chain.proceed();
            if (layout != null) {
                if (!clock.isAttachedToWindow()) apply(clock);
                else DateEffects.deferUpdate(clock);
            }
            return result;
        });
        hooked.add(scale);
        module.deoptimize(scale);
    }

    static void apply(View clock) {
        Profile profile = profiles.get(clock);
        if (profile == null) return;
        boolean mediaExpanded = false;
        try {
            mediaExpanded = ClockTransitionBinding.of(clock).mediaExpanded.getBoolean(clock);
        } catch (RuntimeException | ReflectiveOperationException ignored) {}
        if (mediaExpanded) {
            restore(clock);
            return;
        }
        Bounds bounds = editorBounds.get(clock);
        float nativeScale = 1f;
        if (bounds == null) {
            float scale = ClockLabelScale.zoom(clock.getScaleX());
            if (clock instanceof ViewGroup group) {
                View content = clockContent(group);
                if (content != null) scale *= ClockLabelScale.zoom(content.getScaleX());
            }
            int width = providerWidths.getOrDefault(clock, 0);
            if (width <= 0) {
                width = clock.getResources().getDisplayMetrics().widthPixels
                        - clock.getPaddingLeft() - clock.getPaddingRight();
            }
            bounds = new Bounds(scale, width, clock.getResources().getDisplayMetrics().heightPixels);
            nativeScale = nativeScale(clock);
        }
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        ClockLabelLayout layout = labelLayouts.computeIfAbsent(clock, ignored -> new ClockLabelLayout());
        float factor = ClockLabelScale.labelFactor(profile.reference(), bounds.scale(), nativeScale);
        layout.apply(clock, profile.date(), profile.weather(), factor,
                (int) (bounds.width() / bounds.scale()), (int) (bounds.height() / bounds.scale()));
    }

    private static float nativeScale(View clock) {
        try {
            Object value = Reflect.call(clock, "getScale");
            if (value instanceof Number scale) return scale.floatValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return 1f;
    }

    private static View clockContent(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ("common_time_date_container".equals(DateEffects.entryName(child))) return child;
            if (child instanceof ViewGroup children) {
                View content = clockContent(children);
                if (content != null) return content;
            }
        }
        return null;
    }

}
