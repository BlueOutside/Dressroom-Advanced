package dev.denver.dressroomadvanced;

import android.graphics.Typeface;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Provider font operations used by the shared file/axis renderer. */
final class FontBinding {
    private static final String FIXED_ANCHOR = "fixTypeface: [";
    private static final String CLOCK_TEXT_VIEW =
            "Lcom/samsung/android/uniform/widget/ClockTextView;";
    final Method fixed, variable;
    private final Method copy, refresh;
    private final ClockBinding clock;
    private static final ClassValue<FontBinding> bindings =
            new ClassValue<>() {
                @Override
                protected FontBinding computeValue(Class<?> owner) {
                    Class<?> base = owner;
                    while (base != null
                            && !base.getName()
                                    .equals(
                                            "com.samsung.android.uniform.widget.clock.AbsClockView"))
                        base = base.getSuperclass();
                    if (base == null) throw new IllegalStateException("Unknown clock font owner");
                    try {
                        if (owner != base) return bindings.get(base);
                        return new FontBinding(base);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported clock font contract", error);
                    }
                }
            };

    static FontBinding of(Object owner) {
        return bindings.get(owner instanceof Class<?> type ? type : owner.getClass());
    }

    private FontBinding(Class<?> base) throws ReflectiveOperationException {
        clock = ClockBinding.of(base);
        fixed = findMethod(base, FIXED_ANCHOR, null, Typeface.class, TextView[].class);
        variable =
                findMethod(base, null, CLOCK_TEXT_VIEW, int.class, String.class, TextView[].class);
        Class<?> data = clock.render.getParameterTypes()[0];
        copy =
                NativeContract.method(
                        data,
                        "copy",
                        data,
                        int.class,
                        String.class,
                        Integer.class,
                        Integer.class,
                        List.class);
        refresh = NativeContract.method(base, "refresh", void.class);
    }

    private static Method findMethod(
            Class<?> base, String anchor, String calledOwner, Class<?>... parameters)
            throws ReflectiveOperationException {
        try {
            return NativeContract.findMethod(base, null, void.class, parameters);
        } catch (NoSuchMethodException ambiguous) {
            List<Method> matches = new ArrayList<>();
            for (Method candidate : base.getDeclaredMethods()) {
                if (Modifier.isStatic(candidate.getModifiers())
                        || candidate.isBridge()
                        || candidate.isSynthetic()
                        || candidate.getReturnType() != void.class
                        || !Arrays.equals(candidate.getParameterTypes(), parameters)) continue;
                matches.add(candidate);
            }
            if (matches.size() == 1) {
                Method resolved = matches.get(0);
                resolved.setAccessible(true);
                return resolved;
            }
            if (matches.isEmpty()) throw ambiguous;
            List<Method> filtered = new ArrayList<>();
            try {
                for (Method candidate : matches) {
                    DexSignatures.Body body =
                            anchor != null
                                    ? DexSignatures.body(candidate, Set.of(anchor))
                                    : DexSignatures.body(candidate);
                    boolean ok =
                            anchor != null
                                    ? body.anchors().containsKey(anchor)
                                    : body.calls().stream()
                                            .anyMatch(call -> call.owner().equals(calledOwner));
                    if (ok) filtered.add(candidate);
                }
            } catch (IOException error) {
                throw (NoSuchMethodException)
                        new NoSuchMethodException("Failed to inspect clock font DEX contract")
                                .initCause(error);
            }
            if (filtered.size() != 1) throw ambiguous;
            Method resolved = filtered.get(0);
            resolved.setAccessible(true);
            return resolved;
        }
    }

    Method renderer(Class<?> owner) throws NoSuchMethodException {
        return owner.getMethod(clock.render.getName(), clock.render.getParameterTypes());
    }

    Object plain(Object original) throws ReflectiveOperationException {
        // Only the draw request loses Samsung's HEGT data; saved font identity stays intact.
        return copy.invoke(original, 0, "", null, null, null);
    }

    void apply(Object owner, Typeface typeface, TextView[] views)
            throws ReflectiveOperationException {
        fixed.invoke(owner, typeface, views);
    }

    void refresh(Object owner) throws ReflectiveOperationException {
        refresh.invoke(owner);
    }
}
