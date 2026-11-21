package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class OneUi9MediaBinding {
    private static final String MUSIC_PAGE_CLASS =
            "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage";
    private static final String MUSIC_VM_CLASS =
            "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicViewModel";
    private static final String MASK_CLASS =
            "com.samsung.android.uniform.nowbar.MediaMaskingFrameLayout";
    private static final String MASK_DESCRIPTOR =
            "Lcom/samsung/android/uniform/nowbar/MediaMaskingFrameLayout;";
    private static final String LIGHTING_CLASS =
            "com.samsung.android.uniform.nowbar.MediaLightingView";
    private static final String CARD_CLASS =
            "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView";
    private static final String PAGE_CLASS =
            "com.samsung.android.app.facewidget.pages.FaceWidgetPage";
    private static final String CARD_VM_DESCRIPTOR =
            "Lcom/samsung/android/app/facewidget/presentation/NowBarCardViewModel;";

    private static final String SHOW_HIDE_ANCHOR = "showHideMusicPageWithAnimation isShow = ";
    private static final String REVEAL_ANCHOR = "revealAnimation";
    private static final String LIGHTING_ANCHOR =
            "Canceled pending color change due to new animation request";
    private static final String CARD_STATE_ANCHOR = "onExpandStateChanged expand ";
    private static final String CANCEL_ANIMATES_ANCHOR = "cancelAnimates";
    private static final String SPRING_DESCRIPTOR =
            "Landroidx/dynamicanimation/animation/SpringAnimation;";
    private static final String EXPAND_TARGET_ANCHOR = "startAlbumArtExpandAnimation,  targetView is null";
    private static final String COLLAPSE_GEOMETRY_ANCHOR =
            "runCollapseAnimation,  sourceParent is null";
    private static final String COLLAPSE_CLEANUP_ANCHOR =
            "startAlbumArtCollapseAnimation, runCollapseAnimation(), runCollapseCleanup()";
    private static final String EXPANSION_CLEANUP_ANCHOR =
            "startAlbumArtExpandAnimation,  runAnimation() runCleanup()  = ";
    private static final String PILL_BOUNDS_ANCHOR = "isInContentsArea x = ";

    private static final String[] HOLDER_CANDIDATES = {
        "aod.fa2", "aod.u92", "aod.ga2", "aod.qr1"
    };
    private static final String[] COORDINATOR_CANDIDATES = {
        "aod.ag4", "aod.wf4", "aod.bg4", "aod.wq3"
    };
    private static final String[] ANIMATOR_LISTENER_CANDIDATES = {
        "aod.o8", "aod.k8", "aod.k20"
    };

    private static final Map<ClassLoader, HolderContract> HOLDERS = new WeakHashMap<>();
    private static final Map<ClassLoader, CoordinatorContract> COORDINATORS = new WeakHashMap<>();
    private static final Map<ClassLoader, Method> ANIMATORS = new WeakHashMap<>();
    private static final Map<ClassLoader, CardContract> CARDS = new WeakHashMap<>();

    record HolderContract(
            Class<?> type,
            Field bitmap,
            Field controls,
            Field controlHost,
            Field titleArtist,
            Field mask,
            Field lighting,
            Field albumArtParent,
            Field albumArtBg,
            Field prevAlbumArt,
            Field showing,
            Field viewModel,
            Method viewModelExpand) {}

    record CoordinatorContract(Class<?> type, Field mask, Method reveal, Method lighting) {}

    record CardContract(
            Class<?> cardType,
            Method cardStateChanged,
            Field expandedMediaView,
            Method collapseGeometry,
            Method collapseCleanup,
            Method expansionCleanup,
            Method expandInvoke,
            Field expandCaseField,
            int expandCase,
            Field expandPage,
            Field expandOverlay,
            Method springCancel,
            List<Field> cardSpringFields,
            List<Field> cardAnimatorFields) {
        CardContract(
                Class<?> cardType,
                Method cardStateChanged,
                Field expandedMediaView,
                Method collapseGeometry,
                Method collapseCleanup,
                Method expansionCleanup,
                Method expandInvoke,
                Field expandCaseField,
                int expandCase,
                Field expandPage,
                Field expandOverlay) {
            this(
                    cardType,
                    cardStateChanged,
                    expandedMediaView,
                    collapseGeometry,
                    collapseCleanup,
                    expansionCleanup,
                    expandInvoke,
                    expandCaseField,
                    expandCase,
                    expandPage,
                    expandOverlay,
                    null,
                    List.of(),
                    List.of());
        }
    }

    private OneUi9MediaBinding() {}

    static Class<?> holder(ClassLoader loader) throws ReflectiveOperationException {
        return holderContract(loader).type();
    }

    static HolderContract holderContract(ClassLoader loader) throws ReflectiveOperationException {
        return cached(HOLDERS, loader, "Now Bar media holder", () -> resolveHolderContract(loader));
    }

    static Class<?> coordinator(ClassLoader loader) throws ReflectiveOperationException {
        return coordinatorContract(loader).type();
    }

    static CoordinatorContract coordinatorContract(ClassLoader loader)
            throws ReflectiveOperationException {
        return cached(
                COORDINATORS,
                loader,
                "Now Bar media coordinator",
                () -> resolveCoordinatorContract(loader));
    }

    static Method animatorUpdate(ClassLoader loader) throws ReflectiveOperationException {
        return cached(
                ANIMATORS,
                loader,
                "Now Bar reveal animator update",
                () -> resolveAnimatorUpdate(loader));
    }

    static Method expandInvoke(ClassLoader loader) throws ReflectiveOperationException {
        return cardContract(loader).expandInvoke();
    }

    static CardContract cardContract(ClassLoader loader) throws ReflectiveOperationException {
        return cached(CARDS, loader, "Now Bar media card", () -> resolveCardContract(loader));
    }

    static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Inaccessible media field " + field.getName(), error);
        }
    }

    private interface Resolver<T> {
        T resolve() throws ReflectiveOperationException, IOException;
    }

    private static <T> T cached(
            Map<ClassLoader, T> cache, ClassLoader loader, String description, Resolver<T> work)
            throws ReflectiveOperationException {
        synchronized (cache) {
            T existing = cache.get(loader);
            if (existing != null) return existing;
        }
        T resolved;
        try {
            resolved = DexSignatures.cached(work::resolve);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (ClassNotFoundException)
                    new ClassNotFoundException(description + " resolution failed").initCause(error);
        }
        synchronized (cache) {
            T existing = cache.get(loader);
            if (existing != null) return existing;
            cache.put(loader, resolved);
            return resolved;
        }
    }

    private static Class<?> resolveHolderClass(
            ClassLoader loader,
            Class<?> page,
            Class<?> model,
            Class<?> mask,
            Class<?> lighting)
            throws ReflectiveOperationException, IOException {
        java.util.function.Predicate<Class<?>> match =
                type ->
                        NativeContract.hasField(type, page)
                                && NativeContract.hasField(type, model)
                                && NativeContract.hasField(type, mask)
                                && NativeContract.hasField(type, lighting)
                                && NativeContract.hasField(type, Bitmap.class);
        try {
            return NativeContract.findClass(loader, match, HOLDER_CANDIDATES);
        } catch (ClassNotFoundException ignored) {
            // Fall through to full-DEX anchor scan when obfuscation renames the holder.
        }
        Set<Class<?>> found = new LinkedHashSet<>();
        for (var body : DexSignatures.find(loader, Set.of(SHOW_HIDE_ANCHOR))) {
            Class<?> candidate = loadDescriptor(loader, body.owner());
            if (match.test(candidate)) found.add(candidate);
        }
        if (found.size() == 1) return found.iterator().next();
        throw new ClassNotFoundException("No unique Now Bar media holder class");
    }

    private static HolderContract resolveHolderContract(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        Class<?> page = Class.forName(MUSIC_PAGE_CLASS, false, loader);
        Class<?> model = Class.forName(MUSIC_VM_CLASS, false, loader);
        Class<?> maskClass = Class.forName(MASK_CLASS, false, loader);
        Class<?> lightingClass = Class.forName(LIGHTING_CLASS, false, loader);
        Class<?> holder = resolveHolderClass(loader, page, model, maskClass, lightingClass);

        Constructor<?> holderCtor = findHolderConstructor(holder, page, model);
        Map<String, DexSignatures.Member> idFields =
                DexSignatures.staticReadWrites(holderCtor, holder);

        Method showHide = findAnchoredMethod(holder, void.class, SHOW_HIDE_ANCHOR, boolean.class);
        String holderDesc = descriptor(holder);
        Field showing =
                DexSignatures.body(showHide, Set.of(SHOW_HIDE_ANCHOR))
                        .uniqueWrite(holderDesc, "Z", null)
                        .field(loader);
        showing.setAccessible(true);

        Method viewModelExpand = findViewModelExpand(model);

        return new HolderContract(
                holder,
                uniqueExactField(holder, Bitmap.class),
                idField(loader, idFields, "facewidget_music_controller_content_container"),
                idField(loader, idFields, "facewidget_music_container_expand_Player"),
                idField(loader, idFields, "facewidget_music_page_title_artist_container"),
                uniqueExactField(holder, maskClass),
                uniqueExactField(holder, lightingClass),
                idField(loader, idFields, "facewidget_album_art_vi_container_parent"),
                idField(loader, idFields, "facewidget_album_art_background"),
                idField(loader, idFields, "now_bar_media_prev_album_art"),
                showing,
                uniqueExactField(holder, model),
                viewModelExpand);
    }

    private static Constructor<?> findHolderConstructor(
            Class<?> holder, Class<?> page, Class<?> model) throws NoSuchMethodException {
        List<Constructor<?>> matches = new ArrayList<>();
        for (Constructor<?> ctor : holder.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 4
                    && params[0] == Context.class
                    && params[1] == View.class
                    && params[2] == page
                    && params[3] == model) {
                matches.add(ctor);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        throw new NoSuchMethodException("Unique constructor not found on " + holder.getName());
    }

    private static Method findViewModelExpand(Class<?> model)
            throws ReflectiveOperationException, IOException {
        List<Method> matches = new ArrayList<>();
        for (Method method : model.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != void.class) {
                continue;
            }
            if (isViewModelExpandBody(DexSignatures.body(method))) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        throw new NoSuchMethodException("Unique expand method not found on " + model.getName());
    }

    static boolean isViewModelExpandBody(DexSignatures.Body body) {
        return !Modifier.isStatic(body.access())
                && body.parameters().isEmpty()
                && "V".equals(body.result())
                && body.calls().stream().anyMatch(call -> CARD_VM_DESCRIPTOR.equals(call.owner()));
    }

    private static Class<?> resolveCoordinatorClass(
            ClassLoader loader, Class<?> mask, Class<?> lighting)
            throws ReflectiveOperationException, IOException {
        java.util.function.Predicate<Class<?>> match =
                type ->
                        NativeContract.hasConstructor(
                                        type, Context.class, Handler.class, boolean.class)
                                && NativeContract.hasField(type, mask)
                                && NativeContract.hasField(type, lighting)
                                && NativeContract.hasMethod(type, void.class, Drawable.class)
                                && NativeContract.hasMethod(
                                        type,
                                        void.class,
                                        FrameLayout.class,
                                        lighting,
                                        mask,
                                        ImageView.class,
                                        ImageView.class);
        try {
            return NativeContract.findClass(loader, match, COORDINATOR_CANDIDATES);
        } catch (ClassNotFoundException ignored) {
            // Fall through to full-DEX anchor scan when obfuscation renames the coordinator.
        }
        Set<Class<?>> found = new LinkedHashSet<>();
        for (var body : DexSignatures.find(loader, Set.of(REVEAL_ANCHOR))) {
            Class<?> candidate = loadDescriptor(loader, body.owner());
            if (match.test(candidate)) found.add(candidate);
        }
        if (found.size() == 1) return found.iterator().next();
        throw new ClassNotFoundException("No unique Now Bar media coordinator class");
    }

    private static CoordinatorContract resolveCoordinatorContract(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        Class<?> lighting = Class.forName(LIGHTING_CLASS, false, loader);
        Class<?> maskClass = Class.forName(MASK_CLASS, false, loader);
        Class<?> coordinator = resolveCoordinatorClass(loader, maskClass, lighting);
        return new CoordinatorContract(
                coordinator,
                uniqueExactField(coordinator, maskClass),
                findAnchoredMethod(coordinator, void.class, REVEAL_ANCHOR, Drawable.class),
                findAnchoredMethod(coordinator, void.class, LIGHTING_ANCHOR, Drawable.class));
    }

    private static Method resolveAnimatorUpdate(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        CoordinatorContract coordinator = coordinatorContract(loader);
        Set<Method> matches = new LinkedHashSet<>();
        for (var call : DexSignatures.body(coordinator.reveal()).calls()) {
            if (!"<init>".equals(call.name())
                    || !call.parameters().equals(List.of("I", "Ljava/lang/Object;"))) {
                continue;
            }
            Class<?> candidate = loadDescriptor(loader, call.owner());
            Method update = findMaskAnimatorUpdate(candidate);
            if (update != null) matches.add(update);
        }
        if (matches.size() == 1) return matches.iterator().next();

        for (String name : ANIMATOR_LISTENER_CANDIDATES) {
            try {
                Class<?> candidate = Class.forName(name, false, loader);
                if (!NativeContract.hasConstructor(candidate, int.class, Object.class)) continue;
                Method update = findMaskAnimatorUpdate(candidate);
                if (update != null) matches.add(update);
            } catch (ClassNotFoundException ignored) {
                // Candidate absent in this APK build.
            }
        }
        if (matches.size() == 1) return matches.iterator().next();
        throw new NoSuchMethodException("Unique Now Bar mask animator update listener not found");
    }

    private static Method findMaskAnimatorUpdate(Class<?> candidate)
            throws ReflectiveOperationException, IOException {
        if (!ValueAnimator.AnimatorUpdateListener.class.isAssignableFrom(candidate)) return null;
        try {
            Method update =
                    candidate.getDeclaredMethod("onAnimationUpdate", ValueAnimator.class);
            if (isMaskAnimatorUpdateListenerBody(DexSignatures.body(update))) {
                update.setAccessible(true);
                return update;
            }
        } catch (NoSuchMethodException ignored) {
            return null;
        }
        return null;
    }

    static boolean isMaskAnimatorUpdateListenerBody(DexSignatures.Body body) {
        return !Modifier.isStatic(body.access())
                && "onAnimationUpdate".equals(body.name())
                && "V".equals(body.result())
                && body.parameters().equals(List.of("Landroid/animation/ValueAnimator;"))
                && body.calls().stream()
                        .anyMatch(
                                call ->
                                        !call.isStatic()
                                                && MASK_DESCRIPTOR.equals(call.owner())
                                                && "setAnimationProgress".equals(call.name())
                                                && "V".equals(call.result())
                                                && call.parameters().equals(List.of("F")));
    }

    private static CardContract resolveCardContract(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        Class<?> card = Class.forName(CARD_CLASS, false, loader);
        Class<?> page = Class.forName(PAGE_CLASS, false, loader);
        Field expandedMediaView = uniqueExactField(card, page);
        Method cardStateChanged =
                findAnchoredMethod(card, void.class, CARD_STATE_ANCHOR, boolean.class);
        Method collapseGeometry =
                findAnchoredStaticMethod(card, void.class, COLLAPSE_GEOMETRY_ANCHOR, 9);
        Method collapseCleanup =
                findAnchoredStaticMethod(card, void.class, COLLAPSE_CLEANUP_ANCHOR, 7);
        Method expansionCleanup =
                findAnchoredStaticMethod(card, void.class, EXPANSION_CLEANUP_ANCHOR, 9, true);

        Class<?> expandClass = findExpandLambdaClass(loader, cardStateChanged);
        Method expandInvoke = NativeContract.findMethod(expandClass, "invoke", Object.class);
        expandInvoke.setAccessible(true);
        Constructor<?> lambdaCtor =
                expandClass.getDeclaredConstructor(
                        Object.class, Object.class, Object.class, Object.class, int.class);
        int expandCase = DexSignatures.constructorIntArgument(cardStateChanged, expandClass, 5, 4);

        DexSignatures.Body ctorBody = DexSignatures.constructor(lambdaCtor);
        String expandDesc = descriptor(expandClass);
        Field caseField = ctorBody.uniqueWrite(expandDesc, "I", null).field(loader);
        caseField.setAccessible(true);

        List<DexSignatures.Member> objectWrites = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (var write : ctorBody.writes()) {
            DexSignatures.Member member = write.field();
            if (expandDesc.equals(member.owner())
                    && "Ljava/lang/Object;".equals(member.type())
                    && seenNames.add(member.name())) {
                objectWrites.add(member);
            }
        }
        if (objectWrites.size() != 4) {
            throw new NoSuchFieldException(
                    "Expected 4 Object captures on " + expandClass.getName());
        }
        Field expandPage = objectWrites.get(1).field(loader);
        Field expandOverlay = objectWrites.get(2).field(loader);
        expandPage.setAccessible(true);
        expandOverlay.setAccessible(true);

        return new CardContract(
                card,
                cardStateChanged,
                expandedMediaView,
                collapseGeometry,
                collapseCleanup,
                expansionCleanup,
                expandInvoke,
                caseField,
                expandCase,
                expandPage,
                expandOverlay,
                findSpringCancel(loader, card),
                fieldsByTypeName(card, "androidx.dynamicanimation.animation.SpringAnimation"),
                fieldsByTypeName(card, "android.animation.ObjectAnimator"));
    }

    private static Method findSpringCancel(ClassLoader loader, Class<?> card) {
        try {
            Method cancel = findAnchoredMethod(card, void.class, CANCEL_ANIMATES_ANCHOR);
            for (var call : DexSignatures.body(cancel).calls()) {
                if (!call.isStatic()
                        && SPRING_DESCRIPTOR.equals(call.owner())
                        && "V".equals(call.result())
                        && call.parameters().isEmpty()) {
                    Method method = call.method(loader);
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (ReflectiveOperationException | IOException ignored) {
            // Optional on synthetic test fixtures.
        }
        return null;
    }

    private static List<Field> fieldsByTypeName(Class<?> owner, String typeName) {
        List<Field> matches = new ArrayList<>();
        for (Field field : owner.getDeclaredFields()) {
            if (typeName.equals(field.getType().getName())) {
                field.setAccessible(true);
                matches.add(field);
            }
        }
        return List.copyOf(matches);
    }

    private static Class<?> findExpandLambdaClass(ClassLoader loader, Method cardStateChanged)
            throws ReflectiveOperationException, IOException {
        List<String> fiveArgParams =
                List.of(
                        "Ljava/lang/Object;",
                        "Ljava/lang/Object;",
                        "Ljava/lang/Object;",
                        "Ljava/lang/Object;",
                        "I");
        Set<Class<?>> candidates = new LinkedHashSet<>();
        for (var call : DexSignatures.body(cardStateChanged).calls()) {
            if ("<init>".equals(call.name()) && call.parameters().equals(fiveArgParams)) {
                candidates.add(loadDescriptor(loader, call.owner()));
            }
        }
        Set<Class<?>> matching = new LinkedHashSet<>();
        for (Class<?> candidate : candidates) {
            try {
                Method invoke = NativeContract.findMethod(candidate, "invoke", Object.class);
                if (DexSignatures.body(invoke, Set.of(EXPAND_TARGET_ANCHOR))
                        .anchors()
                        .containsKey(EXPAND_TARGET_ANCHOR)) {
                    matching.add(candidate);
                }
            } catch (NoSuchMethodException ignored) {
                // Skip non-Function0 helper lambdas.
            }
        }
        if (matching.size() == 1) return matching.iterator().next();
        throw new ClassNotFoundException("Unique Now Bar expand lambda class not found");
    }

    private static Method findAnchoredMethod(
            Class<?> owner, Class<?> returnType, String anchor, Class<?>... params)
            throws ReflectiveOperationException, IOException {
        List<Method> matches = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != returnType
                    || !java.util.Arrays.equals(method.getParameterTypes(), params)) {
                continue;
            }
            if (DexSignatures.body(method, Set.of(anchor)).anchors().containsKey(anchor)) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        throw new NoSuchMethodException(
                "Anchor '" + anchor + "' not uniquely matched on " + owner.getName());
    }

    private static Method findAnchoredStaticMethod(
            Class<?> owner, Class<?> returnType, String anchor, int paramCount)
            throws ReflectiveOperationException, IOException {
        return findAnchoredStaticMethod(owner, returnType, anchor, paramCount, false);
    }

    private static Method findAnchoredStaticMethod(
            Class<?> owner, Class<?> returnType, String anchor, int paramCount,
            boolean extraExpansionContainer)
            throws ReflectiveOperationException, IOException {
        List<Method> matches = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != returnType) {
                continue;
            }
            if (method.getParameterCount() != paramCount
                    && !(extraExpansionContainer && OneUi95Aod.artworkCleanup(method, owner))) continue;
            if (DexSignatures.body(method, Set.of(anchor)).anchors().containsKey(anchor)) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        throw new NoSuchMethodException(
                "Static anchor '" + anchor + "' not uniquely matched on " + owner.getName());
    }

    private static Field uniqueExactField(Class<?> owner, Class<?> exactType)
            throws NoSuchFieldException {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() != exactType) continue;
            if (found != null) {
                throw new NoSuchFieldException(
                        "Multiple " + exactType.getName() + " fields on " + owner.getName());
            }
            found = field;
        }
        if (found == null) {
            throw new NoSuchFieldException(
                    "Missing " + exactType.getName() + " field on " + owner.getName());
        }
        found.setAccessible(true);
        return found;
    }

    private static Field idField(
            ClassLoader loader, Map<String, DexSignatures.Member> idFields, String idName)
            throws ReflectiveOperationException {
        DexSignatures.Member member = idFields.get(idName);
        if (member == null) {
            throw new NoSuchFieldException("No holder field mapped from R.id." + idName);
        }
        Field field = member.field(loader);
        field.setAccessible(true);
        return field;
    }

    private static Class<?> loadDescriptor(ClassLoader loader, String descriptor)
            throws ClassNotFoundException {
        String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        return Class.forName(name, false, loader);
    }

    private static String descriptor(Class<?> type) {
        return "L" + type.getName().replace('.', '/') + ";";
    }


    static Method pillBounds(ClassLoader loader) throws ReflectiveOperationException {
        try {
            return DexSignatures.cached(() -> resolvePillBounds(loader));
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (NoSuchMethodException)
                    new NoSuchMethodException("Now Bar pill bounds helper").initCause(error);
        }
    }

    private static Method resolvePillBounds(ClassLoader loader)
            throws ReflectiveOperationException {
        try {
            Class<?> viewModel =
                    Class.forName(
                            "com.samsung.android.app.facewidget.FaceWidgetViewModel",
                            false,
                            loader);
            Method isInContentBounds =
                    NativeContract.findMethod(
                            viewModel, "isInContentBounds", boolean.class, float.class, float.class);
            java.util.Set<Method> matches = new java.util.LinkedHashSet<>();
            for (var call : DexSignatures.body(isInContentBounds).calls()) {
                if (!call.isStatic()
                        || !"Z".equals(call.result())
                        || !call.parameters()
                                .equals(java.util.List.of("Landroid/view/View;", "I", "I"))) {
                    continue;
                }
                Method candidate = call.method(loader);
                if (isPillBoundsBody(
                        DexSignatures.body(candidate, java.util.Set.of(PILL_BOUNDS_ANCHOR)))) {
                    candidate.setAccessible(true);
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1) return matches.iterator().next();
        } catch (ReflectiveOperationException | java.io.IOException ignored) {
            // Fall through to full-DEX string scan if FaceWidgetViewModel or its method shifted.
        }
        try {
            java.util.Set<Method> matches = new java.util.LinkedHashSet<>();
            for (var body : DexSignatures.find(loader, java.util.Set.of(PILL_BOUNDS_ANCHOR))) {
                if (isPillBoundsBody(body)) {
                    Method method = body.method(loader);
                    method.setAccessible(true);
                    matches.add(method);
                }
            }
            if (matches.size() == 1) return matches.iterator().next();
        } catch (java.io.IOException error) {
            throw (NoSuchMethodException)
                    new NoSuchMethodException("Now Bar pill bounds helper").initCause(error);
        }
        throw new NoSuchMethodException("Now Bar pill bounds helper");
    }

    static boolean isPillBoundsBody(DexSignatures.Body body) {
        return java.lang.reflect.Modifier.isStatic(body.access())
                && "Z".equals(body.result())
                && body.parameters().equals(java.util.List.of("Landroid/view/View;", "I", "I"))
                && body.anchors().containsKey(PILL_BOUNDS_ANCHOR)
                && body.calls().stream()
                        .anyMatch(
                                call ->
                                        !call.isStatic()
                                                && "Landroid/graphics/Rect;".equals(call.owner())
                                                && "contains".equals(call.name())
                                                && "Z".equals(call.result())
                                                && call.parameters()
                                                        .equals(java.util.List.of("I", "I")));
    }
}
