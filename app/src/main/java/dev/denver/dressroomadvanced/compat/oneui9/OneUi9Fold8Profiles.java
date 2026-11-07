package dev.denver.dressroomadvanced;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native profile adapter for the inspected Fold8 AOD revision only. */
final class OneUi9Fold8Profiles {
    private static final String ENTITY = "com.samsung.android.app.clockpack.profile.entity.";
    private static final String DB = "com.samsung.android.app.clockpack.db.entity.";
    private static final String AOD = "com.samsung.android.app.aodservice";
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final Map<String, String> ready = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> reportedReads = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Boolean> raw = ThreadLocal.withInitial(() -> false);
    private final ClassLoader loader;
    private final Set<String> confirmed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    OneUi9Fold8Profiles(ClassLoader loader) { this.loader = loader; }

    private boolean confirmed(String id) {
        String key = profileId(id);
        if (ready.containsKey(key) || confirmed.contains(key)) return true;
        try {
            Context app = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app == null) return false;
            android.os.Bundle result = app.getContentResolver().call(SettingsProvider.URI, "readFoldProfile", key, null);
            if (result != null && result.getBoolean("ready")) { confirmed.add(key); return true; }
        } catch (Exception ignored) { }
        return false;
    }

    private void publish(Context context, String key) {
        android.os.Bundle result = context.getContentResolver().call(SettingsProvider.URI, "markFoldProfile", key, null);
        if (result == null || !result.getBoolean("ready"))
            throw new IllegalStateException("Migration confirmation unavailable");
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    static String profileId(String id) {
        if (id == null || id.isEmpty()) return "0";
        try { return Long.toString(Long.parseLong(id)); }
        catch (NumberFormatException ignored) { return "0"; }
    }

    private static Object get(Object owner, String method) throws ReflectiveOperationException {
        return Reflect.call(owner, method);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object choice(String name, String value) throws ReflectiveOperationException {
        return Enum.valueOf((Class) type(DB + name), value);
    }

    Object seed(Object bundle, Object policy) throws ReflectiveOperationException {
        Object primary = get(bundle, "getPrimary");
        String widgets = (String) get(primary, "getEnabledWidgetsIdList");
        Integer align = (Integer) get(primary, "getWidgetAlignType");
        Class<?> function = type("kotlin.jvm.functions.Function1");
        Object action = Proxy.newProxyInstance(loader, new Class<?>[] {function}, (p, m, args) -> {
            if (!m.getName().equals("invoke")) throw new UnsupportedOperationException(m.getName());
            args[0].getClass().getMethod("setEnabledWidgetsIdList", String.class)
                    .invoke(args[0], widgets);
            args[0].getClass().getMethod("setWidgetAlignType", Integer.class)
                    .invoke(args[0], align);
            return type("kotlin.Unit").getField("a").get(null);
        });
        for (String screen : new String[] {"Bar", "Fold"}) {
            Object state = type(DB + "ProfileState")
                    .getConstructor(type(DB + "ProfileScreen"), type(DB + "ProfileOrientation"))
                    .newInstance(choice("ProfileScreen", screen), choice("ProfileOrientation", "Portrait"));
            Object profile = type(ENTITY + "LockProfileV3")
                    .getConstructor(String.class, type(DB + "ProfileState"),
                            type(ENTITY + "LockProfileBundle"), type("aod.dl5"))
                    .newInstance("0", state, bundle, policy);
            bundle = profile.getClass().getMethod("copyBundle", function).invoke(profile, action);
        }
        return bundle;
    }

    static Object screenValue(Object profile, Object property) throws ReflectiveOperationException {
        String name = (String) get(property, "getName");
        if (!name.equals("enabledWidgetsIdList") && !name.equals("widgetAlignType")) return null;
        Object screen = get(get(profile, "getState"), "getScreen");
        Object record = ((Map<?, ?>) get(get(profile, "getBundle"), "getScreenRelevant")).get(screen);
        return record == null ? null : Reflect.call(property, "get", record);
    }

    static String fingerprint(Object bundle) throws ReflectiveOperationException {
        TreeMap<String, String> values = new TreeMap<>();
        add(values, "primary", get(bundle, "getPrimary"));
        for (var entry : ((Map<?, ?>) get(bundle, "getScreenRelevant")).entrySet())
            add(values, String.valueOf(entry.getKey()), entry.getValue());
        return DepthStorage.hash(values.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void add(Map<String, String> values, String scope, Object record)
            throws ReflectiveOperationException {
        // Length-prefix values so widget strings cannot collide through map punctuation.
        Object widgets = get(record, "getEnabledWidgetsIdList");
        String text = widgets == null ? "<null>" : (String) widgets;
        values.put(scope, (widgets == null ? -1 : text.length()) + ":" + text
                + ":" + get(record, "getWidgetAlignType"));
    }

    private static void journal(AtomicFile file, JSONObject data) throws Exception {
        FileOutputStream out = file.startWrite();
        try {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception error) { file.failWrite(out); throw error; }
    }

    static int recovery(JSONObject status, String current) {
        if (status.optBoolean("ready")) return 2;
        if (current.equals(status.optString("after", ""))) return 1;
        return status.has("before") && !current.equals(status.optString("before")) ? -1 : 0;
    }

    static ArrayList<Integer> unreferenced(ArrayList<?> requested, Set<Integer> protectedIds) {
        ArrayList<Integer> allowed = new ArrayList<>();
        for (Object value : requested) {
            int id = ((Number) value).intValue();
            if (!protectedIds.contains(id)) allowed.add(id);
        }
        return allowed;
    }

    synchronized Object migrate(Object mediator, String id, Object original) throws Exception {
        Context context = (Context) Reflect.field(Reflect.field(mediator, "o"), "a");
        if (!AOD.equals(context.getPackageName())
                || context.getPackageManager().getPackageInfo(AOD, 0).getLongVersionCode() != 895701000L)
            return original;
        if (!Boolean.TRUE.equals(get(mediator, "j"))) return original;
        String canonical = profileId(id);
        Object query = type("aod.ce0").getConstructor(int.class, long.class)
                .newInstance(4, Long.parseLong(canonical));
        Object mapping = type("aod.pe4").getMethod("v", type("aod.u86"), boolean.class,
                        boolean.class, type("kotlin.jvm.functions.Function1"))
                .invoke(null, Reflect.field(get(mediator, "h"), "o"), true, false, query);
        if (mapping == null) { ready.remove(canonical); return original; }
        String rowId = String.valueOf(get(mapping, "getId"));
        if (rowId.equals(ready.get(canonical))) return original;
        ready.remove(canonical);
        File directory = new File(context.getFilesDir(), "dressroom_advanced_fold_profiles_v1");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Profile journal unavailable");
        // Native profile storage is shared by the AOD/provider processes. Serialize migration
        // across both processes and reread under the lock, never from an earlier cached read.
        try (RandomAccessFile lock = new RandomAccessFile(new File(directory, "migration.lock"), "rw");
                FileLock held = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(directory, rowId + ".json"));
            JSONObject status;
            try { status = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)); }
            catch (java.io.FileNotFoundException missing) { status = new JSONObject(); }
            raw.set(true);
            try {
                Object current = Reflect.call(mediator, "f", id);
                String before = fingerprint(current);
                int recovery = recovery(status, before);
                if (recovery == 2) {
                    publish(context, canonical);
                    ready.put(canonical, rowId);
                    return current;
                }
                if (recovery == 1) {
                    status.put("ready", true);
                    journal(file, status);
                    publish(context, canonical);
                    ready.put(canonical, rowId);
                    return current;
                }
                if (recovery < 0)
                    throw new IllegalStateException("Native profile changed during interrupted migration");
                Object seeded = seed(current, get(mediator, "e"));
                String after = fingerprint(seeded);
                status.put("before", before).put("after", after).put("ready", false);
                journal(file, status);
                Reflect.call(mediator, "l", id, seeded);
                Object saved = Reflect.call(mediator, "f", id);
                if (!after.equals(fingerprint(saved)))
                    throw new IllegalStateException("Native screen seed was not persisted");
                status.put("ready", true);
                journal(file, status);
                publish(context, canonical);
                ready.put(canonical, rowId);
                android.util.Log.i(DressroomModule.TAG, "Fold screen widget migration verified");
                return saved;
            } finally { raw.set(false); }
        }
    }

    private static ArrayList<Integer> deletions(Context context, ArrayList<?> requested) {
        Set<Integer> protectedIds = new HashSet<>();
        Uri uri = Uri.parse("content://com.samsung.android.app.clockpack.provider/lock_settings/all_profiles_complication_enabled_list");
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("Widget references unavailable");
            String value = cursor.getString(cursor.getColumnIndexOrThrow("complication_all_profiles_enabled_widget_id_list"));
            JSONArray ids = new JSONArray(value == null || value.isEmpty() ? "[]" : value);
            for (int i = 0; i < ids.length(); i++) protectedIds.add(ids.getInt(i));
        } catch (Exception error) {
            android.util.Log.w(DressroomModule.TAG, "Deferring widget deletion until profile references are available");
            return new ArrayList<>();
        }
        return unreferenced(requested, protectedIds);
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (!ScreenIdentity.inspectedFold8() || installed.contains(loader)) return;
        try {
            OneUi9Fold8Profiles adapter = new OneUi9Fold8Profiles(loader);
            Class<?> mediator = adapter.type("com.samsung.android.app.aodservice.datamanager.data.item.a");
            Method load = NativeContract.method(mediator, "f", adapter.type(ENTITY + "LockProfileBundle"), String.class);
            Method read = NativeContract.method(adapter.type("aod.dl5"), "c", Object.class,
                    adapter.type(ENTITY + "LockProfileV3"), adapter.type("aod.lp3"));
            Method delete = NativeContract.method(adapter.type("aod.v01"), "e", void.class, ArrayList.class);
            try (HookTransaction hooks = new HookTransaction()) {
                Class<?> nativeProfile = adapter.type(ENTITY + "LockProfileV3");
                for (String getter : new String[] {"getEnabledWidgetsIdList", "getWidgetAlignType"}) {
                    Method method = nativeProfile.getDeclaredMethod(getter);
                    hooks.add(module.hook(method).setId("dressroom-advanced.fold-direct-" + getter).intercept(chain -> {
                        Object owner = chain.getThisObject();
                        boolean migrated = adapter.confirmed((String) get(owner, "getProfileId"));
                        Object screen = get(get(owner, "getState"), "getScreen");
                        String diagnostic = getter + ":" + screen + ":" + migrated;
                        if (adapter.reportedReads.add(diagnostic))
                            android.util.Log.i(DressroomModule.TAG, "Fold native read " + diagnostic);
                        if (!migrated) return chain.proceed();
                        Object record = ((Map<?, ?>) get(get(owner, "getBundle"), "getScreenRelevant")).get(screen);
                        Object value = record == null ? null : get(record, getter);
                        return value == null ? chain.proceed() : value;
                    })::unhook);
                    module.deoptimize(method);
                }
                hooks.add(module.hook(load).setId("dressroom-advanced.fold-profile-migration").intercept(chain -> {
                    Object result = chain.proceed();
                    if (adapter.raw.get() || result == null) return result;
                    try { return adapter.migrate(chain.getThisObject(), (String) chain.getArg(0), result); }
                    catch (Exception error) { module.report("Fold profile migration deferred; native shared policy retained", error); return result; }
                })::unhook);
                hooks.add(module.hook(read).setId("dressroom-advanced.fold-widget-selection").intercept(chain -> {
                    Object profile = chain.getArg(0);
                    if (!adapter.confirmed((String) get(profile, "getProfileId"))) return chain.proceed();
                    Object value = screenValue(profile, chain.getArg(1));
                    return value == null ? chain.proceed() : value;
                })::unhook);
                Method global = adapter.type("com.samsung.android.app.aodservice.common.data.AODDataManager")
                        .getDeclaredMethod("r", String.class);
                hooks.add(module.hook(global).setId("dressroom-advanced.fold-preserve-native-item").intercept(chain -> {
                    // Legacy global selection must not overwrite a display-scoped SDK item.
                    return adapter.confirmed("0") ? null : chain.proceed();
                })::unhook);
                module.deoptimize(global);
                hooks.add(module.hook(delete).setId("dressroom-advanced.preserve-sibling-widget-ids").intercept(chain -> {
                    Context context = (Context) Reflect.field(chain.getThisObject(), "c");
                    if (context.getPackageManager().getPackageInfo(AOD, 0).getLongVersionCode() != 895701000L)
                        return chain.proceed();
                    return chain.proceed(new Object[] {deletions(context, (ArrayList<?>) chain.getArg(0))});
                })::unhook);
                module.deoptimize(load);
                module.deoptimize(read);
                module.deoptimize(delete);
                Class<?> profile = adapter.type(ENTITY + "LockProfileV3");
                module.deoptimize(profile.getDeclaredMethod("getEnabledWidgetsIdList"));
                module.deoptimize(profile.getDeclaredMethod("getWidgetAlignType"));
                module.deoptimize(profile.getDeclaredMethod("get", adapter.type("aod.lp3")));
                hooks.commit();
            }
            installed.add(loader);
            android.util.Log.i(DressroomModule.TAG, "Fold8 native screen profiles installed");
        } catch (Exception | LinkageError error) { module.report("Fold8 screen profile adapter unavailable", error); }
    }
}
