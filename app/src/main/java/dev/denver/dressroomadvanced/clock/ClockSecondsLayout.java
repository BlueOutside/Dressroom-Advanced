package dev.denver.dressroomadvanced;

import android.view.Gravity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure geometry, axis, and directional motion-blur helpers for inline clock seconds. */
final class ClockSecondsLayout {
    private static final float MIN_FIT_SCALE = 0.35f;
    private static final float MIN_CONDENSED_ASPECT = 0.72f;
    private static final float BLUR_EPSILON = 0.35f;
    private static final Pattern HEGT_AXIS =
            Pattern.compile("(['\"]HEGT['\"]\\s*)[-+]?\\d+(?:\\.\\d+)?");

    record Plan(
            float fitScale,
            float shiftX,
            float anchorX,
            float anchorY,
            float slideDistance) {}

    private ClockSecondsLayout() {}

    static Plan computePlan(
            float hmLeft,
            float hmTop,
            float hmRight,
            float hmBottom,
            float mmRight,
            float gapPx,
            float secWidth,
            float parentScale,
            float hmWinLeft,
            float hmWinRight,
            float rootWidth,
            float edgeMarginPx,
            int horizontalGravity,
            boolean dateAbove,
            float maxSlidePx) {
        return computePlan(hmLeft, hmTop, hmRight, hmBottom, mmRight, gapPx, secWidth,
                parentScale, hmWinLeft, hmWinRight, rootWidth, edgeMarginPx, horizontalGravity,
                dateAbove, maxSlidePx, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    static Plan computePlan(
            float hmLeft, float hmTop, float hmRight, float hmBottom, float mmRight,
            float gapPx, float secWidth, float parentScale, float hmWinLeft, float hmWinRight,
            float rootWidth, float edgeMarginPx, int horizontalGravity, boolean dateAbove,
            float maxSlidePx, float clipLeft, float clipRight) {
        float scale = Float.isFinite(parentScale) && parentScale > 0.01f ? parentScale : 1f;
        float cleanGap = Math.max(0f, gapPx);
        float cleanSec = Math.max(0f, secWidth);
        float combLeft = hmLeft;
        float combRight = Math.max(hmRight, mmRight + cleanGap + cleanSec);
        float combWidth = Math.max(1f, combRight - combLeft);

        float safeWinLeft = Math.max(clipLeft, Math.min(hmWinLeft, Math.max(0f, edgeMarginPx)));
        float safeWinRight = Math.min(clipRight, Math.max(hmWinRight, rootWidth - Math.max(0f, edgeMarginPx)));
        float availWinWidth = Math.max(1f, safeWinRight - safeWinLeft);
        float combWinWidth = Math.max(1f, combWidth * scale);

        float fitScale = clamp(Math.min(1f, availWinWidth / combWinWidth), MIN_FIT_SCALE, 1f);
        float targetWinWidth = fitScale * combWinWidth;

        float desiredWinLeft;
        int masked = horizontalGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (masked == Gravity.LEFT) {
            desiredWinLeft = hmWinLeft;
        } else if (masked == Gravity.RIGHT) {
            desiredWinLeft = hmWinRight - targetWinWidth;
        } else {
            float hmWinCenter = 0.5f * (hmWinLeft + hmWinRight);
            desiredWinLeft = hmWinCenter - 0.5f * targetWinWidth;
        }

        float maxWinLeft = Math.max(safeWinLeft, safeWinRight - targetWinWidth);
        float targetWinLeft = clamp(desiredWinLeft, safeWinLeft, maxWinLeft);
        float shiftX = (targetWinLeft - hmWinLeft) / scale;
        float anchorY = dateAbove ? hmTop : hmBottom;
        float slide = Math.min(cleanSec * 0.55f, Math.max(0f, maxSlidePx));
        return new Plan(fitScale, shiftX, combLeft, anchorY, slide);
    }

    static float scaleAt(Plan plan, float progress) {
        float u = clamp01(progress);
        return 1f + (plan.fitScale - 1f) * u;
    }

    static float fitScaleY(float fitScaleX) {
        if (!Float.isFinite(fitScaleX)) return 1f;
        return clamp(fitScaleX / MIN_CONDENSED_ASPECT, MIN_FIT_SCALE, 1f);
    }

    static float scaleYAt(Plan plan, float progress) {
        float u = clamp01(progress);
        return 1f + (fitScaleY(plan.fitScale) - 1f) * u;
    }

    static float shiftAt(Plan plan, float progress) {
        return plan.shiftX * clamp01(progress);
    }

    static float timeGroupTx(Plan plan, float progress, float tgLeft, float baseTx) {
        float s = scaleAt(plan, progress);
        float dx = shiftAt(plan, progress);
        return baseTx + dx + (1f - s) * (plan.anchorX - tgLeft);
    }

    static float timeGroupTy(Plan plan, float progress, float tgTop, float baseTy) {
        float sy = scaleYAt(plan, progress);
        return baseTy + (1f - sy) * (plan.anchorY - tgTop);
    }

    static float hostTx(Plan plan, float progress) {
        float s = scaleAt(plan, progress);
        float dx = shiftAt(plan, progress);
        return dx + (1f - s) * plan.anchorX;
    }

    static float hostTy(Plan plan, float progress) {
        float sy = scaleYAt(plan, progress);
        return (1f - sy) * plan.anchorY;
    }

    static float slideOffset(Plan plan, float progress) {
        return (1f - clamp01(progress)) * plan.slideDistance;
    }

    static float motionSigmaX(float progress, float maxSigmaX) {
        float u = clamp01(progress);
        if (u >= 0.995f || maxSigmaX <= 0f) return 0f;
        float sigma = maxSigmaX * (float) Math.pow(1f - u, 1.35);
        return sigma < BLUR_EPSILON ? 0f : sigma;
    }

    static float motionSigmaY(float progress, float maxSigmaY) {
        float u = clamp01(progress);
        if (u >= 0.995f || maxSigmaY <= 0f) return 0f;
        float sigma = maxSigmaY * (float) Math.pow(1f - u, 1.6);
        return sigma < BLUR_EPSILON ? 0f : sigma;
    }

    static float revealAlpha(float progress) {
        float u = clamp01(progress);
        if (u <= 0f) return 0f;
        if (u >= 1f) return 1f;
        return clamp01((float) Math.pow(u, 0.72));
    }

    static int compactVerticalFontType(int fontType) {
        return switch (fontType) {
            case 802 -> 801;
            case 805 -> 804;
            default -> fontType;
        };
    }

    static String stripStretchAxis(String variation) {
        if (variation == null || variation.isEmpty()) return variation;
        Matcher matcher = HEGT_AXIS.matcher(variation);
        return matcher.find() ? matcher.replaceAll("$10") : variation;
    }

    static String twoDigits(int value, Locale locale) {
        int clamped = Math.max(0, Math.min(59, value));
        Locale active = locale == null ? Locale.getDefault() : locale;
        return String.format(active, "%02d", clamped);
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) return 0f;
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float clamp(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        return v < min ? min : (v > max ? max : v);
    }
}
