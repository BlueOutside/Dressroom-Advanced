package dev.denver.dressroomadvanced;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genuine live interactive Android Views rendered directly inside NowBarOngoingCardView's
 * expandContentRootView (and reused inside DiyFullscreenCardActivity and Studio preview).
 * Zero RemoteViews limitations: smooth draggable One UI sliders, live hardware toggle pills,
 * and a real-time Canvas Bezier telemetry sparkline chart.
 */
final class DiyNowBarLiveViews {
    private static final String LIVE_VIEW_TAG = "dressroom_diy_live_panel";
    private static final int MAX_SAMPLES = 24;

    static final class TelemetrySample {
        final int batteryPct;
        final float watts;
        final float tempC;
        final int ramPct;

        TelemetrySample(int batteryPct, float watts, float tempC, int ramPct) {
            this.batteryPct = batteryPct;
            this.watts = watts;
            this.tempC = tempC;
            this.ramPct = ramPct;
        }
    }

    private static final List<TelemetrySample> history = new ArrayList<>(MAX_SAMPLES);
    private static final Map<String, Integer> selectedMetrics =
            Collections.synchronizedMap(new HashMap<>());
    private static long lastSampleMs;

    private DiyNowBarLiveViews() {}

    static synchronized void recordTelemetrySample(DiyNowBarPublisher.StatsSnapshot stats) {
        if (stats == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!history.isEmpty() && now - lastSampleMs < 4000L) return;
        lastSampleMs = now;
        int ramPct =
                stats.ramTotalBytes > 0L
                        ? Math.max(
                                0,
                                Math.min(
                                        100,
                                        (int) Math.round(stats.ramUsedBytes * 100.0 / stats.ramTotalBytes)))
                        : 0;
        if (history.size() >= MAX_SAMPLES) {
            history.remove(0);
        }
        history.add(
                new TelemetrySample(
                        stats.batteryPct, Math.abs(stats.batteryWatts), stats.batteryTempC, ramPct));
    }

    static synchronized List<TelemetrySample> snapshotHistory(Context ctx) {
        if (history.isEmpty() && ctx != null) {
            recordTelemetrySample(DiyNowBarPublisher.collectStats(ctx));
        }
        return new ArrayList<>(history);
    }

    /**
     * Attaches or updates a live Android View inside {@code cardView}'s expanded root when the card
     * uses Live View mode (TYPE_CONTROLS or TYPE_STATS with liveExpandedView).
     */
    static View syncLiveExpandedView(View cardView, Bundle extra) {
        if (cardView == null || extra == null) return null;
        int cardType = extra.getInt(DiyNowBarCard.EXTRA_CARD_TYPE, DiyNowBarCard.TYPE_CUSTOM);
        boolean liveEnabled = extra.getBoolean(DiyNowBarCard.EXTRA_LIVE_VIEW, true);
        boolean wantsLive =
                cardType == DiyNowBarCard.TYPE_CONTROLS
                        || (cardType == DiyNowBarCard.TYPE_STATS && liveEnabled);

        ViewGroup expandRoot = resolveExpandRoot(cardView);
        if (expandRoot == null) return null;

        View existing = expandRoot.findViewWithTag(LIVE_VIEW_TAG);
        if (!wantsLive) {
            removeLiveView(expandRoot, existing);
            return null;
        }

        String cardId = extra.getString(DiyNowBarCard.EXTRA_CARD_ID, "");
        String title = extra.getString(DiyNowBarCard.EXTRA_CARD_TITLE, "");
        String subtitle = extra.getString(DiyNowBarCard.EXTRA_CARD_SUBTITLE, "");
        int accent = extra.getInt(DiyNowBarCard.EXTRA_ACCENT, 0xFF3B82F6);
        int statsMask =
                extra.getInt(
                        DiyNowBarCard.EXTRA_STATS_MASK,
                        DiyNowBarCard.STAT_BATTERY | DiyNowBarCard.STAT_RAM);
        boolean ignoreBouncer = extra.getBoolean(DiyNowBarCard.EXTRA_IGNORE_BOUNCER, false);
        DiyNowBarCard badgeCard = new DiyNowBarCard();
        badgeCard.iconType = extra.getInt(DiyNowBarCard.EXTRA_ICON_TYPE, DiyNowBarCard.ICON_SYMBOL);
        badgeCard.iconValue =
                extra.getString(
                        DiyNowBarCard.EXTRA_ICON_VALUE,
                        cardType == DiyNowBarCard.TYPE_CONTROLS ? "sliders" : "cpu");
        badgeCard.iconShape =
                extra.getInt(DiyNowBarCard.EXTRA_ICON_SHAPE, DiyNowBarCard.SHAPE_SQUIRCLE);
        badgeCard.accentColor = accent;

        // Hide the fallback RemoteViews children cleanly while our live View owns the container
        for (int i = 0; i < expandRoot.getChildCount(); i++) {
            View child = expandRoot.getChildAt(i);
            if (child != existing) child.setVisibility(View.GONE);
        }

        Context ctx = cardView.getContext();
        View livePanel;
        if (cardType == DiyNowBarCard.TYPE_CONTROLS) {
            LiveControlsPanel panel;
            if (existing instanceof LiveControlsPanel p) {
                panel = p;
            } else {
                if (existing != null) expandRoot.removeView(existing);
                panel = new LiveControlsPanel(ctx, false);
                panel.setTag(LIVE_VIEW_TAG);
                expandRoot.addView(
                        panel,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            panel.bind(cardId, title, subtitle, accent, badgeCard, ignoreBouncer);
            livePanel = panel;
        } else {
            LiveTelemetryPanel panel;
            if (existing instanceof LiveTelemetryPanel p) {
                panel = p;
            } else {
                if (existing != null) expandRoot.removeView(existing);
                panel = new LiveTelemetryPanel(ctx, false);
                panel.setTag(LIVE_VIEW_TAG);
                expandRoot.addView(
                        panel,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            panel.bind(cardId, title, subtitle, accent, statsMask, badgeCard, ignoreBouncer);
            livePanel = panel;
        }
        resizeExpandedHostToFit(cardView, expandRoot, livePanel);
        return livePanel;
    }

    /**
     * Samsung's NowBarOngoingCardView measures the fallback RemoteViews to set expandCardHeight.
     * Re-measure our live interactive View and update BaseNowBarCardView's expandCardHeight (and
     * currentHeight when already expanded) so zero content ever clips at any DPI or font scale.
     */
    private static void resizeExpandedHostToFit(View cardView, ViewGroup expandRoot, View panel) {
        if (cardView == null || expandRoot == null || panel == null) return;
        try {
            android.util.DisplayMetrics dm = cardView.getResources().getDisplayMetrics();
            int expandW = 0;
            try {
                Object wObj = Reflect.call(cardView, "getExpandCardWidth");
                if (wObj instanceof Number n) expandW = Math.round(n.floatValue());
            } catch (Throwable ignored) {
            }
            if (expandW <= 0) expandW = expandRoot.getWidth();
            if (expandW <= 0) expandW = Math.max(1, dm.widthPixels - Math.round(32f * dm.density));

            int wSpec = View.MeasureSpec.makeMeasureSpec(expandW, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST);
            panel.measure(wSpec, hSpec);
            int measuredH = panel.getMeasuredHeight();
            int minH = Math.round(160f * dm.density);
            try {
                int minId =
                        cardView.getResources()
                                .getIdentifier(
                                        "now_bar_cardview_min_expand_height",
                                        "dimen",
                                        "com.samsung.android.app.aodservice");
                if (minId != 0) minH = cardView.getResources().getDimensionPixelSize(minId);
            } catch (Throwable ignored) {
            }
            int targetH = Math.max(measuredH, minH);
            if (targetH <= 0) return;

            Reflect.call(cardView, "setExpandCardHeight", (float) targetH);
            ViewGroup.LayoutParams plp = panel.getLayoutParams();
            if (plp != null && plp.height != targetH) {
                plp.height = targetH;
                panel.setLayoutParams(plp);
            }
            ViewGroup.LayoutParams rlp = expandRoot.getLayoutParams();
            if (rlp != null && rlp.height != targetH) {
                rlp.height = targetH;
                expandRoot.setLayoutParams(rlp);
            }
            if (isCardCurrentlyExpanded(cardView)) {
                Reflect.call(cardView, "setCurrentHeight", (float) targetH);
                Object containerObj = Reflect.call(cardView, "getCardViewContainer");
                if (containerObj instanceof View container) {
                    ViewGroup.LayoutParams clp = container.getLayoutParams();
                    if (clp != null && clp.height != targetH) {
                        clp.height = targetH;
                        container.setLayoutParams(clp);
                    }
                }
                expandRoot.setScaleX(1f);
                expandRoot.setScaleY(1f);
                DiyNowBarEffects.syncCardGeometry(cardView);
            }
            expandRoot.requestLayout();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCardCurrentlyExpanded(View cardView) {
        try {
            Object st = Reflect.call(cardView, "getCardState");
            if (st instanceof Number n && n.intValue() == 1) return true;
        } catch (Throwable ignored) {
        }
        try {
            Object exp = Reflect.call(cardView, "isExpanded");
            if (Boolean.TRUE.equals(exp)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void clearLiveExpandedView(View cardView) {
        ViewGroup expandRoot = resolveExpandRoot(cardView);
        if (expandRoot == null) return;
        View existing = expandRoot.findViewWithTag(LIVE_VIEW_TAG);
        removeLiveView(expandRoot, existing);
    }

    static void applyDarkAmount(View liveView, float darkAmount) {
        if (liveView == null) return;
        float alpha = Math.max(0.25f, 1f - darkAmount * 0.7f);
        liveView.setAlpha(alpha);
    }

    private static void removeLiveView(ViewGroup expandRoot, View existing) {
        if (existing != null) {
            expandRoot.removeView(existing);
        }
        for (int i = 0; i < expandRoot.getChildCount(); i++) {
            expandRoot.getChildAt(i).setVisibility(View.VISIBLE);
        }
    }

    private static ViewGroup resolveExpandRoot(View cardView) {
        try {
            Object root = Reflect.call(cardView, "getExpandContentRootView");
            if (root instanceof ViewGroup vg) return vg;
        } catch (Throwable ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Live Telemetry Panel (Stats 2x2 Grid + Real-Time Bezier Sparkline Graph)
    // -------------------------------------------------------------------------

    static final class LiveTelemetryPanel extends LinearLayout {
        private static final String[] METRIC_LABELS = {"Bat %", "Power", "Temp", "RAM %"};

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final boolean previewOnly;
        private final TextView titleTv;
        private final TextView subtitleTv;
        private final TextView badgeTv;
        private final ImageView iconIv;
        private final ImageView editBtn;
        private final SparklineChartView chartView;
        private final LinearLayout metricRow;
        private final LinearLayout statsGrid;

        private String cardId = "";
        private String rawTitle = "Device Stats";
        private String rawSubtitle = "";
        private int accentColor = 0xFF10B981;
        private int statsMask = DiyNowBarCard.STAT_BATTERY | DiyNowBarCard.STAT_RAM;
        private int selectedMetric;
        private boolean ignoreBouncer;

        private final Runnable tick =
                new Runnable() {
                    @Override
                    public void run() {
                        if (!isAttachedToWindow()) return;
                        if (isShown()) {
                            refreshData();
                            handler.postDelayed(this, 3000L);
                        }
                    }
                };

        LiveTelemetryPanel(Context context, boolean previewOnly) {
            super(context);
            this.previewOnly = previewOnly;
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(11), dp(14), dp(10));

            // Header
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            iconIv = new ImageView(context);
            header.addView(iconIv, new LayoutParams(dp(30), dp(30)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(dp(9));
            tlp.setMarginEnd(dp(6));

            titleTv = new TextView(context);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(titleTv);

            subtitleTv = new TextView(context);
            subtitleTv.setTextColor(0xCCFFFFFF);
            subtitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            subtitleTv.setSingleLine(true);
            subtitleTv.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitleTv);
            header.addView(texts, tlp);

            badgeTv = new TextView(context);
            badgeTv.setText("LIVE");
            badgeTv.setTextColor(0xDDFFFFFF);
            badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            badgeTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            badgeTv.setPadding(dp(7), dp(2), dp(7), dp(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(9));
            badgeBg.setColor(0x34000000);
            badgeTv.setBackground(badgeBg);
            LayoutParams blp =
                    new LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.setMarginEnd(dp(6));
            header.addView(badgeTv, blp);

            editBtn = new ImageView(context);
            editBtn.setImageBitmap(DiyNowBarIcons.drawActionGlyph("edit", dp(13), Color.WHITE));
            editBtn.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable editBg = new GradientDrawable();
            editBg.setCornerRadius(dp(12));
            editBg.setColor(0x34000000);
            editBtn.setBackground(editBg);
            editBtn.setClickable(!previewOnly);
            if (!previewOnly) {
                editBtn.setOnClickListener(v -> launchEditor(getContext(), cardId, ignoreBouncer));
            }
            header.addView(editBtn, new LayoutParams(dp(24), dp(24)));
            addView(header);

            // Interactive metric tabs
            metricRow = new LinearLayout(context);
            metricRow.setOrientation(HORIZONTAL);
            LayoutParams mlp =
                    new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = dp(7);
            addView(metricRow, mlp);

            // Live Canvas Sparkline
            chartView = new SparklineChartView(context);
            LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
            clp.topMargin = dp(7);
            addView(chartView, clp);

            // Crisp 2x2 (or 1x2) telemetry grid; host height auto-fits dynamically
            statsGrid = new LinearLayout(context);
            statsGrid.setOrientation(VERTICAL);
            LayoutParams glp =
                    new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = dp(7);
            addView(statsGrid, glp);
        }

        void bind(
                String cardId,
                String title,
                String subtitle,
                int accent,
                int statsMask,
                DiyNowBarCard badgeCard,
                boolean ignoreBouncer) {
            this.cardId = cardId == null ? "" : cardId;
            this.rawTitle = title == null || title.isEmpty() ? "Device Stats" : title;
            this.rawSubtitle = subtitle == null ? "" : subtitle;
            this.accentColor = accent == 0 ? 0xFF10B981 : accent;
            this.statsMask = statsMask;
            this.ignoreBouncer = ignoreBouncer;
            Integer savedMetric = selectedMetrics.get(this.cardId);
            if (savedMetric != null && savedMetric >= 0 && savedMetric < METRIC_LABELS.length) {
                this.selectedMetric = savedMetric;
            }

            titleTv.setText(rawTitle);
            iconIv.setBackground(null);
            iconIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (badgeCard != null) {
                iconIv.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(getContext(), badgeCard, dp(30)));
            } else {
                GradientDrawable iconBg = new GradientDrawable();
                iconBg.setCornerRadius(dp(9));
                iconBg.setColor(accentColor);
                iconIv.setBackground(iconBg);
                iconIv.setScaleType(ImageView.ScaleType.CENTER);
                iconIv.setImageBitmap(DiyNowBarIcons.drawSymbolGlyph("cpu", dp(18), Color.WHITE));
            }

            rebuildMetricPills();
            refreshData();
        }

        private void rebuildMetricPills() {
            metricRow.removeAllViews();
            for (int i = 0; i < METRIC_LABELS.length; i++) {
                final int idx = i;
                boolean active = idx == selectedMetric;
                TextView pill = new TextView(getContext());
                pill.setText(METRIC_LABELS[i]);
                pill.setTextColor(active ? Color.WHITE : 0xDEFFFFFF);
                pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                pill.setTypeface(
                        Typeface.create(
                                active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
                pill.setGravity(Gravity.CENTER);
                pill.setPadding(dp(8), dp(5), dp(8), dp(5));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(12));
                bg.setColor(active ? withAlpha(accentColor, 0xE0) : 0x30000000);
                pill.setBackground(bg);
                pill.setClickable(true);
                pill.setOnClickListener(
                        v -> {
                            selectedMetric = idx;
                            if (!cardId.isEmpty()) selectedMetrics.put(cardId, idx);
                            rebuildMetricPills();
                            refreshData();
                        });
                LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) lp.setMarginStart(dp(5));
                metricRow.addView(pill, lp);
            }
        }

        void refreshData() {
            DiyNowBarPublisher.StatsSnapshot snap = DiyNowBarPublisher.collectStats(getContext());
            recordTelemetrySample(snap);
            List<TelemetrySample> samples = snapshotHistory(getContext());

            String sub =
                    rawSubtitle.isEmpty() ? snap.compact(DiyNowBarCard.STAT_BATTERY) : snap.format(rawSubtitle);
            subtitleTv.setText(sub);
            subtitleTv.setVisibility(sub.isEmpty() ? GONE : VISIBLE);

            float[] series = new float[Math.max(samples.size(), 2)];
            String unit;
            String currentText;
            for (int i = 0; i < samples.size(); i++) {
                TelemetrySample s = samples.get(i);
                series[i] =
                        switch (selectedMetric) {
                            case 1 -> s.watts;
                            case 2 -> s.tempC;
                            case 3 -> s.ramPct;
                            default -> s.batteryPct;
                        };
            }
            if (samples.size() == 1) {
                series[1] = series[0];
            }
            TelemetrySample last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
            switch (selectedMetric) {
                case 1 -> {
                    unit = "W";
                    currentText = last == null ? "--W" : String.format(Locale.US, "%.1fW", last.watts);
                }
                case 2 -> {
                    unit = "\u00B0C";
                    currentText =
                            last == null ? "--\u00B0C" : String.format(Locale.US, "%.1f\u00B0C", last.tempC);
                }
                case 3 -> {
                    unit = "%";
                    currentText = last == null ? "--%" : last.ramPct + "%";
                }
                default -> {
                    unit = "%";
                    currentText = last == null ? "--%" : last.batteryPct + "%";
                }
            }
            chartView.setSeries(series, accentColor, METRIC_LABELS[selectedMetric], currentText, unit);

            // Refresh 2x2 (or 1x2) telemetry grid; host height auto-expands cleanly with zero clip
            statsGrid.removeAllViews();
            List<Integer> bits = DiyNowBarCard.enabledStats(statsMask);
            int count = Math.min(4, bits.size());
            for (int r = 0; r < count; r += 2) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                LayoutParams rlp =
                        new LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                if (r > 0) rlp.topMargin = dp(5);
                for (int c = 0; c < 2; c++) {
                    int idx = r + c;
                    LinearLayout tile = new LinearLayout(getContext());
                    tile.setOrientation(VERTICAL);
                    tile.setPadding(dp(10), dp(6), dp(10), dp(6));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(11));
                    bg.setColor(0x30000000);
                    tile.setBackground(bg);

                    LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (c == 0) tlp.setMarginEnd(dp(5));
                    if (idx < count) {
                        int bit = bits.get(idx);
                        TextView l = new TextView(getContext());
                        l.setText(DiyNowBarCard.statName(bit));
                        l.setTextColor(0xCCFFFFFF);
                        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                        l.setSingleLine(true);
                        tile.addView(l);

                        TextView v = new TextView(getContext());
                        v.setText(snap.detail(bit));
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
                statsGrid.addView(row, rlp);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!previewOnly && isShown()) {
                handler.removeCallbacks(tick);
                handler.postDelayed(tick, 3000L);
            }
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (!previewOnly) {
                handler.removeCallbacks(tick);
                if (visibility == VISIBLE && isAttachedToWindow() && isShown()) {
                    refreshData();
                    handler.postDelayed(tick, 3000L);
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        private int dp(int v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }
    }

    /** Crisp GPU-accelerated Canvas Bezier sparkline chart with vertical accent fill. */
    static final class SparklineChartView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();
        private final RectF cardRect = new RectF();

        private float[] values = new float[] {50f, 52f};
        private int accentColor = 0xFF10B981;
        private String metricName = "Bat %";
        private String currentValue = "52%";
        private String unit = "%";

        SparklineChartView(Context context) {
            super(context);
            float d = context.getResources().getDisplayMetrics().density;
            bgPaint.setColor(0x30000000);
            gridPaint.setColor(0x22FFFFFF);
            gridPaint.setStrokeWidth(d);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2.4f * d);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.WHITE);
            glowPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(12f * d);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labelPaint.setColor(0xCCFFFFFF);
            labelPaint.setTextSize(10.5f * d);
        }

        void setSeries(float[] series, int accent, String name, String cur, String unitStr) {
            this.values = series == null || series.length < 2 ? new float[] {0f, 0f} : series;
            this.accentColor = accent;
            this.metricName = name == null ? "" : name;
            this.currentValue = cur == null ? "" : cur;
            this.unit = unitStr == null ? "" : unitStr;
            linePaint.setColor(accentColor);
            glowPaint.setColor(withAlpha(accentColor, 0x55));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float d = getResources().getDisplayMetrics().density;
            float r = 12f * d;
            cardRect.set(0f, 0f, w, h);
            canvas.drawRoundRect(cardRect, r, r, bgPaint);

            float padL = 10f * d;
            float padR = 10f * d;
            float top = 22f * d;
            float bot = h - 8f * d;
            float chartW = Math.max(1f, w - padL - padR);
            float chartH = Math.max(1f, bot - top);

            // Subtle mid horizontal rule
            float midY = top + chartH * 0.5f;
            canvas.drawLine(padL, midY, w - padR, midY, gridPaint);

            // Header metric + live value
            canvas.drawText(metricName, padL, 14f * d, labelPaint);
            float valW = textPaint.measureText(currentValue);
            canvas.drawText(currentValue, w - padR - valW, 14.5f * d, textPaint);

            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (float v : values) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            if (Math.abs(max - min) < 0.5f) {
                min = Math.max(0f, min - 5f);
                max = min + 10f;
            } else {
                float span = max - min;
                min = Math.max(0f, min - span * 0.12f);
                max = max + span * 0.12f;
            }
            float range = Math.max(0.1f, max - min);

            linePath.reset();
            fillPath.reset();
            int n = values.length;
            float prevX = padL;
            float prevY = bot - ((values[0] - min) / range) * chartH;
            linePath.moveTo(prevX, prevY);
            fillPath.moveTo(prevX, bot);
            fillPath.lineTo(prevX, prevY);
            for (int i = 1; i < n; i++) {
                float x = padL + (chartW * i) / (n - 1);
                float y = bot - ((values[i] - min) / range) * chartH;
                float cx = (prevX + x) * 0.5f;
                linePath.cubicTo(cx, prevY, cx, y, x, y);
                fillPath.cubicTo(cx, prevY, cx, y, x, y);
                prevX = x;
                prevY = y;
            }
            fillPath.lineTo(prevX, bot);
            fillPath.close();

            fillPaint.setShader(
                    new LinearGradient(
                            0f,
                            top,
                            0f,
                            bot,
                            withAlpha(accentColor, 0x70),
                            withAlpha(accentColor, 0x05),
                            Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);

            // Live pulsing endpoint dot
            canvas.drawCircle(prevX, prevY, 6f * d, glowPaint);
            canvas.drawCircle(prevX, prevY, 3.2f * d, dotPaint);
        }
    }

    // -------------------------------------------------------------------------
    // Live Controls Panel (Brightness & Media Volume One UI Sliders + Quick Toggles)
    // -------------------------------------------------------------------------

    static final class LiveControlsPanel extends LinearLayout {
        private final boolean previewOnly;
        private final ImageView iconIv;
        private final TextView titleTv;
        private final TextView subTv;
        private final ImageView editBtn;
        private final LivePillSlider brightnessSlider;
        private final LivePillSlider volumeSlider;
        private final TextView torchPill;
        private final TextView ringerPill;
        private final TextView autoBrightPill;

        private String cardId = "";
        private int accentColor = 0xFF06B6D4;
        private boolean ignoreBouncer = true;
        private boolean torchActive;
        private CameraManager.TorchCallback torchCallback;

        LiveControlsPanel(Context context, boolean previewOnly) {
            super(context);
            this.previewOnly = previewOnly;
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(12), dp(14), dp(12));

            // Header
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            iconIv = new ImageView(context);
            header.addView(iconIv, new LayoutParams(dp(30), dp(30)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(dp(9));
            tlp.setMarginEnd(dp(6));

            titleTv = new TextView(context);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(titleTv);

            subTv = new TextView(context);
            subTv.setTextColor(0xCCFFFFFF);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subTv);
            header.addView(texts, tlp);

            editBtn = new ImageView(context);
            editBtn.setImageBitmap(DiyNowBarIcons.drawActionGlyph("edit", dp(13), Color.WHITE));
            editBtn.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable editBg = new GradientDrawable();
            editBg.setCornerRadius(dp(12));
            editBg.setColor(0x28FFFFFF);
            editBtn.setBackground(editBg);
            editBtn.setClickable(!previewOnly);
            if (!previewOnly) {
                editBtn.setOnClickListener(v -> launchEditor(getContext(), cardId, ignoreBouncer));
            }
            header.addView(editBtn, new LayoutParams(dp(24), dp(24)));
            addView(header);

            // Brightness slider
            brightnessSlider = new LivePillSlider(context, "Brightness", "sun", previewOnly);
            brightnessSlider.setOnFractionChanged(
                    frac -> {
                        if (!previewOnly) applyBrightness(getContext(), frac);
                        updateHeaderSummary();
                        syncTogglePills();
                    });
            LayoutParams bLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
            bLp.topMargin = dp(8);
            addView(brightnessSlider, bLp);

            // Media volume slider
            volumeSlider = new LivePillSlider(context, "Media", "music", previewOnly);
            volumeSlider.setOnFractionChanged(
                    frac -> {
                        if (!previewOnly) applyMediaVolume(getContext(), frac);
                        updateHeaderSummary();
                    });
            LayoutParams vLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
            vLp.topMargin = dp(6);
            addView(volumeSlider, vLp);

            // Quick toggles row
            LinearLayout toggles = new LinearLayout(context);
            toggles.setOrientation(HORIZONTAL);
            LayoutParams tgLp =
                    new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            tgLp.topMargin = dp(8);

            torchPill = makeTogglePill(context, "Torch");
            torchPill.setOnClickListener(
                    v -> {
                        if (!previewOnly) {
                            torchActive = toggleSystemTorch(getContext(), !torchActive);
                        } else {
                            torchActive = !torchActive;
                        }
                        syncTogglePills();
                    });
            LayoutParams p1 = new LayoutParams(0, dp(32), 1f);
            p1.setMarginEnd(dp(6));
            toggles.addView(torchPill, p1);

            ringerPill = makeTogglePill(context, "Sound");
            ringerPill.setOnClickListener(
                    v -> {
                        if (!previewOnly) cycleRingerMode(getContext());
                        syncTogglePills();
                    });
            LayoutParams p2 = new LayoutParams(0, dp(32), 1f);
            p2.setMarginEnd(dp(6));
            toggles.addView(ringerPill, p2);

            autoBrightPill = makeTogglePill(context, "Auto");
            autoBrightPill.setOnClickListener(
                    v -> {
                        if (!previewOnly) toggleAutoBrightness(getContext());
                        syncTogglePills();
                    });
            toggles.addView(autoBrightPill, new LayoutParams(0, dp(32), 1f));
            addView(toggles, tgLp);
        }

        void bind(
                String cardId,
                String title,
                String subtitle,
                int accent,
                DiyNowBarCard badgeCard,
                boolean ignoreBouncer) {
            this.cardId = cardId == null ? "" : cardId;
            this.accentColor = accent == 0 ? 0xFF06B6D4 : accent;
            this.ignoreBouncer = ignoreBouncer;

            titleTv.setText(title == null || title.isEmpty() ? "Live Controls" : title);
            iconIv.setBackground(null);
            iconIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (badgeCard != null) {
                iconIv.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(getContext(), badgeCard, dp(30)));
            } else {
                GradientDrawable iconBg = new GradientDrawable();
                iconBg.setCornerRadius(dp(9));
                iconBg.setColor(accentColor);
                iconIv.setBackground(iconBg);
                iconIv.setScaleType(ImageView.ScaleType.CENTER);
                iconIv.setImageBitmap(DiyNowBarIcons.drawSymbolGlyph("sliders", dp(18), Color.WHITE));
            }

            brightnessSlider.setAccent(accentColor);
            volumeSlider.setAccent(accentColor);
            refreshFromSystem();
        }

        void refreshFromSystem() {
            brightnessSlider.setFraction(readBrightnessFraction(getContext()));
            volumeSlider.setFraction(readMediaVolumeFraction(getContext()));
            updateHeaderSummary();
            syncTogglePills();
        }

        private void updateHeaderSummary() {
            int bPct = Math.round(brightnessSlider.getFraction() * 100f);
            int vPct = Math.round(volumeSlider.getFraction() * 100f);
            subTv.setText("Bright " + bPct + "% \u00B7 Vol " + vPct + "%");
        }

        private void syncTogglePills() {
            styleTogglePill(torchPill, torchActive ? "Torch On" : "Torch", torchActive);
            int ringer = readRingerMode(getContext());
            String ringerLabel =
                    switch (ringer) {
                        case AudioManager.RINGER_MODE_SILENT -> "Mute";
                        case AudioManager.RINGER_MODE_VIBRATE -> "Vibrate";
                        default -> "Sound";
                    };
            styleTogglePill(ringerPill, ringerLabel, ringer != AudioManager.RINGER_MODE_SILENT);
            boolean auto = isAutoBrightness(getContext());
            styleTogglePill(autoBrightPill, auto ? "Auto On" : "Auto", auto);
        }

        private TextView makeTogglePill(Context ctx, String text) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setClickable(true);
            return tv;
        }

        private void styleTogglePill(TextView pill, String text, boolean active) {
            pill.setText(text);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(active ? withAlpha(accentColor, 0xE0) : 0x30000000);
            pill.setBackground(bg);
            pill.setTextColor(active ? Color.WHITE : 0xE6FFFFFF);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (previewOnly || torchCallback != null) return;
            try {
                CameraManager cm = getContext().getSystemService(CameraManager.class);
                if (cm != null) {
                    torchCallback =
                            new CameraManager.TorchCallback() {
                                @Override
                                public void onTorchModeChanged(String cameraId, boolean enabled) {
                                    torchActive = enabled;
                                    syncTogglePills();
                                }
                            };
                    cm.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (torchCallback != null) {
                try {
                    CameraManager cm = getContext().getSystemService(CameraManager.class);
                    if (cm != null) cm.unregisterTorchCallback(torchCallback);
                } catch (Throwable ignored) {
                }
                torchCallback = null;
            }
            super.onDetachedFromWindow();
        }

        private int dp(int v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }
    }

    /**
     * Smooth Samsung One UI thick rounded pill slider that blocks NowBarCardStackView horizontal
     * swipe interception so dragging feels crisp directly on the Lock Screen.
     */
    static final class LivePillSlider extends View {
        interface OnFractionChanged {
            void onChanged(float fraction);
        }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();
        private final String label;
        private final String symbolKey;
        private final boolean previewOnly;

        private android.graphics.Bitmap glyphBmp;
        private int accentColor = 0xFF06B6D4;
        private float fraction = 0.5f;
        private OnFractionChanged listener;

        LivePillSlider(Context context, String label, String symbolKey, boolean previewOnly) {
            super(context);
            this.label = label;
            this.symbolKey = symbolKey;
            this.previewOnly = previewOnly;
            float d = context.getResources().getDisplayMetrics().density;
            trackPaint.setColor(0x32000000);
            fillPaint.setColor(withAlpha(accentColor, 0xE0));
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(12.5f * d);
            labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            valuePaint.setColor(0xE6FFFFFF);
            valuePaint.setTextSize(12f * d);
            valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            glyphBmp = DiyNowBarIcons.drawSymbolGlyph(symbolKey, Math.round(18 * d), Color.WHITE);
            setClickable(true);
        }

        void setAccent(int color) {
            this.accentColor = color;
            fillPaint.setColor(withAlpha(color, 0xD8));
            invalidate();
        }

        void setFraction(float f) {
            this.fraction = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        float getFraction() {
            return fraction;
        }

        void setOnFractionChanged(OnFractionChanged listener) {
            this.listener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (previewOnly) return super.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                ViewParent parent = getParent();
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                    parent = parent.getParent();
                }
                updateFromTouch(event.getX());
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                updateFromTouch(event.getX());
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                updateFromTouch(event.getX());
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateFromTouch(float x) {
            int w = getWidth();
            if (w <= 0) return;
            float next = Math.max(0f, Math.min(1f, x / w));
            if (Math.abs(next - fraction) >= 0.008f) {
                fraction = next;
                invalidate();
                if (listener != null) listener.onChanged(fraction);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h * 0.5f;
            rect.set(0f, 0f, w, h);
            clipPath.reset();
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRect(0f, 0f, w, h, trackPaint);
            float fillW = Math.max(h * 0.45f, w * fraction);
            canvas.drawRoundRect(0f, 0f, fillW, h, r, r, fillPaint);
            canvas.restoreToCount(save);

            float d = getResources().getDisplayMetrics().density;
            float textX = 12f * d;
            if (glyphBmp != null) {
                float gy = (h - glyphBmp.getHeight()) * 0.5f;
                canvas.drawBitmap(glyphBmp, 11f * d, gy, null);
                textX = 11f * d + glyphBmp.getWidth() + 8f * d;
            }
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float baseY = (h * 0.5f) - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(label, textX, baseY, labelPaint);

            String pct = Math.round(fraction * 100f) + "%";
            float pw = valuePaint.measureText(pct);
            canvas.drawText(pct, w - 13f * d - pw, baseY, valuePaint);
        }
    }

    // -------------------------------------------------------------------------
    // System Control Helpers (safe inside SystemUI & Activity)
    // -------------------------------------------------------------------------

    static float readBrightnessFraction(Context ctx) {
        try {
            int raw = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return Math.max(0f, Math.min(1f, raw / 255f));
        } catch (Throwable ignored) {
            return 0.5f;
        }
    }

    static void applyBrightness(Context ctx, float fraction) {
        try {
            Settings.System.putInt(
                    ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int value = Math.max(4, Math.min(255, Math.round(fraction * 255f)));
            Settings.System.putInt(
                    ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (Throwable ignored) {
        }
    }

    static boolean isAutoBrightness(Context ctx) {
        try {
            return Settings.System.getInt(
                            ctx.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void toggleAutoBrightness(Context ctx) {
        try {
            boolean next = !isAutoBrightness(ctx);
            Settings.System.putInt(
                    ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    next
                            ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                            : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Throwable ignored) {
        }
    }

    static float readMediaVolumeFraction(Context ctx) {
        try {
            AudioManager am = ctx.getSystemService(AudioManager.class);
            if (am == null) return 0.5f;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return 0f;
            return Math.max(0f, Math.min(1f, am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max));
        } catch (Throwable ignored) {
            return 0.5f;
        }
    }

    static void applyMediaVolume(Context ctx, float fraction) {
        try {
            AudioManager am = ctx.getSystemService(AudioManager.class);
            if (am == null) return;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return;
            int vol = Math.max(0, Math.min(max, Math.round(fraction * max)));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
        } catch (Throwable ignored) {
        }
    }

    static int readRingerMode(Context ctx) {
        try {
            AudioManager am = ctx.getSystemService(AudioManager.class);
            return am == null ? AudioManager.RINGER_MODE_NORMAL : am.getRingerMode();
        } catch (Throwable ignored) {
            return AudioManager.RINGER_MODE_NORMAL;
        }
    }

    static void cycleRingerMode(Context ctx) {
        try {
            AudioManager am = ctx.getSystemService(AudioManager.class);
            if (am == null) return;
            int cur = am.getRingerMode();
            int next =
                    switch (cur) {
                        case AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE;
                        case AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT;
                        default -> AudioManager.RINGER_MODE_NORMAL;
                    };
            am.setRingerMode(next);
        } catch (Throwable ignored) {
        }
    }

    static boolean toggleSystemTorch(Context ctx, boolean enable) {
        try {
            CameraManager cm = ctx.getSystemService(CameraManager.class);
            if (cm == null) return false;
            for (String cameraId : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                Boolean hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash)
                        && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    cm.setTorchMode(cameraId, enable);
                    return enable;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static String controlsSummary(Context ctx) {
        int bPct = Math.round(readBrightnessFraction(ctx) * 100f);
        int vPct = Math.round(readMediaVolumeFraction(ctx) * 100f);
        return "Bright " + bPct + "% \u00B7 Vol " + vPct + "%";
    }

    static void launchEditor(Context ctx, String cardId, boolean ignoreBouncer) {
        try {
            Intent intent =
                    new Intent()
                            .setClassName(
                                    DiyNowBarCard.PACKAGE_ID,
                                    "dev.denver.dressroomadvanced.NowBarStudioActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (cardId != null && !cardId.isEmpty()) {
                intent.putExtra(NowBarStudioActivity.EXTRA_EDIT_CARD_ID, cardId);
            }
            KeyguardManager km = ctx.getSystemService(KeyguardManager.class);
            if (!ignoreBouncer && km != null && km.isKeyguardLocked()) {
                PendingIntent pi =
                        PendingIntent.getActivity(
                                ctx,
                                Math.abs(("live_edit:" + cardId).hashCode()),
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Intent fill = new Intent();
                fill.putExtra("dismissIfInsecure", true);
                Reflect.call(km, "semSetPendingIntentAfterUnlock", pi, fill);
            } else {
                ctx.startActivity(intent);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }
}
