package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Lockscreen-ready Fullscreen Expanded Now Bar Card overlay launched via Samsung's native
 * nowbar_key_has_fullscreen_card / nowbar_key_need_to_fullview pipeline.
 * Displays interactive controls, live telemetry sparklines, checklists, goals, timers, and
 * quick launchers directly over the lock screen without requiring unlock.
 */
public final class DiyFullscreenCardActivity extends Activity {
    static final String EXTRA_CARD_ID = "card_id";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout rootBackdrop;
    private String cardId = "";
    private DiyNowBarCard card;

    private final Runnable clockTick =
            new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed() || isFinishing() || card == null) return;
                    if (card.type == DiyNowBarCard.TYPE_TIMER) {
                        renderCard();
                        handler.postDelayed(this, 1000L);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        Window window = getWindow();
        if (window != null) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.setBlurBehindRadius(48);
            window.setAttributes(attrs);
        }

        rootBackdrop = new FrameLayout(this);
        rootBackdrop.setBackgroundColor(0xB8090C14);
        rootBackdrop.setOnClickListener(v -> finish());
        setContentView(rootBackdrop);

        cardId = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_CARD_ID);
        if (cardId == null) cardId = "";
        reloadAndRender();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        cardId = intent == null ? "" : intent.getStringExtra(EXTRA_CARD_ID);
        if (cardId == null) cardId = "";
        reloadAndRender();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void reloadAndRender() {
        List<DiyNowBarCard> cards = DiyNowBarStore.read(this);
        card = null;
        for (DiyNowBarCard c : cards) {
            if (c != null && c.id.equals(cardId)) {
                card = c;
                break;
            }
        }
        if (card == null && !cards.isEmpty()) {
            card = cards.get(0);
            cardId = card.id;
        }
        if (card == null) {
            finish();
            return;
        }
        renderCard();
        handler.removeCallbacks(clockTick);
        if (card.type == DiyNowBarCard.TYPE_TIMER) {
            handler.postDelayed(clockTick, 1000L);
        }
    }

    private void renderCard() {
        rootBackdrop.removeAllViews();
        if (card == null) return;

        FrameLayout cardShell = new FrameLayout(this);
        cardShell.setClipToOutline(true);
        GradientDrawable shellBg = new GradientDrawable();
        shellBg.setCornerRadius(dp(30));
        shellBg.setColor(0xE8161A24);
        shellBg.setStroke(Math.max(1, dp(1)), 0x30FFFFFF);
        cardShell.setBackground(shellBg);
        cardShell.setClickable(true);

        if (card.effect >= DiyNowBarCard.EFFECT_AI_PRESENCE) {
            DiyNowBarAuraView aura = new DiyNowBarAuraView(this);
            aura.configure(
                    card.effect == DiyNowBarCard.EFFECT_OUTER_GLOW
                            ? DiyNowBarCard.EFFECT_AI_PRESENCE
                            : card.effect,
                    card.effectColor1,
                    card.effectColor2,
                    dp(30),
                    0f);
            cardShell.addView(
                    aura,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        if (card.type == DiyNowBarCard.TYPE_CONTROLS) {
            DiyNowBarLiveViews.LiveControlsPanel panel =
                    new DiyNowBarLiveViews.LiveControlsPanel(this, false);
            panel.bind(card.id, card.title, card.subtitle, card.accentColor, card, card.ignoreBouncer);
            content.addView(panel);
        } else if (card.type == DiyNowBarCard.TYPE_STATS && card.liveExpandedView) {
            DiyNowBarLiveViews.LiveTelemetryPanel panel =
                    new DiyNowBarLiveViews.LiveTelemetryPanel(this, false);
            panel.bind(
                    card.id,
                    card.title,
                    card.subtitle,
                    card.accentColor,
                    card.statsMask,
                    card,
                    card.ignoreBouncer);
            content.addView(panel);
        } else {
            buildInteractiveBody(content);
        }

        // Bottom controls (Edit in Studio + Close)
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.topMargin = dp(16);

        TextView editBtn = makePill(ModuleStrings.get(this, R.string.nowbar_edit_in_studio), 0x28FFFFFF);
        editBtn.setOnClickListener(
                v -> {
                    DiyNowBarLiveViews.launchEditor(this, card.id, card.ignoreBouncer);
                    finish();
                });
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        eLp.setMarginEnd(dp(8));
        footer.addView(editBtn, eLp);

        TextView closeBtn = makePill(ModuleStrings.get(this, R.string.common_close), withAlpha(card.accentColor, 0xCC));
        closeBtn.setOnClickListener(v -> finish());
        footer.addView(closeBtn, new LinearLayout.LayoutParams(0, dp(42), 1f));
        content.addView(footer, fLp);

        scroll.addView(content);
        cardShell.addView(
                scroll,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams shellLp =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);
        shellLp.setMargins(dp(20), dp(48), dp(20), dp(48));
        rootBackdrop.addView(cardShell, shellLp);
    }

    private void buildInteractiveBody(LinearLayout content) {
        long now = System.currentTimeMillis();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(this, card, dp(46)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(14));

        TextView title = new TextView(this);
        title.setText(card.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(title);

        if (!card.subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(card.subtitle);
            sub.setTextColor(0xCCFFFFFF);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            texts.addView(sub);
        }
        header.addView(texts, tlp);
        content.addView(header);

        switch (card.type) {
            case DiyNowBarCard.TYPE_TIMER -> {
                String readout =
                        card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                                ? DiyNowBarCard.formatDuration(card.stopwatchElapsedMs(now))
                                : (card.countdownRemainingMs(now) <= 0L
                                        ? ModuleStrings.get(this, R.string.nowbar_timer_completed)
                                        : DiyNowBarCard.formatDuration(
                                                card.countdownRemainingMs(now)));
                TextView big = new TextView(this);
                big.setText(readout);
                big.setTextColor(Color.WHITE);
                big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42);
                big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                big.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams blp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = dp(20);
                blp.bottomMargin = dp(12);
                content.addView(big, blp);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                if (card.timerMode == DiyNowBarCard.TIMER_STOPWATCH) {
                    TextView toggle =
                            makePill(
                                    ModuleStrings.get(
                                            this,
                                            card.timerRunning
                                                    ? R.string.common_pause
                                                    : R.string.common_start),
                                    withAlpha(card.accentColor, 0xCC));
                    toggle.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_TIMER_TOGGLE, -1));
                    LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
                    lp1.setMarginEnd(dp(8));
                    row.addView(toggle, lp1);
                }
                TextView reset = makePill(ModuleStrings.get(this, R.string.common_reset), 0x28FFFFFF);
                reset.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_TIMER_RESET, -1));
                row.addView(reset, new LinearLayout.LayoutParams(0, dp(42), 1f));
                content.addView(row);
            }
            case DiyNowBarCard.TYPE_GOAL -> {
                TextView summary = new TextView(this);
                summary.setText(card.goalSummary());
                summary.setTextColor(Color.WHITE);
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                summary.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                summary.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams slp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = dp(18);
                slp.bottomMargin = dp(14);
                content.addView(summary, slp);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView dec = makePill("-" + card.stepValue, 0x28FFFFFF);
                dec.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_GOAL_DEC, -1));
                LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp1.setMarginEnd(dp(8));
                row.addView(dec, lp1);

                TextView inc = makePill("+" + card.stepValue, withAlpha(card.accentColor, 0xCC));
                inc.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_GOAL_INC, -1));
                LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp2.setMarginEnd(dp(8));
                row.addView(inc, lp2);

                TextView rst = makePill(ModuleStrings.get(this, R.string.common_reset), 0x28FFFFFF);
                rst.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_GOAL_RESET, -1));
                row.addView(rst, new LinearLayout.LayoutParams(0, dp(44), 1f));
                content.addView(row);
            }
            case DiyNowBarCard.TYPE_CHECKLIST -> {
                for (int i = 0; i < card.checklist.size(); i++) {
                    final int idx = i;
                    DiyNowBarCard.CheckItem item = card.checklist.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(14));
                    bg.setColor(0x20FFFFFF);
                    row.setBackground(bg);
                    row.setClickable(true);
                    row.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_CHECK_TOGGLE, idx));

                    ImageView cb = new ImageView(this);
                    cb.setImageBitmap(DiyNowBarIcons.drawCheckbox(item.done, card.accentColor, dp(20)));
                    row.addView(cb, new LinearLayout.LayoutParams(dp(20), dp(20)));

                    TextView tv = new TextView(this);
                    tv.setText(item.text);
                    tv.setTextColor(item.done ? 0x80FFFFFF : Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    LinearLayout.LayoutParams tvLp =
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tvLp.setMarginStart(dp(10));
                    row.addView(tv, tvLp);

                    LinearLayout.LayoutParams rlp =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = i == 0 ? dp(14) : dp(6);
                    content.addView(row, rlp);
                }
                if (card.checklistDoneCount() > 0) {
                    TextView clear = makePill(ModuleStrings.get(this, R.string.nowbar_uncheck_all), 0x28FFFFFF);
                    clear.setOnClickListener(v -> mutateAndReload(DiyNowBarCard.MUTATE_CHECK_CLEAR, -1));
                    LinearLayout.LayoutParams clp =
                            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
                    clp.topMargin = dp(10);
                    content.addView(clear, clp);
                }
            }
            case DiyNowBarCard.TYPE_LAUNCHER -> {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(16);
                for (int i = 0; i < card.shortcuts.size(); i++) {
                    DiyNowBarCard.ShortcutSlot slot = card.shortcuts.get(i);
                    if (slot.action == DiyNowBarCard.ACTION_NONE) continue;
                    LinearLayout tile = new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.CENTER);
                    tile.setPadding(dp(8), dp(12), dp(8), dp(12));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(16));
                    bg.setColor(0x20FFFFFF);
                    tile.setBackground(bg);
                    tile.setClickable(true);
                    tile.setOnClickListener(v -> runShortcut(slot));

                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(
                            DiyNowBarIcons.renderShortcutBitmap(this, slot, card.accentColor, dp(36)));
                    tile.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));

                    TextView lbl = new TextView(this);
                    lbl.setText(slot.label.isEmpty() ? ModuleStrings.get(this, R.string.common_open) : slot.label);
                    lbl.setTextColor(Color.WHITE);
                    lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    lbl.setSingleLine(true);
                    lbl.setEllipsize(TextUtils.TruncateAt.END);
                    lbl.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams llp =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    llp.topMargin = dp(6);
                    tile.addView(lbl, llp);

                    LinearLayout.LayoutParams tileLp =
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (row.getChildCount() > 0) tileLp.setMarginStart(dp(8));
                    row.addView(tile, tileLp);
                }
                content.addView(row, rlp);
            }
            default -> {
                if (!card.body.isEmpty()) {
                    TextView body = new TextView(this);
                    body.setText(card.body);
                    body.setTextColor(0xE6FFFFFF);
                    body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    LinearLayout.LayoutParams blp =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.topMargin = dp(14);
                    content.addView(body, blp);
                }
            }
        }
    }

    private void runShortcut(DiyNowBarCard.ShortcutSlot slot) {
        try {
            switch (slot.action) {
                case DiyNowBarCard.ACTION_FLASHLIGHT ->
                        DiyNowBarLiveViews.toggleSystemTorch(this, true);
                case DiyNowBarCard.ACTION_OPEN_EDITOR -> {
                    DiyNowBarLiveViews.launchEditor(this, card.id, card.ignoreBouncer);
                    finish();
                }
                case DiyNowBarCard.ACTION_OPEN_APP -> {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(slot.target);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                        finish();
                    }
                }
                case DiyNowBarCard.ACTION_OPEN_URL -> {
                    String url = slot.target.contains("://") ? slot.target : "https://" + slot.target;
                    startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                }
                default -> {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void mutateAndReload(String op, int index) {
        if (DiyNowBarStore.mutate(this, cardId, op, index)) {
            reloadAndRender();
        }
    }

    private TextView makePill(String text, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(21));
        bg.setColor(bgColor);
        tv.setBackground(bg);
        tv.setClickable(true);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }
}
