package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Objects;

final class DepthAuto {
    private final DressroomModule module;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<View> clock = new WeakReference<>(null),
            image = new WeakReference<>(null);
    private Bundle profile;
    private Object stamp, resolved;
    private boolean enabled;
    private long revision;
    private Runnable check;

    private record Request(long revision, long session, WeakReference<View> image, Object stamp) {}

    private final LatestDepthWork<Request> jobs = new LatestDepthWork<>(this::start);

    DepthAuto(DressroomModule module) {
        this.module = module;
        DepthEffects.listenWallpaper(this::wallpaper);
    }

    void reset() {
        enabled = false;
        revision++;
        stamp = resolved = null;
        profile = null;
        clock.clear();
        image.clear();
        jobs.clear();
        if (check != null) main.removeCallbacks(check);
        check = null;
        DepthEffects.cancelScan();
        DepthEffects.pendingSource(false);
    }

    void bind(View clock, Bundle profile, boolean widgets) {
        if (!DepthEffects.isEditorClock(clock)) return;
        if (!module.depthImageAvailable()) {
            reset();
            return;
        }
        boolean changed =
                this.clock.get() != clock
                        || this.profile == null
                        || profile == null
                        || this.profile.getInt("type", -1) != profile.getInt("type", -1)
                        || this.profile.getInt("font", -1) != profile.getInt("font", -1);
        if (changed) {
            revision++;
            resolved = null;
            jobs.clear();
            if (check != null) {
                main.removeCallbacks(check);
                check = null;
            }
        }
        this.clock = new WeakReference<>(clock);
        this.profile = profile == null ? null : new Bundle(profile);
        boolean active = profile != null && (profile.getBoolean("depthEnabled") || widgets);
        if (!active) {
            if (enabled) {
                enabled = false;
                revision++;
                resolved = null;
                jobs.clear();
                if (check != null) main.removeCallbacks(check);
                check = null;
                DepthEffects.cancelScan();
                DepthEffects.pendingSource(false);
            }
            return;
        }
        if (!enabled) DepthEffects.pendingSource(true);
        enabled = true;
        View wallpaper = DepthEffects.wallpaper(clock);
        if (EditorWallpaperSource.available(wallpaper)) wallpaper(wallpaper);
    }

    void retry() {
        resolved = null;
        revision++;
        jobs.clear();
        schedule(0, true);
    }

    void settings(Bundle settings, boolean widgets) {
        View renderer = clock.get();
        if (renderer != null) bind(renderer, settings, widgets);
    }

    private void wallpaper(View view) {
        if (!enabled) return;
        View renderer = clock.get();
        if (renderer == null || renderer.getWindowToken() != view.getWindowToken()) return;
        if (image.get() != view) {
            View previous = image.get();
            if (EditorWallpaperSource.available(previous)
                    && (long) previous.getWidth() * previous.getHeight()
                            > (long) view.getWidth() * view.getHeight()) return;
            if (DepthEffects.wallpaper(renderer) != view) return;
        }
        try {
            Object current = LiveSubject.stamp(view);
            if (image.get() != view || !Objects.equals(stamp, current)) {
                image = new WeakReference<>(view);
                stamp = current;
                resolved = null;
                revision++;
                jobs.clear();
                DepthEffects.cancelScan();
                DepthEffects.pendingSource(true);
                schedule(350, false);
            } else if (resolved == null && check == null) schedule(350, false);
        } catch (Exception ignored) {
        }
    }

    private void schedule(long delay, boolean force) {
        if (!enabled) return;
        if (check != null) main.removeCallbacks(check);
        long expected = revision;
        check =
                () -> {
                    check = null;
                    if (!enabled || revision != expected) return;
                    View wallpaper = image.get(), renderer = clock.get();
                    if (renderer == null || !EditorWallpaperSource.available(wallpaper)) return;
                    try {
                        String fingerprint = LiveSubject.fingerprint(wallpaper);
                        byte[] live = force ? null : DepthEffects.liveMask(wallpaper);
                        if (live != null) {
                            resolved = stamp;
                            DepthEffects.pendingSource(false);
                            module.depthDetected(
                                    renderer, live, "Subject reused for this clock style");
                            return;
                        }
                        if (!force
                                && fingerprint.equals(profile.getString(DepthMetadata.FINGERPRINT))
                                && profile.getByteArray(DepthMetadata.SOURCE) != null) {
                            DepthEffects.restoreSource(wallpaper, profile, fingerprint);
                            resolved = stamp;
                            DepthEffects.pendingSource(false);
                            module.depthDetected(
                                    renderer,
                                    profile.getByteArray(DepthMetadata.SOURCE),
                                    "Subject restored · follows wallpaper movement");
                            return;
                        }
                        resolved = stamp;
                        DepthEffects.pendingSource(true);
                        module.depthStatus("Detecting the full wallpaper subject…");
                        jobs.offer(
                                new Request(
                                        revision,
                                        module.generation(),
                                        new WeakReference<>(wallpaper),
                                        stamp));
                    } catch (Exception error) {
                        module.invalidateDepth("Depth detection failed: " + error.getMessage());
                    }
                };
        main.postDelayed(check, delay);
    }

    private boolean current(Request request) {
        return enabled
                && request.revision == revision
                && request.session == module.generation()
                && image.get() == request.image.get()
                && Objects.equals(stamp, request.stamp)
                && module.depthImageAvailable();
    }

    private void start(Request request, Runnable complete) {
        View renderer = clock.get();
        if (!current(request) || renderer == null) {
            complete.run();
            return;
        }
        View wallpaper = request.image.get();
        if (wallpaper == null) {
            complete.run();
            return;
        }
        DepthEffects.scan(
                wallpaper.getContext(),
                renderer,
                (mask, message) -> {
                    if (!current(request)) return;
                    if (mask != null) {
                        DepthEffects.pendingSource(false);
                        module.depthDetected(clock.get(), mask, message);
                    } else module.invalidateDepth(message);
                },
                complete);
    }
}
