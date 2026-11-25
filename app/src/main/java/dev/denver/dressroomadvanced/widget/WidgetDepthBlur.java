package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.view.ViewParent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class WidgetDepthBlur {
    private static final String CONTAINER =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";

    private record Watch(
            java.lang.ref.WeakReference<ViewTreeObserver> observer,
            ViewTreeObserver.OnPreDrawListener listener) {}

    private static final Map<View, Watch> roots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, ViewGeometry> geometry =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Object> originals =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, java.lang.ref.WeakReference<View>> windowOwners =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> blurDisabled =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> verified =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<View> hidden =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<View> previewLayout =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<ClassLoader> deoptimized =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static boolean installed;
    private static boolean separateSurfaces;

    static void clipWidgets(View root, boolean depth) {
        if (separateSurfaces) WidgetDepthClip.apply(root, depth);
        else WidgetContentClip.apply(root, depth);
    }

    private WidgetDepthBlur() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        install(module, loader, false);
    }

    static synchronized void install(DressroomModule module, ClassLoader loader, boolean early) {
        UnlockBlur.installProvider(module, loader);
        AodWidgetAppearance.install(module, loader);
        AodWidgetBackground.install(module, loader);
        WidgetBackgroundOptions.install(module, loader);
        MediaWidgetFade.install(module, loader);
        try {
            if (ScreenIdentity.supported()) separateSurfaces = true;
            if (!installed) {
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                WidgetBlurInfo copies = new WidgetBlurInfo(info);
                Method submit = View.class.getDeclaredMethod("semSetBlurInfo", info);
                BlurAppearance.install(info, submit);
                if (module.inEditorProcess()) {
                    try {
                        EditorBlurCapture.install(module);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        module.report("Optional editor blur capture unavailable", error);
                    }
                }
                if (!early) {
                    try {
                        ClockBlurSync.install(module);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        module.report("Blur appearance draw synchronization unavailable", error);
                    }
                }
                try (HookTransaction hooks = new HookTransaction()) {
                    hooks.add(module.hook(submit)
                            .setId("dressroom-advanced.widget-depth-submit")
                            .intercept(
                                chain -> {
                                    View view = (View) chain.getThisObject();
                                    Object original = chain.getArg(0);
                                    if (EditorWidgetBlur.isCanvasRequest(original)) return chain.proceed();
                                    if (module.inEditorProcess()) EditorBlurCapture.clear(view);
                                    if (original == null && !NowBarGlass.replacing(view)
                                            && (MediaWidgetFade.holdClear(view)
                                            || UnlockBlur.holdContainerClear(view)))
                                        return null;
                                    boolean widget = isBackground(view);
                                    boolean clock = isClockBlurHost(view);
                                    if (early && clock) return chain.proceed();
                                    if (clock && OneUi85FullscreenArt.suppressClockBlur(view, original))
                                        return chain.proceed(new Object[] {null});
                                    String surface = BlurAppearance.surface(view, clock, widget);
                                    if (!widget) {
                                        NowBarGlass.nativeRequest(view, original);
                                        Object styled = BlurAppearance.submit(view, original, original, surface);
                                        if (clock) {
                                            // Keep the full-screen host measured while native blur is cleared.
                                            view.setVisibility(styled == null ? View.INVISIBLE : View.VISIBLE);
                                            if (styled != null && module.inEditorProcess()
                                                    && "lock_clock_blur_on_lockeditor".equals(view.getTag())) {
                                                try {
                                                    styled = BlurAppearance.editorCanvas(view, styled, true);
                                                } catch (ReflectiveOperationException | RuntimeException error) {
                                                    DressroomModule.note("Optional editor clock canvas blur", error);
                                                }
                                            }
                                        }
                                        Object result = styled == original ? chain.proceed()
                                                : chain.proceed(new Object[] {styled});
                                        if (clock && !module.inEditorProcess()
                                                && "clockBlur".equals(surface))
                                            MediaClockVisibility.blurSubmitted(view);
                                        BlurAppearance.tintSubmitted(view, styled, surface);
                                        NowBarGlass.submitted(view, original);
                                        DepthBlurHooks.clockBlurSubmitted(view, styled);
                                        UnlockBlur.submitted(view, styled != null, surface);
                                        return result;
                                    }
                                    if (original == null) originals.remove(view);
                                    else originals.put(view, original);
                                    Object submitted = original;
                                    try {
                                        Path subject =
                                                original == null || separateSurfaces
                                                        ? null
                                                        : DepthEffects.localSubjectPath(view);
                                        Path cover =
                                                original == null
                                                        ? null
                                                        : WidgetMediaOcclusion.localPath(view);
                                        if (original != null && copies.supportsPathClip()
                                                && (subject != null || cover != null)
                                                && view.getWidth() > 0
                                                && view.getHeight() > 0) {
                                            Path visible =
                                                    copies.visible(
                                                            original,
                                                            view.getWidth(),
                                                            view.getHeight(),
                                                            subject);
                                            if (cover != null)
                                                visible = DepthGeometry.subtract(visible, cover);
                                            if (visible != null) {
                                                submitted = copies.submission(original, visible);
                                                if (submitted == null && hidden.add(view))
                                                    android.util.Log.i(
                                                            DressroomModule.TAG,
                                                            "Fully covered complication blur"
                                                                    + " surface removed");
                                                if (verified.add(view))
                                                    android.util.Log.i(
                                                            DressroomModule.TAG,
                                                            "Depth applied at final complication"
                                                                    + " blur submission");
                                            }
                                        }
                                    } catch (Exception error) {
                                        module.report("Widget final blur clip unavailable", error);
                                    }
                                    if (blurOff(view)) submitted = null;
                                    submitted = BlurAppearance.submit(view, submitted, original, surface);
                                    if (submitted != null && module.inEditorProcess()
                                            && EditorWidgetBlur.canvasView(view)) {
                                        if (previewLayout.add(view))
                                            view.addOnLayoutChangeListener(
                                                    WidgetDepthBlur::resizeEditorBlur);
                                        if (view.getWidth() > 0 && view.getHeight() > 0) {
                                            try {
                                                submitted = BlurAppearance.editorCanvas(view, submitted, false);
                                            } catch (ReflectiveOperationException | RuntimeException error) {
                                                module.report("Optional editor widget canvas blur unavailable", error);
                                                submitted = null;
                                            }
                                        } else submitted = null;
                                    }
                                    Object result = submitted == original
                                            ? chain.proceed()
                                            : chain.proceed(new Object[] {submitted});
                                    BlurAppearance.tintSubmitted(view, submitted, surface);
                                    if (!module.inEditorProcess() && submitted != null
                                            && BlurAppearance.hasWindowDrawable(view))
                                        rememberWindowOwner(view);
                                    UnlockBlur.submitted(view, submitted != null, surface);
                                    return result;
                                })::unhook);
                    module.deoptimize(submit);
                    hooks.commit();
                    installed = true;
                }
                android.util.Log.i(
                        DressroomModule.TAG, "Final complication blur clipping installed");
            }
            if (!deoptimized.contains(loader)) {
                if (early) {
                    for (Method method : OneUi80WidgetRuntime.blurSubmissions(loader))
                        module.deoptimize(method);
                } else if (ClockBinding.of(
                                Class.forName(
                                        "com.samsung.android.uniform.widget.clock.AbsClockView",
                                        false,
                                        loader))
                        .legacy) {
                    for (Method method : OneUi85AodBlur.submissions(loader))
                        module.deoptimize(method);
                } else {
                    for (Method method : OneUi9AodBlur.submissions(loader))
                        module.deoptimize(method);
                }
                deoptimized.add(loader);
            }
        } catch (Exception | LinkageError error) {
            module.report("Complication blur hooks unavailable", error);
        }
    }

    private static void resizeEditorBlur(
            View view, int l, int t, int r, int b, int oldL, int oldT, int oldR, int oldB) {
        if (r - l == oldR - oldL && b - t == oldB - oldT) return;
        Object original = originals.get(view);
        if (original == null || r <= l || b <= t) return;
        try {
            Reflect.call(view, "semSetBlurInfo", original);
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Editor widget blur resize", error);
        }
    }

    static boolean isBackground(View view) {
        return "BlurView".equals(view.getTag())
                || (view.getParent() instanceof View parent
                        && parent.getClass().getName().equals(CONTAINER)
                        && !(view instanceof ViewGroup));
    }

    private static void rememberWindowOwner(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof View owner && owner.getClass().getName().equals(CONTAINER)) {
            var known = windowOwners.get(view);
            if (known == null || known.get() != owner)
                windowOwners.put(view, new java.lang.ref.WeakReference<>(owner));
        }
    }

    static java.util.List<View> windowSurfaces() {
        synchronized (windowOwners) {
            return new java.util.ArrayList<>(windowOwners.keySet());
        }
    }

    static void recoverWindowSurface(View view) throws ReflectiveOperationException {
        var reference = windowOwners.get(view);
        View owner = reference == null ? null : reference.get();
        if (owner == null || view.getParent() != owner || blurOff(view)
                || Reflect.call(owner, "getBlurView") != view
                || !Boolean.TRUE.equals(Reflect.call(owner, "getEnableBlur"))
                || Boolean.TRUE.equals(Reflect.field(owner, "isDozing"))
                || Boolean.TRUE.equals(Reflect.field(owner, "musicExpandState"))) return;
        float dark = ((Number) Reflect.field(owner, "darkAmount")).floatValue();
        if (dark != 0f) return;
        java.lang.reflect.Field alphaField;
        try {
            alphaField = NativeContract.field(owner.getClass(), "blurBackgroundAlpha", int.class);
        } catch (NoSuchFieldException absent) {
            alphaField = null;
        }
        int alpha = alphaField != null
                ? ((Number) alphaField.get(owner)).intValue()
                : (BlurAppearance.hasWindowDrawable(view) ? view.getBackground().getAlpha() : 0);
        if (alpha <= 0) return;
        // AOD can clear the old request. Ask the widget for its current preset.
        Reflect.call(owner, "refreshBlur", dark);
        if (BlurAppearance.hasWindowDrawable(view)) {
            if (alphaField != null) Reflect.call(owner, "setBlurBackgroundAlpha", alpha);
            else view.getBackground().setAlpha(alpha);
        }
    }

    static boolean isClockBlurHost(View view) {
        Object tag = view.getTag();
        if ("lock_clock_blur".equals(tag)
                || "lock_clock_blur_on_lockeditor".equals(tag)
                || "Clock_Blur_Container".equals(tag)
                || ClockInlineSeconds.BLUR_TAG.equals(tag)) {
            return true;
        }
        try {
            if (view.getId() != View.NO_ID) {
                String name = view.getResources().getResourceEntryName(view.getId());
                if ("lock_clock_blur".equals(name)
                        || "lock_clock_blur_on_lockeditor".equals(name)
                        || "Clock_Blur_Container".equals(name)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static void blur(View root, boolean off) {
        boolean previous = Boolean.TRUE.equals(blurDisabled.get(root));
        if (off) blurDisabled.put(root, true);
        else blurDisabled.remove(root);
        if (off != previous) resubmitBlur(root);
    }

    private static boolean blurOff(View view) {
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null)
            if (Boolean.TRUE.equals(blurDisabled.get(current))) return true;
        return false;
    }

    private static void resubmitBlur(View view) {
        Object original = originals.get(view);
        if (original != null)
            try {
                Reflect.call(view, "semSetBlurInfo", original);
            } catch (ReflectiveOperationException e) {
                android.util.Log.w(DressroomModule.TAG, "Widget blur restoration failed", e);
            }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) resubmitBlur(group.getChildAt(i));
        view.invalidate();
    }

    static void watch(View widgets, boolean enabled) {
        if (!enabled) {
            Watch watch = roots.remove(widgets);
            ViewTreeObserver oldObserver = watch == null ? null : watch.observer.get();
            if (oldObserver != null && oldObserver.isAlive())
                oldObserver.removeOnPreDrawListener(watch.listener);
            if (separateSurfaces) WidgetDepthClip.apply(widgets, false);
            refreshTree(widgets, true);
            geometry.remove(widgets);
            return;
        }
        ViewTreeObserver observer = widgets.getViewTreeObserver();
        Watch previous = roots.get(widgets);
        ViewTreeObserver oldObserver = previous == null ? null : previous.observer.get();
        if (oldObserver == observer && observer.isAlive()) return;
        if (oldObserver != null && oldObserver.isAlive())
            oldObserver.removeOnPreDrawListener(previous.listener);
        geometry.remove(widgets);
        java.lang.ref.WeakReference<View> reference = new java.lang.ref.WeakReference<>(widgets);
        ViewTreeObserver.OnPreDrawListener listener =
                () -> {
                    View root = reference.get();
                    if (root != null) {
                        if (separateSurfaces) {
                            clipWidgets(root, DepthEffects.hasVisibleSubject(root));
                        } else {
                            boolean outlineChanged = WidgetContentClip.apply(root, true);
                            if (geometryChanged(root) || outlineChanged) WidgetContentClip.refresh(root);
                        }
                        refreshTree(root, false);
                    }
                    return true;
                };
        roots.put(widgets, new Watch(new java.lang.ref.WeakReference<>(observer), listener));
        observer.addOnPreDrawListener(listener);
        widgets.invalidate();
    }

    static void afterPlacement(View root) {
        java.util.List<Map.Entry<View, Watch>> snapshot;
        synchronized (roots) {
            snapshot = new java.util.ArrayList<>(roots.entrySet());
        }
        for (var entry : snapshot) {
            View current = entry.getKey();
            while (current != null && current != root)
                current = current.getParent() instanceof View parent ? parent : null;
            if (current == null) continue;
            Watch watch = entry.getValue();
            ViewTreeObserver observer = watch.observer.get();
            if (observer != null && observer.isAlive()) {
                // Sample the root and nested blur surfaces after free placement,
                // otherwise this frame uses the previous widget location.
                observer.removeOnPreDrawListener(watch.listener);
                observer.addOnPreDrawListener(watch.listener);
            }
        }
    }

    static void mediaChanged(View root) {
        refreshTree(root, false);
    }

    private static void refreshTree(View view, boolean clear) {
        if (isBackground(view)) {
            boolean active =
                    !clear && (DepthEffects.hasVisibleSubject(view) || WidgetMediaOcclusion.active(view));
            boolean outlineChanged = WidgetContentClip.apply(view, active);
            Object original = originals.get(view);
            if (view.getWidth() > 0 && view.getHeight() > 0)
                try {
                    if (clear) {
                        if (geometry.remove(view) == null) return;
                    } else {
                        if (!geometryChanged(view) && !outlineChanged) return;
                        WidgetContentClip.refresh(view);
                    }
                    if (original != null) Reflect.call(view, "semSetBlurInfo", original);
                } catch (Exception error) {
                    android.util.Log.w(
                            DressroomModule.TAG, "Complication depth resubmit unavailable", error);
                }
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) refreshTree(group.getChildAt(i), clear);
    }

    private static boolean geometryChanged(View view) {
        ViewGeometry state;
        synchronized (geometry) {
            state = geometry.computeIfAbsent(view, key -> new ViewGeometry());
        }
        return state.update(
                view, DepthEffects.subjectIdentity(view), WidgetMediaOcclusion.source(view));
    }
}
