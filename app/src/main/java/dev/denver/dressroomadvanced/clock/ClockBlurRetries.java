package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ClockBlurRetries {
    private final ClockBlurRetryBinding binding;
    private final java.lang.reflect.Method blurHost;
    private final boolean forceRefresh;
    private final BlurRetryState requests = new BlurRetryState();
    private final Map<Object, BlurRetryState.Ticket> tasks =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ClockBlurRetries(ClockBlurRetryBinding binding) {
        this.binding = binding;
        java.lang.reflect.Method host;
        try {
            host = NativeContract.findMethod(binding.render.getDeclaringClass(), null,
                    android.view.ViewGroup.class);
        } catch (ReflectiveOperationException unavailable) {
            host = null;
        }
        blurHost = host;
        boolean supportsForce = false;
        try {
            String skip = "applyTextBlurInner was skipped by blurOnAnimator is running";
            var body = DexSignatures.body(binding.submit, java.util.Set.of(skip));
            supportsForce = body.anchors().containsKey(skip) && body.calls().stream().anyMatch(call ->
                    call.owner().equals("Landroid/animation/ValueAnimator;")
                            && call.name().equals("isRunning") && call.parameters().isEmpty()
                            && call.result().equals("Z"));
        } catch (ReflectiveOperationException | java.io.IOException ignored) {
            // Unfamiliar force semantics keep native timing and the completion refresh.
        }
        forceRefresh = supportsForce;
    }

    static void install(DressroomModule module, Class<?> base)
            throws ReflectiveOperationException, java.io.IOException {
        ClockBlurRetryBinding binding = new ClockBlurRetryBinding(base);
        ClockBlurRetries lifecycle = new ClockBlurRetries(binding);
        GlyphOutlineBinding glyph = resolveGlyphs(module, base.getClassLoader());
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(binding.submit)
                    .setId("dressroom-advanced.native-blur-glyph-ready")
                    .intercept(chain -> {
                        Object manager = chain.getThisObject();
                        View clock;
                        try {
                            clock = binding.clock(manager);
                        } catch (ReflectiveOperationException error) {
                            return chain.proceed();
                        }
                        if (ClockBlurMotion.outgoing(clock)) return null;
                        ClockBlurAlignment.watch(clock, manager, binding.enabled);
                        if (binding.animator != null)
                            try {
                                ClockBlurAlignment.awaitAnimator(clock,
                                        (android.animation.ValueAnimator) binding.animator.get(manager));
                            } catch (IllegalAccessException ignored) {}
                        int retry = ((Number) chain.getArg(1)).intValue();
                        if (retry > 0 && !lifecycle.active(clock, manager)) return null;
                        BlurRetryState.Ticket ticket =
                                retry == 0 && !ClockBlurSync.rebuilding(clock)
                                        ? lifecycle.requests.begin(manager)
                                        : lifecycle.requests.current(manager);
                        if (retry == 0 && clock != null && clock.isShown() && clock.isAttachedToWindow()
                                && !ClockBlurSync.rebuilding(clock)) {
                            boolean canQueue = !ClockBlurSync.pending(clock);
                            boolean needsDraw = clock.isLayoutRequested();
                            try {
                                // Unsupported glyph layouts keep native readiness;
                                // retry lifetime checks still apply.
                                Object strategy = binding.strategy.get(manager);
                                if (strategy != null && binding.textViews.get(strategy) instanceof List<?> views) {
                                    UnlockBlur.glyphs(clock, views);
                                    if (glyph != null)
                                        needsDraw |= GlyphBlurReadiness.needsDraw(views, glyph);
                                }
                            } catch (ReflectiveOperationException ignored) {
                            }
                            boolean retain = shouldRetainReadySurface(
                                    needsDraw, ticket != null, lifecycle.keepReadySurface(manager));
                            if ((canQueue || retain) && needsDraw && ticket != null) {
                                WeakReference<Object> reference = new WeakReference<>(manager);
                                int color = ((Number) chain.getArg(0)).intValue();
                                boolean force = (Boolean) chain.getArg(2);
                                boolean queued = ClockBlurSync.defer(clock, target -> {
                                    Object current = reference.get();
                                    if (!lifecycle.requests.accepts(ticket)
                                            || !lifecycle.active(target, current)) return;
                                    try {
                                        binding.submit.invoke(current, color, retry, force);
                                    } catch (ReflectiveOperationException error) {
                                        module.report("Native blur redraw resume unavailable", error);
                                    }
                                });
                                // A live window-blur matte already exists. Rendering before the new
                                // glyph draw can submit stale outlines or briefly apply Samsung's
                                // failed-readiness alpha (127), then snap to the final alpha.
                                // Rebuild after this draw instead; first render and unsupported
                                // backends retain Samsung's initial submission and timer retries.
                                if (queued && retain) return null;
                            }
                        }
                        // Samsung's explicit force argument permits a geometry refresh during
                        // reveal without ending the animator (and running its finalizer early).
                        // Only use it after our draw barrier on an already live native surface.
                        if (Boolean.FALSE.equals(chain.getArg(2))
                                && lifecycle.forceAfterDraw(clock, manager))
                            return chain.proceed(new Object[] {
                                    chain.getArg(0), chain.getArg(1), true});
                        return chain.proceed();
                    })::unhook);

            if (binding.task != null) {
                hooks.add(module.hook(binding.task)
                        .setId("dressroom-advanced.native-blur-retry-ticket")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            BlurRetryState.Ticket ticket = lifecycle.requests.current(chain.getArg(0));
                            if (ticket != null) lifecycle.tasks.put(chain.getThisObject(), ticket);
                            return result;
                        })::unhook);
                hooks.add(module.hook(binding.run)
                        .setId("dressroom-advanced.native-blur-retry-current")
                        .intercept(chain -> {
                            BlurRetryState.Ticket ticket = lifecycle.tasks.remove(chain.getThisObject());
                            // R8 also uses this Runnable for NestedScrollView.
                            if (ticket != null && !lifecycle.requests.accepts(ticket)) return null;
                            return chain.proceed();
                        })::unhook);
            }
            hooks.add(module.hook(binding.collect)
                    .setId("dressroom-advanced.native-blur-new-glyphs")
                    .intercept(chain -> {
                        lifecycle.cancelManager(chain.getThisObject());
                        return chain.proceed();
                    })::unhook);
            for (String event : new String[] {"removeBlur", "onDetachedFromWindow"}) {
                hooks.add(module.hook(base.getDeclaredMethod(event))
                        .setId("dressroom-advanced.native-blur-cancel-" + event)
                        .intercept(chain -> {
                            if (ClockBlurContinuity.retaining((View) chain.getThisObject())) return chain.proceed();
                            lifecycle.cancelClock((View) chain.getThisObject());
                            return chain.proceed();
                        })::unhook);
            }
            hooks.add(module.hook(base.getDeclaredMethod("setBlurEffectEnabled", boolean.class))
                    .setId("dressroom-advanced.native-blur-disabled")
                    .intercept(chain -> {
                        if (Boolean.FALSE.equals(chain.getArg(0)))
                            lifecycle.cancelClock((View) chain.getThisObject());
                        return chain.proceed();
                    })::unhook);

            module.deoptimize(binding.entry);
            module.deoptimize(binding.submit);
            module.deoptimize(binding.collect);
            module.deoptimize(NativeContract.findMethod(
                    base, null, void.class, int.class, android.widget.TextView[].class));
            if (binding.run != null) module.deoptimize(binding.run);
            module.deoptimize(base.getDeclaredMethod("setClockTextBlur"));
            hooks.commit();
        }
        try { ClockBlurContinuity.install(module, binding); }
        catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            module.report("Optional continuous clock blur unavailable", error);
        }
    }

    private static GlyphOutlineBinding resolveGlyphs(DressroomModule module, ClassLoader loader) {
        try {
            return GlyphOutlineBinding.resolve(loader);
        } catch (ReflectiveOperationException | java.io.IOException | LinkageError error) {
            module.report("Optional cached glyph readiness unavailable", error);
            return null;
        }
    }

    private boolean active(View clock, Object manager) {
        if (clock == null || manager == null || !clock.isAttachedToWindow() || !clock.isShown())
            return false;
        try {
            return binding.manager.get(clock) == manager;
        } catch (IllegalAccessException error) {
            return false;
        }
    }

    static boolean shouldRetainReadySurface(
            boolean needsDraw, boolean hasTicket, boolean readySurface) {
        return needsDraw && hasTicket && readySurface;
    }

    private boolean keepReadySurface(Object manager) {
        return readySurface(manager);
    }

    private boolean forceAfterDraw(View clock, Object manager) {
        if (!forceRefresh || !ClockBlurSync.rebuilding(clock) || !active(clock, manager)
                || binding.animator == null || !readySurface(manager)) return false;
        try {
            return binding.animator.get(manager) instanceof android.animation.ValueAnimator fade
                    && fade.isRunning();
        } catch (IllegalAccessException | RuntimeException unavailable) {
            return false;
        }
    }

    private boolean readySurface(Object manager) {
        if (blurHost == null || binding.enabled == null) return false;
        try {
            Object strategy = binding.strategy.get(manager);
            if (strategy == null || !Boolean.TRUE.equals(binding.enabled.invoke(manager))) return false;
            Object value = blurHost.invoke(strategy);
            if (!(value instanceof View host) || !host.isShown() || host.getAlpha() <= 0f
                    || !BlurAppearance.hasWindowDrawable(host) || host.getBackground().getAlpha() <= 0)
                return false;
            return BlurAppearance.visibleWindowRequest(BlurAppearance.nativeRequest(host));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private void cancelClock(View clock) {
        ClockBlurContinuity.forget(clock);
        ClockBlurAlignment.forget(clock);
        ClockBlurSync.cancel(clock);
        try {
            requests.cancel(binding.manager.get(clock));
        } catch (IllegalAccessException ignored) {
        }
    }

    private void cancelManager(Object manager) {
        requests.cancel(manager);
        try {
            View clock = binding.clock(manager);
            if (clock != null) ClockBlurSync.cancel(clock);
        } catch (IllegalAccessException ignored) {
        }
    }
}
