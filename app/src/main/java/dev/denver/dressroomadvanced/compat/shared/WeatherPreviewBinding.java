package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves DressRoom's weather preview controller without relying on unstable R8 names. */
final class WeatherPreviewBinding {
    static final String PREVIEW_TAG = "WeatherWallpaperPreview";
    static final String INIT_ANCHOR = "initPreview rect: ";
    static final String PLAY_ANCHOR = "playEffect playState:";

    record Hooks(Class<?> owner, Field crop, Field surface, Set<Method> methods) {}

    private WeatherPreviewBinding() {}

    static Hooks resolve(
            ClassLoader loader, Class<?> cropType, Class<?> surfaceType, boolean legacy)
            throws ReflectiveOperationException {
        return resolve(loader, cropType, surfaceType, legacy, null);
    }

    static Hooks resolve(
            ClassLoader loader,
            Class<?> cropType,
            Class<?> surfaceType,
            boolean legacy,
            List<DexSignatures.Body> preloadedBodies)
            throws ReflectiveOperationException {
        Hooks dexResolved =
                resolveFromDex(loader, cropType, surfaceType, preloadedBodies);
        if (dexResolved != null) return dexResolved;

        Class<?> weather =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasField(type, cropType)
                                        && NativeContract.hasField(type, surfaceType),
                        legacy ? "l3.h" : "i7.g",
                        legacy ? "i7.g" : "l3.h");
        Field cropField = NativeContract.findField(weather, null, cropType);
        Field surfaceField = NativeContract.findField(weather, null, surfaceType);
        Set<Method> methods = new LinkedHashSet<>();
        for (Method method : weather.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge()) continue;
            if (method.getReturnType() != void.class) continue;
            if (method.getName().equals(legacy ? "u" : "e")
                    && method.getParameterCount() == (legacy ? 1 : 0)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        if (methods.isEmpty())
            throw new NoSuchMethodException(
                    "Weather preview lifecycle method missing on " + weather.getName());
        return new Hooks(weather, cropField, surfaceField, Collections.unmodifiableSet(methods));
    }

    private static Hooks resolveFromDex(
            ClassLoader loader,
            Class<?> cropType,
            Class<?> surfaceType,
            List<DexSignatures.Body> preloadedBodies) {
        try {
            if (preloadedBodies != null) {
                return resolveFromBodies(loader, cropType, surfaceType, preloadedBodies);
            }
            return DexSignatures.cached(
                    () -> {
                        List<DexSignatures.Body> bodies =
                                DexSignatures.find(
                                        loader, Set.of(PREVIEW_TAG, INIT_ANCHOR, PLAY_ANCHOR));
                        return resolveFromBodies(loader, cropType, surfaceType, bodies);
                    });
        } catch (IOException | ReflectiveOperationException | RuntimeException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Hooks resolveFromBodies(
            ClassLoader loader,
            Class<?> cropType,
            Class<?> surfaceType,
            List<DexSignatures.Body> bodies) {
        Map<Class<?>, Set<Method>> byOwner = new LinkedHashMap<>();
        Map<Class<?>, Field[]> fieldsByOwner = new LinkedHashMap<>();
        for (DexSignatures.Body body : bodies) {
            if ((body.access() & Modifier.STATIC) != 0
                    || !"V".equals(body.result())
                    || !body.anchors().containsKey(PREVIEW_TAG)) {
                continue;
            }
            boolean initHook =
                    body.anchors().containsKey(INIT_ANCHOR) && body.parameters().size() == 1;
            boolean playHook =
                    body.anchors().containsKey(PLAY_ANCHOR) && body.parameters().isEmpty();
            if (!initHook && !playHook) continue;
            Class<?> owner;
            try {
                owner = body.loadOwner(loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                continue;
            }
            Field[] pair = fieldsByOwner.get(owner);
            if (pair == null) {
                try {
                    pair =
                            new Field[] {
                                NativeContract.findField(owner, null, cropType),
                                NativeContract.findField(owner, null, surfaceType)
                            };
                    fieldsByOwner.put(owner, pair);
                } catch (NoSuchFieldException ignored) {
                    continue;
                }
            }
            try {
                byOwner.computeIfAbsent(owner, key -> new LinkedHashSet<>())
                        .add(body.method(loader));
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        if (byOwner.size() != 1) return null;
        Map.Entry<Class<?>, Set<Method>> entry = byOwner.entrySet().iterator().next();
        if (entry.getValue().isEmpty()) return null;
        Field[] pair = fieldsByOwner.get(entry.getKey());
        return new Hooks(
                entry.getKey(), pair[0], pair[1], Collections.unmodifiableSet(entry.getValue()));
    }
}
