package dev.denver.dressroomadvanced;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import io.github.libxposed.api.XposedInterface;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime hooks inside AOD/SystemUI Now Bar views and AOD Settings:
 * 1. Keeps active DIY cards visible even when lockscreen notifications are hidden.
 * 2. Revives scrapped native Samsung cards (NowBarBatteryCardView, NowBarContactCardView) and
 *    unlocks hidden Google Finance & Google Commute cards in Settings and live stack.
 * 3. Reports all live Now Bar cards to Studio and applies custom Stacking Order.
 * 4. Drives Samsung's real native GPU shader engines (AIPresenceView Snake/MVC, OuterGlowView,
 *    GuidingLightEffect) per DIY card or globally, with DiyNowBarAuraView fallback.
 * 5. Attaches live interactive Android Views (Sparkline Telemetry & Live Controls sliders),
 *    unblocks inline secondary mini-icons, supports ignore-bouncer taps, and deep-links
 *    long-press Settings directly into DIY Now Bar Studio.
 */
final class DiyNowBarEffects {
    private static final String PKG_AOD = "com.samsung.android.app.aodservice";
    private static final String MANAGER =
            "Lcom/samsung/android/app/facewidget/domain/NowBarItemManager;";
    private static final String ITEM =
            "Lcom/android/systemui/plugins/keyguardstatusview/NowBarItem;";
    private static final String LIST = "Ljava/util/concurrent/CopyOnWriteArrayList;";
    private static final String FILTER_MARKER = "nowbar_key_need_to_blockcard";
    private static final String FORCE_UPDATE_MARKER = "notifyForceUpdate";
    private static final String ONGOING_VIEW =
            "com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView";
    private static final String ONGOING_DESC =
            "Lcom/samsung/android/app/facewidget/presentation/NowBarOngoingCardView;";
    private static final String BASE_VIEW =
            "com.samsung.android.app.facewidget.presentation.BaseNowBarCardView";
    private static final String SETTING_VIEW =
            "com.samsung.android.app.facewidget.presentation.NowbarCardSettingView";
    private static final String ITEM_CLASS =
            "com.android.systemui.plugins.keyguardstatusview.NowBarItem";
    private static final int TAG_ORIG_SETTINGS_CLICK = 0x5A3D1102;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> labsSettingsInstalled =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, AuraAttachment> attachments =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, ManagerState> trackedManagers =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile DiyNowBarStackOrder.Config stackConfig =
            new DiyNowBarStackOrder.Config();
    private static volatile DiyNowBarLabs labsConfig = new DiyNowBarLabs();
    private static volatile boolean labsLoadedOnce;
    private static volatile boolean stackObserverBound;
    private static volatile Context stackHostContext;
    private static volatile String lastReportedLiveSig = "";
    private static volatile boolean lastKnownPlugged;

    private static final class ManagerHandle {
        final Field rawListField;
        final Field contextField;
        final Field batteryManagerField;
        final Field batteryDataField;
        final Method forceUpdateMethod;
        final Method getExpandedItemMethod;
        final Class<?> itemClass;

        ManagerHandle(
                Field rawListField,
                Field contextField,
                Field batteryManagerField,
                Field batteryDataField,
                Method forceUpdateMethod,
                Method getExpandedItemMethod,
                Class<?> itemClass) {
            this.rawListField = rawListField;
            this.contextField = contextField;
            this.batteryManagerField = batteryManagerField;
            this.batteryDataField = batteryDataField;
            this.forceUpdateMethod = forceUpdateMethod;
            this.getExpandedItemMethod = getExpandedItemMethod;
            this.itemClass = itemClass;
        }
    }

    private static final class ManagerState {
        final ManagerHandle handle;
        long lastAppliedRevision = -1L;
        String lastItemSignature = "";
        Object nativeBatteryItem;
        Object nativeContactItem;

        ManagerState(ManagerHandle handle) {
            this.handle = handle;
        }
    }

    private static final class AuraAttachment {
        final DiyNowBarNativeShaders.CardNativeState nativeState =
                new DiyNowBarNativeShaders.CardNativeState();
        DiyNowBarAuraView innerView;
        DiyNowBarAuraView outerView;
        View liveView;
        View trackedContainer;
        View.OnLayoutChangeListener layoutListener;
        View trackedIconContainer;
        View.OnLayoutChangeListener iconLayoutListener;
        float originalIconRadius = -1f;
        int iconShape = DiyNowBarCard.SHAPE_SQUIRCLE;
        int effect = -1;
        int color1;
        int color2;
        boolean usingNativeShader;
    }

    private DiyNowBarEffects() {}

    static synchronized void install(
            DressroomModule module, ClassLoader loader, boolean hookVisibility) {
        if (loader == null || !installed.add(loader)) return;
        if (hookVisibility) installVisibilityHook(module, loader);
        installCardShaderHooks(module, loader);
        installBouncerBypassHook(module, loader);
        installLongPressStudioHook(module, loader);
        installLabsSettingsHooks(module, loader);
    }

    /**
     * Installs Google Finance & Google Commute unlock hooks inside both SystemUI's AOD plugin
     * ClassLoader and AODService's Settings process (FaceWidgetNowBarSettingsActivity).
     */
    static synchronized void installLabsSettingsHooks(DressroomModule module, ClassLoader loader) {
        if (loader == null || !labsSettingsInstalled.add(loader)) return;
        try {
            // 1. AbstractC0814t1.m11184A(Context): scpm_aod_common_policy -> "nowbar_commute"
            for (DexSignatures.Body body :
                    DexSignatures.find(loader, Set.of("scpm_aod_common_policy", "nowbar_commute"))) {
                if (body.parameters().equals(List.of("Landroid/content/Context;"))
                        && "Z".equals(body.result())) {
                    Method m = body.method(loader);
                    hookAndDeopt(
                            module,
                            m,
                            "dressroom-advanced.labs-commute-policy",
                            chain -> {
                                Context ctx =
                                        chain.getArg(0) instanceof Context c ? c : stackHostContext;
                                DiyNowBarLabs labs = ensureLabsSnapshot(ctx);
                                if (labs.unlockGoogleCommute) return true;
                                return chain.proceed();
                            });
                    break;
                }
            }

            // 2. AbstractC0814t1.m11185B(Context): Google Commute enabled check
            for (DexSignatures.Body body :
                    DexSignatures.find(
                            loader,
                            Set.of("com.google.android.apps.maps_internal", "key_now_bar_"))) {
                if (body.parameters().equals(List.of("Landroid/content/Context;"))
                        && "Z".equals(body.result())) {
                    Method m = body.method(loader);
                    hookAndDeopt(
                            module,
                            m,
                            "dressroom-advanced.labs-commute-enabled",
                            chain -> {
                                Context ctx =
                                        chain.getArg(0) instanceof Context c ? c : stackHostContext;
                                DiyNowBarLabs labs = ensureLabsSnapshot(ctx);
                                if (labs.unlockGoogleCommute && ctx != null) {
                                    return Settings.System.getInt(
                                                    ctx.getContentResolver(),
                                                    "key_now_bar_com_google_android_apps_maps_internal",
                                                    1)
                                            == 1;
                                }
                                return chain.proceed();
                            });
                    break;
                }
            }

            // 3. AbstractC0814t1.m11186C(Context): Google Finance enabled check
            for (DexSignatures.Body body :
                    DexSignatures.find(
                            loader,
                            Set.of(
                                    "com.google.android.googlequicksearchbox_finance",
                                    "key_now_bar_"))) {
                if (body.parameters().equals(List.of("Landroid/content/Context;"))
                        && "Z".equals(body.result())) {
                    Method m = body.method(loader);
                    hookAndDeopt(
                            module,
                            m,
                            "dressroom-advanced.labs-finance-enabled",
                            chain -> {
                                Context ctx =
                                        chain.getArg(0) instanceof Context c ? c : stackHostContext;
                                DiyNowBarLabs labs = ensureLabsSnapshot(ctx);
                                if (labs.unlockGoogleFinance && ctx != null) {
                                    return Settings.System.getInt(
                                                    ctx.getContentResolver(),
                                                    "key_now_bar_com_google_android_googlequicksearchbox_finance",
                                                    1)
                                            == 1;
                                }
                                return chain.proceed();
                            });
                    break;
                }
            }

            // 4. b05.m1840g(Context): NowBarOngoingListUtils.getOngoingList
            for (DexSignatures.Body body :
                    DexSignatures.find(
                            loader, Set.of("NowBarOngoingListUtils", "getOngoingList "))) {
                if (body.parameters().equals(List.of("Landroid/content/Context;"))
                        && "Ljava/util/List;".equals(body.result())) {
                    Method m = body.method(loader);
                    hookAndDeopt(
                            module,
                            m,
                            "dressroom-advanced.labs-ongoing-list",
                            chain -> {
                                Object res = chain.proceed();
                                Context ctx =
                                        chain.getArg(0) instanceof Context c ? c : stackHostContext;
                                DiyNowBarLabs labs = ensureLabsSnapshot(ctx);
                                if (!labs.unlockGoogleFinance && !labs.unlockGoogleCommute) {
                                    return res;
                                }
                                List<Object> out =
                                        res instanceof List<?> raw
                                                ? new ArrayList<>(raw)
                                                : new ArrayList<>();
                                if (labs.unlockGoogleFinance
                                        && !out.contains(
                                                "com.google.android.googlequicksearchbox_finance")) {
                                    out.add(0, "com.google.android.googlequicksearchbox_finance");
                                }
                                if (labs.unlockGoogleCommute
                                        && !out.contains("com.google.android.apps.maps_internal")) {
                                    out.add(0, "com.google.android.apps.maps_internal");
                                }
                                return out;
                            });
                    break;
                }
            }
        } catch (Throwable error) {
            DressroomModule.note("Now Bar Labs settings hooks unavailable", error);
        }
    }

    private static DiyNowBarLabs ensureLabsSnapshot(Context ctx) {
        if (ctx != null) ensureStackObserver(ctx);
        if (!labsLoadedOnce && ctx != null) {
            try {
                labsConfig = DiyNowBarStore.readLabs(ctx);
                labsLoadedOnce = true;
            } catch (Throwable ignored) {
            }
        }
        return labsConfig;
    }

    private static void installVisibilityHook(DressroomModule module, ClassLoader loader) {
        try {
            Method filter = resolveFilter(loader);
            ManagerHandle handle = resolveManagerHandle(loader, filter.getDeclaringClass());
            module.deoptimize(filter);
            module.hook(filter)
                    .setId("dressroom-advanced.diy-nowbar-visibility")
                    .intercept(
                            chain -> {
                                Object manager = chain.getThisObject();
                                Object result = chain.proceed();
                                if (!(result instanceof CopyOnWriteArrayList<?> visible)) {
                                    return result;
                                }

                                ManagerState state = registerManager(manager, handle);
                                CopyOnWriteArrayList<Object> working =
                                        restoreDiyCards(chain.getArg(0), visible);

                                Context ctx = resolveStackContext(manager, handle);
                                if (ctx != null) ensureStackObserver(ctx);

                                syncNativeLabCards(manager, state, ctx, working);
                                working = applyStackOrder(manager, state, working);

                                if (ctx != null) reportLiveCardsAsync(ctx, working);
                                return working;
                            });
        } catch (Throwable error) {
            DressroomModule.note("DIY Now Bar visibility hook unavailable", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void syncNativeLabCards(
            Object manager,
            ManagerState state,
            Context ctx,
            CopyOnWriteArrayList<Object> visible) {
        if (manager == null || state == null || state.handle.itemClass == null) return;
        DiyNowBarLabs labs = labsConfig;
        if (labs == null) return;

        CopyOnWriteArrayList<Object> rawList = null;
        if (state.handle.rawListField != null) {
            try {
                Object rawObj = state.handle.rawListField.get(manager);
                if (rawObj instanceof CopyOnWriteArrayList<?> list) {
                    rawList = (CopyOnWriteArrayList<Object>) list;
                }
            } catch (Throwable ignored) {
            }
        }

        // 1. Scrapped Native Battery Card (NowBarViewStyle.BATTERY = 4)
        boolean wantBattery =
                labs.nativeBatteryCard
                        && (labs.nativeBatteryAlwaysShow || isDevicePluggedIn(ctx));
        if (wantBattery) {
            Object batItem = ensureNativeBatteryItem(manager, state);
            if (batItem != null) {
                ensurePresent(rawList, batItem, DiyNowBarLabs.KEY_NATIVE_BATTERY);
                ensurePresent(visible, batItem, DiyNowBarLabs.KEY_NATIVE_BATTERY);
            }
        } else {
            removeByKey(rawList, DiyNowBarLabs.KEY_NATIVE_BATTERY);
            removeByKey(visible, DiyNowBarLabs.KEY_NATIVE_BATTERY);
            state.nativeBatteryItem = null;
        }

        // 2. Hidden Native Lockscreen Contact Info Card (NowBarViewStyle.CONTACT_INFO = 5)
        if (labs.nativeContactCard) {
            Object contactItem = ensureNativeContactItem(state);
            if (contactItem != null) {
                ensurePresent(rawList, contactItem, DiyNowBarLabs.KEY_NATIVE_CONTACT);
                ensurePresent(visible, contactItem, DiyNowBarLabs.KEY_NATIVE_CONTACT);
            }
        } else {
            removeByKey(rawList, DiyNowBarLabs.KEY_NATIVE_CONTACT);
            removeByKey(visible, DiyNowBarLabs.KEY_NATIVE_CONTACT);
            state.nativeContactItem = null;
        }
    }

    private static Object ensureNativeBatteryItem(Object manager, ManagerState state) {
        try {
            Object batData = readBatteryDataClone(manager, state.handle);
            Object item = state.nativeBatteryItem;
            if (item == null) {
                item = state.handle.itemClass.getConstructor().newInstance();
                Reflect.call(item, "setNotiID", "");
                Reflect.call(item, "setNowBarKey", DiyNowBarLabs.KEY_NATIVE_BATTERY);
                Reflect.call(item, "setNowBarPackage", PKG_AOD);
                Reflect.call(item, "setNowBarViewStyle", DiyNowBarStackOrder.STYLE_BATTERY);
                Reflect.call(item, "setPrimaryInfo", "battery_plugged_in");
                Reflect.call(item, "setNowbarPrimaryInfo", "battery_plugged_in");
                Bundle extra = new Bundle();
                extra.putInt("nowbar_key_screen_type", 3);
                Reflect.call(item, "setExtraData", extra);
                state.nativeBatteryItem = item;
            }
            if (batData != null) {
                Reflect.call(item, "setObject", batData);
            }
            return item;
        } catch (Throwable error) {
            DressroomModule.note("Native Battery Now Bar card synthesis failed", error);
            return null;
        }
    }

    private static Object ensureNativeContactItem(ManagerState state) {
        try {
            if (state.nativeContactItem != null) return state.nativeContactItem;
            Object item = state.handle.itemClass.getConstructor().newInstance();
            Reflect.call(item, "setNotiID", "");
            Reflect.call(item, "setNowBarKey", DiyNowBarLabs.KEY_NATIVE_CONTACT);
            Reflect.call(item, "setNowBarPackage", PKG_AOD);
            Reflect.call(item, "setNowBarViewStyle", DiyNowBarStackOrder.STYLE_CONTACT_INFO);
            Reflect.call(item, "setPrimaryInfo", "Contact Info");
            Reflect.call(item, "setNowbarPrimaryInfo", "Contact Info");
            Bundle extra = new Bundle();
            extra.putInt("nowbar_key_screen_type", 3);
            Reflect.call(item, "setExtraData", extra);
            state.nativeContactItem = item;
            return item;
        } catch (Throwable error) {
            DressroomModule.note("Native Contact Info Now Bar card synthesis failed", error);
            return null;
        }
    }

    private static Object readBatteryDataClone(Object manager, ManagerHandle handle) {
        if (manager == null || handle == null || handle.batteryManagerField == null) return null;
        try {
            Object batMgr = handle.batteryManagerField.get(manager);
            if (batMgr == null || handle.batteryDataField == null) return null;
            Object data = handle.batteryDataField.get(batMgr);
            if (data == null) return null;
            try {
                Method clone = data.getClass().getDeclaredMethod("clone");
                clone.setAccessible(true);
                return clone.invoke(data);
            } catch (Throwable ignored) {
                return data;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDevicePluggedIn(Context ctx) {
        if (ctx == null) return lastKnownPlugged;
        try {
            Intent bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                lastKnownPlugged = plugged != 0;
            }
        } catch (Throwable ignored) {
        }
        return lastKnownPlugged;
    }

    private static void ensurePresent(
            CopyOnWriteArrayList<Object> list, Object item, String targetKey) {
        if (list == null || item == null) return;
        for (Object existing : list) {
            if (existing == item || targetKey.equals(safeCallString(existing, "getNowBarKey"))) {
                return;
            }
        }
        list.add(item);
    }

    private static void removeByKey(CopyOnWriteArrayList<Object> list, String targetKey) {
        if (list == null) return;
        for (Object existing : list) {
            if (targetKey.equals(safeCallString(existing, "getNowBarKey"))) {
                list.remove(existing);
            }
        }
    }

    private static CopyOnWriteArrayList<Object> restoreDiyCards(
            Object rawArg, CopyOnWriteArrayList<?> visible) {
        List<Object> diyItems = NowBarExtras.customItems();
        if (diyItems.isEmpty() || !(rawArg instanceof List<?> cards)) {
            return new CopyOnWriteArrayList<>(visible);
        }
        CopyOnWriteArrayList<Object> restored = new CopyOnWriteArrayList<>(visible);
        for (Object diy : diyItems) {
            if (diy == null || restored.contains(diy)) continue;
            int position = 0;
            boolean inSource = false;
            for (Object card : cards) {
                if (card == diy) {
                    inSource = true;
                    break;
                }
                if (restored.contains(card)) position++;
            }
            if (!inSource) continue;
            restored.add(Math.min(position, restored.size()), diy);
        }
        return restored;
    }

    private static ManagerHandle resolveManagerHandle(ClassLoader loader, Class<?> managerClass) {
        Field rawListField = null;
        Field contextField = null;
        Field batteryManagerField = null;
        Field batteryDataField = null;
        for (Field f : managerClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == CopyOnWriteArrayList.class && rawListField == null) {
                f.setAccessible(true);
                rawListField = f;
            } else if (Context.class.isAssignableFrom(f.getType()) && contextField == null) {
                f.setAccessible(true);
                contextField = f;
            } else if (f.getType().getName().endsWith("AODBatteryManager")
                    && batteryManagerField == null) {
                f.setAccessible(true);
                batteryManagerField = f;
                for (Field bf : f.getType().getDeclaredFields()) {
                    if (!Modifier.isStatic(bf.getModifiers())
                            && bf.getType().getName().endsWith("BatteryData")) {
                        bf.setAccessible(true);
                        batteryDataField = bf;
                        break;
                    }
                }
            }
        }
        Method forceUpdate = null;
        try {
            List<DexSignatures.Body> matches =
                    DexSignatures.find(loader, Set.of(FORCE_UPDATE_MARKER)).stream()
                            .filter(
                                    body ->
                                            body.owner().equals(MANAGER)
                                                    && body.parameters().isEmpty()
                                                    && body.result().equals("V"))
                            .toList();
            if (matches.size() == 1) {
                forceUpdate = matches.get(0).method(loader);
                forceUpdate.setAccessible(true);
            }
        } catch (Throwable ignored) {
        }
        Class<?> itemClass = null;
        Method getExpanded = null;
        try {
            itemClass = Class.forName(ITEM_CLASS, false, loader);
            Method candidate = null;
            for (Method m : managerClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() == 0 && m.getReturnType() == itemClass) {
                    if (candidate != null) {
                        candidate = null;
                        break;
                    }
                    candidate = m;
                }
            }
            if (candidate != null) {
                candidate.setAccessible(true);
                getExpanded = candidate;
            }
        } catch (Throwable ignored) {
        }
        return new ManagerHandle(
                rawListField,
                contextField,
                batteryManagerField,
                batteryDataField,
                forceUpdate,
                getExpanded,
                itemClass);
    }

    private static ManagerState registerManager(Object manager, ManagerHandle handle) {
        if (manager == null) return null;
        synchronized (trackedManagers) {
            ManagerState existing = trackedManagers.get(manager);
            if (existing != null) return existing;
            ManagerState created = new ManagerState(handle);
            trackedManagers.put(manager, created);
            return created;
        }
    }

    private static Context resolveStackContext(Object manager, ManagerHandle handle) {
        if (stackHostContext != null) return stackHostContext;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context ctx) {
                stackHostContext = ctx;
                return ctx;
            }
        } catch (Throwable ignored) {
        }
        if (manager != null && handle != null && handle.contextField != null) {
            try {
                Object c = handle.contextField.get(manager);
                if (c instanceof Context ctx) {
                    Context appCtx = ctx.getApplicationContext();
                    stackHostContext = appCtx != null ? appCtx : ctx;
                    return stackHostContext;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void ensureStackObserver(Context ctx) {
        if (stackObserverBound || ctx == null) return;
        synchronized (DiyNowBarEffects.class) {
            if (stackObserverBound) return;
            stackObserverBound = true;
            Context appCtx = ctx.getApplicationContext();
            Context targetCtx = appCtx != null ? appCtx : ctx;
            if (stackHostContext == null) stackHostContext = targetCtx;
            reloadConfigsAsync(targetCtx, true);
            try {
                targetCtx
                        .getContentResolver()
                        .registerContentObserver(
                                SettingsProvider.URI,
                                false,
                                new ContentObserver(MAIN) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        reloadConfigsAsync(targetCtx, true);
                                    }
                                });
            } catch (Throwable error) {
                DressroomModule.note("Now Bar stack observer unavailable", error);
            }
            try {
                IntentFilter sysFilter = new IntentFilter();
                sysFilter.addAction(Intent.ACTION_SCREEN_OFF);
                sysFilter.addAction(Intent.ACTION_POWER_CONNECTED);
                sysFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
                targetCtx.registerReceiver(
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context c, Intent intent) {
                                String action = intent == null ? "" : intent.getAction();
                                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                                    DiyNowBarStackOrder.Config cfg = stackConfig;
                                    if (cfg == null || !cfg.enabled || !cfg.resetOnScreenOff) return;
                                    synchronized (trackedManagers) {
                                        for (ManagerState st : trackedManagers.values()) {
                                            if (st != null) st.lastItemSignature = "";
                                        }
                                    }
                                } else if (Intent.ACTION_POWER_CONNECTED.equals(action)
                                        || Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                                    lastKnownPlugged =
                                            Intent.ACTION_POWER_CONNECTED.equals(action);
                                    if (labsConfig != null && labsConfig.nativeBatteryCard) {
                                        MAIN.post(DiyNowBarEffects::refreshTrackedManagers);
                                    }
                                }
                            }
                        },
                        sysFilter,
                        Context.RECEIVER_NOT_EXPORTED);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void reloadConfigsAsync(Context ctx, boolean triggerRefresh) {
        IO.execute(
                () -> {
                    DiyNowBarStackOrder.Config nextStack = DiyNowBarStore.readStackOrder(ctx);
                    DiyNowBarStackOrder.Config prevStack = stackConfig;
                    stackConfig = nextStack;
                    boolean stackChanged =
                            prevStack == null
                                    || prevStack.enabled != nextStack.enabled
                                    || prevStack.revision != nextStack.revision
                                    || !prevStack.order.equals(nextStack.order);

                    DiyNowBarLabs nextLabs = DiyNowBarStore.readLabs(ctx);
                    DiyNowBarLabs prevLabs = labsConfig;
                    labsConfig = nextLabs;
                    labsLoadedOnce = true;
                    boolean labsChanged =
                            prevLabs == null
                                    || prevLabs.revision != nextLabs.revision
                                    || !prevLabs.sameContent(nextLabs);

                    if (triggerRefresh && (stackChanged || labsChanged)) {
                        MAIN.post(
                                () -> {
                                    refreshTrackedManagers();
                                    if (labsChanged) refreshAttachedCards();
                                });
                    }
                });
    }

    private static void refreshTrackedManagers() {
        List<Map.Entry<Object, ManagerState>> snapshot;
        synchronized (trackedManagers) {
            snapshot = new ArrayList<>(trackedManagers.entrySet());
        }
        DiyNowBarStackOrder.Config cfg = stackConfig;
        for (Map.Entry<Object, ManagerState> entry : snapshot) {
            Object manager = entry.getKey();
            ManagerState state = entry.getValue();
            if (manager == null || state == null) continue;
            state.lastAppliedRevision = -1L;
            if (cfg != null && cfg.enabled && !cfg.order.isEmpty()) {
                sortManagerRawList(manager, state.handle, cfg.order);
            }
            if (state.handle.forceUpdateMethod != null) {
                try {
                    state.handle.forceUpdateMethod.invoke(manager);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void refreshAttachedCards() {
        List<View> views;
        synchronized (attachments) {
            views = new ArrayList<>(attachments.keySet());
        }
        for (View v : views) {
            if (v != null && v.isAttachedToWindow()) {
                syncCard(v);
            }
        }
    }

    private static CopyOnWriteArrayList<Object> applyStackOrder(
            Object manager, ManagerState state, CopyOnWriteArrayList<Object> items) {
        DiyNowBarStackOrder.Config cfg = stackConfig;
        if (state == null
                || cfg == null
                || !cfg.enabled
                || cfg.order.isEmpty()
                || items.size() <= 1) {
            return items;
        }
        List<String> slotIds = new ArrayList<>(items.size());
        for (Object item : items) {
            slotIds.add(classifyRuntimeItem(item).slotId);
        }
        String multisetSig = DiyNowBarStackOrder.multisetSignature(slotIds);
        boolean configChanged = state.lastAppliedRevision != cfg.revision;
        boolean setChanged = !multisetSig.equals(state.lastItemSignature);
        if (!configChanged && !setChanged) {
            return items;
        }

        Object expanded = readExpandedItem(manager, state.handle);
        List<Object> ordered = reorderListWithExpandedPin(items, cfg.order, expanded);
        state.lastAppliedRevision = cfg.revision;
        state.lastItemSignature = multisetSig;
        sortManagerRawList(manager, state.handle, cfg.order);
        return new CopyOnWriteArrayList<>(ordered);
    }

    private static List<Object> reorderListWithExpandedPin(
            List<Object> items, List<String> preferredOrder, Object expanded) {
        List<Object> rest = new ArrayList<>(items.size());
        Object pinned = null;
        for (Object item : items) {
            if (expanded != null && sameNowBarItem(item, expanded) && pinned == null) {
                pinned = item;
            } else {
                rest.add(item);
            }
        }
        List<String> restSlots = new ArrayList<>(rest.size());
        for (Object item : rest) restSlots.add(classifyRuntimeItem(item).slotId);
        int[] perm = DiyNowBarStackOrder.sortIndices(restSlots, preferredOrder);
        List<Object> out = new ArrayList<>(items.size());
        if (pinned != null) out.add(pinned);
        for (int idx : perm) out.add(rest.get(idx));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void sortManagerRawList(
            Object manager, ManagerHandle handle, List<String> preferredOrder) {
        if (manager == null || handle == null || handle.rawListField == null) return;
        try {
            Object rawObj = handle.rawListField.get(manager);
            if (!(rawObj instanceof CopyOnWriteArrayList<?> rawList) || rawList.size() <= 1) return;
            Object expanded = readExpandedItem(manager, handle);
            synchronized (manager) {
                List<Object> snapshot = new ArrayList<>(rawList);
                List<Object> reordered =
                        reorderListWithExpandedPin(snapshot, preferredOrder, expanded);
                if (rawList.size() == reordered.size()) {
                    CopyOnWriteArrayList<Object> typed = (CopyOnWriteArrayList<Object>) rawList;
                    for (int i = 0; i < reordered.size(); i++) {
                        if (typed.get(i) != reordered.get(i)) {
                            typed.set(i, reordered.get(i));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object readExpandedItem(Object manager, ManagerHandle handle) {
        if (manager == null || handle == null || handle.getExpandedItemMethod == null) return null;
        try {
            return handle.getExpandedItemMethod.invoke(manager);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean sameNowBarItem(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        String ka = safeCallString(a, "getNowBarKey");
        String kb = safeCallString(b, "getNowBarKey");
        if (!ka.isEmpty() && ka.equals(kb)) return true;
        String na = safeCallString(a, "getNotiID");
        String nb = safeCallString(b, "getNotiID");
        return !na.isEmpty() && na.equals(nb);
    }

    private static void reportLiveCardsAsync(Context ctx, List<Object> items) {
        List<DiyNowBarStackOrder.LiveEntry> entries = new ArrayList<>(items.size());
        StringBuilder sig = new StringBuilder();
        for (Object item : items) {
            if (item == null) continue;
            DiyNowBarStackOrder.LiveEntry e = classifyRuntimeItem(item);
            if (e.slotId.isEmpty()) continue;
            entries.add(e);
            sig.append(e.slotId)
                    .append('|')
                    .append(e.label)
                    .append('|')
                    .append(e.subtitle)
                    .append(';');
        }
        String currentSig = sig.toString();
        if (currentSig.equals(lastReportedLiveSig)) return;
        lastReportedLiveSig = currentSig;
        IO.execute(() -> DiyNowBarStore.reportLiveStack(ctx, entries));
    }

    private static DiyNowBarStackOrder.LiveEntry classifyRuntimeItem(Object item) {
        if (item == null) {
            return new DiyNowBarStackOrder.LiveEntry("", "", "", "", "", 0, true);
        }
        String notiID = safeCallString(item, "getNotiID");
        String key = safeCallString(item, "getNowBarKey");
        String pkg = safeCallString(item, "getNowBarPackage");
        int style = safeCallInt(item, "getNowBarViewStyle", 0);
        boolean isCall = safeCallBoolean(item, "isCall");
        String pri = safeCallString(item, "getNowbarPrimaryInfo");
        if (pri.isEmpty()) pri = safeCallString(item, "getPrimaryInfo");
        String sec = safeCallString(item, "getNowbarSecondaryInfo");
        if (sec.isEmpty()) sec = safeCallString(item, "getSecondaryInfo");
        int iconBg = safeCallInt(item, "getCardIconBg", 0);

        String song = "";
        String artist = "";
        String mediaApp = "";
        String mediaPkg = "";
        try {
            Object media = Reflect.call(item, "getFaceWidgetMediaData");
            if (media != null) {
                song = safeCallString(media, "getSong");
                artist = safeCallString(media, "getArtist");
                mediaApp = safeCallString(media, "getApp");
                mediaPkg = safeCallString(media, "getPackageName");
            }
        } catch (Throwable ignored) {
        }
        return DiyNowBarStackOrder.classifyItem(
                notiID, key, pkg, style, isCall, pri, sec, song, artist, mediaApp, mediaPkg, iconBg);
    }

    private static String safeCallString(Object target, String method) {
        try {
            Object val = Reflect.call(target, method);
            return val == null ? "" : val.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int safeCallInt(Object target, String method, int fallback) {
        try {
            Object val = Reflect.call(target, method);
            return val instanceof Number n ? n.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean safeCallBoolean(Object target, String method) {
        try {
            Object val = Reflect.call(target, method);
            return Boolean.TRUE.equals(val);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Method resolveFilter(ClassLoader loader) throws ReflectiveOperationException {
        try {
            List<DexSignatures.Body> matches =
                    DexSignatures.find(loader, Set.of(FILTER_MARKER)).stream()
                            .filter(
                                    body ->
                                            body.owner().equals(MANAGER)
                                                    && body.parameters().equals(List.of(LIST))
                                                    && body.result().equals(LIST)
                                                    && body.calls().stream()
                                                            .anyMatch(
                                                                    call ->
                                                                            call.owner()
                                                                                            .equals(
                                                                                                    ITEM)
                                                                                    && call.name()
                                                                                            .equals(
                                                                                                    "getNotiID")
                                                                                    && call.parameters()
                                                                                            .isEmpty()))
                            .toList();
            if (matches.size() != 1) {
                throw new NoSuchMethodException(
                        "Expected unique NowBarItemManager filter method, found " + matches.size());
            }
            return matches.get(0).method(loader);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not resolve NowBarItemManager filter", error);
        }
    }

    // -------------------------------------------------------------------------
    // Ignore-Bouncer Direct Tap Hook (NowBarOngoingCardView.mo15853m)
    // -------------------------------------------------------------------------

    private static void installBouncerBypassHook(DressroomModule module, ClassLoader loader) {
        try {
            for (DexSignatures.Body body : DexSignatures.find(loader, Set.of("LSE4510"))) {
                if (ONGOING_DESC.equals(body.owner())
                        && body.parameters().isEmpty()
                        && "V".equals(body.result())) {
                    Method tapMethod = body.method(loader);
                    hookAndDeopt(
                            module,
                            tapMethod,
                            "dressroom-advanced.diy-nowbar-ignore-bouncer",
                            chain -> {
                                if (chain.getThisObject() instanceof View cardView
                                        && tryDirectBouncerFreeTap(cardView)) {
                                    return null;
                                }
                                return chain.proceed();
                            });
                    break;
                }
            }
        } catch (Throwable error) {
            DressroomModule.note("DIY Now Bar bouncer bypass hook unavailable", error);
        }
    }

    private static boolean tryDirectBouncerFreeTap(View cardView) {
        try {
            Object item = readCardItem(cardView);
            if (item == null) return false;
            String key = safeCallString(item, "getNowBarKey");
            if (!DiyNowBarCard.isDiyKey(key)) return false;
            Bundle extra = (Bundle) Reflect.call(item, "getExtraData");
            if (extra == null || !extra.getBoolean("nowbar_key_ignore_bouncer", false)) {
                return false;
            }
            Object piObj = Reflect.call(item, "getPendingIntent");
            if (!(piObj instanceof PendingIntent pi)) return false;
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            pi.send(cardView.getContext(), 0, null, null, null, null, opts.toBundle());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Long-Press Card Settings -> Open DIY Now Bar Studio Deep-Link
    // -------------------------------------------------------------------------

    private static void installLongPressStudioHook(DressroomModule module, ClassLoader loader) {
        try {
            Class<?> settingViewClass = Class.forName(SETTING_VIEW, false, loader);
            Class<?> itemClass = Class.forName(ITEM_CLASS, false, loader);
            Method bindIcon = settingViewClass.getDeclaredMethod("setNotificationIcon", itemClass);
            hookAndDeopt(
                    module,
                    bindIcon,
                    "dressroom-advanced.diy-nowbar-longpress-studio",
                    chain -> {
                        Object res = chain.proceed();
                        if (chain.getThisObject() instanceof View settingView) {
                            syncLongPressSettingsButton(settingView, chain.getArg(0));
                        }
                        return res;
                    });
        } catch (Throwable error) {
            DressroomModule.note("DIY Now Bar long-press Studio hook unavailable", error);
        }
    }

    private static void syncLongPressSettingsButton(View settingView, Object item) {
        try {
            View btn = findAodView(settingView, "nowbar_card_settings_noti_btn");
            if (btn == null) return;
            DiyNowBarLabs labs = ensureLabsSnapshot(settingView.getContext());
            String key = item == null ? "" : safeCallString(item, "getNowBarKey");
            boolean isDiy = DiyNowBarCard.isDiyKey(key);

            Object savedOrig = btn.getTag(TAG_ORIG_SETTINGS_CLICK);
            if (savedOrig == null) {
                View.OnClickListener current = readOnClickListener(btn);
                if (current != null) btn.setTag(TAG_ORIG_SETTINGS_CLICK, current);
            }

            if (isDiy && labs != null && labs.longPressOpensStudio) {
                String cardId = key.substring(DiyNowBarCard.KEY_PREFIX.length());
                btn.setOnClickListener(
                        v -> {
                            DiyNowBarLiveViews.launchEditor(v.getContext(), cardId, false);
                            dismissCardSettingsOverlay(settingView);
                        });
            } else if (btn.getTag(TAG_ORIG_SETTINGS_CLICK) instanceof View.OnClickListener orig) {
                btn.setOnClickListener(orig);
            }
        } catch (Throwable ignored) {
        }
    }

    private static View.OnClickListener readOnClickListener(View view) {
        try {
            Method getListenerInfo = View.class.getDeclaredMethod("getListenerInfo");
            getListenerInfo.setAccessible(true);
            Object info = getListenerInfo.invoke(view);
            if (info == null) return null;
            Field f = info.getClass().getDeclaredField("mOnClickListener");
            f.setAccessible(true);
            Object l = f.get(info);
            return l instanceof View.OnClickListener ocl ? ocl : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void dismissCardSettingsOverlay(View settingView) {
        try {
            Object listener = Reflect.call(settingView, "getCardSettingViewListener");
            if (listener == null) return;
            // Find BaseNowBarCardView stored on the listener wrapper and close settings
            for (Field f : listener.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object candidate = f.get(listener);
                if (candidate instanceof View cardView
                        && cardView.getClass().getName().contains("NowBar")) {
                    for (Method m : cardView.getClass().getSuperclass().getDeclaredMethods()) {
                        if (m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == boolean.class
                                && m.getReturnType() == void.class) {
                            // Best effort collapse; skip if ambiguous
                        }
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Card Shaders, Live Views, Secondary Mini-Icon & Icon Shape Hooks
    // -------------------------------------------------------------------------

    private static void installCardShaderHooks(DressroomModule module, ClassLoader loader) {
        try {
            Class<?> ongoing = Class.forName(ONGOING_VIEW, false, loader);
            Class<?> base = resolveBaseCardClass(ongoing, loader);
            Class<?> itemType = Class.forName(ITEM_CLASS, false, loader);

            Method onAttach = ongoing.getDeclaredMethod("onAttachedToWindow");
            hookAndDeopt(
                    module,
                    onAttach,
                    "dressroom-advanced.diy-nowbar-attach",
                    chain -> {
                        Object res = chain.proceed();
                        if (chain.getThisObject() instanceof View view) syncCard(view);
                        return res;
                    });

            Method onDetach = ongoing.getDeclaredMethod("onDetachedFromWindow");
            hookAndDeopt(
                    module,
                    onDetach,
                    "dressroom-advanced.diy-nowbar-detach",
                    chain -> {
                        if (chain.getThisObject() instanceof View view) detachCard(view);
                        return chain.proceed();
                    });

            Method bindItem = resolveMethod(base, void.class, itemType, Integer.class);
            if (bindItem != null) {
                hookAndDeopt(
                        module,
                        bindItem,
                        "dressroom-advanced.diy-nowbar-bind",
                        chain -> {
                            Object res = chain.proceed();
                            if (chain.getThisObject() instanceof View view
                                    && ongoing.isInstance(view)) {
                                syncCard(view);
                            }
                            return res;
                        });
            }

            Method colorChanged =
                    resolveMethod(ongoing, void.class, boolean.class, boolean.class, boolean.class);
            if (colorChanged == null) {
                colorChanged =
                        resolveMethod(base, void.class, boolean.class, boolean.class, boolean.class);
            }
            if (colorChanged != null) {
                hookAndDeopt(
                        module,
                        colorChanged,
                        "dressroom-advanced.diy-nowbar-color",
                        chain -> {
                            Object res = chain.proceed();
                            if (chain.getThisObject() instanceof View view
                                    && ongoing.isInstance(view)) {
                                syncCard(view);
                            }
                            return res;
                        });
            }

            Method setDepth = resolveMethod(base, void.class, int.class, "setCardDepth");
            if (setDepth != null) {
                hookAndDeopt(
                        module,
                        setDepth,
                        "dressroom-advanced.diy-nowbar-depth",
                        chain -> {
                            Object res = chain.proceed();
                            if (chain.getThisObject() instanceof View view
                                    && ongoing.isInstance(view)) {
                                syncDarkAndDepth(view);
                            }
                            return res;
                        });
            }

            // Synchronous geometry sync on every expand/collapse animation frame (0-frame lag)
            for (DexSignatures.Body body :
                    DexSignatures.find(loader, Set.of("onShapeAnimationValueChanged isExpanded "))) {
                if (ONGOING_DESC.equals(body.owner())
                        && body.parameters().isEmpty()
                        && "V".equals(body.result())) {
                    Method shapeChanged = body.method(loader);
                    hookAndDeopt(
                            module,
                            shapeChanged,
                            "dressroom-advanced.diy-nowbar-shape",
                            chain -> {
                                Object res = chain.proceed();
                                if (chain.getThisObject() instanceof View view) {
                                    syncCardGeometry(view);
                                }
                                return res;
                            });
                    break;
                }
            }
        } catch (Throwable error) {
            DressroomModule.note("DIY Now Bar shader hooks unavailable", error);
        }
    }

    private static Class<?> resolveBaseCardClass(Class<?> ongoing, ClassLoader loader)
            throws ClassNotFoundException {
        Class<?> sup = ongoing.getSuperclass();
        if (sup != null && !Object.class.equals(sup) && !FrameLayout.class.equals(sup)) {
            return sup;
        }
        return Class.forName(BASE_VIEW, false, loader);
    }

    private static Method resolveMethod(Class<?> owner, Class<?> returnType, Class<?>... params) {
        Method match = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getReturnType() != returnType) continue;
            if (!java.util.Arrays.equals(m.getParameterTypes(), params)) continue;
            if (match != null) return null;
            match = m;
        }
        return match;
    }

    private static Method resolveMethod(
            Class<?> owner, Class<?> returnType, Class<?> param, String name) {
        try {
            Method m = owner.getDeclaredMethod(name, param);
            return m.getReturnType() == returnType ? m : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void hookAndDeopt(
            DressroomModule module,
            Method method,
            String id,
            XposedInterface.Hooker hooker) {
        module.hook(method).setId(id).intercept(hooker);
        module.deoptimize(method);
    }

    private static Object readCardItem(View cardView) {
        Object item = null;
        try {
            item = Reflect.call(cardView, "getNowbarItem");
        } catch (Throwable ignored) {
        }
        if (item == null) {
            try {
                item = Reflect.call(cardView, "getItem");
            } catch (Throwable ignored) {
            }
        }
        return item;
    }

    private static void syncCard(View cardView) {
        try {
            Object item = readCardItem(cardView);
            if (item == null) {
                clearAttachment(cardView);
                return;
            }
            String key = safeCallString(item, "getNowBarKey");
            boolean isDiy = DiyNowBarCard.isDiyKey(key);
            DiyNowBarLabs labs = ensureLabsSnapshot(cardView.getContext());

            int effect;
            int color1;
            int color2;
            Bundle extra = null;
            try {
                extra = (Bundle) Reflect.call(item, "getExtraData");
            } catch (Throwable ignored) {
            }

            if (isDiy) {
                effect =
                        extra == null
                                ? DiyNowBarCard.EFFECT_RADIAL_GLOW
                                : extra.getInt(
                                        DiyNowBarCard.EXTRA_EFFECT,
                                        DiyNowBarCard.EFFECT_RADIAL_GLOW);
                color1 =
                        extra == null
                                ? 0xFF4E8CFF
                                : extra.getInt(DiyNowBarCard.EXTRA_COLOR_1, 0xFF4E8CFF);
                color2 =
                        extra == null
                                ? 0xFFA86BFF
                                : extra.getInt(DiyNowBarCard.EXTRA_COLOR_2, 0xFFA86BFF);
            } else {
                if (labs == null || labs.globalCardShader == DiyNowBarCard.EFFECT_NONE) {
                    clearAttachment(cardView);
                    return;
                }
                effect = labs.globalCardShader;
                color1 = labs.globalColor1;
                color2 = labs.globalColor2;
            }

            AuraAttachment state = attachments.get(cardView);
            if (state == null) {
                state = new AuraAttachment();
                attachments.put(cardView, state);
            }

            if (isDiy) {
                int iconShape =
                        extra == null
                                ? DiyNowBarCard.SHAPE_SQUIRCLE
                                : extra.getInt(
                                        DiyNowBarCard.EXTRA_ICON_SHAPE,
                                        DiyNowBarCard.SHAPE_SQUIRCLE);
                int iconType =
                        extra == null
                                ? DiyNowBarCard.ICON_SYMBOL
                                : extra.getInt(
                                        DiyNowBarCard.EXTRA_ICON_TYPE, DiyNowBarCard.ICON_SYMBOL);
                applyCollapsedIconShape(cardView, state, iconShape, iconType);
                applySecondaryMiniIcon(cardView, extra);
                state.liveView = DiyNowBarLiveViews.syncLiveExpandedView(cardView, extra);

                Object gradient = Reflect.call(cardView, "getGradientBgView");
                if (gradient instanceof View gradView) {
                    boolean showNativeRadial = effect == DiyNowBarCard.EFFECT_RADIAL_GLOW;
                    try {
                        Reflect.call(gradient, "setGradientBackgroundVisible", showNativeRadial);
                    } catch (Throwable ignored) {
                        gradView.setVisibility(showNativeRadial ? View.VISIBLE : View.GONE);
                    }
                }
            } else {
                DiyNowBarLiveViews.clearLiveExpandedView(cardView);
                state.liveView = null;
            }

            if (effect == DiyNowBarCard.EFFECT_NONE || effect == DiyNowBarCard.EFFECT_RADIAL_GLOW) {
                DiyNowBarNativeShaders.release(cardView, state.nativeState);
                state.usingNativeShader = false;
                clearAuraViewsOnly(state);
                syncDarkAndDepth(cardView);
                return;
            }

            Object containerObj = Reflect.call(cardView, "getCardViewContainer");
            if (!(containerObj instanceof ViewGroup container)) {
                DiyNowBarNativeShaders.release(cardView, state.nativeState);
                state.usingNativeShader = false;
                clearAuraViewsOnly(state);
                return;
            }

            state.effect = effect;
            state.color1 = color1;
            state.color2 = color2;

            Context ctx = cardView.getContext();
            float density = ctx.getResources().getDisplayMetrics().density;
            float radius = readRadius(cardView, container, density * 24f);
            float outerInset = density * 12f;
            float dark = readDarkAmount(cardView);
            int depth = readCardDepth(cardView);

            // Try Samsung's real native GPU shader engine first (AI Mesh Warp / Guiding Light)
            boolean nativeBound =
                    DiyNowBarNativeShaders.isNativeCapableEffect(effect)
                            && DiyNowBarNativeShaders.sync(
                                    cardView,
                                    container,
                                    state.nativeState,
                                    effect,
                                    color1,
                                    color2,
                                    radius,
                                    dark,
                                    depth);
            state.usingNativeShader = nativeBound;

            if (nativeBound) {
                removeViewSafe(state.innerView);
                removeViewSafe(state.outerView);
                state.innerView = null;
                state.outerView = null;
            } else {
                DiyNowBarNativeShaders.release(cardView, state.nativeState);
                if (effect == DiyNowBarCard.EFFECT_OUTER_GLOW) {
                    removeViewSafe(state.innerView);
                    state.innerView = null;
                    if (cardView instanceof ViewGroup rootGroup) {
                        rootGroup.setClipChildren(false);
                        rootGroup.setClipToPadding(false);
                        if (state.outerView == null) {
                            state.outerView = new DiyNowBarAuraView(ctx);
                            rootGroup.addView(state.outerView, 0);
                        }
                        state.outerView.configure(effect, color1, color2, radius, outerInset);
                        syncOuterBounds(cardView, state.outerView, container, radius, outerInset);
                    }
                } else {
                    removeViewSafe(state.outerView);
                    state.outerView = null;
                    if (state.innerView == null) {
                        state.innerView = new DiyNowBarAuraView(ctx);
                        container.addView(
                                state.innerView,
                                new FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    state.innerView.configure(effect, color1, color2, radius, 0f);
                }
            }

            bindLayoutTracker(cardView, container, state, outerInset);
            syncDarkAndDepth(cardView);
        } catch (Throwable error) {
            DressroomModule.note("DIY Now Bar shader sync failed", error);
        }
    }

    static void syncCardGeometry(View cardView) {
        if (cardView == null) return;
        AuraAttachment state = attachments.get(cardView);
        if (state == null) return;
        View container = state.trackedContainer;
        if (container == null) {
            try {
                Object c = Reflect.call(cardView, "getCardViewContainer");
                if (c instanceof View v) container = v;
            } catch (Throwable ignored) {
            }
        }
        if (container == null) return;
        float density = container.getResources().getDisplayMetrics().density;
        float radius = readRadius(cardView, container, density * 24f);
        float outerInset = density * 12f;
        if (state.usingNativeShader) {
            DiyNowBarNativeShaders.updateGeometry(
                    cardView,
                    container,
                    state.nativeState,
                    radius,
                    readDarkAmount(cardView),
                    readCardDepth(cardView));
        }
        if (state.innerView != null) {
            state.innerView.setCornerRadiusPx(radius);
        }
        if (state.outerView != null) {
            syncOuterBounds(cardView, state.outerView, container, radius, outerInset);
        }
    }

    @SuppressWarnings("deprecation")
    private static void applySecondaryMiniIcon(View cardView, Bundle extra) {
        try {
            View subIconView = findAodView(cardView, "nowbar_sub_text_icon_on_normal_card");
            if (!(subIconView instanceof ImageView iv)) return;
            Icon icon =
                    extra == null
                            ? null
                            : extra.getParcelable("nowbar_key_second_info_icon", Icon.class);
            if (icon != null) {
                iv.setImageIcon(icon);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void bindLayoutTracker(
            View cardView, ViewGroup container, AuraAttachment state, float outerInset) {
        if (state.trackedContainer == container && state.layoutListener != null) return;
        if (state.trackedContainer != null && state.layoutListener != null) {
            state.trackedContainer.removeOnLayoutChangeListener(state.layoutListener);
        }
        state.trackedContainer = container;
        state.layoutListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                        return;
                    }
                    float density = v.getResources().getDisplayMetrics().density;
                    float radius = readRadius(cardView, v, density * 24f);
                    if (state.usingNativeShader) {
                        DiyNowBarNativeShaders.updateGeometry(
                                cardView,
                                v,
                                state.nativeState,
                                radius,
                                readDarkAmount(cardView),
                                readCardDepth(cardView));
                    }
                    if (state.innerView != null) {
                        state.innerView.setCornerRadiusPx(radius);
                    }
                    if (state.outerView != null) {
                        syncOuterBounds(cardView, state.outerView, v, radius, outerInset);
                    }
                };
        container.addOnLayoutChangeListener(state.layoutListener);
    }

    private static void syncOuterBounds(
            View cardView, DiyNowBarAuraView outer, View container, float radius, float inset) {
        int cw = 0;
        int ch = 0;
        try {
            Object wObj = Reflect.call(cardView, "getCurrentWidth");
            Object hObj = Reflect.call(cardView, "getCurrentHeight");
            if (wObj instanceof Number nw && hObj instanceof Number nh) {
                cw = Math.round(nw.floatValue());
                ch = Math.round(nh.floatValue());
            }
        } catch (Throwable ignored) {
        }
        if (cw <= 0 || ch <= 0) {
            cw = container.getWidth();
            ch = container.getHeight();
        }
        if (cw <= 0 || ch <= 0) {
            ViewGroup.LayoutParams lp = container.getLayoutParams();
            if (lp != null && lp.width > 0 && lp.height > 0) {
                cw = lp.width;
                ch = lp.height;
            }
        }
        if (cw <= 0 || ch <= 0) return;
        int pad = Math.round(inset);
        int targetW = cw + pad * 2;
        int targetH = ch + pad * 2;
        ViewGroup.LayoutParams raw = outer.getLayoutParams();
        FrameLayout.LayoutParams flp =
                raw instanceof FrameLayout.LayoutParams f
                        ? f
                        : new FrameLayout.LayoutParams(targetW, targetH);
        if (flp.width != targetW
                || flp.height != targetH
                || flp.bottomMargin != -pad
                || flp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)) {
            flp.width = targetW;
            flp.height = targetH;
            flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            flp.bottomMargin = -pad;
            outer.setLayoutParams(flp);
        }
        outer.setCornerRadiusPx(radius);
    }

    private static float readDarkAmount(View cardView) {
        try {
            Object darkObj = Reflect.call(cardView, "getDarkAmount");
            if (darkObj instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    private static int readCardDepth(View cardView) {
        try {
            Object depthObj = Reflect.call(cardView, "getCardDepth");
            if (depthObj instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void syncDarkAndDepth(View cardView) {
        AuraAttachment state = attachments.get(cardView);
        if (state == null) return;
        try {
            float dark = readDarkAmount(cardView);
            int depth = readCardDepth(cardView);
            int vis = depth == 0 ? View.VISIBLE : View.INVISIBLE;
            if (state.usingNativeShader) {
                DiyNowBarNativeShaders.syncVisibility(cardView, state.nativeState, dark, depth);
            }
            if (state.innerView != null) {
                state.innerView.setDarkAmount(dark);
                state.innerView.setVisibility(vis);
            }
            if (state.outerView != null) {
                state.outerView.setDarkAmount(dark);
                state.outerView.setVisibility(vis);
            }
            if (state.liveView != null) {
                DiyNowBarLiveViews.applyDarkAmount(state.liveView, dark);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float readRadius(View cardView, View container, float fallback) {
        try {
            Object r = Reflect.call(cardView, "getCurrentRadius");
            if (r instanceof Number n && n.floatValue() > 0f) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Object r = Reflect.call(container, "getRadius");
            if (r instanceof Number n && n.floatValue() > 0f) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void detachCard(View cardView) {
        AuraAttachment state = attachments.get(cardView);
        if (state == null) return;
        if (state.usingNativeShader) {
            DiyNowBarNativeShaders.syncVisibility(cardView, state.nativeState, 1f, 1);
        }
        if (state.innerView != null) state.innerView.setDarkAmount(1f);
        if (state.outerView != null) state.outerView.setDarkAmount(1f);
    }

    private static void applyCollapsedIconShape(
            View cardView, AuraAttachment state, int iconShape, int iconType) {
        try {
            View iconContainer = findAodView(cardView, "ongoing_app_icon_container");
            if (iconContainer == null) return;
            if (state.originalIconRadius < 0f) {
                Object r = Reflect.call(iconContainer, "getRadius");
                if (r instanceof Number n) state.originalIconRadius = n.floatValue();
            }
            state.iconShape = iconShape;
            updateIconContainerRadius(iconContainer, state);
            if (iconType != DiyNowBarCard.ICON_SYMBOL) {
                Reflect.call(
                        iconContainer,
                        "setCardBackgroundColor",
                        android.graphics.Color.TRANSPARENT);
            }
            if (state.trackedIconContainer != iconContainer) {
                if (state.trackedIconContainer != null && state.iconLayoutListener != null) {
                    state.trackedIconContainer.removeOnLayoutChangeListener(
                            state.iconLayoutListener);
                }
                state.trackedIconContainer = iconContainer;
                state.iconLayoutListener =
                        (v, l, t, r, b, ol, ot, or2, ob) -> updateIconContainerRadius(v, state);
                iconContainer.addOnLayoutChangeListener(state.iconLayoutListener);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void updateIconContainerRadius(View iconContainer, AuraAttachment state) {
        try {
            if (state.iconShape == DiyNowBarCard.SHAPE_CIRCLE) {
                int size = Math.min(iconContainer.getWidth(), iconContainer.getHeight());
                float radius =
                        size > 0
                                ? size * 0.5f
                                : iconContainer.getResources().getDisplayMetrics().density * 18f;
                Reflect.call(iconContainer, "setRadius", radius);
            } else if (state.originalIconRadius >= 0f) {
                Reflect.call(iconContainer, "setRadius", state.originalIconRadius);
            }
        } catch (Throwable ignored) {
        }
    }

    private static View findAodView(View root, String idName) {
        Resources res = root.getResources();
        int id = res.getIdentifier(idName, "id", PKG_AOD);
        if (id == 0) {
            id = res.getIdentifier(idName, "id", root.getContext().getPackageName());
        }
        return id == 0 ? null : root.findViewById(id);
    }

    private static void clearAuraViewsOnly(AuraAttachment state) {
        if (state == null) return;
        if (state.trackedContainer != null && state.layoutListener != null) {
            state.trackedContainer.removeOnLayoutChangeListener(state.layoutListener);
            state.trackedContainer = null;
            state.layoutListener = null;
        }
        removeViewSafe(state.innerView);
        removeViewSafe(state.outerView);
        state.innerView = null;
        state.outerView = null;
    }

    private static void clearAttachment(View cardView) {
        AuraAttachment state = attachments.remove(cardView);
        if (state == null) return;
        DiyNowBarNativeShaders.release(cardView, state.nativeState);
        DiyNowBarLiveViews.clearLiveExpandedView(cardView);
        state.liveView = null;
        clearAuraViewsOnly(state);
        if (state.trackedIconContainer != null) {
            if (state.iconLayoutListener != null) {
                state.trackedIconContainer.removeOnLayoutChangeListener(state.iconLayoutListener);
            }
            if (state.originalIconRadius >= 0f) {
                try {
                    Reflect.call(state.trackedIconContainer, "setRadius", state.originalIconRadius);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void removeViewSafe(View view) {
        if (view != null && view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }
}
