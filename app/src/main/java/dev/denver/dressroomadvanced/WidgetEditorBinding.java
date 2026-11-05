package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class WidgetEditorBinding {
    static final String REPOSITORIES =
            "com.samsung.android.lockscreen.editor.item.data.repository.item.widget.";
    final Method toolbar, create, getGrid, metrics, position;
    private final Class<?> menuItem;

    WidgetEditorBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> preview = Class.forName("ib.l0", false, loader);
        create = NativeContract.method(preview, "d", View.class);
        NativeContract.field(
                preview,
                "I",
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData",
                        false,
                        loader));
        NativeContract.assignableField(preview, "J", ViewGroup.class);
        NativeContract.assignableField(preview, "N", View.class);
        NativeContract.field(
                Class.forName("X6.i", false, loader), "a", Class.forName("S6.e", false, loader));
        NativeContract.method(
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                        false,
                        loader),
                "dispatchTouchEvent",
                boolean.class,
                android.view.MotionEvent.class);
        Class.forName("lb.c", false, loader)
                .getDeclaredMethod(
                        "r",
                        View.class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        boolean.class);
        Class.forName("androidx.databinding.ViewDataBinding", false, loader)
                .getDeclaredMethod("getBinding", View.class);
        toolbar = NativeContract.method(Class.forName("V3.v", false, loader), "H", void.class);
        menuItem = Class.forName("T2.H", false, loader);
        menuItem.getDeclaredConstructor(String.class, int.class, Runnable.class, String.class);
        getGrid =
                NativeContract.method(
                        Class.forName("O5.i", false, loader),
                        "r",
                        Class.forName(
                                "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                                false,
                                loader));
        metrics =
                NativeContract.method(
                        Class.forName(REPOSITORIES + "K", false, loader),
                        "a",
                        void.class,
                        entity(loader, "item.widget.WidgetGridData"),
                        entity(loader, "item.widget.WidgetSizeData"),
                        boolean.class,
                        entity(loader, "system.PreviewScale"),
                        boolean.class,
                        entity(loader, "item.widget.WidgetAssistData"),
                        boolean.class,
                        entity(loader, "system.ScreenSize"),
                        entity(loader, "system.DeviceType"),
                        boolean.class,
                        entity(loader, "preview.UIMode"),
                        entity(loader, "item.common.ModuleRectInfo"));
        NativeContract.field(
                metrics.getDeclaringClass(), "d", Class.forName("kotlin.Lazy", false, loader));
        position =
                NativeContract.method(
                        Class.forName(REPOSITORIES + "z", false, loader),
                        "b",
                        void.class,
                        entity(loader, "item.widget.WidgetData"),
                        entity(loader, "item.widget.WidgetAssistData"),
                        entity(loader, "item.clock.ClockAssistData"),
                        entity(loader, "preview.UIMode"),
                        boolean.class,
                        entity(loader, "system.ScreenSize"),
                        boolean.class,
                        entity(loader, "item.common.ModuleRectInfo"));
    }

    private static Class<?> entity(ClassLoader loader, String suffix)
            throws ClassNotFoundException {
        return Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity." + suffix,
                false,
                loader);
    }

    Object data(Object owner) throws ReflectiveOperationException {
        return Reflect.call(repo(owner), "getData");
    }

    Object repo(Object owner) throws ReflectiveOperationException {
        return Reflect.field(Reflect.field(owner, "n"), "a");
    }

    Object grid(Object owner) throws ReflectiveOperationException {
        return getGrid.invoke(Reflect.call(Reflect.field(owner, "a"), "get"));
    }

    boolean main(Object owner) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(
                Reflect.call(
                        Reflect.call(Reflect.call(Reflect.field(owner, "c"), "b"), "getData"),
                        "isLockScreen"));
    }

    void recalculate(Object owner) throws ReflectiveOperationException {
        Reflect.call(Reflect.field(owner, "B"), "a");
    }

    void finalizeList(Object owner) throws ReflectiveOperationException {
        Reflect.call(owner, "b");
    }

    void addMenu(Object owner, Runnable open, String tag) throws ReflectiveOperationException {
        Object current = Reflect.field(owner, "f");
        if (current == null || Array.getLength(current) == 0) return;
        ArrayList<Object> items = new ArrayList<>();
        for (int i = 0; i < Array.getLength(current); i++) {
            Object item = Array.get(current, i);
            if (tag.equals(Reflect.call(item, "j"))) return;
            items.add(item);
        }
        Context context = (Context) Reflect.field(owner, "a");
        int icon =
                context.getResources()
                        .getIdentifier(
                                "preview_menu_icon_effect", "drawable", context.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        items.add(
                menuItem.getDeclaredConstructor(
                                String.class, int.class, Runnable.class, String.class)
                        .newInstance("Advanced", icon, open, tag));
        Object updated = Array.newInstance(menuItem, items.size());
        for (int i = 0; i < items.size(); i++) Array.set(updated, i, items.get(i));
        Reflect.set(owner, "f", updated);
    }

    static void metrics(Object size, Object grid, Object manager)
            throws ReflectiveOperationException {
        int columns = ((Number) Reflect.call(manager, "getColumn")).intValue();
        int rows = ((Number) Reflect.call(manager, "getRow")).intValue();
        int unit = (int) Math.ceil(((Number) Reflect.call(manager, "getUnitSize")).floatValue());
        int padding = ((Number) Reflect.call(manager, "getPadding")).intValue();
        float scale = ((Number) Reflect.call(manager, "getScaleFactor")).floatValue();
        Reflect.call(grid, "setCol", columns);
        Reflect.call(grid, "setRow", rows);
        Reflect.call(size, "setWidgetUnitSize", unit);
        Reflect.call(size, "setWidgetInnerSizePadding", padding);
        Reflect.call(size, "setWidgetSideMargin", padding);
        Reflect.call(size, "setScaleFactor", scale);
        Reflect.call(size, "setPreviewLayoutScale", scale);
        int width = columns * unit + (columns - 1) * padding * 2;
        int height = rows * unit + (rows - 1) * padding * 2;
        Reflect.call(size, "setContainerWidth", width);
        Reflect.call(size, "setContainerHeight", height);
        Reflect.call(size, "setDisableWidth", width);
        Reflect.call(size, "setDisableHeight", height);
        Reflect.call(size, "setDisableExpandWidth", width);
        float radius = ((Number) Reflect.call(manager, "radius", 1)).floatValue();
        Reflect.call(size, "setRadius", radius);
        Reflect.call(size, "setPreviewRadius", radius / scale);
    }
}
