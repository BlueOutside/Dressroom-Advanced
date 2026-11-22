package dev.denver.dressroomadvanced;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.util.WeakHashMap;

/** Shared spacing and text hierarchy for rows inside Samsung's editor panels. */
final class PanelRows {
    private static final Object MISSING_RECOIL = new Object();
    private static final WeakHashMap<ClassLoader, Object> recoilConstructors = new WeakHashMap<>();

    private PanelRows() {}

    static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) return Color.GRAY;
        return value.resourceId == 0 ? value.data : context.getColor(value.resourceId);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void text(TextView view, boolean secondary) {
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondary ? 12 : 16);
        view.setTextColor(
                color(
                        view.getContext(),
                        secondary
                                ? android.R.attr.textColorSecondary
                                : android.R.attr.textColorPrimary));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPaddingRelative(0, dp(view.getContext(), 8), 0, dp(view.getContext(), 8));
        ViewGroup.LayoutParams layout = view.getLayoutParams();
        if (layout != null) {
            layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(layout);
        }
    }

    static void heading(TextView view) {
        text(view, true);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPaddingRelative(0, dp(view.getContext(), 16), 0, dp(view.getContext(), 8));
    }

    static void slider(
            android.widget.LinearLayout group, TextView caption, android.view.View slider) {
        Context context = group.getContext();
        group.setPaddingRelative(0, dp(context, 12), 0, dp(context, 12));
        caption.setMinHeight(0);
        caption.setMinimumHeight(0);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setPaddingRelative(0, 0, 0, 0);
        caption.setTextColor(color(context, android.R.attr.textColorSecondary));
        slider.setMinimumHeight(0);
        int inset = dp(context, 16);
        try {
            Object drawable = Reflect.call(slider, "getThumb");
            if (drawable instanceof android.graphics.drawable.Drawable thumb)
                inset =
                        Math.max(
                                inset,
                                Math.max(thumb.getIntrinsicWidth(), thumb.getMinimumWidth()) / 2
                                        + dp(context, 4));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The default inset also leaves room for the native pressed-state ring.
        }
        slider.setPadding(
                Math.max(slider.getPaddingLeft(), inset),
                0,
                Math.max(slider.getPaddingRight(), inset),
                0);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        slider.setClipToOutline(false);
        android.widget.LinearLayout.LayoutParams layout =
                new android.widget.LinearLayout.LayoutParams(slider.getLayoutParams());
        if (slider.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams margins) {
            layout.setMarginStart(margins.getMarginStart());
            layout.setMarginEnd(margins.getMarginEnd());
        }
        layout.height = dp(context, 40);
        layout.topMargin = -dp(context, 4);
        layout.bottomMargin = 0;
        slider.setLayoutParams(layout);
    }

    static android.widget.LinearLayout.LayoutParams divider(Context context, int height) {
        android.widget.LinearLayout.LayoutParams layout =
                new android.widget.LinearLayout.LayoutParams(-1, height);
        layout.topMargin = dp(context, 12);
        layout.bottomMargin = dp(context, 12);
        return layout;
    }

    static void action(TextView view, String title, String summary) {
        text(view, false);
        Context context = view.getContext();
        view.setMinHeight(dp(context, 56));
        view.setPaddingRelative(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setClickable(true);
        view.setFocusable(true);
        buttonAccessibility(view, false);
        int foreground = color(context, android.R.attr.textColorPrimary);
        view.setBackground(glassBackground(context, foreground, 0x10000000, 0x18000000, 0x20000000));
        applyRecoilAnimator(view, true);
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
            margins.topMargin = dp(context, 6);
            margins.bottomMargin = dp(context, 6);
            view.setLayoutParams(margins);
        }
        actionText(view, title, summary);
    }

    static void tabButton(TextView view, boolean selected) {
        Context context = view.getContext();
        int foreground = color(context, android.R.attr.textColorPrimary);
        int secondary = color(context, android.R.attr.textColorSecondary);
        view.setMinHeight(dp(context, 40));
        view.setMinimumHeight(dp(context, 40));
        view.setPaddingRelative(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setAllCaps(false);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        view.setTextColor(selected ? foreground : secondary);
        view.setSelected(selected);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(
                glassBackground(
                        context,
                        foreground,
                        selected ? 0x24000000 : 0x0D000000,
                        selected ? 0x40000000 : 0x18000000,
                        selected ? 0x28000000 : 0x20000000));
        applyRecoilAnimator(view, false);
        buttonAccessibility(view, true);
    }

    private static Drawable glassBackground(
            Context context, int foreground, int fillAlpha, int strokeAlpha, int rippleAlpha) {
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(context, 16));
        fill.setColor((foreground & 0x00ffffff) | fillAlpha);
        fill.setStroke(dp(context, 1), (foreground & 0x00ffffff) | strokeAlpha);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(context, 16));
        mask.setColor(Color.WHITE);
        int recoilColor = (foreground & 0x00ffffff) | rippleAlpha;
        Constructor<?> ctor = recoilConstructor(context.getClassLoader());
        if (ctor != null) {
            try {
                mask.setTint(0);
                mask.setTintBlendMode(android.graphics.BlendMode.SRC_IN);
                return (Drawable) ctor.newInstance(recoilColor, new Drawable[] {fill}, mask);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                mask.setTintList(null);
            }
        }
        return new RippleDrawable(ColorStateList.valueOf(recoilColor), fill, mask);
    }

    private static Constructor<?> recoilConstructor(ClassLoader loader) {
        if (loader == null) return null;
        synchronized (recoilConstructors) {
            if (recoilConstructors.containsKey(loader)) {
                Object cached = recoilConstructors.get(loader);
                return cached instanceof Constructor<?> ctor ? ctor : null;
            }
        }
        Object resolved = MISSING_RECOIL;
        try {
            Class<?> type =
                    Class.forName(
                            "androidx.appcompat.graphics.drawable.SeslRecoilDrawable",
                            false,
                            loader);
            Constructor<?> ctor =
                    type.getConstructor(int.class, Drawable[].class, Drawable.class);
            ctor.setAccessible(true);
            resolved = ctor;
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        synchronized (recoilConstructors) {
            Object existing = recoilConstructors.putIfAbsent(loader, resolved);
            Object value = existing != null ? existing : resolved;
            return value instanceof Constructor<?> ctor ? ctor : null;
        }
    }

    private static void applyRecoilAnimator(TextView view, boolean card) {
        if (view.getStateListAnimator() != null) return;
        Context context = view.getContext();
        Resources res = context.getResources();
        String pkg = context.getPackageName();
        int id =
                card
                        ? res.getIdentifier("sesl_recoil_card_selector", "animator", pkg)
                        : res.getIdentifier("sesl_recoil_button_selector", "animator", pkg);
        if (id != 0) {
            try {
                StateListAnimator nativeAnimator =
                        AnimatorInflater.loadStateListAnimator(context, id);
                if (nativeAnimator != null) {
                    view.setStateListAnimator(nativeAnimator);
                    return;
                }
            } catch (RuntimeException ignored) {
            }
        }
        float scale = card ? 0.98f : 0.96f;
        StateListAnimator animator = new StateListAnimator();
        animator.addState(
                new int[] {android.R.attr.state_pressed},
                scaleAnimator(view, scale, 100L, new LinearInterpolator()));
        animator.addState(
                new int[0],
                scaleAnimator(view, 1f, 350L, new PathInterpolator(0.22f, 0.25f, 0f, 1f)));
        view.setStateListAnimator(animator);
    }

    private static AnimatorSet scaleAnimator(
            TextView view, float target, long durationMs, TimeInterpolator interpolator) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", target);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", target);
        sx.setDuration(durationMs);
        sy.setDuration(durationMs);
        sx.setInterpolator(interpolator);
        sy.setInterpolator(interpolator);
        set.playTogether(sx, sy);
        return set;
    }

    private static void buttonAccessibility(TextView view, boolean selectable) {
        view.setAccessibilityDelegate(
                new android.view.View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                            android.view.View host,
                            android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName(android.widget.Button.class.getName());
                        if (selectable) info.setSelected(host.isSelected());
                    }
                });
    }

    static void actionText(TextView view, String title, String summary) {
        if (summary == null || summary.isBlank()) {
            view.setText(title);
            return;
        }
        SpannableString text = new SpannableString(title + "\n" + summary);
        int start = title.length() + 1;
        text.setSpan(
                new RelativeSizeSpan(.75f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new ForegroundColorSpan(
                        color(view.getContext(), android.R.attr.textColorSecondary)),
                start,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }
}
