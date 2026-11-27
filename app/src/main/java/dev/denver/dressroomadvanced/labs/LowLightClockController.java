package dev.denver.dressroomadvanced;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drives and styles SystemUI's dormant {@code LowLightClockDreamService} into a StandBy nightstand
 * clock while charging/dreaming in dark rooms. Only listens to the ambient light sensor while
 * plugged in or dreaming, supports instant 1-tap preview from Dressroom Extras, and cleanly
 * restores both {@code DreamManager.setSystemDreamComponent(null)} and the disabled manifest
 * component state when turned off.
 */
final class LowLightClockController {
    private static final String SERVICE_CLASS =
            "com.android.systemui.lowlightclock.LowLightClockDreamService";
    private static final ComponentName SERVICE_COMPONENT =
            new ComponentName("com.android.systemui", SERVICE_CLASS);
    private static final long BURN_IN_INTERVAL_MS = 60_000L;

    private static final String[] DISPLAY_BOLD_FONTS = {
        "/system/fonts/Clock2025.ttf",
        "/system/fonts/Clock2023.ttf",
        "/system/fonts/Clock2021.ttf",
        "/system/fonts/SamsungSharpSans-Bold.ttf",
        "/system/fonts/SECNum-3R.ttf"
    };

    private static final class DreamUiState {
        View burnInContainer;
        TextClock dateClock;
        TextView chargingView;
        boolean burnInEnabled;
        int brightnessPercent;
        int driftStep;
        Runnable driftTask;
    }

    private static final Map<DreamService, DreamUiState> activeDreams =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean installed;
    private static boolean listening;
    private static boolean previewObserved;
    private static boolean featureEnabled;
    private static int enterLux = AodTweaks.DEFAULT_LOW_LIGHT_LUX;
    private static volatile Bundle activePreviewExtras;
    private static volatile boolean previewActive;

    private static Context appContext;
    private static Object dreamManager;
    private static Method setSystemDreamComponent;
    private static Method isDreamingMethod;
    private static Method startDreamMethod;
    private static SensorManager sensorManager;
    private static Sensor lightSensor;

    private static boolean componentEnabledByUs;
    private static boolean systemDreamApplied;
    private static boolean receiverRegistered;
    private static boolean sensorRegistered;
    private static boolean pluggedIn;
    private static boolean dreaming;

    private static final AodTweaks.LowLightGate gate = new AodTweaks.LowLightGate();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable evaluateRunnable = LowLightClockController::evaluatePending;

    private static final BroadcastReceiver stateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !featureEnabled) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                        pluggedIn = true;
                        updateMonitoring();
                    } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                        pluggedIn = false;
                        updateMonitoring();
                    } else if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                        dreaming = true;
                        updateMonitoring();
                    } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                        dreaming = false;
                        updateMonitoring();
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)
                            || Intent.ACTION_SCREEN_OFF.equals(action)) {
                        refreshLiveStates();
                        updateMonitoring();
                    }
                }
            };

    private static final SensorEventListener lightListener =
            new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (!featureEnabled
                            || event == null
                            || event.values == null
                            || event.values.length == 0) return;
                    long remaining =
                            gate.onLux(
                                    event.values[0],
                                    enterLux,
                                    dreaming,
                                    SystemClock.uptimeMillis());
                    applyGateState(remaining);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };

    private LowLightClockController() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed || loader == null) return;
        Class<?> serviceClass;
        try {
            serviceClass = Class.forName(SERVICE_CLASS, false, loader);
            Class<?> dreamManagerClass = Class.forName("android.app.DreamManager");
            setSystemDreamComponent =
                    dreamManagerClass.getMethod("setSystemDreamComponent", ComponentName.class);
            isDreamingMethod = dreamManagerClass.getMethod("isDreaming");
            startDreamMethod = dreamManagerClass.getMethod("startDream");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return;
        }
        installDreamUiHooks(module, serviceClass);
        installed = true;
        if (!listening) {
            listening = true;
            RuntimeExtras.listen(() -> main.post(LowLightClockController::syncFromExtras));
        }
        main.post(LowLightClockController::syncFromExtras);
    }

    private static void installDreamUiHooks(DressroomModule module, Class<?> serviceClass) {
        try {
            Method attached = serviceClass.getDeclaredMethod("onAttachedToWindow");
            module.hook(attached)
                    .setId("dressroom-advanced.lowlight-ui-attach")
                    .intercept(
                            chain -> {
                                Object res = chain.proceed();
                                if (chain.getThisObject() instanceof DreamService service) {
                                    styleDream(service);
                                }
                                return res;
                            });
            module.deoptimize(attached);

            Method started = serviceClass.getDeclaredMethod("onDreamingStarted");
            module.hook(started)
                    .setId("dressroom-advanced.lowlight-ui-start")
                    .intercept(
                            chain -> {
                                Object res = chain.proceed();
                                if (chain.getThisObject() instanceof DreamService service) {
                                    onDreamStarted(service);
                                }
                                return res;
                            });
            module.deoptimize(started);

            Method wakeUp = serviceClass.getDeclaredMethod("onWakeUp");
            module.hook(wakeUp)
                    .setId("dressroom-advanced.lowlight-ui-wakeup")
                    .intercept(
                            chain -> {
                                if (chain.getThisObject() instanceof DreamService service) {
                                    onDreamWakeUp(service);
                                }
                                return chain.proceed();
                            });
            module.deoptimize(wakeUp);

            Method detached = serviceClass.getDeclaredMethod("onDetachedFromWindow");
            module.hook(detached)
                    .setId("dressroom-advanced.lowlight-ui-detach")
                    .intercept(
                            chain -> {
                                if (chain.getThisObject() instanceof DreamService service) {
                                    onDreamDetached(service);
                                }
                                return chain.proceed();
                            });
            module.deoptimize(detached);
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.report("Optional StandBy low-light clock styling unavailable", error);
        }
    }

    private static Bundle currentStyleExtras(Context ctx) {
        Bundle preview = activePreviewExtras;
        if (preview != null) return preview;
        try {
            return RuntimeExtras.get(ctx);
        } catch (RuntimeException unavailable) {
            return RuntimeExtras.defaults();
        }
    }

    private static void styleDream(DreamService service) {
        try {
            Context ctx = service.getApplicationContext();
            if (ctx == null) ctx = service;
            Bundle extras = currentStyleExtras(ctx);
            if (!previewActive && !extras.getBoolean(AodTweaks.LOW_LIGHT_CLOCK)) return;

            Resources res = service.getResources();
            int clockId = res.getIdentifier("low_light_text_clock", "id", "com.android.systemui");
            int chargingId =
                    res.getIdentifier("charging_status_text_view", "id", "com.android.systemui");
            View clockView = clockId != 0 ? service.findViewById(clockId) : null;
            if (!(clockView instanceof TextClock clock)) return;
            View chargingRaw = chargingId != 0 ? service.findViewById(chargingId) : null;
            TextView chargingView = chargingRaw instanceof TextView tv ? tv : null;

            ViewGroup root = clock.getParent() instanceof ViewGroup vg ? vg : null;
            if (root == null) return;

            int[] palette = AodTweaks.lowLightPalette(AodTweaks.lowLightTheme(extras));
            int primaryColor = palette[0];
            int secondaryColor = palette[1];
            int glowColor = palette[2];
            int fontStyle = AodTweaks.lowLightFont(extras);
            boolean showDate = extras.getBoolean(AodTweaks.LOW_LIGHT_SHOW_DATE, true);
            boolean burnIn = extras.getBoolean(AodTweaks.LOW_LIGHT_BURN_IN, true);
            int brightness = AodTweaks.lowLightBrightness(extras);

            root.setBackgroundColor(Color.BLACK);
            root.setClipChildren(false);
            root.setClipToPadding(false);

            DisplayMetrics dm = res.getDisplayMetrics();
            boolean landscape =
                    res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                            || dm.widthPixels > dm.heightPixels;
            float density = dm.density;
            int padH = Math.round(24f * density);

            LinearLayout container = new LinearLayout(service);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            FrameLayout.LayoutParams containerLp =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER);
            int bottomReserve = Math.round((landscape ? 36f : 64f) * density);
            container.setPadding(padH, Math.round(16f * density), padH, bottomReserve);

            TextClock dateClock = null;
            if (showDate) {
                dateClock = new TextClock(service);
                String pattern =
                        DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMd");
                dateClock.setFormat12Hour(pattern);
                dateClock.setFormat24Hour(pattern);
                dateClock.setAllCaps(true);
                dateClock.setTextColor(secondaryColor);
                dateClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 18f : 15f);
                dateClock.setLetterSpacing(0.16f);
                dateClock.setTypeface(Typeface.create(Typeface.SANS_SERIF, 600, false));
                dateClock.setGravity(Gravity.CENTER);
                dateClock.setIncludeFontPadding(false);
                dateClock.setAlpha(0f);
                LinearLayout.LayoutParams dateLp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                dateLp.bottomMargin = Math.round((landscape ? 4f : 10f) * density);
                container.addView(dateClock, dateLp);
            }

            root.removeView(clock);
            Typeface clockTypeface = resolveClockTypeface(ctx, extras, fontStyle);
            clock.setTypeface(clockTypeface);
            clock.setTextColor(primaryColor);
            clock.setFontFeatureSettings("tnum");
            clock.setLetterSpacing(clockLetterSpacing(fontStyle));
            clock.setIncludeFontPadding(false);
            clock.setMaxLines(1);
            clock.setSingleLine(true);
            clock.setGravity(Gravity.CENTER);
            clock.setShadowLayer(Math.round(20f * density), 0f, 0f, glowColor);
            int targetHeight =
                    Math.max(
                            Math.round(96f * density),
                            Math.round(dm.heightPixels * (landscape ? 0.54f : 0.23f)));
            int maxSp = landscape ? 240 : 150;
            int minSp = landscape ? 48 : 36;
            clock.setAutoSizeTextTypeUniformWithConfiguration(
                    minSp, maxSp, 2, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams clockLp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, targetHeight);
            container.addView(clock, clockLp);
            root.addView(container, 0, containerLp);

            if (chargingView != null) {
                chargingView.setTextColor(secondaryColor);
                chargingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 14f : 13f);
                chargingView.setLetterSpacing(0.05f);
                chargingView.setTypeface(Typeface.create(Typeface.SANS_SERIF, 500, false));
                chargingView.setShadowLayer(Math.round(8f * density), 0f, 0f, glowColor);
                ViewGroup.LayoutParams rawLp = chargingView.getLayoutParams();
                if (rawLp instanceof ViewGroup.MarginLayoutParams mlp) {
                    mlp.bottomMargin = Math.round((landscape ? 20f : 32f) * density);
                    chargingView.setLayoutParams(mlp);
                }
            }

            DreamUiState state = new DreamUiState();
            state.burnInContainer = container;
            state.dateClock = dateClock;
            state.chargingView = chargingView;
            state.burnInEnabled = burnIn;
            state.brightnessPercent = brightness;
            activeDreams.put(service, state);
        } catch (RuntimeException error) {
            Log.w(DressroomModule.TAG, "Failed to style StandBy low-light clock", error);
        }
    }

    private static void onDreamStarted(DreamService service) {
        DreamUiState state = activeDreams.get(service);
        if (state == null) return;
        float targetBrightness = AodTweaks.lowLightBrightnessFloat(state.brightnessPercent);
        try {
            Reflect.call(service, "setScreenBrightness", targetBrightness);
        } catch (ReflectiveOperationException | RuntimeException fallback) {
            try {
                if (service.getWindow() != null) {
                    android.view.WindowManager.LayoutParams lp =
                            service.getWindow().getAttributes();
                    lp.screenBrightness = targetBrightness;
                    service.getWindow().setAttributes(lp);
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (state.dateClock != null) {
            float offset = 16f * service.getResources().getDisplayMetrics().density;
            state.dateClock.setTranslationY(offset);
            state.dateClock.setAlpha(0f);
            state.dateClock
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(600L)
                    .setStartDelay(120L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (state.burnInEnabled && state.burnInContainer != null) {
            float density = service.getResources().getDisplayMetrics().density;
            float maxX = 18f * density;
            float maxY = 12f * density;
            state.driftTask =
                    new Runnable() {
                        @Override
                        public void run() {
                            if (activeDreams.get(service) != state) return;
                            state.driftStep++;
                            float[] offset =
                                    AodTweaks.lowLightDriftOffset(state.driftStep, maxX, maxY);
                            state.burnInContainer
                                    .animate()
                                    .translationX(offset[0])
                                    .translationY(offset[1])
                                    .setDuration(1200L)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                            if (state.chargingView != null) {
                                state.chargingView
                                        .animate()
                                        .translationX(offset[0] * 0.65f)
                                        .translationY(offset[1] * 0.5f)
                                        .setDuration(1200L)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .start();
                            }
                            main.postDelayed(this, BURN_IN_INTERVAL_MS);
                        }
                    };
            main.postDelayed(state.driftTask, BURN_IN_INTERVAL_MS);
        }
    }

    private static void onDreamWakeUp(DreamService service) {
        DreamUiState state = activeDreams.get(service);
        if (state == null) return;
        if (state.driftTask != null) {
            main.removeCallbacks(state.driftTask);
            state.driftTask = null;
        }
        if (state.dateClock != null) {
            state.dateClock.animate().alpha(0f).setDuration(220L).start();
        }
    }

    private static void onDreamDetached(DreamService service) {
        DreamUiState state = activeDreams.remove(service);
        if (state != null && state.driftTask != null) {
            main.removeCallbacks(state.driftTask);
            state.driftTask = null;
        }
        if (previewActive) {
            previewActive = false;
            activePreviewExtras = null;
            main.post(LowLightClockController::finishPreviewCleanup);
        }
    }

    private static void finishPreviewCleanup() {
        Context ctx = resolveApplication();
        if (ctx == null) return;
        if (!featureEnabled) {
            disableAll(ctx);
            return;
        }
        refreshLiveStates();
        if (!(gate.isDark() && (pluggedIn || dreaming)) && systemDreamApplied) {
            applySystemDream(false);
        }
        updateMonitoring();
    }

    private static Typeface resolveClockTypeface(Context ctx, Bundle extras, int fontStyle) {
        if (fontStyle == 4) {
            String fontFile = extras.getString(ExtrasPreferences.LYRICS_FONT_FILE, "");
            if (!fontFile.isEmpty()) {
                try {
                    Typeface custom =
                            VariableFonts.lyricsTypeface(
                                    ctx,
                                    fontFile,
                                    extras.getBundle(ExtrasPreferences.LYRICS_FONT_AXES),
                                    extras.getInt(
                                            ExtrasPreferences.LYRICS_FONT_WEIGHT,
                                            ExtrasPreferences.DEFAULT_LYRICS_FONT_WEIGHT));
                    if (custom != null) return custom;
                } catch (RuntimeException ignored) {
                }
            }
            fontStyle = 0;
        }
        if (fontStyle == 1) return Typeface.create(Typeface.SANS_SERIF, 200, false);
        if (fontStyle == 2) return Typeface.create(Typeface.SERIF, 700, false);
        if (fontStyle == 3) return Typeface.create(Typeface.MONOSPACE, 700, false);
        for (String path : DISPLAY_BOLD_FONTS) {
            File file = new File(path);
            if (file.isFile() && file.canRead()) {
                try {
                    return Typeface.createFromFile(file);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return Typeface.create(Typeface.SANS_SERIF, 800, false);
    }

    private static float clockLetterSpacing(int fontStyle) {
        return switch (fontStyle) {
            case 1 -> -0.015f;
            case 2 -> -0.03f;
            case 3 -> -0.02f;
            default -> -0.045f;
        };
    }

    private static Context resolveApplication() {
        if (appContext != null) return appContext;
        try {
            Context app =
                    (Context)
                            Class.forName("android.app.ActivityThread")
                                    .getMethod("currentApplication")
                                    .invoke(null);
            if (app != null) appContext = app;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return appContext;
    }

    private static void ensurePreviewObserver(Context ctx) {
        if (previewObserved || ctx == null) return;
        try {
            ctx.getContentResolver()
                    .registerContentObserver(
                            SettingsProvider.LOW_LIGHT_PREVIEW_URI,
                            false,
                            new ContentObserver(main) {
                                @Override
                                public void onChange(boolean selfChange) {
                                    triggerPreviewNow();
                                }
                            });
            previewObserved = true;
        } catch (RuntimeException ignored) {
        }
    }

    private static void triggerPreviewNow() {
        Context ctx = resolveApplication();
        if (ctx == null || !ensureServices(ctx) || startDreamMethod == null) return;
        Bundle preview;
        try {
            preview =
                    ctx.getContentResolver()
                            .call(SettingsProvider.URI, "readLowLightPreview", null, null);
        } catch (RuntimeException unavailable) {
            return;
        }
        if (preview == null || preview.isEmpty()) return;
        activePreviewExtras = preview;
        previewActive = true;
        if (!componentEnabledByUs) {
            setServiceComponentEnabled(ctx, true);
            componentEnabledByUs = true;
        }
        applySystemDream(true);
        try {
            startDreamMethod.invoke(dreamManager);
        } catch (ReflectiveOperationException | RuntimeException error) {
            previewActive = false;
            activePreviewExtras = null;
            Log.w(DressroomModule.TAG, "Failed to start low-light clock preview dream", error);
            finishPreviewCleanup();
        }
    }

    private static void syncFromExtras() {
        Context ctx = resolveApplication();
        if (ctx == null) return;
        ensurePreviewObserver(ctx);
        Bundle extras;
        try {
            extras = RuntimeExtras.get(ctx);
        } catch (RuntimeException unavailable) {
            return;
        }
        boolean wantEnabled = extras.getBoolean(AodTweaks.LOW_LIGHT_CLOCK);
        int wantLux = AodTweaks.lowLightLux(extras);
        enterLux = wantLux;
        if (!wantEnabled) {
            if (!previewActive && (featureEnabled || systemDreamApplied || componentEnabledByUs)) {
                disableAll(ctx);
            }
            return;
        }
        if (!ensureServices(ctx)) return;
        boolean wasEnabled = featureEnabled;
        featureEnabled = true;
        if (!componentEnabledByUs) {
            setServiceComponentEnabled(ctx, true);
            componentEnabledByUs = true;
        }
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_DREAMING_STARTED);
            filter.addAction(Intent.ACTION_DREAMING_STOPPED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            ctx.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        refreshLiveStates();
        updateMonitoring();
        if (wasEnabled && sensorRegistered) {
            evaluatePending();
        }
    }

    private static boolean ensureServices(Context ctx) {
        if (dreamManager == null) {
            try {
                Class<?> dreamManagerClass = Class.forName("android.app.DreamManager");
                dreamManager = ctx.getSystemService(dreamManagerClass);
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.w(DressroomModule.TAG, "DreamManager unavailable for low-light clock", error);
                return false;
            }
        }
        if (sensorManager == null) {
            sensorManager = ctx.getSystemService(SensorManager.class);
            if (sensorManager != null) {
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }
        }
        return dreamManager != null && sensorManager != null && lightSensor != null;
    }

    private static void refreshLiveStates() {
        Context ctx = appContext;
        if (ctx != null) {
            try {
                Intent battery =
                        ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                pluggedIn =
                        battery != null
                                && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            } catch (RuntimeException ignored) {
            }
        }
        if (dreamManager != null && isDreamingMethod != null) {
            try {
                dreaming = Boolean.TRUE.equals(isDreamingMethod.invoke(dreamManager));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private static void updateMonitoring() {
        if (!featureEnabled) return;
        boolean shouldWatch = pluggedIn || dreaming;
        if (shouldWatch) {
            if (!sensorRegistered && sensorManager != null && lightSensor != null) {
                sensorRegistered =
                        sensorManager.registerListener(
                                lightListener,
                                lightSensor,
                                SensorManager.SENSOR_DELAY_NORMAL,
                                main);
            }
            if (dreaming && gate.isDark() && !systemDreamApplied) {
                applySystemDream(true);
            }
            return;
        }
        unregisterSensor();
        gate.reset();
        if (systemDreamApplied && !previewActive) {
            applySystemDream(false);
        }
    }

    private static void evaluatePending() {
        if (!featureEnabled) return;
        long remaining = gate.evaluate(enterLux, dreaming, SystemClock.uptimeMillis());
        applyGateState(remaining);
    }

    private static void applyGateState(long remainingMs) {
        main.removeCallbacks(evaluateRunnable);
        if (remainingMs > 0L) {
            main.postDelayed(evaluateRunnable, remainingMs);
        }
        boolean wantDark = gate.isDark() && (pluggedIn || dreaming);
        if (wantDark != systemDreamApplied && !previewActive) {
            applySystemDream(wantDark);
        }
    }

    private static void applySystemDream(boolean enable) {
        if (dreamManager == null || setSystemDreamComponent == null) return;
        try {
            setSystemDreamComponent.invoke(dreamManager, enable ? SERVICE_COMPONENT : null);
            systemDreamApplied = enable;
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(DressroomModule.TAG, "Failed to update low-light system dream", error);
        }
    }

    private static void unregisterSensor() {
        main.removeCallbacks(evaluateRunnable);
        if (sensorRegistered && sensorManager != null) {
            try {
                sensorManager.unregisterListener(lightListener);
            } catch (RuntimeException ignored) {
            }
            sensorRegistered = false;
        }
    }

    private static void disableAll(Context ctx) {
        featureEnabled = false;
        unregisterSensor();
        gate.reset();
        if (receiverRegistered) {
            try {
                ctx.unregisterReceiver(stateReceiver);
            } catch (RuntimeException ignored) {
            }
            receiverRegistered = false;
        }
        if (systemDreamApplied) {
            applySystemDream(false);
        }
        if (componentEnabledByUs) {
            setServiceComponentEnabled(ctx, false);
            componentEnabledByUs = false;
        }
    }

    private static void setServiceComponentEnabled(Context ctx, boolean enable) {
        try {
            ctx.getPackageManager()
                    .setComponentEnabledSetting(
                            SERVICE_COMPONENT,
                            enable
                                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
        } catch (RuntimeException error) {
            Log.w(DressroomModule.TAG, "Failed to toggle LowLightClockDreamService state", error);
        }
    }
}
