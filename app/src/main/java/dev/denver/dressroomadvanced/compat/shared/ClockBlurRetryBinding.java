package dev.denver.dressroomadvanced;

import android.view.View;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Follow the public clock API to its manager and the retry it actually schedules. */
final class ClockBlurRetryBinding {
    private static final class BindingFailure extends RuntimeException {
        final ReflectiveOperationException cause;

        BindingFailure(ReflectiveOperationException cause) {
            this.cause = cause;
        }
    }

    private static final ClassValue<ClockBlurRetryBinding> bindings =
            new ClassValue<>() {
                @Override
                protected ClockBlurRetryBinding computeValue(Class<?> type) {
                    try {
                        return new ClockBlurRetryBinding(type);
                    } catch (ReflectiveOperationException error) {
                        throw new BindingFailure(error);
                    } catch (IOException error) {
                        throw new BindingFailure(
                                new ReflectiveOperationException(
                                        "Could not read clock blur DEX", error));
                    }
                }
            };

    final Method entry, submit, collect, run, enabled, render;
    final Constructor<?> task;
    final Field manager, strategy, textViews, animator;
    private final Field owner, clock;

    static Class<?> absClockView(Class<?> clock) throws ClassNotFoundException {
        for (Class<?> type = clock; type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView"))
                return type;
        }
        throw new ClassNotFoundException("Missing AbsClockView: " + clock.getName());
    }

    static ClockBlurRetryBinding of(Class<?> base) throws ReflectiveOperationException {
        try {
            return bindings.get(absClockView(base));
        } catch (BindingFailure failure) {
            throw failure.cause;
        }
    }

    // Matches owner -> holder -> clock across obfuscated R8 fields via two-hop DEX reads.
    static Field[] ownerClockPath(DexSignatures.Body body, Class<?> ownerType, ClassLoader loader)
            throws ReflectiveOperationException {
        record OwnerPath(Field owner, Field clock) {}
        String ownerDescriptor = DexSignatures.descriptor(ownerType);
        Set<OwnerPath> paths = new LinkedHashSet<>();
        for (DexSignatures.Read outer : body.reads()) {
            if (!outer.field().owner().equals(ownerDescriptor)) continue;
            for (DexSignatures.Read inner : body.reads()) {
                if (!inner.field().owner().equals(outer.field().type())) continue;
                Field value = inner.field().field(loader);
                if (value.getType() == Object.class || View.class.isAssignableFrom(value.getType()))
                    paths.add(new OwnerPath(outer.field().field(loader), value));
            }
        }
        if (paths.size() != 1)
            throw new NoSuchFieldException(
                    "Ambiguous clock blur owner on " + ownerType.getName() + ": " + paths.size());
        OwnerPath path = paths.iterator().next();
        return new Field[] {path.owner(), path.clock()};
    }

    private static Method resolveEnabled(Class<?> base, Class<?> managerType) {
        try {
            Set<Method> readers = new LinkedHashSet<>();
            String owner = DexSignatures.descriptor(managerType);
            for (var call : DexSignatures.body(base.getDeclaredMethod(
                    "setBlurEffectEnabled", boolean.class)).calls()) {
                if (!call.isStatic() && call.owner().equals(owner)
                        && call.result().equals("Z") && call.parameters().isEmpty())
                    readers.add(call.method(base.getClassLoader()));
            }
            return readers.size() == 1 ? readers.iterator().next() : null;
        } catch (ReflectiveOperationException | IOException ignored) {
            return null;
        }
    }

    ClockBlurRetryBinding(Class<?> clockBase) throws ReflectiveOperationException, IOException {
        Class<?> base = absClockView(clockBase);
        ClassLoader loader = base.getClassLoader();
        Set<Method> entries = new LinkedHashSet<>();
        for (DexSignatures.Call call :
                DexSignatures.body(base.getDeclaredMethod("setClockTextBlur")).calls())
            if (!call.isStatic()
                    && call.result().equals("V")
                    && call.parameters().equals(List.of("Ljava/lang/Integer;", "Z")))
                entries.add(call.method(loader));
        if (entries.size() != 1)
            throw new NoSuchMethodException("Ambiguous clock blur entry: " + entries.size());
        entry = entries.iterator().next();
        Class<?> managerType = entry.getDeclaringClass();
        manager = NativeContract.findField(base, null, managerType);
        enabled = resolveEnabled(base, managerType);
        submit = NativeContract.findMethod(
                managerType, null, void.class, int.class, int.class, boolean.class);
        collect = NativeContract.findMethod(
                managerType, null, void.class, ArrayList.class, int.class, float.class);
        if (DexSignatures.body(entry).calls().stream().noneMatch(call -> calls(call, submit)))
            throw new NoSuchMethodException("Clock blur entry does not call retry method");

        DexSignatures.Body body = DexSignatures.body(submit);
        Set<Field> animators = new LinkedHashSet<>();
        for (DexSignatures.Read read : body.reads())
            if (read.field().owner().equals(DexSignatures.descriptor(managerType))
                    && read.field().type().equals("Landroid/animation/ValueAnimator;"))
                animators.add(read.field().field(loader));
        // Optional: unfamiliar animation layouts keep the existing retry hooks.
        animator = animators.size() == 1 ? animators.iterator().next() : null;
        Set<Method> renders = new LinkedHashSet<>();
        for (DexSignatures.Call call : body.calls())
            if (!call.isStatic() && call.parameters().size() == 3
                    && call.parameters().get(0).equals("Landroid/widget/ImageView;")
                    && call.parameters().get(1).equals(
                            "Lcom/samsung/android/clockpack/plugins/clock/WeatherInfo;"))
                renders.add(call.method(loader));
        if (renders.size() != 1)
            throw new NoSuchMethodException("Ambiguous clock blur strategy: " + renders.size());
        render = renders.iterator().next();
        Class<?> strategyType = render.getDeclaringClass();
        strategy = NativeContract.findField(managerType, null, strategyType);
        textViews = NativeContract.findField(strategyType, null, ArrayList.class);
        Set<Class<?>> tasks = new LinkedHashSet<>();
        for (DexSignatures.Call call : body.calls())
            if (call.name().equals("<init>")
                    && call.parameters().equals(
                            List.of(DexSignatures.descriptor(managerType), "I", "I", "Z")))
                tasks.add(Class.forName(
                        call.owner().substring(1, call.owner().length() - 1).replace('/', '.'),
                        false, loader));
        if (tasks.size() != 1)
            throw new NoSuchMethodException("Ambiguous clock blur retry task: " + tasks.size());
        Class<?> taskType = tasks.iterator().next();
        if (!Runnable.class.isAssignableFrom(taskType))
            throw new NoSuchMethodException("Clock blur retry is not runnable");
        task = taskType.getDeclaredConstructor(
                managerType, int.class, int.class, boolean.class);
        task.setAccessible(true);
        run = taskType.getDeclaredMethod("run");
        if (DexSignatures.body(run).calls().stream().noneMatch(call -> calls(call, submit)))
            throw new NoSuchMethodException("Clock blur retry does not return to its manager");

        // The retry reads manager -> holder -> clock. Match both DEX reads;
        // unrelated Object fields in R8's merged holder classes are not enough.
        Field[] ownerPath = ownerClockPath(body, managerType, loader);
        owner = ownerPath[0];
        clock = ownerPath[1];
    }

    View clock(Object nativeManager) throws IllegalAccessException {
        Object holder = owner.get(nativeManager);
        Object value = holder == null ? null : clock.get(holder);
        return value instanceof View view ? view : null;
    }

    private static boolean calls(DexSignatures.Call call, Method method) {
        return !call.isStatic()
                && call.owner().equals(DexSignatures.descriptor(method.getDeclaringClass()))
                && call.name().equals(method.getName())
                && call.result().equals("V")
                && call.parameters().equals(List.of("I", "I", "Z"));
    }
}
