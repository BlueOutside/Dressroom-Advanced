package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class EditorWidgets {
    private static final String TAG = "dressroom.advanced.widgets.menu";
    private final DressroomModule module;

    private boolean fold8() {
        return module.editorBinding() != null && module.editorBinding().fold8;
    }

    private Class<?> nativeClass(String name, boolean initialize, ClassLoader loader)
            throws ClassNotFoundException {
        if (fold8()) name = switch (name) {
            case "op.m" -> "lp.l";
            case "sd.q" -> "sd.p";
            case "sd.h" -> "sd.g";
            case "sd.o" -> "sd.n";
            case "sd.m" -> "sd.l";
            case "sd.d" -> "sd.c";
            case "cg.v" -> "cg.u";
            case "rp.e" -> "u2.a";
            default -> name;
        };
        return Class.forName(name, initialize, loader);
    }

    private Object field(Object owner, String name) throws ReflectiveOperationException {
        if (fold8() && owner != null && owner.getClass().getName().equals("lp.l"))
            name = switch (name) {
                case "G" -> "F";
                case "H" -> "G";
                case "L" -> "K";
                default -> name;
            };
        return Reflect.field(owner, name);
    }

    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> ui = new WeakReference<>(null);
    private WeakReference<Context> host = new WeakReference<>(null);
    private Bundle draft, clock;
    private String scene;
    private boolean dirty;
    private int depthLayouts = -1, sharedDepth;
    private final java.util.Map<String, Bundle> layoutDrafts = new java.util.HashMap<>();
    private final java.util.Set<String> dirtyLayouts = new java.util.HashSet<>();
    private final ThreadLocal<Boolean> largePicker = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> nativePreview = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> nativeLifecycle = ThreadLocal.withInitial(() -> false);
    private Bundle dragStart;
    private android.view.MotionEvent dragDown;
    private float dragX, dragY, dragModelX, dragModelY;
    private boolean dragging;
    private boolean dragWasDirty;
    private long lastDrag;
    private WidgetEditorBinding legacyBinding;
    private boolean legacyScaleReady;
    private Method pendingBinding;
    private final WidgetScalePreview scalePreview = new WidgetScalePreview();
    private final RootLayoutWatch previewLayoutWatch = new RootLayoutWatch();

    EditorWidgets(DressroomModule module) {
        this.module = module;
    }

    boolean legacy() {
        return legacyBinding != null;
    }

    boolean stackAvailable() {
        if (!legacy()) return true;
        try {
            NowBarStackBinding.of(
                    nativeClass(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            grid().getClass().getClassLoader()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    boolean depthAvailable() {
        if (!legacy()) return true;
        try {
            return module.depthAvailable(
                    nativeClass(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            grid().getClass().getClassLoader()));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    boolean depthImageAvailable() {
        return module.depthImageAvailable();
    }

    void clearDepth() {
        for (Bundle value : layoutDrafts.values()) {
            DepthSafety.clearMask(value);
            if (value.getInt("depth") == 1) value.putInt("depth", 2);
        }
        if (draft != null) {
            DepthSafety.clearMask(draft);
            if (draft.getInt("depth") == 1) draft.putInt("depth", 2);
        }
        if (sharedDepth == 1) sharedDepth = 2;
        clock = null;
        applyPolicy();
        applyEffects();
    }

    String rowTag(EditorBinding.Row row) {
        return module.editorBinding().rowTag(row);
    }

    void reset() {
        cancelScalePreview();
        previewLayoutWatch.clear();
        owner.clear();
        ui.clear();
        host.clear();
        draft = clock = null;
        scene = null;
        dirty = false;
        layoutDrafts.clear();
        dirtyLayouts.clear();
        depthLayouts = -1;
        sharedDepth = 0;
        dragStart = null;
        dragging = false;
        legacyScaleReady = false;
        if (dragDown != null) {
            dragDown.recycle();
            dragDown = null;
        }
        DepthEffects.setEditorWidgetPolicy(null, null);
    }

    Bundle settings(Context context) {
        Context application = context.getApplicationContext();
        host = new WeakReference<>(application == null ? context : application);
        String current = module.scene();
        int orientation = module.layoutOrientation(context);
        int screen = ScreenIdentity.current();
        if (draft == null
                || !current.equals(scene)
                || draft.getInt(LayoutOrientation.FIELD) != orientation
                || ScreenIdentity.value(draft) != screen) {
            if (!current.equals(scene) || draft == null || ScreenIdentity.value(draft) != screen)
                depthLayouts = -1;
            String key = current + ":" + orientation + ":" + screen;
            Bundle previous = layoutDrafts.get(key);
            draft =
                    previous == null
                            ? WidgetPreferences.read(context, current, orientation)
                            : new Bundle(previous);
            scene = current;
            if (previous == null && module.depthProfilesReset()) {
                DepthSafety.clearMask(draft);
                if (draft.getInt("depth") == 1) draft.putInt("depth", 2);
            }
            dirty = dirtyLayouts.contains(key);
            clock = null;
            if (depthLayouts < 0) adoptDepthPolicy(draft);
            overlayDepthPolicy(draft);
            applyPolicy();
        }
        return draft;
    }

    void layoutChanging() {
        cancelScalePreview();
        if (draft == null) return;
        String key = scene + ":" + ScreenIdentity.draft(draft);
        Bundle frozen = DepthEffects.freezeMask(draft);
        frozen.putString("scene", scene);
        layoutDrafts.put(key, frozen);
        if (dirty) dirtyLayouts.add(key);
    }

    void clockProfile(Context context, Bundle profile) {
        settings(context);
        clock = profile == null ? null : new Bundle(profile);
        if (clock != null
                && clock.getByteArray("depthMask") != null
                && draft.getInt("depth") == 1) {
            draft.putByteArray("depthMask", clock.getByteArray("depthMask").clone());
            DepthMetadata.copy(clock, draft);
        }
        applyPolicy();
        applyEffects();
    }

    private void applyPolicy() {
        if (draft == null) return;
        int mode = draft.getInt("depth");
        Boolean policy = mode == 0 ? null : Boolean.valueOf(mode == 1);
        if (!WidgetPreferences.covers(draft)) policy = Boolean.FALSE;
        DepthEffects.setEditorWidgetPolicy(policy, WidgetPreferences.mask(draft, clock));
    }

    private void adoptDepthPolicy(Bundle value) {
        depthLayouts = value.getInt("depthLayouts");
        sharedDepth = value.getInt("depth");
    }

    private void overlayDepthPolicy(Bundle value) {
        if (depthLayouts < 0 || (draft != null && ScreenIdentity.value(value) != ScreenIdentity.value(draft))) return;
        value.putInt("depthLayouts", depthLayouts);
        if (depthLayouts != 0) value.putInt("depth", sharedDepth);
    }

    Bundle freeze() {
        cancelScalePreview();
        if (draft == null) return null;
        try {
            if (dirty && owner.get() != null) {
                if ((Boolean) Reflect.call(data(), "getWidgetAlignChanging"))
                    throw new IllegalStateException(
                            "Wait for widget rotation to finish before Apply.");
                if (legacy()) legacyBinding.finalizeList(owner.get());
                else Reflect.call(owner.get(), "c");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not finalize Samsung widget positions", e);
        }
        Bundle b = DepthEffects.freezeMask(WidgetPreferences.clean(draft));
        b.putString("scene", module.scene());
        layoutDrafts.put(scene + ":" + ScreenIdentity.draft(b), b);
        java.util.ArrayList<Bundle> layouts = new java.util.ArrayList<>();
        for (var entry : layoutDrafts.entrySet()) {
            if (module.scene().equals(entry.getValue().getString("scene", ""))) {
                Bundle layout = new Bundle(entry.getValue());
                overlayDepthPolicy(layout);
                layouts.add(layout);
            }
        }
        Bundle batch = new Bundle();
        batch.putParcelableArrayList("layoutProfiles", layouts);
        return batch;
    }

    Context context() {
        return host.get();
    }

    EditorExtras extras() {
        return module.editorExtras;
    }

    void committed() {
        dirty = false;
        dirtyLayouts.clear();
    }

    boolean vertical() throws ReflectiveOperationException {
        return (Boolean) Reflect.call(Reflect.call(data(), "getWidgetAlignType"), "isVertical");
    }

    void rotate() throws Exception {
        if (!draft.getBoolean("layout"))
            throw new IllegalStateException("Enable Custom grid to unlock rotation on this phone.");
        Object target =
                WidgetEngine.enumValue(
                        Reflect.call(data(), "getWidgetAlignType").getClass(),
                        vertical() ? "Default" : "Vertical");
        if (!Boolean.TRUE.equals(Reflect.call(field(owner.get(), "y"), "a", target)))
            throw new IllegalStateException("Wait for the current layout change to finish.");
    }

    void orient(Bundle b, boolean vertical) throws ReflectiveOperationException {
        int[] dimensions =
                WidgetLayout.dimensions(
                        b.getInt("columns"), b.getInt("rows"), b.getBoolean("vertical"), vertical);
        int columns = dimensions[0], rows = dimensions[1];
        Object value = Reflect.call(grid(), "getModuleInfo");
        if (value instanceof List<?> items)
            for (Object item : items) {
                columns = Math.max(columns, ((Number) Reflect.call(item, "getWidth")).intValue());
                rows = Math.max(rows, ((Number) Reflect.call(item, "getHeight")).intValue());
            }
        if (vertical) rows = Math.max(rows, columns + 1);
        else columns = Math.max(columns, rows);
        b.putBoolean("vertical", vertical);
        b.putInt("columns", columns);
        b.putInt("rows", rows);
    }

    private Object grid() throws ReflectiveOperationException {
        Object item = owner.get();
        if (item == null) throw new IllegalStateException("Widget preview is not ready");
        if (legacy()) return legacyBinding.grid(item);
        Object wrapper = Reflect.call(field(item, "a"), "get");
        return Reflect.call(wrapper, "d");
    }

    private Object data() throws ReflectiveOperationException {
        if (legacy()) return legacyBinding.data(owner.get());
        return Reflect.call(field(field(owner.get(), "l"), "a"), "getData");
    }

    void update(Bundle next, boolean structural) throws Exception {
        cancelScalePreview();
        Context c = host.get();
        if (c == null || owner.get() == null)
            throw new IllegalStateException("Widget preview is not ready");
        if (!next.containsKey(LayoutOrientation.FIELD) && draft != null)
            LayoutOrientation.copy(draft, next);
        if (!next.containsKey(ScreenIdentity.FIELD) && draft != null)
            ScreenIdentity.copy(draft, next);
        if (ScreenIdentity.value(next) != ScreenIdentity.current())
            throw new IllegalStateException("Reopen widget controls after switching screens.");
        if (next.getInt(LayoutOrientation.FIELD) != module.layoutOrientation(c))
            throw new IllegalStateException("Layout changed. Open Advanced again.");
        next = WidgetPreferences.clean(next);
        if (legacy()) {
            if (structural)
                throw new IllegalArgumentException(
                        "This widget option is not available for this provider yet.");
            if (!legacyScaleReady) throw new IllegalStateException("Widget sizing is not ready.");
            if (WidgetPreferences.covers(next)
                    && next.getInt("depth") == 1
                    && WidgetPreferences.mask(next, clock) == null)
                throw new IllegalStateException(
                        "Detect a wallpaper subject in Clock → Advanced first.");
            Bundle previous = draft;
            boolean previousDirty = dirty;
            draft = next;
            dirty = true;
            adoptDepthPolicy(next);
            try {
                if (previous == null || previous.getFloat("scale", 1) != next.getFloat("scale", 1))
                    module.widgetEngine.refreshChecked(grid());
                recalculate();
                applyPolicy();
                applyEffects();
                module.widgetDepthChanged();
            } catch (Exception error) {
                draft = previous;
                dirty = previousDirty;
                adoptDepthPolicy(previous);
                applyPolicy();
                applyEffects();
                try {
                    module.widgetEngine.refreshChecked(grid());
                    recalculate();
                } catch (Exception rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            }
            return;
        }
        if (structural && !next.getBoolean("layout") && vertical())
            throw new IllegalArgumentException(
                    "Rotate back to Horizontal before restoring the stock phone grid.");
        if (structural
                && next.getBoolean("layout")
                && !WidgetLayout.matchesOrientation(
                        next.getInt("columns"), next.getInt("rows"), vertical()))
            throw new IllegalArgumentException(
                    vertical()
                            ? "A vertical grid needs more rows than columns. Use Rotate to change"
                                    + " arrangement."
                            : "A horizontal grid needs at least as many columns as rows. Use Rotate"
                                    + " to change arrangement.");
        if (structural && !module.widgetEngine.fits(grid(), next))
            throw new IllegalArgumentException(
                    "The current widgets do not fit this grid. Remove a widget or increase"
                            + " rows/columns.");
        if (WidgetPreferences.covers(next) && next.getInt("depth") == 1) {
            byte[] mask = WidgetPreferences.mask(next, clock);
            if (mask == null)
                throw new IllegalStateException(
                        "Detect a wallpaper subject in Clock → Advanced → Depth effect first.");
            next.putByteArray("depthMask", mask.clone());
        }
        Bundle previous = draft;
        boolean wasDirty = dirty;
        draft = next;
        dirty = true;
        adoptDepthPolicy(next);
        try {
            if (structural) {
                Object model = data();
                Reflect.call(model, "setEditableAlign", draft.getBoolean("layout"));
                module.widgetEngine.refreshChecked(grid());
                module.widgetEngine.reflow(grid());
                Reflect.call(
                        field(owner.get(), "p"),
                        "a"); // Refresh native widget list after reflow.
            } else if (previous == null
                    || previous.getFloat("scale", 1) != draft.getFloat("scale", 1)) {
                module.widgetEngine.refreshChecked(grid());
            }
            recalculate();
            applyPolicy();
            applyEffects();
            module.widgetDepthChanged();
        } catch (Exception e) {
            draft = previous;
            dirty = wasDirty;
            adoptDepthPolicy(previous);
            applyPolicy();
            try {
                module.widgetEngine.refresh(grid());
                recalculate();
                applyEffects();
            } catch (Exception rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
    }

    void previewScale(float zoom) throws ReflectiveOperationException {
        Object item = owner.get();
        if (item == null || draft == null) return;
        if (field(item, legacy() ? "N" : "L") instanceof View view)
            scalePreview.show(
                    view, draft.getFloat("scale", 1), WidgetLayout.finite(zoom, 1, .5f, 2));
    }

    void cancelScalePreview() {
        scalePreview.clear();
    }

    void resetAdjustments() throws Exception {
        Bundle b = WidgetPreferences.defaults();
        if (legacy()) {
            update(b, false);
            return;
        }
        b.putBoolean("vertical", false);
        if (!module.widgetEngine.fits(grid(), b))
            throw new IllegalStateException(
                    "The selected widgets need more than the stock 4 × 1 grid.");
        update(b, true);
    }

    private void recalculate() throws ReflectiveOperationException {
        if (legacy()) {
            if (owner.get() != null) legacyBinding.recalculate(owner.get());
            return;
        }
        Object item = owner.get();
        if (item != null) Reflect.call(field(item, "z"), "a");
    }

    void applyEffects() {
        Object item = owner.get();
        if (item == null || draft == null) return;
        try {
            if (legacy()) {
                if (depthAvailable() && field(item, "N") instanceof View root) {
                    DepthEffects.applyEditorWidgets(
                            root,
                            clock != null && clock.getBoolean("depthEnabled"),
                            clock == null ? null : clock.getByteArray("depthMask"));
                    DepthEffects.applyWidgetRoot(
                            root,
                            WidgetPreferences.depth(draft, clock),
                            WidgetPreferences.mask(draft, clock));
                }
                return;
            }
            Object view = field(item, "L");
            if (view instanceof View root)
                DepthEffects.applyEditorWidgets(
                        root,
                        clock != null && clock.getBoolean("depthEnabled"),
                        clock == null ? null : clock.getByteArray("depthMask"));
            Object inner = field(item, "H");
            if (inner instanceof View root) WidgetDepthBlur.blur(root, draft.getBoolean("blurOff"));
        } catch (Exception e) {
            module.report("Widget editor effects unavailable", e);
        }
    }

    private boolean isMain(Object item) {
        if (legacy()) {
            try {
                return legacyBinding.main(item);
            } catch (ReflectiveOperationException error) {
                return false;
            }
        }
        try {
            return (Boolean)
                    Reflect.call(Reflect.call(field(item, "c"), "b"), "isLockScreen");
        } catch (Exception e) {
            return false;
        }
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        if (fold8()) OneUi9Fold8Widgets.validate(loader);
        if (!fold8()) DepthEffects.watchEditorWallpaper(module, loader);
        Class<?> toolbar = nativeClass("v8.y", false, loader);
        Method rebuild = toolbar.getDeclaredMethod("l");
        module.hook(rebuild)
                .setId("dressroom-advanced.widgets-toolbar")
                .intercept(
                        chain -> {
                            try {
                                addMenu(chain.getThisObject(), loader);
                            } catch (Exception e) {
                                module.report("Advanced toolbar item unavailable", e);
                            }
                            return chain.proceed();
                        });
        module.deoptimize(rebuild);
        Class<?> wrapper = nativeClass("pc.h", false, loader);
        Method getGrid = wrapper.getDeclaredMethod("d");
        module.hook(getGrid)
                .setId("dressroom-advanced.editor-widget-provider")
                .intercept(
                        chain -> {
                            Object result = chain.proceed();
                            if (result != null)
                                module.widgetEngine.install(result.getClass().getClassLoader());
                            return result;
                        });
        module.deoptimize(getGrid);
        Method create = nativeClass("op.m", false, loader).getDeclaredMethod("b");
        module.hook(create)
                .setId("dressroom-advanced.widget-editor-binding")
                .intercept(
                        chain -> {
                            Object item = chain.getThisObject();
                            if (isMain(item)) {
                                owner = new WeakReference<>(item);
                                Context c = (Context) field(item, "b");
                                host = new WeakReference<>(c);
                                ui = new WeakReference<>(field(item, "i"));
                                settings(c);
                                restoreModel(field(item, "G"));
                            }
                            Object result;
                            boolean previousPreview = nativePreview.get();
                            nativePreview.set(fold8() && item == owner.get());
                            try {
                                result = chain.proceed();
                            } finally {
                                nativePreview.set(previousPreview);
                            }
                            if (item == owner.get()) {
                                applyEffects();
                                if (result instanceof View view) {
                                    WeakReference<Object> expected = new WeakReference<>(item);
                                    previewLayoutWatch.bind(
                                            view,
                                            (v, l, t, r, b, ol, ot, or, ob) -> {
                                                if (expected.get() == owner.get()) applyEffects();
                                            });
                                }
                            }
                            return result;
                        });
        module.deoptimize(create);
        Class<?> store = nativeClass("sd.q", false, loader);
        for (Method m : store.getDeclaredMethods())
            if (m.getName().equals("b") && m.getParameterCount() == 2) {
                module.hook(m)
                        .setId("dressroom-advanced.restore-widget-draft")
                        .intercept(
                                chain -> {
                                    Object model = chain.proceed();
                                    if (model != null
                                            && (Boolean)
                                                    Reflect.call(chain.getArg(1), "isLockScreen")) {
                                        Context c =
                                                (Context) field(chain.getThisObject(), "a");
                                        settings(c);
                                        restoreModel(model);
                                    }
                                    return model;
                                });
                module.deoptimize(m);
            }
        installMetrics(loader);
        installPicker(loader);
        if (fold8()) {
            Method portrait = Class.forName("df.b", false, loader).getDeclaredMethod("a");
            module.hook(portrait)
                    .setId("dressroom-advanced.fold8-widget-arrangement")
                    .intercept(chain -> nativePreview.get() && draft != null && draft.getBoolean("layout")
                            ? false : chain.proceed());
            module.deoptimize(portrait);
        } else {
        Method trimmed = nativeClass("rh.a", false, loader).getDeclaredMethod("i");
        module.hook(trimmed)
                .setId("dressroom-advanced.portrait-widget-arrangement")
                .intercept(
                        chain -> {
                            if (owner.get() != null
                                    && draft != null
                                    && draft.getBoolean("layout")
                                    && chain.getThisObject() == field(owner.get(), "D"))
                                return Reflect.call(data(), "getWidgetAlignType");
                            return chain.proceed();
                        });
        module.deoptimize(trimmed);
        }
        Method editable =
                nativeClass(
                                "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData",
                                false,
                                loader)
                        .getDeclaredMethod("setEditableAlign", boolean.class);
        module.hook(editable)
                .setId("dressroom-advanced.widget-rotation-capability")
                .intercept(
                        chain -> {
                            if (owner.get() != null
                                    && draft != null
                                    && draft.getBoolean("layout")
                                    && chain.getThisObject() == field(owner.get(), "G"))
                                return chain.proceed(new Object[] {true});
                            return chain.proceed();
                        });
        module.deoptimize(editable);
        Class<?> alignType =
                nativeClass(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                        false,
                        loader);
        Method lifecycle =
                nativeClass("cg.n", false, loader)
                        .getDeclaredMethod("c", nativeClass("gf.c", false, loader));
        module.hook(lifecycle)
                .setId("dressroom-advanced.widget-resume-scope")
                .intercept(
                        chain -> {
                            boolean previous = nativeLifecycle.get();
                            Object mode =
                                    Reflect.call(
                                            field(chain.getThisObject(), "e"), "getData");
                            nativeLifecycle.set((Boolean) Reflect.call(mode, "isLockScreen"));
                            try {
                                return chain.proceed();
                            } finally {
                                nativeLifecycle.set(previous);
                            }
                        });
        module.deoptimize(lifecycle);
        Method providerAlign =
                nativeClass("pc.h", false, loader).getDeclaredMethod("a", alignType);
        module.hook(providerAlign)
                .setId("dressroom-advanced.widget-resume-arrangement")
                .intercept(
                        chain -> {
                            if (nativeLifecycle.get()
                                    && owner.get() != null
                                    && draft != null
                                    && draft.getBoolean("layout"))
                                return chain.proceed(
                                        new Object[] {Reflect.call(data(), "getWidgetAlignType")});
                            return chain.proceed();
                        });
        module.deoptimize(providerAlign);
        Method rotate = nativeClass("cg.v", false, loader).getDeclaredMethod("a", alignType);
        module.hook(rotate)
                .setId("dressroom-advanced.native-widget-rotation")
                .intercept(
                        chain -> {
                            if (owner.get() == null
                                    || draft == null
                                    || !draft.getBoolean("layout")
                                    || chain.getThisObject() != field(owner.get(), "y"))
                                return chain.proceed();
                            Object model = data();
                            if ((Boolean) Reflect.call(model, "getWidgetAlignChanging"))
                                return chain.proceed();
                            boolean target = (Boolean) Reflect.call(chain.getArg(0), "isVertical");
                            if (target == vertical()) return chain.proceed();
                            Bundle previous = draft;
                            boolean wasDirty = dirty;
                            Bundle next = new Bundle(draft);
                            orient(next, target);
                            if (!WidgetLayout.fits(
                                            next.getInt("columns"),
                                            next.getInt("rows"),
                                            new int[0][])
                                    || !module.widgetEngine.fits(grid(), next)) {
                                android.widget.Toast.makeText(
                                                context(),
                                                "These widgets need a larger grid for this"
                                                        + " arrangement.",
                                                android.widget.Toast.LENGTH_LONG)
                                        .show();
                                return false;
                            }
                            draft = WidgetPreferences.clean(next);
                            dirty = true;
                            try {
                                Object result = chain.proceed();
                                if (!Boolean.TRUE.equals(result)) {
                                    draft = previous;
                                    dirty = wasDirty;
                                }
                                return result;
                            } catch (Throwable error) {
                                draft = previous;
                                dirty = wasDirty;
                                throw error;
                            }
                        });
        module.deoptimize(rotate);
    }

    void installLegacy(ClassLoader loader) throws ReflectiveOperationException {
        WidgetEditorBinding nativeWidgets = new WidgetEditorBinding(loader);
        Class<?> widgetData =
                nativeClass(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData",
                        false,
                        loader);
        Method feedback =
                NativeContract.method(
                        nativeClass(
                                "com.samsung.android.lockscreen.editor.item.data.repository.item.clock.c",
                                false,
                                loader),
                        "o",
                        void.class,
                        widgetData);
        legacyBinding = nativeWidgets;
        try (HookTransaction hooks = new HookTransaction()) {
            var menu =
                    module.hook(nativeWidgets.toolbar)
                            .setId("dressroom-advanced.85-widget-menu")
                            .intercept(
                                    chain -> {
                                        try {
                                            addMenu(chain.getThisObject(), loader);
                                        } catch (Exception error) {
                                            module.report("8.5 Advanced menu unavailable", error);
                                        }
                                        return chain.proceed();
                                    });
            hooks.add(menu::unhook);
            module.deoptimize(nativeWidgets.toolbar);
            var provider =
                    module.hook(nativeWidgets.getGrid)
                            .setId("dressroom-advanced.85-widget-provider")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (result != null)
                                            try {
                                                module.widgetEngine.installScale(
                                                        result.getClass().getClassLoader());
                                                legacyScaleReady = true;
                                            } catch (Exception | LinkageError error) {
                                                module.report(
                                                        "8.5 widget sizing unavailable", error);
                                            }
                                        return result;
                                    });
            hooks.add(provider::unhook);
            module.deoptimize(nativeWidgets.getGrid);
            var preview =
                    module.hook(nativeWidgets.create)
                            .setId("dressroom-advanced.85-widget-preview")
                            .intercept(
                                    chain -> {
                                        Object item = chain.getThisObject();
                                        if (isMain(item)) {
                                            owner = new WeakReference<>(item);
                                            Context context = (Context) field(item, "b");
                                            host = new WeakReference<>(context);
                                            ui = new WeakReference<>(field(item, "k"));
                                            settings(context);
                                        }
                                        Object result = chain.proceed();
                                        if (item == owner.get() && result instanceof View root) {
                                            long generation = module.generation();
                                            WeakReference<Object> expected =
                                                    new WeakReference<>(item);
                                            root.post(
                                                    () -> {
                                                        if (expected.get() != owner.get()
                                                                || module.generation() != generation
                                                                || !root.isAttachedToWindow())
                                                            return;
                                                        try {
                                                            module.widgetEngine.refreshChecked(
                                                                    grid());
                                                            recalculate();
                                                            applyEffects();
                                                        } catch (Exception error) {
                                                            module.report(
                                                                    "Saved widget sizing"
                                                                            + " unavailable",
                                                                    error);
                                                        }
                                                    });
                                        }
                                        return result;
                                    });
            hooks.add(preview::unhook);
            module.deoptimize(nativeWidgets.create);
            var metrics =
                    module.hook(nativeWidgets.metrics)
                            .setId("dressroom-advanced.85-widget-metrics")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (owner.get() != null
                                                && draft != null
                                                && draft.getFloat("scale", 1) != 1
                                                && Boolean.TRUE.equals(
                                                        Reflect.call(
                                                                chain.getArg(10),
                                                                "isLockScreen"))) {
                                            Object manager =
                                                    Reflect.call(
                                                            field(
                                                                    chain.getThisObject(), "d"),
                                                            "getValue");
                                            WidgetEditorBinding.metrics(
                                                    chain.getArg(1), chain.getArg(0), manager);
                                        }
                                        return result;
                                    });
            hooks.add(metrics::unhook);
            module.deoptimize(nativeWidgets.metrics);
            var position =
                    module.hook(nativeWidgets.position)
                            .setId("dressroom-advanced.85-widget-placement")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (owner.get() == null
                                                || draft == null
                                                || draft.getInt("anchor") == 0
                                                || !Boolean.TRUE.equals(
                                                        Reflect.call(
                                                                chain.getArg(3), "isLockScreen")))
                                            return result;
                                        placeLegacy(
                                                chain.getArg(0), chain.getArg(1), chain.getArg(5));
                                        return result;
                                    });
            hooks.add(position::unhook);
            module.deoptimize(nativeWidgets.position);
            var align =
                    module.hook(feedback)
                            .setId("dressroom-advanced.85-widget-feedback")
                            .intercept(
                                    chain -> {
                                        if (owner.get() != null
                                                && draft != null
                                                && draft.getInt("anchor") != 0
                                                && chain.getThisObject()
                                                        == field(
                                                                field(owner.get(), "B"),
                                                                "l")) return null;
                                        return chain.proceed();
                                    });
            hooks.add(align::unhook);
            module.deoptimize(feedback);
            var direct = installDirectDragBinding(loader);
            hooks.add(direct::unhook);
            var drag = installDrag(loader);
            hooks.add(drag::unhook);
            hooks.commit();
        } catch (RuntimeException | LinkageError error) {
            legacyBinding = null;
            throw error;
        }
    }

    private void placeLegacy(Object model, Object assist, Object screen)
            throws ReflectiveOperationException {
        Rect clockRect = (Rect) Reflect.call(assist, "getClockPositionRect");
        float width = ((Number) Reflect.call(model, "getWidth")).floatValue();
        float height = ((Number) Reflect.call(model, "getHeight")).floatValue();
        float sw = ((Number) Reflect.call(screen, "getWidth")).floatValue();
        float sh = ((Number) Reflect.call(screen, "getHeight")).floatValue();
        Context context = host.get();
        if (context == null) return;
        float density =
                context.getResources().getDisplayMetrics().density
                        * sw
                        / context.getResources().getDisplayMetrics().widthPixels;
        float[] point =
                WidgetLayout.place(
                        draft.getInt("anchor"),
                        clockRect.left,
                        clockRect.top,
                        clockRect.right,
                        clockRect.bottom,
                        width,
                        height,
                        sw,
                        sh,
                        draft.getFloat("gap", 8) * density,
                        draft.getFloat("freeX", .5f),
                        draft.getFloat("freeY", .4f),
                        draft.getFloat("dockOffset") * density);
        int x = Math.round(point[0]), y = Math.round(point[1]);
        Reflect.call(model, "setX", x);
        Reflect.call(model, "setY", y);
        Reflect.call(
                model,
                "setWidgetPositionRect",
                new Rect(x, y, x + Math.round(width), y + Math.round(height)));
    }

    private void restoreModel(Object model) throws ReflectiveOperationException {
        if (draft == null) return;
        boolean vertical =
                (Boolean) Reflect.call(Reflect.call(model, "getWidgetAlignType"), "isVertical");
        int[] dimensions =
                WidgetLayout.dimensions(
                        draft.getInt("columns"),
                        draft.getInt("rows"),
                        draft.getBoolean("vertical"),
                        vertical);
        draft.putBoolean("vertical", vertical);
        draft.putInt("columns", dimensions[0]);
        draft.putInt("rows", dimensions[1]);
        if (!draft.getBoolean("layout")) return;
        Reflect.call(model, "setEditableAlign", true);
        Object grid = Reflect.call(model, "getWidgetGridData");
        Reflect.call(grid, "setCol", draft.getInt("columns"));
        Reflect.call(grid, "setRow", draft.getInt("rows"));
    }

    private void installMetrics(ClassLoader loader) throws ReflectiveOperationException {
        if (!fold8()) {
        Method guide = nativeClass("sp.y1", false, loader).getDeclaredMethod("executeBindings");
        module.hook(guide)
                .setId("dressroom-advanced.hide-widget-rotation-guide")
                .intercept(
                        chain -> {
                            Object result = chain.proceed();
                            ((View) Reflect.call(chain.getThisObject(), "getRoot"))
                                    .setVisibility(View.GONE);
                            return result;
                        });
        module.deoptimize(guide);
        }
        installDrag(loader);
        installDirectDragBinding(loader);
        for (Method m : nativeClass("sd.h", false, loader).getDeclaredMethods())
            if (m.getName().equals("a")) {
                module.hook(m)
                        .setId("dressroom-advanced.widget-editor-grid-metrics")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (draft != null && draft.getBoolean("layout")) {
                                        boolean vertical =
                                                (Boolean)
                                                        Reflect.call(chain.getArg(1), "isVertical");
                                        int[] dimensions =
                                                WidgetLayout.dimensions(
                                                        draft.getInt("columns"),
                                                        draft.getInt("rows"),
                                                        draft.getBoolean("vertical"),
                                                        vertical);
                                        Reflect.call(chain.getArg(0), "setCol", dimensions[0]);
                                        Reflect.call(chain.getArg(0), "setRow", dimensions[1]);
                                    }
                                    return result;
                                });
                module.deoptimize(m);
            }
        for (Method m : nativeClass("sd.o", false, loader).getDeclaredMethods())
            if (m.getName().equals("a")) {
                module.hook(m)
                        .setId("dressroom-advanced.widget-editor-cell-metrics")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (draft != null
                                            && (draft.getBoolean("layout")
                                                    || draft.getFloat("scale", 1) != 1)
                                            && (Boolean)
                                                    Reflect.call(chain.getArg(7), "isLockScreen")) {
                                        Object manager =
                                                Reflect.call(
                                                        field(chain.getThisObject(), "c"),
                                                        "getValue");
                                        Object size = chain.getArg(1), grid = chain.getArg(0);
                                        Reflect.call(
                                                grid,
                                                "setCol",
                                                ((Number) Reflect.call(manager, "getColumn"))
                                                        .intValue());
                                        Reflect.call(
                                                grid,
                                                "setRow",
                                                ((Number) Reflect.call(manager, "getRow"))
                                                        .intValue());
                                        int unit =
                                                (int)
                                                        Math.ceil(
                                                                ((Number)
                                                                                Reflect.call(
                                                                                        manager,
                                                                                        "getUnitSize"))
                                                                        .floatValue());
                                        int padding =
                                                ((Number) Reflect.call(manager, "getPadding"))
                                                        .intValue();
                                        float scale =
                                                ((Number) Reflect.call(manager, "getScaleFactor"))
                                                        .floatValue();
                                        int
                                                columns =
                                                        ((Number) Reflect.call(grid, "getCol"))
                                                                .intValue(),
                                                rows =
                                                        ((Number) Reflect.call(grid, "getRow"))
                                                                .intValue();
                                        Reflect.call(size, "setWidgetUnitSize", unit);
                                        Reflect.call(size, "setWidgetInnerSizePadding", padding);
                                        Reflect.call(size, "setWidgetSideMargin", padding);
                                        Reflect.call(size, "setScaleFactor", scale);
                                        Reflect.call(size, "setPreviewLayoutScale", scale);
                                        Reflect.call(
                                                size,
                                                "setContainerWidth",
                                                columns * unit + (columns - 1) * padding * 2);
                                        Reflect.call(
                                                size,
                                                "setContainerHeight",
                                                rows * unit + (rows - 1) * padding * 2);
                                        Reflect.call(
                                                size,
                                                "setDisableWidth",
                                                columns * unit + (columns - 1) * padding * 2);
                                        Reflect.call(
                                                size,
                                                "setDisableHeight",
                                                rows * unit + (rows - 1) * padding * 2);
                                        Reflect.call(
                                                size,
                                                "setDisableExpandWidth",
                                                columns * unit + (columns - 1) * padding * 2);
                                        float radius =
                                                ((Number) Reflect.call(manager, "radius", 1))
                                                        .floatValue();
                                        Reflect.call(size, "setRadius", radius);
                                        Reflect.call(size, "setPreviewRadius", radius / scale);
                                    }
                                    return result;
                                });
                module.deoptimize(m);
            }
        for (Method m : nativeClass("sd.m", false, loader).getDeclaredMethods())
            if (m.getName().equals("f")) {
                module.hook(m)
                        .setId("dressroom-advanced.widget-editor-placement")
                        .intercept(
                                chain -> {
                                    Object result = chain.proceed();
                                    if (draft == null
                                            || !(Boolean)
                                                    Reflect.call(chain.getArg(3), "isLockScreen"))
                                        return result;
                                    boolean custom = draft.getBoolean("layout");
                                    int anchor = WidgetLayout.effectiveAnchor(draft.getInt("anchor"), custom,
                                            draft.getBoolean("vertical"), fold8());
                                    if (!custom && anchor == 0) return result;
                                    Object model = chain.getArg(0),
                                            assist = chain.getArg(1),
                                            screen = chain.getArg(5);
                                    Rect clockRect =
                                            (Rect) Reflect.call(assist, "getClockPositionRect");
                                    float
                                            width =
                                                    ((Number) Reflect.call(model, "getWidth"))
                                                            .floatValue(),
                                            height =
                                                    ((Number) Reflect.call(model, "getHeight"))
                                                            .floatValue();
                                    if (custom) {
                                        Object size = Reflect.call(model, "getWidgetSizeData");
                                        width =
                                                ((Number) Reflect.call(size, "getContainerWidth"))
                                                        .floatValue();
                                        height =
                                                ((Number) Reflect.call(size, "getContainerHeight"))
                                                        .floatValue();
                                        Reflect.call(model, "setWidth", Math.round(width));
                                        Reflect.call(model, "setHeight", Math.round(height));
                                    }
                                    if (anchor == 0) {
                                        int x = ((Number) Reflect.call(model, "getX")).intValue(),
                                                y =
                                                        ((Number) Reflect.call(model, "getY"))
                                                                .intValue();
                                        float sw =
                                                ((Number) Reflect.call(screen, "getWidth"))
                                                        .floatValue();
                                        float sh =
                                                ((Number) Reflect.call(screen, "getHeight"))
                                                        .floatValue();
                                        x =
                                                Math.max(
                                                        0,
                                                        Math.min(
                                                                Math.max(0, Math.round(sw - width)),
                                                                x));
                                        y =
                                                Math.max(
                                                        0,
                                                        Math.min(
                                                                Math.max(
                                                                        0, Math.round(sh - height)),
                                                                y));
                                        Reflect.call(model, "setX", x);
                                        Reflect.call(model, "setY", y);
                                        Reflect.call(
                                                model,
                                                "setWidgetPositionRect",
                                                new Rect(
                                                        x,
                                                        y,
                                                        x + Math.round(width),
                                                        y + Math.round(height)));
                                        return result;
                                    }
                                    float
                                            sw =
                                                    ((Number) Reflect.call(screen, "getWidth"))
                                                            .floatValue(),
                                            sh =
                                                    ((Number) Reflect.call(screen, "getHeight"))
                                                            .floatValue();
                                    Context c = (Context) field(chain.getThisObject(), "a");
                                    float density =
                                            c.getResources().getDisplayMetrics().density
                                                    * sw
                                                    / c.getResources()
                                                            .getDisplayMetrics()
                                                            .widthPixels;
                                    float[] position =
                                            WidgetLayout.place(
                                                    anchor,
                                                    clockRect.left,
                                                    clockRect.top,
                                                    clockRect.right,
                                                    clockRect.bottom,
                                                    width,
                                                    height,
                                                    sw,
                                                    sh,
                                                    draft.getFloat("gap", 8) * density,
                                                    draft.getFloat("freeX"),
                                                    draft.getFloat("freeY"),
                                                    draft.getFloat("dockOffset") * density);
                                    int x = Math.round(position[0]), y = Math.round(position[1]);
                                    Reflect.call(model, "setX", x);
                                    Reflect.call(model, "setY", y);
                                    Reflect.call(
                                            model,
                                            "setWidgetPositionRect",
                                            new Rect(
                                                    x,
                                                    y,
                                                    x + Math.round(width),
                                                    y + Math.round(height)));
                                    return result;
                                });
                module.deoptimize(m);
            }
        Method alignFeedback =
                nativeClass("yc.b", false, loader)
                        .getDeclaredMethod(
                                "c",
                                nativeClass(
                                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData",
                                        false,
                                        loader));
        module.hook(alignFeedback)
                .setId("dressroom-advanced.widget-clock-feedback")
                .intercept(
                        chain -> {
                            if (draft != null && draft.getInt("anchor") != 0) return null;
                            return chain.proceed();
                        });
        module.deoptimize(alignFeedback);
    }

    private io.github.libxposed.api.XposedInterface.HookHandle installDrag(ClassLoader loader)
            throws ReflectiveOperationException {
        Method touch =
                nativeClass(
                                "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                                false,
                                loader)
                        .getDeclaredMethod("dispatchTouchEvent", android.view.MotionEvent.class);
        Class<?> bindingType = nativeClass("androidx.databinding.ViewDataBinding", false, loader);
        pendingBinding = bindingType.getDeclaredMethod("getBinding", View.class);
        pendingBinding.setAccessible(true);
        var handle =
                module.hook(touch)
                        .setId("dressroom-advanced.drag-widget-group")
                        .intercept(
                                chain -> {
                                    if (owner.get() == null
                                            || !(field(owner.get(), legacy() ? "N" : "L")
                                                    instanceof View root)
                                            || !root.isShown()) return chain.proceed();
                                    if (legacy()
                                            && dragStart == null
                                            && (draft == null || draft.getInt("anchor") == 0))
                                        return chain.proceed();
                                    android.view.MotionEvent event =
                                            (android.view.MotionEvent) chain.getArg(0);
                                    int action = event.getActionMasked();
                                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                                        Rect bounds = new Rect();
                                        if (!root.getGlobalVisibleRect(bounds)
                                                || !bounds.contains(
                                                        Math.round(event.getRawX()),
                                                        Math.round(event.getRawY())))
                                            return chain.proceed();
                                        if (dragDown != null) dragDown.recycle();
                                        dragDown = android.view.MotionEvent.obtain(event);
                                        dragStart = new Bundle(settings(root.getContext()));
                                        dragging = false;
                                        dragWasDirty = dirty;
                                        dragX = event.getRawX();
                                        dragY = event.getRawY();
                                        dragModelX =
                                                ((Number) Reflect.call(data(), "getX"))
                                                        .floatValue();
                                        dragModelY =
                                                ((Number) Reflect.call(data(), "getY"))
                                                        .floatValue();
                                        return true;
                                    }
                                    if (dragStart == null) return chain.proceed();
                                    if (action == android.view.MotionEvent.ACTION_MOVE
                                            && !dragging
                                            && Math.hypot(
                                                            event.getRawX() - dragX,
                                                            event.getRawY() - dragY)
                                                    > android.view.ViewConfiguration.get(
                                                                    root.getContext())
                                                            .getScaledTouchSlop()) {
                                        dragging = true;
                                        lastDrag = 0;
                                    }
                                    if (!dragging) {
                                        if (action == 1 || action == 3) {
                                            dragStart = null;
                                            try {
                                                if (action == 1 && dragDown != null) {
                                                    chain.proceed(new Object[] {dragDown});
                                                    return chain.proceed();
                                                }
                                            } finally {
                                                if (dragDown != null) {
                                                    dragDown.recycle();
                                                    dragDown = null;
                                                }
                                            }
                                        }
                                        return true;
                                    }
                                    try {
                                        if (action == 2 || action == 1) {
                                            if (action == 1
                                                    || event.getEventTime() - lastDrag >= 16) {
                                                lastDrag = event.getEventTime();
                                                moveGroup(
                                                        event.getRawX() - dragX,
                                                        event.getRawY() - dragY);
                                            }
                                        } else if (action == 3) {
                                            update(new Bundle(dragStart), false);
                                            dirty = dragWasDirty;
                                        }
                                    } catch (Exception e) {
                                        module.report("Widget dragging failed", e);
                                    }
                                    if (action == 1 || action == 3) {
                                        dragStart = null;
                                        dragging = false;
                                        if (dragDown != null) {
                                            dragDown.recycle();
                                            dragDown = null;
                                        }
                                    }
                                    return true;
                                });
        module.deoptimize(touch);
        return handle;
    }

    private io.github.libxposed.api.XposedInterface.HookHandle installDirectDragBinding(
            ClassLoader loader) throws ReflectiveOperationException {
        Class<?> adapter = nativeClass(legacy() ? "lb.c" : "rp.e", false, loader);
        Method position =
                adapter.getDeclaredMethod(
                        legacy() ? "r" : fold8() ? "V" : "b",
                        View.class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        boolean.class);
        position.setAccessible(true);
        var handle =
                module.hook(position)
                        .setId("dressroom-advanced.widget-drag-immediate-binding")
                        .intercept(
                                chain -> {
                                    if (dragging
                                            && owner.get() != null
                                            && chain.getArg(0)
                                                    == field(
                                                            owner.get(), legacy() ? "N" : "L")) {
                                        Object[] args = new Object[7];
                                        for (int i = 0; i < args.length; i++)
                                            args[i] = chain.getArg(i);
                                        args[6] = true;
                                        return chain.proceed(args);
                                    }
                                    return chain.proceed();
                                });
        module.deoptimize(position);
        return handle;
    }

    private void moveGroup(float rawX, float rawY) throws Exception {
        float dx, dy;
        if (legacy()) {
            View root = (View) field(owner.get(), "N");
            android.graphics.Matrix global = new android.graphics.Matrix(),
                    inverse = new android.graphics.Matrix();
            root.transformMatrixToGlobal(global);
            if (!global.invert(inverse)) return;
            float[] delta = {rawX, rawY};
            inverse.mapVectors(delta);
            dx = delta[0];
            dy = delta[1];
        } else {
            float previewScale =
                    ((Number) field(field(owner.get(), "g"), "r")).floatValue();
            if (!Float.isFinite(previewScale) || previewScale <= 0) return;
            dx = rawX / previewScale;
            dy = rawY / previewScale;
        }
        Bundle next = new Bundle(dragStart);
        int anchor = next.getInt("anchor");
        if (anchor >= 1 && anchor <= 4) {
            float density = context().getResources().getDisplayMetrics().density;
            next.putFloat(
                    "dockOffset", next.getFloat("dockOffset") + (anchor <= 2 ? dx : dy) / density);
        } else {
            Object screen =
                    Reflect.call(
                            field(field(owner.get(), legacy() ? "E" : "C"), "a"),
                            "getData");
            float sw = ((Number) Reflect.call(screen, "getWidth")).floatValue(),
                    sh = ((Number) Reflect.call(screen, "getHeight")).floatValue();
            float width = ((Number) Reflect.call(data(), "getWidth")).floatValue(),
                    height = ((Number) Reflect.call(data(), "getHeight")).floatValue();
            next.putInt("anchor", 5);
            next.putFloat("freeX", (dragModelX + dx) / Math.max(1, sw - width));
            next.putFloat("freeY", (dragModelY + dy) / Math.max(1, sh - height));
        }
        update(next, false);
        if (pendingBinding != null) {
            View root = (View) field(owner.get(), legacy() ? "N" : "L");
            Object binding = pendingBinding.invoke(null, root);
            if (binding != null) Reflect.call(binding, "executePendingBindings");
        }
    }

    private void installPicker(ClassLoader loader) throws ReflectiveOperationException {
        Method tablet =
                nativeClass(
                                "com.samsung.android.lockscreen.editor.item.domain.entity.system.DeviceType",
                                false,
                                loader)
                        .getDeclaredMethod("isTablet");
        module.hook(tablet)
                .setId("dressroom-advanced.two-row-widget-eligibility")
                .intercept(chain -> largePicker.get() ? true : chain.proceed());
        module.deoptimize(tablet);
        for (Method m : nativeClass("sd.d", false, loader).getDeclaredMethods())
            if (m.getName().equals("a") && m.getParameterCount() == 2) {
                module.hook(m)
                        .setId("dressroom-advanced.widget-picker-size-scope")
                        .intercept(
                                chain -> {
                                    boolean old = largePicker.get();
                                    largePicker.set(
                                            draft != null
                                                    && draft.getBoolean("layout")
                                                    && draft.getInt("rows") >= 2
                                                    && (Boolean)
                                                            Reflect.call(
                                                                    chain.getArg(0),
                                                                    "isLockScreen"));
                                    try {
                                        return chain.proceed();
                                    } finally {
                                        largePicker.set(old);
                                    }
                                });
                module.deoptimize(m);
            }
    }

    private void addMenu(Object toolbar, ClassLoader loader) throws Exception {
        if (legacy()) {
            Context context = (Context) field(toolbar, "a");
            legacyBinding.addMenu(toolbar, () -> open(context), TAG);
            return;
        }
        Object models = field(toolbar, "g");
        if (models == null || Array.getLength(models) == 0) return;
        Context c = (Context) field(toolbar, "a");
        Class<?> model = nativeClass("k6.m", false, loader);
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < Array.getLength(models); i++) {
            Object value = Array.get(models, i);
            if (TAG.equals(field(value, "e"))) return;
            list.add(value);
        }
        int icon =
                c.getResources()
                        .getIdentifier("preview_menu_icon_effect", "drawable", c.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        Object advanced =
                model.getDeclaredConstructor(int.class, int.class, Runnable.class, String.class)
                        .newInstance(-1, icon, (Runnable) () -> open(c), TAG);
        Reflect.set(advanced, "f", "Advanced");
        list.add(advanced);
        Object result = Array.newInstance(model, list.size());
        for (int i = 0; i < list.size(); i++) Array.set(result, i, list.get(i));
        Reflect.set(toolbar, "g", result);
    }

    private void open(Context context) {
        try {
            if (owner.get() == null || ui.get() == null)
                throw new IllegalStateException(
                        "Open the lockscreen editor with widgets available first.");
            new WidgetPanel(context, owner.get().getClass().getClassLoader(), this).show(ui.get());
        } catch (Exception e) {
            module.report("Widget Advanced panel unavailable", e);
            android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                    .show();
        }
    }
}
