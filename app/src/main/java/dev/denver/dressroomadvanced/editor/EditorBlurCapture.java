package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Samsung's canvas blur normally records the whole window, including foreground content. */
final class EditorBlurCapture {
    private static final Map<View, WeakReference<View>> wallpapers = new WeakHashMap<>();
    private static final Map<View, Capture> targets = new WeakHashMap<>();
    private static final ThreadLocal<Capture> capturing = new ThreadLocal<>();
    private static final Paint SNAPSHOT_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static WallpaperSnapshot snapshotCache;
    private static boolean installed;
    private static boolean reported;

    private static final class LayerState {
        final WeakReference<View> view;
        final int visibility;
        final int alphaBits;
        final WeakReference<Drawable> drawable;
        final Rect drawableBounds;
        final int drawableLevel;
        final WeakReference<ColorFilter> drawableColorFilter;
        final WeakReference<ColorFilter> viewColorFilter;
        final int bitmapGeneration;
        final WeakReference<Drawable> background;
        final Rect backgroundBounds;
        final int backgroundLevel;
        final WeakReference<ColorFilter> backgroundColorFilter;
        final int backgroundGeneration;
        final Matrix imageMatrix;
        final Matrix viewMatrix;

        private LayerState(View child) {
            this.view = new WeakReference<>(child);
            this.visibility = child.getVisibility();
            this.alphaBits = Float.floatToIntBits(child.getAlpha());
            Drawable d = child instanceof ImageView iv ? iv.getDrawable() : null;
            this.drawable = new WeakReference<>(d);
            this.drawableBounds = d == null ? null : new Rect(d.getBounds());
            this.drawableLevel = d == null ? 0 : d.getLevel();
            this.drawableColorFilter =
                    new WeakReference<>(d == null ? null : d.getColorFilter());
            this.viewColorFilter =
                    new WeakReference<>(child instanceof ImageView iv ? iv.getColorFilter() : null);
            this.bitmapGeneration = drawableBitmapGeneration(d);
            Drawable bg = child.getBackground();
            this.background = new WeakReference<>(bg);
            this.backgroundBounds = bg == null ? null : new Rect(bg.getBounds());
            this.backgroundLevel = bg == null ? 0 : bg.getLevel();
            this.backgroundColorFilter =
                    new WeakReference<>(bg == null ? null : bg.getColorFilter());
            this.backgroundGeneration = drawableBitmapGeneration(bg);
            this.imageMatrix =
                    child instanceof ImageView iv ? new Matrix(iv.getImageMatrix()) : null;
            Matrix vm = child.getMatrix();
            this.viewMatrix = vm == null || vm.isIdentity() ? null : new Matrix(vm);
        }

        private static int drawableBitmapGeneration(Drawable drawable) {
            Drawable current = drawable;
            for (int depth = 0; depth < 8 && current instanceof DrawableWrapper wrapper; depth++) {
                current = wrapper.getDrawable();
            }
            if (current instanceof BitmapDrawable bd) {
                Bitmap bmp = bd.getBitmap();
                if (bmp != null && !bmp.isRecycled()) return bmp.getGenerationId();
            }
            return 0;
        }

        boolean matches(LayerState other) {
            return other != null
                    && view.get() != null
                    && view.get() == other.view.get()
                    && visibility == other.visibility
                    && alphaBits == other.alphaBits
                    && drawable.get() == other.drawable.get()
                    && Objects.equals(drawableBounds, other.drawableBounds)
                    && drawableLevel == other.drawableLevel
                    && Objects.equals(drawableColorFilter.get(), other.drawableColorFilter.get())
                    && Objects.equals(viewColorFilter.get(), other.viewColorFilter.get())
                    && bitmapGeneration == other.bitmapGeneration
                    && background.get() == other.background.get()
                    && Objects.equals(backgroundBounds, other.backgroundBounds)
                    && backgroundLevel == other.backgroundLevel
                    && Objects.equals(backgroundColorFilter.get(), other.backgroundColorFilter.get())
                    && backgroundGeneration == other.backgroundGeneration
                    && Objects.equals(imageMatrix, other.imageMatrix)
                    && Objects.equals(viewMatrix, other.viewMatrix);
        }
    }

    private static final class CompositeSignature {
        final WeakReference<View> source;
        final int width;
        final int height;
        final RectF cropBounds;
        final List<LayerState> layers;

        private CompositeSignature(
                View source, int width, int height, RectF cropBounds, List<LayerState> layers) {
            this.source = new WeakReference<>(source);
            this.width = width;
            this.height = height;
            this.cropBounds = cropBounds == null ? null : new RectF(cropBounds);
            this.layers = layers;
        }

        static CompositeSignature capture(View image, View source, RectF cropBounds) {
            List<LayerState> layers = new ArrayList<>(4);
            collect(source, image, source, layers);
            return new CompositeSignature(
                    source, source.getWidth(), source.getHeight(), cropBounds, layers);
        }

        private static void collect(
                View node, View primary, View rootSource, List<LayerState> out) {
            if (node == null) return;
            boolean include = node == rootSource || node == primary || node.isShown();
            if (!include) return;
            out.add(new LayerState(node));
            if (node instanceof ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    collect(group.getChildAt(i), primary, rootSource, out);
                }
            }
        }

        boolean matches(CompositeSignature other) {
            if (other == null
                    || source.get() == null
                    || source.get() != other.source.get()
                    || width != other.width
                    || height != other.height
                    || !Objects.equals(cropBounds, other.cropBounds)
                    || layers.size() != other.layers.size()) {
                return false;
            }
            for (int i = 0; i < layers.size(); i++) {
                if (!layers.get(i).matches(other.layers.get(i))) return false;
            }
            return true;
        }
    }

    private static final class WallpaperSnapshot {
        final CompositeSignature signature;
        final Bitmap bitmap;

        WallpaperSnapshot(CompositeSignature signature, Bitmap bitmap) {
            this.signature = signature;
            this.bitmap = bitmap;
        }

        boolean matches(CompositeSignature current) {
            return bitmap != null && !bitmap.isRecycled() && signature.matches(current);
        }
    }

    private EditorBlurCapture() {}

    static boolean capturingWallpaper() {
        return capturing.get() != null;
    }

    static void install(DressroomModule module) throws ReflectiveOperationException {
        if (installed || !EditorWidgetBlur.canvasSupported()) return;
        Method blur = View.class.getDeclaredMethod("drawBlurEffect", Canvas.class);
        Method draw = View.class.getDeclaredMethod("draw", Canvas.class);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(blur)
                    .setId("dressroom-advanced.editor-canvas-blur-capture")
                    .intercept(chain -> {
                        if (targets.isEmpty()) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        Capture capture = targets.get(view);
                        if (capture == null) return chain.proceed();
                        View image = capture.wallpaper(view);
                        if (image == null) return null;
                        LockscreenFrameBoost.pulse(view, 220L);
                        Capture previous = capturing.get();
                        capturing.set(capture);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) capturing.remove();
                            else capturing.set(previous);
                        }
                    })::unhook);
            hooks.add(module.hook(draw)
                    .setId("dressroom-advanced.editor-canvas-blur-wallpaper")
                    .intercept(chain -> {
                        Capture capture = capturing.get();
                        if (capture == null) return chain.proceed();
                        View image = capture.image.get();
                        if (image == null || chain.getThisObject() != image.getRootView()
                                || ((Canvas) chain.getArg(0)).isHardwareAccelerated())
                            return chain.proceed();
                        try {
                            drawWallpaper(
                                    image,
                                    capture.frame.get(),
                                    capture.cropBounds(image),
                                    (Canvas) chain.getArg(0));
                        } catch (RuntimeException error) {
                            if (!reported) {
                                reported = true;
                                DressroomModule.note("Editor wallpaper blur capture", error);
                            }
                        }
                        return null;
                    })::unhook);
            module.deoptimize(blur);
            module.deoptimize(draw);
            hooks.commit();
            installed = true;
        }
    }

    static boolean available() {
        return installed;
    }

    static boolean prepare(View view, boolean clock) {
        if (!installed) return false;
        Capture capture = targets.get(view);
        if (capture != null) return capture.wallpaper(view) != null;
        capture = new Capture(view, clock);
        if (capture.wallpaper(view) == null) return false;
        targets.put(view, capture);
        if (!clock) capture.watch(view);
        return true;
    }

    static void clear(View view) {
        Capture capture = targets.remove(view);
        if (capture != null) capture.release(view);
    }

    static void afterPlacement(View root) {
        for (var entry : targets.entrySet())
            if (!entry.getValue().clock && inside(entry.getKey(), root))
                entry.getValue().moveWatcherToEnd();
    }

    private static final class Capture implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        final WeakReference<View> target;
        final boolean clock;
        final ViewGeometry widgetGeometry = new ViewGeometry();
        final ViewGeometry imageGeometry = new ViewGeometry();
        final ViewGeometry frameGeometry = new ViewGeometry();
        WeakReference<View> frame = new WeakReference<>(null), image = new WeakReference<>(null);
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        Class<?> cropClass;
        Method cropBounds;
        RectF lastBounds;
        CompositeSignature lastSignature;

        Capture(View target, boolean clock) {
            this.target = new WeakReference<>(target);
            this.clock = clock;
        }

        View wallpaper(View view) {
            View root = view.getRootView();
            View preview = frame.get();
            View crop = image.get();
            if (ready(crop, root) && (clock || (preview != null && inside(view, preview)
                    && inside(crop, preview)))) return crop;
            if (clock) {
                WeakReference<View> ref = wallpapers.get(root);
                crop = ref == null ? null : ref.get();
                if (!ready(crop, root)) {
                    crop = DepthEffects.findCropView(root);
                    if (!ready(crop, root)) return null;
                    wallpapers.put(root, new WeakReference<>(crop));
                }
            } else {
                preview = previewFrame(view);
                View wallpaper = preview == null ? null : preview.findViewWithTag("WallpaperPreviewContainer");
                crop = wallpaper == null ? null : DepthEffects.findCropView(wallpaper);
                if (!ready(crop, root)) {
                    image.clear();
                    return null;
                }
                frame = new WeakReference<>(preview);
            }
            image = new WeakReference<>(crop);
            return crop;
        }

        void watch(View view) {
            ViewTreeObserver tree = view.getViewTreeObserver();
            observer = new WeakReference<>(tree);
            tree.addOnPreDrawListener(this);
            view.addOnAttachStateChangeListener(this);
        }

        void moveWatcherToEnd() {
            ViewTreeObserver tree = observer.get();
            if (tree == null || !tree.isAlive()) return;
            tree.removeOnPreDrawListener(this);
            tree.addOnPreDrawListener(this);
        }

        void release(View view) {
            ViewTreeObserver tree = observer.get();
            if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
            if (tree != view.getViewTreeObserver()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View view) {
            observer = new WeakReference<>(view.getViewTreeObserver());
        }

        @Override public void onViewDetachedFromWindow(View view) {
            clear(view);
        }

        @Override public boolean onPreDraw() {
            View widget = target.get();
            if (widget == null) {
                ViewTreeObserver tree = observer.get();
                if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
                return true;
            }
            if (!widget.isShown()) return true;
            View crop = wallpaper(widget);
            View preview = frame.get();
            if (crop == null || preview == null) return true;
            View source = captureSource(crop);
            if (source == null) return true;
            boolean moved = widgetGeometry.update(widget, source, preview);
            moved |= imageGeometry.update(source,
                    source instanceof ImageView imageView ? imageView.getDrawable() : null,
                    preview);
            moved |= frameGeometry.update(preview, null, null);
            RectF bounds = cropBounds(crop);
            moved |= !Objects.equals(lastBounds, bounds);
            lastBounds = bounds;
            CompositeSignature signature = CompositeSignature.capture(crop, source, bounds);
            moved |= lastSignature == null || !lastSignature.matches(signature);
            lastSignature = signature;
            if (moved) widget.invalidate();
            return true;
        }

        RectF cropBounds(View crop) {
            if (cropClass != crop.getClass()) {
                cropClass = crop.getClass();
                try {
                    cropBounds = cropClass.getMethod("getDrawableBounds");
                } catch (NoSuchMethodException absent) {
                    cropBounds = null;
                }
            }
            if (cropBounds == null) return null;
            try {
                return (RectF) cropBounds.invoke(crop);
            } catch (ReflectiveOperationException | RuntimeException error) {
                cropBounds = null;
                return null;
            }
        }
    }

    private static View previewFrame(View view) {
        for (View current = view; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getId() == View.NO_ID) continue;
            try {
                if ("preview_single_view_root".equals(
                        current.getResources().getResourceEntryName(current.getId()))) return current;
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        }
        return null;
    }

    private static boolean inside(View view, View parent) {
        for (View current = view; current != null;
                current = current.getParent() instanceof View up ? up : null)
            if (current == parent) return true;
        return false;
    }

    private static View captureSource(View image) {
        if (image != null
                && image.getParent() instanceof ViewGroup parent
                && parent.isShown()
                && EditorWallpaperSource.hasVisibleEffectOverlay(image)) {
            return parent;
        }
        return image;
    }

    private static boolean ready(View image, View root) {
        if (image == null || image.getRootView() != root || !EditorWallpaperSource.available(image)) {
            return false;
        }
        View source = captureSource(image);
        return source != null && source.getWidth() > 0 && source.getHeight() > 0;
    }

    private static Bitmap snapshotBitmap(View image, View source, RectF cropBounds) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) return null;
        CompositeSignature signature = CompositeSignature.capture(image, source, cropBounds);
        WallpaperSnapshot existing = snapshotCache;
        if (existing != null && existing.matches(signature)) {
            return existing.bitmap;
        }
        int sw = Math.max(1, (w + 2) / 3);
        int sh = Math.max(1, (h + 2) / 3);
        try {
            Bitmap bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale((float) sw / w, (float) sh / h);
            source.draw(c);
            if (existing != null && existing.bitmap != null && !existing.bitmap.isRecycled()) {
                existing.bitmap.recycle();
            }
            snapshotCache = new WallpaperSnapshot(signature, bmp);
            return bmp;
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    private static void drawWallpaper(
            View image, View preview, RectF cropBounds, Canvas canvas) {
        View source = captureSource(image);
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        Matrix rootToWindow = new Matrix(), windowToRoot = new Matrix();
        source.getRootView().transformMatrixToGlobal(rootToWindow);
        if (!rootToWindow.invert(windowToRoot)) return;
        Matrix sourceToWindow = new Matrix(), sourceToRoot = new Matrix();
        source.transformMatrixToGlobal(sourceToWindow);
        sourceToRoot.setConcat(windowToRoot, sourceToWindow);
        int saved = canvas.save();
        try {
            if (preview != null) {
                Matrix frameToWindow = new Matrix(), frameToRoot = new Matrix();
                preview.transformMatrixToGlobal(frameToWindow);
                frameToRoot.setConcat(windowToRoot, frameToWindow);
                Path frameClip = new Path();
                frameClip.addRect(0, 0, preview.getWidth(), preview.getHeight(), Path.Direction.CW);
                frameClip.transform(frameToRoot);
                canvas.clipPath(frameClip);
            }
            canvas.concat(sourceToRoot);
            canvas.clipRect(0, 0, source.getWidth(), source.getHeight());
            Bitmap cached = snapshotBitmap(image, source, cropBounds);
            if (cached != null) {
                canvas.drawBitmap(
                        cached,
                        null,
                        new RectF(0, 0, source.getWidth(), source.getHeight()),
                        SNAPSHOT_PAINT);
            } else {
                source.draw(canvas);
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
