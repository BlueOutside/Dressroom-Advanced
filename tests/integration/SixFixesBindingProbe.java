package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Method;

/** Read-only live-APK DEX contract check for digit alignment, LockStar blur, Now Bar AOD, and editor settle. */
public final class SixFixesBindingProbe {
    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        if (args.length != 2) throw new IllegalArgumentException("Usage: aod.apk dressroom.apk");

        ClassLoader aodLoader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        ClassLoader editorLoader = new PathClassLoader(args[1], ClassLoader.getSystemClassLoader());

        DexSignatures.cachedRun(
                () -> {
                    try {
                        Class<?> textClass =
                                Class.forName(
                                        "com.samsung.android.uniform.widget.NonePaddingTextView",
                                        false,
                                        aodLoader);
                        Method measure = DigitAlignmentFix.findMeasureMethod(textClass);
                        if (measure == null)
                            throw new AssertionError("Missing NonePaddingTextView measure method");
                        var digit =
                                DigitAlignmentFix.resolve(
                                        textClass, measure, DexSignatures.body(measure), aodLoader);
                        if (digit == null)
                            throw new AssertionError("DigitAlignmentFix failed to resolve");
                        System.out.println(
                                "DigitAlignmentFix resolved: method="
                                        + digit.measure().getName()
                                        + " clip="
                                        + digit.clipDigitForAlign().getName()
                                        + " ready="
                                        + digit.alignmentSampleReady().getName()
                                        + " sample="
                                        + digit.alignmentSampleText().getName()
                                        + " len="
                                        + digit.alignmentSampleLength().getName());

                        Class<?> blurController =
                                Class.forName(
                                        "com.samsung.android.app.facewidget.pages.clock.blur.ClockBlurController",
                                        false,
                                        aodLoader);
                        var lockStar =
                                LockStarClockBlur.resolve(
                                        blurController,
                                        aodLoader,
                                        anchors -> DexSignatures.find(aodLoader, anchors));
                        if (lockStar == null)
                            throw new AssertionError("LockStarClockBlur failed to resolve");
                        System.out.println(
                                "LockStarClockBlur resolved: setter="
                                        + lockStar.setSggActive().getName()
                                        + " machine="
                                        + lockStar.stateMachine().getName()
                                        + " sgg="
                                        + lockStar.sggActive().getName());

                        Class<?> settingsHelper =
                                Class.forName(
                                        "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper",
                                        false,
                                        aodLoader);
                        var nowBarAod =
                                NowBarAodOption.resolve(
                                        settingsHelper,
                                        aodLoader,
                                        anchors -> DexSignatures.find(aodLoader, anchors));
                        if (nowBarAod == null)
                            throw new AssertionError("NowBarAodOption failed to resolve");
                        System.out.println(
                                "NowBarAodOption resolved: entry="
                                        + nowBarAod.getIntValue().getDeclaringClass().getName()
                                        + "."
                                        + nowBarAod.getIntValue().getName()
                                        + " uri="
                                        + nowBarAod.keyUri().getName());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });

        DexSignatures.cachedRun(
                () -> {
                    try {
                        var editor =
                                ClockBlurDelays.resolveEditorContract(
                                        editorLoader,
                                        anchors -> DexSignatures.find(editorLoader, anchors));
                        if (editor == null)
                            throw new AssertionError("ClockBlurDelays editor contract failed");
                        System.out.println(
                                "ClockBlurDelays editor resolved: coordinator="
                                        + editor.coordinator().getName()
                                        + " delay="
                                        + editor.delay().getDeclaringClass().getName()
                                        + "."
                                        + editor.delay().getName());
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
    }
}
