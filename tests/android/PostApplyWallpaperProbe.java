package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic delayed native-ID and overlapping Apply checks; no app data accessed. */
public final class PostApplyWallpaperProbe {
    private static int checks;
    private static void equal(Object expected, Object actual, String message) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(message);
    }
    private static Bundle profile(int screen, int orientation, int id, String edit) {
        Bundle b = new Bundle(); b.putInt(ScreenIdentity.FIELD, screen);
        b.putInt(LayoutOrientation.FIELD, orientation); b.putInt("wallpaperId", id);
        b.putInt("type", 2); b.putInt("font", 800); b.putString("edit", edit);
        b.putBoolean("depthEnabled", true); return b;
    }
    public static void main(String[] args) {
        try { run(); System.out.println("PASS PostApplyWallpaperProbe: " + checks + " checks"); System.exit(0); }
        catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    private static void run() {
        long[] now = {0}; int[] ids = {0, 62, 68};
        List<Runnable> queue = new ArrayList<>(); List<Bundle> writes = new ArrayList<>();
        PostApplyWallpaper tracker = new PostApplyWallpaper((task, delay) -> queue.add(task),
                () -> now[0], screen -> ids[screen], (method, data) -> writes.add(new Bundle(data)));
        Bundle cover = profile(2, 1, 68, "cover");
        tracker.follow("commit", cover);
        now[0] = 250; queue.remove(0).run();
        equal(0, writes.size(), "Stable ID caused a redundant write");
        ids[2] = 70; now[0] = 500; queue.remove(0).run();
        equal(1, writes.size(), "Late wallpaper ID was not followed");
        equal(70, writes.get(0).getInt("wallpaperId"), "Wrong committed cover ID");
        equal(2, ScreenIdentity.value(writes.get(0)), "Delayed Apply switched screen");
        equal(1, writes.get(0).getInt(LayoutOrientation.FIELD), "Delayed Apply switched orientation");
        equal(true, writes.get(0).getBoolean("depthEnabled"), "Rebind lost depth settings");
        equal(68, cover.getInt("wallpaperId"), "Rebind mutated original snapshot");
        ids[2] = 72; now[0] = 750; queue.remove(0).run();
        equal(72, writes.get(1).getInt("wallpaperId"), "Second native ID update was missed");
        Runnable old = queue.remove(0);
        tracker.follow("commit", profile(2, 1, 72, "new cover"));
        ids[2] = 74; now[0] = 1000; old.run();
        equal(2, writes.size(), "Old request overrode a newer Apply");
        queue.remove(0).run();
        equal("new cover", writes.get(2).getString("edit"), "Latest Apply did not win");
        tracker.follow("commitWidgets", profile(1, 2, 62, "inner widgets"));
        ids[1] = 64;
        now[0] = 1250;
        for (Runnable task : new ArrayList<>(queue)) task.run();
        equal(1, ScreenIdentity.value(writes.get(writes.size()-1)), "Inner request followed cover posture");
        equal(64, writes.get(writes.size()-1).getInt("wallpaperId"), "Inner wallpaper ID lost");
        int count = writes.size();
        now[0] = 10000; ids[1] = 90; ids[2] = 92;
        for (Runnable task : new ArrayList<>(queue)) task.run();
        equal(count, writes.size(), "Expired Apply followed an unrelated wallpaper change");
        queue.clear(); tracker.follow("commitExtras", cover);
        equal(0, queue.size(), "Extras incorrectly followed wallpaper IDs");
        tracker.follow("commit", profile(0, 1, 62, "non-fold"));
        equal(0, queue.size(), "Non-fold path acquired delayed rebinding");
        tracker.follow("commit", profile(2, 1, 92, "unavailable"));
        now[0] = 10250; ids[2] = -1; queue.remove(0).run();
        equal(count, writes.size(), "Unavailable wallpaper ID destroyed a valid profile");
    }
}
