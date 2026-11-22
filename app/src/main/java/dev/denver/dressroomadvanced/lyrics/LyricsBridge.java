package dev.denver.dressroomadvanced.lyrics;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Asynchronous IPC client bridge used inside SystemUI to request lyrics
 * from the module app's {@code SettingsProvider} without blocking the main UI thread.
 */
public final class LyricsBridge {
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.denver.dressroomadvanced.settings");
    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DressroomLyricsBridge");
        t.setDaemon(true);
        return t;
    });

    private static volatile LyricsEntry inMemoryCachedEntry;
    private static volatile String inMemoryCacheKey = "";

    private LyricsBridge() {}

    public static LyricsEntry inMemoryCurrent() {
        return inMemoryCachedEntry;
    }

    public static void requestLyrics(
            Context context,
            String artist,
            String title,
            String album,
            long durationMs,
            Consumer<LyricsEntry> callback) {

        if (context == null) {
            if (callback != null) callback.accept(null);
            return;
        }

        String requestKey = LyricsCache.cacheKey(artist, title) + "|" + album + "|" + durationMs;
        LyricsEntry current = inMemoryCachedEntry;
        if (current != null && requestKey.equals(inMemoryCacheKey) && current.matchesDuration(durationMs)) {
            if (callback != null) callback.accept(current);
            return;
        }

        WORKER.execute(() -> {
            LyricsEntry result = null;
            try {
                Bundle query = new Bundle();
                query.putString("artist", artist);
                query.putString("title", title);
                query.putString("album", album);
                query.putLong("durationMs", durationMs);

                Bundle response = context.getContentResolver().call(SETTINGS_URI, "getLyrics", null, query);
                if (response != null) {
                    result = LyricsEntry.fromBundle(response);
                }
            } catch (Exception e) {
                android.util.Log.w("LyricsBridge", "Failed to fetch lyrics via IPC", e);
            }

            final LyricsEntry finalResult = result != null
                    ? result
                    : LyricsEntry.notFound(artist, title, album, durationMs);

            inMemoryCachedEntry = finalResult;
            inMemoryCacheKey = requestKey;

            if (callback != null) {
                Main.HANDLER.post(() -> callback.accept(finalResult));
            }
        });
    }

    public static void clearInMemory() {
        inMemoryCachedEntry = null;
        inMemoryCacheKey = "";
    }
}
