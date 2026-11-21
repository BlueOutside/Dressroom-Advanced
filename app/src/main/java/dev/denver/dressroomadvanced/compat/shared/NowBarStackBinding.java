package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Checked entry points for the stack engines; obfuscated names live with their provider. */
final class NowBarStackBinding {
    record Names(
            String model,
            String states,
            String manager,
            String stackSize,
            String keys,
            String key,
            String items,
            String restricted,
            String mode,
            String screenOn,
            String dragging,
            String reordering,
            String dismissing,
            String expanding,
            String animationManager,
            String animationsEnabled,
            String animate,
            boolean countedAnimate,
            String bindItem,
            String hideContents,
            String wave,
            String resources) {}

    final Class<?> stack, card, model;
    final Field items, restricted, mode, screenOn, dragging, reordering, dismissing, expanding;
    final Field animationManager, animationsEnabled, state, animator;
    final Method refresh,
            animate,
            expanded,
            content,
            data,
            item,
            key,
            blur,
            bindItem,
            hideContents,
            wave;
    final Constructor<?> stackSize;
    final String resources;

    static NowBarStackBinding of(Class<?> clock) throws ReflectiveOperationException {
        ClockBinding provider = ClockBinding.of(clock);
        return new NowBarStackBinding(
                clock.getClassLoader(),
                provider.legacy
                        ? (provider.clockStyle ? OneUi85Stack.names() : OneUi85AodStack.names())
                        : (provider.clockStyle
                                ? OneUi9ClockStyleStack.names()
                                : OneUi9Stack.names()));
    }

    final Method scale;
    final Method offset;
    final Method depth;
    final Method visibilityByExpandState;
    final Method cardBlurAlpha;

    static final class StackState {
        private static final String VIEW_MODE =
                "com.samsung.android.app.facewidget.common.NowBarConstants$Companion$ViewMode";
        private static final java.util.Map<Class<?>, StackState> CACHE =
                java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

        final Method expanded;
        final Method settingOpen;
        final Field expanding;
        final Field mode;
        final Method cardBlurAlpha;

        private StackState(
                Method expanded,
                Method settingOpen,
                Field expanding,
                Field mode,
                Method cardBlurAlpha) {
            this.expanded = expanded;
            this.settingOpen = settingOpen;
            this.expanding = expanding;
            this.mode = mode;
            this.cardBlurAlpha = cardBlurAlpha;
        }

        static StackState of(Class<?> root) throws ReflectiveOperationException {
            StackState cached = CACHE.get(root);
            if (cached != null) return cached;
            StackState resolved;
            try {
                resolved = DexSignatures.cached(() -> resolve(root));
            } catch (ReflectiveOperationException | RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw (NoSuchMethodException)
                        new NoSuchMethodException("Now Bar stack state: " + root.getName())
                                .initCause(error);
            }
            CACHE.put(root, resolved);
            return resolved;
        }

        private static StackState resolve(Class<?> root) throws ReflectiveOperationException {
            boolean isRealStack =
                    "com.samsung.android.app.facewidget.utils.NowBarCardStackView"
                                    .equals(root.getName())
                            || hasDeclaredMethodInHierarchy(
                                    root, "setExpandVIStarted", boolean.class);
            Method expanded =
                    isRealStack
                            ? resolveExpanded(root)
                            : NativeContract.method(root, "k", boolean.class);
            Method settingOpen =
                    isRealStack
                            ? resolveSettingOpen(root)
                            : NativeContract.method(root, "n", boolean.class);
            Field expanding =
                    isRealStack
                            ? resolveExpanding(root, "U")
                            : NativeContract.field(root, "U", boolean.class);
            Field mode = resolveModeOptional(root);
            Method cardBlurAlpha = isRealStack ? resolveCardBlurAlpha(root) : null;
            return new StackState(expanded, settingOpen, expanding, mode, cardBlurAlpha);
        }
    }

    private static boolean hasDeclaredMethodInHierarchy(
            Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> cur = type; cur != null; cur = cur.getSuperclass()) {
            try {
                cur.getDeclaredMethod(name, parameterTypes);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return false;
    }

    private static String classNameFromDescriptor(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            return null;
        }
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    static String resolveItemsFieldName(String stackDesc, DexSignatures.Body getCountBody)
            throws NoSuchFieldException {
        return getCountBody.uniqueRead(stackDesc, "Ljava/util/ArrayList;", null).name();
    }

    private static Field resolveItems(Class<?> stack, String fallbackName)
            throws ReflectiveOperationException {
        try {
            Method getCount = NativeContract.findMethod(stack, "getCount", int.class);
            String fieldName =
                    resolveItemsFieldName(
                            DexSignatures.descriptor(stack), DexSignatures.body(getCount));
            return NativeContract.findField(stack, fieldName, ArrayList.class);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.findField(stack, fallbackName, ArrayList.class);
    }

    static String resolveRestrictedFieldName(String stackDesc, DexSignatures.Body getMaxSizeBody)
            throws NoSuchFieldException {
        return getMaxSizeBody.uniqueRead(stackDesc, "Z", null).name();
    }

    private static Field resolveRestricted(Class<?> stack, String fallbackName)
            throws ReflectiveOperationException {
        try {
            Method getMaxSize = NativeContract.findMethod(stack, "getStackMaxSize", int.class);
            String fieldName =
                    resolveRestrictedFieldName(
                            DexSignatures.descriptor(stack), DexSignatures.body(getMaxSize));
            return NativeContract.field(stack, fieldName, boolean.class);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.field(stack, fallbackName, boolean.class);
    }

    private static Field resolveModeOptional(Class<?> stack) {
        java.util.List<Field> matches = new java.util.ArrayList<>();
        for (Class<?> cur = stack; cur != null; cur = cur.getSuperclass()) {
            for (Field field : cur.getDeclaredFields()) {
                if (StackState.VIEW_MODE.equals(field.getType().getName())) {
                    field.setAccessible(true);
                    matches.add(field);
                }
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static Field resolveMode(Class<?> stack, String fallbackName)
            throws ReflectiveOperationException {
        Field resolved = resolveModeOptional(stack);
        if (resolved != null) return resolved;
        return NativeContract.findField(stack, fallbackName, Enum.class);
    }

    static String resolveExpandingFieldName(String stackDesc, DexSignatures.Body setterBody)
            throws NoSuchFieldException {
        return setterBody.uniqueWrite(stackDesc, "Z", null).name();
    }

    private static Field resolveExpanding(Class<?> stack, String fallbackName)
            throws ReflectiveOperationException {
        try {
            Method setter =
                    NativeContract.findMethod(
                            stack, "setExpandVIStarted", void.class, boolean.class);
            String fieldName =
                    resolveExpandingFieldName(
                            DexSignatures.descriptor(stack), DexSignatures.body(setter));
            return NativeContract.field(stack, fieldName, boolean.class);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.field(stack, fallbackName, boolean.class);
    }

    private static Method resolveRefresh(Class<?> stack, Class<?> item) throws ReflectiveOperationException {
        try {
            return NativeContract.findMethod(stack, "q", void.class, CopyOnWriteArrayList.class);
        } catch (NoSuchMethodException changed) {
            String anchor = "NowBar_NowBarCardStackView.#refreshAllViews";
            ClassLoader loader = stack.getClassLoader();
            try {
                return OneUi95Editor.unique(DexSignatures.find(loader, Set.of(anchor)).stream()
                        .filter(body -> body.owner().equals(DexSignatures.descriptor(stack))
                                && (body.access() & 8) == 0 && body.result().equals("V")
                                && body.parameters().equals(List.of(DexSignatures.descriptor(item),
                                        "Ljava/util/concurrent/CopyOnWriteArrayList;")))
                        .toList(), "Now Bar full refresh").method(loader);
            } catch (java.io.IOException invalidDex) {
                throw new ReflectiveOperationException("Now Bar refresh DEX unavailable", invalidDex);
            }
        }
    }

    int refreshItemsArgument() {
        return refresh.getParameterCount() - 1;
    }

    void refreshCards(Object view, CopyOnWriteArrayList<?> current)
            throws ReflectiveOperationException {
        if (refreshItemsArgument() == 0) refresh.invoke(view, current);
        else refresh.invoke(view, null, current);
    }

    boolean isDozing(ViewGroup view) throws IllegalAccessException {
        return !screenOn.getBoolean(view);
    }

    private static DexSignatures.Body resolveMakeCardBody(Class<?> stack, Class<?> item)
            throws ReflectiveOperationException, java.io.IOException {
        String anchor = "NowBar_NowBarCardStackView.#makeNowBarCardView";
        String stackType = DexSignatures.descriptor(stack);
        return OneUi95Editor.unique(DexSignatures.find(stack.getClassLoader(), Set.of(anchor))
                .stream().filter(candidate -> candidate.owner().equals(stackType)
                        && candidate.result().equals("Landroid/view/View;")
                        && candidate.parameters().equals(
                                List.of(DexSignatures.descriptor(item), stackType, "I")))
                .toList(), "Now Bar card creation");
    }

    private static Field resolveBetaDozing(
            Class<?> stack, Class<?> card, DexSignatures.Body body)
            throws ReflectiveOperationException {
        String stackType = DexSignatures.descriptor(stack);
        int setter = OneUi95Editor.call(body, DexSignatures.descriptor(card),
                "setInitialDozing").offset();
        DexSignatures.Read read = body.reads().stream()
                .filter(field -> field.offset() < setter && field.field().owner().equals(stackType)
                        && field.field().type().equals("Z"))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new NoSuchFieldException("Now Bar initial dozing"));
        return read.field().field(stack.getClassLoader());
    }

    static Field resolveScreenOnFromRefresh(
            Class<?> stack, DexSignatures.Body refreshBody, String fallbackName)
            throws ReflectiveOperationException {
        if (refreshBody != null) {
            String stackDesc = DexSignatures.descriptor(stack);
            java.util.List<DexSignatures.Read> boolReads =
                    refreshBody.reads().stream()
                            .filter(
                                    read ->
                                            stackDesc.equals(read.field().owner())
                                                    && "Z".equals(read.field().type()))
                            .sorted(
                                    java.util.Comparator.comparingInt(
                                            DexSignatures.Read::offset))
                            .toList();
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
            for (var call : refreshBody.calls()) {
                if (!call.isStatic()
                        && "setInitialDozing".equals(call.name())
                        && call.parameters().equals(java.util.List.of("Z"))) {
                    String prior = null;
                    for (var read : boolReads) {
                        if (read.offset() < call.offset()) prior = read.field().name();
                    }
                    if (prior != null) candidates.add(prior);
                }
            }
            if (candidates.size() != 1) {
                throw new NoSuchFieldException("NowBarCardStackView screenOn field");
            }
            return NativeContract.field(stack, candidates.iterator().next(), boolean.class);
        }
        return NativeContract.field(stack, fallbackName, boolean.class);
    }

    static java.util.List<Field> resolveMotionFlagsFromRefresh(
            Class<?> stack, DexSignatures.Body refreshBody, Names names)
            throws ReflectiveOperationException {
        if (refreshBody != null) {
            String stackDesc = DexSignatures.descriptor(stack);
            java.util.List<String> distinctBools = new java.util.ArrayList<>();
            refreshBody.reads().stream()
                    .filter(
                            read ->
                                    stackDesc.equals(read.field().owner())
                                            && "Z".equals(read.field().type()))
                    .sorted(java.util.Comparator.comparingInt(DexSignatures.Read::offset))
                    .forEach(
                            read -> {
                                String name = read.field().name();
                                if (!distinctBools.contains(name)) distinctBools.add(name);
                            });
            if (distinctBools.size() < 3) {
                throw new NoSuchFieldException("NowBarCardStackView drag/reorder/dismiss fields");
            }
            return java.util.List.of(
                    NativeContract.field(stack, distinctBools.get(0), boolean.class),
                    NativeContract.field(stack, distinctBools.get(1), boolean.class),
                    NativeContract.field(stack, distinctBools.get(2), boolean.class));
        }
        return java.util.List.of(
                NativeContract.field(stack, names.dragging, boolean.class),
                NativeContract.field(stack, names.reordering, boolean.class),
                NativeContract.field(stack, names.dismissing, boolean.class));
    }

    static String resolveExpandedDelegateKey(String stackDesc, DexSignatures.Body setterBody) {
        java.util.Set<String> delegates = new java.util.LinkedHashSet<>();
        for (var read : setterBody.reads()) {
            // Skip synthetic KProperty[] metadata arrays shared with unrelated delegate getters.
            if (stackDesc.equals(read.field().owner())
                    && !read.field().type().startsWith("[")) {
                delegates.add(read.field().name() + ":" + read.field().type());
            }
        }
        return delegates.size() == 1 ? delegates.iterator().next() : null;
    }

    private static Method resolveExpanded(Class<?> stack) throws ReflectiveOperationException {
        try {
            Method setter =
                    NativeContract.findMethod(
                            stack, "setExpandedCardState", void.class, boolean.class);
            String stackDesc = DexSignatures.descriptor(stack);
            String delegate = resolveExpandedDelegateKey(stackDesc, DexSignatures.body(setter));
            if (delegate != null) {
                java.util.List<Method> matches = new java.util.ArrayList<>();
                for (Method m : stack.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                            || m.getReturnType() != boolean.class
                            || m.getParameterCount() != 0) continue;
                    for (var read : DexSignatures.body(m).reads()) {
                        if (stackDesc.equals(read.field().owner())
                                && delegate.equals(read.field().name() + ":" + read.field().type())) {
                            m.setAccessible(true);
                            matches.add(m);
                            break;
                        }
                    }
                }
                if (matches.size() == 1) return matches.get(0);
                for (Method m : matches) if ("k".equals(m.getName())) return m;
                if (!matches.isEmpty()) return matches.get(0);
            }
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "k", boolean.class);
    }

    private static Method resolveSettingOpen(Class<?> stack) throws ReflectiveOperationException {
        try {
            String stackDesc = DexSignatures.descriptor(stack);
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != boolean.class
                        || m.getParameterCount() != 0) continue;
                boolean callsGetTopCard =
                        DexSignatures.body(m).calls().stream()
                                .anyMatch(
                                        call ->
                                                !call.isStatic()
                                                        && stackDesc.equals(call.owner())
                                                        && "getTopCard".equals(call.name())
                                                        && call.parameters().isEmpty()
                                                        && "Landroid/view/View;"
                                                                .equals(call.result()));
                if (callsGetTopCard) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() == 1) return matches.get(0);
            for (Method m : matches) if ("n".equals(m.getName())) return m;
            if (!matches.isEmpty()) return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "n", boolean.class);
    }

    private static Method resolveAnimate(
            Class<?> manager, Class<?> model, Names names) throws ReflectiveOperationException {
        if (names.countedAnimate) {
            try {
                return NativeContract.findMethod(
                        manager, names.animate, void.class, View.class, model, int.class);
            } catch (NoSuchMethodException fallback) {
                return NativeContract.findMethod(
                        manager, names.animate, void.class, View.class, model);
            }
        }
        try {
            return NativeContract.findMethod(
                    manager, names.animate, void.class, View.class, model);
        } catch (NoSuchMethodException fallback) {
            return NativeContract.findMethod(
                    manager, names.animate, void.class, View.class, model, int.class);
        }
    }

    private static Method resolveHideContents(Class<?> card, String fallbackName)
            throws ReflectiveOperationException {
        String anchor = "hideContents depth: ";
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : card.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != void.class
                        || m.getParameterCount() != 2
                        || m.getParameterTypes()[0] != int.class
                        || m.getParameterTypes()[1] != boolean.class) continue;
                if (DexSignatures.body(m, java.util.Set.of(anchor)).anchors().containsKey(anchor)) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBar card hideContents method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(card, fallbackName, void.class, int.class, boolean.class);
    }

    static Constructor<?> resolveStackSize(
            ClassLoader loader,
            Class<?> stack,
            Class<?> card,
            DexSignatures.Body refreshBody,
            String fallbackClass)
            throws ReflectiveOperationException {
        return resolveStackSize(loader, stack, card, refreshBody, null, fallbackClass);
    }

    static Constructor<?> resolveStackSize(
            ClassLoader loader,
            Class<?> stack,
            Class<?> card,
            DexSignatures.Body refreshBody,
            DexSignatures.Body makeCardBody,
            String fallbackClass)
            throws ReflectiveOperationException {
        Class<?> samType = null;
        for (Method m : card.getDeclaredMethods()) {
            if ("setGetStackSize".equals(m.getName()) && m.getParameterCount() == 1) {
                samType = m.getParameterTypes()[0];
                break;
            }
        }
        String stackDesc = DexSignatures.descriptor(stack);
        java.util.Set<Class<?>> types = new java.util.LinkedHashSet<>();
        for (DexSignatures.Body body : new DexSignatures.Body[] {refreshBody, makeCardBody}) {
            if (body == null) continue;
            for (var call : body.calls()) {
                if (!"<init>".equals(call.name())
                        || !call.parameters().equals(java.util.List.of(stackDesc, "I"))) {
                    continue;
                }
                String ownerName = classNameFromDescriptor(call.owner());
                if (ownerName == null) continue;
                Class<?> candidate = Class.forName(ownerName, false, loader);
                if ((samType == null || samType.isAssignableFrom(candidate))
                        && NativeContract.hasConstructor(candidate, stack, int.class)
                        && NativeContract.hasMethod(candidate, Object.class)) {
                    types.add(candidate);
                }
            }
        }
        if (types.size() == 1) {
            Constructor<?> ctor =
                    types.iterator().next().getDeclaredConstructor(stack, int.class);
            ctor.setAccessible(true);
            return ctor;
        }
        Class<?> sam = samType;
        Class<?> stackSizeClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                (sam == null || sam.isAssignableFrom(cls))
                                        && NativeContract.hasConstructor(cls, stack, int.class)
                                        && NativeContract.hasMethod(cls, Object.class),
                        fallbackClass,
                        "aod.px4");
        Constructor<?> ctor = stackSizeClass.getDeclaredConstructor(stack, int.class);
        ctor.setAccessible(true);
        return ctor;
    }

    static final String DEPTH_ANCHOR = "getCardDepthFromView exception ";
    static final String WAVE_ANCHOR = "updateWaveDisplay, isDozing = ";

    static boolean isWaveBody(DexSignatures.Body body) {
        return (body.access() & java.lang.reflect.Modifier.STATIC) == 0
                && "V".equals(body.result())
                && body.parameters().isEmpty()
                && body.anchors().containsKey(WAVE_ANCHOR);
    }

    static boolean isDepthBody(DexSignatures.Body body) {
        return (body.access() & java.lang.reflect.Modifier.STATIC) == 0
                && "I".equals(body.result())
                && body.parameters().equals(List.of("Landroid/view/View;", "Z"))
                && body.anchors().containsKey(DEPTH_ANCHOR);
    }

    private static Method resolveWave(ClassLoader loader, String fallbackName)
            throws ReflectiveOperationException {
        if (fallbackName == null) return null;
        Class<?> media =
                Class.forName(
                        "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView",
                        false,
                        loader);
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : media.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != void.class
                        || m.getParameterCount() != 0) continue;
                if (isWaveBody(DexSignatures.body(m, java.util.Set.of(WAVE_ANCHOR)))) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBarMediaCardView updateWaveDisplay method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        try {
            return NativeContract.method(media, fallbackName, void.class);
        } catch (NoSuchMethodException renamed) {
            return NativeContract.findMethod(media, "w0", void.class);
        }
    }

    private static Method resolveScale(Class<?> stack) throws ReflectiveOperationException {
        String anchor = "getCardInitialScale Not defined index ";
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != float.class
                        || m.getParameterCount() != 1
                        || m.getParameterTypes()[0] != int.class) continue;
                if (DexSignatures.body(m, java.util.Set.of(anchor)).anchors().containsKey(anchor)) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBarCardStackView scale method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "f", float.class, int.class);
    }

    private static Method resolveOffset(Class<?> stack) throws ReflectiveOperationException {
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != float.class
                        || m.getParameterCount() != 1
                        || m.getParameterTypes()[0] != int.class) continue;
                boolean readsDimen =
                        DexSignatures.body(m).calls().stream()
                                .anyMatch(
                                        call ->
                                                "Landroid/content/res/Resources;"
                                                                .equals(call.owner())
                                                        && "getDimensionPixelSize"
                                                                .equals(call.name()));
                if (readsDimen) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBarCardStackView offset method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "g", float.class, int.class);
    }

    private static Method resolveDepth(Class<?> stack) throws ReflectiveOperationException {
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != int.class
                        || m.getParameterCount() != 2
                        || m.getParameterTypes()[0] != View.class
                        || m.getParameterTypes()[1] != boolean.class) continue;
                if (isDepthBody(DexSignatures.body(m, java.util.Set.of(DEPTH_ANCHOR)))) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBarCardStackView depth method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "e", int.class, View.class, boolean.class);
    }

    private static Method resolveVisibilityByExpandState(Class<?> stack)
            throws ReflectiveOperationException {
        String anchor = "updateCardVisibilityByExpandState ";
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != void.class
                        || m.getParameterCount() != 0) continue;
                if (DexSignatures.body(m, java.util.Set.of(anchor)).anchors().containsKey(anchor)) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException(
                        "NowBarCardStackView updateCardVisibilityByExpandState method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.method(stack, "z", void.class);
    }

    private static Method resolveCardBlurAlpha(Class<?> stack)
            throws ReflectiveOperationException {
        String anchor = "updateCardAlpha ";
        try {
            java.util.List<Method> matches = new java.util.ArrayList<>();
            for (Method m : stack.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != void.class
                        || m.getParameterCount() != 2
                        || m.getParameterTypes()[0] != float.class
                        || m.getParameterTypes()[1] != View.class) continue;
                DexSignatures.Body body = DexSignatures.body(m, java.util.Set.of(anchor));
                boolean setsDrawableAlpha =
                        body.calls().stream()
                                .anyMatch(
                                        call ->
                                                "Landroid/graphics/drawable/Drawable;"
                                                                .equals(call.owner())
                                                        && "setAlpha".equals(call.name()));
                if (body.anchors().containsKey(anchor) && setsDrawableAlpha) {
                    m.setAccessible(true);
                    matches.add(m);
                }
            }
            if (matches.size() != 1) {
                throw new NoSuchMethodException("NowBarCardStackView cardBlurAlpha method");
            }
            return matches.get(0);
        } catch (java.io.IOException ignored) {
        }
        return NativeContract.findStaticMethod(stack, "y", void.class, float.class, View.class);
    }

    static Method cardBlurAlpha(Class<?> stack) throws ReflectiveOperationException {
        return resolveCardBlurAlpha(stack);
    }

    static Class<?> resolveModelClass(ClassLoader loader, Class<?> card, Names names)
            throws ClassNotFoundException {
        try {
            Class<?> candidate = card.getDeclaredMethod("getShowHideAnimationData").getReturnType();
            if (NativeContract.hasField(candidate, Animator.class)) return candidate;
        } catch (NoSuchMethodException ignored) {
        }
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasField(cls, Animator.class)
                                && NativeContract.hasMethod(card, cls),
                names.model,
                "aod.kx4");
    }

    static Class<?> resolveManagerClass(
            ClassLoader loader, Class<?> stack, Class<?> model, Names names)
            throws ClassNotFoundException {
        Class<?> matched = null;
        for (Field field : stack.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (type.isPrimitive() || type == Object.class) continue;
            if (NativeContract.hasMethod(type, void.class, View.class, model, int.class)
                    || NativeContract.hasMethod(type, void.class, View.class, model)) {
                if (matched != null && matched != type) {
                    matched = null;
                    break;
                }
                matched = type;
            }
        }
        if (matched != null) return matched;
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasField(stack, cls)
                                && (NativeContract.hasMethod(
                                                cls, void.class, View.class, model, int.class)
                                        || NativeContract.hasMethod(
                                                cls, void.class, View.class, model)),
                names.manager,
                "aod.cy6");
    }

    NowBarStackBinding(ClassLoader loader, Names names) throws ReflectiveOperationException {
        stack =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.NowBarCardStackView",
                        false,
                        loader);
        Class<?> itemType =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                        false,
                        loader);
        card =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasMethod(cls, ViewGroup.class)
                                        && NativeContract.hasMethod(cls, itemType),
                        "com.samsung.android.app.facewidget.presentation.a");
        model = resolveModelClass(loader, card, names);
        Class<?> manager = resolveManagerClass(loader, stack, model, names);
        animationManager = NativeContract.findField(stack, names.animationManager, manager);
        animationsEnabled =
                NativeContract.findField(manager, names.animationsEnabled, boolean.class);
        state = NativeContract.findField(model, "a", Enum.class);
        animator = NativeContract.findField(model, "f", Animator.class);
        content = NativeContract.findMethod(card, "getNormalContentRootView", ViewGroup.class);
        data = NativeContract.findMethod(card, "getShowHideAnimationData", model);
        item = NativeContract.findMethod(card, "getNowbarItem", itemType);
        blur = NativeContract.findMethod(card, "getBlurContainer", View.class);
        bindItem =
                NativeContract.findMethod(
                        card, names.bindItem, void.class, itemType, Integer.class);
        resources = names.resources;

        record Resolved(
                Field items,
                Field restricted,
                Field mode,
                Field screenOn,
                Field dragging,
                Field reordering,
                Field dismissing,
                Field expanding,
                Method refresh,
                Method animate,
                Method expanded,
                Method hideContents,
                Method key,
                Constructor<?> stackSize,
                Method wave,
                Method scale,
                Method offset,
                Method depth,
                Method visibilityByExpandState,
                Method cardBlurAlpha) {}
        Resolved resolved;
        try {
            resolved =
                    DexSignatures.cached(
                            () -> {
                                Field itemsField = resolveItems(stack, names.items);
                                Field restrictedField =
                                        resolveRestricted(stack, names.restricted);
                                Field modeField = resolveMode(stack, names.mode);
                                Method refreshMethod = resolveRefresh(stack, itemType);
                                DexSignatures.Body refreshBody = null;
                                try {
                                    refreshBody = DexSignatures.body(refreshMethod);
                                } catch (java.io.IOException ignored) {
                                }
                                DexSignatures.Body makeCardBody =
                                        refreshMethod.getParameterCount() == 2
                                                ? resolveMakeCardBody(stack, itemType)
                                                : null;
                                Field screenOnField =
                                        makeCardBody != null
                                                ? resolveBetaDozing(stack, card, makeCardBody)
                                                : resolveScreenOnFromRefresh(
                                                        stack, refreshBody, names.screenOn);
                                java.util.List<Field> flags =
                                        resolveMotionFlagsFromRefresh(
                                                stack, refreshBody, names);
                                Field expandingField = resolveExpanding(stack, names.expanding);
                                Method animateMethod = resolveAnimate(manager, model, names);
                                Method expandedMethod = resolveExpanded(stack);
                                Method hideContentsMethod =
                                        resolveHideContents(card, names.hideContents);
                                Method keyMethod = null;
                                try {
                                    Method listMethod =
                                            stack.getDeclaredMethod("getCurrentCardViewInfoList");
                                    String itemDesc = DexSignatures.descriptor(itemType);
                                    java.util.Set<Method> candidates =
                                            new java.util.LinkedHashSet<>();
                                    for (var call : DexSignatures.body(listMethod).calls()) {
                                        if (call.isStatic()
                                                && "Ljava/lang/String;".equals(call.result())
                                                && call.parameters()
                                                        .equals(java.util.List.of(itemDesc))) {
                                            Method candidate = call.method(loader);
                                            candidate.setAccessible(true);
                                            candidates.add(candidate);
                                        }
                                    }
                                    if (candidates.size() == 1) {
                                        keyMethod = candidates.iterator().next();
                                    }
                                } catch (ReflectiveOperationException
                                        | java.io.IOException ignored) {
                                }
                                if (keyMethod == null) {
                                    keyMethod = resolveKey(stack, itemType, names);
                                }
                                Constructor<?> stackSizeCtor =
                                        resolveStackSize(
                                                loader,
                                                stack,
                                                card,
                                                refreshBody,
                                                makeCardBody,
                                                names.stackSize);
                                Method waveMethod = resolveWave(loader, names.wave);
                                Method scaleMethod = resolveScale(stack);
                                Method offsetMethod = resolveOffset(stack);
                                Method depthMethod = resolveDepth(stack);
                                Method visibilityMethod = resolveVisibilityByExpandState(stack);
                                Method blurAlphaMethod = resolveCardBlurAlpha(stack);
                                return new Resolved(
                                        itemsField,
                                        restrictedField,
                                        modeField,
                                        screenOnField,
                                        flags.get(0),
                                        flags.get(1),
                                        flags.get(2),
                                        expandingField,
                                        refreshMethod,
                                        animateMethod,
                                        expandedMethod,
                                        hideContentsMethod,
                                        keyMethod,
                                        stackSizeCtor,
                                        waveMethod,
                                        scaleMethod,
                                        offsetMethod,
                                        depthMethod,
                                        visibilityMethod,
                                        blurAlphaMethod);
                            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (NoSuchMethodException)
                    new NoSuchMethodException("NowBar stack binding").initCause(error);
        }
        items = resolved.items();
        restricted = resolved.restricted();
        mode = resolved.mode();
        screenOn = resolved.screenOn();
        dragging = resolved.dragging();
        reordering = resolved.reordering();
        dismissing = resolved.dismissing();
        expanding = resolved.expanding();
        refresh = resolved.refresh();
        animate = resolved.animate();
        expanded = resolved.expanded();
        hideContents = resolved.hideContents();
        key = resolved.key();
        stackSize = resolved.stackSize();
        wave = resolved.wave();
        scale = resolved.scale();
        offset = resolved.offset();
        depth = resolved.depth();
        visibilityByExpandState = resolved.visibilityByExpandState();
        cardBlurAlpha = resolved.cardBlurAlpha();
    }

    private static Method resolveKey(Class<?> stack, Class<?> itemType, Names names)
            throws ReflectiveOperationException {
        ClassLoader loader = stack.getClassLoader();
        Class<?> known =
                NativeContract.knownClass(
                        loader,
                        type -> NativeContract.hasStaticMethod(type, String.class, itemType),
                        names.keys);
        if (known != null)
            return NativeContract.findStaticMethod(known, names.key, String.class, itemType);
        var matches = new java.util.LinkedHashSet<Method>();
        String item = DexSignatures.descriptor(itemType);
        try {
            // Follow the stack's own identity lookup. NowBarItem itself may come from
            // SystemUI's parent loader, which cannot see the provider's callers.
            String anchor = "getCurrentCardViewInfoList cardDepth=";
            for (var body : DexSignatures.find(loader, Set.of(anchor))) {
                if (!body.owner().equals(DexSignatures.descriptor(stack))
                        || !body.parameters().isEmpty()
                        || !body.result().equals("Ljava/util/List;")) continue;
                for (var call : body.calls()) {
                    if (call.isStatic() && call.parameters().equals(List.of(item))
                            && call.result().equals("Ljava/lang/String;")) {
                        matches.add(call.method(loader));
                    }
                }
            }
            if (matches.size() == 1) return matches.iterator().next();
            if (matches.size() > 1) throw new NoSuchMethodException("Ambiguous stack card identity call");
            for (DexSignatures.Body body :
                    DexSignatures.callers(loader, itemType, "getNowBarKey", String.class)) {
                if ((body.access() & 8) == 0
                        || !body.result().equals("Ljava/lang/String;")
                        || !body.parameters().equals(java.util.List.of(item))) continue;
                var getters = new java.util.HashSet<String>();
                for (DexSignatures.Call call : body.calls())
                    if (!call.isStatic()
                            && call.owner().equals(item)
                            && call.parameters().isEmpty()
                            && call.result().equals("Ljava/lang/String;")) getters.add(call.name());
                // Card identity combines these three values. Package names and debug
                // labels have the same outer signature but read a different set.
                if (getters.containsAll(
                        java.util.Set.of("getNotiID", "getNowBarKey", "getNowBarPackage")))
                    matches.add(body.method(loader));
            }
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read Now Bar identity signature", invalidDex);
        }
        if (matches.isEmpty()) throw new NoSuchMethodException("Now Bar identity helper unavailable");
        if (matches.size() != 1)
            throw new NoSuchMethodException("Ambiguous Now Bar identity: " + matches.size());
        return matches.iterator().next();
    }
}
