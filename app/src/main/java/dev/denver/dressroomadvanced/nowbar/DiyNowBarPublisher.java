package dev.denver.dressroomadvanced;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Publishes active DIY Now Bar cards into Samsung's FaceWidgetNotificationControllerWrapper
 * inside SystemUI and handles zero-unlock lockscreen/AOD quick actions in-process.
 */
final class DiyNowBarPublisher {
    private static final String ACTION_INTERNAL =
            "dev.denver.dressroomadvanced.internal.DIY_NOWBAR_ACTION";
    private static final String EXTRA_CARD_ID = "card_id";
    private static final String EXTRA_OP = "op";
    private static final String EXTRA_INDEX = "index";
    private static final long LIVE_TICK_MS = 30_000L;

    /** Runtime resource IDs resolved from the module APK (no compile-time R.java needed). */
    private static final class DiyRes {
        final int layoutChrono;
        final int layoutExpand;
        final int idChrono;
        final int idExpandIcon;
        final int idExpandTitle;
        final int idExpandSubtitle;
        final int idExpandBadge;
        final int idExpandEditBtn;
        final int idExpandChrono;
        final int idExpandBody;
        final int idExpandProgressGroup;
        final int idExpandProgressLabel;
        final int idExpandProgressValue;
        final int idExpandProgress;
        final int idStatsGrid;
        final int idStatsRow2;
        final int idCheckGroup;
        final int idLaunchRow;
        final int idActionRow;
        final int[] statTileIds = new int[4];
        final int[] statLabelIds = new int[4];
        final int[] statValueIds = new int[4];
        final int[] checkRowIds = new int[DiyNowBarCard.MAX_CHECKLIST];
        final int[] checkIconIds = new int[DiyNowBarCard.MAX_CHECKLIST];
        final int[] checkTextIds = new int[DiyNowBarCard.MAX_CHECKLIST];
        final int[] launchTileIds = new int[DiyNowBarCard.MAX_SHORTCUTS];
        final int[] launchIconIds = new int[DiyNowBarCard.MAX_SHORTCUTS];
        final int[] launchLabelIds = new int[DiyNowBarCard.MAX_SHORTCUTS];
        final int[] btnIds = new int[3];

        private DiyRes(Resources r) {
            layoutChrono = layout(r, "diy_nowbar_chrono");
            layoutExpand = layout(r, "diy_nowbar_expand");
            idChrono = id(r, "diy_chrono");
            idExpandIcon = id(r, "diy_expand_icon");
            idExpandTitle = id(r, "diy_expand_title");
            idExpandSubtitle = id(r, "diy_expand_subtitle");
            idExpandBadge = id(r, "diy_expand_badge");
            idExpandEditBtn = id(r, "diy_expand_edit_btn");
            idExpandChrono = id(r, "diy_expand_chrono");
            idExpandBody = id(r, "diy_expand_body");
            idExpandProgressGroup = id(r, "diy_expand_progress_group");
            idExpandProgressLabel = id(r, "diy_expand_progress_label");
            idExpandProgressValue = id(r, "diy_expand_progress_value");
            idExpandProgress = id(r, "diy_expand_progress");
            idStatsGrid = id(r, "diy_stats_grid");
            idStatsRow2 = id(r, "diy_stats_row_2");
            idCheckGroup = id(r, "diy_check_group");
            idLaunchRow = id(r, "diy_launch_row");
            idActionRow = id(r, "diy_action_row");
            for (int i = 0; i < 4; i++) {
                statTileIds[i] = id(r, "diy_stat_" + i);
                statLabelIds[i] = id(r, "diy_stat_" + i + "_label");
                statValueIds[i] = id(r, "diy_stat_" + i + "_value");
            }
            for (int i = 0; i < DiyNowBarCard.MAX_CHECKLIST; i++) {
                checkRowIds[i] = id(r, "diy_check_row_" + i);
                checkIconIds[i] = id(r, "diy_check_icon_" + i);
                checkTextIds[i] = id(r, "diy_check_text_" + i);
            }
            for (int i = 0; i < DiyNowBarCard.MAX_SHORTCUTS; i++) {
                launchTileIds[i] = id(r, "diy_launch_" + i);
                launchIconIds[i] = id(r, "diy_launch_icon_" + i);
                launchLabelIds[i] = id(r, "diy_launch_label_" + i);
            }
            for (int i = 0; i < 3; i++) {
                btnIds[i] = id(r, "diy_btn_" + i);
            }
        }

        private static int layout(Resources r, String name) {
            return r.getIdentifier(name, "layout", DiyNowBarCard.PACKAGE_ID);
        }

        private static int id(Resources r, String name) {
            return r.getIdentifier(name, "id", DiyNowBarCard.PACKAGE_ID);
        }

        boolean valid() {
            return layoutExpand != 0 && layoutChrono != 0 && idExpandTitle != 0;
        }
    }

    private static DiyRes cachedRes;

    private static DiyRes resolveRes(Context ctx) {
        if (cachedRes != null && cachedRes.valid()) return cachedRes;
        try {
            Context modCtx =
                    ctx.createPackageContext(
                            DiyNowBarCard.PACKAGE_ID, Context.CONTEXT_IGNORE_SECURITY);
            DiyRes r = new DiyRes(modCtx.getResources());
            if (r.valid()) cachedRes = r;
            return r;
        } catch (Throwable error) {
            android.util.Log.w(DressroomModule.TAG, "Could not resolve DIY Now Bar resources", error);
            return null;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Object> publishedItems = new LinkedHashMap<>();
    private static final Map<String, String> publishedSignatures = new LinkedHashMap<>();

    private static WeakReference<Object> hostRef = new WeakReference<>(null);
    private static Context appContext;
    private static ClassLoader hostLoader;
    private static boolean receiversBound;
    private static boolean torchOn;
    private static boolean needsPeriodicTick;
    private static boolean hasStatsCard;

    private static final Runnable liveTick =
            new Runnable() {
                @Override
                public void run() {
                    if (!needsPeriodicTick || appContext == null) return;
                    PowerManager pm = appContext.getSystemService(PowerManager.class);
                    if (pm != null && pm.isInteractive()) {
                        syncInternal(false);
                        MAIN.postDelayed(this, LIVE_TICK_MS);
                    }
                }
            };

    private DiyNowBarPublisher() {}

    static synchronized List<Object> activeItems() {
        return new ArrayList<>(publishedItems.values());
    }

    static synchronized void resetHost() {
        publishedItems.clear();
        publishedSignatures.clear();
    }

    static void sync(Object host, Context context, ClassLoader loader) {
        if (host == null || context == null || loader == null) return;
        synchronized (DiyNowBarPublisher.class) {
            hostRef = new WeakReference<>(host);
            appContext = resolveHostProcessContext(context);
            hostLoader = loader;
            ensureReceivers(appContext);
        }
        syncInternal(false);
    }

    /**
     * initPlugin passes AOD's pluginContext (com.samsung.android.app.aodservice), but this code
     * runs inside com.android.systemui. Registering our internal BroadcastReceiver and creating
     * broadcast PendingIntents on the host process Application guarantees broadcasts route back
     * to our live in-process receiver.
     */
    private static Context resolveHostProcessContext(Context fallback) {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method curApp = at.getMethod("currentApplication");
            Object app = curApp.invoke(null);
            if (app instanceof Context ctx) return ctx;
        } catch (Throwable ignored) {
        }
        try {
            return fallback.createPackageContext(
                    "com.android.systemui", Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable ignored) {
        }
        Context appCtx = fallback.getApplicationContext();
        return appCtx != null ? appCtx : fallback;
    }

    private static void ensureReceivers(Context ctx) {
        if (receiversBound) return;
        receiversBound = true;
        android.util.Log.i(
                DressroomModule.TAG,
                "DIY Now Bar action receiver bound on " + ctx.getPackageName());
        ctx.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        handleActionIntent(c, intent);
                    }
                },
                new IntentFilter(ACTION_INTERNAL),
                Context.RECEIVER_NOT_EXPORTED);

        IntentFilter sysFilter = new IntentFilter();
        sysFilter.addAction(Intent.ACTION_SCREEN_ON);
        sysFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        ctx.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        String action = intent == null ? "" : intent.getAction();
                        if (Intent.ACTION_SCREEN_ON.equals(action)) {
                            if (needsPeriodicTick) {
                                MAIN.removeCallbacks(liveTick);
                                MAIN.post(liveTick);
                            }
                        } else if (Intent.ACTION_BATTERY_CHANGED.equals(action) && hasStatsCard) {
                            MAIN.post(() -> syncInternal(false));
                        }
                    }
                },
                sysFilter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    private static void handleActionIntent(Context context, Intent intent) {
        if (intent == null) return;
        String op = intent.getStringExtra(EXTRA_OP);
        String cardId = intent.getStringExtra(EXTRA_CARD_ID);
        int index = intent.getIntExtra(EXTRA_INDEX, -1);
        if ("flashlight".equals(op)) {
            toggleFlashlight(context);
            return;
        }
        if (cardId != null && !cardId.isEmpty() && op != null && !op.isEmpty()) {
            boolean changed = DiyNowBarStore.mutate(context, cardId, op, index);
            if (changed) MAIN.post(() -> syncInternal(true));
        }
    }

    private static void toggleFlashlight(Context context) {
        try {
            CameraManager cm = context.getSystemService(CameraManager.class);
            if (cm == null) return;
            for (String cameraId : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                Boolean hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash)
                        && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    torchOn = !torchOn;
                    cm.setTorchMode(cameraId, torchOn);
                    return;
                }
            }
        } catch (Throwable error) {
            android.util.Log.w(DressroomModule.TAG, "Torch toggle unavailable", error);
        }
    }

    private static synchronized void syncInternal(boolean force) {
        Object host = hostRef.get();
        Context ctx = appContext;
        ClassLoader loader = hostLoader;
        if (host == null || ctx == null || loader == null) return;
        try {
            List<DiyNowBarCard> allCards = DiyNowBarStore.read(ctx);
            List<DiyNowBarCard> active = new ArrayList<>();
            boolean wantsTick = false;
            boolean statsPresent = false;
            long now = System.currentTimeMillis();
            for (DiyNowBarCard card : allCards) {
                if (card == null || !card.enabled) continue;
                card.sanitize();
                active.add(card);
                if (card.type == DiyNowBarCard.TYPE_STATS) {
                    wantsTick = true;
                    statsPresent = true;
                } else if (card.type == DiyNowBarCard.TYPE_CONTROLS) {
                    wantsTick = true;
                } else if (card.type == DiyNowBarCard.TYPE_TIMER
                        && card.timerMode == DiyNowBarCard.TIMER_COUNTDOWN
                        && !card.useLiveChronometer(now)) {
                    wantsTick = true;
                }
            }
            needsPeriodicTick = wantsTick;
            hasStatsCard = statsPresent;
            MAIN.removeCallbacks(liveTick);
            if (wantsTick) MAIN.postDelayed(liveTick, LIVE_TICK_MS);

            StatsSnapshot stats = statsPresent ? collectStats(ctx) : null;
            if (stats != null) DiyNowBarLiveViews.recordTelemetrySample(stats);
            Set<String> keepIds = new LinkedHashSet<>();
            Class<?> itemClass =
                    Class.forName(
                            "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                            false,
                            loader);

            for (DiyNowBarCard card : active) {
                keepIds.add(card.id);
                String sig = buildSignature(card, stats, now);
                if (!force && sig.equals(publishedSignatures.get(card.id))) continue;
                Object item = buildNowBarItem(ctx, itemClass, card, stats, now);
                Object previous = publishedItems.get(card.id);
                publishedItems.put(card.id, item);
                try {
                    Reflect.call(host, "updateItem", item);
                    publishedSignatures.put(card.id, sig);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    if (previous != null) publishedItems.put(card.id, previous);
                    else publishedItems.remove(card.id);
                    throw error;
                }
            }

            List<String> stale = new ArrayList<>();
            for (String existingId : publishedItems.keySet()) {
                if (!keepIds.contains(existingId)) stale.add(existingId);
            }
            for (String id : stale) {
                Object old = publishedItems.remove(id);
                publishedSignatures.remove(id);
                if (old != null) {
                    try {
                        Reflect.call(host, "removeItem", old);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable error) {
            android.util.Log.w(DressroomModule.TAG, "DIY Now Bar sync unavailable", error);
        }
    }

    private static Object buildNowBarItem(
            Context ctx, Class<?> itemClass, DiyNowBarCard card, StatsSnapshot stats, long now)
            throws ReflectiveOperationException {
        Object item = itemClass.getConstructor().newInstance();
        int userId = android.os.Process.myUid() / 100000;

        String pillTitle = resolvePillTitle(card, stats, now);
        String pillSub = resolvePillSubtitle(ctx, card, stats, now);
        Icon icon = DiyNowBarIcons.pillIcon(ctx, card);

        Reflect.call(item, "setNotiID", card.notiKey());
        Reflect.call(item, "setNowBarKey", card.nowBarKey());
        Reflect.call(item, "setNowBarPackage", DiyNowBarCard.PACKAGE_ID);
        Reflect.call(item, "setNowBarViewStyle", 0); // UNDEFINED -> NowBarOngoingCardView
        Reflect.call(item, "setUserId", userId);
        Reflect.call(item, "setCardIcon", icon);
        Reflect.call(item, "setNowbarIcon", icon);
        Reflect.call(item, "setCardIconBg", card.accentColor);
        Reflect.call(item, "setChipBackground", card.accentColor);
        Reflect.call(item, "setPrimaryInfo", pillTitle);
        Reflect.call(item, "setNowbarPrimaryInfo", pillTitle);
        Reflect.call(item, "setSecondaryInfo", pillSub);
        Reflect.call(item, "setNowbarSecondaryInfo", pillSub);

        Bundle extra = new Bundle();
        extra.putInt("nowbar_key_screen_type", 3);
        if (card.iconShape == DiyNowBarCard.SHAPE_SQUIRCLE) {
            extra.putString("nowbar_key_nowbar_icon_type", "squircle");
        }
        if (card.iconType == DiyNowBarCard.ICON_SYMBOL) {
            extra.putBoolean("nowbar_key_only_small_icon", true);
        } else {
            extra.putBoolean("nowbar_key_only_app_icon", true);
            extra.putParcelable("nowbar_key_app_icon", icon);
        }
        if (card.hideOnLock) {
            extra.putBoolean("nowbar_key_need_to_hide_content", true);
        }
        if (card.effect == DiyNowBarCard.EFFECT_GRADIENT_FILL) {
            extra.putIntArray(
                    "nowbar_key_linear_gradient",
                    new int[] {card.effectColor1, card.effectColor2});
        }
        if (!card.secondaryIcon.isEmpty()) {
            Icon subIcon =
                    Icon.createWithBitmap(
                            DiyNowBarIcons.drawSymbolGlyph(card.secondaryIcon, 40, Color.WHITE));
            extra.putParcelable("nowbar_key_second_info_icon", subIcon);
        }
        if (card.ignoreBouncer) {
            extra.putBoolean("nowbar_key_ignore_bouncer", true);
        }
        boolean useFullscreen = card.fullscreenExpand && card.expandable;
        if (useFullscreen) {
            extra.putBoolean("nowbar_key_has_fullscreen_card", true);
            extra.putBoolean("nowbar_key_need_to_fullview", true);
        }
        extra.putInt(DiyNowBarCard.EXTRA_EFFECT, card.effect);
        extra.putInt(DiyNowBarCard.EXTRA_COLOR_1, card.effectColor1);
        extra.putInt(DiyNowBarCard.EXTRA_COLOR_2, card.effectColor2);
        extra.putInt(DiyNowBarCard.EXTRA_ACCENT, card.accentColor);
        extra.putBoolean(DiyNowBarCard.EXTRA_EXPANDABLE, card.expandable);
        extra.putInt(DiyNowBarCard.EXTRA_ICON_SHAPE, card.iconShape);
        extra.putInt(DiyNowBarCard.EXTRA_ICON_TYPE, card.iconType);
        extra.putString(DiyNowBarCard.EXTRA_ICON_VALUE, card.iconValue);
        extra.putString(DiyNowBarCard.EXTRA_CARD_ID, card.id);
        extra.putInt(DiyNowBarCard.EXTRA_CARD_TYPE, card.type);
        extra.putBoolean(DiyNowBarCard.EXTRA_LIVE_VIEW, card.liveExpandedView);
        extra.putBoolean(DiyNowBarCard.EXTRA_IGNORE_BOUNCER, card.ignoreBouncer);
        extra.putBoolean(DiyNowBarCard.EXTRA_FULLSCREEN, useFullscreen);
        extra.putInt(DiyNowBarCard.EXTRA_STATS_MASK, card.statsMask);
        extra.putInt(DiyNowBarCard.EXTRA_STATS_PRIMARY, card.statsPrimary);
        extra.putString(DiyNowBarCard.EXTRA_CARD_TITLE, card.title);
        extra.putString(DiyNowBarCard.EXTRA_CARD_SUBTITLE, card.subtitle);

        DiyRes res = resolveRes(ctx);

        // Live ticking chronometer for Countdown (<24h) or running Stopwatch
        if (card.useLiveChronometer(now) && res != null && res.layoutChrono != 0) {
            long baseElapsed = chronometerBaseElapsed(card, now);
            boolean countDown = card.timerMode == DiyNowBarCard.TIMER_COUNTDOWN;
            RemoteViews chronoRv = new RemoteViews(DiyNowBarCard.PACKAGE_ID, res.layoutChrono);
            chronoRv.setChronometer(res.idChrono, baseElapsed, null, true);
            chronoRv.setChronometerCountDown(res.idChrono, countDown);
            Reflect.call(item, "setChronometerView", chronoRv);
            Reflect.call(item, "setChronometerTag", "diy_chrono");
            if (card.bigChronometer) {
                extra.putInt("nowbar_key_chronometerRemoteViewPosition", 1);
                String primarySub = card.subtitle.isEmpty() ? "" : card.subtitle;
                Reflect.call(item, "setSecondaryInfo", primarySub);
                Reflect.call(item, "setNowbarSecondaryInfo", primarySub);
            } else {
                extra.putInt("nowbar_key_chronometerRemoteViewPosition", 2);
                Reflect.call(item, "setSecondaryInfo", "");
                Reflect.call(item, "setNowbarSecondaryInfo", "");
            }
        }

        // Right-side Quick Action button on collapsed pill (works even on AOD!)
        Notification.Action quickAction = buildPillQuickAction(ctx, card, now);
        if (quickAction != null) {
            ArrayList<Notification.Action> actions = new ArrayList<>();
            actions.add(quickAction);
            ArrayList<Integer> bgColors = new ArrayList<>();
            bgColors.add(withAlpha(card.accentColor, 0x38));
            Reflect.call(item, "setActions", actions);
            Reflect.call(item, "setActionBgColors", bgColors);
            Reflect.call(item, "setPrimaryActionNum", 0);
        } else {
            Reflect.call(item, "setPrimaryActionNum", -1);
        }

        // Tap PendingIntent (Fullscreen card overlay takes priority when enabled so Samsung's
        // NowBarCardStackChangeListener never NPEs on hasFullscreenCard)
        PendingIntent tapPi =
                useFullscreen
                        ? fullscreenCardPendingIntent(ctx, card.id)
                        : buildActionPendingIntent(
                                ctx, card, card.tapAction, card.tapActionTarget, 700);
        if (tapPi != null) {
            Reflect.call(item, "setPendingIntent", tapPi);
        }

        // Expanded RemoteViews
        if (card.expandable && res != null && res.layoutExpand != 0) {
            RemoteViews expandRv = buildExpandedRemoteViews(ctx, res, card, stats, now);
            Reflect.call(item, "setOngoingExpandView", expandRv);
        }

        Reflect.call(item, "setExtraData", extra);
        return item;
    }

    private static RemoteViews buildExpandedRemoteViews(
            Context ctx, DiyRes res, DiyNowBarCard card, StatsSnapshot stats, long now) {
        RemoteViews rv = new RemoteViews(DiyNowBarCard.PACKAGE_ID, res.layoutExpand);
        rv.setImageViewBitmap(res.idExpandIcon, DiyNowBarIcons.renderBadgeBitmap(ctx, card, 108));
        rv.setTextViewText(res.idExpandTitle, card.title);

        String sub = resolveExpandedSubtitle(card, stats, now);
        rv.setTextViewText(res.idExpandSubtitle, sub);
        rv.setViewVisibility(res.idExpandSubtitle, sub.isEmpty() ? View.GONE : View.VISIBLE);

        String badge = resolveExpandedBadge(card, stats, now);
        rv.setTextViewText(res.idExpandBadge, badge);
        rv.setViewVisibility(res.idExpandBadge, badge.isEmpty() ? View.GONE : View.VISIBLE);

        rv.setImageViewBitmap(
                res.idExpandEditBtn, DiyNowBarIcons.drawActionGlyph("edit", 48, Color.WHITE));
        rv.setOnClickPendingIntent(res.idExpandEditBtn, editCardPendingIntent(ctx, card.id));

        // Reset optional sections
        rv.setViewVisibility(res.idExpandChrono, View.GONE);
        rv.setViewVisibility(res.idExpandBody, View.GONE);
        rv.setViewVisibility(res.idExpandProgressGroup, View.GONE);
        rv.setViewVisibility(res.idStatsGrid, View.GONE);
        rv.setViewVisibility(res.idCheckGroup, View.GONE);
        rv.setViewVisibility(res.idLaunchRow, View.GONE);
        rv.setViewVisibility(res.idActionRow, View.GONE);

        switch (card.type) {
            case DiyNowBarCard.TYPE_CUSTOM -> bindCustomExpanded(ctx, res, rv, card);
            case DiyNowBarCard.TYPE_TIMER -> bindTimerExpanded(ctx, res, rv, card, now);
            case DiyNowBarCard.TYPE_GOAL -> bindGoalExpanded(ctx, res, rv, card);
            case DiyNowBarCard.TYPE_STATS -> bindStatsExpanded(res, rv, card, stats);
            case DiyNowBarCard.TYPE_LAUNCHER -> bindLauncherExpanded(ctx, res, rv, card);
            case DiyNowBarCard.TYPE_CHECKLIST -> bindChecklistExpanded(ctx, res, rv, card);
            case DiyNowBarCard.TYPE_CONTROLS -> bindControlsExpanded(ctx, res, rv, card);
            default -> bindCustomExpanded(ctx, res, rv, card);
        }
        return rv;
    }

    private static void bindCustomExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card) {
        if (!card.body.isEmpty()) {
            rv.setTextViewText(res.idExpandBody, card.body);
            rv.setViewVisibility(res.idExpandBody, View.VISIBLE);
        }
        if (card.showProgress) {
            rv.setViewVisibility(res.idExpandProgressGroup, View.VISIBLE);
            String label = card.progressLabel.isEmpty() ? "Progress" : card.progressLabel;
            rv.setTextViewText(res.idExpandProgressLabel, label);
            rv.setTextViewText(res.idExpandProgressValue, card.goalSummary());
            rv.setProgressBar(res.idExpandProgress, 100, card.goalPercent(), false);
        }
        List<BtnSpec> buttons = new ArrayList<>();
        PendingIntent tapPi =
                buildActionPendingIntent(ctx, card, card.tapAction, card.tapActionTarget, 501);
        if (tapPi != null) {
            buttons.add(new BtnSpec(actionLabel(ctx, card.tapAction, card.tapActionTarget), tapPi));
        }
        PendingIntent qaPi =
                card.quickAction != DiyNowBarCard.ACTION_AUTO
                        ? buildActionPendingIntent(
                                ctx, card, card.quickAction, card.quickActionTarget, 502)
                        : null;
        if (qaPi != null) {
            buttons.add(
                    new BtnSpec(actionLabel(ctx, card.quickAction, card.quickActionTarget), qaPi));
        }
        bindButtons(res, rv, buttons);
    }

    private static void bindTimerExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card, long now) {
        if (card.useLiveChronometer(now)) {
            rv.setViewVisibility(res.idExpandChrono, View.VISIBLE);
            rv.setChronometer(
                    res.idExpandChrono,
                    chronometerBaseElapsed(card, now),
                    null,
                    true);
            rv.setChronometerCountDown(
                    res.idExpandChrono, card.timerMode == DiyNowBarCard.TIMER_COUNTDOWN);
        } else {
            String readout =
                    card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                            ? DiyNowBarCard.formatDuration(card.stopwatchElapsedMs(now))
                            : (card.countdownRemainingMs(now) <= 0L
                                    ? "Completed"
                                    : DiyNowBarCard.formatDuration(card.countdownRemainingMs(now)));
            rv.setTextViewText(res.idExpandBody, readout);
            rv.setViewVisibility(res.idExpandBody, View.VISIBLE);
        }
        if (card.timerMode == DiyNowBarCard.TIMER_COUNTDOWN) {
            int pct = card.countdownProgress(now);
            rv.setViewVisibility(res.idExpandProgressGroup, View.VISIBLE);
            rv.setTextViewText(res.idExpandProgressLabel, "Elapsed");
            rv.setTextViewText(res.idExpandProgressValue, pct + "%");
            rv.setProgressBar(res.idExpandProgress, 100, pct, false);
            List<BtnSpec> btns = new ArrayList<>();
            if (card.countdownRemainingMs(now) <= 0L) {
                btns.add(
                        new BtnSpec(
                                "+15m",
                                internalBroadcastPi(
                                        ctx, card.id, DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, 201)));
            }
            btns.add(
                    new BtnSpec(
                            "Restart",
                            internalBroadcastPi(
                                    ctx, card.id, DiyNowBarCard.MUTATE_TIMER_RESET, -1, 202)));
            bindButtons(res, rv, btns);
        } else {
            List<BtnSpec> btns = new ArrayList<>();
            btns.add(
                    new BtnSpec(
                            card.timerRunning ? "Pause" : "Start",
                            internalBroadcastPi(
                                    ctx, card.id, DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, 203)));
            btns.add(
                    new BtnSpec(
                            "Reset",
                            internalBroadcastPi(
                                    ctx, card.id, DiyNowBarCard.MUTATE_TIMER_RESET, -1, 204)));
            bindButtons(res, rv, btns);
        }
    }

    private static void bindGoalExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card) {
        rv.setViewVisibility(res.idExpandProgressGroup, View.VISIBLE);
        rv.setTextViewText(
                res.idExpandProgressLabel,
                card.unit.isEmpty() ? "Progress" : card.unit);
        rv.setTextViewText(res.idExpandProgressValue, card.goalSummary());
        rv.setProgressBar(res.idExpandProgress, 100, card.goalPercent(), false);

        List<BtnSpec> btns = new ArrayList<>();
        btns.add(
                new BtnSpec(
                        "-" + card.stepValue,
                        internalBroadcastPi(ctx, card.id, DiyNowBarCard.MUTATE_GOAL_DEC, -1, 301)));
        btns.add(
                new BtnSpec(
                        "+" + card.stepValue,
                        internalBroadcastPi(ctx, card.id, DiyNowBarCard.MUTATE_GOAL_INC, -1, 302)));
        btns.add(
                new BtnSpec(
                        "Reset",
                        internalBroadcastPi(
                                ctx, card.id, DiyNowBarCard.MUTATE_GOAL_RESET, -1, 303)));
        bindButtons(res, rv, btns);
    }

    private static void bindStatsExpanded(
            DiyRes res, RemoteViews rv, DiyNowBarCard card, StatsSnapshot stats) {
        if (stats == null) return;
        List<Integer> enabled = DiyNowBarCard.enabledStats(card.statsMask);
        rv.setViewVisibility(res.idStatsGrid, View.VISIBLE);
        rv.setViewVisibility(
                res.idStatsRow2, enabled.size() > 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) {
            if (i < enabled.size()) {
                int bit = enabled.get(i);
                rv.setViewVisibility(res.statTileIds[i], View.VISIBLE);
                rv.setTextViewText(res.statLabelIds[i], DiyNowBarCard.statName(bit));
                rv.setTextViewText(res.statValueIds[i], stats.detail(bit));
            } else {
                rv.setViewVisibility(res.statTileIds[i], View.INVISIBLE);
            }
        }
    }

    private static void bindLauncherExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card) {
        int shown = 0;
        for (int i = 0; i < DiyNowBarCard.MAX_SHORTCUTS && i < card.shortcuts.size(); i++) {
            DiyNowBarCard.ShortcutSlot slot = card.shortcuts.get(i);
            if (slot.action == DiyNowBarCard.ACTION_NONE) {
                rv.setViewVisibility(res.launchTileIds[i], View.GONE);
                continue;
            }
            PendingIntent pi =
                    buildActionPendingIntent(ctx, card, slot.action, slot.target, 400 + i);
            if (pi == null) {
                rv.setViewVisibility(res.launchTileIds[i], View.GONE);
                continue;
            }
            shown++;
            rv.setViewVisibility(res.launchTileIds[i], View.VISIBLE);
            rv.setImageViewBitmap(
                    res.launchIconIds[i],
                    DiyNowBarIcons.renderShortcutBitmap(ctx, slot, card.accentColor, 84));
            String label =
                    slot.label.isEmpty() ? actionLabel(ctx, slot.action, slot.target) : slot.label;
            rv.setTextViewText(res.launchLabelIds[i], label);
            rv.setOnClickPendingIntent(res.launchTileIds[i], pi);
        }
        rv.setViewVisibility(res.idLaunchRow, shown > 0 ? View.VISIBLE : View.GONE);
        if (!card.body.isEmpty()) {
            rv.setTextViewText(res.idExpandBody, card.body);
            rv.setViewVisibility(res.idExpandBody, View.VISIBLE);
        }
    }

    private static void bindChecklistExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card) {
        if (card.checklist.isEmpty()) {
            rv.setTextViewText(res.idExpandBody, "Add checklist items in DIY Now Bar Studio.");
            rv.setViewVisibility(res.idExpandBody, View.VISIBLE);
            return;
        }
        rv.setViewVisibility(res.idCheckGroup, View.VISIBLE);
        for (int i = 0; i < DiyNowBarCard.MAX_CHECKLIST; i++) {
            if (i < card.checklist.size()) {
                DiyNowBarCard.CheckItem item = card.checklist.get(i);
                rv.setViewVisibility(res.checkRowIds[i], View.VISIBLE);
                rv.setImageViewBitmap(
                        res.checkIconIds[i],
                        DiyNowBarIcons.drawCheckbox(item.done, card.accentColor, 48));
                rv.setTextViewText(res.checkTextIds[i], item.text);
                rv.setTextColor(res.checkTextIds[i], item.done ? 0x80FFFFFF : Color.WHITE);
                rv.setOnClickPendingIntent(
                        res.checkRowIds[i],
                        internalBroadcastPi(
                                ctx, card.id, DiyNowBarCard.MUTATE_CHECK_TOGGLE, i, 600 + i));
            } else {
                rv.setViewVisibility(res.checkRowIds[i], View.GONE);
            }
        }
        if (card.checklistDoneCount() > 0) {
            bindButtons(
                    res,
                    rv,
                    Collections.singletonList(
                            new BtnSpec(
                                    "Uncheck all",
                                    internalBroadcastPi(
                                            ctx,
                                            card.id,
                                            DiyNowBarCard.MUTATE_CHECK_CLEAR,
                                            -1,
                                            650))));
        }
    }

    private static void bindControlsExpanded(
            Context ctx, DiyRes res, RemoteViews rv, DiyNowBarCard card) {
        rv.setTextViewText(res.idExpandBody, DiyNowBarLiveViews.controlsSummary(ctx));
        rv.setViewVisibility(res.idExpandBody, View.VISIBLE);
        List<BtnSpec> btns = new ArrayList<>();
        btns.add(new BtnSpec("Torch", internalBroadcastPi(ctx, card.id, "flashlight", -1, 680)));
        btns.add(new BtnSpec("Studio", editCardPendingIntent(ctx, card.id)));
        bindButtons(res, rv, btns);
    }

    private record BtnSpec(String label, PendingIntent intent) {}

    private static void bindButtons(DiyRes res, RemoteViews rv, List<BtnSpec> specs) {
        if (specs.isEmpty()) {
            rv.setViewVisibility(res.idActionRow, View.GONE);
            return;
        }
        rv.setViewVisibility(res.idActionRow, View.VISIBLE);
        for (int i = 0; i < res.btnIds.length; i++) {
            if (i < specs.size()) {
                BtnSpec spec = specs.get(i);
                rv.setViewVisibility(res.btnIds[i], View.VISIBLE);
                rv.setTextViewText(res.btnIds[i], spec.label());
                rv.setOnClickPendingIntent(res.btnIds[i], spec.intent());
            } else {
                rv.setViewVisibility(res.btnIds[i], View.GONE);
            }
        }
    }

    private static Notification.Action buildPillQuickAction(
            Context ctx, DiyNowBarCard card, long now) {
        if (card.quickAction == DiyNowBarCard.ACTION_NONE) return null;
        if (card.quickAction == DiyNowBarCard.ACTION_AUTO) {
            return switch (card.type) {
                case DiyNowBarCard.TYPE_GOAL ->
                        buildAction(
                                "plus",
                                "Add " + card.stepValue,
                                internalBroadcastPi(
                                        ctx, card.id, DiyNowBarCard.MUTATE_GOAL_INC, -1, 101),
                                false);
                case DiyNowBarCard.TYPE_TIMER -> {
                    if (card.timerMode == DiyNowBarCard.TIMER_STOPWATCH) {
                        yield buildAction(
                                card.timerRunning ? "pause" : "play",
                                card.timerRunning ? "Pause" : "Start",
                                internalBroadcastPi(
                                        ctx, card.id, DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1, 102),
                                false);
                    }
                    yield null;
                }
                case DiyNowBarCard.TYPE_CHECKLIST -> {
                    if (card.checklistDoneCount() < card.checklist.size()) {
                        yield buildAction(
                                "check",
                                "Check next",
                                internalBroadcastPi(
                                        ctx, card.id, DiyNowBarCard.MUTATE_CHECK_NEXT, -1, 103),
                                false);
                    }
                    yield null;
                }
                case DiyNowBarCard.TYPE_CONTROLS ->
                        buildAction(
                                "bolt",
                                "Torch",
                                internalBroadcastPi(ctx, card.id, "flashlight", -1, 104),
                                false);
                default -> null;
            };
        }
        PendingIntent pi =
                buildActionPendingIntent(ctx, card, card.quickAction, card.quickActionTarget, 105);
        if (pi == null) return null;
        String glyph =
                switch (card.quickAction) {
                    case DiyNowBarCard.ACTION_FLASHLIGHT -> "bolt";
                    case DiyNowBarCard.ACTION_OPEN_EDITOR -> "edit";
                    default -> "open";
                };
        boolean auth = !card.ignoreBouncer && card.quickAction != DiyNowBarCard.ACTION_FLASHLIGHT;
        return buildAction(glyph, actionLabel(ctx, card.quickAction, card.quickActionTarget), pi, auth);
    }

    private static Notification.Action buildAction(
            String glyph, String title, PendingIntent pi, boolean requireAuth) {
        Icon icon = Icon.createWithBitmap(DiyNowBarIcons.drawActionGlyph(glyph, 64, Color.WHITE));
        return new Notification.Action.Builder(icon, title, pi)
                .setAuthenticationRequired(requireAuth)
                .build();
    }

    private static PendingIntent internalBroadcastPi(
            Context ctx, String cardId, String op, int index, int slotCode) {
        Intent intent =
                new Intent(ACTION_INTERNAL)
                        .setPackage(ctx.getPackageName())
                        .putExtra(EXTRA_CARD_ID, cardId)
                        .putExtra(EXTRA_OP, op)
                        .putExtra(EXTRA_INDEX, index);
        int reqCode = Math.abs((cardId + ":" + op + ":" + index + ":" + slotCode).hashCode());
        return PendingIntent.getBroadcast(
                ctx, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent editCardPendingIntent(Context ctx, String cardId) {
        Intent intent =
                new Intent()
                        .setClassName(
                                DiyNowBarCard.PACKAGE_ID,
                                "dev.denver.dressroomadvanced.NowBarStudioActivity")
                        .putExtra("edit_card_id", cardId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int reqCode = Math.abs(("edit:" + cardId).hashCode());
        return PendingIntent.getActivity(
                ctx, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent fullscreenCardPendingIntent(Context ctx, String cardId) {
        Intent intent =
                new Intent()
                        .setClassName(
                                DiyNowBarCard.PACKAGE_ID,
                                "dev.denver.dressroomadvanced.DiyFullscreenCardActivity")
                        .putExtra(DiyFullscreenCardActivity.EXTRA_CARD_ID, cardId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int reqCode = Math.abs(("full:" + cardId).hashCode());
        return PendingIntent.getActivity(
                ctx, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent buildActionPendingIntent(
            Context ctx, DiyNowBarCard card, int action, String target, int slotCode) {
        return switch (action) {
            case DiyNowBarCard.ACTION_FLASHLIGHT ->
                    internalBroadcastPi(ctx, card.id, "flashlight", -1, slotCode);
            case DiyNowBarCard.ACTION_OPEN_EDITOR -> editCardPendingIntent(ctx, card.id);
            case DiyNowBarCard.ACTION_OPEN_APP -> {
                if (target == null || target.isEmpty()) yield null;
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(target);
                if (launch == null) yield null;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                int req = Math.abs(("app:" + card.id + ":" + target + ":" + slotCode).hashCode());
                yield PendingIntent.getActivity(
                        ctx, req, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            case DiyNowBarCard.ACTION_OPEN_URL -> {
                if (target == null || target.isEmpty()) yield null;
                String url = target.contains("://") ? target : "https://" + target;
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                int req = Math.abs(("url:" + card.id + ":" + url + ":" + slotCode).hashCode());
                yield PendingIntent.getActivity(
                        ctx, req, view, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            default -> null;
        };
    }

    private static String actionLabel(Context ctx, int action, String target) {
        return switch (action) {
            case DiyNowBarCard.ACTION_FLASHLIGHT -> "Torch";
            case DiyNowBarCard.ACTION_OPEN_EDITOR -> "Edit";
            case DiyNowBarCard.ACTION_OPEN_APP -> {
                String label = DiyNowBarIcons.loadAppLabel(ctx, target);
                yield label.isEmpty() ? "Open App" : label;
            }
            case DiyNowBarCard.ACTION_OPEN_URL -> "Open Link";
            default -> "Action";
        };
    }

    private static String resolvePillTitle(DiyNowBarCard card, StatsSnapshot stats, long now) {
        return card.title;
    }

    private static String resolvePillSubtitle(
            Context ctx, DiyNowBarCard card, StatsSnapshot stats, long now) {
        return switch (card.type) {
            case DiyNowBarCard.TYPE_GOAL -> card.goalSummary();
            case DiyNowBarCard.TYPE_CHECKLIST -> card.checklistSummary();
            case DiyNowBarCard.TYPE_STATS -> {
                if (!card.statsFormat.isEmpty()) {
                    yield stats == null ? card.statsFormat : stats.format(card.statsFormat);
                }
                yield stats == null ? card.subtitle : stats.compact(card.statsPrimary);
            }
            case DiyNowBarCard.TYPE_LAUNCHER -> {
                if (!card.subtitle.isEmpty()) yield card.subtitle;
                int count = card.activeShortcutCount();
                yield count + (count == 1 ? " shortcut" : " shortcuts");
            }
            case DiyNowBarCard.TYPE_TIMER -> {
                if (card.timerMode == DiyNowBarCard.TIMER_STOPWATCH) {
                    yield card.timerRunning
                            ? "Running"
                            : DiyNowBarCard.formatDuration(card.stopwatchElapsedMs(now));
                }
                long rem = card.countdownRemainingMs(now);
                yield rem <= 0L ? "Done" : DiyNowBarCard.formatCountdownBadge(rem);
            }
            case DiyNowBarCard.TYPE_CONTROLS ->
                    card.subtitle.isEmpty() ? DiyNowBarLiveViews.controlsSummary(ctx) : card.subtitle;
            default -> card.subtitle;
        };
    }

    private static String resolveExpandedSubtitle(DiyNowBarCard card, StatsSnapshot stats, long now) {
        if (card.type == DiyNowBarCard.TYPE_STATS) {
            if (!card.subtitle.isEmpty()) {
                return stats == null ? card.subtitle : stats.format(card.subtitle);
            }
            if (!card.statsFormat.isEmpty()) {
                return stats == null ? card.statsFormat : stats.format(card.statsFormat);
            }
            return stats == null ? "Live telemetry" : stats.compact(card.statsPrimary);
        }
        if (!card.subtitle.isEmpty() && card.type != DiyNowBarCard.TYPE_GOAL) return card.subtitle;
        return switch (card.type) {
            case DiyNowBarCard.TYPE_GOAL -> card.goalSummary();
            case DiyNowBarCard.TYPE_CHECKLIST -> card.checklistSummary();
            case DiyNowBarCard.TYPE_TIMER ->
                    card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                            ? (card.timerRunning ? "Stopwatch running" : "Stopwatch paused")
                            : DiyNowBarCard.formatCountdownBadge(card.countdownRemainingMs(now));
            default -> card.subtitle;
        };
    }

    private static String resolveExpandedBadge(DiyNowBarCard card, StatsSnapshot stats, long now) {
        return switch (card.type) {
            case DiyNowBarCard.TYPE_GOAL -> card.goalPercent() + "%";
            case DiyNowBarCard.TYPE_CHECKLIST ->
                    card.checklistDoneCount() + "/" + card.checklist.size();
            case DiyNowBarCard.TYPE_TIMER ->
                    card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                            ? (card.timerRunning ? "LIVE" : "PAUSED")
                            : (card.countdownRemainingMs(now) <= 0L ? "DONE" : "TIMER");
            case DiyNowBarCard.TYPE_STATS -> "LIVE";
            case DiyNowBarCard.TYPE_CONTROLS -> "LIVE";
            default -> "";
        };
    }

    private static long chronometerBaseElapsed(DiyNowBarCard card, long nowEpochMs) {
        long elapsedNow = SystemClock.elapsedRealtime();
        if (card.timerMode == DiyNowBarCard.TIMER_STOPWATCH) {
            return elapsedNow - card.stopwatchElapsedMs(nowEpochMs);
        }
        return elapsedNow + card.countdownRemainingMs(nowEpochMs);
    }

    private static String buildSignature(DiyNowBarCard card, StatsSnapshot stats, long now) {
        StringBuilder sb = new StringBuilder();
        sb.append(DiyNowBarStore.toJson(Collections.singletonList(card)));
        if (card.type == DiyNowBarCard.TYPE_STATS && stats != null) {
            sb.append("|stats:").append(stats.signature());
        } else if (card.type == DiyNowBarCard.TYPE_TIMER && !card.useLiveChronometer(now)) {
            sb.append("|t:").append(card.countdownRemainingMs(now) / 60_000L);
        }
        return sb.toString();
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    static final class StatsSnapshot {
        final int batteryPct;
        final float batteryTempC;
        final float batteryWatts;
        final boolean charging;
        final long ramUsedBytes;
        final long ramTotalBytes;
        final long storageFreeBytes;
        final long storageTotalBytes;
        final String networkLabel;
        final long uptimeMs;

        StatsSnapshot(
                int batteryPct,
                float batteryTempC,
                float batteryWatts,
                boolean charging,
                long ramUsedBytes,
                long ramTotalBytes,
                long storageFreeBytes,
                long storageTotalBytes,
                String networkLabel,
                long uptimeMs) {
            this.batteryPct = batteryPct;
            this.batteryTempC = batteryTempC;
            this.batteryWatts = batteryWatts;
            this.charging = charging;
            this.ramUsedBytes = ramUsedBytes;
            this.ramTotalBytes = ramTotalBytes;
            this.storageFreeBytes = storageFreeBytes;
            this.storageTotalBytes = storageTotalBytes;
            this.networkLabel = networkLabel;
            this.uptimeMs = uptimeMs;
        }

        String format(String template) {
            return DiyNowBarCard.formatStatsTemplate(
                    template,
                    batteryPct,
                    batteryTempC,
                    batteryWatts,
                    ramUsedBytes,
                    ramTotalBytes,
                    storageFreeBytes,
                    storageTotalBytes,
                    networkLabel,
                    uptimeMs);
        }

        String compact(int bit) {
            return switch (bit) {
                case DiyNowBarCard.STAT_BATTERY -> {
                    String s = batteryPct + "%";
                    if (batteryTempC > 0f) s += String.format(Locale.US, " \u00B7 %.1f\u00B0C", batteryTempC);
                    if (Math.abs(batteryWatts) >= 0.5f) {
                        s += String.format(Locale.US, " \u00B7 %.1fW", Math.abs(batteryWatts));
                    }
                    yield s;
                }
                case DiyNowBarCard.STAT_RAM -> {
                    int pct = ramTotalBytes > 0 ? (int) Math.round(ramUsedBytes * 100.0 / ramTotalBytes) : 0;
                    yield DiyNowBarCard.formatBytesGb(ramUsedBytes) + " (" + pct + "%)";
                }
                case DiyNowBarCard.STAT_STORAGE -> DiyNowBarCard.formatBytesGb(storageFreeBytes) + " free";
                case DiyNowBarCard.STAT_NETWORK -> networkLabel;
                case DiyNowBarCard.STAT_UPTIME -> "Up " + DiyNowBarCard.formatUptime(uptimeMs);
                default -> "";
            };
        }

        String detail(int bit) {
            return switch (bit) {
                case DiyNowBarCard.STAT_BATTERY -> {
                    String s = batteryPct + "%";
                    if (batteryTempC > 0f) s += String.format(Locale.US, " \u00B7 %.1f\u00B0C", batteryTempC);
                    if (charging && Math.abs(batteryWatts) >= 0.5f) {
                        s += String.format(Locale.US, " \u00B7 +%.1fW", Math.abs(batteryWatts));
                    }
                    yield s;
                }
                case DiyNowBarCard.STAT_RAM ->
                        DiyNowBarCard.formatBytesGb(ramUsedBytes)
                                + " / "
                                + DiyNowBarCard.formatBytesGb(ramTotalBytes);
                case DiyNowBarCard.STAT_STORAGE ->
                        DiyNowBarCard.formatBytesGb(storageFreeBytes) + " free";
                case DiyNowBarCard.STAT_NETWORK -> networkLabel;
                case DiyNowBarCard.STAT_UPTIME -> DiyNowBarCard.formatUptime(uptimeMs);
                default -> "-";
            };
        }

        String signature() {
            return batteryPct
                    + ":"
                    + Math.round(batteryTempC * 10f)
                    + ":"
                    + Math.round(batteryWatts)
                    + ":"
                    + (ramUsedBytes / (100 * 1024 * 1024L))
                    + ":"
                    + (storageFreeBytes / (256 * 1024 * 1024L))
                    + ":"
                    + networkLabel
                    + ":"
                    + (uptimeMs / 60_000L);
        }
    }

    static StatsSnapshot collectStats(Context ctx) {
        int pct = 0;
        float tempC = 0f;
        float watts = 0f;
        boolean charging = false;
        try {
            Intent bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) pct = Math.round(level * 100f / scale);
                tempC = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
                int voltMv = bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                int plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                charging = plugged != 0;
                BatteryManager bm = ctx.getSystemService(BatteryManager.class);
                if (bm != null && voltMv > 0) {
                    int currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    if (currentUa != Integer.MIN_VALUE && currentUa != 0) {
                        // Normalize microamps or milliamps into Watts
                        float amps = Math.abs(currentUa) > 10_000 ? currentUa / 1_000_000f : currentUa / 1000f;
                        watts = (voltMv / 1000f) * amps;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        long ramUsed = 0L;
        long ramTotal = 0L;
        try {
            ActivityManager am = ctx.getSystemService(ActivityManager.class);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                ramTotal = mi.totalMem;
                ramUsed = Math.max(0L, mi.totalMem - mi.availMem);
            }
        } catch (Throwable ignored) {
        }

        long storageFree = 0L;
        long storageTotal = 0L;
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            storageFree = sf.getAvailableBytes();
            storageTotal = sf.getTotalBytes();
        } catch (Throwable ignored) {
        }

        String net = "Offline";
        try {
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            if (cm != null) {
                Network active = cm.getActiveNetwork();
                NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        net = "Wi-Fi";
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        net = "Mobile Data";
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        net = "Ethernet";
                    } else {
                        net = "Connected";
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        net += " \u00B7 VPN";
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return new StatsSnapshot(
                pct,
                tempC,
                watts,
                charging,
                ramUsed,
                ramTotal,
                storageFree,
                storageTotal,
                net,
                SystemClock.elapsedRealtime());
    }
}
