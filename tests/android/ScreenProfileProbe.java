package dev.denver.dressroomadvanced;

import android.content.SharedPreferences;
import android.os.Bundle;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.json.JSONObject;

/** In-memory preference and migration-journal checks; never accesses live app state. */
public final class ScreenProfileProbe {
    private static int checks;

    private static void equal(Object expected, Object actual, String message) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(message);
    }

    private static SharedPreferences prefs(Map<String, Object> data) {
        return (SharedPreferences) Proxy.newProxyInstance(ScreenProfileProbe.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class}, (p, method, args) -> {
                    if (method.getName().startsWith("get")) return data.getOrDefault(args[0], args[1]);
                    if (method.getName().equals("edit")) {
                        Map<String, Object> staged = new HashMap<>();
                        return Proxy.newProxyInstance(ScreenProfileProbe.class.getClassLoader(),
                                new Class<?>[] {SharedPreferences.Editor.class}, (e, operation, values) -> {
                                    if (operation.getName().startsWith("put")) {
                                        staged.put((String) values[0], values[1]); return e;
                                    }
                                    if (operation.getName().equals("commit")) { data.putAll(staged); return true; }
                                    throw new AssertionError(operation.getName());
                                });
                    }
                    throw new AssertionError(method.getName());
                });
    }

    public static void main(String[] args) {
        try { run(); System.out.println("PASS ScreenProfileProbe: " + checks + " checks"); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }

    private static void run() throws Exception {
        String base = "wallpaper_77_main";
        Bundle main = new Bundle(), sub = new Bundle();
        main.putInt(LayoutOrientation.FIELD, 1); sub.putInt(LayoutOrientation.FIELD, 1);
        main.putInt(ScreenIdentity.FIELD, ScreenIdentity.MAIN);
        sub.putInt(ScreenIdentity.FIELD, ScreenIdentity.SUB);
        equal(false, LayoutOrientation.key(base, main).equals(LayoutOrientation.key(base, sub)),
                "Same-orientation screens must have different keys");
        Map<String, Object> data = new HashMap<>();
        String old = "{\"scale\":1.4,\"depthMaskRef\":\"inner-mask\",\"depthSourceMaskRef\":\"source\"}";
        data.put(base + "_portrait", old);
        SharedPreferences preferences = prefs(data);
        JSONObject inner = new JSONObject(LayoutOrientation.read(preferences, base, main));
        JSONObject cover = new JSONObject(LayoutOrientation.read(preferences, base, sub));
        equal("inner-mask", inner.getString("depthMaskRef"), "Inner migration must retain its baked mask");
        equal(false, cover.has("depthMaskRef"), "Cover must not reuse an inner baked mask");
        equal("source", cover.getString("depthSourceMaskRef"), "Cover must retain reusable source subject");
        equal(1.4, cover.getDouble("scale"), "Cover must inherit the current layout preference");
        equal(old, data.get(base + "_portrait"), "Legacy rollback record must remain unchanged");
        data.put(LayoutOrientation.key(base, main), "{\"scale\":1.8}");
        equal(1.4, new JSONObject(LayoutOrientation.read(preferences, base, sub)).getDouble("scale"),
                "Editing inner must not become a later cover seed");
        sub.putInt(LayoutOrientation.FIELD, 2);
        data.put(base + "_landscape", "{\"scale\":0.8}");
        equal(0.8, new JSONObject(LayoutOrientation.read(preferences, base, sub)).getDouble("scale"),
                "Screen and orientation dimensions must remain independent");
        Bundle frozen = WidgetPreferences.clean(main);
        equal(1, ScreenIdentity.value(frozen), "Cleaning widgets must retain original screen");
        equal("0", OneUi9Fold8Profiles.profileId(""), "Empty native profile is database profile zero");
        equal("0", OneUi9Fold8Profiles.profileId("000"), "Numeric profile aliases must canonicalize");
        equal("42", OneUi9Fold8Profiles.profileId("0042"), "Nonzero aliases must canonicalize");
        JSONObject pending = new JSONObject().put("before", "shared").put("after", "seeded");
        equal(0, OneUi9Fold8Profiles.recovery(new JSONObject(), "shared"), "Fresh migration must seed");
        equal(0, OneUi9Fold8Profiles.recovery(pending, "shared"), "Pre-write interruption must retry seeding");
        equal(1, OneUi9Fold8Profiles.recovery(pending, "seeded"), "Post-write interruption must finalize, not reseed");
        equal(-1, OneUi9Fold8Profiles.recovery(pending, "external-edit"), "Conflicting pending state must preserve external edits");
        pending.put("ready", true);
        equal(2, OneUi9Fold8Profiles.recovery(pending, "independent-layout"), "Completed migration must never reseed edits");
        ArrayList<Integer> requested = new ArrayList<>(List.of(701, 702, 703));
        equal(List.of(703), OneUi9Fold8Profiles.unreferenced(requested, Set.of(701, 702)),
                "Deletion must protect IDs still used by sibling profiles");
        equal(List.of(701, 702, 703), requested, "Deletion filter must not mutate native input");
        equal(List.of(), OneUi9Fold8Profiles.unreferenced(requested, Set.of(701, 702, 703)),
                "No referenced ID may be deleted");
        equal(requested, OneUi9Fold8Profiles.unreferenced(requested, Set.of()),
                "Unreferenced draft IDs must remain eligible for cleanup");
        Bundle coverScope = new Bundle(main);
        coverScope.putInt(ScreenIdentity.FIELD, ScreenIdentity.SUB);
        String oldBase = "wallpaper_90_main", newBase = "wallpaper_91_main";
        valuesForWallpaper(preferences, data, coverScope, oldBase, newBase);
    }

    private static void valuesForWallpaper(SharedPreferences preferences, Map<String, Object> data,
            Bundle scope, String oldBase, String newBase) throws Exception {
        String original = "{\"scale\":1.7,\"depthMaskRef\":\"cover-mask\"}";
        data.put(LayoutOrientation.key(oldBase, scope), original);
        equal(original, LayoutOrientation.readWallpaper(preferences, newBase, oldBase, scope),
                "Explicit wallpaper identity lost the existing cover layout");
        equal(original, data.get(LayoutOrientation.key(oldBase, scope)), "Wallpaper migration destroyed old key");
        equal(null, LayoutOrientation.readWallpaper(preferences, "wallpaper_92_main", oldBase, scope),
                "A later wallpaper change inherited a stale cover mask");
        equal(original, LayoutOrientation.readWallpaper(preferences, newBase, oldBase, scope),
                "Returning to the original wallpaper lost its own saved layout");
    }
}
