package dev.denver.dressroomadvanced;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;

import java.lang.reflect.Method;

/** The same GPU filter Samsung uses for SemBlurInfo canvas blur. */
final class ArtworkBlurFilter {
    static final int MAX_CACHE_DIM = 256;

    private final float radius;
    private final View owner;
    private final ColorMatrixColorFilter color;
    private Object samsung;
    private Method build, setSamsungRadius;
    private Object legacy;
    private Method drawLegacy, detachLegacy, setLegacyRadius;
    private boolean legacyUnavailable;
    private int width, height;
    private float appliedRadius = -1f;

    private ArtworkBlurFilter(View owner, float radius, ColorMatrixColorFilter color) {
        this.owner = owner;
        this.radius = radius;
        this.color = color;
        try {
            Class<?> type = Class.forName("com.samsung.android.graphics.RenderEffectImageFilter");
            samsung = type.getConstructor().newInstance();
            setSamsungRadius = type.getMethod("setBlurRadius", float.class);
            setSamsungRadius.invoke(samsung, radius);
            build = type.getMethod("build", int.class, int.class);
            if (build.getReturnType() != RenderEffect.class) samsung = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            samsung = null;
        }
    }

    static int[] cacheSize(int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return new int[] {0, 0};
        int max = Math.max(viewportWidth, viewportHeight);
        if (max <= MAX_CACHE_DIM) return new int[] {viewportWidth, viewportHeight};
        float scale = MAX_CACHE_DIM / (float) max;
        return new int[] {
            Math.max(1, Math.round(viewportWidth * scale)),
            Math.max(1, Math.round(viewportHeight * scale))
        };
    }

    static float cacheScale(
            int viewportWidth, int viewportHeight, int cacheWidth, int cacheHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || cacheWidth <= 0 || cacheHeight <= 0) {
            return 1f;
        }
        return Math.min(cacheWidth / (float) viewportWidth, cacheHeight / (float) viewportHeight);
    }

    void resize(RenderNode node, int width, int height) {
        resize(node, width, height, 1f);
    }

    void resize(RenderNode node, int width, int height, float radiusScale) {
        float scaledRadius = Math.max(1f, radius * radiusScale);
        if (width <= 0
                || height <= 0
                || (this.width == width
                        && this.height == height
                        && Math.abs(appliedRadius - scaledRadius) < 0.25f)) {
            return;
        }
        this.width = width;
        this.height = height;
        this.appliedRadius = scaledRadius;
        RenderEffect effect = null;
        if (samsung != null) {
            try {
                setSamsungRadius.invoke(samsung, scaledRadius);
                effect = (RenderEffect) build.invoke(samsung, width, height);
                if (effect == null) samsung = null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                samsung = null;
            }
        }
        if (effect == null && (legacy != null || attachLegacy(scaledRadius))) {
            if (legacy != null && setLegacyRadius != null) {
                try {
                    setLegacyRadius.invoke(legacy, scaledRadius);
                } catch (ReflectiveOperationException
                        | RuntimeException
                        | LinkageError unavailable) {
                    detach();
                }
            }
            if (legacy != null) {
                node.setRenderEffect(
                        color == null ? null : RenderEffect.createColorFilterEffect(color));
                return;
            }
        }
        node.setRenderEffect(tint(effect != null ? effect : fallback(scaledRadius)));
    }

    void finishRecording(RenderNode node, Canvas canvas) {
        try {
            if (legacy != null) drawLegacy.invoke(legacy, canvas);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            legacyUnavailable = true;
            detach();
            node.setRenderEffect(tint(fallback(appliedRadius > 0f ? appliedRadius : radius)));
        } finally {
            node.endRecording();
        }
    }

    void detach() {
        if (legacy == null) return;
        try {
            detachLegacy.invoke(legacy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            DressroomModule.note("Media artwork filter release", unavailable);
        } finally {
            legacy = null;
            width = height = 0;
            appliedRadius = -1f;
        }
    }

    private boolean attachLegacy(float blurRadius) {
        if (legacyUnavailable) return false;
        try {
            Class<?> type = Class.forName("com.samsung.android.graphics.SemGfxImageFilter");
            Object filter = type.getConstructor().newInstance();
            drawLegacy = type.getMethod("draw", Canvas.class);
            detachLegacy = type.getMethod("onDetachedFromView");
            setLegacyRadius = type.getMethod("setBlurRadius", float.class);
            setLegacyRadius.invoke(filter, blurRadius);
            type.getMethod("onAttachToView", View.class).invoke(filter, owner);
            legacy = filter;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            legacyUnavailable = true;
            return false;
        }
    }

    private static RenderEffect fallback(float blurRadius) {
        return RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
    }

    private RenderEffect tint(RenderEffect effect) {
        return color == null ? effect : RenderEffect.createColorFilterEffect(color, effect);
    }

    static final float TOP_SOFT_RADIUS_DP = 36f;
    static final float TOP_MEDIUM_RADIUS_DP = 105f;
    static final int TOP_CROP_PAD_PX = 20;

    static int topCropHeight(int cacheHeight, float localEnd, int padPx) {
        if (cacheHeight <= 0 || !Float.isFinite(localEnd) || localEnd <= 0f) return 0;
        int needed = (int) Math.ceil(localEnd) + Math.max(0, padPx);
        int quantized = ((needed + 15) / 16) * 16;
        return Math.max(1, Math.min(cacheHeight, quantized));
    }

    static ArtworkBlurFilter fullscreen(View owner) {
        return tinted(owner, 300f);
    }

    static ArtworkBlurFilter topSoft(View owner) {
        return tinted(owner, TOP_SOFT_RADIUS_DP);
    }

    static ArtworkBlurFilter topMedium(View owner) {
        return tinted(owner, TOP_MEDIUM_RADIUS_DP);
    }

    private static ArtworkBlurFilter tinted(View owner, float radiusDp) {
        ColorMatrix color = new ColorMatrix();
        color.setSaturation(2f);
        return new ArtworkBlurFilter(
                owner,
                radiusDp * owner.getResources().getDisplayMetrics().density,
                new ColorMatrixColorFilter(color));
    }

    /**
     * Computes {@code [solid, end]} in viewport coordinates for an upside-down progressive blur
     * band behind the top clock/weather area, returning {@code {bandTop, bandTop}} when inactive.
     */
    static float[] topBandRange(
            float bandTop, float clockBottom, float density, float maxBottom) {
        if (maxBottom <= bandTop || clockBottom <= bandTop || density <= 0f) {
            return new float[] {bandTop, bandTop};
        }
        float clampedBottom = Math.min(clockBottom, maxBottom);
        float solid = Math.max(bandTop, clampedBottom - 36f * density);
        float end = Math.min(maxBottom, clampedBottom + 60f * density);
        if (end <= solid) return new float[] {bandTop, bandTop};
        return new float[] {solid, end};
    }

    /**
     * Caches a small filtered cover texture separately from cover motion so HWUI transforms the
     * blurred texture rather than re-recording and re-filtering the full screen each frame.
     */
    static final class Layers {
        private final View owner;
        private final RenderNode source = new RenderNode("Media artwork blur source");
        private final Tier controlsTier;
        private Tier topSoftTier;
        private Tier topMediumTier;
        private final RenderNode fadedBand = new RenderNode("Fullscreen media controls fade");
        private final RenderNode topBand = new RenderNode("Fullscreen media top progressive fade");
        private final Paint bandFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topMediumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF sourceBounds = new RectF();
        private int viewportWidth, viewportHeight, cacheWidth, cacheHeight;
        private int revision;
        private int bandRevision = -1, bandWidth, bandHeight;
        private float bandLocalStart = Float.NaN, bandLocalSolid = Float.NaN;
        private int topBandRevision = -1, topBandWidth, topBandHeight;
        private float topLocalSolid = Float.NaN, topLocalEnd = Float.NaN;

        private static final class Tier {
            final RenderNode filtered;
            final RenderNode cached;
            final ArtworkBlurFilter filter;
            int revision = -1;

            Tier(
                    String filteredName,
                    String cachedName,
                    ArtworkBlurFilter filter,
                    boolean useCacheLayer) {
                this.filtered = new RenderNode(filteredName);
                this.cached = useCacheLayer ? new RenderNode(cachedName) : null;
                if (this.cached != null) this.cached.setUseCompositingLayer(true, null);
                this.filter = filter;
            }

            RenderNode ensure(
                    RenderNode source, int viewportWidth, int viewportHeight, int sourceRevision) {
                return ensureCropped(
                        source, viewportWidth, viewportHeight, sourceRevision, source.getHeight());
            }

            RenderNode ensureCropped(
                    RenderNode source,
                    int viewportWidth,
                    int viewportHeight,
                    int sourceRevision,
                    int maxHeight) {
                int width = source.getWidth();
                int sourceHeight = source.getHeight();
                int height = Math.max(1, Math.min(sourceHeight, maxHeight));
                if (revision != sourceRevision
                        || !filtered.hasDisplayList()
                        || filtered.getWidth() != width
                        || filtered.getHeight() != height
                        || (cached != null
                                && (!cached.hasDisplayList()
                                        || cached.getWidth() != width
                                        || cached.getHeight() != height))) {
                    float radiusScale =
                            cacheScale(viewportWidth, viewportHeight, width, sourceHeight);
                    filter.resize(filtered, width, height, radiusScale);
                    filtered.setPosition(0, 0, width, height);
                    Canvas record = filtered.beginRecording();
                    record.drawRenderNode(source);
                    filter.finishRecording(filtered, record);

                    if (cached != null) {
                        cached.setPosition(0, 0, width, height);
                        Canvas cachedRecord = cached.beginRecording();
                        cachedRecord.drawRenderNode(filtered);
                        cached.endRecording();
                    }
                    revision = sourceRevision;
                }
                return cached != null ? cached : filtered;
            }

            void discard() {
                filtered.discardDisplayList();
                if (cached != null) cached.discardDisplayList();
                revision = -1;
            }

            void detach() {
                filter.detach();
                discard();
            }
        }

        Layers(View owner) {
            this.owner = owner;
            this.controlsTier =
                    new Tier(
                            "Fullscreen media blur",
                            "Fullscreen media blur cache",
                            fullscreen(owner),
                            true);
            fadedBand.setUseCompositingLayer(true, null);
            topBand.setUseCompositingLayer(true, null);
            bandFadePaint.setBlendMode(BlendMode.DST_IN);
            topFadePaint.setBlendMode(BlendMode.DST_IN);
            topMediumPaint.setBlendMode(BlendMode.DST_IN);
        }

        boolean needsRecording(int width, int height) {
            if (!source.hasDisplayList() || viewportWidth != width || viewportHeight != height) {
                return true;
            }
            int[] size = cacheSize(width, height);
            return cacheWidth != size[0] || cacheHeight != size[1];
        }

        Canvas beginRecording(int width, int height) {
            int[] size = cacheSize(width, height);
            viewportWidth = width;
            viewportHeight = height;
            cacheWidth = size[0];
            cacheHeight = size[1];
            sourceBounds.set(0f, 0f, cacheWidth, cacheHeight);
            source.setPosition(0, 0, cacheWidth, cacheHeight);
            return source.beginRecording();
        }

        RectF sourceBounds() {
            return sourceBounds;
        }

        void endRecording() {
            source.endRecording();
            revision++;
        }

        int revision() {
            return revision;
        }

        boolean hasBlurred() {
            return source.hasDisplayList() && cacheWidth > 0 && cacheHeight > 0;
        }

        RenderNode blurred() {
            return controlsTier.ensure(source, viewportWidth, viewportHeight, revision);
        }

        private RenderNode ensureTopSoft(int cropHeight) {
            if (topSoftTier == null) {
                topSoftTier =
                        new Tier(
                                "Fullscreen media top soft blur",
                                null,
                                topSoft(owner),
                                false);
            }
            return topSoftTier.ensureCropped(
                    source, viewportWidth, viewportHeight, revision, cropHeight);
        }

        private RenderNode ensureTopMedium(int cropHeight) {
            if (topMediumTier == null) {
                topMediumTier =
                        new Tier(
                                "Fullscreen media top medium blur",
                                null,
                                topMedium(owner),
                                false);
            }
            return topMediumTier.ensureCropped(
                    source, viewportWidth, viewportHeight, revision, cropHeight);
        }

        void draw(Canvas canvas, RectF destination) {
            RenderNode node = blurred();
            int w = node.getWidth(), h = node.getHeight();
            if (w <= 0 || h <= 0 || destination.isEmpty() || !node.hasDisplayList()) return;
            int save = canvas.save();
            canvas.translate(destination.left, destination.top);
            canvas.scale(destination.width() / w, destination.height() / h);
            canvas.drawRenderNode(node);
            canvas.restoreToCount(save);
        }

        void draw(Canvas canvas, RectF destination, float opacity) {
            if (opacity <= 0.005f || destination.isEmpty()) return;
            if (opacity >= 0.996f) {
                draw(canvas, destination);
                return;
            }
            RenderNode node = blurred();
            int w = node.getWidth(), h = node.getHeight();
            if (w <= 0 || h <= 0 || !node.hasDisplayList()) return;
            ensureBandNode(node, w, h, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            fadedBand.setAlpha(Math.max(0f, Math.min(1f, opacity)));
            int save = canvas.save();
            canvas.translate(destination.left, destination.top);
            canvas.scale(destination.width() / w, destination.height() / h);
            canvas.drawRenderNode(fadedBand);
            canvas.restoreToCount(save);
        }

        void drawFadedBand(
                Canvas canvas,
                RectF destination,
                float start,
                float solid,
                float end,
                float opacity,
                Path clip) {
            float layerTop = Math.max(0f, Math.max(start, destination.top));
            float layerBottom = Math.min(end, destination.bottom);
            if (opacity <= 0.005f
                    || destination.isEmpty()
                    || solid <= start
                    || layerBottom <= layerTop) {
                return;
            }
            RenderNode node = blurred();
            int w = node.getWidth(), h = node.getHeight();
            if (w <= 0 || h <= 0 || !node.hasDisplayList()) return;
            float scaleY = h / destination.height();
            float localStart = (start - destination.top) * scaleY;
            float localSolid = (solid - destination.top) * scaleY;
            if (localStart >= h) return;
            float targetStart = localSolid <= 0f ? Float.NEGATIVE_INFINITY : localStart;
            float targetSolid = localSolid <= 0f ? Float.NEGATIVE_INFINITY : localSolid;
            ensureBandNode(node, w, h, targetStart, targetSolid);
            fadedBand.setAlpha(Math.max(0f, Math.min(1f, opacity)));
            int coverClip = canvas.save();
            if (clip != null) canvas.clipPath(clip);
            canvas.clipRect(destination.left, layerTop, destination.right, layerBottom);
            canvas.translate(destination.left, destination.top);
            canvas.scale(destination.width() / w, destination.height() / h);
            canvas.drawRenderNode(fadedBand);
            canvas.restoreToCount(coverClip);
        }

        /** Draws the flipped, lighter 2-tier progressive blur band over the top clock region. */
        void drawTopProgressiveBand(
                Canvas canvas,
                RectF destination,
                float solid,
                float end,
                float opacity,
                Path clip) {
            float layerTop = Math.max(0f, destination.top);
            float layerBottom = Math.min(end, destination.bottom);
            if (opacity <= 0.005f
                    || destination.isEmpty()
                    || end <= solid
                    || layerBottom <= layerTop
                    || !hasBlurred()) {
                return;
            }
            int w = cacheWidth, h = cacheHeight;
            if (w <= 0 || h <= 0) return;
            float scaleY = h / destination.height();
            float localSolid = Math.max(0f, (solid - destination.top) * scaleY);
            float localEnd = Math.min(h, (end - destination.top) * scaleY);
            if (localEnd <= localSolid + 0.25f) return;
            int cropH = topCropHeight(h, localEnd, TOP_CROP_PAD_PX);
            if (cropH <= 0) return;
            RenderNode softNode = ensureTopSoft(cropH);
            RenderNode mediumNode = ensureTopMedium(cropH);
            if (!softNode.hasDisplayList() || !mediumNode.hasDisplayList()) {
                return;
            }
            int bandH = Math.min(cropH, Math.max(1, (int) Math.ceil(localEnd) + 1));
            ensureTopBandNode(softNode, mediumNode, w, bandH, localSolid, localEnd);
            topBand.setAlpha(Math.max(0f, Math.min(1f, opacity)));
            int coverClip = canvas.save();
            if (clip != null) canvas.clipPath(clip);
            canvas.clipRect(destination.left, layerTop, destination.right, layerBottom);
            canvas.translate(destination.left, destination.top);
            canvas.scale(destination.width() / w, destination.height() / h);
            canvas.drawRenderNode(topBand);
            canvas.restoreToCount(coverClip);
        }

        private void ensureBandNode(
                RenderNode node, int w, int h, float targetStart, float targetSolid) {
            if (bandRevision != revision
                    || !fadedBand.hasDisplayList()
                    || bandWidth != w
                    || bandHeight != h
                    || !matchesStops(bandLocalStart, bandLocalSolid, targetStart, targetSolid)) {
                if (Float.isFinite(targetStart) && Float.isFinite(targetSolid)) {
                    bandFadePaint.setShader(
                            new LinearGradient(
                                    0f,
                                    targetStart,
                                    0f,
                                    targetSolid,
                                    0x00ffffff,
                                    0xffffffff,
                                    Shader.TileMode.CLAMP));
                } else {
                    bandFadePaint.setShader(null);
                }
                fadedBand.setPosition(0, 0, w, h);
                Canvas record = fadedBand.beginRecording(w, h);
                record.drawRenderNode(node);
                if (bandFadePaint.getShader() != null) record.drawRect(0f, 0f, w, h, bandFadePaint);
                fadedBand.endRecording();
                bandRevision = revision;
                bandWidth = w;
                bandHeight = h;
                bandLocalStart = targetStart;
                bandLocalSolid = targetSolid;
            }
        }

        private void ensureTopBandNode(
                RenderNode softNode,
                RenderNode mediumNode,
                int w,
                int h,
                float targetSolid,
                float targetEnd) {
            if (topBandRevision != revision
                    || !topBand.hasDisplayList()
                    || topBandWidth != w
                    || topBandHeight != h
                    || !matchesStops(topLocalSolid, topLocalEnd, targetSolid, targetEnd)) {
                topFadePaint.setShader(
                        new LinearGradient(
                                0f,
                                targetSolid,
                                0f,
                                targetEnd,
                                0xffffffff,
                                0x00ffffff,
                                Shader.TileMode.CLAMP));
                float midSolid = targetSolid * 0.25f;
                float midEnd = targetSolid > 0.25f
                        ? targetSolid + (targetEnd - targetSolid) * 0.45f
                        : targetEnd * 0.55f;
                boolean drawMedium = midEnd > midSolid + 0.25f;
                if (drawMedium) {
                    topMediumPaint.setShader(
                            new LinearGradient(
                                    0f,
                                    midSolid,
                                    0f,
                                    midEnd,
                                    0xffffffff,
                                    0x00ffffff,
                                    Shader.TileMode.CLAMP));
                } else {
                    topMediumPaint.setShader(null);
                }
                topBand.setPosition(0, 0, w, h);
                Canvas record = topBand.beginRecording(w, h);
                int softLayer = record.saveLayer(0f, 0f, w, h, null);
                record.drawRenderNode(softNode);
                record.drawRect(0f, 0f, w, h, topFadePaint);
                record.restoreToCount(softLayer);
                if (drawMedium) {
                    int medLayer = record.saveLayer(0f, 0f, w, h, null);
                    record.drawRenderNode(mediumNode);
                    record.drawRect(0f, 0f, w, h, topMediumPaint);
                    record.restoreToCount(medLayer);
                }
                topBand.endRecording();
                topBandRevision = revision;
                topBandWidth = w;
                topBandHeight = h;
                topLocalSolid = targetSolid;
                topLocalEnd = targetEnd;
            }
        }

        private static boolean matchesStops(float a0, float a1, float b0, float b1) {
            boolean hasA = Float.isFinite(a0) && Float.isFinite(a1);
            boolean hasB = Float.isFinite(b0) && Float.isFinite(b1);
            return hasA == hasB
                    && (!hasA || (Math.abs(a0 - b0) < 0.25f && Math.abs(a1 - b1) < 0.25f));
        }

        void discardDisplayLists() {
            source.discardDisplayList();
            controlsTier.discard();
            if (topSoftTier != null) topSoftTier.discard();
            if (topMediumTier != null) topMediumTier.discard();
            fadedBand.discardDisplayList();
            topBand.discardDisplayList();
            viewportWidth = viewportHeight = cacheWidth = cacheHeight = 0;
            bandRevision = topBandRevision = -1;
            bandWidth = bandHeight = topBandWidth = topBandHeight = 0;
            bandLocalStart = bandLocalSolid = topLocalSolid = topLocalEnd = Float.NaN;
        }

        void detach() {
            controlsTier.detach();
            if (topSoftTier != null) topSoftTier.detach();
            if (topMediumTier != null) topMediumTier.detach();
            discardDisplayLists();
        }
    }
}
