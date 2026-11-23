package dev.denver.dressroomadvanced;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only metadata checks. Does not download or launch the installer. */
public final class UpdateProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static JSONObject release(String url, long size) throws Exception {
        JSONObject asset =
                new JSONObject()
                        .put("name", "module.apk")
                        .put("size", size)
                        .put("browser_download_url", url);
        return new JSONObject().put("assets", new JSONArray().put(asset));
    }

    public static void main(String[] args) throws Exception {
        verifyReloadShell();
        String official =
                "https://github.com/BlueOutside/Dressroom-Advanced/releases/download/v1.1.9/module.apk";
        check(UpdateRepository.asset(release(official, 100)) != null, "Official APK rejected");
        check(
                UpdateRepository.asset(release("https://example.com/module.apk", 100)) == null,
                "Untrusted asset accepted");
        check(UpdateRepository.asset(release(official, 0)) == null, "Empty asset accepted");
        check(
                UpdateRepository.asset(release(official, 129L * 1024 * 1024)) == null,
                "Unbounded asset accepted");
        JSONObject ambiguous = release(official, 100);
        ambiguous.getJSONArray("assets").put(ambiguous.getJSONArray("assets").get(0));
        try {
            UpdateRepository.asset(ambiguous);
            throw new AssertionError("Ambiguous APK accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
        UpdateFileProvider files = new UpdateFileProvider();
        check(
                files.getType(UpdateFileProvider.URI)
                        .equals("application/vnd.android.package-archive"),
                "Wrong APK MIME type");
        try {
            files.getType(
                    android.net.Uri.parse(
                            "content://dev.denver.dressroomadvanced.updates/../settings"));
            throw new AssertionError("Unexpected file URI accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(!ObjectCaptureBackend.available(new ClassLoader(null) {}), "Missing SDK was enabled");
        if (args.length > 0) {
            android.os.Looper.prepareMainLooper();
            Class<?> shared = Class.forName("com.android.internal.os.ApplicationSharedMemory");
            shared.getMethod("setInstance", shared)
                    .invoke(null, shared.getMethod("create").invoke(null));
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object owner = thread.getMethod("systemMain").invoke(null);
            android.content.Context context =
                    (android.content.Context) thread.getMethod("getSystemContext").invoke(owner);
            UpdateRepository.verify(context, new java.io.File(args[0]), "1.1.9");
            checks++;
            try {
                UpdateRepository.verify(context, new java.io.File(args[0]), "999.0.0");
                throw new AssertionError("Wrong release version accepted");
            } catch (java.io.IOException expected) {
                checks++;
            }
            android.content.Context editor =
                    context.createPackageContext("com.samsung.android.app.dressroom", 3);
            check(
                    ObjectCaptureBackend.available(editor.getClassLoader()),
                    "Samsung detector libraries incorrectly disabled on the test phone");
        }
        System.out.println("Update/depth availability: " + checks + " checks passed");
        System.exit(0);
    }

    private record ShellResult(int exit, String output) {}

    private static ShellResult shell(String fixture) throws Exception {
        // These functions shadow the system commands: no real process receives a signal.
        Process process = new ProcessBuilder("/system/bin/sh", "-c", fixture + "\n"
                + RootUpdater.relaunchCommand()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new ShellResult(process.waitFor(), output);
    }

    private static void verifyReloadShell() throws Exception {
        String fixture = "ps() { printf '%s\\n' 'PID NAME' "
                + "'101 com.android.systemui' '102 com.android.systemui:screenshot' "
                + "'103 com.samsung.android.app.dressroom' "
                + "'104 com.samsung.android.app.aodservice:provider' "
                + "'105 com.samsung.android.app.aodservice' "
                + "'106 com.samsung.android.app.clockpack:provider' "
                + "'107 com.samsung.android.wallpaper.live:wallpaper' "
                + "'108 com.android.systemui.other' '109 dev.denver.dressroomadvanced'; }\n"
                + "kill() { return 0; }\n";
        ShellResult result = shell(fixture);
        check(result.exit == 0, "Scoped reload script failed: " + result.output);
        for (int pid = 101; pid <= 107; pid++)
            check(result.output.contains("(pid " + pid + ")"), "Scoped subprocess missed: " + pid);
        check(!result.output.contains("pid 108") && !result.output.contains("pid 109"),
                "Reload matched a lookalike package or the module itself");
        check(result.output.indexOf("pid 103") < result.output.indexOf("pid 101"),
                "SystemUI reloads before its providers");
        check(shell("ps() { return 1; }\nkill() { return 0; }").exit != 0,
                "Process enumeration failure reported success");
        ShellResult denied = shell("""
                ps() { printf '%s\\n' 'PID NAME' '101 com.android.systemui'; }
                kill() { if [ "$1" = '-0' ]; then return 0; else return 1; fi; }
                """);
        check(denied.exit != 0 && denied.output.contains("Could not stop com.android.systemui"),
                "A denied kill is silently reported as success");
        check(shell("""
                ps() { printf '%s\\n' 'PID NAME' '101 com.android.systemui'; }
                kill() { return 1; }
                """).exit == 0, "A process that already exited is reported as a failed kill");
    }
}
