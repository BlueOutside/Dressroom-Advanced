package dev.denver.dressroomadvanced;

/** Read-only binding check. Pass the editor APK path to app_process. */
public final class SaveSignatureProbe {
    public static void main(String[] args) {
        try {
            ClassLoader loader =
                    new dalvik.system.PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
            EditorSaveCompletion binding = new EditorSaveCompletion(loader);
            if (binding.started.getParameterCount() != 0
                    || binding.completed.getParameterCount() != 0)
                throw new AssertionError("Invalid save entry points");
            System.out.println(
                    "Name-free editor save contract resolved: "
                            + binding.started
                            + " / "
                            + binding.completed);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
