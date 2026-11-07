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

    private OneUi85FullscreenArt() {}

    static synchronized void install(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        OneUi85ArtworkBinding binding = new OneUi85ArtworkBinding(base);
        OneUi85WallpaperFadeBinding wallpaper = new OneUi85WallpaperFadeBinding(base);
        MediaBlurHost.install(module);
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
                            layer.update();
                            layer.invalidate();
                        }
                    }
                });
        android.util.Log.i(DressroomModule.TAG, "8.5 fullscreen artwork source installed");
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

    static void wallpaperFade(View host, ValueAnimator animator, boolean expanded) {
        if (host == null || animator == null) return;
        Layer layer = ensure(host);
        if (layer != null) layer.bindFade(animator, expanded);
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
        final WeakReference<View> item, frame, background;
        final WeakReference<ImageView> image;
        final Rect visible = new Rect();

        Source(View item, View frame, ImageView image) {
            this.item = new WeakReference<>(item);
            this.frame = new WeakReference<>(frame);
            this.background = new WeakReference<>(find(item, "nowbar_expandview_background"));
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
            if (view != null && animation instanceof ValueAnimator value) view.sample(value);
        }
    }

    private static final class Layer extends View implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ViewGroup> root;
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        final Set<View> held = Collections.newSetFromMap(new WeakHashMap<>());
        final Map<View, Blank> backgrounds = new WeakHashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF bounds = new RectF(), crop = new RectF();
        Source current;
        Bitmap bitmap;
        WeakReference<ValueAnimator> fadeAnimator = new WeakReference<>(null);
        FadeListener fadeListener;
        boolean nativeFade, nativeExpanded;
        float nativeAlpha;

        Layer(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
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
            clearFade();
            restore();
            current = null;
            bitmap = null;
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
            // The native artwork is hidden before the wallpaper-return fade finishes.
            // Keep its last texture until that native fade reaches zero.
            if (enabled
                    && nativeFade
                    && !nativeExpanded
                    && bitmap != null
                    && !bitmap.isRecycled()) {
                present(nativeAlpha);
                if (nativeAlpha <= 0) hide();
                return;
            }
            Source best = null;
            float score = 0, alpha = 0;
            if (enabled)
                for (Source source : sources.values()) {
                    View frame = source.frame.get();
                    if (frame == null
                            || frame.getRootView() != parent
                            || source.bitmap() == null
                            || !frame.getGlobalVisibleRect(source.visible)) continue;
                    float value = nativeFade ? nativeAlpha : opacity(frame, parent);
                    float size = source.visible.width() * (float) source.visible.height() * value;
                    if (size > score) {
                        score = size;
                        best = source;
                        alpha = value;
                    }
                }
            if (best == null) {
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
                invalidate();
            }
            present(alpha);
        }

        void present(float alpha) {
            if (getAlpha() != alpha) setAlpha(alpha);
            setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
        }

        void hide() {
            restore();
            current = null;
            bitmap = null;
            setVisibility(INVISIBLE);
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
            if (!animation.isStarted()) {
                Object value = animation.getAnimatedValue();
                if (!(value instanceof Number number)
                        || !Float.isFinite(number.floatValue())
                        || (expanded ? number.floatValue() < 255 : number.floatValue() > 0)) {
                    clearFade();
                    invalidateWindow();
                    return;
                }
            }
            if (old != animation) {
                clearFade();
                fadeAnimator = new WeakReference<>(animation);
                fadeListener = new FadeListener(this);
                animation.addUpdateListener(fadeListener);
                animation.addListener(fadeListener);
            }
            nativeExpanded = expanded;
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
            NativeArtworkReveal.bitmap(canvas, bitmap, bounds, 1, 1, paint, crop);
        }
    }
}
