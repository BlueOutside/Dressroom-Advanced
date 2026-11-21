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

final class DateEffects {
    private static final Set<String> DATE_TEXT_ENTRIES =
            Set.of(
                    "common_date",
                    "common_local_date",
                    "common_roaming_date_view",
                    "common_roaming_date_upper_view",
                    "common_roaming_date_lower_view",
                    "common_home_date_view",
                    "common_home_date_upper_view",
                    "common_home_date_lower_view");
    private static final Map<View, Appearance> appearances =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, int[]> identities =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> originalVisibility =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> weatherMargins =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> measured =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<View> pending =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Method> hooked = new java.util.HashSet<>();

    private static final Map<View, ClockLabelLayout> labelLayouts =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<View, Bounds> editorBounds =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> providerWidths =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record Bounds(float scale, int width, int height) {}

    private record Appearance(boolean hide, int date, int weather, float reference) {
        static final Appearance DEFAULT =
                new Appearance(false, ClockLabelScale.DEFAULT, ClockLabelScale.DEFAULT, 0f);

        static Appearance from(Bundle settings) {
            if (settings == null) return DEFAULT;
            int date = ClockLabelScale.clamp(settings.getInt(ClockLabelScale.DATE, ClockLabelScale.DEFAULT));
            int weather = ClockLabelScale.clamp(settings.getInt(ClockLabelScale.WEATHER, ClockLabelScale.DEFAULT));
            float reference = ClockLabelScale.reference(
                    settings.getFloat(ClockLabelScale.REFERENCE), date, weather);
            return new Appearance(settings.getBoolean("hideDate"), date, weather, reference);
        }
    }

    private DateEffects() {}

    static void resetPreviews() {
        appearances.clear();
        identities.clear();
        editorBounds.clear();
        synchronized (labelLayouts) {
            labelLayouts.values().forEach(ClockLabelLayout::restore);
            labelLayouts.clear();
        }
    }

    static synchronized void attach(DressroomModule module, View clock) {
        if (tracked.add(clock)) {
            clock.addOnLayoutChangeListener(
                    (v, l, t, r, b, ol, ot, or, ob) -> updateDate(v));
        }
        try {
            Method setter = clock.getClass().getMethod("setDateVisibility", int.class);
            if (!hooked.contains(setter)) {
                module.hook(setter)
                        .setId("dressroom-advanced.date-visibility")
                        .intercept(
                                chain -> {
                                    View target = (View) chain.getThisObject();
                                    Object result = chain.proceed();
                                    updateDate(target);
                                    deferUpdate(target);
                                    return result;
                                });
                hooked.add(setter);
            }
            Method weatherSetter =
                    clock.getClass().getMethod("setShowWeatherInformation", boolean.class);
            if (!hooked.contains(weatherSetter)) {
                module.hook(weatherSetter)
                        .setId("dressroom-advanced.weather-layout")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    View target = (View) chain.getThisObject();
                                    updateDate(target);
                                    deferUpdate(target);
                                    return result;
                                });
                hooked.add(weatherSetter);
            }
            for (Method method : clock.getClass().getMethods()) {
                if (!method.getName().equals("updateWeatherInfo")
                        || method.getParameterCount() != 1
                        || hooked.contains(method)) continue;
                module.hook(method)
                        .setId("dressroom-advanced.weather-info-layout")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    View target = (View) chain.getThisObject();
                                    updateDate(target);
                                    deferUpdate(target);
                                    return result;
                                });
                hooked.add(method);
            }
        } catch (Exception | LinkageError e) {
            module.report("Date visibility unavailable for this clock", e);
        }
        apply(clock);
    }

    static synchronized void prepareMeasurement(DressroomModule module, View clock) {
        // Clock Style's size probe is never attached to a window. Hide its date
        // before measuring too, including when Samsung reuses and refreshes it.
        measured.add(clock);
        attach(module, clock);
        try {
            Method refresh = clock.getClass().getMethod("forceRefresh");
            if (!hooked.contains(refresh)) {
                module.hook(refresh)
                        .setId("dressroom-advanced.date-measurement-refresh")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    View target = (View) chain.getThisObject();
                                    if (measured.contains(target)) updateDate(target);
                                    return result;
                                });
                hooked.add(refresh);
                module.deoptimize(refresh);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.report("Clock Style date measurement unavailable", error);
        }
    }

    static void preview(View clock, boolean hide, int date, int weather, float reference) {
        setAppearance(clock, new Appearance(hide, date, weather, reference));
    }

    static void bindSettings(View clock, Bundle settings) {
        setAppearance(clock, Appearance.from(settings));
    }

    static void editorBounds(View clock, Rect area, float scale, int maxHeight) {
        if (area.isEmpty()) return;
        scale = ClockLabelScale.zoom(scale);
        int height = maxHeight > 0 ? Math.min(maxHeight, area.height()) : area.height();
        Bounds next = new Bounds(scale, area.width(), height);
        if (next.equals(editorBounds.put(clock, next))) return;
        if (appearance(clock).reference() > 0f) apply(clock);
    }

    static ClockLabelFit.Size stockSize(View clock) {
        ClockLabelLayout layout = labelLayouts.get(clock);
        return layout == null ? null : layout.stock();
    }

    static synchronized void installProviderSizing(DressroomModule module, Class<?> renderer)
            throws ReflectiveOperationException {
        Method width = renderer.getMethod("setClockMaxWidth", int.class);
        installProviderWidth(module, width);

        Method scale = renderer.getMethod("setScale", float.class);
        if (hooked.contains(scale)) return;
        module.hook(scale)
                .setId("dressroom-advanced.label-native-scale")
                .intercept(chain -> {
                    View clock = (View) chain.getThisObject();
                    ClockLabelLayout layout = labelLayouts.get(clock);
                    // Stock sizes for Samsung's scale pass
                    if (layout != null) layout.restore();
                    Object result = chain.proceed();
                    if (layout != null) deferUpdate(clock);
                    return result;
                });
        hooked.add(scale);
        module.deoptimize(scale);
    }

    private static void installProviderWidth(DressroomModule module, Method width) {
        if (hooked.contains(width)) return;
        module.hook(width)
                .setId("dressroom-advanced.date-width")
                .intercept(chain -> {
                    View clock = (View) chain.getThisObject();
                    providerWidths.put(clock, (Integer) chain.getArg(0));
                    Object result = chain.proceed();
                    if (tracked.contains(clock)) apply(clock);
                    return result;
                });
        hooked.add(width);
        module.deoptimize(width);
    }

    private static void setAppearance(View clock, Appearance appearance) {
        appearances.put(clock, appearance);
        apply(clock);
    }

    static void bindIdentity(View clock, int type, int font, int orientation) {
        int[] next = {type, font, orientation};
        if (java.util.Arrays.equals(identities.put(clock, next), next)) return;
        appearances.remove(clock);
        apply(clock);
    }

    static void clearPreview(View clock) {
        Appearance previous = appearances.remove(clock);
        identities.remove(clock);
        editorBounds.remove(clock);
        ClockLabelLayout layout = labelLayouts.remove(clock);
        if (layout != null)
            layout.restore();
        if (previous != null && previous.hide()) apply(clock);
    }

    static void apply(View clock) {
        updateDate(clock);
        deferUpdate(clock);
    }

    static void deferUpdate(View clock) {
        // Detached size probes never drain View.post's run queue. They already
        // received the synchronous update; only attached clocks need a second pass.
        if (!clock.isAttachedToWindow() || !pending.add(clock)) return;
        java.lang.ref.WeakReference<View> reference = new java.lang.ref.WeakReference<>(clock);
        try {
            if (clock.post(
                    () -> {
                        View current = reference.get();
                        if (current == null) return;
                        pending.remove(current);
                        if (current.isAttachedToWindow()) updateDate(current);
                    })) return;
        } catch (RuntimeException error) {
            pending.remove(clock);
            android.util.Log.w(DressroomModule.TAG, "Deferred date refresh unavailable", error);
            return;
        }
        pending.remove(clock);
    }

    private static Appearance appearance(View clock) {
        Appearance bound = appearances.get(clock);
        if (bound != null) return bound;
        try {
            Bundle query = new Bundle();
            int[] identity = identities.get(clock);
            query.putInt(
                    "type",
                    identity == null
                            ? ((Number) Reflect.call(clock, "getClockType")).intValue()
                            : identity[0]);
            query.putInt(
                    "font", identity == null ? ClockBinding.of(clock).fontId(clock) : identity[1]);
            if (identity != null) query.putInt(LayoutOrientation.FIELD, identity[2]);
            return Appearance.from(SettingsBridge.read(clock.getContext(), query));
        } catch (Exception ignored) {
            return Appearance.DEFAULT;
        }
    }

    private static void updateDate(View clock) {
        Appearance appearance = appearance(clock);
        boolean hide = appearance.hide();
        View[] rowPair = new View[2];
        applySubtree(clock, hide, rowPair);
        View weather = rowPair[0], dateRow = rowPair[1];
        if (dateRow != null && weather != null) {
            updateText(dateRow, hide && weather.getVisibility() != View.VISIBLE);
        }
        boolean resize = (!measured.contains(clock) || clock.isAttachedToWindow())
                && appearance.reference() > 0f;
        if (!resize) {
            ClockLabelLayout previous = labelLayouts.remove(clock);
            if (previous != null) previous.restore();
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
        float factor = ClockLabelScale.labelFactor(appearance.reference(), bounds.scale(), nativeScale);
        layout.apply(clock, appearance.date(), appearance.weather(), factor,
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
            if ("common_time_date_container".equals(entryName(child))) return child;
            if (child instanceof ViewGroup children) {
                View content = clockContent(children);
                if (content != null) return content;
            }
        }
        return null;
    }

    private static void applySubtree(View view, boolean hide, View[] rowPair) {
        if (view == null) return;
        String entry = entryName(view);
        if (entry != null) {
            if (isDate(entry)) {
                updateText(view, hide);
            } else if ("common_weather_container".equals(entry)) {
                updateWeatherMargin(view, hide);
                if (rowPair[0] == null) rowPair[0] = view;
            } else if ("common_date_container".equals(entry)) {
                if (rowPair[1] == null) rowPair[1] = view;
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                applySubtree(group.getChildAt(i), hide, rowPair);
            }
        }
    }

    private static void updateWeatherMargin(View weather, boolean hide) {
        ViewGroup.LayoutParams params = weather.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams margins)) return;
        if (hide) {
            weatherMargins.putIfAbsent(weather, margins.getMarginStart());
            if (margins.getMarginStart() != 0) {
                margins.setMarginStart(0);
                weather.setLayoutParams(margins);
            }
        } else {
            Integer original = weatherMargins.remove(weather);
            if (original != null && margins.getMarginStart() != original) {
                margins.setMarginStart(original);
                weather.setLayoutParams(margins);
            }
        }
    }

    private static void updateText(View date, boolean hide) {
        if (date == null) return;
        if (hide) {
            if (date.getVisibility() != View.GONE || !originalVisibility.containsKey(date))
                originalVisibility.put(date, date.getVisibility());
            if (date.getVisibility() != View.GONE) date.setVisibility(View.GONE);
        } else {
            Integer original = originalVisibility.remove(date);
            if (original != null && date.getVisibility() == View.GONE) date.setVisibility(original);
        }
    }

    static boolean isDate(String entry) {
        return entry != null && DATE_TEXT_ENTRIES.contains(entry);
    }

    static String entryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return null;
        }
    }
}
