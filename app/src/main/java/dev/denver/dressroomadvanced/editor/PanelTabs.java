package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class PanelTabs {
    private final View stripView;
    private final Map<View, Integer> groups = new LinkedHashMap<>();
    private int selected;
    private final int pageCount;

    PanelTabs(
            Context context,
            String[] names,
            int initial,
            int horizontalInsetPx,
            IntConsumer changed) {
        pageCount = names.length;
        selected = Math.max(0, Math.min(names.length - 1, initial));
        boolean scrollable = names.length > 3;

        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);

        HorizontalScrollView scroll = null;
        if (scrollable) {
            scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setPaddingRelative(
                    horizontalInsetPx,
                    PanelRows.dp(context, 10),
                    horizontalInsetPx,
                    PanelRows.dp(context, 6));
            scroll.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll.addView(
                    strip,
                    new HorizontalScrollView.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            stripView = scroll;
        } else {
            LinearLayout.LayoutParams stripLayout = new LinearLayout.LayoutParams(-1, -2);
            stripLayout.setMarginStart(horizontalInsetPx);
            stripLayout.setMarginEnd(horizontalInsetPx);
            stripLayout.topMargin = PanelRows.dp(context, 10);
            stripLayout.bottomMargin = PanelRows.dp(context, 6);
            strip.setLayoutParams(stripLayout);
            stripView = strip;
        }

        TextView[] buttons = new TextView[names.length];
        int gap = PanelRows.dp(context, 8);
        int chipPadH = PanelRows.dp(context, 14);
        HorizontalScrollView scrollRef = scroll;
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView button = new TextView(context);
            button.setText(names[i]);
            LinearLayout.LayoutParams buttonLayout =
                    scrollable
                            ? new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT)
                            : new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) buttonLayout.setMarginStart(gap);
            strip.addView(button, buttonLayout);
            styleButton(button, i == selected, scrollable, chipPadH);
            buttons[i] = button;
            button.setOnClickListener(
                    v -> {
                        if (selected == index) return;
                        selected = index;
                        for (int j = 0; j < buttons.length; j++) {
                            styleButton(buttons[j], j == selected, scrollable, chipPadH);
                        }
                        if (scrollRef != null) scrollToButton(scrollRef, buttons[selected], true);
                        update();
                        changed.accept(selected);
                    });
        }
        if (scrollRef != null && buttons.length > 0) {
            TextView initialBtn = buttons[selected];
            scrollRef.post(() -> scrollToButton(scrollRef, initialBtn, false));
        }
    }

    private static void styleButton(
            TextView button, boolean selected, boolean scrollable, int chipPadH) {
        PanelRows.tabButton(button, selected);
        if (scrollable) {
            button.setPaddingRelative(
                    chipPadH, button.getPaddingTop(), chipPadH, button.getPaddingBottom());
        }
    }

    private static void scrollToButton(
            HorizontalScrollView scroll, TextView button, boolean smooth) {
        int width = scroll.getWidth();
        if (width <= 0) return;
        int target = Math.max(0, button.getLeft() - (width - button.getWidth()) / 2);
        if (smooth) scroll.smoothScrollTo(target, 0);
        else scroll.scrollTo(target, 0);
    }

    View strip() {
        return stripView;
    }

    void clear() {
        groups.clear();
    }

    boolean hasGroup(int page) {
        return groups.containsValue(page);
    }

    LinearLayout group(LinearLayout parent, int page) {
        if (page < 0 || page >= pageCount)
            throw new IllegalArgumentException("Panel page has no tab: " + page);
        LinearLayout group = new LinearLayout(parent.getContext());
        group.setOrientation(LinearLayout.VERTICAL);
        groups.put(group, page);
        group.setVisibility(page == selected ? View.VISIBLE : View.GONE);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private void update() {
        for (Map.Entry<View, Integer> group : groups.entrySet())
            group.getKey().setVisibility(group.getValue() == selected ? View.VISIBLE : View.GONE);
    }
}
