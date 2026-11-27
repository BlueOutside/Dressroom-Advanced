package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interactive drag-to-reorder Now Bar pill stack for NowBarStudioActivity.
 * Renders every live and built-in Now Bar card as a draggable 58dp rounded pill with instant drag
 * on the grip handle, 110ms lift-to-drag anywhere on the pill, smooth One UI neighbor swap
 * animations, haptic ticks, and edge auto-scrolling inside a parent ScrollView.
 */
final class DiyStackDragListView extends LinearLayout {
    private static final PathInterpolator ONE_UI_INTERP =
            new PathInterpolator(0.22f, 0.25f, 0f, 1f);
    private static final long HOLD_TO_DRAG_MS = 110L;
    private static final int AUTO_SCROLL_STEP_DP = 7;

    private final DiyStudioUi ui;
    private final int touchSlop;
    private final int[] screenLoc = new int[2];
    private final List<DiyNowBarStackOrder.LiveEntry> items = new ArrayList<>();
    private final List<TextView> rankBadges = new ArrayList<>();

    private Map<String, DiyNowBarCard> diyById = Collections.emptyMap();
    private Consumer<List<String>> onOrderCommitted;

    private View pendingView;
    private int pendingIndex = -1;
    private float downRawX;
    private float downRawY;
    private float lastRawY;
    private float grabOffsetY;

    private View dragView;
    private int dragIndex = -1;
    private boolean dragging;
    private boolean orderChangedDuringDrag;
    private int autoScrollDir;

    private final Runnable startHoldDrag =
            () -> {
                if (pendingView != null && !dragging && pendingIndex >= 0) {
                    beginDrag(pendingView, pendingIndex, lastRawY);
                }
            };

    private final Runnable autoScrollTick =
            new Runnable() {
                @Override
                public void run() {
                    if (!dragging || autoScrollDir == 0) return;
                    ScrollView sv = findParentScrollView();
                    if (sv == null) return;
                    int delta = ui.dp(AUTO_SCROLL_STEP_DP) * autoScrollDir;
                    int before = sv.getScrollY();
                    sv.scrollBy(0, delta);
                    if (sv.getScrollY() != before) {
                        updateDragPosition();
                        postOnAnimation(this);
                    }
                }
            };

    DiyStackDragListView(DiyStudioUi ui) {
        super(ui.host);
        this.ui = ui;
        this.touchSlop = ViewConfiguration.get(ui.host).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4));
    }

    void bind(
            List<DiyNowBarStackOrder.LiveEntry> entries,
            Map<String, DiyNowBarCard> diyCardsById,
            Consumer<List<String>> onOrderCommitted) {
        cancelDragImmediate();
        this.items.clear();
        if (entries != null) this.items.addAll(entries);
        this.diyById = diyCardsById == null ? Collections.emptyMap() : diyCardsById;
        this.onOrderCommitted = onOrderCommitted;
        rebuildPills();
    }

    private void rebuildPills() {
        removeAllViews();
        rankBadges.clear();
        for (int i = 0; i < items.size(); i++) {
            View pill = buildPillRow(items.get(i), i);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = ui.dp(8);
            addView(pill, lp);
        }
        refreshRankBadges();
    }

    private View buildPillRow(DiyNowBarStackOrder.LiveEntry entry, int index) {
        LinearLayout pill = new LinearLayout(ui.host);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setMinimumHeight(ui.dp(60));
        pill.setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10));
        pill.setBackground(makePillBackground(entry.live, false));
        pill.setClipToOutline(false);

        String diyId = DiyNowBarStackOrder.diyCardIdFromSlot(entry.slotId);
        DiyNowBarCard diyCard = diyId.isEmpty() ? null : diyById.get(diyId);

        ImageView icon = new ImageView(ui.host);
        icon.setImageBitmap(
                DiyNowBarIcons.renderStackSlotBitmap(ui.host, entry, diyCard, ui.dp(38)));
        pill.addView(icon, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));

        LinearLayout texts = new LinearLayout(ui.host);
        texts.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(ui.dp(12));
        textLp.setMarginEnd(ui.dp(8));

        LinearLayout titleLine = new LinearLayout(ui.host);
        titleLine.setOrientation(HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(ui.host);
        String label = DiyNowBarStackOrder.displayLabel(ui.host, entry);
        titleTv.setText(label.isEmpty() ? entry.slotId : label);
        titleTv.setTextColor(ui.primaryText);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(
                titleTv,
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (entry.live) {
            TextView liveBadge = new TextView(ui.host);
            liveBadge.setText(ModuleStrings.getOrFallback(ui.host, R.string.common_badge_live, "LIVE"));
            liveBadge.setTextColor(0xFF22C55E);
            liveBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            liveBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            liveBadge.setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(ui.dp(8));
            badgeBg.setColor(0x2622C55E);
            liveBadge.setBackground(badgeBg);
            LinearLayout.LayoutParams blp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.setMarginStart(ui.dp(6));
            titleLine.addView(liveBadge, blp);
        }
        texts.addView(titleLine);

        TextView subTv = new TextView(ui.host);
        String subText = buildSubtitle(entry, diyCard);
        subTv.setText(subText);
        subTv.setTextColor(entry.live ? ui.accent : ui.secondaryText);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = ui.dp(2);
        texts.addView(subTv, slp);
        pill.addView(texts, textLp);

        TextView rankTv = new TextView(ui.host);
        rankTv.setText("#" + (index + 1));
        rankTv.setTextColor(ui.secondaryText);
        rankTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        rankTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        rankTv.setGravity(Gravity.CENTER);
        rankTv.setMinWidth(ui.dp(24));
        rankBadges.add(rankTv);
        LinearLayout.LayoutParams rlp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMarginEnd(ui.dp(4));
        pill.addView(rankTv, rlp);

        ImageView handle = new ImageView(ui.host);
        handle.setImageBitmap(
                DiyNowBarIcons.drawDragHandle(
                        ui.dp(20), ui.dp(24), DiyStudioUi.withAlpha(ui.secondaryText, 0xCC)));
        handle.setScaleType(ImageView.ScaleType.CENTER);
        handle.setContentDescription(
                ModuleStrings.getOrFallback(ui.host, R.string.nowbar_drag_to_reorder, "Drag to reorder"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(40));
        pill.addView(handle, hlp);

        pill.setTag(entry);
        return pill;
    }

    private String buildSubtitle(DiyNowBarStackOrder.LiveEntry entry, DiyNowBarCard diyCard) {
        if (entry.live) {
            if (!entry.subtitle.isEmpty()) {
                return ModuleStrings.getOrFallback(
                        ui.host,
                        R.string.nowbar_on_nowbar_detail,
                        "On Now Bar \u00B7 %1$s",
                        entry.subtitle);
            }
            return ModuleStrings.getOrFallback(
                    ui.host, R.string.nowbar_currently_on_nowbar, "Currently on Now Bar");
        }
        if (diyCard != null) {
            return ModuleStrings.getOrFallback(
                    ui.host,
                    R.string.nowbar_diy_card_type,
                    "DIY %1$s card",
                    DiyNowBarCard.typeName(ui.host, diyCard.type));
        }
        return ModuleStrings.getOrFallback(
                ui.host, R.string.nowbar_applies_when_active, "Applies when active");
    }

    private GradientDrawable makePillBackground(boolean live, boolean elevated) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(30));
        if (elevated) {
            bg.setColor(ui.dark ? 0xFF23242A : 0xFFFFFFFF);
            bg.setStroke(Math.max(1, ui.dp(1)), DiyStudioUi.withAlpha(ui.accent, 0xCC));
        } else {
            bg.setColor(ui.cardBg);
            if (live) {
                bg.setStroke(Math.max(1, ui.dp(1)), DiyStudioUi.withAlpha(0xFF22C55E, 0x55));
            }
        }
        return bg;
    }

    private void refreshRankBadges() {
        for (int i = 0; i < rankBadges.size(); i++) {
            TextView badge = rankBadges.get(i);
            badge.setText("#" + (i + 1));
            badge.setTextColor(i == 0 ? ui.accent : ui.secondaryText);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                removeCallbacks(startHoldDrag);
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                lastRawY = downRawY;
                int hitIdx = findChildAtY(ev.getY());
                if (hitIdx < 0) return false;
                pendingIndex = hitIdx;
                pendingView = getChildAt(hitIdx);
                boolean hitHandle = ev.getX() >= pendingView.getRight() - ui.dp(56);
                if (hitHandle) {
                    beginDrag(pendingView, pendingIndex, downRawY);
                } else {
                    postDelayed(startHoldDrag, HOLD_TO_DRAG_MS);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                lastRawY = ev.getRawY();
                if (!dragging) {
                    float dx = Math.abs(ev.getRawX() - downRawX);
                    float dy = Math.abs(lastRawY - downRawY);
                    if (dy > touchSlop || dx > touchSlop) {
                        removeCallbacks(startHoldDrag);
                        pendingView = null;
                        pendingIndex = -1;
                        return false;
                    }
                    return true;
                }
                updateDragPosition();
                updateAutoScroll();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(startHoldDrag);
                pendingView = null;
                pendingIndex = -1;
                if (dragging) {
                    finishDrag();
                    return true;
                }
                return true;
            }
            default -> {
                return super.onTouchEvent(ev);
            }
        }
    }

    private int findChildAtY(float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (y >= child.getTop() - ui.dp(4) && y <= child.getBottom() + ui.dp(4)) {
                return i;
            }
        }
        return -1;
    }

    private void beginDrag(View view, int index, float rawY) {
        if (view == null || index < 0 || index >= items.size()) return;
        dragging = true;
        orderChangedDuringDrag = false;
        dragView = view;
        dragIndex = index;
        lastRawY = rawY;

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);

        getLocationOnScreen(screenLoc);
        float pointerYInList = rawY - screenLoc[1];
        grabOffsetY = pointerYInList - view.getTop();

        DiyNowBarStackOrder.LiveEntry entry = items.get(index);
        view.setBackground(makePillBackground(entry.live, true));
        view.setTranslationZ(ui.dp(12));
        view.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(140L)
                .setInterpolator(ONE_UI_INTERP)
                .start();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void updateDragPosition() {
        if (!dragging || dragView == null || dragIndex < 0) return;
        getLocationOnScreen(screenLoc);
        float pointerYInList = lastRawY - screenLoc[1];
        float minTop = getPaddingTop();
        float maxTop = Math.max(minTop, getHeight() - getPaddingBottom() - dragView.getHeight());
        float desiredTop = Math.max(minTop, Math.min(maxTop, pointerYInList - grabOffsetY));
        dragView.setTranslationY(desiredTop - dragView.getTop());

        float centerY = desiredTop + dragView.getHeight() * 0.5f;
        if (dragIndex > 0) {
            View above = getChildAt(dragIndex - 1);
            if (centerY < above.getTop() + above.getHeight() * 0.5f) {
                swapDraggedWith(dragIndex - 1);
                return;
            }
        }
        if (dragIndex + 1 < getChildCount()) {
            View below = getChildAt(dragIndex + 1);
            if (centerY > below.getTop() + below.getHeight() * 0.5f) {
                swapDraggedWith(dragIndex + 1);
            }
        }
    }

    private void swapDraggedWith(int targetIndex) {
        if (dragView == null || targetIndex < 0 || targetIndex >= items.size()) return;
        final View neighbor = getChildAt(targetIndex);
        final int oldNeighborTop = neighbor.getTop();
        final int fromIndex = dragIndex;

        Collections.swap(items, fromIndex, targetIndex);
        Collections.swap(rankBadges, fromIndex, targetIndex);
        orderChangedDuringDrag = true;

        removeViewAt(fromIndex);
        addView(dragView, targetIndex);
        dragIndex = targetIndex;

        // Re-apply vertical spacing margins cleanly across all children
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getLayoutParams() instanceof LinearLayout.LayoutParams lp) {
                int wantTop = i == 0 ? 0 : ui.dp(8);
                if (lp.topMargin != wantTop) {
                    lp.topMargin = wantTop;
                    child.setLayoutParams(lp);
                }
            }
        }

        refreshRankBadges();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

        getViewTreeObserver()
                .addOnPreDrawListener(
                        new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                getViewTreeObserver().removeOnPreDrawListener(this);
                                int delta = oldNeighborTop - neighbor.getTop();
                                if (delta != 0) {
                                    neighbor.animate().cancel();
                                    neighbor.setTranslationY(delta);
                                    neighbor.animate()
                                            .translationY(0f)
                                            .setDuration(180L)
                                            .setInterpolator(ONE_UI_INTERP)
                                            .start();
                                }
                                if (dragging && dragView != null) {
                                    updateDragPosition();
                                }
                                return true;
                            }
                        });
    }

    private void updateAutoScroll() {
        ScrollView sv = findParentScrollView();
        if (sv == null) {
            autoScrollDir = 0;
            return;
        }
        int[] svLoc = new int[2];
        sv.getLocationOnScreen(svLoc);
        int edgeZone = ui.dp(64);
        int nextDir = 0;
        if (lastRawY < svLoc[1] + edgeZone) {
            nextDir = -1;
        } else if (lastRawY > svLoc[1] + sv.getHeight() - edgeZone) {
            nextDir = 1;
        }
        if (nextDir != autoScrollDir) {
            autoScrollDir = nextDir;
            removeCallbacks(autoScrollTick);
            if (autoScrollDir != 0) {
                postOnAnimation(autoScrollTick);
            }
        }
    }

    private void finishDrag() {
        autoScrollDir = 0;
        removeCallbacks(autoScrollTick);
        View view = dragView;
        int idx = dragIndex;
        boolean changed = orderChangedDuringDrag;
        dragging = false;
        dragView = null;
        dragIndex = -1;
        orderChangedDuringDrag = false;

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);

        if (view != null) {
            boolean live = idx >= 0 && idx < items.size() && items.get(idx).live;
            view.setBackground(makePillBackground(live, false));
            view.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(ONE_UI_INTERP)
                    .withEndAction(() -> view.setTranslationZ(0f))
                    .start();
        }

        if (changed && onOrderCommitted != null) {
            List<String> slotOrder = new ArrayList<>(items.size());
            for (DiyNowBarStackOrder.LiveEntry e : items) {
                if (!e.slotId.isEmpty()) slotOrder.add(e.slotId);
            }
            onOrderCommitted.accept(slotOrder);
        }
    }

    private void cancelDragImmediate() {
        removeCallbacks(startHoldDrag);
        removeCallbacks(autoScrollTick);
        autoScrollDir = 0;
        dragging = false;
        pendingView = null;
        pendingIndex = -1;
        dragView = null;
        dragIndex = -1;
        orderChangedDuringDrag = false;
    }

    private ScrollView findParentScrollView() {
        ViewParent p = getParent();
        while (p != null) {
            if (p instanceof ScrollView sv) return sv;
            p = p.getParent();
        }
        return null;
    }
}
