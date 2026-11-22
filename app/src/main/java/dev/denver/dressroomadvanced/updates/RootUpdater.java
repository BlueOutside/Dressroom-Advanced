package dev.denver.dressroomadvanced;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Optional root helpers to install updates silently and relaunch scoped host processes. */
final class RootUpdater {
    static final List<String> HOOKED_PROCESSES =
            List.of(
                    "com.samsung.android.app.dressroom",
                    "com.samsung.android.app.aodservice:provider",
                    "com.samsung.android.app.aodservice",
                    "com.samsung.android.app.clockpack",
                    "com.samsung.android.wallpaper.live",
                    "com.android.systemui");
    private static final int MAX_OUTPUT_BYTES = 8192;

    record CommandResult(int exitCode, String output, boolean timedOut) {}

    private RootUpdater() {}

    static String shellQuote(String value) {
        if (value == null
                || value.indexOf('\0') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Unsupported shell argument");
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static String installCommand(String apkPath, long sizeBytes) {
        if (apkPath == null || apkPath.isEmpty()) {
            throw new IllegalArgumentException("APK path is missing");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("APK size must be positive");
        }
        return "cat " + shellQuote(apkPath) + " | cmd package install -r -S " + sizeBytes + " -";
    }

    static String relaunchCommand() {
        StringBuilder command = new StringBuilder();
        for (int i = 0; i < HOOKED_PROCESSES.size(); i++) {
            if (i > 0) command.append("; ");
            command.append("killall ").append(shellQuote(HOOKED_PROCESSES.get(i))).append(" >/dev/null 2>&1");
        }
        command.append("; true");
        return command.toString();
    }

    static boolean isRootOutput(CommandResult result) {
        return result != null
                && !result.timedOut()
                && result.exitCode() == 0
                && result.output() != null
                && result.output().contains("uid=0");
    }

    static boolean isInstallSuccess(CommandResult result) {
        return result != null
                && !result.timedOut()
                && result.exitCode() == 0
                && result.output() != null
                && result.output().contains("Success")
                && !result.output().contains("Failure");
    }

    static boolean hasRoot() {
        return isRootOutput(runSu("id", 15_000L));
    }

    static CommandResult installApk(File apk) {
        if (apk == null || !apk.isFile() || apk.length() <= 0) {
            throw new IllegalArgumentException("Update APK is missing or empty");
        }
        return runSu(installCommand(apk.getAbsolutePath(), apk.length()), 60_000L);
    }

    static CommandResult relaunchHookedProcesses() {
        return runSu(relaunchCommand(), 10_000L);
    }

    private static CommandResult runSu(String command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            InputStream stream = process.getInputStream();
            Thread reader = new Thread(() -> drain(stream, buffer), "root-updater-io");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(500L);
                return new CommandResult(-1, buffer.toString(StandardCharsets.UTF_8), true);
            }
            reader.join(500L);
            return new CommandResult(
                    process.exitValue(), buffer.toString(StandardCharsets.UTF_8), false);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new CommandResult(-1, "Interrupted", false);
        } catch (IOException error) {
            if (process != null) process.destroyForcibly();
            return new CommandResult(
                    -1, error.getMessage() == null ? "IOException" : error.getMessage(), false);
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream buffer) {
        byte[] chunk = new byte[1024];
        try {
            int count;
            while ((count = stream.read(chunk)) != -1) {
                int remaining = MAX_OUTPUT_BYTES - buffer.size();
                if (remaining > 0) {
                    buffer.write(chunk, 0, Math.min(count, remaining));
                }
            }
        } catch (IOException ignored) {
        }
    }
}
