package dev.denver.dressroomadvanced;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Handles module replacements from the updater, a package installer, or ADB. */
public final class UpdateInstalledReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null
                || intent == null
                || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        try {
            UpdateRepository.apk(context).delete();
        } catch (RuntimeException ignored) {
        }
        PendingResult pending = goAsync();
        UpdateProcessRelaunch.check(context, true, pending::finish);
    }
}
