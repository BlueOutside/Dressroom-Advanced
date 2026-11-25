package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Keep the live surface when seamless AOD requests its flat preview substitute. */
final class AodWidgetBackground {
    private static final ThreadLocal<Boolean> aodSubstitution = new ThreadLocal<>();
    private static final Map<Class<?>, Method> installed = new WeakHashMap<>();
    private static final Map<Object, Boolean> requested =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AodWidgetBackground() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess() || !WallpaperAod.fullscreenSupported()) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> manager = Class.forName(
                    "com.samsung.android.uniform.briefwidget.impl.BriefWidgetManagerImpl", false, loader);
            if (installed.containsKey(manager)) return;
            Method visibility = NativeContract.method(manager, "setWidgetBlurBGVisibility", void.class, boolean.class);
            Field seamless = NativeContract.field(manager, "isSeamlessAOD", boolean.class);
            Field preview = NativeContract.field(manager, "isPreviewMode", boolean.class);
            Field editing = NativeContract.field(manager, "isClockEditMode", boolean.class);
            Class<?> owner = Class.forName(
                    "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer", false, loader);
            Field surface = NativeContract.field(owner, "blurView", View.class);
            Field dim = NativeContract.field(owner, "previewDimView", View.class);
            Field wallpaper = NativeContract.field(owner, "showWallpaperOnAOD", boolean.class);
            Field enabled = NativeContract.field(owner, "enableBlur", boolean.class);
            Field expanded = NativeContract.field(owner, "musicExpandState", boolean.class);
            Field animator = NativeContract.field(owner, "editModeAnimator", Animator.class);
            Field amount = NativeContract.field(owner, "darkAmount", float.class);
            Method restore = NativeContract.method(owner, "removePreviewDimBG", void.class, boolean.class);
            Method refresh = NativeContract.method(owner, "refreshBlur", void.class, float.class);
            hooks.add(module.hook(visibility).setId("dressroom-advanced.aod-widget-background-handoff")
                    .intercept(chain -> {
                        Object instance = chain.getThisObject();
                        boolean visible = (Boolean) chain.getArg(0);
                        requested.put(instance, visible);
                        Boolean previous = aodSubstitution.get();
                        aodSubstitution.set(!visible && seamless.getBoolean(instance)
                                && !preview.getBoolean(instance) && !editing.getBoolean(instance));
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) aodSubstitution.remove();
                            else aodSubstitution.set(previous);
                        }
                    })::unhook);
            for (String name : new String[] {"setPreviewBGVisible", "updatePreviewBGColor"}) {
                Method substitute = name.equals("setPreviewBGVisible")
                        ? NativeContract.method(owner, name, void.class)
                        : NativeContract.method(owner, name, void.class, int.class);
                hooks.add(module.hook(substitute).setId("dressroom-advanced.aod-widget-" + name)
                        .intercept(chain -> {
                            View container = (View) chain.getThisObject();
                            if (!Boolean.TRUE.equals(aodSubstitution.get())
                                    || !wallpaper.getBoolean(container) || !enabled.getBoolean(container)
                                    || expanded.getBoolean(container)
                                    || animator.get(container) instanceof Animator animation && animation.isRunning()
                                    || !BlurAppearance.liveWindowBlurAllowed(container.getContext()))
                                return chain.proceed();
                            var settings = RuntimeExtras.get(container.getContext());
                            if (!settings.getBoolean(AodWidgetAppearance.KEY)
                                    && !settings.getBoolean(AodWidgetAppearance.PROFILE + "Custom"))
                                return chain.proceed();
                            View blur = (View) surface.get(container);
                            // Use Samsung's inverse handoff; don't alter widget/content visibility.
                            if (blur.getVisibility() != View.VISIBLE || dim.get(container) != null
                                    || !BlurAppearance.hasWindowDrawable(blur)) {
                                restore.invoke(container, false);
                                refresh.invoke(container, amount.getFloat(container));
                            }
                            return null;
                        })::unhook);
                module.deoptimize(substitute);
            }
            module.deoptimize(visibility);
            hooks.commit();
            installed.put(manager, visibility);
            RuntimeExtras.listen(() -> replay(manager, visibility));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.report("Optional seamless AOD widget background handoff unavailable", unavailable);
        }
    }

    private static void replay(Class<?> manager, Method visibility) {
        // Re-evaluate both enabling and disabling against the last native request.
        java.util.List<Map.Entry<Object, Boolean>> snapshot;
        synchronized (requested) {
            snapshot = new ArrayList<>();
            requested.forEach((instance, value) -> snapshot.add(Map.entry(instance, value)));
        }
        for (var entry : snapshot) {
            if (!manager.isInstance(entry.getKey())) continue;
            try {
                visibility.invoke(entry.getKey(), entry.getValue());
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                DressroomModule.note("Seamless AOD widget background refresh", unavailable);
            }
        }
    }
}
