package dev.denver.dressroomadvanced;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class NowBarGlass {
    static final String KEY = "nowBarGlass";
    private static final String PRESENTATION = "com.samsung.android.app.facewidget.presentation.";
    private static final String MEDIA = PRESENTATION + "NowBarMediaCardView";
    private static final String BRIEF = PRESENTATION + "NowBarBriefCardView";
    private static final String CARD_VIEW = "Landroidx/cardview/widget/CardView;";
    private static final Map<Class<?>, Boolean> installed = new WeakHashMap<>();
    private static final Map<View, Surface> surfaces = new WeakHashMap<>();
    private static final Map<View, Brief> briefs = new WeakHashMap<>();
    private static final PresetCache unattachedBriefCache = new PresetCache();
    private static final ThreadLocal<View> building = new ThreadLocal<>();
    private static volatile Binding activeBinding;
    private static volatile float lastBriefDark;
    private static final View.OnAttachStateChangeListener attach = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            view.postOnAnimation(() -> {
                Surface state = surfaces.get(view);
                if (state != null) update(view, state);
            });
        }

        @Override public void onViewDetachedFromWindow(View view) {
            Surface state = surfaces.remove(view);
            view.removeOnAttachStateChangeListener(this);
            if (state != null) restore(view, state);
        }
    };
    private static boolean observing;

    private record Binding(Method dark, Method getBlur, Method selectPreset, Constructor<?> builder,
            Method preset, Method corners, Method color, Method build, Method submit) {}

    private static final class PresetCache {
        int cachedPreset;
        int selectedPreset;
        long sampleUntil;
        Object presetInfo;

        int sample(Binding binding, android.content.Context context)
                throws ReflectiveOperationException {
            // Same default branch as Samsung's ordinary pill, with lockscreen wallpaper sampling.
            long now = android.os.SystemClock.uptimeMillis();
            if (now >= sampleUntil) {
                selectedPreset = invokePresetSelector(binding.selectPreset, context);
                sampleUntil = now + 100;
            }
            return selectedPreset;
        }

        Object requestInfo(Binding binding, android.content.Context context)
                throws ReflectiveOperationException {
            int preset = sample(binding, context);
            if (preset <= 0) return null;
            if (presetInfo == null || cachedPreset != preset) {
                presetInfo = request(binding, preset, 0f);
                cachedPreset = preset;
            }
            return presetInfo;
        }
    }

    private static final class Brief {
        final Binding binding;
        final Method refresh;
        final PresetCache cache = new PresetCache();
        boolean untinted;
        NowBriefOutline outline;

        Brief(Binding binding, Method refresh) {
            this.binding = binding;
            this.refresh = refresh;
        }
    }

    private static final class Surface {
        final WeakReference<View> card;
        final Binding binding;
        final boolean media;
        final PresetCache cache = new PresetCache();
        float radius;
        Drawable background, foreground, addedBlur;
        Object generatedRequest;
        float submittedRadius;
        int submittedPreset;
        boolean glassEnabled;

        Surface(View card, Binding binding, boolean media) {
            this.card = new WeakReference<>(card);
            this.binding = binding;
            this.media = media;
        }
    }

    private NowBarGlass() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess() || android.os.Build.VERSION.SDK_INT < 37) return;
        try {
            DexSignatures.cached(
                    () -> {
                        installNow(module, loader);
                        return null;
                    });
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void installNow(DressroomModule module, ClassLoader loader) {
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> media = Class.forName(MEDIA, false, loader);
            if (installed.containsKey(media)) return;
            Class<?> card = media.getSuperclass();
            Class<?> item = Class.forName(
                    "com.android.systemui.plugins.keyguardstatusview.NowBarItem", false, loader);
            Class<?> info = Class.forName("android.view.SemBlurInfo");
            Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
            Method background = bindBackground(card, item);
            Method selectPreset = bindPreset(background, loader);
            Binding binding = new Binding(NativeContract.method(card, "getDarkAmount", float.class),
                    card.getMethod("getBlurContainer"), selectPreset, builder.getConstructor(int.class),
                    builder.getMethod("setColorCurvePreset", int.class),
                    builder.getMethod("setBackgroundCornerRadius", float.class),
                    builder.getMethod("setBackgroundColor", int.class), builder.getMethod("build"),
                    View.class.getMethod("semSetBlurInfo", info));
            Method gradient = bindMediaGradient(media);
            hooks.add(module.hook(background).setId("dressroom-advanced.now-bar-glass-background")
                    .intercept(chain -> {
                        View owner = (View) chain.getThisObject();
                        View surface = (View) chain.getArg(1);
                        Object result = chain.proceed();
                        if (surface != null && !media.isInstance(owner)
                                && !owner.getClass().getName().equals(BRIEF)
                                && isPill(surface, "nowbar_cardview_background")) {
                            remember(owner, surface, binding, (Float) chain.getArg(2), false);
                        }
                        return result;
                    })::unhook);
            hooks.add(module.hook(gradient).setId("dressroom-advanced.now-bar-glass-media")
                    .intercept(chain -> {
                        View owner = (View) chain.getThisObject();
                        Object result = chain.proceed();
                        try {
                            View surface = (View) binding.getBlur.invoke(owner);
                            if (surface != null && isPill(surface, "stack_background_cardview"))
                                remember(owner, surface, binding, (Float) chain.getArg(1), true);
                        } catch (ReflectiveOperationException | RuntimeException unavailable) {
                            DressroomModule.note("Now Bar media glass", unavailable);
                        }
                        return result;
                    })::unhook);
            module.deoptimize(background);
            module.deoptimize(gradient);
            for (var caller : DexSignatures.callers(background)) {
                if (caller.owner().equals(DexSignatures.descriptor(card)))
                    module.deoptimize(caller.method(loader));
            }
            for (var caller : DexSignatures.callers(gradient)) {
                if (caller.owner().equals(DexSignatures.descriptor(media)))
                    module.deoptimize(caller.method(loader));
            }
            hooks.commit();
            installed.put(media, true);
            activeBinding = binding;
            installMediaMenu(module, media, binding);
            installBrief(module, loader, card, binding);
            try {
                NowBriefFill.install(module, loader);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                module.report("Optional Now Brief GenUI fill unavailable", unavailable);
            }
            if (!observing) {
                observing = true;
                RuntimeExtras.listen(NowBarGlass::refresh);
            }
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional Now Bar pill glass unavailable", unavailable);
        }
    }

    private static void installMediaMenu(DressroomModule module, Class<?> media, Binding binding) {
        try (HookTransaction hooks = new HookTransaction()) {
            Method menu = NativeContract.method(media, "setSettingBgColor", void.class, float.class);
            var body = DexSignatures.body(menu, Set.of("nowbar_key_media_bg_color_primary"));
            if (!body.anchors().containsKey("nowbar_key_media_bg_color_primary")
                    || body.calls().stream().noneMatch(call -> call.name().equals("setForeground")))
                throw new NoSuchMethodException("Now Bar media menu background signature");
            ClassLoader loader = media.getClassLoader();
            var background = body.uniqueRead(DexSignatures.descriptor(media),
                    "Landroid/widget/FrameLayout;", null).field(loader);
            hooks.add(module.hook(menu).setId("dressroom-advanced.now-bar-glass-media-menu")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        View card = (View) chain.getThisObject();
                        try {
                            View view = (View) background.get(card);
                            if (view != null && isPill(view, "nowbar_cardview_bgview_for_media_setting"))
                                remember(card, view, binding, (Float) chain.getArg(0), true);
                        } catch (IllegalAccessException unavailable) {
                            DressroomModule.note("Now Bar media menu background", unavailable);
                        }
                        return result;
                    })::unhook);
            module.deoptimize(menu);
            for (var caller : DexSignatures.callers(menu))
                if (!caller.name().equals("<init>")) module.deoptimize(caller.method(loader));
            hooks.commit();
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional Now Bar media menu glass unavailable", unavailable);
        }
    }

    private static void installBrief(
            DressroomModule module, ClassLoader loader, Class<?> card, Binding binding) {
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> type = Class.forName(BRIEF, false, loader);
            Method surface = card.getMethod("getBlurContainer");
            Method cardState = NativeContract.method(card, "getCardState", int.class);
            Method corner = NativeContract.method(card, "getCurrentRadius", float.class);
            Method border = NativeContract.method(card, "getBorderStrokeView", View.class);
            BriefBlur resolved = resolveBriefBlurMethods(type, card, loader);
            Method refresh = resolved.refresh();
            Method tint = resolved.tint();
            if (DexSignatures.body(tint).calls().stream().noneMatch(call ->
                    call.owner().equals(DexSignatures.descriptor(card)) && call.result().equals("I")
                            && call.parameters().equals(List.of("F", "F"))))
                throw new NoSuchMethodException("Now Brief background color signature");
            hooks.add(module.hook(tint).setId("dressroom-advanced.now-brief-clear-glass-tint")
                    .intercept(chain -> {
                        View owner = (View) chain.getThisObject();
                        Brief state = briefs.computeIfAbsent(owner, key -> new Brief(binding, refresh));
                        float darkAmount = (Float) chain.getArg(0);
                        lastBriefDark = darkAmount;
                        try {
                            state.untinted = glassAllowed(owner, darkAmount);
                        } catch (RuntimeException unavailable) {
                            state.untinted = false;
                        }
                        if (state.untinted && state.outline == null)
                            state.outline =
                                    new NowBriefOutline(
                                            owner, surface, cardState, corner, binding.dark, border);
                        else if (!state.untinted && state.outline != null) {
                            state.outline.close(owner);
                            state.outline = null;
                        }
                        // GenUI treats null as "keep the previous color". Zero explicitly clears it.
                        return state.untinted ? 0 : chain.proceed();
                    })::unhook);
            module.deoptimize(tint);
            for (var caller : DexSignatures.callers(tint))
                if (!caller.name().equals("<init>")) module.deoptimize(caller.method(loader));
            hooks.commit();
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional Now Brief glass tint unavailable", unavailable);
        }
    }

    private static Method bindBackground(Class<?> card, Class<?> item)
            throws ReflectiveOperationException, java.io.IOException {
        Method method = NativeContract.findMethod(card, null, void.class, item, View.class,
                float.class, int.class);
        var body = DexSignatures.body(method, Set.of("NowbarBlurUtils"));
        if (!body.anchors().containsKey("NowbarBlurUtils")
                || body.calls().stream().noneMatch(call -> call.owner().equals(CARD_VIEW)
                        && call.name().equals("setCardBackgroundColor")))
            throw new NoSuchMethodException("Now Bar pill background signature");
        return method;
    }

    private static Method bindMediaGradient(Class<?> media)
            throws ReflectiveOperationException, java.io.IOException {
        for (Method method : media.getDeclaredMethods()) {
            if (method.getReturnType() != void.class || !List.of(method.getParameterTypes())
                    .equals(List.of(int.class, float.class, int.class, int.class))) continue;
            var body = DexSignatures.body(method);
            boolean setsForeground = body.calls().stream().anyMatch(call ->
                    call.owner().equals("Landroid/view/View;") && call.name().equals("setForeground")
                            && call.parameters().equals(List.of("Landroid/graphics/drawable/Drawable;")));
            boolean roundsGradient = body.calls().stream().anyMatch(call ->
                    call.owner().equals("Landroid/graphics/drawable/GradientDrawable;")
                            && call.name().equals("setCornerRadius"));
            if (setsForeground && roundsGradient) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("Now Bar media gradient signature");
    }

    private static final List<String> PRESET_SELECTOR_5 =
            List.of("I", "Landroid/content/Context;", "Z", "Z", "Z");
    private static final List<String> PRESET_SELECTOR_4 =
            List.of("Landroid/content/Context;", "Z", "Z", "I");

    record BriefBlur(Method refresh, Method tint) {}

    static BriefBlur resolveBriefBlurMethods(
            Class<?> type, Class<?> card, ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        String style = "Lcom/samsung/android/genui/model/style/BlurStyle;";
        String typeDesc = DexSignatures.descriptor(type);
        String cardDesc = DexSignatures.descriptor(card);
        List<BriefBlur> matches = new java.util.ArrayList<>();
        for (Method candidate : type.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())
                    || candidate.getReturnType() != void.class
                    || candidate.getParameterCount() != 1
                    || candidate.getParameterTypes()[0] != float.class) continue;
            var body = DexSignatures.body(candidate);
            if (!isBriefRefreshBody(body, style)) continue;
            Set<Method> tints = new java.util.LinkedHashSet<>();
            for (var call : briefTintCalls(body, typeDesc)) tints.add(call.method(loader));
            if (tints.size() != 1) continue;
            Method tint = tints.iterator().next();
            if (isBriefTintBody(DexSignatures.body(tint), cardDesc)) {
                candidate.setAccessible(true);
                tint.setAccessible(true);
                matches.add(new BriefBlur(candidate, tint));
            }
        }
        if (matches.size() != 1) throw new NoSuchMethodException("Now Brief GenUI tint signature");
        return matches.get(0);
    }

    static boolean isBriefRefreshBody(DexSignatures.Body body, String styleDesc) {
        boolean buildsBlurStyle =
                body.calls().stream()
                        .anyMatch(
                                call ->
                                        call.owner().equals(styleDesc)
                                                && call.name().equals("<init>"));
        boolean updatesGenUi =
                body.calls().stream()
                        .anyMatch(
                                call ->
                                        call.name().equals("updateBlurStyle")
                                                && call.parameters().equals(List.of(styleDesc)));
        return buildsBlurStyle && updatesGenUi;
    }

    static List<DexSignatures.Call> briefTintCalls(
            DexSignatures.Body refreshBody, String typeDesc) {
        return refreshBody.calls().stream()
                .filter(
                        call ->
                                !call.isStatic()
                                        && call.owner().equals(typeDesc)
                                        && call.result().equals("I")
                                        && call.parameters().equals(List.of("F")))
                .toList();
    }

    static boolean isBriefTintBody(DexSignatures.Body tintBody, String cardDesc) {
        return tintBody.calls().stream()
                .anyMatch(
                        call ->
                                !call.isStatic()
                                        && call.owner().equals(cardDesc)
                                        && call.result().equals("I")
                                        && call.parameters().equals(List.of("F", "F")));
    }

    private static Method bindPreset(Method background, ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        return selectPresetMethod(DexSignatures.body(background).calls(), loader);
    }

    static Method selectPresetMethod(
            java.util.Collection<DexSignatures.Call> calls, ClassLoader loader)
            throws ReflectiveOperationException {
        Set<Method> selectors = new java.util.HashSet<>();
        for (var call : calls) {
            if (isPresetSelectorCall(call)) selectors.add(call.method(loader));
        }
        if (selectors.size() != 1) throw new NoSuchMethodException("Now Bar wallpaper preset selector");
        return selectors.iterator().next();
    }

    static boolean isPresetSelectorCall(DexSignatures.Call call) {
        return call.isStatic()
                && "I".equals(call.result())
                && (call.parameters().equals(PRESET_SELECTOR_5)
                        || call.parameters().equals(PRESET_SELECTOR_4));
    }

    static int invokePresetSelector(Method selectPreset, android.content.Context context)
            throws ReflectiveOperationException {
        Class<?>[] types = selectPreset.getParameterTypes();
        if (types.length == 5
                && types[0] == int.class
                && types[1] == android.content.Context.class
                && types[2] == boolean.class
                && types[3] == boolean.class
                && types[4] == boolean.class) {
            return (Integer) selectPreset.invoke(null, 62, context, false, false, false);
        }
        if (types.length == 4
                && types[0] == android.content.Context.class
                && types[1] == boolean.class
                && types[2] == boolean.class
                && types[3] == int.class) {
            return (Integer) selectPreset.invoke(null, context, false, false, 62);
        }
        throw new NoSuchMethodException(
                "Unsupported Now Bar wallpaper preset selector: " + selectPreset);
    }

    private static boolean isPill(View view, String resource) {
        try {
            return view.getId() != View.NO_ID
                    && resource.equals(view.getResources().getResourceEntryName(view.getId()));
        } catch (RuntimeException missing) {
            return false;
        }
    }

    private static void remember(View card, View view, Binding binding, float radius, boolean media) {
        Surface state = surfaces.get(view);
        if (state == null) {
            state = new Surface(card, binding, media);
            surfaces.put(view, state);
            view.addOnAttachStateChangeListener(attach);
        }
        state.radius = radius;
        update(view, state);
    }

    static Object glassPreset(View view) throws ReflectiveOperationException {
        Surface state = surfaces.get(view);
        if (state != null) {
            if (!enabled(view, state)) return null;
            return state.cache.requestInfo(state.binding, view.getContext());
        }
        return briefGlassPreset(view);
    }

    /** Resolves the wallpaper-sampled glass preset for a Now Brief GenUI blur FrameLayout. */
    private static Object briefGlassPreset(View view) throws ReflectiveOperationException {
        if (view == null) return null;
        View card = BlurAppearance.nowBarCardOwner(view);
        Brief owner = card == null ? null : briefs.get(card);
        if (owner != null) {
            if (!view.isAttachedToWindow() && !NowBriefFill.isBriefBlurView(view)) return null;
            float dark;
            try {
                dark = (Float) owner.binding.dark.invoke(card);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return null;
            }
            if (!glassAllowed(view, dark)) return null;
            return owner.cache.requestInfo(owner.binding, view.getContext());
        }
        if (!NowBriefFill.isBriefBlurView(view)) return null;
        if (view.isAttachedToWindow() && card == null) return null;
        Binding binding = activeBinding;
        if (binding == null) return null;
        float dark = card != null ? cardDarkAmount(card) : lastBriefDark;
        if (!glassAllowed(view, dark)) return null;
        synchronized (unattachedBriefCache) {
            return unattachedBriefCache.requestInfo(binding, view.getContext());
        }
    }

    private static float cardDarkAmount(View card) {
        try {
            Object value = Reflect.call(card, "getDarkAmount");
            if (value instanceof Number dark) return dark.floatValue();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            DressroomModule.note("Now Brief card darkAmount", unavailable);
        }
        return lastBriefDark;
    }

    private static Object request(Binding binding, int preset, float radius)
            throws ReflectiveOperationException {
        Object builder = binding.builder.newInstance(0);
        binding.preset.invoke(builder, preset);
        binding.corners.invoke(builder, radius);
        binding.color.invoke(builder, 0);
        return binding.build.invoke(builder);
    }

    private static boolean enabled(View view, Surface state) {
        View card = state.card.get();
        if (card == null || !view.isAttachedToWindow()) return false;
        try {
            return glassAllowed(view, (Float) state.binding.dark.invoke(card));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean glassAllowed(View view, float darkAmount) {
        return glassAllowed(view.getContext(), darkAmount);
    }

    static boolean glassAllowed(android.content.Context context, float darkAmount) {
        return RuntimeExtras.enabled(context, KEY)
                && BlurAppearance.liveWindowBlurAllowed(context)
                && Settings.System.getInt(context.getContentResolver(), "minimal_battery_use", 0) == 0
                && (darkAmount == 0f || WallpaperAod.shown(context));
    }

    static void nativeRequest(View view, Object request) {
        if (surfaces.isEmpty() || building.get() == view) return;
        Surface state = surfaces.get(view);
        if (state != null && (request == null || request != state.generatedRequest)) {
            state.generatedRequest = null;
            state.addedBlur = null;
            if (request == null) {
                if (state.foreground != null && view.getForeground() == null)
                    view.setForeground(state.foreground);
                state.foreground = null;
            }
        }
    }

    static void submitted(View view, Object request) {
        if (request == null || surfaces.isEmpty() || building.get() == view) return;
        Surface state = surfaces.get(view);
        if (state != null && request == state.generatedRequest && BlurAppearance.hasWindowDrawable(view))
            state.addedBlur = view.getBackground();
    }

    private static void update(View view, Surface state) {
        try {
            boolean enabled = enabled(view, state);
            boolean changedMode = state.glassEnabled != enabled;
            state.glassEnabled = enabled;
            if (!enabled) {
                boolean added = state.addedBlur != null;
                restore(view, state);
                if (changedMode && !added) BlurAppearance.refreshNativeNowBar(view);
                return;
            }
            Drawable previous = view.getBackground();
            int preset = state.cache.sample(state.binding, view.getContext());
            boolean changed = state.radius != state.submittedRadius || preset != state.submittedPreset;
            if (!BlurAppearance.hasWindowDrawable(view) || (previous == state.addedBlur && changed)) {
                if (state.addedBlur == null || previous != state.addedBlur)
                    state.background = previous;
                View previousOwner = building.get();
                building.set(view);
                try {
                    Object request = request(state.binding, preset, state.radius);
                    state.binding.submit.invoke(view, request);
                    state.generatedRequest = request;
                } finally {
                    if (previousOwner == null) building.remove();
                    else building.set(previousOwner);
                }
                if (!BlurAppearance.hasWindowDrawable(view)) return;
                state.addedBlur = view.getBackground();
                state.submittedRadius = state.radius;
                state.submittedPreset = preset;
                if (previous != null) state.addedBlur.setAlpha(previous.getAlpha());
            } else if (changedMode) BlurAppearance.refreshNativeNowBar(view);
            if (state.media && view.getForeground() instanceof GradientDrawable gradient) {
                state.foreground = gradient;
                view.setForeground(null);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            DressroomModule.note("Now Bar pill glass", unavailable);
        }
    }

    private static void restore(View view, Surface state) {
        if (state.addedBlur != null && view.getBackground() == state.addedBlur) {
            state.addedBlur = null;
            BlurAppearance.clearWindowSurface(view);
            if (view.getBackground() == null && state.background != null)
                view.setBackground(state.background);
        }
        if (state.foreground != null && view.getForeground() == null)
            view.setForeground(state.foreground);
        state.addedBlur = null;
        state.generatedRequest = null;
        state.foreground = null;
        state.background = null;
    }

    private static void refresh() {
        for (View view : new ArrayList<>(surfaces.keySet())) {
            Surface state = surfaces.get(view);
            if (state == null) continue;
            update(view, state);
        }
        for (View view : new ArrayList<>(briefs.keySet())) {
            Brief state = briefs.get(view);
            if (state == null || !view.isAttachedToWindow()) continue;
            try {
                float dark = (Float) state.binding.dark.invoke(view);
                if (state.untinted != glassAllowed(view, dark)) state.refresh.invoke(view, dark);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                DressroomModule.note("Now Brief glass refresh", unavailable);
            }
        }
        NowBriefFill.refreshAttachedBlurViews();
    }
}
