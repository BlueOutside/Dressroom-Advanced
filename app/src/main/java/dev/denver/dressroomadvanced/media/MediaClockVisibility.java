package dev.denver.dressroomadvanced;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class MediaClockVisibility {
    static final String KEY = "hideClockInExpandedPlayer";
    private static final String BLUR_TAG = "lock_clock_blur";
    private static final Map<View, Window> windows = new WeakHashMap<>();
    private static final Map<View, Window> clockWindows = new WeakHashMap<>();
    private static final Set<View> expanded = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> dozing = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean observing;
    private static boolean lyricsMode;

    public static void setLyricsMode(boolean active) {
        if (lyricsMode == active) return;
        lyricsMode = active;
        refreshAll();
    }

    public static boolean isLyricsMode() {
        return lyricsMode;
    }

    private static final class Window {
        final Set<View> clocks = Collections.newSetFromMap(new WeakHashMap<>());
        final Set<View> blurHosts = Collections.newSetFromMap(new WeakHashMap<>());
        final Map<View, Float> transitionAlphas = new WeakHashMap<>();
        final Map<View, WeakReference<Drawable>> hiddenBlur = new WeakHashMap<>();
        WeakReference<View> owner = new WeakReference<>(null);
        boolean hidden;
    }

    private static final View.OnAttachStateChangeListener attachment =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}

                @Override public void onViewDetachedFromWindow(View view) {
                    Window window = clockWindows.get(view);
                    if (window == null) return;
                    window.clocks.remove(view);
                    show(window, view);
                    if (window.owner.get() == view) {
                        window.owner.clear();
                        for (View clock : window.clocks) {
                            if (expanded.contains(clock)) {
                                window.owner = new WeakReference<>(clock);
                                break;
                            }
                        }
                        refresh(view.getRootView(), window);
                    }
                }
            };

    private MediaClockVisibility() {}

    static void attach(View clock) {
        if (!clock.isAttachedToWindow()) return;
        View root = clock.getRootView();
        Window window = windows.computeIfAbsent(root, ignored -> new Window());
        clockWindows.put(clock, window);
        window.clocks.add(clock);
        if (tracked.add(clock)) clock.addOnAttachStateChangeListener(attachment);
        if (!observing) {
            RuntimeExtras.listen(MediaClockVisibility::refreshAll);
            observing = true;
        }
        if (expanded.contains(clock)) window.owner = new WeakReference<>(clock);
        if (window.owner.get() != null) refresh(root, window);
    }

    static void update(View clock, boolean opening) {
        boolean wasExpanded = expanded.contains(clock);
        if (opening) expanded.add(clock);
        else expanded.remove(clock);
        Window window = clockWindows.get(clock);
        if (opening && clock.isAttachedToWindow() && (window == null || !window.clocks.contains(clock))) {
            attach(clock);
            return;
        }
        if (window == null) return;
        if (opening) window.owner = new WeakReference<>(clock);
        else if (wasExpanded && window.clocks.contains(clock)) {
            // The page can return with a different clock than the one that opened it.
            window.owner.clear();
            expanded.removeAll(window.clocks);
        } else return;
        if (clock.isAttachedToWindow()) refresh(clock.getRootView(), window);
        else refreshAll();
    }

    static void dozing(View clock, boolean value) {
        if (value) dozing.add(clock);
        else dozing.remove(clock);
        Window window = clockWindows.get(clock);
        if (window != null && window.owner.get() != null && clock.isAttachedToWindow())
            refresh(clock.getRootView(), window);
    }

    static boolean hidden(View view) {
        Window window = windows.get(view.getRootView());
        return window != null && window.hidden;
    }

    static void blurSubmitted(View host) {
        if (!host.isAttachedToWindow()) return;
        Window window = windows.get(host.getRootView());
        if (window == null) return;
        window.blurHosts.add(host);
        if (window.hidden) hideBlur(window, host);
    }

    private static void refreshAll() {
        windows.forEach(MediaClockVisibility::refresh);
    }

    private static void refresh(View root, Window window) {
        View owner = window.owner.get();
        boolean wasHidden = window.hidden;
        window.hidden = lyricsMode
                || (owner != null && !window.clocks.isEmpty()
                    && RuntimeExtras.enabled(root.getContext(), KEY)
                    && (!dozing.contains(owner) || WallpaperAod.shown(root.getContext())));
        if (window.hidden) {
            for (View view : window.clocks) hide(window, view);
            if (!lyricsMode) {
                View tagged = root.findViewWithTag(BLUR_TAG);
                if (tagged != null) window.blurHosts.add(tagged);
                for (View view : window.blurHosts) hideBlur(window, view);
            }
        } else if (!window.transitionAlphas.isEmpty()) {
            for (View view : window.clocks) show(window, view);
            for (View view : window.blurHosts) show(window, view);
        }
        if (wasHidden != window.hidden) MediaFullscreenExtras.clockVisibilityChanged(root);
    }

    private static void hide(Window window, View view) {
        // Transition alpha is separate from the alpha and visibility Samsung animates.
        window.transitionAlphas.putIfAbsent(view, view.getTransitionAlpha());
        view.setTransitionAlpha(0f);
    }

    private static void hideBlur(Window window, View host) {
        if (!host.isAttachedToWindow()) return;
        hide(window, host);
        // Window blur also has a region registered with the compositor.
        if (!BlurAppearance.hasWindowDrawable(host)) return;
        Drawable blur = host.getBackground();
        if (!blur.isVisible()) return;
        window.hiddenBlur.put(host, new WeakReference<>(blur));
        blur.setVisible(false, false);
    }

    private static void show(Window window, View view) {
        Float alpha = window.transitionAlphas.remove(view);
        if (alpha != null && view.getTransitionAlpha() == 0f) view.setTransitionAlpha(alpha);
        var reference = window.hiddenBlur.remove(view);
        Drawable blur = reference == null ? null : reference.get();
        if (blur != null && blur == view.getBackground()
                && view.isShown() && view.getWindowVisibility() == View.VISIBLE)
            blur.setVisible(true, false);
    }
}
