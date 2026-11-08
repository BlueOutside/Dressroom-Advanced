package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Resolve editor and provider startup contracts without installing either APK. */
public final class StartupBindingProbe {
    public static void main(String[] args) {
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("editor.apk provider.apk editorVersionCode");
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
            ClassLoader parent = ClassLoader.getSystemClassLoader();
            ClassLoader editor = new PathClassLoader(args[0], parent);
            EditorBinding binding = EditorBinding.resolve(editor, Long.parseLong(args[2]));
            new EditorSaveCompletion(editor);
            new WallpaperSourceBinding(editor, binding.legacy);
            DepthBinding.validateEditor(editor);
            System.out.println(
                    "Editor panel, final save, wallpaper source and depth prerequisites resolved");
            ClassLoader provider = new PathClassLoader(args[1], parent);
            Class<?> base =
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            provider);
            ClockBinding clock = ClockBinding.of(base);
            new ClockSourceBinding(provider, clock);
            if (!clock.clockStyle) {
                ClockBlurBinding.resolve(base);
                if (clock.legacy) OneUi85AodBlur.submissions(provider);
            }
            FontBinding.of(base);
            System.out.println("Provider height normalizer, source, blur and font resolved");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
