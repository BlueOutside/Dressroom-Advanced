package dev.denver.dressroomadvanced;

import android.net.Uri;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves the editor's active wallpaper controller and {@code EditorData} source slot. */
final class WallpaperSourceBinding {
    record Source(Object data, String key, boolean image, boolean effectApplied, String externalUri) {
        Source(Object data, String key, boolean image) {
            this(data, key, image, false, "");
        }

        Source(Object data, String key, boolean image, boolean effectApplied) {
            this(data, key, image, effectApplied, "");
        }
    }

    private record Candidate(Field f0, Field f1, Field f2, Field f3, Method changed) {
        Class<?> controller() {
            return f2.getType();
        }
    }

    interface DexResolver {
        List<DexSignatures.Body> find(Set<String> anchors)
                throws ReflectiveOperationException, IOException;

        DexSignatures.Body body(Method method) throws ReflectiveOperationException, IOException;
    }

    private static final String RECREATE_ANCHOR = "need_to_recreate_preview";

    private final Field[] path;
    private final Method parentType;
    final Method changed;
    final Class<?> dataType;

    WallpaperSourceBinding(ClassLoader loader, boolean legacy) throws ReflectiveOperationException {
        this(loader, legacy, defaultDex(loader));
    }

    WallpaperSourceBinding(ClassLoader loader, boolean legacy, DexResolver dex)
            throws ReflectiveOperationException {
        dataType =
                Class.forName(
                        "com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData",
                        false,
                        loader);
        NativeContract.method(dataType, "getOriginalUri", Uri.class);
        NativeContract.method(dataType, "getUri", Uri.class);
        NativeContract.method(dataType, "getStorageFilePath", String.class);
        NativeContract.method(dataType, "getWallpaperSubType", int.class);
        Method parent;
        try {
            parent = NativeContract.method(dataType, "getWallpaperParentSubType", int.class);
        } catch (NoSuchMethodException missing) {
            if (!legacy) throw missing;
            parent = null;
        }
        parentType = parent;
        Class<?> activityType =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        Candidate resolved = resolveBinding(loader, activityType, dataType, legacy, dex);
        resolved.f0.setAccessible(true);
        resolved.f1.setAccessible(true);
        resolved.f2.setAccessible(true);
        resolved.f3.setAccessible(true);
        resolved.changed.setAccessible(true);
        path = new Field[] {resolved.f0, resolved.f1, resolved.f2, resolved.f3};
        changed = resolved.changed;
    }

    private static DexResolver defaultDex(ClassLoader loader) {
        return new DexResolver() {
            @Override
            public List<DexSignatures.Body> find(Set<String> anchors)
                    throws ReflectiveOperationException, IOException {
                return DexSignatures.find(loader, anchors);
            }

            @Override
            public DexSignatures.Body body(Method method)
                    throws ReflectiveOperationException, IOException {
                return DexSignatures.body(method);
            }
        };
    }

    private static Candidate resolveBinding(
            ClassLoader loader,
            Class<?> activityType,
            Class<?> dataType,
            boolean legacy,
            DexResolver dex)
            throws ReflectiveOperationException {
        try {
            return resolveKnown(loader, activityType, dataType, legacy, dex);
        } catch (ReflectiveOperationException fallback) {
            List<Candidate> candidates = discoverCandidates(activityType, dataType, dex);
            if (candidates.isEmpty()) throw fallback;
            return selectCandidate(candidates, legacy, dex);
        }
    }

    private static Candidate resolveKnown(
            ClassLoader loader,
            Class<?> activityType,
            Class<?> dataType,
            boolean legacy,
            DexResolver dex)
            throws ReflectiveOperationException {
        String[] types =
                legacy
                        ? new String[] {"P3.f", "X2.B", "X2.W"}
                        : new String[] {"p8.a", "o6.t", "o6.e0"};
        String[] names =
                legacy ? new String[] {"n", "v", "w", "Y"} : new String[] {"j", "p", "t", "T"};
        Field[] direct = new Field[4];
        Class<?> owner = activityType;
        for (int i = 0; i < direct.length; i++) {
            Class<?> next = i == 3 ? dataType : Class.forName(types[i], false, loader);
            direct[i] = NativeContract.findField(owner, names[i], next);
            owner = direct[i].getType();
        }
        Method onChanged =
                resolveChangedMethod(
                        dex,
                        direct[2].getType(),
                        direct[3],
                        dataType,
                        legacy ? "b3" : "c1");
        return new Candidate(direct[0], direct[1], direct[2], direct[3], onChanged);
    }

    private static List<Candidate> discoverCandidates(
            Class<?> activityType, Class<?> dataType, DexResolver dex) {
        List<Candidate> found = new ArrayList<>();
        for (Field f0 : declaredFields(activityType)) {
            Class<?> t0 = f0.getType();
            if (!isHopCandidate(t0) || t0 == activityType) continue;
            for (Field f1 : declaredFields(t0)) {
                Class<?> t1 = f1.getType();
                if (!isHopCandidate(t1) || t1 == activityType || t1 == t0) continue;
                for (Field f2 : declaredFields(t1)) {
                    Class<?> t2 = f2.getType();
                    if (!isHopCandidate(t2) || t2 == activityType || t2 == t0 || t2 == t1) continue;
                    Field f3 = uniqueDataField(t2, dataType);
                    if (f3 == null) continue;
                    try {
                        Method onChanged = resolveChangedMethod(dex, t2, f3, dataType, null);
                        found.add(new Candidate(f0, f1, f2, f3, onChanged));
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            }
        }
        return found;
    }

    private static Candidate selectCandidate(
            List<Candidate> candidates, boolean legacy, DexResolver dex)
            throws ReflectiveOperationException {
        List<Candidate> baseTypes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean hasSuperCandidate = false;
            for (Candidate other : candidates) {
                if (other != candidate
                        && other.controller() != candidate.controller()
                        && other.controller().isAssignableFrom(candidate.controller())) {
                    hasSuperCandidate = true;
                    break;
                }
            }
            if (!hasSuperCandidate) baseTypes.add(candidate);
        }
        if (baseTypes.size() == 1) return baseTypes.get(0);

        Candidate byRecreate = pickUniqueByDexUsage(baseTypes, true, dex);
        if (byRecreate != null) return byRecreate;

        Candidate byUsage = pickUniqueByDexUsage(baseTypes, false, dex);
        if (byUsage != null) return byUsage;

        String preferred = legacy ? "w" : "t";
        Candidate named = null;
        for (Candidate candidate : baseTypes) {
            if (preferred.equals(candidate.f2.getName())) {
                if (named != null) {
                    named = null;
                    break;
                }
                named = candidate;
            }
        }
        if (named != null) return named;

        throw new NoSuchFieldException(
                "Ambiguous wallpaper source controller candidates: " + baseTypes.size());
    }

    private static Candidate pickUniqueByDexUsage(
            List<Candidate> candidates, boolean recreateOnly, DexResolver dex) {
        try {
            return DexSignatures.cached(
                    () -> {
                        Map<Class<?>, List<DexSignatures.Body>> holderBodies =
                                new LinkedHashMap<>();
                        if (recreateOnly) {
                            ClassLoader loader = candidates.get(0).f1.getType().getClassLoader();
                            List<DexSignatures.Body> anchors = dex.find(Set.of(RECREATE_ANCHOR));
                            for (DexSignatures.Body body : anchors) {
                                Class<?> owner;
                                try {
                                    owner = body.loadOwner(loader);
                                } catch (ClassNotFoundException | LinkageError ignored) {
                                    continue;
                                }
                                holderBodies
                                        .computeIfAbsent(owner, key -> new ArrayList<>())
                                        .add(body);
                            }
                        } else {
                            Set<Class<?>> holders = new LinkedHashSet<>();
                            for (Candidate candidate : candidates)
                                holders.add(candidate.f1.getType());
                            for (Class<?> holder : holders) {
                                List<DexSignatures.Body> list = new ArrayList<>();
                                for (Method method : declaredMethods(holder)) {
                                    if (method.isBridge()) continue;
                                    try {
                                        list.add(dex.body(method));
                                    } catch (ReflectiveOperationException | IOException ignored) {
                                    }
                                }
                                holderBodies.put(holder, list);
                            }
                        }

                        int bestScore = 0;
                        Candidate best = null;
                        boolean tied = false;
                        for (Candidate candidate : candidates) {
                            List<DexSignatures.Body> bodies =
                                    holderBodies.get(candidate.f1.getType());
                            if (bodies == null || bodies.isEmpty()) continue;
                            String ownerDesc =
                                    DexSignatures.descriptor(candidate.f2.getDeclaringClass());
                            String fieldName = candidate.f2.getName();
                            String fieldTypeDesc = DexSignatures.descriptor(candidate.f2.getType());
                            int score = 0;
                            for (DexSignatures.Body body : bodies) {
                                for (DexSignatures.Read read : body.reads()) {
                                    if (matchesMember(read.field(), ownerDesc, fieldName, fieldTypeDesc))
                                        score++;
                                }
                                for (DexSignatures.Write write : body.writes()) {
                                    if (matchesMember(
                                            write.field(), ownerDesc, fieldName, fieldTypeDesc))
                                        score++;
                                }
                            }
                            if (score > bestScore) {
                                bestScore = score;
                                best = candidate;
                                tied = false;
                            } else if (score == bestScore && score > 0) {
                                tied = true;
                            }
                        }
                        return !tied && bestScore > 0 ? best : null;
                    });
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean matchesMember(
            DexSignatures.Member member, String ownerDesc, String name, String typeDesc) {
        return member.owner().equals(ownerDesc)
                && member.name().equals(name)
                && member.type().equals(typeDesc);
    }

    private static Field uniqueDataField(Class<?> owner, Class<?> dataType) {
        Field found = null;
        for (Field field : declaredFields(owner)) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == dataType) {
                if (found != null) return null;
                found = field;
            }
        }
        return found;
    }

    private static Method resolveChangedMethod(
            DexResolver dex,
            Class<?> owner,
            Field dataField,
            Class<?> dataType,
            String preferredName)
            throws NoSuchMethodException {
        List<Method> matches = new ArrayList<>();
        for (Method method : declaredMethods(owner)) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.isBridge()
                    || method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == dataType) matches.add(method);
        }
        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty())
            throw new NoSuchMethodException(
                    owner.getName() + " has no void(" + dataType.getName() + ") method");

        String ownerDesc = DexSignatures.descriptor(dataField.getDeclaringClass());
        String fieldName = dataField.getName();
        String fieldTypeDesc = DexSignatures.descriptor(dataField.getType());
        try {
            Method writer =
                    DexSignatures.cached(
                            () -> {
                                Method matched = null;
                                for (Method method : matches) {
                                    DexSignatures.Body body;
                                    try {
                                        body = dex.body(method);
                                    } catch (ReflectiveOperationException | IOException ignored) {
                                        continue;
                                    }
                                    for (DexSignatures.Write write : body.writes()) {
                                        if (matchesMember(
                                                write.field(), ownerDesc, fieldName, fieldTypeDesc)) {
                                            if (matched != null) return null;
                                            matched = method;
                                            break;
                                        }
                                    }
                                }
                                return matched;
                            });
            if (writer != null) return writer;
        } catch (Exception ignored) {
        }

        if (preferredName != null) {
            Method named = null;
            for (Method method : matches) {
                if (method.getName().equals(preferredName)) {
                    if (named != null) {
                        named = null;
                        break;
                    }
                    named = method;
                }
            }
            if (named != null) return named;
        }
        throw new NoSuchMethodException(
                "Ambiguous source change methods on " + owner.getName() + ": " + matches.size());
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (LinkageError | TypeNotPresentException ignored) {
            return new Field[0];
        }
    }

    private static Method[] declaredMethods(Class<?> type) {
        try {
            return type.getDeclaredMethods();
        } catch (LinkageError | TypeNotPresentException ignored) {
            return new Method[0];
        }
    }

    private static boolean isHopCandidate(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum()) return false;
        String name = type.getName();
        return !name.startsWith("android.")
                && !name.startsWith("androidx.")
                && !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("kotlin.")
                && !name.startsWith("dalvik.");
    }

    boolean acceptsActivity(Object activity) {
        return activity != null && path[0].getDeclaringClass().isInstance(activity);
    }

    Source read(Object activity) throws ReflectiveOperationException {
        Object data = activity;
        for (Field field : path) {
            if (data == null) return null;
            data = field.get(data);
        }
        if (data == null) return null;
        int type = ((Number) Reflect.call(data, "getWallpaperSubType")).intValue();
        int parent = parentType == null ? type : ((Number) parentType.invoke(data)).intValue();
        boolean cover = Boolean.TRUE.equals(optionalCall(data, "getIsCover"));
        boolean imageType = DepthSourcePolicy.image(type, parent, cover);

        String key = nonEmpty(Reflect.call(data, "getOriginalUri"));
        if (key.isEmpty()) key = nonEmpty(Reflect.call(data, "getUri"));
        if (key.isEmpty()) key = nonEmpty(optionalCall(data, "getOriginalFilePath"));
        if (key.isEmpty()) key = nonEmpty(Reflect.call(data, "getStorageFilePath"));
        if (key.isEmpty()) key = nonEmpty(optionalCall(data, "getPath"));
        if (key.isEmpty()) key = nonEmpty(optionalCall(data, "getPreviewPath"));
        if (key.isEmpty()) key = nonEmpty(optionalCall(data, "getFileName"));
        if (key.isEmpty() && type == 402) {
            String weatherId = nonEmpty(optionalCall(data, "getWeatherId"));
            if (!weatherId.isEmpty()
                    && !"-1".equals(weatherId)
                    && !"null".equalsIgnoreCase(weatherId)) {
                key = "weather:" + weatherId;
            }
        }
        boolean effectApplied = Boolean.TRUE.equals(optionalCall(data, "isApplyEffect"));
        if (!effectApplied) {
            String filterInfo = nonEmpty(optionalCall(data, "getImageFilterInfo"));
            if (!filterInfo.isEmpty() && !"Original".equalsIgnoreCase(filterInfo)) {
                effectApplied = true;
            }
        }
        if (imageType && key.isEmpty()) return null;
        return new Source(data, key, imageType && !key.isEmpty(), effectApplied,
                imageType ? nonEmpty(optionalCall(data, "getExternalOriginalUri")) : "");
    }

    private static Object optionalCall(Object target, String name)
            throws ReflectiveOperationException {
        try {
            return Reflect.call(target, name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    static String nonEmpty(Object value) {
        if (value == null) return "";
        String text = value.toString();
        return text == null || text.isEmpty() ? "" : text;
    }
}
