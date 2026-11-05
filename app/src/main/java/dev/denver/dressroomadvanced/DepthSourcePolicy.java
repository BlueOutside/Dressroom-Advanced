package dev.denver.dressroomadvanced;

/** Only the inspected still-image branches may reuse a static subject matte. */
final class DepthSourcePolicy {
    private String key;
    boolean observe(String next) { boolean changed = key != null && !key.equals(next); key = next; return changed; }
    void reset() { key = null; }
    String key() { return key; }
    static boolean image(int type, int parent) { return still(type) && (parent <= 0 || still(parent)); }
    private static boolean still(int type) { return type == 0 || type == 19 || type == 23 || type == 401 || type == 402 || type == 408; }
}
