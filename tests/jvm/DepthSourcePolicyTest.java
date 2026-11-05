package dev.denver.dressroomadvanced;

public final class DepthSourcePolicyTest {
    private static int checks;
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    public static void main(String[] args) {
        DepthSourcePolicy state = new DepthSourcePolicy();
        check(!state.observe("photo-a"), "Initial source treated as replacement");
        check(!state.observe("photo-a"), "Same image reset depth");
        check(state.observe("photo-b"), "Replacement image retained depth");
        check(!state.observe("photo-b"), "Repeated source notification reset depth");
        state.reset(); check(!state.observe("photo-b"), "New editor retained old source identity");
        for (int type : new int[]{0, 19, 23, 401, 402, 408}) check(DepthSourcePolicy.image(type, 0), "Still-image effect rejected: " + type);
        for (int type : new int[]{1, 3, 5, 7, 8, 9, 15, 202, 403, 999}) check(!DepthSourcePolicy.image(type, 0), "Unsupported renderer accepted: " + type);
        check(!DepthSourcePolicy.image(0, 8), "Video parent accepted through image child");
        check(DepthSourcePolicy.image(402, 401), "Weather/frame wrapping changed source eligibility");
        System.out.println("Depth source policy: " + checks + " checks passed");
    }
}
