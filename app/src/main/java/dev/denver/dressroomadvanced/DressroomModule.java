package dev.denver.dressroomadvanced;

import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class DressroomModule extends XposedModule {
    static final String TAG = "DressroomAdvanced";
    final IdentityLocks stretchLocks = new IdentityLocks();
    final java.util.Set<Integer> forceBlurTypes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private EditorBinding editorBinding;
    private final Set<ClassLoader> installing = Collections.newSetFromMap(new WeakHashMap<>());
    private final ThreadLocal<HookTransaction> editorInstallation = new ThreadLocal<>();
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> rendererInstalling =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> rendererHooks = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<Object, Boolean> savedManualRenderers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final java.util.Map<View, android.os.Bundle> editorRendererProfiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private java.lang.ref.WeakReference<Object> manualRenderer =
            new java.lang.ref.WeakReference<>(null);
    private final ThreadLocal<Boolean> manualFontCall = ThreadLocal.withInitial(() -> false);
    private android.content.Context editorContext;
    private android.os.Bundle draftSettings;
    private int editorOrientation, editorScreen;
    private boolean foldEditorObserved;
    private boolean layoutCheckpointed;
    private final java.util.Map<String, android.os.Bundle> layoutClocks =
            new java.util.HashMap<>();
    private final java.util.Map<String, android.os.Bundle> editorDrafts = new java.util.HashMap<>();
    private final Set<String> restoredSelections = new java.util.HashSet<>();
    private final SaveSnapshot<android.os.Bundle> saving = new SaveSnapshot<>();
    private final SaveSnapshot<android.os.Bundle> savingWidgets = new SaveSnapshot<>();
    private final SaveSnapshot<android.os.Bundle> savingExtras = new SaveSnapshot<>();

    private record LegacySave(
            android.content.Context context,
            android.os.Bundle clock,
            android.os.Bundle widgets,
            android.os.Bundle extras) {}

    private final NativeSaveRequests<LegacySave> legacySaves = new NativeSaveRequests<>();
    final EditorExtras editorExtras = new EditorExtras();
    final WidgetEngine widgetEngine = new WidgetEngine(this);
    EditorWidgets editorWidgets;
    private java.lang.ref.WeakReference<NativeClockPanel> panel =
            new java.lang.ref.WeakReference<>(null);
    private String scene = "", processName = "";
    private boolean platformReported;
    private long sessionGeneration;
    private final java.util.Map<View, android.os.Bundle> previewDepthProfiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private RuntimeClockHooks runtimeHooks;
    private DepthAuto depthAuto;
    private boolean legacyDepthReady;
    private WallpaperSourceBinding wallpaperSourceBinding;
    private final DepthSourcePolicy wallpaperSource = new DepthSourcePolicy();
    private java.lang.ref.WeakReference<Object> editorActivity =
            new java.lang.ref.WeakReference<>(null);
    private boolean sourceKnown, sourceImage, sourceSyncing, resetDepthProfiles;
    private int depthSaveHolds;
    private final java.util.concurrent.atomic.AtomicBoolean nativeSaveStarted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private long depthSourceRevision;
    private final android.os.Handler sourceMain =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean sourceCheckQueued =
            new java.util.concurrent.atomic.AtomicBoolean();

    boolean depthImageAvailable() {
        syncWallpaperSource();
        return sourceKnown && sourceImage;
    }

    boolean depthProfilesReset() {
        return resetDepthProfiles;
    }

    private String imageKey() {
        String key = wallpaperSource.key();
        return key == null
                ? ""
                : DepthStorage.hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void syncWallpaperSource() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            if (sourceCheckQueued.compareAndSet(false, true))
                sourceMain.post(
                        () -> {
                            sourceCheckQueued.set(false);
                            syncWallpaperSource();
                        });
            return;
        }
        if (sourceSyncing || wallpaperSourceBinding == null || editorActivity.get() == null) return;
        sourceSyncing = true;
        try {
            WallpaperSourceBinding.Source current =
                    wallpaperSourceBinding.read(editorActivity.get());
            if (current == null) return;
            boolean changed = wallpaperSource.observe(current.key());
            sourceKnown = true;
            sourceImage = current.image();
            if (depthSaveHolds == 0 && (changed || !sourceImage)) {
                if (changed || !resetDepthProfiles)
                    invalidateDepth(
                            changed
                                    ? "Wallpaper image changed. Enable depth for the new image."
                                    : "Depth requires a still-image wallpaper.");
            }
        } catch (Exception error) {
            sourceKnown = true;
            sourceImage = false;
            if (!resetDepthProfiles)
                invalidateDepth("Wallpaper source is unavailable. Depth was cleared.");
            report("Wallpaper source check failed", error);
        } finally {
            sourceSyncing = false;
        }
    }

    void invalidateDepth(String reason) {
        depthSourceRevision++;
        resetDepthProfiles = true;
        try {
            Object owner = activeClockController.get();
            Object renderer = owner == null ? null : editorBinding.renderer(owner);
            android.os.Bundle applied =
                    renderer instanceof View view ? editorRendererProfiles.get(view) : null;
            if ((draftSettings != null && draftSettings.getBoolean("depthOwnsManual"))
                    || (applied != null && applied.getBoolean("depthOwnsManual"))) {
                Object stretch =
                        Reflect.call(
                                Reflect.call(editorBinding.data(owner), "getClockFontData"),
                                "getClockStretchData");
                stretchLocks.remove(stretch);
                setManualRenderer(renderer, false);
                Reflect.call(stretch, "setFontHeight", (Object) null);
                Reflect.call(stretch, "setFontHeightRects", (Object) null);
            }
        } catch (Exception error) {
            report("Depth-owned stretch cleanup unavailable", error);
        }
        if (depthAuto != null) depthAuto.reset();
        DepthEffects.clearEditorSubject();
        editorDrafts.values().forEach(DepthSafety::disable);
        layoutClocks.values().forEach(DepthSafety::disable);
        DepthSafety.disable(draftSettings);
        previewDepthProfiles.clear();
        editorRendererProfiles.clear();
        if (editorWidgets != null) editorWidgets.clearDepth();
        NativeClockPanel current = panel.get();
        if (current != null) current.clearDepth(reason);
        depthStatus(reason);
    }

    boolean depthAvailable(Object clock) {
        return clock != null
                && DepthBinding.provider(clock)
                && (editorBinding == null || !editorBinding.legacy || legacyDepthReady);
    }

    private java.lang.ref.WeakReference<Object> activeClockController =
            new java.lang.ref.WeakReference<>(null);

    void stageSettings(android.content.Context context, android.os.Bundle settings) {
        long revision = depthSourceRevision;
        syncWallpaperSource();
        if (revision != depthSourceRevision || (sourceKnown && !sourceImage))
            DepthSafety.disable(settings);
        if (sourceKnown) settings.putString("depthImageKey", imageKey());
        DepthEffects.sourceMetadata(settings);
        android.content.Context application = context.getApplicationContext();
        if (application != null
                && application.getPackageName().equals("com.samsung.android.app.dressroom"))
            editorContext = application;
        else if (editorContext == null) editorContext = context;
        int orientation = layoutOrientation(context);
        draftSettings = new android.os.Bundle(settings);
        layoutCheckpointed = false;
        draftSettings.putInt(LayoutOrientation.FIELD, orientation);
        draftSettings.putInt(ScreenIdentity.FIELD, editorScreen);
        if (draftSettings.getInt("wallpaperId", -1) < 0)
            draftSettings.putInt("wallpaperId", SettingsBridge.wallpaperId(context));
        draftSettings.putString("scene", scene);
        editorDrafts.put(
                selectionKey(
                        settings.getInt("type"),
                        settings.getInt("font"),
                        settings.getString("fontPath", "")),
                new android.os.Bundle(draftSettings));
        if (depthAuto != null) depthAuto.settings(draftSettings, widgetDepthOn(context));
    }

    private boolean widgetDepthOn(android.content.Context context) {
        if (editorWidgets == null) return false;
        android.os.Bundle settings = editorWidgets.settings(context);
        return settings.getInt("depth") == 1 && WidgetPreferences.covers(settings);
    }

    void detectDepth() {
        if (depthAuto != null) depthAuto.retry();
    }

    void widgetDepthChanged() {
        if (depthAuto != null && editorContext != null) {
            Object owner = activeClockController.get();
            if (owner != null)
                try {
                    Object data = editorBinding.data(owner);
                    android.os.Bundle settings = readSettings(data, editorContext);
                    if (settings == null) settings = new android.os.Bundle();
                    settings.putInt(
                            "type", ((Number) Reflect.call(data, "getClockType")).intValue());
                    settings.putInt(
                            "font",
                            ((Number)
                                            Reflect.call(
                                                    Reflect.call(data, "getClockFontData"),
                                                    "getClockFontType"))
                                    .intValue());
                    depthAuto.settings(settings, widgetDepthOn(editorContext));
                } catch (Exception error) {
                    report("Widget depth detection unavailable", error);
                }
        }
    }

    void depthStatus(String message) {
        NativeClockPanel current = panel.get();
        if (current != null) current.depthStatus(message);
    }

    void depthDetected(View clock, byte[] mask, String message) {
        if (clock == null || !clock.isAttachedToWindow()) return;
        try {
            int type = editorType(clock), font = editorFont(clock, type);
            android.os.Bundle settings = editorSettings(clock.getContext(), type, font);
            if (settings == null) settings = new android.os.Bundle();
            settings.putInt("type", type);
            settings.putInt("font", font);
            settings.putByteArray("depthMask", mask);
            View wallpaper = DepthEffects.wallpaper(clock);
            stageSettings(
                    wallpaper == null ? clock.getContext() : wallpaper.getContext(), settings);
            applyEditorDepth(clock);
            NativeClockPanel current = panel.get();
            if (current != null) current.adoptDepth(settings, message);
            depthStatus(message);
        } catch (Exception error) {
            report("Detected depth could not update the editor", error);
            depthStatus("Could not apply subject: " + error.getMessage());
        }
    }

    String scene() {
        return scene;
    }

    EditorBinding editorBinding() {
        return editorBinding;
    }

    long generation() {
        return sessionGeneration;
    }

    void registerPanel(NativeClockPanel current) {
        panel = new java.lang.ref.WeakReference<>(current);
    }

    int layoutOrientation(android.content.Context context) {
        int orientation = editorOrientation == 0 ? LayoutOrientation.current(context) : editorOrientation;
        if (editorScreen != ScreenIdentity.current()) switchLayout(orientation);
        return orientation;
    }

    private String selectionKey(int type, int font) {
        return selectionKey(type, font, editorFontPath(type, font));
    }

    private String selectionKey(int type, int font, String fontPath) {
        String key = scene + ":" + editorOrientation + ":" + editorScreen + ":" + type + ":" + font;
        return fontPath == null || fontPath.isEmpty()
                ? key
                : key + ":" + Integer.toHexString(fontPath.hashCode());
    }

    android.os.Bundle editorSettings(android.content.Context context, int type, int font) {
        return editorSettings(context, type, font, editorFontPath(type, font));
    }

    android.os.Bundle editorSettings(
            android.content.Context context, int type, int font, String fontPath) {
        android.os.Bundle staged = editorDrafts.get(selectionKey(type, font, fontPath));
        if (staged != null) return new android.os.Bundle(staged);
        android.os.Bundle query = new android.os.Bundle();
        query.putInt("type", type);
        query.putInt("font", font);
        query.putString("scene", scene);
        query.putInt(LayoutOrientation.FIELD, layoutOrientation(context));
        query.putInt(ScreenIdentity.FIELD, editorScreen);
        if (fontPath != null && !fontPath.isEmpty()) query.putString("fontPath", fontPath);
        android.os.Bundle saved =
                SettingsBridge.read(editorContext == null ? context : editorContext, query);
        if (saved != null) {
            boolean replaced =
                    !saved.getString("depthImageKey", "").isEmpty()
                            && sourceKnown
                            && !imageKey().equals(saved.getString("depthImageKey"));
            if (replaced && !resetDepthProfiles)
                invalidateDepth("Wallpaper image changed. Enable depth for the new image.");
            if (resetDepthProfiles || (sourceKnown && !sourceImage) || replaced)
                DepthSafety.disable(saved);
        }
        return saved;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        if (!PlatformGate.supported() && !platformReported) {
            platformReported = true;
            log(
                    Log.WARN,
                    TAG,
                    "Unsupported platform, no hooks installed: " + PlatformGate.description());
        }
    }

    private void beginEditor() {
        sourceKnown = sourceImage = sourceSyncing = resetDepthProfiles = false;
        depthSaveHolds = 0;
        wallpaperSource.reset();
        editorActivity.clear();
        if (depthAuto != null) depthAuto.reset();
        sessionGeneration++;
        draftSettings = null;
        editorContext = null;
        scene = "";
        editorOrientation = editorScreen = 0;
        layoutCheckpointed = false;
        layoutClocks.clear();
        editorDrafts.clear();
        restoredSelections.clear();
        previewDepthProfiles.clear();
        saving.cancel();
        panel.clear();
        manualRenderer.clear();
        savedManualRenderers.clear();
        stretchLocks.clear();
        editorRendererProfiles.clear();
        savingWidgets.cancel();
        savingExtras.cancel();
        editorExtras.reset();
        legacySaves.clear();
        activeClockController.clear();
        if (editorWidgets != null) editorWidgets.reset();
        EditorClockRefresh.cancelAll();
        DateEffects.resetPreviews();
        BlurEffects.resetPreviews();
        DepthEffects.resetEditor();
    }

    private void installCommitHook(ClassLoader loader) throws ReflectiveOperationException {
        wallpaperSourceBinding = new WallpaperSourceBinding(loader, editorBinding.legacy, editorBinding.fold8);
        java.util.ArrayList<Method> sourceChanges = new java.util.ArrayList<>();
        sourceChanges.add(wallpaperSourceBinding.changed);
        for (Method method : wallpaperSourceBinding.dataType.getDeclaredMethods())
            if (java.util.Set.of(
                            "setOriginalUri",
                            "setUri",
                            "setWallpaperSubType",
                            "setWallpaperParentSubType")
                    .contains(method.getName())) sourceChanges.add(method);
        for (Method method : sourceChanges) {
            record(
                    hook(method)
                            .setId("dressroom-advanced.wallpaper-source-change")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        syncWallpaperSource();
                                        return result;
                                    }));
            deoptimize(method);
        }
        Method configuration =
                Class.forName(
                                "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                                false,
                                loader)
                        .getDeclaredMethod(
                                "onConfigurationChanged", android.content.res.Configuration.class);
        record(
                hook(configuration)
                        .setId("dressroom-advanced.freeze-layout-before-rotation")
                        .intercept(
                                chain -> {
                                    int next =
                                            ((android.content.res.Configuration) chain.getArg(0))
                                                    .orientation;
                                    if (next != editorOrientation || editorScreen != ScreenIdentity.current()) checkpointLayout();
                                    Object result = chain.proceed();
                                    Object owner = activeClockController.get();
                                    if (owner != null)
                                        try {
                                            Object renderer = editorBinding.renderer(owner);
                                            if (renderer instanceof View clock)
                                                clock.postOnAnimation(
                                                        () -> applyEditorDepth(clock));
                                        } catch (Exception error) {
                                            report("Rotated editor refresh unavailable", error);
                                        }
                                    return result;
                                }));
        deoptimize(configuration);
        Method created =
                android.app.Activity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
        record(
                hook(created)
                        .setId("dressroom-advanced.new-editor-session")
                        .intercept(
                                chain -> {
                                    if (chain.getThisObject()
                                            .getClass()
                                            .getName()
                                            .equals(
                                                    "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity")) {
                                        beginEditor();
                                        editorActivity =
                                                new java.lang.ref.WeakReference<>(
                                                        chain.getThisObject());
                                    }
                                    return chain.proceed();
                                }));
        Method destroyed = android.app.Activity.class.getDeclaredMethod("onDestroy");
        record(
                hook(destroyed)
                        .setId("dressroom-advanced.close-editor-detection")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (chain.getThisObject()
                                            .getClass()
                                            .getName()
                                            .equals(
                                                    "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity")) {
                                        DepthEffects.resetEditor();
                                        if (depthAuto != null) depthAuto.reset();
                                    }
                                    return result;
                                }));
        deoptimize(destroyed);
        if (editorBinding.legacy) {
            installLegacySave(loader);
            return;
        }
        Class<?> saveController = Class.forName("dc.p", false, loader);
        for (Method request : saveController.getDeclaredMethods()) {
            if (!request.getName().equals("Y") || request.getParameterCount() != 2) continue;
            record(
                    hook(request)
                            .setId("dressroom-advanced.freeze-native-save")
                            .intercept(
                                    chain -> {
                                        nativeSaveStarted.set(false);
                                        syncSaveLayout();
                                        NativeClockPanel current = panel.get();
                                        boolean captured = current != null && current.prepareSave();
                                        saving.begin(
                                                freezeClockLayouts(
                                                        captured
                                                                ? DepthEffects.freezeMask(
                                                                        draftSettings)
                                                                : clockSnapshotWithoutPanel()));
                                        depthSaveHolds++;
                                        try {
                                            savingWidgets.begin(
                                                    editorWidgets == null
                                                            ? null
                                                            : editorWidgets.freeze());
                                            savingExtras.begin(editorExtras.freeze());
                                            Object result = chain.proceed();
                                            if (!nativeSaveStarted.get()) {
                                                releaseSaveHold();
                                                log(
                                                        Log.INFO,
                                                        TAG,
                                                        "Native save skipped, pending snapshots"
                                                                + " released");
                                            }
                                            return result;
                                        } catch (Throwable error) {
                                            releaseSaveHold();
                                            throw error;
                                        }
                                    }));
            deoptimize(request);
        }
        Class<?> saveCallback = Class.forName("androidx.work.impl.utils.c", false, loader);
        java.lang.reflect.Constructor<?> saveStarted =
                saveCallback.getDeclaredConstructor(int.class, Object.class, Object.class);
        record(
                hook(saveStarted)
                        .setId("dressroom-advanced.native-save-start")
                        .intercept(
                                chain -> {
                                    boolean tagged = false, owner = false;
                                    for (Object arg : chain.getArgs()) {
                                        if (arg instanceof Number && ((Number) arg).intValue() == 5)
                                            tagged = true;
                                        else if (arg != null
                                                && arg.getClass().getName().equals("dc.p"))
                                            owner = true;
                                    }
                                    if (tagged && owner) nativeSaveStarted.set(true);
                                    return chain.proceed();
                                }));
        deoptimize(saveStarted);
        Method completed = saveCallback.getDeclaredMethod("invoke");
        record(
                hook(completed)
                        .setId("dressroom-advanced.commit-settings")
                        .intercept(
                                chain -> {
                                    Object callback = chain.getThisObject();
                                    Object result = chain.proceed();
                                    boolean nativeSave = false;
                                    try {
                                        nativeSave =
                                                ((Number) Reflect.field(callback, "a")).intValue()
                                                                == 5
                                                        && Reflect.field(callback, "c")
                                                                .getClass()
                                                                .getName()
                                                                .equals("dc.p");
                                        if (nativeSave) {
                                            android.os.Bundle snapshot = saving.complete();
                                            android.os.Bundle widgets = savingWidgets.complete();
                                            android.os.Bundle extras = savingExtras.complete();
                                            if (extras != null) editorExtras.commit(extras);
                                            if (snapshot != null && editorContext != null) {
                                                stampSavedSource(snapshot);
                                                android.os.Bundle saved =
                                                        SettingsBridge.commit(
                                                                editorContext, snapshot);
                                                if (saved == null || !saved.getBoolean("saved"))
                                                    throw new IllegalStateException(
                                                            "Settings were not saved");
                                                log(
                                                        Log.INFO,
                                                        TAG,
                                                        "Advanced clock settings committed after"
                                                                + " native save");
                                            }
                                            if (widgets != null
                                                    && editorWidgets != null
                                                    && editorWidgets.context() != null) {
                                                WidgetPreferences.commit(
                                                        editorWidgets.context(), widgets);
                                                editorWidgets.committed();
                                                log(
                                                        Log.INFO,
                                                        TAG,
                                                        "Widget settings committed after native"
                                                                + " save");
                                            }
                                        }
                                    } catch (Exception e) {
                                        report("Advanced settings commit failed", e);
                                    } finally {
                                        if (nativeSave)
                                            depthSaveHolds = Math.max(0, depthSaveHolds - 1);
                                    }
                                    return result;
                                }));
    }

    private void releaseSaveHold() {
        depthSaveHolds = Math.max(0, depthSaveHolds - 1);
        saving.cancel();
        savingWidgets.cancel();
        savingExtras.cancel();
    }

    private void preflightLegacySave(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> save =
                Class.forName("com.samsung.android.lockscreen.editor.component.H$e", false, loader);
        NativeContract.field(save, "b", int.class);
        NativeContract.method(save, "invokeSuspend", Object.class, Object.class);
        Class.forName("kotlin.Unit", false, loader).getField("INSTANCE");
    }

    private void installLegacySave(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> save =
                Class.forName("com.samsung.android.lockscreen.editor.component.H$e", false, loader);
        java.lang.reflect.Field label = NativeContract.field(save, "b", int.class);
        Method resume = NativeContract.method(save, "invokeSuspend", Object.class, Object.class);
        Object unit = Class.forName("kotlin.Unit", false, loader).getField("INSTANCE").get(null);
        record(
                hook(resume)
                        .setId("dressroom-advanced.85-native-apply")
                        .intercept(
                                chain -> {
                                    Object request = chain.getThisObject();
                                    if (label.getInt(request) == 0) {
                                        syncSaveLayout();
                                        NativeClockPanel current = panel.get();
                                        boolean captured = current != null && current.prepareSave();
                                        android.os.Bundle snapshot =
                                                freezeClockLayouts(
                                                        captured
                                                                ? DepthEffects.freezeMask(
                                                                        draftSettings)
                                                                : clockSnapshotWithoutPanel());
                                        android.os.Bundle widgets =
                                                editorWidgets == null
                                                        ? null
                                                        : editorWidgets.freeze();
                                        android.content.Context context =
                                                editorContext != null
                                                        ? editorContext
                                                        : editorWidgets == null
                                                                ? null
                                                                : editorWidgets.context();
                                        legacySaves.begin(
                                                request,
                                                new LegacySave(
                                                        context,
                                                        snapshot,
                                                        widgets,
                                                        editorExtras.freeze()));
                                        depthSaveHolds++;
                                    }
                                    try {
                                        Object result = chain.proceed();
                                        if (result == unit) {
                                            LegacySave frozen = legacySaves.complete(request);
                                            if (frozen != null
                                                    && frozen.context() != null
                                                    && frozen.extras() != null) {
                                                try {
                                                    ExtrasPreferences.commit(
                                                            frozen.context(), frozen.extras());
                                                    editorExtras.committed(frozen.extras());
                                                } catch (Exception error) {
                                                    report("8.5 extras commit failed", error);
                                                }
                                            }
                                            if (frozen != null
                                                    && frozen.context() != null
                                                    && frozen.widgets() != null) {
                                                try {
                                                    WidgetPreferences.commit(
                                                            frozen.context(), frozen.widgets());
                                                    if (editorWidgets != null)
                                                        editorWidgets.committed();
                                                } catch (Exception error) {
                                                    report("8.5 widget commit failed", error);
                                                }
                                            }
                                            if (frozen != null
                                                    && frozen.context() != null
                                                    && frozen.clock() != null) {
                                                try {
                                                    stampSavedSource(frozen.clock());
                                                    android.os.Bundle saved =
                                                            SettingsBridge.commit(
                                                                    frozen.context(),
                                                                    frozen.clock());
                                                    if (saved == null || !saved.getBoolean("saved"))
                                                        throw new IllegalStateException(
                                                                "Settings were not saved");
                                                } catch (Exception error) {
                                                    report("8.5 settings commit failed", error);
                                                }
                                            }
                                            if (frozen != null)
                                                depthSaveHolds = Math.max(0, depthSaveHolds - 1);
                                        }
                                        return result;
                                    } catch (Throwable error) {
                                        depthSaveHolds = Math.max(0, depthSaveHolds - 1);
                                        legacySaves.fail(request);
                                        throw error;
                                    }
                                }));
        deoptimize(resume);
    }

    private void record(io.github.libxposed.api.XposedInterface.HookHandle handle) {
        HookTransaction transaction = editorInstallation.get();
        if (transaction != null) transaction.add(handle::unhook);
    }

    private android.os.Bundle clockSnapshotWithoutPanel() {
        Object controller = activeClockController.get();
        if (controller == null) return null;
        try {
            Object data = editorBinding.data(controller);
            int type = ((Number) Reflect.call(data, "getClockType")).intValue();
            int font =
                    ((Number)
                                    Reflect.call(
                                            Reflect.call(data, "getClockFontData"),
                                            "getClockFontType"))
                            .intValue();
            android.os.Bundle snapshot = draftSettings;
            if (snapshot == null
                    || type != snapshot.getInt("type", -1)
                    || font != snapshot.getInt("font", -1))
                snapshot = readSettings(data, editorContext);
            if (snapshot == null
                    || !SettingsScope.sceneMatches(scene, snapshot.getString("scene", "")))
                return null;
            return DepthEffects.freezeMask(snapshot);
        } catch (Exception e) {
            report("Clock profile could not follow native Apply", e);
            return null;
        }
    }

    private android.os.Bundle freezeClockLayouts(android.os.Bundle current) {
        if (current != null) layoutClocks.put(ScreenIdentity.draft(current), current);
        java.util.ArrayList<android.os.Bundle> layouts = new java.util.ArrayList<>();
        for (android.os.Bundle value : layoutClocks.values()) {
            if (SettingsScope.sceneMatches(scene, value.getString("scene", "")))
                layouts.add(new android.os.Bundle(value));
        }
        if (layouts.isEmpty()) return null;
        android.os.Bundle batch = new android.os.Bundle();
        batch.putParcelableArrayList("layoutProfiles", layouts);
        return batch;
    }

    private void checkpointLayout() {
        if (draftSettings != null) {
            android.os.Bundle frozen = DepthEffects.freezeMask(draftSettings);
            layoutClocks.put(ScreenIdentity.draft(frozen), frozen);
            editorDrafts.put(
                    selectionKey(
                            frozen.getInt("type"),
                            frozen.getInt("font"),
                            frozen.getString("fontPath", "")),
                    frozen);
        }
        if (editorWidgets != null) editorWidgets.layoutChanging();
        layoutCheckpointed = true;
    }

    private void syncSaveLayout() {
        try {
            syncWallpaperSource();
            Object owner = activeClockController.get();
            if (owner == null) return;
            switchLayout(editorBinding.orientation(owner));
            if (editorContext != null && editorWidgets != null)
                editorWidgets.settings(editorContext);
        } catch (Exception error) {
            report("Native Apply layout refresh unavailable", error);
        }
    }

    private void stampSavedSource(android.os.Bundle snapshot) {
        syncWallpaperSource();
        java.util.ArrayList<android.os.Bundle> layouts =
                snapshot.getParcelableArrayList("layoutProfiles", android.os.Bundle.class);
        if (layouts != null) {
            for (android.os.Bundle layout : layouts) stampSavedSource(layout);
            return;
        }
        if (sourceKnown) snapshot.putString("depthImageKey", imageKey());
    }

    private void switchLayout(int nextOrientation) {
        int nextScreen = ScreenIdentity.current();
        if (nextOrientation == 0
                || (nextOrientation == editorOrientation && nextScreen == editorScreen)) return;
        if (!layoutCheckpointed) checkpointLayout();
        layoutCheckpointed = false;
        editorOrientation = nextOrientation;
        editorScreen = nextScreen;
        sessionGeneration++;
        draftSettings = null;
        manualRenderer.clear();
        stretchLocks.clear();
        savedManualRenderers.clear();
        editorRendererProfiles.clear();
        previewDepthProfiles.clear();
        DateEffects.resetPreviews();
        BlurEffects.resetPreviews();
        DepthEffects.resetEditor();
        if (depthAuto != null) depthAuto.reset();
    }

    private void installClockBindingHook(ClassLoader loader) throws ReflectiveOperationException {
        Method weather = editorBinding.weather;
        record(
                hook(weather)
                        .setId("dressroom-advanced.native-weather-draft")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    try {
                                        Object model =
                                                Reflect.call(
                                                        Reflect.field(chain.getThisObject(), "a"),
                                                        "getData");
                                        int type =
                                                ((Number) Reflect.call(model, "getClockType"))
                                                        .intValue();
                                        int font =
                                                ((Number)
                                                                Reflect.call(
                                                                        Reflect.call(
                                                                                model,
                                                                                "getClockFontData"),
                                                                        "getClockFontType"))
                                                        .intValue();
                                        if (editorContext != null) {
                                            android.os.Bundle settings =
                                                    editorSettings(editorContext, type, font);
                                            if (settings != null) {
                                                settings.putBoolean(
                                                        "weatherEnabled",
                                                        (Boolean) chain.getArg(0));
                                                stageSettings(editorContext, settings);
                                            }
                                        }
                                    } catch (Exception error) {
                                        report(
                                                "Native weather draft synchronization unavailable",
                                                error);
                                    }
                                    return result;
                                }));
        deoptimize(weather);
        Method bind = editorBinding.bind;
        record(
                hook(bind)
                        .setId("dressroom-advanced.clock-binding")
                        .intercept(
                                chain -> {
                                    Object renderer = chain.getArg(0);
                                    if (renderer instanceof View) {
                                        prepareRenderer(renderer);
                                        DateEffects.attach(this, (View) renderer);
                                    }
                                    Object result = chain.proceed();
                                    if (renderer instanceof View) {
                                        View clock = (View) renderer;
                                        applyEditorDepth(clock);
                                        clock.post(() -> applyEditorDepth(clock));
                                        clock.postDelayed(
                                                () -> {
                                                    if (clock.isAttachedToWindow())
                                                        applyEditorDepth(clock);
                                                },
                                                240);
                                    }
                                    return result;
                                }));
        deoptimize(bind);
        Method create = editorBinding.create;
        record(
                hook(create)
                        .setId("dressroom-advanced.main-clock-model")
                        .intercept(
                                chain -> {
                                    activeClockController =
                                            new java.lang.ref.WeakReference<>(
                                                    chain.getThisObject());
                                    Object data = chain.getArg(1);
                                    android.os.Bundle saved = null;
                                    try {
                                        int nextOrientation =
                                                editorBinding.orientation(chain.getThisObject());
                                        if (nextOrientation == 0
                                                && chain.getArg(0) instanceof View preview)
                                            nextOrientation =
                                                    LayoutOrientation.current(preview.getContext());
                                        switchLayout(nextOrientation);
                                        String nextScene = scene;
                                        try {
                                            Object focused =
                                                    editorBinding.focusedScene(
                                                            chain.getThisObject());
                                            Object id = Reflect.call(focused, "getId");
                                            nextScene = id == null ? "" : String.valueOf(id);
                                        } catch (Exception error) {
                                            report("Wallpaper scene identity unavailable", error);
                                        }
                                        if (!scene.equals(nextScene)) {
                                            layoutClocks.clear();
                                            scene = nextScene;
                                            sessionGeneration++;
                                            manualRenderer.clear();
                                            stretchLocks.clear();
                                            editorRendererProfiles.clear();
                                            DepthEffects.resetEditor();
                                            if (depthAuto != null) depthAuto.reset();
                                        }
                                        Object activity =
                                                editorBinding.activity(chain.getThisObject());
                                        if (activity instanceof android.content.Context) {
                                            editorContext =
                                                    ((android.content.Context) activity)
                                                            .getApplicationContext();
                                            saved =
                                                    readSettings(
                                                            data,
                                                            (android.content.Context) activity);
                                            int type =
                                                    ((Number) Reflect.call(data, "getClockType"))
                                                            .intValue();
                                            int font =
                                                    ((Number)
                                                                    Reflect.call(
                                                                            Reflect.call(
                                                                                    data,
                                                                                    "getClockFontData"),
                                                                            "getClockFontType"))
                                                            .intValue();
                                            if (saved != null
                                                    && saved.containsKey("weatherEnabled")) {
                                                boolean value = saved.getBoolean("weatherEnabled");
                                                editorBinding.restoreWeather(
                                                        chain.getThisObject(), data, value);
                                            }
                                            restoreManualData(data, saved);
                                            if (restoredSelections.add(selectionKey(type, font))) {
                                                restoreShadowData(data, saved);
                                                if (!editorBinding.legacy
                                                        && saved != null
                                                        && saved.getBoolean("forceBlur")) {
                                                    forceBlurTypes.add(type);
                                                    Reflect.call(
                                                            Reflect.call(data, "getClockTypeData"),
                                                            "setSupportTextBlur",
                                                            true);
                                                    Reflect.call(data, "setBlurEnabled", true);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        report("Saved clock data unavailable", e);
                                    }
                                    Object result = chain.proceed();
                                    try {
                                        Object renderer =
                                                editorBinding.renderer(chain.getThisObject());
                                        if (renderer instanceof View) {
                                            View clock = (View) renderer;
                                            int type =
                                                    ((Number) Reflect.call(data, "getClockType"))
                                                            .intValue();
                                            int font =
                                                    ((Number)
                                                                    Reflect.call(
                                                                            Reflect.call(
                                                                                    data,
                                                                                    "getClockFontData"),
                                                                            "getClockFontType"))
                                                            .intValue();
                                            DateEffects.bindIdentity(
                                                    clock,
                                                    type,
                                                    font,
                                                    layoutOrientation(clock.getContext()));
                                            android.os.Bundle draft =
                                                    editorDrafts.get(selectionKey(type, font));
                                            draftSettings =
                                                    draft == null
                                                            ? (saved == null
                                                                    ? null
                                                                    : new android.os.Bundle(saved))
                                                            : new android.os.Bundle(draft);
                                            if (draftSettings == null)
                                                layoutClocks.remove(editorOrientation + ":" + editorScreen);
                                            else
                                                layoutClocks.put(
                                                        editorOrientation + ":" + editorScreen,
                                                        new android.os.Bundle(draftSettings));
                                            if (draft != null
                                                    && draft.getInt("type", -1) == type
                                                    && draft.getInt("font", -1) == font)
                                                DateEffects.preview(
                                                        clock, this, draft.getBoolean("hideDate"));
                                            android.os.Bundle effects =
                                                    draft != null ? draft : saved;
                                            if (effects != null) {
                                                float scale =
                                                        ((Number) Reflect.call(data, "getScale"))
                                                                .floatValue();
                                                if (!Float.isFinite(scale) || scale <= 0)
                                                    scale = 1f;
                                                ShadowEffects.apply(
                                                        clock,
                                                        effects.getBoolean("enabled"),
                                                        effects.getFloat("radius") / scale,
                                                        effects.getFloat("dx") / scale,
                                                        effects.getFloat("dy") / scale,
                                                        effects.getInt("color"));
                                                if (!editorBinding.legacy)
                                                    BlurEffects.preview(
                                                            clock,
                                                            effects.getBoolean("forceBlur")
                                                                    && Boolean.TRUE.equals(
                                                                            Reflect.call(
                                                                                    data,
                                                                                    "getBlurEnabled")),
                                                            effects.getInt("blurRadius", 200),
                                                            effects.getFloat("blurVisibility", -1));
                                                ClockBinding.of(clock).refreshAppearance(clock);
                                            }
                                            android.os.Bundle knownDepth =
                                                    draft != null
                                                                    && draft.getInt("type", -1)
                                                                            == type
                                                                    && draft.getInt("font", -1)
                                                                            == font
                                                            ? draft
                                                            : saved;
                                            if (knownDepth != null)
                                                previewDepthProfiles.put(
                                                        clock, new android.os.Bundle(knownDepth));
                                            applyEditorDepth(clock);
                                            if (effects != null
                                                    && effects.containsKey("weatherEnabled")) {
                                                editorBinding.notifyWeather(chain.getThisObject());
                                            }
                                            if (depthAvailable(clock)
                                                    && effects != null
                                                    && effects.getBoolean("depthEnabled")) {
                                                float previewScale =
                                                        editorBinding.previewScale(
                                                                chain.getThisObject());
                                                android.graphics.Rect clockRect =
                                                        (android.graphics.Rect)
                                                                Reflect.call(
                                                                        data,
                                                                        "getClockPositionRect");
                                                DepthEffects.editorTransform(
                                                        clock, previewScale, clockRect);
                                            }
                                        }
                                    } catch (Exception e) {
                                        report("Main clock identity unavailable", e);
                                    }
                                    return result;
                                }));
        deoptimize(create);
    }

    void restoreManualData(Object data, android.content.Context context) throws Exception {
        restoreManualData(data, readSettings(data, context));
    }

    private android.os.Bundle readSettings(Object data, android.content.Context context)
            throws Exception {
        Object fontData = Reflect.call(data, "getClockFontData");
        return editorSettings(
                context,
                ((Number) Reflect.call(data, "getClockType")).intValue(),
                ((Number) Reflect.call(fontData, "getClockFontType")).intValue());
    }

    private void restoreManualData(Object data, android.os.Bundle saved) throws Exception {
        if (saved == null
                || !FontAxisSettings.nativeStretch(saved)
                || (!saved.getBoolean("manual") && !saved.getBoolean("depthEnabled"))) return;
        if (!Boolean.TRUE.equals(Reflect.call(data, "getStretchable"))) return;
        Object fontData = Reflect.call(data, "getClockFontData");
        Object stretch = Reflect.call(fontData, "getClockStretchData");
        float[] values = saved.getFloatArray("manualHeights");
        List<Float> heights = new java.util.ArrayList<>();
        if (values != null && values.length > 0) for (float value : values) heights.add(value);
        else {
            @SuppressWarnings("unchecked")
            List<Float> current = (List<Float>) Reflect.call(stretch, "getFontHeight");
            if (current != null) heights.addAll(current);
        }
        if (heights.isEmpty()) return;
        stretchLocks.put(stretch, heights);
        Reflect.call(stretch, "setFontHeight", heights);
    }

    private void restoreShadowData(Object data, android.os.Bundle saved) throws Exception {
        if (saved == null) return;
        Object colors = Reflect.call(data, "getClockColorData");
        Object old = Reflect.call(colors, "getClockShadowStyle");
        Object shadow =
                old.getClass()
                        .getConstructor(float.class, float.class, float.class, int.class)
                        .newInstance(
                                saved.getFloat("radius"),
                                saved.getFloat("dx"),
                                saved.getFloat("dy"),
                                saved.getInt("color"));
        Reflect.call(colors, "setClockShadowStyle", shadow);
    }

    void prepareRenderer(Object renderer) {
        if (renderer == null) return;
        ClassLoader loader = renderer.getClass().getClassLoader();
        if (rendererHooks.contains(loader) || !rendererInstalling.add(loader)) return;
        HookTransaction previousInstallation = editorInstallation.get();
        try (HookTransaction transaction = new HookTransaction()) {
            editorInstallation.set(transaction);
            Class<?> base =
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
            ClockBinding binding = ClockBinding.of(base);
            VariableFonts.install(this, base, this::fontSettings);
            DepthEffects.install(this);
            ShadowEffects.install(this);
            if (!binding.legacy) {
                BlurEffects.install(this, base);
                widgetEngine.install(loader);
            } else if (!binding.clockStyle) {
                DepthBlurHooks.install(this, base);
            }
            Class<?> font =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockFontData",
                            false,
                            loader);
            Method normalizer = binding.balance;
            record(
                    hook(normalizer)
                            .setId("dressroom-advanced.independent-segments")
                            .intercept(
                                    chain ->
                                            manualFontCall.get()
                                                    ? chain.getArg(binding.balanceHeights)
                                                    : chain.proceed()));
            Method setter = base.getDeclaredMethod("setFontType", font);
            record(
                    hook(setter)
                            .setId("dressroom-advanced.manual-font-scope")
                            .intercept(
                                    chain -> {
                                        boolean previous = manualFontCall.get();
                                        boolean enabled =
                                                manualRenderer.get() == chain.getThisObject();
                                        int selectedFont = -1;
                                        int selectedType = -1;
                                        Object requested = chain.getArg(0);
                                        if (chain.getThisObject() instanceof View) {
                                            try {
                                                View view = (View) chain.getThisObject();
                                                android.os.Bundle query = new android.os.Bundle();
                                                selectedType =
                                                        ((Number)
                                                                        Reflect.call(
                                                                                view,
                                                                                "getClockType"))
                                                                .intValue();
                                                selectedFont =
                                                        ((Number)
                                                                        Reflect.call(
                                                                                chain.getArg(0),
                                                                                "getFontType"))
                                                                .intValue();
                                                query.putInt("type", selectedType);
                                                query.putInt("font", selectedFont);
                                                android.os.Bundle saved =
                                                        editorSettings(
                                                                view.getContext(),
                                                                selectedType,
                                                                selectedFont,
                                                                String.valueOf(
                                                                        Reflect.call(
                                                                                chain.getArg(0),
                                                                                "getFontPath")));
                                                enabled =
                                                        saved != null
                                                                && FontAxisSettings.nativeStretch(
                                                                        saved)
                                                                && (saved.getBoolean("manual")
                                                                        || saved.getBoolean(
                                                                                "depthEnabled"));
                                                if (saved == null)
                                                    editorRendererProfiles.remove(view);
                                                else editorRendererProfiles.put(view, saved);
                                                VariableFonts.configure(this, view, saved);
                                                if (enabled)
                                                    requested =
                                                            SavedStretch.font(
                                                                    requested,
                                                                    SavedStretch.heights(
                                                                            saved.getFloatArray(
                                                                                    "manualHeights")));
                                            } catch (Exception e) {
                                                report(
                                                        "Saved manual stretch unavailable in"
                                                                + " editor",
                                                        e);
                                            }
                                        }
                                        savedManualRenderers.put(chain.getThisObject(), enabled);
                                        if (chain.getThisObject() instanceof View)
                                            VariableFonts.forget((View) chain.getThisObject());
                                        manualFontCall.set(previous || enabled);
                                        try {
                                            Object result =
                                                    requested == chain.getArg(0)
                                                            ? chain.proceed()
                                                            : chain.proceed(
                                                                    new Object[] {requested});
                                            if (selectedType >= 0
                                                    && selectedFont >= 0
                                                    && chain.getThisObject() instanceof View)
                                                DateEffects.bindIdentity(
                                                        (View) chain.getThisObject(),
                                                        selectedType,
                                                        selectedFont,
                                                        layoutOrientation(
                                                                ((View) chain.getThisObject())
                                                                        .getContext()));
                                            if (chain.getThisObject() instanceof View) {
                                                View clock = (View) chain.getThisObject();
                                                applyEditorDepth(clock);
                                                clock.post(() -> applyEditorDepth(clock));
                                            }
                                            return result;
                                        } finally {
                                            manualFontCall.set(previous);
                                        }
                                    }));
            Method renderFont = binding.render;
            record(
                    hook(renderFont)
                            .setId("dressroom-advanced.editor-depth-manual-render")
                            .intercept(
                                    chain -> {
                                        android.os.Bundle saved =
                                                editorRendererProfiles.get(
                                                        (View) chain.getThisObject());
                                        if (saved == null
                                                || !FontAxisSettings.nativeStretch(saved)
                                                || (!saved.getBoolean("manual")
                                                        && !saved.getBoolean("depthEnabled")))
                                            return chain.proceed();
                                        boolean previous = manualFontCall.get();
                                        manualFontCall.set(true);
                                        try {
                                            Object requested =
                                                    SavedStretch.font(
                                                            chain.getArg(0),
                                                            SavedStretch.heights(
                                                                    saved.getFloatArray(
                                                                            "manualHeights")));
                                            return requested == chain.getArg(0)
                                                    ? chain.proceed()
                                                    : chain.proceed(new Object[] {requested});
                                        } finally {
                                            manualFontCall.set(previous);
                                        }
                                    }));
            deoptimize(renderFont);
            Method validator = base.getDeclaredMethod("isStretchable", List.class);
            record(
                    hook(validator)
                            .setId("dressroom-advanced.manual-stretch-validation")
                            .intercept(
                                    chain -> {
                                        if (manualRenderer.get() != chain.getThisObject()
                                                && !Boolean.TRUE.equals(
                                                        savedManualRenderers.get(
                                                                chain.getThisObject())))
                                            return chain.proceed();
                                        Object values = chain.getArg(0);
                                        if (!(values instanceof List<?>)
                                                || ((List<?>) values).isEmpty()) return false;
                                        for (Object value : (List<?>) values)
                                            if (!(value instanceof Number)
                                                    || !Float.isFinite(
                                                            ((Number) value).floatValue())
                                                    || ((Number) value).floatValue() <= 0)
                                                return false;
                                        return true;
                                    }));
            deoptimize(setter);
            Method attached = base.getDeclaredMethod("onAttachedToWindow");
            record(
                    hook(attached)
                            .setId("dressroom-advanced.editor-date-attach")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (chain.getThisObject() instanceof View) {
                                            DateEffects.attach(this, (View) chain.getThisObject());
                                            applyEditorDepth((View) chain.getThisObject());
                                        }
                                        return result;
                                    }));
            transaction.commit();
            rendererHooks.add(loader);
            log(
                    Log.INFO,
                    TAG,
                    "Provider manual stretch hooks installed: " + renderer.getClass().getName());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            report("Provider independent stretch hooks unavailable", e);
        } finally {
            rendererInstalling.remove(loader);
            if (previousInstallation == null) editorInstallation.remove();
            else editorInstallation.set(previousInstallation);
        }
    }

    private Object editorModel(View clock) throws Exception {
        Object owner = activeClockController.get();
        if (owner != null && editorBinding.renderer(owner) == clock) {
            return editorBinding.data(owner);
        }
        return null;
    }

    private int editorType(View clock) throws Exception {
        Object model = editorModel(clock);
        return ((Number) Reflect.call(model == null ? clock : model, "getClockType")).intValue();
    }

    private int editorFont(View clock, int type) throws Exception {
        Object model = editorModel(clock);
        if (model != null)
            return ((Number)
                            Reflect.call(
                                    Reflect.call(model, "getClockFontData"), "getClockFontType"))
                    .intValue();
        return ClockBinding.of(clock).fontId(clock);
    }

    private String editorFontPath(int type, int font) {
        Object owner = activeClockController.get();
        try {
            if (owner != null && editorBinding != null) {
                Object data = editorBinding.data(owner);
                if (((Number) Reflect.call(data, "getClockType")).intValue() == type) {
                    Object fontData = Reflect.call(data, "getClockFontData");
                    if (((Number) Reflect.call(fontData, "getClockFontType")).intValue() == font) {
                        Object path = Reflect.call(fontData, "getClockFontPath");
                        if (path instanceof String) return (String) path;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        android.os.Bundle draft = draftSettings;
        if (draft != null && draft.getInt("type", -1) == type && draft.getInt("font", -1) == font)
            return draft.getString("fontPath", "");
        return "";
    }

    android.os.Bundle fontSettings(View clock) {
        android.os.Bundle draft = draftSettings;
        if (draft != null) {
            try {
                int type = editorType(clock);
                int font = editorFont(clock, type);
                if (draft.getInt("type", -1) == type && draft.getInt("font", -1) == font) {
                    String path = draft.getString("fontPath", "");
                    Object current = ClockBinding.of(clock).font(clock);
                    Object currentPath =
                            current == null ? null : Reflect.call(current, "getFontPath");
                    if (path.isEmpty()
                            || !(currentPath instanceof String)
                            || path.equals(currentPath)) return draft;
                }
            } catch (Exception ignored) {
            }
        }
        android.os.Bundle saved = editorRendererProfiles.get(clock);
        if (saved == null) return null;
        try {
            String path = saved.getString("fontPath", "");
            Object current = ClockBinding.of(clock).font(clock);
            Object currentPath = current == null ? null : Reflect.call(current, "getFontPath");
            if (!path.isEmpty() && currentPath instanceof String && !path.equals(currentPath))
                return null;
        } catch (Exception ignored) {
        }
        return saved;
    }

    private void applyEditorDepth(View clock) {
        if (editorBinding == null || !depthAvailable(clock) || !DepthEffects.isEditorClock(clock))
            return;
        try {
            syncWallpaperSource();
            Object controller = activeClockController.get();
            if (controller != null) {
                Object selected = editorBinding.renderer(controller);
                if (selected != null && selected != clock) return;
                switchLayout(editorBinding.orientation(controller));
            }
            int type = editorType(clock);
            int font = editorFont(clock, type);
            android.os.Bundle settings = draftSettings;
            if (settings == null
                    || settings.getInt("type", -1) != type
                    || settings.getInt("font", -1) != font) {
                android.os.Bundle query = new android.os.Bundle();
                query.putInt("type", type);
                query.putInt("font", font);
                settings = editorSettings(clock.getContext(), type, font);
            }
            if (settings == null) settings = previewDepthProfiles.get(clock);
            android.os.Bundle previous = editorRendererProfiles.get(clock);
            if (settings == null) editorRendererProfiles.remove(clock);
            else editorRendererProfiles.put(clock, settings);
            VariableFonts.configure(this, clock, settings);
            Object selectedModel = editorModel(clock);
            boolean stretchable =
                    selectedModel != null
                            && Boolean.TRUE.equals(Reflect.call(selectedModel, "getStretchable"));
            if (settings != null
                    && FontAxisSettings.nativeStretch(settings)
                    && stretchable
                    && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))) {
                setManualRenderer(clock, true);
                Object owner = activeClockController.get();
                if (owner != null) {
                    Object model = editorBinding.data(owner);
                    if (((Number) Reflect.call(model, "getClockType")).intValue() == type)
                        restoreManualData(model, settings);
                }
                if (previous == null
                        || !java.util.Arrays.equals(
                                previous.getFloatArray("manualHeights"),
                                settings.getFloatArray("manualHeights"))) {
                    Object fontData = ClockBinding.of(clock).font(clock);
                    if (fontData != null) {
                        ClockBinding.of(clock).render(clock, fontData);
                        clock.requestLayout();
                        clock.invalidate();
                    }
                }
            } else if (manualRenderer.get() == clock) setManualRenderer(clock, false);
            if (editorWidgets != null) editorWidgets.clockProfile(clock.getContext(), settings);
            boolean widgetDepth = widgetDepthOn(clock.getContext());
            android.os.Bundle sourceProfile = settings;
            if (widgetDepth && sourceProfile == null) {
                sourceProfile = new android.os.Bundle();
                sourceProfile.putInt("type", type);
                sourceProfile.putInt("font", font);
            }
            if (widgetDepth
                    && sourceProfile != null
                    && sourceProfile.getByteArray(DepthMetadata.SOURCE) == null) {
                android.os.Bundle widgets = editorWidgets.settings(clock.getContext());
                DepthMetadata.copy(widgets, sourceProfile);
                if (sourceProfile.getByteArray("depthMask") == null)
                    sourceProfile.putByteArray("depthMask", widgets.getByteArray("depthMask"));
            }
            if (depthAuto == null) depthAuto = new DepthAuto(this);
            depthAuto.bind(clock, sourceProfile, widgetDepth);
            DepthEffects.setEditorState(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyEditor(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyEditorDigits(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyWidgets(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"),
                    true);
        } catch (Exception e) {
            report("Editor depth unavailable", e);
        }
    }

    void setManualRenderer(Object renderer, boolean enabled) {
        if (enabled
                && (renderer == null
                        || !rendererHooks.contains(renderer.getClass().getClassLoader())))
            throw new IllegalStateException(
                    "Independent stretching is unavailable for this renderer");
        manualRenderer = new java.lang.ref.WeakReference<>(enabled ? renderer : null);
        if (renderer != null) savedManualRenderers.put(renderer, enabled);
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!PlatformGate.supported()) return;
        if ("com.android.systemui".equals(param.getPackageName())
                || "com.samsung.android.app.aodservice".equals(param.getPackageName())
                || "com.samsung.android.app.clockpack".equals(param.getPackageName())) {
            if (!(processName.equals("com.android.systemui")
                    || processName.startsWith("com.android.systemui:")
                    || processName.equals("com.samsung.android.app.aodservice")
                    || processName.startsWith("com.samsung.android.app.aodservice:")
                    || processName.equals("com.samsung.android.app.clockpack")
                    || processName.startsWith("com.samsung.android.app.clockpack:"))) return;
            if ("com.samsung.android.app.aodservice".equals(param.getPackageName()))
                OneUi9Fold8Profiles.install(this, param.getClassLoader());
            if (runtimeHooks == null) {
                runtimeHooks = new RuntimeClockHooks(this);
                runtimeHooks.install(param.getClassLoader());
            }
            if ("com.android.systemui".equals(param.getPackageName())
                    && processName.equals("com.android.systemui")
                    && android.os.Build.VERSION.SDK_INT >= 36) {
                ClassLoader classes = param.getClassLoader();
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(
                                () -> {
                                    if (android.os.Build.VERSION.SDK_INT >= 37)
                                        MediaFullscreenExtras.installRoot(this, classes);
                                    NowBarExtras.install(this, classes);
                                });
            }
            return;
        }
        if (!"com.samsung.android.app.dressroom".equals(param.getPackageName())) return;
        if (!processName.equals("com.samsung.android.app.dressroom")
                && !processName.startsWith("com.samsung.android.app.dressroom:")) return;
        if (!foldEditorObserved) {
            foldEditorObserved = true;
            ScreenIdentity.observe(() -> sourceMain.post(() -> {
                if (editorContext != null) switchLayout(layoutOrientation(editorContext));
            }));
        }
        ClassLoader loader = param.getClassLoader();
        synchronized (installed) {
            if (installed.contains(loader) || !installing.add(loader)) return;
            try (HookTransaction transaction = new HookTransaction()) {
                editorInstallation.set(transaction);
                editorBinding =
                        EditorBinding.resolve(
                                loader,
                                ((Number)
                                                Reflect.field(
                                                        param.getApplicationInfo(),
                                                        "longVersionCode"))
                                        .longValue());
                Method open = editorBinding.open;
                if (editorBinding.legacy) preflightLegacySave(loader);
                installStretchHooks(loader);
                if (!editorBinding.legacy) {
                    installBlurHooks(loader);
                    EditorWidgets widgets = new EditorWidgets(this);
                    editorWidgets = widgets;
                    try {
                        widgets.install(loader);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        editorWidgets = null;
                        throw error;
                    }
                }
                installCommitHook(loader);
                installClockBindingHook(loader);
                record(
                        hook(open)
                                .setId("dressroom-advanced.clock-panel")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            if (Boolean.TRUE.equals(result)) {
                                                try {
                                                    attach(chain.getThisObject());
                                                } catch (Exception e) {
                                                    report("Advanced tab injection skipped", e);
                                                }
                                            }
                                            return result;
                                        }));
                deoptimize(open);
                transaction.commit();
                installed.add(loader);
                if (editorBinding.legacy || editorBinding.fold8) {
                    try {
                        DepthBinding.validateEditor(loader);
                        // Reference installs tracking through EditorWidgets. Fold8 installs it
                        // here so depth remains independent of widget toolbar support.
                        DepthEffects.watchEditorWallpaper(this, loader, editorBinding.legacy);
                        legacyDepthReady = true;
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        report("Core editor depth source unavailable", error);
                    }
                }
                if (editorBinding.legacy) {
                    try {
                        EditorWidgets candidate = new EditorWidgets(this);
                        candidate.installLegacy(loader);
                        editorWidgets = candidate;
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        report("Optional 8.5 widget editor unavailable", error);
                    }
                }
                log(
                        Log.INFO,
                        TAG,
                        "Editor adapter installed: "
                                + (editorBinding.legacy
                                        ? "Dressroom 2.9.60.50/51 (core controls)"
                                        : editorBinding.fold8 ? "Dressroom 3.0.01.20 (Fold8)" : "Dressroom 3.0.10.38"));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                report("Unsupported Dressroom; clock panel injection rolled back", e);
            } finally {
                editorInstallation.remove();
                installing.remove(loader);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void installStretchHooks(ClassLoader loader) {
        try {
            Class<?> data =
                    Class.forName(
                            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockStretchData",
                            false,
                            loader);
            Method setter = data.getDeclaredMethod("setFontHeight", List.class);
            record(
                    hook(setter)
                            .setId("dressroom-advanced.manual-heights")
                            .intercept(
                                    chain -> {
                                        List<Float> fixed = stretchLocks.get(chain.getThisObject());
                                        return fixed == null
                                                ? chain.proceed()
                                                : chain.proceed(new Object[] {fixed});
                                    }));
            Method rectSetter = data.getDeclaredMethod("setFontHeightRects", List.class);
            record(
                    hook(rectSetter)
                            .setId("dressroom-advanced.manual-rects")
                            .intercept(
                                    chain -> {
                                        return stretchLocks.get(chain.getThisObject()) == null
                                                ? chain.proceed()
                                                : null;
                                    }));
            for (Method m : data.getDeclaredMethods()) {
                if (!m.getName().equals("copy") || m.getReturnType() != data) continue;
                record(
                        hook(m).setId("dressroom-advanced.copy-manual-heights")
                                .intercept(
                                        chain -> {
                                            List<Float> fixed =
                                                    stretchLocks.get(chain.getThisObject());
                                            Object result = chain.proceed();
                                            if (fixed != null && result != null)
                                                stretchLocks.put(result, fixed);
                                            return result;
                                        }));
            }
            for (String[] target :
                    new String[][] {
                        {
                            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockFontData",
                            "setClockFontType",
                            "getClockFontType"
                        },
                        {
                            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockTypeData",
                            "setClockType",
                            "getClockType"
                        }
                    }) {
                Method method =
                        Class.forName(target[0], false, loader)
                                .getDeclaredMethod(target[1], int.class);
                record(
                        hook(method)
                                .setId("dressroom-advanced.invalidate-stretch")
                                .intercept(
                                        chain -> {
                                            boolean changed = true;
                                            try {
                                                changed =
                                                        ((Number)
                                                                                Reflect.call(
                                                                                        chain
                                                                                                .getThisObject(),
                                                                                        target[2]))
                                                                        .intValue()
                                                                != ((Number) chain.getArg(0))
                                                                        .intValue();
                                            } catch (Exception ignored) {
                                            }
                                            if (changed) {
                                                stretchLocks.clear();
                                                manualRenderer.clear();
                                            }
                                            return chain.proceed();
                                        }));
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Manual stretch guard unavailable", e);
        }
    }

    private void installBlurHooks(ClassLoader loader) {
        try {
            Class<?> model =
                    Class.forName(
                            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData",
                            false,
                            loader);
            for (String name : new String[] {"isSupportTextBlurClock", "isBlurEffectEnabled"}) {
                Method method = model.getDeclaredMethod(name);
                record(
                        hook(method)
                                .setId("dressroom-advanced.compatible-blur")
                                .intercept(
                                        chain -> {
                                            int type =
                                                    ((Number)
                                                                    Reflect.call(
                                                                            chain.getThisObject(),
                                                                            "getClockType"))
                                                            .intValue();
                                            if (!forceBlurTypes.contains(type))
                                                return chain.proceed();
                                            return name.equals("isSupportTextBlurClock")
                                                    || Boolean.TRUE.equals(
                                                            Reflect.call(
                                                                    chain.getThisObject(),
                                                                    "getBlurEnabled"));
                                        }));
            }
            Class<?> clockView =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
            Class<?> extension =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.extension.ClockBlurExtension",
                            false,
                            loader);
            Class<?> helper = Class.forName("ah.g", false, loader);
            Method enable = helper.getDeclaredMethod("m", clockView, boolean.class);
            record(
                    hook(enable)
                            .setId("dressroom-advanced.blur-helper")
                            .intercept(
                                    chain -> {
                                        Object view = chain.getArg(0);
                                        if (view != null
                                                && extension.isInstance(view)
                                                && forceBlurTypes.contains(
                                                        ((Number)
                                                                        Reflect.call(
                                                                                view,
                                                                                "getClockType"))
                                                                .intValue())) {
                                            Reflect.call(
                                                    view, "setBlurEffectEnabled", chain.getArg(1));
                                            return null;
                                        }
                                        return chain.proceed();
                                    }));
            Method text = helper.getDeclaredMethod("o", clockView);
            record(
                    hook(text)
                            .setId("dressroom-advanced.text-blur-helper")
                            .intercept(
                                    chain -> {
                                        Object view = chain.getArg(0);
                                        if (view != null
                                                && extension.isInstance(view)
                                                && forceBlurTypes.contains(
                                                        ((Number)
                                                                        Reflect.call(
                                                                                view,
                                                                                "getClockType"))
                                                                .intValue())) {
                                            Reflect.call(view, "setClockTextBlur");
                                            return null;
                                        }
                                        return chain.proceed();
                                    }));
        } catch (ReflectiveOperationException | LinkageError e) {
            report("Compatible blur override unavailable", e);
        }
    }

    private void attach(Object controller) throws ReflectiveOperationException {
        Object value = editorBinding.root(controller);
        if (!(value instanceof LinearLayout))
            throw new IllegalStateException("Unexpected clock root");
        LinearLayout root = (LinearLayout) value;
        Object repo = editorBinding.repo(controller);
        Object model = Reflect.call(repo, "getData");
        if (model == null
                || !model.getClass()
                        .getName()
                        .equals(
                                "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData")) {
            throw new IllegalStateException("Unexpected clock model");
        }
        NativeClockPanel.attach(root, controller, repo, this);
    }

    private static final java.util.Set<String> noted =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void note(String key, Throwable error) {
        if (noted.size() > 256 || !noted.add(key)) return;
        android.util.Log.w(TAG, key, error);
    }

    void report(String message, Throwable error) {
        log(Log.ERROR, TAG, message, error);
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        return false;
    }
}
