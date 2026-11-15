package dev.denver.dressroomadvanced;

public final class BlurOpacityTest {
    private static int checks;

    private static void check(float value, float expected) {
        if (Math.abs(value - expected) > .00001f)
            throw new AssertionError(value + " != " + expected);
        checks++;
    }

    public static void main(String[] args) {
        check(BlurOpacity.resolve(-1, .502f), .502f);
        check(BlurOpacity.resolve(Float.NaN, .35f), .35f);
        check(BlurOpacity.resolve(Float.POSITIVE_INFINITY, .65f), .65f);
        check(BlurOpacity.resolve(.8f, 1), .8f);
        check(BlurOpacity.resolve(0, 1), 0);
        check(BlurOpacity.resolve(2, 1), 1);
        System.out.println("Clock blur opacity: " + checks + " checks passed");
    }
}
