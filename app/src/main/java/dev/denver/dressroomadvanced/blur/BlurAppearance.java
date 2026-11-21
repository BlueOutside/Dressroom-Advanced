package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Changes native blur parameters, never the backend, geometry or visibility policy. */
final class BlurAppearance {
    private record Submission(Object nativeInfo, String surface, int width, int height) {
        boolean fits(View view) {
            return width > 0 && height > 0 && view.getWidth() == width
                    && view.getHeight() == height && !view.isLayoutRequested();
        }
    }

    record ShadeSurface(java.lang.ref.WeakReference<View> view, Submission submission, int alpha) {}
    private record AwakePill(java.lang.ref.WeakReference<Object> item, Submission submission,
            int alpha) {}

    private static final Map<View, Submission> submissions = new WeakHashMap<>();
    private static final Map<View, AwakePill> awakePills = new WeakHashMap<>();
    private static final Map<Integer, Bundle> previews = new java.util.HashMap<>();
    private static final Map<Integer, Bundle> committed = new java.util.HashMap<>();
    private static final java.util.Set<String> changedSurfaces = new java.util.HashSet<>();
    private static final java.util.Set<View> clocks =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static Boolean samsungWindowBlur;
    private static WidgetBlurInfo copies;
    private static Method setter;
    private static Field radius, color, curve, mode;
    private static Field curveSaturation;
    private static Constructor<?> curveConstructor;
    private static Field[] curveFields;
    private static boolean refreshPending, reported;

    private BlurAppearance() {}

    static void install(Class<?> info, Method submit) {
        if (setter != null) return;
        try {
            WidgetBlurInfo copy = new WidgetBlurInfo(info);
            Field r = field(info, "mBlurRadius"), c = field(info, "mBackgroundBlurColor");
            Field cv = field(info, "mColorCurve"), m = field(info, "mBlurMode");
            Class<?> type = cv.getType();
            Field saturation;
            try {
                saturation = field(type, "mSaturation");
            } catch (NoSuchFieldException ignored) {
                saturation = null;
            }
            Constructor<?> constructor =
                    type.getConstructor(
                            float.class,
                            float.class,
                            float.class,
                            float.class,
                            float.class,
                            float.class);
            Field[] fields = new Field[5];
            String[] names = {"mCurveBias", "mMinX", "mMaxX", "mMinY", "mMaxY"};
            for (int i = 0; i < fields.length; i++) fields[i] = field(type, names[i]);
            copies = copy;
            radius = r;
            color = c;
            curve = cv;
            mode = m;
            curveConstructor = constructor;
            curveFields = fields;
            curveSaturation = saturation;
            setter = submit;
            BlurTintFade.install();
            RuntimeExtras.listen(BlurAppearance::committedChanged);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            setter = null;
            android.util.Log.w(DressroomModule.TAG, "Blur appearance unavailable", error);
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static boolean available(Context context, String surface) {
        if (setter == null || reducedTransparency(context) || systemBlurDisabled(context)) return false;
        if (windowBlur(context)) return true;
        // A provider can already be using canvas, captured blur, or a native
        // drawable even when the advertised window-blur capability is absent.
        for (Map.Entry<View, Submission> item : new ArrayList<>(submissions.entrySet())) {
            Submission s = item.getValue();
            View view = item.getKey();
            if (s.surface.equals(surface) && view != null
                    && view.isAttachedToWindow() && view.isHardwareAccelerated())
                try {
                    int backend = mode.getInt(s.nativeInfo);
                    if (backend == 1 || backend == 2 || hasWindowDrawable(view)) return true;
                } catch (ReflectiveOperationException ignored) {
                }
        }
        return false;
    }

    private static boolean reducedTransparency(Context context) {
        return android.provider.Settings.System.getInt(
                        context.getContentResolver(), "accessibility_reduce_transparency", 0)
                != 0;
    }

    static boolean windowBlur(Context context) {
        WindowManager manager = context.getSystemService(WindowManager.class);
        return permitsWindowBlur(
                manager != null && manager.isCrossWindowBlurEnabled(), samsungWindowBlur(),
                systemBlurDisabled(context));
    }

    private static boolean systemBlurDisabled(Context context) {
        return android.provider.Settings.Global.getInt(
                context.getContentResolver(), "disable_window_blurs", 0) != 0;
    }

    static boolean hasWindowDrawable(View view) {
        var background = view.getBackground();
        return background != null && background.getClass().getName().equals(
                "com.android.internal.graphics.drawable.BackgroundBlurDrawable");
    }

    static boolean visibleWindowRequest(Object request) throws ReflectiveOperationException {
        if (request == null || mode == null || mode.getInt(request) != 0
                || radius.getInt(request) <= 0) return false;
        var path = copies.path(request);
        return path == null || !path.isEmpty();
    }

    static boolean permitsWindowBlur(boolean androidBlur, boolean samsungBlur, boolean disabled) {
        return !disabled && (androidBlur || samsungBlur);
    }

    static boolean liveWindowBlurAllowed(Context context) {
        return !reducedTransparency(context) && windowBlur(context);
    }

    private static boolean samsungWindowBlur() {
        if (samsungWindowBlur == null) {
            try {
                samsungWindowBlur = Class.forName("com.samsung.android.rune.CoreRune")
                        .getField("FW_WINDOW_BLUR_SUPPORTED").getBoolean(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                samsungWindowBlur = featureBoolean(
                        "SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_3D_SURFACE_TRANSITION_FLAG");
            }
        }
        return samsungWindowBlur;
    }

    private static boolean featureBoolean(String key) {
        try {
            Class<?> feature = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object instance = feature.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(
                    feature.getMethod("getBoolean", String.class).invoke(instance, key));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static Object nativeRequest(View view) {
        Submission value = submissions.get(view);
        return value == null ? null : value.nativeInfo;
    }

    static void submitClockRequest(View view, Object request) {
        if (setter == null) return;
        try {
            setter.invoke(view, request);
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("8.5 clock blur restore unavailable", error);
        }
    }

    static Object submit(View view, Object submitted, Object nativeInfo, String surface) {
        if (setter == null) return submitted;
        try {
            if (surface == null) {
                submissions.remove(view);
                return submitted;
            }
            if (nativeInfo == null) submissions.remove(view);
            else {
                Submission previous = submissions.get(view);
                int width = view.getWidth(), height = view.getHeight();
                if (previous == null || previous.nativeInfo != nativeInfo
                        || !previous.surface.equals(surface)
                        || previous.width != width || previous.height != height) {
                    submissions.put(view, new Submission(nativeInfo, surface, width, height));
                }
            }
            if (submitted == null || reducedTransparency(view.getContext())
                    || systemBlurDisabled(view.getContext())) return submitted;
            int backend = mode.getInt(submitted);
            if (backend == 0 && !windowBlur(view.getContext()) && !hasWindowDrawable(view))
                return submitted;
            int screen = ScreenIdentity.current();
            Bundle b = previews.get(screen);
            if (b == null) {
                b = RuntimeExtras.get(view.getContext());
                if (!committed.containsKey(screen)) committed.put(screen, new Bundle(b));
            }
            String profile = surface;
            if (surface.equals("widgetBlur") && backend == 0
                    && b.getBoolean(AodWidgetAppearance.PROFILE + "Custom")
                    && AodWidgetAppearance.applies(view))
                profile = AodWidgetAppearance.PROFILE;
            boolean custom = b.getBoolean(profile + "Custom");
            Object input = submitted;
            if (backend == 0 && surface.equals("nowBarBlur")) {
                Object preset = NowBarGlass.glassPreset(view, submitted);
                if (preset != null && radius.getInt(preset) > 0) {
                    input = copies.copy(input);
                    color.setInt(input, 0);
                    radius.setInt(input, radius.getInt(preset));
                    curve.set(input, curve.get(preset));
                }
            }
            Object result = custom ? styled(input, b, profile) : input;
            boolean neutral = profile.equals("clockBlur")
                    && b.getBoolean(BlurAppearanceSettings.NEUTRAL_CLOCK);
            neutral |= profile.equals(AodWidgetAppearance.PROFILE)
                    && b.getBoolean(AodWidgetAppearance.NEUTRAL);
            if (neutral) {
                if (!custom) result = copies.copy(submitted);
                float saturation = custom ? b.getInt(profile + "Saturation") / 100f : 0f;
                curve.set(result, curveConstructor.newInstance(saturation, 0f, 0f, 255f, 0f, 255f));
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!reported) {
                reported = true;
                android.util.Log.w(DressroomModule.TAG, "Keeping native blur appearance", error);
            }
            return submitted;
        }
    }

    static void tintSubmitted(View view, Object request, String surface) {
        if (setter == null || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return;
        try {
            if (("widgetBlur".equals(surface) || "nowBarBlur".equals(surface))
                    && request != null && mode.getInt(request) == 0)
                BlurTintFade.submitted(view, color.getInt(request));
            else BlurTintFade.clear(view);
        } catch (IllegalAccessException | RuntimeException error) {
            BlurTintFade.clear(view);
            DressroomModule.note("Window blur tint submission", error);
        }
    }


    static Object styled(Object original, Bundle b, String surface)
            throws ReflectiveOperationException {
        Object result = copies.copy(original);
        radius.setInt(result, b.getInt(surface + "Radius", 200));
        int adjustment = b.getInt(surface + "Saturation");
        if (adjustment != 0) {
            Object oldCurve = curve.get(original);
            float[] values = {0, 0, 255, 0, 255};
            if (oldCurve != null)
                for (int i = 0; i < values.length; i++) values[i] = curveFields[i].getFloat(oldCurve);
            float baseline =
                    oldCurve != null && curveSaturation != null
                            ? curveSaturation.getFloat(oldCurve) : 0f;
            float saturation = Math.max(-1f, Math.min(1f, baseline + adjustment / 100f));
            curve.set(
                    result,
                    curveConstructor.newInstance(
                            saturation, values[0], values[1], values[2], values[3], values[4]));
        }
        int tint = b.getInt(surface + "Tint");
        // Only window blur accepts a background tint; canvas/captured modes reject it.
        if (tint != 0 && mode.getInt(original) == 0) {
            int alpha = tint == 3 ? 0 : Math.round(b.getInt(surface + "TintOpacity", 20) * 2.55f);
            color.setInt(result, (alpha << 24) | (tint == 2 ? 0x00ffffff : 0));
        }
        return result;
    }

    static Object editorCanvas(View view, Object info, boolean clock) throws ReflectiveOperationException {
        if (!copies.supportsPathClip() || mode.getInt(info) != 0) return info;
        var clip = clock ? copies.path(info) : copies.visible(info, view.getWidth(), view.getHeight(), null);
        if (clip == null || clip.isEmpty() || !EditorBlurCapture.prepare(view, clock)) return info;
        Object canvas = copies.clipped(info, clip);
        mode.setInt(canvas, 2);
        EditorWidgetBlur.canvasRequest(canvas);
        return canvas;
    }

    /** Walks ancestors to find the owning Now Bar card View, or null if none is attached yet. */
    static View nowBarCardOwner(View view) {
        for (View parent = view;
                parent != null;
                parent = parent.getParent() instanceof View next ? next : null) {
            if (isNowBarCardType(parent.getClass())) return parent;
        }
        return null;
    }

    static boolean isNowBarCard(View view) {
        if (nowBarCardOwner(view) != null) return true;
        return view != null && !view.isAttachedToWindow() && NowBriefFill.isBriefBlurView(view);
    }

    static boolean isNowBarCardType(Class<?> viewType) {
        for (Class<?> type = viewType; type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ((name.startsWith("com.samsung.android.app.facewidget.presentation.NowBar")
                            || name.startsWith(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBar"))
                    && name.endsWith("CardView")) return true;
        }
        return false;
    }

    private static String surface(View view) {
        return surface(view, WidgetDepthBlur.isClockBlurHost(view), WidgetDepthBlur.isBackground(view));
    }

    static String surface(View view, boolean clock, boolean widget) {
        if (view instanceof OneUi85FullscreenArt.BannerBlur) return null;
        if (clock) return "clockBlur";
        if (widget) return "widgetBlur";
        return isNowBarCard(view) ? "nowBarBlur" : null;
    }

    static void preview(Bundle b) {
        Bundle previous = previews.put(ScreenIdentity.value(b), new Bundle(b));
        if (previous != null && BlurAppearanceSettings.same(previous, b)) return;
        Bundle old = previous != null ? previous : committed.get(ScreenIdentity.value(b));
        markChanges(old, b);
        refresh();
    }

    static void clearPreview() {
        if (previews.isEmpty()) return;
        for (var preview : previews.entrySet())
            markChanges(preview.getValue(), committed.get(preview.getKey()));
        previews.clear();
        refresh();
    }

    static void trackClock(View clock) {
        clocks.add(clock);
    }

    static boolean customClockRadius(Context context) {
        Bundle settings = previews.get(ScreenIdentity.current());
        if (settings == null) settings = RuntimeExtras.get(context);
        return settings.getBoolean("clockBlurCustom") && available(context, "clockBlur");
    }

    private static void committedChanged() {
        for (View view : new ArrayList<>(submissions.keySet())) {
            if (view == null || !view.isAttachedToWindow()) continue;
            Bundle next = RuntimeExtras.get(view.getContext());
            Bundle previous = committed.put(ScreenIdentity.current(), new Bundle(next));
            if (previous == null || !BlurAppearanceSettings.same(previous, next)) {
                markChanges(previous, next);
                refresh();
            }
            return;
        }
    }

    static boolean canReplay(String surface) {
        return !"clockBlur".equals(surface);
    }

    private static void markChanges(Bundle old, Bundle next) {
        Bundle before = old == null ? RuntimeExtras.defaults() : old;
        Bundle after = next == null ? RuntimeExtras.defaults() : next;
        for (String surface : BlurAppearanceSettings.SURFACES) {
            if (!BlurAppearanceSettings.same(before, after, surface))
                changedSurfaces.add(surface);
        }
    }

    static void clearWindowSurface(View view) {
        if (setter == null) return;
        try {
            setter.invoke(view, (Object) null);
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Unlock blur surface clear", error);
        }
    }

    static java.util.List<View> visibleWindowSurfaces() {
        var visible = new ArrayList<View>();
        for (var entry : submissions.entrySet()) {
            View view = entry.getKey();
            if (view != null && view.isAttachedToWindow() && view.isShown()
                    && view.getWidth() > 0 && view.getHeight() > 0
                    && canReplay(entry.getValue().surface)
                    && entry.getValue().surface.equals(surface(view)))
                visible.add(view);
        }
        return visible;
    }

    static void recreateWindowSurface(View view, android.graphics.drawable.Drawable background)
            throws ReflectiveOperationException {
        Submission current = submissions.get(view);
        if (setter == null || current == null || !canReplay(current.surface)
                || mode.getInt(current.nativeInfo) != 0
                || view.getBackground() != background
                || !current.surface.equals(surface(view))
                || !liveWindowBlurAllowed(view.getContext())) return;
        int alpha = background.getAlpha();
        // Re-submit through the normal hook so widget depth and appearance are reapplied.
        // Removing the drawable makes Samsung allocate a RenderNode with a fresh position.
        view.setBackground(null);
        try {
            setter.invoke(view, current.nativeInfo);
            var replacement = view.getBackground();
            if (replacement == null) view.setBackground(background);
            else replacement.setAlpha(alpha);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (view.getBackground() == null) view.setBackground(background);
            throw error;
        }
    }

    static boolean hasLiveWindowRequest(View view) throws IllegalAccessException {
        Submission current = submissions.get(view);
        return setter != null && current != null && canReplay(current.surface)
                && mode.getInt(current.nativeInfo) == 0
                && current.surface.equals(surface(view));
    }

    static void restoreMissingWindowSurface(View view) throws ReflectiveOperationException {
        if (view.getBackground() == null && hasLiveWindowRequest(view))
            setter.invoke(view, submissions.get(view).nativeInfo);
    }

    static void refreshNativeNowBar(View view) throws ReflectiveOperationException {
        Submission current = submissions.get(view);
        if (current != null && current.surface.equals("nowBarBlur") && hasLiveWindowRequest(view))
            setter.invoke(view, current.nativeInfo);
    }

    static void forgetNowBarRequest(View view) {
        Submission current = submissions.get(view);
        if (current != null && current.surface.equals("nowBarBlur")) submissions.remove(view);
    }

    static void captureAwakePills() {
        awakePills.clear();
        for (var entry : submissions.entrySet()) {
            View view = entry.getKey();
            Submission saved = entry.getValue();
            if (view == null || !view.isShown() || !saved.surface.equals("nowBarBlur")
                    || !hasWindowDrawable(view) || NowBarGlass.ownsRequest(view, saved.nativeInfo))
                continue;
            try {
                View card = nowBarCardOwner(view);
                Object item = card == null ? null : Reflect.call(card, "getItem");
                int alpha = view.getBackground().getAlpha();
                if (item != null && alpha > 0 && visibleWindowRequest(saved.nativeInfo))
                    awakePills.put(view, new AwakePill(new java.lang.ref.WeakReference<>(item),
                            saved, alpha));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Cards without stable item identity can still replay their current request.
            }
        }
    }

    static void rebindAwakePills() {
        var candidates = new java.util.LinkedHashSet<>(submissions.keySet());
        candidates.addAll(awakePills.keySet());
        try {
            for (View view : candidates) rebindAwakePill(view);
        } finally {
            awakePills.clear();
        }
    }

    private static void rebindAwakePill(View view) {
        if (setter == null || view == null || !view.isAttachedToWindow() || !view.isShown()
                || !view.isHardwareAccelerated() || view.getWidth() <= 0 || view.getHeight() <= 0)
            return;
        var display = view.getDisplay();
        if (display == null || display.getState() != android.view.Display.STATE_ON) return;
        Submission current = submissions.get(view);
        if (current != null && !current.surface.equals("nowBarBlur")) return;
        View card = nowBarCardOwner(view);
        if (card == null || !liveWindowBlurAllowed(view.getContext())) return;
        try {
            AwakePill awake = awakePills.get(view);
            Object request = current == null ? null : current.nativeInfo;
            var previous = view.getBackground();
            int opacity = hasWindowDrawable(view) && previous.getAlpha() > 0 ? previous.getAlpha() : 255;
            boolean restoredAwake = false;
            if (awake != null && awake.submission.fits(view) && awake.item.get() != null
                    && awake.item.get() == Reflect.call(card, "getItem")
                    && (request == null || staleDozeTint(
                            color.getInt(request), color.getInt(awake.submission.nativeInfo)))) {
                request = awake.submission.nativeInfo;
                opacity = awake.alpha;
                restoredAwake = true;
            }
            if (!restoredAwake && (!(Reflect.call(card, "getDarkAmount") instanceof Number dark)
                    || dark.floatValue() > .01f)) return;
            if (!visibleWindowRequest(request)) return;
            BlurTintFade.clear(view);
            // Reuse the native request but allocate a fresh blur region after LTPO resumes.
            if (hasWindowDrawable(view)) view.setBackground(null);
            try {
                setter.invoke(view, request);
                if (hasWindowDrawable(view)) {
                    view.getBackground().setAlpha(opacity);
                    view.invalidate();
                }
            } finally {
                if (view.getBackground() == null && previous != null) view.setBackground(previous);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            DressroomModule.note("Now Bar blur wake handoff", unavailable);
        }
    }

    static boolean staleDozeTint(int current, int awake) {
        return (current & 0x00ffffff) == 0 && (current >>> 24) >= 0xa0
                && (current >>> 24) > (awake >>> 24);
    }

    /** Re-submits an earlier GenUI blur request once the view attaches to its window tree. */
    static void recheckDetachedGenUi(View view) {
        Submission current = submissions.get(view);
        if (setter == null || current == null) return;
        try {
            setter.invoke(view, current.nativeInfo);
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("GenUI blur attach refresh", error);
        }
    }

    static java.util.List<ShadeSurface> captureShadeSurfaces(View root) {
        var visible = new ArrayList<ShadeSurface>();
        for (var entry : submissions.entrySet()) {
            View view = entry.getKey();
            Submission submission = entry.getValue();
            if (view == null || view.getRootView() != root || !view.isShown()
                    || !canReplay(submission.surface)) continue;
            var background = view.getBackground();
            if (background != null && background.getAlpha() > 0)
                visible.add(new ShadeSurface(new java.lang.ref.WeakReference<>(view),
                        submission, background.getAlpha()));
        }
        return visible;
    }

    static void restoreShadeSurfaces(View root, java.util.List<ShadeSurface> captured) {
        if (setter == null) return;
        for (ShadeSurface saved : captured) {
            View view = saved.view.get();
            if (view == null || !view.isAttachedToWindow() || !view.isShown()
                    || view.getRootView() != root || view.getAlpha() <= 0
                    || !saved.submission.surface.equals(surface(view))
                    || reducedTransparency(view.getContext())) continue;
            Submission current = submissions.get(view);
            var background = view.getBackground();
            if (current != null && background != null && background.getAlpha() > 0) continue;
            try {
                setter.invoke(view, current == null ? saved.submission.nativeInfo : current.nativeInfo);
                background = view.getBackground();
                if (background != null) background.setAlpha(saved.alpha);
            } catch (ReflectiveOperationException | RuntimeException error) {
                DressroomModule.note("Lockscreen container blur after shade", error);
            }
        }
    }

    private static void refresh() {
        if (refreshPending || changedSurfaces.isEmpty()) return;
        refreshPending = true;
        android.view.Choreographer.getInstance().postFrameCallback(
                frame -> {
                    refreshPending = false;
                    var changed = new java.util.HashSet<>(changedSurfaces);
                    changedSurfaces.clear();
                    var updatedClockRoots = new java.util.HashSet<View>();
                    // Resubmit the untouched input through the usual depth/occlusion hooks.
                    for (Map.Entry<View, Submission> item :
                            new ArrayList<>(submissions.entrySet())) {
                        View view = item.getKey();
                        if (view == null || !view.isAttachedToWindow()) continue;
                        Submission submission = item.getValue();
                        boolean aodProfile = changed.contains(AodWidgetAppearance.PROFILE)
                                && submission.surface.equals("widgetBlur");
                        if (!aodProfile && !changed.contains(submission.surface)) continue;
                        if (!submission.surface.equals(surface(view))) {
                            submissions.remove(view);
                            continue;
                        }
                        boolean editorClock = !canReplay(submission.surface);
                        // Radius and color changes can reuse the editor's current glyph clip.
                        // Runtime transitions still rebuild from current geometry.
                        if (editorClock
                                && (!"com.samsung.android.app.dressroom".equals(
                                                view.getContext().getOpPackageName())
                                        || !view.isShown()
                                        || !submission.fits(view))) continue;
                        try {
                            if (aodProfile && AodWidgetAppearance.refresh(view)) continue;
                            if (!changed.contains(submission.surface)) continue;
                            setter.invoke(view, submission.nativeInfo);
                            if (editorClock) updatedClockRoots.add(view.getRootView());
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            android.util.Log.w(
                                    DressroomModule.TAG, "Blur appearance refresh failed", error);
                        }
                    }
                    if (changed.contains("clockBlur")) {
                        for (View clock : new ArrayList<>(clocks)) {
                            if (!clock.isAttachedToWindow() || !clock.isShown()) continue;
                            if (updatedClockRoots.contains(clock.getRootView())) continue;
                            if (ClockBlurSync.pending(clock)) continue;
                            ClockBlurSync.defer(
                                    clock,
                                    target -> {
                                        if (!target.isAttachedToWindow() || !target.isShown())
                                            return;
                                        try {
                                            Reflect.call(target, "setClockTextBlur");
                                        } catch (ReflectiveOperationException error) {
                                            android.util.Log.w(
                                                    DressroomModule.TAG,
                                                    "Clock blur appearance refresh failed",
                                                    error);
                                        }
                                    });
                        }
                    }
                });
    }
}
