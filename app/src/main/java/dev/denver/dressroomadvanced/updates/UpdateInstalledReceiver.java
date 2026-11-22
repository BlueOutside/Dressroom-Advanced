package dev.denver.dressroomadvanced;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Relaunches scoped processes after a root-authorized in-app module replacement finishes. */
public final class UpdateInstalledReceiver extends BroadcastReceiver {
    private static final long RELAUNCH_DELAY_MS = 1500L;
    private static final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "update-relaunch");
                        thread.setDaemon(true);
                        return thread;
                    });

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null
                || intent == null
                || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        try {
            UpdateRepository.apk(app).delete();
        } catch (RuntimeException ignored) {
        }
        if (!UpdateRepository.consumePendingRelaunch(app)) {
            return;
        }
        PendingResult pending = goAsync();
        worker.execute(
                () -> {
                    try {
                        Thread.sleep(RELAUNCH_DELAY_MS);
                        RootUpdater.CommandResult result = RootUpdater.relaunchHookedProcesses();
                        if (result.timedOut() || result.exitCode() != 0) {
                            Log.w(
                                    DressroomModule.TAG,
                                    "Post-update process relaunch incomplete: exit="
                                            + result.exitCode()
                                            + ", timedOut="
                                            + result.timedOut());
                        } else {
                            Log.i(
                                    DressroomModule.TAG,
                                    "Relaunched scoped processes after in-app update");
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException error) {
                        Log.w(DressroomModule.TAG, "Post-update process relaunch failed", error);
                    } finally {
                        pending.finish();
                    }
                });
    }
}
