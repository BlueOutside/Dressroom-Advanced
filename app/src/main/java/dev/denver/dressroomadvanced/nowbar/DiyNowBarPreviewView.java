package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact, accurately-sized Samsung One UI Now Bar live preview for NowBarStudioActivity.
 * Renders a native 56dp floating Now Bar pill (tap to expand into the expanded card) with real
 * animated AGSL shaders and Lock Screen / AOD mode toggle.
 */
final class DiyNowBarPreviewView extends LinearLayout {
    private final DiyStudioUi ui;
    private final LinearLayout stage;
    private final OuterAuraLayout outerHost;
    private final DiyNowBarAuraView outerAura;
    private final CardOverlayLayout cardContainer;
    private final View radialGlowView;
    private final DiyNowBarAuraView innerAura;
    private final DiyNowBarNativeShaders.Preview nativeEffectPreview;
    private final FrameLayout contentSlot;
    private final TextView hintLabel;
    private final TextView modeToggle;

    private DiyNowBarCard card;
    private boolean expanded;
    private boolean aodMode;
    private DiyNowBarPublisher.StatsSnapshot cachedStats;
    private long cachedStatsAtMs;

    private final Runnable liveClockTick =
            new Runnable() {
                @Override
                public void run() {
                    if (!isAttachedToWindow() || card == null) return;
                    if (needsLiveTick()) {
                        renderContent();
                        postDelayed(this, 1000L);
                    }
                }
            };

    /** Measures content first, then sizes background shader/glow overlays to exact content bounds. */
    private static final class CardOverlayLayout extends FrameLayout {
        private View contentView;

        CardOverlayLayout(Context context) {
            super(context);
        }

        void setContentView(View content) {
            this.contentView = content;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (contentView == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            measureChildWithMargins(contentView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            int w = Math.max(contentView.getMeasuredWidth(), getSuggestedMinimumWidth());
            int h = Math.max(contentView.getMeasuredHeight(), getSuggestedMinimumHeight());
            int exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
            int exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != contentView && child.getVisibility() != GONE) {
                    child.measure(exactW, exactH);
                }
            }
            setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
        }
    }

    /** Measures the card first, then sizes the outer glow halo exactly pad px larger on all sides. */
    private static final class OuterAuraLayout extends FrameLayout {
        private View cardView;
        private View auraView;
        private final int padPx;

        OuterAuraLayout(Context context, int padPx) {
            super(context);
            this.padPx = padPx;
            setClipChildren(false);
            setClipToPadding(false);
        }

        void attachChildren(View aura, View card) {
            this.auraView = aura;
            this.cardView = card;
            addView(aura);
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(padPx, padPx, padPx, padPx);
            addView(card, lp);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (cardView == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            int availW = MeasureSpec.getSize(widthMeasureSpec);
            int cardWSpec =
                    MeasureSpec.makeMeasureSpec(
                            Math.max(0, availW - padPx * 2), MeasureSpec.getMode(widthMeasureSpec));
            int cardHSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            cardView.measure(cardWSpec, cardHSpec);
            int totalW = cardView.getMeasuredWidth() + padPx * 2;
            int totalH = cardView.getMeasuredHeight() + padPx * 2;
            if (auraView != null && auraView.getVisibility() != GONE) {
                auraView.measure(
                        MeasureSpec.makeMeasureSpec(totalW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(totalH, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(
                    resolveSize(totalW, widthMeasureSpec), resolveSize(totalH, heightMeasureSpec));
        }
    }

    DiyNowBarPreviewView(DiyStudioUi ui) {
        super(ui.host);
        this.ui = ui;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        stage = new LinearLayout(ui.host);
        stage.setOrientation(VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(8));

        int glowPad = ui.dp(12);
        outerHost = new OuterAuraLayout(ui.host, glowPad);
        outerAura = new DiyNowBarAuraView(ui.host);

        cardContainer = new CardOverlayLayout(ui.host);
        cardContainer.setClipToOutline(true);
        cardContainer.setClickable(true);
        cardContainer.setFocusable(true);
        cardContainer.setOnClickListener(
                v -> {
                    if (card != null && card.expandable) setExpanded(!expanded);
                });
        ui.applyNativeRecoil(cardContainer, true);

        radialGlowView = new View(ui.host);
        radialGlowView.setVisibility(GONE);
        cardContainer.addView(radialGlowView);

        innerAura = new DiyNowBarAuraView(ui.host);
        cardContainer.addView(innerAura);

        nativeEffectPreview = new DiyNowBarNativeShaders.Preview(ui.host, this::showUnavailablePreview);
        cardContainer.addView(nativeEffectPreview);

        contentSlot = new FrameLayout(ui.host);
        cardContainer.addView(
                contentSlot,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContainer.setContentView(contentSlot);

        outerHost.attachChildren(outerAura, cardContainer);
        stage.addView(
                outerHost,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Subtle footer hint + Lock/AOD switcher inside stage
        LinearLayout metaRow = new LinearLayout(ui.host);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(2));

        hintLabel = new TextView(ui.host);
        hintLabel.setTextColor(0x99FFFFFF);
        hintLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hintLabel.setClickable(true);
        hintLabel.setOnClickListener(
                v -> {
                    if (card != null && card.expandable) setExpanded(!expanded);
                });
        metaRow.addView(
                hintLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        modeToggle = new TextView(ui.host);
        modeToggle.setTextColor(0xCCFFFFFF);
        modeToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        modeToggle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        modeToggle.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        modeToggle.setBackground(ui.roundedRipple(0x1EFFFFFF, ui.dp(12)));
        modeToggle.setClickable(true);
        modeToggle.setOnClickListener(v -> setAodMode(!aodMode));
        metaRow.addView(modeToggle);

        stage.addView(
                metaRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(
                stage,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateStageChrome();
    }

    void bind(DiyNowBarCard card) {
        this.card = card;
        if (card != null && !card.expandable && expanded) {
            expanded = false;
        }
        updateStageChrome();
        renderContent();
        scheduleTick();
    }

    void setExpanded(boolean expand) {
        if (card != null && !card.expandable) expand = false;
        this.expanded = expand;
        updateStageChrome();
        renderContent();
    }

    void setAodMode(boolean aod) {
        this.aodMode = aod;
        updateStageChrome();
        renderContent();
    }

    private void updateStageChrome() {
        boolean canExpand = card == null || card.expandable;
        if (!canExpand) {
            hintLabel.setText("Collapsed pill only");
        } else {
            hintLabel.setText(expanded ? "Tap card to collapse" : "Tap pill to preview expanded");
        }
        modeToggle.setText(aodMode ? "AOD preview" : "Lock screen");

        GradientDrawable stageBg = new GradientDrawable();
        stageBg.setCornerRadius(ui.dp(26));
        if (aodMode) {
            stageBg.setColor(0xFF000000);
        } else {
            stageBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            stageBg.setColors(new int[] {0xFF101624, 0xFF1A162C, 0xFF101824});
        }
        stage.setBackground(stageBg);

        float radius = ui.dp(expanded ? 24 : 28);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(radius);
        if (aodMode) {
            cardBg.setColor(0xFF08080A);
            cardBg.setStroke(Math.max(1, ui.dp(1)), 0x38FFFFFF);
        } else {
            cardBg.setColor(0xD91D212B);
            cardBg.setStroke(Math.max(1, ui.dp(1) / 2), 0x24FFFFFF);
        }
        cardContainer.setBackground(cardBg);

        if (card == null) return;
        float darkAmt = aodMode ? 1f : 0f;
        int effect = card.effect;
        if (effect == DiyNowBarCard.EFFECT_RADIAL_GLOW && !aodMode) {
            GradientDrawable radial = new GradientDrawable();
            radial.setShape(GradientDrawable.RECTANGLE);
            radial.setCornerRadius(radius);
            radial.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            radial.setGradientCenter(0.14f, 0.5f);
            radial.setGradientRadius(ui.dp(expanded ? 170 : 120));
            radial.setColors(
                    new int[] {
                        DiyStudioUi.withAlpha(card.accentColor, 0x72),
                        DiyStudioUi.withAlpha(card.accentColor, 0x18),
                        0x00000000
                    });
            radialGlowView.setBackground(radial);
            radialGlowView.setVisibility(VISIBLE);
        } else {
            radialGlowView.setVisibility(GONE);
        }

        if (DiyNowBarNativeShaders.isNativeCapableEffect(effect)) {
            innerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
            outerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
            if (!nativeEffectPreview.configure(effect, card.effectColor1, card.effectColor2, radius, !aodMode))
                showUnavailablePreview();
            return;
        }
        nativeEffectPreview.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, false);
        if (effect == DiyNowBarCard.EFFECT_OUTER_GLOW) {
            innerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
            outerAura.configure(effect, card.effectColor1, card.effectColor2, radius, ui.dp(12));
            outerAura.setDarkAmount(darkAmt);
        } else if (effect == DiyNowBarCard.EFFECT_AI_PRESENCE
                || effect == DiyNowBarCard.EFFECT_GRADIENT_FILL
                || effect == DiyNowBarCard.EFFECT_AI_MESH_WARP
                || effect == DiyNowBarCard.EFFECT_GUIDING_LIGHT) {
            outerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
            innerAura.configure(effect, card.effectColor1, card.effectColor2, radius, 0f);
            innerAura.setDarkAmount(darkAmt);
        } else {
            outerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
            innerAura.configure(DiyNowBarCard.EFFECT_NONE, 0, 0, radius, 0f);
        }
    }

    private void showUnavailablePreview() {
        if (hintLabel != null && card != null && DiyNowBarNativeShaders.isNativeCapableEffect(card.effect))
            hintLabel.setText("Native preview unavailable on this provider");
    }

    private void renderContent() {
        contentSlot.removeAllViews();
        if (card == null) return;
        long now = System.currentTimeMillis();
        DiyNowBarPublisher.StatsSnapshot stats =
                card.type == DiyNowBarCard.TYPE_STATS ? getLiveStats(now) : null;
        if (expanded && card.expandable) {
            contentSlot.addView(buildExpandedPreview(stats, now));
        } else {
            contentSlot.addView(buildPillPreview(stats, now));
        }
        requestLayout();
    }

    private View buildPillPreview(DiyNowBarPublisher.StatsSnapshot stats, long now) {
        LinearLayout row = new LinearLayout(ui.host);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56)));
        row.setPadding(ui.dp(10), 0, ui.dp(12), 0);

        ImageView iconView = new ImageView(ui.host);
        iconView.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(ui.host, card, ui.dp(36)));
        iconView.setAlpha(aodMode ? 0.8f : 1f);
        row.addView(iconView, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));

        LinearLayout textCol = new LinearLayout(ui.host);
        textCol.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(ui.dp(11));
        textLp.setMarginEnd(ui.dp(8));

        boolean bigTimer =
                card.type == DiyNowBarCard.TYPE_TIMER
                        && card.bigChronometer
                        && card.useLiveChronometer(now);
        String primaryText =
                bigTimer
                        ? previewPillSubtitle(card, stats, now)
                        : (card.title.isEmpty() ? DiyNowBarCard.TYPE_NAMES[card.type] : card.title);
        String sub =
                bigTimer
                        ? card.subtitle
                        : previewPillSubtitle(ui.host, card, stats, now);

        TextView titleTv = new TextView(ui.host);
        titleTv.setText(primaryText);
        titleTv.setTextColor(aodMode ? 0xDDFFFFFF : Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, bigTimer ? 18 : 14);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleTv);

        if (!sub.isEmpty()) {
            LinearLayout subRow = new LinearLayout(ui.host);
            subRow.setOrientation(HORIZONTAL);
            subRow.setGravity(Gravity.CENTER_VERTICAL);
            if (!card.secondaryIcon.isEmpty()) {
                ImageView mini = new ImageView(ui.host);
                mini.setImageBitmap(
                        DiyNowBarIcons.drawSymbolGlyph(
                                card.secondaryIcon, ui.dp(12), aodMode ? 0x99FFFFFF : 0xCCFFFFFF));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12));
                mlp.setMarginEnd(ui.dp(4));
                subRow.addView(mini, mlp);
            }
            TextView subTv = new TextView(ui.host);
            subTv.setText(sub);
            subTv.setTextColor(aodMode ? 0x99FFFFFF : 0xCCFFFFFF);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            subRow.addView(
                    subTv,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            textCol.addView(subRow);
        }
        row.addView(textCol, textLp);

        String actionGlyph = resolveQuickActionGlyph(card);
        if (actionGlyph != null) {
            ImageView actBtn = new ImageView(ui.host);
            actBtn.setImageBitmap(
                    DiyNowBarIcons.drawActionGlyph(actionGlyph, ui.dp(16), Color.WHITE));
            actBtn.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable actBg = new GradientDrawable();
            actBg.setShape(GradientDrawable.OVAL);
            actBg.setColor(aodMode ? 0x22FFFFFF : DiyStudioUi.withAlpha(card.accentColor, 0x55));
            actBtn.setBackground(actBg);
            row.addView(actBtn, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        }
        return row;
    }

    private View buildExpandedPreview(DiyNowBarPublisher.StatsSnapshot stats, long now) {
        if (card.type == DiyNowBarCard.TYPE_CONTROLS) {
            DiyNowBarLiveViews.LiveControlsPanel panel =
                    new DiyNowBarLiveViews.LiveControlsPanel(ui.host, true);
            panel.bind(card.id, card.title, card.subtitle, card.accentColor, card, card.ignoreBouncer);
            return panel;
        }
        if (card.type == DiyNowBarCard.TYPE_STATS && card.liveExpandedView) {
            DiyNowBarLiveViews.LiveTelemetryPanel panel =
                    new DiyNowBarLiveViews.LiveTelemetryPanel(ui.host, true);
            panel.bind(
                    card.id,
                    card.title,
                    card.subtitle,
                    card.accentColor,
                    card.statsMask,
                    card,
                    card.ignoreBouncer);
            return panel;
        }
        LinearLayout root = new LinearLayout(ui.host);
        root.setOrientation(VERTICAL);
        root.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));

        LinearLayout header = new LinearLayout(ui.host);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(ui.host);
        icon.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(ui.host, card, ui.dp(36)));
        header.addView(icon, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));

        LinearLayout headTexts = new LinearLayout(ui.host);
        headTexts.setOrientation(VERTICAL);
        LinearLayout.LayoutParams htLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htLp.setMarginStart(ui.dp(11));
        htLp.setMarginEnd(ui.dp(8));

        TextView titleTv = new TextView(ui.host);
        titleTv.setText(card.title.isEmpty() ? DiyNowBarCard.TYPE_NAMES[card.type] : card.title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        headTexts.addView(titleTv);

        String sub = previewExpandedSubtitle(card, stats, now);
        if (!sub.isEmpty()) {
            TextView subTv = new TextView(ui.host);
            subTv.setText(sub);
            subTv.setTextColor(0xCCFFFFFF);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            headTexts.addView(subTv);
        }
        header.addView(headTexts, htLp);

        String badge = previewBadge(card, now);
        if (!badge.isEmpty()) {
            TextView badgeTv = new TextView(ui.host);
            badgeTv.setText(badge);
            badgeTv.setTextColor(0xB3FFFFFF);
            badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badgeTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams bLp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            bLp.setMarginEnd(ui.dp(6));
            header.addView(badgeTv, bLp);
        }

        ImageView editIv = new ImageView(ui.host);
        editIv.setImageBitmap(DiyNowBarIcons.drawActionGlyph("edit", ui.dp(14), Color.WHITE));
        editIv.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable editBg = new GradientDrawable();
        editBg.setCornerRadius(ui.dp(14));
        editBg.setColor(0x28FFFFFF);
        editIv.setBackground(editBg);
        header.addView(editIv, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));

        root.addView(header);

        switch (card.type) {
            case DiyNowBarCard.TYPE_CUSTOM -> {
                if (!card.body.isEmpty()) addBodyText(root, card.body);
                if (card.showProgress) {
                    addProgressBlock(
                            root,
                            card.progressLabel.isEmpty() ? "Progress" : card.progressLabel,
                            card.goalSummary(),
                            card.goalPercent());
                }
            }
            case DiyNowBarCard.TYPE_TIMER -> {
                TextView big = new TextView(ui.host);
                String readout =
                        card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                                ? DiyNowBarCard.formatDuration(card.stopwatchElapsedMs(now))
                                : (card.countdownRemainingMs(now) <= 0L
                                        ? "Completed"
                                        : DiyNowBarCard.formatDuration(
                                                card.countdownRemainingMs(now)));
                big.setText(readout);
                big.setTextColor(Color.WHITE);
                big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                LinearLayout.LayoutParams bigLp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                bigLp.topMargin = ui.dp(8);
                root.addView(big, bigLp);
                if (card.timerMode == DiyNowBarCard.TIMER_COUNTDOWN) {
                    int pct = card.countdownProgress(now);
                    addProgressBlock(root, "Elapsed", pct + "%", pct);
                    addPreviewButtons(root, List.of("Restart"));
                } else {
                    addPreviewButtons(
                            root, List.of(card.timerRunning ? "Pause" : "Start", "Reset"));
                }
            }
            case DiyNowBarCard.TYPE_GOAL -> {
                addProgressBlock(
                        root,
                        card.unit.isEmpty() ? "Progress" : card.unit,
                        card.goalSummary(),
                        card.goalPercent());
                addPreviewButtons(
                        root, List.of("-" + card.stepValue, "+" + card.stepValue, "Reset"));
            }
            case DiyNowBarCard.TYPE_STATS -> {
                if (stats != null) addStatsGrid(root, stats);
            }
            case DiyNowBarCard.TYPE_LAUNCHER -> {
                addLauncherRow(root);
                if (!card.body.isEmpty()) addBodyText(root, card.body);
            }
            case DiyNowBarCard.TYPE_CHECKLIST -> {
                addChecklistRows(root);
                if (card.checklistDoneCount() > 0) addPreviewButtons(root, List.of("Uncheck all"));
            }
            default -> {}
        }
        return root;
    }

    private void addBodyText(LinearLayout root, String text) {
        TextView body = new TextView(ui.host);
        body.setText(text);
        body.setTextColor(0xE6FFFFFF);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(8);
        root.addView(body, lp);
    }

    private void addProgressBlock(LinearLayout root, String label, String value, int percent) {
        LinearLayout wrap = new LinearLayout(ui.host);
        wrap.setOrientation(VERTICAL);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(10);

        LinearLayout row = new LinearLayout(ui.host);
        row.setOrientation(HORIZONTAL);
        TextView lTv = new TextView(ui.host);
        lTv.setText(label);
        lTv.setTextColor(0xCCFFFFFF);
        lTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(lTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView vTv = new TextView(ui.host);
        vTv.setText(value);
        vTv.setTextColor(Color.WHITE);
        vTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        vTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(vTv);
        wrap.addView(row);

        ProgressBar pb = new ProgressBar(ui.host, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(Math.max(0, Math.min(100, percent)));
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(ui.dp(4));
        track.setColor(0x2EFFFFFF);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(ui.dp(4));
        fill.setColor(aodMode ? 0xCCFFFFFF : card.accentColor);
        ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        pb.setProgressDrawable(layers);

        LinearLayout.LayoutParams pbLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(7));
        pbLp.topMargin = ui.dp(5);
        wrap.addView(pb, pbLp);
        root.addView(wrap, lp);
    }

    private void addStatsGrid(LinearLayout root, DiyNowBarPublisher.StatsSnapshot stats) {
        List<Integer> bits = DiyNowBarCard.enabledStats(card.statsMask);
        int count = Math.min(4, bits.size());
        LinearLayout grid = new LinearLayout(ui.host);
        grid.setOrientation(VERTICAL);
        LinearLayout.LayoutParams glp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = ui.dp(8);

        for (int r = 0; r < count; r += 2) {
            LinearLayout row = new LinearLayout(ui.host);
            row.setOrientation(HORIZONTAL);
            if (r > 0) {
                LinearLayout.LayoutParams rlp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = ui.dp(6);
                row.setLayoutParams(rlp);
            }
            for (int c = 0; c < 2; c++) {
                int idx = r + c;
                LinearLayout tile = new LinearLayout(ui.host);
                tile.setOrientation(VERTICAL);
                tile.setPadding(ui.dp(9), ui.dp(7), ui.dp(9), ui.dp(7));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(ui.dp(12));
                bg.setColor(0x1EFFFFFF);
                tile.setBackground(bg);

                LinearLayout.LayoutParams tlp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (c == 0) tlp.setMarginEnd(ui.dp(6));
                if (idx < count) {
                    int bit = bits.get(idx);
                    TextView l = new TextView(ui.host);
                    l.setText(DiyNowBarCard.statName(bit));
                    l.setTextColor(0xB3FFFFFF);
                    l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    tile.addView(l);

                    TextView v = new TextView(ui.host);
                    v.setText(stats.detail(bit));
                    v.setTextColor(Color.WHITE);
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    v.setSingleLine(true);
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    tile.addView(v);
                } else {
                    tile.setVisibility(INVISIBLE);
                }
                row.addView(tile, tlp);
            }
            grid.addView(row);
        }
        root.addView(grid, glp);
    }

    private void addLauncherRow(LinearLayout root) {
        List<DiyNowBarCard.ShortcutSlot> active = new ArrayList<>();
        for (DiyNowBarCard.ShortcutSlot slot : card.shortcuts) {
            if (slot.action != DiyNowBarCard.ACTION_NONE) active.add(slot);
        }
        if (active.isEmpty()) {
            addBodyText(root, "Configure up to 4 quick launcher tiles below.");
            return;
        }
        LinearLayout row = new LinearLayout(ui.host);
        row.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams rlp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = ui.dp(10);

        for (int i = 0; i < active.size(); i++) {
            DiyNowBarCard.ShortcutSlot slot = active.get(i);
            LinearLayout tile = new LinearLayout(ui.host);
            tile.setOrientation(VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(12));
            bg.setColor(0x1EFFFFFF);
            tile.setBackground(bg);

            ImageView iv = new ImageView(ui.host);
            iv.setImageBitmap(
                    DiyNowBarIcons.renderShortcutBitmap(ui.host, slot, card.accentColor, ui.dp(28)));
            tile.addView(iv, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));

            TextView tv = new TextView(ui.host);
            String label = slot.label.isEmpty() ? shortcutFallbackLabel(slot) : slot.label;
            tv.setText(label);
            tv.setTextColor(0xE6FFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lblLp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lblLp.topMargin = ui.dp(4);
            tile.addView(tv, lblLp);

            LinearLayout.LayoutParams tlp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i + 1 < active.size()) tlp.setMarginEnd(ui.dp(6));
            row.addView(tile, tlp);
        }
        root.addView(row, rlp);
    }

    private void addChecklistRows(LinearLayout root) {
        if (card.checklist.isEmpty()) {
            addBodyText(root, "Add checklist items below.");
            return;
        }
        LinearLayout list = new LinearLayout(ui.host);
        list.setOrientation(VERTICAL);
        LinearLayout.LayoutParams llp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = ui.dp(8);

        int max = Math.min(4, card.checklist.size());
        for (int i = 0; i < max; i++) {
            DiyNowBarCard.CheckItem item = card.checklist.get(i);
            LinearLayout row = new LinearLayout(ui.host);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(9), ui.dp(6), ui.dp(9), ui.dp(6));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(10));
            bg.setColor(0x1EFFFFFF);
            row.setBackground(bg);

            ImageView cb = new ImageView(ui.host);
            cb.setImageBitmap(DiyNowBarIcons.drawCheckbox(item.done, card.accentColor, ui.dp(16)));
            row.addView(cb, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));

            TextView tv = new TextView(ui.host);
            tv.setText(item.text);
            tv.setTextColor(item.done ? 0x80FFFFFF : Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvLp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvLp.setMarginStart(ui.dp(8));
            row.addView(tv, tvLp);

            LinearLayout.LayoutParams rlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = ui.dp(4);
            list.addView(row, rlp);
        }
        root.addView(list, llp);
    }

    private void addPreviewButtons(LinearLayout root, List<String> labels) {
        if (labels.isEmpty()) return;
        LinearLayout row = new LinearLayout(ui.host);
        row.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams rlp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = ui.dp(10);
        for (int i = 0; i < labels.size(); i++) {
            TextView b = new TextView(ui.host);
            b.setText(labels.get(i));
            b.setTextColor(Color.WHITE);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(16));
            bg.setColor(0x28FFFFFF);
            b.setBackground(bg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ui.dp(32), 1f);
            if (i + 1 < labels.size()) blp.setMarginEnd(ui.dp(6));
            row.addView(b, blp);
        }
        root.addView(row, rlp);
    }

    private String shortcutFallbackLabel(DiyNowBarCard.ShortcutSlot slot) {
        return switch (slot.action) {
            case DiyNowBarCard.ACTION_FLASHLIGHT -> "Torch";
            case DiyNowBarCard.ACTION_OPEN_EDITOR -> "Studio";
            case DiyNowBarCard.ACTION_OPEN_URL -> "Link";
            case DiyNowBarCard.ACTION_OPEN_APP -> {
                String lbl = DiyNowBarIcons.loadAppLabel(ui.host, slot.target);
                yield lbl.isEmpty() ? "App" : lbl;
            }
            default -> "Action";
        };
    }

    static String previewPillSubtitle(
            DiyNowBarCard card, DiyNowBarPublisher.StatsSnapshot stats, long now) {
        return previewPillSubtitle(null, card, stats, now);
    }

    static String previewPillSubtitle(
            Context ctx, DiyNowBarCard card, DiyNowBarPublisher.StatsSnapshot stats, long now) {
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
                    yield DiyNowBarCard.formatDuration(card.stopwatchElapsedMs(now));
                }
                long rem = card.countdownRemainingMs(now);
                if (rem <= 0L) yield "Done";
                yield rem < 86_400_000L
                        ? DiyNowBarCard.formatDuration(rem)
                        : DiyNowBarCard.formatCountdownBadge(rem);
            }
            case DiyNowBarCard.TYPE_CONTROLS ->
                    !card.subtitle.isEmpty()
                            ? card.subtitle
                            : (ctx != null
                                    ? DiyNowBarLiveViews.controlsSummary(ctx)
                                    : "Bright 65% \u00B7 Vol 50%");
            default -> card.subtitle;
        };
    }

    private static String previewExpandedSubtitle(
            DiyNowBarCard card, DiyNowBarPublisher.StatsSnapshot stats, long now) {
        if (card.type == DiyNowBarCard.TYPE_STATS) {
            if (!card.subtitle.isEmpty()) {
                return stats == null ? card.subtitle : stats.format(card.subtitle);
            }
            return previewPillSubtitle(card, stats, now);
        }
        if (!card.subtitle.isEmpty() && card.type != DiyNowBarCard.TYPE_GOAL) return card.subtitle;
        return previewPillSubtitle(card, stats, now);
    }

    private static String previewBadge(DiyNowBarCard card, long now) {
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

    private static String resolveQuickActionGlyph(DiyNowBarCard card) {
        if (card.quickAction == DiyNowBarCard.ACTION_NONE) return null;
        if (card.quickAction == DiyNowBarCard.ACTION_AUTO) {
            return switch (card.type) {
                case DiyNowBarCard.TYPE_GOAL -> "plus";
                case DiyNowBarCard.TYPE_TIMER ->
                        card.timerMode == DiyNowBarCard.TIMER_STOPWATCH
                                ? (card.timerRunning ? "pause" : "play")
                                : null;
                case DiyNowBarCard.TYPE_CHECKLIST ->
                        card.checklistDoneCount() < card.checklist.size() ? "check" : null;
                case DiyNowBarCard.TYPE_CONTROLS -> "bolt";
                default -> null;
            };
        }
        return switch (card.quickAction) {
            case DiyNowBarCard.ACTION_FLASHLIGHT -> "bolt";
            case DiyNowBarCard.ACTION_OPEN_EDITOR -> "edit";
            default -> "open";
        };
    }

    private DiyNowBarPublisher.StatsSnapshot getLiveStats(long now) {
        if (cachedStats == null || now - cachedStatsAtMs > 5000L) {
            cachedStats = DiyNowBarPublisher.collectStats(ui.host);
            cachedStatsAtMs = now;
        }
        return cachedStats;
    }

    private boolean needsLiveTick() {
        return card != null && card.useLiveChronometer(System.currentTimeMillis());
    }

    private void scheduleTick() {
        removeCallbacks(liveClockTick);
        if (isAttachedToWindow() && needsLiveTick()) {
            postDelayed(liveClockTick, 1000L);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(liveClockTick);
        super.onDetachedFromWindow();
    }
}
