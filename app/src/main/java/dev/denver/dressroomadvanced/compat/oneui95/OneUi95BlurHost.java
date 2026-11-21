package dev.denver.dressroomadvanced;

import android.view.View;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

record OneUi95BlurHost(Field strategy, Field holder, Field viewReference) {
    static OneUi95BlurHost resolve(ClockBlurRetryBinding binding)
            throws ReflectiveOperationException, IOException {
        Class<?> type = binding.strategy.getType();
        ClassLoader loader = type.getClassLoader();
        Method clear = ClockBlurBinding.resolveClear(type);
        for (var call : DexSignatures.body(clear).calls()) {
            if (call.isStatic() || !call.parameters().isEmpty() || !call.result().equals("V")
                    || !call.owner().startsWith("L") || call.owner().startsWith("Landroid/")
                    || call.owner().equals(DexSignatures.descriptor(type))) continue;
            var body = DexSignatures.body(call.method(loader));
            if (body.calls().stream().noneMatch(nested -> nested.name().equals("semSetBlurInfo")
                    && nested.owner().equals("Landroid/view/View;")
                    && nested.parameters().equals(List.of("Landroid/view/SemBlurInfo;")))) continue;
            Field holder = DexSignatures.body(clear).uniqueRead(DexSignatures.descriptor(type),
                    call.owner(), null).field(loader);
            Field reference = body.uniqueWrite(call.owner(),
                    "Ljava/lang/ref/WeakReference;", null).field(loader);
            return new OneUi95BlurHost(binding.strategy, holder, reference);
        }
        throw new NoSuchFieldException("Clock blur host lease");
    }

    void release(Object manager) throws IllegalAccessException {
        Object strategyOwner = strategy.get(manager);
        Object lease = strategyOwner == null ? null : holder.get(strategyOwner);
        if (lease != null && viewReference.get(lease) instanceof WeakReference<?> ref
                && ref.get() instanceof View view && "lock_clock_blur".equals(view.getTag()))
            viewReference.set(lease, null);
    }
}
