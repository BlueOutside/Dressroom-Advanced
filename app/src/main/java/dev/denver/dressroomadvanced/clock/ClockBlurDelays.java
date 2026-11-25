package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Rect;
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

final class ClockBlurDelays {
    static final String KEY = "shortClockBlurDelay";
    static final long NATIVE_EDITOR_SETTLE_MS = 300L;
    static final long DEFAULT_EDITOR_SETTLE_MS = 48L;
    static final long SHORT_EDITOR_SETTLE_MS = 24L;
    static final String POSITION_IDLE_ANCHOR = "clockPositionRect idle -> emit true";

    private static final Set<String> shortened = Set.of("AlphaChange", "StretchAnimation",
            "MediaExpand", "ShortcutSwiping", "OverlaySwiping");
    private static final Set<Class<?>> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> editorInstalled =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Class<?>, Field[]> continuationFieldCache =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static Context context;

    @FunctionalInterface
    interface AnchorFinder {
        List<DexSignatures.Body> find(Set<String> anchors)
                throws ReflectiveOperationException, IOException;
    }

    record EditorContract(Class<?> coordinator, Method delay) {}

    private ClockBlurDelays() {}

    static long editorSettleDelayMs(boolean shortDelayEnabled) {
        return shortDelayEnabled ? SHORT_EDITOR_SETTLE_MS : DEFAULT_EDITOR_SETTLE_MS;
    }

    static synchronized void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        if (module.inEditorProcess()) return;
        String anchor = ": Scheduled deferred update (delay=";
        var bodies = DexSignatures.find(loader, Set.of(anchor)).stream().filter(body ->
                body.anchors().containsKey(anchor) && body.matches(anchor)
                        && body.calls().stream().anyMatch(call ->
                            call.owner().equals("Landroid/os/Handler;")
                            && call.name().equals("postDelayed")
                            && call.parameters().equals(List.of("Ljava/lang/Runnable;", "J"))))
                .toList();
        if (bodies.size() != 1) return;
        Method schedule = bodies.get(0).method(loader);
        Class<?> owner = schedule.getDeclaringClass();
        if (installed.contains(owner)) return;
        Field handler = NativeContract.findField(owner, null, Handler.class);
        Field tag = NativeContract.findField(owner, null, String.class);
        Field callback = NativeContract.findField(owner, null, Runnable.class);
        module.hook(schedule).setId("dressroom-advanced.short-clock-blur-delay")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object debouncer = chain.getThisObject();
                    try {
                        Handler target = (Handler) handler.get(debouncer);
                        if (target == null || Looper.myLooper() != target.getLooper()
                                || !shortened.contains((String) tag.get(debouncer))
                                || !enabled()) return result;
                        Runnable pending = (Runnable) callback.get(debouncer);
                        if (pending != null) {
                            // Keep the native callback and cancellation handle.
                            target.removeCallbacks(pending);
                            target.postDelayed(pending, 16);
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                    return result;
                });
        module.deoptimize(schedule);
        installed.add(owner);
    }

    static synchronized void installEditor(DressroomModule module, ClassLoader loader) {
        if (!module.inEditorProcess() || loader == null || !editorInstalled.add(loader)) return;
        try {
            EditorContract contract =
                    resolveEditorContract(loader, anchors -> DexSignatures.find(loader, anchors));
            if (contract == null) return;
            var handle =
                    module.hook(contract.delay())
                            .setId("dressroom-advanced.editor-clock-blur-settle")
                            .intercept(
                                    chain -> {
                                        if (!(chain.getArg(0) instanceof Number ms)
                                                || ms.longValue() != NATIVE_EDITOR_SETTLE_MS
                                                || !capturesCoordinator(
                                                        chain.getArg(1), contract.coordinator()))
                                            return chain.proceed();
                                        boolean shorter = false;
                                        try {
                                            shorter = enabled();
                                        } catch (ReflectiveOperationException ignored) {
                                        }
                                        return chain.proceed(
                                                new Object[] {
                                                    editorSettleDelayMs(shorter), chain.getArg(1)
                                                });
                                    });
            try {
                module.deoptimize(contract.delay());
            } catch (RuntimeException | Error error) {
                handle.unhook();
                throw error;
            }
        } catch (Exception | LinkageError error) {
            editorInstalled.remove(loader);
            DressroomModule.note("Optional editor clock blur settle tuning unavailable", error);
        }
    }

    static EditorContract resolveEditorContract(ClassLoader loader, AnchorFinder finder)
            throws ReflectiveOperationException, IOException {
        List<DexSignatures.Body> matches =
                finder.find(Set.of(POSITION_IDLE_ANCHOR)).stream()
                        .filter(
                                body ->
                                        body.anchors().containsKey(POSITION_IDLE_ANCHOR)
                                                && "invokeSuspend".equals(body.name())
                                                && body.parameters()
                                                        .equals(List.of("Ljava/lang/Object;"))
                                                && "Ljava/lang/Object;".equals(body.result()))
                        .toList();
        if (matches.isEmpty()) return null;
        if (matches.size() > 1)
            throw new NoSuchMethodException("Ambiguous editor clock position idle coroutine");
        DexSignatures.Body positionBody = matches.get(0);
        Class<?> lambdaClass = positionBody.method(loader).getDeclaringClass();
        Set<Class<?>> coordinators = new LinkedHashSet<>();
        for (var read : positionBody.reads()) {
            var member = read.field();
            if (!positionBody.owner().equals(member.owner())) continue;
            if (!member.type().startsWith("L") || member.type().endsWith("Rect;")) continue;
            Class<?> fieldType = member.field(loader).getType();
            if (fieldType.isInterface()
                    || fieldType.isPrimitive()
                    || fieldType == Rect.class
                    || fieldType == Object.class
                    || fieldType.getName().startsWith("java.")
                    || fieldType.getName().startsWith("android.")) continue;
            coordinators.add(fieldType);
        }
        if (coordinators.size() != 1)
            throw new NoSuchFieldException(
                    "Unexpected editor idle coordinator captures: " + coordinators);
        Set<Method> delays = new LinkedHashSet<>();
        for (var call : positionBody.calls()) {
            if (!call.isStatic()
                    || !"Ljava/lang/Object;".equals(call.result())
                    || call.parameters().size() != 2
                    || !"J".equals(call.parameters().get(0))) continue;
            Method candidate = call.method(loader);
            if (candidate.getParameterTypes()[1].isAssignableFrom(lambdaClass)) {
                candidate.setAccessible(true);
                delays.add(candidate);
            }
        }
        if (delays.size() != 1)
            throw new NoSuchMethodException("Unexpected editor coroutine delay target: " + delays);
        return new EditorContract(coordinators.iterator().next(), delays.iterator().next());
    }

    static boolean capturesCoordinator(Object continuation, Class<?> coordinatorClass) {
        if (continuation == null || coordinatorClass == null) return false;
        Class<?> type = continuation.getClass();
        Field[] candidateFields =
                continuationFieldCache.computeIfAbsent(
                        type,
                        cls -> {
                            List<Field> list = new ArrayList<>();
                            for (Field f : cls.getDeclaredFields()) {
                                if (Modifier.isStatic(f.getModifiers())) continue;
                                Class<?> ft = f.getType();
                                if (ft == coordinatorClass || ft == Object.class) {
                                    f.setAccessible(true);
                                    list.add(f);
                                }
                            }
                            return list.toArray(new Field[0]);
                        });
        for (Field field : candidateFields) {
            try {
                if (coordinatorClass.isInstance(field.get(continuation))) return true;
            } catch (IllegalAccessException ignored) {
            }
        }
        return false;
    }

    private static boolean enabled() throws ReflectiveOperationException {
        if (context == null)
            context = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        return context != null && RuntimeExtras.enabled(context, KEY);
    }
}
