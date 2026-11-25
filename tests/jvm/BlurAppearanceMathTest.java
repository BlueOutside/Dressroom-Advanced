package dev.denver.dressroomadvanced;

import java.util.Arrays;

public final class BlurAppearanceMathTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        float[][] presets = {
            {.15f, 18f, 5f, 235f, 36.7f, 245.8f},
            {.1f, -15f, 0f, 255f, 9.8f, 70f},
            {0f, 0f, 0f, 255f, 0f, 255f}
        };
        for (float[] preset : presets) {
            float[] values = preset.clone();
            BlurColorCurve.adjust(values, 0, 0, 0);
            check(Arrays.equals(values, preset), "Zero adjustments must retain Samsung's preset");
            for (int saturation : new int[] {-100, 0, 100}) {
                for (int contrast : new int[] {-100, 0, 100}) {
                    for (int brightness : new int[] {-100, 0, 100}) {
                        values = preset.clone();
                        BlurColorCurve.adjust(values, saturation, contrast, brightness);
                        check(values[0] >= -1f && values[0] <= 1f, "Saturation outside native limits");
                        check(values[1] >= -100f && values[1] <= 100f, "Curve bias outside native limits");
                        check(values[2] == preset[2] && values[3] == preset[3], "Input bounds changed");
                        check(values[4] >= 0f && values[5] <= 255f && values[4] <= values[5],
                                "Brightness inverted or exceeded output luminance bounds");
                    }
                }
            }
        }
        float[] natural = {0f, 0f, 0f, 255f, 0f, 255f};
        BlurColorCurve.adjust(natural, -40, 12, -10);
        check(natural[0] == -.4f && natural[1] == 12f && natural[5] < 255f,
                "Neutral colors must still respond to custom curve/brightness controls");
        check(BlurTintPolicy.canAnimate(2, false), "Awake tint fades disabled");
        check(!BlurTintPolicy.canAnimate(2, true), "AOD transition can leave a tint fade suspended");
        for (int display : new int[] {0, 1, 3, 4, 5, 6})
            check(!BlurTintPolicy.canAnimate(display, false), "Inactive display must settle tint immediately");
        System.out.println("Blur appearance math: " + checks + " checks passed");
    }
}
