package dev.denver.dressroomadvanced;

public final class ReleaseVersionTest {
    private static int checks;

    private static void order(String a, String b, int expected) {
        if (Integer.signum(ReleaseVersion.parse(a).compareTo(ReleaseVersion.parse(b))) != expected)
            throw new AssertionError(a + " compared with " + b);
        checks++;
    }

    public static void main(String[] args) {
        order("1.1.10", "1.1.9", 1);
        order("v1.2.0", "1.1.99", 1);
        order("1.1.9", "v1.1.9", 0);
        order("1.1.9-alpha.2", "1.1.9-alpha.10", -1);
        order("1.1.9-alpha", "1.1.9", -1);
        order("1.1.9+build.7", "1.1.9", 0);
        order("1.1.8", "1.1.9", -1);
        order("1.1.9-alpha.1", "1.1.9-alpha.beta", -1);
        for (String bad :
                new String[] {"", "latest", "1.1", "1.1.9/evil", "v999999999999999999999.0.0"}) {
            try {
                ReleaseVersion.parse(bad);
                throw new AssertionError("Accepted " + bad);
            } catch (IllegalArgumentException expected) {
                checks++;
            }
        }
        System.out.println("Release versions: " + checks + " checks passed");
    }
}
