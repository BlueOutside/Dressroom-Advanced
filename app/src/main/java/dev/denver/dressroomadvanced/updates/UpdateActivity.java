package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.Executors;

/** Owns the download and installer permission, not the hooked editor process. */
public final class UpdateActivity extends Activity {
    private static final java.util.concurrent.ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private AlertDialog dialog;
    private File ready;
    private long targetVersionCode = -1L;
    private boolean waiting;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        if (window != null) {
            window.setDimAmount(0f);
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        waiting = state != null && state.getBoolean("waiting");
        targetVersionCode = state == null ? -1L : state.getLong("targetVersionCode", -1L);
        org.json.JSONObject release;
        try {
            release = UpdateRepository.authorized(this, getIntent().getStringExtra("token"));
        } catch (Exception error) {
            closeHost();
            return;
        }
        if (state != null && state.getBoolean("ready")) {
            if (targetVersionCode > 0
                    && UpdateRepository.installedVersionCode(this) >= targetVersionCode) {
                closeHost();
                return;
            }
            ready = UpdateRepository.apk(this);
            if (!ready.isFile()) {
                if (waiting) closeHost();
                else error("The downloaded update is no longer available");
                return;
            }
            if (!waiting) launchSystemInstaller();
            return;
        }
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        ProgressBar progress =
                new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        TextView percent = new TextView(this);
        percent.setText("0%");
        content.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        content.addView(percent);
        dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Downloading update")
                        .setView(content)
                        .setNegativeButton(
                                "Cancel",
                                (d, which) -> cancelAndClose())
                        .setOnCancelListener(
                                d -> cancelAndClose())
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Context appContext = getApplicationContext();
        worker.execute(
                () -> {
                    try {
                        File file =
                                UpdateRepository.download(
                                        appContext,
                                        release,
                                        new UpdateRepository.Progress() {
                                            public boolean cancelled() {
                                                return cancelled;
                                            }

                                            public void update(int value) {
                                                runOnUiThread(
                                                        () -> {
                                                            if (!isDestroyed()) {
                                                                progress.setProgress(value);
                                                                percent.setText(value + "%");
                                                            }
                                                        });
                                            }
                                        });
                        if (cancelled || isDestroyed()) return;
                        long targetCode = UpdateRepository.archiveVersionCode(appContext, file);
                        runOnUiThread(
                                () -> {
                                    if (cancelled || isDestroyed()) return;
                                    ready = file;
                                    targetVersionCode = targetCode;
                                    dialog.setTitle("Installing update");
                                    progress.setIndeterminate(true);
                                    percent.setText("Checking root access...");
                                });
                        boolean hasRoot = RootUpdater.hasRoot();
                        if (cancelled || isDestroyed()) return;
                        if (hasRoot) {
                            runOnUiThread(
                                    () -> {
                                        if (cancelled || isDestroyed()) return;
                                        dialog.setCancelable(false);
                                        Button negative =
                                                dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                                        if (negative != null) negative.setEnabled(false);
                                        percent.setText("Installing with root...");
                                    });
                            RootUpdater.CommandResult installResult = RootUpdater.installApk(file);
                            if (RootUpdater.isInstallSuccess(installResult)) {
                                runOnUiThread(
                                        () -> {
                                            if (!isDestroyed()) closeHost();
                                        });
                            } else {
                                android.util.Log.w(
                                        DressroomModule.TAG,
                                        "Root install failed, falling back to package installer: exit="
                                                + installResult.exitCode()
                                                + ", timedOut="
                                                + installResult.timedOut());
                                runOnUiThread(
                                        () -> {
                                            if (cancelled || isDestroyed()) return;
                                            dismissDialog();
                                            launchSystemInstaller();
                                        });
                            }
                        } else {
                            runOnUiThread(
                                    () -> {
                                        if (cancelled || isDestroyed()) return;
                                        dismissDialog();
                                        launchSystemInstaller();
                                    });
                        }
                    } catch (Exception error) {
                        runOnUiThread(
                                () -> {
                                    if (!cancelled && !isDestroyed()) error(error.getMessage());
                                });
                    }
                });
    }

    private void cancelAndClose() {
        cancelled = true;
        closeHost();
    }

    private void launchSystemInstaller() {
        if (isFinishing() || isDestroyed()) return;
        if (ready == null || !ready.isFile()) {
            error("The downloaded update is no longer available");
            return;
        }
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                waiting = true;
                startActivityForResult(
                        new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())),
                        1);
                return;
            }
            Uri uri = UpdateFileProvider.URI;
            Intent install =
                    new Intent(Intent.ACTION_INSTALL_PACKAGE)
                            .setData(uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            install.setClipData(ClipData.newRawUri("Dressroom Advanced update", uri));
            waiting = true;
            startActivityForResult(install, 2);
        } catch (RuntimeException error) {
            error("Could not open Android's package installer");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        waiting = false;
        if (request == 1) {
            if (getPackageManager().canRequestPackageInstalls()) {
                launchSystemInstaller();
            } else {
                error("Allow Dressroom Advanced to install updates, then try again.");
            }
        } else if (request == 2) {
            closeHost();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("ready", ready != null);
        state.putBoolean("waiting", waiting);
        state.putLong("targetVersionCode", targetVersionCode);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        dismissDialog();
        super.onDestroy();
    }

    private void dismissDialog() {
        if (dialog != null) {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (RuntimeException ignored) {
            }
            dialog = null;
        }
    }

    private void closeHost() {
        dismissDialog();
        if (!isFinishing()) finishAndRemoveTask();
    }

    private void error(String message) {
        waiting = false;
        if (cancelled || isFinishing() || isDestroyed()) return;
        dismissDialog();
        dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Update failed")
                        .setMessage(
                                message == null
                                        ? "Could not download the update. Please try again later."
                                        : message)
                        .setPositiveButton("OK", (d, which) -> closeHost())
                        .setOnCancelListener(d -> closeHost())
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
