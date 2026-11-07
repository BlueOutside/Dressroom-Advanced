package dev.denver.dressroomadvanced;

import android.net.Uri;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;

final class OneUi9WidgetEditor {
    static final String TOOLBAR = "v8.y";
    static final String TOOLBAR_METHOD = "l";
    static final String GRID_WRAPPER = "pc.h";
    static final String PREVIEW = "op.m";
    static final String PREVIEW_METHOD = "b";
    static final String STORE = "sd.q";
    static final String ROTATION_TRIMMED = "rh.a";
    static final String LIFECYCLE = "cg.n";
    static final String LIFECYCLE_ARG = "gf.c";
    static final String ROTATION = "cg.v";
    static final String GUIDE = "sp.y1";
    static final String GRID_METRICS = "sd.h";
    static final String CELL_METRICS = "sd.o";
    static final String PLACEMENT = "sd.m";
    static final String ALIGN_FEEDBACK = "yc.b";
    static final String ADAPTER = "rp.e";
    static final String ADAPTER_METHOD = "b";
    static final String PICKER_SIZE = "sd.d";
    static final String MENU_ITEM = "k6.m";
    static final String PANEL_MENU = "k6.o";

    private OneUi9WidgetEditor() {}

    private static Class<?> entity(ClassLoader loader, String suffix)
            throws ClassNotFoundException {
        return Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity." + suffix,
                false,
                loader);
    }

    static Method toolbar(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> baseActivity =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasConstructor(cls, baseActivity, View.class),
                        TOOLBAR);
        return NativeContract.findMethod(owner, TOOLBAR_METHOD, void.class);
    }

    static Class<?> gridWrapper(ClassLoader loader) throws ClassNotFoundException {
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        return NativeContract.findClass(
                loader,
                cls -> !cls.isInterface() && NativeContract.hasMethod(cls, uiController),
                GRID_WRAPPER);
    }

    static Method getGrid(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        return NativeContract.findMethod(gridWrapper(loader), "d", uiController);
    }

    static Method providerAlign(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        return NativeContract.findMethod(gridWrapper(loader), "a", void.class, alignType);
    }

    static Method create(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, widgetData)
                                        && NativeContract.hasMethod(cls, View.class),
                        PREVIEW);
        return NativeContract.findMethod(owner, PREVIEW_METHOD, View.class);
    }

    static Method restore(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        Class<?> profileData = entity(loader, "profile.ProfileData");
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasField(cls, Uri.class)
                                        && NativeContract.hasMethod(
                                                cls, widgetData, profileData, uiMode),
                        STORE);
        return NativeContract.findMethod(owner, "b", widgetData, profileData, uiMode);
    }

    static Method rotationTrimmed(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.getName().startsWith("com.samsung.")
                                        && NativeContract.hasMethod(cls, alignType),
                        ROTATION_TRIMMED);
        return NativeContract.findMethod(owner, "i", alignType);
    }

    static Method lifecycle(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> argType =
                NativeContract.findClass(loader, Class::isEnum, LIFECYCLE_ARG);
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasMethod(cls, void.class, argType),
                        LIFECYCLE);
        return NativeContract.findMethod(owner, "c", void.class, argType);
    }

    static Method rotate(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasMethod(cls, boolean.class, alignType),
                        ROTATION);
        return NativeContract.findMethod(owner, "a", boolean.class, alignType);
    }

    static Method guide(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> dataBinding =
                Class.forName("androidx.databinding.ViewDataBinding", false, loader);
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                dataBinding.isAssignableFrom(cls)
                                        && NativeContract.hasMethod(cls, void.class),
                        GUIDE);
        return NativeContract.findMethod(owner, "executeBindings", void.class);
    }

    static Method gridMetrics(ClassLoader loader, Class<?> gridType, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, alignType),
                        GRID_METRICS);
        return NativeContract.findMethodPrefix(owner, "a", void.class, gridType, alignType);
    }

    static Method cellMetrics(ClassLoader loader, Class<?> gridType, Class<?> sizeType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, sizeType),
                        CELL_METRICS);
        return NativeContract.findMethodPrefix(owner, "a", void.class, gridType, sizeType);
    }

    static Method placement(ClassLoader loader, Class<?> widgetDataType, Class<?> widgetAssistType)
            throws ReflectiveOperationException {
        Class<?> clockAssist = entity(loader, "item.clock.ClockAssistData");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls,
                                                void.class,
                                                widgetDataType,
                                                widgetAssistType,
                                                clockAssist),
                        PLACEMENT);
        return NativeContract.findMethodPrefix(
                owner, "f", void.class, widgetDataType, widgetAssistType, clockAssist);
    }

    static Method alignFeedback(ClassLoader loader, Class<?> widgetDataType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        // The sub-clock repository shares this signature but
                                        // has no boolean state; require it to avoid binding it.
                                        && NativeContract.hasField(cls, boolean.class)
                                        && NativeContract.hasMethod(
                                                cls, void.class, widgetDataType),
                        ALIGN_FEEDBACK);
        return NativeContract.findMethod(owner, "c", void.class, widgetDataType);
    }

    static Class<?> adapter(ClassLoader loader) throws ClassNotFoundException {
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasStaticMethod(
                                cls,
                                void.class,
                                View.class,
                                int.class,
                                int.class,
                                float.class,
                                int.class,
                                int.class,
                                boolean.class),
                ADAPTER);
    }

    static Method pickerScope(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> deviceType = entity(loader, "system.DeviceType");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethod(
                                                cls, ArrayList.class, uiMode, deviceType),
                        PICKER_SIZE);
        return NativeContract.findMethod(owner, "a", ArrayList.class, uiMode, deviceType);
    }
}
