package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class PanelTabs {
    private final LinearLayout strip;
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
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        LinearLayout.LayoutParams stripLayout = new LinearLayout.LayoutParams(-1, -2);
        stripLayout.setMarginStart(horizontalInsetPx);
        stripLayout.setMarginEnd(horizontalInsetPx);
        stripLayout.topMargin = PanelRows.dp(context, 10);
        stripLayout.bottomMargin = PanelRows.dp(context, 6);
        strip.setLayoutParams(stripLayout);

        TextView[] buttons = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView button = new TextView(context);
            button.setText(names[i]);
            LinearLayout.LayoutParams buttonLayout =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) buttonLayout.setMarginStart(PanelRows.dp(context, 8));
            strip.addView(button, buttonLayout);
            PanelRows.tabButton(button, i == selected);
            buttons[i] = button;
            button.setOnClickListener(
                    v -> {
                        if (selected == index) return;
                        selected = index;
                        for (int j = 0; j < buttons.length; j++) {
                            PanelRows.tabButton(buttons[j], j == selected);
                        }
                        update();
                        changed.accept(selected);
                    });
        }
    }

    View strip() {
        return strip;
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
