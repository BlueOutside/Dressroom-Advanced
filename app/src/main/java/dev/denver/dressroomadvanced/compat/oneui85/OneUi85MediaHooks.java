package dev.denver.dressroomadvanced;

import android.view.View;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class OneUi85MediaHooks {
    private static final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());

    private OneUi85MediaHooks() {}

    static synchronized void install(DressroomModule module, Class<?> base) throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        if (installed.contains(loader)) return;
        OneUi85MediaBinding binding = new OneUi85MediaBinding(base);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(module.hook(binding.expanded)
                    .setId("dressroom-advanced.85-media-depth")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        MediaDepthTransition.update((View) chain.getThisObject(),
                                Boolean.TRUE.equals(chain.getArg(0)), binding::animation);
                        return result;
                    })::unhook);
            module.deoptimize(binding.expanded);
            for (var caller : binding.callers) module.deoptimize(caller);
            hooks.commit();
            installed.add(loader);
        }
        android.util.Log.i(DressroomModule.TAG, "8.5 native media depth lifecycle installed");
    }
}
