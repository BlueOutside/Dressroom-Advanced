package dev.denver.dressroomadvanced;

import android.widget.TextView;

import java.util.List;

/** Scope the RGB correction to the native strategy's own frame/finalizer. */
final class ClockBlurColors {
    record Scope(ClockBlurColorBinding binding, List<?> views) {}

    private static final ThreadLocal<Scope> current = new ThreadLocal<>();
    private static boolean getterInstalled;

    private ClockBlurColors() {}

    static synchronized void install(DressroomModule module, ClockBlurRetryBinding retry)
            throws ReflectiveOperationException, java.io.IOException {
        ClockBlurColorBinding binding = ClockBlurColorBinding.resolve(retry);
        if (binding == null) return;
        try (HookTransaction hooks = new HookTransaction()) {
            if (!getterInstalled) {
                hooks.add(TextColorHooks.acquire(module));
            }
            for (var writer : binding.writers) {
                hooks.add(module.hook(writer.method())
                        .setId("dressroom-advanced.clock-path-ink-scope")
                        .intercept(chain -> {
                            Scope scope = null;
                            try {
                                Object strategy = owner(writer, chain.getThisObject(), chain.getArg(0),
                                        binding.textViews.getDeclaringClass());
                                if (binding.textViews.getDeclaringClass().isInstance(strategy)
                                        && binding.textViews.get(strategy)
                                        instanceof List<?> views)
                                    scope = new Scope(binding, views);
                            } catch (ReflectiveOperationException | RuntimeException ignored) {
                                // An unfamiliar merged callback branch retains native behavior.
                            }
                            Scope previous = swap(scope);
                            try { return chain.proceed(); }
                            finally { swap(previous); }
                        })::unhook);
                module.deoptimize(writer.method());
            }
            hooks.commit();
            getterInstalled = true;
        }
    }

    static Object owner(ClockBlurColorBinding.Writer writer, Object receiver, Object argument,
            Class<?> strategyClass) throws IllegalAccessException {
        if (writer.owners().isEmpty()) return strategyClass.isInstance(argument) ? argument : null;
        Object found = null;
        for (var field : writer.owners()) {
            Object candidate = field.get(receiver);
            if (!strategyClass.isInstance(candidate) || writer.animator().get(candidate) != argument)
                continue;
            if (found != null && found != candidate) return null;
            found = candidate;
        }
        return found;
    }

    static Scope swap(Scope scope) {
        Scope previous = current.get();
        if (scope == null) current.remove();
        else current.set(scope);
        return previous;
    }

    static Integer read(TextView view) {
        Scope scope = current.get();
        if (scope == null) return null;
        try { return scope.binding.read(view, scope.views); }
        catch (IllegalAccessException | RuntimeException unavailable) { return null; }
    }
}
