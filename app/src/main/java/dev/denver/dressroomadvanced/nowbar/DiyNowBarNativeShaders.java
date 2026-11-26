package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RuntimeShader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Drives Samsung's real native One UI 9 GPU shader engines inside NowBarOngoingCardView:
 * - AIPresenceView BORDER_TRAVEL (8-point MVC mesh warp with Samsung's exact uh5.NUDGE ViewData
 *   preset & 6 perimeter brushes recolored to the user's palette, keeping the pill center clear).
 * - GuidingLightEffect v2.2.1 (aod.w23 inside nudge_container) with 4-color lighting.
 *
 * AI Presence uses the shared aura renderer. Outer Glow uses the provider's original
 * shader through DiyOuterGlowShader, with a persistent blur pass in both Studio and runtime.
 */
final class DiyNowBarNativeShaders {
    private static final String PKG_AOD = "com.samsung.android.app.aodservice";
    private static final String ONGOING_DESC =
            "Lcom/samsung/android/app/facewidget/presentation/NowBarOngoingCardView;";
    private static final String AI_PRESENCE_DESC =
            "Lcom/samsung/android/sesl/widget/AIPresenceView;";

    private static final Map<ClassLoader, NativeHandles> handleCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    static final class CardNativeState {
        int activeEngine = DiyNowBarCard.EFFECT_NONE;
        int color1;
        int color2;
        float lastRadiusPx = -1f;
        int lastW;
        int lastH;
        View aiContainer;
        View aiView;
        FrameLayout nudgeContainer;
        Object guidingLightEffect;
    }

    private static final class NativeHandles {
        // AIPresenceView (BORDER_TRAVEL 8-point MVC mesh warp)
        Class<?> aiClass;
        Method aiSetMode;
        Object aiModeBorderTravel;
        Method aiSetCornerRadius;
        Method aiSetFrameRate;
        Method aiStart;
        Method aiPause;
        Method aiUpdateUniforms;
        Field aiBrushesField;
        Field aiViewDataField;
        Field aiShaderField;
        Constructor<?> aiBrushCtor;
        Constructor<?> aiViewDataCtor;

        // OuterGlowView cleanup handles (stops any lingering native OuterGlowView on recycled cards)
        Field outerControllerField;
        Method outerCtrlStop;

        // GuidingLightEffect (w23)
        Class<?> guidingClass;
        Constructor<?> guidingCtor;
        Method guidingGetConfig;
        Method guidingSetCornerRadius;
        Method guidingSetSize;
        Method guidingSetColors;
        Method guidingShow;
        Method guidingHide;
        Method guidingRemoveView;
        Method guidingRelease;
        Object guidingShowAnim;
        Object guidingHideAnim;

        boolean aiMeshReady() {
            return aiClass != null
                    && aiSetMode != null
                    && aiModeBorderTravel != null
                    && aiViewDataField != null
                    && aiViewDataCtor != null
                    && aiStart != null
                    && aiPause != null;
        }

        boolean guidingReady() {
            return guidingClass != null
                    && guidingCtor != null
                    && guidingGetConfig != null
                    && guidingSetCornerRadius != null
                    && guidingSetSize != null
                    && guidingSetColors != null
                    && guidingShow != null
                    && guidingRelease != null
                    && guidingShowAnim != null;
        }
    }

    private DiyNowBarNativeShaders() {}

    /** Hosts the same provider views used at runtime, without creating a native Now Bar card. */
    static final class Preview extends FrameLayout {
        private NativeHandles handles;
        private Context providerContext;
        private View ai;
        private FrameLayout guidingSurface;
        private Object guiding;
        private int effect, primary, secondary, appliedWidth = -1, appliedHeight = -1;
        private int appliedPrimary, appliedSecondary;
        private float radius, appliedRadius = -1f;
        private boolean awake, running, failed, paletteApplied, updating;
        private final Runnable unavailable;

        Preview(Context context, Runnable unavailable) {
            super(context);
            this.unavailable = unavailable;
            setWillNotDraw(false);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setVisibility(GONE);
        }

        boolean configure(int nextEffect, int color1, int color2, float corner, boolean awake) {
            if (effect != nextEffect) {
                release();
                effect = nextEffect;
                failed = false;
            }
            primary = color1;
            secondary = color2;
            radius = corner;
            this.awake = awake;
            if (!isNativeCapableEffect(effect) || failed) {
                setVisibility(GONE);
                return false;
            }
            setVisibility(VISIBLE);
            update();
            return !failed;
        }

        private void loadProvider() throws Exception {
            for (String pkg : new String[]{PKG_AOD, "com.samsung.android.app.clockpack"}) {
                Context candidate;
                try {
                    candidate = getContext().createPackageContext(pkg,
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                } catch (android.content.pm.PackageManager.NameNotFoundException absent) { continue; }
                NativeHandles h = handlesFor(candidate.getClassLoader());
                if (h == null || (effect == DiyNowBarCard.EFFECT_AI_MESH_WARP
                        ? !h.aiMeshReady() : !h.guidingReady())) continue;
                // The provider's Application is not instantiated inside Studio.
                providerContext = new ContextWrapper(candidate) {
                    @Override public Context getApplicationContext() { return this; }
                };
                handles = h;
                return;
            }
            throw new ClassNotFoundException("Native effect unavailable in installed providers");
        }

        private void update() {
            if (updating || failed || !isNativeCapableEffect(effect)) return;
            updating = true;
            try {
                if (handles == null) loadProvider();
                NativeHandles h = handles;
                if (effect == DiyNowBarCard.EFFECT_AI_MESH_WARP && ai == null) {
                    ai = (View) h.aiClass.getConstructor(Context.class, android.util.AttributeSet.class)
                            .newInstance(providerContext, null);
                    initializeAiBorder(ai, h, radius / getResources().getDisplayMetrics().density);
                    addView(ai, new FrameLayout.LayoutParams(-1, -1));
                } else if (effect == DiyNowBarCard.EFFECT_GUIDING_LIGHT && guiding == null) {
                    // Native renderers decode their textures through the host view's Context.
                    guidingSurface = new FrameLayout(providerContext) {
                        @Override protected void onDraw(Canvas canvas) {
                            super.onDraw(canvas);
                            canvas.drawColor(Color.TRANSPARENT);
                        }
                    };
                    guidingSurface.setWillNotDraw(false);
                    addView(guidingSurface, new FrameLayout.LayoutParams(-1, -1));
                    Object nativeCard = h.guidingGetConfig.getDeclaringClass()
                            .getConstructor(Context.class, android.util.AttributeSet.class)
                            .newInstance(providerContext, null);
                    Object config = h.guidingGetConfig.invoke(nativeCard);
                    guiding = h.guidingCtor.newInstance(providerContext, guidingSurface, config);
                }
                boolean paletteChanged = !paletteApplied
                        || primary != appliedPrimary || secondary != appliedSecondary;
                if (paletteChanged) {
                    if (ai != null) applyAiNudgeBrushes(ai, h, primary, secondary);
                    else h.guidingSetColors.invoke(guiding, Arrays.asList(primary,
                            blendColor(primary, secondary, 0.45f), secondary,
                            blendColor(secondary, Color.WHITE, 0.25f)));
                    paletteApplied = true;
                    appliedPrimary = primary;
                    appliedSecondary = secondary;
                }
                if (radius != appliedRadius) {
                    if (ai != null && h.aiSetCornerRadius != null)
                        h.aiSetCornerRadius.invoke(ai, radius / getResources().getDisplayMetrics().density);
                    else if (guiding != null) h.guidingSetCornerRadius.invoke(guiding, radius);
                    appliedRadius = radius;
                    invalidateOutline();
                }
                if (guiding != null && getWidth() > 0 && getHeight() > 0
                        && (appliedWidth != getWidth() || appliedHeight != getHeight())) {
                    h.guidingSetSize.invoke(guiding, getWidth(), getHeight());
                    appliedWidth = getWidth();
                    appliedHeight = getHeight();
                }
                boolean visible = awake && isShown() && getWindowVisibility() == VISIBLE;
                if (running != visible) {
                    running = visible;
                    if (ai != null) {
                        ai.setVisibility(visible ? VISIBLE : INVISIBLE);
                        (visible ? h.aiStart : h.aiPause).invoke(ai);
                    } else if (visible) h.guidingShow.invoke(guiding, h.guidingShowAnim);
                    else if (h.guidingHide != null && h.guidingHideAnim != null)
                        h.guidingHide.invoke(guiding, h.guidingHideAnim);
                }
                setAlpha(awake ? 1f : 0f);
            } catch (Exception | LinkageError error) {
                android.util.Log.w(DressroomModule.TAG, "Native Studio effect preview unavailable", error);
                release();
                failed = true;
                setVisibility(GONE);
                if (unavailable != null) post(unavailable);
            } finally {
                updating = false;
            }
        }

        private void release() {
            boolean wasUpdating = updating;
            updating = true;
            try {
                if (ai != null && handles != null) handles.aiPause.invoke(ai);
                if (guiding != null && handles != null) handles.guidingRelease.invoke(guiding);
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
            ai = null;
            guidingSurface = null;
            guiding = null;
            running = paletteApplied = false;
            appliedWidth = appliedHeight = -1;
            appliedRadius = -1f;
            removeAllViews();
            updating = wasUpdating;
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            update();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            update();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            release();
        }

        @Override protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (handles != null) update();
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (handles != null) update();
        }
    }

    static boolean isNativeCapableEffect(int effect) {
        return effect == DiyNowBarCard.EFFECT_AI_MESH_WARP
                || effect == DiyNowBarCard.EFFECT_GUIDING_LIGHT;
    }

    static boolean sync(
            View cardView,
            View container,
            CardNativeState state,
            int effect,
            int color1,
            int color2,
            float radiusPx,
            float darkAmount,
            int cardDepth) {
        if (cardView == null || state == null) return false;
        NativeHandles h = handlesFor(cardView.getClass().getClassLoader());
        if (h == null) return false;

        if (state.activeEngine != effect) {
            releaseAll(cardView, state, h);
        }

        boolean visible = darkAmount < 0.25f && cardDepth == 0;
        if (effect == DiyNowBarCard.EFFECT_AI_MESH_WARP) {
            return syncAiMeshWarp(
                    cardView, container, state, h, color1, color2, radiusPx, visible);
        }
        if (effect == DiyNowBarCard.EFFECT_GUIDING_LIGHT) {
            return syncGuidingLight(
                    cardView, container, state, h, color1, color2, radiusPx, visible);
        }
        return false;
    }

    static void updateGeometry(
            View cardView,
            View container,
            CardNativeState state,
            float radiusPx,
            float darkAmount,
            int cardDepth) {
        if (cardView == null || state == null || state.activeEngine == DiyNowBarCard.EFFECT_NONE) {
            return;
        }
        NativeHandles h = handlesFor(cardView.getClass().getClassLoader());
        if (h == null) return;
        boolean visible = darkAmount < 0.25f && cardDepth == 0;
        switch (state.activeEngine) {
            case DiyNowBarCard.EFFECT_AI_MESH_WARP ->
                    syncAiPresenceGeometry(cardView, state, h, radiusPx, visible);
            case DiyNowBarCard.EFFECT_GUIDING_LIGHT ->
                    syncGuidingLightGeometry(cardView, container, state, h, radiusPx, visible);
            default -> {}
        }
    }

    static void syncVisibility(
            View cardView, CardNativeState state, float darkAmount, int cardDepth) {
        if (cardView == null || state == null || state.activeEngine == DiyNowBarCard.EFFECT_NONE) {
            return;
        }
        NativeHandles h = handlesFor(cardView.getClass().getClassLoader());
        if (h == null) return;
        boolean visible = darkAmount < 0.25f && cardDepth == 0;
        try {
            switch (state.activeEngine) {
                case DiyNowBarCard.EFFECT_AI_MESH_WARP -> {
                    if (state.aiView != null) {
                        state.aiView.setAlpha(visible ? 1f : 0f);
                        state.aiView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
                        if (state.aiContainer != null) {
                            state.aiContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
                        }
                        if (visible) h.aiStart.invoke(state.aiView);
                        else h.aiPause.invoke(state.aiView);
                    }
                }
                case DiyNowBarCard.EFFECT_GUIDING_LIGHT -> {
                    if (state.nudgeContainer != null) {
                        state.nudgeContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                    if (state.guidingLightEffect != null) {
                        if (visible) {
                            h.guidingShow.invoke(state.guidingLightEffect, h.guidingShowAnim);
                        } else if (h.guidingHide != null && h.guidingHideAnim != null) {
                            h.guidingHide.invoke(state.guidingLightEffect, h.guidingHideAnim);
                        }
                    }
                }
                default -> {}
            }
        } catch (Throwable ignored) {
        }
    }

    static void release(View cardView, CardNativeState state) {
        if (state == null) return;
        NativeHandles h =
                cardView == null ? null : handlesFor(cardView.getClass().getClassLoader());
        releaseAll(cardView, state, h);
    }

    // -------------------------------------------------------------------------
    // Engine 1: Samsung Native AIPresenceView BORDER_TRAVEL (uh5.NUDGE MVC Mesh)
    // -------------------------------------------------------------------------

    private static boolean syncAiMeshWarp(
            View cardView,
            View container,
            CardNativeState state,
            NativeHandles h,
            int color1,
            int color2,
            float radiusPx,
            boolean visible) {
        if (!h.aiMeshReady()) return false;
        try {
            View aiContainer = findAodView(cardView, "nowbar_cardview_ai_presence_container");
            View aiView = findAodView(cardView, "nowbar_cardview_ai_presence_view");
            if (aiView == null || !h.aiClass.isInstance(aiView)) return false;

            boolean firstBind =
                    state.aiView != aiView
                            || state.activeEngine != DiyNowBarCard.EFFECT_AI_MESH_WARP;
            boolean colorsChanged = firstBind || state.color1 != color1 || state.color2 != color2;

            state.aiContainer = aiContainer;
            state.aiView = aiView;
            state.activeEngine = DiyNowBarCard.EFFECT_AI_MESH_WARP;
            state.color1 = color1;
            state.color2 = color2;

            float density = cardView.getResources().getDisplayMetrics().density;
            float radiusDp = radiusPx / Math.max(density, 1f);

            if (firstBind) {
                initializeAiBorder(aiView, h, radiusDp);
            } else {
                h.aiSetMode.invoke(aiView, h.aiModeBorderTravel);
                if (h.aiSetCornerRadius != null) h.aiSetCornerRadius.invoke(aiView, radiusDp);
            }

            if (colorsChanged) {
                applyAiNudgeBrushes(aiView, h, color1, color2);
            }

            aiView.setAlpha(visible ? 1f : 0f);
            syncAiPresenceGeometry(cardView, state, h, radiusPx, visible);
            return true;
        } catch (Throwable error) {
            DressroomModule.note("Native AIPresenceView MVC mesh sync failed", error);
            return false;
        }
    }

    private static void initializeAiBorder(View view, NativeHandles h, float radiusDp)
            throws ReflectiveOperationException {
        // Same Samsung NUDGE preset in Studio and on the lockscreen.
        Object data = h.aiViewDataCtor.newInstance(
                133.867f, 36f, radiusDp, 0f, 0.698f, 0.1f, 0.845f, 0.546f, 0.05f,
                70f, 10f, 100f, 200f, 0.95f, 5, 0.03f, 15f,
                h.aiModeBorderTravel, 4000L, -1f, 0f);
        h.aiViewDataField.set(view, data);
        if (h.aiSetFrameRate != null) h.aiSetFrameRate.invoke(view, 60L);
    }

    private static void applyAiNudgeBrushes(
            View aiView, NativeHandles h, int color1, int color2) {
        if (h.aiBrushesField == null) return;
        try {
            Constructor<?> ctor = h.aiBrushCtor;
            if (ctor == null) {
                Object existing = h.aiBrushesField.get(aiView);
                if (existing instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                    Class<?> brushClass = list.get(0).getClass();
                    for (Constructor<?> c : brushClass.getDeclaredConstructors()) {
                        Class<?>[] pt = c.getParameterTypes();
                        if (pt.length == 8
                                && pt[0] == float.class
                                && pt[4] == int.class
                                && pt[7] == float.class) {
                            c.setAccessible(true);
                            ctor = c;
                            h.aiBrushCtor = c;
                            break;
                        }
                    }
                }
            }
            if (ctor == null) return;
            int mid = blendColor(color1, color2, 0.5f);
            int highlight = blendColor(color2, Color.WHITE, 0.25f);
            int deep = blendColor(color1, color2, 0.22f);
            // Samsung uh5.NUDGE 6-brush perimeter layout recolored with the user's shader palette
            List<Object> brushes = new ArrayList<>(6);
            brushes.add(ctor.newInstance(-79.2f, -10.8f, 70f, 111f, color1, 1.762f, 0.799f, -3.24f));
            brushes.add(ctor.newInstance(-61.2f, 58.8f, 85f, 85f, deep, 1.074f, 0.8f, -80.64f));
            brushes.add(ctor.newInstance(145.2f, 98.4f, 110f, 110f, color2, 1.1f, 1.0f, -115.56f));
            brushes.add(ctor.newInstance(97.2f, 25.2f, 100f, 100f, highlight, 0.754f, 0.6f, 0f));
            brushes.add(ctor.newInstance(0f, 49.2f, 80f, 80f, mid, 1.058f, 0.8f, -2.52f));
            brushes.add(ctor.newInstance(0f, -62.4f, 130f, 112f, color1, 0.846f, 0.7f, 0f));
            h.aiBrushesField.set(aiView, brushes);
            if (h.aiUpdateUniforms != null && h.aiShaderField != null) {
                Object shader = h.aiShaderField.get(aiView);
                if (shader instanceof RuntimeShader) {
                    h.aiUpdateUniforms.invoke(aiView, shader);
                }
            }
            aiView.invalidate();
        } catch (Throwable ignored) {
        }
    }

    private static void syncAiPresenceGeometry(
            View cardView,
            CardNativeState state,
            NativeHandles h,
            float radiusPx,
            boolean visible) {
        try {
            if (state.aiView == null) return;
            state.lastRadiusPx = radiusPx;
            float density = cardView.getResources().getDisplayMetrics().density;
            if (h.aiSetCornerRadius != null) {
                h.aiSetCornerRadius.invoke(state.aiView, radiusPx / Math.max(density, 1f));
            }
            if (state.aiContainer != null) {
                state.aiContainer.setOutlineProvider(
                        new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                            }
                        });
                state.aiContainer.setClipToOutline(true);
                state.aiContainer.invalidateOutline();
                state.aiContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
            state.aiView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            if (visible) h.aiStart.invoke(state.aiView);
            else h.aiPause.invoke(state.aiView);
            state.aiView.invalidate();
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Engine 2: Samsung Native GuidingLightEffect v2.2.1 (aod.w23 in nudge_container)
    // -------------------------------------------------------------------------

    private static boolean syncGuidingLight(
            View cardView,
            View container,
            CardNativeState state,
            NativeHandles h,
            int color1,
            int color2,
            float radiusPx,
            boolean visible) {
        if (!h.guidingReady()) return false;
        try {
            View nudgeView = findAodView(cardView, "nudge_container");
            if (!(nudgeView instanceof FrameLayout nudge)) return false;

            boolean created = false;
            if (state.guidingLightEffect == null || state.nudgeContainer != nudge) {
                releaseGuidingLight(state, h);
                Object cfg = h.guidingGetConfig.invoke(cardView);
                if (cfg == null) return false;
                Context appCtx = cardView.getContext().getApplicationContext();
                if (appCtx == null) appCtx = cardView.getContext();
                sizeNudgeContainer(cardView, container, nudge);
                nudge.setVisibility(View.VISIBLE);
                state.nudgeContainer = nudge;
                state.guidingLightEffect = h.guidingCtor.newInstance(appCtx, nudge, cfg);
                created = true;
            }
            state.activeEngine = DiyNowBarCard.EFFECT_GUIDING_LIGHT;
            boolean colorsChanged = created || state.color1 != color1 || state.color2 != color2;
            state.color1 = color1;
            state.color2 = color2;

            if (colorsChanged) {
                int mid = blendColor(color1, color2, 0.45f);
                int bright = blendColor(color2, Color.WHITE, 0.25f);
                List<Integer> palette = Arrays.asList(color1, mid, color2, bright);
                h.guidingSetColors.invoke(state.guidingLightEffect, palette);
            }
            syncGuidingLightGeometry(cardView, container, state, h, radiusPx, visible);
            return true;
        } catch (Throwable error) {
            DressroomModule.note("Native GuidingLightEffect sync failed", error);
            releaseGuidingLight(state, h);
            return false;
        }
    }

    private static void sizeNudgeContainer(View cardView, View container, FrameLayout nudge) {
        int w = 0;
        int hPx = 0;
        try {
            Object wObj = Reflect.call(cardView, "getCurrentWidth");
            Object hObj = Reflect.call(cardView, "getCurrentHeight");
            if (wObj instanceof Number nw && hObj instanceof Number nh) {
                w = Math.round(nw.floatValue());
                hPx = Math.round(nh.floatValue());
            }
        } catch (Throwable ignored) {
        }
        if (w <= 0 || hPx <= 0) {
            w = container == null ? nudge.getWidth() : container.getWidth();
            hPx = container == null ? nudge.getHeight() : container.getHeight();
        }
        if (w <= 0 || hPx <= 0) return;
        ViewGroup.LayoutParams cur = nudge.getLayoutParams();
        if (!(cur instanceof FrameLayout.LayoutParams flp)
                || flp.width != w
                || flp.height != hPx
                || flp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)) {
            FrameLayout.LayoutParams next = new FrameLayout.LayoutParams(w, hPx);
            next.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            nudge.setLayoutParams(next);
        }
    }

    private static void syncGuidingLightGeometry(
            View cardView,
            View container,
            CardNativeState state,
            NativeHandles h,
            float radiusPx,
            boolean visible) {
        if (state.guidingLightEffect == null || state.nudgeContainer == null) return;
        try {
            sizeNudgeContainer(cardView, container, state.nudgeContainer);
            state.lastRadiusPx = radiusPx;
            h.guidingSetCornerRadius.invoke(state.guidingLightEffect, radiusPx);
            ViewGroup.LayoutParams nlp = state.nudgeContainer.getLayoutParams();
            int w =
                    nlp != null && nlp.width > 0
                            ? nlp.width
                            : (container == null ? state.nudgeContainer.getWidth() : container.getWidth());
            int hPx =
                    nlp != null && nlp.height > 0
                            ? nlp.height
                            : (container == null
                                    ? state.nudgeContainer.getHeight()
                                    : container.getHeight());
            if (w > 0 && hPx > 0 && (w != state.lastW || hPx != state.lastH)) {
                state.lastW = w;
                state.lastH = hPx;
                h.guidingSetSize.invoke(state.guidingLightEffect, w, hPx);
            }
            state.nudgeContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                h.guidingShow.invoke(state.guidingLightEffect, h.guidingShowAnim);
            } else if (h.guidingHide != null && h.guidingHideAnim != null) {
                h.guidingHide.invoke(state.guidingLightEffect, h.guidingHideAnim);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void releaseGuidingLight(CardNativeState state, NativeHandles h) {
        if (state.guidingLightEffect != null) {
            try {
                if (h != null && h.guidingHide != null && h.guidingHideAnim != null) {
                    h.guidingHide.invoke(state.guidingLightEffect, h.guidingHideAnim);
                }
                if (h != null && h.guidingRemoveView != null && state.nudgeContainer != null) {
                    h.guidingRemoveView.invoke(state.guidingLightEffect, state.nudgeContainer);
                }
                if (h != null && h.guidingRelease != null) {
                    h.guidingRelease.invoke(state.guidingLightEffect);
                }
            } catch (Throwable ignored) {
            }
            state.guidingLightEffect = null;
        }
        if (state.nudgeContainer != null) {
            state.nudgeContainer.setRenderEffect(null);
            state.nudgeContainer.setVisibility(View.GONE);
            state.nudgeContainer = null;
        }
    }

    private static void releaseAll(View cardView, CardNativeState state, NativeHandles h) {
        if (state.aiView != null) {
            try {
                if (h != null && h.aiPause != null) h.aiPause.invoke(state.aiView);
            } catch (Throwable ignored) {
            }
            state.aiView.setVisibility(View.GONE);
            state.aiView = null;
        }
        if (state.aiContainer != null) {
            state.aiContainer = null;
        }
        if (cardView != null) {
            View og = findAodView(cardView, "nowbar_cardview_guidingeffect_view");
            if (og != null && og.getVisibility() == View.VISIBLE) {
                try {
                    if (h != null && h.outerControllerField != null && h.outerCtrlStop != null) {
                        Object ctrl = h.outerControllerField.get(og);
                        if (ctrl != null) h.outerCtrlStop.invoke(ctrl);
                    }
                } catch (Throwable ignored) {
                }
                og.setRenderEffect(null);
                og.setVisibility(View.GONE);
            }
        }
        releaseGuidingLight(state, h);
        state.activeEngine = DiyNowBarCard.EFFECT_NONE;
        state.lastRadiusPx = -1f;
        state.lastW = 0;
        state.lastH = 0;
    }

    // -------------------------------------------------------------------------
    // Cached Reflection & DexSignatures Resolution
    // -------------------------------------------------------------------------

    private static NativeHandles handlesFor(ClassLoader loader) {
        if (loader == null) return null;
        synchronized (handleCache) {
            NativeHandles existing = handleCache.get(loader);
            if (existing != null) return existing;
            NativeHandles built = resolveHandles(loader);
            handleCache.put(loader, built);
            return built;
        }
    }

    private static NativeHandles resolveHandles(ClassLoader loader) {
        NativeHandles h = new NativeHandles();
        DexSignatures.cachedRun(
                () -> {
                    resolveAiPresenceHandles(loader, h);
                    resolveOuterGlowStopHandles(loader, h);
                    resolveGuidingLightHandles(loader, h);
                });
        return h;
    }

    private static void resolveAiPresenceHandles(ClassLoader loader, NativeHandles h) {
        try {
            Class<?> ai = Class.forName("com.samsung.android.sesl.widget.AIPresenceView", false, loader);
            h.aiClass = ai;
            Class<?> modeEnumClass = null;
            for (Method m : ai.getDeclaredMethods()) {
                String name = m.getName();
                int pc = m.getParameterCount();
                if ("setViewDataAnimationMode".equals(name) && pc == 1) {
                    m.setAccessible(true);
                    h.aiSetMode = m;
                    modeEnumClass = m.getParameterTypes()[0];
                    Object[] constants = modeEnumClass.getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if ("BORDER_TRAVEL".equals(String.valueOf(c))) {
                                h.aiModeBorderTravel = c;
                                break;
                            }
                        }
                        if (h.aiModeBorderTravel == null && constants.length > 2) {
                            h.aiModeBorderTravel = constants[2];
                        }
                    }
                } else if ("setViewDataCornerRadius".equals(name) && pc == 1) {
                    m.setAccessible(true);
                    h.aiSetCornerRadius = m;
                } else if ("setFrameRate".equals(name) && pc == 1) {
                    m.setAccessible(true);
                    h.aiSetFrameRate = m;
                } else if (pc == 1 && m.getParameterTypes()[0] == RuntimeShader.class) {
                    m.setAccessible(true);
                    h.aiUpdateUniforms = m;
                }
            }
            for (Field f : ai.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (List.class.isAssignableFrom(ft) && h.aiBrushesField == null) {
                    f.setAccessible(true);
                    h.aiBrushesField = f;
                } else if (RuntimeShader.class.isAssignableFrom(ft) && h.aiShaderField == null) {
                    f.setAccessible(true);
                    h.aiShaderField = f;
                } else if (h.aiViewDataField == null
                        && !ft.isPrimitive()
                        && !ft.isEnum()
                        && modeEnumClass != null) {
                    for (Constructor<?> c : ft.getDeclaredConstructors()) {
                        Class<?>[] pt = c.getParameterTypes();
                        if (pt.length == 21
                                && pt[0] == float.class
                                && pt[14] == int.class
                                && pt[17] == modeEnumClass
                                && pt[18] == long.class) {
                            f.setAccessible(true);
                            c.setAccessible(true);
                            h.aiViewDataField = f;
                            h.aiViewDataCtor = c;
                            break;
                        }
                    }
                }
            }
            List<DexSignatures.Body> aiBodies =
                    DexSignatures.findInClass(
                            loader,
                            ONGOING_DESC,
                            Set.of(
                                    "startAiPresenceViewEffect already started",
                                    "stopAiPresenceViewEffect run"));
            for (DexSignatures.Body body : aiBodies) {
                if (!ONGOING_DESC.equals(body.owner())) continue;
                boolean isStart =
                        body.anchors().containsKey("startAiPresenceViewEffect already started");
                boolean isStop = body.anchors().containsKey("stopAiPresenceViewEffect run");
                for (DexSignatures.Call call : body.calls()) {
                    if (AI_PRESENCE_DESC.equals(call.owner())
                            && call.parameters().isEmpty()
                            && "V".equals(call.result())) {
                        Method m = ai.getDeclaredMethod(call.name());
                        m.setAccessible(true);
                        if (isStart && h.aiStart == null) h.aiStart = m;
                        if (isStop && h.aiPause == null) h.aiPause = m;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveOuterGlowStopHandles(ClassLoader loader, NativeHandles h) {
        try {
            Class<?> og = Class.forName("com.samsung.android.sesl.widget.OuterGlowView", false, loader);
            Class<?> base = og.getSuperclass();
            if (base == null) return;
            for (Field f : base.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (!ft.isPrimitive() && ft != String.class && !View.class.isAssignableFrom(ft)) {
                    f.setAccessible(true);
                    h.outerControllerField = f;
                    break;
                }
            }
            if (h.outerControllerField != null) {
                String ctrlDesc =
                        "L" + h.outerControllerField.getType().getName().replace('.', '/') + ";";
                for (DexSignatures.Body body :
                        DexSignatures.findInClass(
                                loader, ONGOING_DESC, Set.of("stopOuterGlowEffect already stopped"))) {
                    if (!ONGOING_DESC.equals(body.owner())) continue;
                    for (DexSignatures.Call call : body.calls()) {
                        if (ctrlDesc.equals(call.owner())
                                && call.parameters().isEmpty()
                                && "V".equals(call.result())) {
                            Method m = h.outerControllerField.getType().getDeclaredMethod(call.name());
                            m.setAccessible(true);
                            h.outerCtrlStop = m;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resolveGuidingLightHandles(ClassLoader loader, NativeHandles h) {
        try {
            Class<?> ongoing =
                    Class.forName(
                            "com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView",
                            false,
                            loader);
            Method getCfg = ongoing.getDeclaredMethod("getNudgeAutomationConfigByUiMode");
            getCfg.setAccessible(true);
            h.guidingGetConfig = getCfg;
            Class<?> cfgClass = getCfg.getReturnType();

            List<DexSignatures.Body> ctorMatches =
                    DexSignatures.find(loader, Set.of("Create effect, version: 2.2.1 config:"));
            Class<?> glClass = null;
            for (DexSignatures.Body body : ctorMatches) {
                String clsName = body.owner().substring(1, body.owner().length() - 1).replace('/', '.');
                glClass = Class.forName(clsName, false, loader);
                break;
            }
            if (glClass == null) return;
            h.guidingClass = glClass;

            Constructor<?> ctor = glClass.getDeclaredConstructor(Context.class, FrameLayout.class, cfgClass);
            ctor.setAccessible(true);
            h.guidingCtor = ctor;

            String glDesc = "L" + glClass.getName().replace('.', '/') + ";";
            for (DexSignatures.Body body :
                    DexSignatures.findInClass(
                            glClass, Set.of("Hide Guiding Light Effect: ", "release"))) {
                if (!glDesc.equals(body.owner())) continue;
                if (h.guidingHide == null
                        && body.anchors().containsKey("Hide Guiding Light Effect: ")
                        && body.parameters().size() == 1) {
                    Method m = body.method(loader);
                    m.setAccessible(true);
                    h.guidingHide = m;
                    Object[] constants = m.getParameterTypes()[0].getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if ("NONE".equals(String.valueOf(c))) {
                                h.guidingHideAnim = c;
                                break;
                            }
                        }
                        if (h.guidingHideAnim == null && constants.length > 0) {
                            h.guidingHideAnim = constants[0];
                        }
                    }
                } else if (h.guidingRelease == null
                        && body.anchors().containsKey("release")
                        && body.parameters().isEmpty()
                        && "V".equals(body.result())) {
                    Method m = body.method(loader);
                    m.setAccessible(true);
                    h.guidingRelease = m;
                }
            }

            for (Method m : glClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 1 && pt[0] == float.class) {
                    m.setAccessible(true);
                    h.guidingSetCornerRadius = m;
                } else if (pt.length == 2 && pt[0] == int.class && pt[1] == int.class) {
                    m.setAccessible(true);
                    h.guidingSetSize = m;
                } else if (pt.length == 1 && pt[0] == List.class) {
                    m.setAccessible(true);
                    h.guidingSetColors = m;
                } else if (pt.length == 1 && pt[0] == FrameLayout.class) {
                    m.setAccessible(true);
                    h.guidingRemoveView = m;
                } else if (pt.length == 1
                        && pt[0].isEnum()
                        && (h.guidingHide == null || !m.equals(h.guidingHide))) {
                    m.setAccessible(true);
                    h.guidingShow = m;
                    Object[] constants = pt[0].getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if ("NONE".equals(String.valueOf(c))) {
                                h.guidingShowAnim = c;
                                break;
                            }
                        }
                        if (h.guidingShowAnim == null && constants.length > 0) {
                            h.guidingShowAnim = constants[0];
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static View findAodView(View root, String idName) {
        if (root == null) return null;
        Resources res = root.getResources();
        int id = res.getIdentifier(idName, "id", PKG_AOD);
        if (id == 0) {
            id = res.getIdentifier(idName, "id", root.getContext().getPackageName());
        }
        return id == 0 ? null : root.findViewById(id);
    }

    private static int blendColor(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(clampByte(r), clampByte(g), clampByte(bl));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
