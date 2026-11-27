package dev.denver.dressroomadvanced;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BlendMode;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Genuine Samsung One UI 9 SESL UI bridge for NowBarStudioActivity.
 * Loads Samsung's actual SwitchCompat, SeslRecoilDrawable, SESL icons, RadioButtons, CheckBoxes,
 * and recoil animators directly from AOD / Dressroom with theme-matched dark/light configuration.
 */
final class DiyStudioUi {
    private static final String PKG_DRESSROOM = "com.samsung.android.app.dressroom";
    private static final String PKG_AOD = "com.samsung.android.app.aodservice";

    final Context host;
    final Context aodThemed;
    final Context dressroom;
    final boolean dark;
    final int pageBg;
    final int cardBg;
    final int pillBg;
    final int dividerColor;
    final int recoilColor;
    final int primaryText;
    final int secondaryText;
    final int accent;
    final int dangerColor;

    private final Context switchCompatCtx;
    private final Constructor<?> switchCompatCtor;
    private final Constructor<?> seslRecoilCtor;
    private final Method smoothCornerMethod;
    private final boolean wallpaperThemeActive;

    DiyStudioUi(Context activityContext) {
        this.host = activityContext;
        int nightMask =
                activityContext.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
        this.dark = nightMask == Configuration.UI_MODE_NIGHT_YES;
        this.wallpaperThemeActive = checkWallpaperThemeActive(activityContext);

        Context aodPkg = createCodeContext(activityContext, PKG_AOD);
        Context dressPkg = createCodeContext(activityContext, PKG_DRESSROOM);
        this.aodThemed = wrapSeslTheme(aodPkg, activityContext, PKG_AOD);
        this.dressroom = wrapSeslTheme(dressPkg, activityContext, PKG_DRESSROOM);

        Context swPkg = dressPkg != null ? dressPkg : aodPkg;
        Constructor<?> swCtor = resolveSwitchCompatCtor(swPkg);
        if (swCtor == null && swPkg != aodPkg && aodPkg != null) {
            swPkg = aodPkg;
            swCtor = resolveSwitchCompatCtor(swPkg);
        }
        this.switchCompatCtor = swCtor;
        this.switchCompatCtx =
                swPkg != null
                        ? new ContextThemeWrapper(swPkg, android.R.style.Theme_DeviceDefault_DayNight)
                        : null;
        this.seslRecoilCtor = resolveRecoilCtor(aodThemed != null ? aodThemed : dressroom);
        this.smoothCornerMethod = resolveSmoothCornerMethod();

        int defaultPage = dark ? 0xFF010101 : 0xFFF6F6F8;
        int defaultCard = dark ? 0xFF17171A : 0xFFFCFCFF;
        int defaultAccent = PanelRows.color(activityContext, android.R.attr.colorAccent);
        if (defaultAccent == Color.GRAY || defaultAccent == 0) {
            defaultAccent = dark ? 0xFF3E91FF : 0xFF0381FE;
        }

        this.pageBg =
                resolveColor(
                        dark ? "sesl_round_and_bgcolor_dark" : "sesl_round_and_bgcolor_light",
                        defaultPage);
        this.cardBg =
                resolveColor(
                        dark ? "sesl_background_color_dark" : "sesl_background_color_light",
                        defaultCard);
        this.accent =
                resolveColor(
                        dark ? "sesl_primary_color_dark" : "sesl_primary_color_light",
                        defaultAccent);
        this.primaryText = dark ? 0xFFFAFAFA : 0xFF010101;
        this.secondaryText = dark ? 0xFFA3A3A8 : 0xFF636368;
        this.pillBg = dark ? 0x18FFFFFF : 0x0B000000;
        this.dividerColor = dark ? 0x1AFFFFFF : 0x14000000;
        this.recoilColor =
                resolveColor(
                        dark ? "sesl_ripple_color_dark" : "sesl_ripple_color_light",
                        dark ? 0x26FFFFFF : 0x14000000);
        this.dangerColor = dark ? 0xFFFF6B6B : 0xFFD93025;
    }

    int dp(int value) {
        return PanelRows.dp(host, value);
    }

    /** Flat 56dp One UI top app bar with native SESL icons and borderless SESL recoil. */
    LinearLayout toolbar(String title, Runnable onBack, View... endActions) {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(56));
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));

        if (onBack != null) {
            bar.addView(
                    toolbarIconButton(
                            "back",
                            primaryText,
                            ModuleStrings.getOrFallback(
                                    host, R.string.nowbar_navigate_up, "Navigate up"),
                            onBack));
        }

        TextView titleTv = new TextView(host);
        titleTv.setText(title);
        titleTv.setTextColor(primaryText);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(onBack != null ? dp(4) : dp(16));
        tLp.setMarginEnd(dp(8));
        bar.addView(titleTv, tLp);

        if (endActions != null) {
            for (View action : endActions) {
                if (action != null) bar.addView(action);
            }
        }
        return bar;
    }

    ImageView toolbarIconButton(String glyph, int tint, String desc, Runnable onClick) {
        ImageView iv = new ImageView(host);
        Drawable sesl = resolveToolbarDrawable(glyph);
        if (sesl != null) {
            Drawable mutated = sesl.mutate();
            mutated.setTint(tint);
            iv.setImageDrawable(mutated);
            int p = dp(10);
            iv.setPadding(p, p, p, p);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            iv.setImageBitmap(DiyNowBarIcons.drawActionGlyph(glyph, dp(22), tint));
            iv.setScaleType(ImageView.ScaleType.CENTER);
        }
        iv.setContentDescription(desc);
        iv.setBackground(makeRecoilDrawable(Color.TRANSPARENT, dp(22)));
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        applyRecoil(iv, "sesl_recoil_button_selector", 0.94f);
        if (onClick != null) iv.setOnClickListener(v -> onClick.run());
        return iv;
    }

    private Drawable resolveToolbarDrawable(String glyph) {
        return switch (glyph) {
            case "back" ->
                    resolveSeslDrawable(
                            dark ? "sesl_ic_ab_back_dark" : "sesl_ic_ab_back_light",
                            "sesl_ic_ab_back_dark");
            case "plus" -> resolveSeslDrawable("sesl_ic_ab_add_mtrl", "ic_add_button");
            case "more" ->
                    resolveSeslDrawable(
                            dark ? "sesl_ic_menu_overflow_dark" : "sesl_ic_menu_overflow_light",
                            "sesl_ic_menu_overflow_dark");
            case "trash" -> resolveSeslDrawable("cover_screen_reply_delete_btn");
            default -> null;
        };
    }

    /** Standard One UI Cancel | Save bottom action bar with pure SESL recoil (zero pink ripple). */
    LinearLayout bottomActionBar(
            String leftLabel, Runnable onLeft, String rightLabel, Runnable onRight) {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(pageBg);
        bar.setPadding(dp(16), dp(6), dp(16), dp(10));

        TextView left = bottomBarButton(leftLabel, primaryText, onLeft);
        TextView right = bottomBarButton(rightLabel, accent, onRight);

        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lLp.setMarginEnd(dp(8));
        bar.addView(left, lLp);
        bar.addView(right, new LinearLayout.LayoutParams(0, dp(46), 1f));
        return bar;
    }

    private TextView bottomBarButton(String label, int color, Runnable onClick) {
        TextView tv = new TextView(host);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setBackground(makeRecoilDrawable(Color.TRANSPARENT, dp(23)));
        tv.setClickable(true);
        tv.setFocusable(true);
        applyRecoil(tv, "sesl_recoil_dialog_button_selector", 0.96f);
        if (onClick != null) tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    /** One UI section subheader placed outside above a rounded preference group. */
    LinearLayout sectionHeader(String title, String trailing) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), dp(14), dp(22), dp(6));

        TextView left = new TextView(host);
        left.setText(title);
        left.setTextColor(secondaryText);
        left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        left.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailing != null && !trailing.isEmpty()) {
            TextView right = new TextView(host);
            right.setText(trailing);
            right.setTextColor(secondaryText);
            right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            row.addView(right);
        }
        return row;
    }

    /** Borderless 26dp One UI rounded preference card group (zero stroke). */
    LinearLayout cardGroup() {
        LinearLayout group = new LinearLayout(host);
        group.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(dp(26));
        group.setBackground(bg);
        group.setClipToOutline(true);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        group.setLayoutParams(lp);
        return group;
    }

    /** Standard One UI full-width list preference row with pure SESL recoil (no Android ripple). */
    LinearLayout prefRow(
            String title, String summary, boolean accentSummary, Runnable onClick) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        if (onClick != null) {
            row.setBackground(listRowRecoil());
            row.setClickable(true);
            row.setFocusable(true);
            applyRecoil(row, "sesl_recoil_list_selector", 0.99f);
            row.setOnClickListener(v -> onClick.run());
        }

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(host);
        titleTv.setText(title);
        titleTv.setTextColor(primaryText);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleTv);

        if (summary != null && !summary.isEmpty()) {
            TextView subTv = new TextView(host);
            subTv.setText(summary);
            subTv.setTextColor(accentSummary ? accent : secondaryText);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            subTv.setMaxLines(2);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            texts.addView(subTv, slp);
        }

        row.addView(
                texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Standard One UI switch preference row using genuine SESL SwitchCompat. */
    LinearLayout switchRow(
            String title, String summary, boolean checked, Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(20), dp(12), dp(18), dp(12));
        row.setBackground(listRowRecoil());
        row.setClickable(true);
        row.setFocusable(true);
        applyRecoil(row, "sesl_recoil_list_selector", 0.99f);

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(host);
        titleTv.setText(title);
        titleTv.setTextColor(primaryText);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(titleTv);

        if (summary != null && !summary.isEmpty()) {
            TextView subTv = new TextView(host);
            subTv.setText(summary);
            subTv.setTextColor(secondaryText);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams slp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            texts.addView(subTv, slp);
        }

        LinearLayout.LayoutParams tlp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginEnd(dp(14));
        row.addView(texts, tlp);

        CompoundButton sw = createSwitch(checked, false, onChange);
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    /**
     * Instantiates Samsung's genuine One UI 9 androidx.appcompat.widget.SwitchCompat directly from
     * Dressroom/AOD with Samsung's exact 32dp x 20dp SeslShapeDrawable-matched track crossfade,
     * animated/themed SESL thumb selector, 300ms PathInterpolator slide, haptic feedback, and
     * SeslRecoilDrawable touch feedback.
     */
    CompoundButton createSwitch(boolean checked, boolean clickable, Consumer<Boolean> onChange) {
        int widthPx = resolveDimenPx("sesl_switch_width", dp(32));
        int heightPx = dp(20);
        int radiusPx = dp(10);

        Drawable seslThumb = resolveSwitchThumbDrawable();
        Drawable seslTrack = buildSeslSwitchTrack(widthPx, heightPx, radiusPx);

        CompoundButton sw = null;
        if (switchCompatCtor != null && switchCompatCtx != null) {
            try {
                Object instance = switchCompatCtor.newInstance(switchCompatCtx, null);
                if (instance instanceof CompoundButton nativeSw) {
                    if (seslThumb != null) Reflect.call(nativeSw, "setThumbDrawable", seslThumb);
                    Reflect.call(nativeSw, "setTrackDrawable", seslTrack);
                    Reflect.call(nativeSw, "setShowText", false);
                    nativeSw.setText(null);
                    nativeSw.setPadding(0, 0, 0, 0);
                    nativeSw.setGravity(Gravity.CENTER_VERTICAL);
                    nativeSw.setBackground(makeRecoilDrawable(Color.TRANSPARENT, dp(18)));
                    try {
                        Reflect.call(nativeSw, "setCheckedWithoutAnimation", checked);
                    } catch (Throwable ignored) {
                        nativeSw.setChecked(checked);
                        nativeSw.jumpDrawablesToCurrentState();
                    }
                    sw = nativeSw;
                }
            } catch (Throwable error) {
                android.util.Log.w(
                        DressroomModule.TAG,
                        "Native SESL SwitchCompat construction fell back",
                        error);
            }
        }

        if (sw == null) {
            Switch fallback = new Switch(host);
            if (seslThumb != null) {
                fallback.setThumbDrawable(seslThumb);
            } else {
                GradientDrawable thumb = new GradientDrawable();
                thumb.setShape(GradientDrawable.OVAL);
                thumb.setSize(dp(16), dp(16));
                thumb.setColor(Color.WHITE);
                fallback.setThumbDrawable(thumb);
            }
            fallback.setThumbTintList(null);
            fallback.setTrackDrawable(seslTrack);
            fallback.setTrackTintList(null);
            fallback.setSwitchMinWidth(widthPx);
            fallback.setShowText(false);
            fallback.setTextOn("");
            fallback.setTextOff("");
            fallback.setBackground(makeRecoilDrawable(Color.TRANSPARENT, dp(18)));
            fallback.setChecked(checked);
            fallback.jumpDrawablesToCurrentState();
            sw = fallback;
        }

        sw.setClickable(clickable);
        sw.setFocusable(false);
        if (onChange != null) {
            sw.setOnCheckedChangeListener((btn, isChecked) -> onChange.accept(isChecked));
        }
        return sw;
    }

    private Drawable resolveSwitchThumbDrawable() {
        if (wallpaperThemeActive) {
            Drawable themedThumb =
                    resolveSeslDrawable(
                            dark
                                    ? "sesl_switch_thumb_selector_dark_for_theme"
                                    : "sesl_switch_thumb_selector_light_for_theme");
            if (themedThumb != null) return themedThumb;
        }
        return resolveSeslDrawable(
                dark ? "sesl_switch_thumb_selector_dark" : "sesl_switch_thumb_selector_light");
    }

    private Drawable buildSeslSwitchTrack(int widthPx, int heightPx, int radiusPx) {
        int onColor = resolveColor("sesl_switch_track_on_color_light", accent);
        int offColor =
                resolveColor(
                        dark ? "sesl_switch_track_off_color_dark" : "sesl_switch_track_off_color",
                        dark ? 0xFF636368 : 0xFF99999E);
        int onDisabled =
                resolveColor(
                        "sesl_switch_track_on_disabled_color", withAlpha(onColor, 0x66));
        int offDisabled =
                resolveColor(
                        dark
                                ? "sesl_switch_track_off_disabled_color_dark"
                                : "sesl_switch_track_off_disabled_color",
                        withAlpha(offColor, 0x66));

        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[] {-android.R.attr.state_enabled, android.R.attr.state_checked},
                buildSwitchTrackLayer(widthPx, heightPx, radiusPx, onDisabled));
        states.addState(
                new int[] {-android.R.attr.state_enabled},
                buildSwitchTrackLayer(widthPx, heightPx, radiusPx, offDisabled));
        states.addState(
                new int[] {android.R.attr.state_checked},
                buildSwitchTrackLayer(widthPx, heightPx, radiusPx, onColor));
        states.addState(new int[0], buildSwitchTrackLayer(widthPx, heightPx, radiusPx, offColor));
        return states;
    }

    private Drawable buildSwitchTrackLayer(int widthPx, int heightPx, int radiusPx, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radiusPx);
        shape.setSize(widthPx, heightPx);
        shape.setColor(color);
        if (smoothCornerMethod != null) {
            try {
                smoothCornerMethod.invoke(shape, Boolean.TRUE);
            } catch (Throwable ignored) {
            }
        }
        LayerDrawable layer = new LayerDrawable(new Drawable[] {shape});
        layer.setLayerGravity(0, Gravity.CENTER);
        return layer;
    }

    /** Inflates Samsung's genuine One UI animated SESL CheckBox. */
    CompoundButton createCheckBox(boolean checked, Consumer<Boolean> onChange) {
        CompoundButton cb = null;
        if (aodThemed != null) {
            try {
                int layoutId =
                        aodThemed
                                .getResources()
                                .getIdentifier(
                                        "sesl_preference_widget_checkbox", "layout", PKG_AOD);
                if (layoutId != 0) {
                    View v = LayoutInflater.from(aodThemed).inflate(layoutId, null, false);
                    if (v instanceof CompoundButton inflated) {
                        inflated.setId(View.NO_ID);
                        cb = inflated;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (cb == null) {
            CheckBox fallback = new CheckBox(host);
            Drawable seslCheck = resolveSeslDrawable("sesl_btn_check");
            if (seslCheck != null) fallback.setButtonDrawable(seslCheck);
            fallback.setButtonTintList(controlTintList());
            cb = fallback;
        }
        cb.setChecked(checked);
        cb.setClickable(true);
        cb.setFocusable(false);
        if (onChange != null) {
            cb.setOnCheckedChangeListener((btn, isChecked) -> onChange.accept(isChecked));
        }
        return cb;
    }

    /** Creates Samsung's genuine One UI animated SESL RadioButton. */
    RadioButton createRadioButton(boolean checked) {
        RadioButton rb =
                aodThemed != null ? new RadioButton(aodThemed) : new RadioButton(host);
        Drawable seslRadio = resolveSeslDrawable("sesl_btn_radio");
        if (seslRadio != null) rb.setButtonDrawable(seslRadio);
        rb.setButtonTintList(controlTintList());
        rb.setBackground(null);
        rb.setChecked(checked);
        rb.setClickable(false);
        rb.setFocusable(false);
        return rb;
    }

    private ColorStateList controlTintList() {
        int off = dark ? 0xFF8C8C91 : 0xFF75757A;
        return new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
                new int[] {accent, off});
    }

    /** Thin One UI inset horizontal divider between rows inside a cardGroup. */
    View insetDivider() {
        View line = new View(host);
        Drawable seslDiv =
                resolveSeslDrawable(
                        dark ? "sesl_list_divider_dark" : "sesl_list_divider_light",
                        "clock_panel_item_divider");
        if (seslDiv != null) {
            line.setBackground(seslDiv);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.max(1, seslDiv.getIntrinsicHeight()));
            lp.setMarginStart(dp(20));
            lp.setMarginEnd(dp(20));
            line.setLayoutParams(lp);
            return line;
        }
        line.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        lp.setMarginStart(dp(20));
        lp.setMarginEnd(dp(20));
        line.setLayoutParams(lp);
        return line;
    }

    /** Subtle vertical divider used before a master toggle Switch (Samsung Settings style). */
    View verticalDivider() {
        View line = new View(host);
        line.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dp(1) / 2), dp(22));
        lp.setMarginStart(dp(6));
        lp.setMarginEnd(dp(12));
        line.setLayoutParams(lp);
        return line;
    }

    /** Clean One UI sub-tab bar with pure SESL recoil (no Android circle ripple). */
    LinearLayout subTabBar(String[] labels, int selected, IntConsumer onSelect) {
        LinearLayout strip = new LinearLayout(host);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(16), dp(4), dp(16), dp(6));

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean active = i == selected;
            TextView tv = new TextView(host);
            tv.setText(labels[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTypeface(
                    Typeface.create(
                            active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            tv.setTextColor(active ? primaryText : secondaryText);
            tv.setMinHeight(dp(36));
            tv.setPadding(dp(8), dp(6), dp(8), dp(6));
            int fill = active ? (dark ? 0x22FFFFFF : 0x14000000) : Color.TRANSPARENT;
            tv.setBackground(makeRecoilDrawable(fill, dp(18)));
            tv.setClickable(true);
            tv.setFocusable(true);
            applyRecoil(tv, "sesl_recoil_button_selector", 0.96f);
            tv.setOnClickListener(
                    v -> {
                        if (onSelect != null) onSelect.accept(index);
                    });
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(dp(4));
            strip.addView(tv, lp);
        }
        return strip;
    }

    /** Borderless One UI choice chip using pure SESL recoil. */
    TextView chip(String label, boolean selected, Runnable onClick) {
        TextView tv = new TextView(host);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(
                Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        tv.setMinHeight(dp(36));
        tv.setPaddingRelative(dp(14), dp(7), dp(14), dp(7));
        if (selected) {
            tv.setTextColor(accent);
            tv.setBackground(makeRecoilDrawable(withAlpha(accent, dark ? 0x30 : 0x22), dp(18)));
        } else {
            tv.setTextColor(primaryText);
            tv.setBackground(makeRecoilDrawable(pillBg, dp(18)));
        }
        tv.setClickable(true);
        tv.setFocusable(true);
        applyRecoil(tv, "sesl_recoil_button_selector", 0.96f);
        if (onClick != null) tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    /** Native One UI button using SESL button fill color and SeslRecoilDrawable. */
    TextView flatPillButton(String label, int textColor, Runnable onClick) {
        TextView tv = new TextView(host);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setMinHeight(dp(40));
        tv.setPaddingRelative(dp(16), dp(8), dp(16), dp(8));
        int btnFill =
                resolveColor(
                        dark ? "sesl_btn_background_color_dark" : "sesl_btn_background_color_light",
                        pillBg);
        tv.setBackground(makeRecoilDrawable(btnFill, dp(20)));
        tv.setClickable(true);
        tv.setFocusable(true);
        applyRecoil(tv, "sesl_recoil_button_selector", 0.96f);
        if (onClick != null) tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    /** Clean One UI labeled EditText block inside a cardGroup. */
    LinearLayout inputBlock(
            String label,
            String hint,
            String value,
            int maxLength,
            boolean multiline,
            int inputType,
            Consumer<String> onChange) {
        LinearLayout wrap = new LinearLayout(host);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), dp(12));

        if (label != null && !label.isEmpty()) {
            TextView lbl = new TextView(host);
            lbl.setText(label);
            lbl.setTextColor(secondaryText);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            wrap.addView(lbl);
        }

        EditText edit = rawInput(hint, value, maxLength, multiline, inputType, onChange);
        wrap.addView(
                edit,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    EditText rawInput(
            String hint,
            String value,
            int maxLength,
            boolean multiline,
            int inputType,
            Consumer<String> onChange) {
        EditText edit = new EditText(host);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextColor(primaryText);
        edit.setHintTextColor(withAlpha(secondaryText, 0x99));
        edit.setHighlightColor(withAlpha(accent, 0x55));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        edit.setBackgroundTintList(
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_focused}, new int[0]},
                        new int[] {accent, dividerColor}));
        edit.setPadding(0, dp(6), 0, dp(8));
        if (multiline) {
            edit.setSingleLine(false);
            edit.setMinLines(2);
            edit.setMaxLines(4);
            edit.setGravity(Gravity.TOP | Gravity.START);
        } else {
            edit.setSingleLine(true);
            edit.setInputType(inputType);
        }
        if (maxLength > 0) {
            edit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
        }
        if (onChange != null) {
            edit.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(
                                CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(
                                CharSequence s, int start, int before, int count) {
                            onChange.accept(s == null ? "" : s.toString());
                        }

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
        }
        return edit;
    }

    TextView footerNote(String text) {
        TextView tv = new TextView(host);
        tv.setText(text);
        tv.setTextColor(secondaryText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(dp(22), dp(10), dp(22), dp(12));
        return tv;
    }

    /** Pure SESL list row recoil background (no double Android circle ripple). */
    Drawable listRowRecoil() {
        return makeRecoilDrawable(Color.TRANSPARENT, 0);
    }

    /** Builds a genuine SeslRecoilDrawable with custom rounded fill and mask (zero Android ripple). */
    Drawable roundedRipple(int fillColor, int radiusPx) {
        return makeRecoilDrawable(fillColor, radiusPx);
    }

    Drawable makeRecoilDrawable(int fillColor, int radiusPx) {
        GradientDrawable fill = new GradientDrawable();
        if (radiusPx > 0) fill.setCornerRadius(radiusPx);
        fill.setColor(fillColor);

        if (seslRecoilCtor != null) {
            try {
                GradientDrawable mask = new GradientDrawable();
                if (radiusPx > 0) mask.setCornerRadius(radiusPx);
                mask.setColor(Color.WHITE);
                mask.setTint(0);
                mask.setTintBlendMode(BlendMode.SRC_IN);
                return (Drawable)
                        seslRecoilCtor.newInstance(recoilColor, new Drawable[] {fill}, mask);
            } catch (Throwable ignored) {
            }
        }
        // Clean flat press highlight fallback (never Android's noisy circle ripple)
        GradientDrawable pressed = new GradientDrawable();
        if (radiusPx > 0) pressed.setCornerRadius(radiusPx);
        pressed.setColor(blendColors(fillColor, recoilColor));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], fill);
        return states;
    }

    void applyNativeRecoil(View view, boolean card) {
        applyRecoil(
                view,
                card ? "sesl_recoil_card_selector" : "sesl_recoil_button_selector",
                card ? 0.98f : 0.96f);
    }

    void applyRecoil(View view, String animatorName, float fallbackScale) {
        for (Context source : new Context[] {aodThemed, dressroom}) {
            if (source == null) continue;
            int id =
                    source.getResources()
                            .getIdentifier(animatorName, "animator", source.getPackageName());
            if (id != 0) {
                try {
                    StateListAnimator loaded = AnimatorInflater.loadStateListAnimator(source, id);
                    if (loaded != null) {
                        view.setStateListAnimator(loaded);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        StateListAnimator anim = new StateListAnimator();
        anim.addState(
                new int[] {android.R.attr.state_pressed},
                scaleAnim(view, fallbackScale, 100L, new LinearInterpolator()));
        anim.addState(
                new int[0],
                scaleAnim(view, 1f, 350L, new PathInterpolator(0.22f, 0.25f, 0f, 1f)));
        view.setStateListAnimator(anim);
    }

    private static AnimatorSet scaleAnim(
            View view, float target, long duration, TimeInterpolator interp) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", target);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", target);
        sx.setDuration(duration);
        sy.setDuration(duration);
        sx.setInterpolator(interp);
        sy.setInterpolator(interp);
        set.playTogether(sx, sy);
        return set;
    }

    int resolveDimenPx(String name, int fallback) {
        for (Context src : new Context[] {aodThemed, dressroom}) {
            if (src == null) continue;
            Resources res = src.getResources();
            int id = res.getIdentifier(name, "dimen", src.getPackageName());
            if (id != 0) {
                try {
                    int px = res.getDimensionPixelSize(id);
                    if (px > 0) return px;
                } catch (Throwable ignored) {
                }
            }
        }
        return fallback;
    }

    int resolveColor(String name, int fallback) {
        for (Context src : new Context[] {aodThemed, dressroom}) {
            if (src == null) continue;
            Resources res = src.getResources();
            int id = res.getIdentifier(name, "color", src.getPackageName());
            if (id != 0) {
                try {
                    return res.getColor(id, src.getTheme());
                } catch (Throwable ignored) {
                }
            }
        }
        return fallback;
    }

    Drawable resolveSeslDrawable(String... names) {
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            for (Context src : new Context[] {aodThemed, dressroom}) {
                if (src == null) continue;
                Resources res = src.getResources();
                int id = res.getIdentifier(name, "drawable", src.getPackageName());
                if (id != 0) {
                    try {
                        Drawable d = res.getDrawable(id, src.getTheme());
                        if (d != null) return d.mutate();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    static int withAlpha(int color, int alpha) {
        return ((Math.max(0, Math.min(255, alpha))) << 24) | (color & 0xFFFFFF);
    }

    private static int blendColors(int base, int overlay) {
        float oa = Color.alpha(overlay) / 255f;
        float ba = Color.alpha(base) / 255f;
        float outA = oa + ba * (1f - oa);
        if (outA <= 0.001f) return 0;
        int r = Math.round((Color.red(overlay) * oa + Color.red(base) * ba * (1f - oa)) / outA);
        int g = Math.round((Color.green(overlay) * oa + Color.green(base) * ba * (1f - oa)) / outA);
        int b = Math.round((Color.blue(overlay) * oa + Color.blue(base) * ba * (1f - oa)) / outA);
        return Color.argb(Math.round(outA * 255f), r, g, b);
    }

    private static boolean checkWallpaperThemeActive(Context host) {
        try {
            String pkg =
                    Settings.System.getString(
                            host.getContentResolver(), "current_sec_active_themepackage");
            if (!TextUtils.isEmpty(pkg)) return true;
            return Settings.System.getInt(host.getContentResolver(), "wallpapertheme_state", 0) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Context createCodeContext(Context host, String pkg) {
        try {
            return host.createPackageContext(
                    pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable error) {
            android.util.Log.w(DressroomModule.TAG, "Could not load SESL code context for " + pkg, error);
            return null;
        }
    }

    private static Context wrapSeslTheme(Context codeCtx, Context host, String pkg) {
        Context base = codeCtx != null ? codeCtx : createResourceContext(host, pkg);
        if (base == null) return null;
        Resources res = base.getResources();
        String[] candidates = {
            "SettingsThemeNoActionBar",
            "SettingsTheme",
            "Theme.AppCompat.DayNight.NoActionBar",
            "Theme.AppCompat.DayNight",
            "AppTheme"
        };
        for (String name : candidates) {
            int themeId = res.getIdentifier(name, "style", pkg);
            if (themeId != 0) {
                return new ContextThemeWrapper(base, themeId);
            }
        }
        return base;
    }

    private static Context createResourceContext(Context host, String pkg) {
        try {
            Context pkgCtx = host.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            Configuration cfg = new Configuration(host.getResources().getConfiguration());
            return pkgCtx.createConfigurationContext(cfg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Constructor<?> resolveSwitchCompatCtor(Context pkgCtx) {
        if (pkgCtx == null) return null;
        try {
            Class<?> cls =
                    Class.forName(
                            "androidx.appcompat.widget.SwitchCompat",
                            false,
                            pkgCtx.getClassLoader());
            Constructor<?> ctor = cls.getConstructor(Context.class, AttributeSet.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveSmoothCornerMethod() {
        try {
            Method m = GradientDrawable.class.getMethod("setSmoothCorner", boolean.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Constructor<?> resolveRecoilCtor(Context themed) {
        if (themed == null) return null;
        try {
            Class<?> cls =
                    Class.forName(
                            "androidx.appcompat.graphics.drawable.SeslRecoilDrawable",
                            false,
                            themed.getClassLoader());
            Constructor<?> ctor = cls.getConstructor(int.class, Drawable[].class, Drawable.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
