package dev.denver.dressroomadvanced;

import java.util.HashMap;
import java.util.Map;

public final class LifecycleContractTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        SaveSnapshot<Map<String, Integer>> save = new SaveSnapshot<>();
        Map<String, Integer> draft = new HashMap<>();
        draft.put("weather", 1);
        save.begin(Map.copyOf(draft));
        draft.put("weather", 0);
        check(save.complete().get("weather") == 1, "Late draft edits changed the captured native save");
        check(save.complete() == null, "Completion committed twice");
        save.begin(Map.copyOf(draft)); save.cancel();
        check(save.complete() == null, "Canceled editor retained a save");
        save.begin(Map.of("selection", 1)); save.begin(Map.of("selection", 2));
        check(save.complete().get("selection") == 2, "A newer save reused the previous selection");
        check(!SettingsScope.key(10, 2, 803).equals(SettingsScope.key(11, 2, 803)), "Masks leaked across wallpapers");
        check(!SettingsScope.key(10, 2, 803).equals(SettingsScope.key(10, 2, 804)), "Font profiles collided");
        check(!SettingsScope.key(10, 2, 803).equals(SettingsScope.key(10, 3, 803)), "Clock profiles collided");
        check(SettingsScope.canImportLegacy(10, false), "Existing settings could not migrate");
        check(!SettingsScope.canImportLegacy(11, true), "Legacy mask imported on a second wallpaper");
        check(!SettingsScope.canImportLegacy(-1, false), "Unknown wallpaper consumed the legacy migration");
        check(SettingsScope.sceneMatches("a", "a"), "Saved scene did not reopen");
        check(!SettingsScope.sceneMatches("b", "a"), "Wrong scene received saved geometry");
        check(SettingsScope.sceneMatches("", "a"), "Runtime wallpaper lookup could not read the committed profile");
        check(SettingsScope.sceneMatches("a", ""), "Legacy profile failed the first editor load");
        check(SavedStretch.heights(null) == null, "Missing stretch values became default heights");
        check(SavedStretch.heights(new float[]{2f, Float.NaN}) == null, "Invalid saved heights reached the renderer");
        check(SavedStretch.heights(new float[]{2f, 0f}) == null, "Zero segment height reached the renderer");
        check(SavedStretch.heights(new float[]{2f, 1.2f}).equals(java.util.List.of(2f, 1.2f)), "Saved unequal heights changed at runtime handoff");
        System.out.println("Lifecycle contracts: " + checks + " checks passed");
    }
}
