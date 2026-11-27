package dev.denver.dressroomadvanced;

import android.view.Gravity;

import java.util.Locale;

public final class ClockInlineSecondsGeometryTest {
    private static int checks;

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError("Inline seconds check failed: " + detail);
        checks++;
    }

    private static boolean near(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }

    public static void main(String[] args) {
        // 1. Centered clock with plenty of room: fitScale == 1, shifts left by half of ':SS' width.
        ClockSecondsLayout.Plan centered =
                ClockSecondsLayout.computePlan(
                        0f, 0f, 400f, 180f,
                        400f, 0f, 220f, 1f,
                        340f, 740f, 1080f, 40f,
                        Gravity.CENTER_HORIZONTAL, false, 60f);
        check(near(centered.fitScale(), 1f, 1e-4f), "centered fitScale should stay 1.0");
        check(near(centered.shiftX(), -110f, 1e-3f), "centered shiftX should center HH:MM:SS");
        check(near(centered.anchorY(), 180f, 1e-4f), "date below anchors to bottom");

        // 2. Left-aligned clock with enough room on right: stays pinned on left (shiftX == 0).
        ClockSecondsLayout.Plan leftRoom =
                ClockSecondsLayout.computePlan(
                        0f, 0f, 360f, 160f,
                        360f, 0f, 200f, 1f,
                        80f, 440f, 1080f, 40f,
                        Gravity.LEFT, true, 60f);
        check(near(leftRoom.fitScale(), 1f, 1e-4f), "left fitScale should stay 1.0");
        check(near(leftRoom.shiftX(), 0f, 1e-3f), "left shiftX stays 0 when room exists");
        check(near(leftRoom.anchorY(), 0f, 1e-4f), "date above anchors to top");

        // 3. Right-aligned clock with enough room on left: shifts left by full ':SS' width.
        ClockSecondsLayout.Plan rightRoom =
                ClockSecondsLayout.computePlan(
                        0f, 0f, 360f, 160f,
                        360f, 0f, 200f, 1f,
                        640f, 1000f, 1080f, 40f,
                        Gravity.RIGHT, false, 60f);
        check(near(rightRoom.fitScale(), 1f, 1e-4f), "right fitScale should stay 1.0");
        check(near(rightRoom.shiftX(), -200f, 1e-3f), "right shiftX shifts left by secWidth");

        // 4. Stretched wide clock exceeding screen width: smoothly scales down and clamps inside safe margins.
        ClockSecondsLayout.Plan wide =
                ClockSecondsLayout.computePlan(
                        0f, 0f, 800f, 420f,
                        800f, 0f, 480f, 1f,
                        140f, 940f, 1080f, 48f,
                        Gravity.CENTER_HORIZONTAL, false, 60f);
        float avail = 1080f - 96f;
        float expectedScale = avail / 1280f;
        check(near(wide.fitScale(), expectedScale, 1e-4f), "wide clock scales smoothly to fit");
        check(near(ClockSecondsLayout.scaleYAt(wide, 1f), 1f, 1e-4f),
                "moderately wide clock preserves full height while condensing horizontally");
        float finalWinLeft = 140f + wide.shiftX();
        float finalWinRight = finalWinLeft + wide.fitScale() * 1280f;
        check(finalWinLeft >= 48f - 1e-2f && finalWinRight <= 1032f + 1e-2f,
                "scaled combined bounds stay inside safe screen margins");

        ClockSecondsLayout.Plan extreme =
                ClockSecondsLayout.computePlan(
                        0f, 0f, 900f, 420f,
                        900f, 0f, 540f, 1f,
                        90f, 990f, 840f, 32f,
                        Gravity.CENTER_HORIZONTAL, false, 60f);
        float expectedExtremeY = extreme.fitScale() / 0.72f;
        check(near(ClockSecondsLayout.scaleYAt(extreme, 1f), expectedExtremeY, 1e-4f)
                        && ClockSecondsLayout.scaleYAt(extreme, 1f) < 1f,
                "extreme narrow viewport gently scales height once horizontal condense passes 0.72");

        ClockSecondsLayout.Plan clippedViewport = ClockSecondsLayout.computePlan(
                0f, 0f, 800f, 420f, 800f, 0f, 480f, 1f,
                140f, 940f, 1080f, 48f, Gravity.CENTER_HORIZONTAL, false, 60f, 96f, 984f);
        float clippedLeft = 140f + clippedViewport.shiftX();
        float clippedRight = clippedLeft + clippedViewport.fitScale() * 1280f;
        check(clippedLeft >= 96f - 1e-2f && clippedRight <= 984f + 1e-2f,
                "combined clock fits the narrower native viewport");

        // 5. Clock dragged closer to bezel than edgeMargin: zero jump at progress=0.
        ClockSecondsLayout.Plan nearBezel =
                ClockSecondsLayout.computePlan(
                        10f, 0f, 310f, 150f,
                        310f, 0f, 160f, 1f,
                        12f, 312f, 1080f, 48f,
                        Gravity.LEFT, false, 60f);
        check(near(ClockSecondsLayout.timeGroupTx(nearBezel, 0f, 10f, 0f), 0f, 1e-4f),
                "progress 0 never jumps near bezel");

        // 6. Lockstep affine transform agreement between timeGroup and ZeroFootprintHost at every progress.
        for (ClockSecondsLayout.Plan p : new ClockSecondsLayout.Plan[] {wide, extreme}) {
            for (float u : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
                float sx = ClockSecondsLayout.scaleAt(p, u);
                float sy = ClockSecondsLayout.scaleYAt(p, u);
                float tgLeft = 24f;
                float tgTop = 12f;
                float xLocalInTg = 135f;
                float yLocalInTg = 90f;
                float xStage = tgLeft + xLocalInTg;
                float yStage = tgTop + yLocalInTg;

                float fromTgX = tgLeft + ClockSecondsLayout.timeGroupTx(p, u, tgLeft, 0f) + sx * xLocalInTg;
                float fromHostX = ClockSecondsLayout.hostTx(p, u) + sx * xStage;
                float fromTgY = tgTop + ClockSecondsLayout.timeGroupTy(p, u, tgTop, 0f) + sy * yLocalInTg;
                float fromHostY = ClockSecondsLayout.hostTy(p, u) + sy * yStage;
                check(near(fromTgX, fromHostX, 1e-3f) && near(fromTgY, fromHostY, 1e-3f),
                        "timeGroup and host stay in sub-pixel lockstep at u=" + u);
            }
        }

        // 7. Directional motion blur: horizontal streak >> vertical blur, monotonic decay to 0 at rest.
        float sx0 = ClockSecondsLayout.motionSigmaX(0f, 54f);
        float sy0 = ClockSecondsLayout.motionSigmaY(0f, 10f);
        float sxMid = ClockSecondsLayout.motionSigmaX(0.5f, 54f);
        float sxEnd = ClockSecondsLayout.motionSigmaX(1f, 54f);
        float syEnd = ClockSecondsLayout.motionSigmaY(1f, 10f);
        check(sx0 > sy0 * 4f, "directional motion blur is strongly horizontal");
        check(sx0 > sxMid && sxMid > 0f, "motion blur decays smoothly through progress");
        check(sxEnd == 0f && syEnd == 0f, "motion blur clears to zero at rest");

        // 8. Vertical compact font mapping & HEGT stripping preserve weight and roundness.
        check(ClockSecondsLayout.compactVerticalFontType(802) == 801, "802 maps to 801");
        check(ClockSecondsLayout.compactVerticalFontType(805) == 804, "805 maps to 804");
        check(ClockSecondsLayout.compactVerticalFontType(801) == 801, "801 unchanged");
        String stripped = ClockSecondsLayout.stripStretchAxis("'HEGT' 780, 'wght' 520, 'ROND' 60");
        check("'HEGT' 0, 'wght' 520, 'ROND' 60".equals(stripped), "HEGT stripped to 0: " + stripped);
        check("07".equals(ClockSecondsLayout.twoDigits(7, Locale.US)), "twoDigits pads single digit");

        // 9. Frame boost safely no-ops off-device / null view and GlyphFontCache.Applied stays exact.
        LockscreenFrameBoost.pulse(null);
        LockscreenFrameBoost.pulse(null, 250L);
        Object dummyFace = new Object();
        GlyphFontCache.Applied applied =
                new GlyphFontCache.Applied(801, "'HEGT' 2.3456, 'wght' 300", dummyFace);
        check(
                applied.matches(801, "'HEGT' 2.3456, 'wght' 300", dummyFace),
                "GlyphFontCache.Applied matches identical font request");
        check(
                !applied.matches(801, "'HEGT' 2.3457, 'wght' 300", dummyFace),
                "GlyphFontCache.Applied preserves sub-step spring precision");
        check(
                VariableFonts.compactTypeface(null, null) == null,
                "compactTypeface safely returns fallback for null clock");

        System.out.println("Inline clock seconds geometry: " + checks + " checks passed");
    }
}
