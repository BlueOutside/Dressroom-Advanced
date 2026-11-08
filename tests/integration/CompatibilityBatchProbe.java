package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Read-only compatibility audit. Each binding group runs even when another group fails. */
public final class CompatibilityBatchProbe {
    private interface Check {
        void run() throws Exception;
    }

    private static int passed, failed;

    private static void check(String label, Check action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS " + label);
        } catch (Throwable error) {
            failed++;
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            System.out.println("FAIL " + label + ": " + cause);
            StackTraceElement[] stack = cause.getStackTrace();
            for (int i = 0; i < Math.min(2, stack.length); i++) System.out.println("  " + stack[i]);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("editor.apk provider.apk editorVersionCode");
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
            ClassLoader parent = ClassLoader.getSystemClassLoader();
            ClassLoader editor = new PathClassLoader(args[0], parent);
            ClassLoader provider = new PathClassLoader(args[1], parent);
            boolean legacyEditor = Long.parseLong(args[2]) < 300000000L;
            check(
                    "editor clock panel",
                    () -> EditorBinding.resolve(editor, Long.parseLong(args[2])));
            check(
                    "editor native tabs",
                    () ->
                            NativeTabs.forType(
                                    Class.forName(
                                            "com.google.android.material.tabs.TabLayout",
                                            false,
                                            editor)));
            check("editor final save", () -> new EditorSaveCompletion(editor));
            check(
                    "editor wallpaper source",
                    () -> new WallpaperSourceBinding(editor, legacyEditor));
            check("editor depth SDK/crop", () -> DepthBinding.validateEditor(editor));
            check(
                    "editor configuration callback",
                    () ->
                            NativeContract.method(
                                    Class.forName(
                                            "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                                            false,
                                            editor),
                                    "onConfigurationChanged",
                                    void.class,
                                    android.content.res.Configuration.class));
            check(
                    "editor stretch setters",
                    () -> {
                        Class<?> type =
                                Class.forName(
                                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockStretchData",
                                        false,
                                        editor);
                        NativeContract.method(
                                type, "setFontHeight", void.class, java.util.List.class);
                        NativeContract.method(
                                type, "setFontHeightRects", void.class, java.util.List.class);
                    });
            if (legacyEditor) {
                check(
                        "editor widget toolbar/preview/metrics",
                        () -> new WidgetEditorBinding(editor));
                check(
                        "editor widget rotation/restore",
                        () -> {
                            WidgetEditorBinding widgets = new WidgetEditorBinding(editor);
                            new OneUi85WidgetLayout(editor, widgets.create.getDeclaringClass());
                        });
                check("editor widget resize adapter", () -> OneUi85WidgetEditor.adapter(editor));
                check(
                        "editor widget menu item",
                        () ->
                                NativeContract.findClass(
                                        editor,
                                        type ->
                                                NativeContract.hasConstructor(
                                                        type,
                                                        String.class,
                                                        int.class,
                                                        Runnable.class,
                                                        String.class)));
            } else {
                check("9.0 widget guide", () -> OneUi9WidgetEditor.guide(editor));
                check("9.0 widget adapter", () -> OneUi9WidgetEditor.adapter(editor));
            }
            Class<?> base =
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            provider);
            check("provider clock renderer/height/identity", () -> ClockBinding.of(base));
            check("provider font axes", () -> FontBinding.of(base));
            check(
                    "provider source/measurement callers",
                    () -> new ClockSourceBinding(provider, ClockBinding.of(base)));
            ClockBinding family = ClockBinding.of(base);
            if (!family.clockStyle) {
                check("provider native clock blur", () -> ClockBlurBinding.resolve(base));
                check(
                        "provider widget blur submissions",
                        () -> {
                            if (family.legacy) OneUi85AodBlur.submissions(provider);
                            else OneUi9AodBlur.submissions(provider);
                        });
            }
            check("provider widget host sizing", () -> WidgetHostSizing.validate(provider));
            check(
                    "provider grid refresh/options",
                    () ->
                            WidgetEngine.validateLegacyGrid(
                                    Class.forName(WidgetEngine.GRID, false, provider)));
            if (family.legacy) {
                check(
                        "provider widget lifecycle",
                        () ->
                                RuntimeWidgetHooks.legacyUpdates(
                                        Class.forName(
                                                "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController",
                                                false,
                                                provider)));
                check("provider media depth", () -> new OneUi85MediaBinding(base));
                check("provider full-screen artwork", () -> new OneUi85ArtworkBinding(base));
                check("provider wallpaper fade", () -> new OneUi85WallpaperFadeBinding(base));
            } else {
                check("9.0 media coordinator", () -> OneUi9MediaBinding.coordinator(provider));
                check("9.0 media holder", () -> OneUi9MediaBinding.holder(provider));
            }
            check("provider Now Bar stack", () -> NowBarStackBinding.of(base));
            System.out.println("AUDIT: " + passed + " passed, " + failed + " failed");
            System.exit(failed == 0 ? 0 : 2);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
