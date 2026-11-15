package dev.denver.dressroomadvanced;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Complete native profile transaction contract, resolved before any migration hooks install. */
final class FoldProfileBinding {
    private static final String ENTITY = "com.samsung.android.app.clockpack.profile.entity.";
    private static final String MAPPING_SQL =
            "SELECT * from lock_profile_ids where ids LIKE '%|' || ? || '|%'";
    private static final String MANAGER_CLASS =
            "com.samsung.android.app.aodservice.common.data.AODDataManager";

    final Class<?> mediator, policy, property;
    final Constructor<?> profile, query;
    final Method load, save, getPolicy, active, getDao, queryMapping, read, delete, global, contextValue;
    final Field contextProvider, database, deleteContext;
    final int queryCase;

    FoldProfileBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> bundle = Class.forName(ENTITY + "LockProfileBundle", false, loader);
        Class<?> nativeProfile = Class.forName(ENTITY + "LockProfileV3", false, loader);
        Class<?> state = Class.forName("com.samsung.android.app.clockpack.db.entity.ProfileState", false, loader);
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
        profile = profileConstructor(nativeProfile, state, bundle);
        policy = profile.getParameterTypes()[3];
        mediator = NativeContract.findClass(loader,
                cls -> NativeContract.hasMethod(cls, bundle, String.class)
                        && NativeContract.hasMethod(cls, void.class, String.class, bundle)
                        && NativeContract.hasMethod(cls, policy),
                "com.samsung.android.app.aodservice.datamanager.data.item.a");
        load = NativeContract.findMethod(mediator, null, bundle, String.class);
        save = NativeContract.findMethod(mediator, null, void.class, String.class, bundle);
        getPolicy = NativeContract.findMethod(mediator, null, policy);
        active = NativeContract.findMethod(mediator, null, boolean.class);
        Method genericGet = NativeContract.findMethod(nativeProfile, "get", Object.class, (Class<?>) null);
        property = genericGet.getParameterTypes()[0];
        read = NativeContract.findMethod(policy, null, Object.class, nativeProfile, property);

        Class<?> provider = Class.forName("javax.inject.Provider", false, loader);
        contextValue = provider.getMethod("get");
        Class<?> mapping = Class.forName("com.samsung.android.app.clockpack.db.entity.LockProfileMappingIdEntity", false, loader);
        NativeContract.method(mapping, "getId", Long.class);
        Class<?> manager = Class.forName(MANAGER_CLASS, false, loader);

        try {
            String mediatorDesc = DexSignatures.descriptor(mediator);
            contextProvider = resolveContextProvider(loader, mediatorDesc, provider, active);
            DexSignatures.Body loadBody = DexSignatures.body(load);
            getDao = resolveDaoGetter(loader, mediatorDesc, loadBody);
            Class<?> daoType = getDao.getReturnType();
            database = loadBody.uniqueRead(
                    DexSignatures.descriptor(daoType), "Ljava/lang/Object;", null).field(loader);
            queryMapping = resolveQueryMapping(loader, loadBody);

            Method mappingInvoke = resolveMappingInvoke(loader, function);
            query = mappingInvoke.getDeclaringClass().getConstructor(int.class, long.class);
            queryCase = DexSignatures.switchCase(mappingInvoke, MAPPING_SQL);

            delete = resolveDeletionMethod(loader);
            deleteContext = NativeContract.findField(delete.getDeclaringClass(), null, Context.class);
            global = resolveGlobalUpdateMethod(loader, manager);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Could not inspect profile DEX contracts", invalidDex);
        }
    }

    private static Constructor<?> profileConstructor(
            Class<?> nativeProfile, Class<?> state, Class<?> bundle) throws NoSuchMethodException {
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : nativeProfile.getDeclaredConstructors()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 4 && params[0] == String.class && params[1] == state && params[2] == bundle) {
                if (constructor != null) throw new NoSuchMethodException("Ambiguous profile constructors");
                constructor = candidate;
            }
        }
        if (constructor == null) throw new NoSuchMethodException("Profile constructor unavailable");
        return constructor;
    }

    private static Field resolveContextProvider(
            ClassLoader loader, String mediatorDesc, Class<?> provider, Method activeMethod)
            throws ReflectiveOperationException, IOException {
        Field found = null;
        for (DexSignatures.Read read : DexSignatures.body(activeMethod).reads()) {
            if (!read.field().owner().equals(mediatorDesc) || read.field().type().equals("Z")) continue;
            Field candidate = read.field().field(loader);
            if (!provider.isAssignableFrom(candidate.getType())) continue;
            if (found != null && !found.equals(candidate))
                throw new NoSuchFieldException("Ambiguous context provider reads in " + activeMethod.getName());
            found = candidate;
        }
        if (found == null) throw new NoSuchFieldException("Context provider not read in " + activeMethod.getName());
        return found;
    }

    private static Method resolveDaoGetter(
            ClassLoader loader, String mediatorDesc, DexSignatures.Body loadBody)
            throws ReflectiveOperationException {
        Method found = null;
        for (DexSignatures.Call call : loadBody.calls()) {
            if (call.isStatic()
                    || !call.owner().equals(mediatorDesc)
                    || !call.parameters().isEmpty()
                    || call.result().equals("Z")
                    || call.result().equals("V")
                    || call.result().equals("Ljava/lang/String;")) continue;
            Method candidate = call.method(loader);
            if (found != null && !found.equals(candidate))
                throw new NoSuchMethodException("Ambiguous profile DAO getter in " + loadBody.name());
            found = candidate;
        }
        if (found == null) throw new NoSuchMethodException("Profile DAO getter not found in " + loadBody.name());
        return found;
    }

    private static Method resolveQueryMapping(ClassLoader loader, DexSignatures.Body loadBody)
            throws ReflectiveOperationException {
        Method found = null;
        for (DexSignatures.Call call : loadBody.calls()) {
            List<String> params = call.parameters();
            if (!call.isStatic()
                    || !call.result().equals("Ljava/lang/Object;")
                    || params.size() != 4
                    || !params.get(1).equals("Z")
                    || !params.get(2).equals("Z")
                    || !params.get(3).equals("Lkotlin/jvm/functions/Function1;")) continue;
            Method candidate = call.method(loader);
            if (found != null && !found.equals(candidate))
                throw new NoSuchMethodException("Ambiguous profile DB query runner in " + loadBody.name());
            found = candidate;
        }
        if (found == null) throw new NoSuchMethodException("Profile DB query runner not found in " + loadBody.name());
        return found;
    }

    private static Method resolveMappingInvoke(ClassLoader loader, Class<?> function)
            throws ReflectiveOperationException, IOException {
        Method found = null;
        for (DexSignatures.Body body : DexSignatures.find(loader, Set.of(MAPPING_SQL))) {
            if (Modifier.isStatic(body.access())
                    || !body.result().equals("Ljava/lang/Object;")
                    || !body.parameters().equals(List.of("Ljava/lang/Object;"))) continue;
            Method candidate = body.method(loader);
            Class<?> owner = candidate.getDeclaringClass();
            if (!function.isAssignableFrom(owner) || !NativeContract.hasConstructor(owner, int.class, long.class))
                continue;
            if (found != null && !found.equals(candidate))
                throw new NativeContract.AmbiguousClassException(
                        "Ambiguous mapping query classes: " + found.getDeclaringClass().getName()
                                + " and " + owner.getName());
            found = candidate;
        }
        if (found == null) throw new ClassNotFoundException("Mapping query contract unavailable");
        return found;
    }

    private static Method resolveDeletionMethod(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        Method found = null;
        Set<String> anchors = Set.of("ComplicationWidgetDataProvider", " clearWidgetIdInfo  ");
        for (DexSignatures.Body body : DexSignatures.find(loader, anchors)) {
            if (body.anchors().size() != anchors.size()
                    || Modifier.isStatic(body.access())
                    || !body.result().equals("V")
                    || !body.parameters().equals(List.of("Ljava/util/ArrayList;"))) continue;
            Method candidate = body.method(loader);
            Class<?> owner = candidate.getDeclaringClass();
            if (!NativeContract.hasField(owner, Context.class)
                    || !NativeContract.hasField(owner, ConcurrentHashMap.class)) continue;
            if (found != null && !found.equals(candidate))
                throw new NoSuchMethodException("Ambiguous complication widget cleanup methods");
            found = candidate;
        }
        if (found == null) throw new ClassNotFoundException("Complication widget cleanup contract unavailable");
        return found;
    }

    private static Method resolveGlobalUpdateMethod(ClassLoader loader, Class<?> manager)
            throws ReflectiveOperationException, IOException {
        String managerDesc = DexSignatures.descriptor(manager);
        Set<String> anchors = Set.of("Not found required update widget data", "updateEnableWidgetIdList: ");
        Method found = null;
        for (DexSignatures.Body body : DexSignatures.find(loader, anchors)) {
            if (body.anchors().size() != anchors.size()
                    || !body.owner().equals(managerDesc)
                    || Modifier.isStatic(body.access())
                    || !body.result().equals("V")
                    || !body.parameters().equals(List.of("Ljava/lang/String;"))) continue;
            Method candidate = body.method(loader);
            if (found != null && !found.equals(candidate))
                throw new NoSuchMethodException("Ambiguous global widget update methods");
            found = candidate;
        }
        if (found == null) throw new NoSuchMethodException("Global widget update method unavailable");
        return found;
    }
}
