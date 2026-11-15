package dev.denver.dressroomadvanced;

public final class MediaWallpaperBlurTest {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        MediaWallpaperBlur.Hold state = new MediaWallpaperBlur.Hold();
        Object first = new Object(), updated = new Object();
        check(state.request(first) == first, "Inactive fullscreen changed native blur");
        check(state.hold(), "First artwork frame did not acquire blur override");
        check(!state.hold(), "Repeated artwork frame reacquired override");
        check(state.request(updated) == null, "Native update blurred visible artwork");
        check(state.nativeInfo == updated, "Latest native request was discarded");
        check(state.release(), "Closing artwork did not release override");
        check(state.nativeInfo == updated && !state.held, "Release restored stale native state");
        check(!state.release(), "Repeated release changed native state");
        state.hold();
        check(state.request(null) == null, "Native close clear was blocked");
        state.release();
        check(state.nativeInfo == null, "Close resurrected an old wallpaper blur");
        check(state.request(first) == first, "Framed player lost native blur after fullscreen");
        MediaWallpaperBlur.Hold other = new MediaWallpaperBlur.Hold();
        other.request(updated);
        state.hold();
        check(other.nativeInfo == updated && !other.held, "Override leaked to another host");
        System.out.println("Legacy artwork wallpaper blur state: " + checks + " checks passed");
    }
}
