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
                !BlurAppearance.permitsWindowBlur(false, true, true),
                "Explicit system blur disable ignored for the Samsung backend");
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
                    read(curve, "mCurveBias").equals(surface.equals(AodWidgetAppearance.PROFILE) ? 0f : 4f)
                            && read(curve, "mMaxY").equals(surface.equals(AodWidgetAppearance.PROFILE) ? 255f : 245f),
                    "Wrong native/explicit-AOD luminance curve");
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
        for (int tint : new int[] {1, 2, 3}) {
            for (int opacity : new int[] {0, 5, 50, 100}) {
                Bundle explicit = new Bundle(untinted);
                explicit.putInt(aod + "Tint", tint);
                explicit.putInt(aod + "TintOpacity", opacity);
                Object request = BlurAppearance.styled(nativeInfo, explicit, aod);
                Object explicitCurve = read(request, "mColorCurve");
                check(read(explicitCurve, "mCurveBias").equals(0f)
                                && read(explicitCurve, "mMinX").equals(0f)
                                && read(explicitCurve, "mMaxX").equals(255f)
                                && read(explicitCurve, "mMinY").equals(0f)
                                && read(explicitCurve, "mMaxY").equals(255f),
                        "Explicit AOD tint retains Samsung's pale luminance preset");
                int alpha = tint == 3 ? 0 : Math.round(opacity * 2.55f);
                int expected = (alpha << 24) | (tint == 2 ? 0xffffff : 0);
                check(read(request, "mBackgroundBlurColor").equals(expected),
                        "AOD tint opacity differs from the selected amount");
            }
        }
        untinted.putBoolean(AodWidgetAppearance.NEUTRAL, true);
        untinted.putInt(aod + "Contrast", 17);
        untinted.putInt(aod + "Brightness", -20);
        Object natural = BlurAppearance.styled(nativeInfo, untinted, aod);
        Object naturalCurve = read(natural, "mColorCurve");
        check(read(naturalCurve, "mCurveBias").equals(17f),
                "Neutral colors overwrote the custom tone curve");
        check((Float) read(naturalCurve, "mMaxY") < 255f,
                "Neutral colors overwrote the custom brightness");
        check(read(natural, "mBackgroundBlurColor").equals(0), "Neutral colors restored a removed tint");
        JSONObject naturalJson = new JSONObject();
        BlurAppearanceSettings.write(untinted, naturalJson);
        Bundle naturalRestored = new Bundle();
        BlurAppearanceSettings.read(naturalJson, naturalRestored);
        check(naturalRestored.getInt(aod + "Contrast") == 17
                        && naturalRestored.getInt(aod + "Brightness") == -20,
                "Tone curve and brightness did not round-trip through saved settings");
        restored.putInt("widgetBlurRadius", 9999);
        restored.putInt("clockBlurSaturation", -999);
        restored.putInt(aod + "Contrast", 999);
        restored.putInt(aod + "Brightness", -999);
        Bundle cleaned = ExtrasPreferences.clean(restored);
        check(cleaned.getInt("widgetBlurRadius") == 300, "Radius not bounded");
        check(cleaned.getInt("clockBlurSaturation") == -100, "Saturation not bounded");
        check(cleaned.getInt(aod + "Contrast") == 100, "Tone curve not bounded");
        check(cleaned.getInt(aod + "Brightness") == -100, "Brightness not bounded");
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
        // Framework SemBlurInfo.scaleDownRadius() mutates mBlurRadius in place when submitted;
        // stored requests must isolate submitted copies and restore unscaled native radius.
        Object isolated = BlurAppearance.isolateSubmission(nativeInfo, nativeInfo);
        check(isolated != nativeInfo && read(isolated, "mBlurRadius").equals(77),
                "Direct submission was not isolated from in-place framework radius scaling");
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        View sampleHost = new View(
                (android.content.Context) Class.forName("android.app.ActivityThread")
                        .getMethod("getSystemContext")
                        .invoke(Class.forName("android.app.ActivityThread")
                                .getMethod("systemMain").invoke(null)));
        sampleHost.setTag("BlurView");
        sampleHost.layout(0, 0, 100, 50);
        Object submittedCopy = BlurAppearance.submit(sampleHost, nativeInfo, nativeInfo, "widgetBlur");
        check(submittedCopy != nativeInfo, "submit() returned live canonical SemBlurInfo reference");
        Field radiusField = info.getDeclaredField("mBlurRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(submittedCopy, 19);
        check(read(BlurAppearance.nativeRequest(sampleHost), "mBlurRadius").equals(77),
                "Mutated framework copy poisoned stored nativeRequest radius");
        radiusField.setInt(nativeInfo, 19);
        check(read(BlurAppearance.nativeRequest(sampleHost), "mBlurRadius").equals(77),
                "In-place nativeInfo mutation was not restored from snapshot radius");
    }
}
