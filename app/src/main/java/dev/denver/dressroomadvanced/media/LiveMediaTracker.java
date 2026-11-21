package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks the currently active SystemUI media session metadata for animated cover lookup by
 * combining {@link MediaSessionManager#getActiveSessions} with {@link MediaArtworkUncap}'s
 * {@code MediaDataLoaderResult} notifications. Uses only public Android SDK APIs.
 */
final class LiveMediaTracker {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    private static WeakReference<Context> appContext = new WeakReference<>(null);
    private static MediaSessionManager sessionManager;
    private static MediaController activeController;
    private static boolean managerRegistered;

    private static volatile AnimatedCoverProvider.TrackQuery loaderSnapshot;
    private static volatile AnimatedCoverProvider.TrackQuery latestQuery;
    private static volatile AnimatedCoverProvider.TrackQuery sizedBase;
    private static volatile AnimatedCoverProvider.TrackQuery sizedQuery;
    private static long lastManualRefreshUptimeMs;

    private LiveMediaTracker() {}

    public static MediaController activeController() {
        return activeController;
    }

    private static final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            controllers -> refreshFromControllers(controllers);

    private static final MediaController.Callback controllerCallback =
            new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    refreshSessions(appContext.get());
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    refreshSessions(appContext.get());
                }

                @Override
                public void onSessionDestroyed() {
                    refreshSessions(appContext.get());
                }
            };

    static void onLoaderMetadata(
            Context context,
            String artist,
            String song,
            String album,
            String packageName,
            String artworkUri) {
        if (context != null) ensureStarted(context);
        AnimatedCoverProvider.TrackQuery candidate =
                new AnimatedCoverProvider.TrackQuery(artist, song, album, packageName, artworkUri, 1080);
        if (candidate.isValid()) {
            loaderSnapshot = candidate;
            main.post(() -> refreshSessions(context != null ? context : appContext.get()));
        }
    }

    static AnimatedCoverProvider.TrackQuery currentTrack(Context context, int maxWidthPx) {
        ensureStarted(context);
        long now = SystemClock.uptimeMillis();
        if (!managerRegistered
                || (latestQuery == null && now - lastManualRefreshUptimeMs >= 1000L)) {
            lastManualRefreshUptimeMs = now;
            refreshSessions(context);
        }
        return latestTrack(maxWidthPx);
    }

    static AnimatedCoverProvider.TrackQuery latestTrack(int maxWidthPx) {
        AnimatedCoverProvider.TrackQuery current = latestQuery;
        if (current == null || !current.isValid()) return null;
        if (current.maxWidthPx() == maxWidthPx) return current;
        AnimatedCoverProvider.TrackQuery cached = sizedQuery;
        if (sizedBase == current && cached != null && cached.maxWidthPx() == maxWidthPx) {
            return cached;
        }
        AnimatedCoverProvider.TrackQuery resized = new AnimatedCoverProvider.TrackQuery(
                current.artist(),
                current.song(),
                current.album(),
                current.packageName(),
                current.artworkUri(),
                maxWidthPx);
        sizedBase = current;
        sizedQuery = resized;
        return resized;
    }

    static Bitmap artworkFor(AnimatedCoverProvider.TrackQuery track) {
        MediaController controller = activeController;
        if (controller == null) return null;
        MediaMetadata metadata = controller.getMetadata();
        if (!sameIdentity(track, queryFromMetadata(controller, metadata))) return null;
        Bitmap best = null;
        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap candidate = metadata.getBitmap(key);
            if (candidate != null && !candidate.isRecycled()
                    && (best == null || candidate.getWidth() > best.getWidth())) best = candidate;
        }
        return best;
    }

    static void addListener(Context context, Runnable listener) {
        if (listener == null) return;
        listeners.add(listener);
        ensureStarted(context);
    }

    static void removeListener(Runnable listener) {
        if (listener != null) listeners.remove(listener);
    }

    private static synchronized void ensureStarted(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        appContext = new WeakReference<>(app);
        if (managerRegistered) return;
        try {
            MediaSessionManager mgr = app.getSystemService(MediaSessionManager.class);
            if (mgr == null) return;
            sessionManager = mgr;
            mgr.addOnActiveSessionsChangedListener(sessionsListener, null, main);
            managerRegistered = true;
            refreshFromControllers(mgr.getActiveSessions(null));
        } catch (RuntimeException ignored) {
            managerRegistered = true;
        }
    }

    private static void refreshSessions(Context context) {
        MediaSessionManager mgr = sessionManager;
        if (mgr == null && context != null) {
            try {
                mgr = context.getApplicationContext().getSystemService(MediaSessionManager.class);
            } catch (RuntimeException ignored) {
            }
        }
        if (mgr == null) {
            publish(loaderSnapshot);
            return;
        }
        try {
            refreshFromControllers(mgr.getActiveSessions(null));
        } catch (RuntimeException ignored) {
            publish(loaderSnapshot);
        }
    }

    private static void refreshFromControllers(List<MediaController> controllers) {
        MediaController selected = pickPrimaryController(controllers);
        rebindControllerCallback(selected);
        if (selected != null) {
            AnimatedCoverProvider.TrackQuery fromController = queryFromController(selected);
            if (fromController != null && fromController.isValid()) {
                publish(fromController);
                return;
            }
        }
        if (loaderSnapshot != null && loaderSnapshot.isValid()) {
            publish(loaderSnapshot);
        } else {
            publish(null);
        }
    }

    private static void rebindControllerCallback(MediaController next) {
        if (activeController == next || (activeController != null && next != null
                && activeController.getSessionToken().equals(next.getSessionToken()))) return;
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
            }
        }
        activeController = next;
        if (next != null) {
            try {
                next.registerCallback(controllerCallback, main);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static MediaController pickPrimaryController(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) return null;
        MediaController firstValid = null;
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            AnimatedCoverProvider.TrackQuery q = queryFromController(controller);
            if (q == null || !q.isValid()) continue;
            if (firstValid == null) firstValid = controller;
            PlaybackState state = controller.getPlaybackState();
            int code = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (code == PlaybackState.STATE_PLAYING || code == PlaybackState.STATE_BUFFERING) {
                return controller;
            }
        }
        return firstValid != null ? firstValid : controllers.get(0);
    }

    private static AnimatedCoverProvider.TrackQuery queryFromController(MediaController controller) {
        return queryFromMetadata(controller, controller.getMetadata());
    }

    static AnimatedCoverProvider.TrackQuery queryFromMetadata(MediaController controller, MediaMetadata md) {
        if (md == null) return null;
        String artist = firstNonEmpty(
                md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        String title = firstNonEmpty(
                md.getString(MediaMetadata.METADATA_KEY_TITLE),
                md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String album = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String artUri = firstNonEmpty(
                md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                md.getString(MediaMetadata.METADATA_KEY_ART_URI),
                md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI));
        return new AnimatedCoverProvider.TrackQuery(
                artist, title, album, controller.getPackageName(), artUri, 1080);
    }

    static String packageForToken(Context context, MediaSession.Token token) {
        if (context == null || token == null) return "";
        try {
            return new MediaController(context, token).getPackageName();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static void publish(AnimatedCoverProvider.TrackQuery next) {
        AnimatedCoverProvider.TrackQuery prev = latestQuery;
        if (sameIdentity(prev, next)) return;
        latestQuery = next;
        Runnable notify =
                () -> {
                    for (Runnable listener : listeners) {
                        try {
                            listener.run();
                        } catch (RuntimeException error) {
                            DressroomModule.note("Live media tracker notify", error);
                        }
                    }
                };
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run();
        else main.post(notify);
    }

    private static boolean sameIdentity(
            AnimatedCoverProvider.TrackQuery a, AnimatedCoverProvider.TrackQuery b) {
        return sameTrack(a, b);
    }

    static boolean sameTrack(
            AnimatedCoverProvider.TrackQuery a, AnimatedCoverProvider.TrackQuery b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.playbackKey().equals(b.playbackKey());
    }
}
