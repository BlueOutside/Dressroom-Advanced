package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import dev.denver.dressroomadvanced.lyrics.LyricsController;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class MediaFullscreenExtras {
    private static final Map<View, Art> art = new WeakHashMap<>();
    private static final Map<View, Art> collapsing = new WeakHashMap<>();
    private static final Map<View, Art> maskOwners = new WeakHashMap<>();
    private static final ThreadLocal<Art> nativeSong = new ThreadLocal<>();
    private static final Map<ViewGroup, WeakReference<Backdrop>> hosts = new WeakHashMap<>();

    private record ClockPath(Path path, float x, float y, float[] shape) {}

    private static final Map<View, ClockPath> clockPaths = new WeakHashMap<>();
    private static final Set<View> blurRebuilt =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> motionInstalled =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Main {
        static final android.os.Handler HANDLER =
                new android.os.Handler(android.os.Looper.getMainLooper());
    }
    private static DressroomModule module;
    private static boolean rootInstalled;
    private static boolean nativeAmbientState;

    static void screenChanged() {
        clockPaths.clear();
        markCandidatesDirty();
        for (WeakReference<Backdrop> reference : new ArrayList<>(hosts.values())) {
            Backdrop backdrop = reference.get();
            if (backdrop == null) continue;
            backdrop.ambientCollapsed = false;
            backdrop.topBandSolidNorm = DEFAULT_TOP_BAND_SOLID_NORM;
            backdrop.topBandEndNorm = DEFAULT_TOP_BAND_END_NORM;
            backdrop.lastClockBottom = 0f;
            backdrop.resetWindow();
        }
        for (Art state : new ArrayList<>(art.values())) {
            state.reveal.discard();
            state.overlayActive = state.closing = false;
            state.returningCard.clear();
        }
    }

    static void markCandidatesDirty() {
        for (WeakReference<Backdrop> reference : hosts.values()) {
            Backdrop backdrop = reference.get();
            if (backdrop == null) continue;
            backdrop.candidatesDirty = true;
            backdrop.hiddenDirty = true;
        }
    }

    public static ViewGroup getNotificationShadeWindowView() {
        for (ViewGroup root : hosts.keySet()) {
            if (root != null) return root;
        }
        return null;
    }

    public static Bitmap currentArtworkBitmap() {
        for (WeakReference<Backdrop> ref : hosts.values()) {
            Backdrop host = ref == null ? null : ref.get();
            if (host != null && host.bitmap != null && !host.bitmap.isRecycled()) {
                return host.bitmap;
            }
        }
        for (Art state : art.values()) {
            if (state == null) continue;
            Bitmap bmp = state.bitmap();
            if (bmp != null && !bmp.isRecycled()) return bmp;
        }
        return null;
    }

    public static boolean isFullscreenBackdropActive() {
        for (WeakReference<Backdrop> ref : hosts.values()) {
            Backdrop host = ref == null ? null : ref.get();
            if (host != null && host.showing && host.getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
    }

    public static void invalidateAllHosts() {
        Main.HANDLER.post(() -> {
            for (WeakReference<Backdrop> ref : hosts.values()) {
                Backdrop host = ref == null ? null : ref.get();
                if (host != null) {
                    if (host.liveCover != null) {
                        host.liveCover.setLyricsBlurAmount(MediaCoverWakefulness.lyricsBlurAmount());
                    }
                    host.candidatesDirty = true;
                    host.blurDirty = true;
                    host.invalidate();
                    if (host.overlayLayer != null) host.overlayLayer.invalidate();
                    if (host.returnLayer != null) host.returnLayer.invalidate();
                }
            }
        });
    }

    static void invalidateLyricsBackdrop() {
        for (WeakReference<Backdrop> ref : hosts.values()) {
            Backdrop host = ref == null ? null : ref.get();
            if (host == null) continue;
            host.invalidate();
            host.overlayLayer.invalidate();
        }
    }

    static boolean shouldCloseAmbient(
            boolean expanded,
            boolean previousExpanded,
            boolean ambientVisible,
            boolean showing,
            boolean ambientHandoff) {
        return !expanded && (previousExpanded || ambientVisible || (showing && ambientHandoff));
    }

    /** Suppresses the 320ms song-change crossfade on ambient wake or while native reveal owns the transition. */
    static boolean shouldStartSongCrossfade(
            boolean showing,
            boolean sameArt,
            boolean closing,
            boolean hasSmall,
            float progress,
            boolean exitingAmbient,
            boolean nativeRevealActive) {
        return showing
                && sameArt
                && !closing
                && !hasSmall
                && progress >= .98f
                && !exitingAmbient
                && !nativeRevealActive;
    }

    /**
     * Avoids restarting a song transition from 0% when the fallback crossfade has already visibly
     * progressed for the same incoming artwork.
     */
    static boolean shouldDeferToRunningCrossfade(
            boolean crossfadeRunning, boolean sameTarget, float crossfadeFraction) {
        return crossfadeRunning && sameTarget && crossfadeFraction > .02f;
    }

    static final long WAKE_HOLD_TIMEOUT_MS = 1500L;

    /** Holds a wake-handoff cover while native artwork still points at the pre-doze bitmap. */
    static boolean shouldHoldWakeArtwork(
            boolean hasHoldBitmap, boolean matchesStaleBitmap, long nowUptimeMs, long holdUntilMs) {
        return hasHoldBitmap && matchesStaleBitmap && nowUptimeMs < holdUntilMs;
    }

    static final float DEFAULT_TOP_BAND_SOLID_NORM = .16f;
    static final float DEFAULT_TOP_BAND_END_NORM = .26f;

    /** Returns whether the top clock blur should be suppressed because the clock is disabled/hidden. */
    static boolean isTopBlurDisabled(boolean hideClockPrefEnabled, boolean clockHiddenInWindow) {
        return hideClockPrefEnabled || clockHiddenInWindow;
    }

    /** Keeps the controls blur live through open/close motion while respecting live controls alpha when settled. */
    static float controlsBandOpacity(
            float liveControlsOpacity, float progress, boolean closing, boolean compact) {
        float p = clamp(progress);
        float live = clamp(liveControlsOpacity);
        if (closing || compact) return p;
        return p < .995f ? Math.max(live, p) : live;
    }

    /** A closing cover keeps its clock renderer until the native return actually finishes. */
    static boolean keepClockGlass(
            float progress, boolean closing, boolean compact, boolean active) {
        float p = clamp(progress);
        if (p <= .005f) return false;
        return closing || compact ? active : p >= .50f;
    }

    /** Keeps the top progressive blur live through open/close motion unless the clock is disabled. */
    static float topBandOpacity(
            float progress, boolean closing, boolean compact, boolean disabled) {
        if (disabled) return 0f;
        float p = clamp(progress);
        if (closing || compact || p < .995f) return p;
        return 1f;
    }

    static void clockPath(View clock, float x, float y, Path path) {
        ClockPath old = clockPaths.get(clock);
        if (path == null) {
            if (clockPaths.remove(clock) == null) return;
        } else {
            float[] shape = path.approximate(.5f);
            if (old != null
                    && old.x == x
                    && old.y == y
                    && java.util.Arrays.equals(old.shape, shape)) return;
            clockPaths.put(clock, new ClockPath(new Path(path), x, y, shape));
        }
        for (WeakReference<Backdrop> ref : new ArrayList<>(hosts.values())) {
            Backdrop backdrop = ref.get();
            ViewGroup root = backdrop == null ? null : backdrop.root.get();
            if (root != null
                    && root.getWindowToken() == clock.getWindowToken()
                    && backdrop.showing) {
                backdrop.clockMaskDirty = true;
                backdrop.invalidate();
                backdrop.overlayLayer.invalidate();
            }
        }
    }

    static void clockVisibilityChanged(View root) {
        MediaClockContrast.invalidate(root);
        WeakReference<Backdrop> reference = hosts.get(root);
        Backdrop backdrop = reference == null ? null : reference.get();
        if (backdrop == null) return;
        if (MediaClockVisibility.hidden(root)
                || RuntimeExtras.enabled(root.getContext(), MediaClockVisibility.KEY)) {
            backdrop.topBandOpacity = 0f;
        }
        backdrop.candidatesDirty = true;
        backdrop.clockMaskDirty = true;
        backdrop.overlayLayer.invalidate();
    }

    static void installRoot(DressroomModule owner, ClassLoader loader) {
        module = owner;
        if (rootInstalled) return;
        try {
            MediaBlurHost.install(owner);
            Class<?> shade =
                    Class.forName(
                            "com.android.systemui.shade.NotificationShadeWindowView",
                            false,
                            loader);
            Method attach = shade.getDeclaredMethod("onAttachedToWindow");
            owner.hook(attach)
                    .setId("dressroom-advanced.media-shade-layer")
                    .intercept(
                            chain -> {
                                Object result = chain.proceed();
                                ensureHost((ViewGroup) chain.getThisObject());
                                return result;
                            });
            rootInstalled = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            owner.report("Artwork shade binding unavailable", e);
        }
    }

    private static Backdrop ensureHost(ViewGroup root) {
        WeakReference<Backdrop> reference = hosts.get(root);
        Backdrop host = reference == null ? null : reference.get();
        if (host != null) return host;
        if (!root.getClass()
                .getName()
                .equals("com.android.systemui.shade.NotificationShadeWindowView")) return null;
        host = new Backdrop(root);
        hosts.put(root, new WeakReference<>(host));
        root.addView(host, backdropIndex(root), new FrameLayout.LayoutParams(-1, -1));
        android.util.Log.i(
                DressroomModule.TAG, "Artwork backdrop attached behind native clock/player");
        return host;
    }

    static int backdropIndex(ViewGroup root) {
        int fallback = root.getChildCount();
        for (int i = 0; i < root.getChildCount(); i++) {
            String name = name(root.getChildAt(i));
            if (name.equals("notification_panel")) return i;
            if (name.equals("lock_clock_blur")) fallback = i;
        }
        return fallback;
    }

    private static String name(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (android.content.res.Resources.NotFoundException e) {
            return "";
        }
    }

    private static final class Art {
        final WeakReference<View> target;
        final WeakReference<ImageView> image;
        final WeakReference<Object> holder;
        final Field bitmapField;
        WeakReference<View> small = new WeakReference<>(null);
        float startScale = 1, collapseScale = 1;
        WeakReference<View> overlayHost = new WeakReference<>(null);
        WeakReference<View> returningCard = new WeakReference<>(null);
        WeakReference<View> normalContent = new WeakReference<>(null);
        float returnHeight;
        final WeakReference<View> controls;
        final WeakReference<View> controlHost;
        boolean overlayActive, closing, thumbnailMuted;
        final NativeArtworkReveal reveal;
        Drawable displayedDrawable;
        Bitmap displayedBitmap;
        Bitmap previousBitmap;
        boolean bitmapChangedBeforeReveal;

        Art(
                View view,
                ImageView image,
                Object holder,
                OneUi9MediaBinding.HolderContract contract) {
            target = new WeakReference<>(view);
            this.image = new WeakReference<>(image);
            this.holder = new WeakReference<>(holder);
            bitmapField = contract.bitmap();

            controls = new WeakReference<>((View) OneUi9MediaBinding.read(contract.controls(), holder));
            controlHost =
                    new WeakReference<>(
                            (View) OneUi9MediaBinding.read(contract.controlHost(), holder));
            View mask = optionalView(holder, contract.mask()),
                    lighting = optionalView(holder, contract.lighting());
            View prior = optionalView(holder, contract.prevAlbumArt());
            reveal =
                    new NativeArtworkReveal(
                            mask, image, prior instanceof ImageView old ? old : null, lighting);
            if (mask != null) maskOwners.put(mask, this);
        }

        private static View optionalView(Object holder, Field field) {
            try {
                return field.get(holder) instanceof View view ? view : null;
            } catch (Exception ignored) {
                DressroomModule.note("Fullscreen optional holder field", ignored);
                return null;
            }
        }

        Bitmap bitmap() {
            ImageView view = image.get();
            Drawable drawable = view == null ? null : view.getDrawable();
            if (drawable == null) {
                displayedDrawable = null;
                displayedBitmap = null;
                previousBitmap = null;
                bitmapChangedBeforeReveal = false;
                return null;
            }
            if (drawable == displayedDrawable
                    && displayedBitmap != null
                    && !displayedBitmap.isRecycled()) return displayedBitmap;
            Bitmap source = drawable instanceof BitmapDrawable bd ? bd.getBitmap() : null;
            try {
                Object object = holder.get();
                Bitmap original = object == null ? null : (Bitmap) bitmapField.get(object);
                if (original != null && !original.isRecycled()) source = original;
            } catch (IllegalAccessException ignored) {
                DressroomModule.note("Fullscreen bitmap field", ignored);
            }
            if (source != null
                    && !source.isRecycled()
                    && displayedBitmap != null
                    && !displayedBitmap.isRecycled()
                    && displayedBitmap != source) {
                previousBitmap = displayedBitmap;
                bitmapChangedBeforeReveal = true;
            }
            displayedDrawable = drawable;
            displayedBitmap = source;
            return source != null && !source.isRecycled() ? source : null;
        }

        View moving() {
            View view = small.get();
            return view == null ? target.get() : view;
        }

        float progress() {
            View view = moving();
            if (view == null) return 0;
            float scale = view.getScaleX();
            if (small.get() != null)
                return clamp(collapseScale > 1.001f ? (scale - 1) / (collapseScale - 1) : 0);
            if (scale < .99f) startScale = Math.min(startScale, scale);
            return startScale < .99f ? clamp((scale - startScale) / (1 - startScale)) : 1;
        }
    }

    private static float clamp(float v) {
        return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;
    }

    static void returnBridgeBounds(RectF result, RectF pill, RectF movingArtwork) {
        result.set(pill);
        if (!result.intersect(movingArtwork)) result.setEmpty();
    }

    private static final class EmptyBackground extends Drawable {
        final Drawable original;

        EmptyBackground(Drawable original) {
            this.original = original;
        }

        public void draw(Canvas canvas) {}

        public void setAlpha(int alpha) {
            original.setAlpha(alpha);
        }

        public int getAlpha() {
            return original.getAlpha();
        }

        public void setColorFilter(ColorFilter filter) {
            original.setColorFilter(filter);
        }

        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private static final class Saved {
        final View view;
        final WeakReference<ViewGroup> root;
        final float alpha;
        final int imageAlpha;
        final Drawable foreground;
        final int foregroundAlpha;
        EmptyBackground empty;
        boolean hidePixels, hideView, clearBackground, muteBlurHost;

        Saved(View v, ViewGroup owner) {
            view = v;
            root = new WeakReference<>(owner);
            alpha = v.getAlpha();
            imageAlpha = v instanceof ImageView image ? image.getImageAlpha() : 255;
            foreground = v.getForeground();
            foregroundAlpha = foreground == null ? 255 : foreground.getAlpha();
        }

        void apply() {
            if (muteBlurHost) MediaBlurHost.holdForArtwork(view, root.get());
            if (hidePixels && view instanceof ImageView image && image.getImageAlpha() != 0)
                image.setImageAlpha(0);
            if (hideView) MediaBlurHost.hold(view);
            if (clearBackground
                    && view.getBackground() != null
                    && !(view.getBackground() instanceof EmptyBackground)) {
                empty = new EmptyBackground(view.getBackground());
                view.setBackground(empty);
            }
            if (hidePixels && foreground != null && foreground.getAlpha() != 0)
                foreground.setAlpha(0);
        }

        void restore() {
            if (muteBlurHost) MediaBlurHost.release(view);
            if (hidePixels && view instanceof ImageView image) image.setImageAlpha(imageAlpha);
            if (hideView) MediaBlurHost.release(view);
            if (empty != null && view.getBackground() == empty) view.setBackground(empty.original);
            if (hidePixels && foreground != null) foreground.setAlpha(foregroundAlpha);
        }
    }

    private static final class Backdrop extends FrameLayout {
        final WeakReference<ViewGroup> root;
        private final android.view.ViewTreeObserver.OnPreDrawListener redraw = () -> {
            update();
            return true;
        };
        private android.view.ViewTreeObserver observer;
        final Map<View, Saved> hidden = new WeakHashMap<>();
        final Map<View, Boolean> shortcuts = new WeakHashMap<>(), revealHeld = new WeakHashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF destination = new RectF(), cover = new RectF(), next = new RectF();
        final RectF nativeArtBounds = new RectF();
        final RectF controlRect = new RectF();
        final Path nativeCover = new Path(), nativeLocal = new Path();
        final android.graphics.Outline nativeOutline = new android.graphics.Outline();
        final android.graphics.Rect nativeRect = new android.graphics.Rect();
        boolean regularPublished;
        final Path coverShape = new Path(), maskScratch = new Path();
        final Matrix maskParent = new Matrix(), maskLocal = new Matrix(), maskRoot = new Matrix();
        final Map<View, Boolean> maskVisibility = new WeakHashMap<>();
        int maskSources = -1;
        final View returnLayer;
        final LiveCoverController liveCover;
        final View overlayLayer;
        final Path outline = new Path();
        final RectF returnBounds = new RectF(),
                returnTitleBounds = new RectF(),
                returnExclusion = new RectF(),
                previousExclusion = new RectF();
        final Path returnClip = new Path();
        final ArtworkBlurFilter.Layers artworkBlur;
        final ArtworkCrossfade songFade;
        final Path clockMask = new Path();
        boolean clockMaskDirty = true;
        boolean compact;
        final RectF ambientRect = new RectF();
        final RectF clockRect = new RectF();
        float bandTopNorm = .58f, bandSolidNorm = .83f;
        float controlsBandTop, controlsBandSolid, controlsBandOpacity;
        float topBandSolidNorm = DEFAULT_TOP_BAND_SOLID_NORM,
                topBandEndNorm = DEFAULT_TOP_BAND_END_NORM;
        float topBandSolid, topBandEnd, topBandOpacity;
        float lastClockBottom;
        boolean blurDirty = true;
        boolean liveBlurDirty;
        boolean lastLiveVisible;
        boolean wasAmbient;
        Bitmap preAmbientNative;
        Bitmap wakeHoldBitmap;
        Bitmap wakeHoldStale;
        long wakeHoldUntilMs;
        final ArrayList<Art> candidateScratch = new ArrayList<>();
        WeakReference<Art> lastArt = new WeakReference<>(null);
        boolean candidatesDirty = true;
        boolean hiddenDirty = true;
        final Matrix global = new Matrix(),
                rootGlobal = new Matrix(),
                inverse = new Matrix(),
                local = new Matrix();
        Art current;
        Bitmap bitmap;
        int bitmapGeneration;
        View boundPage;
        boolean showing, ambientCollapsed;

        Backdrop(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            setWillNotDraw(false);
            returnLayer =
                    new View(root.getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            if (!showing || current == null || current.small.get() == null) return;
                            if (LyricsController.isLyricsActive()) return;
                            if (returnBounds.isEmpty() && returnTitleBounds.isEmpty()) return;
                            int save = canvas.save();
                            returnClip.rewind();
                            returnClip.addRect(returnBounds, Path.Direction.CW);
                            returnClip.addRect(returnTitleBounds, Path.Direction.CW);
                            canvas.clipPath(returnClip);
                            if (!returnExclusion.isEmpty()) canvas.clipOutRect(returnExclusion);
                            // Only the moving cover belongs above the pill, not the controls blur.
                            Backdrop.this.drawArtwork(canvas);
                            canvas.restoreToCount(save);
                        }
                    };
            returnLayer.setClickable(false);
            returnLayer.setFocusable(false);
            returnLayer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            returnLayer.setVisibility(INVISIBLE);
            artworkBlur = new ArtworkBlurFilter.Layers(this);
            overlayLayer =
                    new View(root.getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            drawForegroundBlur(canvas);
                        }
                    };
            overlayLayer.setClickable(false);
            overlayLayer.setFocusable(false);
            overlayLayer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            liveCover =
                    new LiveCoverController(
                            root.getContext(),
                            () -> {
                                if (!liveBlurDirty) {
                                    liveBlurDirty = true;
                                    overlayLayer.invalidate();
                                }
                                if (returnLayer.getVisibility() == VISIBLE) {
                                    returnLayer.invalidate();
                                }
                            });
            songFade = new ArtworkCrossfade(this, () -> {
                blurDirty = true;
                invalidate();
                overlayLayer.invalidate();
            });
            addView(liveCover.hostView(), new FrameLayout.LayoutParams(0, 0));
            addView(overlayLayer, new FrameLayout.LayoutParams(-1, -1));
            setVisibility(INVISIBLE);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            liveCover.onAttached();
            ViewGroup parent = root.get();
            if (parent == null) return;
            parent.getOverlay().add(returnLayer);
            observer = getViewTreeObserver();
            observer.addOnPreDrawListener(redraw);
        }

        @Override
        protected void onDetachedFromWindow() {
            liveCover.onDetached();
            artworkBlur.detach();
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(redraw);
            observer = null;
            resetWindow();
            ViewGroup parent = root.get();
            if (parent != null) parent.getOverlay().remove(returnLayer);
            super.onDetachedFromWindow();
        }

        void resetWindow() {
            resetWindow(false);
        }

        void resetWindow(boolean immediateContrast) {
            liveCover.reset();
            songFade.clear();
            lastLiveVisible = false;
            wasAmbient = false;
            controlsBandOpacity = 0f;
            topBandOpacity = 0f;
            preAmbientNative = wakeHoldBitmap = wakeHoldStale = null;
            wakeHoldUntilMs = 0L;
            restore();
            showing = regularPublished = false;
            current = null;
            bitmap = null;
            candidatesDirty = true;
            hiddenDirty = true;
            clockMask.reset();
            clockMaskDirty = true;
            maskVisibility.clear();
            maskSources = -1;
            setVisibility(INVISIBLE);
            overlayLayer.invalidate();
            artworkBlur.discardDisplayLists();
            ViewGroup parent = root.get();
            if (parent != null) {
                if (immediateContrast) MediaClockContrast.clearImmediate(parent);
                else MediaClockContrast.clear(parent);
                MediaStatusBarContrast.clear(parent);
                WidgetMediaOcclusion.publish(parent, null);
                if (immediateContrast) parent.invalidate();
            }
        }

        private static boolean isUsable(Art candidate, ViewGroup parent) {
            if (candidate == null || candidate.target.get() == null) return false;
            View small = candidate.small.get();
            if (small != null
                    && candidate.collapseScale > 1.001f
                    && small.getWidth() > 0
                    && small.getWidth() * (small.getScaleX() - 1f) <= 1.5f) {
                if (candidate.thumbnailMuted) {
                    MediaBlurHost.release(small);
                    candidate.thumbnailMuted = false;
                }
                return false;
            }
            View moving = candidate.moving();
            View overlay = candidate.overlayHost.get();
            boolean inOverlay =
                    candidate.overlayActive
                            && overlay != null
                            && overlay.getWindowToken() == parent.getWindowToken();
            return moving != null
                    && (inOverlay
                            || (moving.isShown()
                                    && moving.getWindowToken() != null
                                    && moving.getWindowToken() == parent.getWindowToken()))
                    && candidate.bitmap() != null;
        }

        private boolean hooksActive;

        private void setRuntimeHooksActive(boolean active) {
            if (hooksActive == active) return;
            hooksActive = active;
            MediaClockContrast.setHooksEnabled(active);
            MediaBlurHost.setHooksEnabled(active);
            MediaInteraction.setActive(active);
        }

        void update() {
            ViewGroup parent = root.get();
            if (parent == null) {
                setRuntimeHooksActive(false);
                return;
            }
            if (ambientCollapsed) {
                if (!nativeAmbientState && !liveCover.ambientHandoff()) {
                    ambientCollapsed = false;
                } else {
                    setRuntimeHooksActive(false);
                    updateRegularWidgets(parent);
                    return;
                }
            }
            boolean fullscreenEnabled = RuntimeExtras.enabled(getContext(), "fullscreenArt");
            if (fullscreenEnabled
                    && liveCover.retainOnWallpaperAod(
                            showing && current != null && !current.closing ? current.bitmap() : null)) {
                setRuntimeHooksActive(true);
                if (!wasAmbient) {
                    preAmbientNative =
                            showing && current != null && !current.closing
                                    ? current.bitmap()
                                    : bitmap;
                }
                wasAmbient = true;
                detachReturnLayer();
                for (Art candidate : art.values()) {
                    View framed = candidate.target.get();
                    if (framed != null && framed.getRootView() == parent)
                        collectAmbientArtwork(framed);
                }
                Bitmap ambientArt = liveCover.ambientBitmap();
                Bitmap fallbackArt =
                        current != null && !current.closing ? current.bitmap() : null;
                Bitmap synced =
                        ambientArt != null && !ambientArt.isRecycled() ? ambientArt : fallbackArt;
                if (synced != null && !synced.isRecycled() && bitmap != synced) {
                    songFade.update(synced, false);
                    bitmap = synced;
                    bitmapGeneration = synced.getGenerationId();
                    blurDirty = true;
                }
                if (current != null && current.reveal != null && !current.reveal.running()) {
                    current.reveal.progress(1f);
                }
                if (current != null) current.bitmapChangedBeforeReveal = false;
                liveCover.hideVideoSurface();
                if (getAlpha() != 1f) setAlpha(1f);
                if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
                liveCover.prepareAmbientClock(parent, this);
                ambientRect.set(0f, 0f, parent.getWidth(), parent.getHeight());
                if (updateTopBand(parent, ambientRect, 1f, 1f, false, false)) {
                    overlayLayer.invalidate();
                }
                return;
            }
            boolean exitingAmbient = wasAmbient;
            wasAmbient = false;
            Art best = null;
            if (fullscreenEnabled) {
                if (!candidatesDirty && current != null && isUsable(current, parent)) {
                    best = current;
                } else if (!showing && !candidatesDirty) {
                    Art recent = lastArt.get();
                    if (recent != null && isUsable(recent, parent)) candidatesDirty = true;
                }
                if (best == null && (candidatesDirty || showing || current != null)) {
                    float area = 0;
                    candidateScratch.clear();
                    candidateScratch.addAll(art.values());
                    for (int i = 0, n = candidateScratch.size(); i < n; i++) {
                        Art candidate = candidateScratch.get(i);
                        if (!isUsable(candidate, parent)) continue;
                        View moving = candidate.moving();
                        float size =
                                moving.getWidth()
                                        * moving.getHeight()
                                        * moving.getScaleX()
                                        * moving.getScaleY();
                        if (size > area) {
                            best = candidate;
                            area = size;
                        }
                    }
                    candidateScratch.clear();
                    if (best == null) {
                        Art stale = lastArt.get();
                        if (stale != null && stale.target.get() == null) lastArt.clear();
                        candidatesDirty = false;
                    } else {
                        if (lastArt.get() != best) lastArt = new WeakReference<>(best);
                        boolean settled =
                                !best.closing
                                        && best.small.get() == null
                                        && best.progress() >= 0.998f;
                        candidatesDirty = !settled;
                    }
                }
            } else {
                candidatesDirty = true;
            }
            if (best == null) {
                if (showing) {
                    liveCover.reset();
                    songFade.clear();
                    lastLiveVisible = false;
                    controlsBandOpacity = 0f;
                    topBandOpacity = 0f;
                    preAmbientNative = wakeHoldBitmap = wakeHoldStale = null;
                    wakeHoldUntilMs = 0L;
                    restore();
                    MediaClockContrast.clearImmediate(parent);
                    MediaStatusBarContrast.clear(parent);
                    WidgetMediaOcclusion.publish(parent, null);
                    showing = false;
                    bitmap = null;
                    current = null;
                    hiddenDirty = true;
                    for (View clock : new ArrayList<>(clockPaths.keySet()))
                        if (clock != null && clock.isAttachedToWindow())
                            try {
                                Reflect.call(clock, "setClockTextBlur");
                                if (blurRebuilt.add(clock))
                                    android.util.Log.i(
                                            DressroomModule.TAG,
                                            "Clock blur rebuilt after media return");
                            } catch (Exception ignored) {
                                DressroomModule.note("Fullscreen clock blur rebuild", ignored);
                            }
                    clockMask.reset();
                    clockMaskDirty = true;
                    maskVisibility.clear();
                    maskSources = -1;
                    artworkBlur.discardDisplayLists();
                    setVisibility(INVISIBLE);
                    returnLayer.setVisibility(INVISIBLE);
                }
                setRuntimeHooksActive(false);
                updateRegularWidgets(parent);
                return;
            }
            setRuntimeHooksActive(true);
            regularPublished = false;
            View target = best.target.get();
            View page = target;
            for (View v = target; v != null; v = v.getParent() instanceof View p ? p : null) {
                if (name(v).equals("facewidget_music_page_container")) {
                    page = v;
                    break;
                }
            }
            if (best != current || page != boundPage) {
                if (best != current) {
                    bandTopNorm = .58f;
                    bandSolidNorm = .83f;
                }
                restore();
                current = best;
                boundPage = page;
                View mediaCard = BlurAppearance.nowBarCardOwner(page);
                if (mediaCard == null) mediaCard = BlurAppearance.nowBarCardOwner(target);
                collect(parent, mediaCard);
                collect(target, mediaCard);
                hiddenDirty = true;
                android.util.Log.i(
                        DressroomModule.TAG,
                        "Artwork layer active: bitmap="
                                + best.bitmap().getWidth()
                                + "x"
                                + best.bitmap().getHeight()
                                + " target="
                                + target.getWidth()
                                + "x"
                                + target.getHeight()
                                + " replacements="
                                + hidden.size());
            }
            boolean forceApplyHidden = hiddenDirty || best.closing;
            View nativeThumbnail = best.small.get();
            if (nativeThumbnail != null) {
                MediaBlurHost.hold(nativeThumbnail);
                best.thumbnailMuted = true;
            }

            Bitmap image = best.bitmap();
            boolean releasedWakeHold = false;
            if (exitingAmbient) {
                Bitmap wakeArt = liveCover.consumeWakeAmbientArt();
                if (wakeArt != null
                        && !wakeArt.isRecycled()
                        && preAmbientNative != null
                        && image == preAmbientNative) {
                    wakeHoldBitmap = wakeArt;
                    wakeHoldStale = preAmbientNative;
                    wakeHoldUntilMs =
                            android.os.SystemClock.uptimeMillis() + WAKE_HOLD_TIMEOUT_MS;
                }
                preAmbientNative = null;
            }
            if (wakeHoldBitmap != null) {
                if (!wakeHoldBitmap.isRecycled()
                        && shouldHoldWakeArtwork(
                                true,
                                image == wakeHoldStale,
                                android.os.SystemClock.uptimeMillis(),
                                wakeHoldUntilMs)) {
                    image = wakeHoldBitmap;
                } else {
                    wakeHoldBitmap = null;
                    wakeHoldStale = null;
                    wakeHoldUntilMs = 0L;
                    releasedWakeHold = true;
                }
            }
            View moving = best.moving();
            if (moving == null) return;
            global.reset();
            rootGlobal.reset();
            View overlay = best.overlayHost.get();
            if (best.overlayActive && overlay != null) {
                global.set(moving.getMatrix());
                global.postTranslate(moving.getLeft(), moving.getTop());
                overlay.transformMatrixToGlobal(rootGlobal);
                local.setConcat(rootGlobal, global);
                global.set(local);
            } else moving.transformMatrixToGlobal(global);
            rootGlobal.reset();
            parent.transformMatrixToGlobal(rootGlobal);
            boolean maskChanged = !maskRoot.equals(rootGlobal);
            if (maskChanged) maskRoot.set(rootGlobal);
            if (maskSources != clockPaths.size()) {
                maskSources = clockPaths.size();
                maskVisibility.clear();
                maskChanged = true;
            }
            for (View clock : clockPaths.keySet()) {
                boolean visible = contributesClockMask(clock, parent);
                Boolean old = maskVisibility.put(clock, visible);
                if (old == null || old != visible) maskChanged = true;
            }
            if (maskChanged) clockMaskDirty = true;
            if (!rootGlobal.invert(inverse)) return;
            local.setConcat(inverse, global);
            next.set(0, 0, moving.getWidth(), moving.getHeight());
            local.mapRect(next);
            nativeArtBounds.set(next);
            float p = best.progress();
            if (best.closing
                    || best.small.get() != null
                    || best.reveal.running()
                    || (p > 0.002f && p < 0.998f)) {
                LockscreenFrameBoost.pulse(this, 220L);
            }
            if (best.closing || best.small.get() != null) {
                float reveal = compactReveal(p);
                for (View shortcut : shortcuts.keySet()) {
                    MediaBlurHost.fade(shortcut, reveal);
                    revealHeld.put(shortcut, true);
                }
                View normal = best.normalContent.get();
                if (normal != null) {
                    MediaBlurHost.fade(normal, reveal);
                    revealHeld.put(normal, true);
                }
            } else releaseReveal();
            next.set(
                    next.left * (1 - p),
                    next.top * (1 - p),
                    next.right + (parent.getWidth() - next.right) * p,
                    next.bottom + (parent.getHeight() - next.bottom) * p);
            int generation = image.getGenerationId();
            boolean geometryChanged = !showing || !destination.equals(next);
            boolean contentChanged = !showing || bitmap != image || bitmapGeneration != generation;
            boolean changed = geometryChanged || contentChanged;
            if (best.reveal.update()) {
                changed = true;
                contentChanged = true;
            }
            if (best.reveal.running()) postInvalidateOnAnimation();
            if (forceApplyHidden || contentChanged || geometryChanged) {
                for (Saved saved : hidden.values()) saved.apply();
                hiddenDirty = false;
            }
            if (contentChanged) {
                MediaClockContrast.prefetch(image);
                LyricsController.notifyArtworkUpdated(image);
            }
            boolean clockActive = MediaClockContrast.isActive(parent);
            if (keepClockGlass(p, best.closing, best.small.get() != null, clockActive)) {
                try {
                    if (best.closing) MediaClockContrast.returnToNative(parent, p);
                    else MediaClockContrast.apply(parent, image, next, overlayLayer);
                } catch (RuntimeException error) {
                    MediaClockContrast.clear(parent);
                }
            } else if (clockActive) {
                MediaClockContrast.clearImmediate(parent);
            }
            if (best.closing || best.small.get() != null || p < .50f) {
                if (MediaStatusBarContrast.isActive(parent)) MediaStatusBarContrast.clear(parent);
            } else {
                try {
                    MediaStatusBarContrast.apply(parent, image, next);
                } catch (RuntimeException error) {
                    MediaStatusBarContrast.clear(parent);
                }
            }
            View controls = best.controls.get();
            View controlHost = best.controlHost.get();
            if ((exitingAmbient || releasedWakeHold) && !best.reveal.running()) {
                best.reveal.progress(1f);
            }
            if (exitingAmbient || releasedWakeHold) {
                best.bitmapChangedBeforeReveal = false;
            }
            boolean nativeRevealActive = best.reveal.isActive();
            if (nativeRevealActive) {
                if (songFade.running()) songFade.cancelTo(image);
                else songFade.update(image, false);
            } else {
                songFade.update(
                        image,
                        shouldStartSongCrossfade(
                                showing,
                                current == best,
                                best.closing,
                                best.small.get() != null,
                                p,
                                exitingAmbient || releasedWakeHold,
                                false));
            }
            compact = best.small.get() != null;
            bitmap = image;
            bitmapGeneration = generation;
            destination.set(next);
            showing = true;
            if (geometryChanged) {
                coverShape.reset();
                float corner = Math.min(destination.width(), destination.height()) * .5f * (1 - p);
                coverShape.addRoundRect(destination, corner, corner, Path.Direction.CW);
                WidgetMediaOcclusion.publish(parent, coverShape);
            }
            View card = best.returningCard.get();
            if (best.small.get() != null && card != null) {
                global.reset();
                card.transformMatrixToGlobal(global);
                local.setConcat(inverse, global);
                next.set(0, 0, card.getWidth(), card.getHeight());
                local.mapRect(next);
                if (best.returnHeight > 0)
                    next.top = Math.max(next.top, next.bottom - best.returnHeight);
                // Title text is above the backdrop. Let the same moving cover pass over it
                // without changing the title fade or broadening the bridge over the controls.
                controlRect.setEmpty();
                if (compactReveal(p) > 0) {
                    View title = find(best.normalContent.get(), "stack_card_main_text");
                    if (visibleControls(title)) {
                        global.reset();
                        title.transformMatrixToGlobal(global);
                        local.setConcat(inverse, global);
                        controlRect.set(0, 0, title.getWidth(), title.getHeight());
                        local.mapRect(controlRect);
                        if (!controlRect.intersect(next) || !controlRect.intersect(destination))
                            controlRect.setEmpty();
                    }
                }
                if (!returnTitleBounds.equals(controlRect)) {
                    returnTitleBounds.set(controlRect);
                    changed = true;
                }
                // Fullscreen interpolation extends behind controls; the foreground bridge must not.
                returnBridgeBounds(next, next, nativeArtBounds);
                if (!returnBounds.equals(next)) {
                    returnBounds.set(next);
                    changed = true;
                }
                previousExclusion.set(returnExclusion);
                returnExclusion.setEmpty();
                if (visibleControls(controlHost) && visibleControls(controls)) {
                    global.reset();
                    controlHost.transformMatrixToGlobal(global);
                    local.setConcat(inverse, global);
                    returnExclusion.set(0, 0, controlHost.getWidth(), controlHost.getHeight());
                    local.mapRect(returnExclusion);
                }
                if (!previousExclusion.equals(returnExclusion)) changed = true;
                if (returnLayer.getWidth() != parent.getWidth()
                        || returnLayer.getHeight() != parent.getHeight())
                    returnLayer.layout(0, 0, parent.getWidth(), parent.getHeight());
                if (returnLayer.getVisibility() != VISIBLE) returnLayer.setVisibility(VISIBLE);
            } else detachReturnLayer();
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            float alpha = 1;
            for (View v = page;
                    v != null && v != parent;
                    v = v.getParent() instanceof View pv ? pv : null) {
                Saved state = hidden.get(v);
                alpha *= state != null && state.hideView ? state.alpha : v.getAlpha();
            }
            if (getAlpha() != alpha) setAlpha(alpha);
            changed |= updateControlsBand(
                    parent, controls, destination, p, alpha, best.closing, compact);
            changed |= updateTopBand(parent, destination, p, alpha, best.closing, compact);
            if (contentChanged) blurDirty = true;
            boolean liveExpanded =
                    current != null
                            && !compact
                            && !current.closing
                            && current.small.get() == null
                            && current.progress() >= 0.98f;
            boolean closing = current == null || compact || current.closing || current.small.get() != null;
            liveCover.holdVideoRevealDuringPoster(liveExpanded
                    && (best.reveal.hasActiveMask(image) || songFade.running()));
            liveCover.update(
                    fullscreenEnabled,
                    liveExpanded,
                    closing,
                    destination.left,
                    destination.top,
                    destination.right,
                    destination.bottom,
                    coverCorner(),
                    1f);
            boolean liveNow = liveCover.isVideoVisible();
            if (lastLiveVisible != liveNow) {
                lastLiveVisible = liveNow;
                blurDirty = true;
                changed = true;
            }
            if (changed || maskChanged) {
                invalidate();
                overlayLayer.invalidate();
            }
            if (changed && returnLayer.getVisibility() == VISIBLE) returnLayer.invalidate();
        }

        private boolean updateControlsBand(
                ViewGroup parent,
                View controls,
                RectF coverBounds,
                float progress,
                float artworkAlpha,
                boolean closing,
                boolean compact) {
            if (artworkAlpha <= .005f || coverBounds.isEmpty()) {
                boolean changed = controlsBandOpacity != 0f;
                controlsBandOpacity = 0f;
                return changed;
            }
            float rawControlsAlpha = controlsAlpha(controls);
            boolean hasLiveControls =
                    rawControlsAlpha > .005f
                            && controls != null
                            && controls.getWidth() > 0
                            && controls.getHeight() > 0
                            && controls.getRootView() == parent;
            // The backdrop already fades with the music page. Only apply the extra fade
            // from the native controls here, regardless of the cover spring's progress.
            float liveOpacity = hasLiveControls ? clamp(rawControlsAlpha / artworkAlpha) : 0f;
            float density = getResources().getDisplayMetrics().density;
            float top, solid;
            if (hasLiveControls) {
                global.reset();
                controls.transformMatrixToGlobal(global);
                local.setConcat(inverse, global);
                controlRect.set(0, 0, controls.getWidth(), controls.getHeight());
                local.mapRect(controlRect);
                float liveTop = controlRect.top - 64f * density;
                float liveSolid = controlRect.top + 48f * density;
                if (!closing && !compact && parent.getHeight() > 0 && liveSolid > liveTop) {
                    bandTopNorm = clamp(liveTop / parent.getHeight());
                    bandSolidNorm = clamp(liveSolid / parent.getHeight());
                }
                if (!closing && !compact && progress >= .98f) {
                    top = liveTop;
                    solid = liveSolid;
                } else {
                    top = coverBounds.top + bandTopNorm * coverBounds.height();
                    solid = coverBounds.top + bandSolidNorm * coverBounds.height();
                }
            } else {
                top = coverBounds.top + bandTopNorm * coverBounds.height();
                solid = coverBounds.top + bandSolidNorm * coverBounds.height();
            }
            float opacity =
                    solid > top ? controlsBandOpacity(liveOpacity, progress, closing, compact) : 0f;
            boolean changed =
                    controlsBandTop != top
                            || controlsBandSolid != solid
                            || controlsBandOpacity != opacity;
            controlsBandTop = top;
            controlsBandSolid = solid;
            controlsBandOpacity = opacity;
            return changed;
        }

        private boolean updateTopBand(
                ViewGroup parent,
                RectF coverBounds,
                float progress,
                float artworkAlpha,
                boolean closing,
                boolean compact) {
            boolean disabled =
                    isTopBlurDisabled(
                            RuntimeExtras.enabled(getContext(), MediaClockVisibility.KEY),
                            MediaClockVisibility.hidden(parent));
            if (disabled || artworkAlpha <= .005f || coverBounds.isEmpty()) {
                boolean changed = topBandOpacity != 0f;
                topBandOpacity = 0f;
                return changed;
            }
            boolean hasClock = MediaClockContrast.clockBounds(parent, clockRect);
            float density = getResources().getDisplayMetrics().density;
            if (hasClock && clockRect.bottom > 0f) {
                lastClockBottom = clockRect.bottom;
                if (!closing && !compact && parent.getHeight() > 0) {
                    float[] fullRange =
                            ArtworkBlurFilter.topBandRange(
                                    0f, clockRect.bottom, density, parent.getHeight());
                    if (fullRange[1] > fullRange[0]) {
                        topBandSolidNorm = clamp(fullRange[0] / parent.getHeight());
                        topBandEndNorm = clamp(fullRange[1] / parent.getHeight());
                    }
                }
            }
            float solid, end;
            if (!closing
                    && !compact
                    && progress >= .98f
                    && hasClock
                    && clockRect.bottom > coverBounds.top) {
                float[] range =
                        ArtworkBlurFilter.topBandRange(
                                coverBounds.top, clockRect.bottom, density, coverBounds.bottom);
                solid = range[0];
                end = range[1];
            } else {
                solid = coverBounds.top + topBandSolidNorm * coverBounds.height();
                end = coverBounds.top + topBandEndNorm * coverBounds.height();
            }
            float opacity = end > solid ? topBandOpacity(progress, closing, compact, false) : 0f;
            boolean changed =
                    topBandSolid != solid || topBandEnd != end || topBandOpacity != opacity;
            topBandSolid = solid;
            topBandEnd = end;
            topBandOpacity = opacity;
            return changed;
        }

        private void collectAmbientArtwork(View view) {
            if (view instanceof ImageView && isArtworkImage(name(view))) {
                Saved saved = hidden.computeIfAbsent(view, v -> new Saved(v, root.get()));
                saved.hidePixels = true;
                saved.apply();
            } else if (view instanceof ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++)
                    collectAmbientArtwork(group.getChildAt(i));
            }
        }

        private static boolean contributesClockMask(View clock, ViewGroup parent) {
            return clock.isShown() && clock.getAlpha() > 0f
                    && clock.getWindowToken() == parent.getWindowToken()
                    && !ClockBlurMotion.outgoing(clock);
        }

        void collect(View view, View mediaCard) {
            if (view != mediaCard && BlurAppearance.isNowBarCardType(view.getClass())) return;
            String n = name(view);
            if (n.equals("left_shortcut_area") || n.equals("right_shortcut_area"))
                shortcuts.put(view, true);
            boolean pixels = isArtworkImage(n);
            boolean whole =
                    n.equals("now_bar_media_album_art_circle_reveal")
                            || n.equals("now_bar_media_album_art_circle_ripple_parent");
            boolean liveBlur = n.equals("lock_clock_blur");
            boolean bg =
                    n.equals("keyguard_status_view")
                            || n.equals("expand_content_rootview")
                            || n.equals("nowbar_expandview_background")
                            || n.equals("nowbar_cardview_bgview_for_media_setting");
            if ((pixels || whole || bg || liveBlur) && !hidden.containsKey(view)) {
                Saved saved = new Saved(view, root.get());
                saved.hidePixels = pixels;
                saved.hideView = whole;
                saved.clearBackground = bg;
                saved.muteBlurHost = liveBlur;
                hidden.put(view, saved);
            }
            if (view instanceof ViewGroup group)
                for (int i = 0; i < group.getChildCount(); i++)
                    collect(group.getChildAt(i), mediaCard);
        }

        private static boolean isArtworkImage(String id) {
            return id.equals("facewidget_album_art_background")
                    || id.equals("now_bar_media_prev_album_art")
                    || id.equals("now_bar_media_expand_album_art_aod_mask");
        }

        private void detachReturnLayer() {
            returnLayer.setVisibility(INVISIBLE);
            returnBounds.setEmpty();
            returnTitleBounds.setEmpty();
            returnExclusion.setEmpty();
        }

        void beginClosing(Art state) {
            if (!showing || current != state) return;
            state.closing = true;
            if (nativeAmbientState) {
                if (closeAmbient()) return;
                liveCover.beginCollapse();
            } else if (!liveCover.retainOnWallpaperAod(bitmap)) liveCover.beginCollapse();
            lastLiveVisible = false;
            blurDirty = true;
            overlayLayer.invalidate();
            for (View shortcut : shortcuts.keySet()) {
                MediaBlurHost.fade(shortcut, 0);
                revealHeld.put(shortcut, true);
            }
            View normal = state.normalContent.get();
            if (normal != null) {
                MediaBlurHost.fade(normal, 0);
                revealHeld.put(normal, true);
            }
        }

        boolean closeAmbient() {
            boolean ambient = liveCover.ambientHandoff();
            boolean visible =
                    showing || liveCover.isAmbientVisible() || getVisibility() == VISIBLE;
            liveCover.releaseAmbient();
            if (!ambient || !visible) return false;
            // Closed while dozing: the old artwork view can still be attached to the stack.
            ambientCollapsed = true;
            resetWindow(true);
            return true;
        }

        private void releaseReveal() {
            for (View view : revealHeld.keySet()) MediaBlurHost.release(view);
            revealHeld.clear();
        }

        void restore() {
            releaseReveal();
            shortcuts.clear();
            detachReturnLayer();
            View small = current == null ? null : current.small.get();
            if (small != null && current.thumbnailMuted) {
                MediaBlurHost.release(small);
                current.thumbnailMuted = false;
            }
            if (current != null) {
                current.reveal.discard();
                current.previousBitmap = null;
                current.bitmapChangedBeforeReveal = false;
            }
            for (Saved saved : hidden.values()) saved.restore();
            hidden.clear();
            boundPage = null;
        }

        private void drawArtwork(Canvas canvas) {
            Bitmap image = bitmap;
            if (!showing || image == null || image.isRecycled() || destination.isEmpty()) return;
            float scale =
                    Math.max(
                            destination.width() / image.getWidth(),
                            destination.height() / image.getHeight());
            cover.set(
                    destination.centerX() - image.getWidth() * scale / 2,
                    destination.centerY() - image.getHeight() * scale / 2,
                    destination.centerX() + image.getWidth() * scale / 2,
                    destination.centerY() + image.getHeight() * scale / 2);
            float radius = coverCorner();
            outline.reset();
            outline.addRoundRect(destination, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(outline);
            drawPixels(canvas, destination);
            canvas.restoreToCount(save);
        }

        private void drawPixels(Canvas canvas, RectF bounds) {
            drawPixels(canvas, bounds, true);
        }

        private void drawBlurPixels(Canvas canvas, RectF bounds) {
            drawPixels(canvas, bounds, false);
        }

        private void drawPixels(Canvas canvas, RectF bounds, boolean hardwareReveal) {
            if (current == null || bounds.isEmpty()) return;
            Bitmap closingStill = liveCover.closingStill();
            if ((compact || current.closing) && closingStill != null && !closingStill.isRecycled()) {
                NativeArtworkReveal.bitmap(canvas, closingStill, bounds, 1f, 1f, paint, cover);
                return;
            }
            if (songFade.running() && !current.reveal.hasActiveMask(bitmap)) {
                if (hardwareReveal) {
                    songFade.draw(canvas, bitmap, bounds, paint);
                    current.reveal.drawLighting(canvas, bounds);
                } else {
                    songFade.drawSimple(canvas, bitmap, bounds, paint);
                }
            } else if (hardwareReveal) {
                current.reveal.draw(canvas, bitmap, bounds, paint);
            } else {
                current.reveal.drawSimple(canvas, bitmap, bounds, paint);
            }
        }

        private void updateRegularWidgets(ViewGroup parent) {
            if (RuntimeExtras.enabled(getContext(), "fullscreenArt")) return;
            Art source = null;
            for (Art state : art.values()) {
                View view = state.moving(), overlay = state.overlayHost.get();
                boolean owns =
                        state.overlayActive
                                && overlay != null
                                && overlay.getWindowToken() == parent.getWindowToken();
                if (state.closing
                        && view != null
                        && (owns
                                || view.isShown()
                                        && view.getWindowToken() == parent.getWindowToken())) {
                    source = state;
                    break;
                }
            }
            if (source == null) {
                if (regularPublished) {
                    WidgetMediaOcclusion.publish(parent, null);
                    regularPublished = false;
                }
                return;
            }
            View view = source.moving();
            global.reset();
            rootGlobal.reset();
            View overlay = source.overlayHost.get();
            if (source.overlayActive && overlay != null) {
                global.set(view.getMatrix());
                global.postTranslate(view.getLeft(), view.getTop());
                overlay.transformMatrixToGlobal(rootGlobal);
                local.setConcat(rootGlobal, global);
                global.set(local);
            } else view.transformMatrixToGlobal(global);
            rootGlobal.reset();
            parent.transformMatrixToGlobal(rootGlobal);
            if (rootGlobal.invert(inverse)) {
                local.setConcat(inverse, global);
                nativeLocal.reset();
                nativeOutline.setEmpty();
                if (view.getOutlineProvider() != null)
                    view.getOutlineProvider().getOutline(view, nativeOutline);
                if (view.getClipToOutline() && nativeOutline.getRect(nativeRect))
                    nativeLocal.addRoundRect(
                            new RectF(nativeRect),
                            nativeOutline.getRadius(),
                            nativeOutline.getRadius(),
                            Path.Direction.CW);
                else
                    nativeLocal.addRect(0, 0, view.getWidth(), view.getHeight(), Path.Direction.CW);
                nativeLocal.transform(local, nativeCover);
                WidgetMediaOcclusion.publish(parent, nativeCover);
                regularPublished = true;
            }
        }

        float coverCorner() {
            return Math.min(destination.width(), destination.height())
                    * .5f * (1 - current.progress());
        }

        private void ensureArtworkBlurRecorded(ViewGroup parent) {
            if (artworkBlur == null) return;
            if (destination.isEmpty() && getWidth() > 0 && getHeight() > 0) {
                destination.set(0, 0, getWidth(), getHeight());
            }
            if (destination.isEmpty()) return;
            if (blurDirty || liveBlurDirty || artworkBlur.needsRecording(getWidth(), getHeight())) {
                Canvas record = artworkBlur.beginRecording(getWidth(), getHeight());
                RectF blurRect = artworkBlur.sourceBounds();
                boolean skipStatic = liveCover.coversBlurSource();
                if (!skipStatic) drawBlurPixels(record, blurRect);
                boolean drewLive =
                        parent != null && liveCover.drawBlurFrames(
                                record, parent, blurRect, destination, paint);
                if (skipStatic && !drewLive) drawBlurPixels(record, blurRect);
                artworkBlur.endRecording();
                blurDirty = false;
                liveBlurDirty = false;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float lyricsMix = MediaCoverWakefulness.lyricsBlurAmount();
            if (lyricsMix < 1f && !liveCover.drawAmbient(canvas, root.get(), this)) drawArtwork(canvas);
            if (lyricsMix > 0f) {
                ViewGroup parent = root.get();
                ensureArtworkBlurRecorded(parent);
                if (artworkBlur != null && !destination.isEmpty()) {
                    artworkBlur.drawLyricsBackdrop(canvas, destination, lyricsMix);
                }
                canvas.drawColor(Color.argb(Math.round(45 * lyricsMix), 0, 0, 0));
            }
        }

        private void drawForegroundBlur(Canvas canvas) {
            float lyricsMix = MediaCoverWakefulness.lyricsBlurAmount();
            if (lyricsMix > 0f) canvas.drawColor(Color.argb(Math.round(55 * lyricsMix), 0, 0, 0));
            if (lyricsMix >= 1f) return;
            float playerMix = 1f - lyricsMix;
            ViewGroup parent = root.get();
            boolean topBlurDisabled =
                    isTopBlurDisabled(
                            RuntimeExtras.enabled(getContext(), MediaClockVisibility.KEY),
                            parent != null && MediaClockVisibility.hidden(parent));
            if (liveCover.isAmbientVisible()) {
                ArtworkBlurFilter.Layers ambient = liveCover.ambientArtworkBlur();
                if (canvas.isHardwareAccelerated() && ambient != null) {
                    ambientRect.set(0f, 0f, getWidth(), getHeight());
                    ambient.drawFadedBand(
                            canvas,
                            ambientRect,
                            getHeight() * bandTopNorm,
                            getHeight() * bandSolidNorm,
                            getHeight(),
                            playerMix,
                            null);
                    if (!topBlurDisabled && topBandEndNorm > topBandSolidNorm) {
                        ambient.drawTopProgressiveBand(
                                canvas,
                                ambientRect,
                                getHeight() * topBandSolidNorm,
                                getHeight() * topBandEndNorm,
                                playerMix,
                                null);
                    }
                }
                return;
            }
            Bitmap image = bitmap;
            if (!showing || image == null || image.isRecycled() || destination.isEmpty()) return;
            if (canvas.isHardwareAccelerated()) {
                boolean band = controlsBandOpacity > .005f && destination.bottom > controlsBandTop;
                boolean topBand =
                        !topBlurDisabled
                                && topBandOpacity > .005f
                                && topBandEnd > topBandSolid
                                && destination.top < topBandEnd;
                boolean showClock = parent != null && !MediaClockVisibility.hidden(parent);
                boolean requestedGlass = current != null && MediaClockContrast.wantsGlass(parent);
                boolean glass = false;
                if (band || topBand || requestedGlass || (showClock && (clockMaskDirty || !clockMask.isEmpty()))) {
                    ensureArtworkBlurRecorded(parent);
                    if (requestedGlass) {
                        MediaClockContrast.artwork(
                                parent, artworkBlur.blurred(), artworkBlur.revision(),
                                destination, outline, current.progress());
                        glass = MediaClockContrast.hasGlass(parent);
                    }
                    if (showClock && clockMaskDirty) {
                        clockMask.reset();
                        maskParent.reset();
                        parent.transformMatrixToGlobal(maskParent);
                        if (maskParent.invert(maskLocal))
                            for (Map.Entry<View, ClockPath> entry : clockPaths.entrySet()) {
                                View clock = entry.getKey();
                                if (!contributesClockMask(clock, parent)) continue;
                                ClockPath shape = entry.getValue();
                                maskScratch.set(shape.path);
                                maskScratch.offset(shape.x, shape.y);
                                maskScratch.transform(maskLocal);
                                clockMask.addPath(maskScratch);
                            }
                        clockMaskDirty = false;
                    }
                    // The cached outline draws a second clock, independently of View alpha.
                    if (showClock && !glass && !compact && current != null && !current.closing
                            && current.progress() >= .50f && !clockMask.isEmpty()) {
                        int clip = canvas.save();
                        canvas.clipPath(outline);
                        canvas.clipPath(clockMask);
                        artworkBlur.draw(canvas, destination);
                        canvas.restoreToCount(clip);
                    }
                    if (band) {
                        artworkBlur.drawFadedBand(
                                canvas,
                                destination,
                                controlsBandTop,
                                controlsBandSolid,
                                destination.bottom,
                                controlsBandOpacity * playerMix,
                                outline);
                    }
                    if (topBand) {
                        artworkBlur.drawTopProgressiveBand(
                                canvas,
                                destination,
                                topBandSolid,
                                topBandEnd,
                                topBandOpacity * playerMix,
                                outline);
                    }
                } else {
                    liveBlurDirty = false;
                }
            } else {
                liveBlurDirty = false;
            }
        }
    }

    static void install(DressroomModule owner, ClassLoader loader)
            throws ReflectiveOperationException {
        module = owner;
        OneUi9MediaBinding.HolderContract contract = OneUi9MediaBinding.holderContract(loader);
        installNativeDoze(owner, contract);
        installNativeMediaState(owner, loader);
        for (java.lang.reflect.Constructor<?> constructor : contract.type().getDeclaredConstructors())
            owner.hook(constructor)
                    .setId("dressroom-advanced.media-art-owner")
                    .intercept(
                            chain -> {
                                Object result = chain.proceed();
                                Object object = chain.getThisObject();
                                try {
                                    View view = (View) contract.albumArtParent().get(object);
                                    ImageView image = (ImageView) contract.albumArtBg().get(object);
                                    if (view != null) {
                                        Art artState = new Art(view, image, object, contract);
                                        art.put(view, artState);
                                        markCandidatesDirty();
                                        try {
                                            MediaInteraction.bind(owner, object, view);
                                        } catch (Exception error) {
                                            owner.report("Media touch binding unavailable", error);
                                        }
                                        try {
                                            View controlHost = (View) OneUi9MediaBinding.read(contract.controlHost(), object);
                                            View titleArtist = (View) OneUi9MediaBinding.read(contract.titleArtist(), object);
                                            Object arg1 = chain.getArg(1);
                                            Object arg2 = chain.getArg(2);

                                            ViewGroup pageContainer = null;
                                            if (arg2 instanceof ViewGroup pageGroup) {
                                                pageContainer = pageGroup;
                                            } else if (arg1 instanceof ViewGroup itemGroup) {
                                                pageContainer = itemGroup;
                                            } else if (controlHost != null && controlHost.getParent() instanceof ViewGroup parentGroup) {
                                                pageContainer = parentGroup;
                                            } else if (view.getParent() instanceof ViewGroup parentGroup) {
                                                pageContainer = parentGroup;
                                            }
                                            if (controlHost instanceof ViewGroup hostGroup) {
                                                var lyricsCtrl = dev.denver.dressroomadvanced.lyrics.LyricsController.bind(
                                                        pageContainer,
                                                        hostGroup,
                                                        titleArtist,
                                                        view,
                                                        artState::bitmap);
                                                if (lyricsCtrl != null) {
                                                    lyricsCtrl.applyPreferences(RuntimeExtras.get(hostGroup.getContext()));
                                                }
                                            }
                                        } catch (Exception err) {
                                            android.util.Log.e("DressroomLyrics", "Lyrics binding failed in Holder ctor", err);
                                            owner.report("Lyrics binding skipped", err);
                                        }
                                        view.addOnLayoutChangeListener(
                                                (v, l, t, r, b, ol, ot, or, ob) ->
                                                        markCandidatesDirty());
                                        view.addOnAttachStateChangeListener(
                                                new View.OnAttachStateChangeListener() {
                                                    public void onViewAttachedToWindow(View v) {
                                                        markCandidatesDirty();
                                                        bind(v);
                                                    }

                                                    public void onViewDetachedFromWindow(View v) {
                                                        markCandidatesDirty();
                                                    }
                                                });
                                        bind(view);
                                    }
                                } catch (Exception e) {
                                    owner.report("Artwork binding skipped", e);
                                }
                                return result;
                            });
        installMotion(loader);
        try {
            installSongMotion(owner, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            owner.report("Native song reveal binding unavailable", error);
        }
        try {
            MediaClockContrast.install(owner, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            owner.report("Artwork clock contrast unavailable", error);
        }
        try {
            MediaStatusBarContrast.installPlugin(owner, loader);
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError error) {
            owner.report("Notification icon artwork contrast unavailable", error);
        }
        RuntimeExtras.listen(
                () -> {
                    markCandidatesDirty();
                    for (WeakReference<Backdrop> reference : new ArrayList<>(hosts.values())) {
                        Backdrop backdrop = reference.get();
                        if (backdrop != null) {
                            backdrop.update();
                            backdrop.invalidate();
                            backdrop.overlayLayer.invalidate();
                            ViewGroup root = backdrop.root.get();
                            if (root != null) root.invalidate();
                            dev.denver.dressroomadvanced.lyrics.LyricsController.updateAllPreferences(backdrop.getContext());
                        }
                    }
                });
        android.util.Log.i(DressroomModule.TAG, "Native media artwork source binding installed");
    }

    private static void applyNativeMediaState(
            Backdrop backdrop, boolean previous, boolean expanded) {
        if (backdrop == null) return;
        backdrop.candidatesDirty = true;
        backdrop.hiddenDirty = true;
        if (expanded) {
            if (!previous || backdrop.ambientCollapsed) {
                backdrop.ambientCollapsed = false;
                backdrop.update();
                backdrop.invalidate();
                backdrop.overlayLayer.invalidate();
                ViewGroup root = backdrop.root.get();
                if (root != null) root.invalidate();
            }
        } else if (shouldCloseAmbient(
                false,
                previous,
                backdrop.liveCover.isAmbientVisible(),
                backdrop.showing,
                backdrop.liveCover.ambientHandoff())) {
            backdrop.closeAmbient();
        }
    }

    private static void installNativeMediaState(DressroomModule module, ClassLoader loader) {
        try {
            var binding = OneUi95MediaState.resolve(loader);
            module.hook(binding.changed()).setId("dressroom-advanced.ambient-media-state")
                    .intercept(chain -> {
                        Object observer = chain.getThisObject();
                        boolean previous;
                        try {
                            previous = binding.expanded().getBoolean(observer);
                        } catch (ReflectiveOperationException unavailable) {
                            return chain.proceed();
                        }
                        Object result = chain.proceed();
                        try {
                            boolean expanded = binding.expanded().getBoolean(observer);
                            ViewGroup targetRoot =
                                    binding.status().get(observer) instanceof View status
                                                    && status.getRootView() instanceof ViewGroup r
                                            ? r
                                            : null;
                            WeakReference<Backdrop> direct =
                                    targetRoot == null ? null : hosts.get(targetRoot);
                            Backdrop matched = direct == null ? null : direct.get();
                            if (matched != null) {
                                applyNativeMediaState(matched, previous, expanded);
                            } else if (previous != expanded || !expanded) {
                                for (WeakReference<Backdrop> ref : new ArrayList<>(hosts.values())) {
                                    applyNativeMediaState(ref.get(), previous, expanded);
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException unavailable) {
                            DressroomModule.note("Ambient media state unavailable", unavailable);
                        }
                        return result;
                    });
            module.deoptimize(binding.changed());
            nativeAmbientState = true;
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            DressroomModule.note("Optional ambient media state binding", error);
        }
    }

    private static void installNativeDoze(DressroomModule module,
            OneUi9MediaBinding.HolderContract contract) {
        try {
            ArrayList<Method> candidates = new ArrayList<>();
            for (Method method : contract.type().getDeclaredMethods()) {
                if (method.getReturnType() != void.class || method.getParameterCount() != 2
                        || method.getParameterTypes()[0].isPrimitive()
                        || method.getParameterTypes()[1] != boolean.class) continue;
                var body = DexSignatures.body(method);
                boolean wallpaper = body.calls().stream().anyMatch(call ->
                        call.owner().equals("Lcom/android/systemui/plugins/aod/PluginAODSystemUIConfiguration;")
                                && call.name().equals("get") && call.result().equals("I")
                                && call.parameters().equals(java.util.List.of("I", "I")));
                boolean tint = body.calls().stream().anyMatch(call ->
                        call.owner().equals("Landroid/widget/ImageView;")
                                && call.name().equals("setImageTintList"));
                if (wallpaper && tint) candidates.add(method);
            }
            if (candidates.size() != 1) throw new NoSuchMethodException("Music page AOD appearance");
            Method apply = candidates.get(0);
            apply.setAccessible(true);
            module.hook(apply).setId("dressroom-advanced.media-wallpaper-aod")
                    .intercept(chain -> {
                        try {
                            Object target = contract.albumArtParent().get(chain.getThisObject());
                            if (target instanceof View artwork && artwork.isAttachedToWindow()
                                    && artwork.getRootView() instanceof ViewGroup root) {
                                WeakReference<Backdrop> ref = hosts.get(root);
                                Backdrop host = ref == null ? null : ref.get();
                                if (host != null) {
                                    host.hiddenDirty = true;
                                    host.liveCover.nativeDozeChanged(Boolean.TRUE.equals(chain.getArg(1)));
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            DressroomModule.note("Media artwork AOD state", error);
                        }
                        return chain.proceed();
                    });
            module.deoptimize(apply);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            DressroomModule.note("Optional native media doze binding", error);
        }
    }

    private static void invalidateSong(Art state) {
        markCandidatesDirty();
        for (WeakReference<Backdrop> reference : hosts.values()) {
            Backdrop host = reference.get();
            if (host != null && host.current == state && host.showing) {
                host.blurDirty = true;
                host.invalidate();
                host.overlayLayer.invalidate();
            }
        }
    }

    private static boolean shouldDeferNativeReveal(Art state, Bitmap currentArt) {
        if (state == null || currentArt == null || currentArt.isRecycled()) return false;
        for (WeakReference<Backdrop> reference : hosts.values()) {
            Backdrop host = reference.get();
            if (host != null
                    && host.current == state
                    && host.showing
                    && shouldDeferToRunningCrossfade(
                            host.songFade.running(),
                            host.songFade.target() == currentArt,
                            host.songFade.fraction())) {
                return true;
            }
        }
        return false;
    }

    private static void handoffCrossfadeToNativeReveal(Art state, Bitmap currentArt) {
        if (state == null) return;
        for (WeakReference<Backdrop> reference : hosts.values()) {
            Backdrop host = reference.get();
            if (host != null
                    && host.current == state
                    && host.showing
                    && host.songFade.running()) {
                host.songFade.cancelTo(currentArt);
            }
        }
    }

    private static void installSongMotion(DressroomModule owner, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> masking =
                Class.forName(
                        "com.samsung.android.uniform.nowbar.MediaMaskingFrameLayout",
                        false,
                        loader);
        OneUi9MediaBinding.CoordinatorContract coordinator =
                OneUi9MediaBinding.coordinatorContract(loader);
        Method progress = masking.getDeclaredMethod("setAnimationProgress", float.class),
                random = masking.getDeclaredMethod("setRandomOffset", float.class);
        Method reveal = coordinator.reveal(),
                start = android.animation.AnimatorSet.class.getDeclaredMethod("start");
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    owner.hook(random)
                                    .setId("dressroom-advanced.song-seed")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                Art state = maskOwners.get(chain.getThisObject());
                                                if (state != null)
                                                    state.reveal.random(
                                                            ((Number) chain.getArg(0))
                                                                    .floatValue());
                                                return result;
                                            })
                            ::unhook);
            hooks.add(
                    owner.hook(progress)
                                    .setId("dressroom-advanced.song-progress")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                Art state = maskOwners.get(chain.getThisObject());
                                                if (state != null) {
                                                    float value =
                                                            ((Number) chain.getArg(0)).floatValue();
                                                    Bitmap currentArt = state.bitmap();
                                                    if (value == 0f
                                                            && RuntimeExtras.enabled(
                                                                    ((View) chain.getThisObject())
                                                                            .getContext(),
                                                                    "fullscreenArt")) {
                                                        if (shouldDeferNativeReveal(
                                                                state, currentArt)) {
                                                            state.bitmapChangedBeforeReveal = false;
                                                            state.reveal.progress(1f);
                                                            return result;
                                                        }
                                                        Bitmap previous =
                                                                state.bitmapChangedBeforeReveal
                                                                        ? state.previousBitmap
                                                                        : currentArt;
                                                        state.bitmapChangedBeforeReveal = false;
                                                        handoffCrossfadeToNativeReveal(
                                                                state, currentArt);
                                                        state.reveal.begin(previous, currentArt);
                                                    } else if (shouldDeferNativeReveal(
                                                            state, currentArt)) {
                                                        return result;
                                                    }
                                                    state.reveal.progress(value);
                                                    invalidateSong(state);
                                                }
                                                return result;
                                            })
                            ::unhook);
            hooks.add(
                    owner.hook(reveal)
                                    .setId("dressroom-advanced.song-native-scope")
                                    .intercept(
                                            chain -> {
                                                Art previous = nativeSong.get();
                                                Art state = null;
                                                try {
                                                    state =
                                                            maskOwners.get(
                                                                    coordinator
                                                                            .mask()
                                                                            .get(
                                                                                    chain
                                                                                            .getThisObject()));
                                                } catch (Exception ignored) {
                                                    DressroomModule.note(
                                                            "Fullscreen mask owner field", ignored);
                                                }
                                                if (state != null) nativeSong.set(state);
                                                try {
                                                    return chain.proceed();
                                                } finally {
                                                    if (previous == null) nativeSong.remove();
                                                    else nativeSong.set(previous);
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    owner.hook(start)
                                    .setId("dressroom-advanced.song-native-animator")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                Art state = nativeSong.get();
                                                if (state != null
                                                        && !shouldDeferNativeReveal(
                                                                state, state.displayedBitmap)) {
                                                    state.reveal.animation(
                                                            (android.animation.Animator)
                                                                    chain.getThisObject());
                                                    invalidateSong(state);
                                                }
                                                return result;
                                            })
                            ::unhook);
            owner.deoptimize(progress);
            owner.deoptimize(random);
            owner.deoptimize(reveal);
            owner.deoptimize(start);
            owner.deoptimize(coordinator.lighting());
            owner.deoptimize(OneUi9MediaBinding.animatorUpdate(loader));
            hooks.commit();
        }
    }

    private static void bind(View view) {
        if (view.getRootView() instanceof ViewGroup root) Main.HANDLER.post(() -> ensureHost(root));
        for (View v = view; v != null; v = v.getParent() instanceof View p ? p : null)
            if (v.getClass()
                    .getName()
                    .equals("com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage")) {
                ClassLoader loader = v.getClass().getClassLoader();
                Main.HANDLER.post(() -> installMotion(loader));
                break;
            }
    }

    private static void installMotion(ClassLoader loader) {
        try {
            OneUi9MediaBinding.CardContract cardContract = OneUi9MediaBinding.cardContract(loader);
            Class<?> card = cardContract.cardType();
            if (!motionInstalled.add(card.getClassLoader())) return;
            try {
                MediaInteraction.installStack(module, card.getClassLoader());
            } catch (Exception error) {
                module.report("Pill touch bounds unavailable", error);
            }
            Method invoke = cardContract.expandInvoke();
            module.hook(invoke)
                    .setId("dressroom-advanced.media-expansion-overlay")
                    .intercept(
                            chain -> {
                                Object lambda = chain.getThisObject();
                                Art state = null;
                                View target = null;
                                ViewParent before = null;
                                try {
                                    if (lambda != null
                                            && ((Number) cardContract.expandCaseField().get(lambda))
                                                            .intValue()
                                                    == cardContract.expandCase()) {
                                        View page = (View) cardContract.expandPage().get(lambda),
                                                overlay =
                                                        (View)
                                                                cardContract
                                                                        .expandOverlay()
                                                                        .get(lambda);
                                        target =
                                                find(
                                                        page,
                                                        "facewidget_album_art_vi_container_parent");
                                        state = art.get(target);
                                        if (state != null
                                                && page != null
                                                && RuntimeExtras.enabled(
                                                        page.getContext(), "fullscreenArt")) {
                                            before = target.getParent();
                                            state.overlayHost = new WeakReference<>(overlay);
                                            state.startScale = 1;
                                        } else state = null;
                                    }
                                } catch (Exception error) {
                                    state = null;
                                    target = null;
                                }
                                Object result = chain.proceed();
                                if (state != null && target != null) {
                                    state.startScale = target.getScaleX();
                                    state.overlayActive = before != target.getParent();
                                    markCandidatesDirty();
                                }
                                return result;
                            });
            module.deoptimize(invoke);
            Method stateChanged = cardContract.cardStateChanged();
            module.hook(stateChanged)
                    .setId("dressroom-advanced.media-native-reveal")
                    .intercept(
                            chain -> {
                                boolean animatedFullscreenReturn = false;
                                try {
                                    View page =
                                            (View)
                                                    cardContract
                                                            .expandedMediaView()
                                                            .get(chain.getThisObject());
                                    Art state =
                                            art.get(
                                                    find(
                                                            page,
                                                            "facewidget_album_art_vi_container_parent"));
                                    if (state != null) {
                                        markCandidatesDirty();
                                        state.closing = Boolean.FALSE.equals(chain.getArg(0));
                                        if (Boolean.TRUE.equals(chain.getArg(0))) {
                                            for (WeakReference<Backdrop> reference :
                                                    hosts.values()) {
                                                Backdrop host = reference.get();
                                                if (host != null) host.ambientCollapsed = false;
                                            }
                                        }
                                        if (state.closing) {
                                            View closingCard = (View) chain.getThisObject();
                                            animatedFullscreenReturn = RuntimeExtras.enabled(
                                                    closingCard.getContext(), "fullscreenArt");
                                            state.normalContent =
                                                    new WeakReference<>(
                                                            (View)
                                                                    Reflect.call(
                                                                            chain.getThisObject(),
                                                                            "getNormalContentRootView"));
                                            for (WeakReference<Backdrop> reference :
                                                    hosts.values()) {
                                                Backdrop host = reference.get();
                                                if (host != null) host.beginClosing(state);
                                            }
                                        }
                                    }
                                } catch (Exception error) {
                                    module.report(
                                            "Media compact-content reveal unavailable", error);
                                }
                                Object result = chain.proceed();
                                if (Boolean.FALSE.equals(chain.getArg(0))) {
                                    if (LyricsController.isLyricsActive()) {
                                        LyricsController.hideActiveLyrics();
                                    }
                                    if (!animatedFullscreenReturn
                                            && chain.getThisObject() instanceof View mediaCard) {
                                        finishCompactCollapse(mediaCard, cardContract);
                                    }
                                }
                                return result;
                            });
            module.deoptimize(stateChanged);

            Method collapseGeometry = cardContract.collapseGeometry();
            module.hook(collapseGeometry)
                    .setId("dressroom-advanced.media-collapse-geometry")
                    .intercept(
                            chain -> {
                                if (LyricsController.isLyricsActive()) {
                                    LyricsController.hideActiveLyrics();
                                }
                                View big = (View) chain.getArg(2),
                                        small = (View) chain.getArg(3);
                                ViewParent before = small.getParent();
                                Object result = chain.proceed();
                                Art state = art.get(big);
                                if (state != null && before != small.getParent()) {
                                    state.closing = true;
                                    state.small = new WeakReference<>(small);
                                    state.collapseScale = small.getScaleX();
                                    state.overlayHost = new WeakReference<>((View) chain.getArg(1));
                                    state.overlayActive = true;
                                    state.returningCard =
                                            new WeakReference<>((View) chain.getArg(0));
                                    try {
                                        state.normalContent =
                                                new WeakReference<>(
                                                        (View)
                                                                Reflect.call(
                                                                        chain.getArg(0),
                                                                        "getNormalContentRootView"));
                                    } catch (Exception ignored) {
                                        DressroomModule.note(
                                                "Fullscreen normal content root", ignored);
                                        state.normalContent.clear();
                                    }
                                    try {
                                        state.returnHeight =
                                                ((Number)
                                                                Reflect.call(
                                                                        chain.getArg(0),
                                                                        "getNormalCardHeight"))
                                                        .floatValue();
                                    } catch (Exception ignored) {
                                        DressroomModule.note(
                                                "Fullscreen normal card height", ignored);
                                        state.returnHeight = small.getHeight() * 1.8f;
                                    }
                                    collapsing.put(small, state);
                                    if (RuntimeExtras.enabled(big.getContext(), "fullscreenArt")) {
                                        MediaBlurHost.hold(small);
                                        state.thumbnailMuted = true;
                                    }
                                    markCandidatesDirty();
                                }
                                return result;
                            });
            module.deoptimize(collapseGeometry);

            Method collapseCleanup = cardContract.collapseCleanup();
            module.hook(collapseCleanup)
                    .setId("dressroom-advanced.media-collapse-cleanup")
                    .intercept(
                            chain -> {
                                if (LyricsController.isLyricsActive()) {
                                    LyricsController.hideActiveLyrics();
                                }
                                View small = (View) chain.getArg(1);
                                Art state = collapsing.remove(small);
                                Object result = chain.proceed();
                                if (state != null) {
                                    View closingCard = state.returningCard.get();
                                    if (state.thumbnailMuted) {
                                        MediaBlurHost.release(small);
                                        state.thumbnailMuted = false;
                                    }
                                    state.small.clear();
                                    state.closing = false;
                                    state.startScale = 1;
                                    state.overlayActive = false;
                                    state.overlayHost.clear();
                                    state.returningCard.clear();
                                    state.normalContent.clear();
                                    markCandidatesDirty();
                                    if (closingCard != null && RuntimeExtras.enabled(
                                            closingCard.getContext(), "fullscreenArt")) {
                                        // The compact pill can settle once the artwork reaches its thumbnail.
                                        finishCompactCollapse(closingCard, cardContract);
                                    }
                                }
                                return result;
                            });
            module.deoptimize(collapseCleanup);

            Method expansionCleanup = cardContract.expansionCleanup();
            module.hook(expansionCleanup)
                    .setId("dressroom-advanced.media-expansion-cleanup")
                    .intercept(
                            chain -> {
                                Art state = art.get((View) chain.getArg(1));
                                Object result = chain.proceed();
                                if (state != null) {
                                    state.closing = false;
                                    state.overlayActive = false;
                                    state.overlayHost.clear();
                                    state.startScale = 1;
                                    markCandidatesDirty();
                                }
                                return result;
                            });
            module.deoptimize(expansionCleanup);
        } catch (Exception | LinkageError e) {
            module.report("Media motion binding unavailable", e);
        }
    }

    static float compactReveal(float coverProgress) {
        return clamp((.2f - coverProgress) / .2f);
    }

    static void finishCompactCollapse(
            View mediaCard, OneUi9MediaBinding.CardContract contract) {
        if (mediaCard == null || contract == null) return;
        boolean awake = true;
        try {
            if (Reflect.call(mediaCard, "getDarkAmount") instanceof Number dark)
                awake = dark.floatValue() == 0f;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Method cancelSpring = contract.springCancel();
            if (cancelSpring != null) {
                for (Field field : contract.cardSpringFields()) {
                    Object spring = field.get(mediaCard);
                    if (spring != null) cancelSpring.invoke(spring);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Media pill spring settle", error);
        }
        try {
            if (Reflect.call(mediaCard, "getNormalCardRadius") instanceof Number radius)
                Reflect.call(mediaCard, "setCurrentRadius", radius.floatValue());
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Media pill radius settle", error);
        }
        try {
            for (Field field : contract.cardAnimatorFields()) {
                if (field.get(mediaCard) instanceof android.animation.ObjectAnimator animator
                        && (animator.isStarted() || animator.isRunning())) {
                    animator.end();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Media pill alpha animator settle", error);
        }
        try {
            if (Reflect.call(mediaCard, "getBlurContainer") instanceof View blur) {
                blur.setVisibility(View.VISIBLE);
                NowBarGlass.ensurePillBlur(mediaCard, blur);
                Drawable background = blur.getBackground();
                if (background != null) background.setAlpha(255);
            }
            if ( awake && Reflect.call(mediaCard, "getBorderStrokeView") instanceof View border) {
                border.setVisibility(View.VISIBLE);
                border.setAlpha(1f);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Media pill blur restore", error);
        }
        if (awake && mediaCard.getParent() instanceof ViewGroup stack) {
            for (int i = 0; i < stack.getChildCount(); i++) {
                View sibling = stack.getChildAt(i);
                if (sibling != null
                        && sibling != mediaCard
                        && sibling.getVisibility() == View.INVISIBLE) {
                    sibling.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    static boolean visibleControls(View view) {
        return controlsAlpha(view) > .01f;
    }

    private static float controlsAlpha(View view) {
        if (view == null || !view.isShown()) return 0f;
        float alpha = 1;
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            alpha *= current.getAlpha();
            if (alpha <= .005f) return 0f;
        }
        return alpha;
    }

    private static View find(View root, String name) {
        if (root == null) return null;
        if (name(root).equals(name)) return root;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), name);
                if (found != null) return found;
            }
        return null;
    }
}
