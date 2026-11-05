package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.reflect.Proxy;

final class WidgetPanel {
    private final Context context;
    private final ClassLoader loader;
    private final EditorWidgets editor;
    private final LinearLayout content;
    private PanelTabs pages;
    private int widgetPage;
    private LinearLayout group;
    private boolean binding;
    private TextView status;
    private boolean extrasSelected;

    interface Choice {
        void change(Bundle b, int value) throws Exception;
    }

    WidgetPanel(Context context, ClassLoader loader, EditorWidgets editor) {
        this.context = context;
        this.loader = loader;
        this.editor = editor;
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
    }

    void show(Object ui) throws Exception {
        View tabs = nativeView(EditorBinding.Row.TABS);
        NativeTabs nativeTabs = NativeTabs.of(tabs);
        nativeTabs.add(tabs, "Widgets", true);
        nativeTabs.add(tabs, "Extras", false);
        Class<?> listener = nativeTabs.listener;
        Object events =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                if (method.getName().equals("equals")) return proxy == args[0];
                                if (method.getName().equals("hashCode"))
                                    return System.identityHashCode(proxy);
                                return "Advanced panel tabs";
                            }
                            if (method.getName().equals("onTabSelected")
                                    || method.getName().equals("onTabReselected")) {
                                extrasSelected = nativeTabs.position(args[0]) == 1;
                                try {
                                    render();
                                } catch (Exception e) {
                                    error(e);
                                }
                            }
                            return null;
                        });
        nativeTabs.listen(tabs, events);
        render();
        Object nativePanel = Reflect.field(Reflect.field(ui, "a"), "c");
        Reflect.set(nativePanel, editor.legacy() ? "j" : "h", true);
        Reflect.call(nativePanel, "setAnchorStateSupported", false);
        Object type =
                WidgetEngine.enumValue(
                        Class.forName(editor.legacy() ? "T2.K" : "k6.o", false, loader), "WIDGET");
        Reflect.call(
                ui,
                editor.legacy() ? "j" : "d",
                content,
                tabs,
                null,
                dp(376),
                dp(376),
                null,
                type,
                null,
                false,
                null);
    }

    private Bundle draft() {
        return extrasSelected
                ? editor.extras().settings(context)
                : new Bundle(editor.settings(context));
    }

    private void render() throws Exception {
        editor.cancelScalePreview();
        binding = true;
        content.removeAllViews();
        group = content;
        Bundle b = draft();
        if (extrasSelected) {
            renderExtras(b);
            binding = false;
            content.requestLayout();
            return;
        }
        View pageStrip = nativeView(EditorBinding.Row.TABS);
        content.addView(pageStrip);
        pages =
                new PanelTabs(
                        pageStrip,
                        loader,
                        new String[] {editor.legacy() ? "Size" : "Layout", "Position", "Effects"},
                        widgetPage,
                        page -> {
                            widgetPage = page;
                            content.requestLayout();
                        });
        section("Layout & size");
        if (!editor.legacy()) {
            toggle(
                    "Custom grid",
                    b.getBoolean("layout"),
                    next -> {
                        next.putBoolean("layout", !b.getBoolean("layout"));
                        if (!next.getBoolean("layout")) {
                            next.putInt("columns", 4);
                            next.putInt("rows", 1);
                            next.putBoolean("vertical", false);
                        }
                    },
                    true);
            if (b.getBoolean("layout")) {
                TextView rotate =
                        label(
                                "Arrangement: "
                                        + (editor.vertical() ? "Vertical" : "Horizontal")
                                        + "  •  Rotate");
                rotate.setMinHeight(dp(48));
                ripple(rotate);
                rotate.setOnClickListener(
                        v -> {
                            try {
                                editor.rotate();
                                content.postDelayed(
                                        () -> {
                                            try {
                                                render();
                                            } catch (Exception e) {
                                                error(e);
                                            }
                                        },
                                        500);
                            } catch (Exception e) {
                                error(e);
                            }
                        });
                note(
                        "Rotate uses Samsung’s layout control. Wide grids are horizontal; tall"
                                + " grids are vertical.");
                slider(
                        "Columns",
                        1,
                        8,
                        b.getInt("columns"),
                        "",
                        (next, value) -> next.putInt("columns", value),
                        true);
                slider(
                        "Rows",
                        1,
                        Math.min(8, 32 / b.getInt("columns")),
                        b.getInt("rows"),
                        "",
                        (next, value) -> next.putInt("rows", value),
                        true);
                note(
                        "Grid capacity: "
                                + b.getInt("columns")
                                + " × "
                                + b.getInt("rows")
                                + ". Larger widget variants appear in the widget picker when"
                                + " supported.");
            }
        } else
            note(
                    "Use Samsung’s widget layout control to switch between horizontal and vertical."
                            + " Custom grid capacity is not available in this build.");
        slider(
                "Widget scale",
                50,
                200,
                Math.round(b.getFloat("scale", 1) * 100),
                "%",
                (next, value) -> next.putFloat("scale", value / 100f),
                false);
        note(
                editor.legacy()
                        ? "Scale changes cell size while keeping Samsung’s arrangement."
                        : "Scale changes cell size. Rows and columns control how much the grid can"
                                + " hold.");
        section("Placement");
        choice(
                "Attach to clock",
                new String[] {
                    "Samsung default", "Below", "Above", "Left", "Right", "Free position"
                },
                b.getInt("anchor"),
                (next, index) -> next.putInt("anchor", index),
                false);
        int anchor = b.getInt("anchor");
        note(
                "Drag the widget group in the preview to move it. Snapped groups slide along the"
                        + " clock edge; Free position moves in both directions.");
        if (anchor >= 1 && anchor <= 4) {
            slider(
                    "Gap from clock",
                    0,
                    64,
                    Math.round(b.getFloat("gap", 8)),
                    " dp",
                    (next, value) -> next.putFloat("gap", value),
                    false);
            slider(
                    "Along clock edge",
                    -600,
                    600,
                    Math.round(b.getFloat("dockOffset")),
                    " dp",
                    (next, value) -> next.putFloat("dockOffset", value),
                    false);
        }
        if (anchor == 5) {
            slider(
                    "Horizontal position",
                    0,
                    100,
                    Math.round(b.getFloat("freeX", .5f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeX", value / 100f),
                    false);
            slider(
                    "Vertical position",
                    0,
                    100,
                    Math.round(b.getFloat("freeY", .4f) * 100),
                    "%",
                    (next, value) -> next.putFloat("freeY", value / 100f),
                    false);
        }
        if (editor.depthAvailable()) {
            section("Appearance");
            boolean imageAvailable = editor.depthImageAvailable();
            choice(
                    "Subject depth",
                    new String[] {"Follow clock", "On", "Off"},
                    b.getInt("depth"),
                    (next, index) -> next.putInt("depth", index),
                    false,
                    imageAvailable);
            choice(
                    "Depth applies to",
                    new String[] {
                        "Separate per orientation",
                        "Portrait only",
                        "Landscape only",
                        "Both orientations"
                    },
                    b.getInt("depthLayouts"),
                    (next, index) -> next.putInt("depthLayouts", index),
                    false,
                    imageAvailable);
            if (!imageAvailable)
                note(
                        "Depth requires a still-image wallpaper. Gyro, video and live wallpapers"
                                + " are not supported.");
            note(
                    "Portrait only, Landscape only and Both share the depth choice across layouts."
                            + " Off disables it in both. Size and placement stay separate.");
            note(
                    "On places widgets behind the detected subject independently of clock depth."
                            + " Detect the subject in Clock → Advanced first.");
            if (!editor.legacy()) {
                toggle(
                        "Live background blur",
                        !b.getBoolean("blurOff"),
                        next -> next.putBoolean("blurOff", !b.getBoolean("blurOff")),
                        false);
                note(
                        "When enabled, Samsung controls blur for the current screen and"
                                + " accessibility settings. Depth clips blur around the subject.");
            }
        }
        section("Reset");
        TextView reset = label("Reset widget adjustments");
        reset.setMinHeight(dp(48));
        reset.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ripple(reset);
        reset.setOnClickListener(
                v -> {
                    try {
                        editor.resetAdjustments();
                        render();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        group = content;
        status = label("Changes preview here. Use the editor’s Done / Apply to save.");
        binding = false;
        content.requestLayout();
    }

    interface Edit {
        void change(Bundle b) throws Exception;
    }

    private void renderExtras(Bundle b) throws Exception {
        label("Now Bar");
        toggle(
                "Keep charging card",
                b.getBoolean("keepCharging"),
                next -> next.putBoolean("keepCharging", !b.getBoolean("keepCharging")),
                false);
        note("Keep the finished charging card visible while plugged in. It closes when unplugged.");
        if (!editor.legacy()) {
            toggle(
                    "Full-screen album artwork",
                    b.getBoolean("fullscreenArt"),
                    next -> next.putBoolean("fullscreenArt", !b.getBoolean("fullscreenArt")),
                    false);
            note(
                    "Expand artwork to fill the screen using the player’s native movement, without"
                            + " its artwork frame or top blur.");
        }
        label("Your Now Bar");
        toggle(
                "Show a custom message",
                b.getBoolean("textCard"),
                next -> next.putBoolean("textCard", !b.getBoolean("textCard")),
                false);
        if (b.getBoolean("textCard")) {
            text("Title", "title", b.getString("title"), 80);
            text("Message", "message", b.getString("message"), 180);
            note("This text is visible on the lockscreen.");
        }
        if (!editor.legacy()) {
            label("Always On Display");
            toggle(
                    "Keep clock stretched",
                    b.getBoolean("keepStretch"),
                    next -> next.putBoolean("keepStretch", !b.getBoolean("keepStretch")),
                    false);
            note(
                    "Keep a dynamic clock’s height when entering AOD. Native color and weight"
                            + " transitions stay active.");
        }
        TextView reset = label("Reset extra settings");
        reset.setMinHeight(dp(48));
        ripple(reset);
        reset.setOnClickListener(
                v -> {
                    editor.extras().update(context, ExtrasPreferences.clean(null));
                    try {
                        render();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        status = label("These settings apply to all wallpapers. Use Done / Apply to save.");
    }

    private void text(String caption, String key, String value, int limit) {
        label(caption);
        android.widget.EditText input = new android.widget.EditText(context);
        input.setText(value);
        input.setHint(caption);
        input.setSingleLine(true);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(limit)});
        input.setContentDescription(caption);
        add(input);
        input.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        change(b -> b.putString(key, s.toString()), false, false);
                    }

                    public void afterTextChanged(android.text.Editable e) {}
                });
    }

    private void change(Edit edit, boolean structural, boolean rebuild) {
        if (binding) return;
        Bundle next = draft();
        try {
            edit.change(next);
            if (extrasSelected) {
                editor.extras().update(context, next);
            } else editor.update(next, structural);
            if (rebuild)
                content.post(
                        () -> {
                            try {
                                render();
                            } catch (Exception e) {
                                error(e);
                            }
                        });
            else if (status != null)
                status.setText(
                        extrasSelected
                                ? "Use Done / Apply to save extra settings."
                                : "Preview updated. Apply to save.");
        } catch (Exception e) {
            error(e);
            content.post(
                    () -> {
                        try {
                            render();
                            status.setText(e.getMessage());
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private void toggle(String name, boolean checked, Edit edit, boolean structural)
            throws Exception {
        CompoundButton toggle = (CompoundButton) nativeView(EditorBinding.Row.TOGGLE);
        toggle.setText(name);
        toggle.setChecked(checked);
        add(toggle);
        toggle.setOnCheckedChangeListener((v, value) -> change(edit, structural, true));
    }

    private void choice(
            String name, String[] entries, int selected, Choice edit, boolean structural) {
        choice(name, entries, selected, edit, structural, true);
    }

    private void choice(
            String name,
            String[] entries,
            int selected,
            Choice edit,
            boolean structural,
            boolean enabled) {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        label(name);
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected, false);
        spinner.setContentDescription(name);
        spinner.setEnabled(enabled);
        add(spinner);
        group = parent;
        spinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int index, long id) {
                        if (index != selected)
                            change(next -> edit.change(next, index), structural, true);
                    }
                });
    }

    private void slider(
            String name,
            int min,
            int max,
            int current,
            String suffix,
            Choice edit,
            boolean structural)
            throws Exception {
        LinearLayout parent = group;
        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = label(name + "   " + current + suffix);
        caption.setMinHeight(0);
        caption.setMinimumHeight(0);
        caption.setPadding(caption.getPaddingLeft(), dp(12), caption.getPaddingRight(), 0);
        int captionSize =
                context.getResources()
                        .getIdentifier(
                                "clock_font_weight_seekbar_title_text_size",
                                "dimen",
                                "com.samsung.android.app.dressroom");
        caption.setTextSize(
                TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(captionSize));
        View slider = nativeView(EditorBinding.Row.SLIDER);
        Reflect.call(slider, "setMax", max - min);
        Reflect.call(slider, "setProgress", Math.max(0, Math.min(max - min, current - min)));
        slider.setContentDescription(name);
        add(slider);
        group = parent;
        Class<?> listener =
                Class.forName(
                        "androidx.appcompat.widget.SeslSeekBar$OnSeekBarChangeListener",
                        false,
                        loader);
        int[] pending = {current};
        Object proxy =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {listener},
                        (p, m, args) -> {
                            if (m.getDeclaringClass() == Object.class) {
                                if (m.getName().equals("equals")) return p == args[0];
                                if (m.getName().equals("hashCode"))
                                    return System.identityHashCode(p);
                                return "Widget slider";
                            }
                            if (m.getName().equals("onProgressChanged")
                                    && Boolean.TRUE.equals(args[2])) {
                                pending[0] = min + ((Number) args[1]).intValue();
                                caption.setText(name + "   " + pending[0] + suffix);
                                if (name.equals("Widget scale")) {
                                    try {
                                        editor.previewScale(pending[0] / 100f);
                                    } catch (Exception e) {
                                        error(e);
                                    }
                                }
                                if (!structural && !name.equals("Widget scale"))
                                    change(next -> edit.change(next, pending[0]), false, false);
                            }
                            if (m.getName().equals("onStopTrackingTouch")) {
                                if (name.equals("Widget scale")) editor.cancelScalePreview();
                                change(
                                        next -> edit.change(next, pending[0]),
                                        structural,
                                        structural);
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", proxy);
        if (name.equals("Widget scale"))
            slider.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {}

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                            editor.cancelScalePreview();
                        }
                    });
    }

    private void section(String name) {
        int page =
                switch (name) {
                    case "Placement" -> 1;
                    case "Appearance" -> 2;
                    default -> 0;
                };
        group = pages.group(content, page);
        int divider =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_item_divider",
                                "drawable",
                                "com.samsung.android.app.dressroom");
        if (divider != 0) {
            View line = new View(context);
            android.graphics.drawable.Drawable background = context.getDrawable(divider);
            line.setBackground(background);
            group.addView(
                    line,
                    new LinearLayout.LayoutParams(
                            -1, Math.max(1, background.getIntrinsicHeight())));
        }
        if (!name.equals("Reset")) {
            TextView title = label(name);
            title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
    }

    private TextView label(String value) {
        TextView label = (TextView) nativeView(EditorBinding.Row.LABEL);
        label.setText(value);
        add(label);
        return label;
    }

    private void note(String value) {
        TextView v = label(value);
        v.setMinHeight(0);
        v.setMinimumHeight(0);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setAlpha(.7f);
    }

    private void ripple(View v) {
        TypedValue value = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackground, value, true))
            v.setBackgroundResource(value.resourceId);
        v.setClickable(true);
        v.setFocusable(true);
    }

    private void add(View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMarginStart(dp(16));
        p.setMarginEnd(dp(16));
        group.addView(v, p);
    }

    private View nativeView(EditorBinding.Row row) {
        String tag = editor.rowTag(row);
        int id =
                context.getResources()
                        .getIdentifier(
                                "clock_panel_layout",
                                "layout",
                                "com.samsung.android.app.dressroom");
        try (XmlResourceParser xml = context.getResources().getLayout(id)) {
            while (xml.getEventType() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG
                        && tag.equals(
                                xml.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android", "tag"))) {
                    View v =
                            LayoutInflater.from(context)
                                    .createView(
                                            context,
                                            xml.getName(),
                                            null,
                                            android.util.Xml.asAttributeSet(xml));
                    v.setId(View.NO_ID);
                    v.setTag(null);
                    return v;
                }
                xml.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Native widget control unavailable: " + tag, e);
        }
        throw new IllegalStateException("Missing native control " + tag);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void error(Exception e) {
        android.util.Log.e(DressroomModule.TAG, "Widget control failed", e);
        if (status != null) status.setText(e.getMessage());
        android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                .show();
    }
}
