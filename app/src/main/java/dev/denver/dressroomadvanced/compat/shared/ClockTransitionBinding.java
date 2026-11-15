package dev.denver.dressroomadvanced;

import android.animation.Animator;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolved structurally per provider; transition state is not tied to hardcoded R8 names. */
final class ClockTransitionBinding {
    private static final String SPRING_ANCHOR = "useSpringAnimation: changed to ";
    private static final String DOZING_ANCHOR = "onDozingChanged: ";
    final Method transition;
    final Field controller, expanded, dozing, animation, axes, amount, mediaExpanded;
    private static final ClassValue<ClockTransitionBinding> bindings =
            new ClassValue<>() {
                @Override
                protected ClockTransitionBinding computeValue(Class<?> type) {
                    try {
                        return new ClockTransitionBinding(type);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Clock transition ABI unavailable", e);
                    }
                }
            };

    static ClockTransitionBinding of(Class<?> type) {
        return bindings.get(type);
    }

    static ClockTransitionBinding of(Object clock) {
        return of(clock.getClass());
    }

    private ClockTransitionBinding(Class<?> base) throws ReflectiveOperationException {
        while (!base.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")) {
            base = base.getSuperclass();
            if (base == null) throw new ClassNotFoundException("Clock transition owner");
        }
        ClassLoader loader = base.getClassLoader();
        String baseDesc = DexSignatures.descriptor(base);
        transition = findTransition(base);

        Field resolved = null;
        for (Field field : base.getDeclaredFields()) {
            Class<?> owner = field.getType();
            if (!Modifier.isStatic(field.getModifiers())
                    && !owner.isPrimitive()
                    && NativeContract.hasField(owner, Animator.class)
                    && NativeContract.hasField(owner, List.class)) {
                if (resolved != null)
                    throw new NoSuchFieldException("Ambiguous clock transition owners");
                resolved = field;
            }
        }
        if (resolved == null) throw new NoSuchFieldException("Clock transition controller");
        resolved.setAccessible(true);
        controller = resolved;

        Class<?> owner = controller.getType();
        String ctrlDesc = DexSignatures.descriptor(owner);
        try {
            DexSignatures.Body mediaBody =
                    DexSignatures.body(
                            NativeContract.method(
                                    base, "setMediaExpanded", void.class, boolean.class));
            mediaExpanded = mediaBody.uniqueWrite(baseDesc, "Z", null).field(loader);
            expanded = mediaBody.uniqueWrite(ctrlDesc, "Z", null).field(loader);

            DexSignatures.Body dozeAmountBody =
                    DexSignatures.body(
                            NativeContract.method(base, "setDozeAmount", void.class, float.class));
            amount = dozeAmountBody.uniqueWrite(baseDesc, "F", null).field(loader);

            DexSignatures.Body setDozingBody =
                    DexSignatures.body(
                            NativeContract.method(base, "setDozing", void.class, boolean.class));
            dozing = findDozingField(baseDesc, ctrlDesc, loader, setDozingBody);
        } catch (IOException error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Clock transition DEX inspection failed")
                            .initCause(error);
        }

        animation = NativeContract.findField(owner, null, Animator.class);
        axes = NativeContract.findField(owner, null, List.class);
    }

    private static Method findTransition(Class<?> base) throws ReflectiveOperationException {
        Method found = null;
        for (Method candidate : base.getDeclaredMethods()) {
            if (Modifier.isStatic(candidate.getModifiers())
                    || candidate.isBridge()
                    || candidate.isSynthetic()
                    || candidate.getReturnType() != void.class
                    || !Arrays.equals(
                            candidate.getParameterTypes(),
                            new Class<?>[] {float.class, boolean.class})) continue;
            try {
                if (!DexSignatures.body(candidate, Set.of(SPRING_ANCHOR))
                        .anchors()
                        .containsKey(SPRING_ANCHOR)) continue;
            } catch (IOException error) {
                throw (NoSuchMethodException)
                        new NoSuchMethodException("Failed to inspect clock transition DEX body")
                                .initCause(error);
            }
            if (found != null)
                throw new NoSuchMethodException("Ambiguous clock transition methods");
            found = candidate;
        }
        if (found == null)
            throw new NoSuchMethodException("Missing clock transition method on " + base.getName());
        found.setAccessible(true);
        return found;
    }

    private static Field findDozingField(
            String baseDesc, String ctrlDesc, ClassLoader loader, DexSignatures.Body setDozingBody)
            throws ReflectiveOperationException, IOException {
        Set<Method> seen = new LinkedHashSet<>();
        DexSignatures.Body matched = null;
        for (DexSignatures.Call call : setDozingBody.calls()) {
            if (call.isStatic()
                    || !call.owner().equals(baseDesc)
                    || !call.parameters().isEmpty()
                    || !call.result().equals("V")) continue;
            Method target = call.method(loader);
            if (!seen.add(target)) continue;
            DexSignatures.Body candidate = DexSignatures.body(target, Set.of(DOZING_ANCHOR));
            if (!candidate.anchors().containsKey(DOZING_ANCHOR)) continue;
            if (matched != null)
                throw new NoSuchFieldException("Ambiguous dozing callback in " + baseDesc);
            matched = candidate;
        }
        if (matched == null)
            throw new NoSuchFieldException("Missing dozing callback in " + baseDesc);
        return matched.uniqueWrite(ctrlDesc, "Z", null).field(loader);
    }
}
