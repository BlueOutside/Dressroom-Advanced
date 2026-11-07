package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Bundle;

final class SettingsBridge {
    private SettingsBridge() {}

    static int wallpaperId(Context context) {
        return wallpaperId(context, ScreenIdentity.current());
    }

    static int wallpaperId(Context context, int screen) {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            int lock = manager.getWallpaperId(ScreenIdentity.wallpaperWhich(screen, true));
            return lock >= 0 ? lock : manager.getWallpaperId(ScreenIdentity.wallpaperWhich(screen, false));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static Bundle read(Context context, Bundle identity) {
        Bundle query = new Bundle(identity);
        LayoutOrientation.stamp(context, query);
        query.putInt("wallpaperId", wallpaperId(context, ScreenIdentity.value(query)));
        query.putInt("legacyWallpaperId", wallpaperId(context, 0));
        if (query.getInt("wallpaperId", -1) < 0) return null;
        return DepthStorage.unpack(
                context,
                context.getContentResolver().call(SettingsProvider.URI, "read", null, query));
    }

    static Bundle commit(Context context, Bundle snapshot) {
        java.util.ArrayList<Bundle> layouts =
                snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            Bundle result = null;
            for (Bundle layout : layouts) result = commit(context, layout);
            return result;
        }
        Bundle saved = new Bundle(snapshot);
        LayoutOrientation.stamp(context, saved);
        if (saved.getInt("wallpaperId", -1) < 0)
            saved.putInt("wallpaperId", wallpaperId(context, ScreenIdentity.value(saved)));
        if (saved.getInt("wallpaperId", -1) < 0)
            throw new IllegalStateException("Wallpaper identity is unavailable");
        return context.getContentResolver()
                .call(SettingsProvider.URI, "commit", null, DepthStorage.pack(context, saved));
    }
}
