package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class MediaBlurHost {
    private static final class Opacity {
        float nativeAlpha, factor;

        Opacity(float alpha) {
            nativeAlpha = alpha;
        }
    }

    private static final Map<View, Opacity> held = new WeakHashMap<>();
    private static final Map<View, WeakReference<ViewGroup>> artworkHosts = new WeakHashMap<>();
    private static DressroomModule hookModule;
    private static Runnable alphaLease;
    private static boolean hooksWanted = true;

    static synchronized void install(DressroomModule module) throws ReflectiveOperationException {
        hookModule = module;
        syncHook();
    }

    static synchronized void setHooksEnabled(boolean enabled) {
        if (hooksWanted == enabled) return;
        hooksWanted = enabled;
        syncHook();
    }

    static boolean hasHeld() {
        return !held.isEmpty();
    }

    static synchronized void syncHook() {
        boolean needed = hooksWanted || !held.isEmpty() || MediaClockContrast.hasIcons();
        if (needed && alphaLease == null && hookModule != null) {
            try {
                alphaLease = ViewAlphaHooks.acquire(hookModule);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                hookModule.report("Live clock blur host opacity hook unavailable", error);
            }
        } else if (!needed && alphaLease != null) {
            Runnable lease = alphaLease;
            alphaLease = null;
            try {
                lease.run();
            } catch (RuntimeException | LinkageError error) {
                if (hookModule != null)
                    hookModule.report("Live clock blur host opacity cleanup failed", error);
            }
        }
    }

    static float nativeAlpha(View view, float value) {
        Opacity state = held.get(view);
        if (state != null && !ViewAlphaHooks.isWriting()) {
            state.nativeAlpha = value;
            return value * state.factor;
        }
        return value;
    }

    static float requestedAlpha(View view) {
        Opacity state = held.get(view);
        return state == null ? view.getAlpha() : state.nativeAlpha;
    }

    static float effectiveAlpha(View view, float fallbackNativeAlpha) {
        Opacity state = held.get(view);
        return state == null ? fallbackNativeAlpha : state.nativeAlpha * state.factor;
    }

    static void hold(View view) {
        fade(view, 0f);
    }

    /** Keep native glass until the replacement has an actual drawable frame. */
    static void holdForArtwork(View view, ViewGroup root) {
        if (root == null) return;
        WeakReference<ViewGroup> owner = artworkHosts.get(view);
        if (owner == null || owner.get() != root)
            artworkHosts.put(view, new WeakReference<>(root));
        fade(view, artworkFactor(root));
    }

    static void refreshArtwork(ViewGroup root) {
        if (root == null || artworkHosts.isEmpty()) return;
        float factor = artworkFactor(root);
        for (var entry : artworkHosts.entrySet())
            if (entry.getValue().get() == root) fade(entry.getKey(), factor);
    }

    private static float artworkFactor(ViewGroup root) {
        return MediaClockVisibility.hidden(root) ? 0f : 1f - MediaClockContrast.glassWeight(root);
    }

    static void fade(View view, float factor) {
        Opacity state = held.get(view);
        if (state == null) {
            state = new Opacity(UnlockBlur.requestedClockAlpha(view));
            held.put(view, state);
            syncHook();
        }
        state.factor = Math.max(0f, Math.min(1f, factor));
        float alpha = UnlockBlur.effectiveClockAlpha(view, state.nativeAlpha * state.factor);
        if (view.getAlpha() != alpha) ViewAlphaHooks.write(view, alpha);
    }

    static void release(View view) {
        artworkHosts.remove(view);
        Opacity state = held.remove(view);
        syncHook();
        if (state != null)
            ViewAlphaHooks.write(view, UnlockBlur.effectiveClockAlpha(view, state.nativeAlpha));
    }
}
