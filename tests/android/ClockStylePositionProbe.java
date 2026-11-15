package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.util.SizeF;

public final class ClockStylePositionProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        SizeF portrait = new SizeF(1080, 2400);
        Rect nativeArea = new Rect(40, 160, 1040, 1800);
        Rect saved = new Rect(40, 160, 1040, 1900);
        check(
                ClockStyleWidgetPosition.useSaved(nativeArea, saved, portrait, true),
                "Widget area mismatch ignored");
        check(
                !ClockStyleWidgetPosition.useSaved(nativeArea, saved, portrait, false),
                "Widget-free clock changed");
        check(
                !ClockStyleWidgetPosition.useSaved(nativeArea, null, portrait, true),
                "Default position changed");
        check(
                !ClockStyleWidgetPosition.useSaved(
                        nativeArea, new Rect(nativeArea), portrait, true),
                "Equal bounds changed");
        check(
                !ClockStyleWidgetPosition.useSaved(
                        nativeArea, new Rect(-1, 0, 900, 1900), portrait, true),
                "Negative bounds accepted");
        check(
                !ClockStyleWidgetPosition.useSaved(
                        nativeArea, new Rect(0, 0, 1081, 1900), portrait, true),
                "Offscreen width accepted");
        check(
                !ClockStyleWidgetPosition.useSaved(
                        nativeArea, new Rect(0, 0, 1000, 2401), portrait, true),
                "Offscreen height accepted");
        check(
                !ClockStyleWidgetPosition.useSaved(nativeArea, new Rect(), portrait, true),
                "Empty saved bounds accepted");
        check(
                !ClockStyleWidgetPosition.useSaved(null, saved, portrait, true),
                "Missing native result changed");
        check(
                !ClockStyleWidgetPosition.useSaved(nativeArea, saved, null, true),
                "Missing display accepted");
        check(
                !ClockStyleWidgetPosition.useSaved(nativeArea, saved, new SizeF(0, 2400), true),
                "Unready display accepted");
        SizeF landscape = new SizeF(2400, 1080);
        check(
                ClockStyleWidgetPosition.useSaved(
                        new Rect(80, 40, 2300, 800), new Rect(80, 40, 2300, 900), landscape, true),
                "Landscape bounds rejected");
        check(nativeArea.bottom == 1800 && saved.bottom == 1900, "Input bounds mutated");
        if (args.length > 0) {
            ClassLoader loader =
                    new dalvik.system.PathClassLoader(
                            args[0], ClockStylePositionProbe.class.getClassLoader());
            var binding = ClockStyleWidgetPosition.bind(loader);
            check(
                    binding.update()
                            .getDeclaringClass()
                            .getName()
                            .endsWith("KeyguardClockPositionManager"),
                    "Wrong update owner");
            check(
                    binding.resolve().getReturnType() == Rect.class
                            && binding.hasWidgets().getType() == boolean.class,
                    "Wrong native contracts");
            System.out.println(
                    "Native position bindings: "
                            + binding.update().getName()
                            + ", "
                            + binding.resolve().getDeclaringClass().getName()
                            + "."
                            + binding.resolve().getName());
        }
        System.out.println("Clock Style widget position: " + checks + " checks passed");
    }
}
