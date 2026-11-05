package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

public final class VersionBindingProbe {
    public static void main(String[] args) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        if (args.length != 3)
            throw new IllegalArgumentException("editor.apk provider.apk editorVersionCode");
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        ClassLoader widgetProvider = new PathClassLoader(args[1], parent);
        Class<?> providerOwner =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                        false,
                        widgetProvider);
        NativeContract.field(providerOwner, "context", android.content.Context.class);
        NativeContract.method(providerOwner, "updateWallpaperColorsItem", void.class, Object.class);
        Class<?> providerClock = Class.forName(
                "com.samsung.android.uniform.widget.clock.AbsClockView", false, widgetProvider);
        ClockSourceBinding source = new ClockSourceBinding(widgetProvider, ClockBinding.of(providerClock));
        System.out.println("Clock source and " + source.callers.size() + " measurement callers resolved");
        ClassLoader editor = new PathClassLoader(args[0], parent);
        EditorBinding binding = EditorBinding.resolve(editor, Long.parseLong(args[2]));
        new WallpaperSourceBinding(editor, binding.legacy);
        Class<?> screen =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.system.ScreenSize",
                        false,
                        editor);
        NativeContract.method(screen, "getWidth", int.class);
        NativeContract.method(screen, "getHeight", int.class);
        NativeContract.method(
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                        false,
                        editor),
                "onConfigurationChanged",
                void.class,
                android.content.res.Configuration.class);
        if (!binding.legacy) {
            Class.forName("rp.e", false, editor)
                    .getDeclaredMethod(
                            "b",
                            android.view.View.class,
                            int.class,
                            int.class,
                            float.class,
                            int.class,
                            int.class,
                            boolean.class);
            Class.forName("androidx.databinding.ViewDataBinding", false, editor)
                    .getDeclaredMethod("getBinding", android.view.View.class);
            ClassLoader provider = new PathClassLoader(args[1], parent);
            Class<?> mask =
                    Class.forName(
                            "com.samsung.android.uniform.nowbar.MediaMaskingFrameLayout",
                            false,
                            provider);
            mask.getDeclaredMethod("setAnimationProgress", float.class);
            mask.getDeclaredMethod("setRandomOffset", float.class);
            Class<?> song = Class.forName("aod.ag4", false, provider);
            song.getDeclaredMethod("d", android.graphics.drawable.Drawable.class);
            Class<?> host =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer",
                            false,
                            provider);
            host.getDeclaredMethod("setAlpha", float.class);
            host.getDeclaredMethod("setBlurBackgroundAlpha", int.class);
            host.getDeclaredMethod("refreshBlur", float.class);
            Class<?> observer =
                    Class.forName(
                            "com.samsung.android.app.facewidget.complication.WidgetObserverManager",
                            false,
                            provider);
            observer.getDeclaredMethod(
                    "mediaExpandStateObserver$lambda$0", observer, boolean.class);
            System.out.println("9.0 native song reveal and widget opacity declarations resolved");
            WidgetHostSizing.validate(provider);
            WidgetEngine.validateLegacyGrid(Class.forName(WidgetEngine.GRID, false, provider));
            System.out.println(
                    "9.0 reference: widget creation, nested-host sizing, provider options and"
                            + " immediate drag binding resolved");
            return;
        }
        Class<?> save =
                Class.forName("com.samsung.android.lockscreen.editor.component.H$e", false, editor);
        NativeContract.field(save, "b", int.class);
        NativeContract.method(save, "invokeSuspend", Object.class, Object.class);
        Class<?> model = Class.forName("Pa.L", false, editor);
        Class.forName("Pa.L$b", false, editor)
                .getDeclaredConstructor(
                        model,
                        boolean.class,
                        Class.forName("kotlin.coroutines.Continuation", false, editor));
        System.out.println(
                "8.5 editor: owner, model, tabs, weather, bind, create, save and date coroutine"
                        + " resolved");
        new WidgetEditorBinding(editor);
        DepthBinding.validateEditor(editor);
        System.out.println(
                "8.5 depth: Object Capture SDK and private crop drawable access resolved");
        System.out.println(
                "8.5 widgets: toolbar model, native preview, grid getter, metrics and placement"
                        + " resolved");
        ClassLoader provider = new PathClassLoader(args[1], parent);
        Class<?> clock =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView", false, provider);
        ClockBinding renderer = ClockBinding.of(clock);
        if (!renderer.legacy) throw new AssertionError("Expected 8.5 provider binding");
        if (!renderer.clockStyle) {
            ClockBlurBinding.resolve(clock);
            OneUi85AodBlur.submissions(provider);
            System.out.println("8.5 AOD clock blur owner, clip and widget submission bindings resolved");
        }
        RuntimeWidgetHooks.legacyUpdates(
                Class.forName(
                        "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController",
                        false,
                        provider));
        WidgetHostSizing.validate(provider);
        WidgetEngine.validateLegacyGrid(Class.forName(WidgetEngine.GRID, false, provider));
        System.out.println(
                "8.5 widgets: attached host traversal and native provider-size notification"
                        + " resolved");
        System.out.println(
                "8.5 provider: "
                        + (renderer.clockStyle ? "Clock Style" : "AOD")
                        + "; stored font, identity, render, shadow and scoped height normalizer"
                        + " resolved");
    }
}
