package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.reflect.Proxy;

final class WidgetPanel {
    private final Context context;
    private final ClassLoader loader;
    private final EditorWidgets editor;
    private final LinearLayout content;
    private PanelTabs pages;
    private int widgetPage;
    private int extrasPage;
    private LinearLayout group;
    private boolean binding;
    private TextView status;
    private boolean extrasSelected;
    private int renderedScreen;

    interface Choice {
        void change(Bundle b, int value) throws Exception;
    }

    interface FloatChoice {
        void change(Bundle b, float value) throws Exception;
    }

    WidgetPanel(Context context, ClassLoader loader, EditorWidgets editor) {
        this.context = context;
        this.loader = loader;
        this.editor = editor;
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        PanelKeyboard.attach(content);
    }

    private View buildTabs() throws Exception {
        View tabs = nativeView(EditorBinding.Row.TABS);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(52));
        layout.setMarginStart(dp(24));
        layout.setMarginEnd(dp(24));
        tabs.setLayoutParams(layout);
        NativeTabs nativeTabs = NativeTabs.of(tabs);
        nativeTabs.add(tabs, str(R.string.common_widgets), true);
        nativeTabs.add(tabs, str(R.string.common_extras), false);
        nativeTabs.applyClockStyle(context, tabs, true);
        Class<?> listener = nativeTabs.listener;
        Object events =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                if (method.getName().equals("equals")) return proxy == args[0];
                                if (method.getName().equals("hashCode"))
                                    return System.identityHashCode(proxy);
                                return "Advanced panel tabs";
                            }
                            if (method.getName().equals("onTabSelected")
                                    || method.getName().equals("onTabReselected")) {
                                extrasSelected = nativeTabs.position(args[0]) == 1;
                                try {
                                    render();
                                } catch (Exception e) {
                                    error(e);
                                }
                            }
                            return null;
                        });
        nativeTabs.listen(tabs, events);
        return tabs;
    }

    void showDialog(boolean widgetsAvailable) throws Exception {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        extrasSelected = !widgetsAvailable;
        if (widgetsAvailable) panel.addView(buildTabs());
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        render();
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle(
                        str(
                                widgetsAvailable
                                        ? R.string.common_widgets_and_extras
                                        : R.string.common_extras))
                .setView(panel)
                .setPositiveButton(str(R.string.common_done), null)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    void show(Object ui) throws Exception {
        View tabs = buildTabs();
        render();
        java.lang.reflect.Method earlyShow = editor.earlyPanelShow();
        if (earlyShow != null) {
            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.addView(tabs);
            panel.addView(content, new LinearLayout.LayoutParams(-1, -2));
            earlyShow.invoke(ui, panel, null, null, dp(376), dp(376), null, false, null);
            return;
        }
        Object nativePanel = findNativePanel(ui);
        enableNestedScroll(nativePanel);
        NativeContract.method(
                        nativePanel.getClass(), "setAnchorStateSupported", void.class, boolean.class)
                .invoke(nativePanel, false);
        java.lang.reflect.Method showMethod = findShowMethod(ui.getClass());
        Class<?> menuType = showMethod.getParameterTypes()[6];
        Object type = WidgetEngine.enumValue(menuType, "WIDGET");
        showMethod.invoke(
                ui,
                content,
                tabs,
                null,
                dp(376),
                dp(376),
                null,
                type,
                null,
                false,
                null);
    }

    private static java.lang.reflect.Method findShowMethod(Class<?> uiClass)
            throws NoSuchMethodException {
        java.lang.reflect.Method found = null;
        for (java.lang.reflect.Method m : uiClass.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getReturnType() == void.class
                    && params.length == 10
                    && params[6].isEnum()
                    && params[7] == android.graphics.Rect.class
                    && params[8] == boolean.class) {
                if (found != null) {
                    throw new NoSuchMethodException(
                            "Ambiguous panel show methods on " + uiClass.getName());
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("Missing panel show method on " + uiClass.getName());
        }
        found.setAccessible(true);
        return found;
    }

    private static Object findNativePanel(Object ui) throws ReflectiveOperationException {
        Object found = null;
        try {
            found = panelFromMediator(Reflect.field(ui, "a"), "c");
        } catch (ReflectiveOperationException ignored) {
        }
        if (found != null) return found;
        for (java.lang.reflect.Field f : ui.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object mediator = f.get(ui);
            if (mediator == null || mediator instanceof Context) continue;
            Object candidate = panelFromMediator(mediator, null);
            if (candidate != null) {
                if (found != null && found != candidate) {
                    throw new NoSuchFieldException(
                            "Ambiguous panel views on " + ui.getClass().getName());
                }
                found = candidate;
            }
        }
        if (found == null) {
            throw new NoSuchFieldException("Missing panel view on " + ui.getClass().getName());
        }
        return found;
    }

    private static Object panelFromMediator(Object mediator, String preferredField)
            throws ReflectiveOperationException {
        if (mediator == null) return null;
        if (preferredField != null) {
            try {
                java.lang.reflect.Field f = mediator.getClass().getDeclaredField(preferredField);
                f.setAccessible(true);
                Object v = f.get(mediator);
                if (isNativePanel(v)) return v;
            } catch (NoSuchFieldException ignored) {
            }
        }
        Object found = null;
        for (java.lang.reflect.Field f : mediator.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!android.view.ViewGroup.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object v = f.get(mediator);
            if (isNativePanel(v)) {
                if (found != null && found != v) {
                    throw new NoSuchFieldException(
                            "Ambiguous panel fields on " + mediator.getClass().getName());
                }
                found = v;
            }
        }
        return found;
    }

    private static boolean isNativePanel(Object view) {
        if (!(view instanceof android.view.ViewGroup)) return false;
        Class<?> type = view.getClass();
        return hasDeclaredMethod(type, "setAnchorStateSupported", boolean.class)
                && hasDeclaredMethod(type, "setInnerFooterView", View.class);
    }

    private static boolean hasDeclaredMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            owner.getDeclaredMethod(name, params);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private void enableNestedScroll(Object nativePanel) throws ReflectiveOperationException {
        Class<?> panelClass = nativePanel.getClass();
        java.lang.reflect.Method footer =
                NativeContract.method(panelClass, "setInnerFooterView", void.class, View.class);
        try {
            DexSignatures.Member field =
                    DexSignatures.body(footer)
                            .uniqueRead(
                                    "L" + panelClass.getName().replace('.', '/') + ";", "Z", null);
            field.field(loader).setBoolean(nativePanel, true);
            return;
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError ignored) {
        }
        NativeContract.field(panelClass, editor.legacy() ? "j" : "h", boolean.class)
                .setBoolean(nativePanel, true);
    }

    private Bundle draft() {
        return extrasSelected
                ? editor.extras().settings(context)
                : new Bundle(editor.settings(context));
    }

    private void render() throws Exception {
        editor.cancelScalePreview();
        binding = true;
        content.removeAllViews();
        group = content;
        Bundle b = draft();
        renderedScreen = ScreenIdentity.current();
        if (extrasSelected) {
            renderExtras(b);
            binding = false;
            content.requestLayout();
            return;
        }
        pages =
                new PanelTabs(
                        context,
                        !editor.scaleAvailable()
                                ? new String[] {
                                    str(R.string.widget_tab_position),
                                    str(R.string.common_effects)
                                }
                                : new String[] {
                                    str(R.string.common_layout),
                                    str(R.string.widget_tab_position),
                                    str(R.string.common_effects)
                                },
                        widgetPage,
                        dp(16),
                        page -> {
                            widgetPage = page;
                            content.requestLayout();
                        });
        content.addView(pages.strip());
        if (editor.scaleAvailable()) renderWidgetSizing(b);
        renderWidgetPlacement(b);
    }

    private void renderWidgetSizing(Bundle b) throws Exception {
        section(0, R.string.widget_section_layout_size);
        if (editor.customGridAvailable()) {
            toggle(
                    str(R.string.widget_custom_grid),
                    b.getBoolean("layout"),
                    next -> {
                        next.putBoolean("layout", !b.getBoolean("layout"));
                        if (!next.getBoolean("layout")) {
                            next.putInt("columns", 4);
                            next.putInt("rows", 1);
                            next.putBoolean("vertical", false);
                        }
                    },
                    true);
            if (b.getBoolean("layout")) {
                if (!editor.nativeRotationAvailable()) {
                    String orientation =
                            str(
                                    editor.vertical()
                                            ? R.string.widget_orientation_vertical
                                            : R.string.widget_orientation_horizontal);
                    TextView rotate = label(str(R.string.widget_rotate_arrangement));
                    PanelRows.action(rotate, str(R.string.widget_rotate_arrangement), orientation);
                    rotate.setOnClickListener(
                            v -> {
                                try {
                                    editor.rotate();
                                    content.postDelayed(
                                            () -> {
                                                try {
                                                    render();
                                                } catch (Exception e) {
                                                    error(e);
                                                }
                                            },
                                            500);
                                } catch (Exception e) {
                                    error(e);
                                }
                            });
                } else note(str(R.string.widget_rotate_native_note));
                slider(
                        str(R.string.widget_columns),
                        1,
                        8,
                        b.getInt("columns"),
                        "",
                        (next, value) -> next.putInt("columns", value),
                        true);
                slider(
                        str(R.string.widget_rows),
                        1,
                        Math.min(8, 32 / b.getInt("columns")),
                        b.getInt("rows"),
                        "",
                        (next, value) -> next.putInt("rows", value),
                        true);
                note(str(R.string.widget_grid_capacity, b.getInt("columns"), b.getInt("rows")));
            }
        } else if (!editor.earlyClockEditor()) {
            note(str(R.string.widget_custom_grid_unavailable));
        }
        slider(
                str(R.string.widget_scale),
                50,
                200,
                Math.round(b.getFloat("scale", 1) * 100),
                "%",
                (next, value) -> next.putFloat("scale", value / 100f),
                false,
                true);
    }

    private void renderWidgetPlacement(Bundle b) throws Exception {
        section(1, R.string.widget_section_placement);
        choice(
                str(R.string.widget_attach_to_clock),
                new String[] {
                    str(R.string.common_samsung_default),
                    str(R.string.widget_attach_below),
                    str(R.string.widget_attach_above),
                    str(R.string.widget_attach_left),
                    str(R.string.widget_attach_right),
                    str(R.string.widget_attach_free)
                },
                b.getInt("anchor"),
                (next, index) -> next.putInt("anchor", index),
                false);
        int anchor = b.getInt("anchor");
        note(str(R.string.widget_placement_drag_note));
        if (anchor >= 1 && anchor <= 4) {
            slider(
                    str(R.string.widget_gap_from_clock),
                    0,
                    64,
                    Math.round(b.getFloat("gap", 8)),
                    " dp",
                    (next, value) -> next.putFloat("gap", value),
                    false);
            slider(
                    str(R.string.widget_along_clock_edge),
                    -600,
                    600,
                    Math.round(b.getFloat("dockOffset")),
                    " dp",
                    (next, value) -> next.putFloat("dockOffset", value),
                    false);
        }
        if (anchor == 5) {
            slider(
                    str(R.string.widget_horizontal_position),
                    0,
                    100,
                    Math.round(b.getFloat("freeX", .5f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeX", value / 100f),
                    false);
            slider(
                    str(R.string.widget_vertical_position),
                    0,
                    100,
                    Math.round(b.getFloat("freeY", .4f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeY", value / 100f),
                    false);
        }
        if (editor.depthAvailable()) {
            section(2, R.string.common_appearance);
            boolean hardwareAvailable = editor.depthHardwareAvailable();
            boolean imageAvailable = hardwareAvailable && editor.depthImageAvailable();
            int mode = b.getInt("depth");
            choice(
                    str(R.string.widget_subject_depth),
                    new String[] {
                        str(R.string.widget_depth_follow_clock),
                        str(R.string.common_on),
                        str(R.string.common_off)
                    },
                    mode,
                    (next, index) -> next.putInt("depth", index),
                    false,
                    imageAvailable);
            toggle(
                    str(R.string.common_disable_depth_on_aod),
                    b.getBoolean("depthOffAod"),
                    imageAvailable && mode != 2,
                    next -> next.putBoolean("depthOffAod", !next.getBoolean("depthOffAod")),
                    false);
            choice(
                    str(R.string.widget_depth_applies_to),
                    new String[] {
                        str(R.string.widget_depth_separate_orientation),
                        str(R.string.widget_depth_portrait_only),
                        str(R.string.widget_depth_landscape_only),
                        str(R.string.widget_depth_both_orientations)
                    },
                    b.getInt("depthLayouts"),
                    (next, index) -> next.putInt("depthLayouts", index),
                    false,
                    imageAvailable);
            if (!hardwareAvailable) note(ObjectCaptureBackend.unavailableReason(context));
            else if (!imageAvailable) note(str(R.string.common_depth_requires_still_wallpaper));

            toggle(
                    str(R.string.widget_live_background_blur),
                    !b.getBoolean("blurOff"),
                    next -> next.putBoolean("blurOff", !b.getBoolean("blurOff")),
                    false);
        }
        section(0, 0);
        TextView reset = label(str(R.string.widget_reset_adjustments));
        PanelRows.action(reset, str(R.string.widget_reset_adjustments), "");
        reset.setOnClickListener(
                v -> {
                    try {
                        checkScreen();
                        editor.resetAdjustments();
                        render();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        if (!editor.earlyClockEditor()) {
            TextView wipe = label(str(R.string.widget_wipe_all));
            PanelRows.action(
                    wipe, str(R.string.widget_wipe_all), str(R.string.widget_wipe_all_summary));
            wipe.setOnClickListener(v -> confirmWipeWidgets());
        }
        group = content;
        status = label("");
        status.setVisibility(View.GONE);
        binding = false;
        content.requestLayout();
    }

    private void confirmWipeWidgets() {
        try {
            checkScreen();
            new android.app.AlertDialog.Builder(context)
                    .setTitle(str(R.string.widget_wipe_dialog_title))
                    .setMessage(str(R.string.widget_wipe_dialog_message))
                    .setNegativeButton(str(R.string.common_cancel), null)
                    .setPositiveButton(
                            str(R.string.widget_wipe_confirm),
                            (dialog, which) -> {
                                try {
                                    checkScreen();
                                    editor.wipeWidgets(context);
                                    render();
                                    setStatus(str(R.string.widget_wiped_status));
                                    android.widget.Toast.makeText(
                                                    context,
                                                    str(R.string.widget_wiped_toast),
                                                    android.widget.Toast.LENGTH_SHORT)
                                            .show();
                                } catch (Exception e) {
                                    error(e);
                                }
                            })
                    .show();
        } catch (Exception e) {
            error(e);
        }
    }

    interface Edit {
        void change(Bundle b) throws Exception;
    }

    private void renderExtras(Bundle b) throws Exception {
        boolean showAodTab = !editor.earlyClockEditor();
        String[] tabNames =
                showAodTab
                        ? new String[] {
                            str(R.string.common_now_bar),
                            str(R.string.extras_tab_media),
                            str(R.string.extras_tab_lock),
                            str(R.string.extras_tab_aod),
                            str(R.string.extras_tab_blur),
                            str(R.string.extras_tab_more)
                        }
                        : new String[] {
                            str(R.string.common_now_bar),
                            str(R.string.extras_tab_media),
                            str(R.string.extras_tab_lock),
                            str(R.string.extras_tab_blur),
                            str(R.string.extras_tab_more)
                        };
        int blurPage = showAodTab ? 4 : 3;
        int morePage = showAodTab ? 5 : 4;
        if (extrasPage >= tabNames.length) extrasPage = 0;
        PanelTabs extraPages =
                new PanelTabs(
                        context,
                        tabNames,
                        extrasPage,
                        dp(16),
                        page -> extrasPage = page);
        content.addView(extraPages.strip());
        if (ScreenIdentity.value(b) != 0) {
            group = content;
            label(
                    str(
                            ScreenIdentity.value(b) == ScreenIdentity.SUB
                                    ? R.string.extras_fold_cover_screen
                                    : R.string.extras_fold_inner_screen));
        }

        // Tab 0: Now Bar (DIY Studio, Labs, stack & cards)
        group = extraPages.group(content, 0);
        PanelRows.heading(label(str(R.string.extras_section_diy_nowbar_labs)));
        TextView manageDiy = label(str(R.string.extras_manage_diy_cards_labs));
        PanelRows.action(manageDiy, str(R.string.extras_manage_diy_cards_labs), diyNowBarSummary());
        manageDiy.setOnClickListener(
                v -> {
                    try {
                        android.content.Intent studio =
                                new android.content.Intent()
                                        .setClassName(
                                                "dev.denver.dressroomadvanced",
                                                "dev.denver.dressroomadvanced.NowBarStudioActivity")
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(studio);
                    } catch (RuntimeException error) {
                        error(error);
                    }
                });
        note(str(R.string.extras_diy_nowbar_note));

        PanelRows.heading(label(str(R.string.extras_section_nowbar_stack)));
        toggle(
                str(R.string.extras_keep_charging_card),
                b.getBoolean("keepCharging"),
                next -> next.putBoolean("keepCharging", !b.getBoolean("keepCharging")),
                false);
        if (showAodTab) {
            boolean nowBarAod = NowBarAodOption.shownInEditor(context, b);
            toggle(
                    str(R.string.extras_show_nowbar_on_aod),
                    nowBarAod,
                    next -> NowBarAodOption.setInEditor(next, !nowBarAod),
                    false);
        }
        if (editor.stackAvailable()) {
            slider(
                    str(R.string.extras_visible_cards),
                    3,
                    8,
                    b.getInt("stackCards", 3),
                    "",
                    (next, value) -> next.putInt("stackCards", value),
                    false);
        }

        // Tab 1: Media (Album artwork, expanded player & lyrics)
        group = extraPages.group(content, 1);
        PanelRows.heading(label(str(R.string.extras_section_album_artwork)));
        extraToggle(b, str(R.string.extras_fullscreen_album_art), "fullscreenArt");
        if (b.getBoolean("fullscreenArt")) {
            extraToggle(b, str(R.string.extras_live_animated_art), LiveCoverController.KEY);
            note(str(R.string.extras_live_animated_art_note));
            if (b.getBoolean(LiveCoverController.KEY)) {
                boolean wifiOnly = b.getBoolean(LiveCoverController.WIFI_ONLY, true);
                toggle(
                        str(R.string.extras_live_art_wifi_only),
                        wifiOnly,
                        next -> next.putBoolean(LiveCoverController.WIFI_ONLY, !wifiOnly),
                        false);
            }
        }

        PanelRows.heading(label(str(R.string.extras_section_expanded_player)));
        extraToggle(b, str(R.string.extras_hide_clock_expanded_player), MediaClockVisibility.KEY);
        if (showAodTab) {
            extraToggle(b, str(R.string.extras_fade_widgets_player), MediaWidgetFade.KEY);
        }

        PanelRows.heading(label(str(R.string.extras_section_lyrics)));
        boolean lyricsEnabled = b.getBoolean("lockscreenLyrics", true);
        toggle(
                str(R.string.extras_lockscreen_lyrics),
                lyricsEnabled,
                next -> next.putBoolean("lockscreenLyrics", !lyricsEnabled),
                false);
        note(str(R.string.extras_lockscreen_lyrics_note));
        if (lyricsEnabled) {
            boolean syncedHighlight = b.getBoolean("lyricsSyncedHighlight", true);
            toggle(
                    str(R.string.extras_lyrics_auto_scroll),
                    syncedHighlight,
                    next -> next.putBoolean("lyricsSyncedHighlight", !syncedHighlight),
                    false);
            TextView lyricsPreview = renderLyricsFontPreview(b);
            slider(
                    str(R.string.extras_lyrics_text_size),
                    16,
                    38,
                    b.getInt("lyricsTextSize", 28),
                    " sp",
                    (next, value) -> {
                        next.putInt("lyricsTextSize", value);
                        updateLyricsFontPreview(lyricsPreview, next);
                    },
                    false);
            renderLyricsFontSection(b, lyricsPreview);
            renderLyricsCacheSection();
        }

        // Tab 2: Lock screen (Clock, stretch physics & widgets)
        group = extraPages.group(content, 2);
        PanelRows.heading(label(str(R.string.common_clock)));
        boolean inlineSec = b.getBoolean(ClockInlineSeconds.KEY, true);
        toggle(
                str(R.string.extras_inline_clock_seconds),
                inlineSec,
                next -> next.putBoolean(ClockInlineSeconds.KEY, !inlineSec),
                false);
        toggle(
                str(R.string.extras_keep_seconds_open),
                b.getBoolean(ClockInlineSeconds.KEEP_OPEN),
                inlineSec,
                next ->
                        next.putBoolean(
                                ClockInlineSeconds.KEEP_OPEN,
                                !b.getBoolean(ClockInlineSeconds.KEEP_OPEN)),
                false);
        note(str(R.string.extras_inline_seconds_note));

        if (android.os.Build.VERSION.SDK_INT >= 37 || showAodTab) {
            PanelRows.heading(label(str(R.string.extras_section_clock_stretch)));
            if (android.os.Build.VERSION.SDK_INT >= 37) {
                extraToggle(b, str(R.string.extras_allow_more_stretch_shapes), StretchSuggestions.KEY);
            }
            if (showAodTab) {
                slider(
                        str(R.string.extras_stretch_speed),
                        0,
                        AodTweaks.MAX_SPEED_LEVEL,
                        AodTweaks.speed(b),
                        "",
                        (next, value) -> next.putInt(AodTweaks.STRETCH_SPEED, value),
                        false);
                slider(
                        str(R.string.extras_stretch_bounce),
                        0,
                        AodTweaks.MAX_BOUNCE,
                        AodTweaks.bounce(b),
                        "%",
                        (next, value) -> next.putInt(AodTweaks.STRETCH_BOUNCE, value),
                        false);
                note(str(R.string.extras_stretch_physics_note));
            }
        }

        PanelRows.heading(label(str(R.string.common_widgets)));
        extraToggle(b, str(R.string.extras_disable_widget_clicks), WidgetClickBlock.KEY);
        note(str(R.string.extras_disable_widget_clicks_note));
        if (showAodTab && BlurAppearance.liveWindowBlurAllowed(context)) {
            extraToggle(
                    b,
                    str(R.string.extras_remove_picture_frame_dimming),
                    WidgetBackgroundOptions.PICTURE_FRAME);
        }

        // Tab 3: AOD & StandBy Nightstand
        if (showAodTab) {
            group = extraPages.group(content, 3);
            PanelRows.heading(label(str(R.string.extras_section_aod)));
            if (!editor.legacy()) {
                toggle(
                        str(R.string.extras_keep_clock_stretched_aod),
                        b.getBoolean("keepStretch"),
                        next -> next.putBoolean("keepStretch", !b.getBoolean("keepStretch")),
                        false);
            }
            extraToggle(b, str(R.string.extras_aod_erase_unlock), AodTweaks.ERASE_UNLOCK);
            note(str(R.string.extras_aod_erase_unlock_note));
            extraToggle(b, str(R.string.extras_aod_fullscreen_unlock), AodTweaks.FULLSCREEN_UNLOCK);
            note(str(R.string.extras_aod_fullscreen_unlock_note));
            extraToggle(b, str(R.string.extras_aod_auto_unlock), AodTweaks.AUTO_AOD_UNLOCK);
            note(str(R.string.extras_aod_auto_unlock_note));

            PanelRows.heading(label(str(R.string.extras_section_standby)));
            boolean lowLightClock = b.getBoolean(AodTweaks.LOW_LIGHT_CLOCK);
            extraToggle(b, str(R.string.extras_low_light_clock), AodTweaks.LOW_LIGHT_CLOCK);
            if (lowLightClock) {
                choice(
                        str(R.string.extras_standby_color_theme),
                        AodTweaks.lowLightThemeLabels(context),
                        AodTweaks.lowLightTheme(b),
                        (next, index) -> next.putInt(AodTweaks.LOW_LIGHT_THEME, index),
                        false);
                choice(
                        str(R.string.extras_standby_clock_style),
                        AodTweaks.lowLightFontLabels(context),
                        AodTweaks.lowLightFont(b),
                        (next, index) -> next.putInt(AodTweaks.LOW_LIGHT_FONT, index),
                        false);
                boolean showDate = b.getBoolean(AodTweaks.LOW_LIGHT_SHOW_DATE, true);
                toggle(
                        str(R.string.clock_show_date),
                        showDate,
                        next -> next.putBoolean(AodTweaks.LOW_LIGHT_SHOW_DATE, !showDate),
                        false);
                boolean burnIn = b.getBoolean(AodTweaks.LOW_LIGHT_BURN_IN, true);
                toggle(
                        str(R.string.extras_standby_burn_in_drift),
                        burnIn,
                        next -> next.putBoolean(AodTweaks.LOW_LIGHT_BURN_IN, !burnIn),
                        false);
                slider(
                        str(R.string.extras_standby_clock_brightness),
                        AodTweaks.MIN_LOW_LIGHT_BRIGHTNESS,
                        AodTweaks.MAX_LOW_LIGHT_BRIGHTNESS,
                        AodTweaks.lowLightBrightness(b),
                        "%",
                        (next, value) -> next.putInt(AodTweaks.LOW_LIGHT_BRIGHTNESS, value),
                        false);
                slider(
                        str(R.string.extras_standby_darkness_threshold),
                        AodTweaks.MIN_LOW_LIGHT_LUX,
                        AodTweaks.MAX_LOW_LIGHT_LUX,
                        AodTweaks.lowLightLux(b),
                        " lux",
                        (next, value) -> next.putInt(AodTweaks.LOW_LIGHT_LUX, value),
                        false);
                TextView previewDream = label(str(R.string.extras_standby_preview_now));
                PanelRows.action(
                        previewDream,
                        str(R.string.extras_standby_preview_now),
                        str(R.string.extras_standby_preview_now_summary));
                previewDream.setOnClickListener(
                        v -> {
                            try {
                                checkScreen();
                                context.getContentResolver()
                                        .call(
                                                SettingsProvider.URI,
                                                "previewLowLightClock",
                                                null,
                                                draft());
                            } catch (RuntimeException error) {
                                error(error);
                            }
                        });
                TextView dreamSettings = label(str(R.string.extras_standby_screen_saver_settings));
                PanelRows.action(
                        dreamSettings,
                        str(R.string.extras_standby_screen_saver_settings),
                        str(R.string.extras_standby_screen_saver_settings_summary));
                dreamSettings.setOnClickListener(
                        v -> {
                            try {
                                context.startActivity(
                                        new android.content.Intent(
                                                        android.provider.Settings
                                                                .ACTION_DREAM_SETTINGS)
                                                .addFlags(
                                                        android.content.Intent
                                                                .FLAG_ACTIVITY_NEW_TASK));
                            } catch (RuntimeException error) {
                                error(error);
                            }
                        });
            }
            note(str(R.string.extras_standby_note));
        }

        // Blur tab
        renderExtraBlur(b, extraPages, blurPage);

        // More tab (Updates & Reset)
        group = extraPages.group(content, morePage);
        renderUpdatesSection();
        PanelRows.heading(label(str(R.string.common_reset)));
        TextView reset = label(str(R.string.extras_reset_settings));
        PanelRows.action(
                reset,
                str(R.string.extras_reset_settings),
                str(R.string.extras_reset_settings_summary));
        reset.setOnClickListener(
                v -> {
                    try {
                        checkScreen();
                        new android.app.AlertDialog.Builder(context)
                                .setTitle(str(R.string.extras_reset_dialog_title))
                                .setMessage(str(R.string.extras_reset_dialog_message))
                                .setNegativeButton(str(R.string.common_cancel), null)
                                .setPositiveButton(
                                        str(R.string.common_reset),
                                        (d, which) -> {
                                            try {
                                                checkScreen();
                                                editor.extras()
                                                        .update(
                                                                context,
                                                                ExtrasPreferences.clean(null));
                                                render();
                                            } catch (Exception e) {
                                                error(e);
                                            }
                                        })
                                .show();
                    } catch (Exception e) {
                        error(e);
                    }
                });

        group = content;
        status = label("");
        status.setVisibility(View.GONE);
    }

    private TextView renderLyricsFontPreview(Bundle b) {
        TextView preview = label(str(R.string.extras_lyrics_preview));
        preview.setPadding(dp(4), dp(8), dp(4), dp(8));
        preview.setSingleLine(false);
        preview.setMaxLines(2);
        updateLyricsFontPreview(preview, b);
        return preview;
    }

    private void updateLyricsFontPreview(TextView preview, Bundle b) {
        if (preview == null || b == null) return;
        String fontFile = b.getString(ExtrasPreferences.LYRICS_FONT_FILE, "");
        Bundle axes = b.getBundle(ExtrasPreferences.LYRICS_FONT_AXES);
        int weight =
                b.getInt(
                        ExtrasPreferences.LYRICS_FONT_WEIGHT,
                        ExtrasPreferences.DEFAULT_LYRICS_FONT_WEIGHT);
        int textSizeSp = b.getInt("lyricsTextSize", 28);
        preview.setTypeface(VariableFonts.lyricsTypeface(context, fontFile, axes, weight));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15f, Math.min(28f, textSizeSp * 0.78f)));
    }

    private void renderLyricsFontSection(Bundle b, TextView preview) throws Exception {
        String fontFile = b.getString(ExtrasPreferences.LYRICS_FONT_FILE, "");
        String fontFileName = b.getString(ExtrasPreferences.LYRICS_FONT_FILE_NAME, "");
        Bundle fontAxesBundle = FontAxisSettings.clean(b.getBundle(ExtrasPreferences.LYRICS_FONT_AXES));

        TextView chooseFont = label(str(R.string.extras_lyrics_choose_font));
        PanelRows.action(
                chooseFont,
                str(R.string.extras_lyrics_choose_font),
                fontFile.isEmpty() ? str(R.string.common_font_file_types) : fontFileName);
        chooseFont.setOnClickListener(v -> pickLyricsFont());

        if (!fontFile.isEmpty()) {
            TextView useDefault = label(str(R.string.extras_lyrics_default_font));
            PanelRows.action(useDefault, str(R.string.extras_lyrics_default_font), "");
            useDefault.setOnClickListener(
                    v ->
                            change(
                                    next -> {
                                        next.putString(ExtrasPreferences.LYRICS_FONT_FILE, "");
                                        next.putString(ExtrasPreferences.LYRICS_FONT_FILE_NAME, "");
                                        next.putBundle(ExtrasPreferences.LYRICS_FONT_AXES, new Bundle());
                                    },
                                    false,
                                    true));

            VariableFonts.Axes axes = VariableFonts.importedAxes(context, fontFile);
            if (axes != null && axes.hasAny()) {
                for (FontAxes.Axis axis : axes.values) {
                    if (axis.max() <= axis.min()) continue;
                    float current =
                            axis.clamp(
                                    fontAxesBundle.containsKey(axis.tag())
                                            ? fontAxesBundle.getFloat(axis.tag())
                                            : axis.defaultValue());
                    if (axis.tag().equals("ital") && axis.min() == 0 && axis.max() == 1) {
                        boolean italic = current >= 0.5f;
                        toggle(
                                axis.label(context),
                                italic,
                                next -> {
                                    Bundle updated =
                                            FontAxisSettings.clean(
                                                    next.getBundle(ExtrasPreferences.LYRICS_FONT_AXES));
                                    updated.putFloat(axis.tag(), italic ? 0f : 1f);
                                    next.putBundle(ExtrasPreferences.LYRICS_FONT_AXES, updated);
                                    updateLyricsFontPreview(preview, next);
                                },
                                false);
                    } else {
                        floatSlider(
                                axis.label(context),
                                axis.min(),
                                axis.max(),
                                current,
                                1000,
                                (next, value) -> {
                                    Bundle updated =
                                            FontAxisSettings.clean(
                                                    next.getBundle(ExtrasPreferences.LYRICS_FONT_AXES));
                                    updated.putFloat(axis.tag(), axis.clamp(value));
                                    next.putBundle(ExtrasPreferences.LYRICS_FONT_AXES, updated);
                                    updateLyricsFontPreview(preview, next);
                                });
                    }
                }
            } else {
                note(str(R.string.common_font_no_adjustable_axes));
            }
        } else {
            slider(
                    str(R.string.extras_lyrics_font_weight),
                    100,
                    900,
                    b.getInt(
                            ExtrasPreferences.LYRICS_FONT_WEIGHT,
                            ExtrasPreferences.DEFAULT_LYRICS_FONT_WEIGHT),
                    "",
                    (next, value) -> {
                        next.putInt(ExtrasPreferences.LYRICS_FONT_WEIGHT, value);
                        updateLyricsFontPreview(preview, next);
                    },
                    false);
        }
    }

    private void pickLyricsFont() {
        int screen = renderedScreen;
        java.lang.ref.WeakReference<WidgetPanel> reference = new java.lang.ref.WeakReference<>(this);
        try {
            checkScreen();
            FontPicker.open(
                    context,
                    result -> {
                        WidgetPanel panel = reference.get();
                        if (panel == null
                                || !panel.content.isAttachedToWindow()
                                || screen != ScreenIdentity.current()) return;
                        String err = result.getString("error", "");
                        if (!err.isEmpty()) {
                            panel.setStatus(err);
                            return;
                        }
                        String file = result.getString("fontFile", "");
                        if (file.isEmpty()) return;
                        String name =
                                result.getString(
                                        "fontFileName", panel.str(R.string.common_custom_font));
                        Bundle defaultAxes = new Bundle();
                        VariableFonts.Axes importedAxes = VariableFonts.importedAxes(panel.context, file);
                        if (importedAxes != null) {
                            for (FontAxes.Axis axis : importedAxes.values) {
                                defaultAxes.putFloat(axis.tag(), axis.defaultValue());
                            }
                        }
                        panel.change(
                                next -> {
                                    next.putString(ExtrasPreferences.LYRICS_FONT_FILE, file);
                                    next.putString(ExtrasPreferences.LYRICS_FONT_FILE_NAME, name);
                                    next.putBundle(ExtrasPreferences.LYRICS_FONT_AXES, defaultAxes);
                                },
                                false,
                                true);
                    });
        } catch (RuntimeException error) {
            error(error);
        }
    }

    private static String formatAxisValue(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private void floatSlider(
            String name,
            float min,
            float max,
            float current,
            int steps,
            FloatChoice edit)
            throws Exception {
        int maxSteps = Math.max(1, steps);
        float span = max - min;
        float clamped = Math.max(min, Math.min(max, current));
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = label(name + "   " + formatAxisValue(clamped));
        View slider = nativeView(EditorBinding.Row.SLIDER);
        Reflect.call(slider, "setMax", maxSteps);
        int initialProgress = span > 0f ? Math.round(((clamped - min) / span) * maxSteps) : 0;
        Reflect.call(slider, "setProgress", Math.max(0, Math.min(maxSteps, initialProgress)));
        slider.setContentDescription(name);
        add(slider);
        PanelRows.slider(group, caption, slider);
        group = parent;
        Class<?> listener =
                Class.forName(
                        "androidx.appcompat.widget.SeslSeekBar$OnSeekBarChangeListener",
                        false,
                        loader);
        float[] pending = {clamped};
        Object proxy =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (p, m, args) -> {
                            if (m.getDeclaringClass() == Object.class) {
                                if (m.getName().equals("equals")) return p == args[0];
                                if (m.getName().equals("hashCode"))
                                    return System.identityHashCode(p);
                                return "Widget float slider";
                            }
                            if (m.getName().equals("onProgressChanged")
                                    && Boolean.TRUE.equals(args[2])) {
                                int pos = ((Number) args[1]).intValue();
                                float value = min + span * (pos / (float) maxSteps);
                                pending[0] = Math.max(min, Math.min(max, value));
                                caption.setText(name + "   " + formatAxisValue(pending[0]));
                                change(next -> edit.change(next, pending[0]), false, false);
                            }
                            if (m.getName().equals("onStopTrackingTouch")) {
                                change(next -> edit.change(next, pending[0]), false, false);
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", proxy);
    }

    private void renderLyricsCacheSection() {
        int count = 0;
        String formattedSize = "0 B";
        try {
            Bundle info = context.getContentResolver()
                    .call(SettingsProvider.URI, "getLyricsCacheInfo", null, null);
            if (info != null) {
                count = info.getInt("count", 0);
                formattedSize = info.getString("formattedSize", "0 B");
            }
        } catch (Exception ignored) {}

        TextView clearBtn = label(str(R.string.extras_lyrics_clear_cache));
        PanelRows.action(
                clearBtn,
                str(R.string.extras_lyrics_clear_cache),
                ModuleStrings.quantity(
                        context,
                        R.plurals.extras_lyrics_cache_summary,
                        count,
                        count == 1 ? "Cached: %1$d track (%2$s)" : "Cached: %1$d tracks (%2$s)",
                        count,
                        formattedSize));
        final String sizeForDialog = formattedSize;
        clearBtn.setOnClickListener(v -> {
            try {
                checkScreen();
                new android.app.AlertDialog.Builder(context)
                        .setTitle(str(R.string.extras_lyrics_clear_cache_title))
                        .setMessage(str(R.string.extras_lyrics_clear_cache_message, sizeForDialog))
                        .setNegativeButton(str(R.string.common_cancel), null)
                        .setPositiveButton(str(R.string.common_clear), (dialog, which) -> {
                            try {
                                checkScreen();
                                context.getContentResolver()
                                        .call(SettingsProvider.URI, "clearLyricsCache", null, null);
                                render();
                                setStatus(str(R.string.extras_lyrics_cache_cleared_status));
                                android.widget.Toast.makeText(
                                        context,
                                        str(R.string.extras_lyrics_cache_cleared_toast),
                                        android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                error(e);
                            }
                        })
                        .show();
            } catch (Exception e) {
                error(e);
            }
        });
    }

    private String diyNowBarSummary() {
        java.util.List<DiyNowBarCard> saved = DiyNowBarStore.read(context);
        if (saved.isEmpty()) return str(R.string.extras_diy_nowbar_empty_summary);
        int active = 0;
        for (DiyNowBarCard c : saved) if (c.enabled) active++;
        return str(R.string.extras_diy_nowbar_active_summary, active, saved.size());
    }

    private void renderUpdatesSection() {
        PanelRows.heading(label(str(R.string.extras_section_updates)));
        boolean prerelease = false;
        String installed = "";
        try {
            Bundle prefs =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, "getUpdatePreferences", null, null);
            if (prefs != null) {
                prerelease = prefs.getBoolean("prerelease");
                installed = prefs.getString("installed", "");
            }
        } catch (RuntimeException ignored) {
        }
        CompoundButton pre = (CompoundButton) nativeView(EditorBinding.Row.TOGGLE);
        pre.setText(str(R.string.extras_prerelease_updates));
        pre.setChecked(prerelease);
        add(pre);
        boolean[] updating = {false};
        pre.setOnCheckedChangeListener(
                (v, value) -> {
                    if (binding || updating[0]) return;
                    updating[0] = true;
                    try {
                        Bundle arg = new Bundle();
                        arg.putBoolean("prerelease", value);
                        context.getContentResolver()
                                .call(SettingsProvider.URI, "setPrereleaseUpdates", null, arg);
                    } catch (Exception e) {
                        pre.setChecked(!value);
                        error(e);
                    } finally {
                        updating[0] = false;
                    }
                });
        String baseNote = str(R.string.extras_prerelease_note);
        note(
                installed.isEmpty()
                        ? baseNote
                        : str(R.string.extras_prerelease_note_installed, installed, baseNote));
        TextView check = label(str(R.string.extras_check_for_updates));
        PanelRows.action(check, str(R.string.extras_check_for_updates), "");
        check.setOnClickListener(
                v -> {
                    android.app.Activity activity = findActivity(context);
                    if (activity == null) {
                        android.widget.Toast.makeText(
                                        context,
                                        str(R.string.extras_reopen_editor_updates),
                                        android.widget.Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    UpdatePrompt.checkNow(activity);
                });
    }

    private static android.app.Activity findActivity(Context ctx) {
        while (ctx instanceof android.content.ContextWrapper wrapper) {
            if (ctx instanceof android.app.Activity activity) return activity;
            ctx = wrapper.getBaseContext();
        }
        return null;
    }

    private void renderExtraBlur(Bundle b, PanelTabs extraPages, int pageIndex) throws Exception {
        group = extraPages.group(content, pageIndex);
        note(str(R.string.extras_blur_disclaimer));
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            toggle(str(R.string.extras_blur_keep_swipe_unlock), b.getBoolean("swipeBlur"),
                    next -> next.putBoolean("swipeBlur", !b.getBoolean("swipeBlur")), false);
            if (!BlurAppearance.available(context, "clockBlur"))
                group.getChildAt(group.getChildCount() - 1).setEnabled(false);
        }
        if (!editor.earlyClockEditor()) {
            blurSection(b, R.string.common_clock, R.string.extras_blur_customize_clock, "clockBlur");
        }
        blurSection(b, R.string.common_widgets, R.string.extras_blur_customize_widgets, "widgetBlur");
        blurSection(b, R.string.common_now_bar, R.string.extras_blur_customize_now_bar, "nowBarBlur");
        if (android.os.Build.VERSION.SDK_INT >= 37 && BlurAppearance.liveWindowBlurAllowed(context)) {
            PanelRows.heading(label(str(R.string.extras_section_lockscreen_notifications)));
            extraToggle(b, str(R.string.extras_blur_colorized_cards), NotificationBlurOptions.COLORIZED);
            extraToggle(b, str(R.string.extras_blur_custom_cards), NotificationBlurOptions.CUSTOM);
        }
    }

    private void blurSection(Bundle b, int titleRes, int customizeRes, String key) throws Exception {
        PanelRows.heading(label(str(titleRes)));
        boolean available = BlurAppearance.available(context, key);
        toggle(str(customizeRes),
                b.getBoolean(key + "Custom"),
                next -> next.putBoolean(key + "Custom", !b.getBoolean(key + "Custom")), false);
        if (!available) {
            group.getChildAt(group.getChildCount() - 1).setEnabled(false);
            note(str(R.string.extras_blur_unavailable));
            return;
        }
        if (key.equals("clockBlur")) {
            extraToggle(b, str(R.string.extras_blur_neutral_colors), BlurAppearanceSettings.NEUTRAL_CLOCK);
            if (android.os.Build.VERSION.SDK_INT >= 37) {
                extraToggle(b, str(R.string.extras_blur_shorter_return_delay), ClockBlurDelays.KEY);
                extraToggle(b, str(R.string.extras_blur_keep_with_lockstar), LockStarClockBlur.KEY);
                if (WallpaperAod.supported() && ScreenIdentity.value(b) != ScreenIdentity.SUB
                        && BlurAppearance.windowBlur(context)) {
                    extraToggle(b, str(R.string.extras_blur_keep_clock_aod), WallpaperAod.CLOCK_BLUR);
                    note(str(R.string.extras_blur_applies_wallpaper_aod));
                }
            }
        }
        if (key.equals("nowBarBlur") && android.os.Build.VERSION.SDK_INT >= 37
                && BlurAppearance.liveWindowBlurAllowed(context))
            extraToggle(b, str(R.string.extras_blur_glass_colored_pills), NowBarGlass.KEY);
        if (key.equals("widgetBlur") && !editor.earlyClockEditor()
                && BlurAppearance.liveWindowBlurAllowed(context)) {
            extraToggle(b, str(R.string.extras_blur_behind_camera_widget), WidgetBackgroundOptions.CAMERA);
        }
        if (b.getBoolean(key + "Custom")) blurControls(b, key);
        if (key.equals("widgetBlur") && !editor.earlyClockEditor() && WallpaperAod.fullscreenSupported()
                && BlurAppearance.windowBlur(context)) {
            PanelRows.heading(label(str(R.string.extras_section_widgets_on_aod)));
            note(str(R.string.extras_widgets_aod_apply_note));
            extraToggle(b, str(R.string.extras_keep_widget_blur_colors_aod), AodWidgetAppearance.KEY);
            String aod = AodWidgetAppearance.PROFILE;
            extraToggle(b, str(R.string.extras_customize_aod_widget_blur), aod + "Custom");
            if (b.getBoolean(aod + "Custom")) {
                extraToggle(b, str(R.string.extras_blur_neutral_colors), AodWidgetAppearance.NEUTRAL);
                blurControls(b, aod);
            }
        }
    }

    private void blurControls(Bundle b, String key) throws Exception {
        boolean aod = key.equals(AodWidgetAppearance.PROFILE);
        int defaultRadius = aod ? 110 : 200;
        slider(str(R.string.extras_blur_radius), 1, 300, b.getInt(key + "Radius", defaultRadius), " px",
                (next, value) -> next.putInt(key + "Radius", value), false);
        slider(str(R.string.extras_blur_saturation), -100, 100, b.getInt(key + "Saturation"), "%",
                (next, value) -> next.putInt(key + "Saturation", value), false);
        slider(str(R.string.extras_blur_tone_curve), -100, 100, b.getInt(key + "Contrast"), "",
                (next, value) -> next.putInt(key + "Contrast", value), false);
        slider(str(R.string.extras_blur_brightness), -100, 100, b.getInt(key + "Brightness"), "%",
                (next, value) -> next.putInt(key + "Brightness", value), false);
        if (!BlurAppearance.windowBlur(context)) {
            note(str(R.string.extras_blur_tint_window_only));
            return;
        }
        String[] tints =
                aod
                        ? new String[] {
                            str(R.string.common_samsung_default),
                            str(R.string.extras_blur_tint_dark),
                            str(R.string.extras_blur_tint_light),
                            str(R.string.common_none)
                        }
                        : new String[] {
                            str(R.string.common_samsung_default),
                            str(R.string.extras_blur_tint_dark),
                            str(R.string.extras_blur_tint_light)
                        };
        int tint = b.getInt(key + "Tint");
        choice(str(R.string.extras_blur_tint), tints, tint,
                (next, value) -> next.putInt(key + "Tint", value), false);
        if (aod && tint != 0) note(str(R.string.extras_blur_tint_aod_note));
        if (key.equals("clockBlur")) note(str(R.string.extras_blur_tint_clock_note));
        if (tint == 1 || tint == 2)
            slider(str(R.string.extras_blur_tint_opacity), 0, 100, b.getInt(key + "TintOpacity", 20), "%",
                    (next, value) -> next.putInt(key + "TintOpacity", value), false);
    }

    private void extraToggle(Bundle settings, String title, String key) throws Exception {
        toggle(title, settings.getBoolean(key), next -> next.putBoolean(key, !settings.getBoolean(key)), false);
    }

    private void text(String caption, String key, String value, int limit) {
        PanelRows.heading(label(caption));
        android.widget.EditText input = new android.widget.EditText(context);
        input.setText(value);
        input.setHint(caption);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(limit)});
        input.setContentDescription(caption);
        add(input);
        input.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        change(b -> b.putString(key, s.toString()), false, false);
                    }

                    public void afterTextChanged(android.text.Editable e) {}
                });
    }

    private void change(Edit edit, boolean structural, boolean rebuild) {
        if (binding) return;
        try {
            checkScreen();
            Bundle next = draft();
            edit.change(next);
            if (extrasSelected) {
                editor.extras().update(context, next);
            } else editor.update(next, structural);
            if (rebuild)
                content.post(
                        () -> {
                            try {
                                render();
                            } catch (Exception e) {
                                error(e);
                            }
                        });

        } catch (Exception e) {
            error(e);
            content.post(
                    () -> {
                        try {
                            render();
                            setStatus(e.getMessage());
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private void checkScreen() {
        if (renderedScreen != ScreenIdentity.current())
            throw new IllegalStateException(str(R.string.editor_error_reopen_after_screen_switch));
    }

    private void toggle(String name, boolean checked, Edit edit, boolean structural)
            throws Exception {
        toggle(name, checked, true, edit, structural);
    }

    private void toggle(
            String name, boolean checked, boolean enabled, Edit edit, boolean structural)
            throws Exception {
        CompoundButton toggle = (CompoundButton) nativeView(EditorBinding.Row.TOGGLE);
        toggle.setText(name);
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        add(toggle);
        toggle.setOnCheckedChangeListener((v, value) -> change(edit, structural, true));
    }

    private void choice(
            String name, String[] entries, int selected, Choice edit, boolean structural) {
        choice(name, entries, selected, edit, structural, true);
    }

    private void choice(
            String name,
            String[] entries,
            int selected,
            Choice edit,
            boolean structural,
            boolean enabled) {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = label(name);
        PanelRows.heading(heading);
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected, false);
        spinner.setContentDescription(name);
        spinner.setMinimumHeight(dp(48));
        spinner.setEnabled(enabled);
        add(spinner);
        group = parent;
        spinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int index, long id) {
                        if (index != selected)
                            change(next -> edit.change(next, index), structural, true);
                    }
                });
    }

    private void slider(
            String name,
            int min,
            int max,
            int current,
            String suffix,
            Choice edit,
            boolean structural)
            throws Exception {
        slider(name, min, max, current, suffix, edit, structural, false);
    }

    private void slider(
            String name,
            int min,
            int max,
            int current,
            String suffix,
            Choice edit,
            boolean structural,
            boolean scalePreview)
            throws Exception {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = label(name + "   " + current + suffix);
        View slider = nativeView(EditorBinding.Row.SLIDER);
        Reflect.call(slider, "setMax", max - min);
        Reflect.call(slider, "setProgress", Math.max(0, Math.min(max - min, current - min)));
        slider.setContentDescription(name);
        add(slider);
        PanelRows.slider(group, caption, slider);
        group = parent;
        Class<?> listener =
                Class.forName(
                        "androidx.appcompat.widget.SeslSeekBar$OnSeekBarChangeListener",
                        false,
                        loader);
        int[] pending = {current};
        Object proxy =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (p, m, args) -> {
                            if (m.getDeclaringClass() == Object.class) {
                                if (m.getName().equals("equals")) return p == args[0];
                                if (m.getName().equals("hashCode"))
                                    return System.identityHashCode(p);
                                return "Widget slider";
                            }
                            if (m.getName().equals("onProgressChanged")
                                    && Boolean.TRUE.equals(args[2])) {
                                pending[0] = min + ((Number) args[1]).intValue();
                                caption.setText(name + "   " + pending[0] + suffix);
                                if (scalePreview) {
                                    try {
                                        editor.previewScale(pending[0] / 100f);
                                    } catch (Exception e) {
                                        error(e);
                                    }
                                }
                                if (!structural && !scalePreview)
                                    change(next -> edit.change(next, pending[0]), false, false);
                            }
                            if (m.getName().equals("onStopTrackingTouch")) {
                                if (scalePreview) editor.cancelScalePreview();
                                change(
                                        next -> edit.change(next, pending[0]),
                                        structural,
                                        structural);
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", proxy);
        if (scalePreview)
            slider.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {}

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                            editor.cancelScalePreview();
                        }
                    });
    }

    private void section(int page, int titleRes) {
        if (!editor.scaleAvailable()) page = Math.max(0, page - 1);
        boolean separated = pages.hasGroup(page);
        group = pages.group(content, page);
        int divider =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_item_divider",
                                "drawable",
                                "com.samsung.android.app.dressroom");
        if (separated && divider != 0) {
            View line = new View(context);
            android.graphics.drawable.Drawable background = context.getDrawable(divider);
            line.setBackground(background);
            group.addView(
                    line, PanelRows.divider(context, Math.max(1, background.getIntrinsicHeight())));
        }
        if (titleRes != 0) {
            TextView title = label(str(titleRes));
            PanelRows.heading(title);
        }
    }

    private String str(int resId) {
        return ModuleStrings.get(context, resId);
    }

    private String str(int resId, Object... args) {
        return ModuleStrings.get(context, resId, args);
    }

    private TextView label(String value) {
        TextView label = (TextView) nativeView(EditorBinding.Row.LABEL);
        label.setText(value);
        PanelRows.text(label, false);
        add(label);
        return label;
    }

    private void note(String value) {
        TextView v = label(value);
        PanelRows.text(v, true);
    }

    private void add(View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMarginStart(dp(16));
        p.setMarginEnd(dp(16));
        group.addView(v, p);
    }

    private View nativeView(EditorBinding.Row row) {
        String tag = editor.rowTag(row);
        int id =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_layout",
                                "layout",
                                "com.samsung.android.app.dressroom");
        try (XmlResourceParser xml = context.getResources().getLayout(id)) {
            while (xml.getEventType() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG
                        && tag.equals(
                                xml.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android", "tag"))) {
                    View v =
                            LayoutInflater.from(context)
                                    .createView(
                                            context,
                                            xml.getName(),
                                            null,
                                            android.util.Xml.asAttributeSet(xml));
                    v.setId(View.NO_ID);
                    v.setTag(null);
                    return v;
                }
                xml.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Native widget control unavailable: " + tag, e);
        }
        throw new IllegalStateException("Missing native control " + tag);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void setStatus(String message) {
        if (status == null) return;
        status.setText(message);
        status.setVisibility(message == null || message.isBlank() ? View.GONE : View.VISIBLE);
    }

    private void error(Exception e) {
        android.util.Log.e(DressroomModule.TAG, "Widget control failed", e);
        if (status != null) setStatus(e.getMessage());
        android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                .show();
    }
}
