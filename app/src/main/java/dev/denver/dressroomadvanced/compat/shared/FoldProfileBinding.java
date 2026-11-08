package dev.denver.dressroomadvanced;

import android.content.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Complete native profile transaction contract, resolved before any migration hooks install. */
final class FoldProfileBinding {
    private static final String ENTITY = "com.samsung.android.app.clockpack.profile.entity.";
    final Class<?> mediator, policy, property;
    final Constructor<?> profile, query;
    final Method load, save, getPolicy, active, getDao, queryMapping, read, delete, global, contextValue;
    final Field contextProvider, database, deleteContext;

    FoldProfileBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> bundle = Class.forName(ENTITY + "LockProfileBundle", false, loader);
        Class<?> nativeProfile = Class.forName(ENTITY + "LockProfileV3", false, loader);
        Class<?> state = Class.forName("com.samsung.android.app.clockpack.db.entity.ProfileState", false, loader);
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : nativeProfile.getDeclaredConstructors()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 4 && params[0] == String.class && params[1] == state && params[2] == bundle) {
                if (constructor != null) throw new NoSuchMethodException("Ambiguous profile constructors");
                constructor = candidate;
            }
        }
        if (constructor == null) throw new NoSuchMethodException("Profile constructor unavailable");
        profile = constructor;
        policy = profile.getParameterTypes()[3];
        mediator = NativeContract.findClass(loader,
                cls -> NativeContract.hasMethod(cls, bundle, String.class)
                        && NativeContract.hasMethod(cls, void.class, String.class, bundle)
                        && NativeContract.hasMethod(cls, policy),
                "com.samsung.android.app.aodservice.datamanager.data.item.a");
        load = NativeContract.findMethod(mediator, "f", bundle, String.class);
        save = NativeContract.findMethod(mediator, "l", void.class, String.class, bundle);
        getPolicy = NativeContract.findMethod(mediator, "e", policy);
        active = NativeContract.findMethod(mediator, "j", boolean.class);
        Method genericGet = NativeContract.findMethod(nativeProfile, "get", Object.class, (Class<?>) null);
        property = genericGet.getParameterTypes()[0];
        read = NativeContract.findMethod(policy, "c", Object.class, nativeProfile, property);
        // The context is supplied through the native Provider interface, not a captured Object field.
        Class<?> provider = Class.forName("javax.inject.Provider", false, loader);
        contextProvider = NativeContract.findField(mediator, "o", provider);
        contextValue = provider.getMethod("get");
        Class<?> daoType = NativeContract.findClass(loader,
                cls -> NativeContract.hasField(cls, Object.class)
                        && NativeContract.hasMethod(mediator, cls), "aod.qi5");
        getDao = NativeContract.findMethod(mediator, "h", daoType);
        database = NativeContract.field(daoType, "o", Object.class);
        Class<?> mapping = Class.forName("com.samsung.android.app.clockpack.db.entity.LockProfileMappingIdEntity", false, loader);
        Class<?> mappingQuery = NativeContract.findClass(loader,
                cls -> function.isAssignableFrom(cls) && NativeContract.hasConstructor(cls, int.class, long.class), "aod.ce0");
        query = mappingQuery.getConstructor(int.class, long.class);
        Class<?> queryOwner = NativeContract.findClass(loader,
                cls -> NativeContract.hasStaticMethod(cls, Object.class, null, boolean.class, boolean.class, function), "aod.pe4");
        queryMapping = NativeContract.findStaticMethod(queryOwner, "v", Object.class, null, boolean.class, boolean.class, function);
        NativeContract.method(mapping, "getId", Long.class);
        Class<?> deletionOwner = NativeContract.findClass(loader,
                cls -> NativeContract.hasField(cls, Context.class)
                        && NativeContract.hasField(cls, java.util.concurrent.ConcurrentHashMap.class)
                        && NativeContract.hasMethod(cls, void.class, ArrayList.class), "aod.v01");
        delete = NativeContract.findMethod(deletionOwner, "e", void.class, ArrayList.class);
        deleteContext = NativeContract.findField(deletionOwner, "c", Context.class);
        Class<?> manager = Class.forName("com.samsung.android.app.aodservice.common.data.AODDataManager", false, loader);
        // Many String setters have indistinguishable signatures. Keep this inspected semantic hint;
        // a renamed ambiguous manager disables this optional capability rather than guessing.
        global = NativeContract.findMethod(manager, "r", void.class, String.class);
    }
}
