package dev.denver.dressroomadvanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pure-Java model, formatting, and mutation logic for DIY Now Bar cards. */
final class DiyNowBarCard {
    static final String KEY_PREFIX = "dressroom_diy_";
    static final String PACKAGE_ID = "dev.denver.dressroomadvanced";
    static final int MAX_CARDS = 8;
    static final int MAX_CHECKLIST = 6;
    static final int MAX_SHORTCUTS = 4;

    static final int TYPE_CUSTOM = 0;
    static final int TYPE_TIMER = 1;
    static final int TYPE_GOAL = 2;
    static final int TYPE_STATS = 3;
    static final int TYPE_LAUNCHER = 4;
    static final int TYPE_CHECKLIST = 5;
    static final int TYPE_CONTROLS = 6;

    static final int ICON_SYMBOL = 0;
    static final int ICON_EMOJI = 1;
    static final int ICON_APP = 2;
    static final int ICON_PHOTO = 3;

    static final int SHAPE_CIRCLE = 0;
    static final int SHAPE_SQUIRCLE = 1;

    static final int EFFECT_NONE = 0;
    static final int EFFECT_RADIAL_GLOW = 1;
    static final int EFFECT_AI_PRESENCE = 2;
    static final int EFFECT_OUTER_GLOW = 3;
    static final int EFFECT_GRADIENT_FILL = 4;
    static final int EFFECT_AI_MESH_WARP = 5;
    static final int EFFECT_GUIDING_LIGHT = 6;

    static final int ACTION_NONE = 0;
    static final int ACTION_AUTO = 1;
    static final int ACTION_OPEN_APP = 2;
    static final int ACTION_OPEN_URL = 3;
    static final int ACTION_FLASHLIGHT = 4;
    static final int ACTION_OPEN_EDITOR = 5;

    static final int TIMER_COUNTDOWN = 0;
    static final int TIMER_STOPWATCH = 1;

    static final int STAT_BATTERY = 1;
    static final int STAT_RAM = 2;
    static final int STAT_STORAGE = 4;
    static final int STAT_NETWORK = 8;
    static final int STAT_UPTIME = 16;
    static final int STAT_ALL = STAT_BATTERY | STAT_RAM | STAT_STORAGE | STAT_NETWORK | STAT_UPTIME;

    static final String[] STAT_TOKENS = {
        "{bat}", "{temp}", "{watts}", "{ram}", "{ram_pct}", "{storage}", "{net}", "{uptime}"
    };

    static final String MUTATE_GOAL_INC = "goal_inc";
    static final String MUTATE_GOAL_DEC = "goal_dec";
    static final String MUTATE_GOAL_RESET = "goal_reset";
    static final String MUTATE_TIMER_TOGGLE = "timer_toggle";
    static final String MUTATE_TIMER_RESET = "timer_reset";
    static final String MUTATE_CHECK_TOGGLE = "check_toggle";
    static final String MUTATE_CHECK_NEXT = "check_next";
    static final String MUTATE_CHECK_CLEAR = "check_clear";

    static final String EXTRA_EFFECT = "dressroom_diy_effect";
    static final String EXTRA_COLOR_1 = "dressroom_diy_color1";
    static final String EXTRA_COLOR_2 = "dressroom_diy_color2";
    static final String EXTRA_ACCENT = "dressroom_diy_accent";
    static final String EXTRA_EXPANDABLE = "dressroom_diy_expandable";
    static final String EXTRA_ICON_SHAPE = "dressroom_diy_icon_shape";
    static final String EXTRA_ICON_TYPE = "dressroom_diy_icon_type";
    static final String EXTRA_ICON_VALUE = "dressroom_diy_icon_value";
    static final String EXTRA_CARD_ID = "dressroom_diy_card_id";
    static final String EXTRA_CARD_TYPE = "dressroom_diy_card_type";
    static final String EXTRA_LIVE_VIEW = "dressroom_diy_live_view";
    static final String EXTRA_IGNORE_BOUNCER = "dressroom_diy_ignore_bouncer";
    static final String EXTRA_FULLSCREEN = "dressroom_diy_fullscreen";
    static final String EXTRA_STATS_MASK = "dressroom_diy_stats_mask";
    static final String EXTRA_STATS_PRIMARY = "dressroom_diy_stats_primary";
    static final String EXTRA_CARD_TITLE = "dressroom_diy_card_title";
    static final String EXTRA_CARD_SUBTITLE = "dressroom_diy_card_subtitle";

    static final String[] TYPE_NAMES = {
        "Custom", "Timer", "Goal", "Stats", "Launcher", "Checklist", "Controls"
    };

    static final String[] EFFECT_NAMES = {
        "Clean Glass",
        "Radial Glow",
        "AI Presence (Snake)",
        "Outer Glow Ring",
        "Gradient Fill",
        "AI Border Travel",
        "Guiding Light"
    };

    static final String[] SYMBOL_KEYS = {
        "sparkles", "star", "heart", "bolt", "flame", "clock",
        "target", "check", "music", "coffee", "dumbbell", "book",
        "rocket", "calendar", "water", "battery", "cpu", "grid",
        "note", "pin", "moon", "sun", "shield", "bell", "sliders"
    };

    static final int[] ACCENT_PALETTE = {
        0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFFEF4444,
        0xFFF97316, 0xFFEAB308, 0xFF10B981, 0xFF06B6D4,
        0xFF6366F1, 0xFF14B8A6, 0xFFF43F5E, 0xFF64748B
    };

    static final ShaderPreset[] SHADER_PRESETS = {
        new ShaderPreset("Galaxy AI", 0xFF4E8CFF, 0xFFA86BFF),
        new ShaderPreset("Cyber Neon", 0xFF00E5FF, 0xFFFF3D9A),
        new ShaderPreset("Sunset", 0xFFFF6B4A, 0xFFFFB84D),
        new ShaderPreset("Aurora", 0xFF2EE59D, 0xFF4C8DFF),
        new ShaderPreset("Emerald", 0xFF10B981, 0xFF22D3EE),
        new ShaderPreset("Rose Gold", 0xFFF472B6, 0xFFFBBF24)
    };

    static final class ShaderPreset {
        final String name;
        final int color1;
        final int color2;

        ShaderPreset(String name, int color1, int color2) {
            this.name = name;
            this.color1 = color1;
            this.color2 = color2;
        }
    }

    static final class CheckItem {
        String text;
        boolean done;

        CheckItem(String text, boolean done) {
            this.text = text == null ? "" : text;
            this.done = done;
        }

        CheckItem copy() {
            return new CheckItem(text, done);
        }
    }

    static final class ShortcutSlot {
        int action;
        String label;
        String target;

        ShortcutSlot(int action, String label, String target) {
            this.action = action;
            this.label = label == null ? "" : label;
            this.target = target == null ? "" : target;
        }

        ShortcutSlot copy() {
            return new ShortcutSlot(action, label, target);
        }
    }

    String id = "";
    boolean enabled = true;
    int type = TYPE_CUSTOM;
    String title = "My Now Bar";
    String subtitle = "";
    String body = "";

    int iconType = ICON_SYMBOL;
    String iconValue = "sparkles";
    int iconShape = SHAPE_SQUIRCLE;
    int accentColor = 0xFF3B82F6;

    int effect = EFFECT_RADIAL_GLOW;
    int effectColor1 = 0xFF4E8CFF;
    int effectColor2 = 0xFFA86BFF;

    boolean expandable = true;
    boolean hideOnLock = false;
    boolean bigChronometer = false;
    boolean ignoreBouncer = false;
    boolean fullscreenExpand = false;
    boolean liveExpandedView = true;
    String secondaryIcon = "";

    int quickAction = ACTION_AUTO;
    String quickActionTarget = "";
    int tapAction = ACTION_NONE;
    String tapActionTarget = "";

    // Custom optional progress & Goal tracker fields
    boolean showProgress = false;
    String progressLabel = "";
    int currentValue = 0;
    int targetValue = 10;
    int stepValue = 1;
    String unit = "";

    // Timer / Countdown / Stopwatch fields
    int timerMode = TIMER_COUNTDOWN;
    long timerCreatedAtMs = 0L;
    long timerTargetAtMs = 0L;
    boolean timerRunning = false;
    long timerStartedAtMs = 0L;
    long timerAccumulatedMs = 0L;

    // System stats fields
    int statsMask = STAT_BATTERY | STAT_RAM | STAT_STORAGE | STAT_NETWORK;
    int statsPrimary = STAT_BATTERY;
    String statsFormat = "";

    // Checklist items & Quick launcher slots
    final List<CheckItem> checklist = new ArrayList<>();
    final List<ShortcutSlot> shortcuts = new ArrayList<>();

    static DiyNowBarCard defaultForType(int type, long nowEpochMs) {
        DiyNowBarCard c = new DiyNowBarCard();
        c.id = generateId();
        c.enabled = true;
        c.type = clamp(type, TYPE_CUSTOM, TYPE_CONTROLS);
        c.iconShape = SHAPE_SQUIRCLE;
        c.expandable = true;
        c.liveExpandedView = true;
        c.quickAction = ACTION_AUTO;
        switch (c.type) {
            case TYPE_TIMER -> {
                c.title = "Countdown";
                c.subtitle = "Event timer";
                c.iconValue = "clock";
                c.accentColor = 0xFF8B5CF6;
                c.effect = EFFECT_AI_PRESENCE;
                c.effectColor1 = 0xFF4E8CFF;
                c.effectColor2 = 0xFFA86BFF;
                c.timerMode = TIMER_COUNTDOWN;
                c.timerCreatedAtMs = nowEpochMs;
                c.timerTargetAtMs = nowEpochMs + 3_600_000L;
            }
            case TYPE_GOAL -> {
                c.title = "Water Intake";
                c.subtitle = "Daily goal";
                c.iconValue = "water";
                c.accentColor = 0xFF06B6D4;
                c.effect = EFFECT_RADIAL_GLOW;
                c.effectColor1 = 0xFF06B6D4;
                c.effectColor2 = 0xFF3B82F6;
                c.showProgress = true;
                c.currentValue = 3;
                c.targetValue = 8;
                c.stepValue = 1;
                c.unit = "glasses";
            }
            case TYPE_STATS -> {
                c.title = "Device Stats";
                c.subtitle = "Live telemetry";
                c.iconValue = "cpu";
                c.accentColor = 0xFF10B981;
                c.effect = EFFECT_OUTER_GLOW;
                c.effectColor1 = 0xFF2EE59D;
                c.effectColor2 = 0xFF4C8DFF;
                c.statsMask = STAT_BATTERY | STAT_RAM | STAT_STORAGE | STAT_NETWORK;
                c.statsPrimary = STAT_BATTERY;
            }
            case TYPE_LAUNCHER -> {
                c.title = "Quick Actions";
                c.subtitle = "Tap to expand";
                c.iconValue = "grid";
                c.accentColor = 0xFFF97316;
                c.effect = EFFECT_RADIAL_GLOW;
                c.effectColor1 = 0xFFFF6B4A;
                c.effectColor2 = 0xFFFFB84D;
                c.shortcuts.add(new ShortcutSlot(ACTION_FLASHLIGHT, "Torch", ""));
                c.shortcuts.add(new ShortcutSlot(ACTION_OPEN_EDITOR, "Studio", ""));
                c.shortcuts.add(new ShortcutSlot(ACTION_NONE, "", ""));
                c.shortcuts.add(new ShortcutSlot(ACTION_NONE, "", ""));
            }
            case TYPE_CHECKLIST -> {
                c.title = "Today's Tasks";
                c.subtitle = "Tap to check off";
                c.iconValue = "check";
                c.accentColor = 0xFFEC4899;
                c.effect = EFFECT_GRADIENT_FILL;
                c.effectColor1 = 0xFFEC4899;
                c.effectColor2 = 0xFF8B5CF6;
                c.checklist.add(new CheckItem("Morning stretch", true));
                c.checklist.add(new CheckItem("Drink water", false));
                c.checklist.add(new CheckItem("Reply to messages", false));
            }
            case TYPE_CONTROLS -> {
                c.title = "Live Controls";
                c.subtitle = "Brightness & Volume";
                c.iconValue = "sliders";
                c.accentColor = 0xFF06B6D4;
                c.effect = EFFECT_GUIDING_LIGHT;
                c.effectColor1 = 0xFF00E5FF;
                c.effectColor2 = 0xFF8B5CF6;
                c.ignoreBouncer = true;
            }
            default -> {
                c.title = "My Now Bar";
                c.subtitle = "Tap to expand";
                c.body = "Customize this card with icons, progress bars, quick actions, and live AGSL shaders.";
                c.iconValue = "sparkles";
                c.accentColor = 0xFF3B82F6;
                c.effect = EFFECT_AI_PRESENCE;
                c.effectColor1 = 0xFF4E8CFF;
                c.effectColor2 = 0xFFA86BFF;
            }
        }
        return c.sanitize();
    }

    static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    DiyNowBarCard copy() {
        DiyNowBarCard c = new DiyNowBarCard();
        c.id = id;
        c.enabled = enabled;
        c.type = type;
        c.title = title;
        c.subtitle = subtitle;
        c.body = body;
        c.iconType = iconType;
        c.iconValue = iconValue;
        c.iconShape = iconShape;
        c.accentColor = accentColor;
        c.effect = effect;
        c.effectColor1 = effectColor1;
        c.effectColor2 = effectColor2;
        c.expandable = expandable;
        c.hideOnLock = hideOnLock;
        c.bigChronometer = bigChronometer;
        c.ignoreBouncer = ignoreBouncer;
        c.fullscreenExpand = fullscreenExpand;
        c.liveExpandedView = liveExpandedView;
        c.secondaryIcon = secondaryIcon;
        c.quickAction = quickAction;
        c.quickActionTarget = quickActionTarget;
        c.tapAction = tapAction;
        c.tapActionTarget = tapActionTarget;
        c.showProgress = showProgress;
        c.progressLabel = progressLabel;
        c.currentValue = currentValue;
        c.targetValue = targetValue;
        c.stepValue = stepValue;
        c.unit = unit;
        c.timerMode = timerMode;
        c.timerCreatedAtMs = timerCreatedAtMs;
        c.timerTargetAtMs = timerTargetAtMs;
        c.timerRunning = timerRunning;
        c.timerStartedAtMs = timerStartedAtMs;
        c.timerAccumulatedMs = timerAccumulatedMs;
        c.statsMask = statsMask;
        c.statsPrimary = statsPrimary;
        c.statsFormat = statsFormat;
        for (CheckItem item : checklist) c.checklist.add(item.copy());
        for (ShortcutSlot slot : shortcuts) c.shortcuts.add(slot.copy());
        return c;
    }

    DiyNowBarCard sanitize() {
        id = cleanId(id);
        if (id.isEmpty()) id = generateId();
        type = clamp(type, TYPE_CUSTOM, TYPE_CONTROLS);
        title = cleanText(title, 60);
        if (title.isEmpty()) title = TYPE_NAMES[type];
        subtitle = cleanText(subtitle, 80);
        body = cleanMultiline(body, 280);
        iconType = clamp(iconType, ICON_SYMBOL, ICON_PHOTO);
        iconValue = cleanText(iconValue, 120);
        if (iconType == ICON_SYMBOL && !isValidSymbol(iconValue)) iconValue = SYMBOL_KEYS[0];
        if (iconType == ICON_EMOJI && iconValue.isEmpty()) iconValue = "\u2728";
        iconShape = clamp(iconShape, SHAPE_CIRCLE, SHAPE_SQUIRCLE);
        accentColor = opaque(accentColor, 0xFF3B82F6);
        effect = clamp(effect, EFFECT_NONE, EFFECT_GUIDING_LIGHT);
        effectColor1 = opaque(effectColor1, accentColor);
        effectColor2 = opaque(effectColor2, 0xFFA86BFF);
        secondaryIcon = cleanText(secondaryIcon, 24);
        if (!secondaryIcon.isEmpty() && !isValidSymbol(secondaryIcon)) secondaryIcon = "";
        quickAction = clamp(quickAction, ACTION_NONE, ACTION_OPEN_EDITOR);
        quickActionTarget = cleanText(quickActionTarget, 240);
        tapAction = clamp(tapAction, ACTION_NONE, ACTION_OPEN_EDITOR);
        tapActionTarget = cleanText(tapActionTarget, 240);
        progressLabel = cleanText(progressLabel, 40);
        targetValue = clamp(targetValue, 1, 100_000);
        stepValue = clamp(stepValue, 1, 10_000);
        currentValue = clamp(currentValue, 0, targetValue * 10);
        unit = cleanText(unit, 20);
        timerMode = clamp(timerMode, TIMER_COUNTDOWN, TIMER_STOPWATCH);
        timerAccumulatedMs = Math.max(0L, timerAccumulatedMs);
        statsMask &= STAT_ALL;
        if (statsMask == 0) statsMask = STAT_BATTERY | STAT_RAM;
        if ((statsMask & statsPrimary) == 0) {
            statsPrimary = firstStatBit(statsMask);
        }
        statsFormat = cleanText(statsFormat, 80);
        while (checklist.size() > MAX_CHECKLIST) checklist.remove(checklist.size() - 1);
        for (int i = checklist.size() - 1; i >= 0; i--) {
            CheckItem item = checklist.get(i);
            item.text = cleanText(item.text, 64);
            if (item.text.isEmpty()) checklist.remove(i);
        }
        while (shortcuts.size() > MAX_SHORTCUTS) shortcuts.remove(shortcuts.size() - 1);
        while (shortcuts.size() < MAX_SHORTCUTS) {
            shortcuts.add(new ShortcutSlot(ACTION_NONE, "", ""));
        }
        for (ShortcutSlot slot : shortcuts) {
            slot.action = clamp(slot.action, ACTION_NONE, ACTION_OPEN_EDITOR);
            if (slot.action == ACTION_AUTO) slot.action = ACTION_NONE;
            slot.label = cleanText(slot.label, 24);
            slot.target = cleanText(slot.target, 240);
        }
        return this;
    }

    String nowBarKey() {
        return KEY_PREFIX + id;
    }

    int stableNotiId() {
        return 9000 + Math.abs(id.hashCode() % 80000);
    }

    String notiKey() {
        return "0|" + PACKAGE_ID + "|" + stableNotiId() + "|" + id;
    }

    int goalPercent() {
        if (targetValue <= 0) return 0;
        return clamp((int) Math.round((currentValue * 100.0) / targetValue), 0, 100);
    }

    String goalSummary() {
        String base = currentValue + " / " + targetValue;
        if (!unit.isEmpty()) base = base + " " + unit;
        return base + " \u00B7 " + goalPercent() + "%";
    }

    int checklistDoneCount() {
        int done = 0;
        for (CheckItem item : checklist) if (item.done) done++;
        return done;
    }

    String checklistSummary() {
        int total = checklist.size();
        if (total == 0) return "No items yet";
        int done = checklistDoneCount();
        if (done == total) return "All " + total + " done";
        String next = nextUncheckedText();
        return done + "/" + total + (next.isEmpty() ? " done" : " \u00B7 " + next);
    }

    String nextUncheckedText() {
        for (CheckItem item : checklist) {
            if (!item.done && !item.text.isEmpty()) return item.text;
        }
        return "";
    }

    int activeShortcutCount() {
        int count = 0;
        for (ShortcutSlot slot : shortcuts) {
            if (slot.action != ACTION_NONE) count++;
        }
        return count;
    }

    long stopwatchElapsedMs(long nowEpochMs) {
        long elapsed = Math.max(0L, timerAccumulatedMs);
        if (timerRunning && timerStartedAtMs > 0L && nowEpochMs > timerStartedAtMs) {
            elapsed += (nowEpochMs - timerStartedAtMs);
        }
        return elapsed;
    }

    long countdownRemainingMs(long nowEpochMs) {
        return Math.max(0L, timerTargetAtMs - nowEpochMs);
    }

    int countdownProgress(long nowEpochMs) {
        long span = timerTargetAtMs - timerCreatedAtMs;
        if (span <= 0L) return timerTargetAtMs <= nowEpochMs ? 100 : 0;
        long done = nowEpochMs - timerCreatedAtMs;
        return clamp((int) Math.round((done * 100.0) / span), 0, 100);
    }

    boolean useLiveChronometer(long nowEpochMs) {
        if (type != TYPE_TIMER) return false;
        if (timerMode == TIMER_STOPWATCH) return timerRunning;
        long remaining = countdownRemainingMs(nowEpochMs);
        return remaining > 0L && remaining < 86_400_000L;
    }

    boolean applyMutation(String action, int index, long nowEpochMs) {
        if (action == null) return false;
        switch (action) {
            case MUTATE_GOAL_INC -> {
                int next = clamp(currentValue + stepValue, 0, targetValue * 10);
                if (next == currentValue) return false;
                currentValue = next;
                return true;
            }
            case MUTATE_GOAL_DEC -> {
                int next = clamp(currentValue - stepValue, 0, targetValue * 10);
                if (next == currentValue) return false;
                currentValue = next;
                return true;
            }
            case MUTATE_GOAL_RESET -> {
                if (currentValue == 0) return false;
                currentValue = 0;
                return true;
            }
            case MUTATE_TIMER_TOGGLE -> {
                if (timerMode != TIMER_STOPWATCH) {
                    // For countdown, add +15 min if finished or reset start to now
                    if (timerTargetAtMs <= nowEpochMs) {
                        timerCreatedAtMs = nowEpochMs;
                        timerTargetAtMs = nowEpochMs + 15 * 60_000L;
                        return true;
                    }
                    return false;
                }
                if (timerRunning) {
                    timerAccumulatedMs = stopwatchElapsedMs(nowEpochMs);
                    timerRunning = false;
                    timerStartedAtMs = 0L;
                } else {
                    timerRunning = true;
                    timerStartedAtMs = nowEpochMs;
                }
                return true;
            }
            case MUTATE_TIMER_RESET -> {
                if (timerMode == TIMER_STOPWATCH) {
                    boolean changed = timerRunning || timerAccumulatedMs != 0L;
                    timerRunning = false;
                    timerStartedAtMs = 0L;
                    timerAccumulatedMs = 0L;
                    return changed;
                }
                long span = Math.max(60_000L, timerTargetAtMs - timerCreatedAtMs);
                timerCreatedAtMs = nowEpochMs;
                timerTargetAtMs = nowEpochMs + span;
                return true;
            }
            case MUTATE_CHECK_TOGGLE -> {
                if (index < 0 || index >= checklist.size()) return false;
                CheckItem item = checklist.get(index);
                item.done = !item.done;
                return true;
            }
            case MUTATE_CHECK_NEXT -> {
                for (CheckItem item : checklist) {
                    if (!item.done) {
                        item.done = true;
                        return true;
                    }
                }
                return false;
            }
            case MUTATE_CHECK_CLEAR -> {
                boolean removed = false;
                for (CheckItem item : checklist) {
                    if (item.done) {
                        item.done = false;
                        removed = true;
                    }
                }
                return removed;
            }
            default -> {
                return false;
            }
        }
    }

    static String formatDuration(long millis) {
        long totalSec = Math.max(0L, millis / 1000L);
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        if (days > 0) {
            return String.format(Locale.US, "%dd %dh %dm", days, hours, minutes);
        }
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    static String formatCountdownBadge(long remainingMs) {
        if (remainingMs <= 0L) return "Done";
        long totalMin = (remainingMs + 59_999L) / 60_000L;
        long days = totalMin / 1440L;
        long hours = (totalMin % 1440L) / 60L;
        long mins = totalMin % 60L;
        if (days > 0) return days + "d " + hours + "h left";
        if (hours > 0) return hours + "h " + mins + "m left";
        return Math.max(1L, mins) + "m left";
    }

    static String formatUptime(long uptimeMs) {
        long totalMin = Math.max(0L, uptimeMs / 60_000L);
        long days = totalMin / 1440L;
        long hours = (totalMin % 1440L) / 60L;
        long mins = totalMin % 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    static String formatBytesGb(long bytes) {
        double gb = Math.max(0L, bytes) / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 100.0) return String.format(Locale.US, "%.0f GB", gb);
        return String.format(Locale.US, "%.1f GB", gb);
    }

    static int parseHexColor(String input, int fallback) {
        if (input == null) return fallback;
        String s = input.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) {
            try {
                return 0xFF000000 | Integer.parseInt(s, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        if (s.length() == 8) {
            try {
                return (int) Long.parseLong(s, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static String formatHexColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    static boolean isDiyKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    static boolean isValidSymbol(String key) {
        if (key == null) return false;
        for (String symbol : SYMBOL_KEYS) {
            if (symbol.equals(key)) return true;
        }
        return false;
    }

    static List<Integer> enabledStats(int mask) {
        List<Integer> out = new ArrayList<>();
        int[] order = {STAT_BATTERY, STAT_RAM, STAT_STORAGE, STAT_NETWORK, STAT_UPTIME};
        for (int bit : order) {
            if ((mask & bit) != 0) out.add(bit);
        }
        if (out.isEmpty()) out.add(STAT_BATTERY);
        return Collections.unmodifiableList(out);
    }

    static String statName(int bit) {
        return switch (bit) {
            case STAT_BATTERY -> "Battery";
            case STAT_RAM -> "Memory";
            case STAT_STORAGE -> "Storage";
            case STAT_NETWORK -> "Network";
            case STAT_UPTIME -> "Uptime";
            default -> "Stat";
        };
    }

    /** Expands live telemetry placeholders in custom Stats pill/subtitle text. */
    static String formatStatsTemplate(
            String template,
            int batteryPct,
            float batteryTempC,
            float batteryWatts,
            long ramUsedBytes,
            long ramTotalBytes,
            long storageFreeBytes,
            long storageTotalBytes,
            String networkLabel,
            long uptimeMs) {
        if (template == null || template.isEmpty()) return "";
        if (template.indexOf('{') < 0) return template;
        int ramPct =
                ramTotalBytes > 0L
                        ? clamp((int) Math.round(ramUsedBytes * 100.0 / ramTotalBytes), 0, 100)
                        : 0;
        String bat = batteryPct + "%";
        String temp =
                batteryTempC > 0f
                        ? String.format(Locale.US, "%.1f\u00B0C", batteryTempC)
                        : "--\u00B0C";
        String watts = String.format(Locale.US, "%.1fW", Math.abs(batteryWatts));
        String ram = formatBytesGb(ramUsedBytes);
        String ramTotal = formatBytesGb(ramTotalBytes);
        String storage = formatBytesGb(storageFreeBytes);
        String storageTotal = formatBytesGb(storageTotalBytes);
        String net = networkLabel == null || networkLabel.isEmpty() ? "Offline" : networkLabel;
        String up = formatUptime(uptimeMs);

        String out = replaceToken(template, "{bat}", bat);
        out = replaceToken(out, "{temp}", temp);
        out = replaceToken(out, "{watts}", watts);
        out = replaceToken(out, "{ram}", ram);
        out = replaceToken(out, "{ram_pct}", ramPct + "%");
        out = replaceToken(out, "{ram_total}", ramTotal);
        out = replaceToken(out, "{storage}", storage);
        out = replaceToken(out, "{storage_total}", storageTotal);
        out = replaceToken(out, "{net}", net);
        out = replaceToken(out, "{uptime}", up);
        return out;
    }

    private static String replaceToken(String src, String token, String value) {
        String lowerSrc = src.toLowerCase(Locale.US);
        int idx = lowerSrc.indexOf(token);
        if (idx < 0) return src;
        StringBuilder sb = new StringBuilder(src.length() + 16);
        int pos = 0;
        int len = token.length();
        while (idx >= 0) {
            sb.append(src, pos, idx).append(value);
            pos = idx + len;
            idx = lowerSrc.indexOf(token, pos);
        }
        sb.append(src, pos, src.length());
        return sb.toString();
    }

    private static int firstStatBit(int mask) {
        for (int bit : new int[] {STAT_BATTERY, STAT_RAM, STAT_STORAGE, STAT_NETWORK, STAT_UPTIME}) {
            if ((mask & bit) != 0) return bit;
        }
        return STAT_BATTERY;
    }

    private static int opaque(int color, int fallback) {
        if (color == 0) return fallback;
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String cleanId(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < 24; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') {
                out.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                out.append((char) (ch + 32));
            }
        }
        return out.toString();
    }

    private static String cleanText(String raw, int limit) {
        if (raw == null) return "";
        String clean = raw.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return clean.codePointCount(0, clean.length()) <= limit
                ? clean
                : clean.substring(0, clean.offsetByCodePoints(0, limit));
    }

    private static String cleanMultiline(String raw, int limit) {
        if (raw == null) return "";
        String clean = raw.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n').trim();
        return clean.codePointCount(0, clean.length()) <= limit
                ? clean
                : clean.substring(0, clean.offsetByCodePoints(0, limit));
    }
}
