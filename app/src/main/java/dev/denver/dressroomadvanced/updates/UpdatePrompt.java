package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

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
        if (!checked.add(activity)) return;
        destination = new WeakReference<>(activity);
        if (!running.compareAndSet(false, true)) return;
        var context = activity.getApplicationContext();
        worker.execute(
                () -> {
                    try {
                        Bundle update =
                                context.getContentResolver()
                                        .call(SettingsProvider.URI, "checkUpdate", null, null);
                        if (update == null || update.getString("token") == null) return;
                        WeakReference<Activity> reference = destination;
                        Activity current = reference.get();
                        if (current == null) return;
                        current.runOnUiThread(() -> show(reference, update, 0));
                    } catch (Exception error) {
                        // Offline/rate-limited checks should not interrupt wallpaper editing.
                        android.util.Log.i(
                                DressroomModule.TAG,
                                "Update check unavailable: " + error.getClass().getSimpleName());
                    } finally {
                        running.set(false);
                    }
                });
    }

    private static void show(WeakReference<Activity> reference, Bundle update, int attempt) {
        try {
            showWindow(reference, update, attempt);
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Update prompt could not attach", error);
        }
    }

    private static void showWindow(WeakReference<Activity> reference, Bundle update, int attempt) {
        Activity host = reference.get();
        if (reference != destination || host == null || !checked.contains(host)
                || host.isFinishing() || host.isDestroyed()) return;
        WeakReference<AlertDialog> existing = dialogs.get(host);
        AlertDialog shown = existing == null ? null : existing.get();
        if (shown != null && shown.isShowing()) return;
        if (!host.hasWindowFocus()) {
            if (attempt < 10)
                host.getWindow()
                        .getDecorView()
                        .postDelayed(() -> show(reference, update, attempt + 1), 500);
            return;
        }
        AlertDialog dialog =
                new AlertDialog.Builder(host)
                        .setTitle(L10n.t("Dressroom Advanced update"))
                        .setMessage(
                                update.getString("version")
                                        + L10n.t(" is available. Installed: ")
                                        + update.getString("installed")
                                        + L10n.t(". Download and install it?"))
                        .setNegativeButton(L10n.t("Cancel"), null)
                        .setPositiveButton(
                                L10n.t("Update"),
                                (ignored, which) -> {
                                    Intent intent =
                                            new Intent()
                                                    .setClassName(
                                                            UpdateRepository.PACKAGE,
                                                            UpdateRepository.PACKAGE
                                                                    + ".UpdateActivity")
                                                    .putExtra("token", update.getString("token"));
                                    try {
                                        host.startActivity(intent);
                                    } catch (RuntimeException error) {
                                        android.widget.Toast.makeText(
                                                        host,
                                                        L10n.t("Could not open the updater"),
                                                        android.widget.Toast.LENGTH_LONG)
                                                .show();
                                    }
                                })
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(
                ignored -> {
                    Activity owner = reference.get();
                    if (owner != null) dialogs.remove(owner);
                });
        dialog.show();
        dialogs.put(host, new WeakReference<>(dialog));
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
