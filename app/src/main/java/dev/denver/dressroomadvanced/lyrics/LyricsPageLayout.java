package dev.denver.dressroomadvanced.lyrics;

/** Available lyrics space around Samsung's measured, transformed player controls. */
public final class LyricsPageLayout {
    public record Bounds(int left, int top, int right, int bottom) {
        public int width() { return Math.max(0, right - left); }
        public int height() { return Math.max(0, bottom - top); }
        long area() { return (long) width() * height(); }
    }

    private LyricsPageLayout() {}

    public static Bounds place(Bounds safe, Bounds controls, int gap, int minWidth, int minHeight) {
        if (safe == null || controls == null || safe.area() == 0) return null;
        int left = Math.max(safe.left, controls.left), top = Math.max(safe.top, controls.top);
        int right = Math.min(safe.right, controls.right), bottom = Math.min(safe.bottom, controls.bottom);
        if (right <= left || bottom <= top) return null;
        gap = Math.max(0, gap);
        Bounds above = new Bounds(safe.left, safe.top, safe.right, Math.max(safe.top, top - gap));
        Bounds before = new Bounds(safe.left, safe.top, Math.max(safe.left, left - gap), safe.bottom);
        Bounds after = new Bounds(Math.min(safe.right, right + gap), safe.top, safe.right, safe.bottom);
        Bounds below = new Bounds(safe.left, Math.min(safe.bottom, bottom + gap), safe.right, safe.bottom);
        if (above.width() >= minWidth && above.height() >= minHeight) return above;
        Bounds best = null;
        for (Bounds candidate : new Bounds[] {before, after, above, below}) {
            if (candidate.width() < minWidth || candidate.height() < minHeight) continue;
            if (best == null || candidate.area() > best.area()) best = candidate;
        }
        return best;
    }
}
