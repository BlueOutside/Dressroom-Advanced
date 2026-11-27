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
    private String sourceKey = "";
    private boolean enabled;
    private long revision;
    private int settlingRetries, detectionRetries;
    private Runnable check, deadline;
    private Request timedRequest;

    private record Request(long revision, long session, WeakReference<View> image, Object stamp) {}

    private final LatestDepthWork<Request> jobs = new LatestDepthWork<>(this::start);

    DepthAuto(DressroomModule module) {
        this.module = module;
        DepthEffects.listenWallpaper(this::wallpaper);
    }

    void reset() {
        cancelDeadline();
        enabled = false;
        revision++;
        settlingRetries = 0;
        detectionRetries = 0;
        stamp = resolved = null;
        sourceKey = "";
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
            DepthEffects.pendingSource(true);
            return;
        }
        boolean changed =
                this.clock.get() != clock
                        || this.profile == null
                        || profile == null
                        || this.profile.getInt("type", -1) != profile.getInt("type", -1)
                        || this.profile.getInt("font", -1) != profile.getInt("font", -1);
        if (changed) {
            cancelDeadline();
            detectionRetries = 0;
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
                cancelDeadline();
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
        cancelDeadline();
        detectionRetries = 0;
        resolved = null;
        revision++;
        settlingRetries = 0;
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
            String currentKey = DepthEffects.currentSourceKey();
            if (image.get() != view || !Objects.equals(stamp, current)) {
                boolean sameGeometryEffect =
                        image.get() == view
                                && !sourceKey.isEmpty()
                                && sourceKey.equals(currentKey)
                                && LiveSubject.samePhotoGeometry(stamp, current)
                                && (DepthEffects.hasSubjectFor(view)
                                        || (profile != null
                                                && DepthEffects.effectApplied()
                                                && sourceKey.equals(
                                                        profile.getString("depthImageKey", ""))
                                                && profile.getByteArray(DepthMetadata.SOURCE)
                                                        != null));
                boolean sameWallpaper =
                        image.get() == view
                                && (LiveSubject.isSameWallpaper(stamp, current)
                                        || sameGeometryEffect);
                if (sameWallpaper
                        && resolved != null
                        && DepthEffects.liveMask(view) != null) {
                    image = new WeakReference<>(view);
                    stamp = current;
                    resolved = current;
                    sourceKey = currentKey;
                    DepthEffects.pendingSource(false);
                    return;
                }
                cancelDeadline();
                image = new WeakReference<>(view);
                stamp = current;
                sourceKey = currentKey;
                detectionRetries = 0;
                settlingRetries = 0;
                if (!sameWallpaper || DepthEffects.liveMask(view) == null) {
                    resolved = null;
                    revision++;
                    jobs.clear();
                    DepthEffects.cancelScan();
                } else {
                    resolved = current;
                }
                if (!sameWallpaper) {
                    DepthEffects.pendingSource(true);
                }
                schedule(sameWallpaper ? 50 : 350, false);
            } else if (resolved == null && check == null) schedule(350, false);
            else if (resolved != null && DepthEffects.liveMask(view) != null
                    && module.depthImageAvailable()) DepthEffects.pendingSource(false);
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
                    if (renderer == null) return;
                    if (!renderer.isAttachedToWindow()
                            || !EditorWallpaperSource.available(wallpaper)
                            || (wallpaper.getWidth() <= 0 || wallpaper.getHeight() <= 0)) {
                        if (settlingRetries < 5) {
                            settlingRetries++;
                            schedule(250, force);
                        } else {
                            module.depthStatus(
                                    ModuleStrings.getOrFallback(
                                            renderer.getContext(),
                                            R.string.depth_status_preview_unavailable,
                                            "Wallpaper preview is unavailable. Tap Detect wallpaper subject when it is visible."));
                        }
                        return;
                    }
                    try {
                        android.content.Context ctx = renderer.getContext();
                        if (sourceKey.isEmpty()) sourceKey = DepthEffects.currentSourceKey();
                        byte[] live = force ? null : DepthEffects.liveMask(wallpaper);
                        if (live != null) {
                            resolved = stamp;
                            settlingRetries = 0;
                            DepthEffects.pendingSource(false);
                            if (!module.depthDetected(
                                    renderer,
                                    live,
                                    ModuleStrings.getOrFallback(
                                            ctx,
                                            R.string.depth_status_reused,
                                            "Subject reused for this clock style")))
                                retryDetection(
                                        ModuleStrings.getOrFallback(
                                                ctx,
                                                R.string.depth_status_preview_not_ready,
                                                "Clock preview was not ready to receive the subject"));
                            return;
                        }
                        String fingerprint = LiveSubject.fingerprint(wallpaper);
                        String savedFp = profile.getString(DepthMetadata.FINGERPRINT);
                        byte[] savedSource = profile.getByteArray(DepthMetadata.SOURCE);
                        boolean exactMatch = fingerprint.equals(savedFp);
                        String activeKey = DepthEffects.currentSourceKey();
                        boolean effectKeyMatch =
                                DepthEffects.effectApplied()
                                        && !activeKey.isEmpty()
                                        && activeKey.equals(profile.getString("depthImageKey", ""))
                                        && savedFp != null
                                        && !savedFp.isEmpty();
                        if (!force && savedSource != null && (exactMatch || effectKeyMatch)) {
                            String restoreFp = exactMatch ? fingerprint : savedFp;
                            DepthEffects.restoreSource(wallpaper, profile, restoreFp);
                            resolved = stamp;
                            settlingRetries = 0;
                            DepthEffects.pendingSource(false);
                            if (!module.depthDetected(
                                    renderer,
                                    savedSource,
                                    ModuleStrings.getOrFallback(
                                            ctx,
                                            R.string.depth_status_restored,
                                            "Subject restored \u00B7 follows wallpaper movement")))
                                retryDetection(
                                        ModuleStrings.getOrFallback(
                                                ctx,
                                                R.string.depth_status_preview_not_ready,
                                                "Clock preview was not ready to receive the subject"));
                            return;
                        }
                        resolved = stamp;
                        settlingRetries = 0;
                        DepthEffects.pendingSource(true);
                        module.depthStatus(
                                ModuleStrings.getOrFallback(
                                        ctx,
                                        R.string.depth_status_detecting_full,
                                        "Detecting the full wallpaper subject..."));
                        Request request =
                                new Request(
                                        revision,
                                        module.generation(),
                                        new WeakReference<>(wallpaper),
                                        stamp);
                        armDeadline(request);
                        jobs.offer(request);
                    } catch (Exception error) {
                        String msg = error.getMessage();
                        if (settlingRetries < 5
                                && msg != null
                                && (msg.contains("ready") || msg.contains("measured"))) {
                            settlingRetries++;
                            schedule(250, force);
                            return;
                        }
                        module.depthDetectionFailed(
                                ModuleStrings.getOrFallback(
                                        renderer.getContext(),
                                        R.string.depth_status_failed,
                                        "Depth detection failed: %1$s",
                                        msg == null ? "" : msg));
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
        android.content.Context ctx = wallpaper.getContext();
        boolean[] answered = {false};
        DepthEffects.scan(
                ctx,
                renderer,
                wallpaper,
                new DepthEffects.Result() {
                    @Override
                    public void done(byte[] mask, String message) {
                        answered[0] = true;
                        if (!current(request)) return;
                        if (mask != null) {
                            DepthEffects.pendingSource(false);
                            if (module.depthDetected(clock.get(), mask, message)) {
                                detectionRetries = 0;
                            } else
                                retryDetection(
                                        ModuleStrings.getOrFallback(
                                                ctx,
                                                R.string.depth_status_preview_not_ready,
                                                "Clock preview was not ready to receive the subject"));
                        } else module.depthDetectionFailed(message);
                    }

                    @Override
                    public void retry(String message) {
                        answered[0] = true;
                        if (current(request)) retryDetection(message);
                    }
                },
                () -> {
                    if (timedRequest == request) cancelDeadline();
                    if (!answered[0] && current(request))
                        retryDetection(
                                ModuleStrings.getOrFallback(
                                        ctx,
                                        R.string.depth_status_superseded,
                                        "Subject scan was superseded before its result was applied"));
                    complete.run();
                });
    }

    private void cancelDeadline() {
        if (deadline != null) main.removeCallbacks(deadline);
        deadline = null;
        timedRequest = null;
    }

    private void armDeadline(Request request) {
        cancelDeadline();
        timedRequest = request;
        deadline =
                () -> {
                    deadline = null;
                    if (!current(request)) return;
                    View view = clock.get();
                    android.content.Context ctx = view == null ? null : view.getContext();
                    String message =
                            ModuleStrings.getOrFallback(
                                    ctx,
                                    R.string.depth_status_detector_timeout,
                                    "Samsung's subject detector has not finished. If it stays here, restart Dressroom.");
                    DepthEffects.detectionFailure(message);
                    module.depthStatus(message);
                    // Do not interrupt JNI or start another native thread. A late valid result
                    // may still finish; LatestDepthWork keeps only one waiting request.
                };
        main.postDelayed(deadline, 60_000);
    }

    private void retryDetection(String message) {
        DepthEffects.pendingSource(true);
        View view = clock.get();
        android.content.Context ctx = view == null ? null : view.getContext();
        if (++detectionRetries <= 2) {
            resolved = null;
            module.depthStatus(
                    ModuleStrings.getOrFallback(
                            ctx,
                            R.string.depth_status_waiting_preview,
                            "Waiting for the wallpaper preview..."));
            schedule(350, false);
        } else {
            resolved = stamp;
            String failure =
                    ModuleStrings.getOrFallback(
                            ctx,
                            R.string.depth_status_retry_hint,
                            "%1$s. Tap Detect wallpaper subject to retry.",
                            message == null ? "" : message);
            if (DepthEffects.liveMask(image.get()) != null) {
                DepthEffects.detectionFailure(failure);
                module.depthStatus(failure);
            } else module.depthDetectionFailed(failure);
        }
    }
}
