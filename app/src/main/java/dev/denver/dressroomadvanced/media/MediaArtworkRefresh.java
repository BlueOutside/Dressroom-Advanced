package dev.denver.dressroomadvanced;

import android.app.Notification;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lets native media loading publish immediately and pick up downloaded art afterwards. */
final class MediaArtworkRefresh {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, Entry> notifications = new LinkedHashMap<>();
    private static final Map<String, Refresh> queued = new LinkedHashMap<>();
    private record Entry(WeakReference<Object> owner, Method reload,
            StatusBarNotification notification, MediaSession.Token token) {}
    private record Refresh(Context context, AnimatedCoverProvider.TrackQuery query, String uri) {}

    private MediaArtworkRefresh() {}

    static void install(DressroomModule module, ClassLoader loader) {
        try (HookTransaction hooks = new HookTransaction()) {
            int count = 0;
            for (String name : new String[] {
                    "com.android.systemui.media.controls.domain.pipeline.MediaDataProcessor",
                    "com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor",
                    "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl"}) {
                Method added, removed;
                try {
                    Class<?> processor = Class.forName(name, false, loader);
                    added = processor.getDeclaredMethod("onNotificationAdded", String.class,
                            StatusBarNotification.class);
                    removed = processor.getDeclaredMethod("onNotificationRemoved", String.class);
                    if (added.getReturnType() != void.class || Modifier.isAbstract(added.getModifiers())
                            || removed.getReturnType() != void.class) continue;
                } catch (ClassNotFoundException | NoSuchMethodException absent) {
                    continue;
                }
                added.setAccessible(true);
                int index = ++count;
                hooks.add(module.hook(added).setId("dressroom-advanced.artwork-refresh-source-" + index)
                        .intercept(chain -> {
                            try {
                                StatusBarNotification notification = (StatusBarNotification) chain.getArg(1);
                                MediaSession.Token token = notification == null ? null
                                        : notification.getNotification().extras.getParcelable(
                                                Notification.EXTRA_MEDIA_SESSION, MediaSession.Token.class);
                                synchronized (notifications) {
                                    String key = (String) chain.getArg(0);
                                    if (token == null) notifications.remove(key);
                                    else {
                                        notifications.put(key, new Entry(new WeakReference<>(chain.getThisObject()),
                                                added, notification, token));
                                        while (notifications.size() > 8)
                                            notifications.remove(notifications.keySet().iterator().next());
                                    }
                                }
                            } catch (RuntimeException error) {
                                DressroomModule.note("Media artwork notification tracking unavailable", error);
                            }
                            return chain.proceed();
                        })::unhook);
                hooks.add(module.hook(removed).setId("dressroom-advanced.artwork-refresh-remove-" + index)
                        .intercept(chain -> {
                            synchronized (notifications) { notifications.remove((String) chain.getArg(0)); }
                            return chain.proceed();
                        })::unhook);
                module.deoptimize(added);
                module.deoptimize(removed);
            }
            if (count == 0) throw new NoSuchMethodException("Native media notification callbacks changed");
            hooks.commit();
            android.util.Log.i(DressroomModule.TAG, "Asynchronous media artwork refresh installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.report("Optional asynchronous media artwork refresh unavailable", unavailable);
        }
    }

    static boolean current(Context context, MediaSession.Token token,
            AnimatedCoverProvider.TrackQuery query) {
        if (context == null || token == null || query == null) return false;
        try {
            MediaController controller = new MediaController(context, token);
            return matchesTrack(query,
                    LiveMediaTracker.queryFromMetadata(controller, controller.getMetadata()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void track(Context context, AnimatedCoverProvider.TrackQuery query) {
        refresh(context, query, null);
    }

    static void uri(Context context, String uri) {
        refresh(context, null, uri);
    }

    private static void refresh(Context context, AnimatedCoverProvider.TrackQuery query, String uri) {
        if (context == null) return;
        main.post(() -> {
            ArrayList<Map.Entry<String, Entry>> snapshot;
            synchronized (notifications) { snapshot = new ArrayList<>(notifications.entrySet()); }
            for (var candidate : snapshot) {
                String key = candidate.getKey();
                Entry entry = candidate.getValue();
                if (!matches(context, entry, query, uri)) continue;
                if (queued.put(key, new Refresh(context, query, uri)) != null) continue;
                main.postDelayed(() -> {
                    Refresh request = queued.remove(key);
                    Entry latest;
                    synchronized (notifications) { latest = notifications.get(key); }
                    if (request == null || latest == null
                            || !matches(request.context, latest, request.query, request.uri)) return;
                    Object owner = latest.owner.get();
                    if (owner == null) return;
                    try {
                        latest.reload.invoke(owner, key, latest.notification);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("Media artwork refresh unavailable", error);
                    }
                }, 80);
            }
        });
    }

    private static boolean matches(Context context, Entry entry,
            AnimatedCoverProvider.TrackQuery query, String uri) {
        try {
            MediaController controller = new MediaController(context, entry.token);
            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) return false;
            if (query != null)
                return matchesTrack(query,
                        LiveMediaTracker.queryFromMetadata(controller, metadata));
            for (String key : new String[] {MediaMetadata.METADATA_KEY_ART_URI,
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI})
                if (uri != null && uri.equals(metadata.getString(key))) return true;
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static boolean matchesTrack(AnimatedCoverProvider.TrackQuery expected,
            AnimatedCoverProvider.TrackQuery current) {
        return expected != null && current != null && LiveMediaTracker.sameTrack(expected, current)
                && (expected.artworkUri().isEmpty() || current.artworkUri().isEmpty()
                        || expected.artworkUri().equals(current.artworkUri()));
    }
}
