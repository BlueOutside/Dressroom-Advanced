package dev.denver.dressroomadvanced;

public final class ClockLabelScaleTest {
    private static int checks;

    private static void equal(float expected, float actual) {
        if (!(Math.abs(expected - actual) < .2f))
            throw new AssertionError("Expected " + expected + " but got " + actual);
        checks++;
    }

    private static ClockLabelFit.Size row(ClockLabelFit.Scales scales) {
        return new ClockLabelFit.Size(125 + 110 * scales.date() + 40 * scales.weather(), 50);
    }

    private static ClockLabelFit.Size stacked(ClockLabelFit.Scales scales) {
        return new ClockLabelFit.Size(
                Math.max(140, Math.max(100 * scales.date(), 58 * scales.weather())),
                115 + 20 * scales.date() + 14 * scales.weather());
    }

    public static void main(String[] args) {
        equal(50, ClockLabelScale.clamp(0));
        equal(100, ClockLabelScale.clamp(100));
        equal(175, ClockLabelScale.clamp(175));
        equal(200, ClockLabelScale.clamp(300));
        equal(0, ClockLabelScale.reference(0f, 100, 100));
        equal(1, ClockLabelScale.reference(0f, 110, 100));
        equal(1.35f, ClockLabelScale.reference(1.35f, 100, 100));

        float zoom = 1.6875f;
        float previewFactor = ClockLabelScale.labelFactor(1, zoom, 1);
        float appliedFactor = ClockLabelScale.labelFactor(1, 1, zoom);
        equal(20, 20 * previewFactor * zoom);
        equal(20, 20 * zoom * appliedFactor);
        equal(40, 20 * zoom * appliedFactor * 2);
        equal(20, 20 * ClockLabelScale.labelFactor(1, 1.125f, 1.5f) * 1.125f * 1.5f);
        equal(1, ClockLabelScale.labelFactor(1, Float.NaN, 0));

        ClockLabelFit.Size walls = new ClockLabelFit.Size(420, 240);
        ClockLabelFit.Scales loose = ClockLabelFit.fit(walls, new ClockLabelFit.Scales(2, 1),
                1, ClockLabelScaleTest::row);
        equal(2, loose.date());
        equal(1, loose.weather());
        equal(385, row(loose).width());

        ClockLabelFit.Scales tight = ClockLabelFit.fit(new ClockLabelFit.Size(320, 80),
                new ClockLabelFit.Scales(2, 1), 1, ClockLabelScaleTest::row);
        equal((320 - 125 - 40) / 110f, tight.date());
        equal(1, tight.weather());
        equal(320, row(tight).width());

        ClockLabelFit.Scales crowded = ClockLabelFit.fit(new ClockLabelFit.Size(300, 80),
                new ClockLabelFit.Scales(2, 2), 1, ClockLabelScaleTest::row);
        equal(300, row(crowded).width());

        ClockLabelFit.Scales stacked = ClockLabelFit.fit(new ClockLabelFit.Size(170, 240),
                new ClockLabelFit.Scales(2, 2), 1, ClockLabelScaleTest::stacked);
        equal(1.7f, stacked.date());
        equal(2, stacked.weather());
        equal(170, stacked(stacked).width());
        ClockLabelFit.Scales shortBox = ClockLabelFit.fit(new ClockLabelFit.Size(400, 150),
                new ClockLabelFit.Scales(2, 1), 1, ClockLabelScaleTest::stacked);
        equal(1.05f, shortBox.date());
        equal(1, shortBox.weather());
        equal(150, stacked(shortBox).height());

        equal(60, ClockLabelFit.centeredStart(100, 80, 0, 260));
        equal(50, ClockLabelFit.centeredStart(100, 100, 0, 260));
        equal(10, ClockLabelFit.centeredStart(35, 80, 10, 260));
        equal(180, ClockLabelFit.centeredStart(250, 80, 10, 260));
        System.out.println("Clock label scale and bounds: " + checks + " checks passed");
    }
}
