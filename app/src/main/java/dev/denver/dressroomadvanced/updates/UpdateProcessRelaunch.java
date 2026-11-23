package dev.denver.dressroomadvanced;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deduplicates update reloads and retries missed replacement broadcasts when Studio opens. */
final class UpdateProcessRelaunch {
    private static final String RELOADED_INSTALL = "reloaded_install_time";
    private static final AtomicBoolean running = new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "update-relaunch");
                thread.setDaemon(true);
                return thread;
            });
    private static long retryAfter;

    private UpdateProcessRelaunch() {}

    static boolean needsReload(long firstInstall, long updated, long reloaded, boolean replacement) {
        return updated > 0 && reloaded != updated && (replacement || updated != firstInstall);
    }

    static void check(Context context, boolean replacement, Runnable complete) {
        if (!running.compareAndSet(false, true)) {
            complete.run();
            return;
        }
        Context application = context.getApplicationContext();
        Context app = application == null ? context : application;
        try {
            worker.execute(() -> {
                try {
                    var installed = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                    var prefs = app.getSharedPreferences("updates", 0);
                    long reloaded = prefs.getLong(RELOADED_INSTALL, 0);
                    if (!needsReload(installed.firstInstallTime, installed.lastUpdateTime, reloaded, replacement))
                        return;
                    if (!replacement && android.os.SystemClock.elapsedRealtime() < retryAfter) return;
                    if (replacement) Thread.sleep(1500L);
                    RootUpdater.CommandResult result = RootUpdater.relaunchHookedProcesses();
                    if (result.timedOut() || result.exitCode() != 0) {
                        retryAfter = android.os.SystemClock.elapsedRealtime() + 30_000L;
                        Log.w(DressroomModule.TAG, "Post-update process reload incomplete: exit="
                                + result.exitCode() + ", timedOut=" + result.timedOut()
                                + ", output=" + result.output());
                        return;
                    }
                    if (!prefs.edit().putLong(RELOADED_INSTALL, installed.lastUpdateTime).commit())
                        throw new IllegalStateException("Could not record completed process reload");
                    retryAfter = 0;
                    Log.i(DressroomModule.TAG, "Reloaded scoped processes after module update:\n" + result.output());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } catch (Exception | LinkageError error) {
                    Log.w(DressroomModule.TAG, "Post-update process reload failed", error);
                } finally {
                    running.set(false);
                    complete.run();
                }
            });
        } catch (RuntimeException error) {
            running.set(false);
            complete.run();
            Log.w(DressroomModule.TAG, "Could not schedule process reload", error);
        }
    }
}
