package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Clip final compositor submissions, including submissions during AOD transitions. */
final class WidgetDepthBlur {
    private static final String CONTAINER =
        "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";
    private record Watch(java.lang.ref.WeakReference<ViewTreeObserver> observer, ViewTreeObserver.OnPreDrawListener listener) {}
    private static final Map<View, Watch> roots = new WeakHashMap<>();
    private static final Map<View, ViewGeometry> geometry = new WeakHashMap<>();
    private static final Map<View, Object> originals = new WeakHashMap<>();
    private static final Map<View, Boolean> blurDisabled = new WeakHashMap<>();
    private static final Set<View> verified = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> hidden = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> deoptimized = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean installed;
    private WidgetDepthBlur() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        try {
            if (!installed) {
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                WidgetBlurInfo copies = new WidgetBlurInfo(info);
                Method submit = View.class.getDeclaredMethod("semSetBlurInfo", info);
                module.hook(submit).setId("dressroom-advanced.widget-depth-submit").intercept(chain -> {
                    View view = (View) chain.getThisObject();
                    if (!isBackground(view)) return chain.proceed();
                    Object original = chain.getArg(0);
                    if (original == null) originals.remove(view); else originals.put(view, original);
                    Object submitted = original;
                    try {
                        Path subject = original == null ? null : DepthEffects.localSubjectPath(view);
                        Path cover=original==null?null:WidgetMediaOcclusion.localPath(view);
                        if (original!=null && (subject != null || cover!=null) && view.getWidth() > 0 && view.getHeight() > 0) {
                            Path visible = copies.visible(original, view.getWidth(), view.getHeight(), subject);
                            if(cover!=null)visible=DepthGeometry.subtract(visible,cover);
                            if (visible != null) {
                                submitted = copies.submission(original, visible);
                                if (submitted == null && hidden.add(view)) android.util.Log.i(DressroomModule.TAG,
                                    "Fully covered complication blur surface removed");
                                if (verified.add(view)) android.util.Log.i(DressroomModule.TAG,
                                    "Depth applied at final complication blur submission");
                            }
                        }
                    } catch (Exception error) { module.report("Widget final blur clip unavailable", error); }
                    if (blurOff(view)) submitted = null;
                    return submitted == original ? chain.proceed() : chain.proceed(new Object[]{submitted});
                });
                module.deoptimize(submit);
                installed = true;
                android.util.Log.i(DressroomModule.TAG, "Final complication blur clipping installed");
            }
            if (deoptimized.add(loader)) {
                Class<?> utils = Class.forName("aod.e70", false, loader);
                module.deoptimize(utils.getDeclaredMethod("c", View.class, float.class, float[].class, Integer.class));
                module.deoptimize(utils.getDeclaredMethod("d", View.class, int.class, float.class, Integer.class, String.class));
            }
        } catch (Exception | LinkageError error) { module.report("Complication blur hooks unavailable", error); }
    }

    private static boolean isBackground(View view) {
        return "BlurView".equals(view.getTag()) || (view.getParent() instanceof View parent
            && parent.getClass().getName().equals(CONTAINER) && !(view instanceof ViewGroup));
    }

    static void blur(View root, boolean off) {
        boolean previous = Boolean.TRUE.equals(blurDisabled.get(root));
        if (off) blurDisabled.put(root, true); else blurDisabled.remove(root);
        if (off != previous) resubmitBlur(root);
    }
    private static boolean blurOff(View view) {
        for (View current = view; current != null; current = current.getParent() instanceof View parent ? parent : null)
            if (Boolean.TRUE.equals(blurDisabled.get(current))) return true;
        return false;
    }
    private static void resubmitBlur(View view) {
        Object original = originals.get(view);
        if (original != null) try { Reflect.call(view, "semSetBlurInfo", original); }
        catch (ReflectiveOperationException e) { android.util.Log.w(DressroomModule.TAG,"Widget blur restoration failed",e); }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) resubmitBlur(group.getChildAt(i));
        view.invalidate();
    }

    static void watch(View widgets, boolean enabled) {
        if (!enabled) {
            Watch watch = roots.remove(widgets);
            ViewTreeObserver oldObserver = watch == null ? null : watch.observer.get();
            if (oldObserver != null && oldObserver.isAlive()) oldObserver.removeOnPreDrawListener(watch.listener);
            refreshTree(widgets, true);
            geometry.remove(widgets);
            return;
        }
        ViewTreeObserver observer = widgets.getViewTreeObserver();
        Watch previous = roots.get(widgets);
        ViewTreeObserver oldObserver = previous == null ? null : previous.observer.get();
        if (oldObserver == observer && observer.isAlive()) return;
        if (oldObserver != null && oldObserver.isAlive()) oldObserver.removeOnPreDrawListener(previous.listener);
        geometry.remove(widgets);
        java.lang.ref.WeakReference<View> reference = new java.lang.ref.WeakReference<>(widgets);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            View root = reference.get();
            if (root != null) {
                boolean outlineChanged = WidgetContentClip.apply(root, true);
                if (geometryChanged(root) || outlineChanged) {
                    // Re-record the old group display list once its screen transform changes.
                    WidgetContentClip.refresh(root);
                }
                refreshTree(root, false);
            }
            return true;
        };
        roots.put(widgets, new Watch(new java.lang.ref.WeakReference<>(observer), listener));
        observer.addOnPreDrawListener(listener);
        widgets.invalidate();
    }
    static void mediaChanged(View root){refreshTree(root,false);}

    private static void refreshTree(View view, boolean clear) {
        if (isBackground(view)) {
            boolean active = !clear && (DepthEffects.hasSubject(view) || WidgetMediaOcclusion.active(view));
            // Samsung may replace the rounded outline while changing background modes.
            boolean outlineChanged = WidgetContentClip.apply(view, active);
            Object original = originals.get(view);
            if (view.getWidth() > 0 && view.getHeight() > 0) try {
                if (clear) {
                    if (geometry.remove(view) == null) return;
                } else {
                    if (!geometryChanged(view) && !outlineChanged) return;
                    WidgetContentClip.refresh(view);
                }
                // Resubmit the native original; the hook creates a fresh clip for this view.
                if (original != null) Reflect.call(view, "semSetBlurInfo", original);
            } catch (Exception error) {
                android.util.Log.w(DressroomModule.TAG, "Complication depth resubmit unavailable", error);
            }
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) refreshTree(group.getChildAt(i), clear);
    }

    private static boolean geometryChanged(View view) {
        ViewGeometry state=geometry.computeIfAbsent(view,key->new ViewGeometry());
        return state.update(view,DepthEffects.subjectIdentity(view),WidgetMediaOcclusion.source(view));
    }
}
