package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import org.json.JSONObject;

import java.lang.reflect.Field;

public final class BlurAppearanceProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static Object read(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    public static void main(String[] args) {
        try {
            run();
            System.out.println("Blur appearance: " + checks + " checks passed");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        check(
                BlurAppearance.permitsWindowBlur(false, true, false),
                "Samsung blur rejected because Android compositor flag is false");
        check(
                BlurAppearance.permitsWindowBlur(false, true, true),
                "Android window-blur disable hid Samsung's own blur");
        check(
                !BlurAppearance.permitsWindowBlur(true, false, true),
                "Explicit system disable ignored for the Android backend");
        check(
                BlurAppearance.permitsWindowBlur(true, false, false),
                "Android compositor blur rejected");
        check(
                !BlurAppearance.permitsWindowBlur(false, false, false),
                "Unsupported device gained window blur");
        check(!BlurAppearance.canReplay("clockBlur"), "Old clock coordinates can be replayed");
        check(BlurAppearance.canReplay("widgetBlur"), "Widget geometry refresh disabled");
        Looper.prepareMainLooper();
        Class<?> feature = Class.forName("com.samsung.android.feature.SemFloatingFeature");
        System.out.println("Samsung native blur capability: " + feature.getMethod("getBoolean", String.class)
                .invoke(feature.getMethod("getInstance").invoke(null),
                        "SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_3D_SURFACE_TRANSITION_FLAG"));
        Class<?> info = Class.forName("android.view.SemBlurInfo");
        BlurAppearance.install(info, View.class.getDeclaredMethod("semSetBlurInfo", info));
        Class<?> builderType = Class.forName("android.view.SemBlurInfo$Builder");
        Object builder = builderType.getConstructor(int.class).newInstance(0);
        builderType.getMethod("setRadius", int.class).invoke(builder, 77);
        builderType.getMethod("setBackgroundColor", int.class).invoke(builder, 0x55123456);
        builderType
                .getMethod(
                        "setColorCurve",
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class)
                .invoke(builder, .2f, 4f, 3f, 240f, 9f, 245f);
        Path path = new Path();
        path.addCircle(30, 30, 20, Path.Direction.CW);
        builderType.getMethod("setBackgroundClipPath", Path.class).invoke(builder, path);
        Object nativeInfo = builderType.getMethod("build").invoke(builder);
        Bundle settings = ExtrasPreferences.clean(null);
        for (String surface : BlurAppearanceSettings.SURFACES) {
            check(!settings.getBoolean(surface + "Custom"), "New install overrides Samsung blur");
            settings.putBoolean(surface + "Custom", true);
            settings.putInt(surface + "Radius", 251);
            settings.putInt(surface + "Saturation", -70);
            settings.putInt(surface + "Tint", 2);
            settings.putInt(surface + "TintOpacity", 40);
            Object styled = BlurAppearance.styled(nativeInfo, settings, surface);
            check(read(styled, "mBlurRadius").equals(251), "Radius not applied");
            check(read(styled, "mBlurMode").equals(0), "Backend changed");
            check(read(styled, "mClipPath") == read(nativeInfo, "mClipPath"), "Depth path changed");
            check(read(styled, "mBackgroundBlurColor").equals(0x66ffffff), "Tint incorrect");
            Object curve = read(styled, "mColorCurve"), oldCurve = read(nativeInfo, "mColorCurve");
            check(curve != oldCurve, "Shared native curve mutated");
            check(read(curve, "mSaturation").equals(-.5f), "Signed saturation adjustment not applied");
            check(
                    read(curve, "mCurveBias").equals(4f) && read(curve, "mMaxY").equals(245f),
                    "Native contrast curve lost");
            check(
                    read(nativeInfo, "mBlurRadius").equals(77)
                            && read(oldCurve, "mSaturation").equals(.2f),
                    "Native input changed");
            settings.putInt(surface + "Saturation", 0);
            check(
                    read(
                                    read(
                                            BlurAppearance.styled(nativeInfo, settings, surface),
                                            "mColorCurve"),
                                    "mSaturation")
                            .equals(.2f),
                    "Neutral saturation replaced Samsung's curve");
            settings.putInt(surface + "Tint", 0);
            check(
                    read(
                                    BlurAppearance.styled(nativeInfo, settings, surface),
                                    "mBackgroundBlurColor")
                            .equals(0x55123456),
                    "Default tint not preserved");
        }
        JSONObject json = new JSONObject();
        BlurAppearanceSettings.write(settings, json);
        Bundle restored = new Bundle();
        BlurAppearanceSettings.read(json, restored);
        restored = ExtrasPreferences.clean(restored);
        check(BlurAppearanceSettings.same(settings, restored), "Settings JSON lost blur values");
        String aod = AodWidgetAppearance.PROFILE;
        check(ExtrasPreferences.clean(null).getInt(aod + "Radius") == 110,
                "AOD radius does not start from the native preset");
        restored.putInt(aod + "Tint", 3);
        Bundle untinted = ExtrasPreferences.clean(restored);
        check(read(BlurAppearance.styled(nativeInfo, untinted, aod), "mBackgroundBlurColor").equals(0),
                "AOD tint removal did not keep the window blur untinted");
        check(BlurAppearanceSettings.same(settings, untinted, "widgetBlur"),
                "AOD tint altered the awake widget profile");
        restored.putInt("widgetBlurRadius", 9999);
        restored.putInt("clockBlurSaturation", -999);
        Bundle cleaned = ExtrasPreferences.clean(restored);
        check(cleaned.getInt("widgetBlurRadius") == 300, "Radius not bounded");
        check(cleaned.getInt("clockBlurSaturation") == -100, "Saturation not bounded");
        check(!BlurAppearanceSettings.same(settings, cleaned), "Changed draft marked committed");
        // A null color curve must get a linear curve rather than borrowing mutable native state.
        Object noCurve =
                info.getConstructor(
                                int.class,
                                Bitmap.class,
                                int.class,
                                int.class,
                                float.class,
                                float.class,
                                float.class,
                                float.class,
                                int.class)
                        .newInstance(0, null, 50, 0, 12f, 13f, 14f, 15f, 0);
        Object styled = BlurAppearance.styled(noCurve, settings, "clockBlur");
        check(
                read(styled, "mCornerRadiusTL").equals(12f)
                        && read(styled, "mCornerRadiusBR").equals(15f),
                "Rounded geometry changed");
        check(read(noCurve, "mColorCurve") == null, "Missing native curve mutated");
        builder = builderType.getConstructor(int.class).newInstance(2);
        Object canvas = builderType.getMethod("build").invoke(builder);
        settings.putInt("clockBlurTint", 2);
        styled = BlurAppearance.styled(canvas, settings, "clockBlur");
        check(
                read(styled, "mBackgroundBlurColor").equals(read(canvas, "mBackgroundBlurColor")),
                "Canvas blur received an unsupported tint");
    }
}
