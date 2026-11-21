package dev.denver.dressroomadvanced;

final class DepthSourcePolicy {
    static boolean visible(boolean interactive, boolean aodWallpaper) {
        return interactive || aodWallpaper;
    }

    static boolean suppressedOnAod(
            boolean interactive, boolean hideOnAod, boolean aodView, boolean dozing) {
        return hideOnAod && (aodView || dozing || !interactive);
    }

    private String key;

    boolean observe(String next) {
        boolean changed = key != null && !key.equals(next);
        key = next;
        return changed;
    }

    void reset() {
        key = null;
    }

    String key() {
        return key;
    }

    static boolean invalidProfile(
            boolean known, boolean image, boolean reset, String currentKey, String savedKey) {
        if (known && !image) return true;
        boolean stamped = savedKey != null && !savedKey.isEmpty();
        boolean current = known && image && stamped && savedKey.equals(currentKey);
        if (known && stamped && !current) return true;
        return reset && !current;
    }

    static boolean image(int type, int parent) {
        return image(type, parent, false);
    }

    static boolean image(int type, int parent, boolean cover) {
        return still(type, cover) && (parent <= 0 || still(parent, cover));
    }

    private static boolean still(int type, boolean cover) {
        return type == 0
                || type == 12
                || type == 16
                || type == 17
                || type == 19
                || type == 23
                || type == 401
                || type == 402
                || type == 408
                || type == 409
                || (cover && type == 5);
    }
}
