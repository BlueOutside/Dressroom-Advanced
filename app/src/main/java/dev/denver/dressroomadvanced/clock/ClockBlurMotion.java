package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
        boolean targetSubmitted;

        Handoff(View target) {
            this.target = new WeakReference<>(target);
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
            if (moving || aod) movingClocks.add(clock);
            else movingClocks.remove(clock);
            return moving || aod;
        }

        void refresh(View clock) {
            if (states.get(clock) != this || !(moving || aod) || !clock.isShown()) return;
            try {
                Object manager = binding.manager.get(clock);
                if (!Boolean.TRUE.equals(binding.enabled.invoke(manager)))
                    enable.invoke(clock, scale, false);
                Reflect.call(clock, "setClockTextBlur");
            } catch (ReflectiveOperationException | RuntimeException error) {
                moving = false;
                movingClocks.remove(clock);
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
        if (reasons.isEmpty() || !Float.isFinite(amount) || amount < 0 || amount > 1) return false;
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
                        state.reasons = Set.copyOf(names);
                        state.scale = scale.getFloat(chain.getThisObject());
                        // The transition destination receives its first state
                        // before pre-draw attaches it. Keep the return marked from
                        // the page transition so this apply does not remove blur.
                        boolean transitionReturn = returning(clock);
                        if (transitionReturn && (names.contains("DASHBOARD_ACTIVE")
                                || names.contains("FACE_WIDGET_TRANSITIONING"))) {
                            state.dashboardReturn = true;
                        }
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
                            if (state.dashboardReturn
                                    && !names.contains("DASHBOARD_ACTIVE")
                                    && !names.contains("FACE_WIDGET_TRANSITIONING")) {
                                state.dashboardReturn = false;
                            }
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
            var supportChecks = DexSignatures.find(loader, Set.of(WallpaperAod.MAIN)).stream()
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
        String bindAnchor = "initViews() mClockView=";
        String updateAnchor = "updateContentsView()";
        var bodies = DexSignatures.find(loader, Set.of(
                "common_clock_transition_second", "makeSourceData()", "handleTransitionFinished()",
                bindAnchor, updateAnchor));
        DexSignatures.Body configuration = null;
        Class<?> transition = null;
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
            if (configuration != null) throw new NoSuchMethodException("Ambiguous page transition");
            configuration = body;
            transition = body.method(loader).getParameterTypes()[0];
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
        // The destination is stored on the page before its transition is configured.
        Field destination = configuration
                .uniqueRead(DexSignatures.descriptor(page), "Landroid/view/View;", null)
                .field(loader);
        Field pageState = pageStateField(configuration, page, loader);
        Method attach = transitionMethod(bodies, transition, "makeSourceData()");
        Method finish = transitionMethod(bodies, transition, "handleTransitionFinished()");
        Set<Field> sources = new HashSet<>();
        Set<Field> targets = new HashSet<>();
        for (var read : DexSignatures.body(attach).reads()) {
            var field = read.field();
            if (field.owner().equals(DexSignatures.descriptor(transition))
                    && field.type().equals("Landroid/view/View;")) {
                Field candidate = field.field(loader);
                // The source is final. Samsung assigns the destination before starting.
                if (Modifier.isFinal(candidate.getModifiers())) sources.add(candidate);
                else targets.add(candidate);
            }
        }
        if (sources.size() != 1 || targets.size() != 1)
            throw new NoSuchFieldException("Clock transition source or target");
        Field source = sources.iterator().next();
        Field target = targets.iterator().next();
        Field host = cachedBlurHost(module, binding);
        try (HookTransaction hooks = new HookTransaction()) {
            if (tracksBoundClock) {
                hooks.add(module.hook(bind).setId("dressroom-advanced.clock-bound-page")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            View clock = transitionClock((View) chain.getArg(0), base);
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
                        try {
                            View pageView = (View) chain.getThisObject();
                            Object value = pageState.get(pageView);
                            String now = value instanceof Enum<?> state ? state.name() : null;
                            View from = transitionClock((View) source.get(chain.getArg(0)), base);
                            boolean fromDashboard = dashboardClocks.contains(from);
                            if (!tracksBoundClock && now != null) {
                                String previous = lastTransitionStates.put(pageView, now);
                                fromDashboard = "DASHBOARD".equals(previous)
                                        || "DASHBOARD_AOD".equals(previous);
                            }
                            returning = ("SMALL".equals(now) || "EXPAND".equals(now))
                                    && fromDashboard;
                            View view = (View) destination.get(pageView);
                            if (returning) {
                                View clock = markReturning(view, base);
                                Object owner = blurController(pageView, controller);
                                if (clock != null && owner != null) {
                                    returnControllers.put(clock, owner);
                                }
                                // Configure runs synchronously before the destination
                                // is bound, so the blocker must be suppressed here.
                                returnTransition.set(Boolean.TRUE);
                                android.util.Log.d(
                                        DressroomModule.TAG, "Seconds-view return blur armed");
                            } else {
                                clearReturning(view, base);
                            }
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            DressroomModule.note("Seconds-view return direction", error);
                        }
                        try {
                            return chain.proceed();
                        } finally {
                            if (returning) returnTransition.remove();
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
                        View clock = transitionClock((View) target.get(transitionState), base);
                        View from = transitionClock((View) source.get(transitionState), base);
                        Handoff handoff = prepareHandoff(from, clock);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable error) {
                            if (handoff != null) outgoingClocks.remove(from, handoff);
                            throw error;
                        }
                        if (handoff != null) {
                            if (outgoingHandoff(from) == handoff) releaseBlurHost(from, binding, host);
                            else outgoingClocks.remove(from, handoff);
                        }
                        if (clock != null && returning(clock)) {
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
                                    enable.invoke(clock, value, true);
                                    if (state.update(clock)) queue(clock, state);
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
                            View to = (View) target.get(transitionState);
                            View from = transitionClock((View) source.get(transitionState), base);
                            Handoff next = outgoingClocks.get(from);
                            if (next != null && next.target.get() == transitionClock(to, base))
                                outgoingClocks.remove(from);
                            clearReturning(to, base);
                        }
                    })::unhook);
            if (tracksBoundClock) module.deoptimize(bind);
            module.deoptimize(configure);
            module.deoptimize(transitionBlock);
            module.deoptimize(attach);
            module.deoptimize(finish);
            // The holder invokes configure and bind through the parent declarations.
            String parent = DexSignatures.descriptor(page.getSuperclass());
            for (var body : bodies)
                if (body.owner().equals(parent) && body.matches(updateAnchor))
                    module.deoptimize(body.method(loader));
            for (var caller : DexSignatures.callers(configure))
                module.deoptimize(caller.method(loader));
            for (var caller : DexSignatures.callers(attach))
                if (caller.name().equals("onPreDraw") && caller.parameters().isEmpty())
                    module.deoptimize(caller.method(loader));
            hooks.commit();
        }
        android.util.Log.i(DressroomModule.TAG,
                "Seconds-view return blur installed via " + configure.getName());
    }

    private static Field cachedBlurHost(DressroomModule module, ClockBlurRetryBinding binding) {
        try {
            return NativeContract.findField(binding.manager.getType(), null, ViewGroup.class);
        } catch (ReflectiveOperationException error) {
            module.report("Optional clock reveal host unavailable", error);
            return null;
        }
    }

    private static Handoff prepareHandoff(View from, View to) {
        if (from == null || to == null || from == to || !from.isAttachedToWindow()) return null;
        // Adding the destination can synchronously submit blur from its attach callback.
        Handoff handoff = new Handoff(to);
        outgoingClocks.remove(to);
        outgoingClocks.put(from, handoff);
        return handoff;
    }

    private static void releaseBlurHost(View from, ClockBlurRetryBinding binding, Field host) {
        ClockBlurSync.cancel(from);
        if (host == null) return;
        try {
            Object manager = binding.manager.get(from);
            // The old reveal can still fade its own text. Drop its handle to the shared drawable.
            if (manager != null && host.get(manager) instanceof View cached
                    && "lock_clock_blur".equals(cached.getTag())) host.set(manager, null);
        } catch (IllegalAccessException error) {
            DressroomModule.note("Clock blur host handoff", error);
        }
    }

    static boolean outgoing(View clock) {
        return outgoingHandoff(clock) != null;
    }

    static boolean allowOutgoingClear(View clock) {
        Handoff handoff = outgoingHandoff(clock);
        return handoff != null && !handoff.targetSubmitted;
    }

    static void submitted(View clock) {
        if (outgoingClocks.isEmpty()) return;
        for (View view = clock; view != null;
                view = view.getParent() instanceof View parent ? parent : null) {
            for (Handoff handoff : outgoingClocks.values())
                if (handoff.target.get() == view) handoff.targetSubmitted = true;
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

    private static View transitionClock(View target, Class<?> base) {
        if (base.isInstance(target)) return target;
        if (target instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View clock = transitionClock(group.getChildAt(i), base);
                if (clock != null) return clock;
            }
        }
        return null;
    }

    private static Method transitionMethod(
            java.util.List<DexSignatures.Body> bodies, Class<?> owner, String anchor)
            throws ReflectiveOperationException {
        Method found = null;
        for (var body : bodies) {
            if (!body.owner().equals(DexSignatures.descriptor(owner)) || !body.matches(anchor)) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous transition anchor " + anchor);
            found = body.method(owner.getClassLoader());
        }
        if (found == null) throw new NoSuchMethodException("Missing transition anchor " + anchor);
        return found;
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

    private static View markReturning(View view, Class<?> base) {
        View clock = transitionClock(view, base);
        if (clock != null) returningClocks.add(clock);
        android.util.Log.d(DressroomModule.TAG, "Return mark view="
                + (view == null ? "none" : view.getClass().getSimpleName())
                + " clock=" + (clock == null ? "none" : System.identityHashCode(clock)));
        return clock;
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

    private static void clearReturning(View view, Class<?> base) {
        View clock = transitionClock(view, base);
        if (clock == null) return;
        returningClocks.remove(clock);
        returnControllers.remove(clock);
    }

    private static boolean returning(View clock) {
        return clock != null && returningClocks.contains(clock);
    }

    static boolean moving(View clock) {
        return !movingClocks.isEmpty() && movingClocks.contains(clock);
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
        movingClocks.remove(clock);
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
