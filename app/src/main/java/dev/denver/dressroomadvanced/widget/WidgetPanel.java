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
        nativeTabs.add(tabs, "Widgets", true);
        nativeTabs.add(tabs, "Extras", false);
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
                .setTitle(widgetsAvailable ? "Widgets and Extras" : "Extras")
                .setView(panel)
                .setPositiveButton("Done", null)
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
                                ? new String[] {"Position", "Effects"}
                                : new String[] {"Layout", "Position", "Effects"},
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
        section("Layout & size");
        if (editor.customGridAvailable()) {
            toggle(
                    "Custom grid",
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
                    TextView rotate =
                            label(
                                    "Arrangement: "
                                            + (editor.vertical() ? "Vertical" : "Horizontal")
                                            + "  •  Rotate");
                    PanelRows.action(
                            rotate,
                            "Rotate arrangement",
                            editor.vertical() ? "Vertical" : "Horizontal");
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
                } else note("Rotate using Samsung's layout control.");
                slider(
                        "Columns",
                        1,
                        8,
                        b.getInt("columns"),
                        "",
                        (next, value) -> next.putInt("columns", value),
                        true);
                slider(
                        "Rows",
                        1,
                        Math.min(8, 32 / b.getInt("columns")),
                        b.getInt("rows"),
                        "",
                        (next, value) -> next.putInt("rows", value),
                        true);
                note("Grid capacity: " + b.getInt("columns") + " × " + b.getInt("rows") + ".");
            }
        } else if (!editor.earlyClockEditor()) {
            note("Custom grid is unavailable for this editor/provider combination.");
        }
        slider(
                "Widget scale",
                50,
                200,
                Math.round(b.getFloat("scale", 1) * 100),
                "%",
                (next, value) -> next.putFloat("scale", value / 100f),
                false);
    }

    private void renderWidgetPlacement(Bundle b) throws Exception {
        section("Placement");
        choice(
                "Attach to clock",
                new String[] {
                    "Samsung default", "Below", "Above", "Left", "Right", "Free position"
                },
                b.getInt("anchor"),
                (next, index) -> next.putInt("anchor", index),
                false);
        int anchor = b.getInt("anchor");
        note("Drag in the preview. Attached groups move along the clock edge.");
        if (anchor >= 1 && anchor <= 4) {
            slider(
                    "Gap from clock",
                    0,
                    64,
                    Math.round(b.getFloat("gap", 8)),
                    " dp",
                    (next, value) -> next.putFloat("gap", value),
                    false);
            slider(
                    "Along clock edge",
                    -600,
                    600,
                    Math.round(b.getFloat("dockOffset")),
                    " dp",
                    (next, value) -> next.putFloat("dockOffset", value),
                    false);
        }
        if (anchor == 5) {
            slider(
                    "Horizontal position",
                    0,
                    100,
                    Math.round(b.getFloat("freeX", .5f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeX", value / 100f),
                    false);
            slider(
                    "Vertical position",
                    0,
                    100,
                    Math.round(b.getFloat("freeY", .4f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeY", value / 100f),
                    false);
        }
        if (editor.depthAvailable()) {
            section("Appearance");
            boolean hardwareAvailable = editor.depthHardwareAvailable();
            boolean imageAvailable = hardwareAvailable && editor.depthImageAvailable();
            int mode = b.getInt("depth");
            choice(
                    "Subject depth",
                    new String[] {"Follow clock", "On", "Off"},
                    mode,
                    (next, index) -> next.putInt("depth", index),
                    false,
                    imageAvailable);
            toggle(
                    "Disable depth on AOD",
                    b.getBoolean("depthOffAod"),
                    imageAvailable && mode != 2,
                    next -> next.putBoolean("depthOffAod", !next.getBoolean("depthOffAod")),
                    false);
            choice(
                    "Depth applies to",
                    new String[] {
                        "Separate per orientation",
                        "Portrait only",
                        "Landscape only",
                        "Both orientations"
                    },
                    b.getInt("depthLayouts"),
                    (next, index) -> next.putInt("depthLayouts", index),
                    false,
                    imageAvailable);
            if (!hardwareAvailable) note(ObjectCaptureBackend.unavailableReason());
            else if (!imageAvailable)
                note(
                        "Depth requires a still-image wallpaper. Gyro, video and live wallpapers"
                                + " are not supported.");

            toggle(
                    "Live background blur",
                    !b.getBoolean("blurOff"),
                    next -> next.putBoolean("blurOff", !b.getBoolean("blurOff")),
                    false);
        }
        section("Reset");
        TextView reset = label("Reset widget adjustments");
        PanelRows.action(reset, "Reset widget adjustments", "");
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
            TextView wipe = label("Wipe all widgets");
            PanelRows.action(
                    wipe, "Wipe all widgets", "Remove all lockscreen widgets and reset layout");
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
                    .setTitle("Wipe all widgets?")
                    .setMessage(
                            "Removes all lockscreen widgets, clears orphaned widget hosts, and"
                                + " resets widget layout adjustments immediately.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                            "Wipe",
                            (dialog, which) -> {
                                try {
                                    checkScreen();
                                    editor.wipeWidgets(context);
                                    render();
                                    setStatus("Widgets wiped.");
                                    android.widget.Toast.makeText(
                                                    context,
                                                    "All lockscreen widgets wiped.",
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
                        ? new String[] {"Now Bar", "Media", "Lock", "AOD", "Blur", "More"}
                        : new String[] {"Now Bar", "Media", "Lock", "Blur", "More"};
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
                    ScreenIdentity.value(b) == ScreenIdentity.SUB
                            ? "Settings for the cover screen"
                            : "Settings for the inner screen");
        }

        // Tab 0: Now Bar (DIY Studio, Labs, stack & cards)
        group = extraPages.group(content, 0);
        PanelRows.heading(label("DIY Now Bar & Labs"));
        TextView manageDiy = label("Manage DIY cards");
        PanelRows.action(manageDiy, "Manage DIY cards & Labs", diyNowBarSummary());
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
        note("Create custom cards, shaders, and Now Bar Labs unlocks (Sports, Finance, Commute, Buds).");

        PanelRows.heading(label("Now Bar stack"));
        toggle(
                "Keep charging card",
                b.getBoolean("keepCharging"),
                next -> next.putBoolean("keepCharging", !b.getBoolean("keepCharging")),
                false);
        if (showAodTab) {
            boolean nowBarAod = NowBarAodOption.shownInEditor(context, b);
            toggle(
                    "Show Now Bar on AOD",
                    nowBarAod,
                    next -> NowBarAodOption.setInEditor(next, !nowBarAod),
                    false);
        }
        if (editor.stackAvailable()) {
            slider(
                    "Visible cards",
                    3,
                    8,
                    b.getInt("stackCards", 3),
                    "",
                    (next, value) -> next.putInt("stackCards", value),
                    false);
        }

        // Tab 1: Media (Album artwork, expanded player & lyrics)
        group = extraPages.group(content, 1);
        PanelRows.heading(label("Album artwork"));
        extraToggle(b, "Full-screen album artwork", "fullscreenArt");
        if (b.getBoolean("fullscreenArt")) {
            extraToggle(b, "Live animated artwork", LiveCoverController.KEY);
            note("Uses Apple Music motion covers when available.");
            if (b.getBoolean(LiveCoverController.KEY)) {
                boolean wifiOnly = b.getBoolean(LiveCoverController.WIFI_ONLY, true);
                toggle(
                        "Only download on Wi-Fi",
                        wifiOnly,
                        next -> next.putBoolean(LiveCoverController.WIFI_ONLY, !wifiOnly),
                        false);
            }
        }

        PanelRows.heading(label("Expanded player"));
        extraToggle(b, "Hide clock in expanded player", MediaClockVisibility.KEY);
        if (showAodTab) {
            extraToggle(b, "Fade widgets when opening the player", MediaWidgetFade.KEY);
        }

        PanelRows.heading(label("Lyrics"));
        boolean lyricsEnabled = b.getBoolean("lockscreenLyrics", true);
        toggle(
                "Lockscreen lyrics",
                lyricsEnabled,
                next -> next.putBoolean("lockscreenLyrics", !lyricsEnabled),
                false);
        note("Displays synced or plain lyrics in the expanded media player.");
        if (lyricsEnabled) {
            boolean syncedHighlight = b.getBoolean("lyricsSyncedHighlight", true);
            toggle(
                    "Synced lyrics auto-scroll",
                    syncedHighlight,
                    next -> next.putBoolean("lyricsSyncedHighlight", !syncedHighlight),
                    false);
            TextView lyricsPreview = renderLyricsFontPreview(b);
            slider(
                    "Lyrics text size",
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
        PanelRows.heading(label("Clock"));
        boolean inlineSec = b.getBoolean(ClockInlineSeconds.KEY, true);
        toggle(
                "Inline clock seconds",
                inlineSec,
                next -> next.putBoolean(ClockInlineSeconds.KEY, !inlineSec),
                false);
        toggle(
                "Keep seconds open",
                b.getBoolean(ClockInlineSeconds.KEEP_OPEN),
                inlineSec,
                next ->
                        next.putBoolean(
                                ClockInlineSeconds.KEEP_OPEN,
                                !b.getBoolean(ClockInlineSeconds.KEEP_OPEN)),
                false);
        note("Replaces the full-screen seconds view when tapping a digital clock.");

        if (android.os.Build.VERSION.SDK_INT >= 37 || showAodTab) {
            PanelRows.heading(label("Clock stretch"));
            if (android.os.Build.VERSION.SDK_INT >= 37) {
                extraToggle(b, "Allow more stretch shapes", StretchSuggestions.KEY);
            }
            if (showAodTab) {
                slider(
                        "Stretch speed (0 = Samsung)",
                        0,
                        AodTweaks.MAX_SPEED_LEVEL,
                        AodTweaks.speed(b),
                        "",
                        (next, value) -> next.putInt(AodTweaks.STRETCH_SPEED, value),
                        false);
                slider(
                        "Stretch bounce",
                        0,
                        AodTweaks.MAX_BOUNCE,
                        AodTweaks.bounce(b),
                        "%",
                        (next, value) -> next.putInt(AodTweaks.STRETCH_BOUNCE, value),
                        false);
                note("Tunes Samsung's stretch physics. Applies the next time the clock is rebuilt.");
            }
        }

        PanelRows.heading(label("Widgets"));
        extraToggle(b, "Disable widget clicks", WidgetClickBlock.KEY);
        note("Prevents tapping lockscreen widgets from opening apps while keeping swipe-to-unlock working.");
        if (showAodTab && BlurAppearance.liveWindowBlurAllowed(context)) {
            extraToggle(b, "Remove Picture Frame dimming", WidgetBackgroundOptions.PICTURE_FRAME);
        }

        // Tab 3: AOD & StandBy Nightstand
        if (showAodTab) {
            group = extraPages.group(content, 3);
            PanelRows.heading(label("Always On Display"));
            if (!editor.legacy()) {
                toggle(
                        "Keep clock stretched on AOD",
                        b.getBoolean("keepStretch"),
                        next -> next.putBoolean("keepStretch", !b.getBoolean("keepStretch")),
                        false);
            }
            extraToggle(b, "Subject-only AOD for any subject", AodTweaks.ERASE_UNLOCK);
            note("Keeps the subject cutout Samsung rejects so Settings > AOD > Erase background works. Apply the wallpaper again after enabling.");
            extraToggle(b, "Fullscreen AOD on this device", AodTweaks.FULLSCREEN_UNLOCK);
            note("Reports fullscreen AOD support across SystemUI, AOD, and wallpaper. Restart SystemUI or reboot to apply.");
            extraToggle(b, "Unlock Auto AOD option", AodTweaks.AUTO_AOD_UNLOCK);
            note("Removes the device exclusion that hides Settings > AOD > When to show > Auto. Restart AOD/SystemUI or reboot to apply.");

            PanelRows.heading(label("StandBy nightstand"));
            boolean lowLightClock = b.getBoolean(AodTweaks.LOW_LIGHT_CLOCK);
            extraToggle(b, "Low-light bedside clock", AodTweaks.LOW_LIGHT_CLOCK);
            if (lowLightClock) {
                choice(
                        "Color theme",
                        AodTweaks.LOW_LIGHT_THEME_LABELS,
                        AodTweaks.lowLightTheme(b),
                        (next, index) -> next.putInt(AodTweaks.LOW_LIGHT_THEME, index),
                        false);
                choice(
                        "Clock style",
                        AodTweaks.LOW_LIGHT_FONT_LABELS,
                        AodTweaks.lowLightFont(b),
                        (next, index) -> next.putInt(AodTweaks.LOW_LIGHT_FONT, index),
                        false);
                boolean showDate = b.getBoolean(AodTweaks.LOW_LIGHT_SHOW_DATE, true);
                toggle(
                        "Show date",
                        showDate,
                        next -> next.putBoolean(AodTweaks.LOW_LIGHT_SHOW_DATE, !showDate),
                        false);
                boolean burnIn = b.getBoolean(AodTweaks.LOW_LIGHT_BURN_IN, true);
                toggle(
                        "OLED burn-in drift",
                        burnIn,
                        next -> next.putBoolean(AodTweaks.LOW_LIGHT_BURN_IN, !burnIn),
                        false);
                slider(
                        "Clock brightness",
                        AodTweaks.MIN_LOW_LIGHT_BRIGHTNESS,
                        AodTweaks.MAX_LOW_LIGHT_BRIGHTNESS,
                        AodTweaks.lowLightBrightness(b),
                        "%",
                        (next, value) -> next.putInt(AodTweaks.LOW_LIGHT_BRIGHTNESS, value),
                        false);
                slider(
                        "Darkness threshold",
                        AodTweaks.MIN_LOW_LIGHT_LUX,
                        AodTweaks.MAX_LOW_LIGHT_LUX,
                        AodTweaks.lowLightLux(b),
                        " lux",
                        (next, value) -> next.putInt(AodTweaks.LOW_LIGHT_LUX, value),
                        false);
                TextView previewDream = label("Preview bedside clock now");
                PanelRows.action(
                        previewDream,
                        "Preview bedside clock now",
                        "Launches the styled StandBy clock immediately (tap screen to exit)");
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
                TextView dreamSettings = label("Open Screen saver settings");
                PanelRows.action(
                        dreamSettings,
                        "Open Screen saver settings",
                        "Pick any screen saver (other than None) to enable while charging");
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
            note("Restyles SystemUI's hidden low-light dream into a StandBy nightstand clock while charging in dark rooms.");
        }

        // Blur tab
        renderExtraBlur(b, extraPages, blurPage);

        // More tab (Updates & Reset)
        group = extraPages.group(content, morePage);
        renderUpdatesSection();
        PanelRows.heading(label("Reset"));
        TextView reset = label("Reset extra settings");
        PanelRows.action(
                reset, "Reset extra settings", "Restore all Extras options to their defaults");
        reset.setOnClickListener(
                v -> {
                    try {
                        checkScreen();
                        new android.app.AlertDialog.Builder(context)
                                .setTitle("Reset extra settings?")
                                .setMessage("Restore all Extras options on this screen to defaults?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Reset",
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
        TextView preview = label("Synced lyrics preview");
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

        TextView chooseFont = label("Choose lyrics font");
        PanelRows.action(
                chooseFont,
                "Choose lyrics font",
                fontFile.isEmpty() ? "TTF, OTF or TTC" : fontFileName);
        chooseFont.setOnClickListener(v -> pickLyricsFont());

        if (!fontFile.isEmpty()) {
            TextView useDefault = label("Use default lyrics font");
            PanelRows.action(useDefault, "Use default lyrics font", "");
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
                                axis.label(),
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
                                axis.label(),
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
                note("This font has no adjustable axes.");
            }
        } else {
            slider(
                    "Font weight",
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
                        String name = result.getString("fontFileName", "Custom font");
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

        TextView clearBtn = label("Clear lyrics cache");
        PanelRows.action(clearBtn, "Clear lyrics cache", "Cached: " + count + " tracks (" + formattedSize + ")");
        final String sizeForDialog = formattedSize;
        clearBtn.setOnClickListener(v -> {
            try {
                checkScreen();
                new android.app.AlertDialog.Builder(context)
                        .setTitle("Clear lyrics cache?")
                        .setMessage("Delete all saved offline lyrics (" + sizeForDialog + ")?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (dialog, which) -> {
                            try {
                                checkScreen();
                                context.getContentResolver()
                                        .call(SettingsProvider.URI, "clearLyricsCache", null, null);
                                render();
                                setStatus("Lyrics cache cleared.");
                                android.widget.Toast.makeText(context, "Lyrics cache cleared", android.widget.Toast.LENGTH_SHORT).show();
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
        if (saved.isEmpty()) return "Tap to create custom Now Bar cards";
        int active = 0;
        for (DiyNowBarCard c : saved) if (c.enabled) active++;
        return active + " active / " + saved.size() + " saved";
    }

    private void renderUpdatesSection() {
        PanelRows.heading(label("Updates"));
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
        pre.setText("Pre-release updates");
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
        String baseNote =
                "Receive dev pre-release builds and newer stable releases. Defaults to on for dev"
                        + " builds.";
        note(installed.isEmpty() ? baseNote : "Installed: " + installed + ". " + baseNote);
        TextView check = label("Check for updates");
        PanelRows.action(check, "Check for updates", "");
        check.setOnClickListener(
                v -> {
                    android.app.Activity activity = findActivity(context);
                    if (activity == null) {
                        android.widget.Toast.makeText(
                                        context,
                                        "Reopen the editor to check for updates",
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
        note("Customizes existing blur. System blur restrictions still apply.");
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            toggle("Keep blur while swiping to unlock", b.getBoolean("swipeBlur"),
                    next -> next.putBoolean("swipeBlur", !b.getBoolean("swipeBlur")), false);
            if (!BlurAppearance.available(context, "clockBlur"))
                group.getChildAt(group.getChildCount() - 1).setEnabled(false);
        }
        if (!editor.earlyClockEditor()) blurSection(b, "Clock", "clockBlur");
        blurSection(b, "Widgets", "widgetBlur");
        blurSection(b, "Now Bar", "nowBarBlur");
        if (android.os.Build.VERSION.SDK_INT >= 37 && BlurAppearance.liveWindowBlurAllowed(context)) {
            PanelRows.heading(label("Lockscreen notifications"));
            extraToggle(b, "Blur behind colorized cards", NotificationBlurOptions.COLORIZED);
            extraToggle(b, "Blur behind custom cards", NotificationBlurOptions.CUSTOM);
        }
    }

    private void blurSection(Bundle b, String title, String key) throws Exception {
        PanelRows.heading(label(title));
        boolean available = BlurAppearance.available(context, key);
        toggle("Customize " + title.toLowerCase(java.util.Locale.ROOT) + " blur",
                b.getBoolean(key + "Custom"),
                next -> next.putBoolean(key + "Custom", !b.getBoolean(key + "Custom")), false);
        if (!available) {
            group.getChildAt(group.getChildCount() - 1).setEnabled(false);
            note("Blur is unavailable or disabled by the system.");
            return;
        }
        if (key.equals("clockBlur")) {
            extraToggle(b, "Neutral blur colors", BlurAppearanceSettings.NEUTRAL_CLOCK);
            if (android.os.Build.VERSION.SDK_INT >= 37) {
                extraToggle(b, "Shorter blur return delay", ClockBlurDelays.KEY);
                extraToggle(b, "Keep clock blur with LockStar", LockStarClockBlur.KEY);
                if (WallpaperAod.supported() && ScreenIdentity.value(b) != ScreenIdentity.SUB
                        && BlurAppearance.windowBlur(context)) {
                    extraToggle(b, "Keep clock blur on AOD", WallpaperAod.CLOCK_BLUR);
                    note("Applies when AOD shows your wallpaper.");
                }
            }
        }
        if (key.equals("nowBarBlur") && android.os.Build.VERSION.SDK_INT >= 37
                && BlurAppearance.liveWindowBlurAllowed(context))
            extraToggle(b, "Glass backgrounds for colored pills", NowBarGlass.KEY);
        if (key.equals("widgetBlur") && !editor.earlyClockEditor()
                && BlurAppearance.liveWindowBlurAllowed(context)) {
            extraToggle(b, "Blur behind the camera widget", WidgetBackgroundOptions.CAMERA);
        }
        if (b.getBoolean(key + "Custom")) blurControls(b, key);
        if (key.equals("widgetBlur") && !editor.earlyClockEditor() && WallpaperAod.fullscreenSupported()
                && BlurAppearance.windowBlur(context)) {
            PanelRows.heading(label("Widgets on AOD"));
            note("Apply, then enter AOD with your wallpaper showing to see changes.");
            extraToggle(b, "Keep widget blur colors on AOD", AodWidgetAppearance.KEY);
            String aod = AodWidgetAppearance.PROFILE;
            extraToggle(b, "Customize AOD widget blur", aod + "Custom");
            if (b.getBoolean(aod + "Custom")) {
                extraToggle(b, "Neutral blur colors", AodWidgetAppearance.NEUTRAL);
                blurControls(b, aod);
            }
        }
    }

    private void blurControls(Bundle b, String key) throws Exception {
        boolean aod = key.equals(AodWidgetAppearance.PROFILE);
        int defaultRadius = aod ? 110 : 200;
        slider("Radius", 1, 300, b.getInt(key + "Radius", defaultRadius), " px",
                (next, value) -> next.putInt(key + "Radius", value), false);
        slider("Saturation adjustment", -100, 100, b.getInt(key + "Saturation"), "%",
                (next, value) -> next.putInt(key + "Saturation", value), false);
        slider("Tone curve adjustment", -100, 100, b.getInt(key + "Contrast"), "",
                (next, value) -> next.putInt(key + "Contrast", value), false);
        slider("Brightness adjustment", -100, 100, b.getInt(key + "Brightness"), "%",
                (next, value) -> next.putInt(key + "Brightness", value), false);
        if (!BlurAppearance.windowBlur(context)) {
            note("Tint requires window blur. Only radius and saturation are available here.");
            return;
        }
        String[] tints = aod ? new String[] {"Samsung default", "Dark", "Light", "None"}
                : new String[] {"Samsung default", "Dark", "Light"};
        int tint = b.getInt(key + "Tint");
        choice("Tint", tints, tint,
                (next, value) -> next.putInt(key + "Tint", value), false);
        if (aod && tint != 0) note("Replaces Samsung's brightness preset. Opacity controls the tint over wallpaper blur.");
        if (key.equals("clockBlur")) note("Tint applies to window blur on the lockscreen.");
        if (tint == 1 || tint == 2)
            slider("Tint opacity", 0, 100, b.getInt(key + "TintOpacity", 20), "%",
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
            throw new IllegalStateException("Reopen Advanced after switching screens.");
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
                                if (name.equals("Widget scale")) {
                                    try {
                                        editor.previewScale(pending[0] / 100f);
                                    } catch (Exception e) {
                                        error(e);
                                    }
                                }
                                if (!structural && !name.equals("Widget scale"))
                                    change(next -> edit.change(next, pending[0]), false, false);
                            }
                            if (m.getName().equals("onStopTrackingTouch")) {
                                if (name.equals("Widget scale")) editor.cancelScalePreview();
                                change(
                                        next -> edit.change(next, pending[0]),
                                        structural,
                                        structural);
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", proxy);
        if (name.equals("Widget scale"))
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

    private void section(String name) {
        int page =
                switch (name) {
                    case "Placement" -> 1;
                    case "Appearance" -> 2;
                    default -> 0;
                };
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
        if (!name.equals("Reset")) {
            TextView title = label(name);
            PanelRows.heading(title);
        }
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
