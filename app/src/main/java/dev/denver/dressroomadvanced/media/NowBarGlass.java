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
    private static final int UNKNOWN_BRIGHTNESS = -1;
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
    private static final View.OnLayoutChangeListener layout = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return;
        Surface state = surfaces.get(view);
        if (state != null && view.isAttachedToWindow()) update(view, state);
    };
    private static final View.OnAttachStateChangeListener attach = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            Surface immediate = surfaces.get(view);
            if (immediate != null) update(view, immediate);
            view.postOnAnimation(() -> {
                Surface state = surfaces.get(view);
                if (state != null) update(view, state);
            });
        }

        @Override public void onViewDetachedFromWindow(View view) {
            Surface state = surfaces.get(view);
            if (state == null) return;
            restore(view, state);
            if (state.binding.beta) {
                state.glassEnabled = false;
                state.lastNativeRequest = null;
            } else {
                surfaces.remove(view);
                view.removeOnAttachStateChangeListener(this);
            }
        }
    };
    private static boolean observing;

    private record Binding(Method dark, Method getBlur, Method selectPreset, Constructor<?> builder,
            Method preset, Method corners, Method color, Method build, Method submit, boolean beta,
            CardBrightness brightness) {}

    private record CardBrightness(Method model, Method value) {
        static CardBrightness resolve(Class<?> card) {
            try {
                Method model = card.getMethod("getNowbarColorModel");
                return new CardBrightness(model, NativeContract.method(
                        model.getReturnType(), "getWallpaperBrightness", int.class));
            } catch (NoSuchMethodException | RuntimeException unavailable) {
                return null;
            }
        }

        int read(View card) {
            if (card == null) return UNKNOWN_BRIGHTNESS;
            try {
                Object colorModel = model.invoke(card);
                if (colorModel == null) return UNKNOWN_BRIGHTNESS;
                int brightness = (Integer) value.invoke(colorModel);
                return brightness >= 0 && brightness <= 100 ? brightness : UNKNOWN_BRIGHTNESS;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return UNKNOWN_BRIGHTNESS;
            }
        }
    }

    private static final class PresetCache {
        int cachedPreset;
        int selectedPreset;
        int sampledBrightness = Integer.MIN_VALUE;
        long sampleUntil;
        Object presetInfo;

        int sample(Binding binding, android.content.Context context, View card)
                throws ReflectiveOperationException {
            if (binding.beta) {
                // The model already tracks the local wallpaper luminance and its global fallback.
                int brightness = binding.brightness == null
                        ? UNKNOWN_BRIGHTNESS : binding.brightness.read(card);
                if (sampledBrightness != brightness) {
                    selectedPreset = invokePresetSelector(binding.selectPreset, context, brightness);
                    sampledBrightness = brightness;
                }
                return selectedPreset;
            }
            // Same default branch as Samsung's ordinary pill, with lockscreen wallpaper sampling.
            long now = android.os.SystemClock.uptimeMillis();
            if (now >= sampleUntil) {
                selectedPreset = invokePresetSelector(binding.selectPreset, context, UNKNOWN_BRIGHTNESS);
                sampleUntil = now + 100;
            }
            return selectedPreset;
        }

        Object requestInfo(Binding binding, android.content.Context context, View card)
                throws ReflectiveOperationException {
            int preset = sample(binding, context, card);
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
        Object generatedRequest, lastNativeRequest;
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
            boolean beta = List.of(selectPreset.getParameterTypes()).equals(List.of(
                    android.content.Context.class, boolean.class, boolean.class, Integer.class,
                    int.class));
            Binding binding = new Binding(NativeContract.method(card, "getDarkAmount", float.class),
                    card.getMethod("getBlurContainer"), selectPreset, builder.getConstructor(int.class),
                    builder.getMethod("setColorCurvePreset", int.class),
                    builder.getMethod("setBackgroundCornerRadius", float.class),
                    builder.getMethod("setBackgroundColor", int.class), builder.getMethod("build"),
                    View.class.getMethod("semSetBlurInfo", info), beta,
                    beta ? CardBrightness.resolve(card) : null);
            Method gradient = bindMediaGradient(media);
            hooks.add(module.hook(background).setId("dressroom-advanced.now-bar-glass-background")
                    .intercept(chain -> {
                        View owner = (View) chain.getThisObject();
                        View surface = (View) chain.getArg(1);
                        boolean eligible = surface != null && !media.isInstance(owner)
                                && !owner.getClass().getName().equals(BRIEF)
                                && isPill(surface, "nowbar_cardview_background");
                        if (eligible) {
                            track(owner, surface, binding, (Float) chain.getArg(2), false);
                        }
                        Object result = chain.proceed();
                        if (eligible) {
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
                        if (state.untinted && !binding.beta && state.outline == null)
                            state.outline =
                                    new NowBriefOutline(
                                            owner, surface, cardState, corner, binding.dark, border);
                        else if (!state.untinted && state.outline != null) {
                            state.outline.close(owner);
                            state.outline = null;
                        }
                        // GenUI treats null as "keep the previous color". Zero explicitly clears it.
                        // Beta GenUI can skip blur for this card. Keep its fallback tint in
                        // the model and restyle the actual window request when it is submitted.
                        return state.untinted && !binding.beta ? 0 : chain.proceed();
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
        boolean cardDrawable = body.calls().stream().anyMatch(call -> call.owner().equals(CARD_VIEW)
                && call.name().equals("setCardBackgroundColor"));
        boolean viewBlur = body.calls().stream().anyMatch(call -> call.owner().equals("Landroid/view/View;")
                && call.name().equals("semSetBlurInfo")
                && call.parameters().equals(List.of("Landroid/view/SemBlurInfo;")))
                && body.calls().stream().anyMatch(call -> call.owner().equals("Landroid/view/View;")
                        && call.name().equals("setBackground")
                        && call.parameters().equals(List.of("Landroid/graphics/drawable/Drawable;")));
        if (!body.anchors().containsKey("NowbarBlurUtils")
                || !(cardDrawable || viewBlur))
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
    private static final List<String> PRESET_SELECTOR_LOCAL_COLOR =
            List.of("Landroid/content/Context;", "Z", "Z", "Ljava/lang/Integer;", "I");

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
            if (!isBriefRefreshBody(body, style)
                    && !(buildsBriefBlurStyle(body, style)
                            && OneUi95Aod.updatesBriefBlur(body, style, type))) continue;
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
        return buildsBriefBlurStyle(body, styleDesc)
                && body.calls().stream()
                        .anyMatch(
                                call ->
                                        call.name().equals("updateBlurStyle")
                                                && call.parameters().equals(List.of(styleDesc)));
    }

    private static boolean buildsBriefBlurStyle(DexSignatures.Body body, String style) {
        return body.calls().stream().anyMatch(
                call -> call.owner().equals(style) && call.name().equals("<init>"));
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
        var calls = DexSignatures.body(background).calls();
        try {
            return selectPresetMethod(calls, loader);
        } catch (NoSuchMethodException changed) {
            Set<Method> selectors = new java.util.LinkedHashSet<>();
            String anchor = "getNowBarBlurPreset wallpaperBrightnessNav:";
            for (var call : calls) {
                if (!call.isStatic() || !call.result().equals("I")
                        || !call.parameters().equals(PRESET_SELECTOR_LOCAL_COLOR)) continue;
                Method method = call.method(loader);
                if (DexSignatures.body(method, Set.of(anchor)).anchors().containsKey(anchor))
                    selectors.add(method);
            }
            return OneUi95Editor.unique(List.copyOf(selectors), "Now Bar local color preset");
        }
    }

    static void validatePillBackground(Class<?> media, Class<?> item)
            throws ReflectiveOperationException, java.io.IOException {
        ClassLoader loader = media.getClassLoader();
        bindPreset(bindBackground(media.getSuperclass(), item), loader);
        bindMediaGradient(media);
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

    static int invokePresetSelector(Method selectPreset, android.content.Context context, int brightness)
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
        if (types.length == 5
                && types[0] == android.content.Context.class
                && types[1] == boolean.class && types[2] == boolean.class
                && types[3] == Integer.class && types[4] == int.class) {
            // Null sends the 9.5 selector through semGetWallpaperColors on the UI thread.
            return (Integer) selectPreset.invoke(null, context, false, false, brightness, 62);
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
        update(view, track(card, view, binding, radius, media));
    }

    private static Surface track(View card, View view, Binding binding, float radius, boolean media) {
        Surface state = surfaces.get(view);
        if (state == null) {
            state = new Surface(card, binding, media);
            surfaces.put(view, state);
            view.addOnAttachStateChangeListener(attach);
            if (binding.beta) view.addOnLayoutChangeListener(layout);
        }
        state.radius = radius;
        return state;
    }

    static Object glassPreset(View view, Object request) throws ReflectiveOperationException {
        Surface state = surfaces.get(view);
        if (state != null) {
            View card = state.card.get();
            if (card == null || (state.binding.beta && !ready(view))) return null;
            try {
                if (!glassAllowed(view, (Float) state.binding.dark.invoke(card))) return null;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return null;
            }
            if (state.binding.beta && !BlurAppearance.visibleWindowRequest(request)) return null;
            return state.cache.requestInfo(state.binding, view.getContext(), card);
        }
        return briefGlassPreset(view, request);
    }

    /** Resolves the wallpaper-sampled glass preset for a Now Brief GenUI blur FrameLayout. */
    private static Object briefGlassPreset(View view, Object request)
            throws ReflectiveOperationException {
        if (view == null) return null;
        View card = BlurAppearance.nowBarCardOwner(view);
        Brief owner = card == null ? null : briefs.get(card);
        if (owner != null) {
            if (owner.binding.beta && !NowBriefFill.isBriefBlurView(view)) return null;
            if (!view.isAttachedToWindow() && !NowBriefFill.isBriefBlurView(view)) return null;
            float dark;
            try {
                dark = (Float) owner.binding.dark.invoke(card);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return null;
            }
            if (!glassAllowed(view, dark)) return null;
            if (owner.binding.beta && !BlurAppearance.visibleWindowRequest(request)) return null;
            return owner.cache.requestInfo(owner.binding, view.getContext(), card);
        }
        if (!NowBriefFill.isBriefBlurView(view)) return null;
        if (view.isAttachedToWindow() && card == null) return null;
        Binding binding = activeBinding;
        if (binding == null) return null;
        float dark = card != null ? cardDarkAmount(card) : lastBriefDark;
        if (!glassAllowed(view, dark)) return null;
        if (binding.beta && !BlurAppearance.visibleWindowRequest(request)) return null;
        synchronized (unattachedBriefCache) {
            return unattachedBriefCache.requestInfo(binding, view.getContext(), card);
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
                && !BlurAppearance.minimalBatteryUse(context)
                && (darkAmount == 0f || WallpaperAod.shown(context));
    }

    static void nativeRequest(View view, Object request) {
        if (surfaces.isEmpty() || building.get() == view) return;
        Surface state = surfaces.get(view);
        if (state != null) {
            if (request != null && request != state.generatedRequest)
                state.lastNativeRequest = request;
            if (request == null || request != state.generatedRequest) {
                state.generatedRequest = null;
                state.addedBlur = null;
                if (request == null) {
                    if (state.foreground != null && view.getForeground() == null)
                        view.setForeground(state.foreground);
                    state.foreground = null;
                }
            }
        }
    }

    static boolean replacing(View view) {
        return building.get() == view;
    }

    static boolean ownsRequest(View view, Object request) {
        Surface state = surfaces.get(view);
        return request != null && state != null && request == state.generatedRequest;
    }

    private static boolean ready(View view) {
        return view.isAttachedToWindow() && view.isShown() && view.isHardwareAccelerated()
                && view.getWidth() > 0 && view.getHeight() > 0;
    }

    static void ensurePillBlur(View card, View view) {
        if (card == null || view == null) return;
        Binding binding = activeBinding;
        if (binding == null) return;
        Surface state = surfaces.get(view);
        float radius = state != null ? state.radius : 0f;
        try {
            if (Reflect.call(card, "getNormalCardRadius") instanceof Number normal)
                radius = normal.floatValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        remember(card, view, binding, radius, true);
        state = surfaces.get(view);
        if (state != null
                && (!binding.beta || ready(view))
                && !BlurAppearance.hasWindowDrawable(view)
                && state.lastNativeRequest != null
                && BlurAppearance.liveWindowBlurAllowed(view.getContext())) {
            try {
                state.binding.submit.invoke(view, state.lastNativeRequest);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
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
            if (!enabled) {
                state.glassEnabled = false;
                boolean added = state.addedBlur != null;
                restore(view, state);
                if (changedMode && !added) BlurAppearance.refreshNativeNowBar(view);
                return;
            }
            if (state.binding.beta && !ready(view)) return;
            Drawable previous = view.getBackground();
            int preset = state.cache.sample(state.binding, view.getContext(), state.card.get());
            if (preset <= 0) return;
            boolean changed = state.radius != state.submittedRadius || preset != state.submittedPreset;
            if (!BlurAppearance.hasWindowDrawable(view) || (previous == state.addedBlur && changed)) {
                Object request = request(state.binding, preset, state.radius);
                if (state.binding.beta && !BlurAppearance.visibleWindowRequest(request)) return;
                if (state.addedBlur == null || previous != state.addedBlur)
                    state.background = previous;
                boolean previousBlur = BlurAppearance.hasWindowDrawable(view);
                View previousOwner = building.get();
                building.set(view);
                try {
                    state.generatedRequest = request;
                    state.binding.submit.invoke(view, request);
                } finally {
                    if (state.binding.beta && !BlurAppearance.hasWindowDrawable(view)) {
                        state.generatedRequest = null;
                        BlurAppearance.forgetNowBarRequest(view);
                        if (view.getBackground() == null && previous != null)
                            view.setBackground(previous);
                    }
                    if (previousOwner == null) building.remove();
                    else building.set(previousOwner);
                }
                if (!BlurAppearance.hasWindowDrawable(view)) return;
                state.addedBlur = view.getBackground();
                state.submittedRadius = state.radius;
                state.submittedPreset = preset;
                // A colored fill's alpha is tint, while blur alpha controls the entire region.
                if (previous != null && (!state.binding.beta || previousBlur))
                    state.addedBlur.setAlpha(previous.getAlpha());
            } else if (changedMode) BlurAppearance.refreshNativeNowBar(view);
            if (state.binding.beta && (!BlurAppearance.hasWindowDrawable(view)
                    || !view.getBackground().isVisible() || view.getBackground().getAlpha() == 0))
                return;
            state.glassEnabled = true;
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
            Drawable added = state.addedBlur;
            View previousOwner = building.get();
            if (state.binding.beta) building.set(view);
            try {
                BlurAppearance.clearWindowSurface(view);
                if (view.getBackground() == null || (state.binding.beta && view.getBackground() == added))
                    view.setBackground(state.background);
            } finally {
                if (state.binding.beta) {
                    if (previousOwner == null) building.remove();
                    else building.set(previousOwner);
                }
            }
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
