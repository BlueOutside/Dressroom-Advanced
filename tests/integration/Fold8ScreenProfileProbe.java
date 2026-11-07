package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Synthetic profile experiment for AOD 8.9.57.1. All objects, including the modified
 * policy, belong to this separate process. Never calls a provider, DB or widget host.
 */
public final class Fold8ScreenProfileProbe {
    private static final String ENTITY = "com.samsung.android.app.clockpack.profile.entity.";
    private static final String DB = "com.samsung.android.app.clockpack.db.entity.";
    private static final String BASE = "example.one/Widget;701;1;1;0~0,0~0-example.two/Widget;702;1;1;1~0,1~0";
    private static final String INNER = "example.one/Widget;701;1;1;0~0,0~0-example.three/Widget;703;1;1;1~0,1~0";
    private static final String COVER = "example.two/Widget;702;1;1;0~0,0~0";
    private static ClassLoader loader;
    private static int checks;

    private static Class<?> type(String name) throws Exception {
        return Class.forName(name, true, loader);
    }

    private static Object get(Object owner, String name) throws Exception {
        return owner.getClass().getMethod(name).invoke(owner);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object choice(String name, String value) throws Exception {
        return Enum.valueOf((Class) type(DB + name), value);
    }

    private static Object profile(Object bundle, Object policy, String screen, String orientation)
            throws Exception {
        Object state = type(DB + "ProfileState")
                .getConstructor(type(DB + "ProfileScreen"), type(DB + "ProfileOrientation"))
                .newInstance(choice("ProfileScreen", screen), choice("ProfileOrientation", orientation));
        return type(ENTITY + "LockProfileV3")
                .getConstructor(String.class, type(DB + "ProfileState"),
                        type(ENTITY + "LockProfileBundle"), type("aod.dl5"))
                .newInstance("synthetic", state, bundle, policy);
    }

    private static Object edit(Object profile, String widgets, Integer align, Float scale)
            throws Exception {
        Class<?> function = type("kotlin.jvm.functions.Function1");
        Object action = Proxy.newProxyInstance(loader, new Class<?>[] {function}, (p, m, args) -> {
            if (!m.getName().equals("invoke")) throw new UnsupportedOperationException(m.getName());
            Object b = args[0];
            if (widgets != null)
                b.getClass().getMethod("setEnabledWidgetsIdList", String.class).invoke(b, widgets);
            if (align != null)
                b.getClass().getMethod("setWidgetAlignType", Integer.class).invoke(b, align);
            if (scale != null)
                b.getClass().getMethod("setClockScaleRate", Float.class).invoke(b, scale);
            return type("kotlin.Unit").getField("a").get(null);
        });
        return profile.getClass().getMethod("copyBundle", function).invoke(profile, action);
    }

    private static Object screenRecord(Object bundle, String screen) throws Exception {
        return ((Map<?, ?>) get(bundle, "getScreenRelevant")).get(choice("ProfileScreen", screen));
    }

    private static void equal(Object expected, Object actual, String message) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(message);
    }

    private static Set<?> lazySet(Object owner, Class<?> declaring, String name) throws Exception {
        Object lazy = declaring.getField(name).get(owner);
        return (Set<?>) get(lazy, "getValue");
    }

    private static void replaceLazySet(Object owner, Class<?> declaring, String name, Set<?> value)
            throws Exception {
        Object lazy = declaring.getField(name).get(owner);
        get(lazy, "getValue");
        Field stored = type("aod.rf8").getField("p");
        stored.set(lazy, value);
    }

    /** Experimental change to one policy object in this probe process only. */
    private static void separateWidgets(Object policy) throws Exception {
        Class<?> fold = type("aod.hw4"), base = type("aod.dl5");
        Set<Object> widgets = Set.of(type("aod.ij5").getField("o").get(null),
                type("aod.jj5").getField("o").get(null));
        Set<Object> screens = new LinkedHashSet<>(lazySet(policy, fold, "u"));
        Set<Object> scoped = new LinkedHashSet<>(lazySet(policy, fold, "v"));
        Set<Object> primary = new LinkedHashSet<>(lazySet(policy, base, "p"));
        screens.addAll(widgets);
        scoped.addAll(widgets);
        primary.removeAll(widgets);
        replaceLazySet(policy, fold, "u", screens);
        replaceLazySet(policy, fold, "v", scoped);
        replaceLazySet(policy, base, "p", primary);
    }

    private static Object roundTrip(Object bundle) throws Exception {
        Object serializer = type("com.samsung.android.app.clockpack.serializer.a")
                .getConstructor().newInstance();
        Object gson = serializer.getClass().getField("a").get(serializer);
        String json = (String) gson.getClass().getMethod("i", Object.class).invoke(gson, bundle);
        return serializer.getClass().getMethod("a", String.class, Class.class)
                .invoke(serializer, json, type(ENTITY + "LockProfileBundle"));
    }

    private static Set<Integer> widgetIds(Object bundle) throws Exception {
        Set<Integer> ids = new HashSet<>();
        Map<?, ?> records = (Map<?, ?>) get(bundle, "getScreenRelevant");
        for (Object record : records.values()) {
            String raw = (String) get(record, "getEnabledWidgetsIdList");
            if (raw == null || raw.isEmpty()) continue;
            for (String widget : raw.split("-")) ids.add(Integer.valueOf(widget.split(";")[1]));
        }
        return ids;
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("aod.apk");
        android.os.Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        android.content.Context system = (android.content.Context)
                threadClass.getMethod("getSystemContext").invoke(thread);
        // Samsung feature constants consult currentApplication. Supply a detached
        // framework Application; never construct AODApplication or run onCreate.
        android.app.Application application = new android.app.Application();
        java.lang.reflect.Method attach = android.content.ContextWrapper.class
                .getDeclaredMethod("attachBaseContext", android.content.Context.class);
        attach.setAccessible(true);
        attach.invoke(application, system);
        Field initial = threadClass.getDeclaredField("mInitialApplication");
        initial.setAccessible(true);
        initial.set(thread, application);
        loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Object policy = type("aod.hw4").getConstructor().newInstance();
        System.out.println("Fold screen identity=" + ScreenIdentity.current());
        Object bundle = type(ENTITY + "LockProfileV3Kt").getMethod("makeDefaultBundle").invoke(null);
        Object unseeded = edit(profile(bundle, policy, "Fold", "Portrait"), BASE, 0, null);

        // Seed both screens from the effective shared selection before enabling the policy.
        bundle = edit(profile(bundle, policy, "Bar", "Portrait"), BASE, 0, 1.1f);
        bundle = edit(profile(bundle, policy, "Fold", "Portrait"), BASE, 0, 1.7f);
        Object seeded = bundle;
        bundle = edit(profile(bundle, policy, "Fold", "Portrait"), INNER, 1, null);
        equal(INNER, get(profile(bundle, policy, "Bar", "Portrait"), "getEnabledWidgetsIdList"),
                "Stock policy must reproduce shared selection");
        equal(1, get(profile(bundle, policy, "Bar", "Portrait"), "getWidgetAlignType"),
                "Stock policy must reproduce shared alignment");
        equal(BASE, get(screenRecord(bundle, "Bar"), "getEnabledWidgetsIdList"),
                "Native save must retain the sibling record");

        separateWidgets(policy);
        OneUi9FoldProfiles adapter = new OneUi9FoldProfiles(loader);
        Object productionSeed = adapter.seed(unseeded, policy);
        equal(BASE, get(profile(productionSeed, policy, "Bar", "Portrait"), "getEnabledWidgetsIdList"),
                "Production migration must seed the dormant cover record");
        equal(BASE, get(profile(productionSeed, policy, "Fold", "Portrait"), "getEnabledWidgetsIdList"),
                "Production migration must preserve the effective inner selection");
        equal(false, BASE.equals(get(profile(unseeded, policy, "Bar", "Portrait"),
                        "getEnabledWidgetsIdList")),
                "An unseeded sibling must expose why migration is required");
        Set<?> primaryFields = lazySet(policy, type("aod.dl5"), "p");
        Set<String> primaryNames = new HashSet<>();
        for (Object field : primaryFields) primaryNames.add((String) get(field, "getName"));
        equal(Set.of("clockVisibility", "fromKnoxProfile", "isBlurEffectEnabled",
                        "shortcutsList", "shortcutsVisibility"), primaryNames,
                "Candidate policy must leave unrelated shared fields unchanged");
        for (String orientation : new String[] {"Portrait", "Landscape"}) {
            equal(BASE, get(profile(bundle, policy, "Bar", orientation), "getEnabledWidgetsIdList"),
                    "Cover selection must be isolated even with matching orientation");
            equal(INNER, get(profile(bundle, policy, "Fold", orientation), "getEnabledWidgetsIdList"),
                    "Inner selection must follow screen across orientation");
            equal(BASE, OneUi9FoldProfiles.screenValue(profile(bundle, policy, "Bar", orientation),
                            type("aod.ij5").getField("o").get(null)),
                    "Production read adapter must select cover widgets");
            equal(INNER, OneUi9FoldProfiles.screenValue(profile(bundle, policy, "Fold", orientation),
                            type("aod.ij5").getField("o").get(null)),
                    "Production read adapter must select inner widgets");
            equal(0, get(profile(bundle, policy, "Bar", orientation), "getWidgetAlignType"),
                    "Cover alignment must be isolated");
            equal(1, get(profile(bundle, policy, "Fold", orientation), "getWidgetAlignType"),
                    "Inner alignment must be isolated");
        }
        equal(Set.of(701, 702, 703), widgetIds(bundle), "Both screens' IDs must survive");

        Object changed = edit(profile(bundle, policy, "Bar", "Portrait"), COVER, 0, null);
        equal(INNER, get(profile(changed, policy, "Fold", "Portrait"), "getEnabledWidgetsIdList"),
                "Saving cover must retain inner selection");
        equal(COVER, get(profile(changed, policy, "Bar", "Portrait"), "getEnabledWidgetsIdList"),
                "Saving cover must update cover selection");
        equal(BASE, get(screenRecord(bundle, "Bar"), "getEnabledWidgetsIdList"),
                "A cancelled copy must not mutate the original bundle");
        equal(1.1f, get(profile(changed, policy, "Bar", "Portrait"), "getClockScaleRate"),
                "Widget edit must preserve cover clock settings");
        equal(1.7f, get(profile(changed, policy, "Fold", "Portrait"), "getClockScaleRate"),
                "Widget edit must preserve inner clock settings");
        equal(Set.of(701, 702, 703), widgetIds(changed),
                "Removing shared widget from cover must retain the inner ID");

        Object empty = edit(profile(changed, policy, "Fold", "Landscape"), "", 1, null);
        equal("", get(profile(empty, policy, "Fold", "Portrait"), "getEnabledWidgetsIdList"),
                "An intentionally empty screen must not fall back to the sibling");
        equal(COVER, get(profile(empty, policy, "Bar", "Portrait"), "getEnabledWidgetsIdList"),
                "Clearing inner widgets must preserve cover");

        Object restored = roundTrip(changed);
        equal(changed, restored, "Samsung serializer must retain independent records");
        equal(OneUi9FoldProfiles.fingerprint(changed), OneUi9FoldProfiles.fingerprint(restored),
                "Migration fingerprint must survive Samsung serialization");
        equal(COVER, get(profile(restored, policy, "Bar", "Portrait"), "getEnabledWidgetsIdList"),
                "Cover selection must survive serialization");
        equal(INNER, get(profile(restored, policy, "Fold", "Portrait"), "getEnabledWidgetsIdList"),
                "Inner selection must survive serialization");
        equal(Set.of(701, 702, 703), widgetIds(restored),
                "Samsung serialization must retain IDs needed by both screens");
        equal(BASE, get(screenRecord(seeded, "Fold"), "getEnabledWidgetsIdList"),
                "Migration seed must remain immutable through subsequent edits");
        System.out.println("PASS Fold8ScreenProfileProbe: " + checks + " checks; synthetic data only");
    }
}
