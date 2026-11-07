package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class OrientationProfileProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static SharedPreferences prefs(Map<String, Object> values) {
        return (SharedPreferences)
                Proxy.newProxyInstance(
                        OrientationProfileProbe.class.getClassLoader(),
                        new Class<?>[] {SharedPreferences.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getString", "getInt" ->
                                            values.getOrDefault(args[0], args[1]);
                                    case "edit" ->
                                            Proxy.newProxyInstance(
                                                    OrientationProfileProbe.class.getClassLoader(),
                                                    new Class<?>[] {SharedPreferences.Editor.class},
                                                    new java.lang.reflect.InvocationHandler() {
                                                        private final Map<String, Object> staged =
                                                                new HashMap<>();

                                                        public Object invoke(
                                                                Object editor,
                                                                java.lang.reflect.Method operation,
                                                                Object[] input) {
                                                            if (operation
                                                                    .getName()
                                                                    .startsWith("put")) {
                                                                staged.put(
                                                                        (String) input[0],
                                                                        input[1]);
                                                                return editor;
                                                            }
                                                            if (operation
                                                                    .getName()
                                                                    .equals("commit")) {
                                                                values.putAll(staged);
                                                                return true;
                                                            }
                                                            throw new AssertionError(
                                                                    operation.getName());
                                                        }
                                                    });
                                    default -> throw new AssertionError(method.getName());
                                });
    }

    @SuppressWarnings("deprecation")
    private static Context context(int orientation) {
        Configuration config = new Configuration(Resources.getSystem().getConfiguration());
        config.orientation = orientation;
        Resources resources =
                new Resources(
                        Resources.getSystem().getAssets(),
                        Resources.getSystem().getDisplayMetrics(),
                        config);
        return new ContextWrapper(null) {
            @Override
            public Resources getResources() {
                return resources;
            }
        };
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        check(SettingsScope.orientation(1200, 2000) == 1, "Portrait preview identity");
        check(SettingsScope.orientation(2000, 1200) == 2, "Landscape preview identity");
        check(SettingsScope.orientation(0, 1200) == 0, "Unmeasured preview is not landscape");
        check(SettingsScope.orientation(1200, 1200) == 0, "Square preview must not guess");
        check(SettingsScope.orientation(88) == 0, "Unknown configuration");
        String base = SettingsScope.key(77, 2, 800);
        Bundle portrait = new Bundle();
        portrait.putInt(LayoutOrientation.FIELD, 1);
        Bundle landscape = new Bundle();
        landscape.putInt(LayoutOrientation.FIELD, 2);
        check(
                !LayoutOrientation.key(base, portrait)
                        .equals(LayoutOrientation.key(base, landscape)),
                "Layout keys collided");
        check(LayoutOrientation.key(base, new Bundle()).equals(base), "Legacy key changed");
        String legacy =
                "{\"depthEnabled\":true,\"depthMaskRef\":\"baked\",\"depthSourceMaskRef\":\"source\"}";
        Map<String, Object> values = new HashMap<>();
        values.put(base, legacy);
        SharedPreferences prefs = prefs(values);
        check(
                LayoutOrientation.read(prefs, base, landscape).equals(legacy),
                "Migration lost saved settings");
        org.json.JSONObject inherited =
                new org.json.JSONObject(LayoutOrientation.read(prefs, base, portrait));
        check(!inherited.has("depthMaskRef"), "Landscape mask imported into portrait");
        check(inherited.getBoolean("depthEnabled"), "Depth preference lost in second layout");
        check(
                inherited.getString("depthSourceMaskRef").equals("source"),
                "Reusable source subject lost");
        check(values.get(base).equals(legacy), "Migration destroyed rollback record");
        values.put(LayoutOrientation.key(base, portrait), "portrait-mask");
        values.put(LayoutOrientation.key(base, landscape), "landscape-mask");
        check(
                LayoutOrientation.read(prefs, base, portrait).equals("portrait-mask"),
                "Portrait profile overwritten");
        check(
                LayoutOrientation.read(prefs, base, landscape).equals("landscape-mask"),
                "Landscape profile overwritten");
        values.remove(LayoutOrientation.key(base, portrait));
        check(
                !new org.json.JSONObject(LayoutOrientation.read(prefs, base, portrait))
                        .has("depthMaskRef"),
                "Missing portrait profile reused landscape geometry");
        check(
                LayoutOrientation.read(prefs, SettingsScope.key(78, 2, 800), portrait) == null,
                "Wallpaper identities leaked");
        check(
                LayoutOrientation.read(prefs, base, new Bundle()).equals(legacy),
                "Legacy reader lost its record");
        Bundle frozen = new Bundle();
        LayoutOrientation.stamp(context(1), frozen);
        LayoutOrientation.stamp(context(2), frozen);
        check(
                frozen.getInt(LayoutOrientation.FIELD) == 1,
                "Late Apply adopted completion orientation");
        Bundle widgets = WidgetPreferences.clean(frozen);
        check(
                widgets.getInt(LayoutOrientation.FIELD) == 1,
                "Widget sanitizer dropped layout identity");
        Bundle copied = new Bundle();
        LayoutOrientation.copy(landscape, copied);
        check(copied.getInt(LayoutOrientation.FIELD) == 2, "Clock result lost layout identity");
        Bundle invalid = new Bundle();
        invalid.putInt(LayoutOrientation.FIELD, -1);
        LayoutOrientation.copy(invalid, copied);
        check(copied.getInt(LayoutOrientation.FIELD) == 0, "Invalid layout reached persistence");
        Bundle policy = WidgetPreferences.defaults();
        policy.putInt("depth", 1);
        policy.putInt("depthLayouts", 3);
        policy.putInt(LayoutOrientation.FIELD, 1);
        check(WidgetPreferences.depth(policy, null), "Both-layout policy excludes portrait");
        policy.putInt(LayoutOrientation.FIELD, 2);
        check(WidgetPreferences.depth(policy, null), "Both-layout policy excludes landscape");
        policy.putInt("depthLayouts", 1);
        check(!WidgetPreferences.depth(policy, null), "Portrait-only policy leaks into landscape");
        policy.putInt(LayoutOrientation.FIELD, 1);
        check(WidgetPreferences.depth(policy, null), "Portrait-only policy lost portrait");
        policy.putInt("depthLayouts", 2);
        check(!WidgetPreferences.depth(policy, null), "Landscape-only policy leaks into portrait");
        policy.putInt("depthLayouts", 0);
        check(
                WidgetPreferences.depth(policy, null),
                "Separate-layout policy lost current On state");
        policy.putInt("depthLayouts", 3);
        policy.putInt("depth", 2);
        check(!WidgetPreferences.depth(policy, null), "Off policy still clips widgets");
        policy.putInt("depth", 0);
        Bundle clock = new Bundle();
        clock.putBoolean("depthEnabled", true);
        check(
                WidgetPreferences.depth(policy, clock),
                "Shared Follow clock lost current clock state");
        clock.putBoolean("depthEnabled", false);
        check(!WidgetPreferences.depth(policy, clock), "Shared Follow clock ignored clock Off");
        String widgetBase = "wallpaper_77_main";
        values.put(widgetBase + "_portrait", "{\"depth\":2,\"scale\":1.5}");
        values.put(widgetBase + "_landscape", "{\"depth\":2,\"scale\":0.75}");
        values.put(widgetBase + "_depth_policy", "{\"depth\":1,\"depthLayouts\":3}");
        Context store =
                new ContextWrapper(context(1)) {
                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return prefs;
                    }
                };
        portrait.putInt("wallpaperId", 77);
        landscape.putInt("wallpaperId", 77);
        Bundle p = WidgetPreferences.store(store, "readWidgets", portrait);
        Bundle l = WidgetPreferences.store(store, "readWidgets", landscape);
        check(
                WidgetPreferences.depth(p, null) && WidgetPreferences.depth(l, null),
                "Shared policy not read in both layouts");
        check(
                p.getFloat("scale") == 1.5f && l.getFloat("scale") == .75f,
                "Depth policy overwrote layout-specific scale");
        values.remove(widgetBase + "_depth_policy");
        values.put(widgetBase + "_landscape", "{\"depth\":2,\"depthLayouts\":3,\"scale\":0.75}");
        l = WidgetPreferences.store(store, "readWidgets", landscape);
        check(
                l.getInt("depthLayouts") == 0 && !WidgetPreferences.depth(l, null),
                "Removed shared policy reappeared from an old layout record");
        System.out.println("Orientation profile isolation: " + checks + " checks passed");
    }
}
