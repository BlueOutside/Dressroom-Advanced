package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** The 8.5 player fades its own content; it has no cover-to-pill movement to reproduce. */
final class OneUi85FullscreenArt {
    private static final Map<View, Source> sources = new WeakHashMap<>();
    private static final Map<ViewGroup, WeakReference<Layer>> layers = new WeakHashMap<>();
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<java.lang.reflect.Method> captureTraces = new java.util.HashSet<>();
    private static final String TRACE = "DR85Cover";
    private static DressroomModule traceModule;

    private OneUi85FullscreenArt() {}

    private static void traceLog(String message) {
        if (traceModule != null)
            traceModule.log(android.util.Log.WARN, DressroomModule.TAG, TRACE + " " + message);
    }

    static synchronized void install(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        traceModule = module;
        OneUi85ArtworkBinding binding = new OneUi85ArtworkBinding(base);
        OneUi85WallpaperFadeBinding wallpaper = new OneUi85WallpaperFadeBinding(base);
        MediaBlurHost.install(module);
        try {
            MediaWallpaperBlur.install(module, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            module.report("Optional legacy wallpaper blur override unavailable", error);
        }
        if (!binding.early) installCaptureTrace(module, loader);
        try {
            MediaClockContrast.install(module, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            module.report("8.5 artwork clock appearance unavailable", error);
        }
        try {
            MediaStatusBarContrast.installPlugin(module, loader);
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError error) {
            module.report("8.5 notification icon artwork contrast unavailable", error);
        }
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(binding.constructor)
                                    .setId("dressroom-advanced.85-artwork-owner")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                try {
                                                    register((View) chain.getArg(1), chain.getThisObject(), binding);
                                                } catch (RuntimeException error) {
                                                    module.report(
                                                            "8.5 artwork view unavailable", error);
                                                }
                                                return result;
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(wallpaper.changed)
                                    .setId("dressroom-advanced.85-artwork-wallpaper-fade")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                try {
                                                    wallpaper.update(chain.getThisObject());
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    module.report(
                                                            "8.5 wallpaper fade unavailable",
                                                            error);
                                                }
                                                return result;
                                            })
                            ::unhook);
            module.deoptimize(wallpaper.changed);
            hooks.commit();
        }
        installed.add(loader);
        RuntimeExtras.listen(
                () -> {
                    for (var reference : layers.values()) {
                        Layer layer = reference.get();
                        if (layer != null) {
                            layer.clearBanner();
                            layer.update();
                            layer.invalidate();
                        }
                    }
                });
        traceLog("8.5 fullscreen artwork trace ready");
    }

    private static void installCaptureTrace(DressroomModule module, ClassLoader loader) {
        try {
            Class<?> callbacks = Class.forName(
                    "com.android.systemui.facewidget.plugin.PluginFaceWidgetManager", false, loader);
            var apply = NativeContract.method(callbacks, "applyBlur", void.class, int.class);
            if (captureTraces.contains(apply)) return;
            module.hook(apply).setId("dressroom-advanced.85-cover-capture-trace")
                    .intercept(chain -> {
                        int amount = (int) chain.getArg(0);
                        for (var reference : layers.values()) {
                            Layer layer = reference.get();
                            if (layer != null && (layer.nativeExpanded || layer.nativeFade)
                                    && layer.trace && layer.lastCapture != amount) {
                                layer.lastCapture = amount;
                                traceLog("SystemUI captured blur amount=" + amount
                                        + " expanded=" + layer.nativeExpanded);
                            }
                        }
                        return chain.proceed();
                    });
            captureTraces.add(apply);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            traceLog("SystemUI captured blur trace unavailable");
        }
    }

    private static void register(View item, Object holder, OneUi85ArtworkBinding binding) {
        View frame = find(item, binding.early ? "facewidget_album_art_container"
                : "now_bar_media_album_art_vi_container");
        View image = find(item, "facewidget_album_art_background");
        if (frame == null || !(image instanceof ImageView art)) return;
        Source source = new Source(item, frame, art, holder, binding);
        sources.put(frame, source);
        try {
            View controlsView = source.controls.get();
            if (controlsView instanceof ViewGroup hostGroup) {
                var lyricsCtrl = dev.denver.dressroomadvanced.lyrics.LyricsController.bind(hostGroup);
                if (lyricsCtrl != null) {
                    boolean lyricsEnabled = RuntimeExtras.enabled(hostGroup.getContext(), "lockscreenLyrics");
                    boolean autoScroll = RuntimeExtras.enabled(hostGroup.getContext(), "lyricsSyncedHighlight");
                    int textSize = RuntimeExtras.get(hostGroup.getContext()).getInt("lyricsTextSize", 16);
                    lyricsCtrl.setPreferences(lyricsEnabled, autoScroll, textSize);
                }
            }
        } catch (Exception ignored) {}
        frame.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                        attach(view);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        for (var reference : layers.values()) {
                            Layer layer = reference.get();
                            if (layer != null) layer.invalidateWindow();
                        }
                    }
                });
        attach(frame);
    }

    static void attach(View view) {
        ensure(view);
    }

    static boolean suppressClockBlur(View host, Object request) {
        if (layers.isEmpty()) return false;
        WeakReference<Layer> reference = layers.get(host.getRootView());
        Layer layer = reference == null ? null : reference.get();
        if (layer == null || !layer.clockBlurHeld || layer.clearingClockBlur
                || !("lock_clock_blur".equals(host.getTag()) || "lock_clock_blur".equals(name(host))))
            return false;
        layer.clockBlurRequests.put(host, request);
        if (layer.clockBlurHosts.add(host)) MediaBlurHost.hold(host);
        return true;
    }

    static void wallpaperFade(View host, ValueAnimator animator, boolean expanded) {
        if (host == null) return;
        Layer layer = ensure(host);
        if (layer != null) {
            View previous = layer.wallpaperHost.get();
            if (previous != host)
                MediaWallpaperBlur.release(previous, layer.nativeFade ? layer.nativeAlpha : 1);
            layer.wallpaperHost = new WeakReference<>(host);
            layer.bindFade(animator, expanded);
        }
    }

    private static Layer ensure(View view) {
        if (!(view.getRootView() instanceof ViewGroup root)
                || !root.getClass()
                        .getName()
                        .equals("com.android.systemui.shade.NotificationShadeWindowView"))
            return null;
        WeakReference<Layer> reference = layers.get(root);
        Layer existing = reference == null ? null : reference.get();
        if (existing != null) return existing;
        Layer layer = new Layer(root);
        layers.put(root, new WeakReference<>(layer));
        root.addView(layer, MediaFullscreenExtras.backdropIndex(root),
                new FrameLayout.LayoutParams(-1, -1));
        layer.bind();
        return layer;
    }

    static float opacity(View view, View root) {
        float alpha = 1;
        for (View current = view; current != root; ) {
            if (current == null || current.getVisibility() != View.VISIBLE) return 0;
            alpha *= Math.max(0, Math.min(1, MediaBlurHost.requestedAlpha(current)));
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return Float.isFinite(alpha) ? alpha : 0;
    }

    private static String name(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return "";
        }
    }

    private static View find(View view, String id) {
        if (name(view).equals(id)) return view;
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), id);
                if (found != null) return found;
            }
        return null;
    }

    private static final class Source {
        final WeakReference<View> item, frame, background, controls, tint, nativeBannerBlur;
        final WeakReference<ImageView> image;
        final WeakReference<Object> holder;
        final java.lang.reflect.Field sourceArtwork;
        final Rect visible = new Rect();
        Drawable lastDrawable;
        Bitmap lastBitmap;

        Source(View item, View frame, ImageView image, Object holder, OneUi85ArtworkBinding binding) {
            this.item = new WeakReference<>(item);
            this.frame = new WeakReference<>(frame);
            this.background = new WeakReference<>(find(item, "nowbar_expandview_background"));
            this.controls = new WeakReference<>(find(item, binding.early
                    ? "facewidget_music_container_small_expand" : "facewidget_music_container_expand_Player"));
            this.tint = new WeakReference<>(find(item, "facewidget_music_page_controller_background"));
            this.nativeBannerBlur = new WeakReference<>(find(item, "facewidget_music_page_controller_blur_view"));
            this.image = new WeakReference<>(image);
            this.holder = new WeakReference<>(holder);
            this.sourceArtwork = binding.sourceArtwork;
        }

        Bitmap bitmap() {
            ImageView art = image.get();
            Drawable current = art == null ? null : art.getDrawable();
            if (current == null) {
                lastDrawable = null;
                lastBitmap = null;
                return null;
            }
            if (current == lastDrawable && lastBitmap != null && !lastBitmap.isRecycled())
                return lastBitmap;
            Bitmap bitmap = current instanceof BitmapDrawable drawable ? drawable.getBitmap() : null;
            Object owner = holder.get();
            if (sourceArtwork != null && owner != null) {
                try {
                    if (sourceArtwork.get(owner) instanceof Bitmap source && !source.isRecycled()
                            && source.getWidth() > 1 && source.getHeight() > 1) bitmap = source;
                } catch (IllegalAccessException error) {
                    DressroomModule.note("8.5 source artwork read", error);
                }
            }
            lastDrawable = current;
            lastBitmap = bitmap != null && !bitmap.isRecycled() ? bitmap : null;
            return lastBitmap;
        }
    }

    /** Preserve native alpha/tint writes while suppressing only this card background. */
    static final class Blank extends Drawable {
        final Drawable original;

        Blank(Drawable original) {
            this.original = original;
        }

        @Override
        public void draw(Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {
            original.setAlpha(alpha);
        }

        @Override
        public int getAlpha() {
            return original.getAlpha();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            original.setColorFilter(filter);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            original.setBounds(bounds);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private static final class FadeListener extends AnimatorListenerAdapter
            implements ValueAnimator.AnimatorUpdateListener {
        final WeakReference<Layer> layer;

        FadeListener(Layer layer) {
            this.layer = new WeakReference<>(layer);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            Layer view = layer.get();
            if (view != null) view.sample(animation);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            Layer view = layer.get();
            if (view != null && animation instanceof ValueAnimator value) view.finishFade(value);
        }
    }

    private static final class Layer extends FrameLayout
            implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ViewGroup> root;
        final RectF paletteBounds = new RectF();
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        final Set<View> held = Collections.newSetFromMap(new WeakHashMap<>());
        final Set<View> clockBlurHosts = Collections.newSetFromMap(new WeakHashMap<>());
        final Map<View, Object> clockBlurRequests = new WeakHashMap<>();
        final Map<View, Blank> backgrounds = new WeakHashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fadePaint = new Paint(paint);
        final RectF bounds = new RectF(), bannerBounds = new RectF(), measuredBanner = new RectF();
        final RectF closingCrop = new RectF();
        final android.graphics.Path bannerClip = new android.graphics.Path();
        final android.graphics.Matrix bannerMatrix = new android.graphics.Matrix();
        final android.graphics.Matrix rootInverse = new android.graphics.Matrix();
        final ArtworkBlurFilter.Layers artworkBlur;
        final ArtworkCrossfade songFade;
        final LiveCoverController liveCover;
        final View blurOverlay;
        final RectF clockRect = new RectF();
        boolean blurDirty = true;
        boolean liveBlurDirty;
        boolean lastLiveVisible;
        boolean wasAmbient;
        Bitmap preAmbientNative, wakeHoldBitmap, wakeHoldStale;
        long wakeHoldUntilMs;
        float topBandSolidNorm = MediaFullscreenExtras.DEFAULT_TOP_BAND_SOLID_NORM;
        float topBandEndNorm = MediaFullscreenExtras.DEFAULT_TOP_BAND_END_NORM;
        float topBandSolid, topBandEnd, topBandOpacity;
        float lastClockBottom;
        Source current;
        Bitmap bitmap;
        WeakReference<ValueAnimator> fadeAnimator = new WeakReference<>(null);
        WeakReference<View> wallpaperHost = new WeakReference<>(null);
        FadeListener fadeListener;
        Source bannerSource;
        View bannerBlur;
        boolean nativeFade, nativeExpanded, mediaStateKnown, clockBlurHeld;
        boolean clearingClockBlur;
        boolean trace, tracedSettled, tracedDraw;
        int lastCapture = -1;
        float nativeAlpha;
        float coverAlpha = 1f;
        float bannerRadius;

        Layer(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            setWillNotDraw(false);
            artworkBlur = new ArtworkBlurFilter.Layers(this);
            blurOverlay =
                    new View(root.getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            drawBlurOverlay(canvas);
                        }
                    };
            blurOverlay.setClickable(false);
            blurOverlay.setFocusable(false);
            blurOverlay.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            liveCover =
                    new LiveCoverController(
                            root.getContext(),
                            () -> {
                                if (!liveBlurDirty) {
                                    liveBlurDirty = true;
                                    blurOverlay.invalidate();
                                }
                            });
            songFade = new ArtworkCrossfade(this, () -> {
                blurDirty = true;
                invalidate();
                blurOverlay.invalidate();
            });
            addView(liveCover.hostView(), new FrameLayout.LayoutParams(-1, -1));
            addView(blurOverlay, new FrameLayout.LayoutParams(-1, -1));
            setVisibility(INVISIBLE);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void bind() {
            ViewGroup parent = root.get();
            if (parent == null) return;
            ViewTreeObserver next = parent.getViewTreeObserver(), old = observer.get();
            if (old != next) {
                if (old != null && old.isAlive()) old.removeOnPreDrawListener(this);
                next.addOnPreDrawListener(this);
                observer = new WeakReference<>(next);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            bind();
            liveCover.onAttached();
        }

        @Override
        protected void onDetachedFromWindow() {
            liveCover.onDetached();
            artworkBlur.detach();
            ViewGroup parent = root.get();
            if (parent != null) {
                MediaClockContrast.clear(parent);
                MediaStatusBarContrast.clear(parent);
            }
            clearBanner();
            clearFade();
            nativeExpanded = false;
            mediaStateKnown = false;
            nativeAlpha = 0f;
            restore();
            releaseClockBlur();
            current = null;
            bitmap = null;
            wasAmbient = false;
            topBandOpacity = 0f;
            preAmbientNative = wakeHoldBitmap = wakeHoldStale = null;
            wakeHoldUntilMs = 0L;
            artworkBlur.discardDisplayLists();
            blurDirty = true;
            ViewTreeObserver old = observer.get();
            if (old != null && old.isAlive()) old.removeOnPreDrawListener(this);
            observer.clear();
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onPreDraw() {
            update();
            return true;
        }

        void update() {
            update(RuntimeExtras.enabled(getContext(), "fullscreenArt"));
        }

        void update(boolean enabled) {
            ViewGroup parent = root.get();
            if (parent == null) return;
            trace = enabled;
            updateBanner(parent, enabled);
            if (enabled
                    && (!mediaStateKnown || nativeExpanded)
                    && liveCover.retainOnWallpaperAod(
                            nativeExpanded && current != null ? current.bitmap() : null)) {
                if (!wasAmbient) {
                    preAmbientNative =
                            nativeExpanded && current != null ? current.bitmap() : bitmap;
                }
                wasAmbient = true;
                for (Source source : sources.values()) {
                    View item = source.item.get();
                    if (item != null && item.getRootView() == parent) {
                        hold(source.image.get());
                        hold(find(item, "facewidget_aod_music_album_art_mask"));
                        hold(find(item, "now_bar_media_expand_album_art_aod_mask"));
                    }
                }
                Bitmap ambientArt = liveCover.ambientBitmap();
                Bitmap fallbackArt = nativeExpanded && current != null ? current.bitmap() : null;
                Bitmap synced =
                        ambientArt != null && !ambientArt.isRecycled() ? ambientArt : fallbackArt;
                if (synced != null && !synced.isRecycled() && bitmap != synced) {
                    songFade.update(synced, false);
                    bitmap = synced;
                    blurDirty = true;
                }
                liveCover.hideVideoSurface();
                if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
                liveCover.prepareAmbientClock(parent, this);
                paletteBounds.set(0, 0, parent.getWidth(), parent.getHeight());
                if (updateTopBand(parent, paletteBounds, 1f, false)) blurOverlay.invalidate();
                return;
            }
            boolean exitingAmbient = wasAmbient;
            wasAmbient = false;
            // The native artwork is hidden before the wallpaper-return fade finishes.
            // Keep its last texture until that native fade reaches zero.
            if (enabled
                    && !nativeExpanded
                    && nativeFade
                    && bitmap != null
                    && !bitmap.isRecycled()) {
                liveCover.beginCollapse();
                liveCover.update(enabled, false, true, 0f, 0f, getWidth(), getHeight(),
                        0f, nativeAlpha);
                MediaClockContrast.clear(parent);
                MediaStatusBarContrast.clear(parent);
                paletteBounds.set(0, 0, parent.getWidth(), parent.getHeight());
                if (updateTopBand(parent, paletteBounds, nativeAlpha, true)) {
                    blurOverlay.invalidate();
                }
                present(nativeAlpha);
                if (nativeAlpha <= 0) hide();
                return;
            }
            if (mediaStateKnown && !nativeExpanded) {
                hide(liveCover.ambientHandoff());
                return;
            }
            Source best = null;
            float score = 0, alpha = 0;
            if (enabled)
                for (Source source : sources.values()) {
                    View item = source.item.get();
                    View frame = source.frame.get();
                    if (item == null || item.getRootView() != parent || source.bitmap() == null) continue;
                    boolean frameVisible = frame != null && frame.getGlobalVisibleRect(source.visible) && !source.visible.isEmpty();
                    if (!frameVisible) {
                        if (nativeExpanded) {
                            View bg = source.background.get();
                            View anchor = bg != null ? bg : item;
                            if (anchor.isAttachedToWindow() && anchor.getVisibility() == View.VISIBLE
                                    && anchor.getGlobalVisibleRect(source.visible) && !source.visible.isEmpty()) {
                                // Frame collapsed post-expansion; card anchor is visible
                            } else if (item.isAttachedToWindow() && item.getVisibility() == View.VISIBLE) {
                                source.visible.set(0, 0, parent.getWidth(), parent.getHeight());
                            } else {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    }
                    float value;
                    if (nativeFade) {
                        value = nativeAlpha;
                    } else if (nativeExpanded) {
                        value = 1f;
                    } else {
                        value = opacity(frame != null ? frame : item, parent);
                    }
                    float size = source.visible.width() * (float) source.visible.height() * value;
                    if (size > score) {
                        score = size;
                        best = source;
                        alpha = value;
                    }
                }
            if (best == null) {
                // AOD can hide or detach the artwork view once the page has opened.
                // Keep the last valid texture until the native expanded state clears.
                if (enabled && nativeExpanded && bitmap != null && !bitmap.isRecycled()) {
                    float retainedAlpha = nativeFade ? nativeAlpha : 1f;
                    present(retainedAlpha);
                    syncLiveCover(enabled, parent, retainedAlpha);
                    return;
                }
                hide();
                return;
            }
            if (best != current) {
                restore();
                current = best;
                collect(best);
            }
            // Samsung can replace the background on a doze/color change.
            suppressBackground(best.background.get());
            Bitmap next = best.bitmap();
            boolean releasedWakeHold = false;
            if (exitingAmbient) {
                Bitmap wakeArt = liveCover.consumeWakeAmbientArt();
                if (wakeArt != null
                        && !wakeArt.isRecycled()
                        && preAmbientNative != null
                        && next == preAmbientNative) {
                    wakeHoldBitmap = wakeArt;
                    wakeHoldStale = preAmbientNative;
                    wakeHoldUntilMs =
                            android.os.SystemClock.uptimeMillis()
                                    + MediaFullscreenExtras.WAKE_HOLD_TIMEOUT_MS;
                }
                preAmbientNative = null;
            }
            if (wakeHoldBitmap != null) {
                if (!wakeHoldBitmap.isRecycled()
                        && MediaFullscreenExtras.shouldHoldWakeArtwork(
                                true,
                                next == wakeHoldStale,
                                android.os.SystemClock.uptimeMillis(),
                                wakeHoldUntilMs)) {
                    next = wakeHoldBitmap;
                } else {
                    wakeHoldBitmap = null;
                    wakeHoldStale = null;
                    wakeHoldUntilMs = 0L;
                    releasedWakeHold = true;
                }
            }
            if (bitmap != next) {
                songFade.update(
                        next,
                        bitmap != null
                                && nativeExpanded
                                && !nativeFade
                                && !exitingAmbient
                                && !releasedWakeHold);
                bitmap = next;
                blurDirty = true;
                invalidate();
                blurOverlay.invalidate();
            }
            paletteBounds.set(0, 0, parent.getWidth(), parent.getHeight());
            try {
                MediaClockContrast.apply(parent, bitmap, paletteBounds, blurOverlay);
            } catch (RuntimeException error) {
                MediaClockContrast.clear(parent);
            }
            try {
                MediaStatusBarContrast.apply(parent, bitmap, paletteBounds);
            } catch (RuntimeException error) {
                MediaStatusBarContrast.clear(parent);
            }
            if (updateTopBand(parent, paletteBounds, alpha, !nativeExpanded)) {
                blurOverlay.invalidate();
            }
            present(alpha);
            syncLiveCover(enabled, parent, alpha);
        }

        private boolean updateTopBand(
                ViewGroup parent, RectF coverBounds, float artworkAlpha, boolean closing) {
            boolean disabled =
                    MediaFullscreenExtras.isTopBlurDisabled(
                            RuntimeExtras.enabled(getContext(), MediaClockVisibility.KEY),
                            MediaClockVisibility.hidden(parent));
            if (disabled || artworkAlpha <= .005f || coverBounds.isEmpty()) {
                boolean changed = topBandOpacity != 0f;
                topBandOpacity = 0f;
                return changed;
            }
            boolean hasClock = MediaClockContrast.clockBounds(parent, clockRect);
            float density = getResources().getDisplayMetrics().density;
            float solid, end;
            if (hasClock && clockRect.bottom > 0f) {
                lastClockBottom = clockRect.bottom;
                float[] range =
                        ArtworkBlurFilter.topBandRange(
                                coverBounds.top, clockRect.bottom, density, coverBounds.bottom);
                solid = range[0];
                end = range[1];
                if (!closing && parent.getHeight() > 0 && end > solid) {
                    topBandSolidNorm = Math.max(0f, Math.min(1f, solid / parent.getHeight()));
                    topBandEndNorm = Math.max(0f, Math.min(1f, end / parent.getHeight()));
                }
            } else {
                solid = coverBounds.top + topBandSolidNorm * coverBounds.height();
                end = coverBounds.top + topBandEndNorm * coverBounds.height();
            }
            float opacity = end > solid ? 1f : 0f;
            boolean changed =
                    topBandSolid != solid || topBandEnd != end || topBandOpacity != opacity;
            topBandSolid = solid;
            topBandEnd = end;
            topBandOpacity = opacity;
            return changed;
        }

        private void syncLiveCover(boolean enabled, ViewGroup parent, float alpha) {
            boolean expandedVisible =
                    enabled && nativeExpanded && bitmap != null && !bitmap.isRecycled();
            boolean closing = !nativeExpanded;
            int w = getWidth() > 0 ? getWidth() : parent.getWidth();
            int h = getHeight() > 0 ? getHeight() : parent.getHeight();
            liveCover.update(enabled, expandedVisible, closing, 0f, 0f, w, h, 0f, alpha);
            boolean liveNow = liveCover.isVideoVisible();
            if (lastLiveVisible != liveNow) {
                lastLiveVisible = liveNow;
                blurDirty = true;
                blurOverlay.invalidate();
            }
        }

        void present(float alpha) {
            if (alpha > 0) {
                MediaWallpaperBlur.hold(wallpaperHost.get());
                ViewGroup parent = root.get();
                if (!clockBlurHeld && parent != null) {
                    clockBlurHeld = true;
                    View host = parent.findViewWithTag("lock_clock_blur");
                    if (host == null) host = find(parent, "lock_clock_blur");
                    if (host != null) holdClockBlur(host);
                }
            } else {
                MediaWallpaperBlur.release(wallpaperHost.get(), 0);
                releaseClockBlur();
            }
            if (coverAlpha != alpha) {
                coverAlpha = alpha;
                invalidate();
                blurOverlay.invalidate();
            }
            int visibility = alpha > 0 ? VISIBLE : INVISIBLE;
            if (getVisibility() != visibility) setVisibility(visibility);
            traceSettled();
        }

        void holdClockBlur(View host) {
            if (clockBlurHosts.add(host)) MediaBlurHost.hold(host);
            clockBlurRequests.put(host, BlurAppearance.nativeRequest(host));
            // Samsung also animates the blur drawable's own compositor opacity.
            // Clear the existing region and retain later glyph requests for the return.
            clearingClockBlur = true;
            try {
                BlurAppearance.submitClockRequest(host, null);
            } finally {
                clearingClockBlur = false;
            }
        }

        void releaseClockBlur() {
            if (!clockBlurHeld) return;
            clockBlurHeld = false;
            for (View host : clockBlurHosts) {
                MediaBlurHost.release(host);
                if (host.isAttachedToWindow()) {
                    Object request = clockBlurRequests.get(host);
                    if (request != null) BlurAppearance.submitClockRequest(host, request);
                }
            }
            clockBlurHosts.clear();
            clockBlurRequests.clear();
        }

        void hide() {
            hide(false);
        }

        void hide(boolean immediateContrast) {
            liveCover.reset();
            songFade.clear();
            lastLiveVisible = false;
            releaseClockBlur();
            if (bitmap == null && current == null && getVisibility() == INVISIBLE) return;
            ViewGroup parent = root.get();
            if (parent != null) {
                if (immediateContrast) MediaClockContrast.clearImmediate(parent);
                else MediaClockContrast.clear(parent);
                MediaStatusBarContrast.clear(parent);
            }
            restore();
            current = null;
            bitmap = null;
            wasAmbient = false;
            topBandOpacity = 0f;
            preAmbientNative = wakeHoldBitmap = wakeHoldStale = null;
            wakeHoldUntilMs = 0L;
            artworkBlur.discardDisplayLists();
            blurDirty = true;
            setVisibility(INVISIBLE);
            if (immediateContrast) invalidateWindow();
            traceSettled();
        }

        void traceSettled() {
            if (!trace || !nativeExpanded || nativeFade || tracedSettled) return;
            tracedSettled = true;
            View host = wallpaperHost.get();
            Drawable background = host == null ? null : host.getBackground();
            Rect visible = new Rect();
            boolean inClip = getGlobalVisibleRect(visible);
            traceLog("settled alpha=" + coverAlpha + " viewAlpha=" + getAlpha()
                    + " visibility=" + getVisibility()
                    + " bitmap=" + (bitmap != null && !bitmap.isRecycled())
                    + " size=" + getWidth() + "x" + getHeight()
                    + " shown=" + isShown() + " inClip=" + inClip + " clockBlurHosts=" + clockBlurHosts.size()
                    + " hostBackground=" + (background == null ? "none" : background.getClass().getSimpleName())
                    + " capturedAmount=" + lastCapture);
        }

        void updateBanner(ViewGroup parent, boolean fullscreen) {
            boolean enabled = fullscreen || RuntimeExtras.enabled(getContext(), NowBarGlass.KEY);
            if (!enabled || (!nativeExpanded && nativeAlpha <= 0)) {
                clearBanner();
                return;
            }
            if (bannerSource != null) {
                View controls = bannerSource.controls.get();
                if (controls != null && controls.getRootView() == parent) {
                    if (fullscreen == (bannerBlur == null)) {
                        measureBanner(parent, controls);
                        return;
                    }
                } else if (fullscreen && !nativeExpanded && !bannerBounds.isEmpty()) {
                    return;
                }
                clearBanner();
            }
            if (!nativeExpanded) return;
            for (Source source : sources.values()) {
                if (!(source.controls.get() instanceof FrameLayout controls)
                        || controls.getRootView() != parent || !controls.isShown()
                        || source.tint.get() == null) continue;
                bannerSource = source;
                bannerBlur = fullscreen ? null : BannerBlur.attach(controls);
                if (fullscreen || bannerBlur != null) {
                    MediaBlurHost.hold(source.tint.get());
                    View nativeBlur = source.nativeBannerBlur.get();
                    if (nativeBlur != null) MediaBlurHost.hold(nativeBlur);
                    measureBanner(parent, controls);
                }
                return;
            }
        }

        private void measureBanner(ViewGroup parent, View controls) {
            if (!controls.isShown() || controls.getWidth() <= 0 || controls.getHeight() <= 0)
                return;
            bannerMatrix.reset();
            parent.transformMatrixToGlobal(bannerMatrix);
            if (!bannerMatrix.invert(rootInverse)) return;
            bannerMatrix.reset();
            controls.transformMatrixToGlobal(bannerMatrix);
            bannerMatrix.postConcat(rootInverse);
            measuredBanner.set(0, 0, controls.getWidth(), controls.getHeight());
            bannerMatrix.mapRect(measuredBanner);
            if (bannerBounds.equals(measuredBanner)) return;
            if (bannerBounds.isEmpty()) bannerRadius = BannerBlur.radius(controls);
            bannerBounds.set(measuredBanner);
            bannerClip.rewind();
            bannerClip.addRoundRect(bannerBounds, bannerRadius, bannerRadius,
                    android.graphics.Path.Direction.CW);
            blurOverlay.invalidate();
        }

        void clearBanner() {
            if (bannerSource != null) {
                View tint = bannerSource.tint.get();
                if (tint != null) MediaBlurHost.release(tint);
                View nativeBlur = bannerSource.nativeBannerBlur.get();
                if (nativeBlur != null) MediaBlurHost.release(nativeBlur);
                if (bannerBlur != null && bannerBlur.getParent() instanceof ViewGroup group)
                    group.removeView(bannerBlur);
            }
            bannerSource = null;
            bannerBlur = null;
            bannerBounds.setEmpty();
            bannerClip.rewind();
        }

        void invalidateWindow() {
            ViewGroup parent = root.get();
            if (parent != null) parent.invalidate();
        }

        void clearFade() {
            ValueAnimator old = fadeAnimator.get();
            if (old != null && fadeListener != null) {
                old.removeUpdateListener(fadeListener);
                old.removeListener(fadeListener);
            }
            fadeAnimator.clear();
            fadeListener = null;
            nativeFade = false;
        }

        void bindFade(ValueAnimator animation, boolean expanded) {
            ValueAnimator old = fadeAnimator.get();
            boolean changed = nativeExpanded != expanded;
            if (!mediaStateKnown || changed) {
                tracedSettled = false;
                tracedDraw = false;
                lastCapture = -1;
                if (trace) traceLog("native expanded=" + expanded
                        + " fadeStarted=" + (animation != null && animation.isStarted())
                        + " retainedBitmap=" + (bitmap != null));
            }
            mediaStateKnown = true;
            nativeExpanded = expanded;
            if (!expanded && (liveCover.isAmbientVisible() || liveCover.ambientHandoff())) {
                clearFade();
                nativeAlpha = 0f;
                liveCover.releaseAmbient();
                hide(true);
                invalidateWindow();
                return;
            }
            if (animation == null || !animation.isStarted() || (changed && old == animation)) {
                clearFade();
                nativeAlpha = expanded ? 1f : 0f;
                invalidateWindow();
                return;
            }
            if (old != animation) {
                clearFade();
                fadeAnimator = new WeakReference<>(animation);
                fadeListener = new FadeListener(this);
                animation.addUpdateListener(fadeListener);
                animation.addListener(fadeListener);
            }
            nativeFade = true;
            sample(animation);
        }

        void sample(ValueAnimator animation) {
            if (fadeAnimator.get() != animation) return;
            LockscreenFrameBoost.pulse(this, 220L);
            // The raw integer is not initialized until the first animator frame. The native
            // interpolated fraction starts at zero and follows the same 0<->255 curve.
            float fraction = Math.max(0, Math.min(1, animation.getAnimatedFraction()));
            nativeAlpha = nativeExpanded ? fraction : 1 - fraction;
            // Do not inspect/remove sources inside Samsung's observer dispatch. Its view tree
            // and replacement animator are only consistent when that dispatch has finished.
            invalidateWindow();
        }

        void finishFade(ValueAnimator animation) {
            if (fadeAnimator.get() != animation) return;
            // The wallpaper animator can be cancelled or reset by Samsung. The media
            // state still owns the settled cover, so don't keep its last sampled fraction.
            nativeAlpha = nativeExpanded ? 1f : 0f;
            clearFade();
            if (trace) traceLog("fade completed expanded=" + nativeExpanded);
            // Parent invalidation alone can reuse the cover's previous display list.
            // Submit the fully opaque frame from the cover itself, after this traversal.
            if (nativeExpanded && isAttachedToWindow()) postInvalidateOnAnimation();
            else invalidateWindow();
        }

        void collect(Source source) {
            View item = source.item.get(), frame = source.frame.get();
            hold(frame);
            if (item != null) {
                for (String id :
                        new String[] {
                            "facewidget_music_background",
                            "facewidget_aod_music_album_art_mask",
                            "now_bar_media_expand_album_art_aod_mask"
                        }) hold(find(item, id));
                suppressBackground(source.background.get());
                for (View parent = item;
                        parent != null;
                        parent = parent.getParent() instanceof View up ? up : null) {
                    if (parent.getClass()
                            .getName()
                            .equals(
                                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView")) {
                        hold(find(parent, "nowbar_cardview_bgview_for_media_setting"));
                        break;
                    }
                }
            }
        }

        void hold(View view) {
            if (view != null && held.add(view)) MediaBlurHost.hold(view);
        }

        void suppressBackground(View view) {
            if (view == null
                    || view.getBackground() == null
                    || view.getBackground() instanceof Blank) return;
            Blank blank = new Blank(view.getBackground());
            backgrounds.put(view, blank);
            view.setBackground(blank);
        }

        void restore() {
            MediaWallpaperBlur.release(wallpaperHost.get(), nativeFade ? nativeAlpha : 1);
            for (View view : held) MediaBlurHost.release(view);
            held.clear();
            for (var entry : backgrounds.entrySet())
                if (entry.getKey().getBackground() == entry.getValue())
                    entry.getKey().setBackground(entry.getValue().original);
            backgrounds.clear();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (liveCover.drawAmbient(canvas, root.get(), this)) return;
            if (bitmap == null || bitmap.isRecycled()) return;
            bounds.set(0, 0, getWidth(), getHeight());
            // Keep the view opaque in HWUI. Changing View.alpha at 1 can switch off
            // its compositing layer exactly when the native blur regions settle.
            fadePaint.setAlpha(Math.round(coverAlpha * 255));
            drawCover(canvas, bounds, fadePaint);
            if (trace && nativeExpanded && !nativeFade && !tracedDraw) {
                tracedDraw = true;
                traceLog("settled cover drawn hardware=" + canvas.isHardwareAccelerated());
            }
        }

        private void drawBlurOverlay(Canvas canvas) {
            ViewGroup parent = root.get();
            boolean topBlurDisabled =
                    MediaFullscreenExtras.isTopBlurDisabled(
                            RuntimeExtras.enabled(getContext(), MediaClockVisibility.KEY),
                            parent != null && MediaClockVisibility.hidden(parent));
            if (liveCover.isAmbientVisible()) {
                ArtworkBlurFilter.Layers art = liveCover.ambientArtworkBlur();
                if (canvas.isHardwareAccelerated() && art != null) {
                    bounds.set(0, 0, getWidth(), getHeight());
                    if (!bannerBounds.isEmpty()) drawBannerBlur(canvas, art, 1f);
                    if (!topBlurDisabled && topBandEndNorm > topBandSolidNorm) {
                        art.drawTopProgressiveBand(
                                canvas,
                                bounds,
                                getHeight() * topBandSolidNorm,
                                getHeight() * topBandEndNorm,
                                1f,
                                null);
                    }
                }
                return;
            }
            if (bitmap == null || bitmap.isRecycled()) return;
            bounds.set(0, 0, getWidth(), getHeight());
            boolean clock = MediaClockContrast.hasGlass(parent);
            boolean banner = bannerBlur == null && !bannerBounds.isEmpty();
            boolean topBand =
                    !topBlurDisabled
                            && topBandOpacity > .005f
                            && topBandEnd > topBandSolid
                            && coverAlpha > .005f;
            if (canvas.isHardwareAccelerated() && (clock || banner || topBand)) {
                boolean needRecord =
                        blurDirty
                                || liveBlurDirty
                                || artworkBlur.needsRecording(getWidth(), getHeight());
                if (needRecord) {
                    Canvas record = artworkBlur.beginRecording(getWidth(), getHeight());
                    RectF blurRect = artworkBlur.sourceBounds();
                    boolean skipStatic = liveCover.coversBlurSource();
                    if (!skipStatic) drawBlurCover(record, blurRect, paint);
                    boolean drewLive =
                            liveCover.drawBlurFrames(record, parent, blurRect, bounds, paint);
                    if (skipStatic && !drewLive) drawBlurCover(record, blurRect, paint);
                    artworkBlur.endRecording();
                    blurDirty = false;
                    liveBlurDirty = false;
                }
                if (clock)
                    MediaClockContrast.artwork(
                            parent,
                            artworkBlur.blurred(),
                            artworkBlur.revision(),
                            bounds,
                            null,
                            coverAlpha);
                if (banner && coverAlpha > .005f) drawBannerBlur(canvas, artworkBlur, coverAlpha);
                if (topBand) {
                    artworkBlur.drawTopProgressiveBand(
                            canvas,
                            bounds,
                            topBandSolid,
                            topBandEnd,
                            topBandOpacity * coverAlpha,
                            null);
                }
            } else {
                liveBlurDirty = false;
            }
        }

        private void drawBannerBlur(
                Canvas canvas, ArtworkBlurFilter.Layers layers, float opacity) {
            if (layers == null || opacity <= .005f || bounds.isEmpty()) return;
            int clip = canvas.save();
            canvas.clipRect(bounds);
            canvas.clipPath(bannerClip);
            layers.draw(canvas, bounds, opacity);
            canvas.restoreToCount(clip);
        }

        private void drawCover(Canvas canvas, RectF target, Paint paint) {
            drawCover(canvas, target, paint, true);
        }

        private void drawBlurCover(Canvas canvas, RectF target, Paint paint) {
            drawCover(canvas, target, paint, false);
        }

        private void drawCover(
                Canvas canvas, RectF target, Paint paint, boolean hardwareReveal) {
            Bitmap still = liveCover.closingStill();
            if (!nativeExpanded && still != null && !still.isRecycled()) {
                NativeArtworkReveal.bitmap(canvas, still, target, 1f, 1f, paint, closingCrop);
            } else if (hardwareReveal) {
                songFade.draw(canvas, bitmap, target, paint);
            } else {
                songFade.drawSimple(canvas, bitmap, target, paint);
            }
        }
    }

    static final class BannerBlur extends View {
        private BannerBlur(android.content.Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        static float radius(View controls) {
            var resources = controls.getResources();
            int dimension = resources.getIdentifier("now_bar_music_page_controller_radius",
                    "dimen", controls.getContext().getPackageName());
            if (dimension == 0) dimension = resources.getIdentifier(
                    "now_bar_music_page_controller_radius", "dimen", "com.samsung.android.app.aodservice");
            if (dimension == 0) dimension = resources.getIdentifier(
                    "now_bar_music_page_controller_radius", "dimen", "com.samsung.android.app.clockpack");
            return dimension == 0 ? 36f * resources.getDisplayMetrics().density
                    : resources.getDimension(dimension);
        }

        static View attach(FrameLayout controls) {
            var context = controls.getContext();
            if (!BlurAppearance.liveWindowBlurAllowed(context)) return null;
            BannerBlur layer = new BannerBlur(context);
            try {
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
                Object request = builder.getConstructor(int.class).newInstance(0);
                builder.getMethod("setRadius", int.class).invoke(request, 127);
                builder.getMethod("setBackgroundCornerRadius", float.class)
                        .invoke(request, radius(controls));
                builder.getMethod("setBackgroundColor", int.class).invoke(request, 0);
                controls.addView(layer, 0, new FrameLayout.LayoutParams(-1, -1));
                View.class.getMethod("semSetBlurInfo", info)
                        .invoke(layer, builder.getMethod("build").invoke(request));
                if (BlurAppearance.hasWindowDrawable(layer)) return layer;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                DressroomModule.note("8.5 media controls blur unavailable", error);
            }
            controls.removeView(layer);
            return null;
        }
    }
}
