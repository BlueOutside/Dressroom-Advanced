package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class NativeTabs {
    record Binding(Method create, Method text, Method add, Method listen, Method style,
            Field position, Class<?> listener) {}

    final Class<?> listener;
    final boolean early;
    private final Method create, text, add, listen, style, positionMethod;
    private final Field positionField;
    private static final ClassValue<NativeTabs> bindings =
            new ClassValue<>() {
                @Override
                protected NativeTabs computeValue(Class<?> type) {
                    try {
                        return new NativeTabs(type);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported native tabs", error);
                    }
                }
            };

    static NativeTabs of(Object strip) {
        return bindings.get(strip.getClass());
    }

    static NativeTabs forType(Class<?> strip) {
        return bindings.get(strip);
    }

    private NativeTabs(Class<?> strip) throws ReflectiveOperationException {
        ClassLoader loader = strip.getClassLoader();
        boolean older = false;
        boolean legacy;
        try {
            strip.getDeclaredMethod("g0");
            legacy = true;
        } catch (NoSuchMethodException ignored) {
            legacy = false;
        }
        if (legacy) {
            Class<?> tab =
                    Class.forName("com.google.android.material.tabs.TabLayout$g", false, loader);
            listener = Class.forName("com.google.android.material.tabs.TabLayout$d", false, loader);
            create = NativeContract.method(strip, "g0", tab);
            text = NativeContract.method(tab, "C", tab, CharSequence.class);
            add = NativeContract.method(strip, "D", void.class, tab, boolean.class);
            listen = NativeContract.method(strip, "A", void.class, listener);
            style = NativeContract.method(strip, "r0", void.class);
            positionMethod = NativeContract.method(tab, "o", int.class);
            positionField = null;
        } else if (hasReferenceFactory(strip)) {
            create = strip.getDeclaredMethod("p");
            create.setAccessible(true);
            Class<?> tab = create.getReturnType();
            listener = Class.forName("com.google.android.material.tabs.c", false, loader);
            text = NativeContract.method(tab, "b", void.class, CharSequence.class);
            add = NativeContract.method(strip, "f", void.class, tab, boolean.class);
            listen = NativeContract.method(strip, "d", void.class, listener);
            style = NativeContract.method(strip, "t", void.class);
            positionField = NativeContract.field(tab, "d", int.class);
            positionMethod = null;
        } else {
            Binding beta;
            try {
                beta = OneUi95Tabs.bind(strip);
            } catch (ReflectiveOperationException modern) {
                try {
                    beta = OneUi80Tabs.bind(strip);
                    older = true;
                } catch (ReflectiveOperationException unavailable) {
                    unavailable.addSuppressed(modern);
                    throw unavailable;
                }
            }
            create = beta.create();
            text = beta.text();
            add = beta.add();
            listen = beta.listen();
            style = beta.style();
            positionField = beta.position();
            listener = beta.listener();
            positionMethod = null;
        }
        early = older;
    }

    private static boolean hasReferenceFactory(Class<?> strip) {
        try {
            return strip.getDeclaredMethod("p").getReturnType().getPackageName()
                    .equals(strip.getPackageName());
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    void subTabs(Object strip) throws ReflectiveOperationException {
        style.invoke(strip);
    }

    void applyClockStyle(
            android.content.Context context, Object strip, boolean enableSubTab)
            throws ReflectiveOperationException {
        if (enableSubTab) subTabs(strip);
        android.content.res.Resources res = context.getResources();
        int unselectedId =
                res.getIdentifier(
                        "clock_panel_unselected_tab_text_color",
                        "color",
                        "com.samsung.android.app.dressroom");
        int selectedId =
                res.getIdentifier(
                        "clock_panel_selected_tab_text_color",
                        "color",
                        "com.samsung.android.app.dressroom");
        int indicatorId =
                res.getIdentifier(
                        "clock_panel_selected_tab_background_color",
                        "color",
                        "com.samsung.android.app.dressroom");
        if (unselectedId != 0 && selectedId != 0) {
            int unselected = context.getColor(unselectedId);
            int selected = context.getColor(selectedId);
            android.content.res.ColorStateList colors =
                    new android.content.res.ColorStateList(
                            new int[][] {
                                new int[] {android.R.attr.state_selected}, new int[0]
                            },
                            new int[] {selected, unselected});
            Reflect.call(strip, "setTabTextColors", colors);
        }
        if (indicatorId != 0) {
            Reflect.call(strip, "setSelectedTabIndicatorColor", context.getColor(indicatorId));
        }
    }

    void listen(Object strip, Object callback) throws ReflectiveOperationException {
        listen.invoke(strip, callback);
    }

    void add(Object strip, String name, boolean selected) throws ReflectiveOperationException {
        Object tab = create.invoke(strip);
        text.invoke(tab, name);
        add.invoke(strip, tab, selected);
    }

    int position(Object tab) throws ReflectiveOperationException {
        return positionField != null
                ? positionField.getInt(tab)
                : ((Number) positionMethod.invoke(tab)).intValue();
    }
}
