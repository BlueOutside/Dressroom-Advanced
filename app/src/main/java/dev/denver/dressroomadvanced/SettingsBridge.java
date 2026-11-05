package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Bundle;

/** The wallpaper ID is shared by the editor and actual lockscreen render processes. */
final class SettingsBridge {
    private SettingsBridge() {}
    static int wallpaperId(Context context) {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            int lock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK);
            return lock >= 0 ? lock : manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        }
        catch (RuntimeException e) { return -1; }
    }
    static Bundle read(Context context, Bundle identity) {
        Bundle query = new Bundle(identity);
        LayoutOrientation.stamp(context, query);
        query.putInt("wallpaperId", wallpaperId(context));
        if (query.getInt("wallpaperId", -1) < 0) return null;
        return DepthStorage.unpack(context,context.getContentResolver().call(SettingsProvider.URI, "read", null, query));
    }
    static Bundle commit(Context context, Bundle snapshot) {
        java.util.ArrayList<Bundle> layouts = snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            Bundle result = null;
            for (Bundle layout : layouts) result = commit(context, layout);
            return result;
        }
        Bundle saved = new Bundle(snapshot);
        LayoutOrientation.stamp(context, saved);
        // Native Apply may allocate a new wallpaper ID. Resolve it at successful completion.
        saved.putInt("wallpaperId", wallpaperId(context));
        if (saved.getInt("wallpaperId", -1) < 0) throw new IllegalStateException("Wallpaper identity is unavailable");
        return context.getContentResolver().call(SettingsProvider.URI, "commit", null, DepthStorage.pack(context,saved));
    }
}
