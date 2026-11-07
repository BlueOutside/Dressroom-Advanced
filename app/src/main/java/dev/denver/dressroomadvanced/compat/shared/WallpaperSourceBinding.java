package dev.denver.dressroomadvanced;

import android.net.Uri;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class WallpaperSourceBinding {
    record Source(Object data, String key, boolean image) {}

    private final Field[] path;
    final Method changed;
    final Class<?> dataType;

    WallpaperSourceBinding(ClassLoader loader, boolean legacy) throws ReflectiveOperationException {
        this(loader, legacy, false);
    }

    WallpaperSourceBinding(ClassLoader loader, boolean legacy, boolean fold8) throws ReflectiveOperationException {
        dataType =
                Class.forName(
                        "com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData",
                        false,
                        loader);
        String[] types =
                legacy
                        ? new String[] {"P3.f", "X2.B", "X2.W"}
                        : new String[] {"p8.a", "o6.t", "o6.e0"};
        String[] names =
                legacy ? new String[] {"n", "v", "w", "Y"} : new String[] {"j", "p", "t", "T"};
        path = new Field[4];
        Class<?> owner =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        for (int i = 0; i < path.length; i++) {
            Class<?> next = i == 3 ? dataType : Class.forName(types[i], false, loader);
            path[i] = NativeContract.field(owner, names[i], next);
            owner = next;
        }
        changed =
                NativeContract.method(
                        path[2].getType(), legacy ? "b3" : fold8 ? "b1" : "c1", void.class, dataType);
        NativeContract.method(dataType, "getOriginalUri", Uri.class);
        NativeContract.method(dataType, "getUri", Uri.class);
        NativeContract.method(dataType, "getStorageFilePath", String.class);
        NativeContract.method(dataType, "getWallpaperSubType", int.class);
        NativeContract.method(dataType, "getWallpaperParentSubType", int.class);
    }

    Source read(Object activity) throws ReflectiveOperationException {
        Object data = activity;
        for (Field field : path) {
            if (data == null) return null;
            data = field.get(data);
        }
        if (data == null) return null;
        int type = ((Number) Reflect.call(data, "getWallpaperSubType")).intValue();
        int parent = ((Number) Reflect.call(data, "getWallpaperParentSubType")).intValue();
        Object uri = Reflect.call(data, "getOriginalUri");
        if (uri == null) uri = Reflect.call(data, "getUri");
        if (uri == null) uri = Reflect.call(data, "getStorageFilePath");
        String key = uri == null ? "" : uri.toString();
        if (DepthSourcePolicy.image(type, parent) && key.isEmpty()) return null;
        return new Source(data, key, DepthSourcePolicy.image(type, parent) && !key.isEmpty());
    }
}
