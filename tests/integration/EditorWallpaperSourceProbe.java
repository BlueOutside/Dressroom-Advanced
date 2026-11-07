package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;

public final class EditorWallpaperSourceProbe {
    private static int checks;

    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }

    static final class Source extends View {
        boolean shown, attached;
        IBinder token;

        Source(Context context) {
            super(context);
        }

        @Override
        public boolean isShown() {
            return shown;
        }

        @Override
        public boolean isAttachedToWindow() {
            return attached;
        }

        @Override
        public IBinder getWindowToken() {
            return token;
        }
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        Source crop = new Source(context), weather = new Source(context);
        crop.token = weather.token = new Binder();
        check(!EditorWallpaperSource.available(null), "Null source accepted");
        crop.shown = true;
        check(EditorWallpaperSource.available(crop), "Visible static wallpaper rejected");
        crop.shown = false;
        check(!EditorWallpaperSource.available(crop), "Unbound hidden preview accepted");
        EditorWallpaperSource.bind(crop, weather);
        weather.shown = true;
        check(!EditorWallpaperSource.available(crop), "Detached source accepted");
        crop.attached = true;
        check(EditorWallpaperSource.available(crop), "Weather's hidden crop rejected");
        weather.token = new Binder();
        check(!EditorWallpaperSource.available(crop), "Other window's surface accepted");
        weather.token = crop.token;
        weather.shown = false;
        check(!EditorWallpaperSource.available(crop), "Stopped weather surface accepted");
        weather.shown = true;
        EditorWallpaperSource.reset();
        check(
                !EditorWallpaperSource.available(crop),
                "Previous draft weather binding survived reset");
        System.out.println("Editor wallpaper/weather ownership: " + checks + " checks passed");
        System.exit(0);
    }
}
