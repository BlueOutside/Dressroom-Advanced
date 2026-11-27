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

    private static final ClassValue<java.util.Optional<OneUi95Widgets.Toolbar>> betaToolbars =
            new ClassValue<>() {
                @Override
                protected java.util.Optional<OneUi95Widgets.Toolbar> computeValue(Class<?> owner) {
                    try {
                        return java.util.Optional.ofNullable(OneUi95Widgets.toolbar(owner));
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Beta toolbar ABI unavailable", error);
                    }
                }
            };

    private record Preview(java.lang.reflect.Field root, java.lang.reflect.Field inner,
            java.lang.reflect.Field data) {}
    private static final ClassValue<Preview> previews = new ClassValue<>() {
        @Override protected Preview computeValue(Class<?> owner) {
            try {
                Class<?> data = entity(owner.getClassLoader(), "item.widget.WidgetData");
                java.lang.reflect.Field root = resolvePreviewRoot(owner);
                java.lang.reflect.Field inner;
                try { inner = NativeContract.assignableField(owner, "H", android.widget.FrameLayout.class); }
                catch (NoSuchFieldException changed) { inner = NativeContract.findField(owner, "G", android.widget.FrameLayout.class); }
                return new Preview(root, inner, NativeContract.findField(owner, "G", data));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported widget preview ownership", error);
            }
        }
    };

    static java.lang.reflect.Field resolvePreviewRoot(Class<?> owner)
            throws ReflectiveOperationException {
        try {
            Method create = NativeContract.findMethod(owner, PREVIEW_METHOD, View.class);
            DexSignatures.Body body = DexSignatures.body(create);
            String ownerDesc = DexSignatures.descriptor(owner);
            try {
                return body.uniqueWrite(ownerDesc, "Landroid/view/View;", null)
                        .field(owner.getClassLoader());
            } catch (NoSuchFieldException multipleWrites) {
                return body.uniqueRead(ownerDesc, "Landroid/view/View;", null)
                        .field(owner.getClassLoader());
            }
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError noDex) {
        }
        try { return NativeContract.assignableField(owner, "L", View.class); }
        catch (NoSuchFieldException ignored) {}
        try { return NativeContract.assignableField(owner, "N", View.class); }
        catch (NoSuchFieldException ignored) {}
        return NativeContract.findField(owner, "K", View.class);
    }
    static View root(Object owner) throws IllegalAccessException { return (View) previews.get(owner.getClass()).root.get(owner); }
    static View inner(Object owner) throws IllegalAccessException { return (View) previews.get(owner.getClass()).inner.get(owner); }
    static Object data(Object owner) throws IllegalAccessException { return previews.get(owner.getClass()).data.get(owner); }


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
        var beta = betaToolbars.get(owner);
        return beta.isPresent() ? beta.get().render()
                : NativeContract.findMethod(owner, TOOLBAR_METHOD, void.class);
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
                        PREVIEW, "lp.l");
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
                        STORE, "sd.p");
        return NativeContract.findMethod(owner, "b", widgetData, profileData, uiMode);
    }

    static Method portraitPolicy(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = NativeContract.findClass(loader,
                cls -> cls.isInterface() && NativeContract.hasMethod(cls, int.class)
                        && NativeContract.hasMethod(cls, void.class, int.class)
                        && NativeContract.hasMethod(cls, boolean.class), "df.b");
        return NativeContract.findMethod(owner, "a", boolean.class);
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
        try {
            return referenceLifecycle(loader);
        } catch (ClassNotFoundException | NoSuchMethodException changed) {
            return OneUi95Widgets.lifecycle(loader);
        }
    }

    private static Method referenceLifecycle(ClassLoader loader) throws ReflectiveOperationException {
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
                        ROTATION, "cg.u");
        return NativeContract.findMethod(owner, "a", boolean.class, alignType);
    }

    static Method guide(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> dataBinding = Class.forName("androidx.databinding.ViewDataBinding", false, loader);
        // A generic binding/no-arg shape matches unrelated panels. Unknown guide owners
        // must not hide an arbitrary editor view or prevent the widget controls loading.
        Class<?> owner = NativeContract.knownClass(loader,
                cls -> dataBinding.isAssignableFrom(cls)
                        && NativeContract.hasMethod(cls, void.class), GUIDE);
        if (owner == null) return OneUi95Widgets.rotationGuide(loader);
        return NativeContract.findMethod(owner, "executeBindings", void.class);
    }

    static Method gridMetrics(ClassLoader loader, Class<?> gridType, Class<?> alignType)
            throws ReflectiveOperationException {
        try {
            return referenceGridMetrics(loader, gridType, alignType);
        } catch (ClassNotFoundException | NoSuchMethodException changed) {
            return OneUi95Widgets.gridMetrics(loader, gridType, alignType);
        }
    }

    private static Method referenceGridMetrics(ClassLoader loader, Class<?> gridType, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, alignType),
                        GRID_METRICS, "sd.g");
        return NativeContract.findMethodPrefix(owner, "a", void.class, gridType, alignType);
    }

    static Method cellMetrics(ClassLoader loader, Class<?> gridType, Class<?> sizeType)
            throws ReflectiveOperationException {
        try {
            return referenceCellMetrics(loader, gridType, sizeType);
        } catch (ClassNotFoundException | NoSuchMethodException changed) {
            return OneUi95Widgets.cellMetrics(loader, gridType, sizeType);
        }
    }

    private static Method referenceCellMetrics(ClassLoader loader, Class<?> gridType, Class<?> sizeType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, sizeType),
                        CELL_METRICS, "sd.n");
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
                        PLACEMENT, "sd.l");
        return NativeContract.findMethodPrefix(
                owner, "f", void.class, widgetDataType, widgetAssistType, clockAssist);
    }

    static Method alignFeedback(ClassLoader loader, Class<?> widgetDataType)
            throws ReflectiveOperationException {
        Class<?> known =
                NativeContract.knownClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasField(cls, boolean.class)
                                        && NativeContract.hasMethod(
                                                cls, void.class, widgetDataType),
                        ALIGN_FEEDBACK);
        if (known != null) {
            return NativeContract.findMethod(known, "c", void.class, widgetDataType);
        }
        try {
            return WidgetEditorBinding.clockFeedback(loader, widgetDataType);
        } catch (ReflectiveOperationException fallback) {
        }
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

    static Class<?> adapter(ClassLoader loader) throws ReflectiveOperationException {
        return resizeAnimation(loader).getDeclaringClass();
    }

    static Method resizeAnimation(ClassLoader loader) throws ReflectiveOperationException {
        try {
            return NativeContract.findStaticMethod(referenceAdapter(loader), ADAPTER_METHOD,
                    void.class, View.class, int.class, int.class, float.class,
                    int.class, int.class, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException changed) {
            return OneUi95Widgets.resizeAnimation(loader);
        }
    }

    private static Class<?> referenceAdapter(ClassLoader loader) throws ClassNotFoundException {
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
                ADAPTER, "u2.a");
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
                        PICKER_SIZE, "sd.c");
        return NativeContract.findMethod(owner, "a", ArrayList.class, uiMode, deviceType);
    }

    static android.content.Context context(Object preview) throws ReflectiveOperationException {
        return (android.content.Context)
                NativeContract.findField(preview.getClass(), "b", android.content.Context.class)
                        .get(preview);
    }

    static Object ui(Object preview) throws ReflectiveOperationException {
        return WidgetEditorBinding.findUiController(preview, "i");
    }

    static Object grid(Object preview) throws ReflectiveOperationException {
        ClassLoader loader = preview.getClass().getClassLoader();
        Method getter = getGrid(loader);
        Object wrapper =
                WidgetEditorBinding.unwrapSupplier(preview, "a", getter.getDeclaringClass());
        return getter.invoke(wrapper);
    }

    static Object repoData(Object preview) throws ReflectiveOperationException {
        Class<?> widgetData = entity(preview.getClass().getClassLoader(), "item.widget.WidgetData");
        return Reflect.call(WidgetEditorBinding.findObservableData(preview, "l", widgetData), "getData");
    }

    static void finalizeList(Object preview) throws ReflectiveOperationException {
        try {
            NativeContract.method(preview.getClass(), "c", void.class).invoke(preview);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method found = null;
        for (Method m : preview.getClass().getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && !java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getParameterCount() == 0
                    && m.getReturnType() == void.class
                    && !m.getName().equals("getTag")
                    && !m.getName().equals("getSubTag")) {
                if (found != null) {
                    throw new NoSuchMethodException(
                            "Ambiguous finalize methods: " + found + " and " + m);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException(
                    "Missing finalize method on " + preview.getClass().getName());
        }
        found.setAccessible(true);
        found.invoke(preview);
    }

    static void refreshList(Object preview) throws ReflectiveOperationException {
        java.lang.reflect.Field refreshField =
                WidgetEditorBinding.findFieldMatching(
                        preview.getClass(),
                        "p",
                        f ->
                                NativeContract.hasMethod(
                                        f.getType(), void.class, ArrayList.class, boolean.class));
        Object useCase = refreshField.get(preview);
        NativeContract.findMethod(useCase.getClass(), "a", void.class).invoke(useCase);
    }

    static void recalculate(Object preview) throws ReflectiveOperationException {
        java.lang.reflect.Field refreshPos =
                WidgetEditorBinding.findFieldMatching(
                        preview.getClass(),
                        "z",
                        f -> {
                            for (java.lang.reflect.Constructor<?> c :
                                    f.getType().getDeclaredConstructors()) {
                                if (c.getParameterCount() == 14) return true;
                            }
                            return false;
                        });
        Object target = refreshPos.get(preview);
        NativeContract.findMethod(target.getClass(), "a", void.class).invoke(target);
    }

    static boolean isMain(Object preview) throws ReflectiveOperationException {
        Class<?> uiMode = entity(preview.getClass().getClassLoader(), "preview.UIMode");
        java.lang.reflect.Field uiModeField =
                WidgetEditorBinding.findFieldMatching(
                        preview.getClass(),
                        "c",
                        f -> NativeContract.hasMethod(f.getType(), uiMode));
        Object useCase = uiModeField.get(preview);
        Object mode = NativeContract.findMethod(useCase.getClass(), "b", uiMode).invoke(useCase);
        return Boolean.TRUE.equals(Reflect.call(mode, "isLockScreen"));
    }

    static Object alignUpdater(Object preview, Class<?> alignType)
            throws ReflectiveOperationException {
        java.lang.reflect.Field field =
                WidgetEditorBinding.findFieldMatching(
                        preview.getClass(),
                        "y",
                        f -> NativeContract.hasMethod(f.getType(), boolean.class, alignType));
        return field.get(preview);
    }

    static boolean applyAlign(Object preview, Object alignTarget)
            throws ReflectiveOperationException {
        Object updater = alignUpdater(preview, alignTarget.getClass());
        return Boolean.TRUE.equals(
                NativeContract.findMethod(
                                updater.getClass(), "a", boolean.class, alignTarget.getClass())
                        .invoke(updater, alignTarget));
    }

    static Object rotationTrimmedOwner(Object preview, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> ownerClass = rotationTrimmed(preview.getClass().getClassLoader(), alignType).getDeclaringClass();
        return NativeContract.findField(preview.getClass(), "D", ownerClass).get(preview);
    }

    static Object lifecycleMode(Object lifecycleUseCase) throws ReflectiveOperationException {
        Class<?> uiMode =
                entity(lifecycleUseCase.getClass().getClassLoader(), "preview.UIMode");
        try {
            java.lang.reflect.Field e = lifecycleUseCase.getClass().getDeclaredField("e");
            e.setAccessible(true);
            Object repo = e.get(lifecycleUseCase);
            if (repo != null) {
                Object mode = Reflect.call(repo, "getData");
                if (uiMode.isInstance(mode)) return mode;
            }
        } catch (Throwable ignored) {
        }
        for (java.lang.reflect.Field f : lifecycleUseCase.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive())
                continue;
            f.setAccessible(true);
            Object repo = f.get(lifecycleUseCase);
            if (repo == null) continue;
            try {
                Object mode = Reflect.call(repo, "getData");
                if (uiMode.isInstance(mode)) return mode;
            } catch (Throwable ignored) {
            }
        }
        throw new NoSuchFieldException(
                "Missing UIMode repo on " + lifecycleUseCase.getClass().getName());
    }

    static float previewScale(Object preview) throws ReflectiveOperationException {
        ClassLoader loader = preview.getClass().getClassLoader();
        Class<?> contextClass = android.content.Context.class;
        try {
            java.lang.reflect.Field vmField =
                    WidgetEditorBinding.findFieldMatching(
                            preview.getClass(),
                            "g",
                            f -> {
                                Class<?> t = f.getType();
                                if (!NativeContract.hasField(t, float.class)) return false;
                                for (java.lang.reflect.Constructor<?> c :
                                        t.getDeclaredConstructors()) {
                                    if (c.getParameterCount() == 7
                                            && c.getParameterTypes()[0] == contextClass)
                                        return true;
                                }
                                return false;
                            });
            Object vm = vmField.get(preview);
            return NativeContract.findField(vm.getClass(), "r", float.class).getFloat(vm);
        } catch (NoSuchFieldException migrated) {
        }
        java.lang.reflect.Field refreshPos =
                WidgetEditorBinding.findFieldMatching(
                        preview.getClass(),
                        "B",
                        f -> {
                            for (java.lang.reflect.Constructor<?> c :
                                    f.getType().getDeclaredConstructors()) {
                                if (c.getParameterCount() == 14) return true;
                            }
                            return false;
                        });
        Object target = refreshPos.get(preview);
        Class<?> previewScaleType = entity(loader, "system.PreviewScale");
        Object scaleValue = findDirectData(target, "i", previewScaleType);
        if (scaleValue == null) {
            Object repo = WidgetEditorBinding.findObservableData(target, "i", previewScaleType);
            scaleValue = Reflect.call(repo, "getData");
        }
        return ((Number) Reflect.call(scaleValue, "getScale")).floatValue();
    }

    private static Object findDirectData(
            Object owner, String preferredField, Class<?> expectedType) {
        if (owner == null) return null;
        if (preferredField != null) {
            try {
                java.lang.reflect.Field f = owner.getClass().getDeclaredField(preferredField);
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    Object repo = f.get(owner);
                    if (repo != null) {
                        Object value = Reflect.call(repo, "getData");
                        if (expectedType.isInstance(value)) return value;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (java.lang.reflect.Field f : owner.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive())
                continue;
            try {
                f.setAccessible(true);
                Object repo = f.get(owner);
                if (repo == null) continue;
                Object value = Reflect.call(repo, "getData");
                if (expectedType.isInstance(value)) return value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    static void addMenu(
            Object toolbarOwner,
            Class<?> rendererOwner,
            java.util.function.Function<android.content.Context, Runnable> openFactory,
            String tag)
            throws ReflectiveOperationException {
        var beta = betaToolbars.get(rendererOwner);
        if (beta.isPresent()) {
            beta.get().add(toolbarOwner, openFactory, tag);
            return;
        }
        ClassLoader loader = toolbarOwner.getClass().getClassLoader();
        Class<?> menuItemClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasConstructor(
                                        cls,
                                        int.class,
                                        int.class,
                                        Runnable.class,
                                        String.class),
                        MENU_ITEM);
        Class<?> arrayType = java.lang.reflect.Array.newInstance(menuItemClass, 0).getClass();
        java.lang.reflect.Field arrayField =
                NativeContract.findField(toolbarOwner.getClass(), "g", arrayType);
        Object current = arrayField.get(toolbarOwner);
        if (current == null || java.lang.reflect.Array.getLength(current) == 0) return;
        java.lang.reflect.Field tagField = resolveNineTagField(toolbarOwner.getClass(), menuItemClass);
        java.lang.reflect.Field labelField = resolveOtherStringField(menuItemClass, tagField);
        ArrayList<Object> items = new ArrayList<>();
        for (int i = 0; i < java.lang.reflect.Array.getLength(current); i++) {
            Object item = java.lang.reflect.Array.get(current, i);
            if (tag.equals(tagField.get(item))) return;
            items.add(item);
        }
        android.content.Context context =
                (android.content.Context)
                        WidgetEditorBinding.findFieldMatching(
                                        toolbarOwner.getClass(),
                                        "a",
                                        f -> f.getType() == android.content.Context.class)
                                .get(toolbarOwner);
        int icon =
                context.getResources()
                        .getIdentifier(
                                "preview_menu_icon_effect", "drawable", context.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        Object extra =
                menuItemClass
                        .getDeclaredConstructor(
                                int.class, int.class, Runnable.class, String.class)
                        .newInstance(-1, icon, openFactory.apply(context), tag);
        if (labelField != null)
            labelField.set(
                    extra,
                    ModuleStrings.getOrFallback(context, R.string.common_advanced, "Advanced"));
        items.add(extra);
        Object updated = java.lang.reflect.Array.newInstance(menuItemClass, items.size());
        for (int i = 0; i < items.size(); i++) java.lang.reflect.Array.set(updated, i, items.get(i));
        arrayField.set(toolbarOwner, updated);
    }

    private static java.lang.reflect.Field resolveNineTagField(
            Class<?> toolbarClass, Class<?> menuItemClass) throws NoSuchFieldException {
        try {
            Method renderer = NativeContract.findMethod(toolbarClass, TOOLBAR_METHOD, void.class);
            DexSignatures.Member m =
                    DexSignatures.body(renderer)
                            .uniqueRead(
                                    "L" + menuItemClass.getName().replace('.', '/') + ";",
                                    "Ljava/lang/String;",
                                    null);
            return m.field(menuItemClass.getClassLoader());
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError ignored) {
        }
        return NativeContract.field(menuItemClass, "e", String.class);
    }

    private static java.lang.reflect.Field resolveOtherStringField(
            Class<?> menuItemClass, java.lang.reflect.Field tagField) {
        java.lang.reflect.Field other = null;
        for (java.lang.reflect.Field f : menuItemClass.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    && f.getType() == String.class
                    && !f.getName().equals(tagField.getName())) {
                if (other != null) return null;
                other = f;
            }
        }
        if (other != null) other.setAccessible(true);
        return other;
    }
}
