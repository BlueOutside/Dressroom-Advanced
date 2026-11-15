package dev.denver.dressroomadvanced;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

final class ObjectCaptureBackend {
    private static final String SDK = "com.samsung.android.app.sdk.deepsky.objectcapture.";
    private static volatile String unavailable;

    interface Detection<T> {
        T run(Object detector) throws Exception;
    }

    private ObjectCaptureBackend() {}

    static boolean available(ClassLoader loader) {
        if (unavailable != null) return false;
        boolean arcsoft = library(loader, "objectcapture_jni.arcsoft");
        boolean srib = library(loader, "dvs.camera.samsung");
        if (!arcsoft && !srib) return false;
        for (String name :
                new String[] {"impl.SribObjectCaptureImpl", "impl.ArcSoftObjectCaptureImpl"}) {
            if (name.contains("Srib") ? !srib : !arcsoft) continue;
            try {
                Class.forName(SDK + name, false, loader).getConstructor(Context.class);
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        try {
            Class.forName(SDK + "ObjectCaptureProvider", false, loader)
                    .getMethod("getInstance", Context.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    static String unavailableReason() {
        return unavailable == null
                ? "Depth is unavailable: Samsung's subject detection libraries are missing."
                : unavailable;
    }

    private static boolean library(ClassLoader loader, String name) {
        try {
            Object path =
                    loader.getClass().getMethod("findLibrary", String.class).invoke(loader, name);
            if (path instanceof String && !((String) path).isEmpty()) return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        String folder = android.os.Process.is64Bit() ? "lib64/" : "lib/";
        for (String root : new String[] {"/system/", "/system_ext/", "/product/", "/vendor/"})
            if (new java.io.File(root + folder + "lib" + name + ".so").isFile()) return true;
        return false;
    }

    static <T> T detect(Context context, ClassLoader loader, Detection<T> scan) throws Exception {
        if (!available(loader)) throw new IllegalStateException(unavailableReason());
        List<Callable<?>> factories = new ArrayList<>();
        factories.add(
                () ->
                        Class.forName(SDK + "ObjectCaptureProvider", false, loader)
                                .getMethod("getInstance", Context.class)
                                .invoke(null, context));
        for (String name :
                new String[] {"impl.SribObjectCaptureImpl", "impl.ArcSoftObjectCaptureImpl"}) {
            factories.add(
                    () ->
                            Class.forName(SDK + name, false, loader)
                                    .getConstructor(Context.class)
                                    .newInstance(context));
        }
        return choose(factories, new ArrayList<>(), ObjectCaptureBackend::tryInitArcSoft, detector -> {
            android.util.Log.i(
                    DressroomModule.TAG, "Scanning subject with " + detector.getClass().getSimpleName());
            return scan.run(detector);
        }, false);
    }

    private static boolean missingNativeCode(Throwable error, java.util.Set<Throwable> visited) {
        if (error == null || !visited.add(error)) return false;
        if (error instanceof UnsatisfiedLinkError) return true;
        if (missingNativeCode(error.getCause(), visited)) return true;
        for (Throwable suppressed : error.getSuppressed())
            if (missingNativeCode(suppressed, visited)) return true;
        return false;
    }

    static Object firstUsable(Iterable<? extends Callable<?>> factories, List<Object> failed)
            throws Exception {
        return firstUsable(factories, failed, detector -> false);
    }

    static Object firstUsable(
            Iterable<? extends Callable<?>> factories,
            List<Object> failed,
            java.util.function.Predicate<Object> recover)
            throws Exception {
        return choose(factories, failed, recover, detector -> detector, true);
    }

    static <T> T choose(
            Iterable<? extends Callable<?>> factories,
            List<Object> failed,
            java.util.function.Predicate<Object> recover,
            Detection<T> scan,
            boolean keepSelected)
            throws Exception {
        IllegalStateException failure =
                new IllegalStateException("No Samsung subject detector initialized");
        java.util.Set<Class<?>> attempted = new java.util.HashSet<>();
        List<String> reasons = new ArrayList<>();
        boolean initialized = false;
        boolean empty = false;
        Throwable scanFailure = null;
        for (Callable<?> factory : factories) {
            Object detector = null;
            boolean owned = false, keep = false;
            try {
                detector = factory.call();
                if (detector == null
                        || detector.getClass().getSimpleName().equals("StubObjectCaptureImpl")
                        || !attempted.add(detector.getClass())) continue;
                owned = true;
                // Support-report APIs contain model/SoC policy gates. Native init is the
                // authority here; do not fake successful initialization or touch its state.
                try {
                    Reflect.call(detector, "init");
                } catch (Exception | LinkageError initError) {
                    if (!recover.test(detector)) throw initError;
                }
                initialized = true;
                try {
                    T result = scan.run(detector);
                    if (result != null) {
                        keep = keepSelected;
                        return result;
                    }
                    empty = true;
                } catch (Exception | LinkageError error) {
                    if (error instanceof InterruptedException cancelled) throw cancelled;
                    scanFailure = error;
                }
            } catch (Exception | LinkageError error) {
                if (error instanceof InterruptedException cancelled) throw cancelled;
                Throwable cause = error;
                while (cause instanceof ReflectiveOperationException && cause.getCause() != null)
                    cause = cause.getCause();
                String backend =
                        detector == null
                                ? "SDK factory"
                                : detector.getClass()
                                        .getSimpleName()
                                        .replace("ObjectCaptureImpl", "");
                String detail = cause.getMessage();
                if (detail == null || detail.isBlank()) detail = cause.getClass().getSimpleName();
                reasons.add(backend + ": " + detail);
                failure.addSuppressed(cause);
                if (owned) failed.add(detector);
            } finally {
                if (owned && !keep) {
                    try {
                        Reflect.call(detector, "release");
                    } catch (Exception | LinkageError cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
        }
        if (empty) return null;
        if (initialized) {
            if (scanFailure instanceof Exception error) throw error;
            if (scanFailure instanceof LinkageError error) throw error;
            return null;
        }
        if (!keepSelected && missingNativeCode(
                failure, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())))
            unavailable =
                    "Depth is unavailable: Samsung's subject detection libraries could not be loaded.";
        if (!reasons.isEmpty())
            throw new IllegalStateException(
                    failure.getMessage() + ". " + String.join("; ", reasons), failure);
        throw failure;
    }

    private static boolean tryInitArcSoft(Object detector) {
        if (detector == null || !detector.getClass().getName().contains("ArcSoft")) return false;
        try {
            Object wrapper = null;
            try {
                wrapper = Reflect.call(detector, "getInstance");
            } catch (Exception ignored) {
                try {
                    Object delegate = Reflect.field(detector, "instance$delegate");
                    if (delegate != null) wrapper = Reflect.call(delegate, "getValue");
                } catch (Exception ignored2) {
                }
            }
            if (wrapper == null) return false;
            Object jniInstance = Reflect.field(wrapper, "instance");
            if (jniInstance == null) return false;
            Object lock;
            try {
                java.lang.reflect.Field lockField =
                        detector.getClass().getDeclaredField("CLASS_LOCK");
                lockField.setAccessible(true);
                lock = lockField.get(null);
            } catch (NoSuchFieldException missingOnOlderSdk) {
                lock = detector.getClass();
            }
            synchronized (lock) {
                int[] platformIds = {5, 0, 4, 1, 2, 6};
                for (int id : platformIds) {
                    try {
                        Object ret = Reflect.call(jniInstance, "OBJECT_CAPTURE_Init", id);
                        if (ret instanceof Number n && n.intValue() == 0) {
                            java.lang.reflect.Field initStateField =
                                    detector.getClass().getDeclaredField("IS_INIT_STATE");
                            initStateField.setAccessible(true);
                            java.util.concurrent.atomic.AtomicBoolean initState =
                                    (java.util.concurrent.atomic.AtomicBoolean)
                                            initStateField.get(null);
                            if (initState != null) initState.set(true);
                            android.util.Log.i(
                                    DressroomModule.TAG,
                                    "ArcSoft object capture initialized with fallback platformId="
                                            + id);
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception | LinkageError error) {
            android.util.Log.w(
                    DressroomModule.TAG, "ArcSoft fallback initialization failed", error);
        }
        return false;
    }
}
