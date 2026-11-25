package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MediaClockContrast {
    private static final int SYNC_SAMPLE_MAX_DIM = 96;
    private static final Map<View, ClockCache> clocks = new WeakHashMap<>();
    private static final Map<TextView, Saved> colors = new WeakHashMap<>();
    private static final Map<ViewGroup, Snapshot> samples = new WeakHashMap<>();
    private static final Map<Bitmap, SampleEntry> coverSamples = new WeakHashMap<>();
    private static final Map<ViewGroup, PendingSample> pendingSamples = new WeakHashMap<>();
    private static final Map<ImageView, Icon> icons = new WeakHashMap<>();
    private static final Set<Class<?>> customClasses =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Async {
        static final Handler MAIN = new Handler(Looper.getMainLooper());
        static final ExecutorService POOL =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread t = new Thread(runnable, "DressroomArtSample");
                            t.setDaemon(true);
                            t.setPriority(Thread.NORM_PRIORITY - 1);
                            return t;
                        });
    }

    private static final int DARK_INK = 0xff17191c, LIGHT_INK = 0xfffff9f2;
    record Palette(int textColor, float conflict) {}

    private static final class SampleEntry {
        final int generation;
        volatile ArtworkSamples samples;
        volatile boolean failed;

        SampleEntry(int generation, ArtworkSamples samples) {
            this.generation = generation;
            this.samples = samples;
        }
    }

    private static final class PendingSample {
        final WeakReference<Bitmap> source;
        final int generation;
        final RectF destination = new RectF();
        WeakReference<View> overlay;

        PendingSample(Bitmap source, int generation, RectF destination, View overlay) {
            this.source = new WeakReference<>(source);
            this.generation = generation;
            this.destination.set(destination);
            this.overlay = new WeakReference<>(overlay);
        }
    }

    private static final class ClockCache {
        final ArrayList<TextView> texts = new ArrayList<>();
        final ArrayList<ImageView> weather = new ArrayList<>();
        String category;
        boolean treeDirty = true;
        boolean layoutDirty = true;
        boolean wasActive;

        static ClockCache create(View clock) {
            ClockCache cache = new ClockCache();
            clock.addOnLayoutChangeListener(
                    (v, l, t, r, b, ol, ot, or, ob) -> {
                        cache.treeDirty = true;
                        cache.layoutDirty = true;
                        invalidate(v.getRootView());
                    });
            clock.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            cache.category = null;
                            cache.treeDirty = true;
                            cache.layoutDirty = true;
                            invalidate(v.getRootView());
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            cache.category = null;
                            cache.treeDirty = true;
                            cache.layoutDirty = true;
                        }
                    });
            return cache;
        }
    }

    private static final class Snapshot {
        final WeakReference<Bitmap> source;
        final int generation;
        final ArtworkSamples pixels;
        Palette spec;
        int videoInk;
        long lastVideoSample;
        boolean returning;
        float returnStartProgress;
        WeakReference<View> overlay = new WeakReference<>(null);
        final RectF localBounds = new RectF();
        final RectF destination = new RectF();
        final Path cover = new Path();
        RenderNode art;
        int artRevision = -1;
        float artOpacity;
        boolean glassDrawn;
        final RectF appliedCover = new RectF();
        int appliedOriginX, appliedOriginY;
        int appliedInk;
        int appliedClockCount = -1;
        int appliedClockStateHash;
        boolean settled;
        final Matrix transform = new Matrix();
        final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glassSheen = new Paint();
        final Matrix sheenScale = new Matrix();
        final android.graphics.LinearGradient sheen = new android.graphics.LinearGradient(
                0f, 0f, 0f, 1f, new int[] {0x16eff8ff, 0x00000000, 0x0e000000},
                new float[] {0f, .52f, 1f}, Shader.TileMode.CLAMP);
        final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint iconMask = new Paint(Paint.ANTI_ALIAS_FLAG);
        int iconImageAlpha = -1;

        Snapshot(WeakReference<Bitmap> source, int generation, ArtworkSamples pixels) {
            this.source = source;
            this.generation = generation;
            this.pixels = pixels;
            maskPaint.setBlendMode(BlendMode.DST_IN);
            iconMask.setBlendMode(BlendMode.DST_IN);
            glassSheen.setShader(sheen);
        }

        Paint maskFor(View view) {
            if (!(view instanceof ImageView image)) return maskPaint;
            int alpha = image.getImageAlpha();
            if (iconImageAlpha != alpha) {
                iconImageAlpha = alpha;
                iconMask.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[] {
                        1, 0, 0, 0, 0,
                        0, 1, 0, 0, 0,
                        0, 0, 1, 0, 0,
                        0, 0, 0, 510f / Math.max(1, alpha), 0
                }));
            }
            return iconMask;
        }
    }

    private static final class Work {
        final RectF cover = new RectF();
        final RectF boundsF = new RectF();
        final Matrix matrix = new Matrix();
        final Rect bounds = new Rect();
        final Rect clockBounds = new Rect();
        final int[] origin = new int[2];
        final Set<TextView> visible = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Set<ImageView> weather = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final ArrayList<View> clocks = new ArrayList<>();
        final ArrayList<TextView> texts = new ArrayList<>();
        final ArrayList<ImageView> images = new ArrayList<>();
        boolean busy;

        void clear() {
            visible.clear();
            weather.clear();
            clocks.clear();
            texts.clear();
            images.clear();
            busy = false;
        }
    }

    private static final ThreadLocal<Work> workspace = ThreadLocal.withInitial(Work::new);
    private static DressroomModule hookModule;
    private static boolean installed, activeHooksInstalled, writing;
    private static boolean hooksWanted = true;
    private static final ArrayList<Runnable> activeUnhooks = new ArrayList<>();
    private static final Set<Class<?>> hookedCustomClasses =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Method> deoptimizedMethods =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static Field customOutlineColor, customPathMode;
    private static final ThreadLocal<TextView> drawing = new ThreadLocal<>();

    private static final class Saved {
        final WeakReference<ViewGroup> root;
        ColorStateList nativeColors;
        int color;
        int target;
        boolean closing;
        ValueAnimator animator;
        boolean initialized;
        float emphasis;
        int returnColor;
        float returnEmphasis;
        boolean progressReturn;
        float conflict;
        int shadowColor = 0x99000000;
        final boolean custom;
        Bitmap bitmap;
        String text = "";
        int generation;
        final RectF cover = new RectF();
        final Rect bounds = new Rect();

        Saved(ViewGroup root, TextView view) {
            this.root = new WeakReference<>(root);
            custom = customClasses.stream().anyMatch(type -> type.isInstance(view));
            nativeColors = view.getTextColors();
            color = target = nativeDisplayColor(view, custom, nativeColors);
        }
    }

    private static int nativeDisplayColor(
            TextView view, boolean custom, ColorStateList fallbackColors) {
        if (custom && customOutlineColor != null && customPathMode != null) {
            try {
                if (customPathMode.getBoolean(view)) {
                    return customOutlineColor.getInt(view);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        ColorStateList list = fallbackColors != null ? fallbackColors : view.getTextColors();
        return list != null ? list.getDefaultColor() : view.getCurrentTextColor();
    }

    private static boolean glyphBounds(View view, Work work) {
        if (view.getGlobalVisibleRect(work.bounds) && !work.bounds.isEmpty()) return true;
        if (!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        work.matrix.reset();
        view.transformMatrixToGlobal(work.matrix);
        work.boundsF.set(0f, 0f, view.getWidth(), view.getHeight());
        work.matrix.mapRect(work.boundsF);
        work.bounds.set(
                (int) Math.floor(work.boundsF.left),
                (int) Math.floor(work.boundsF.top),
                (int) Math.ceil(work.boundsF.right),
                (int) Math.ceil(work.boundsF.bottom));
        return !work.bounds.isEmpty();
    }

    private static final class Icon {
        final WeakReference<ViewGroup> root;
        ColorFilter nativeFilter;
        ColorFilter filter;
        float emphasis;
        int returnColor;
        float returnEmphasis;
        boolean progressReturn;
        int color, target;
        float nativeAlpha;
        boolean closing;
        ValueAnimator animator;

        Icon(ViewGroup root, ImageView view) {
            this.root = new WeakReference<>(root);
            nativeFilter = view.getColorFilter();
            nativeAlpha = view.getAlpha();
        }
    }

    static boolean hasIcons() {
        return !icons.isEmpty();
    }

    static boolean tracksWeatherAlpha(View view) {
        return !icons.isEmpty() && view instanceof ImageView && icons.containsKey(view);
    }

    static float weatherAlpha(View view, float alpha) {
        if (writing || ViewAlphaHooks.isWriting() || icons.isEmpty()
                || !(view instanceof ImageView image)) return alpha;
        Icon saved = icons.get(image);
        if (saved == null) return alpha;
        saved.nativeAlpha = alpha;
        return iconAlpha(saved);
    }

    private static float iconAlpha(Icon saved) {
        return saved.nativeAlpha + (1f - saved.nativeAlpha) * saved.emphasis;
    }

    static void track(View clock) {
        if (clock == null) return;
        clocks.computeIfAbsent(clock, ClockCache::create);
    }

    static void invalidate(View root) {
        if (root instanceof ViewGroup group) {
            Snapshot snapshot = samples.get(group);
            if (snapshot != null) snapshot.settled = false;
        }
    }

    static boolean isActive(ViewGroup root) {
        if (root == null) return false;
        if (samples.containsKey(root) || pendingSamples.containsKey(root)) return true;
        for (Saved saved : colors.values()) {
            if (saved.root.get() == root) return true;
        }
        for (Icon saved : icons.values()) {
            if (saved.root.get() == root) return true;
        }
        return false;
    }

    static boolean overridesDraw(TextView view) {
        Saved value = colors.get(view);
        return value != null && value.initialized;
    }


    static Integer readDrawingColor(TextView view) {
        if (colors.isEmpty() || drawing.get() != view) return null;
        Saved saved = colors.get(view);
        return saved != null && saved.custom && saved.initialized
                ? saved.color | 0xff000000
                : null;
    }

    static synchronized void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        hookModule = module;
        try {
            registerCustomGlyphClass(module, loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            DressroomModule.note("Optional fullscreen artwork custom glyph binding", unavailable);
        }
        if (hooksWanted) ensureActiveHooks();
        installed = true;
    }

    static synchronized void setHooksEnabled(boolean enabled) {
        if (hooksWanted == enabled) return;
        hooksWanted = enabled;
        if (enabled) ensureActiveHooks();
        else releaseHooksIfIdle();
    }

    private static void deoptimizeOnce(DressroomModule module, Method method) {
        if (deoptimizedMethods.add(method)) module.deoptimize(method);
    }

    private static synchronized void ensureActiveHooks() {
        if (activeHooksInstalled || hookModule == null) return;
        DressroomModule module = hookModule;
        try (HookTransaction hooks = new HookTransaction()) {
            ArrayList<Runnable> unhooks = new ArrayList<>();
            Method textDraw = TextView.class.getDeclaredMethod("onDraw", Canvas.class);
            Runnable textDrawUnhook =
                    module.hook(textDraw)
                                    .setId("dressroom-advanced.media-clock-soft-shadow")
                                    .intercept(
                                            chain -> {
                                                if (colors.isEmpty()) return chain.proceed();
                                                TextView view = (TextView) chain.getThisObject();
                                                Saved saved = colors.get(view);
                                                if (saved == null || !saved.initialized || saved.custom)
                                                    return chain.proceed();
                                                android.text.TextPaint paint = view.getPaint();
                                                float radius = paint.getShadowLayerRadius();
                                                float dx = paint.getShadowLayerDx(),
                                                        dy = paint.getShadowLayerDy();
                                                int color = paint.getShadowLayerColor();
                                                Shader shader = paint.getShader();
                                                int textColor = paint.getColor();
                                                try {
                                                    paint.setShader(null);
                                                    Canvas canvas = (Canvas) chain.getArg(0);
                                                    ViewGroup root = saved.root.get();
                                                    paint.setColor(saved.color | 0xff000000);
                                                    if (hasArtwork(root, canvas))
                                                        paint.clearShadowLayer();
                                                    else shadow(view, saved);
                                                    return drawArtworkGlyph(
                                                            canvas,
                                                            view,
                                                            root,
                                                            saved.emphasis,
                                                            saved.color,
                                                            target ->
                                                                    chain.proceed(
                                                                            new Object[] {target}));
                                                } finally {
                                                    paint.setShader(shader);
                                                    paint.setColor(textColor);
                                                    paint.setShadowLayer(radius, dx, dy, color);
                                                }
                                            })
                            ::unhook;
            hooks.add(textDrawUnhook);
            unhooks.add(textDrawUnhook);
            deoptimizeOnce(module, textDraw);

            Runnable colorLease = TextColorHooks.acquire(module);
            hooks.add(colorLease);
            unhooks.add(colorLease);

            for (Class<?> type : new Class<?>[] {int.class, ColorStateList.class}) {
                Method setter = TextView.class.getDeclaredMethod("setTextColor", type);
                Runnable setterUnhook =
                        module.hook(setter)
                                        .setId(
                                                "dressroom-advanced.media-clock-contrast-"
                                                        + type.getSimpleName())
                                        .intercept(
                                                chain -> {
                                                    if (writing || colors.isEmpty())
                                                        return chain.proceed();
                                                    Saved saved =
                                                            colors.get(
                                                                    (TextView)
                                                                            chain.getThisObject());
                                                    if (saved == null || saved.custom)
                                                        return chain.proceed();
                                                    Object value = chain.getArg(0);
                                                    if (value instanceof Integer color)
                                                        saved.nativeColors =
                                                                ColorStateList.valueOf(color);
                                                    else if (value instanceof ColorStateList list)
                                                        saved.nativeColors = list;
                                                    else return chain.proceed();
                                                    Object replacement =
                                                            type == int.class
                                                                    ? saved.color
                                                                    : ColorStateList.valueOf(
                                                                            saved.color);
                                                    return chain.proceed(
                                                            new Object[] {replacement});
                                                })
                                ::unhook;
                hooks.add(setterUnhook);
                unhooks.add(setterUnhook);
                deoptimizeOnce(module, setter);
            }
            Method filter = ImageView.class.getDeclaredMethod("setColorFilter", ColorFilter.class);
            Runnable filterUnhook =
                    module.hook(filter)
                                    .setId("dressroom-advanced.media-weather-palette")
                                    .intercept(
                                            chain -> {
                                                if (writing || icons.isEmpty())
                                                    return chain.proceed();
                                                Icon saved =
                                                        icons.get((ImageView) chain.getThisObject());
                                                if (saved == null) return chain.proceed();
                                                saved.nativeFilter = (ColorFilter) chain.getArg(0);
                                                return chain.proceed(new Object[] {saved.filter});
                                            })
                            ::unhook;
            hooks.add(filterUnhook);
            unhooks.add(filterUnhook);
            deoptimizeOnce(module, filter);

            Method iconDraw = ImageView.class.getDeclaredMethod("onDraw", Canvas.class);
            Runnable iconDrawUnhook =
                    module.hook(iconDraw)
                                    .setId("dressroom-advanced.media-weather-artwork-blur")
                                    .intercept(
                                            chain -> {
                                                if (icons.isEmpty()) return chain.proceed();
                                                ImageView view = (ImageView) chain.getThisObject();
                                                Icon saved = icons.get(view);
                                                if (saved == null) return chain.proceed();
                                                return drawArtworkGlyph(
                                                        (Canvas) chain.getArg(0),
                                                        view,
                                                        saved.root.get(),
                                                        saved.emphasis,
                                                        saved.color,
                                                        target ->
                                                                chain.proceed(
                                                                        new Object[] {target}));
                                            })
                            ::unhook;
            hooks.add(iconDrawUnhook);
            unhooks.add(iconDrawUnhook);
            deoptimizeOnce(module, iconDraw);

            hookedCustomClasses.clear();
            for (Class<?> custom : new ArrayList<>(customClasses)) {
                Runnable customUnhook = hookCustomGlyphDraw(module, custom);
                hooks.add(customUnhook);
                unhooks.add(customUnhook);
                hookedCustomClasses.add(custom);
            }
            hooks.commit();
            activeUnhooks.clear();
            activeUnhooks.addAll(unhooks);
            activeHooksInstalled = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Media clock contrast hooks unavailable", error);
        }
    }

    private static synchronized void releaseHooksIfIdle() {
        MediaBlurHost.syncHook();
        if (!activeHooksInstalled || hooksWanted) return;
        if (!colors.isEmpty()
                || !icons.isEmpty()
                || !samples.isEmpty()
                || !pendingSamples.isEmpty()) return;
        Throwable failure = null;
        for (int i = activeUnhooks.size() - 1; i >= 0; i--) {
            try {
                activeUnhooks.get(i).run();
            } catch (RuntimeException | LinkageError error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        activeUnhooks.clear();
        hookedCustomClasses.clear();
        activeHooksInstalled = false;
        if (failure != null && hookModule != null)
            hookModule.report("Media clock contrast hook cleanup failed", failure);
    }

    private static void registerCustomGlyphClass(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> custom =
                Class.forName(
                        "com.samsung.android.uniform.widget.ClockNonePaddingTextView",
                        false,
                        loader);
        if (!customClasses.contains(custom)) {
            final Field outlineColor = resolveOutlineField(custom.getSuperclass());
            final Field pathMode = resolvePathModeField(custom.getSuperclass());
            if (outlineColor != null) customOutlineColor = outlineColor;
            if (pathMode != null) customPathMode = pathMode;
            customClasses.add(custom);
            if (activeHooksInstalled && module != null && !hookedCustomClasses.contains(custom)) {
                Runnable unhook = hookCustomGlyphDraw(module, custom);
                activeUnhooks.add(unhook);
                hookedCustomClasses.add(custom);
            }
        }
    }

    private static Runnable hookCustomGlyphDraw(DressroomModule module, Class<?> custom)
            throws ReflectiveOperationException {
        final Field outlineColor =
                customOutlineColor != null
                        ? customOutlineColor
                        : resolveOutlineField(custom.getSuperclass());
        Method draw = custom.getDeclaredMethod("onDraw", Canvas.class);
        Runnable unhook =
                module.hook(draw)
                                .setId("dressroom-advanced.media-clock-palette-draw")
                                .intercept(
                                        chain -> {
                                            if (colors.isEmpty()) return chain.proceed();
                                            TextView view = (TextView) chain.getThisObject();
                                            Saved saved = colors.get(view);
                                            if (saved == null || !saved.initialized)
                                                return chain.proceed();
                                            android.text.TextPaint paint = view.getPaint();
                                            Shader shader = paint.getShader();
                                            int color = paint.getColor();
                                            int nativeOutline =
                                                    outlineColor == null
                                                            ? 0
                                                            : outlineColor.getInt(view);
                                            float radius = paint.getShadowLayerRadius();
                                            float dx = paint.getShadowLayerDx(),
                                                    dy = paint.getShadowLayerDy();
                                            int shadow = paint.getShadowLayerColor();
                                            TextView previous = drawing.get();
                                            try {
                                                drawing.set(view);
                                                paint.setShader(null);
                                                Canvas canvas = (Canvas) chain.getArg(0);
                                                ViewGroup root = saved.root.get();
                                                boolean glass = hasArtwork(root, canvas);
                                                int drawColor =
                                                        glass
                                                                ? saved.color | 0xff000000
                                                                : saved.color;
                                                paint.setColor(drawColor);
                                                if (outlineColor != null)
                                                    outlineColor.setInt(view, drawColor);
                                                if (glass) paint.clearShadowLayer();
                                                else shadow(view, saved);
                                                return drawArtworkGlyph(
                                                        canvas,
                                                        view,
                                                        root,
                                                        saved.emphasis,
                                                        saved.color,
                                                        target ->
                                                                chain.proceed(
                                                                        new Object[] {target}));
                                            } finally {
                                                paint.setShader(shader);
                                                paint.setColor(color);
                                                if (outlineColor != null)
                                                    outlineColor.setInt(view, nativeOutline);
                                                paint.setShadowLayer(radius, dx, dy, shadow);
                                                if (previous == null) drawing.remove();
                                                else drawing.set(previous);
                                            }
                                        })
                        ::unhook;
        deoptimizeOnce(module, draw);
        return unhook;
    }

    static Palette computePalette(ArtworkSamples.Stats stats) {
        boolean darkText = stats.mean() > .20;
        int textColor = darkText ? DARK_INK : LIGHT_INK;
        float conflict;
        if (darkText) {
            double darkSpot = Math.max(1.0 - stats.min(), (1.0 - stats.mean()) * .85);
            conflict = clamp((float) ((darkSpot - .15) / .65));
        } else {
            double hotSpot = Math.max(stats.max(), stats.mean() * 1.5);
            conflict = clamp((float) ((hotSpot - .03) / .55));
        }
        return new Palette(textColor, conflict);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }

    private static final float GLASS_ART_BLEND = 0.78f;

    /** Scales the frosted artwork fill inside clock glyphs so tinted contrast ink remains visible. */
    static int glassBlendAlpha(float mix, float artOpacity) {
        return Math.round(255f * clamp(mix) * clamp(artOpacity) * GLASS_ART_BLEND);
    }

    static boolean wantsGlass(ViewGroup root) {
        if (root == null || MediaClockVisibility.hidden(root)) return false;
        Snapshot snapshot = samples.get(root);
        return snapshot != null && snapshot.spec != null && !snapshot.localBounds.isEmpty();
    }

    static boolean hasGlass(ViewGroup root) {
        if (!wantsGlass(root)) return false;
        Snapshot snapshot = samples.get(root);
        return artworkReady(snapshot) && snapshot.glassDrawn;
    }

    /** Crossfade the native host with actual player ink, including interrupted returns. */
    static float glassWeight(ViewGroup root) {
        if (!hasGlass(root)) return 0f;
        float weight = 0f;
        boolean found = false;
        for (Saved saved : colors.values()) {
            if (saved.root.get() != root || !saved.initialized) continue;
            found = true;
            weight = Math.max(weight, saved.emphasis);
        }
        for (Icon saved : icons.values()) {
            if (saved.root.get() != root) continue;
            found = true;
            weight = Math.max(weight, saved.emphasis);
        }
        return found ? clamp(weight) : 1f;
    }

    private static boolean artworkReady(Snapshot snapshot) {
        return snapshot != null && snapshot.art != null && snapshot.art.hasDisplayList()
                && snapshot.artOpacity > .005f && !snapshot.destination.isEmpty()
                && !snapshot.cover.isEmpty();
    }

    /** Copies the cached clock/weather bounds in {@code root} coordinates, returning whether available. */
    static boolean clockBounds(ViewGroup root, RectF out) {
        if (out == null) return false;
        out.setEmpty();
        if (root == null || MediaClockVisibility.hidden(root)) return false;
        Snapshot snapshot = samples.get(root);
        if (snapshot == null || snapshot.localBounds.isEmpty()) return false;
        out.set(snapshot.localBounds);
        return true;
    }

    static boolean shouldSampleSync(Bitmap image) {
        return image != null
                && image.getConfig() != Bitmap.Config.HARDWARE
                && image.getWidth() <= SYNC_SAMPLE_MAX_DIM
                && image.getHeight() <= SYNC_SAMPLE_MAX_DIM;
    }

    static void prefetch(Bitmap image) {
        if (!installed || image == null || image.isRecycled()) return;
        obtainSamples(image, image.getGenerationId(), null, null, null);
    }

    static ArtworkSamples requestSamples(Bitmap image) {
        if (image == null || image.isRecycled()) return null;
        return obtainSamples(image, image.getGenerationId(), null, null, null);
    }

    private static ArtworkSamples obtainSamples(
            Bitmap image, int generation, ViewGroup root, RectF destination, View overlay) {
        SampleEntry existing;
        synchronized (coverSamples) {
            existing = coverSamples.get(image);
            if (existing != null && existing.generation == generation) {
                if (existing.samples != null) {
                    if (root != null) pendingSamples.remove(root);
                    return existing.samples;
                }
                if (existing.failed) {
                    if (root != null) pendingSamples.remove(root);
                    return null;
                }
                recordPending(root, image, generation, destination, overlay);
                return null;
            }
        }
        if (shouldSampleSync(image)) {
            ArtworkSamples computed = ArtworkSamples.read(image);
            synchronized (coverSamples) {
                coverSamples.put(image, new SampleEntry(generation, computed));
            }
            if (root != null) pendingSamples.remove(root);
            return computed;
        }
        SampleEntry pending = new SampleEntry(generation, null);
        synchronized (coverSamples) {
            SampleEntry raced = coverSamples.get(image);
            if (raced != null && raced.generation == generation) {
                if (raced.samples != null) {
                    if (root != null) pendingSamples.remove(root);
                    return raced.samples;
                }
                if (raced.failed) return null;
                recordPending(root, image, generation, destination, overlay);
                return null;
            }
            coverSamples.put(image, pending);
        }
        recordPending(root, image, generation, destination, overlay);
        final WeakReference<Bitmap> bitmapRef = new WeakReference<>(image);
        Async.POOL.execute(
                () -> {
                    ArtworkSamples computed = null;
                    Bitmap target = bitmapRef.get();
                    if (target != null && !target.isRecycled()) {
                        try {
                            computed = ArtworkSamples.read(target);
                        } catch (RuntimeException error) {
                            DressroomModule.note("Async artwork palette sample", error);
                        }
                    }
                    final ArtworkSamples result = computed;
                    synchronized (coverSamples) {
                        if (pending.generation == generation) {
                            if (result != null) pending.samples = result;
                            else pending.failed = true;
                        }
                    }
                    if (result != null) {
                        Async.MAIN.post(() -> onSamplesReady(bitmapRef.get(), generation, result));
                    } else {
                        Async.MAIN.post(() -> clearPendingFor(bitmapRef.get(), generation));
                    }
                });
        return null;
    }

    private static void recordPending(
            ViewGroup root, Bitmap image, int generation, RectF destination, View overlay) {
        if (root == null || destination == null) return;
        PendingSample existing = pendingSamples.get(root);
        if (existing != null
                && existing.source.get() == image
                && existing.generation == generation) {
            existing.destination.set(destination);
            if (existing.overlay.get() != overlay) {
                existing.overlay = new WeakReference<>(overlay);
            }
            return;
        }
        pendingSamples.put(root, new PendingSample(image, generation, destination, overlay));
    }

    private static void clearPendingFor(Bitmap image, int generation) {
        MediaStatusBarContrast.clearPendingFor(image, generation);
        if (pendingSamples.isEmpty()) return;
        for (var entry : new ArrayList<>(pendingSamples.entrySet())) {
            ViewGroup root = entry.getKey();
            PendingSample pending = entry.getValue();
            if (root == null
                    || !root.isAttachedToWindow()
                    || (pending.source.get() == image && pending.generation == generation)) {
                pendingSamples.remove(root);
            }
        }
        releaseHooksIfIdle();
    }

    private static void onSamplesReady(Bitmap image, int generation, ArtworkSamples computed) {
        if (image == null || image.isRecycled()) return;
        MediaStatusBarContrast.onSamplesReady(image, generation, computed);
        if (pendingSamples.isEmpty()) return;
        for (var entry : new ArrayList<>(pendingSamples.entrySet())) {
            ViewGroup root = entry.getKey();
            PendingSample pending = entry.getValue();
            if (root == null || !root.isAttachedToWindow()) {
                pendingSamples.remove(root);
                continue;
            }
            if (pending.source.get() == image
                    && pending.generation == generation
                    && image.getGenerationId() == generation) {
                pendingSamples.remove(root);
                apply(root, image, pending.destination, pending.overlay.get());
            }
        }
    }

    /**
     * Share the rendered artwork with the actual glyph draw. A sibling below the clock
     * cannot replace the text paint, and it misses the native icon's alpha mask.
     */
    static void artwork(
            ViewGroup root,
            RenderNode blurredArt,
            int artRevision,
            RectF destination,
            Path cover,
            float opacity) {
        if (root == null || MediaClockVisibility.hidden(root)) return;
        Snapshot snapshot = samples.get(root);
        if (snapshot == null) return;
        if (blurredArt == null || !blurredArt.hasDisplayList()) {
            snapshot.art = null;
            snapshot.glassDrawn = false;
            MediaBlurHost.refreshArtwork(root);
            return;
        }
        if (!artworkReady(snapshot) || snapshot.art != blurredArt) snapshot.glassDrawn = false;
        boolean changed =
                snapshot.art != blurredArt
                        || snapshot.artRevision != artRevision
                        || !snapshot.destination.equals(destination)
                        || Math.abs(snapshot.artOpacity - opacity) > .004f;
        snapshot.art = blurredArt;
        snapshot.artRevision = artRevision;
        snapshot.destination.set(destination);
        snapshot.cover.rewind();
        if (cover == null) snapshot.cover.addRect(destination, Path.Direction.CW);
        else snapshot.cover.set(cover);
        snapshot.artOpacity = opacity;
        MediaBlurHost.refreshArtwork(root);
        if (changed) {
            for (var entry : colors.entrySet())
                if (entry.getKey() != null && entry.getValue().root.get() == root)
                    entry.getKey().invalidate();
            for (var entry : icons.entrySet())
                if (entry.getKey() != null && entry.getValue().root.get() == root)
                    entry.getKey().invalidate();
        }
    }

    private static boolean hasArtwork(ViewGroup root, Canvas canvas) {
        if (root == null || !canvas.isHardwareAccelerated()) return false;
        Snapshot snapshot = samples.get(root);
        return artworkReady(snapshot);
    }

    @FunctionalInterface
    private interface DrawGlyph {
        Object draw(Canvas canvas) throws Throwable;
    }

    private static Object drawArtworkGlyph(Canvas canvas, View view, ViewGroup root, float mix,
            int tint, DrawGlyph glyph) throws Throwable {
        if (!hasArtwork(root, canvas) || view.getWidth() <= 0 || view.getHeight() <= 0)
            return glyph.draw(canvas);
        Snapshot snapshot = samples.get(root);
        snapshot.transform.reset();
        root.transformMatrixToGlobal(snapshot.transform);
        view.transformMatrixToLocal(snapshot.transform);
        int width = view.getWidth(), height = view.getHeight();
        int glassAlpha = glassBlendAlpha(mix, snapshot.artOpacity);
        int layer = canvas.saveLayer(0, 0, width, height, null);
        try {
            // Cached clock paths supply an opaque mask, so their return alpha
            // belongs in the fill. TextView/native icon masks already carry ink
            // alpha; applying it again here would fade those glyphs twice.
            Saved savedText = view instanceof TextView text ? colors.get(text) : null;
            boolean maskHasInkAlpha = savedText != null && !savedText.custom
                    || view instanceof ImageView image && icons.containsKey(image);
            snapshot.ink.setColor(maskHasInkAlpha ? tint | 0xff000000 : tint);
            canvas.drawRect(0, 0, width, height, snapshot.ink);
            int transform = canvas.save();
            canvas.concat(snapshot.transform);
            boolean overArtwork = glassAlpha > 0 && canvas.clipPath(snapshot.cover);
            if (overArtwork) {
                int artW = snapshot.art.getWidth(), artH = snapshot.art.getHeight();
                if (artW > 0 && artH > 0 && !snapshot.destination.isEmpty()) {
                    int blend = canvas.saveLayerAlpha(snapshot.destination, glassAlpha);
                    canvas.translate(snapshot.destination.left, snapshot.destination.top);
                    canvas.scale(
                            snapshot.destination.width() / artW,
                            snapshot.destination.height() / artH);
                    canvas.drawRenderNode(snapshot.art);
                    canvas.restoreToCount(blend);
                }
            }
            canvas.restoreToCount(transform);
            if (overArtwork && view instanceof TextView) {
                snapshot.sheenScale.setScale(1f, height);
                snapshot.sheen.setLocalMatrix(snapshot.sheenScale);
                snapshot.glassSheen.setAlpha(glassAlpha);
                canvas.drawRect(0, 0, width, height, snapshot.glassSheen);
            }
            Paint maskPaint = snapshot.maskFor(view);
            int mask = canvas.saveLayer(0, 0, width, height, maskPaint);
            try {
                Object result = glyph.draw(canvas);
                if (overArtwork && glassAlpha > 0 && !snapshot.glassDrawn) {
                    snapshot.glassDrawn = true;
                    MediaBlurHost.refreshArtwork(root);
                }
                return result;
            } finally {
                canvas.restoreToCount(mask);
            }
        } finally {
            canvas.restoreToCount(layer);
        }
    }

    static void clearImmediate(ViewGroup root) {
        if (root == null) return;
        pendingSamples.remove(root);
        Snapshot snapshot = samples.remove(root);
        if (snapshot != null) snapshot.settled = false;
        MediaBlurHost.refreshArtwork(root);
        if (!colors.isEmpty()) {
            for (TextView view : new ArrayList<>(colors.keySet())) {
                Saved saved = colors.get(view);
                if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                    if (saved.animator != null) {
                        ValueAnimator animator = saved.animator;
                        saved.animator = null;
                        animator.cancel();
                    }
                    colors.remove(view);
                    if (!saved.custom) write(view, saved.nativeColors);
                    view.invalidate();
                }
            }
        }
        if (!icons.isEmpty()) {
            for (ImageView view : new ArrayList<>(icons.keySet())) {
                Icon saved = icons.get(view);
                if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                    if (saved.animator != null) {
                        ValueAnimator animator = saved.animator;
                        saved.animator = null;
                        animator.cancel();
                    }
                    restoreIcon(view, saved);
                    view.invalidate();
                }
            }
        }
        releaseHooksIfIdle();
    }

    static void clear(ViewGroup root) {
        pendingSamples.remove(root);
        Snapshot snapshot = samples.get(root);
        if (snapshot != null) {
            snapshot.returning = true;
            snapshot.settled = false;
        }
        pruneSnapshot(root);
        if (colors.isEmpty() && icons.isEmpty()) return;
        for (TextView view : new ArrayList<>(colors.keySet())) {
            Saved saved = colors.get(view);
            if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                if (!saved.closing) {
                    if (saved.custom) saved.nativeColors = view.getTextColors();
                    fade(
                            view,
                            saved,
                            nativeDisplayColor(view, saved.custom, saved.nativeColors),
                            saved.conflict,
                            true);
                }
            }
        }
        for (ImageView view : new ArrayList<>(icons.keySet())) {
            Icon saved = icons.get(view);
            if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                Integer nativeTint = nativeTint(saved.nativeFilter);
                if (nativeTint != null) {
                    if (!saved.closing) fadeIcon(view, saved, nativeTint, true);
                } else {
                    if (saved.animator != null) saved.animator.cancel();
                    restoreIcon(view, saved);
                }
            }
        }
    }

    private static final class ReturnColors {
        static final android.animation.ArgbEvaluator MIX = new android.animation.ArgbEvaluator();
    }

    /** Restore current native ink with cover motion, rather than keeping player tint until the end. */
    static void returnToNative(ViewGroup root, float progress) {
        pendingSamples.remove(root);
        Snapshot snapshot = samples.get(root);
        if (snapshot == null) return;
        float remaining = clamp(progress);
        if (remaining <= .005f) {
            clearImmediate(root);
            return;
        }
        if (!snapshot.returning || snapshot.returnStartProgress <= 0f)
            snapshot.returnStartProgress = remaining;
        snapshot.returning = true;
        snapshot.settled = false;
        float fraction = 1f - clamp(remaining / snapshot.returnStartProgress);
        for (var entry : colors.entrySet()) {
            TextView view = entry.getKey();
            Saved saved = entry.getValue();
            if (saved.root.get() != root || !saved.initialized) continue;
            if (!saved.progressReturn) {
                if (saved.animator != null) {
                    ValueAnimator animation = saved.animator;
                    saved.animator = null;
                    animation.cancel();
                }
                saved.returnColor = saved.color;
                saved.returnEmphasis = saved.emphasis;
                saved.closing = true;
                saved.progressReturn = true;
            }
            saved.color = (Integer) ReturnColors.MIX.evaluate(fraction, saved.returnColor,
                    nativeDisplayColor(view, saved.custom, saved.nativeColors));
            saved.emphasis = saved.returnEmphasis * (1f - fraction);
            if (!saved.custom) write(view, ColorStateList.valueOf(saved.color));
            view.invalidate();
        }
        for (var entry : icons.entrySet()) {
            ImageView view = entry.getKey();
            Icon saved = entry.getValue();
            if (saved.root.get() != root) continue;
            if (!saved.progressReturn) {
                if (saved.animator != null) {
                    ValueAnimator animation = saved.animator;
                    saved.animator = null;
                    animation.cancel();
                }
                saved.returnColor = saved.color;
                saved.returnEmphasis = saved.emphasis;
                saved.closing = true;
                saved.progressReturn = true;
            }
            Integer nativeColor = nativeTint(saved.nativeFilter);
            saved.color = (Integer) ReturnColors.MIX.evaluate(fraction, saved.returnColor,
                    nativeColor == null ? saved.returnColor : nativeColor);
            saved.emphasis = saved.returnEmphasis * (1f - fraction);
            saved.filter = new PorterDuffColorFilter(saved.color, PorterDuff.Mode.SRC_IN);
            writeIcon(view, saved.filter);
            writeIconAlpha(view, iconAlpha(saved));
            view.invalidate();
        }
        MediaBlurHost.refreshArtwork(root);
    }

    private static void pruneSnapshot(ViewGroup root) {
        Snapshot snapshot = samples.get(root);
        if (snapshot == null || !snapshot.returning) return;
        for (Saved saved : colors.values())
            if (saved.root.get() == root && saved.initialized) return;
        for (Icon saved : icons.values())
            if (saved.root.get() == root) return;
        samples.remove(root);
        MediaBlurHost.refreshArtwork(root);
        releaseHooksIfIdle();
    }

    static int videoInk(double luma, int prior) {
        if (prior == DARK_INK) return luma < .15 ? LIGHT_INK : DARK_INK;
        return luma > .26 ? DARK_INK : LIGHT_INK;
    }

    static void sampleVideo(ViewGroup root, Bitmap frame, RectF destination) {
        Snapshot snapshot = samples.get(root);
        long now = android.os.SystemClock.uptimeMillis();
        if (snapshot == null || snapshot.returning || snapshot.spec == null || destination.isEmpty()
                || now - snapshot.lastVideoSample < 250) return;
        snapshot.lastVideoSample = now;
        RectF clock = snapshot.localBounds;
        double luma = 0;
        for (int y = 0; y < 3; y++) {
            int py = Math.max(0, Math.min(frame.getHeight() - 1, (int) ((clock.top
                    + clock.height() * (y + .5f) / 3 - destination.top)
                    / destination.height() * frame.getHeight())));
            for (int x = 0; x < 5; x++) {
                int px = Math.max(0, Math.min(frame.getWidth() - 1, (int) ((clock.left
                        + clock.width() * (x + .5f) / 5 - destination.left)
                        / destination.width() * frame.getWidth())));
                luma += ArtworkSamples.luminance(frame.getPixel(px, py));
            }
        }
        int ink = videoInk(luma / 15, snapshot.videoInk == 0
                ? snapshot.spec.textColor() : snapshot.videoInk);
        if (ink != snapshot.videoInk) {
            snapshot.videoInk = ink;
            root.postInvalidateOnAnimation();
        }
    }

    static void apply(ViewGroup root, Bitmap image, RectF destination, View overlay) {
        if (MediaClockVisibility.hidden(root)) {
            clear(root);
            return;
        }
        if (!installed || image == null || image.isRecycled() || destination.isEmpty()) return;
        Work work = workspace.get();
        if (work.busy) work = new Work();
        work.busy = true;
        try {
            apply(root, image, destination, overlay, work);
        } finally {
            work.clear();
        }
    }

    private static boolean isClockActive(View clock, ClockCache cache, ViewGroup root) {
        if (clock == null
                || !clock.isShown()
                || clock.getAlpha() <= .005f
                || ClockBlurMotion.outgoing(clock)
                || clock.getWindowToken() != root.getWindowToken()) {
            return false;
        }
        if (clock.getDisplay() == null
                || (!ScreenIdentity.supported() && clock.getDisplay().getDisplayId() != 0)
                || root.getDisplay() == null
                || clock.getDisplay().getDisplayId() != root.getDisplay().getDisplayId()) {
            return false;
        }
        if (cache.category == null) {
            try {
                cache.category = String.valueOf(Reflect.call(clock, "getCategory"));
            } catch (Exception ignored) {
                return false;
            }
        }
        return "LOCK_SCREEN".equals(cache.category) || "AOD".equals(cache.category);
    }

    private static int clockStateHash(View clock, ClockCache cache) {
        int h = 17;
        h = 31 * h + Float.floatToIntBits(clock.getTranslationX());
        h = 31 * h + Float.floatToIntBits(clock.getTranslationY());
        h = 31 * h + Float.floatToIntBits(clock.getScaleX());
        h = 31 * h + Float.floatToIntBits(clock.getScaleY());
        for (int i = 0, n = cache.texts.size(); i < n; i++) {
            TextView tv = cache.texts.get(i);
            boolean shown = tv.isShown();
            CharSequence cs = tv.getText();
            int len = cs == null ? 0 : cs.length();
            h = 31 * h + (shown && len > 0 ? 1 : 0);
            if (shown && len > 0) {
                h = 31 * h + System.identityHashCode(tv);
                h = 31 * h + len;
                h = 31 * h + cs.charAt(0);
                h = 31 * h + cs.charAt(len - 1);
                if (len > 2) h = 31 * h + cs.charAt(len >> 1);
            }
        }
        for (int i = 0, n = cache.weather.size(); i < n; i++) {
            ImageView iv = cache.weather.get(i);
            boolean shown = iv.isShown();
            android.graphics.drawable.Drawable d = iv.getDrawable();
            h = 31 * h + (shown && d != null ? 1 : 0);
            if (shown && d != null) {
                h = 31 * h + System.identityHashCode(iv);
                h = 31 * h + System.identityHashCode(d);
            }
        }
        return h;
    }

    private static void apply(ViewGroup root, Bitmap image, RectF destination, View overlay, Work work) {
        int generation = image.getGenerationId();
        Snapshot snapshot = samples.get(root);
        if (snapshot == null
                || snapshot.source.get() != image
                || snapshot.generation != generation) {
            ArtworkSamples cached = obtainSamples(image, generation, root, destination, overlay);
            if (cached == null) return;
            Snapshot next = new Snapshot(new WeakReference<>(image), generation, cached);
            if (snapshot != null && !snapshot.returning) {
                next.overlay = snapshot.overlay;
                next.localBounds.set(snapshot.localBounds);
                next.destination.set(snapshot.destination);
                next.cover.set(snapshot.cover);
                next.art = snapshot.art;
                next.artRevision = snapshot.artRevision;
                next.artOpacity = snapshot.artOpacity;
                next.glassDrawn = snapshot.glassDrawn;
            }
            snapshot = next;
            samples.put(root, snapshot);
        }
        if (snapshot.overlay.get() != overlay) snapshot.overlay = new WeakReference<>(overlay);
        float scale =
                Math.max(
                        destination.width() / image.getWidth(),
                        destination.height() / image.getHeight());
        RectF cover = work.cover;
        cover.set(
                destination.centerX() - image.getWidth() * scale / 2,
                destination.centerY() - image.getHeight() * scale / 2,
                destination.centerX() + image.getWidth() * scale / 2,
                destination.centerY() + image.getHeight() * scale / 2);
        root.getLocationOnScreen(work.origin);
        cover.offset(work.origin[0], work.origin[1]);

        for (View clock : clocks.keySet()) work.clocks.add(clock);
        boolean anyClockDirty = false;
        int clockStateHash = 1;
        int clockCount = work.clocks.size();
        for (int i = 0; i < clockCount; i++) {
            View clock = work.clocks.get(i);
            ClockCache cache = clocks.get(clock);
            if (cache == null) continue;
            boolean active = isClockActive(clock, cache, root);
            if (active != cache.wasActive || cache.treeDirty || cache.layoutDirty) {
                anyClockDirty = true;
            }
            if (active) {
                if (cache.treeDirty) {
                    cache.texts.clear();
                    cache.weather.clear();
                    collectAll(clock, cache.texts, cache.weather);
                    cache.treeDirty = false;
                }
                clockStateHash = 31 * clockStateHash + System.identityHashCode(clock);
                clockStateHash = 31 * clockStateHash + clockStateHash(clock, cache);
            }
        }

        if (snapshot.settled
                && snapshot.spec != null
                && !snapshot.returning
                && !anyClockDirty
                && snapshot.appliedOriginX == work.origin[0]
                && snapshot.appliedOriginY == work.origin[1]
                && snapshot.appliedCover.equals(cover)
                && snapshot.appliedClockCount == clockCount
                && snapshot.appliedClockStateHash == clockStateHash) {
            int settledInk = snapshot.videoInk == 0 ? snapshot.spec.textColor() : snapshot.videoInk;
            if (snapshot.appliedInk == (settledInk | 0xff000000)) {
                return;
            }
        }
        snapshot.returning = false;
        snapshot.returnStartProgress = 0f;
        snapshot.settled = false;

        Set<TextView> visible = work.visible;
        Set<ImageView> weather = work.weather;
        for (int i = 0; i < clockCount; i++) {
            View clock = work.clocks.get(i);
            ClockCache cache = clocks.get(clock);
            if (cache == null) continue;
            boolean active = isClockActive(clock, cache, root);
            cache.wasActive = active;
            cache.layoutDirty = false;
            if (!active) continue;
            for (int j = 0, tn = cache.texts.size(); j < tn; j++) {
                TextView tv = cache.texts.get(j);
                if (tv.isShown() && tv.getText().length() > 0) visible.add(tv);
            }
            for (int j = 0, wn = cache.weather.size(); j < wn; j++) {
                ImageView iv = cache.weather.get(j);
                if (iv.isShown() && iv.getDrawable() != null) weather.add(iv);
            }
        }
        // Pick once for the whole clock, using the final fullscreen crop. Sampling
        // each moving digit independently made a single time alternate black/white.
        if (!visible.isEmpty()) {
            work.clockBounds.setEmpty();
            for (TextView view : visible)
                if (glyphBounds(view, work)) work.clockBounds.union(work.bounds);
            for (ImageView view : weather)
                if (view.getGlobalVisibleRect(work.bounds)) work.clockBounds.union(work.bounds);
            if (!work.clockBounds.isEmpty()) {
                snapshot.localBounds.set(
                        work.clockBounds.left - work.origin[0],
                        work.clockBounds.top - work.origin[1],
                        work.clockBounds.right - work.origin[0],
                        work.clockBounds.bottom - work.origin[1]);
                if (snapshot.spec == null) {
                    float finalScale =
                            Math.max(
                                    root.getWidth() / (float) image.getWidth(),
                                    root.getHeight() / (float) image.getHeight());
                    work.cover.set(
                            work.origin[0] + (root.getWidth() - image.getWidth() * finalScale) / 2,
                            work.origin[1] + (root.getHeight() - image.getHeight() * finalScale) / 2,
                            work.origin[0] + (root.getWidth() + image.getWidth() * finalScale) / 2,
                            work.origin[1] + (root.getHeight() + image.getHeight() * finalScale) / 2);
                    snapshot.spec =
                            computePalette(snapshot.pixels.analyze(work.cover, work.clockBounds));
                }
            }
        }
        if (snapshot.spec == null) return;
        if (!visible.isEmpty() || !weather.isEmpty()) ensureActiveHooks();
        int ink = snapshot.videoInk == 0 ? snapshot.spec.textColor() : snapshot.videoInk;
        int playerColor = ink | 0xff000000;
        float conflict = snapshot.spec.conflict();
        for (TextView view : visible) {
            if (!glyphBounds(view, work)) continue;
            Rect bounds = work.bounds;
            Saved saved = colors.get(view);
            if (saved == null) {
                saved = new Saved(root, view);
                colors.put(view, saved);
            }
            if (saved.initialized
                    && !saved.closing
                    && saved.bitmap == image
                    && saved.generation == generation
                    && saved.target == playerColor
                    && android.text.TextUtils.equals(saved.text, view.getText())
                    && saved.cover.equals(cover)
                    && saved.bounds.equals(bounds)) continue;
            saved.initialized = true;
            saved.bitmap = image;
            saved.text = view.getText().toString();
            saved.generation = generation;
            saved.cover.set(cover);
            saved.bounds.set(bounds);
            if (overlay != null) overlay.invalidate();
            fade(view, saved, playerColor, conflict, false);
        }
        for (TextView view : colors.keySet()) work.texts.add(view);
        for (TextView view : work.texts) {
            Saved saved = colors.get(view);
            if (saved != null && saved.root.get() == root && !visible.contains(view)) {
                if (saved.animator != null) {
                    ValueAnimator animator = saved.animator;
                    saved.animator = null;
                    animator.cancel();
                }
                colors.remove(view);
                if (!saved.custom) write(view, saved.nativeColors);
                else view.invalidate();
            }
        }
        for (ImageView view : weather) {
            Rect bounds = work.bounds;
            if (!view.getGlobalVisibleRect(bounds) || bounds.isEmpty()) continue;
            Icon saved = icons.get(view);
            if (saved == null) {
                saved = new Icon(root, view);
                icons.put(view, saved);
                MediaBlurHost.syncHook();
            }
            int color = playerColor;
            if (saved.filter == null || color != saved.target || saved.closing)
                fadeIcon(view, saved, color, false);
            if (view.getColorFilter() != saved.filter) writeIcon(view, saved.filter);
        }
        for (ImageView view : icons.keySet()) work.images.add(view);
        for (ImageView view : work.images) {
            Icon saved = icons.get(view);
            if (saved != null && saved.root.get() == root && !weather.contains(view)) {
                if (saved.animator != null) {
                    ValueAnimator animator = saved.animator;
                    saved.animator = null;
                    animator.cancel();
                }
                restoreIcon(view, saved);
            }
        }
        snapshot.appliedInk = playerColor;
        snapshot.appliedOriginX = work.origin[0];
        snapshot.appliedOriginY = work.origin[1];
        snapshot.appliedCover.set(cover);
        snapshot.appliedClockCount = clockCount;
        snapshot.appliedClockStateHash = clockStateHash;
        snapshot.settled = !visible.isEmpty();
    }

    private static void fade(
            TextView view, Saved saved, int target, float conflict, boolean closing) {
        if (saved.target == target
                && saved.closing == closing
                && (closing || saved.conflict == conflict)
                && (saved.animator != null
                        || (saved.color == target && saved.emphasis == (closing ? 0f : 1f))))
            return;
        if (saved.animator != null) {
            ValueAnimator previous = saved.animator;
            saved.animator = null;
            previous.cancel();
        }
        float startEmphasis = saved.emphasis;
        int startColor = saved.color;
        android.animation.ArgbEvaluator returnColor =
                closing ? new android.animation.ArgbEvaluator() : null;
        if (!closing) {
            saved.conflict = conflict;
            if ((target & 0x00ffffff) == (DARK_INK & 0x00ffffff)) {
                int maxAlpha = Math.round(0x55 + (0xcc - 0x55) * conflict);
                saved.shadowColor = (maxAlpha << 24) | 0xffffff;
            } else {
                int maxAlpha = Math.round(0x88 + (0xe0 - 0x88) * conflict);
                saved.shadowColor = maxAlpha << 24;
            }
        }
        saved.target = target;
        saved.closing = closing;
        saved.progressReturn = false;
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    // Native blur can update the glyph or temperature text alpha during
                    // the return. Follow that color before handing the view back.
                    saved.color = returnColor == null
                            ? (Integer) value.getAnimatedValue()
                            : (Integer) returnColor.evaluate(
                                    value.getAnimatedFraction(),
                                    startColor,
                                    nativeDisplayColor(view, saved.custom, saved.nativeColors));
                    saved.emphasis =
                            startEmphasis
                                    + ((closing ? 0f : 1f) - startEmphasis)
                                            * value.getAnimatedFraction();
                    if (!saved.custom) write(view, ColorStateList.valueOf(saved.color));
                    view.invalidate();
                    ViewGroup owner = saved.root.get();
                    if (owner != null) {
                        Snapshot snapshot = samples.get(owner);
                        View overlay = snapshot == null ? null : snapshot.overlay.get();
                        if (overlay != null) overlay.invalidate();
                    }
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && colors.get(view) == saved) {
                            colors.remove(view);
                            pruneSnapshot(saved.root.get());
                            if (!saved.custom) write(view, saved.nativeColors);
                            else view.invalidate();
                        }
                    }
                });
        animator.start();
    }

    private static void fadeIcon(ImageView view, Icon saved, int target, boolean closing) {
        if (saved.animator != null) {
            ValueAnimator previous = saved.animator;
            saved.animator = null;
            previous.cancel();
        }
        if (saved.filter == null) {
            Integer nativeTint = nativeTint(saved.nativeFilter);
            saved.color = nativeTint == null ? target : nativeTint;
        }
        saved.target = target;
        saved.closing = closing;
        saved.progressReturn = false;
        float start = saved.emphasis;
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    saved.emphasis = start + ((closing ? 0f : 1f) - start) * value.getAnimatedFraction();
                    saved.color = (Integer) value.getAnimatedValue();
                    saved.filter = new PorterDuffColorFilter(saved.color, PorterDuff.Mode.SRC_IN);
                    writeIcon(view, saved.filter);
                    writeIconAlpha(view, iconAlpha(saved));
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && icons.get(view) == saved) {
                            restoreIcon(view, saved);
                        }
                    }
                });
        animator.start();
    }

    private static void collectAll(
            View view, ArrayList<TextView> found, ArrayList<ImageView> weather) {
        if (view instanceof TextView text) found.add(text);
        if (view instanceof ImageView image && isWeatherIcon(image)) weather.add(image);
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                collectAll(group.getChildAt(i), found, weather);
    }

    private static boolean isWeatherIcon(ImageView image) {
        if ("common_weather_icon".equals(image.getTag())) return true;
        if (image.getId() == View.NO_ID) return false;
        try {
            return "common_weather_icon".equals(
                    image.getResources().getResourceEntryName(image.getId()));
        } catch (android.content.res.Resources.NotFoundException unavailable) {
            return false;
        }
    }

    private static void shadow(TextView view, Saved saved) {
        if (saved.emphasis <= 0 || !saved.closing) return;
        float density = view.getResources().getDisplayMetrics().density;
        int alpha = Math.round((saved.shadowColor >>> 24) * saved.emphasis);
        float radius = (2.5f + 5.5f * saved.conflict) * density;
        float dy = (.75f * (1f - .6f * saved.conflict)) * density;
        view.getPaint()
                .setShadowLayer(
                        radius,
                        0,
                        dy,
                        (saved.shadowColor & 0xffffff) | (alpha << 24));
    }

    private static void write(TextView view, ColorStateList value) {
        boolean old = writing;
        writing = true;
        try {
            if (view.getTextColors() != value) view.setTextColor(value);
        } finally {
            writing = old;
        }
    }

    private static void writeIcon(ImageView view, ColorFilter value) {
        boolean old = writing;
        writing = true;
        try {
            view.setColorFilter(value);
        } finally {
            writing = old;
        }
    }

    private static void writeIconAlpha(ImageView view, float alpha) {
        if (view.getAlpha() == alpha) return;
        boolean old = writing;
        writing = true;
        try {
            ViewAlphaHooks.write(view, alpha);
        } finally {
            writing = old;
        }
    }

    private static void restoreIcon(ImageView view, Icon saved) {
        icons.remove(view);
        writeIcon(view, saved.nativeFilter);
        writeIconAlpha(view, saved.nativeAlpha);
        pruneSnapshot(saved.root.get());
        releaseHooksIfIdle();
    }

    private static Integer nativeTint(ColorFilter filter) {
        if (filter == null) return null;
        try {
            return ((Number) Reflect.call(filter, "getColor")).intValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    static Field resolveOutlineField(Class<?> textClass) throws NoSuchFieldException {
        if (textClass == null) return null;
        try {
            Method draw = textClass.getDeclaredMethod("onDraw", Canvas.class);
            String desc = "L" + textClass.getName().replace('.', '/') + ";";
            DexSignatures.Body body = DexSignatures.cached(() -> DexSignatures.body(draw));
            DexSignatures.Member member = outlineMember(body, desc);
            if (member == null) return null;
            Field outline = member.field(textClass.getClassLoader());
            if (outline.getType() != int.class) throw new NoSuchFieldException("Outline color");
            outline.setAccessible(true);
            return outline;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (NoSuchFieldException error) {
            throw error;
        } catch (ReflectiveOperationException | IOException error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Outline color field lookup failed").initCause(error);
        } catch (Exception error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Outline color field lookup failed").initCause(error);
        }
    }

    static DexSignatures.Member outlineMember(DexSignatures.Body body, String ownerDescriptor)
            throws NoSuchFieldException {
        Set<String> names = new java.util.LinkedHashSet<>();
        DexSignatures.Member match = null;
        for (var read : body.reads()) {
            DexSignatures.Member member = read.field();
            if (ownerDescriptor.equals(member.owner())
                    && "I".equals(member.type())
                    && names.add(member.name())) {
                match = member;
            }
        }
        if (names.isEmpty()) {
            // Older providers draw text without this separate outline-color field.
            return null;
        }
        if (names.size() > 1) {
            throw new NoSuchFieldException(
                    "Multiple int fields read in " + ownerDescriptor + ".onDraw: " + names);
        }
        return match;
    }

    static Field resolvePathModeField(Class<?> textClass) throws NoSuchFieldException {
        if (textClass == null) return null;
        try {
            Method setColor = textClass.getDeclaredMethod("setTextColor", int.class);
            String desc = DexSignatures.descriptor(textClass);
            DexSignatures.Body body = DexSignatures.cached(() -> DexSignatures.body(setColor));
            Field mode = body.uniqueRead(desc, "Z", null).field(textClass.getClassLoader());
            mode.setAccessible(true);
            return mode;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (NoSuchFieldException error) {
            throw error;
        } catch (ReflectiveOperationException | IOException error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Blur path mode field lookup failed").initCause(error);
        } catch (Exception error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Blur path mode field lookup failed").initCause(error);
        }
    }
}
