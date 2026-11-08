package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Read-only contract checks for the inspected APK pair, using the production shim resolvers. */
public final class Fold8BindingProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("editor.apk aod.apk");
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        ClassLoader editor = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        EditorBinding binding = EditorBinding.resolve(editor, 300120000L);
        if (binding.legacy) throw new AssertionError("Modern editor misclassified by weather capability");
        new WallpaperSourceBinding(editor, false);
        new EditorSaveCompletion(editor);
        DepthBinding.validateEditor(editor);
        OneUi9WidgetEditor.toolbar(editor);
        OneUi9WidgetEditor.create(editor);
        OneUi9WidgetEditor.getGrid(editor);
        OneUi9WidgetEditor.restore(editor);
        OneUi9WidgetEditor.adapter(editor);
        OneUi9WidgetEditor.lifecycle(editor);
        OneUi9WidgetEditor.pickerScope(editor);
        Class<?> align = Class.forName("com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType", false, editor);
        Class<?> data = Class.forName("com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData", false, editor);
        Class<?> assist = Class.forName("com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetAssistData", false, editor);
        Class<?> grid = Class.forName("com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetGridData", false, editor);
        Class<?> size = Class.forName("com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetSizeData", false, editor);
        OneUi9WidgetEditor.gridMetrics(editor, grid, align);
        OneUi9WidgetEditor.cellMetrics(editor, grid, size);
        OneUi9WidgetEditor.placement(editor, data, assist);
        OneUi9WidgetEditor.alignFeedback(editor, data);
        OneUi9WidgetEditor.providerAlign(editor, align);
        OneUi9WidgetEditor.rotate(editor, align);
        try { OneUi9WidgetEditor.rotationTrimmed(editor, align); }
        catch (ClassNotFoundException missing) { OneUi9WidgetEditor.portraitPolicy(editor); }
        System.out.println("PASS inspected editor and widget signature contracts");
        ClassLoader aod = new PathClassLoader(args[1], ClassLoader.getSystemClassLoader());
        Class<?> base = Class.forName("com.samsung.android.uniform.widget.clock.AbsClockView", false, aod);
        ClockBinding clock = ClockBinding.of(base);
        if (clock.legacy || clock.clockStyle) throw new AssertionError("Wrong provider ABI");
        FontBinding.of(base);
        new ClockSourceBinding(aod, clock);
        ClockTransitionBinding.of(base);
        ClockGlassBinding.of(base);
        ClockBlurBinding.resolve(base);
        NowBarStackBinding.of(base);
        WidgetHostSizing.validate(aod);
        WidgetEngine.validateLegacyGrid(Class.forName(WidgetEngine.GRID, false, aod));
        OneUi9FoldProfiles.validate(aod);
        OneUi9MediaBinding.holder(aod);
        OneUi9MediaBinding.coordinator(aod);
        OneUi9MediaBinding.animatorUpdate(aod);
        OneUi9MediaBinding.expandInvoke(aod);
        OneUi9MediaBinding.pillBounds(aod);
        OneUi9AodBlur.submissions(aod);
        System.out.println("PASS inspected clock, native depth, widgets, Now Bar, media and profile signature contracts");
    }
}
