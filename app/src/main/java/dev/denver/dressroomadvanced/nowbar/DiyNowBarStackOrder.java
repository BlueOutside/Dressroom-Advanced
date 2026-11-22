package dev.denver.dressroomadvanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java Now Bar stack classification, live-card merging, and stable priority sorting.
 * Recognizes DIY cards alongside all native Samsung Now Bar cards (Media, DND, Modes, Brief,
 * Charging, Flashlight, Calls, Sports, Clock, and third-party live notifications).
 */
final class DiyNowBarStackOrder {
    static final String PREFIX_DIY = "diy:";
    static final String PREFIX_SYS = "sys:";
    static final String PREFIX_PKG = "pkg:";
    static final String PREFIX_KEY = "key:";

    static final String SLOT_MEDIA = "sys:media";
    static final String SLOT_DND = "sys:dnd";
    static final String SLOT_MODE = "sys:mode";
    static final String SLOT_BRIEF = "sys:brief";
    static final String SLOT_CHARGING = "sys:charging";
    static final String SLOT_NATIVE_BATTERY = "sys:native_battery";
    static final String SLOT_CONTACT_INFO = "sys:contact";
    static final String SLOT_FLASHLIGHT = "sys:flashlight";
    static final String SLOT_CALL = "sys:call";
    static final String SLOT_SPORTS = "sys:sports";
    static final String SLOT_FINANCE = "sys:finance";
    static final String SLOT_COMMUTE = "sys:commute";
    static final String SLOT_CLOCK = "pkg:com.sec.android.app.clockpackage";
    static final String SLOT_RECORDER = "pkg:com.sec.android.app.voicenote";

    static final String KIND_DIY = "diy";
    static final String KIND_MEDIA = "media";
    static final String KIND_DND = "dnd";
    static final String KIND_MODE = "mode";
    static final String KIND_BRIEF = "brief";
    static final String KIND_CHARGING = "charging";
    static final String KIND_CONTACT = "contact";
    static final String KIND_FLASHLIGHT = "flashlight";
    static final String KIND_CALL = "call";
    static final String KIND_SPORTS = "sports";
    static final String KIND_APP = "app";

    // Matches aod.NowBarViewStyle ordinals on One UI 8/9
    static final int STYLE_UNDEFINED = 0;
    static final int STYLE_MEDIA = 1;
    static final int STYLE_MODE = 2;
    static final int STYLE_BRIEFING = 3;
    static final int STYLE_BATTERY = 4;
    static final int STYLE_CONTACT_INFO = 5;
    static final int STYLE_CHARGING_VI = 6;
    static final int STYLE_AI_BRIEF = 7;
    static final int STYLE_NOWBRIEF_LOCK = 8;

    static final int MAX_ORDER_SLOTS = 32;

    static final class Config {
        boolean enabled;
        boolean resetOnScreenOff = true;
        long revision;
        final List<String> order = new ArrayList<>();

        Config copy() {
            Config c = new Config();
            c.enabled = enabled;
            c.resetOnScreenOff = resetOnScreenOff;
            c.revision = revision;
            c.order.addAll(order);
            return c;
        }

        Config sanitize() {
            Set<String> seen = new HashSet<>();
            List<String> clean = new ArrayList<>();
            for (String raw : order) {
                String slot = cleanSlotId(raw);
                if (!slot.isEmpty() && seen.add(slot)) {
                    clean.add(slot);
                    if (clean.size() >= MAX_ORDER_SLOTS) break;
                }
            }
            order.clear();
            order.addAll(clean);
            if (revision < 0L) revision = 0L;
            return this;
        }
    }

    static final class LiveEntry {
        final String slotId;
        final String label;
        final String subtitle;
        final String kind;
        final String packageName;
        final int accentColor;
        final boolean live;

        LiveEntry(
                String slotId,
                String label,
                String subtitle,
                String kind,
                String packageName,
                int accentColor,
                boolean live) {
            this.slotId = cleanSlotId(slotId);
            this.label = cleanLabel(label, 48);
            this.subtitle = cleanLabel(subtitle, 72);
            this.kind = kind == null || kind.isEmpty() ? KIND_APP : kind;
            this.packageName = packageName == null ? "" : packageName.trim();
            this.accentColor = accentColor == 0 ? 0xFF3B82F6 : (0xFF000000 | (accentColor & 0xFFFFFF));
            this.live = live;
        }
    }

    private DiyNowBarStackOrder() {}

    static String diySlotId(String cardId) {
        return cardId == null || cardId.isEmpty() ? "" : PREFIX_DIY + cardId;
    }

    static String diyCardIdFromSlot(String slotId) {
        if (slotId == null || !slotId.startsWith(PREFIX_DIY)) return "";
        return slotId.substring(PREFIX_DIY.length());
    }

    /** Classifies any runtime NowBarItem into a canonical stack slot descriptor. */
    static LiveEntry classifyItem(
            String notiID,
            String nowBarKey,
            String nowBarPackage,
            int viewStyle,
            boolean isCall,
            String primaryInfo,
            String secondaryInfo,
            String mediaSong,
            String mediaArtist,
            String mediaApp,
            String mediaPackage,
            int iconBgColor) {
        String key = safe(nowBarKey);
        String pkg = safe(nowBarPackage);
        String noti = safe(notiID);
        String notiPkg = notiPart(noti, 1);
        String notiTag = notiPart(noti, 3);
        String pri = safe(primaryInfo);
        String sec = safe(secondaryInfo);

        // 1. DIY Now Bar cards
        if (DiyNowBarCard.isDiyKey(key)) {
            String cardId = key.substring(DiyNowBarCard.KEY_PREFIX.length());
            String label = pri.isEmpty() ? "DIY Card" : pri;
            return new LiveEntry(
                    PREFIX_DIY + cardId,
                    label,
                    sec,
                    KIND_DIY,
                    DiyNowBarCard.PACKAGE_ID,
                    normalizeAccent(iconBgColor, 0xFF3B82F6),
                    true);
        }

        // 2. Media Player
        if (viewStyle == STYLE_MEDIA
                || "servicebox_music".equals(notiPkg)
                || "MEDIA_NOWBAR".equals(noti)) {
            String sub = joinNonEmpty(" \u00B7 ", safe(mediaSong), safe(mediaArtist));
            if (sub.isEmpty()) sub = safe(mediaApp);
            if (sub.isEmpty()) sub = pri;
            return new LiveEntry(
                    SLOT_MEDIA,
                    "Media Player",
                    sub,
                    KIND_MEDIA,
                    safe(mediaPackage).isEmpty() ? pkg : safe(mediaPackage),
                    0xFF1DB954,
                    true);
        }

        // 3. Ongoing Call
        if (isCall) {
            return new LiveEntry(
                    SLOT_CALL,
                    "Ongoing Call",
                    joinNonEmpty(" \u00B7 ", pri, sec),
                    KIND_CALL,
                    firstNonEmpty(pkg, notiPkg),
                    0xFF10B981,
                    true);
        }

        // 4. Do Not Disturb
        if ("noti_DoNotDisturb".equals(notiTag) || "do_not_disturb".equals(notiPkg)) {
            return new LiveEntry(
                    SLOT_DND,
                    "Do Not Disturb",
                    firstNonEmpty(sec, pri),
                    KIND_DND,
                    firstNonEmpty(pkg, notiPkg),
                    0xFF6366F1,
                    true);
        }

        // 5. Flashlight
        if ("Flashlight".equals(notiTag) || "flash_light".equals(notiPkg)) {
            return new LiveEntry(
                    SLOT_FLASHLIGHT,
                    "Flashlight",
                    firstNonEmpty(sec, pri),
                    KIND_FLASHLIGHT,
                    firstNonEmpty(pkg, notiPkg),
                    0xFFF59E0B,
                    true);
        }

        // 6. Modes & Routines
        if (viewStyle == STYLE_MODE
                || "com.samsung.android.app.routines".equals(pkg)
                || "com.samsung.android.app.routines".equals(notiPkg)) {
            return new LiveEntry(
                    SLOT_MODE,
                    "Modes & Routines",
                    firstNonEmpty(pri, sec),
                    KIND_MODE,
                    "com.samsung.android.app.routines",
                    0xFF8B5CF6,
                    true);
        }

        // 7. Now Brief / AI Brief
        if (viewStyle == STYLE_AI_BRIEF
                || viewStyle == STYLE_NOWBRIEF_LOCK
                || viewStyle == STYLE_BRIEFING
                || "com.samsung.android.smartsuggestions".equals(pkg)
                || "com.samsung.android.smartsuggestions".equals(notiPkg)
                || pkg.startsWith("com.android.systemui.aibrief")) {
            return new LiveEntry(
                    SLOT_BRIEF,
                    "Now Brief",
                    firstNonEmpty(pri, sec),
                    KIND_BRIEF,
                    "com.samsung.android.smartsuggestions",
                    0xFF4E8CFF,
                    true);
        }

        // 8. Charging / Native Battery / Contact Info
        if (viewStyle == STYLE_BATTERY || DiyNowBarLabs.KEY_NATIVE_BATTERY.equals(key)) {
            return new LiveEntry(
                    SLOT_NATIVE_BATTERY,
                    "Native Battery",
                    "battery_plugged_in".equals(pri) ? sec : firstNonEmpty(pri, sec),
                    KIND_CHARGING,
                    "com.samsung.android.app.aodservice",
                    0xFF22C55E,
                    true);
        }
        if (viewStyle == STYLE_CONTACT_INFO || DiyNowBarLabs.KEY_NATIVE_CONTACT.equals(key)) {
            return new LiveEntry(
                    SLOT_CONTACT_INFO,
                    "Contact Info",
                    firstNonEmpty(pri, sec),
                    KIND_CONTACT,
                    "com.samsung.android.app.aodservice",
                    0xFF3B82F6,
                    true);
        }
        if (viewStyle == STYLE_CHARGING_VI) {
            return new LiveEntry(
                    SLOT_CHARGING,
                    "Charging",
                    firstNonEmpty(pri, sec),
                    KIND_CHARGING,
                    "com.android.systemui",
                    0xFF22C55E,
                    true);
        }

        // 9. Samsung App Search live cards (Sports / Finance / Commute)
        if ("google_sports_primary_info".equals(pri) || "naver_sports".equals(pri)) {
            return new LiveEntry(
                    SLOT_SPORTS, "Live Sports", sec, KIND_SPORTS, firstNonEmpty(pkg, notiPkg), 0xFF06B6D4, true);
        }
        if ("google_finance_primary_info".equals(pri)) {
            return new LiveEntry(
                    SLOT_FINANCE, "Finance", sec, KIND_SPORTS, firstNonEmpty(pkg, notiPkg), 0xFF10B981, true);
        }
        if ("google_commute_primary_info".equals(pri)) {
            return new LiveEntry(
                    SLOT_COMMUTE, "Commute", sec, KIND_SPORTS, firstNonEmpty(pkg, notiPkg), 0xFFF97316, true);
        }

        // 10. Known first-party ongoing apps & any third-party Live Notification app
        String resolvedPkg = firstNonEmpty(pkg, notiPkg);
        if ("com.sec.android.app.clockpackage".equals(resolvedPkg)) {
            return new LiveEntry(
                    SLOT_CLOCK,
                    "Clock / Timer",
                    joinNonEmpty(" \u00B7 ", pri, sec),
                    KIND_APP,
                    resolvedPkg,
                    0xFF8B5CF6,
                    true);
        }
        if ("com.sec.android.app.voicenote".equals(resolvedPkg)) {
            return new LiveEntry(
                    SLOT_RECORDER,
                    "Voice Recorder",
                    joinNonEmpty(" \u00B7 ", pri, sec),
                    KIND_APP,
                    resolvedPkg,
                    0xFFEF4444,
                    true);
        }

        String slotId;
        if (!resolvedPkg.isEmpty()) {
            slotId = PREFIX_PKG + resolvedPkg;
        } else if (!key.isEmpty()) {
            slotId = PREFIX_KEY + key;
        } else {
            slotId = PREFIX_KEY + "style_" + viewStyle;
        }
        String label = !pri.isEmpty() ? pri : friendlyPackageName(resolvedPkg);
        if (label.isEmpty()) label = "Live Card";
        return new LiveEntry(
                slotId,
                label,
                sec,
                KIND_APP,
                resolvedPkg,
                normalizeAccent(iconBgColor, 0xFF64748B),
                true);
    }

    /** Built-in Samsung Now Bar cards always available for priority ordering. */
    static List<LiveEntry> builtinEntries() {
        List<LiveEntry> list = new ArrayList<>();
        list.add(new LiveEntry(SLOT_MEDIA, "Media Player", "", KIND_MEDIA, "", 0xFF1DB954, false));
        list.add(new LiveEntry(SLOT_DND, "Do Not Disturb", "", KIND_DND, "", 0xFF6366F1, false));
        list.add(
                new LiveEntry(
                        SLOT_MODE,
                        "Modes & Routines",
                        "",
                        KIND_MODE,
                        "com.samsung.android.app.routines",
                        0xFF8B5CF6,
                        false));
        list.add(
                new LiveEntry(
                        SLOT_BRIEF,
                        "Now Brief",
                        "",
                        KIND_BRIEF,
                        "com.samsung.android.smartsuggestions",
                        0xFF4E8CFF,
                        false));
        list.add(
                new LiveEntry(
                        SLOT_CHARGING,
                        "Charging",
                        "",
                        KIND_CHARGING,
                        "com.android.systemui",
                        0xFF22C55E,
                        false));
        list.add(
                new LiveEntry(
                        SLOT_CLOCK,
                        "Clock / Timer",
                        "",
                        KIND_APP,
                        "com.sec.android.app.clockpackage",
                        0xFF8B5CF6,
                        false));
        list.add(new LiveEntry(SLOT_FLASHLIGHT, "Flashlight", "", KIND_FLASHLIGHT, "", 0xFFF59E0B, false));
        return list;
    }

    /**
     * Computes a stable index permutation for {@code itemSlotIds} according to {@code preferredOrder}.
     * Items matched in {@code preferredOrder} come first (lowest rule index = top of stack),
     * followed by unranked items in their original relative order.
     */
    static int[] sortIndices(List<String> itemSlotIds, List<String> preferredOrder) {
        int n = itemSlotIds == null ? 0 : itemSlotIds.size();
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        if (n <= 1 || preferredOrder == null || preferredOrder.isEmpty()) return indices;

        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < preferredOrder.size(); i++) {
            String s = preferredOrder.get(i);
            if (s != null && !s.isEmpty() && !rank.containsKey(s)) {
                rank.put(s, i);
            }
        }
        if (rank.isEmpty()) return indices;

        List<Integer> boxed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) boxed.add(i);
        int unranked = preferredOrder.size() + 1000;
        boxed.sort(
                (a, b) -> {
                    int ra = rank.getOrDefault(itemSlotIds.get(a), unranked);
                    int rb = rank.getOrDefault(itemSlotIds.get(b), unranked);
                    if (ra != rb) return Integer.compare(ra, rb);
                    return Integer.compare(a, b);
                });
        for (int i = 0; i < n; i++) indices[i] = boxed.get(i);
        return indices;
    }

    static boolean isIdentityOrder(int[] indices) {
        if (indices == null) return true;
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] != i) return false;
        }
        return true;
    }

    /**
     * Order-independent multiset signature of slot IDs.
     * Unchanged when the user manually swipes/rotates the Now Bar stack; changes whenever any card
     * is added or removed.
     */
    static String multisetSignature(List<String> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(slotIds.size());
        for (String id : slotIds) sorted.add(id == null ? "" : id);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }

    /**
     * Builds the unified draggable pill list for NowBarStudioActivity by combining:
     * 1. Currently live cards detected on SystemUI's Now Bar
     * 2. Enabled DIY cards configured in Studio
     * 3. Built-in Samsung Now Bar cards (Media, DND, Modes, Brief, Charging, Clock, Flashlight)
     * 4. Any previously ordered third-party app slots
     */
    static List<LiveEntry> buildDisplayList(
            Config config, List<LiveEntry> liveEntries, List<DiyNowBarCard> diyCards) {
        Map<String, LiveEntry> pool = new LinkedHashMap<>();
        Map<String, DiyNowBarCard> diyById = new LinkedHashMap<>();
        if (diyCards != null) {
            for (DiyNowBarCard c : diyCards) {
                if (c != null && c.id != null && !c.id.isEmpty()) {
                    diyById.put(c.id, c);
                }
            }
        }

        // First add all currently live cards in their live stack order
        if (liveEntries != null) {
            for (LiveEntry live : liveEntries) {
                if (live == null || live.slotId.isEmpty() || pool.containsKey(live.slotId)) continue;
                String diyId = diyCardIdFromSlot(live.slotId);
                if (!diyId.isEmpty() && diyById.containsKey(diyId)) {
                    DiyNowBarCard card = diyById.get(diyId);
                    pool.put(
                            live.slotId,
                            new LiveEntry(
                                    live.slotId,
                                    card.title,
                                    live.subtitle,
                                    KIND_DIY,
                                    DiyNowBarCard.PACKAGE_ID,
                                    card.accentColor,
                                    true));
                } else {
                    pool.put(live.slotId, live);
                }
            }
        }

        // Ensure all enabled DIY cards appear even before SystemUI reports them
        if (diyCards != null) {
            for (DiyNowBarCard card : diyCards) {
                if (card == null || !card.enabled || card.id == null || card.id.isEmpty()) continue;
                String slotId = diySlotId(card.id);
                if (!pool.containsKey(slotId)) {
                    pool.put(
                            slotId,
                            new LiveEntry(
                                    slotId,
                                    card.title,
                                    DiyNowBarCard.TYPE_NAMES[card.type],
                                    KIND_DIY,
                                    DiyNowBarCard.PACKAGE_ID,
                                    card.accentColor,
                                    false));
                }
            }
        }

        // Add built-in Samsung Now Bar cards
        for (LiveEntry builtin : builtinEntries()) {
            pool.putIfAbsent(builtin.slotId, builtin);
        }

        // Preserve any previously saved custom slots (except deleted/disabled DIY cards)
        List<String> savedOrder = config == null ? Collections.emptyList() : config.order;
        for (String slot : savedOrder) {
            if (slot == null || slot.isEmpty() || pool.containsKey(slot)) continue;
            if (slot.startsWith(PREFIX_DIY)) continue;
            pool.put(slot, fallbackEntryForSlot(slot));
        }

        List<LiveEntry> result = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (String slot : savedOrder) {
            LiveEntry entry = pool.get(slot);
            if (entry != null && emitted.add(slot)) {
                result.add(entry);
            }
        }
        // Append remaining live entries first, then non-live entries
        for (LiveEntry entry : pool.values()) {
            if (entry.live && emitted.add(entry.slotId)) result.add(entry);
        }
        for (LiveEntry entry : pool.values()) {
            if (!entry.live && emitted.add(entry.slotId)) result.add(entry);
        }
        return result;
    }

    static String symbolForKind(String kind) {
        if (kind == null) return "bell";
        return switch (kind) {
            case KIND_MEDIA -> "music";
            case KIND_DND -> "moon";
            case KIND_MODE -> "sparkles";
            case KIND_BRIEF -> "sun";
            case KIND_CHARGING -> "battery";
            case KIND_CONTACT -> "pin";
            case KIND_FLASHLIGHT -> "bolt";
            case KIND_CALL -> "bell";
            case KIND_SPORTS -> "star";
            default -> "grid";
        };
    }

    private static LiveEntry fallbackEntryForSlot(String slotId) {
        if (SLOT_NATIVE_BATTERY.equals(slotId)) {
            return new LiveEntry(
                    SLOT_NATIVE_BATTERY,
                    "Native Battery",
                    "",
                    KIND_CHARGING,
                    "com.samsung.android.app.aodservice",
                    0xFF22C55E,
                    false);
        }
        if (SLOT_CONTACT_INFO.equals(slotId)) {
            return new LiveEntry(
                    SLOT_CONTACT_INFO,
                    "Contact Info",
                    "",
                    KIND_CONTACT,
                    "com.samsung.android.app.aodservice",
                    0xFF3B82F6,
                    false);
        }
        if (SLOT_CALL.equals(slotId)) {
            return new LiveEntry(SLOT_CALL, "Ongoing Call", "", KIND_CALL, "", 0xFF10B981, false);
        }
        if (SLOT_SPORTS.equals(slotId)) {
            return new LiveEntry(SLOT_SPORTS, "Live Sports", "", KIND_SPORTS, "", 0xFF06B6D4, false);
        }
        if (SLOT_FINANCE.equals(slotId)) {
            return new LiveEntry(SLOT_FINANCE, "Finance", "", KIND_SPORTS, "", 0xFF10B981, false);
        }
        if (SLOT_COMMUTE.equals(slotId)) {
            return new LiveEntry(SLOT_COMMUTE, "Commute", "", KIND_SPORTS, "", 0xFFF97316, false);
        }
        if (SLOT_RECORDER.equals(slotId)) {
            return new LiveEntry(
                    SLOT_RECORDER,
                    "Voice Recorder",
                    "",
                    KIND_APP,
                    "com.sec.android.app.voicenote",
                    0xFFEF4444,
                    false);
        }
        if (slotId.startsWith(PREFIX_PKG)) {
            String pkg = slotId.substring(PREFIX_PKG.length());
            return new LiveEntry(slotId, friendlyPackageName(pkg), "", KIND_APP, pkg, 0xFF64748B, false);
        }
        return new LiveEntry(slotId, slotId, "", KIND_APP, "", 0xFF64748B, false);
    }

    static String friendlyPackageName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        int dot = pkg.lastIndexOf('.');
        String tail = dot >= 0 && dot + 1 < pkg.length() ? pkg.substring(dot + 1) : pkg;
        if (tail.isEmpty()) return pkg;
        return Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    private static String notiPart(String notiID, int index) {
        if (notiID == null || notiID.isEmpty()) return "";
        String[] parts = notiID.split("\\|", -1);
        return index >= 0 && index < parts.length ? safe(parts[index]) : "";
    }

    private static int normalizeAccent(int color, int fallback) {
        if (color == 0 || color == 1) return fallback;
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static String joinNonEmpty(String sep, String a, String b) {
        boolean ha = a != null && !a.isEmpty();
        boolean hb = b != null && !b.isEmpty();
        if (ha && hb) return a + sep + b;
        return ha ? a : (hb ? b : "");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cleanSlotId(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() > 140) trimmed = trimmed.substring(0, 140);
        if (trimmed.startsWith(PREFIX_DIY)
                || trimmed.startsWith(PREFIX_SYS)
                || trimmed.startsWith(PREFIX_PKG)
                || trimmed.startsWith(PREFIX_KEY)) {
            return trimmed.toLowerCase(Locale.US).startsWith(PREFIX_SYS)
                    ? trimmed.toLowerCase(Locale.US)
                    : trimmed;
        }
        return "";
    }

    private static String cleanLabel(String raw, int limit) {
        if (raw == null) return "";
        String clean = raw.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return clean.codePointCount(0, clean.length()) <= limit
                ? clean
                : clean.substring(0, clean.offsetByCodePoints(0, limit));
    }
}
