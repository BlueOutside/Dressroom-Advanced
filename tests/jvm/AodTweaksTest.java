package dev.denver.dressroomadvanced;

public final class AodTweaksTest {
    private static int checks;

    private static void check(boolean value) {
        if (!value) throw new AssertionError("AOD tweaks check " + checks);
        checks++;
    }

    public static void main(String[] args) {
        check(AodTweaks.clampSpeed(-4) == 0 && AodTweaks.clampSpeed(9) == AodTweaks.MAX_SPEED_LEVEL);
        check(AodTweaks.clampBounce(-1) == 0 && AodTweaks.clampBounce(500) == AodTweaks.MAX_BOUNCE);
        check(!AodTweaks.stretchActive(0, 0));
        check(AodTweaks.stretchActive(1, 0) && AodTweaks.stretchActive(0, 10));
        float[] samsung = AodTweaks.stretch(0, 0);
        check(samsung[0] == 0.012f && samsung[1] == 0.008f && samsung[2] == 0.06f && samsung[3] == 1f);
        check(samsung[4] == 0f);
        float[] fast = AodTweaks.stretch(2, 0);
        check(fast[0] == 0.020f && fast[2] == 0.100f && fast[3] == 1.35f);
        float[] veryFast = AodTweaks.stretch(99, 0);
        check(veryFast[3] == 1.60f);
        // Samsung's loop sets velocity = velocity * restitution on contact, so a bounce
        // needs a negative factor that reverses the velocity and loses energy.
        float[] bouncy = AodTweaks.stretch(0, 40);
        check(bouncy[4] < 0f && Math.abs(bouncy[4] + 0.40f) < 1e-6f);
        check(AodTweaks.stretch(0, 999)[4] == -AodTweaks.MAX_BOUNCE / 100f);

        // Auto AOD feature token stripping
        check(
                "aodversion-8-2-0,clocktransition"
                        .equals(
                                AodTweaks.stripFeatureToken(
                                        "aodversion-8-2-0,m55,clocktransition", "m55")));
        check(
                "aodversion-8-2-0"
                        .equals(AodTweaks.stripFeatureToken("m55, aodversion-8-2-0", "m55")));
        check(
                "aodversion-8-2-0"
                        .equals(AodTweaks.stripFeatureToken("aodversion-8-2-0,m55=true", "m55")));
        check("".equals(AodTweaks.stripFeatureToken("m55", "m55")));
        check(
                "aodversion-8-2-0,m550,xm55"
                        .equals(AodTweaks.stripFeatureToken("aodversion-8-2-0,m550,xm55", "m55")));
        check(AodTweaks.stripFeatureToken(null, "m55") == null);

        // Low-light StandBy clamping, palettes, brightness, and burn-in drift
        check(AodTweaks.clampLowLightLux(-5) == AodTweaks.MIN_LOW_LIGHT_LUX);
        check(AodTweaks.clampLowLightLux(100) == AodTweaks.MAX_LOW_LIGHT_LUX);
        check(AodTweaks.lowLightExitLux(5) == 13f);
        check(AodTweaks.lowLightExitLux(20) == 50f);

        check(AodTweaks.clampLowLightTheme(-3) == 0);
        check(AodTweaks.clampLowLightTheme(99) == AodTweaks.LOW_LIGHT_THEME_LABELS.length - 1);
        for (int t = 0; t < AodTweaks.LOW_LIGHT_THEME_LABELS.length; t++) {
            int[] p = AodTweaks.lowLightPalette(t);
            check(p.length == 3 && (p[0] >>> 24) == 0xFF && p[1] != 0 && p[2] != 0);
        }
        check(AodTweaks.clampLowLightFont(-1) == 0);
        check(AodTweaks.clampLowLightFont(99) == AodTweaks.LOW_LIGHT_FONT_LABELS.length - 1);
        check(AodTweaks.lowLightBrightnessFloat(-5) == 0f);
        check(AodTweaks.lowLightBrightnessFloat(0) == 0f);
        check(Math.abs(AodTweaks.lowLightBrightnessFloat(4) - 0.04f) < 1e-6f);
        check(Math.abs(AodTweaks.lowLightBrightnessFloat(99) - 0.30f) < 1e-6f);

        float sumX = 0f, sumY = 0f;
        for (int step = 0; step < 12; step++) {
            float[] off = AodTweaks.lowLightDriftOffset(step, 18f, 12f);
            check(Math.abs(off[0]) <= 18.001f && Math.abs(off[1]) <= 12.001f);
            sumX += off[0];
            sumY += off[1];
        }
        check(Math.abs(sumX) < 1e-3f && Math.abs(sumY) < 1e-3f);

        AodTweaks.LowLightGate gate = new AodTweaks.LowLightGate();
        check(!gate.isDark());
        check(gate.onLux(Float.NaN, 5, false, 1000L) == 0L && !gate.isDark());
        check(gate.onLux(-1f, 5, false, 1000L) == 0L && !gate.isDark());
        check(gate.onLux(3f, 5, false, 1000L) == AodTweaks.LowLightGate.ENTER_DEBOUNCE_MS);
        check(!gate.isDark());
        check(gate.evaluate(5, false, 2500L) == 1500L && !gate.isDark());
        check(gate.onLux(20f, 5, false, 2600L) == 0L && !gate.isDark());
        check(gate.onLux(4f, 5, false, 5000L) == AodTweaks.LowLightGate.ENTER_DEBOUNCE_MS);
        check(gate.evaluate(5, false, 8000L) == 0L && gate.isDark());
        check(gate.onLux(10f, 5, true, 9000L) == 0L && gate.isDark());
        check(gate.onLux(15f, 5, true, 10000L) == AodTweaks.LowLightGate.EXIT_DEBOUNCE_MS);
        check(gate.isDark());
        check(gate.evaluate(5, true, 11000L) == 1000L && gate.isDark());
        check(gate.onLux(9f, 5, true, 11500L) == 0L && gate.isDark());
        check(gate.onLux(25f, 5, true, 12000L) == AodTweaks.LowLightGate.EXIT_DEBOUNCE_MS);
        check(gate.evaluate(5, true, 14000L) == 0L && !gate.isDark());
        check(gate.onLux(2f, 5, true, 15000L) == 0L && gate.isDark());
        gate.reset();
        check(!gate.isDark());

        System.out.println("AOD tweaks: " + checks + " checks passed");
    }
}
