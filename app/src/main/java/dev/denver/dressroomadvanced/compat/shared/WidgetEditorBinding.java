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
    final OneUi80WidgetPanel earlyPanel;
    final java.lang.reflect.Field earlyToolbarView;
    private final Class<?> menuItem;
    private final java.lang.reflect.Field rootField;

    WidgetEditorBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        java.util.function.Predicate<Class<?>> previewShape =
                cls -> NativeContract.hasField(cls, widgetData)
                        && NativeContract.hasMethod(cls, View.class);
        Class<?> preview;
        try {
            preview = NativeContract.findClass(
                        loader,
                        previewShape,
                        OneUi85WidgetEditor.PREVIEW,
                        "gb.l0");
        } catch (NativeContract.AmbiguousClassException ambiguous) {
            String anchor = "DRESSROOM_LOCK_COMPLICATION_CONTAINER";
            try {
                var matching = new ArrayList<Class<?>>();
                for (var body : DexSignatures.find(loader, java.util.Set.of(anchor))) {
                    if (body.parameters().isEmpty() && body.result().equals("Landroid/view/View;")) {
                        Class<?> owner = body.loadOwner(loader);
                        if (previewShape.test(owner) && !matching.contains(owner)) matching.add(owner);
                    }
                }
                if (matching.size() != 1) throw ambiguous;
                preview = matching.get(0);
            } catch (java.io.IOException unavailable) {
                ambiguous.addSuppressed(unavailable);
                throw ambiguous;
            }
        }
        create = findPublicViewMethod(preview);
        earlyPanel = NativeTabs.forType(Class.forName(
                "com.google.android.material.tabs.TabLayout", false, loader)).early
                ? OneUi80WidgetPanel.bind(create) : null;
        NativeContract.findField(preview, "I", widgetData);
        NativeContract.findField(preview, "J", ViewGroup.class);
        rootField = resolveRootField(preview, create);
        // The old rotation-host pair was never used here. Rotation is validated
        // against the actual preview's use case by OneUi85WidgetLayout.
        NativeContract.method(
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                        false,
                        loader),
                "dispatchTouchEvent",
                boolean.class,
                android.view.MotionEvent.class);
        Class<?> adapter = OneUi85WidgetEditor.adapter(loader);
        NativeContract.findStaticMethod(
                adapter,
                OneUi85WidgetEditor.ADAPTER_METHOD,
                void.class,
                View.class,
                int.class,
                int.class,
                float.class,
                int.class,
                int.class,
                boolean.class);
        Class.forName("androidx.databinding.ViewDataBinding", false, loader)
                .getDeclaredMethod("getBinding", View.class);
        menuItem =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasConstructor(
                                        cls, String.class, int.class, Runnable.class, String.class),
                        OneUi85WidgetEditor.MENU_ITEM);
        menuItem.getDeclaredConstructor(String.class, int.class, Runnable.class, String.class);
        Class<?> menuArray = Array.newInstance(menuItem, 0).getClass();
        Class<?> toolbarClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, Context.class)
                                        && NativeContract.hasField(cls, menuArray)
                                        && NativeContract.hasConstructor(
                                                cls, android.app.Activity.class, View.class),
                        OneUi85WidgetEditor.TOOLBAR,
                        "V3.r");
        toolbar = toolbarRenderer(loader, toolbarClass, menuArray);
        earlyToolbarView = earlyPanel == null ? null : OneUi80WidgetPanel.toolbarView(toolbar);
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        Class<?> gridController =
                NativeContract.findClass(
                        loader,
                        cls -> !cls.isInterface() && NativeContract.hasMethod(cls, uiController),
                        OneUi85WidgetEditor.GRID_CONTROLLER,
                        "N5.i");
        getGrid =
                NativeContract.findMethod(
                        gridController, OneUi85WidgetEditor.GRID_METHOD, uiController);
        Class<?> gridType = entity(loader, "item.widget.WidgetGridData");
        Class<?> sizeType = entity(loader, "item.widget.WidgetSizeData");
        Class<?> previewScale = entity(loader, "system.PreviewScale");
        Class<?> widgetAssist = entity(loader, "item.widget.WidgetAssistData");
        Class<?> screenSize = entity(loader, "system.ScreenSize");
        Class<?> deviceType = entity(loader, "system.DeviceType");
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> moduleRect = entity(loader, "item.common.ModuleRectInfo");
        Class<?> clockAssist = entity(loader, "item.clock.ClockAssistData");

        Class<?> metricsClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && !cls.getName().startsWith(REPOSITORIES + "sublauncher.")
                                        && NativeContract.hasMethod(
                                                cls,
                                                void.class,
                                                gridType,
                                                sizeType,
                                                boolean.class,
                                                previewScale,
                                                boolean.class,
                                                widgetAssist,
                                                boolean.class,
                                                screenSize,
                                                deviceType,
                                                boolean.class,
                                                uiMode,
                                                moduleRect),
                        REPOSITORIES + "K",
                        REPOSITORIES + "L");
        metrics =
                NativeContract.findMethod(
                        metricsClass,
                        "a",
                        void.class,
                        gridType,
                        sizeType,
                        boolean.class,
                        previewScale,
                        boolean.class,
                        widgetAssist,
                        boolean.class,
                        screenSize,
                        deviceType,
                        boolean.class,
                        uiMode,
                        moduleRect);
        Class<?> sizeMgr =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetSizeManager",
                        false,
                        loader);
        try {
            NativeContract.findMethod(metricsClass, "s", sizeMgr);
        } catch (NoSuchMethodException missing) {
            NativeContract.findField(
                    metrics.getDeclaringClass(), "d", Class.forName("kotlin.Lazy", false, loader));
        }

        Class<?> positionClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && !cls.getName().startsWith(REPOSITORIES + "sublauncher.")
                                        && NativeContract.hasMethod(
                                                cls,
                                                void.class,
                                                widgetData,
                                                widgetAssist,
                                                clockAssist,
                                                uiMode,
                                                boolean.class,
                                                screenSize,
                                                boolean.class,
                                                moduleRect),
                        REPOSITORIES + "z",
                        REPOSITORIES + "A");
        position =
                NativeContract.findMethod(
                        positionClass,
                        "b",
                        void.class,
                        widgetData,
                        widgetAssist,
                        clockAssist,
                        uiMode,
                        boolean.class,
                        screenSize,
                        boolean.class,
                        moduleRect);
    }

    private static Class<?> entity(ClassLoader loader, String suffix)
            throws ClassNotFoundException {
        return Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity." + suffix,
                false,
                loader);
    }

    private static Method toolbarRenderer(ClassLoader loader, Class<?> toolbar, Class<?> menuArray)
            throws ReflectiveOperationException {
        var methods = new java.util.LinkedHashSet<Method>();
        String owner = "L" + toolbar.getName().replace('.', '/') + ";";
        String array = "[L" + menuArray.getComponentType().getName().replace('.', '/') + ";";
        try {
            for (Class<?> container :
                    new Class<?>[] {android.widget.LinearLayout.class, ViewGroup.class})
                for (DexSignatures.Body body :
                        DexSignatures.callers(loader, container, "removeAllViews", void.class)) {
                    if (!body.owner().equals(owner) || !body.matches()) continue;
                    for (DexSignatures.Read read : body.reads())
                        if (read.field().type().equals(array)) {
                            methods.add(body.method(loader));
                            break;
                        }
                }
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read widget toolbar renderer", invalidDex);
        }
        if (methods.size() != 1)
            throw new NoSuchMethodException("Ambiguous widget toolbar renderer: " + methods.size());
        return methods.iterator().next();
    }

    private static Method findPublicViewMethod(Class<?> preview) throws NoSuchMethodException {
        try {
            return NativeContract.method(preview, "d", View.class);
        } catch (NoSuchMethodException ignored) {
        }
        Method found = null;
        for (Method m : preview.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && !java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getParameterCount() == 0
                    && m.getReturnType() == View.class) {
                if (found != null) {
                    throw new NoSuchMethodException(
                            "Ambiguous create methods: " + found + " and " + m);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException(
                    "Missing create method on " + preview.getName());
        }
        found.setAccessible(true);
        return found;
    }

    static java.lang.reflect.Field findFieldMatching(
            Class<?> owner,
            String preferredName,
            java.util.function.Predicate<java.lang.reflect.Field> signature)
            throws NoSuchFieldException {
        if (preferredName != null) {
            try {
                java.lang.reflect.Field f = owner.getDeclaredField(preferredName);
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && signature.test(f)) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        java.lang.reflect.Field found = null;
        for (java.lang.reflect.Field f : owner.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && signature.test(f)) {
                if (found != null) {
                    throw new NoSuchFieldException("Ambiguous fields: " + found + " and " + f);
                }
                found = f;
            }
        }
        if (found == null) {
            throw new NoSuchFieldException("No matching field in " + owner.getName());
        }
        found.setAccessible(true);
        return found;
    }

    static Method clockFeedback(ClassLoader loader, Class<?> widgetData)
            throws ReflectiveOperationException {
        try {
            Class<?> direct =
                    Class.forName(
                            "com.samsung.android.lockscreen.editor.item.data.repository.item.clock.c",
                            false,
                            loader);
            if (isClockFeedbackRepo(direct, widgetData)) {
                return NativeContract.findMethod(direct, null, void.class, widgetData);
            }
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> found = null;
            for (DexSignatures.Body b :
                    DexSignatures.find(
                            loader, java.util.Set.of("ClockAlignPositionDataRepositoryImpl"))) {
                String owner = b.owner();
                if (!owner.startsWith("L") || !owner.endsWith(";")) continue;
                String name = owner.substring(1, owner.length() - 1).replace('/', '.');
                try {
                    Class<?> candidate = Class.forName(name, false, loader);
                    if (isClockFeedbackRepo(candidate, widgetData)) {
                        if (found != null && found != candidate) {
                            found = null;
                            break;
                        }
                        found = candidate;
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (found != null) {
                return NativeContract.findMethod(found, null, void.class, widgetData);
            }
        } catch (java.io.IOException | LinkageError ignored) {
        }
        Class<?> clockRepo =
                NativeContract.findClass(
                        loader,
                        cls -> isClockFeedbackRepo(cls, widgetData));
        return NativeContract.findMethod(clockRepo, null, void.class, widgetData);
    }

    private static boolean isClockFeedbackRepo(Class<?> cls, Class<?> widgetData) {
        return !cls.isInterface()
                && NativeContract.hasConstructor(cls)
                && NativeContract.hasField(cls, boolean.class)
                && NativeContract.hasField(cls, String.class)
                && NativeContract.hasMethod(cls, void.class, widgetData)
                && NativeContract.hasMethod(cls, boolean.class);
    }

    static Object widgetSizeManager(Object metricsRepo, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> sizeMgr =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetSizeManager",
                        false,
                        loader);
        try {
            Method getter = NativeContract.findMethod(metricsRepo.getClass(), "s", sizeMgr);
            Object direct = getter.invoke(metricsRepo);
            if (direct != null) return direct;
        } catch (NoSuchMethodException ignored) {
        }
        java.lang.reflect.Field providerField;
        try {
            providerField = NativeContract.findField(
                        metricsRepo.getClass(),
                        "b",
                        Class.forName("javax.inject.Provider", false, loader));
        } catch (ClassNotFoundException | NoSuchFieldException newerSupplier) {
            var providers = java.util.Arrays.stream(metricsRepo.getClass().getDeclaredFields())
                    .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            && isZeroArgSupplier(field.getType()))
                    .toList();
            providerField = OneUi95Editor.unique(providers, "widget sizing provider");
            providerField.setAccessible(true);
        }
        Object provider = providerField.get(metricsRepo);
        Object wrapper = Reflect.call(provider, "get");
        return NativeContract.findField(wrapper.getClass(), null, sizeMgr).get(wrapper);
    }

    static Object findUiController(Object preview, String preferredField)
            throws ReflectiveOperationException {
        java.lang.reflect.Field uiField =
                findFieldMatching(
                        preview.getClass(),
                        preferredField,
                        f -> hasPanelShowMethod(f.getType()));
        return uiField.get(preview);
    }

    private static boolean hasPanelShowMethod(Class<?> type) {
        for (Method m : type.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getReturnType() == void.class
                    && params.length == 10
                    && params[6].isEnum()
                    && params[7] == android.graphics.Rect.class
                    && params[8] == boolean.class) {
                return true;
            }
        }
        return false;
    }

    static Object screenSizeObservable(Object preview, String preferredField)
            throws ReflectiveOperationException {
        ClassLoader loader = preview.getClass().getClassLoader();
        Class<?> observableData = entity(loader, "common.ObservableData");
        Class<?> screenSize = entity(loader, "system.ScreenSize");
        if (preferredField != null) {
            try {
                java.lang.reflect.Field f = preview.getClass().getDeclaredField(preferredField);
                f.setAccessible(true);
                Object useCase = f.get(preview);
                if (useCase != null) {
                    Object obs = NativeContract.findMethod(useCase.getClass(), "a", observableData).invoke(useCase);
                    if (obs != null && screenSize.isInstance(Reflect.call(obs, "getData"))) {
                        return obs;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        for (java.lang.reflect.Field f : preview.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object useCase = f.get(preview);
            if (useCase == null) continue;
            try {
                Method getObs = NativeContract.findMethod(useCase.getClass(), null, observableData);
                Object obs = getObs.invoke(useCase);
                if (obs != null && screenSize.isInstance(Reflect.call(obs, "getData"))) {
                    return obs;
                }
            } catch (Throwable ignored) {
            }
        }
        throw new NoSuchFieldException("Missing ScreenSize observable on " + preview.getClass().getName());
    }

    static Object findObservableData(Object preview, String preferredField, Class<?> expectedData)
            throws ReflectiveOperationException {
        if (preferredField != null) {
            try {
                java.lang.reflect.Field f = preview.getClass().getDeclaredField(preferredField);
                f.setAccessible(true);
                Object useCase = f.get(preview);
                if (useCase != null) {
                    Object repo = findSingleRepoField(useCase);
                    if (repo != null) {
                        Object value = Reflect.call(repo, "getData");
                        if (expectedData.isInstance(value)) return repo;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        for (java.lang.reflect.Field f : preview.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object useCase = f.get(preview);
            if (useCase == null) continue;
            try {
                Object repo = findSingleRepoField(useCase);
                if (repo != null) {
                    Object value = Reflect.call(repo, "getData");
                    if (expectedData.isInstance(value)) return repo;
                }
            } catch (Throwable ignored) {
            }
        }
        throw new NoSuchFieldException(
                "Missing observable repo for " + expectedData.getName() + " on " + preview.getClass().getName());
    }

    private static Object findSingleRepoField(Object useCase) throws ReflectiveOperationException {
        try {
            java.lang.reflect.Field a = useCase.getClass().getDeclaredField("a");
            if (!java.lang.reflect.Modifier.isStatic(a.getModifiers())) {
                a.setAccessible(true);
                Object v = a.get(useCase);
                if (v != null && hasMethodNamed(v.getClass(), "getData")) return v;
            }
        } catch (NoSuchFieldException ignored) {
        }
        java.lang.reflect.Field found = null;
        for (java.lang.reflect.Field f : useCase.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object v = f.get(useCase);
            if (v != null && hasMethodNamed(v.getClass(), "getData")) {
                if (found != null) return null;
                found = f;
            }
        }
        return found == null ? null : found.get(useCase);
    }

    private static boolean hasMethodNamed(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) return true;
        }
        return false;
    }

    private static java.lang.reflect.Field resolveRootField(Class<?> preview, Method create)
            throws NoSuchFieldException {
        try {
            return NativeContract.assignableField(preview, "N", View.class);
        } catch (NoSuchFieldException ignored) {
        }
        try {
            DexSignatures.Body body = DexSignatures.body(create);
            String ownerDesc = "L" + preview.getName().replace('.', '/') + ";";
            try {
                return body.uniqueWrite(ownerDesc, "Landroid/view/View;", null)
                        .field(preview.getClassLoader());
            } catch (NoSuchFieldException ignored) {
                return body.uniqueRead(ownerDesc, "Landroid/view/View;", null)
                        .field(preview.getClassLoader());
            }
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError ignored) {
        }
        return NativeContract.findField(preview, null, View.class);
    }

    View root(Object owner) throws IllegalAccessException {
        return (View) rootField.get(owner);
    }

    static Context previewContext(Object preview) throws ReflectiveOperationException {
        return (Context)
                findFieldMatching(
                                preview.getClass(), "b", f -> f.getType() == Context.class)
                        .get(preview);
    }

    boolean isClockFeedback(Object preview, Object candidate, Method feedbackMethod)
            throws ReflectiveOperationException {
        if (preview == null || candidate == null) return false;
        java.lang.reflect.Field refreshPos =
                findFieldMatching(
                        preview.getClass(),
                        "B",
                        f -> {
                            for (java.lang.reflect.Constructor<?> c :
                                    f.getType().getDeclaredConstructors()) {
                                if (c.getParameterCount() == 15) return true;
                            }
                            return false;
                        });
        Object useCase = refreshPos.get(preview);
        if (useCase == null) return false;
        Class<?> repoClass = feedbackMethod.getDeclaringClass();
        java.lang.reflect.Field clockRepoField =
                findFieldMatching(
                        useCase.getClass(),
                        "l",
                        f -> f.getType() != Object.class && f.getType().isAssignableFrom(repoClass));
        return clockRepoField.get(useCase) == candidate;
    }

    Object data(Object owner) throws ReflectiveOperationException {
        return Reflect.call(repo(owner), "getData");
    }

    Object repo(Object owner) throws ReflectiveOperationException {
        return findObservableData(
                owner, "n", entity(owner.getClass().getClassLoader(), "item.widget.WidgetData"));
    }

    static boolean isZeroArgSupplier(Class<?> type) {
        if (type == null || !type.isInterface()) return false;
        Method[] methods = type.getMethods();
        if (methods.length != 1) return false;
        Method m = methods[0];
        return m.getName().equals("get")
                && m.getParameterCount() == 0
                && m.getReturnType() == Object.class;
    }

    static Object unwrapSupplier(Object owner, String preferredField, Class<?> expectedValue)
            throws ReflectiveOperationException {
        if (preferredField != null) {
            try {
                java.lang.reflect.Field f = owner.getClass().getDeclaredField(preferredField);
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    Object raw = f.get(owner);
                    if (raw != null) {
                        if (expectedValue.isInstance(raw)) return raw;
                        if (isZeroArgSupplier(f.getType())) {
                            Object supplied = Reflect.call(raw, "get");
                            if (expectedValue.isInstance(supplied)) return supplied;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Object matched = null;
        for (java.lang.reflect.Field f : owner.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive())
                continue;
            f.setAccessible(true);
            Object raw = f.get(owner);
            if (raw == null) continue;
            Object candidate = null;
            if (expectedValue.isInstance(raw)) {
                candidate = raw;
            } else if (isZeroArgSupplier(f.getType())) {
                try {
                    Object supplied = Reflect.call(raw, "get");
                    if (expectedValue.isInstance(supplied)) candidate = supplied;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (candidate != null) {
                if (matched != null && matched != candidate) {
                    throw new NoSuchFieldException(
                            "Ambiguous supplier for "
                                    + expectedValue.getName()
                                    + " on "
                                    + owner.getClass().getName());
                }
                matched = candidate;
            }
        }
        if (matched == null) {
            throw new NoSuchFieldException(
                    "Missing supplier for "
                            + expectedValue.getName()
                            + " on "
                            + owner.getClass().getName());
        }
        return matched;
    }

    Object grid(Object owner) throws ReflectiveOperationException {
        Object wrapper = unwrapSupplier(owner, "a", getGrid.getDeclaringClass());
        return getGrid.invoke(wrapper);
    }

    boolean main(Object owner) throws ReflectiveOperationException {
        if (earlyPanel != null) return earlyPanel.main(owner);
        Class<?> uiMode = entity(owner.getClass().getClassLoader(), "preview.UIMode");
        Object mode = Reflect.call(findObservableData(owner, "c", uiMode), "getData");
        return Boolean.TRUE.equals(Reflect.call(mode, "isLockScreen"));
    }

    Object panel(Object owner) throws ReflectiveOperationException {
        return earlyPanel == null ? findUiController(owner, "k") : earlyPanel.controller().get(owner);
    }

    void recalculate(Object owner) throws ReflectiveOperationException {
        java.lang.reflect.Field refreshPos =
                findFieldMatching(
                        owner.getClass(),
                        "B",
                        f -> {
                            for (java.lang.reflect.Constructor<?> c :
                                    f.getType().getDeclaredConstructors()) {
                                if (c.getParameterCount() == 15) return true;
                            }
                            return false;
                        });
        Object target = refreshPos.get(owner);
        NativeContract.findMethod(target.getClass(), "a", void.class).invoke(target);
    }

    void finalizeList(Object owner) throws ReflectiveOperationException {
        try {
            NativeContract.method(owner.getClass(), "b", void.class).invoke(owner);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method found = null;
        for (Method m : owner.getClass().getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && !java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && !m.isBridge()
                    && m.getParameterCount() == 0
                    && m.getReturnType() == void.class
                    && !m.getName().equals("getSubTag")
                    && !m.getName().equals("getTag")) {
                if (found != null) {
                    throw new NoSuchMethodException("Ambiguous finalize methods: " + found + " and " + m);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("Missing finalize method on " + owner.getClass().getName());
        }
        found.setAccessible(true);
        found.invoke(owner);
    }

    void addMenu(
            Object owner, java.util.function.Function<Context, Runnable> openFactory, String tag)
            throws ReflectiveOperationException {
        java.lang.reflect.Field arrayField =
                NativeContract.findField(
                        owner.getClass(),
                        "f",
                        Array.newInstance(menuItem, 0).getClass());
        Object current = arrayField.get(owner);
        if (current == null || Array.getLength(current) == 0) return;
        Method tagGetter = resolveMenuTagGetter(owner.getClass());
        ArrayList<Object> items = new ArrayList<>();
        for (int i = 0; i < Array.getLength(current); i++) {
            Object item = Array.get(current, i);
            if (tag.equals(tagGetter.invoke(item))) return;
            items.add(item);
        }
        Context context =
                (Context)
                        findFieldMatching(
                                        owner.getClass(),
                                        "a",
                                        f -> f.getType() == Context.class)
                                .get(owner);
        int icon =
                context.getResources()
                        .getIdentifier(
                                "preview_menu_icon_effect", "drawable", context.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        Class<?> itemType = current.getClass().getComponentType();
        if (itemType == null) itemType = menuItem;
        items.add(
                itemType.getDeclaredConstructor(
                                String.class, int.class, Runnable.class, String.class)
                        .newInstance("Advanced", icon, openFactory.apply(context), tag));
        Object updated = Array.newInstance(itemType, items.size());
        for (int i = 0; i < items.size(); i++) Array.set(updated, i, items.get(i));
        arrayField.set(owner, updated);
    }

    private Method resolveMenuTagGetter(Class<?> toolbarClass) throws NoSuchMethodException {
        try {
            return NativeContract.method(menuItem, "j", String.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            String itemDesc = "L" + menuItem.getName().replace('.', '/') + ";";
            Method matched = null;
            for (Method enable : toolbarClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(enable.getModifiers())
                        || enable.isBridge()
                        || enable.getReturnType() != void.class
                        || enable.getParameterCount() != 2
                        || enable.getParameterTypes()[0] != boolean.class
                        || enable.getParameterTypes()[1] != String.class) {
                    continue;
                }
                for (DexSignatures.Call c : DexSignatures.body(enable).calls()) {
                    if (!c.isStatic()
                            && c.owner().equals(itemDesc)
                            && c.parameters().isEmpty()
                            && c.result().equals("Ljava/lang/String;")) {
                        Method candidate = c.method(menuItem.getClassLoader());
                        if (matched != null && !matched.equals(candidate)) {
                            throw new NoSuchMethodException(
                                    "Ambiguous menu item tag getters: "
                                            + matched
                                            + " and "
                                            + candidate);
                        }
                        matched = candidate;
                    }
                }
            }
            if (matched != null) return matched;
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError ignored) {
        }
        throw new NoSuchMethodException("Missing menu item tag getter on " + menuItem.getName());
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
        int width = columns * (unit + padding * 2);
        int height = rows * (unit + padding * 2);
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
