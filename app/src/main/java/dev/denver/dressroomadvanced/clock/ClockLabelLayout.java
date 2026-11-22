package dev.denver.dressroomadvanced;

import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

final class ClockLabelLayout {
    private final Map<TextView, TextSize> textSizes = new WeakHashMap<>();
    private final Map<View, ImageSize> imageSizes = new WeakHashMap<>();
    private ClockLabelFit.Size stock;

    private static final int FIXED = 0, DATE = 1, WEATHER = 2;
    private static final ClockLabelFit.Scales STOCK = new ClockLabelFit.Scales(1f, 1f);

    private static final class TextSize {
        float original, applied;

        TextSize(float size) {
            original = applied = size;
        }
    }

    private static final class ImageSize {
        final int width, height, baseWidth, baseHeight;
        int appliedWidth, appliedHeight;

        ImageSize(View view, ViewGroup.LayoutParams params) {
            width = appliedWidth = params.width;
            height = appliedHeight = params.height;
            baseWidth = width >= 0 ? width : view.getMeasuredWidth();
            baseHeight = height >= 0 ? height : view.getMeasuredHeight();
        }
    }

    private enum Flow { OVERLAY, ROW, COLUMN, INLINE }

    private record Part(
            View view, int role, Flow flow, ArrayList<Part> children, boolean localDate,
            int insetWidth, int insetHeight, int marginWidth, int marginHeight,
            float width, float height, int minWidth, int minHeight, int maxWidth, int maxHeight) {

        ClockLabelFit.Size size(ClockLabelFit.Scales scales) {
            float w, h;
            if (children == null) {
                float scale = scale(scales);
                w = Math.max(minWidth, (float) Math.ceil(width * scale) + insetWidth);
                h = Math.max(minHeight, (float) Math.ceil(height * scale) + insetHeight);
            } else {
                w = 0;
                h = 0;
                float secondWidth = 0, secondHeight = 0;
                for (Part child : children) {
                    ClockLabelFit.Size childSize = child.size(scales);
                    if (flow == Flow.INLINE && child.localDate) {
                        secondWidth = Math.max(secondWidth, childSize.width());
                        secondHeight = Math.max(secondHeight, childSize.height());
                    } else {
                        w = flow == Flow.ROW || flow == Flow.INLINE
                                ? w + childSize.width() : Math.max(w, childSize.width());
                        h = flow == Flow.COLUMN
                                ? h + childSize.height() : Math.max(h, childSize.height());
                    }
                }
                w = Math.max(minWidth, Math.max(w, secondWidth) + insetWidth);
                h = Math.max(minHeight, h + secondHeight + insetHeight);
            }
            if (w > maxWidth || h > maxHeight)
                return new ClockLabelFit.Size(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            return new ClockLabelFit.Size(
                    Math.max(0, w + marginWidth), Math.max(0, h + marginHeight));
        }

        float scale(ClockLabelFit.Scales scales) {
            return role == DATE ? scales.date() : role == WEATHER ? scales.weather() : 1f;
        }

        boolean adjustable() {
            if (children == null) return role != FIXED;
            for (Part child : children) if (child.adjustable()) return true;
            return false;
        }
    }

    ClockLabelFit.Size stock() {
        return stock;
    }

    void apply(View clock, int date, int weather, float base, int width, int height) {
        Part root = measure(clock, FIXED, true);
        if (root == null || !root.adjustable()) {
            restore();
            return;
        }
        ClockLabelFit.Size limit = new ClockLabelFit.Size(width, height);
        stock = root.size(STOCK);
        if (!stock.fits(new ClockLabelFit.Size(Float.MAX_VALUE, Float.MAX_VALUE))) stock = null;
        ClockLabelFit.Scales requested = new ClockLabelFit.Scales(
                base * date / ClockLabelScale.DEFAULT, base * weather / ClockLabelScale.DEFAULT);
        ClockLabelFit.Scales fitted = ClockLabelFit.fit(limit, requested, base,
                root::size);
        resize(root, fitted);
    }

    private Part measure(View view, int role, boolean root) {
        if (view.getVisibility() == View.GONE) return null;
        String entry = DateEffects.entryName(view);
        if (DateEffects.isDate(entry)) role = DATE;
        if ("common_weather_container".equals(entry)) role = WEATHER;
        boolean localDate = "common_local_date".equals(entry);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int marginWidth = 0, marginHeight = 0;
        if (!root && params instanceof ViewGroup.MarginLayoutParams margins) {
            marginWidth = margins.isMarginRelative()
                    ? margins.getMarginStart() + margins.getMarginEnd()
                    : margins.leftMargin + margins.rightMargin;
            marginHeight = margins.topMargin + margins.bottomMargin;
        }
        int fixedWidth = !root && params != null && params.width > 0 ? params.width : 0;
        int fixedHeight = !root && params != null && params.height > 0 ? params.height : 0;
        int minWidth = Math.max(view.getMinimumWidth(), fixedWidth);
        int minHeight = Math.max(view.getMinimumHeight(), fixedHeight);
        int maxWidth = fixedWidth > 0 ? fixedWidth : Integer.MAX_VALUE;
        int maxHeight = fixedHeight > 0 ? fixedHeight : Integer.MAX_VALUE;

        if (view instanceof TextView text) {
            float current = text.getTextSize();
            TextPaint paint = text.getPaint();
            if (role != FIXED) {
                TextSize size = textSizes.computeIfAbsent(text, item -> new TextSize(current));
                if (current != size.applied) size.original = current;
                paint = new TextPaint(paint);
                paint.setTextSize(size.original);
            }
            int horizontal = text.getCompoundPaddingLeft() + text.getCompoundPaddingRight();
            int vertical = text.getCompoundPaddingTop() + text.getCompoundPaddingBottom();
            float width = Layout.getDesiredWidth(text.getText(), paint);
            float height = text.getIncludeFontPadding()
                    ? paint.getFontMetrics().bottom - paint.getFontMetrics().top
                    : paint.getFontMetrics().descent - paint.getFontMetrics().ascent;
            if (role == FIXED) {
                // Native time slots
                width = fixedWidth > 0 ? Math.max(0, fixedWidth - horizontal)
                        : Math.max(width, text.getMeasuredWidth() - horizontal);
                height = Math.max(0, text.getMeasuredHeight() - vertical);
                if (height == 0) height = text.getLineHeight();
                maxHeight = Integer.MAX_VALUE;
            } else {
                if (text.getMinWidth() > 0) minWidth = Math.max(minWidth, text.getMinWidth());
                if (text.getMaxWidth() > 0) maxWidth = Math.min(maxWidth, text.getMaxWidth());
            }
            return new Part(text, role, null, null, localDate, horizontal, vertical,
                    marginWidth, marginHeight, width, height, minWidth, minHeight, maxWidth, maxHeight);
        }

        if (view instanceof ImageView && role == WEATHER && params != null
                && params.width != ViewGroup.LayoutParams.MATCH_PARENT
                && params.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            ImageSize size = imageSizes.get(view);
            if (size == null || size.appliedWidth != params.width || size.appliedHeight != params.height)
                size = new ImageSize(view, params);
            if (size.baseWidth > 0 && size.baseHeight > 0) {
                imageSizes.put(view, size);
                return new Part(view, role, null, null, false, 0, 0,
                        marginWidth, marginHeight, size.baseWidth, size.baseHeight, 0, 0,
                        Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
        }

        if (view instanceof ViewGroup group) {
            Flow flow = group instanceof LinearLayout linear
                    ? (linear.getOrientation() == LinearLayout.HORIZONTAL ? Flow.ROW : Flow.COLUMN)
                    : ("common_time_date_sub_container".equals(entry) ? Flow.INLINE : Flow.OVERLAY);
            ArrayList<Part> children = new ArrayList<>(group.getChildCount());
            for (int i = 0; i < group.getChildCount(); i++) {
                Part child = measure(group.getChildAt(i), role, false);
                if (child != null) children.add(child);
            }
            return new Part(view, role, flow, children, localDate,
                    group.getPaddingLeft() + group.getPaddingRight(),
                    group.getPaddingTop() + group.getPaddingBottom(), marginWidth, marginHeight,
                    0, 0, minWidth, minHeight, maxWidth, maxHeight);
        }

        return new Part(view, FIXED, null, null, false, 0, 0, marginWidth, marginHeight,
                fixedWidth > 0 ? fixedWidth : view.getMeasuredWidth(),
                fixedHeight > 0 ? fixedHeight : view.getMeasuredHeight(), minWidth, minHeight,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void resize(Part part, ClockLabelFit.Scales fitted) {
        if (part.children != null) {
            for (Part child : part.children) resize(child, fitted);
        } else if (part.role != FIXED) {
            float scale = part.scale(fitted);
            if (part.view instanceof TextView text) {
                applyText(text, textSizes.get(text), scale);
            } else {
                applyImage(part.view, imageSizes.get(part.view), scale);
            }
        }
    }

    private static void applyText(TextView text, TextSize stock, float scale) {
        float target = scale == 1f ? stock.original : Math.max(1f, stock.original * scale);
        if (text.getTextSize() != target) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
        stock.applied = target;
    }

    private static void applyImage(View view, ImageSize stock, float scale) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int width = scale == 1f ? stock.width : Math.max(1, (int) (stock.baseWidth * scale));
        int height = scale == 1f ? stock.height : Math.max(1, (int) (stock.baseHeight * scale));
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            view.setLayoutParams(params);
        }
        stock.appliedWidth = width;
        stock.appliedHeight = height;
    }

    void restore() {
        textSizes.forEach((text, size) -> {
            if (text.getTextSize() == size.applied) applyText(text, size, 1f);
        });
        imageSizes.forEach((view, size) -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null && params.width == size.appliedWidth && params.height == size.appliedHeight)
                applyImage(view, size, 1f);
        });
        textSizes.clear();
        imageSizes.clear();
        stock = null;
    }
}
