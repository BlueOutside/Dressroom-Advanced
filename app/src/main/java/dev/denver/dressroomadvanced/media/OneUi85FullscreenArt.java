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
        installCaptureTrace(module, loader);
        try {
            MediaClockContrast.install(module, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            module.report("8.5 artwork clock appearance unavailable", error);
        }
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(binding.constructor)
                                    .setId("dressroom-advanced.85-artwork-owner")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                try {
                                                    register((View) chain.getArg(1));
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

    private static void register(View item) {
        View frame = find(item, "now_bar_media_album_art_vi_container");
        View image = find(item, "facewidget_album_art_background");
        if (frame == null || !(image instanceof ImageView art)) return;
        sources.put(frame, new Source(item, frame, art));
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
        int index = root.getChildCount();
        for (int i = 0; i < root.getChildCount(); i++) {
            String id = name(root.getChildAt(i));
            if (id.equals("notification_panel") || id.equals("lock_clock_blur")) {
                index = i;
                break;
            }
        }
        root.addView(layer, index, new FrameLayout.LayoutParams(-1, -1));
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
        final Rect visible = new Rect();

        Source(View item, View frame, ImageView image) {
            this.item = new WeakReference<>(item);
            this.frame = new WeakReference<>(frame);
            this.background = new WeakReference<>(find(item, "nowbar_expandview_background"));
            this.controls = new WeakReference<>(find(item, "facewidget_music_container_expand_Player"));
            this.tint = new WeakReference<>(find(item, "facewidget_music_page_controller_background"));
            this.nativeBannerBlur = new WeakReference<>(find(item, "facewidget_music_page_controller_blur_view"));
            this.image = new WeakReference<>(image);
        }

        Bitmap bitmap() {
            ImageView art = image.get();
            return art != null
                            && art.getDrawable() instanceof BitmapDrawable drawable
                            && !drawable.getBitmap().isRecycled()
                    ? drawable.getBitmap()
                    : null;
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

    private static final class Layer extends View implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ViewGroup> root;
        final RectF paletteBounds = new RectF();
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        final Set<View> held = Collections.newSetFromMap(new WeakHashMap<>());
        final Set<View> clockBlurHosts = Collections.newSetFromMap(new WeakHashMap<>());
        final Map<View, Object> clockBlurRequests = new WeakHashMap<>();
        final Map<View, Blank> backgrounds = new WeakHashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fadePaint = new Paint(paint);
        final RectF bounds = new RectF(), crop = new RectF();
        final android.graphics.RenderNode blurredArt =
                new android.graphics.RenderNode("Dressroom 8.5 media clock aura");
        boolean blurDirty = true;
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

        Layer(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            float radius = 24f * getResources().getDisplayMetrics().density;
            blurredArt.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                            radius, radius, android.graphics.Shader.TileMode.CLAMP));
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
        }

        @Override
        protected void onDetachedFromWindow() {
            ViewGroup parent = root.get();
            if (parent != null) MediaClockContrast.clear(parent);
            clearBanner();
            clearFade();
            nativeExpanded = false;
            mediaStateKnown = false;
            nativeAlpha = 0f;
            restore();
            releaseClockBlur();
            current = null;
            bitmap = null;
            blurredArt.discardDisplayList();
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
            // The native artwork is hidden before the wallpaper-return fade finishes.
            // Keep its last texture until that native fade reaches zero.
            if (enabled
                    && !nativeExpanded
                    && nativeFade
                    && bitmap != null
                    && !bitmap.isRecycled()) {
                MediaClockContrast.clear(parent);
                present(nativeAlpha);
                if (nativeAlpha <= 0) hide();
                return;
            }
            if (mediaStateKnown && !nativeExpanded) {
                hide();
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
                    present(nativeFade ? nativeAlpha : 1f);
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
            if (bitmap != next) {
                bitmap = next;
                blurDirty = true;
                invalidate();
            }
            paletteBounds.set(0, 0, parent.getWidth(), parent.getHeight());
            try {
                MediaClockContrast.apply(parent, bitmap, paletteBounds);
            } catch (RuntimeException error) {
                MediaClockContrast.clear(parent);
            }
            present(alpha);
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
            releaseClockBlur();
            if (bitmap == null && current == null && getVisibility() == INVISIBLE) return;
            ViewGroup parent = root.get();
            if (parent != null) MediaClockContrast.clear(parent);
            restore();
            current = null;
            bitmap = null;
            blurredArt.discardDisplayList();
            blurDirty = true;
            setVisibility(INVISIBLE);
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
                if (controls != null && controls.getRootView() == parent) return;
                clearBanner();
            }
            if (!nativeExpanded) return;
            for (Source source : sources.values()) {
                if (!(source.controls.get() instanceof FrameLayout controls)
                        || controls.getRootView() != parent || !controls.isShown()
                        || source.tint.get() == null) continue;
                bannerSource = source;
                bannerBlur = BannerBlur.attach(controls);
                if (bannerBlur != null) {
                    MediaBlurHost.hold(source.tint.get());
                    View nativeBlur = source.nativeBannerBlur.get();
                    if (nativeBlur != null) MediaBlurHost.hold(nativeBlur);
                }
                return;
            }
        }

        void clearBanner() {
            if (bannerSource != null && bannerBlur != null) {
                View tint = bannerSource.tint.get();
                if (tint != null) MediaBlurHost.release(tint);
                View nativeBlur = bannerSource.nativeBannerBlur.get();
                if (nativeBlur != null) MediaBlurHost.release(nativeBlur);
                if (bannerBlur.getParent() instanceof ViewGroup group) group.removeView(bannerBlur);
            }
            bannerSource = null;
            bannerBlur = null;
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
            if (bitmap == null || bitmap.isRecycled()) return;
            bounds.set(0, 0, getWidth(), getHeight());
            // Keep the view opaque in HWUI. Changing View.alpha at 1 can switch off
            // its compositing layer exactly when the native blur regions settle.
            fadePaint.setAlpha(Math.round(coverAlpha * 255));
            NativeArtworkReveal.bitmap(canvas, bitmap, bounds, 1, 1, fadePaint, crop);
            if (trace && nativeExpanded && !nativeFade && !tracedDraw) {
                tracedDraw = true;
                traceLog("settled cover drawn hardware=" + canvas.isHardwareAccelerated());
            }
            ViewGroup parent = root.get();
            if (canvas.isHardwareAccelerated() && MediaClockContrast.hasAura(parent)) {
                if (blurDirty
                        || blurredArt.getWidth() != getWidth()
                        || blurredArt.getHeight() != getHeight()) {
                    blurredArt.setPosition(0, 0, getWidth(), getHeight());
                    Canvas record = blurredArt.beginRecording();
                    NativeArtworkReveal.bitmap(record, bitmap, bounds, 1, 1, paint, crop);
                    blurredArt.endRecording();
                    blurDirty = false;
                }
                MediaClockContrast.drawAura(canvas, parent, blurredArt, bounds, null, coverAlpha);
            }
        }
    }

    static final class BannerBlur extends View {
        private BannerBlur(android.content.Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        static View attach(FrameLayout controls) {
            var context = controls.getContext();
            if (!BlurAppearance.liveWindowBlurAllowed(context)) return null;
            BannerBlur layer = new BannerBlur(context);
            try {
                int dimension = controls.getResources().getIdentifier(
                        "now_bar_music_page_controller_radius", "dimen", "com.samsung.android.app.aodservice");
                if (dimension == 0) dimension = controls.getResources().getIdentifier(
                        "now_bar_music_page_controller_radius", "dimen", "com.samsung.android.app.clockpack");
                if (dimension == 0) return null;
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
                Object request = builder.getConstructor(int.class).newInstance(0);
                builder.getMethod("setRadius", int.class).invoke(request, 127);
                builder.getMethod("setBackgroundCornerRadius", float.class)
                        .invoke(request, controls.getResources().getDimension(dimension));
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
