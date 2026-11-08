package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class ObjectCaptureLifecycle {
    private static final String SDK = "com.samsung.android.app.sdk.deepsky.objectcapture.";
    private static final Set<Class<?>> installed = Collections.newSetFromMap(new WeakHashMap<>());

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        for (String backend : new String[] {"ArcSoftObjectCaptureImpl", "SribObjectCaptureImpl"}) {
            try {
                Class<?> type = Class.forName(SDK + "impl." + backend, false, loader);
                if (installed.contains(type)) continue;
                var lockField = type.getDeclaredField("CLASS_LOCK");
                if (lockField.getType() != Object.class
                        || !java.lang.reflect.Modifier.isStatic(lockField.getModifiers()))
                    throw new NoSuchFieldException("Object Capture static lock changed");
                lockField.setAccessible(true);
                Object lock = lockField.get(null);
                ArcSoftEngineBinding arc =
                        backend.equals("ArcSoftObjectCaptureImpl")
                                ? new ArcSoftEngineBinding(type)
                                : null;
                CaptureInstanceState<Object> instance =
                        arc == null ? null : new CaptureInstanceState<>(arc.ready, arc::handle);
                Method init = NativeContract.method(type, "init", void.class);
                Method release = NativeContract.method(type, "release", void.class);
                Method capture =
                        NativeContract.method(
                                type,
                                "capture",
                                Class.forName(SDK + "base.ObjectResult", false, loader),
                                Bitmap.class);
                Set<Object> owners = Collections.newSetFromMap(new WeakHashMap<>());
                try (HookTransaction hooks = new HookTransaction()) {
                    var opened =
                            module.hook(init)
                                    .setId("dressroom-advanced.subject-engine-open")
                                    .intercept(
                                            chain -> {
                                                synchronized (lock) {
                                                    Object owner = chain.getThisObject();
                                                    if (instance != null) instance.prepare(owner);
                                                    try {
                                                        Object result = chain.proceed();
                                                        if (instance != null)
                                                            instance.opened(owner);
                                                        else owners.add(owner);
                                                        return result;
                                                    } finally {
                                                        if (instance != null) instance.restore();
                                                    }
                                                }
                                            });
                    hooks.add(opened::unhook);
                    var closed =
                            module.hook(release)
                                    .setId("dressroom-advanced.subject-engine-close")
                                    .intercept(
                                            chain -> {
                                                synchronized (lock) {
                                                    if (instance != null) {
                                                        Object owner = chain.getThisObject();
                                                        instance.prepare(owner);
                                                        try {
                                                            return chain.proceed();
                                                        } finally {
                                                            instance.closed(owner);
                                                            instance.restore();
                                                        }
                                                    }
                                                    owners.remove(chain.getThisObject());
                                                    // Samsung's initialized flag and native engine
                                                    // are shared by
                                                    // every instance. One scanner must not close
                                                    // another's engine.
                                                    if (!owners.isEmpty()) return null;
                                                    return chain.proceed();
                                                }
                                            });
                    hooks.add(closed::unhook);
                    var detecting =
                            module.hook(capture)
                                    .setId("dressroom-advanced.subject-engine-capture")
                                    .intercept(
                                            chain -> {
                                                synchronized (lock) {
                                                    // Handles a native client initialized before
                                                    // these hooks loaded,
                                                    // and closes the gap between the SDK's state
                                                    // check and its lock.
                                                    init.invoke(chain.getThisObject());
                                                    return chain.proceed();
                                                }
                                            });
                    hooks.add(detecting::unhook);
                    module.deoptimize(init);
                    module.deoptimize(release);
                    module.deoptimize(capture);
                    hooks.commit();
                    installed.add(type);
                }
                android.util.Log.i(
                        DressroomModule.TAG,
                        (arc == null ? "Shared" : "Per-instance")
                                + " Object Capture lifecycle: "
                                + backend);
            } catch (ReflectiveOperationException | LinkageError error) {
                module.report("Object Capture lifecycle binding unavailable: " + backend, error);
            }
        }
    }
}
