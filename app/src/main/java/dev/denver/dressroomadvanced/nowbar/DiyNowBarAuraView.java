package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.SystemClock;
import android.view.View;

/**
 * Hardware-accelerated AGSL shader overlay for DIY Now Bar visual effects:
 * AI Presence liquid-brush edge sweep, Outer Glow animated neon border ring, and Gradient Fill.
 * Used identically inside the real Lockscreen Now Bar and the Studio live preview.
 */
final class DiyNowBarAuraView extends View {
    private static final String AI_PRESENCE_AGSL =
            "uniform float2 uRes;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uRadius;\n"
                    + "uniform float uOpacity;\n"
                    + "uniform half3 uColor1;\n"
                    + "uniform half3 uColor2;\n"
                    + "float sdRoundBox(float2 p, float2 b, float r) {\n"
                    + "    float2 q = abs(p) - (b - float2(r));\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "half4 main(float2 frag) {\n"
                    + "    float2 halfSize = uRes * 0.5;\n"
                    + "    float2 p = frag - halfSize;\n"
                    + "    float minHalf = max(min(halfSize.x, halfSize.y), 1.0);\n"
                    + "    float r = min(uRadius, minHalf);\n"
                    + "    float d = sdRoundBox(p, halfSize, r);\n"
                    + "    if (d > 0.5) return half4(0.0);\n"
                    + "    float edgeBand = smoothstep(-minHalf * 0.72, -1.0, d);\n"
                    + "    float crispRim = exp(-abs(d + minHalf * 0.06) / max(minHalf * 0.14, 3.0));\n"
                    + "    float2 uv = frag / max(uRes, float2(1.0));\n"
                    + "    float t = uTime * 1.15;\n"
                    + "    float2 b1 = float2(0.5 + 0.42 * cos(t), 0.5 + 0.44 * sin(t * 1.13));\n"
                    + "    float2 b2 = float2(0.5 + 0.44 * cos(t + 2.1), 0.5 + 0.42 * sin(t * 0.87 + 1.4));\n"
                    + "    float2 b3 = float2(0.5 + 0.38 * cos(t * 1.3 + 4.2), 0.5 + 0.46 * sin(t + 3.7));\n"
                    + "    float w1 = exp(-dot((uv - b1) * float2(1.4, 2.0), (uv - b1) * float2(1.4, 2.0)) * 2.4);\n"
                    + "    float w2 = exp(-dot((uv - b2) * float2(1.4, 2.0), (uv - b2) * float2(1.4, 2.0)) * 2.4);\n"
                    + "    float w3 = exp(-dot((uv - b3) * float2(1.6, 1.8), (uv - b3) * float2(1.6, 1.8)) * 2.8);\n"
                    + "    half3 mid = mix(uColor1, uColor2, 0.5) * 1.18;\n"
                    + "    half3 col = uColor1 * half(w1) + uColor2 * half(w2) + mid * half(w3 * 0.85);\n"
                    + "    float sum = max(w1 + w2 + w3 * 0.85, 0.001);\n"
                    + "    col /= half(sum);\n"
                    + "    float field = clamp(w1 + w2 + w3 * 0.7, 0.0, 1.35);\n"
                    + "    float rim = crispRim * 0.88 + edgeBand * 0.42;\n"
                    + "    float a = clamp(field * rim * 0.72 * uOpacity, 0.0, 0.9);\n"
                    + "    a *= (1.0 - smoothstep(-1.0, 0.5, d));\n"
                    + "    return half4(col * half(a), half(a));\n"
                    + "}\n";

    private static final String OUTER_GLOW_AGSL =
            "uniform float2 uRes;\n"
                    + "uniform float2 uInset;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uRadius;\n"
                    + "uniform float uOpacity;\n"
                    + "uniform half3 uColor1;\n"
                    + "uniform half3 uColor2;\n"
                    + "float sdRoundBox(float2 p, float2 b, float r) {\n"
                    + "    float2 q = abs(p) - (b - float2(r));\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "float roundRectArcParam(float2 p, float2 b, float r) {\n"
                    + "    float ax = max(b.x - r, 0.001);\n"
                    + "    float ay = max(b.y - r, 0.001);\n"
                    + "    float lH = 2.0 * ax;\n"
                    + "    float lV = 2.0 * ay;\n"
                    + "    float lC = 1.5707963 * r;\n"
                    + "    float total = max(2.0 * lH + 2.0 * lV + 4.0 * lC, 0.001);\n"
                    + "    float s = 0.0;\n"
                    + "    if (abs(p.x) > ax && abs(p.y) > ay) {\n"
                    + "        float2 c = float2(p.x >= 0.0 ? ax : -ax, p.y >= 0.0 ? ay : -ay);\n"
                    + "        float2 v = p - c;\n"
                    + "        float ang = atan(v.y, v.x);\n"
                    + "        if (p.x >= 0.0 && p.y < 0.0) {\n"
                    + "            s = lH + clamp(ang + 1.5707963, 0.0, 1.5707963) * r;\n"
                    + "        } else if (p.x >= 0.0 && p.y >= 0.0) {\n"
                    + "            s = lH + lC + lV + clamp(ang, 0.0, 1.5707963) * r;\n"
                    + "        } else if (p.x < 0.0 && p.y >= 0.0) {\n"
                    + "            s = 2.0 * lH + 2.0 * lC + lV + clamp(ang - 1.5707963, 0.0, 1.5707963) * r;\n"
                    + "        } else {\n"
                    + "            s = 2.0 * lH + 3.0 * lC + 2.0 * lV + clamp(ang + 3.1415926, 0.0, 1.5707963) * r;\n"
                    + "        }\n"
                    + "    } else if (abs(p.y) > ay || (abs(p.x) <= ax && (b.y - abs(p.y)) <= (b.x - abs(p.x)))) {\n"
                    + "        if (p.y <= 0.0) {\n"
                    + "            s = clamp(p.x + ax, 0.0, lH);\n"
                    + "        } else {\n"
                    + "            s = lH + 2.0 * lC + lV + clamp(ax - p.x, 0.0, lH);\n"
                    + "        }\n"
                    + "    } else {\n"
                    + "        if (p.x >= 0.0) {\n"
                    + "            s = lH + lC + clamp(p.y + ay, 0.0, lV);\n"
                    + "        } else {\n"
                    + "            s = 2.0 * lH + 3.0 * lC + lV + clamp(ay - p.y, 0.0, lV);\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return fract(s / total);\n"
                    + "}\n"
                    + "float ogTaper(float p) {\n"
                    + "    float head = mix(0.2, 1.0, smoothstep(0.0, 0.35, p));\n"
                    + "    float tail = mix(1.0, 0.0, smoothstep(0.55, 1.0, p));\n"
                    + "    float bell = clamp(1.0 - abs(p - 0.5) * 1.6, 0.0, 1.0);\n"
                    + "    return clamp(head * tail * bell, 0.0, 1.0);\n"
                    + "}\n"
                    + "half3 ogRamp(float phase, half3 c1, half3 c2) {\n"
                    + "    float t = fract(phase);\n"
                    + "    float b1 = smoothstep(0.3, 0.7, t);\n"
                    + "    float b2 = smoothstep(0.8, 1.0, t);\n"
                    + "    return mix(c1, c2, half(b1 * (1.0 - b2)));\n"
                    + "}\n"
                    + "half4 main(float2 frag) {\n"
                    + "    float2 halfSize = uRes * 0.5;\n"
                    + "    float2 cardHalf = max(halfSize - uInset, float2(4.0));\n"
                    + "    float2 p = frag - halfSize;\n"
                    + "    float r = min(uRadius, min(cardHalf.x, cardHalf.y));\n"
                    + "    float d = sdRoundBox(p, cardHalf, r);\n"
                    + "    float insetRef = max(uInset.x, 8.0);\n"
                    + "    if (d < -6.0 || d > insetRef) return half4(0.0);\n"
                    + "    float u = roundRectArcParam(p, cardHalf, r);\n"
                    + "    float baseTime = -uTime * 0.40;\n"
                    + "    float tailLen = 0.55;\n"
                    + "    float maxThick = max(insetRef * 0.36, 4.2);\n"
                    + "    float blurSig = max(insetRef * 0.28, 3.6);\n"
                    + "    float distFromRim = abs(d - 0.6);\n"
                    + "    float insideMask = smoothstep(-3.8, 0.4, d);\n"
                    + "    float outerFade = 1.0 - smoothstep(insetRef * 0.68, insetRef, d);\n"
                    + "    float mask = insideMask * outerFade;\n"
                    + "    half3 accColor = half3(0.0);\n"
                    + "    float accAlpha = 0.0;\n"
                    + "    float lag1 = fract(baseTime - u);\n"
                    + "    if (lag1 < tailLen) {\n"
                    + "        float p1 = lag1 / tailLen;\n"
                    + "        float thick1 = maxThick * ogTaper(p1);\n"
                    + "        float core1 = 1.0 - smoothstep(thick1 * 0.65, thick1 * 1.25 + 1.2, distFromRim);\n"
                    + "        float halo1 = exp(-(distFromRim * distFromRim) / (blurSig * blurSig)) * ogTaper(p1) * 0.65;\n"
                    + "        float a1 = clamp(core1 + halo1, 0.0, 1.0);\n"
                    + "        half3 c1 = ogRamp(baseTime - lag1 + 0.25, uColor1, uColor2);\n"
                    + "        accColor += c1 * half(a1);\n"
                    + "        accAlpha = max(accAlpha, a1);\n"
                    + "    }\n"
                    + "    float oppTime = baseTime + 0.5;\n"
                    + "    float lag2 = fract(oppTime - u);\n"
                    + "    if (lag2 < tailLen) {\n"
                    + "        float p2 = lag2 / tailLen;\n"
                    + "        float thick2 = maxThick * ogTaper(p2);\n"
                    + "        float core2 = 1.0 - smoothstep(thick2 * 0.65, thick2 * 1.25 + 1.2, distFromRim);\n"
                    + "        float halo2 = exp(-(distFromRim * distFromRim) / (blurSig * blurSig)) * ogTaper(p2) * 0.65;\n"
                    + "        float fade2 = pow(clamp(1.0 - p2, 0.0, 1.0), 0.5);\n"
                    + "        float a2 = clamp((core2 + halo2) * fade2 * 1.15, 0.0, 1.0);\n"
                    + "        half3 cMid = mix(uColor1, uColor2, 0.5);\n"
                    + "        half3 cBright = mix(uColor2, half3(1.0, 0.98, 1.0), 0.25);\n"
                    + "        half3 c2 = ogRamp(oppTime - lag2 + 0.25, cMid, cBright);\n"
                    + "        accColor += c2 * half(a2);\n"
                    + "        accAlpha = max(accAlpha, a2);\n"
                    + "    }\n"
                    + "    accColor = clamp(accColor, half3(0.0), half3(1.0));\n"
                    + "    float finalA = clamp(accAlpha * mask * uOpacity, 0.0, 1.0);\n"
                    + "    return half4(accColor * half(mask * uOpacity), half(finalA));\n"
                    + "}\n";

    private static final String GRADIENT_FILL_AGSL =
            "uniform float2 uRes;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uRadius;\n"
                    + "uniform float uOpacity;\n"
                    + "uniform half3 uColor1;\n"
                    + "uniform half3 uColor2;\n"
                    + "float sdRoundBox(float2 p, float2 b, float r) {\n"
                    + "    float2 q = abs(p) - (b - float2(r));\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "half4 main(float2 frag) {\n"
                    + "    float2 halfSize = uRes * 0.5;\n"
                    + "    float2 p = frag - halfSize;\n"
                    + "    float r = min(uRadius, min(halfSize.x, halfSize.y));\n"
                    + "    float d = sdRoundBox(p, halfSize, r);\n"
                    + "    if (d > 0.5) return half4(0.0);\n"
                    + "    float2 uv = frag / max(uRes, float2(1.0));\n"
                    + "    float shift = 0.14 * sin(uTime * 0.65);\n"
                    + "    float grad = clamp(uv.x * 0.72 + uv.y * 0.35 + shift, 0.0, 1.0);\n"
                    + "    half3 col = mix(uColor1, uColor2, half(grad));\n"
                    + "    float edge = 1.0 - smoothstep(-1.0, 0.5, d);\n"
                    + "    float a = 0.34 * edge * uOpacity;\n"
                    + "    return half4(col * half(a), half(a));\n"
                    + "}\n";

    private static final String AI_MESH_WARP_AGSL =
            "uniform float2 uRes;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uRadius;\n"
                    + "uniform float uOpacity;\n"
                    + "uniform half3 uColor1;\n"
                    + "uniform half3 uColor2;\n"
                    + "float sdRoundBox(float2 p, float2 b, float r) {\n"
                    + "    float2 q = abs(p) - (b - float2(r));\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "half4 main(float2 frag) {\n"
                    + "    float2 halfSize = uRes * 0.5;\n"
                    + "    float2 p = frag - halfSize;\n"
                    + "    float minHalf = max(min(halfSize.x, halfSize.y), 1.0);\n"
                    + "    float r = min(uRadius, minHalf);\n"
                    + "    float d = sdRoundBox(p, halfSize, r);\n"
                    + "    if (d > 0.5) return half4(0.0);\n"
                    + "    float2 uv = frag / max(uRes, float2(1.0));\n"
                    + "    float t = uTime * 1.45;\n"
                    + "    float2 wuv = uv;\n"
                    + "    wuv.x += 0.14 * sin(uv.y * 5.2 + t) + 0.08 * cos(uv.x * 4.0 - t * 1.3);\n"
                    + "    wuv.y += 0.16 * cos(uv.x * 4.8 - t * 1.1) + 0.07 * sin(uv.y * 6.0 + t * 0.8);\n"
                    + "    float perimeter = atan(p.y * (halfSize.x / max(halfSize.y, 1.0)), p.x);\n"
                    + "    float travel = 0.5 + 0.5 * sin(perimeter * 2.0 - t * 1.6);\n"
                    + "    float band = smoothstep(-minHalf * 0.92, -1.0, d);\n"
                    + "    float rim = exp(-abs(d + minHalf * 0.08) / max(minHalf * 0.18, 4.0));\n"
                    + "    float mixK = clamp(0.5 + 0.45 * sin((wuv.x - wuv.y) * 3.8 + t * 0.9), 0.0, 1.0);\n"
                    + "    half3 mid = mix(uColor1, uColor2, 0.5) * 1.24;\n"
                    + "    half3 col = mix(mix(uColor1, uColor2, half(mixK)), mid, half(travel * 0.38));\n"
                    + "    float a = clamp((rim * 0.78 + band * 0.44) * (0.55 + 0.45 * travel) * uOpacity, 0.0, 0.88);\n"
                    + "    a *= (1.0 - smoothstep(-1.0, 0.5, d));\n"
                    + "    return half4(col * half(a), half(a));\n"
                    + "}\n";

    private static final String GUIDING_LIGHT_AGSL =
            "uniform float2 uRes;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uRadius;\n"
                    + "uniform float uOpacity;\n"
                    + "uniform half3 uColor1;\n"
                    + "uniform half3 uColor2;\n"
                    + "float sdRoundBox(float2 p, float2 b, float r) {\n"
                    + "    float2 q = abs(p) - (b - float2(r));\n"
                    + "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
                    + "}\n"
                    + "half4 main(float2 frag) {\n"
                    + "    float2 halfSize = uRes * 0.5;\n"
                    + "    float2 p = frag - halfSize;\n"
                    + "    float minHalf = max(min(halfSize.x, halfSize.y), 1.0);\n"
                    + "    float r = min(uRadius, minHalf);\n"
                    + "    float d = sdRoundBox(p, halfSize, r);\n"
                    + "    if (d > 0.5) return half4(0.0);\n"
                    + "    float2 uv = frag / max(uRes, float2(1.0));\n"
                    + "    float t = uTime * 0.95;\n"
                    + "    float2 s0 = float2(0.18 + 0.14 * cos(t), 0.16 + 0.12 * sin(t * 1.2));\n"
                    + "    float2 s1 = float2(0.82 + 0.14 * sin(t * 0.9), 0.22 + 0.14 * cos(t));\n"
                    + "    float2 s2 = float2(0.80 + 0.12 * cos(t * 1.1 + 1.8), 0.84 + 0.12 * sin(t * 0.8));\n"
                    + "    float2 s3 = float2(0.20 + 0.14 * sin(t + 2.4), 0.82 + 0.12 * cos(t * 1.15));\n"
                    + "    float w0 = exp(-dot((uv - s0) * float2(1.3, 1.9), (uv - s0) * float2(1.3, 1.9)) * 2.1);\n"
                    + "    float w1 = exp(-dot((uv - s1) * float2(1.3, 1.9), (uv - s1) * float2(1.3, 1.9)) * 2.1);\n"
                    + "    float w2 = exp(-dot((uv - s2) * float2(1.3, 1.9), (uv - s2) * float2(1.3, 1.9)) * 2.1);\n"
                    + "    float w3 = exp(-dot((uv - s3) * float2(1.3, 1.9), (uv - s3) * float2(1.3, 1.9)) * 2.1);\n"
                    + "    half3 c3 = mix(uColor1, uColor2, 0.35) * 1.15;\n"
                    + "    half3 c4 = mix(uColor2, half3(1.0, 0.96, 1.0), 0.32);\n"
                    + "    half3 col = uColor1 * half(w0) + c3 * half(w1) + uColor2 * half(w2) + c4 * half(w3);\n"
                    + "    float sum = max(w0 + w1 + w2 + w3, 0.001);\n"
                    + "    col /= half(sum);\n"
                    + "    float rim = exp(-abs(d + minHalf * 0.05) / max(minHalf * 0.16, 3.5));\n"
                    + "    float wash = smoothstep(-minHalf * 1.1, 0.0, d);\n"
                    + "    float a = clamp((rim * 0.72 + wash * 0.42) * min(sum, 1.25) * 0.74 * uOpacity, 0.0, 0.86);\n"
                    + "    a *= (1.0 - smoothstep(-1.0, 0.5, d));\n"
                    + "    return half4(col * half(a), half(a));\n"
                    + "}\n";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RuntimeShader shader;
    private DiyOuterGlowShader originalOuterGlow;
    private int mode = DiyNowBarCard.EFFECT_NONE;
    private int color1 = 0xFF4E8CFF;
    private int color2 = 0xFFA86BFF;
    private float cornerRadiusPx;
    private float insetPx;
    private float darkAmount;
    private boolean animating;
    private long startUptimeMs = SystemClock.uptimeMillis();

    private final Runnable frameTick =
            new Runnable() {
                @Override
                public void run() {
                    if (!animating || !shouldAnimate()) {
                        animating = false;
                        return;
                    }
                    invalidate();
                    postOnAnimation(this);
                }
            };

    DiyNowBarAuraView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void configure(int effectMode, int primaryColor, int secondaryColor, float radiusPx, float outerInsetPx) {
        boolean modeChanged = this.mode != effectMode;
        this.mode = effectMode;
        this.color1 = primaryColor;
        this.color2 = secondaryColor;
        this.cornerRadiusPx = Math.max(0f, radiusPx);
        this.insetPx = Math.max(0f, outerInsetPx);
        if (modeChanged || shader == null) {
            originalOuterGlow = null;
            shader = buildShader(effectMode);
            paint.setShader(shader);
            setRenderEffect(originalOuterGlow == null ? null : originalOuterGlow.blur());
            startUptimeMs = SystemClock.uptimeMillis();
        }
        setVisibility(shader == null ? GONE : VISIBLE);
        syncAnimationState();
        invalidate();
    }

    void setCornerRadiusPx(float radiusPx) {
        if (Math.abs(this.cornerRadiusPx - radiusPx) < 0.5f) return;
        this.cornerRadiusPx = Math.max(0f, radiusPx);
        invalidate();
    }

    void setDarkAmount(float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        if (Math.abs(this.darkAmount - clamped) < 0.01f) return;
        this.darkAmount = clamped;
        syncAnimationState();
        invalidate();
    }

    private RuntimeShader buildShader(int effectMode) {
        try {
            String src =
                    switch (effectMode) {
                        case DiyNowBarCard.EFFECT_AI_PRESENCE -> AI_PRESENCE_AGSL;
                        case DiyNowBarCard.EFFECT_OUTER_GLOW -> {
                            originalOuterGlow = DiyOuterGlowShader.create(getContext());
                            yield originalOuterGlow == null ? OUTER_GLOW_AGSL : null;
                        }
                        case DiyNowBarCard.EFFECT_GRADIENT_FILL -> GRADIENT_FILL_AGSL;
                        case DiyNowBarCard.EFFECT_AI_MESH_WARP -> AI_MESH_WARP_AGSL;
                        case DiyNowBarCard.EFFECT_GUIDING_LIGHT -> GUIDING_LIGHT_AGSL;
                        default -> null;
                    };
            return originalOuterGlow != null ? originalOuterGlow.shader()
                    : src == null ? null : new RuntimeShader(src);
        } catch (Throwable error) {
            android.util.Log.w(DressroomModule.TAG, "AGSL shader unavailable", error);
            return null;
        }
    }

    private boolean shouldAnimate() {
        return shader != null
                && isAttachedToWindow()
                && isShown()
                && getWindowVisibility() == VISIBLE
                && darkAmount < 0.25f;
    }

    private void syncAnimationState() {
        if (shouldAnimate()) {
            if (!animating) {
                animating = true;
                postOnAnimation(frameTick);
            }
        } else {
            animating = false;
            removeCallbacks(frameTick);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(frameTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncAnimationState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncAnimationState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float opacity = Math.max(0f, 1f - darkAmount * 3.2f);
        if (opacity <= 0.01f) return;
        float elapsedSec = (SystemClock.uptimeMillis() - startUptimeMs) / 1000f;
        try {
            if (originalOuterGlow != null) {
                originalOuterGlow.draw(canvas, paint, w, h, elapsedSec, cornerRadiusPx,
                        insetPx, color1, color2, opacity,
                        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
                return;
            }
            shader.setFloatUniform("uRes", w, h);
            shader.setFloatUniform("uTime", elapsedSec);
            shader.setFloatUniform("uRadius", cornerRadiusPx);
            shader.setFloatUniform("uOpacity", opacity);
            shader.setFloatUniform(
                    "uColor1",
                    Color.red(color1) / 255f,
                    Color.green(color1) / 255f,
                    Color.blue(color1) / 255f);
            shader.setFloatUniform(
                    "uColor2",
                    Color.red(color2) / 255f,
                    Color.green(color2) / 255f,
                    Color.blue(color2) / 255f);
            if (mode == DiyNowBarCard.EFFECT_OUTER_GLOW) {
                shader.setFloatUniform("uInset", insetPx, insetPx);
            }
            canvas.drawRect(0f, 0f, w, h, paint);
        } catch (RuntimeException | LinkageError error) {
            // Stop a bad provider shader instead of retrying it on every frame.
            shader = null;
            paint.setShader(null);
            setRenderEffect(null);
            syncAnimationState();
            DressroomModule.note("DIY Now Bar shader draw unavailable", error);
        }
    }
}
