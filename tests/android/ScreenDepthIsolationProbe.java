package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** In-memory screen switches and Apply batches, with no live profile writes. */
public final class ScreenDepthIsolationProbe {
    private static int checks;
    private static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(message);
        checks++;
    }
    private static Bundle profile(int screen, int orientation) {
        Bundle b = new Bundle();
        b.putInt(ScreenIdentity.FIELD, screen);
        b.putInt(LayoutOrientation.FIELD, orientation);
        b.putBoolean("depthEnabled", true);
        b.putBoolean("depthOwnsManual", true);
        b.putBoolean("manual", true);
        b.putFloatArray("manualHeights", new float[] {1.4f});
        b.putByteArray("depthMask", new byte[] {1, 2});
        return b;
    }
    public static void main(String[] args) {
        try { run(); System.out.println("Screen depth/source isolation: " + checks + " checks passed"); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }
    private static void run() throws Exception {
        EditorDepthSources sources = new EditorDepthSources();
        var inner = sources.screen(1);
        var cover = sources.screen(2);
        equal(false, inner.source.observe("inner-image"), "Initial inner source considered a replacement");
        equal(false, cover.source.observe("cover-image"), "First cover read compared with inner source");
        inner.known = inner.image = cover.known = cover.image = true;
        Bundle mainPortrait = profile(1, 1), mainLandscape = profile(1, 2), sub = profile(2, 1);
        var drafts = List.of(mainPortrait, mainLandscape, sub);
        equal(true, cover.source.observe("new-cover-image"), "Cover replacement not detected");
        cover.invalidate();
        equal(false, inner.reset, "Cover reset flag leaked to inner screen");
        equal(0L, inner.revision, "Cover invalidation advanced inner source revision");
        equal(true, cover.reset, "Cover replacement did not set its reset flag");
        EditorDepthSources.forScreen(drafts, 2, DepthSafety::disable);
        equal(true, mainPortrait.getBoolean("depthEnabled"), "Cover replacement disabled inner portrait depth");
        equal(true, mainLandscape.getBoolean("manual"), "Cover replacement removed inner landscape stretch");
        equal(true, mainPortrait.containsKey("depthMask"), "Inner mask was erased");
        equal(false, sub.getBoolean("manual"), "Replaced cover retained depth-owned stretch");
        equal(false, sub.containsKey("depthMask"), "Replaced cover retained old mask");
        equal(false, inner.source.observe("inner-image"), "Returning to inner screen looks like replacement");
        Bundle batch = new Bundle();
        batch.putParcelableArrayList("layoutProfiles", new ArrayList<>(drafts));
        sources.stamp(batch);
        equal(inner.imageKey(), mainPortrait.getString("depthImageKey"), "Inner portrait stamped from cover source");
        equal(inner.imageKey(), mainLandscape.getString("depthImageKey"), "Inner landscape source was overwritten");
        equal(cover.imageKey(), sub.getString("depthImageKey"), "Cover source not stamped");
        equal(false, inner.imageKey().equals(cover.imageKey()), "Test uses identical source identities");
        cover.known = false;
        sub.putString("depthImageKey", "frozen-cover-source");
        sources.stamp(batch);
        equal("frozen-cover-source", sub.getString("depthImageKey"), "Temporary source failure overwrote frozen identity");
        Bundle unknown = profile(0, 1); unknown.putString("depthImageKey", "legacy-source");
        sources.stamp(unknown);
        equal("legacy-source", unknown.getString("depthImageKey"), "Missing source replaced legacy profile identity");
        WidgetDepthPolicies policies = new WidgetDepthPolicies();
        mainPortrait.putInt("depthLayouts", 1); mainPortrait.putInt("depth", 1);
        mainLandscape.putInt("depthLayouts", 3); mainLandscape.putInt("depth", 1);
        sub.putInt("depthLayouts", 2); sub.putInt("depth", 0);
        policies.adopt(mainPortrait); policies.adopt(mainLandscape); policies.adopt(sub);
        for (Bundle value : List.of(sub, mainLandscape, mainPortrait)) policies.overlay(value);
        equal(3, mainPortrait.getInt("depthLayouts"), "Stale inner portrait policy overwrote newer landscape selection");
        equal(3, mainLandscape.getInt("depthLayouts"), "Latest inner policy changed");
        equal(2, sub.getInt("depthLayouts"), "Inner policy leaked to cover");
        policies.clearDepth(2);
        policies.overlay(mainPortrait);
        equal(1, mainPortrait.getInt("depth"), "Clearing cover depth reset inner shared policy");
        mainLandscape.putInt("depthLayouts", 0); mainLandscape.putInt("depth", 2); policies.adopt(mainLandscape);
        mainPortrait.putInt("depth", 1); policies.overlay(mainPortrait);
        equal(0, mainPortrait.getInt("depthLayouts"), "Separate-per-orientation choice was not normalized");
        equal(1, mainPortrait.getInt("depth"), "Separate policy overwrote orientation-specific depth choice");
        EditorWidgets widgetEditor = new EditorWidgets(null);
        @SuppressWarnings("unchecked") var widgetDrafts = (java.util.Map<String, Bundle>) Reflect.field(widgetEditor, "layoutDrafts");
        Bundle mainWidget = profile(1, 1), coverWidget = profile(2, 1);
        mainWidget.putInt("depth", 1); coverWidget.putInt("depth", 1);
        widgetDrafts.put("inner", mainWidget); widgetDrafts.put("cover", coverWidget);
        widgetEditor.clearDepth(2);
        equal(true, mainWidget.containsKey("depthMask"), "Cover invalidation erased inner widget mask");
        equal(1, mainWidget.getInt("depth"), "Cover invalidation disabled inner widget depth");
        equal(false, coverWidget.containsKey("depthMask"), "Cover widget retained stale subject mask");
        equal(2, coverWidget.getInt("depth"), "Cover widget did not suspend replaced-image depth");
        policies.clear(); sources.clear();
        equal(false, policies.contains(1), "New editor session retained old depth policy");
        equal(false, sources.screen(1).known, "New editor session retained old wallpaper source");
    }
}
