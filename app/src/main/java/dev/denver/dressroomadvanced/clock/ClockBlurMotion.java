package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Keep the native blur alive through motion, not through actual visibility changes. */
final class ClockBlurMotion {
    private static final Map<View, State> states = new WeakHashMap<>();
    private static final Map<View, Float> doze = new WeakHashMap<>();
    private static final Set<View> movingClocks = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> returningClocks = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> dashboardClocks =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, String> lastTransitionStates = new WeakHashMap<>();
    private static final Map<View, Handoff> outgoingClocks = new WeakHashMap<>();
    private static final Map<View, Object> returnControllers = new WeakHashMap<>();
    private static final ThreadLocal<View> retained = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> returnTransition = new ThreadLocal<>();
    private static boolean returnWindow;
    private static boolean observesAod;

    private record AodFields(Field active, Field dozing, Method wallpaperSupported) {}

    private ClockBlurMotion() {}

    private static final class Handoff {
        final WeakReference<View> target;
        final ClockBlurRetryBinding binding;
        final Field host;
        final OneUi95BlurHost betaHost;
        boolean targetSubmitted;

        Handoff(View target, ClockBlurRetryBinding binding, Field host, OneUi95BlurHost betaHost) {
            this.target = new WeakReference<>(target);
            this.binding = binding;
            this.host = host;
            this.betaHost = betaHost;
        }
    }

    private static final class State {
        final ClockBlurRetryBinding binding;
        final Method enable;
        final Field configured;
        AodFields aodFields;
        Set<String> reasons = Set.of();
        float scale = 1;
        boolean moving, aod, dashboardReturn;
        java.util.List<BlurAppearance.ShadeSurface> shadeSurfaces;

        State(ClockBlurRetryBinding binding, Method enable, Field configured) {
            this.binding = binding;
            this.enable = enable;
            this.configured = configured;
        }

        boolean update(View clock) throws IllegalAccessException {
            float amount = doze.getOrDefault(clock, 0f);
            boolean visible = clock.isAttachedToWindow() && clock.isShown()
                    && Boolean.TRUE.equals(configured.get(clock));
            boolean keepAod = visible && aodFields != null && amount == 1f
                    && reasons.contains("DOZE_ACTIVE")
                    && allows(reasons, amount, dashboardReturn, true)
                    && useWallpaperAod(clock, aodFields.wallpaperSupported);
            if (keepAod) aodFields.active.setBoolean(clock, true);
            else if (aod) aodFields.active.setBoolean(clock,
                    !aodFields.dozing.getBoolean(clock) && Boolean.TRUE.equals(configured.get(clock)));
            aod = keepAod;
            moving = visible && allows(reasons, amount, dashboardReturn);
            if (moving && !aod) LockscreenFrameBoost.pulse(clock, 240L);
            if (moving || aod) {
                if (movingClocks.add(clock)) ClockBlurSync.syncHook();
            } else if (movingClocks.remove(clock)) {
                ClockBlurSync.syncHook();
            }
            return moving || aod;
        }

        void refresh(View clock) {
            if (states.get(clock) != this || !(moving || aod) || !clock.isShown()) return;
            if (moving && !aod) LockscreenFrameBoost.pulse(clock, 200L);
            try {
                Object manager = binding.manager.get(clock);
                if (!Boolean.TRUE.equals(binding.enabled.invoke(manager)))
                    enable.invoke(clock, scale, false);
                Reflect.call(clock, "setClockTextBlur");
            } catch (ReflectiveOperationException | RuntimeException error) {
                moving = false;
                if (movingClocks.remove(clock)) ClockBlurSync.syncHook();
                DressroomModule.note("Moving clock blur refresh", error);
            }
        }
    }

    static boolean allows(Set<String> reasons, float amount) {
        return allows(reasons, amount, false);
    }

    static boolean allows(Set<String> reasons, float amount, boolean dashboardReturn) {
        return allows(reasons, amount, dashboardReturn, false);
    }

    static boolean allows(Set<String> reasons, float amount, boolean dashboardReturn,
            boolean wallpaperAod) {
        if (!Float.isFinite(amount) || amount < 0 || amount > 1) return false;
        if (reasons.isEmpty()) return dashboardReturn && amount == 0f;
        boolean media = reasons.contains("MEDIA_EXPAND_ANIMATING");
        for (String reason : reasons) {
            switch (reason) {
                case "STRETCH_ANIMATION", "SCREEN_ANIMATION", "MEDIA_EXPAND_ANIMATING" -> {}
                // The controller sets DOZE_ACTIVE at sleep start, before amount moves.
                // Its final frame must still clear blur on a settled AOD.
                case "DOZE_ACTIVE" -> { if (amount >= 1 && !wallpaperAod) return false; }
                case "DASHBOARD_ACTIVE" -> { if (!media && !dashboardReturn) return false; }
                case "FACE_WIDGET_TRANSITIONING" -> { if (!dashboardReturn) return false; }
                default -> { return false; }
            }
        }
        return true;
    }

    static void install(DressroomModule module, ClockBlurRetryBinding binding)
            throws ReflectiveOperationException, java.io.IOException {
        if (android.os.Build.VERSION.SDK_INT < 37) return;
        if (binding.enabled == null) throw new NoSuchMethodException("Clock blur enabled reader");
        Class<?> base = binding.manager.getDeclaringClass();
        Class<?> controller = Class.forName(
                "com.samsung.android.app.facewidget.pages.clock.blur.ClockBlurController",
                false, base.getClassLoader());
        Set<Method> candidates = new HashSet<>();
        for (Method method : controller.getDeclaredMethods())
            if (method.getReturnType() == void.class && method.getParameterCount() == 2
                    && method.getParameterTypes()[1] == base
                    && DexSignatures.body(method).calls().stream().anyMatch(call ->
                            call.name().equals("removeBlur") && call.parameters().isEmpty()))
                candidates.add(method);
        if (candidates.size() != 1)
            throw new NoSuchMethodException("Ambiguous clock blur state application");
        Method apply = candidates.iterator().next();
        Field reasons = NativeContract.findField(apply.getParameterTypes()[0], null, Set.class);
        var body = DexSignatures.body(apply);
        Field scale = body.uniqueRead(DexSignatures.descriptor(controller), "F", null)
                .field(base.getClassLoader());
        Set<Method> enables = new HashSet<>();
        for (var call : body.calls())
            if (call.owner().equals(DexSignatures.descriptor(base)) && call.result().equals("V")
                    && call.parameters().equals(java.util.List.of("F", "Z")))
                enables.add(call.method(base.getClassLoader()));
        if (enables.size() != 1) throw new NoSuchMethodException("Ambiguous clock blur activation");
        Method enable = enables.iterator().next();
        Method clear = base.getDeclaredMethod("removeBlur");
        // The setter can use its argument directly after storing it. The clear
        // entry actually reads the user's saved blur flag before changing colors.
        Field configured = DexSignatures.body(clear)
                .uniqueRead(DexSignatures.descriptor(base), "Ljava/lang/Boolean;", null)
                .field(base.getClassLoader());
        Method amount = base.getDeclaredMethod("setDozeAmount", float.class);
        Method setDozing = base.getDeclaredMethod("setDozing", boolean.class);
        AodFields aodFields = aodFields(module, base, amount, setDozing);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(apply).setId("dressroom-advanced.clock-motion-blur-state")
                    .intercept(chain -> {
                        LockStarClockBlur.observeController(chain.getThisObject());
                        View clock = (View) chain.getArg(1);
                        if (clock == null) return chain.proceed();
                        State state = states.computeIfAbsent(clock, key -> new State(binding, enable, configured));
                        state.aodFields = aodFields;
                        Set<String> names = new HashSet<>();
                        for (Object item : (Set<?>) reasons.get(chain.getArg(0)))
                            names.add(item instanceof Enum<?> value ? value.name() : "UNKNOWN");
                        if (names.contains("QUICK_PANEL_EXPANDED")
                                && !state.reasons.contains("QUICK_PANEL_EXPANDED")) {
                            state.shadeSurfaces = BlurAppearance.captureShadeSurfaces(clock.getRootView());
                        } else if (names.contains("DOZE_ACTIVE") || names.contains("DASHBOARD_ACTIVE")
                                || names.contains("PRIMARY_BOUNCER_VISIBLE")
                                || names.contains("PROFILE_TRANSITIONING")) {
                            state.shadeSurfaces = null;
                        }
                        ClockInlineSeconds.onBlockerState(clock, names);
                        state.reasons = Set.copyOf(names);
                        state.scale = scale.getFloat(chain.getThisObject());
                        // The transition destination receives its first state
                        // before pre-draw attaches it. Keep the return marked from
                        // the page transition so this apply does not remove blur.
                        boolean transitionReturn = returning(clock);
                        state.dashboardReturn = transitionReturn;
                        boolean keep = state.update(clock);
                        // An unattached destination cannot be "moving" yet, but its
                        // clear must still honour the return gesture.
                        boolean hold = keep || (transitionReturn && state.dashboardReturn
                                && Boolean.TRUE.equals(configured.get(clock))
                                && allows(names, doze.getOrDefault(clock, 0f), true));
                        if (returnWindow) {
                            android.util.Log.d(DressroomModule.TAG, "Apply in return: "
                                    + names + " state=" + System.identityHashCode(state)
                                    + " clock=" + System.identityHashCode(clock)
                                    + " returning=" + transitionReturn
                                    + " dashboardReturn=" + state.dashboardReturn
                                    + " attached=" + clock.isAttachedToWindow()
                                    + " shown=" + clock.isShown()
                                    + " configured=" + configured.get(clock)
                                    + " keep=" + keep + " hold=" + hold);
                        }
                        View previous = retained.get();
                        if (hold) retained.set(clock);
                        else retained.remove();
                        try {
                            Object result = chain.proceed();
                            if (keep) queue(clock, state);
                            if (state.aod) observeAod(clock.getContext());
                            if (state.reasons.isEmpty() && state.shadeSurfaces != null)
                                restoreAfterShade(clock, state);
                            return result;
                        } finally {
                            if (previous == null) retained.remove();
                            else retained.set(previous);
                        }
                    })::unhook);
            hooks.add(module.hook(clear).setId("dressroom-advanced.clock-retain-motion-matte")
                    .intercept(chain -> {
                        View clock = (View) chain.getThisObject();
                        if (returnWindow || retained.get() != null) {
                            android.util.Log.d(DressroomModule.TAG, "Clear clock="
                                    + System.identityHashCode(clock)
                                    + " retained=" + (retained.get() == null
                                            ? "none" : System.identityHashCode(retained.get()))
                                    + " returning=" + returning(clock));
                        }
                        if (retained.get() == clock) return null;
                        return chain.proceed();
                    })::unhook);
            hooks.add(module.hook(amount).setId("dressroom-advanced.clock-blur-doze-progress")
                    .intercept(chain -> {
                        View clock = (View) chain.getThisObject();
                        State state = states.get(clock);
                        boolean wasMoving = state != null && (state.moving || state.aod);
                        doze.put(clock, ((Number) chain.getArg(0)).floatValue());
                        Object result = chain.proceed();
                        state = states.get(clock);
                        if (state != null) {
                            if (state.update(clock)) {
                                queue(clock, state);
                                if (state.aod) observeAod(clock.getContext());
                            }
                            else if (wasMoving && state.reasons.contains("DOZE_ACTIVE"))
                                clear.invoke(clock);
                        }
                        return result;
                    })::unhook);
            if (aodFields != null) {
                hooks.add(module.hook(setDozing).setId("dressroom-advanced.clock-aod-blur-color")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            View clock = (View) chain.getThisObject();
                            State state = states.get(clock);
                            if (state != null && doze.getOrDefault(clock, 0f) == 1f
                                    && state.update(clock)) queue(clock, state);
                            return result;
                        })::unhook);
                module.deoptimize(setDozing);
            }
            module.deoptimize(apply);
            module.deoptimize(clear);
            module.deoptimize(amount);
            module.deoptimize(enable);
            hooks.commit();
        }
        android.util.Log.i(DressroomModule.TAG, "Native clock motion blur installed");
        try {
            installDashboardReturn(module, base, controller, binding, enable, configured, scale);
        } catch (NoSuchFieldException | NoSuchMethodException unsupported) {
            android.util.Log.w(DressroomModule.TAG,
                    "Using native seconds-view return blur: " + unsupported.getMessage());
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException error) {
            module.report("Optional seconds-view return blur unavailable", error);
        }
        try {
            ClockBlurDelays.install(module, base.getClassLoader());
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException error) {
            module.report("Optional clock blur delay controls unavailable", error);
        }
        LockStarClockBlur.install(module, base.getClassLoader());
    }

    private static AodFields aodFields(DressroomModule module, Class<?> base, Method amount,
            Method setDozing) {
        if (module.inEditorProcess() || !WallpaperAod.supported()) return null;
        try {
            String owner = DexSignatures.descriptor(base);
            var colors = DexSignatures.body(amount).calls().stream().filter(call ->
                    call.owner().equals(owner) && call.result().equals("[I")
                            && call.parameters().equals(java.util.List.of("[I", "[I", "F"))).toList();
            if (colors.size() != 1) return null;
            ClassLoader loader = base.getClassLoader();
            Method palette = colors.get(0).method(loader);
            String helper =
                    "Lcom/samsung/android/app/aodservice/common/utils/settings/SettingsHelper;";
            var supportChecks = DexSignatures.findInClass(loader, helper, Set.of(WallpaperAod.MAIN)).stream()
                    .filter(body -> body.owner().equals(helper) && body.parameters().isEmpty()
                            && body.result().equals("Z") && body.anchors().containsKey(WallpaperAod.MAIN))
                    .flatMap(body -> body.calls().stream())
                    .filter(call -> call.isStatic() && call.parameters().isEmpty()
                            && call.result().equals("Z")).toList();
            if (supportChecks.size() != 1) return null;
            return new AodFields(DexSignatures.body(palette).uniqueRead(owner, "Z", null)
                    .field(loader),
                    DexSignatures.body(setDozing).uniqueRead(owner, "Z", null)
                            .field(loader), supportChecks.get(0).method(loader));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException unavailable) {
            module.report("Optional wallpaper AOD clock blur unavailable", unavailable);
            return null;
        }
    }

    private static boolean useWallpaperAod(View clock, Method wallpaperSupported) {
        try {
            var context = clock.getContext();
            return RuntimeExtras.enabled(context, WallpaperAod.CLOCK_BLUR)
                    && ScreenIdentity.current() != ScreenIdentity.SUB
                    && WallpaperAod.shown(context)
                    && Boolean.TRUE.equals(wallpaperSupported.invoke(null))
                    && BlurAppearance.liveWindowBlurAllowed(context);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static void observeAod(android.content.Context context) {
        if (observesAod) return;
        try {
            var observer = new android.database.ContentObserver(
                    new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { refreshAod(); }
            };
            var resolver = context.getContentResolver();
            for (String key : new String[] {WallpaperAod.MAIN, WallpaperAod.COVER,
                    "accessibility_reduce_transparency"})
                resolver.registerContentObserver(android.provider.Settings.System.getUriFor(key),
                        false, observer);
            resolver.registerContentObserver(
                    android.provider.Settings.Global.getUriFor("disable_window_blurs"),
                    false, observer);
            RuntimeExtras.listen(ClockBlurMotion::refreshAod);
            observesAod = true;
        } catch (RuntimeException unavailable) {
            observesAod = true;
            DressroomModule.note("AOD blur setting observation", unavailable);
        }
    }

    private static void refreshAod() {
        for (var entry : new java.util.ArrayList<>(states.entrySet())) {
            View clock = entry.getKey();
            State state = entry.getValue();
            if (clock == null || doze.getOrDefault(clock, 0f) != 1f) continue;
            try {
                boolean wasAod = state.aod;
                if (state.update(clock)) queue(clock, state);
                else if (wasAod) Reflect.call(clock, "removeBlur");
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                DressroomModule.note("AOD clock blur setting change", unavailable);
            }
        }
    }

    private static void installDashboardReturn(
            DressroomModule module,
            Class<?> base,
            Class<?> controller,
            ClockBlurRetryBinding binding,
            Method enable,
            Field configured,
            Field controllerScale)
            throws ReflectiveOperationException, java.io.IOException {
        ClassLoader loader = base.getClassLoader();
        Class<?> page = Class.forName(
                "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage", false, loader);
        var transition = ClockPageTransition.resolve(loader);
        String bindAnchor = "initViews() mClockView=";
        var bodies = DexSignatures.findInClass(page, Set.of(
                "common_clock_transition_second", bindAnchor));
        DexSignatures.Body configuration = null;
        Method bind = null;
        for (var body : bodies) {
            if (!body.owner().equals(DexSignatures.descriptor(page))
                    || !body.result().equals("V")
                    || body.parameters().size() != 1) continue;
            if (body.anchors().containsKey(bindAnchor)
                    && body.parameters().equals(java.util.List.of("Landroid/view/View;"))) {
                if (bind != null) throw new NoSuchMethodException("Ambiguous clock view binding");
                bind = body.method(loader);
            }
            if (!body.anchors().containsKey("common_clock_transition_second")) continue;
            // Only the page transition configuration drives the controller's
            // boolean transition blocker for this destination.
            boolean drives = body.calls().stream().anyMatch(call ->
                    call.owner().equals(DexSignatures.descriptor(controller))
                            && call.result().equals("V")
                            && call.parameters().equals(java.util.List.of("Z")));
            if (!drives) continue;
            if (body.method(loader).getParameterTypes()[0] != transition.attach().getDeclaringClass())
                continue;
            if (configuration != null) throw new NoSuchMethodException("Ambiguous page transition");
            configuration = body;
        }
        if (configuration == null) throw new NoSuchMethodException("Seconds-view transition");
        boolean tracksBoundClock = bind != null;
        Method configure = configuration.method(loader);
        Method transitionBlock = null;
        for (var call : configuration.calls())
            if (call.owner().equals(DexSignatures.descriptor(controller))
                    && call.result().equals("V")
                    && call.parameters().equals(java.util.List.of("Z")))
                transitionBlock = call.method(loader);
        if (transitionBlock == null)
            throw new NoSuchMethodException("Clock transition blur blocker");
        var destinationReads = configuration.reads().stream().map(DexSignatures.Read::field)
                .filter(field -> field.owner().equals(DexSignatures.descriptor(page))
                        && field.type().equals("Landroid/view/View;")).distinct().toList();
        if (destinationReads.size() > 1)
            throw new NoSuchFieldException("Ambiguous early clock transition target");
        Field destination = destinationReads.isEmpty() ? null : destinationReads.get(0).field(loader);
        // Some builds assign the target after configuration. Resolve it at attach on those builds.
        Map<Object, WeakReference<Object>> pendingReturns = new WeakHashMap<>();
        Field pageState = pageStateField(configuration, page, loader);
        Method attach = transition.attach();
        Method finish = transition.finish();
        try (HookTransaction hooks = new HookTransaction()) {
            if (tracksBoundClock) {
                hooks.add(module.hook(bind).setId("dressroom-advanced.clock-bound-page")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            View clock = ClockPageTransition.clock((View) chain.getArg(0), base);
                            if (clock != null) {
                                Object state = pageState.get(chain.getThisObject());
                                String name = state instanceof Enum<?> value ? value.name() : null;
                                if ("DASHBOARD".equals(name) || "DASHBOARD_AOD".equals(name))
                                    dashboardClocks.add(clock);
                                else dashboardClocks.remove(clock);
                            }
                            return result;
                        })::unhook);
            }
            hooks.add(module.hook(configure).setId("dressroom-advanced.seconds-return-blur-start")
                    .intercept(chain -> {
                        boolean returning = false;
                        View clock = null;
                        Object transitionState = chain.getArg(0);
                        Boolean previousReturn = returnTransition.get();
                        try {
                            View pageView = (View) chain.getThisObject();
                            Object value = pageState.get(pageView);
                            String now = value instanceof Enum<?> state ? state.name() : null;
                            View from = transition.clock(transitionState, transition.source(), base);
                            boolean fromDashboard = dashboardClocks.contains(from);
                            if (!tracksBoundClock && now != null) {
                                String previous = lastTransitionStates.put(pageView, now);
                                fromDashboard = "DASHBOARD".equals(previous)
                                        || "DASHBOARD_AOD".equals(previous);
                            }
                            returning = ("SMALL".equals(now) || "EXPAND".equals(now))
                                    && fromDashboard;
                            if (destination != null)
                                clock = ClockPageTransition.clock((View) destination.get(pageView), base);
                            if (returning) {
                                Object owner = blurController(pageView, controller);
                                if (clock != null) {
                                    returningClocks.add(clock);
                                    if (owner != null) returnControllers.put(clock, owner);
                                } else if (owner != null) {
                                    pendingReturns.put(transitionState, new WeakReference<>(owner));
                                }
                                // Configure runs synchronously before the destination
                                // is bound, so the blocker must be suppressed here.
                                returnTransition.set(Boolean.TRUE);
                                android.util.Log.d(
                                        DressroomModule.TAG, "Seconds-view return blur armed");
                            } else {
                                pendingReturns.remove(transitionState);
                                clearReturning(clock);
                            }
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            DressroomModule.note("Seconds-view return direction", error);
                        }
                        try {
                            return chain.proceed();
                        } catch (Throwable error) {
                            pendingReturns.remove(transitionState);
                            clearReturning(clock);
                            throw error;
                        } finally {
                            if (returning) {
                                if (previousReturn == null) returnTransition.remove();
                                else returnTransition.set(previousReturn);
                            }
                        }
                    })::unhook);
            hooks.add(module.hook(transitionBlock)
                    .setId("dressroom-advanced.seconds-return-blur-blocker")
                    .intercept(chain -> {
                        if (Boolean.TRUE.equals(returnTransition.get())
                                && Boolean.TRUE.equals(chain.getArg(0))) {
                            android.util.Log.d(DressroomModule.TAG,
                                    "Seconds-view return keeps blur visible");
                            return null;
                        }
                        return chain.proceed();
                    })::unhook);
            hooks.add(module.hook(attach).setId("dressroom-advanced.seconds-return-blur")
                    .intercept(chain -> {
                        Object transitionState = chain.getThisObject();
                        View clock = transition.clock(transitionState, transition.target(), base);
                        var pendingRef = pendingReturns.remove(transitionState);
                        Object pending = pendingRef == null ? null : pendingRef.get();
                        if (clock != null && pending != null) {
                            returningClocks.add(clock);
                            returnControllers.put(clock, pending);
                        }
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable error) {
                            clearReturning(clock);
                            throw error;
                        }
                        if (returning(clock) && clock.isAttachedToWindow()) {
                            Object owner = returnControllers.get(clock);
                            if (owner != null) {
                                try {
                                    State state = states.computeIfAbsent(
                                            clock, key -> new State(binding, enable, configured));
                                    float value = controllerScale.getFloat(owner);
                                    // Native only applies the destination state once the
                                    // dashboard repository clears DASHBOARD_ACTIVE. Start
                                    // the same return fade when the target is attached so
                                    // the clock never returns sharp.
                                    state.scale = value;
                                    state.dashboardReturn = true;
                                    if (state.update(clock)) {
                                        enable.invoke(clock, value, true);
                                        queue(clock, state);
                                    }
                                    android.util.Log.d(DressroomModule.TAG,
                                            "Seconds-view return blur enabled clock="
                                                    + System.identityHashCode(clock)
                                                    + " scale=" + value
                                                    + " attached=" + clock.isAttachedToWindow()
                                                    + " shown=" + clock.isShown());
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    DressroomModule.note("Seconds-view return blur start", error);
                                }
                            }
                        }
                        State state = states.get(clock);
                        android.util.Log.d(DressroomModule.TAG, "Transition attach: clock="
                                + (clock == null ? "none" : System.identityHashCode(clock))
                                + " returning=" + returning(clock)
                                + " reasons=" + (state == null ? "untracked" : state.reasons));
                        return result;
                    })::unhook);
            hooks.add(module.hook(finish).setId("dressroom-advanced.seconds-return-blur-end")
                    .intercept(chain -> {
                        Object transitionState = chain.getThisObject();
                        try {
                            return chain.proceed();
                        } finally {
                            pendingReturns.remove(transitionState);
                            clearReturning(transition.clock(transitionState, transition.target(), base));
                        }
                    })::unhook);
            if (tracksBoundClock) module.deoptimize(bind);
            module.deoptimize(configure);
            module.deoptimize(transitionBlock);
            for (var caller : DexSignatures.callers(configure))
                module.deoptimize(caller.method(loader));
            hooks.commit();
        }
        android.util.Log.i(DressroomModule.TAG,
                "Seconds-view return blur installed via " + configure.getName());
    }

    static void installHandoff(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException, java.io.IOException {
        ClassLoader loader = base.getClassLoader();
        var transition = ClockPageTransition.resolve(loader);
        var binding = ClockBlurRetryBinding.of(base);
        Field host = binding.task == null ? null : cachedBlurHost(module, binding);
        OneUi95BlurHost betaHost = binding.task == null ? OneUi95BlurHost.resolve(binding) : null;
        Method attach = transition.attach();
        Method finish = transition.finish();
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(attach).setId("dressroom-advanced.clock-handoff-attach")
                    .intercept(chain -> {
                        Object state = chain.getThisObject();
                        View from = transition.clock(state, transition.source(), base);
                        View to = transition.clock(state, transition.target(), base);
                        Handoff handoff = prepareHandoff(from, to, binding, host, betaHost);
                        if (handoff == null) return chain.proceed();
                        try {
                            Object result = chain.proceed();
                            if (outgoingHandoff(from) != handoff) outgoingClocks.remove(from, handoff);
                            return result;
                        } catch (Throwable error) {
                            outgoingClocks.remove(from, handoff);
                            throw error;
                        }
                    })::unhook);
            hooks.add(module.hook(finish).setId("dressroom-advanced.clock-handoff-finish")
                    .intercept(chain -> {
                        Object state = chain.getThisObject();
                        View from = transition.clock(state, transition.source(), base);
                        View to = transition.clock(state, transition.target(), base);
                        Handoff handoff = outgoingClocks.get(from);
                        if (handoff == null || handoff.target.get() != to) return chain.proceed();
                        try {
                            return chain.proceed();
                        } finally {
                            outgoingClocks.remove(from, handoff);
                        }
                    })::unhook);
            module.deoptimize(attach);
            module.deoptimize(finish);
            module.deoptimize(transition.update());
            for (var caller : DexSignatures.callers(attach))
                if (caller.name().equals("onPreDraw") && caller.parameters().isEmpty())
                    module.deoptimize(caller.method(loader));
            hooks.commit();
        }
    }

    private static Field cachedBlurHost(DressroomModule module, ClockBlurRetryBinding binding) {
        try {
            return NativeContract.findField(binding.manager.getType(), null, ViewGroup.class);
        } catch (ReflectiveOperationException error) {
            module.report("Optional clock reveal host unavailable", error);
            return null;
        }
    }

    private static Handoff prepareHandoff(View from, View to, ClockBlurRetryBinding binding,
            Field host, OneUi95BlurHost betaHost) {
        if (from == null || to == null || from == to || !from.isAttachedToWindow()) return null;
        // Adding the destination can synchronously submit blur from its attach callback.
        Handoff handoff = new Handoff(to, binding, host, betaHost);
        outgoingClocks.remove(to);
        outgoingClocks.put(from, handoff);
        return handoff;
    }

    private static void releaseBlurHost(View from, Handoff handoff) {
        ClockBlurSync.cancel(from);
        try {
            Object manager = handoff.binding.manager.get(from);
            if (manager == null) return;
            if (handoff.betaHost != null) {
                handoff.betaHost.release(manager);
                return;
            }
            // The old reveal can still fade its own text. Drop its handle to the shared drawable.
            if (handoff.host != null && handoff.host.get(manager) instanceof View cached
                    && "lock_clock_blur".equals(cached.getTag())) handoff.host.set(manager, null);
        } catch (IllegalAccessException error) {
            DressroomModule.note("Clock blur host handoff", error);
        }
    }

    static boolean outgoing(View clock) {
        Handoff handoff = outgoingHandoff(clock);
        return handoff != null && handoff.targetSubmitted;
    }

    static boolean hasHandoff() {
        return !outgoingClocks.isEmpty();
    }

    static void submitted(View clock) {
        if (outgoingClocks.isEmpty()) return;
        for (View view = clock; view != null;
                view = view.getParent() instanceof View parent ? parent : null) {
            for (var entry : outgoingClocks.entrySet()) {
                Handoff handoff = entry.getValue();
                View from = entry.getKey();
                if (handoff.targetSubmitted || handoff.target.get() != view || from == null
                        || outgoingHandoff(from) != handoff) continue;
                handoff.targetSubmitted = true;
                releaseBlurHost(from, handoff);
            }
        }
    }

    private static Handoff outgoingHandoff(View clock) {
        if (clock == null || outgoingClocks.isEmpty()) return null;
        for (View view = clock; view != null;
                view = view.getParent() instanceof View parent ? parent : null) {
            Handoff next = outgoingClocks.get(view);
            if (next == null) continue;
            View to = next.target.get();
            if (to != null && to.isAttachedToWindow() && view.getRootView() == to.getRootView())
                return next;
        }
        return null;
    }

    private static Field pageStateField(
            DexSignatures.Body body, Class<?> page, ClassLoader loader)
            throws ReflectiveOperationException {
        Set<Field> states = new HashSet<>();
        for (var read : body.reads()) {
            Field field;
            try {
                field = read.field().field(loader);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                continue;
            }
            // The clock page inherits its state field from FaceWidgetPage.
            if (!field.getDeclaringClass().isAssignableFrom(page)) continue;
            Class<?> type = field.getType();
            if (!type.isEnum()) continue;
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            Set<String> names = new HashSet<>();
            for (Object constant : constants) names.add(((Enum<?>) constant).name());
            if (names.contains("DASHBOARD") && names.contains("SMALL")) states.add(field);
        }
        if (states.size() != 1) throw new NoSuchFieldException("Clock page state");
        return states.iterator().next();
    }

    private static void queue(View clock, State state) {
        if (!ClockBlurSync.pending(clock)) ClockBlurSync.defer(clock, state::refresh);
    }

    private static void restoreAfterShade(View clock, State state) {
        var captured = state.shadeSurfaces;
        state.shadeSurfaces = null;
        if (captured.isEmpty()) return;
        clock.postOnAnimation(() -> {
            if (states.get(clock) == state && state.reasons.isEmpty()
                    && clock.isAttachedToWindow() && clock.isShown()
                    && doze.getOrDefault(clock, 0f) == 0f)
                BlurAppearance.restoreShadeSurfaces(clock.getRootView(), captured);
        });
    }

    /** Find the page's clock blur controller by its already resolved type. */
    private static Object blurController(View page, Class<?> controller) {
        for (Class<?> type = page.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().equals(controller)) continue;
                try {
                    field.setAccessible(true);
                    return field.get(page);
                } catch (IllegalAccessException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void clearReturning(View clock) {
        if (clock == null) return;
        returningClocks.remove(clock);
        returnControllers.remove(clock);
        State state = states.get(clock);
        if (state == null || !state.dashboardReturn) return;
        state.dashboardReturn = false;
        try {
            state.update(clock);
        } catch (ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Seconds-view return blur cleanup", error);
        }
    }

    static boolean returning(View clock) {
        return clock != null && returningClocks.contains(clock);
    }

    static boolean hasReturningClock() {
        return !returningClocks.isEmpty();
    }

    static boolean moving(View clock) {
        return !movingClocks.isEmpty() && movingClocks.contains(clock);
    }

    static boolean hasMovingClocks() {
        return !movingClocks.isEmpty();
    }

    static boolean retaining(View clock) {
        return retained.get() == clock;
    }

    static void beforeDraw(View clock) {
        if (!moving(clock) || ClockBlurSync.pending(clock)) return;
        State state = states.get(clock);
        if (state != null && state.moving) queue(clock, state);
    }

    static void forget(View clock) {
        if (movingClocks.remove(clock)) ClockBlurSync.syncHook();
        State state = states.get(clock);
        if (state != null) {
            state.moving = false;
            if (state.aod) {
                state.aod = false;
                try {
                    state.aodFields.active.setBoolean(clock, !state.aodFields.dozing.getBoolean(clock)
                            && Boolean.TRUE.equals(state.configured.get(clock)));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        if (!clock.isAttachedToWindow()) {
            states.remove(clock);
            doze.remove(clock);
        }
    }
}
