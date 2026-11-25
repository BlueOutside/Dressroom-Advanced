package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Suppresses Samsung's persistent {@code SGG_ACTIVE} clock-blur blocker on
 * {@code ClockBlurController} when LockStar ({@code dls_state & 16}) is active and the user opts in
 * via Extras.
 */
final class LockStarClockBlur {
    static final String KEY = "lockStarClockBlur";
    static final String ANCHOR = "SGG active state changed: ";

    private static final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Object, Boolean> nativeSggStates =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static volatile Contract activeContract;
    private static boolean listening;
    private static Context appContext;

    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }

    @FunctionalInterface
    interface AnchorFinder {
        List<DexSignatures.Body> find(Set<String> anchors)
                throws ReflectiveOperationException, IOException;
    }

    record Contract(Method setSggActive, Field stateMachine, Field sggActive) {}

    private LockStarClockBlur() {}

    static boolean effectiveSggActive(boolean nativeSggActive, boolean bypassEnabled) {
        return nativeSggActive && !bypassEnabled;
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess()
                || android.os.Build.VERSION.SDK_INT < 37
                || loader == null
                || !installed.add(loader)) return;
        try {
            Class<?> controllerClass;
            try {
                controllerClass =
                        Class.forName(
                                "com.samsung.android.app.facewidget.pages.clock.blur.ClockBlurController",
                                false,
                                loader);
            } catch (ClassNotFoundException absent) {
                return;
            }
            Contract contract =
                    resolve(
                            controllerClass,
                            loader,
                            anchors -> DexSignatures.find(loader, anchors));
            if (contract == null) return;
            var handle =
                    module.hook(contract.setSggActive())
                            .setId("dressroom-advanced.lockstar-clock-blur")
                            .intercept(
                                    chain -> {
                                        if (Boolean.TRUE.equals(writing.get()))
                                            return chain.proceed();
                                        Object controller = chain.getThisObject();
                                        boolean requested = Boolean.TRUE.equals(chain.getArg(0));
                                        nativeSggStates.put(controller, requested);
                                        boolean effective =
                                                effectiveSggActive(requested, bypassEnabled());
                                        return effective == requested
                                                ? chain.proceed()
                                                : chain.proceed(new Object[] {effective});
                                    });
            try {
                module.deoptimize(contract.setSggActive());
            } catch (RuntimeException | Error error) {
                handle.unhook();
                throw error;
            }
            activeContract = contract;
            if (!listening) {
                listening = true;
                RuntimeExtras.listen(LockStarClockBlur::scheduleRefresh);
            }
        } catch (Exception | LinkageError error) {
            installed.remove(loader);
            module.report("Optional LockStar clock blur override unavailable", error);
        }
    }

    static void observeController(Object controller) {
        Contract contract = activeContract;
        if (controller == null || contract == null) return;
        if (!contract.setSggActive().getDeclaringClass().isInstance(controller)) return;
        boolean known;
        synchronized (nativeSggStates) {
            known = nativeSggStates.containsKey(controller);
        }
        if (!known) {
            try {
                Object machine = contract.stateMachine().get(controller);
                if (machine != null) {
                    boolean current = contract.sggActive().getBoolean(machine);
                    nativeSggStates.putIfAbsent(controller, current);
                    if (current && bypassEnabled()) applyToController(controller, contract);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private static void scheduleRefresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) refreshControllers();
        else Main.HANDLER.post(LockStarClockBlur::refreshControllers);
    }

    private static void refreshControllers() {
        Contract contract = activeContract;
        if (contract == null) return;
        List<Object> controllers;
        synchronized (nativeSggStates) {
            controllers = new ArrayList<>(nativeSggStates.keySet());
        }
        for (Object controller : controllers) {
            applyToController(controller, contract);
        }
    }

    private static void applyToController(Object controller, Contract contract) {
        if (controller == null) return;
        Boolean nativeState = nativeSggStates.get(controller);
        if (nativeState == null) return;
        boolean target = effectiveSggActive(nativeState, bypassEnabled());
        Boolean previous = writing.get();
        writing.set(true);
        try {
            contract.setSggActive().invoke(controller, target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }

    static Contract resolve(Class<?> controllerClass, ClassLoader loader, AnchorFinder finder)
            throws ReflectiveOperationException, IOException {
        String controllerDesc = DexSignatures.descriptor(controllerClass);
        List<DexSignatures.Body> matches =
                finder.find(Set.of(ANCHOR)).stream()
                        .filter(
                                body ->
                                        body.anchors().containsKey(ANCHOR)
                                                && controllerDesc.equals(body.owner())
                                                && (body.access() & Modifier.STATIC) == 0
                                                && body.parameters().equals(List.of("Z"))
                                                && "V".equals(body.result()))
                        .toList();
        if (matches.isEmpty()) return null;
        if (matches.size() > 1)
            throw new NoSuchMethodException("Ambiguous ClockBlurController SGG setter");
        DexSignatures.Body body = matches.get(0);
        Method setter = body.method(loader);
        setter.setAccessible(true);
        Set<DexSignatures.Member> machineReads = new LinkedHashSet<>();
        for (var read : body.reads()) {
            var member = read.field();
            if (controllerDesc.equals(member.owner()) && member.type().startsWith("L")) {
                machineReads.add(member);
            }
        }
        if (machineReads.size() != 1)
            throw new NoSuchFieldException("Unexpected ClockBlurController stateMachine read");
        Field stateMachine = machineReads.iterator().next().field(loader);
        stateMachine.setAccessible(true);
        String machineDesc = DexSignatures.descriptor(stateMachine.getType());
        Set<DexSignatures.Member> flagWrites = new LinkedHashSet<>();
        for (var write : body.writes()) {
            var member = write.field();
            if (machineDesc.equals(member.owner()) && "Z".equals(member.type())) {
                flagWrites.add(member);
            }
        }
        if (flagWrites.size() != 1)
            throw new NoSuchFieldException("Unexpected ClockBlurStateMachine sggActive write");
        Field sggField = flagWrites.iterator().next().field(loader);
        sggField.setAccessible(true);
        return new Contract(setter, stateMachine, sggField);
    }

    private static boolean bypassEnabled() {
        try {
            if (appContext == null) {
                appContext =
                        (Context)
                                Class.forName("android.app.ActivityThread")
                                        .getMethod("currentApplication")
                                        .invoke(null);
            }
            return appContext != null && RuntimeExtras.enabled(appContext, KEY);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }
}
