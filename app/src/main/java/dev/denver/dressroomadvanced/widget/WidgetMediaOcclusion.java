package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class WidgetMediaOcclusion {
    private static final Map<ViewGroup, Path> covers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> widgets =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record Local(Path source, Matrix inverse, Path path) {}

    private static final Map<View, Local> local =
            Collections.synchronizedMap(new WeakHashMap<>());

    static void register(View root) {
        widgets.put(root, true);
    }

    static void forget(View root) {
        widgets.remove(root);
        local.remove(root);
    }

    static Path source(View view) {
        if (view.getWindowToken() == null) return null;
        boolean registered = false;
        for (View parent = view;
                parent != null;
                parent = parent.getParent() instanceof View next ? next : null)
            if (widgets.containsKey(parent)) {
                registered = true;
                break;
            }
        if (!registered) return null;
        synchronized (covers) {
            for (Map.Entry<ViewGroup, Path> item : covers.entrySet())
                if (item.getKey().getWindowToken() == view.getWindowToken()) return item.getValue();
        }
        return null;
    }

    static boolean active(View view) {
        return source(view) != null;
    }

    static Path localPath(View view) {
        Path source = source(view);
        if (source == null) return null;
        Matrix global = new Matrix(), inverse = new Matrix();
        view.transformMatrixToGlobal(global);
        if (!global.invert(inverse)) return null;
        Local old = local.get(view);
        Path result;
        if (old != null && old.source == source && old.inverse.equals(inverse)) {
            result = old.path;
        } else {
            result = new Path();
            source.transform(inverse, result);
            local.put(view, new Local(source, new Matrix(inverse), result));
        }
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            RectF bounds = new RectF();
            result.computeBounds(bounds, true);
            if (result.isEmpty() || !bounds.intersects(0, 0, view.getWidth(), view.getHeight()))
                return null;
        }
        return result;
    }

    static void publish(ViewGroup root, Path cover) {
        if (cover == null) {
            if (covers.remove(root) == null) return;
        } else {
            Matrix global = new Matrix();
            root.transformMatrixToGlobal(global);
            Path screen = new Path();
            cover.transform(global, screen);
            covers.put(root, screen);
        }
        List<View> targets;
        synchronized (widgets) {
            targets = new ArrayList<>(widgets.keySet());
        }
        for (View widget : targets)
            if (widget != null
                    && widget.isAttachedToWindow()
                    && widget.getWindowToken() == root.getWindowToken()) {
                boolean depth = DepthEffects.hasVisibleSubject(widget);
                WidgetDepthBlur.clipWidgets(widget, depth);
                WidgetContentClip.refresh(widget);
                WidgetDepthBlur.watch(widget, depth || cover != null);
                WidgetDepthBlur.mediaChanged(widget);
            }
    }
}
