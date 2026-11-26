package dev.denver.dressroomadvanced;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class WidgetContentClip extends ViewOutlineProvider {
    private static final Map<View, WidgetContentClip> clips =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Field nativePath = outlinePathField();
    private ViewOutlineProvider original;
    private boolean originalClip;

    private WidgetContentClip(View view) {
        capture(view);
    }

    static boolean supported() {
        return android.os.Build.VERSION.SDK_INT >= 33;
    }

    private void capture(View view) {
        original = view.getOutlineProvider();
        originalClip = view.getClipToOutline();
    }

    static boolean apply(View view, boolean enabled) {
        enabled =
                ((enabled && !DepthEffects.wallpaperHidden(view))
                                || WidgetMediaOcclusion.active(view))
                        && supported();
        WidgetContentClip clip = clips.get(view);
        if (!enabled) {
            clips.remove(view);
            if (clip != null && view.getOutlineProvider() == clip) {
                view.setOutlineProvider(clip.original);
                view.setClipToOutline(clip.originalClip);
                view.invalidateOutline();
                view.invalidate();
            }
            return clip != null;
        }
        if (clip == null) {
            clip = new WidgetContentClip(view);
            clips.put(view, clip);
        } else if (view.getOutlineProvider() != clip) clip.capture(view);
        if (view.getOutlineProvider() != clip) {
            view.setOutlineProvider(clip);
            view.setClipToOutline(true);
            refresh(view);
            return true;
        }
        if (!view.getClipToOutline()) {
            view.setClipToOutline(true);
            refresh(view);
            return true;
        }
        return false;
    }

    static void refresh(View view) {
        view.invalidateOutline();
        view.invalidate();
    }

    @Override
    public void getOutline(View view, Outline outline) {
        Path subject = DepthEffects.localSubjectPath(view);
        Path cover = WidgetMediaOcclusion.localPath(view);
        if (subject == null && cover == null) {
            if (original != null) original.getOutline(view, outline);
            else outline.setEmpty();
            return;
        }
        Outline nativeOutline = new Outline();
        if (original != null) original.getOutline(view, nativeOutline);
        composeOutline(view, outline, original == null ? null : nativeOutline,
                originalClip, subject, cover);
    }

    static void composeOutline(View view, Outline outline, Outline nativeOutline,
            boolean nativeClip, Path subject, Path cover) {
        Path visible = nativeClip && nativeOutline != null ? copyShape(nativeOutline) : null;
        if (visible == null) {
            int left = 0, top = 0, right = view.getWidth(), bottom = view.getHeight();
            if (view instanceof android.view.ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child != null && child.getVisibility() == View.VISIBLE) {
                        int cx = Math.round(child.getX());
                        int cy = Math.round(child.getY());
                        left = Math.min(left, cx);
                        top = Math.min(top, cy);
                        right = Math.max(right, cx + child.getWidth());
                        bottom = Math.max(bottom, cy + child.getHeight());
                    }
                }
            }
            visible = new Path();
            visible.addRect(left, top, right, bottom, Path.Direction.CW);
        }
        if (subject != null) visible = DepthGeometry.subtract(visible, subject);
        if (cover != null) visible = DepthGeometry.subtract(visible, cover);
        DepthGeometry.outline(outline, visible);
        if (nativeOutline != null) outline.setAlpha(nativeOutline.getAlpha());
    }

    private static Field outlinePathField() {
        try {
            return NativeContract.findField(Outline.class, null, Path.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Path copyShape(Outline outline) {
        Rect rect = new Rect();
        if (outline.getRect(rect)) {
            Path path = new Path();
            float radius = Math.max(0, outline.getRadius());
            path.addRoundRect(new RectF(rect), radius, radius, Path.Direction.CW);
            return path;
        }
        if (nativePath != null)
            try {
                // Samsung's smooth corners are a path, even when all radii
                // match. Copy it before subtracting depth or the player cover.
                Object path = nativePath.get(outline);
                if (path instanceof Path shape && !shape.isEmpty()) return new Path(shape);
            } catch (IllegalAccessException ignored) {
            }
        return null;
    }
}
