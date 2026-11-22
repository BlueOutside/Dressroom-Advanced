package dev.denver.dressroomadvanced;

import java.util.List;

public final class DiyNowBarLogicTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void verifyDefaultsAndSanitize() {
        long now = 1_700_000_000_000L;
        for (int t = DiyNowBarCard.TYPE_CUSTOM; t <= DiyNowBarCard.TYPE_CONTROLS; t++) {
            DiyNowBarCard c = DiyNowBarCard.defaultForType(t, now);
            check(!c.id.isEmpty(), "Card id must be generated for type " + t);
            check(c.enabled, "New card should default to enabled");
            check(c.type == t, "Card type must match requested type");
            check(!c.title.isEmpty(), "Card title must be non-empty");
            check(c.shortcuts.size() == DiyNowBarCard.MAX_SHORTCUTS, "Shortcuts must pad to 4");
            check(DiyNowBarCard.isDiyKey(c.nowBarKey()), "nowBarKey must carry DIY prefix");
            check(c.notiKey().startsWith("0|" + DiyNowBarCard.PACKAGE_ID + "|"), "notiKey format");
        }

        DiyNowBarCard dirty = new DiyNowBarCard();
        dirty.id = "A!B@C#123";
        dirty.type = 99;
        dirty.title = "   ";
        dirty.iconType = DiyNowBarCard.ICON_SYMBOL;
        dirty.iconValue = "does_not_exist";
        dirty.secondaryIcon = "invalid_sym";
        dirty.accentColor = 0;
        dirty.effect = -5;
        dirty.targetValue = -10;
        dirty.stepValue = 0;
        dirty.currentValue = 999999;
        dirty.statsMask = 0;
        dirty.statsPrimary = DiyNowBarCard.STAT_UPTIME;
        for (int i = 0; i < 10; i++) {
            dirty.checklist.add(new DiyNowBarCard.CheckItem(i == 0 ? "   " : "Item " + i, false));
        }
        dirty.sanitize();
        check("abc123".equals(dirty.id), "ID must be lowercased and stripped: " + dirty.id);
        check(dirty.type == DiyNowBarCard.TYPE_CONTROLS, "Type must clamp to max");
        check("Controls".equals(dirty.title), "Blank title falls back to type name");
        check("sparkles".equals(dirty.iconValue), "Invalid symbol falls back to sparkles");
        check("".equals(dirty.secondaryIcon), "Invalid secondaryIcon clears to empty");
        check(dirty.accentColor == 0xFF3B82F6, "Zero accent falls back to blue");
        check(dirty.effect == DiyNowBarCard.EFFECT_NONE, "Negative effect clamps to NONE");
        dirty.effect = 99;
        dirty.secondaryIcon = "bolt";
        dirty.sanitize();
        check(dirty.effect == DiyNowBarCard.EFFECT_GUIDING_LIGHT, "High effect clamps to GUIDING_LIGHT");
        check("bolt".equals(dirty.secondaryIcon), "Valid secondaryIcon preserved");
        check(dirty.targetValue == 1, "Target clamps to >= 1");
        check(dirty.stepValue == 1, "Step clamps to >= 1");
        check(dirty.currentValue == 10, "Current clamps to target * 10");
        check(dirty.statsMask == (DiyNowBarCard.STAT_BATTERY | DiyNowBarCard.STAT_RAM), "Empty stats mask fallback");
        check(dirty.statsPrimary == DiyNowBarCard.STAT_BATTERY, "Primary falls back to enabled bit");
        check(dirty.checklist.size() == DiyNowBarCard.MAX_CHECKLIST - 1, "Blank item removed and clamped");
    }

    private static void verifyGoalMutations() {
        DiyNowBarCard goal = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_GOAL, 1000L);
        goal.currentValue = 2;
        goal.targetValue = 8;
        goal.stepValue = 2;
        goal.unit = "cups";
        check(goal.goalPercent() == 25, "2/8 should be 25%");
        check("2 / 8 cups \u00B7 25%".equals(goal.goalSummary()), "Summary mismatch: " + goal.goalSummary());

        check(goal.applyMutation(DiyNowBarCard.MUTATE_GOAL_INC, -1, 2000L), "INC should mutate");
        check(goal.currentValue == 4 && goal.goalPercent() == 50, "4/8 should be 50%");

        check(goal.applyMutation(DiyNowBarCard.MUTATE_GOAL_DEC, -1, 2000L), "DEC should mutate");
        check(goal.currentValue == 2, "DEC should subtract step");

        check(goal.applyMutation(DiyNowBarCard.MUTATE_GOAL_RESET, -1, 2000L), "RESET should mutate");
        check(goal.currentValue == 0, "RESET must zero current");
        check(!goal.applyMutation(DiyNowBarCard.MUTATE_GOAL_RESET, -1, 2000L), "Second RESET no-op");
        check(!goal.applyMutation(DiyNowBarCard.MUTATE_GOAL_DEC, -1, 2000L), "DEC at 0 no-op");
    }

    private static void verifyTimerMutations() {
        long t0 = 1_700_000_000_000L;
        DiyNowBarCard cd = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_TIMER, t0);
        check(cd.timerMode == DiyNowBarCard.TIMER_COUNTDOWN, "Default timer mode is countdown");
        check(cd.useLiveChronometer(t0), "1h countdown uses live chronometer");
        check(cd.countdownProgress(t0 + 1_800_000L) == 50, "Halfway countdown progress is 50%");
        check(!cd.useLiveChronometer(t0 + 3_600_001L), "Finished countdown stops live chronometer");

        // Multi-day countdown disables live chronometer (>24h)
        cd.timerTargetAtMs = t0 + 3 * 86_400_000L;
        check(!cd.useLiveChronometer(t0), "3-day countdown must not use 24h chronometer");

        // Finished countdown toggle adds +15m
        cd.timerTargetAtMs = t0 - 1000L;
        check(cd.applyMutation(DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, t0), "Finished countdown toggle extends");
        check(cd.countdownRemainingMs(t0) == 15 * 60_000L, "Extended by 15 minutes");

        // Stopwatch lifecycle
        DiyNowBarCard sw = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_TIMER, t0);
        sw.timerMode = DiyNowBarCard.TIMER_STOPWATCH;
        check(!sw.useLiveChronometer(t0), "Paused stopwatch not live");
        check(sw.applyMutation(DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, t0), "Start stopwatch");
        check(sw.timerRunning && sw.useLiveChronometer(t0), "Running stopwatch is live");
        check(sw.stopwatchElapsedMs(t0 + 5000L) == 5000L, "Elapsed 5s while running");
        check(sw.applyMutation(DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, t0 + 5000L), "Pause stopwatch");
        check(!sw.timerRunning && sw.stopwatchElapsedMs(t0 + 99_000L) == 5000L, "Frozen at 5s while paused");
        check(sw.applyMutation(DiyNowBarCard.MUTATE_TIMER_RESET, -1, t0 + 100_000L), "Reset stopwatch");
        check(sw.stopwatchElapsedMs(t0 + 100_000L) == 0L, "Zero after reset");
    }

    private static void verifyChecklistMutations() {
        DiyNowBarCard cl = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_CHECKLIST, 1000L);
        check(cl.checklist.size() == 3, "Default checklist has 3 items");
        check(cl.checklistDoneCount() == 1, "1 done initially");
        check("Drink water".equals(cl.nextUncheckedText()), "Next unchecked matches");

        check(!cl.applyMutation(DiyNowBarCard.MUTATE_CHECK_TOGGLE, 99, 1000L), "Out-of-bounds ignored");
        check(cl.applyMutation(DiyNowBarCard.MUTATE_CHECK_NEXT, -1, 1000L), "Check next marks second");
        check(cl.checklistDoneCount() == 2, "2 done after check next");
        check(cl.applyMutation(DiyNowBarCard.MUTATE_CHECK_TOGGLE, 2, 1000L), "Toggle last item");
        check("All 3 done".equals(cl.checklistSummary()), "All done summary: " + cl.checklistSummary());
        check(!cl.applyMutation(DiyNowBarCard.MUTATE_CHECK_NEXT, -1, 1000L), "Check next no-op when all done");
        check(cl.applyMutation(DiyNowBarCard.MUTATE_CHECK_CLEAR, -1, 1000L), "Clear unchecks all");
        check(cl.checklistDoneCount() == 0, "Zero done after clear");
    }

    private static void verifyFormattersAndCopy() {
        check("05:09".equals(DiyNowBarCard.formatDuration(309_000L)), "mm:ss format");
        check("2:05:09".equals(DiyNowBarCard.formatDuration(7_509_000L)), "h:mm:ss format");
        check("2d 3h 4m".equals(DiyNowBarCard.formatDuration(2 * 86_400_000L + 3 * 3_600_000L + 4 * 60_000L)), "d h m format");
        check("Done".equals(DiyNowBarCard.formatCountdownBadge(0L)), "Zero countdown badge");
        check("1d 2h left".equals(DiyNowBarCard.formatCountdownBadge(26 * 3_600_000L)), "Day badge");
        check("3h 15m".equals(DiyNowBarCard.formatUptime(195 * 60_000L)), "Uptime format");
        check("4.0 GB".equals(DiyNowBarCard.formatBytesGb(4L * 1024 * 1024 * 1024)), "GB format");
        check(DiyNowBarCard.parseHexColor("#3b82f6", 0) == 0xFF3B82F6, "Hex 6-digit parse");
        check(DiyNowBarCard.parseHexColor("bad", 0xFF112233) == 0xFF112233, "Hex fallback");
        check("#3B82F6".equals(DiyNowBarCard.formatHexColor(0xFF3B82F6)), "Hex format");
        check(
                List.of(DiyNowBarCard.STAT_BATTERY, DiyNowBarCard.STAT_NETWORK)
                        .equals(
                                DiyNowBarCard.enabledStats(
                                        DiyNowBarCard.STAT_BATTERY | DiyNowBarCard.STAT_NETWORK)),
                "Enabled stats order");

        DiyNowBarCard original = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_CHECKLIST, 1000L);
        DiyNowBarCard clone = original.copy();
        clone.checklist.get(0).text = "Mutated";
        check(!"Mutated".equals(original.checklist.get(0).text), "Deep copy must isolate checklist");

        String formatted =
                DiyNowBarCard.formatStatsTemplate(
                        "S26 \u00B7 {bat} {TEMP} {watts} {ram}/{ram_pct} {storage} {net} {uptime}",
                        87,
                        31.5f,
                        -14.2f,
                        6L * 1024 * 1024 * 1024,
                        12L * 1024 * 1024 * 1024,
                        140L * 1024 * 1024 * 1024,
                        256L * 1024 * 1024 * 1024,
                        "Wi-Fi",
                        95 * 60_000L);
        check(
                "S26 \u00B7 87% 31.5\u00B0C 14.2W 6.0 GB/50% 140 GB Wi-Fi 1h 35m".equals(formatted),
                "Stats template mismatch: " + formatted);
        check(
                "Plain telemetry".equals(
                        DiyNowBarCard.formatStatsTemplate(
                                "Plain telemetry", 50, 0f, 0f, 0, 0, 0, 0, "Offline", 0)),
                "Plain text template unchanged");
    }

    private static void verifyStackOrder() {
        DiyNowBarStackOrder.LiveEntry diy =
                DiyNowBarStackOrder.classifyItem(
                        "0|dev.denver.dressroomadvanced|9001|abc123",
                        "dressroom_diy_abc123",
                        "dev.denver.dressroomadvanced",
                        0,
                        false,
                        "My Card",
                        "Hello",
                        "",
                        "",
                        "",
                        "",
                        0xFF3B82F6);
        check("diy:abc123".equals(diy.slotId), "DIY slot classification: " + diy.slotId);
        check(DiyNowBarStackOrder.KIND_DIY.equals(diy.kind), "DIY kind");

        DiyNowBarStackOrder.LiveEntry media =
                DiyNowBarStackOrder.classifyItem(
                        "0|servicebox_music|1|",
                        "media_key",
                        "com.spotify.music",
                        DiyNowBarStackOrder.STYLE_MEDIA,
                        false,
                        "",
                        "",
                        "Starboy",
                        "The Weeknd",
                        "Spotify",
                        "com.spotify.music",
                        0);
        check(DiyNowBarStackOrder.SLOT_MEDIA.equals(media.slotId), "Media slot");
        check("Starboy \u00B7 The Weeknd".equals(media.subtitle), "Media subtitle: " + media.subtitle);

        DiyNowBarStackOrder.LiveEntry dnd =
                DiyNowBarStackOrder.classifyItem(
                        "0|android|1|noti_DoNotDisturb",
                        "dnd_key",
                        "android",
                        0,
                        false,
                        "Do Not Disturb",
                        "On",
                        "",
                        "",
                        "",
                        "",
                        0);
        check(DiyNowBarStackOrder.SLOT_DND.equals(dnd.slotId), "DND slot: " + dnd.slotId);

        DiyNowBarStackOrder.LiveEntry mode =
                DiyNowBarStackOrder.classifyItem(
                        "",
                        "mode_sleep",
                        "com.samsung.android.app.routines",
                        DiyNowBarStackOrder.STYLE_MODE,
                        false,
                        "Sleep",
                        "",
                        "",
                        "",
                        "",
                        "",
                        0);
        check(DiyNowBarStackOrder.SLOT_MODE.equals(mode.slotId), "Mode slot");

        DiyNowBarStackOrder.LiveEntry charging =
                DiyNowBarStackOrder.classifyItem(
                        "",
                        "charging",
                        "com.android.systemui",
                        DiyNowBarStackOrder.STYLE_CHARGING_VI,
                        false,
                        "85%",
                        "",
                        "",
                        "",
                        "",
                        "",
                        0);
        check(DiyNowBarStackOrder.SLOT_CHARGING.equals(charging.slotId), "Charging slot");

        DiyNowBarStackOrder.LiveEntry uber =
                DiyNowBarStackOrder.classifyItem(
                        "0|com.ubercab|7|",
                        "uber_trip",
                        "com.ubercab",
                        0,
                        false,
                        "Arriving in 3 min",
                        "Toyota Prius",
                        "",
                        "",
                        "",
                        "",
                        0);
        check("pkg:com.ubercab".equals(uber.slotId), "3P app slot: " + uber.slotId);

        // Stable priority sorting
        List<String> current =
                List.of("sys:dnd", "pkg:com.ubercab", "sys:media", "diy:abc123", "pkg:com.unknown");
        List<String> preferred = List.of("diy:abc123", "sys:media", "sys:dnd");
        int[] perm = DiyNowBarStackOrder.sortIndices(current, preferred);
        check(!DiyNowBarStackOrder.isIdentityOrder(perm), "Non-identity sort expected");
        check(perm[0] == 3 && perm[1] == 2 && perm[2] == 0, "Preferred slots ordered first");
        check(perm[3] == 1 && perm[4] == 4, "Unranked slots preserve relative order");

        // Order-independent multiset signature ignores manual swipes
        String sigA = DiyNowBarStackOrder.multisetSignature(List.of("diy:a", "sys:media", "sys:dnd"));
        String sigB = DiyNowBarStackOrder.multisetSignature(List.of("sys:media", "sys:dnd", "diy:a"));
        String sigC = DiyNowBarStackOrder.multisetSignature(List.of("diy:a", "sys:media"));
        check(sigA.equals(sigB), "Swiped stack keeps identical multiset signature");
        check(!sigA.equals(sigC), "Removed card changes multiset signature");

        // Display list merges live cards, enabled DIY cards, and built-in cards cleanly
        DiyNowBarCard c1 = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_CUSTOM, 1000L);
        c1.id = "abc123";
        c1.title = "S26 Ultra";
        DiyNowBarStackOrder.Config cfg = new DiyNowBarStackOrder.Config();
        cfg.enabled = true;
        cfg.order.add(DiyNowBarStackOrder.SLOT_DND);
        cfg.order.add("diy:abc123");
        List<DiyNowBarStackOrder.LiveEntry> display =
                DiyNowBarStackOrder.buildDisplayList(cfg, List.of(media, dnd, uber), List.of(c1));
        check(DiyNowBarStackOrder.SLOT_DND.equals(display.get(0).slotId) && display.get(0).live, "DND first & live");
        check("diy:abc123".equals(display.get(1).slotId) && "S26 Ultra".equals(display.get(1).label), "DIY second");
        check(
                display.stream().anyMatch(e -> "pkg:com.ubercab".equals(e.slotId) && e.live),
                "Live 3P app included in display list");
        check(
                display.stream().anyMatch(e -> DiyNowBarStackOrder.SLOT_MEDIA.equals(e.slotId) && e.live),
                "Live media included in display list");

        // Scrapped Native Battery & Contact Info stack classification
        DiyNowBarStackOrder.LiveEntry nativeBat =
                DiyNowBarStackOrder.classifyItem(
                        "",
                        DiyNowBarLabs.KEY_NATIVE_BATTERY,
                        "com.samsung.android.app.aodservice",
                        DiyNowBarStackOrder.STYLE_BATTERY,
                        false,
                        "battery_plugged_in",
                        "88%",
                        "",
                        "",
                        "",
                        "",
                        0);
        check(
                DiyNowBarStackOrder.SLOT_NATIVE_BATTERY.equals(nativeBat.slotId),
                "Native Battery slot: " + nativeBat.slotId);

        DiyNowBarStackOrder.LiveEntry contact =
                DiyNowBarStackOrder.classifyItem(
                        "",
                        DiyNowBarLabs.KEY_NATIVE_CONTACT,
                        "com.samsung.android.app.aodservice",
                        DiyNowBarStackOrder.STYLE_CONTACT_INFO,
                        false,
                        "Contact Info",
                        "Owner",
                        "",
                        "",
                        "",
                        "",
                        0);
        check(
                DiyNowBarStackOrder.SLOT_CONTACT_INFO.equals(contact.slotId),
                "Native Contact Info slot: " + contact.slotId);
    }

    private static void verifyLabsAndExtraFlags() {
        DiyNowBarLabs labs = new DiyNowBarLabs();
        check(!labs.nativeBatteryCard && !labs.nativeContactCard, "Labs cards default off");
        check(labs.longPressOpensStudio, "Long-press Studio defaults on");
        labs.nativeBatteryCard = true;
        labs.unlockGoogleCommute = true;
        labs.globalCardShader = 999; // invalid
        labs.globalColor1 = 0;
        labs.sanitize();
        check(labs.globalCardShader == DiyNowBarCard.EFFECT_NONE, "Invalid global shader resets to NONE");
        check(labs.globalColor1 == 0xFF4E8CFF, "Zero global color falls back to Galaxy blue");

        labs.globalCardShader = DiyNowBarCard.EFFECT_AI_MESH_WARP;
        labs.sanitize();
        check(labs.globalCardShader == DiyNowBarCard.EFFECT_AI_MESH_WARP, "Valid global shader kept");
        check(
                "AI Mesh Warp (MVC)".equals(DiyNowBarLabs.globalShaderLabel(labs.globalCardShader)),
                "Global shader label");

        DiyNowBarLabs copy = labs.copy();
        check(labs.sameContent(copy), "Copy matches original");
        copy.unlockGoogleFinance = true;
        check(!labs.sameContent(copy), "Content diff detected");
        check(DiyNowBarLabs.isNativeLabKey(DiyNowBarLabs.KEY_NATIVE_BATTERY), "Native battery lab key");
        check(DiyNowBarLabs.isNativeLabKey(DiyNowBarLabs.KEY_NATIVE_CONTACT), "Native contact lab key");
        check(!DiyNowBarLabs.isNativeLabKey("dressroom_diy_123"), "DIY key is not a native lab key");

        DiyNowBarCard card = DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_TIMER, 1000L);
        card.bigChronometer = true;
        card.ignoreBouncer = true;
        card.fullscreenExpand = true;
        card.liveExpandedView = false;
        card.secondaryIcon = "flame";
        DiyNowBarCard clone = card.copy();
        check(
                clone.bigChronometer
                        && clone.ignoreBouncer
                        && clone.fullscreenExpand
                        && !clone.liveExpandedView
                        && "flame".equals(clone.secondaryIcon),
                "All new extra flags preserved on copy");
    }

    public static void main(String[] args) {
        verifyDefaultsAndSanitize();
        verifyGoalMutations();
        verifyTimerMutations();
        verifyChecklistMutations();
        verifyFormattersAndCopy();
        verifyStackOrder();
        verifyLabsAndExtraFlags();
        System.out.println("DiyNowBar logic tests: " + checks + " checks passed");
    }
}
