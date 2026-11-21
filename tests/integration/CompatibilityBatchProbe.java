package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/** Read-only compatibility audit across Mods #1-#9. Each group runs independently. */
public final class CompatibilityBatchProbe {
    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }

    static final class Audit {
        int passed;
        int failed;
        int skipped;
        final List<String> lines = new ArrayList<>();

        void check(String label, Check action) {
            try {
                action.run();
                passed++;
                lines.add("PASS " + label);
            } catch (Throwable error) {
                failed++;
                Throwable cause = error;
                while (cause.getCause() != null && cause.getCause() != cause) {
                    cause = cause.getCause();
                }
                lines.add("FAIL " + label + ": " + cause);
                StackTraceElement[] stack = cause.getStackTrace();
                for (int i = 0; i < Math.min(2, stack.length); i++) {
                    lines.add("  " + stack[i]);
                }
            }
        }

        void skip(String label, String reason) {
            skipped++;
            lines.add("SKIP " + label + " (" + reason + ")");
        }
    }

    private CompatibilityBatchProbe() {}

    static Audit auditEditor(ClassLoader editor, long editorVersion) {
        Audit audit = new Audit();
        boolean legacyEditor = editorVersion < 300000000L;
        try {
            DexSignatures.cached(() -> {
                audit.check(
                        "editor clock panel",
                        () -> {
                            EditorBinding binding = EditorBinding.resolve(editor, editorVersion);
                            if (!binding.supportsPosition()) {
                                throw new NoSuchMethodException("Advanced clock size and position");
                            }
                        });
                audit.check(
                        "editor native tabs",
                        () -> NativeTabs.forType(
                                load(editor, "com.google.android.material.tabs.TabLayout")));
                audit.check("editor final save", () -> new EditorSaveCompletion(editor));
                audit.check(
                        "editor wallpaper source",
                        () -> new WallpaperSourceBinding(editor, legacyEditor));
                audit.check("editor depth SDK/crop", () -> DepthBinding.validateEditor(editor));
                audit.check(
                        "editor depth ArcSoft SDK",
                        () -> new ArcSoftEngineBinding(load(
                                editor,
                                "com.samsung.android.app.sdk.deepsky.objectcapture.impl.ArcSoftObjectCaptureImpl")));
                audit.check(
                        "editor weather preview",
                        () -> WeatherPreviewBinding.resolve(
                                editor,
                                load(
                                        editor,
                                        "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView"),
                                load(
                                        editor,
                                        "com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather.WeatherSurfaceView"),
                                legacyEditor));
                audit.check(
                        "editor configuration callback",
                        () -> NativeContract.method(
                                load(
                                        editor,
                                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity"),
                                "onConfigurationChanged",
                                void.class,
                                Configuration.class));
                audit.check(
                        "editor stretch setters",
                        () -> {
                            Class<?> type = load(
                                    editor,
                                    "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockStretchData");
                            NativeContract.method(type, "setFontHeight", void.class, List.class);
                            NativeContract.method(
                                    type, "setFontHeightRects", void.class, List.class);
                        });
                if (legacyEditor) {
                    audit.check("editor widget toolbar/preview/metrics", () -> {
                        var widgets = new WidgetEditorBinding(editor);
                        if (widgets.earlyPanel != null) {
                            audit.check("8.0 editor custom grid", () ->
                                    new OneUi85WidgetLayout(
                                            editor, widgets.create.getDeclaringClass(), true));
                            audit.check("8.0 editor widget catalog", () ->
                                    OneUi80WidgetPanel.catalog(widgets.create.getDeclaringClass()));
                        }
                    });
                    if (editorVersion >= 296000000L) {
                        audit.check(
                                "editor widget rotation/restore",
                                () -> {
                                    WidgetEditorBinding widgets = new WidgetEditorBinding(editor);
                                    new OneUi85WidgetLayout(
                                            editor, widgets.create.getDeclaringClass());
                                });
                    }
                    audit.check(
                            "editor widget resize adapter",
                            () -> OneUi85WidgetEditor.adapter(editor));
                    audit.check(
                            "editor widget menu item",
                            () -> NativeContract.findClass(
                                    editor,
                                    type -> NativeContract.hasConstructor(
                                            type,
                                            String.class,
                                            int.class,
                                            Runnable.class,
                                            String.class)));
                } else {
                    audit.check("9.0 widget guide", () -> OneUi9WidgetEditor.guide(editor));
                    audit.check("9.0 widget adapter", () -> OneUi9WidgetEditor.adapter(editor));
                    audit.check("9.0 widget toolbar", () -> OneUi9WidgetEditor.toolbar(editor));
                    audit.check("9.0 widget create", () -> OneUi9WidgetEditor.create(editor));
                    audit.check("modern widget grid and cell measurements", () -> {
                        Class<?> align = load(editor,
                                "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType");
                        String prefix = "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.";
                        Class<?> grid = load(editor, prefix + "WidgetGridData");
                        Class<?> size = load(editor, prefix + "WidgetSizeData");
                        OneUi9WidgetEditor.gridMetrics(editor, grid, align);
                        OneUi9WidgetEditor.cellMetrics(editor, grid, size);
                    });
                    audit.check(
                            "9.0 widget restore & store wiper",
                            () -> {
                                Method restore = OneUi9WidgetEditor.restore(editor);
                                Class<?> storeClass = restore.getDeclaringClass();
                                Class<?> widgetDataType = load(
                                        editor,
                                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData");
                                Class<?> profileDataType = load(
                                        editor,
                                        "com.samsung.android.lockscreen.editor.item.domain.entity.profile.ProfileData");
                                WidgetWiper.resolveStoreSaveMethod(
                                        storeClass, widgetDataType, profileDataType);
                                WidgetWiper.resolveStoreNotifyOnPauseMethod(storeClass);
                            });
                    audit.check(
                            "9.0 widget lifecycle & picker",
                            () -> {
                                OneUi9WidgetEditor.lifecycle(editor);
                                OneUi9WidgetEditor.pickerScope(editor);
                            });
                }
                return null;
            });
        } catch (Exception error) {
            audit.check("editor DEX cache", () -> {
                throw error;
            });
        }
        return audit;
    }

    static Audit auditProvider(ClassLoader provider) {
        Audit audit = new Audit();
        try {
            DexSignatures.cached(() -> {
                Class<?> base = load(
                        provider, "com.samsung.android.uniform.widget.clock.AbsClockView");
                audit.check("provider clock renderer/height/identity", () -> ClockBinding.of(base));
                ClockBinding family = ClockBinding.of(base);
                if (!family.clockStyle && !family.legacy) {
                    audit.check(
                            "provider fold profile",
                            () -> new FoldProfileBinding(provider));
                }
                audit.check("provider clock glass", () -> ClockGlassBinding.of(base));
                audit.check("provider glyph outline", () -> GlyphOutlineBinding.resolve(provider));
                if (!family.clockStyle) {
                    audit.check("provider native clock blur", () -> ClockBlurBinding.resolve(base));
                    audit.check(
                            "provider clock blur retry",
                            () -> {
                                ClockBlurRetryBinding blur = ClockBlurRetryBinding.of(base);
                                if (!family.legacy && blur.task == null)
                                    OneUi95BlurHost.resolve(blur);
                            });
                    audit.check(
                            "provider widget blur submissions",
                            () -> {
                                if (family.legacy) OneUi85AodBlur.submissions(provider);
                                else OneUi9AodBlur.submissions(provider);
                            });
                }
                audit.check("provider font axes", () -> FontBinding.of(base));
                audit.check(
                        "provider source/measurement callers",
                        () -> {
                            ClockSourceBinding source = new ClockSourceBinding(provider, family);
                            if (!family.clockStyle && source.wallpaper == null) {
                                OneUi95ClockPlacement.resolve(provider);
                                OneUi95ClockPlacement.detachedClock(provider);
                                boolean measuresPage = false;
                                String anchor = "captureClockContainerRealHeight";
                                for (Method method : source.callers) {
                                    if (method.getDeclaringClass().getSimpleName()
                                            .equals("FaceWidgetClockPage")
                                            && DexSignatures.body(method, Set.of(anchor))
                                                    .anchors().containsKey(anchor)) {
                                        measuresPage = true;
                                        break;
                                    }
                                }
                                if (!measuresPage) {
                                    throw new NoSuchMethodException("Clock page height measurement");
                                }
                            }
                        });
                if (!family.legacy && !family.clockStyle
                        && ClockBlurRetryBinding.of(base).task == null) {
                    audit.check("beta media collapse state", () -> {
                        Class<?> card = load(provider,
                                "com.samsung.android.app.facewidget.presentation.NowBarCardViewModel");
                        OneUi95MediaCollapse.mediaProvider(card, OneUi95MediaCollapse.viewModel(provider));
                        OneUi95MediaState.resolve(provider);
                        Class<?> page = load(provider,
                                "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage");
                        String clock = DexSignatures.descriptor(base);
                        var observer = OneUi95Editor.unique(DexSignatures.find(provider,
                                        Set.of(OneUi95MediaState.PAGE_LOG)).stream()
                                .filter(body -> body.calls().stream().anyMatch(call ->
                                        call.owner().equals(clock) && call.name().equals("setMediaExpanded")))
                                .toList(), "clock page media observer");
                        OneUi95MediaState.pageExpanded(observer, DexSignatures.descriptor(page),
                                clock, provider);
                    });
                    audit.check("beta widget layout completion and active clock", () -> {
                        Class<?> widget = load(provider,
                                "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController");
                        OneUi95WidgetRuntime.layout(widget);
                        OneUi95WidgetRuntime.currentClock(load(provider, OneUi95WidgetRuntime.CLOCK_PAGE));
                    });
                    audit.check("beta directional AOD widget blur", () -> {
                        String prefix = "com.samsung.android.app.facewidget.complication.";
                        Class<?> host = load(provider, prefix + "ComplicationWidgetHostViewContainer");
                        Method refresh = NativeContract.method(host, "refreshBlur", void.class, float.class);
                        Class<?> curves = load(provider, prefix + "WidgetBlurTypeKt");
                        Class<?> preset = load(provider, prefix + "WidgetBlurType");
                        if (AodWidgetAppearance.directionalCurves(refresh, curves, preset).size() != 2)
                            throw new NoSuchMethodException("Directional widget blur and tint");
                    });
                }
                audit.check("provider glyph font cache", () -> GlyphFontCache.resolve(base));
                audit.check(
                        "provider inline clock seconds",
                        () -> {
                            ClockSecondsBinding.resolve(base);
                            if (family.early) OneUi80ClockSeconds.dashboard(provider);
                        });
                if (!family.legacy) {
                    audit.check("provider clock transition", () -> ClockTransitionBinding.of(base));
                }
                audit.check(
                        "provider date effects",
                        () -> {
                            base.getMethod("setDateVisibility", int.class);
                            base.getMethod("setShowWeatherInformation", boolean.class);
                        });
                if (!family.clockStyle) {
                    audit.check(
                            "provider widget host sizing",
                            () -> WidgetHostSizing.validate(provider));
                    audit.check(
                            "provider grid refresh/options",
                            () -> WidgetEngine.validateLegacyGrid(
                                    load(provider, WidgetEngine.GRID)));
                    audit.check(
                            "provider widget background decorator",
                            () -> {
                                Class<?> decorator = load(
                                        provider,
                                        "com.samsung.android.app.facewidget.complication.ComplicationWidgetBackgroundDecorator");
                                Class<?> widget = load(
                                        provider,
                                        "com.samsung.android.clockpack.plugins.appwidget.SharedWidgetInfo");
                                NativeContract.field(decorator, "context", Context.class);
                                NativeContract.method(
                                        decorator, "notSupportBlurBg", boolean.class, widget);
                                NativeContract.method(
                                        decorator, "getAODDimColor", Integer.class, String.class);
                            });
                }
                if (family.legacy) {
                    audit.check(
                            "provider widget lifecycle",
                            () -> {
                                Class<?> controller = load(provider,
                                        "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController");
                                if (family.early) OneUi80WidgetRuntime.updates(controller);
                                else RuntimeWidgetHooks.legacyUpdates(controller);
                            });
                }
                if (family.early)
                    audit.check("8.0 custom Now Bar filter", () -> OneUi80NowBarText.filter(provider));
                if (family.early) {
                    audit.check("8.0 widget host scale", () -> {
                        OneUi80WidgetRuntime.addHost(provider);
                        WidgetHostSizing.validateResize(provider);
                    });
                    audit.check("8.0 widget/Now Bar blur submission",
                            () -> OneUi80WidgetRuntime.blurSubmissions(provider));
                    audit.check("8.0 provider grid container",
                            () -> OneUi80WidgetRuntime.grid(load(provider, WidgetEngine.GRID)));
                    audit.check("8.0 native media clock",
                            () -> {
                                OneUi95MediaState.resolve(provider);
                                OneUi80MediaHooks.clockField(provider, base);
                            });
                }
                audit.check("provider Now Bar stack", () -> NowBarStackBinding.of(base));
                audit.check(
                        "provider Now Bar glass",
                        () -> {
                            Class<?> media = load(
                                    provider,
                                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView");
                            Class<?> card = media.getSuperclass();
                            Class<?> brief = load(
                                    provider,
                                    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView");
                            NativeContract.method(card, "getDarkAmount", float.class);
                            card.getMethod("getBlurContainer");
                            NowBarGlass.resolveBriefBlurMethods(brief, card, provider);
                            if (!family.legacy && !family.clockStyle) {
                                NowBarGlass.validatePillBackground(media, load(provider,
                                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem"));
                            }
                        });
                if (!family.legacy) {
                    audit.check(
                            "provider Now Brief GenUI StyleBox blur",
                            () -> {
                                Class<?> style = load(provider,
                                        "com.samsung.android.genui.model.style.BaseStyle");
                                NowBriefFill.resolveStyleBoxUpdateBlur(provider, style);
                                try {
                                    load(provider, "com.samsung.android.genui.kit.style.GenUIStyleKit");
                                } catch (ClassNotFoundException beta) {
                                    new OneUi95BriefFill(provider, load(provider,
                                            "com.samsung.android.genui.model.GenUINode"), style);
                                }
                            });
                }
                if (family.legacy && !family.clockStyle) {
                    audit.check("provider media depth", () -> new OneUi85MediaBinding(base));
                }
                if (family.early || (family.legacy && !family.clockStyle)) {
                    audit.check(
                            "provider full-screen artwork",
                            () -> {
                                var binding = new OneUi85ArtworkBinding(base);
                                if (family.early && binding.sourceArtwork == null)
                                    throw new NoSuchFieldException("8.0 original artwork bitmap");
                            });
                    audit.check(
                            "provider wallpaper fade",
                            () -> new OneUi85WallpaperFadeBinding(base));
                } else if (!family.legacy && !family.clockStyle) {
                    audit.check(
                            "9.0 media coordinator",
                            () -> OneUi9MediaBinding.coordinatorContract(provider));
                    audit.check(
                            "9.0 media holder",
                            () -> OneUi9MediaBinding.holderContract(provider));
                    audit.check(
                            "9.0 media pill bounds",
                            () -> OneUi9MediaBinding.pillBounds(provider));
                    audit.check(
                            "9.0 media animator update",
                            () -> OneUi9MediaBinding.animatorUpdate(provider));
                    audit.check(
                            "9.0 media card contract",
                            () -> OneUi9MediaBinding.cardContract(provider));
                    audit.check(
                            "9.0 media lighting contract",
                            () -> {
                                Class<?> lighting = load(
                                        provider,
                                        "com.samsung.android.uniform.nowbar.MediaLightingView");
                                Method resolve = NativeArtworkReveal.class.getDeclaredMethod(
                                        "resolveLightingContract", Class.class);
                                resolve.setAccessible(true);
                                if (resolve.invoke(null, lighting) == null) {
                                    throw new IllegalStateException("Null LightingContract");
                                }
                            });
                }
                audit.check(
                        "provider media clock contrast outline",
                        () -> {
                            Class<?> textView = load(
                                    provider,
                                    "com.samsung.android.uniform.widget.ClockNonePaddingTextView");
                            try {
                                MediaClockContrast.resolveOutlineField(textView);
                            } catch (NoSuchFieldException ignored) {
                                // Pre-8.9.35 ClockNonePaddingTextView omits outlineColor.
                            }
                        });
                if (!family.clockStyle) {
                    audit.check(
                            "provider depth capability",
                            () -> {
                                String issue = DepthBinding.providerIssue(base);
                                if (issue != null) throw new IllegalStateException(issue);
                            });
                }
                audit.check(
                        "provider unlock blur & Now Bar cards",
                        () -> {
                            Class<?> brief = load(
                                    provider,
                                    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView");
                            Class<?> media = load(
                                    provider,
                                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView");
                            if (!BlurAppearance.isNowBarCardType(brief)
                                    || !BlurAppearance.isNowBarCardType(media)) {
                                throw new IllegalStateException("NowBar card type rejected");
                            }
                            load(
                                    provider,
                                    "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer");
                            Class<?> alphaListener = load(
                                    provider,
                                    "com.android.systemui.plugins.keyguardstatusview.PluginKeyguardStatusViewAlphaChangeListener");
                            if (!hasDexSubType(
                                    provider,
                                    "com.samsung.android.app.facewidget.pages.clock.blur.ClockBlurController$",
                                    alphaListener)) {
                                throw new ClassNotFoundException(
                                        "Missing ClockBlurController$ alpha listener");
                            }
                        });
                if (!family.clockStyle) {
                    String cover = "provider cover-screen Now Bar cards";
                    try {
                        Class<?> card = load(provider,
                                "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarMediaCardView");
                        audit.check(cover, () -> {
                            if (!BlurAppearance.isNowBarCardType(card))
                                throw new IllegalStateException("SubLauncher Now Bar card type rejected");
                        });
                    } catch (ClassNotFoundException absent) {
                        audit.skip(cover, "cover media card absent from this provider APK");
                    }
                }
                return null;
            });
        } catch (Exception error) {
            audit.check("provider base clock lookup", () -> {
                throw error;
            });
        }
        return audit;
    }

    static Audit auditSystemUi(ClassLoader sysui) {
        Audit audit = new Audit();
        try {
            DexSignatures.cached(() -> {
                audit.check(
                        "systemui doze blur regions",
                        () -> {
                            Class<?> host = load(
                                    sysui, "com.android.systemui.statusbar.phone.DozeServiceHost");
                            Field statusBar = host.getDeclaredField("mStatusBarStateController");
                            statusBar.getType().getMethod("isDozing");
                            statusBar.getType().getMethod("getState");
                            NativeContract.method(host, "dozeTimeTick", void.class);
                            NativeContract.method(host, "updateDozing", void.class);
                        });
                audit.check(
                        "systemui notification blur options",
                        () -> {
                            String rows = "com.android.systemui.statusbar.notification.row.";
                            Class<?> background = load(sysui, rows + "ActivatableNotificationView");
                            Class<?> row = load(sysui, rows + "ExpandableNotificationRow");
                            NativeContract.field(background, "mOnKeyguard", boolean.class);
                            Field entry = row.getDeclaredField("mEntryAdapter");
                            entry.getType().getMethod("getSbn");
                            Method eligible = NativeContract.method(
                                    background, "isBlurBackground", boolean.class);
                            NotificationBlurOptions.resolveCustomPolicy(
                                    sysui, row, DexSignatures.body(eligible).calls());
                        });
                audit.check(
                        "systemui unlock blur",
                        () -> {
                            Class<?> wrapper = load(
                                    sysui,
                                    "com.android.systemui.facewidget.plugin.KeyguardStatusViewAlphaChangeControllerWrapper");
                            Class<?> listener = load(
                                    sysui,
                                    "com.android.systemui.plugins.keyguardstatusview.PluginKeyguardStatusViewAlphaChangeListener");
                            wrapper.getDeclaredMethod("registerListener", listener);
                            wrapper.getDeclaredMethod("updateAlpha", float.class);
                            Class<?> move = load(
                                    sysui,
                                    "com.android.systemui.keyguard.animator.ActionMoveHandler");
                            move.getDeclaredMethod("handleMotionEvent", MotionEvent.class);
                            Class<?> animator = load(
                                    sysui,
                                    "com.android.systemui.keyguard.animator.KeyguardTouchAnimator");
                            animator.getDeclaredMethod("onTouchEvent", MotionEvent.class);
                            inheritedField(animator, "isUnlockExecuted");
                        });
                audit.check(
                        "systemui built-in wallpaper motion",
                        () -> {
                            Class<?> image = load(
                                    sysui,
                                    "com.android.systemui.wallpaper.engines.image.ImageEngine");
                            Class<?> animator = load(
                                    sysui,
                                    "com.android.systemui.wallpaper.engines.WallpaperAnimator");
                            Method scale = NativeContract.findMethod(
                                    animator, "scaleSurface", void.class, Rect.class);
                            if (DexSignatures.callers(scale).isEmpty()) {
                                throw new IllegalStateException("No callers of scaleSurface(Rect)");
                            }
                            NativeContract.findField(image, null, animator);
                            NativeContract.findField(animator, null, SurfaceHolder.class);
                            NativeContract.findField(animator, null, SurfaceControl.class);
                            image.getMethod("getWhich");
                            image.getMethod("getSourceWhich");
                            image.getMethod("getAppContext");
                            image.getMethod("getCurrentUserId");
                            image.getDeclaredMethod("onSurfaceRedrawNeeded", SurfaceHolder.class);
                            image.getDeclaredMethod("onWhichChanged", int.class);
                            image.getDeclaredMethod("onSurfaceDestroyed", SurfaceHolder.class);
                            image.getDeclaredMethod("onDestroy");
                        });
                audit.check(
                        "systemui NowBar extras",
                        () -> {
                            Class<?> card = load(
                                    sysui, "com.android.systemui.power.view.ChargerNowBarView");
                            card.getDeclaredMethod("onAttachedToWindow");
                            Class<?> bridge = load(
                                    sysui,
                                    "com.android.systemui.facewidget.plugin.FaceWidgetNotificationControllerWrapper");
                            Class<?> plugin = load(
                                    sysui,
                                    "com.android.systemui.plugins.keyguardstatusview.PluginNotificationController");
                            bridge.getDeclaredMethod("initPlugin", plugin, Context.class);
                            Class<?> ui = null;
                            try {
                                ui = load(
                                        sysui,
                                        "com.android.systemui.power.feature.charginganimation.ChargingAnimationUI");
                            } catch (ClassNotFoundException ignored) {
                            }
                            if (ui != null) {
                                ui.getDeclaredMethod("removeNowBarView");
                                card.getDeclaredMethod("switchAnimationResources", int.class);
                                NativeContract.assignableField(
                                        ui, "forceDismissRunnable", Runnable.class);
                                List<Method> callbacks =
                                        NowBarExtras.resolveEndCallbacks(sysui, ui, card);
                                if (callbacks.size() < 2 || callbacks.size() > 3) {
                                    throw new IllegalStateException(
                                            "Expected 2 or 3 end callbacks, found " + callbacks.size());
                                }
                            } else {
                                Class<?> owner = load(
                                        sysui, "com.android.systemui.power.SecPowerUI");
                                NativeContract.field(owner, "mContext", Context.class);
                                NativeContract.field(owner, "mChargerNowBarView", card);
                                NativeContract.field(
                                        owner, "mCurrentChargingAnimation", String.class);
                                NativeContract.method(owner, "onChargerAnimationEnd", void.class);
                                NativeContract.method(owner, "removeNowBarView", void.class);
                            }
                        });
                return null;
            });
        } catch (Exception error) {
            audit.check("systemui DEX cache", () -> {
                throw error;
            });
        }
        return audit;
    }

    static Audit auditWallpaper(ClassLoader wallpaper) {
        Audit audit = new Audit();
        try {
            DexSignatures.cached(() -> {
                audit.check(
                        "wallpaper live motion",
                        () -> new WallpaperMotionBinding(wallpaper));
                return null;
            });
        } catch (Exception error) {
            audit.check("wallpaper DEX cache", () -> {
                throw error;
            });
        }
        return audit;
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    @SuppressWarnings("deprecation")
    private static boolean hasDexSubType(ClassLoader loader, String prefix, Class<?> superType)
            throws ReflectiveOperationException {
        Object elements = Reflect.field(Reflect.field(loader, "pathList"), "dexElements");
        for (int i = 0; i < Array.getLength(elements); i++) {
            Object dexFile = Reflect.field(Array.get(elements, i), "dexFile");
            if (!(dexFile instanceof dalvik.system.DexFile dex)) continue;
            Enumeration<String> entries = dex.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement();
                if (!name.startsWith(prefix)) continue;
                try {
                    if (superType.isAssignableFrom(load(loader, name))) return true;
                } catch (ClassNotFoundException | LinkageError ignored) {
                }
            }
        }
        return false;
    }

    private static Field inheritedField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void print(Audit source, Audit total) {
        total.passed += source.passed;
        total.failed += source.failed;
        total.skipped += source.skipped;
        for (String line : source.lines) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length < 3 || args.length > 5) {
                throw new IllegalArgumentException(
                        "editor.apk provider.apk editorVersionCode [systemui.apk] [wallpaper.apk]");
            }
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
            ClassLoader parent = ClassLoader.getSystemClassLoader();
            ClassLoader editor = new PathClassLoader(args[0], parent);
            ClassLoader provider = new PathClassLoader(args[1], parent);
            long editorVersion = Long.parseLong(args[2]);
            Audit total = new Audit();
            print(auditEditor(editor, editorVersion), total);
            print(auditProvider(provider), total);
            if (args.length >= 4 && !args[3].isEmpty()) {
                print(auditSystemUi(new PathClassLoader(args[3], parent)), total);
            }
            if (args.length >= 5 && !args[4].isEmpty()) {
                print(auditWallpaper(new PathClassLoader(args[4], parent)), total);
            }
            System.out.println("AUDIT: " + total.passed + " passed, " + total.failed + " failed"
                    + (total.skipped == 0 ? "" : ", " + total.skipped + " skipped"));
            System.exit(total.failed == 0 ? 0 : 2);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
