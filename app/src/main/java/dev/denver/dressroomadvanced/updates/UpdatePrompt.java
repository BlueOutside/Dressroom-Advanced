package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;

final class UpdatePrompt {
    private static final Set<Activity> checked = Collections.newSetFromMap(new WeakHashMap<>());
    private static final java.util.concurrent.ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile WeakReference<Activity> destination = new WeakReference<>(null);
    private static final java.util.Map<Activity, WeakReference<AlertDialog>> dialogs =
            new WeakHashMap<>();

    private UpdatePrompt() {}

    static void check(Activity activity) {
        startCheck(activity, false);
    }

    static void checkNow(Activity activity) {
        startCheck(activity, true);
    }

    private static void startCheck(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!manual && !checked.add(activity)) return;
        if (manual) checked.add(activity);
        destination = new WeakReference<>(activity);
        if (!running.compareAndSet(false, true)) {
            if (manual) {
                Toast.makeText(activity, "Already checking for updates", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        var context = activity.getApplicationContext();
        worker.execute(
                () -> {
                    try {
                        Bundle extras = null;
                        if (manual) {
                            extras = new Bundle();
                            extras.putBoolean("force", true);
                        }
                        Bundle update =
                                context.getContentResolver()
                                        .call(SettingsProvider.URI, "checkUpdate", null, extras);
                        WeakReference<Activity> reference = destination;
                        Activity current = reference.get();
                        if (current == null || current.isFinishing() || current.isDestroyed())
                            return;
                        if (update == null || update.getString("token") == null) {
                            if (manual) {
                                String installed =
                                        update == null ? "" : update.getString("installed", "");
                                String message =
                                        installed.isEmpty()
                                                ? "You are up to date"
                                                : "You are up to date (" + installed + ")";
                                current.runOnUiThread(
                                        () ->
                                                Toast.makeText(
                                                                current,
                                                                message,
                                                                Toast.LENGTH_SHORT)
                                                        .show());
                            }
                            return;
                        }
                        current.runOnUiThread(() -> show(reference, update, 0, manual));
                    } catch (Exception error) {
                        // Offline/rate-limited checks should not interrupt wallpaper editing.
                        android.util.Log.i(
                                DressroomModule.TAG,
                                "Update check unavailable: " + error.getClass().getSimpleName());
                        if (manual) {
                            Activity current = destination.get();
                            if (current != null && !current.isFinishing() && !current.isDestroyed()) {
                                current.runOnUiThread(
                                        () ->
                                                Toast.makeText(
                                                                current,
                                                                "Could not check for updates",
                                                                Toast.LENGTH_SHORT)
                                                        .show());
                            }
                        }
                    } finally {
                        running.set(false);
                    }
                });
    }

    private static void show(
            WeakReference<Activity> reference, Bundle update, int attempt, boolean manual) {
        try {
            showWindow(reference, update, attempt, manual);
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Update prompt could not attach", error);
        }
    }

    private static void showWindow(
            WeakReference<Activity> reference, Bundle update, int attempt, boolean manual) {
        Activity host = reference.get();
        if (reference != destination || host == null || !checked.contains(host)
                || host.isFinishing() || host.isDestroyed()) return;
        WeakReference<AlertDialog> existing = dialogs.get(host);
        AlertDialog shown = existing == null ? null : existing.get();
        if (shown != null && shown.isShowing()) return;
        if (!manual && !host.hasWindowFocus()) {
            if (attempt < 10)
                host.getWindow()
                        .getDecorView()
                        .postDelayed(() -> show(reference, update, attempt + 1, false), 500);
            return;
        }
        String version = update.getString("version");
        String installed = update.getString("installed");
        String token = update.getString("token");
        String altToken = update.getString("altToken");
        AlertDialog.Builder builder = new AlertDialog.Builder(host);
        if (altToken != null) {
            String altVersion = update.getString("altVersion");
            builder.setTitle("Dressroom Advanced updates")
                    .setMessage(
                            "Both a stable release ("
                                    + version
                                    + ") and a pre-release ("
                                    + altVersion
                                    + ") are available.\nInstalled: "
                                    + installed
                                    + "\n\nChoose which version to install:")
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton(
                            "Pre-release (" + altVersion + ")",
                            (ignored, which) -> launchUpdater(host, altToken))
                    .setPositiveButton(
                            "Stable (" + version + ")",
                            (ignored, which) -> launchUpdater(host, token));
        } else if (update.getBoolean("prerelease")) {
            builder.setTitle("Dressroom Advanced pre-release")
                    .setMessage(
                            "Pre-release "
                                    + version
                                    + " is available. Installed: "
                                    + installed
                                    + ". Download and install it?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Update", (ignored, which) -> launchUpdater(host, token));
        } else {
            builder.setTitle("Dressroom Advanced update")
                    .setMessage(
                            version
                                    + " is available. Installed: "
                                    + installed
                                    + ". Download and install it?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Update", (ignored, which) -> launchUpdater(host, token));
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(
                ignored -> {
                    Activity owner = reference.get();
                    if (owner != null) dialogs.remove(owner);
                });
        dialog.show();
        dialogs.put(host, new WeakReference<>(dialog));
    }

    private static void launchUpdater(Activity host, String token) {
        Intent intent =
                new Intent()
                        .setClassName(
                                UpdateRepository.PACKAGE,
                                UpdateRepository.PACKAGE + ".UpdateActivity")
                        .putExtra("token", token);
        try {
            host.startActivity(intent);
        } catch (RuntimeException error) {
            Toast.makeText(host, "Could not open the updater", Toast.LENGTH_LONG).show();
        }
    }

    static void close(Activity activity) {
        checked.remove(activity);
        if (destination.get() == activity) destination.clear();
        WeakReference<AlertDialog> reference = dialogs.remove(activity);
        AlertDialog dialog = reference == null ? null : reference.get();
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (RuntimeException error) {
                android.util.Log.w(DressroomModule.TAG, "Update prompt cleanup failed", error);
            }
        }
    }
}
