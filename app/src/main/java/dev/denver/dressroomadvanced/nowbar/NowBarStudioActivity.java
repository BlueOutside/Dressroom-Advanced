package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Samsung One UI 9 styled Manager & Card Editor for DIY Now Bar cards on Lockscreen, AOD,
 * and Cover Screen.
 */
public final class NowBarStudioActivity extends Activity {
    static final String EXTRA_EDIT_CARD_ID = "edit_card_id";
    private static final int REQUEST_PICK_PHOTO = 4101;

    private static final String[] QUICK_EMOJIS = {
        "\u2728", "\uD83D\uDD25", "\uD83D\uDCA7", "\uD83C\uDFAF",
        "\uD83C\uDFB5", "\u2615", "\uD83C\uDFCB\uFE0F", "\uD83D\uDCDA",
        "\uD83D\uDE80", "\uD83C\uDF19", "\u26A1", "\uD83D\uDC9C"
    };

    private String str(int resId) {
        return ModuleStrings.get(this, resId);
    }

    private String str(int resId, Object... args) {
        return ModuleStrings.get(this, resId, args);
    }

    private String templateSummary(int type) {
        return switch (type) {
            case DiyNowBarCard.TYPE_TIMER -> str(R.string.nowbar_template_summary_timer);
            case DiyNowBarCard.TYPE_GOAL -> str(R.string.nowbar_template_summary_goal);
            case DiyNowBarCard.TYPE_STATS -> str(R.string.nowbar_template_summary_stats);
            case DiyNowBarCard.TYPE_LAUNCHER -> str(R.string.nowbar_template_summary_launcher);
            case DiyNowBarCard.TYPE_CHECKLIST -> str(R.string.nowbar_template_summary_checklist);
            case DiyNowBarCard.TYPE_CONTROLS -> str(R.string.nowbar_template_summary_controls);
            default -> str(R.string.nowbar_template_summary_custom);
        };
    }

    private String effectSummary(int effect) {
        return switch (effect) {
            case DiyNowBarCard.EFFECT_RADIAL_GLOW -> str(R.string.nowbar_effect_summary_radial_glow);
            case DiyNowBarCard.EFFECT_AI_PRESENCE -> str(R.string.nowbar_effect_summary_ai_presence);
            case DiyNowBarCard.EFFECT_OUTER_GLOW -> str(R.string.nowbar_effect_summary_outer_glow);
            case DiyNowBarCard.EFFECT_GRADIENT_FILL -> str(R.string.nowbar_effect_summary_gradient_fill);
            case DiyNowBarCard.EFFECT_AI_MESH_WARP -> str(R.string.nowbar_effect_summary_ai_border);
            case DiyNowBarCard.EFFECT_GUIDING_LIGHT -> str(R.string.nowbar_effect_summary_guiding_light);
            default -> str(R.string.nowbar_effect_summary_clean_glass);
        };
    }

    private String[] quickActionLabels() {
        return new String[] {
            str(R.string.common_none),
            str(R.string.nowbar_action_auto_smart),
            str(R.string.nowbar_action_open_app),
            str(R.string.nowbar_action_open_url),
            str(R.string.nowbar_action_toggle_flashlight),
            str(R.string.nowbar_action_open_editor)
        };
    }

    private String[] tapActionLabels() {
        return new String[] {
            str(R.string.common_none),
            str(R.string.nowbar_action_open_app),
            str(R.string.nowbar_action_open_url),
            str(R.string.nowbar_action_toggle_flashlight),
            str(R.string.nowbar_action_open_editor)
        };
    }

    private static final int[] TAP_ACTION_VALUES = {
        DiyNowBarCard.ACTION_NONE,
        DiyNowBarCard.ACTION_OPEN_APP,
        DiyNowBarCard.ACTION_OPEN_URL,
        DiyNowBarCard.ACTION_FLASHLIGHT,
        DiyNowBarCard.ACTION_OPEN_EDITOR
    };

    private record AppEntry(String label, String packageName, Drawable icon) {}

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DiyStudioUi ui;
    private FrameLayout rootHost;
    private List<DiyNowBarCard> cards = new ArrayList<>();
    private DiyNowBarStackOrder.Config stackOrder = new DiyNowBarStackOrder.Config();
    private List<DiyNowBarStackOrder.LiveEntry> liveStack = new ArrayList<>();
    private DiyNowBarLabs labs = new DiyNowBarLabs();
    private int managerTab;
    private DiyNowBarCard editingCard;
    private boolean editingIsNew;
    private int editorTab;
    private DiyNowBarPreviewView previewView;
    private TextView managerCountLabel;
    private LinearLayout tabBarHost;
    private LinearLayout tabBodyHost;
    private List<AppEntry> cachedApps;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new DiyStudioUi(this);
        Window window = getWindow();
        if (window != null) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        rootHost = new FrameLayout(this);
        rootHost.setBackgroundColor(ui.pageBg);
        rootHost.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    android.graphics.Insets bars =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
        setContentView(rootHost);

        if (window != null && window.getInsetsController() != null) {
            int flags =
                    ui.dark
                            ? 0
                            : (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | android.view.WindowInsetsController
                                            .APPEARANCE_LIGHT_NAVIGATION_BARS);
            window.getInsetsController()
                    .setSystemBarsAppearance(
                            flags,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | android.view.WindowInsetsController
                                            .APPEARANCE_LIGHT_NAVIGATION_BARS);
        }

        backCallback = this::handleBackAction;
        getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);

        reloadState();
        if (!openDeepLinkIfRequested(getIntent())) {
            showManager();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        reloadState();
        if (!openDeepLinkIfRequested(intent) && editingCard == null) {
            showManager();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateProcessRelaunch.check(this, false, () -> {});
        if (editingCard == null) {
            reloadState();
            showManager();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && editingCard == null && managerTab == 1) {
            List<DiyNowBarStackOrder.LiveEntry> fresh = DiyNowBarStore.readLiveStack(this);
            if (!DiyNowBarStore.liveEntriesToJson(fresh)
                    .equals(DiyNowBarStore.liveEntriesToJson(liveStack))) {
                liveStack = fresh;
                showManager();
            }
        }
    }

    private void reloadState() {
        cards = DiyNowBarStore.read(this);
        stackOrder = DiyNowBarStore.readStackOrder(this);
        liveStack = DiyNowBarStore.readLiveStack(this);
        labs = DiyNowBarStore.readLabs(this);
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (RuntimeException ignored) {
            }
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void handleBackAction() {
        if (editingCard != null) {
            editingCard = null;
            editingIsNew = false;
            previewView = null;
            tabBarHost = null;
            tabBodyHost = null;
            showManager();
        } else {
            finish();
        }
    }

    private boolean openDeepLinkIfRequested(Intent intent) {
        if (intent == null) return false;
        String targetId = intent.getStringExtra(EXTRA_EDIT_CARD_ID);
        if (targetId == null || targetId.isEmpty()) return false;
        for (DiyNowBarCard c : cards) {
            if (targetId.equals(c.id)) {
                openEditor(c.copy(), false);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Manager Screen (One UI Settings style)
    // -------------------------------------------------------------------------

    private void showManager() {
        editingCard = null;
        previewView = null;
        tabBarHost = null;
        tabBodyHost = null;
        rootHost.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        ImageView addIcon =
                managerTab == 0
                        ? ui.toolbarIconButton(
                                "plus",
                                ui.primaryText,
                                str(R.string.nowbar_add_card),
                                () -> {
                                    if (cards.size() >= DiyNowBarCard.MAX_CARDS) {
                                        Toast.makeText(
                                                        this,
                                                        str(
                                                                R.string.nowbar_max_cards_reached,
                                                                DiyNowBarCard.MAX_CARDS),
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }
                                    showTemplatePickerDialog();
                                })
                        : null;
        page.addView(ui.toolbar(str(R.string.diy_nowbar_setting_title), this::finish, addIcon));

        page.addView(
                ui.subTabBar(
                        new String[] {
                            str(R.string.nowbar_tab_cards),
                            str(R.string.nowbar_tab_stack_order),
                            str(R.string.nowbar_tab_labs)
                        },
                        managerTab,
                        idx -> {
                            if (managerTab == idx) return;
                            managerTab = idx;
                            if (managerTab == 1) {
                                stackOrder = DiyNowBarStore.readStackOrder(this);
                                liveStack = DiyNowBarStore.readLiveStack(this);
                            } else if (managerTab == 2) {
                                labs = DiyNowBarStore.readLabs(this);
                            }
                            showManager();
                        }));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, ui.dp(24));

        if (managerTab == 1) {
            buildStackOrderManagerTab(body);
        } else if (managerTab == 2) {
            buildLabsManagerTab(body);
        } else {
            buildCardsManagerTab(body);
        }

        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootHost.addView(page);
    }

    private void buildCardsManagerTab(LinearLayout body) {
        if (cards.isEmpty()) {
            body.addView(
                    ui.sectionHeader(
                            str(R.string.nowbar_choose_template),
                            "0 / " + DiyNowBarCard.MAX_CARDS));
            LinearLayout group = ui.cardGroup();
            for (int i = 0; i < DiyNowBarCard.TYPE_NAMES.length; i++) {
                final int templateType = i;
                if (i > 0) group.addView(ui.insetDivider());
                group.addView(
                        ui.prefRow(
                                DiyNowBarCard.typeName(this, i),
                                templateSummary(i),
                                false,
                                () -> createFromTemplate(templateType)));
            }
            body.addView(group);
            body.addView(ui.footerNote(str(R.string.nowbar_empty_footer)));
            return;
        }

        int activeCount = 0;
        DiyNowBarPublisher.StatsSnapshot stats = null;
        for (DiyNowBarCard c : cards) {
            if (c.enabled) activeCount++;
            if (c.type == DiyNowBarCard.TYPE_STATS && stats == null) {
                stats = DiyNowBarPublisher.collectStats(this);
            }
        }
        LinearLayout cardsHeader =
                ui.sectionHeader(
                        str(R.string.nowbar_tab_cards),
                        str(
                                R.string.nowbar_cards_count_header,
                                activeCount,
                                cards.size(),
                                DiyNowBarCard.MAX_CARDS));
        if (cardsHeader.getChildCount() >= 2 && cardsHeader.getChildAt(1) instanceof TextView tv) {
            managerCountLabel = tv;
        }
        body.addView(cardsHeader);

        LinearLayout listGroup = ui.cardGroup();
        long now = System.currentTimeMillis();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) listGroup.addView(ui.insetDivider());
            listGroup.addView(buildManagerRow(cards.get(i), i, stats, now));
        }
        body.addView(listGroup);

        body.addView(ui.footerNote(str(R.string.nowbar_cards_footer)));
    }

    private void buildStackOrderManagerTab(LinearLayout body) {
        body.addView(
                ui.sectionHeader(
                        str(R.string.nowbar_stack_priority),
                        str(
                                stackOrder.enabled
                                        ? R.string.nowbar_stack_state_active
                                        : R.string.nowbar_stack_state_default)));
        LinearLayout settingsGroup = ui.cardGroup();
        settingsGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_custom_stack_order),
                        str(R.string.nowbar_custom_stack_order_summary),
                        stackOrder.enabled,
                        checked -> {
                            stackOrder.enabled = checked;
                            if (checked && stackOrder.order.isEmpty()) {
                                List<DiyNowBarStackOrder.LiveEntry> display =
                                        DiyNowBarStackOrder.buildDisplayList(
                                                stackOrder, liveStack, cards);
                                for (DiyNowBarStackOrder.LiveEntry e : display) {
                                    stackOrder.order.add(e.slotId);
                                }
                            }
                            persistStackOrder();
                            body.postDelayed(this::showManager, 200L);
                        }));
        if (stackOrder.enabled) {
            settingsGroup.addView(ui.insetDivider());
            settingsGroup.addView(
                    ui.switchRow(
                            str(R.string.nowbar_reset_top_screen_off),
                            str(R.string.nowbar_reset_top_screen_off_summary),
                            stackOrder.resetOnScreenOff,
                            checked -> {
                                stackOrder.resetOnScreenOff = checked;
                                persistStackOrder();
                            }));
        }
        body.addView(settingsGroup);

        List<DiyNowBarStackOrder.LiveEntry> display =
                DiyNowBarStackOrder.buildDisplayList(stackOrder, liveStack, cards);
        int liveCount = 0;
        for (DiyNowBarStackOrder.LiveEntry e : display) if (e.live) liveCount++;
        String trailing =
                liveCount > 0
                        ? str(R.string.nowbar_stack_live_trailing, liveCount)
                        : str(R.string.nowbar_stack_default_trailing);
        body.addView(ui.sectionHeader(str(R.string.nowbar_drag_pills_header), trailing));

        Map<String, DiyNowBarCard> diyById = new LinkedHashMap<>();
        for (DiyNowBarCard c : cards) diyById.put(c.id, c);

        DiyStackDragListView dragList = new DiyStackDragListView(ui);
        dragList.bind(
                display,
                diyById,
                newOrder -> {
                    boolean wasDisabled = !stackOrder.enabled;
                    stackOrder.enabled = true;
                    stackOrder.order.clear();
                    stackOrder.order.addAll(newOrder);
                    persistStackOrder();
                    if (wasDisabled) {
                        showManager();
                    }
                });
        body.addView(dragList);

        body.addView(ui.footerNote(str(R.string.nowbar_stack_footer)));

        if (stackOrder.enabled || !stackOrder.order.isEmpty()) {
            LinearLayout resetWrap = new LinearLayout(this);
            resetWrap.setOrientation(LinearLayout.HORIZONTAL);
            resetWrap.setGravity(Gravity.CENTER);
            resetWrap.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
            TextView resetBtn =
                    ui.flatPillButton(
                            str(R.string.nowbar_reset_stack_button),
                            ui.primaryText,
                            () -> {
                                stackOrder.enabled = false;
                                stackOrder.order.clear();
                                persistStackOrder();
                                Toast.makeText(
                                                this,
                                                str(R.string.nowbar_reset_stack_toast),
                                                Toast.LENGTH_SHORT)
                                        .show();
                                showManager();
                            });
            resetWrap.addView(
                    resetBtn,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            body.addView(resetWrap);
        }
    }

    private void persistStackOrder() {
        boolean ok = DiyNowBarStore.commitStackOrder(this, stackOrder);
        if (!ok) {
            Toast.makeText(this, str(R.string.nowbar_save_stack_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void persistLabs() {
        boolean ok = DiyNowBarStore.commitLabs(this, labs);
        if (!ok) {
            Toast.makeText(this, str(R.string.nowbar_save_labs_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void buildLabsManagerTab(LinearLayout body) {
        body.addView(
                ui.sectionHeader(
                        str(R.string.nowbar_labs_section_native),
                        str(R.string.nowbar_labs_one_ui_9)));
        LinearLayout nativeGroup = ui.cardGroup();
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_native_battery),
                        str(R.string.nowbar_labs_native_battery_summary),
                        labs.nativeBatteryCard,
                        checked -> {
                            labs.nativeBatteryCard = checked;
                            persistLabs();
                            body.postDelayed(this::showManager, 200L);
                        }));
        if (labs.nativeBatteryCard) {
            nativeGroup.addView(ui.insetDivider());
            nativeGroup.addView(
                    ui.switchRow(
                            str(R.string.nowbar_labs_battery_unplugged),
                            str(R.string.nowbar_labs_battery_unplugged_summary),
                            labs.nativeBatteryAlwaysShow,
                            checked -> {
                                labs.nativeBatteryAlwaysShow = checked;
                                persistLabs();
                            }));
        }
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_contact_card),
                        str(R.string.nowbar_labs_contact_card_summary),
                        labs.nativeContactCard,
                        checked -> {
                            labs.nativeContactCard = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_google_finance),
                        str(R.string.nowbar_labs_google_finance_summary),
                        labs.unlockGoogleFinance,
                        checked -> {
                            labs.unlockGoogleFinance = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_google_commute),
                        str(R.string.nowbar_labs_google_commute_summary),
                        labs.unlockGoogleCommute,
                        checked -> {
                            labs.unlockGoogleCommute = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_google_sports),
                        str(R.string.nowbar_labs_google_sports_summary),
                        labs.unlockGoogleSports,
                        checked -> {
                            labs.unlockGoogleSports = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_naver_sports),
                        str(R.string.nowbar_labs_naver_sports_summary),
                        labs.unlockNaverSports,
                        checked -> {
                            labs.unlockNaverSports = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_gemini_commute),
                        str(R.string.nowbar_labs_gemini_commute_summary),
                        labs.geminiCommuteRecap,
                        checked -> {
                            labs.geminiCommuteRecap = checked;
                            persistLabs();
                        }));
        nativeGroup.addView(ui.insetDivider());
        nativeGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_buds_music),
                        str(R.string.nowbar_labs_buds_music_summary),
                        labs.budsInMusicCard,
                        checked -> {
                            labs.budsInMusicCard = checked;
                            persistLabs();
                        }));
        body.addView(nativeGroup);

        body.addView(
                ui.sectionHeader(
                        str(R.string.nowbar_labs_section_global_shader),
                        str(R.string.nowbar_labs_all_cards)));
        LinearLayout shaderGroup = ui.cardGroup();
        shaderGroup.addView(
                ui.prefRow(
                        str(R.string.nowbar_labs_shader_non_diy),
                        DiyNowBarLabs.globalShaderLabel(this, labs.globalCardShader),
                        labs.globalCardShader != DiyNowBarCard.EFFECT_NONE,
                        () ->
                                new AlertDialog.Builder(this)
                                        .setTitle(str(R.string.nowbar_labs_global_shader_title))
                                        .setSingleChoiceItems(
                                                DiyNowBarLabs.globalShaderLabels(this),
                                                DiyNowBarLabs.globalShaderIndex(
                                                        labs.globalCardShader),
                                                (d, which) -> {
                                                    labs.globalCardShader =
                                                            DiyNowBarLabs.GLOBAL_SHADER_VALUES[
                                                                    which];
                                                    d.dismiss();
                                                    persistLabs();
                                                    showManager();
                                                })
                                        .setNegativeButton(str(R.string.common_cancel), null)
                                        .show()));

        if (labs.globalCardShader != DiyNowBarCard.EFFECT_NONE) {
            shaderGroup.addView(ui.insetDivider());
            LinearLayout presetGrid = new LinearLayout(this);
            presetGrid.setOrientation(LinearLayout.VERTICAL);
            presetGrid.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            for (int r = 0; r < DiyNowBarCard.SHADER_PRESETS.length; r += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                if (r > 0) rlp.topMargin = ui.dp(8);
                for (int c = 0; c < 2; c++) {
                    int idx = r + c;
                    if (idx >= DiyNowBarCard.SHADER_PRESETS.length) break;
                    DiyNowBarCard.ShaderPreset preset = DiyNowBarCard.SHADER_PRESETS[idx];
                    boolean active =
                            labs.globalColor1 == preset.color1
                                    && labs.globalColor2 == preset.color2;
                    TextView pill = new TextView(this);
                    pill.setText(DiyNowBarCard.shaderPresetName(this, idx));
                    pill.setTextColor(Color.WHITE);
                    pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    pill.setGravity(Gravity.CENTER);
                    pill.setMinHeight(ui.dp(40));
                    GradientDrawable gd =
                            new GradientDrawable(
                                    GradientDrawable.Orientation.LEFT_RIGHT,
                                    new int[] {preset.color1, preset.color2});
                    gd.setCornerRadius(ui.dp(18));
                    if (active) gd.setStroke(ui.dp(2), Color.WHITE);
                    pill.setBackground(gd);
                    pill.setClickable(true);
                    ui.applyNativeRecoil(pill, false);
                    pill.setOnClickListener(
                            v -> {
                                labs.globalColor1 = preset.color1;
                                labs.globalColor2 = preset.color2;
                                persistLabs();
                                showManager();
                            });
                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (c > 0) lp.setMarginStart(ui.dp(8));
                    row.addView(pill, lp);
                }
                presetGrid.addView(row, rlp);
            }
            shaderGroup.addView(presetGrid);
        }
        body.addView(shaderGroup);

        body.addView(ui.sectionHeader(str(R.string.nowbar_labs_section_studio), null));
        LinearLayout intGroup = ui.cardGroup();
        intGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_labs_long_press_studio),
                        str(R.string.nowbar_labs_long_press_studio_summary),
                        labs.longPressOpensStudio,
                        checked -> {
                            labs.longPressOpensStudio = checked;
                            persistLabs();
                        }));
        body.addView(intGroup);

        body.addView(ui.footerNote(str(R.string.nowbar_labs_footer)));
    }

    private View buildManagerRow(
            DiyNowBarCard card, int index, DiyNowBarPublisher.StatsSnapshot stats, long now) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(68));
        row.setPadding(ui.dp(18), ui.dp(10), ui.dp(16), ui.dp(10));

        // Left tappable region opens editor
        LinearLayout hitArea = new LinearLayout(this);
        hitArea.setOrientation(LinearLayout.HORIZONTAL);
        hitArea.setGravity(Gravity.CENTER_VERTICAL);
        hitArea.setBackground(ui.listRowRecoil());
        hitArea.setClickable(true);
        hitArea.setFocusable(true);
        ui.applyRecoil(hitArea, "sesl_recoil_list_selector", 0.99f);
        hitArea.setOnClickListener(v -> openEditor(card.copy(), false));

        ImageView badge = new ImageView(this);
        badge.setImageBitmap(DiyNowBarIcons.renderBadgeBitmap(this, card, ui.dp(40)));
        hitArea.addView(badge, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.setMarginStart(ui.dp(14));
        infoLp.setMarginEnd(ui.dp(8));

        TextView title = new TextView(this);
        title.setText(card.title);
        title.setTextColor(ui.primaryText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);

        String sub = DiyNowBarPreviewView.previewPillSubtitle(this, card, stats, now);
        TextView subTv = new TextView(this);
        subTv.setText(
                DiyNowBarCard.typeName(this, card.type)
                        + " \u00B7 "
                        + DiyNowBarCard.effectName(this, card.effect)
                        + (sub.isEmpty() ? "" : " \u00B7 " + sub));
        subTv.setTextColor(ui.secondaryText);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = ui.dp(2);
        info.addView(subTv, slp);
        hitArea.addView(info, infoLp);

        row.addView(
                hitArea, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Overflow menu (Move Up / Move Down / Duplicate / Delete)
        ImageView moreBtn =
                ui.toolbarIconButton(
                        "more", ui.secondaryText, str(R.string.nowbar_card_options), null);
        moreBtn.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        moreBtn.setOnClickListener(v -> showCardOverflowMenu(v, card, index));
        row.addView(moreBtn);

        row.addView(ui.verticalDivider());

        CompoundButton enabledSwitch =
                ui.createSwitch(
                        card.enabled,
                        true,
                        isChecked -> {
                            card.enabled = isChecked;
                            persistCards();
                            updateManagerCountLabel();
                        });
        row.addView(enabledSwitch);
        return row;
    }

    private void updateManagerCountLabel() {
        if (managerCountLabel == null) return;
        int activeCount = 0;
        for (DiyNowBarCard c : cards) if (c.enabled) activeCount++;
        managerCountLabel.setText(
                str(
                        R.string.nowbar_cards_count_header,
                        activeCount,
                        cards.size(),
                        DiyNowBarCard.MAX_CARDS));
    }

    private void showCardOverflowMenu(View anchor, DiyNowBarCard card, int index) {
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu m = menu.getMenu();
        m.add(0, 1, 0, str(R.string.common_edit));
        if (index > 0) m.add(0, 2, 1, str(R.string.nowbar_menu_move_up));
        if (index + 1 < cards.size()) m.add(0, 3, 2, str(R.string.nowbar_menu_move_down));
        if (cards.size() < DiyNowBarCard.MAX_CARDS) m.add(0, 4, 3, str(R.string.nowbar_menu_duplicate));
        m.add(0, 5, 4, str(R.string.common_delete));
        menu.setOnMenuItemClickListener(
                item -> {
                    switch (item.getItemId()) {
                        case 1 -> openEditor(card.copy(), false);
                        case 2 -> {
                            Collections.swap(cards, index, index - 1);
                            persistCards();
                            showManager();
                        }
                        case 3 -> {
                            Collections.swap(cards, index, index + 1);
                            persistCards();
                            showManager();
                        }
                        case 4 -> {
                            DiyNowBarCard dup = card.copy();
                            dup.id = DiyNowBarCard.generateId();
                            dup.title = str(R.string.nowbar_card_copy_title, dup.title);
                            dup.sanitize();
                            cards.add(index + 1, dup);
                            persistCards();
                            showManager();
                        }
                        case 5 -> confirmDeleteCard(card);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                });
        menu.show();
    }

    private void showTemplatePickerDialog() {
        String[] labels = new String[DiyNowBarCard.TYPE_NAMES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = DiyNowBarCard.typeName(this, i) + " - " + templateSummary(i);
        }
        new AlertDialog.Builder(this)
                .setTitle(str(R.string.nowbar_new_card_dialog_title))
                .setItems(labels, (d, which) -> createFromTemplate(which))
                .setNegativeButton(str(R.string.common_cancel), null)
                .show();
    }

    private void createFromTemplate(int type) {
        DiyNowBarCard created = DiyNowBarCard.defaultForType(this, type, System.currentTimeMillis());
        openEditor(created, true);
    }

    private void confirmDeleteCard(DiyNowBarCard card) {
        new AlertDialog.Builder(this)
                .setTitle(str(R.string.nowbar_delete_card_title, card.title))
                .setMessage(str(R.string.nowbar_delete_card_message))
                .setPositiveButton(
                        str(R.string.common_delete),
                        (d, which) -> {
                            cards.removeIf(c -> c.id.equals(card.id));
                            persistCards();
                            showManager();
                        })
                .setNegativeButton(str(R.string.common_cancel), null)
                .show();
    }

    private void persistCards() {
        boolean ok = DiyNowBarStore.commit(this, cards);
        if (!ok) {
            Toast.makeText(this, str(R.string.nowbar_save_cards_failed), Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------------
    // Editor Screen (Native Now Bar Pill at top + One UI Settings below)
    // -------------------------------------------------------------------------

    private void openEditor(DiyNowBarCard card, boolean isNew) {
        this.editingCard = card.sanitize();
        this.editingIsNew = isNew;
        this.editorTab = 0;
        renderEditorShell();
    }

    private void renderEditorShell() {
        rootHost.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // Clean One UI 56dp toolbar
        View deleteIcon =
                editingIsNew
                        ? null
                        : ui.toolbarIconButton(
                                "trash",
                                ui.dangerColor,
                                str(R.string.nowbar_delete_card),
                                () -> confirmDeleteCard(editingCard));
        page.addView(
                ui.toolbar(
                        str(editingIsNew ? R.string.nowbar_new_card_title : R.string.nowbar_edit_card_title),
                        this::handleBackAction,
                        deleteIcon));

        // Compact floating native Now Bar pill preview right at the top
        LinearLayout previewWrap = new LinearLayout(this);
        previewWrap.setOrientation(LinearLayout.VERTICAL);
        previewWrap.setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(6));
        previewWrap.setClipChildren(false);
        previewWrap.setClipToPadding(false);
        previewView = new DiyNowBarPreviewView(ui);
        previewView.bind(editingCard);
        previewWrap.addView(previewView);
        page.addView(previewWrap);

        // Clean One UI Sub-Tab Bar
        tabBarHost = new LinearLayout(this);
        tabBarHost.setOrientation(LinearLayout.VERTICAL);
        page.addView(tabBarHost);
        renderSubTabBar();

        // Scrollable One UI preference groups underneath
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        tabBodyHost = new LinearLayout(this);
        tabBodyHost.setOrientation(LinearLayout.VERTICAL);
        tabBodyHost.setPadding(0, ui.dp(2), 0, ui.dp(20));
        scroll.addView(tabBodyHost);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Standard One UI Cancel | Save bottom bar
        page.addView(
                ui.bottomActionBar(
                        str(R.string.common_cancel),
                        this::handleBackAction,
                        str(R.string.common_save),
                        this::saveEditingCard));

        rootHost.addView(page);
        renderActiveEditorTab();
    }

    private void renderSubTabBar() {
        if (tabBarHost == null) return;
        tabBarHost.removeAllViews();
        tabBarHost.addView(
                ui.subTabBar(
                        new String[] {
                            str(R.string.nowbar_editor_tab_content),
                            str(R.string.nowbar_editor_tab_icon),
                            str(R.string.nowbar_editor_tab_effects),
                            str(R.string.nowbar_editor_tab_actions)
                        },
                        editorTab,
                        idx -> {
                            editorTab = idx;
                            renderSubTabBar();
                            renderActiveEditorTab();
                        }));
    }

    private void refreshPreview() {
        if (previewView != null && editingCard != null) {
            previewView.bind(editingCard);
        }
    }

    private void saveEditingCard() {
        if (editingCard == null) return;
        editingCard.sanitize();
        int existingIdx = -1;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id.equals(editingCard.id)) {
                existingIdx = i;
                break;
            }
        }
        if (existingIdx >= 0) {
            cards.set(existingIdx, editingCard.copy());
        } else {
            if (cards.size() >= DiyNowBarCard.MAX_CARDS) {
                Toast.makeText(
                                this,
                                str(R.string.nowbar_max_cards_reached, DiyNowBarCard.MAX_CARDS),
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            cards.add(editingCard.copy());
        }
        persistCards();
        Toast.makeText(this, str(R.string.nowbar_saved_toast), Toast.LENGTH_SHORT).show();
        showManager();
    }

    private void renderActiveEditorTab() {
        if (tabBodyHost == null || editingCard == null) return;
        tabBodyHost.removeAllViews();
        switch (editorTab) {
            case 0 -> buildContentTab(tabBodyHost);
            case 1 -> buildIconTab(tabBodyHost);
            case 2 -> buildEffectsTab(tabBodyHost);
            case 3 -> buildActionsTab(tabBodyHost);
            default -> buildContentTab(tabBodyHost);
        }
    }

    // -------------------------------------------------------------------------
    // Tab 0: Content
    // -------------------------------------------------------------------------

    private void buildContentTab(LinearLayout host) {
        // Card Type strip
        LinearLayout typeWrap = ui.cardGroup();
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        for (int i = 0; i < DiyNowBarCard.TYPE_NAMES.length; i++) {
            final int t = i;
            TextView chip =
                    ui.chip(
                            DiyNowBarCard.typeName(this, i),
                            editingCard.type == t,
                            () -> switchCardType(t));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(ui.dp(6));
            strip.addView(chip, lp);
        }
        hsv.addView(strip);
        typeWrap.addView(hsv);
        host.addView(typeWrap);

        host.addView(
                ui.sectionHeader(
                        str(
                                R.string.nowbar_editor_type_details,
                                DiyNowBarCard.typeName(this, editingCard.type)),
                        null));

        LinearLayout form = ui.cardGroup();
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_title),
                        str(R.string.nowbar_field_title_hint),
                        editingCard.title,
                        60,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.title = s;
                            refreshPreview();
                        }));

        switch (editingCard.type) {
            case DiyNowBarCard.TYPE_CUSTOM -> buildCustomFields(form);
            case DiyNowBarCard.TYPE_TIMER -> buildTimerFields(form);
            case DiyNowBarCard.TYPE_GOAL -> buildGoalFields(form);
            case DiyNowBarCard.TYPE_STATS -> buildStatsFields(form);
            case DiyNowBarCard.TYPE_LAUNCHER -> buildLauncherFields(form);
            case DiyNowBarCard.TYPE_CHECKLIST -> buildChecklistFields(form);
            case DiyNowBarCard.TYPE_CONTROLS -> buildControlsFields(form);
            default -> buildCustomFields(form);
        }

        // Inline secondary mini-icon (nowbar_key_second_info_icon, unblocked for all DIY cards)
        form.addView(ui.insetDivider());
        form.addView(
                ui.prefRow(
                        str(R.string.nowbar_field_secondary_icon),
                        editingCard.secondaryIcon.isEmpty()
                                ? str(R.string.common_none)
                                : editingCard.secondaryIcon,
                        !editingCard.secondaryIcon.isEmpty(),
                        this::showSecondaryIconPickerDialog));
        host.addView(form);
    }

    private void switchCardType(int newType) {
        if (editingCard.type == newType) return;
        DiyNowBarCard defaults =
                DiyNowBarCard.defaultForType(this, newType, System.currentTimeMillis());
        editingCard.type = newType;
        if (newType == DiyNowBarCard.TYPE_TIMER && editingCard.timerTargetAtMs <= 0L) {
            editingCard.timerCreatedAtMs = defaults.timerCreatedAtMs;
            editingCard.timerTargetAtMs = defaults.timerTargetAtMs;
        }
        if (newType == DiyNowBarCard.TYPE_GOAL) {
            editingCard.showProgress = true;
            if (editingCard.targetValue <= 1) editingCard.targetValue = defaults.targetValue;
            if (editingCard.unit.isEmpty()) editingCard.unit = defaults.unit;
        }
        if (newType == DiyNowBarCard.TYPE_CHECKLIST && editingCard.checklist.isEmpty()) {
            editingCard.checklist.addAll(defaults.checklist);
        }
        if (newType == DiyNowBarCard.TYPE_LAUNCHER && editingCard.activeShortcutCount() == 0) {
            editingCard.shortcuts.clear();
            editingCard.shortcuts.addAll(defaults.shortcuts);
        }
        editingCard.sanitize();
        refreshPreview();
        renderActiveEditorTab();
    }

    private void buildCustomFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_subtitle),
                        str(R.string.nowbar_field_subtitle_collapsed_hint),
                        editingCard.subtitle,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.subtitle = s;
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_expanded_note),
                        str(R.string.nowbar_field_expanded_note_hint),
                        editingCard.body,
                        280,
                        true,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.body = s;
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        form.addView(
                ui.switchRow(
                        str(R.string.nowbar_field_show_progress),
                        str(R.string.nowbar_field_show_progress_summary),
                        editingCard.showProgress,
                        checked -> {
                            editingCard.showProgress = checked;
                            refreshPreview();
                            if (tabBodyHost != null) {
                                tabBodyHost.postDelayed(this::renderActiveEditorTab, 180L);
                            }
                        }));

        if (editingCard.showProgress) {
            form.addView(ui.insetDivider());
            form.addView(
                    ui.inputBlock(
                            str(R.string.nowbar_field_progress_label),
                            str(R.string.nowbar_field_progress_label_hint),
                            editingCard.progressLabel,
                            40,
                            false,
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                            s -> {
                                editingCard.progressLabel = s;
                                refreshPreview();
                            }));
            form.addView(ui.insetDivider());
            form.addView(
                    buildNumberPairRow(
                            str(R.string.nowbar_field_current),
                            editingCard.currentValue,
                            str(R.string.nowbar_field_target),
                            editingCard.targetValue,
                            v -> {
                                editingCard.currentValue = Math.max(0, v);
                                refreshPreview();
                            },
                            v -> {
                                editingCard.targetValue = Math.max(1, v);
                                refreshPreview();
                            }));
        }
    }

    private void buildTimerFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
        TextView cdChip =
                ui.chip(
                        str(R.string.nowbar_timer_countdown),
                        editingCard.timerMode == DiyNowBarCard.TIMER_COUNTDOWN,
                        () -> {
                            editingCard.timerMode = DiyNowBarCard.TIMER_COUNTDOWN;
                            if (editingCard.timerTargetAtMs <= 0L) {
                                long now = System.currentTimeMillis();
                                editingCard.timerCreatedAtMs = now;
                                editingCard.timerTargetAtMs = now + 3_600_000L;
                            }
                            refreshPreview();
                            renderActiveEditorTab();
                        });
        TextView swChip =
                ui.chip(
                        str(R.string.nowbar_timer_stopwatch),
                        editingCard.timerMode == DiyNowBarCard.TIMER_STOPWATCH,
                        () -> {
                            editingCard.timerMode = DiyNowBarCard.TIMER_STOPWATCH;
                            refreshPreview();
                            renderActiveEditorTab();
                        });
        LinearLayout.LayoutParams c1 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c1.setMarginEnd(ui.dp(8));
        modeRow.addView(cdChip, c1);
        modeRow.addView(
                swChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        form.addView(modeRow);

        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_subtitle),
                        str(R.string.nowbar_field_subtitle_expanded_hint),
                        editingCard.subtitle,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.subtitle = s;
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        form.addView(
                ui.switchRow(
                        str(R.string.nowbar_timer_big_clock),
                        str(R.string.nowbar_timer_big_clock_summary),
                        editingCard.bigChronometer,
                        checked -> {
                            editingCard.bigChronometer = checked;
                            refreshPreview();
                        }));

        if (editingCard.timerMode == DiyNowBarCard.TIMER_COUNTDOWN) {
            long target =
                    editingCard.timerTargetAtMs > 0L
                            ? editingCard.timerTargetAtMs
                            : System.currentTimeMillis() + 3_600_000L;
            String dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(target));
            String timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(target));

            form.addView(ui.insetDivider());
            form.addView(
                    ui.prefRow(
                            str(R.string.nowbar_timer_target_date),
                            dateStr,
                            true,
                            this::openCountdownDatePicker));
            form.addView(ui.insetDivider());
            form.addView(
                    ui.prefRow(
                            str(R.string.nowbar_timer_target_time),
                            timeStr,
                            true,
                            this::openCountdownTimePicker));
            form.addView(ui.insetDivider());

            LinearLayout quickRow = new LinearLayout(this);
            quickRow.setOrientation(LinearLayout.HORIZONTAL);
            quickRow.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
            String[] labels = {"+15m", "+1h", "+3h", "+1d", "+7d"};
            long[] offsets = {
                15 * 60_000L, 3_600_000L, 3 * 3_600_000L, 86_400_000L, 7 * 86_400_000L
            };
            for (int i = 0; i < labels.length; i++) {
                final long delta = offsets[i];
                TextView btn =
                        ui.chip(
                                labels[i],
                                false,
                                () -> {
                                    long now = System.currentTimeMillis();
                                    editingCard.timerCreatedAtMs = now;
                                    editingCard.timerTargetAtMs = now + delta;
                                    refreshPreview();
                                    renderActiveEditorTab();
                                });
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) lp.setMarginStart(ui.dp(6));
                quickRow.addView(btn, lp);
            }
            form.addView(quickRow);
        } else {
            form.addView(ui.insetDivider());
            LinearLayout swRow = new LinearLayout(this);
            swRow.setOrientation(LinearLayout.HORIZONTAL);
            swRow.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
            TextView toggleBtn =
                    ui.flatPillButton(
                            str(editingCard.timerRunning ? R.string.common_pause : R.string.common_start),
                            ui.accent,
                            () -> {
                                editingCard.applyMutation(
                                        DiyNowBarCard.MUTATE_TIMER_TOGGLE,
                                        -1,
                                        System.currentTimeMillis());
                                refreshPreview();
                                renderActiveEditorTab();
                            });
            TextView resetBtn =
                    ui.flatPillButton(
                            str(R.string.common_reset),
                            ui.primaryText,
                            () -> {
                                editingCard.applyMutation(
                                        DiyNowBarCard.MUTATE_TIMER_RESET,
                                        -1,
                                        System.currentTimeMillis());
                                refreshPreview();
                                renderActiveEditorTab();
                            });
            LinearLayout.LayoutParams s1 =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            s1.setMarginEnd(ui.dp(8));
            swRow.addView(toggleBtn, s1);
            swRow.addView(
                    resetBtn,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            form.addView(swRow);
        }
    }

    private void openCountdownDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (editingCard.timerTargetAtMs > 0L) cal.setTimeInMillis(editingCard.timerTargetAtMs);
        new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            cal.set(year, month, dayOfMonth);
                            editingCard.timerCreatedAtMs = System.currentTimeMillis();
                            editingCard.timerTargetAtMs = cal.getTimeInMillis();
                            refreshPreview();
                            renderActiveEditorTab();
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void openCountdownTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (editingCard.timerTargetAtMs > 0L) cal.setTimeInMillis(editingCard.timerTargetAtMs);
        new TimePickerDialog(
                        this,
                        (view, hourOfDay, minute) -> {
                            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                            cal.set(Calendar.MINUTE, minute);
                            cal.set(Calendar.SECOND, 0);
                            editingCard.timerCreatedAtMs = System.currentTimeMillis();
                            editingCard.timerTargetAtMs = cal.getTimeInMillis();
                            refreshPreview();
                            renderActiveEditorTab();
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        android.text.format.DateFormat.is24HourFormat(this))
                .show();
    }

    private void buildGoalFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_goal_unit),
                        str(R.string.nowbar_goal_unit_hint),
                        editingCard.unit,
                        20,
                        false,
                        InputType.TYPE_CLASS_TEXT,
                        s -> {
                            editingCard.unit = s;
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        form.addView(
                buildNumberPairRow(
                        str(R.string.nowbar_field_current),
                        editingCard.currentValue,
                        str(R.string.nowbar_field_target),
                        editingCard.targetValue,
                        v -> {
                            editingCard.currentValue = Math.max(0, v);
                            refreshPreview();
                        },
                        v -> {
                            editingCard.targetValue = Math.max(1, v);
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_goal_step_size),
                        "1",
                        String.valueOf(editingCard.stepValue),
                        6,
                        false,
                        InputType.TYPE_CLASS_NUMBER,
                        s -> {
                            editingCard.stepValue = Math.max(1, parseIntSafe(s, 1));
                            refreshPreview();
                        }));

        LinearLayout quickSteps = new LinearLayout(this);
        quickSteps.setOrientation(LinearLayout.HORIZONTAL);
        quickSteps.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(14));
        for (int step : new int[] {1, 5, 10, 25}) {
            TextView chip =
                    ui.chip(
                            "+" + step,
                            editingCard.stepValue == step,
                            () -> {
                                editingCard.stepValue = step;
                                refreshPreview();
                                renderActiveEditorTab();
                            });
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (step != 1) lp.setMarginStart(ui.dp(6));
            quickSteps.addView(chip, lp);
        }
        form.addView(quickSteps);
    }

    private void buildStatsFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_subtitle),
                        str(R.string.nowbar_field_subtitle_stats_hint),
                        editingCard.subtitle,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.subtitle = s;
                            refreshPreview();
                        }));

        form.addView(ui.insetDivider());
        LinearLayout fmtWrap = new LinearLayout(this);
        fmtWrap.setOrientation(LinearLayout.VERTICAL);
        fmtWrap.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(8));

        TextView fmtLbl = new TextView(this);
        fmtLbl.setText(str(R.string.nowbar_stats_custom_pill_text));
        fmtLbl.setTextColor(ui.secondaryText);
        fmtLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fmtWrap.addView(fmtLbl);

        EditText fmtInput =
                ui.rawInput(
                        str(R.string.nowbar_stats_custom_pill_text_hint),
                        editingCard.statsFormat,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT,
                        s -> {
                            editingCard.statsFormat = s;
                            refreshPreview();
                        });
        fmtWrap.addView(
                fmtInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(fmtWrap);

        HorizontalScrollView tokenScroll = new HorizontalScrollView(this);
        tokenScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(12));
        for (int i = 0; i < DiyNowBarCard.STAT_TOKENS.length; i++) {
            String token = DiyNowBarCard.STAT_TOKENS[i];
            TextView tokChip =
                    ui.chip(
                            token,
                            false,
                            () -> {
                                Editable ed = fmtInput.getText();
                                int start = Math.max(0, fmtInput.getSelectionStart());
                                int end = Math.max(0, fmtInput.getSelectionEnd());
                                String insert = token;
                                if (start > 0
                                        && ed.charAt(start - 1) != ' '
                                        && ed.charAt(start - 1) != '\u00B7') {
                                    insert = " \u00B7 " + token;
                                }
                                ed.replace(Math.min(start, end), Math.max(start, end), insert);
                                fmtInput.requestFocus();
                            });
            LinearLayout.LayoutParams tlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) tlp.setMarginStart(ui.dp(6));
            tokenRow.addView(tokChip, tlp);
        }
        tokenScroll.addView(tokenRow);
        form.addView(tokenScroll);

        form.addView(ui.insetDivider());
        form.addView(
                ui.switchRow(
                        str(R.string.nowbar_stats_sparkline),
                        str(R.string.nowbar_stats_sparkline_summary),
                        editingCard.liveExpandedView,
                        checked -> {
                            editingCard.liveExpandedView = checked;
                            refreshPreview();
                        }));

        int[] bits = {
            DiyNowBarCard.STAT_BATTERY,
            DiyNowBarCard.STAT_RAM,
            DiyNowBarCard.STAT_STORAGE,
            DiyNowBarCard.STAT_NETWORK,
            DiyNowBarCard.STAT_UPTIME
        };
        form.addView(ui.insetDivider());
        form.addView(
                ui.prefRow(
                        str(R.string.nowbar_stats_collapsed_metric),
                        editingCard.statsFormat.isEmpty()
                                ? DiyNowBarCard.statName(this, editingCard.statsPrimary)
                                : str(R.string.nowbar_stats_custom_text_active),
                        editingCard.statsFormat.isEmpty(),
                        () -> {
                            String[] names = new String[bits.length];
                            int checked = 0;
                            for (int i = 0; i < bits.length; i++) {
                                names[i] = DiyNowBarCard.statName(this, bits[i]);
                                if (bits[i] == editingCard.statsPrimary) checked = i;
                            }
                            new AlertDialog.Builder(this)
                                    .setTitle(str(R.string.nowbar_stats_collapsed_metric))
                                    .setSingleChoiceItems(
                                            names,
                                            checked,
                                            (d, which) -> {
                                                editingCard.statsPrimary = bits[which];
                                                editingCard.statsMask |= bits[which];
                                                d.dismiss();
                                                refreshPreview();
                                                renderActiveEditorTab();
                                            })
                                    .setNegativeButton(str(R.string.common_cancel), null)
                                    .show();
                        }));

        for (int bit : bits) {
            form.addView(ui.insetDivider());
            boolean checked = (editingCard.statsMask & bit) != 0;
            form.addView(
                    ui.switchRow(
                            DiyNowBarCard.statName(this, bit),
                            null,
                            checked,
                            isOn -> {
                                int next =
                                        isOn
                                                ? (editingCard.statsMask | bit)
                                                : (editingCard.statsMask & ~bit);
                                if (next == 0) {
                                    Toast.makeText(
                                                    this,
                                                    str(R.string.nowbar_stats_keep_one_metric),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                    renderActiveEditorTab();
                                    return;
                                }
                                editingCard.statsMask = next;
                                editingCard.sanitize();
                                refreshPreview();
                            }));
        }
    }

    private void buildLauncherFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_field_subtitle),
                        str(R.string.nowbar_field_subtitle_launcher_hint),
                        editingCard.subtitle,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.subtitle = s;
                            refreshPreview();
                        }));

        editingCard.sanitize();
        for (int i = 0; i < DiyNowBarCard.MAX_SHORTCUTS; i++) {
            final int slotIndex = i;
            DiyNowBarCard.ShortcutSlot slot = editingCard.shortcuts.get(i);
            form.addView(ui.insetDivider());
            form.addView(
                    ui.prefRow(
                            str(R.string.nowbar_shortcut_slot, i + 1),
                            describeSlot(slot),
                            slot.action != DiyNowBarCard.ACTION_NONE,
                            () -> showShortcutSlotDialog(slotIndex)));
        }
    }

    private String describeSlot(DiyNowBarCard.ShortcutSlot slot) {
        return switch (slot.action) {
            case DiyNowBarCard.ACTION_OPEN_APP ->
                    slot.label.isEmpty()
                            ? DiyNowBarIcons.loadAppLabel(this, slot.target)
                            : slot.label;
            case DiyNowBarCard.ACTION_FLASHLIGHT -> str(R.string.nowbar_action_toggle_flashlight);
            case DiyNowBarCard.ACTION_OPEN_URL -> slot.target;
            case DiyNowBarCard.ACTION_OPEN_EDITOR -> str(R.string.diy_nowbar_setting_title);
            default -> str(R.string.nowbar_shortcut_not_set);
        };
    }

    private void showShortcutSlotDialog(int slotIndex) {
        String[] choices = {
            str(R.string.nowbar_shortcut_open_app),
            str(R.string.nowbar_shortcut_flashlight_no_unlock),
            str(R.string.nowbar_shortcut_open_url),
            str(R.string.nowbar_shortcut_open_studio),
            str(R.string.nowbar_shortcut_clear)
        };
        new AlertDialog.Builder(this)
                .setTitle(str(R.string.nowbar_shortcut_slot, slotIndex + 1))
                .setItems(
                        choices,
                        (d, which) -> {
                            DiyNowBarCard.ShortcutSlot slot = editingCard.shortcuts.get(slotIndex);
                            switch (which) {
                                case 0 ->
                                        showAppPickerDialog(
                                                app -> {
                                                    slot.action = DiyNowBarCard.ACTION_OPEN_APP;
                                                    slot.target = app.packageName();
                                                    slot.label = app.label();
                                                    refreshPreview();
                                                    renderActiveEditorTab();
                                                });
                                case 1 -> {
                                    slot.action = DiyNowBarCard.ACTION_FLASHLIGHT;
                                    slot.label = str(R.string.nowbar_short_torch);
                                    slot.target = "";
                                    refreshPreview();
                                    renderActiveEditorTab();
                                }
                                case 2 ->
                                        promptUrlDialog(
                                                slot.target,
                                                url -> {
                                                    slot.action = DiyNowBarCard.ACTION_OPEN_URL;
                                                    slot.target = url;
                                                    slot.label = str(R.string.nowbar_short_link);
                                                    refreshPreview();
                                                    renderActiveEditorTab();
                                                });
                                case 3 -> {
                                    slot.action = DiyNowBarCard.ACTION_OPEN_EDITOR;
                                    slot.label = str(R.string.nowbar_short_studio);
                                    slot.target = "";
                                    refreshPreview();
                                    renderActiveEditorTab();
                                }
                                default -> {
                                    slot.action = DiyNowBarCard.ACTION_NONE;
                                    slot.label = "";
                                    slot.target = "";
                                    refreshPreview();
                                    renderActiveEditorTab();
                                }
                            }
                        })
                .setNegativeButton(str(R.string.common_cancel), null)
                .show();
    }

    private void buildChecklistFields(LinearLayout form) {
        for (int i = 0; i < editingCard.checklist.size(); i++) {
            final int idx = i;
            DiyNowBarCard.CheckItem item = editingCard.checklist.get(i);
            form.addView(ui.insetDivider());
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(14), ui.dp(6), ui.dp(10), ui.dp(6));

            CompoundButton cb =
                    ui.createCheckBox(
                            item.done,
                            isChecked -> {
                                item.done = isChecked;
                                refreshPreview();
                            });
            row.addView(cb);

            EditText edit =
                    ui.rawInput(
                            str(R.string.nowbar_checklist_task_hint, i + 1),
                            item.text,
                            64,
                            false,
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                            s -> {
                                item.text = s;
                                refreshPreview();
                            });
            LinearLayout.LayoutParams elp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            elp.setMarginStart(ui.dp(6));
            elp.setMarginEnd(ui.dp(4));
            row.addView(edit, elp);

            row.addView(
                    ui.toolbarIconButton(
                            "trash",
                            ui.secondaryText,
                            str(R.string.nowbar_checklist_remove_item),
                            () -> {
                                editingCard.checklist.remove(idx);
                                refreshPreview();
                                renderActiveEditorTab();
                            }));
            form.addView(row);
        }

        if (editingCard.checklist.size() < DiyNowBarCard.MAX_CHECKLIST) {
            form.addView(ui.insetDivider());
            form.addView(
                    ui.prefRow(
                            str(R.string.nowbar_checklist_add_task),
                            str(
                                    R.string.nowbar_checklist_count_of,
                                    editingCard.checklist.size(),
                                    DiyNowBarCard.MAX_CHECKLIST),
                            true,
                            () -> {
                                editingCard.checklist.add(
                                        new DiyNowBarCard.CheckItem(
                                                str(R.string.nowbar_checklist_new_task), false));
                                refreshPreview();
                                renderActiveEditorTab();
                            }));
        }
    }

    private void buildControlsFields(LinearLayout form) {
        form.addView(ui.insetDivider());
        form.addView(
                ui.inputBlock(
                        str(R.string.nowbar_controls_subtitle_label),
                        str(R.string.nowbar_controls_subtitle_hint),
                        editingCard.subtitle,
                        80,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        s -> {
                            editingCard.subtitle = s;
                            refreshPreview();
                        }));
        form.addView(ui.insetDivider());
        form.addView(
                ui.prefRow(
                        str(R.string.nowbar_controls_expanded_live_view),
                        str(R.string.nowbar_controls_expanded_live_view_summary),
                        true,
                        () -> {
                            if (previewView != null) previewView.setExpanded(true);
                        }));
    }

    private void showSecondaryIconPickerDialog() {
        String[] options = new String[DiyNowBarCard.SYMBOL_KEYS.length + 1];
        options[0] = str(R.string.common_none);
        int selected = 0;
        for (int i = 0; i < DiyNowBarCard.SYMBOL_KEYS.length; i++) {
            String key = DiyNowBarCard.SYMBOL_KEYS[i];
            options[i + 1] = key;
            if (key.equals(editingCard.secondaryIcon)) selected = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(str(R.string.nowbar_field_secondary_icon))
                .setSingleChoiceItems(
                        options,
                        selected,
                        (d, which) -> {
                            editingCard.secondaryIcon =
                                    which == 0 ? "" : DiyNowBarCard.SYMBOL_KEYS[which - 1];
                            d.dismiss();
                            refreshPreview();
                            renderActiveEditorTab();
                        })
                .setNegativeButton(str(R.string.common_cancel), null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Tab 1: Icon & Accent
    // -------------------------------------------------------------------------

    private void buildIconTab(LinearLayout host) {
        LinearLayout sourceGroup = ui.cardGroup();
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(12));
        String[] sources = {
            str(R.string.nowbar_icon_source_symbol),
            str(R.string.nowbar_icon_source_emoji),
            str(R.string.nowbar_icon_source_app),
            str(R.string.nowbar_icon_source_photo)
        };
        for (int i = 0; i < sources.length; i++) {
            final int mode = i;
            TextView chip =
                    ui.chip(
                            sources[i],
                            editingCard.iconType == mode,
                            () -> {
                                editingCard.iconType = mode;
                                if (mode == DiyNowBarCard.ICON_SYMBOL
                                        && !DiyNowBarCard.isValidSymbol(editingCard.iconValue)) {
                                    editingCard.iconValue = DiyNowBarCard.SYMBOL_KEYS[0];
                                } else if (mode == DiyNowBarCard.ICON_EMOJI
                                        && (editingCard.iconValue.isEmpty()
                                                || DiyNowBarCard.isValidSymbol(
                                                        editingCard.iconValue))) {
                                    editingCard.iconValue = QUICK_EMOJIS[0];
                                }
                                refreshPreview();
                                renderActiveEditorTab();
                            });
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(ui.dp(6));
            sourceRow.addView(chip, lp);
        }
        sourceGroup.addView(sourceRow);

        switch (editingCard.iconType) {
            case DiyNowBarCard.ICON_SYMBOL -> buildSymbolGrid(sourceGroup);
            case DiyNowBarCard.ICON_EMOJI -> buildEmojiPicker(sourceGroup);
            case DiyNowBarCard.ICON_APP -> {
                sourceGroup.addView(ui.insetDivider());
                String currentApp =
                        editingCard.iconValue.isEmpty()
                                ? str(R.string.nowbar_icon_none_selected)
                                : DiyNowBarIcons.loadAppLabel(this, editingCard.iconValue);
                sourceGroup.addView(
                        ui.prefRow(
                                str(R.string.nowbar_icon_installed_app),
                                currentApp,
                                !editingCard.iconValue.isEmpty(),
                                () ->
                                        showAppPickerDialog(
                                                app -> {
                                                    editingCard.iconType = DiyNowBarCard.ICON_APP;
                                                    editingCard.iconValue = app.packageName();
                                                    refreshPreview();
                                                    renderActiveEditorTab();
                                                })));
            }
            case DiyNowBarCard.ICON_PHOTO -> {
                sourceGroup.addView(ui.insetDivider());
                String currentPhoto =
                        editingCard.iconValue.endsWith(".png")
                                ? str(R.string.nowbar_icon_photo_active)
                                : str(R.string.nowbar_icon_photo_pick);
                sourceGroup.addView(
                        ui.prefRow(
                                str(R.string.nowbar_icon_photo_title),
                                currentPhoto,
                                editingCard.iconValue.endsWith(".png"),
                                this::launchPhotoPicker));
            }
            default -> buildSymbolGrid(sourceGroup);
        }
        host.addView(sourceGroup);

        host.addView(ui.sectionHeader(str(R.string.nowbar_icon_shape_accent), null));
        LinearLayout styleGroup = ui.cardGroup();

        LinearLayout shapeRow = new LinearLayout(this);
        shapeRow.setOrientation(LinearLayout.HORIZONTAL);
        shapeRow.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        TextView sqChip =
                ui.chip(
                        str(R.string.nowbar_icon_shape_squircle),
                        editingCard.iconShape == DiyNowBarCard.SHAPE_SQUIRCLE,
                        () -> {
                            editingCard.iconShape = DiyNowBarCard.SHAPE_SQUIRCLE;
                            refreshPreview();
                            renderActiveEditorTab();
                        });
        TextView ciChip =
                ui.chip(
                        str(R.string.nowbar_icon_shape_circle),
                        editingCard.iconShape == DiyNowBarCard.SHAPE_CIRCLE,
                        () -> {
                            editingCard.iconShape = DiyNowBarCard.SHAPE_CIRCLE;
                            refreshPreview();
                            renderActiveEditorTab();
                        });
        LinearLayout.LayoutParams s1 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        s1.setMarginEnd(ui.dp(8));
        shapeRow.addView(sqChip, s1);
        shapeRow.addView(
                ciChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        styleGroup.addView(shapeRow);

        styleGroup.addView(ui.insetDivider());
        buildColorPalettePicker(
                styleGroup,
                editingCard.accentColor,
                color -> {
                    editingCard.accentColor = color;
                    refreshPreview();
                    renderActiveEditorTab();
                });
        host.addView(styleGroup);
    }

    private void buildSymbolGrid(LinearLayout parent) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(14));
        int cols = 6;
        String[] keys = DiyNowBarCard.SYMBOL_KEYS;
        for (int r = 0; r < keys.length; r += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rlp.topMargin = ui.dp(6);
            for (int c = 0; c < cols; c++) {
                int idx = r + c;
                if (idx >= keys.length) break;
                String sym = keys[idx];
                boolean selected = sym.equals(editingCard.iconValue);
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(
                        DiyNowBarIcons.drawSymbolGlyph(
                                sym, ui.dp(22), selected ? Color.WHITE : ui.primaryText));
                iv.setScaleType(ImageView.ScaleType.CENTER);
                iv.setContentDescription(sym);
                iv.setBackground(
                        ui.roundedRipple(selected ? editingCard.accentColor : ui.pillBg, ui.dp(14)));
                iv.setClickable(true);
                iv.setFocusable(true);
                ui.applyNativeRecoil(iv, false);
                iv.setOnClickListener(
                        v -> {
                            editingCard.iconValue = sym;
                            refreshPreview();
                            renderActiveEditorTab();
                        });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(44), 1f);
                if (c > 0) lp.setMarginStart(ui.dp(6));
                row.addView(iv, lp);
            }
            grid.addView(row, rlp);
        }
        parent.addView(grid);
    }

    private void buildEmojiPicker(LinearLayout parent) {
        parent.addView(ui.insetDivider());
        parent.addView(
                ui.inputBlock(
                        str(R.string.nowbar_icon_emoji_label),
                        str(R.string.nowbar_icon_emoji_hint),
                        editingCard.iconValue,
                        8,
                        false,
                        InputType.TYPE_CLASS_TEXT,
                        s -> {
                            editingCard.iconValue = s;
                            refreshPreview();
                        }));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(14));
        int cols = 6;
        for (int r = 0; r < QUICK_EMOJIS.length; r += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rlp.topMargin = ui.dp(6);
            for (int c = 0; c < cols; c++) {
                int idx = r + c;
                if (idx >= QUICK_EMOJIS.length) break;
                String emoji = QUICK_EMOJIS[idx];
                boolean selected = emoji.equals(editingCard.iconValue);
                TextView tv = new TextView(this);
                tv.setText(emoji);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                tv.setGravity(Gravity.CENTER);
                tv.setBackground(
                        ui.roundedRipple(selected ? editingCard.accentColor : ui.pillBg, ui.dp(14)));
                tv.setClickable(true);
                ui.applyNativeRecoil(tv, false);
                tv.setOnClickListener(
                        v -> {
                            editingCard.iconValue = emoji;
                            refreshPreview();
                            renderActiveEditorTab();
                        });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(44), 1f);
                if (c > 0) lp.setMarginStart(ui.dp(6));
                row.addView(tv, lp);
            }
            grid.addView(row, rlp);
        }
        parent.addView(grid);
    }

    private void launchPhotoPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        try {
            startActivityForResult(pick, REQUEST_PICK_PHOTO);
        } catch (RuntimeException error) {
            Toast.makeText(this, str(R.string.nowbar_icon_photo_picker_failed), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_PHOTO
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null
                && editingCard != null) {
            Uri uri = data.getData();
            worker.execute(
                    () -> {
                        try {
                            String fileName = DiyNowBarStore.importPhotoIcon(this, uri);
                            runOnUiThread(
                                    () -> {
                                        if (isDestroyed() || editingCard == null) return;
                                        editingCard.iconType = DiyNowBarCard.ICON_PHOTO;
                                        editingCard.iconValue = fileName;
                                        refreshPreview();
                                        renderActiveEditorTab();
                                    });
                        } catch (Exception error) {
                            runOnUiThread(
                                    () ->
                                            Toast.makeText(
                                                            this,
                                                            str(
                                                                    R.string.nowbar_icon_photo_import_failed,
                                                                    error.getMessage() == null
                                                                            ? ""
                                                                            : error.getMessage()),
                                                            Toast.LENGTH_SHORT)
                                                    .show());
                        }
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Tab 2: Effects & Shaders
    // -------------------------------------------------------------------------

    private void buildEffectsTab(LinearLayout host) {
        LinearLayout fxGroup = ui.cardGroup();
        for (int i = 0; i < DiyNowBarCard.EFFECT_NAMES.length; i++) {
            final int fx = i;
            boolean selected = editingCard.effect == fx;
            if (i > 0) fxGroup.addView(ui.insetDivider());
            LinearLayout row =
                    ui.prefRow(
                            DiyNowBarCard.effectName(this, i),
                            effectSummary(i),
                            selected,
                            () -> {
                                editingCard.effect = fx;
                                refreshPreview();
                                renderActiveEditorTab();
                            });
            android.widget.RadioButton rb = ui.createRadioButton(selected);
            LinearLayout.LayoutParams rlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMarginEnd(ui.dp(10));
            row.addView(rb, 0, rlp);
            fxGroup.addView(row);
        }
        host.addView(fxGroup);

        if (editingCard.effect >= DiyNowBarCard.EFFECT_AI_PRESENCE) {
            host.addView(ui.sectionHeader(str(R.string.nowbar_shader_presets), null));
            LinearLayout presetGroup = ui.cardGroup();
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            for (int r = 0; r < DiyNowBarCard.SHADER_PRESETS.length; r += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                if (r > 0) rlp.topMargin = ui.dp(8);
                for (int c = 0; c < 2; c++) {
                    int idx = r + c;
                    if (idx >= DiyNowBarCard.SHADER_PRESETS.length) break;
                    DiyNowBarCard.ShaderPreset preset = DiyNowBarCard.SHADER_PRESETS[idx];
                    boolean active =
                            editingCard.effectColor1 == preset.color1
                                    && editingCard.effectColor2 == preset.color2;
                    TextView pill = new TextView(this);
                    pill.setText(DiyNowBarCard.shaderPresetName(this, idx));
                    pill.setTextColor(Color.WHITE);
                    pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    pill.setGravity(Gravity.CENTER);
                    pill.setMinHeight(ui.dp(42));
                    GradientDrawable gd =
                            new GradientDrawable(
                                    GradientDrawable.Orientation.LEFT_RIGHT,
                                    new int[] {preset.color1, preset.color2});
                    gd.setCornerRadius(ui.dp(18));
                    if (active) gd.setStroke(ui.dp(2), Color.WHITE);
                    pill.setBackground(gd);
                    pill.setClickable(true);
                    ui.applyNativeRecoil(pill, false);
                    pill.setOnClickListener(
                            v -> {
                                editingCard.effectColor1 = preset.color1;
                                editingCard.effectColor2 = preset.color2;
                                refreshPreview();
                                renderActiveEditorTab();
                            });
                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (c > 0) lp.setMarginStart(ui.dp(8));
                    row.addView(pill, lp);
                }
                grid.addView(row, rlp);
            }
            presetGroup.addView(grid);
            host.addView(presetGroup);

            host.addView(ui.sectionHeader(str(R.string.nowbar_shader_primary_color), null));
            LinearLayout c1Group = ui.cardGroup();
            buildColorPalettePicker(
                    c1Group,
                    editingCard.effectColor1,
                    color -> {
                        editingCard.effectColor1 = color;
                        refreshPreview();
                        renderActiveEditorTab();
                    });
            host.addView(c1Group);

            host.addView(ui.sectionHeader(str(R.string.nowbar_shader_secondary_color), null));
            LinearLayout c2Group = ui.cardGroup();
            buildColorPalettePicker(
                    c2Group,
                    editingCard.effectColor2,
                    color -> {
                        editingCard.effectColor2 = color;
                        refreshPreview();
                        renderActiveEditorTab();
                    });
            host.addView(c2Group);
        }
    }

    // -------------------------------------------------------------------------
    // Tab 3: Actions & Behavior
    // -------------------------------------------------------------------------

    private void buildActionsTab(LinearLayout host) {
        String[] quickActionLabels = quickActionLabels();
        LinearLayout actGroup = ui.cardGroup();
        actGroup.addView(
                ui.prefRow(
                        str(R.string.nowbar_action_collapsed_quick_button),
                        quickActionLabels[editingCard.quickAction],
                        true,
                        () ->
                                new AlertDialog.Builder(this)
                                        .setTitle(str(R.string.nowbar_action_collapsed_quick_button))
                                        .setSingleChoiceItems(
                                                quickActionLabels,
                                                editingCard.quickAction,
                                                (d, which) -> {
                                                    editingCard.quickAction = which;
                                                    d.dismiss();
                                                    refreshPreview();
                                                    renderActiveEditorTab();
                                                })
                                        .setNegativeButton(str(R.string.common_cancel), null)
                                        .show()));
        appendActionTargetRow(
                actGroup,
                editingCard.quickAction,
                editingCard.quickActionTarget,
                target -> {
                    editingCard.quickActionTarget = target;
                    refreshPreview();
                    renderActiveEditorTab();
                },
                target -> editingCard.quickActionTarget = target);

        actGroup.addView(ui.insetDivider());
        int tapIdx = 0;
        for (int i = 0; i < TAP_ACTION_VALUES.length; i++) {
            if (TAP_ACTION_VALUES[i] == editingCard.tapAction) tapIdx = i;
        }
        final int currentTapIdx = tapIdx;
        String[] tapActionLabels = tapActionLabels();
        actGroup.addView(
                ui.prefRow(
                        str(R.string.nowbar_action_expanded_tap),
                        tapActionLabels[currentTapIdx],
                        editingCard.tapAction != DiyNowBarCard.ACTION_NONE,
                        () ->
                                new AlertDialog.Builder(this)
                                        .setTitle(str(R.string.nowbar_action_expanded_tap))
                                        .setSingleChoiceItems(
                                                tapActionLabels,
                                                currentTapIdx,
                                                (d, which) -> {
                                                    editingCard.tapAction = TAP_ACTION_VALUES[which];
                                                    d.dismiss();
                                                    refreshPreview();
                                                    renderActiveEditorTab();
                                                })
                                        .setNegativeButton(str(R.string.common_cancel), null)
                                        .show()));
        appendActionTargetRow(
                actGroup,
                editingCard.tapAction,
                editingCard.tapActionTarget,
                target -> {
                    editingCard.tapActionTarget = target;
                    refreshPreview();
                    renderActiveEditorTab();
                },
                target -> editingCard.tapActionTarget = target);
        host.addView(actGroup);

        host.addView(ui.sectionHeader(str(R.string.nowbar_behavior_section), null));
        LinearLayout behGroup = ui.cardGroup();
        behGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_behavior_expandable),
                        str(R.string.nowbar_behavior_expandable_summary),
                        editingCard.expandable,
                        checked -> {
                            editingCard.expandable = checked;
                            refreshPreview();
                        }));
        behGroup.addView(ui.insetDivider());
        behGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_behavior_fullscreen),
                        str(R.string.nowbar_behavior_fullscreen_summary),
                        editingCard.fullscreenExpand,
                        checked -> editingCard.fullscreenExpand = checked));
        behGroup.addView(ui.insetDivider());
        behGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_behavior_skip_bouncer),
                        str(R.string.nowbar_behavior_skip_bouncer_summary),
                        editingCard.ignoreBouncer,
                        checked -> editingCard.ignoreBouncer = checked));
        behGroup.addView(ui.insetDivider());
        behGroup.addView(
                ui.switchRow(
                        str(R.string.nowbar_behavior_hide_sensitive),
                        str(R.string.nowbar_behavior_hide_sensitive_summary),
                        editingCard.hideOnLock,
                        checked -> editingCard.hideOnLock = checked));
        host.addView(behGroup);
    }

    private void appendActionTargetRow(
            LinearLayout group,
            int action,
            String currentTarget,
            Consumer<String> onAppSelected,
            Consumer<String> onUrlTyped) {
        if (action == DiyNowBarCard.ACTION_OPEN_APP) {
            group.addView(ui.insetDivider());
            String summary =
                    currentTarget == null || currentTarget.isEmpty()
                            ? str(R.string.nowbar_select_app)
                            : DiyNowBarIcons.loadAppLabel(this, currentTarget);
            group.addView(
                    ui.prefRow(
                            str(R.string.nowbar_target_app),
                            summary,
                            currentTarget != null && !currentTarget.isEmpty(),
                            () ->
                                    showAppPickerDialog(
                                            app -> onAppSelected.accept(app.packageName()))));
        } else if (action == DiyNowBarCard.ACTION_OPEN_URL) {
            group.addView(ui.insetDivider());
            group.addView(
                    ui.inputBlock(
                            str(R.string.nowbar_target_url),
                            "https://example.com",
                            currentTarget,
                            240,
                            false,
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                            onUrlTyped));
        }
    }

    // -------------------------------------------------------------------------
    // Shared Pickers & Helpers
    // -------------------------------------------------------------------------

    private void buildColorPalettePicker(
            LinearLayout parent, int currentColor, Consumer<Integer> onPick) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14));

        int cols = 6;
        int[] palette = DiyNowBarCard.ACCENT_PALETTE;
        for (int r = 0; r < palette.length; r += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = ui.dp(10);
            for (int c = 0; c < cols; c++) {
                int idx = r + c;
                if (idx >= palette.length) break;
                int swatchColor = palette[idx];
                boolean selected = (swatchColor & 0xFFFFFF) == (currentColor & 0xFFFFFF);
                FrameLayout cell = new FrameLayout(this);
                View dot = new View(this);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(swatchColor);
                if (selected) bg.setStroke(ui.dp(3), ui.dark ? Color.WHITE : Color.BLACK);
                dot.setBackground(bg);
                dot.setClickable(true);
                ui.applyNativeRecoil(dot, false);
                dot.setOnClickListener(v -> onPick.accept(swatchColor));
                FrameLayout.LayoutParams dlp =
                        new FrameLayout.LayoutParams(ui.dp(36), ui.dp(36), Gravity.CENTER);
                cell.addView(dot, dlp);
                row.addView(
                        cell,
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            wrap.addView(row, rlp);
        }

        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText hexEdit =
                ui.rawInput(
                        "#RRGGBB",
                        DiyNowBarCard.formatHexColor(currentColor),
                        9,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        null);
        LinearLayout.LayoutParams elp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.setMarginEnd(ui.dp(10));
        hexRow.addView(hexEdit, elp);

        hexRow.addView(
                ui.flatPillButton(
                        str(R.string.common_apply),
                        ui.accent,
                        () -> {
                            int parsed =
                                    DiyNowBarCard.parseHexColor(
                                            hexEdit.getText().toString(), 0);
                            if (parsed == 0) {
                                Toast.makeText(
                                                this,
                                                str(R.string.nowbar_hex_color_error),
                                                Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            onPick.accept(parsed);
                        }));
        wrap.addView(hexRow);
        parent.addView(wrap);
    }

    private LinearLayout buildNumberPairRow(
            String label1,
            int val1,
            String label2,
            int val2,
            Consumer<Integer> onVal1,
            Consumer<Integer> onVal2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(12));

        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        TextView l1 = new TextView(this);
        l1.setText(label1);
        l1.setTextColor(ui.secondaryText);
        l1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col1.addView(l1);
        col1.addView(
                ui.rawInput(
                        label1,
                        String.valueOf(val1),
                        7,
                        false,
                        InputType.TYPE_CLASS_NUMBER,
                        s -> onVal1.accept(parseIntSafe(s, val1))));

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        TextView l2 = new TextView(this);
        l2.setText(label2);
        l2.setTextColor(ui.secondaryText);
        l2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col2.addView(l2);
        col2.addView(
                ui.rawInput(
                        label2,
                        String.valueOf(val2),
                        7,
                        false,
                        InputType.TYPE_CLASS_NUMBER,
                        s -> onVal2.accept(parseIntSafe(s, val2))));

        LinearLayout.LayoutParams lp1 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(ui.dp(16));
        row.addView(col1, lp1);
        row.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void promptUrlDialog(String existing, Consumer<String> onOk) {
        EditText edit =
                ui.rawInput(
                        "https://example.com",
                        existing,
                        240,
                        false,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                        null);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(ui.dp(22), ui.dp(12), ui.dp(22), 0);
        wrap.addView(edit);
        new AlertDialog.Builder(this)
                .setTitle(str(R.string.nowbar_enter_url_title))
                .setView(wrap)
                .setPositiveButton(
                        str(R.string.common_save),
                        (d, which) -> {
                            String v = edit.getText().toString().trim();
                            if (!v.isEmpty()) onOk.accept(v);
                        })
                .setNegativeButton(str(R.string.common_cancel), null)
                .show();
    }

    private void showAppPickerDialog(Consumer<AppEntry> onSelect) {
        if (cachedApps != null) {
            presentAppPickerDialog(cachedApps, onSelect);
            return;
        }
        Toast.makeText(this, str(R.string.nowbar_loading_apps), Toast.LENGTH_SHORT).show();
        worker.execute(
                () -> {
                    List<AppEntry> loaded = queryInstalledApps();
                    runOnUiThread(
                            () -> {
                                if (isDestroyed()) return;
                                cachedApps = loaded;
                                presentAppPickerDialog(loaded, onSelect);
                            });
                });
    }

    private List<AppEntry> queryInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        Map<String, AppEntry> dedup = new LinkedHashMap<>();
        for (ResolveInfo ri : resolved) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || dedup.containsKey(pkg)) continue;
            CharSequence labelCs = ri.loadLabel(pm);
            String label = labelCs == null ? pkg : labelCs.toString();
            Drawable icon = null;
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable ignored) {
            }
            dedup.put(pkg, new AppEntry(label, pkg, icon));
        }
        List<AppEntry> list = new ArrayList<>(dedup.values());
        list.sort(Comparator.comparing(e -> e.label().toLowerCase(Locale.ROOT)));
        return list;
    }

    private void presentAppPickerDialog(List<AppEntry> apps, Consumer<AppEntry> onSelect) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8));

        EditText search =
                ui.rawInput(
                        str(R.string.nowbar_search_apps_hint),
                        "",
                        60,
                        false,
                        InputType.TYPE_CLASS_TEXT,
                        null);
        content.addView(search);

        ScrollView scroll = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout);
        content.addView(
                scroll,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(340)));

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(str(R.string.nowbar_choose_app_title))
                        .setView(content)
                        .setNegativeButton(str(R.string.common_cancel), null)
                        .create();

        Runnable populate =
                () -> {
                    listLayout.removeAllViews();
                    String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                    for (AppEntry app : apps) {
                        if (!q.isEmpty()
                                && !app.label().toLowerCase(Locale.ROOT).contains(q)
                                && !app.packageName().toLowerCase(Locale.ROOT).contains(q)) {
                            continue;
                        }
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
                        row.setBackground(ui.listRowRecoil());
                        row.setClickable(true);
                        ui.applyRecoil(row, "sesl_recoil_list_selector", 0.99f);
                        row.setOnClickListener(
                                v -> {
                                    dialog.dismiss();
                                    onSelect.accept(app);
                                });

                        ImageView iv = new ImageView(this);
                        if (app.icon() != null) iv.setImageDrawable(app.icon());
                        row.addView(iv, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

                        TextView tv = new TextView(this);
                        tv.setText(app.label());
                        tv.setTextColor(ui.primaryText);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                        tv.setSingleLine(true);
                        tv.setEllipsize(TextUtils.TruncateAt.END);
                        LinearLayout.LayoutParams tlp =
                                new LinearLayout.LayoutParams(
                                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        tlp.setMarginStart(ui.dp(12));
                        row.addView(tv, tlp);
                        listLayout.addView(row);
                    }
                };
        populate.run();
        search.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        populate.run();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
        dialog.show();
    }

    private static int parseIntSafe(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
