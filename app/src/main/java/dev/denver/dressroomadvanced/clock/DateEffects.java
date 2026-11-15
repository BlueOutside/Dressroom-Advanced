package dev.denver.dressroomadvanced;

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
    private static final Map<View, Boolean> previews =
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

    private DateEffects() {}

    static void resetPreviews() {
        previews.clear();
        identities.clear();
    }

    static synchronized void attach(DressroomModule module, View clock) {
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
                                    return result;
                                });
                hooked.add(method);
            }
            if (tracked.add(clock)) {
                clock.addOnLayoutChangeListener(
                        (v, l, t, r, b, ol, ot, or, ob) -> updateDate(clock));
            }
            apply(clock);
        } catch (Exception | LinkageError e) {
            module.report("Date visibility unavailable for this clock", e);
        }
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

    static void preview(View clock, DressroomModule module, boolean hide) {
        previews.put(clock, hide);
        try {
            apply(clock);
        } catch (Exception e) {
            module.report("Date preview unavailable", e);
        }
    }

    static void bindIdentity(View clock, int type, int font, int orientation) {
        identities.put(clock, new int[] {type, font, orientation});
        apply(clock);
    }

    static void clearPreview(View clock) {
        previews.remove(clock);
        apply(clock);
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

    private static Boolean shouldHide(View clock) {
        Boolean preview = previews.get(clock);
        if (preview != null) return preview;
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
            Bundle saved = SettingsBridge.read(clock.getContext(), query);
            return saved != null && saved.getBoolean("hideDate");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void updateDate(View clock) {
        boolean hide = Boolean.TRUE.equals(shouldHide(clock));
        View[] rowPair = new View[2];
        applySubtree(clock, hide, rowPair);
        View weather = rowPair[0], dateRow = rowPair[1];
        if (dateRow != null && weather != null) {
            updateText(dateRow, hide && weather.getVisibility() != View.VISIBLE);
        }
    }

    private static void applySubtree(View view, boolean hide, View[] rowPair) {
        if (view == null) return;
        String entry = entryName(view);
        if (entry != null) {
            if (DATE_TEXT_ENTRIES.contains(entry)) {
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

    private static String entryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return null;
        }
    }
}
