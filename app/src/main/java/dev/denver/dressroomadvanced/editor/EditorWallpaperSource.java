package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class EditorWallpaperSource {
    private static final String CROP_CLASS =
            "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView";
    private static final String EFFECT_PREFIX = "Effect_";
    private static final Map<View, WeakReference<View>> weather = new WeakHashMap<>();

    static boolean isCropView(View view) {
        return view != null && CROP_CLASS.equals(view.getClass().getName());
    }

    static boolean isEffectOverlay(View view) {
        return view != null
                && view.getTag() instanceof CharSequence tag
                && tag.toString().startsWith(EFFECT_PREFIX);
    }

    static View primaryCropView(View view) {
        if (view == null) return null;
        if (isCropView(view) && !isEffectOverlay(view)) return view;
        if (isEffectOverlay(view) && view.getParent() instanceof android.view.ViewGroup parent) {
            View best = null;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (!isCropView(child) || isEffectOverlay(child)) continue;
                if (best == null
                        || (long) child.getWidth() * child.getHeight()
                                > (long) best.getWidth() * best.getHeight()) {
                    best = child;
                }
            }
            return best;
        }
        return null;
    }

    static boolean hasVisibleEffectOverlay(View crop) {
        if (crop == null || !(crop.getParent() instanceof android.view.ViewGroup parent)) {
            return false;
        }
        if (!parent.isShown()) return false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != crop
                    && isEffectOverlay(child)
                    && child.isShown()
                    && (child.getWidth() > 0 || child.getHeight() > 0)) {
                return true;
            }
        }
        return false;
    }

    static void bind(View crop, View surface) {
        View primary = primaryCropView(crop);
        View target = primary != null ? primary : crop;
        if (target != null && surface != null) weather.put(target, new WeakReference<>(surface));
    }

    static boolean available(View crop) {
        if (crop == null) return false;
        if (crop.isShown()) return true;
        if (!crop.isAttachedToWindow()) return false;
        WeakReference<View> reference = weather.get(crop);
        View surface = reference == null ? null : reference.get();
        if (surface != null
                && surface.isShown()
                && crop.getWindowToken() == surface.getWindowToken()) {
            return true;
        }
        return hasVisibleEffectOverlay(crop);
    }

    static void reset() {
        weather.clear();
    }
}
