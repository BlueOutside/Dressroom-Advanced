package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Persistence, cross-process provider transport, and icon-file storage for DIY Now Bar cards. */
final class DiyNowBarStore {
    static final String METHOD_READ = "readDiyCards";
    static final String METHOD_COMMIT = "commitDiyCards";
    static final String METHOD_MUTATE = "mutateDiyCard";
    static final String METHOD_READ_STACK_ORDER = "readNowBarStackOrder";
    static final String METHOD_COMMIT_STACK_ORDER = "commitNowBarStackOrder";
    static final String METHOD_REPORT_LIVE_STACK = "reportLiveNowBarStack";
    static final String METHOD_READ_LIVE_STACK = "readLiveNowBarStack";
    static final String METHOD_READ_LABS = "readNowBarLabs";
    static final String METHOD_COMMIT_LABS = "commitNowBarLabs";
    private static final String PREFS = "committed_diy_nowbar_cards";
    private static final String KEY_CARDS = "cards_json";
    private static final String KEY_CONFIGURED = "configured";
    private static final String KEY_STACK_ORDER = "stack_order_json";
    private static final String KEY_LIVE_STACK = "live_stack_json";
    private static final String KEY_LIVE_STACK_TS = "live_stack_ts";
    private static final String KEY_LABS = "labs_json";
    private static final String ICON_DIR = "nowbar-icons";

    private DiyNowBarStore() {}

    static List<DiyNowBarCard> read(Context context) {
        try {
            Bundle result =
                    context.getContentResolver().call(SettingsProvider.URI, METHOD_READ, null, null);
            if (result == null) return new ArrayList<>();
            return fromJson(result.getString(KEY_CARDS, "[]"));
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not read DIY Now Bar cards", error);
            return new ArrayList<>();
        }
    }

    static boolean commit(Context context, List<DiyNowBarCard> cards) {
        Bundle payload = new Bundle();
        payload.putString(KEY_CARDS, toJson(cards));
        Bundle result =
                context.getContentResolver()
                        .call(SettingsProvider.URI, METHOD_COMMIT, null, payload);
        return result != null && result.getBoolean("saved");
    }

    static boolean mutate(Context context, String cardId, String action, int index) {
        if (cardId == null || cardId.isEmpty() || action == null || action.isEmpty()) return false;
        Bundle payload = new Bundle();
        payload.putString("cardId", cardId);
        payload.putString("action", action);
        payload.putInt("index", index);
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_MUTATE, null, payload);
            return result != null && result.getBoolean("changed");
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not mutate DIY Now Bar card", error);
            return false;
        }
    }

    static DiyNowBarStackOrder.Config readStackOrder(Context context) {
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_READ_STACK_ORDER, null, null);
            if (result == null) return new DiyNowBarStackOrder.Config();
            return stackOrderFromJson(result.getString(KEY_STACK_ORDER, ""));
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not read Now Bar stack order", error);
            return new DiyNowBarStackOrder.Config();
        }
    }

    static boolean commitStackOrder(Context context, DiyNowBarStackOrder.Config config) {
        Bundle payload = new Bundle();
        payload.putString(KEY_STACK_ORDER, stackOrderToJson(config));
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_COMMIT_STACK_ORDER, null, payload);
            return result != null && result.getBoolean("saved");
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not save Now Bar stack order", error);
            return false;
        }
    }

    static void reportLiveStack(Context context, List<DiyNowBarStackOrder.LiveEntry> entries) {
        try {
            Bundle payload = new Bundle();
            payload.putString(KEY_LIVE_STACK, liveEntriesToJson(entries));
            context.getContentResolver()
                    .call(SettingsProvider.URI, METHOD_REPORT_LIVE_STACK, null, payload);
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not report live Now Bar stack", error);
        }
    }

    static List<DiyNowBarStackOrder.LiveEntry> readLiveStack(Context context) {
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_READ_LIVE_STACK, null, null);
            if (result == null) return new ArrayList<>();
            return liveEntriesFromJson(result.getString(KEY_LIVE_STACK, "[]"));
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not read live Now Bar stack", error);
            return new ArrayList<>();
        }
    }

    static DiyNowBarLabs readLabs(Context context) {
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_READ_LABS, null, null);
            if (result == null) return new DiyNowBarLabs();
            return labsFromJson(result.getString(KEY_LABS, ""));
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not read Now Bar Labs config", error);
            return new DiyNowBarLabs();
        }
    }

    static boolean commitLabs(Context context, DiyNowBarLabs labs) {
        Bundle payload = new Bundle();
        payload.putString(KEY_LABS, labsToJson(labs));
        try {
            Bundle result =
                    context.getContentResolver()
                            .call(SettingsProvider.URI, METHOD_COMMIT_LABS, null, payload);
            return result != null && result.getBoolean("saved");
        } catch (RuntimeException error) {
            android.util.Log.w(DressroomModule.TAG, "Could not save Now Bar Labs config", error);
            return false;
        }
    }

    /** Called inside SettingsProvider's own process. */
    static Bundle handleProviderCall(Context context, String method, Bundle extras) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        if (METHOD_READ.equals(method)) {
            maybeMigrateLegacy(context, prefs);
            Bundle out = new Bundle();
            out.putString(KEY_CARDS, prefs.getString(KEY_CARDS, "[]"));
            return out;
        }
        if (METHOD_COMMIT.equals(method)) {
            String raw = extras == null ? "[]" : extras.getString(KEY_CARDS, "[]");
            List<DiyNowBarCard> clean = fromJson(raw);
            String normalized = toJson(clean);
            boolean ok =
                    prefs.edit()
                            .putString(KEY_CARDS, normalized)
                            .putBoolean(KEY_CONFIGURED, true)
                            .commit();
            if (ok) context.getContentResolver().notifyChange(SettingsProvider.URI, null);
            Bundle out = new Bundle();
            out.putBoolean("saved", ok);
            return out;
        }
        if (METHOD_MUTATE.equals(method)) {
            maybeMigrateLegacy(context, prefs);
            String cardId = extras == null ? "" : extras.getString("cardId", "");
            String action = extras == null ? "" : extras.getString("action", "");
            int index = extras == null ? -1 : extras.getInt("index", -1);
            List<DiyNowBarCard> cards = fromJson(prefs.getString(KEY_CARDS, "[]"));
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (DiyNowBarCard card : cards) {
                if (card.id.equals(cardId)) {
                    changed = card.applyMutation(action, index, now);
                    break;
                }
            }
            if (changed) {
                prefs.edit()
                        .putString(KEY_CARDS, toJson(cards))
                        .putBoolean(KEY_CONFIGURED, true)
                        .commit();
                context.getContentResolver().notifyChange(SettingsProvider.URI, null);
            }
            Bundle out = new Bundle();
            out.putBoolean("changed", changed);
            return out;
        }
        if (METHOD_READ_STACK_ORDER.equals(method)) {
            Bundle out = new Bundle();
            out.putString(KEY_STACK_ORDER, prefs.getString(KEY_STACK_ORDER, ""));
            return out;
        }
        if (METHOD_COMMIT_STACK_ORDER.equals(method)) {
            String raw = extras == null ? "" : extras.getString(KEY_STACK_ORDER, "");
            DiyNowBarStackOrder.Config existing =
                    stackOrderFromJson(prefs.getString(KEY_STACK_ORDER, ""));
            DiyNowBarStackOrder.Config next = stackOrderFromJson(raw);
            next.revision = Math.max(existing.revision + 1L, System.currentTimeMillis());
            boolean ok = prefs.edit().putString(KEY_STACK_ORDER, stackOrderToJson(next)).commit();
            if (ok) context.getContentResolver().notifyChange(SettingsProvider.URI, null);
            Bundle out = new Bundle();
            out.putBoolean("saved", ok);
            out.putLong("revision", next.revision);
            return out;
        }
        if (METHOD_REPORT_LIVE_STACK.equals(method)) {
            String raw = extras == null ? "[]" : extras.getString(KEY_LIVE_STACK, "[]");
            List<DiyNowBarStackOrder.LiveEntry> clean = liveEntriesFromJson(raw);
            boolean ok =
                    prefs.edit()
                            .putString(KEY_LIVE_STACK, liveEntriesToJson(clean))
                            .putLong(KEY_LIVE_STACK_TS, System.currentTimeMillis())
                            .commit();
            Bundle out = new Bundle();
            out.putBoolean("saved", ok);
            return out;
        }
        if (METHOD_READ_LIVE_STACK.equals(method)) {
            Bundle out = new Bundle();
            out.putString(KEY_LIVE_STACK, prefs.getString(KEY_LIVE_STACK, "[]"));
            out.putLong(KEY_LIVE_STACK_TS, prefs.getLong(KEY_LIVE_STACK_TS, 0L));
            return out;
        }
        if (METHOD_READ_LABS.equals(method)) {
            Bundle out = new Bundle();
            out.putString(KEY_LABS, prefs.getString(KEY_LABS, ""));
            return out;
        }
        if (METHOD_COMMIT_LABS.equals(method)) {
            String raw = extras == null ? "" : extras.getString(KEY_LABS, "");
            DiyNowBarLabs existing = labsFromJson(prefs.getString(KEY_LABS, ""));
            DiyNowBarLabs next = labsFromJson(raw);
            next.revision = Math.max(existing.revision + 1L, System.currentTimeMillis());
            boolean ok = prefs.edit().putString(KEY_LABS, labsToJson(next)).commit();
            if (ok) context.getContentResolver().notifyChange(SettingsProvider.URI, null);
            Bundle out = new Bundle();
            out.putBoolean("saved", ok);
            out.putLong("revision", next.revision);
            return out;
        }
        throw new IllegalArgumentException("Unsupported DIY Now Bar operation: " + method);
    }

    private static void maybeMigrateLegacy(Context context, SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_CONFIGURED, false)) return;
        try {
            SharedPreferences legacy =
                    context.getSharedPreferences("committed_lockscreen_extras", 0);
            String raw = legacy.getString("options", null);
            if (raw == null) raw = legacy.getString("options_1", null);
            if (raw == null) return;
            JSONObject json = new JSONObject(raw);
            if (!json.optBoolean("textCard", false)) return;
            String title = json.optString("title", "").trim();
            String message = json.optString("message", "").trim();
            if (title.isEmpty() && message.isEmpty()) return;
            DiyNowBarCard migrated =
                    DiyNowBarCard.defaultForType(DiyNowBarCard.TYPE_CUSTOM, System.currentTimeMillis());
            migrated.title = title.isEmpty() ? "My Now Bar" : title;
            migrated.subtitle = message;
            migrated.body = message;
            migrated.sanitize();
            prefs.edit()
                    .putString(KEY_CARDS, toJson(List.of(migrated)))
                    .putBoolean(KEY_CONFIGURED, true)
                    .commit();
        } catch (Exception ignored) {
        }
    }

    static File iconFile(Context context, String name) {
        if (name == null || !name.matches("[a-z0-9_-]{4,40}\\.png")) {
            throw new IllegalArgumentException("Invalid Now Bar icon name");
        }
        return new File(new File(context.getFilesDir(), ICON_DIR), name);
    }

    static Uri iconUri(String name) {
        return SettingsProvider.URI.buildUpon().appendPath(ICON_DIR).appendPath(name).build();
    }

    /** Copies and center-crops a picked image into app-private storage (called from StudioActivity). */
    static String importPhotoIcon(Context context, Uri source) throws IOException {
        Bitmap decoded;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("Could not open selected image");
            decoded = BitmapFactory.decodeStream(in);
        }
        if (decoded == null) throw new IOException("Selected file is not a valid image");
        int size = Math.min(decoded.getWidth(), decoded.getHeight());
        int x = Math.max(0, (decoded.getWidth() - size) / 2);
        int y = Math.max(0, (decoded.getHeight() - size) / 2);
        Bitmap square = Bitmap.createBitmap(decoded, x, y, size, size);
        if (square != decoded) decoded.recycle();
        Bitmap scaled = Bitmap.createScaledBitmap(square, 192, 192, true);
        if (scaled != square) square.recycle();
        File dir = new File(context.getFilesDir(), ICON_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            scaled.recycle();
            throw new IOException("Could not create icon directory");
        }
        String fileName = "icon_" + DiyNowBarCard.generateId() + ".png";
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)) {
                throw new IOException("Could not save cropped icon");
            }
        } finally {
            scaled.recycle();
        }
        return fileName;
    }

    static Bitmap loadPhotoIcon(Context context, String name) {
        if (name == null || name.isEmpty()) return null;
        try (InputStream in = context.getContentResolver().openInputStream(iconUri(name))) {
            return in == null ? null : BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String toJson(List<DiyNowBarCard> cards) {
        JSONArray array = new JSONArray();
        if (cards != null) {
            int limit = Math.min(cards.size(), DiyNowBarCard.MAX_CARDS);
            for (int i = 0; i < limit; i++) {
                DiyNowBarCard card = cards.get(i);
                if (card == null) continue;
                card.sanitize();
                try {
                    array.put(cardToJson(card));
                } catch (JSONException ignored) {
                }
            }
        }
        return array.toString();
    }

    static List<DiyNowBarCard> fromJson(String raw) {
        List<DiyNowBarCard> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            int limit = Math.min(array.length(), DiyNowBarCard.MAX_CARDS);
            for (int i = 0; i < limit; i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) out.add(cardFromJson(obj));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static JSONObject cardToJson(DiyNowBarCard c) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", c.id);
        j.put("enabled", c.enabled);
        j.put("type", c.type);
        j.put("title", c.title);
        j.put("subtitle", c.subtitle);
        j.put("body", c.body);
        j.put("iconType", c.iconType);
        j.put("iconValue", c.iconValue);
        j.put("iconShape", c.iconShape);
        j.put("accentColor", c.accentColor);
        j.put("effect", c.effect);
        j.put("effectColor1", c.effectColor1);
        j.put("effectColor2", c.effectColor2);
        j.put("expandable", c.expandable);
        j.put("hideOnLock", c.hideOnLock);
        j.put("bigChronometer", c.bigChronometer);
        j.put("ignoreBouncer", c.ignoreBouncer);
        j.put("fullscreenExpand", c.fullscreenExpand);
        j.put("liveExpandedView", c.liveExpandedView);
        j.put("secondaryIcon", c.secondaryIcon);
        j.put("quickAction", c.quickAction);
        j.put("quickActionTarget", c.quickActionTarget);
        j.put("tapAction", c.tapAction);
        j.put("tapActionTarget", c.tapActionTarget);
        j.put("showProgress", c.showProgress);
        j.put("progressLabel", c.progressLabel);
        j.put("currentValue", c.currentValue);
        j.put("targetValue", c.targetValue);
        j.put("stepValue", c.stepValue);
        j.put("unit", c.unit);
        j.put("timerMode", c.timerMode);
        j.put("timerCreatedAtMs", c.timerCreatedAtMs);
        j.put("timerTargetAtMs", c.timerTargetAtMs);
        j.put("timerRunning", c.timerRunning);
        j.put("timerStartedAtMs", c.timerStartedAtMs);
        j.put("timerAccumulatedMs", c.timerAccumulatedMs);
        j.put("statsMask", c.statsMask);
        j.put("statsPrimary", c.statsPrimary);
        j.put("statsFormat", c.statsFormat);

        JSONArray checkArr = new JSONArray();
        for (DiyNowBarCard.CheckItem item : c.checklist) {
            JSONObject ci = new JSONObject();
            ci.put("t", item.text);
            ci.put("d", item.done);
            checkArr.put(ci);
        }
        j.put("checklist", checkArr);

        JSONArray shortArr = new JSONArray();
        for (DiyNowBarCard.ShortcutSlot slot : c.shortcuts) {
            JSONObject si = new JSONObject();
            si.put("a", slot.action);
            si.put("l", slot.label);
            si.put("t", slot.target);
            shortArr.put(si);
        }
        j.put("shortcuts", shortArr);
        return j;
    }

    private static DiyNowBarCard cardFromJson(JSONObject j) {
        DiyNowBarCard c = new DiyNowBarCard();
        c.id = j.optString("id", "");
        c.enabled = j.optBoolean("enabled", true);
        c.type = j.optInt("type", DiyNowBarCard.TYPE_CUSTOM);
        c.title = j.optString("title", "");
        c.subtitle = j.optString("subtitle", "");
        c.body = j.optString("body", "");
        c.iconType = j.optInt("iconType", DiyNowBarCard.ICON_SYMBOL);
        c.iconValue = j.optString("iconValue", "sparkles");
        c.iconShape = j.optInt("iconShape", DiyNowBarCard.SHAPE_SQUIRCLE);
        c.accentColor = j.optInt("accentColor", 0xFF3B82F6);
        c.effect = j.optInt("effect", DiyNowBarCard.EFFECT_RADIAL_GLOW);
        c.effectColor1 = j.optInt("effectColor1", c.accentColor);
        c.effectColor2 = j.optInt("effectColor2", 0xFFA86BFF);
        c.expandable = j.optBoolean("expandable", true);
        c.hideOnLock = j.optBoolean("hideOnLock", false);
        c.bigChronometer = j.optBoolean("bigChronometer", false);
        c.ignoreBouncer = j.optBoolean("ignoreBouncer", false);
        c.fullscreenExpand = j.optBoolean("fullscreenExpand", false);
        c.liveExpandedView = j.optBoolean("liveExpandedView", true);
        c.secondaryIcon = j.optString("secondaryIcon", "");
        c.quickAction = j.optInt("quickAction", DiyNowBarCard.ACTION_AUTO);
        c.quickActionTarget = j.optString("quickActionTarget", "");
        c.tapAction = j.optInt("tapAction", DiyNowBarCard.ACTION_NONE);
        c.tapActionTarget = j.optString("tapActionTarget", "");
        c.showProgress = j.optBoolean("showProgress", false);
        c.progressLabel = j.optString("progressLabel", "");
        c.currentValue = j.optInt("currentValue", 0);
        c.targetValue = j.optInt("targetValue", 10);
        c.stepValue = j.optInt("stepValue", 1);
        c.unit = j.optString("unit", "");
        c.timerMode = j.optInt("timerMode", DiyNowBarCard.TIMER_COUNTDOWN);
        c.timerCreatedAtMs = j.optLong("timerCreatedAtMs", 0L);
        c.timerTargetAtMs = j.optLong("timerTargetAtMs", 0L);
        c.timerRunning = j.optBoolean("timerRunning", false);
        c.timerStartedAtMs = j.optLong("timerStartedAtMs", 0L);
        c.timerAccumulatedMs = j.optLong("timerAccumulatedMs", 0L);
        c.statsMask = j.optInt("statsMask", DiyNowBarCard.STAT_BATTERY | DiyNowBarCard.STAT_RAM);
        c.statsPrimary = j.optInt("statsPrimary", DiyNowBarCard.STAT_BATTERY);
        c.statsFormat = j.optString("statsFormat", "");

        JSONArray checkArr = j.optJSONArray("checklist");
        if (checkArr != null) {
            for (int i = 0; i < checkArr.length() && i < DiyNowBarCard.MAX_CHECKLIST; i++) {
                JSONObject ci = checkArr.optJSONObject(i);
                if (ci != null) {
                    c.checklist.add(new DiyNowBarCard.CheckItem(ci.optString("t", ""), ci.optBoolean("d", false)));
                }
            }
        }
        JSONArray shortArr = j.optJSONArray("shortcuts");
        if (shortArr != null) {
            for (int i = 0; i < shortArr.length() && i < DiyNowBarCard.MAX_SHORTCUTS; i++) {
                JSONObject si = shortArr.optJSONObject(i);
                if (si != null) {
                    c.shortcuts.add(
                            new DiyNowBarCard.ShortcutSlot(
                                    si.optInt("a", DiyNowBarCard.ACTION_NONE),
                                    si.optString("l", ""),
                                    si.optString("t", "")));
                }
            }
        }
        return c.sanitize();
    }

    static String stackOrderToJson(DiyNowBarStackOrder.Config config) {
        JSONObject j = new JSONObject();
        DiyNowBarStackOrder.Config c =
                config == null ? new DiyNowBarStackOrder.Config() : config.copy().sanitize();
        try {
            j.put("enabled", c.enabled);
            j.put("resetOnScreenOff", c.resetOnScreenOff);
            j.put("revision", c.revision);
            JSONArray arr = new JSONArray();
            for (String slot : c.order) arr.put(slot);
            j.put("order", arr);
        } catch (JSONException ignored) {
        }
        return j.toString();
    }

    static DiyNowBarStackOrder.Config stackOrderFromJson(String raw) {
        DiyNowBarStackOrder.Config c = new DiyNowBarStackOrder.Config();
        if (raw == null || raw.isEmpty()) return c;
        try {
            JSONObject j = new JSONObject(raw);
            c.enabled = j.optBoolean("enabled", false);
            c.resetOnScreenOff = j.optBoolean("resetOnScreenOff", true);
            c.revision = j.optLong("revision", 0L);
            JSONArray arr = j.optJSONArray("order");
            if (arr != null) {
                for (int i = 0; i < arr.length() && i < DiyNowBarStackOrder.MAX_ORDER_SLOTS; i++) {
                    String slot = arr.optString(i, "");
                    if (!slot.isEmpty()) c.order.add(slot);
                }
            }
        } catch (JSONException ignored) {
        }
        return c.sanitize();
    }

    static String liveEntriesToJson(List<DiyNowBarStackOrder.LiveEntry> entries) {
        JSONArray arr = new JSONArray();
        if (entries != null) {
            int limit = Math.min(entries.size(), DiyNowBarStackOrder.MAX_ORDER_SLOTS);
            for (int i = 0; i < limit; i++) {
                DiyNowBarStackOrder.LiveEntry e = entries.get(i);
                if (e == null || e.slotId.isEmpty()) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("s", e.slotId);
                    o.put("l", e.label);
                    o.put("sub", e.subtitle);
                    o.put("k", e.kind);
                    o.put("p", e.packageName);
                    o.put("c", e.accentColor);
                    arr.put(o);
                } catch (JSONException ignored) {
                }
            }
        }
        return arr.toString();
    }

    static List<DiyNowBarStackOrder.LiveEntry> liveEntriesFromJson(String raw) {
        List<DiyNowBarStackOrder.LiveEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            int limit = Math.min(arr.length(), DiyNowBarStackOrder.MAX_ORDER_SLOTS);
            for (int i = 0; i < limit; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                DiyNowBarStackOrder.LiveEntry entry =
                        new DiyNowBarStackOrder.LiveEntry(
                                o.optString("s", ""),
                                o.optString("l", ""),
                                o.optString("sub", ""),
                                o.optString("k", DiyNowBarStackOrder.KIND_APP),
                                o.optString("p", ""),
                                o.optInt("c", 0xFF3B82F6),
                                true);
                if (!entry.slotId.isEmpty()) out.add(entry);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static String labsToJson(DiyNowBarLabs labs) {
        JSONObject j = new JSONObject();
        DiyNowBarLabs c = labs == null ? new DiyNowBarLabs() : labs.copy().sanitize();
        try {
            j.put("nativeBatteryCard", c.nativeBatteryCard);
            j.put("nativeBatteryAlwaysShow", c.nativeBatteryAlwaysShow);
            j.put("nativeContactCard", c.nativeContactCard);
            j.put("unlockGoogleFinance", c.unlockGoogleFinance);
            j.put("unlockGoogleCommute", c.unlockGoogleCommute);
            j.put("globalCardShader", c.globalCardShader);
            j.put("globalColor1", c.globalColor1);
            j.put("globalColor2", c.globalColor2);
            j.put("longPressOpensStudio", c.longPressOpensStudio);
            j.put("revision", c.revision);
        } catch (JSONException ignored) {
        }
        return j.toString();
    }

    static DiyNowBarLabs labsFromJson(String raw) {
        DiyNowBarLabs c = new DiyNowBarLabs();
        if (raw == null || raw.isEmpty()) return c;
        try {
            JSONObject j = new JSONObject(raw);
            c.nativeBatteryCard = j.optBoolean("nativeBatteryCard", false);
            c.nativeBatteryAlwaysShow = j.optBoolean("nativeBatteryAlwaysShow", false);
            c.nativeContactCard = j.optBoolean("nativeContactCard", false);
            c.unlockGoogleFinance = j.optBoolean("unlockGoogleFinance", false);
            c.unlockGoogleCommute = j.optBoolean("unlockGoogleCommute", false);
            c.globalCardShader = j.optInt("globalCardShader", DiyNowBarCard.EFFECT_NONE);
            c.globalColor1 = j.optInt("globalColor1", 0xFF4E8CFF);
            c.globalColor2 = j.optInt("globalColor2", 0xFFA86BFF);
            c.longPressOpensStudio = j.optBoolean("longPressOpensStudio", true);
            c.revision = j.optLong("revision", 0L);
        } catch (JSONException ignored) {
        }
        return c.sanitize();
    }
}
