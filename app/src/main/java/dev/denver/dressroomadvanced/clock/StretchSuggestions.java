package dev.denver.dressroomadvanced;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

final class StretchSuggestions {
    static final String KEY = "relaxAutoStretch";
    private static final String CONTEXT = "Landroid/content/Context;";
    private static final Set<Class<?>> installed = Collections.newSetFromMap(new WeakHashMap<>());

    private StretchSuggestions() {}

    static synchronized void install(DressroomModule module, Class<?> base) {
        if (installed.contains(base)) return;
        try {
            Method entry = NativeContract.method(base, "isStretchable", boolean.class, List.class);
            var predicates = DexSignatures.body(entry).calls().stream().filter(call ->
                    call.isStatic() && call.result().equals("Z")
                            && call.parameters().equals(List.of("Ljava/util/ArrayList;", CONTEXT, "I", "I")))
                    .toList();
            if (predicates.size() != 1) return;
            Method policy = predicates.get(0).method(base.getClassLoader());
            String spread = "isStretchable: false, diff=";
            String ratio = "isStretchable: false (vertical imbalance), hegts=";
            var body = DexSignatures.body(policy, Set.of(spread, ratio));
            if (!body.anchors().containsKey(spread) || !body.anchors().containsKey(ratio)) return;
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(
                        module.hook(policy)
                                        .setId("dressroom-advanced.relaxed-auto-stretch")
                                        .intercept(
                                                chain -> {
                                                    if (chain.getArg(1) instanceof Context context
                                                            && chain.getArg(0)
                                                                    instanceof ArrayList<?> heights
                                                            && usable(heights)
                                                            && RuntimeExtras.enabled(context, KEY))
                                                        return true;
                                                    return chain.proceed();
                                                })
                                ::unhook);
                module.deoptimize(policy);
                module.deoptimize(entry);
                hooks.commit();
                installed.add(base);
            }
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional automatic stretch suggestions unavailable", unavailable);
        }
    }

    private static boolean usable(List<?> heights) {
        if (heights.size() < 2) return false;
        boolean stretched = false;
        for (Object value : heights) {
            if (!(value instanceof Integer axis) || axis < 0 || axis > 1000) return false;
            stretched |= axis > 1;
        }
        return stretched;
    }
}
