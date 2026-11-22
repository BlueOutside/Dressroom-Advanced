package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        waiting = state != null && state.getBoolean("waiting");
        org.json.JSONObject release;
        try {
            release = UpdateRepository.authorized(this, getIntent().getStringExtra("token"));
        } catch (Exception error) {
            finish();
            return;
        }
        if (state != null && state.getBoolean("ready")) {
            ready = UpdateRepository.apk(this);
            if (!ready.isFile()) {
                error("The downloaded update is no longer available");
                return;
            }
            if (!state.getBoolean("waiting")) install();
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
                        .setTitle(L10n.t("Downloading update"))
                        .setView(content)
                        .setNegativeButton(
                                L10n.t("Cancel"),
                                (d, which) -> {
                                    cancelled = true;
                                    finish();
                                })
                        .setOnCancelListener(
                                d -> {
                                    cancelled = true;
                                    finish();
                                })
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        worker.execute(
                () -> {
                    try {
                        File file =
                                UpdateRepository.download(
                                        getApplicationContext(),
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
                        runOnUiThread(
                                () -> {
                                    if (cancelled || isDestroyed()) return;
                                    ready = file;
                                    dialog.dismiss();
                                    install();
                                });
                    } catch (Exception error) {
                        runOnUiThread(
                                () -> {
                                    if (!cancelled && !isDestroyed()) error(error.getMessage());
                                });
                    }
                });
    }

    private boolean waiting;

    private void install() {
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
            if (getPackageManager().canRequestPackageInstalls()) install();
            else error("Allow Dressroom Advanced to install updates, then try again.");
        } else if (request == 2) finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("ready", ready != null);
        state.putBoolean("waiting", waiting);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    private void error(String message) {
        message = L10n.t(message);
        waiting = false;
        if (cancelled || isFinishing() || isDestroyed()) return;
        if (dialog != null) dialog.dismiss();
        dialog =
                new AlertDialog.Builder(this)
                        .setTitle(L10n.t("Update failed"))
                        .setMessage(
                                message == null
                                        ? L10n.t("Could not download the update. Please try again later.")
                                        : message)
                        .setPositiveButton(L10n.t("OK"), (d, which) -> finish())
                        .setOnCancelListener(d -> finish())
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
