package dev.denver.dressroomadvanced;

/** Fold support depends on One UI and hardware capabilities, never model names. */
public final class FoldSupportTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ScreenIdentity.supported(37, 90000, "LOCKSCREEN,AOD"), "Book-style fold rejected");
        check(ScreenIdentity.supported(36, 90000, "LOCKSCREEN"), "API 36 One UI 9 fold rejected");
        check(ScreenIdentity.supported(37, 90000, "COVER,WATCHFACE,LARGESCREEN"), "Clamshell fold rejected");
        check(ScreenIdentity.supported(37, 90100, "COVER,LOCKSCREEN"), "One UI 9 update rejected");
        check(!ScreenIdentity.supported(37, 90000, ""), "Non-fold acquired fold hooks");
        check(!ScreenIdentity.supported(37, 90000, null), "Unknown hardware acquired fold hooks");
        check(!ScreenIdentity.supported(37, 80500, "LOCKSCREEN"), "One UI 8.5 acquired One UI 9 hooks");
        check(!ScreenIdentity.supported(37, 100000, "LOCKSCREEN"), "Uninspected One UI major enabled");
        check(!ScreenIdentity.supported(35, 90000, "LOCKSCREEN"), "Unsupported API enabled");
        check(!ScreenIdentity.supported(38, 90000, "LOCKSCREEN"), "Future API silently enabled");
        check(!ScreenIdentity.supported(37, -1, "LOCKSCREEN"), "Unknown One UI version enabled");
        check(!ScreenIdentity.supported(37, 90000, "AOD"), "AOD-only capability treated as fold hardware");
        System.out.println("Fold capability gate: " + checks + " checks passed");
    }
}
