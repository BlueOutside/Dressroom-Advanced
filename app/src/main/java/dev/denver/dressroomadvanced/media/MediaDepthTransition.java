package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Wallpaper masks do not belong on the media clock; restore them after its native return. */
final class MediaDepthTransition {
    private static final Map<View, Return> pending = new WeakHashMap<>();

    private MediaDepthTransition() {}

    static void screenChanged() {
        for (View clock : new java.util.ArrayList<>(pending.keySet())) {
            cancel(clock);
            DepthEffects.media(clock, false);
        }
    }

    static void update(View clock, boolean expanded, Function<View, Animator> animation) {
        cancel(clock);
        if (expanded) {
            DepthEffects.media(clock, true);
        } else if (DepthEffects.mediaActive(clock)) {
            Return request = new Return(clock, animation);
            pending.put(clock, request);
            // Samsung creates the return animator after setMediaExpanded(false) returns.
            clock.postOnAnimation(request);
        }
    }

    private static void cancel(View clock) {
        if (pending.remove(clock) != null) MediaReturnCompletion.cancel(clock);
    }

    private static final class Return implements Runnable {
        final WeakReference<View> clock;
        final Function<View, Animator> animation;

        Return(View clock, Function<View, Animator> animation) {
            this.clock = new WeakReference<>(clock);
            this.animation = animation;
        }

        @Override
        public void run() {
            View view = clock.get();
            if (view == null || pending.get(view) != this) return;
            try {
                if (MediaReturnCompletion.await(
                        view,
                        animation.apply(view),
                        () -> {
                            View current = clock.get();
                            if (current != null) current.postOnAnimation(this::finish);
                        })) return;
            } catch (RuntimeException error) {
                android.util.Log.w(DressroomModule.TAG, "Media depth return unavailable", error);
            }
            finish();
        }

        void finish() {
            View view = clock.get();
            if (view != null && pending.get(view) == this) {
                pending.remove(view);
                MediaReturnCompletion.cancel(view);
                DepthEffects.media(view, false);
            }
        }
    }
}
