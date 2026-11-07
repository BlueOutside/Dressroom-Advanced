package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Detached Extras/migration tests plus read-only wallpaper identity checks. */
public final class FoldParityProbe {
    private static int checks;
    private static void equal(Object wanted, Object actual, String message) {
        checks++;
        if (!Objects.equals(wanted, actual)) throw new AssertionError(message);
    }
    private static Bundle scope(int screen) {
        Bundle b = ExtrasPreferences.clean(null);
        b.putInt(ScreenIdentity.FIELD, screen);
        return b;
    }
    private static SharedPreferences prefs(Map<String, Object> data) {
        return (SharedPreferences) Proxy.newProxyInstance(FoldParityProbe.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class}, (p, m, a) -> {
                    if (m.getName().equals("contains")) return data.containsKey(a[0]);
                    if (m.getName().startsWith("get")) return data.getOrDefault(a[0], a[1]);
                    if (m.getName().equals("edit")) {
                        Map<String, Object> staged = new HashMap<>();
                        return Proxy.newProxyInstance(FoldParityProbe.class.getClassLoader(),
                                new Class<?>[] {SharedPreferences.Editor.class}, (e, op, values) -> {
                                    if (op.getName().startsWith("put")) { staged.put((String) values[0], values[1]); return e; }
                                    if (op.getName().equals("commit")) { data.putAll(staged); return true; }
                                    throw new AssertionError(op.getName());
                                });
                    }
                    throw new AssertionError(m.getName());
                });
    }
    public static void main(String[] args) {
        try { run(); System.out.println("PASS FoldParityProbe: " + checks + " checks"); System.exit(0); }
        catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    @SuppressWarnings("unchecked")
    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory).invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
        equal(6, ScreenIdentity.wallpaperWhich(1, true), "Main lock wallpaper selector");
        equal(18, ScreenIdentity.wallpaperWhich(2, true), "Cover lock wallpaper selector");
        equal(5, ScreenIdentity.wallpaperWhich(1, false), "Main system fallback selector");
        equal(17, ScreenIdentity.wallpaperWhich(2, false), "Cover system fallback selector");
        equal(2, ScreenIdentity.wallpaperWhich(0, true), "Non-fold selector changed");
        int mainId = SettingsBridge.wallpaperId(system, 1), subId = SettingsBridge.wallpaperId(system, 2);
        equal(true, mainId >= 0, "Main wallpaper ID unavailable");
        equal(true, subId >= 0, "Cover wallpaper ID unavailable");
        System.out.println("Wallpaper IDs: main=" + mainId + ", sub=" + subId);
        Bundle main = scope(1), sub = scope(2);
        Map<String, Object> data = new HashMap<>();
        String legacy = "{\"keepCharging\":true,\"fullscreenArt\":true,\"stackCards\":5}";
        data.put("options", legacy);
        SharedPreferences prefs = prefs(data);
        Bundle inherited = ExtrasPreferences.store(prefs, "readExtras", sub);
        equal(true, inherited.getBoolean("keepCharging"), "Cover lost inherited charging setting");
        equal(2, ScreenIdentity.value(inherited), "Extras lost cover identity");
        main.putBoolean("fullscreenArt", false);
        main.putBoolean("textCard", true);
        main.putString("title", "Inner");
        main.putInt("stackCards", 7);
        ExtrasPreferences.store(prefs, "commitExtras", main);
        Bundle unchanged = ExtrasPreferences.store(prefs, "readExtras", sub);
        equal(true, unchanged.getBoolean("fullscreenArt"), "Inner edit overwrote cover art setting");
        equal(5, unchanged.getInt("stackCards"), "Inner edit overwrote cover stack count");
        sub.putBoolean("keepStretch", true);
        sub.putString("title", "Cover");
        ExtrasPreferences.store(prefs, "commitExtras", sub);
        Bundle inner = ExtrasPreferences.store(prefs, "readExtras", main);
        equal("Inner", inner.getString("title"), "Cover edit overwrote inner text card");
        equal(true, inner.getBoolean("textCard"), "Cover edit overwrote inner text flag");
        equal(false, inner.getBoolean("keepStretch"), "Cover stretch leaked into inner");
        equal(legacy, data.get("options"), "Migration destroyed legacy rollback settings");
        equal(false, ExtrasPreferences.store(prefs, "readExtras", sub).getBoolean("fullscreenArt"),
                "Cover disabled art did not persist");
        EditorExtras editor = new EditorExtras();
        Map<Integer, Bundle> drafts = (Map<Integer, Bundle>) Reflect.field(editor, "drafts");
        java.util.Set<Integer> dirty = (java.util.Set<Integer>) Reflect.field(editor, "changed");
        drafts.put(1, main); drafts.put(2, sub); dirty.add(1); dirty.add(2);
        Bundle frozen = editor.freeze();
        equal(2, frozen.getParcelableArrayList("screenProfiles", Bundle.class).size(), "Apply lost a screen draft");
        editor.committed(main);
        equal(false, dirty.contains(1), "Confirmed inner draft stayed dirty");
        equal(true, dirty.contains(2), "Confirming inner cleared unsaved cover draft");
        Bundle stale = new Bundle(sub); stale.putString("title", "Old cover");
        editor.committed(stale);
        equal(true, dirty.contains(2), "Late acknowledgement cleared newer cover edit");
        equal(1, editor.freeze().getParcelableArrayList("screenProfiles", Bundle.class).size(), "Remaining draft lost");
        editor.committed(sub);
        equal(null, editor.freeze(), "Confirmed drafts would be committed again");
        editor.reset();
        equal(true, drafts.isEmpty(), "Editor reset retained cross-session drafts");
        equal(true, dirty.isEmpty(), "Editor reset retained dirty state");
        MediaDepthTransition.screenChanged();
        MediaFullscreenExtras.screenChanged();
    }
}
