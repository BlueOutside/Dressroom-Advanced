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
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
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

final class MediaClockContrast {
    private static final Set<View> clocks = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<TextView, Saved> colors = new WeakHashMap<>();
    private static final Map<ViewGroup, Snapshot> samples = new WeakHashMap<>();
    private static final Map<ImageView, Icon> icons = new WeakHashMap<>();
    private static final Set<Class<?>> customClasses =
            Collections.newSetFromMap(new WeakHashMap<>());

    record AuraSpec(
            int textColor, int scrimColor, float blurAlpha, float scrimAlpha, float conflict) {}

    private static final class Snapshot {
        final WeakReference<Bitmap> source;
        final int generation;
        final ArtworkSamples pixels;
        AuraSpec spec;
        final RectF localBounds = new RectF();
        final RectF auraBounds = new RectF();
        final Matrix shaderMatrix = new Matrix();
        final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RadialGradient maskShader =
                new RadialGradient(
                        0f,
                        0f,
                        1f,
                        new int[] {0xffffffff, 0xd8ffffff, 0x55ffffff, 0x00ffffff},
                        new float[] {0f, .52f, .78f, 1f},
                        Shader.TileMode.CLAMP);

        Snapshot(WeakReference<Bitmap> source, int generation, ArtworkSamples pixels) {
            this.source = source;
            this.generation = generation;
            this.pixels = pixels;
            maskPaint.setBlendMode(BlendMode.DST_IN);
            maskPaint.setShader(maskShader);
        }
    }

    private static final class Work {
        final RectF cover = new RectF();
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
    private static boolean installed, writing;
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
        float conflict;
        int shadowColor = 0x99000000;
        final boolean custom;
        Bitmap bitmap;
        int generation;
        final RectF cover = new RectF();
        final Rect bounds = new Rect();

        Saved(ViewGroup root, TextView view) {
            this.root = new WeakReference<>(root);
            nativeColors = view.getTextColors();
            color = target = nativeColors.getDefaultColor();
            custom = customClasses.stream().anyMatch(type -> type.isInstance(view));
        }
    }

    private static final class Icon {
        final WeakReference<ViewGroup> root;
        ColorFilter nativeFilter;
        ColorFilter filter;
        int color, target;
        boolean closing;
        ValueAnimator animator;

        Icon(ViewGroup root, ImageView view) {
            this.root = new WeakReference<>(root);
            nativeFilter = view.getColorFilter();
        }
    }

    static void track(View clock) {
        clocks.add(clock);
    }

    static boolean overridesDraw(TextView view) {
        Saved value = colors.get(view);
        return value != null && value.initialized;
    }


    static void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> custom =
                Class.forName(
                        "com.samsung.android.uniform.widget.ClockNonePaddingTextView",
                        false,
                        loader);
        if (!customClasses.contains(custom)) {
            final Field outlineColor = resolveOutlineField(custom.getSuperclass());
            Method draw = custom.getDeclaredMethod("onDraw", Canvas.class);
            module.hook(draw)
                    .setId("dressroom-advanced.media-clock-palette-draw")
                    .intercept(
                            chain -> {
                                TextView view = (TextView) chain.getThisObject();
                                Saved saved = colors.get(view);
                                if (saved == null || !saved.initialized) return chain.proceed();
                                android.text.TextPaint paint = view.getPaint();
                                Shader shader = paint.getShader();
                                int color = paint.getColor();
                                int nativeOutline =
                                        outlineColor == null ? 0 : outlineColor.getInt(view);
                                float radius = paint.getShadowLayerRadius();
                                float dx = paint.getShadowLayerDx(), dy = paint.getShadowLayerDy();
                                int shadow = paint.getShadowLayerColor();
                                TextView previous = drawing.get();
                                try {
                                    drawing.set(view);
                                    paint.setShader(null);
                                    paint.setColor(saved.color);
                                    if (outlineColor != null)
                                        outlineColor.setInt(view, saved.color);
                                    shadow(view, saved);
                                    return chain.proceed();
                                } finally {
                                    paint.setShader(shader);
                                    paint.setColor(color);
                                    if (outlineColor != null)
                                        outlineColor.setInt(view, nativeOutline);
                                    paint.setShadowLayer(radius, dx, dy, shadow);
                                    if (previous == null) drawing.remove();
                                    else drawing.set(previous);
                                }
                            });
            module.deoptimize(draw);
            customClasses.add(custom);
        }
        if (installed) return;
        Method textDraw = TextView.class.getDeclaredMethod("onDraw", Canvas.class);
        module.hook(textDraw)
                .setId("dressroom-advanced.media-clock-soft-shadow")
                .intercept(
                        chain -> {
                            TextView view = (TextView) chain.getThisObject();
                            Saved saved = colors.get(view);
                            if (saved == null || !saved.initialized || saved.custom)
                                return chain.proceed();
                            android.text.TextPaint paint = view.getPaint();
                            float radius = paint.getShadowLayerRadius();
                            float dx = paint.getShadowLayerDx(), dy = paint.getShadowLayerDy();
                            int color = paint.getShadowLayerColor();
                            Shader shader = paint.getShader();
                            int textColor = paint.getColor();
                            try {
                                paint.setShader(null);
                                paint.setColor(saved.color);
                                shadow(view, saved);
                                return chain.proceed();
                            } finally {
                                paint.setShader(shader);
                                paint.setColor(textColor);
                                paint.setShadowLayer(radius, dx, dy, color);
                            }
                        });
        module.deoptimize(textDraw);
        Method currentColor = TextView.class.getDeclaredMethod("getCurrentTextColor");
        module.hook(currentColor)
                .setId("dressroom-advanced.media-clock-palette-color")
                .intercept(
                        chain -> {
                            Saved saved = colors.get((TextView) chain.getThisObject());
                            return saved != null
                                            && saved.custom
                                            && saved.initialized
                                            && drawing.get() == chain.getThisObject()
                                    ? saved.color
                                    : chain.proceed();
                        });
        module.deoptimize(currentColor);
        for (Class<?> type : new Class<?>[] {int.class, ColorStateList.class}) {
            Method setter = TextView.class.getDeclaredMethod("setTextColor", type);
            module.hook(setter)
                    .setId("dressroom-advanced.media-clock-contrast-" + type.getSimpleName())
                    .intercept(
                            chain -> {
                                Saved saved = colors.get((TextView) chain.getThisObject());
                                if (saved == null || saved.custom || writing)
                                    return chain.proceed();
                                Object value = chain.getArg(0);
                                if (value instanceof Integer color)
                                    saved.nativeColors = ColorStateList.valueOf(color);
                                else if (value instanceof ColorStateList list)
                                    saved.nativeColors = list;
                                else return chain.proceed();
                                Object replacement =
                                        type == int.class
                                                ? saved.color
                                                : ColorStateList.valueOf(saved.color);
                                return chain.proceed(new Object[] {replacement});
                            });
            module.deoptimize(setter);
        }
        Method filter = ImageView.class.getDeclaredMethod("setColorFilter", ColorFilter.class);
        module.hook(filter)
                .setId("dressroom-advanced.media-weather-palette")
                .intercept(
                        chain -> {
                            Icon saved = icons.get((ImageView) chain.getThisObject());
                            if (saved == null || writing) return chain.proceed();
                            saved.nativeFilter = (ColorFilter) chain.getArg(0);
                            return chain.proceed(new Object[] {saved.filter});
                        });
        module.deoptimize(filter);
        installed = true;
    }

    static AuraSpec computeAura(ArtworkSamples.Stats stats) {
        boolean darkText = stats.mean() > .20;
        int textColor = darkText ? 0xff17191c : 0xfffff9f2;
        int scrimColor = darkText ? 0xfffff9f2 : 0xff000000;
        float spread = clamp((float) ((stats.spread() - .02) / .18));
        float conflict;
        if (darkText) {
            double darkSpot = Math.max(1.0 - stats.min(), (1.0 - stats.mean()) * .85);
            conflict = clamp((float) ((darkSpot - .15) / .65));
        } else {
            double hotSpot = Math.max(stats.max(), stats.mean() * 1.5);
            conflict = clamp((float) ((hotSpot - .03) / .55));
        }
        float scrimAlpha = conflict * (darkText ? .44f : .42f);
        float blurAlpha = Math.max(scrimAlpha, clamp(Math.max(spread, conflict * .85f)));
        return new AuraSpec(textColor, scrimColor, blurAlpha, scrimAlpha, conflict);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }

    static boolean hasAura(ViewGroup root) {
        if (root == null) return false;
        Snapshot snapshot = samples.get(root);
        return snapshot != null
                && snapshot.spec != null
                && snapshot.spec.blurAlpha() > .005f
                && !snapshot.localBounds.isEmpty();
    }

    static void drawAura(
            Canvas canvas,
            ViewGroup root,
            RenderNode blurredArt,
            RectF destination,
            Path clipOutline,
            float progress) {
        if (root == null
                || canvas == null
                || !canvas.isHardwareAccelerated()
                || blurredArt == null
                || !blurredArt.hasDisplayList()
                || destination == null
                || destination.isEmpty()) {
            return;
        }
        Snapshot snapshot = samples.get(root);
        if (snapshot == null
                || snapshot.spec == null
                || snapshot.spec.blurAlpha() <= .005f
                || snapshot.localBounds.isEmpty()) {
            return;
        }
        float emphasis = 0f;
        for (Saved saved : colors.values()) {
            if (saved != null && saved.initialized && saved.root.get() == root) {
                emphasis = Math.max(emphasis, saved.emphasis);
            }
        }
        if (emphasis <= .005f) return;
        float reveal = clamp((progress - .25f) / .75f) * emphasis;
        float layerAlpha = snapshot.spec.blurAlpha() * reveal;
        if (layerAlpha <= .005f) return;
        float density = root.getResources().getDisplayMetrics().density;
        float padX = Math.max(28f * density, snapshot.localBounds.width() * .22f);
        float padY = Math.max(24f * density, snapshot.localBounds.height() * .28f);
        RectF aura = snapshot.auraBounds;
        aura.set(
                snapshot.localBounds.left - padX,
                snapshot.localBounds.top - padY,
                snapshot.localBounds.right + padX,
                snapshot.localBounds.bottom + padY);
        if (!aura.intersect(destination) || aura.isEmpty()) return;
        int clip = canvas.save();
        if (clipOutline != null) canvas.clipPath(clipOutline);
        snapshot.layerPaint.setAlpha(Math.round(layerAlpha * 255f));
        int layer = canvas.saveLayer(aura, snapshot.layerPaint);
        canvas.drawRenderNode(blurredArt);
        if (snapshot.spec.scrimAlpha() > .005f) {
            float scrimRel = clamp(snapshot.spec.scrimAlpha() / Math.max(.005f, snapshot.spec.blurAlpha()));
            snapshot.scrimPaint.setColor(
                    (snapshot.spec.scrimColor() & 0xffffff)
                            | (Math.round(scrimRel * 255f) << 24));
            canvas.drawRect(aura, snapshot.scrimPaint);
        }
        snapshot.shaderMatrix.reset();
        snapshot.shaderMatrix.setScale(aura.width() * .5f, aura.height() * .5f);
        snapshot.shaderMatrix.postTranslate(aura.centerX(), aura.centerY());
        snapshot.maskShader.setLocalMatrix(snapshot.shaderMatrix);
        canvas.drawRect(aura, snapshot.maskPaint);
        canvas.restoreToCount(layer);
        canvas.restoreToCount(clip);
    }

    static void clear(ViewGroup root) {
        samples.remove(root);
        if (colors.isEmpty() && icons.isEmpty()) return;
        for (TextView view : new ArrayList<>(colors.keySet())) {
            Saved saved = colors.get(view);
            if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                if (!saved.closing) {
                    if (saved.custom) saved.nativeColors = view.getTextColors();
                    fade(view, saved, saved.nativeColors.getDefaultColor(), saved.conflict, true);
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
                    icons.remove(view);
                    writeIcon(view, saved.nativeFilter);
                }
            }
        }
    }

    static void apply(ViewGroup root, Bitmap image, RectF destination) {
        if (!installed || image == null || image.isRecycled() || destination.isEmpty()) return;
        Work work = workspace.get();
        if (work.busy) work = new Work();
        work.busy = true;
        try {
            apply(root, image, destination, work);
        } finally {
            work.clear();
        }
    }

    private static void apply(ViewGroup root, Bitmap image, RectF destination, Work work) {
        int generation = image.getGenerationId();
        Snapshot snapshot = samples.get(root);
        if (snapshot == null
                || snapshot.source.get() != image
                || snapshot.generation != generation) {
            Bitmap readable =
                    image.getConfig() == Bitmap.Config.HARDWARE
                            ? image.copy(Bitmap.Config.ARGB_8888, false)
                            : image;
            if (readable == null) return;
            Bitmap pixels = null;
            ArtworkSamples cached;
            try {
                pixels =
                        Bitmap.createScaledBitmap(
                                readable,
                                Math.min(96, image.getWidth()),
                                Math.min(96, image.getHeight()),
                                true);
                if (pixels == null) return;
                cached = ArtworkSamples.read(pixels);
            } finally {
                if (pixels != null && pixels != image && pixels != readable) pixels.recycle();
                if (readable != image) readable.recycle();
            }
            snapshot = new Snapshot(new WeakReference<>(image), generation, cached);
            samples.put(root, snapshot);
        }
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
        Set<TextView> visible = work.visible;
        Set<ImageView> weather = work.weather;
        for (View clock : clocks) work.clocks.add(clock);
        for (View clock : work.clocks) {
            if (clock == null
                    || !clock.isShown()
                    || clock.getWindowToken() != root.getWindowToken()) continue;
            if (clock.getDisplay() == null
                    || (!ScreenIdentity.supported() && clock.getDisplay().getDisplayId() != 0)
                    || root.getDisplay() == null
                    || clock.getDisplay().getDisplayId() != root.getDisplay().getDisplayId())
                continue;
            try {
                String category = String.valueOf(Reflect.call(clock, "getCategory"));
                if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) continue;
            } catch (Exception ignored) {
                continue;
            }
            collect(clock, visible, weather);
        }
        // Pick once for the whole clock, using the final fullscreen crop. Sampling
        // each moving digit independently made a single time alternate black/white.
        if (!visible.isEmpty()) {
            work.clockBounds.setEmpty();
            for (TextView view : visible)
                if (view.getGlobalVisibleRect(work.bounds)) work.clockBounds.union(work.bounds);
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
                            computeAura(snapshot.pixels.analyze(work.cover, work.clockBounds));
                }
            }
        }
        if (snapshot.spec == null) return;
        int playerColor = snapshot.spec.textColor();
        float conflict = snapshot.spec.conflict();
        for (TextView view : visible) {
            Rect bounds = work.bounds;
            if (!view.getGlobalVisibleRect(bounds) || bounds.isEmpty()) continue;
            Saved saved = colors.get(view);
            if (saved == null) {
                saved = new Saved(root, view);
                colors.put(view, saved);
            }
            if (saved.initialized
                    && !saved.closing
                    && saved.bitmap == image
                    && saved.generation == generation
                    && saved.cover.equals(cover)
                    && saved.bounds.equals(bounds)) continue;
            saved.initialized = true;
            saved.bitmap = image;
            saved.generation = generation;
            saved.cover.set(cover);
            saved.bounds.set(bounds);
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
                icons.remove(view);
                writeIcon(view, saved.nativeFilter);
            }
        }
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
                closing && !saved.custom ? new android.animation.ArgbEvaluator() : null;
        if (!closing) {
            saved.conflict = conflict;
            if (target == 0xff17191c) {
                int maxAlpha = Math.round(0x55 + (0xcc - 0x55) * conflict);
                saved.shadowColor = (maxAlpha << 24) | 0xffffff;
            } else {
                int maxAlpha = Math.round(0x88 + (0xe0 - 0x88) * conflict);
                saved.shadowColor = maxAlpha << 24;
            }
        }
        saved.target = target;
        saved.closing = closing;
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    // Native blur can update the temperature's text alpha during
                    // the return. Follow that color before handing the view back.
                    saved.color = returnColor == null
                            ? (Integer) value.getAnimatedValue()
                            : (Integer) returnColor.evaluate(value.getAnimatedFraction(),
                                    startColor, saved.nativeColors.getDefaultColor());
                    saved.emphasis =
                            startEmphasis
                                    + ((closing ? 0f : 1f) - startEmphasis)
                                            * value.getAnimatedFraction();
                    if (!saved.custom) write(view, ColorStateList.valueOf(saved.color));
                    view.invalidate();
                    ViewGroup owner = saved.root.get();
                    if (owner != null) owner.invalidate();
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && colors.get(view) == saved) {
                            colors.remove(view);
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
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    saved.color = (Integer) value.getAnimatedValue();
                    saved.filter = new PorterDuffColorFilter(saved.color, PorterDuff.Mode.SRC_IN);
                    writeIcon(view, saved.filter);
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && icons.get(view) == saved) {
                            icons.remove(view);
                            writeIcon(view, saved.nativeFilter);
                        }
                    }
                });
        animator.start();
    }

    private static void collect(View view, Set<TextView> found, Set<ImageView> weather) {
        if (!view.isShown()) return;
        if (view instanceof TextView text && text.getText().length() > 0) found.add(text);
        if (view instanceof ImageView image
                && image.getDrawable() != null
                && image.getId() != View.NO_ID)
            try {
                if (image.getResources().getResourceEntryName(image.getId()).contains("weather"))
                    weather.add(image);
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                collect(group.getChildAt(i), found, weather);
    }

    private static void shadow(TextView view, Saved saved) {
        if (saved.emphasis <= 0) return;
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
}
