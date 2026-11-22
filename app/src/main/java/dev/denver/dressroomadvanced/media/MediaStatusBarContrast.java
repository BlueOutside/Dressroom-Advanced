package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Mirrors Samsung's wallpaper luminance contract onto {@code KeyguardStatusBarWallpaperHelper},
 * {@code NotificationIconsOnlyContainer}, and the AOD status bar {@code BatteryMeterView} so
 * lockscreen status bar, notification, and LTPO Wallpaper AOD battery/charging icons invert cleanly
 * over bright or dark fullscreen album covers and videos.
 */
final class MediaStatusBarContrast {
    static final int FONT_COLOR_TYPE_LIGHT = 0;
    static final int FONT_COLOR_TYPE_DARK = 1;
    static final int LIGHT_STATUS_COLOR = 0xedffffff;
    static final int AOD_LIGHT_STATUS_COLOR = 0xb2ffffff;
    static final int AOD_DND_LIGHT_COLOR = 0xfffafaff;
    static final int DARK_STATUS_COLOR = 0xb3000000;
    static final String AOD_BATTERY_TAG = "PluginFaceWidgetManager";

    static final double STATIC_DARK_THRESHOLD = 0.26;
    static final double VIDEO_TO_DARK_THRESHOLD = 0.30;
    static final double VIDEO_TO_LIGHT_THRESHOLD = 0.18;
    static final double AMBIENT_DARK_THRESHOLD = 0.18;
    static final String NIO_UPDATE_ICON_COLOR_ANCHOR = "updateIconColor() : ";
    private static final long VIDEO_SAMPLE_INTERVAL_MS = 250L;

    private static final Map<ViewGroup, State> states = new WeakHashMap<>();
    private static final Map<ViewGroup, PendingSample> pendingSamples = new WeakHashMap<>();
    private static final Set<Object> trackedHelpers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Object, Boolean> overrides =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ClassLoader> installedLoaders =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> restoring =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Set<Object> trackedAodBatteries =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Object, Boolean> aodBatteryOverrides =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, AodBatteryState> aodBatteryNativeStates =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> writingAodBattery =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Set<Object> trackedNioContainers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Object, Integer> nioOverrides =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Integer> nioNativeColors =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Integer> nioNativeTypes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ClassLoader> installedPluginLoaders =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> writingNio =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile boolean installed;
    private static volatile boolean pluginInstalled;
    private static Class<?> keyguardStatusBarViewClass;
    private static Field helperField;
    private static Field colorField;
    private static Field typeField;
    private static Field intensityField;
    private static Field listenerField;
    private static Method updateMethod;
    private static Method onWallpaperUpdatedMethod;

    private static Class<?> batteryMeterViewClass;
    private static Field aodBatteryFlagField;
    private static Field batteryIconViewField;
    private static Field batteryDarkIntensityField;
    private static Field batteryDarkTintField;
    private static Method batteryOnDarkChangedMethod;

    private static Class<?> nioContainerClass;
    private static Method nioUpdateIconColorMethod;
    private static Method nioSetChildPropertyMethod;
    private static Method nioGetIconColorMethod;
    private static Method nioGetWallpaperColorTypeMethod;
    private static Method nioSetWallpaperColorTypeMethod;
    private static Method nioSetDozingMethod;

    private record AodBatteryState(float darkIntensity, int darkTint) {}

    private static final class State {
        WeakReference<Bitmap> source = new WeakReference<>(null);
        int generation;
        int appliedRootWidth = -1;
        int appliedRootHeight = -1;
        int appliedHelperCount;
        int appliedAodBatteryCount;
        int appliedNioCount;
        boolean appliedAmbient;
        int appliedDimArgb;
        Boolean staticDark;
        Boolean appliedDark;
        WeakReference<View> statusBarView = new WeakReference<>(null);
        WeakReference<Object> appliedHelper = new WeakReference<>(null);
        final ArrayList<WeakReference<Object>> appliedAodBatteries = new ArrayList<>();
        final ArrayList<WeakReference<Object>> appliedNioContainers = new ArrayList<>();
        final RectF localBarBounds = new RectF();
        long lastVideoSample;
    }

    private static final class PendingSample {
        final WeakReference<Bitmap> source;
        final int generation;
        final RectF destination = new RectF();
        final boolean ambient;
        final int dimOverlayArgb;

        PendingSample(
                Bitmap source,
                int generation,
                RectF destination,
                boolean ambient,
                int dimOverlayArgb) {
            this.source = new WeakReference<>(source);
            this.generation = generation;
            this.destination.set(destination);
            this.ambient = ambient;
            this.dimOverlayArgb = dimOverlayArgb;
        }
    }

    private MediaStatusBarContrast() {}

    static boolean shouldUseDarkIcons(double luma, Boolean priorDark) {
        if (!Double.isFinite(luma)) return Boolean.TRUE.equals(priorDark);
        if (priorDark == null) return luma > STATIC_DARK_THRESHOLD;
        if (priorDark) return luma >= VIDEO_TO_LIGHT_THRESHOLD;
        return luma > VIDEO_TO_DARK_THRESHOLD;
    }

    static double effectiveAmbientLuminance(double rawLuma, int dimOverlayArgb) {
        if (!Double.isFinite(rawLuma)) return rawLuma;
        double clamped = Math.max(0.0, Math.min(1.0, rawLuma));
        int alpha = (dimOverlayArgb >>> 24) & 0xff;
        if (alpha <= 0) return clamped;
        if (alpha >= 255) return 0.0;
        double transmittance = (255 - alpha) / 255.0;
        return clamped * Math.pow(transmittance, 2.2);
    }

    static boolean shouldUseDarkIconsAmbient(
            double rawLuma, int dimOverlayArgb, Boolean priorDark) {
        double effective = effectiveAmbientLuminance(rawLuma, dimOverlayArgb);
        if (!Double.isFinite(effective)) return Boolean.TRUE.equals(priorDark);
        return effective > AMBIENT_DARK_THRESHOLD;
    }

    static int nioColorFor(boolean dark, boolean ambient) {
        return dark ? DARK_STATUS_COLOR : (ambient ? AOD_LIGHT_STATUS_COLOR : LIGHT_STATUS_COLOR);
    }

    static float aodBatteryDarkIntensity(boolean dark) {
        return dark ? 1.0f : 0.0f;
    }

    static int aodDndColorFor(boolean dark) {
        return dark ? DARK_STATUS_COLOR : AOD_DND_LIGHT_COLOR;
    }

    static boolean isAodBatteryTag(Object tag) {
        return AOD_BATTERY_TAG.equals(tag);
    }

    static boolean isNioUpdateIconColorBody(DexSignatures.Body body) {
        return body != null
                && (body.access() & Modifier.STATIC) == 0
                && body.parameters().size() == 1
                && "I".equals(body.parameters().get(0))
                && "V".equals(body.result())
                && body.anchors().containsKey(NIO_UPDATE_ICON_COLOR_ANCHOR);
    }

    static synchronized void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        if (installedLoaders.contains(loader)) return;
        Class<?> helperClass =
                Class.forName(
                        "com.android.systemui.statusbar.phone.KeyguardStatusBarWallpaperHelper",
                        false,
                        loader);
        Class<?> statusBarClass =
                Class.forName(
                        "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                        false,
                        loader);
        Class<?> listenerClass =
                Class.forName(
                        "com.android.systemui.statusbar.phone.KeyguardStatusBarWallpaperListener",
                        false,
                        loader);

        Field helperF = statusBarClass.getDeclaredField("mKeyguardStatusBarWallpaperHelper");
        helperF.setAccessible(true);
        Field colorF = helperClass.getDeclaredField("fontColorFromWallPaper");
        colorF.setAccessible(true);
        Field typeF = helperClass.getDeclaredField("fontColorType");
        typeF.setAccessible(true);
        Field intensityF = helperClass.getDeclaredField("intensity");
        intensityF.setAccessible(true);
        Field listenerF = helperClass.getDeclaredField("listener");
        listenerF.setAccessible(true);
        Method updateM = helperClass.getDeclaredMethod("updateIconsAndTextColors");
        updateM.setAccessible(true);
        Method onUpdatedM = listenerClass.getDeclaredMethod("onWallpaperUpdated");
        onUpdatedM.setAccessible(true);

        keyguardStatusBarViewClass = statusBarClass;
        helperField = helperF;
        colorField = colorF;
        typeField = typeF;
        intensityField = intensityF;
        listenerField = listenerF;
        updateMethod = updateM;
        onWallpaperUpdatedMethod = onUpdatedM;

        try (HookTransaction hooks = new HookTransaction()) {
            for (Constructor<?> ctor : helperClass.getDeclaredConstructors()) {
                hooks.add(
                        module.hook(ctor)
                                .setId("dressroom-advanced.media-status-bar-helper-init")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            Object instance = chain.getThisObject();
                                            if (instance != null) trackedHelpers.add(instance);
                                            return result;
                                        })
                                ::unhook);
            }
            hooks.add(
                    module.hook(updateM)
                            .setId("dressroom-advanced.media-status-bar-contrast")
                            .intercept(
                                    chain -> {
                                        Object helper = chain.getThisObject();
                                        if (helper != null) trackedHelpers.add(helper);
                                        if (!Boolean.TRUE.equals(restoring.get())
                                                && helper != null) {
                                            Boolean dark = overrides.get(helper);
                                            if (dark != null) {
                                                try {
                                                    pushToHelper(helper, dark);
                                                    return null;
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    DressroomModule.note(
                                                            "Status bar icon contrast hook", error);
                                                }
                                            }
                                        }
                                        return chain.proceed();
                                    })
                            ::unhook);
            module.deoptimize(updateM);
            hooks.commit();
        }
        try {
            installAodBattery(module, loader);
        } catch (ReflectiveOperationException | LinkageError error) {
            DressroomModule.note("AOD status bar battery contrast unavailable", error);
        }
        installedLoaders.add(loader);
        installed = true;
        android.util.Log.i(DressroomModule.TAG, "Status bar artwork contrast installed");
    }

    private static void installAodBattery(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> batteryClass =
                Class.forName("com.android.systemui.battery.BatteryMeterView", false, loader);
        Field aodFlagF = batteryClass.getDeclaredField("mIsOnAodBattery");
        aodFlagF.setAccessible(true);
        Field iconViewF = batteryClass.getDeclaredField("mBatteryIconView");
        iconViewF.setAccessible(true);
        Field darkIntensityF = batteryClass.getDeclaredField("mDarkIntensity");
        darkIntensityF.setAccessible(true);
        Field darkTintF = batteryClass.getDeclaredField("mDarkTint");
        darkTintF.setAccessible(true);
        Method onDarkChangedM =
                batteryClass.getDeclaredMethod(
                        "onDarkChanged", ArrayList.class, float.class, int.class);
        onDarkChangedM.setAccessible(true);

        Method createBatteryM = null;
        try {
            Class<?> pluginFwClass =
                    Class.forName(
                            "com.android.systemui.facewidget.plugin.PluginFaceWidgetManager",
                            false,
                            loader);
            createBatteryM = pluginFwClass.getDeclaredMethod("createBatteryMeterView");
            createBatteryM.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        batteryMeterViewClass = batteryClass;
        aodBatteryFlagField = aodFlagF;
        batteryIconViewField = iconViewF;
        batteryDarkIntensityField = darkIntensityF;
        batteryDarkTintField = darkTintF;
        batteryOnDarkChangedMethod = onDarkChangedM;

        try (HookTransaction hooks = new HookTransaction()) {
            if (createBatteryM != null) {
                hooks.add(
                        module.hook(createBatteryM)
                                .setId("dressroom-advanced.media-aod-battery-create")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            if (result != null && batteryClass.isInstance(result)) {
                                                trackedAodBatteries.add(result);
                                            }
                                            return result;
                                        })
                                ::unhook);
            }
            hooks.add(
                    module.hook(onDarkChangedM)
                            .setId("dressroom-advanced.media-aod-battery-contrast")
                            .intercept(
                                    chain -> {
                                        Object view = chain.getThisObject();
                                        if (view != null && isAodBatteryView(view)) {
                                            trackedAodBatteries.add(view);
                                            if (!Boolean.TRUE.equals(writingAodBattery.get())) {
                                                float incomingIntensity = (Float) chain.getArg(1);
                                                int incomingTint = (Integer) chain.getArg(2);
                                                Boolean dark = aodBatteryOverrides.get(view);
                                                if (dark == null) {
                                                    aodBatteryNativeStates.put(
                                                            view,
                                                            new AodBatteryState(
                                                                    incomingIntensity,
                                                                    incomingTint));
                                                } else {
                                                    float targetIntensity =
                                                            aodBatteryDarkIntensity(dark);
                                                    int targetTint =
                                                            dark
                                                                    ? DARK_STATUS_COLOR
                                                                    : LIGHT_STATUS_COLOR;
                                                    if (Float.compare(
                                                                            incomingIntensity,
                                                                            targetIntensity)
                                                                    != 0
                                                            || incomingTint != targetTint) {
                                                        return chain.proceed(
                                                                new Object[] {
                                                                    new ArrayList<>(),
                                                                    targetIntensity,
                                                                    targetTint
                                                                });
                                                    }
                                                }
                                            }
                                        }
                                        return chain.proceed();
                                    })
                            ::unhook);
            module.deoptimize(onDarkChangedM);
            hooks.commit();
        }
    }

    static synchronized void installPlugin(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        if (installedPluginLoaders.contains(loader)) return;
        Class<?> containerClass =
                Class.forName(
                        "com.samsung.android.uniform.widget.notification.NotificationIconsOnlyContainer",
                        false,
                        loader);
        Method updateColorM = resolveNioUpdateIconColor(containerClass);
        Method setChildM = containerClass.getDeclaredMethod("setChildProperty", View.class);
        setChildM.setAccessible(true);
        Method getColorM = containerClass.getDeclaredMethod("getIconColor");
        getColorM.setAccessible(true);
        Method getTypeM = containerClass.getDeclaredMethod("getWallpaperColorType");
        getTypeM.setAccessible(true);
        Method setTypeM = containerClass.getDeclaredMethod("setWallpaperColorType", int.class);
        setTypeM.setAccessible(true);
        Method setDozingM = containerClass.getDeclaredMethod("setDozing", boolean.class);
        setDozingM.setAccessible(true);

        try (HookTransaction hooks = new HookTransaction()) {
            for (Constructor<?> ctor : containerClass.getDeclaredConstructors()) {
                hooks.add(
                        module.hook(ctor)
                                .setId("dressroom-advanced.media-nio-container-init")
                                .intercept(
                                        chain -> {
                                            Object result = chain.proceed();
                                            Object instance = chain.getThisObject();
                                            if (instance != null
                                                    && instance.getClass() == containerClass) {
                                                trackedNioContainers.add(instance);
                                            }
                                            return result;
                                        })
                                ::unhook);
            }
            hooks.add(
                    module.hook(updateColorM)
                            .setId("dressroom-advanced.media-nio-icon-contrast")
                            .intercept(
                                    chain -> {
                                        Object container = chain.getThisObject();
                                        if (container == null
                                                || container.getClass() != containerClass) {
                                            return chain.proceed();
                                        }
                                        trackedNioContainers.add(container);
                                        if (!Boolean.TRUE.equals(writingNio.get())) {
                                            int incoming = (Integer) chain.getArg(0);
                                            Integer target = nioOverrides.get(container);
                                            if (target == null) {
                                                nioNativeColors.put(container, incoming);
                                            } else if (target.intValue() != incoming) {
                                                return chain.proceed(new Object[] {target});
                                            }
                                        }
                                        return chain.proceed();
                                    })
                            ::unhook);
            hooks.add(
                    module.hook(setChildM)
                            .setId("dressroom-advanced.media-nio-child-contrast")
                            .intercept(
                                    chain -> {
                                        Object container = chain.getThisObject();
                                        if (container != null
                                                && container.getClass() == containerClass) {
                                            trackedNioContainers.add(container);
                                        }
                                        Object result = chain.proceed();
                                        if (container != null
                                                && container.getClass() == containerClass) {
                                            Integer target = nioOverrides.get(container);
                                            if (target != null
                                                    && target.intValue() == DARK_STATUS_COLOR
                                                    && chain.getArg(0) instanceof TextView tv
                                                    && tv.getBackground() != null) {
                                                tv.setTextColor(0xffffffff);
                                            }
                                        }
                                        return result;
                                    })
                            ::unhook);
            hooks.add(
                    module.hook(setDozingM)
                            .setId("dressroom-advanced.media-nio-doze-contrast")
                            .intercept(
                                    chain -> {
                                        Object container = chain.getThisObject();
                                        Object result = chain.proceed();
                                        if (container != null
                                                && container.getClass() == containerClass) {
                                            trackedNioContainers.add(container);
                                            Integer target = nioOverrides.get(container);
                                            if (target != null) {
                                                try {
                                                    pushToNio(
                                                            container,
                                                            target,
                                                            target.intValue() == DARK_STATUS_COLOR);
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException error) {
                                                    DressroomModule.note(
                                                            "Notification icon doze contrast hook",
                                                            error);
                                                }
                                            }
                                        }
                                        return result;
                                    })
                            ::unhook);
            module.deoptimize(updateColorM);
            module.deoptimize(setChildM);
            module.deoptimize(setDozingM);
            hooks.commit();
        }

        nioContainerClass = containerClass;
        nioUpdateIconColorMethod = updateColorM;
        nioSetChildPropertyMethod = setChildM;
        nioGetIconColorMethod = getColorM;
        nioGetWallpaperColorTypeMethod = getTypeM;
        nioSetWallpaperColorTypeMethod = setTypeM;
        nioSetDozingMethod = setDozingM;
        installedPluginLoaders.add(loader);
        pluginInstalled = true;
        android.util.Log.i(DressroomModule.TAG, "Notification icon artwork contrast installed");
    }

    private static Method resolveNioUpdateIconColor(Class<?> containerClass)
            throws ReflectiveOperationException, IOException {
        try {
            return DexSignatures.cached(
                    () -> {
                        Method matched = null;
                        for (Method candidate : containerClass.getDeclaredMethods()) {
                            int mods = candidate.getModifiers();
                            if (Modifier.isStatic(mods)
                                    || Modifier.isAbstract(mods)
                                    || candidate.getReturnType() != void.class
                                    || candidate.getParameterCount() != 1
                                    || candidate.getParameterTypes()[0] != int.class) {
                                continue;
                            }
                            DexSignatures.Body body =
                                    DexSignatures.body(
                                            candidate, Set.of(NIO_UPDATE_ICON_COLOR_ANCHOR));
                            if (isNioUpdateIconColorBody(body)) {
                                if (matched != null) {
                                    throw new NoSuchMethodException(
                                            "Multiple NIO updateIconColor methods on "
                                                    + containerClass.getName());
                                }
                                matched = candidate;
                            }
                        }
                        if (matched == null) {
                            throw new NoSuchMethodException(
                                    "Missing NIO updateIconColor method on "
                                            + containerClass.getName());
                        }
                        matched.setAccessible(true);
                        return matched;
                    });
        } catch (ReflectiveOperationException | IOException error) {
            throw error;
        } catch (Exception error) {
            throw (NoSuchMethodException)
                    new NoSuchMethodException(
                                    "NIO updateIconColor lookup failed on "
                                            + containerClass.getName())
                            .initCause(error);
        }
    }

    static boolean isActive(ViewGroup root) {
        return root != null && (states.containsKey(root) || pendingSamples.containsKey(root));
    }

    static void apply(ViewGroup root, Bitmap image, RectF destination) {
        applyInternal(root, image, destination, false, 0);
    }

    static void applyAmbient(
            ViewGroup root, Bitmap image, RectF destination, int dimOverlayArgb) {
        applyInternal(root, image, destination, true, dimOverlayArgb);
    }

    private static void applyInternal(
            ViewGroup root,
            Bitmap image,
            RectF destination,
            boolean ambient,
            int dimOverlayArgb) {
        if ((!installed && !pluginInstalled)
                || root == null
                || image == null
                || image.isRecycled()
                || destination == null
                || destination.isEmpty()
                || root.getWidth() <= 0
                || root.getHeight() <= 0) {
            return;
        }
        int generation = image.getGenerationId();
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        State state = states.get(root);
        if (state != null
                && state.source.get() == image
                && state.generation == generation
                && state.staticDark != null
                && state.appliedDark != null
                && state.appliedAmbient == ambient
                && state.appliedDimArgb == dimOverlayArgb
                && state.appliedRootWidth == rootW
                && state.appliedRootHeight == rootH
                && state.appliedHelperCount == trackedHelpers.size()
                && state.appliedAodBatteryCount == trackedAodBatteries.size()
                && state.appliedNioCount == trackedNioContainers.size()) {
            boolean helperOk = !installed;
            if (!helperOk) {
                Object helper = state.appliedHelper.get();
                helperOk = helper != null && overrides.containsKey(helper);
            }
            boolean aodBatteryOk =
                    !installed
                            || batteryMeterViewClass == null
                            || hasActiveAodBatteryOverride(state);
            boolean nioOk = !pluginInstalled || hasActiveNioOverride(state);
            if (helperOk && aodBatteryOk && nioOk) return;
        }

        ArtworkSamples pixels = MediaClockContrast.requestSamples(image);
        if (pixels == null) {
            pendingSamples.put(
                    root,
                    new PendingSample(image, generation, destination, ambient, dimOverlayArgb));
            return;
        }
        pendingSamples.remove(root);

        if (state == null) {
            state = new State();
            states.put(root, state);
        }

        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        Rect barRect = new Rect();
        View bar = findStatusBarView(root, state);
        if (bar != null && bar.getGlobalVisibleRect(barRect) && !barRect.isEmpty()) {
            state.localBarBounds.set(
                    barRect.left - origin[0],
                    barRect.top - origin[1],
                    barRect.right - origin[0],
                    barRect.bottom - origin[1]);
        } else {
            float density = root.getResources().getDisplayMetrics().density;
            int fallbackBottom = Math.max(1, Math.min(rootH, Math.round(44f * density)));
            barRect.set(origin[0], origin[1], origin[0] + rootW, origin[1] + fallbackBottom);
            state.localBarBounds.set(0f, 0f, rootW, fallbackBottom);
        }

        float finalScale =
                Math.max(rootW / (float) image.getWidth(), rootH / (float) image.getHeight());
        RectF coverRect =
                new RectF(
                        origin[0] + (rootW - image.getWidth() * finalScale) * 0.5f,
                        origin[1] + (rootH - image.getHeight() * finalScale) * 0.5f,
                        origin[0] + (rootW + image.getWidth() * finalScale) * 0.5f,
                        origin[1] + (rootH + image.getHeight() * finalScale) * 0.5f);
        double mean = pixels.analyze(coverRect, barRect).mean();
        boolean dark =
                ambient
                        ? shouldUseDarkIconsAmbient(mean, dimOverlayArgb, null)
                        : shouldUseDarkIcons(mean, null);

        state.source = new WeakReference<>(image);
        state.generation = generation;
        state.lastVideoSample = 0L;
        if (applyOverride(root, state, dark, ambient, dimOverlayArgb)) {
            state.staticDark = dark;
            state.appliedRootWidth = rootW;
            state.appliedRootHeight = rootH;
        }
    }

    static void onSamplesReady(Bitmap image, int generation, ArtworkSamples computed) {
        if ((!installed && !pluginInstalled)
                || computed == null
                || pendingSamples.isEmpty()
                || image == null
                || image.isRecycled()) {
            return;
        }
        for (var entry : new ArrayList<>(pendingSamples.entrySet())) {
            ViewGroup root = entry.getKey();
            PendingSample pending = entry.getValue();
            if (root == null || !root.isAttachedToWindow()) {
                pendingSamples.remove(root);
                continue;
            }
            if (pending.source.get() == image
                    && pending.generation == generation
                    && image.getGenerationId() == generation) {
                pendingSamples.remove(root);
                applyInternal(
                        root, image, pending.destination, pending.ambient, pending.dimOverlayArgb);
            }
        }
    }

    static void clearPendingFor(Bitmap image, int generation) {
        if (pendingSamples.isEmpty()) return;
        for (var entry : new ArrayList<>(pendingSamples.entrySet())) {
            ViewGroup root = entry.getKey();
            PendingSample pending = entry.getValue();
            if (root == null
                    || !root.isAttachedToWindow()
                    || (pending.source.get() == image && pending.generation == generation)) {
                pendingSamples.remove(root);
            }
        }
    }

    static void sampleVideo(ViewGroup root, Bitmap frame, RectF destination) {
        if ((!installed && !pluginInstalled)
                || root == null
                || frame == null
                || frame.isRecycled()
                || destination == null
                || destination.isEmpty()) {
            return;
        }
        State state = states.get(root);
        if (state == null || state.appliedAmbient) return;
        long now = SystemClock.uptimeMillis();
        if (now - state.lastVideoSample < VIDEO_SAMPLE_INTERVAL_MS) return;
        state.lastVideoSample = now;

        RectF bar = state.localBarBounds;
        if (bar.isEmpty()) {
            float density = root.getResources().getDisplayMetrics().density;
            float fallbackBottom = Math.max(1f, Math.min(root.getHeight(), 44f * density));
            bar.set(0f, 0f, root.getWidth(), fallbackBottom);
        }
        int frameW = frame.getWidth();
        int frameH = frame.getHeight();
        if (frameW <= 0 || frameH <= 0) return;

        double luma = 0;
        for (int y = 0; y < 2; y++) {
            int py =
                    Math.max(
                            0,
                            Math.min(
                                    frameH - 1,
                                    (int)
                                            ((bar.top
                                                            + bar.height() * (y + 0.5f) / 2f
                                                            - destination.top)
                                                    / destination.height()
                                                    * frameH)));
            for (int x = 0; x < 6; x++) {
                int px =
                        Math.max(
                                0,
                                Math.min(
                                        frameW - 1,
                                        (int)
                                                ((bar.left
                                                                + bar.width() * (x + 0.5f) / 6f
                                                                - destination.left)
                                                        / destination.width()
                                                        * frameW)));
                luma += ArtworkSamples.luminance(frame.getPixel(px, py));
            }
        }
        boolean dark = shouldUseDarkIcons(luma / 12.0, state.appliedDark);
        applyOverride(root, state, dark, false, 0);
    }

    static void clear(ViewGroup root) {
        if (root == null) return;
        pendingSamples.remove(root);
        State state = states.remove(root);
        if (state == null) return;
        ArrayList<Object> toRestore = new ArrayList<>();
        if (installed) {
            if (states.isEmpty()) {
                synchronized (overrides) {
                    for (Object helper : overrides.keySet()) {
                        if (helper != null) toRestore.add(helper);
                    }
                    overrides.clear();
                }
            } else {
                Object helper = state.appliedHelper.get();
                if (helper != null && !isHelperInOtherState(helper)) {
                    overrides.remove(helper);
                    toRestore.add(helper);
                }
            }
        }
        if (!toRestore.isEmpty() && updateMethod != null) {
            Boolean previousRestoring = restoring.get();
            restoring.set(Boolean.TRUE);
            try {
                for (int i = 0, n = toRestore.size(); i < n; i++) {
                    try {
                        updateMethod.invoke(toRestore.get(i));
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("Status bar icon contrast restore", error);
                    }
                }
            } finally {
                restoring.set(previousRestoring);
            }
        }

        ArrayList<Object> aodBatteriesToRestore = new ArrayList<>();
        if (installed && batteryMeterViewClass != null) {
            if (states.isEmpty()) {
                synchronized (aodBatteryOverrides) {
                    for (Object battery : aodBatteryOverrides.keySet()) {
                        if (battery != null) aodBatteriesToRestore.add(battery);
                    }
                    aodBatteryOverrides.clear();
                }
            } else {
                for (int i = 0, n = state.appliedAodBatteries.size(); i < n; i++) {
                    Object battery = state.appliedAodBatteries.get(i).get();
                    if (battery != null && !isAodBatteryInOtherState(battery)) {
                        aodBatteryOverrides.remove(battery);
                        if (!containsIdentity(aodBatteriesToRestore, battery)) {
                            aodBatteriesToRestore.add(battery);
                        }
                    }
                }
            }
        }
        if (!aodBatteriesToRestore.isEmpty() && batteryOnDarkChangedMethod != null) {
            Boolean previousWriting = writingAodBattery.get();
            writingAodBattery.set(Boolean.TRUE);
            try {
                for (int i = 0, n = aodBatteriesToRestore.size(); i < n; i++) {
                    Object battery = aodBatteriesToRestore.get(i);
                    try {
                        AodBatteryState saved = aodBatteryNativeStates.remove(battery);
                        float savedIntensity = saved != null ? saved.darkIntensity() : 0.0f;
                        int savedTint = saved != null ? saved.darkTint() : LIGHT_STATUS_COLOR;
                        batteryOnDarkChangedMethod.invoke(
                                battery, new ArrayList<>(), savedIntensity, savedTint);
                        invalidateAodBatteryAndSiblings(battery, AOD_DND_LIGHT_COLOR);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("AOD battery contrast restore", error);
                    }
                }
            } finally {
                writingAodBattery.set(previousWriting);
            }
        }

        ArrayList<Object> nioToRestore = new ArrayList<>();
        if (pluginInstalled) {
            if (states.isEmpty()) {
                synchronized (nioOverrides) {
                    for (Object container : nioOverrides.keySet()) {
                        if (container != null) nioToRestore.add(container);
                    }
                    nioOverrides.clear();
                }
            } else {
                for (int i = 0, n = state.appliedNioContainers.size(); i < n; i++) {
                    Object container = state.appliedNioContainers.get(i).get();
                    if (container != null && !isNioInOtherState(container)) {
                        nioOverrides.remove(container);
                        if (!containsIdentity(nioToRestore, container)) {
                            nioToRestore.add(container);
                        }
                    }
                }
            }
        }
        if (!nioToRestore.isEmpty()
                && nioUpdateIconColorMethod != null
                && nioSetWallpaperColorTypeMethod != null) {
            Boolean previousWriting = writingNio.get();
            writingNio.set(Boolean.TRUE);
            try {
                for (int i = 0, n = nioToRestore.size(); i < n; i++) {
                    Object container = nioToRestore.get(i);
                    try {
                        Integer savedType = nioNativeTypes.remove(container);
                        if (savedType != null) {
                            nioSetWallpaperColorTypeMethod.invoke(container, savedType);
                        }
                        Integer savedColor = nioNativeColors.remove(container);
                        if (savedColor != null) {
                            nioUpdateIconColorMethod.invoke(container, savedColor);
                        }
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("Notification icon contrast restore", error);
                    }
                }
            } finally {
                writingNio.set(previousWriting);
            }
        }
    }

    private static boolean hasActiveAodBatteryOverride(State state) {
        if (state.appliedAodBatteries.isEmpty()) return trackedAodBatteries.isEmpty();
        for (int i = 0, n = state.appliedAodBatteries.size(); i < n; i++) {
            Object battery = state.appliedAodBatteries.get(i).get();
            if (battery != null && aodBatteryOverrides.containsKey(battery)) return true;
        }
        return false;
    }

    private static boolean hasActiveNioOverride(State state) {
        if (state.appliedNioContainers.isEmpty()) return trackedNioContainers.isEmpty();
        for (int i = 0, n = state.appliedNioContainers.size(); i < n; i++) {
            Object container = state.appliedNioContainers.get(i).get();
            if (container != null && nioOverrides.containsKey(container)) return true;
        }
        return false;
    }

    private static boolean isHelperInOtherState(Object helper) {
        for (State other : states.values()) {
            if (other != null && other.appliedHelper.get() == helper) return true;
        }
        return false;
    }

    private static boolean isAodBatteryInOtherState(Object battery) {
        for (State other : states.values()) {
            if (other == null) continue;
            for (int i = 0, n = other.appliedAodBatteries.size(); i < n; i++) {
                if (other.appliedAodBatteries.get(i).get() == battery) return true;
            }
        }
        return false;
    }

    private static boolean isNioInOtherState(Object container) {
        for (State other : states.values()) {
            if (other == null) continue;
            for (int i = 0, n = other.appliedNioContainers.size(); i < n; i++) {
                if (other.appliedNioContainers.get(i).get() == container) return true;
            }
        }
        return false;
    }

    private static boolean containsIdentity(List<Object> list, Object item) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i) == item) return true;
        }
        return false;
    }

    private static boolean applyOverride(
            ViewGroup root, State state, boolean dark, boolean ambient, int dimOverlayArgb) {
        ArrayList<Object> helperTargets = new ArrayList<>();
        Object primary = null;
        if (installed) {
            View bar = findStatusBarView(root, state);
            primary = readHelper(bar, state);
            if (primary != null) trackedHelpers.add(primary);

            synchronized (trackedHelpers) {
                helperTargets.ensureCapacity(trackedHelpers.size() + 1);
                for (Object helper : trackedHelpers) {
                    if (helper != null) helperTargets.add(helper);
                }
            }
            if (primary != null && !containsIdentity(helperTargets, primary)) {
                helperTargets.add(primary);
            }
        }

        ArrayList<Object> aodBatteryTargets = new ArrayList<>();
        if (installed && batteryMeterViewClass != null) {
            synchronized (trackedAodBatteries) {
                for (Object battery : trackedAodBatteries) {
                    if (battery != null
                            && isAodBatteryView(battery)
                            && matchesDisplay(root, battery)
                            && !containsIdentity(aodBatteryTargets, battery)) {
                        aodBatteryTargets.add(battery);
                    }
                }
            }
            if (aodBatteryTargets.isEmpty() || state.appliedAodBatteries.isEmpty()) {
                collectAodBatteryViews(root, aodBatteryTargets);
            }
        }

        ArrayList<Object> nioTargets = new ArrayList<>();
        if (pluginInstalled && nioContainerClass != null) {
            synchronized (trackedNioContainers) {
                for (Object container : trackedNioContainers) {
                    if (container != null
                            && container.getClass() == nioContainerClass
                            && matchesDisplay(root, container)
                            && !containsIdentity(nioTargets, container)) {
                        nioTargets.add(container);
                    }
                }
            }
            if (nioTargets.isEmpty() || state.appliedNioContainers.isEmpty()) {
                collectNioContainers(root, nioTargets);
            }
        }

        if (helperTargets.isEmpty() && aodBatteryTargets.isEmpty() && nioTargets.isEmpty()) {
            return false;
        }

        int nioColor = nioColorFor(dark, ambient);
        boolean allMatch =
                state.appliedDark != null
                        && state.appliedDark.booleanValue() == dark
                        && state.appliedAmbient == ambient
                        && state.appliedDimArgb == dimOverlayArgb
                        && state.appliedHelperCount == helperTargets.size()
                        && state.appliedAodBatteryCount == aodBatteryTargets.size()
                        && state.appliedNioCount == nioTargets.size();
        if (allMatch) {
            Boolean expectedHelper = dark;
            for (int i = 0, n = helperTargets.size(); i < n; i++) {
                if (!expectedHelper.equals(overrides.get(helperTargets.get(i)))) {
                    allMatch = false;
                    break;
                }
            }
        }
        if (allMatch) {
            Boolean expectedBattery = dark;
            for (int i = 0, n = aodBatteryTargets.size(); i < n; i++) {
                if (!expectedBattery.equals(aodBatteryOverrides.get(aodBatteryTargets.get(i)))) {
                    allMatch = false;
                    break;
                }
            }
        }
        if (allMatch) {
            Integer expectedNio = nioColor;
            for (int i = 0, n = nioTargets.size(); i < n; i++) {
                if (!expectedNio.equals(nioOverrides.get(nioTargets.get(i)))) {
                    allMatch = false;
                    break;
                }
            }
        }
        if (allMatch) return true;

        boolean anyApplied = false;
        for (int i = 0, n = helperTargets.size(); i < n; i++) {
            Object helper = helperTargets.get(i);
            overrides.put(helper, dark);
            try {
                pushToHelper(helper, dark);
                anyApplied = true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                DressroomModule.note("Status bar icon contrast apply", error);
            }
        }

        for (int i = 0, n = aodBatteryTargets.size(); i < n; i++) {
            Object battery = aodBatteryTargets.get(i);
            try {
                if (!aodBatteryNativeStates.containsKey(battery)) {
                    aodBatteryNativeStates.putIfAbsent(battery, readAodBatteryState(battery));
                }
                aodBatteryOverrides.put(battery, dark);
                pushToAodBattery(battery, dark);
                anyApplied = true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                DressroomModule.note("AOD battery contrast apply", error);
            }
        }

        for (int i = 0, n = nioTargets.size(); i < n; i++) {
            Object container = nioTargets.get(i);
            try {
                if (!nioNativeColors.containsKey(container) && nioGetIconColorMethod != null) {
                    nioNativeColors.putIfAbsent(
                            container, (Integer) nioGetIconColorMethod.invoke(container));
                }
                if (!nioNativeTypes.containsKey(container)
                        && nioGetWallpaperColorTypeMethod != null) {
                    nioNativeTypes.putIfAbsent(
                            container, (Integer) nioGetWallpaperColorTypeMethod.invoke(container));
                }
                nioOverrides.put(container, nioColor);
                pushToNio(container, nioColor, dark);
                anyApplied = true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                DressroomModule.note("Notification icon contrast apply", error);
            }
        }

        if (!anyApplied) return false;
        if (!helperTargets.isEmpty()) {
            state.appliedHelper =
                    new WeakReference<>(primary != null ? primary : helperTargets.get(0));
        }
        state.appliedAodBatteries.clear();
        for (int i = 0, n = aodBatteryTargets.size(); i < n; i++) {
            state.appliedAodBatteries.add(new WeakReference<>(aodBatteryTargets.get(i)));
        }
        state.appliedNioContainers.clear();
        for (int i = 0, n = nioTargets.size(); i < n; i++) {
            state.appliedNioContainers.add(new WeakReference<>(nioTargets.get(i)));
        }
        state.appliedDark = dark;
        state.appliedAmbient = ambient;
        state.appliedDimArgb = dimOverlayArgb;
        state.appliedHelperCount = helperTargets.size();
        state.appliedAodBatteryCount = aodBatteryTargets.size();
        state.appliedNioCount = nioTargets.size();
        return true;
    }

    private static AodBatteryState readAodBatteryState(Object battery) {
        float intensity = 0.0f;
        int tint = LIGHT_STATUS_COLOR;
        try {
            if (batteryDarkIntensityField != null) {
                intensity = batteryDarkIntensityField.getFloat(battery);
            }
            if (batteryDarkTintField != null) {
                tint = batteryDarkTintField.getInt(battery);
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
        return new AodBatteryState(intensity, tint);
    }

    private static void pushToAodBattery(Object battery, boolean dark)
            throws ReflectiveOperationException {
        if (battery == null || batteryOnDarkChangedMethod == null) return;
        Boolean previousWriting = writingAodBattery.get();
        writingAodBattery.set(Boolean.TRUE);
        try {
            batteryOnDarkChangedMethod.invoke(
                    battery,
                    new ArrayList<>(),
                    aodBatteryDarkIntensity(dark),
                    dark ? DARK_STATUS_COLOR : LIGHT_STATUS_COLOR);
            invalidateAodBatteryAndSiblings(battery, aodDndColorFor(dark));
        } finally {
            writingAodBattery.set(previousWriting);
        }
    }

    private static void invalidateAodBatteryAndSiblings(Object battery, int dndColor) {
        if (batteryIconViewField != null) {
            try {
                if (batteryIconViewField.get(battery) instanceof View iconView) {
                    iconView.invalidate();
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        if (battery instanceof View view) {
            view.invalidate();
            if (view.getParent() instanceof ViewGroup parentGroup) {
                for (int i = 0, n = parentGroup.getChildCount(); i < n; i++) {
                    View sibling = parentGroup.getChildAt(i);
                    if (sibling != view && sibling instanceof ImageView dndIcon) {
                        dndIcon.setColorFilter(dndColor);
                        dndIcon.invalidate();
                    }
                }
            }
        }
    }

    private static boolean isAodBatteryView(Object candidate) {
        if (candidate == null
                || batteryMeterViewClass == null
                || !batteryMeterViewClass.isInstance(candidate)) {
            return false;
        }
        if (candidate instanceof View view && isAodBatteryTag(view.getTag())) {
            return true;
        }
        if (aodBatteryFlagField != null) {
            try {
                return aodBatteryFlagField.getBoolean(candidate);
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private static void collectAodBatteryViews(View view, List<Object> out) {
        if (view == null || batteryMeterViewClass == null) return;
        if (isAodBatteryView(view)) {
            trackedAodBatteries.add(view);
            if (!containsIdentity(out, view)) out.add(view);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collectAodBatteryViews(group.getChildAt(i), out);
            }
        }
    }

    private static void pushToNio(Object container, int color, boolean dark)
            throws ReflectiveOperationException {
        if (container == null
                || nioUpdateIconColorMethod == null
                || nioSetWallpaperColorTypeMethod == null) {
            return;
        }
        Boolean previousWriting = writingNio.get();
        writingNio.set(Boolean.TRUE);
        try {
            nioSetWallpaperColorTypeMethod.invoke(
                    container, dark ? FONT_COLOR_TYPE_DARK : FONT_COLOR_TYPE_LIGHT);
            nioUpdateIconColorMethod.invoke(container, color);
        } finally {
            writingNio.set(previousWriting);
        }
    }

    private static boolean matchesDisplay(ViewGroup root, Object candidate) {
        if (!(candidate instanceof View view) || !view.isAttachedToWindow()) return false;
        Display rootDisplay = root.getDisplay();
        Display viewDisplay = view.getDisplay();
        if (rootDisplay != null && viewDisplay != null) {
            return rootDisplay.getDisplayId() == viewDisplay.getDisplayId();
        }
        if (root.getWindowToken() != null && view.getWindowToken() != null) {
            return view.getWindowToken() == root.getWindowToken();
        }
        return view.getRootView() == root;
    }

    private static void collectNioContainers(View view, List<Object> out) {
        if (view == null || nioContainerClass == null) return;
        if (view.getClass() == nioContainerClass) {
            trackedNioContainers.add(view);
            if (!containsIdentity(out, view)) out.add(view);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collectNioContainers(group.getChildAt(i), out);
            }
        }
    }

    private static void pushToHelper(Object helper, boolean dark)
            throws ReflectiveOperationException {
        if (helper == null) return;
        typeField.setInt(helper, dark ? FONT_COLOR_TYPE_DARK : FONT_COLOR_TYPE_LIGHT);
        colorField.setInt(helper, dark ? DARK_STATUS_COLOR : LIGHT_STATUS_COLOR);
        intensityField.setFloat(helper, dark ? 1f : 0f);
        Object listener = listenerField.get(helper);
        if (listener != null) {
            onWallpaperUpdatedMethod.invoke(listener);
        }
    }

    private static Object readHelper(View bar, State state) {
        if (bar != null && helperField != null) {
            try {
                Object helper = helperField.get(bar);
                if (helper != null) return helper;
            } catch (IllegalAccessException | RuntimeException error) {
                DressroomModule.note("Status bar wallpaper helper read", error);
            }
        }
        return state.appliedHelper.get();
    }

    private static View findStatusBarView(ViewGroup root, State state) {
        View cached = state.statusBarView.get();
        if (cached != null
                && cached.isAttachedToWindow()
                && cached.getRootView() == root
                && keyguardStatusBarViewClass != null
                && keyguardStatusBarViewClass.isInstance(cached)) {
            return cached;
        }
        View found = null;
        if (keyguardStatusBarViewClass != null) {
            int id =
                    root.getResources()
                            .getIdentifier(
                                    "keyguard_header", "id", root.getContext().getPackageName());
            if (id != 0) {
                View candidate = root.findViewById(id);
                if (keyguardStatusBarViewClass.isInstance(candidate)) found = candidate;
            }
            if (found == null) found = findByClass(root, keyguardStatusBarViewClass);
        }
        state.statusBarView = new WeakReference<>(found);
        return found;
    }

    private static View findByClass(View view, Class<?> target) {
        if (target.isInstance(view)) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = findByClass(group.getChildAt(i), target);
                if (child != null) return child;
            }
        }
        return null;
    }
}
