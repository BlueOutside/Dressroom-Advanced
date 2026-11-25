package dev.denver.dressroomadvanced;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Exposes Samsung's hidden {@code Settings.System "now_bar_aod_enabled"} control in Extras and
 * synchronizes it cleanly inside SystemUI/AOD without overwriting untouched user settings.
 */
final class NowBarAodOption {
    static final String OVERRIDE_KEY = "nowBarAodOverride";
    static final String ENABLED_KEY = "nowBarAod";
    static final String SYSTEM_SETTING = "now_bar_aod_enabled";

    private static final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile boolean overrideActive;
    private static volatile boolean overrideEnabled = true;
    private static boolean listening;
    private static Context appContext;
    private static Uri targetUri;

    private static final class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }

    @FunctionalInterface
    interface AnchorFinder {
        List<DexSignatures.Body> find(Set<String> anchors)
                throws ReflectiveOperationException, IOException;
    }

    record Contract(Method getIntValue, Field keyUri) {}

    private NowBarAodOption() {}

    static boolean systemEnabled(Context context) {
        if (context == null) return true;
        try {
            return Settings.System.getInt(context.getContentResolver(), SYSTEM_SETTING, 1) != 0;
        } catch (RuntimeException unavailable) {
            return true;
        }
    }

    static boolean shownInEditor(Context context, Bundle extras) {
        if (extras != null && extras.getBoolean(OVERRIDE_KEY)) {
            return extras.getBoolean(ENABLED_KEY, true);
        }
        return systemEnabled(context);
    }

    static void setInEditor(Bundle extras, boolean enabled) {
        if (extras == null) return;
        extras.putBoolean(OVERRIDE_KEY, true);
        extras.putBoolean(ENABLED_KEY, enabled);
    }

    static int resolveEntryValue(boolean overridden, boolean enabled, int nativeValue) {
        return overridden ? (enabled ? 1 : 0) : nativeValue;
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (module.inEditorProcess() || loader == null || !installed.add(loader)) return;
        try {
            Class<?> helperClass;
            try {
                helperClass =
                        Class.forName(
                                "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper",
                                false,
                                loader);
            } catch (ClassNotFoundException absent) {
                return;
            }
            Contract contract =
                    resolve(
                            helperClass,
                            loader,
                            anchors -> DexSignatures.find(loader, anchors));
            if (contract != null) {
                var handle =
                        module.hook(contract.getIntValue())
                                .setId("dressroom-advanced.now-bar-aod-setting")
                                .intercept(
                                        chain -> {
                                            if (!overrideActive) return chain.proceed();
                                            Object entry = chain.getThisObject();
                                            Uri uri = (Uri) contract.keyUri().get(entry);
                                            if (uri != null && uri.equals(settingUri())) {
                                                return overrideEnabled ? 1 : 0;
                                            }
                                            return chain.proceed();
                                        });
                try {
                    module.deoptimize(contract.getIntValue());
                } catch (RuntimeException | Error error) {
                    handle.unhook();
                    throw error;
                }
            }
            if (!listening) {
                listening = true;
                RuntimeExtras.listen(NowBarAodOption::scheduleSync);
            }
            scheduleSync();
        } catch (Exception | LinkageError error) {
            installed.remove(loader);
            module.report("Optional Now Bar on AOD setting hook unavailable", error);
        }
    }

    private static Uri settingUri() {
        Uri uri = targetUri;
        if (uri == null) {
            uri = Settings.System.getUriFor(SYSTEM_SETTING);
            targetUri = uri;
        }
        return uri;
    }

    private static void scheduleSync() {
        if (Looper.myLooper() == Looper.getMainLooper()) syncState();
        else Main.HANDLER.post(NowBarAodOption::syncState);
    }

    private static void syncState() {
        try {
            if (appContext == null) {
                appContext =
                        (Context)
                                Class.forName("android.app.ActivityThread")
                                        .getMethod("currentApplication")
                                        .invoke(null);
            }
            if (appContext == null) return;
            Bundle extras = RuntimeExtras.get(appContext);
            boolean wasOverridden = overrideActive;
            boolean nowOverridden = extras.getBoolean(OVERRIDE_KEY);
            boolean nowEnabled = extras.getBoolean(ENABLED_KEY, true);
            overrideEnabled = nowEnabled;
            overrideActive = nowOverridden;
            if (nowOverridden) {
                int desired = nowEnabled ? 1 : 0;
                int current =
                        Settings.System.getInt(
                                appContext.getContentResolver(), SYSTEM_SETTING, 1);
                if (current != desired) {
                    Settings.System.putInt(
                            appContext.getContentResolver(), SYSTEM_SETTING, desired);
                }
            } else if (wasOverridden) {
                int current =
                        Settings.System.getInt(
                                appContext.getContentResolver(), SYSTEM_SETTING, 1);
                if (current != 1) {
                    Settings.System.putInt(appContext.getContentResolver(), SYSTEM_SETTING, 1);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // SettingKeyEntry hook still enforces the in-memory value when putInt is restricted.
        }
    }

    static Contract resolve(Class<?> helperClass, ClassLoader loader, AnchorFinder finder)
            throws ReflectiveOperationException, IOException {
        String helperDesc = DexSignatures.descriptor(helperClass);
        List<DexSignatures.Body> matches =
                finder.find(Set.of(SYSTEM_SETTING)).stream()
                        .filter(
                                body ->
                                        body.anchors().containsKey(SYSTEM_SETTING)
                                                && helperDesc.equals(body.owner())
                                                && (body.access() & Modifier.STATIC) == 0
                                                && body.parameters().isEmpty()
                                                && "Z".equals(body.result()))
                        .toList();
        if (matches.isEmpty()) return null;
        if (matches.size() > 1)
            throw new NoSuchMethodException("Ambiguous SettingsHelper Now Bar AOD reader");
        DexSignatures.Body reader = matches.get(0);
        Set<Class<?>> entryClasses = new LinkedHashSet<>();
        for (var call : reader.calls()) {
            if (!call.isStatic()
                    && call.parameters().equals(List.of("Ljava/lang/String;"))
                    && call.result().startsWith("L")
                    && !"Ljava/lang/String;".equals(call.result())) {
                entryClasses.add(call.method(loader).getReturnType());
            }
        }
        if (entryClasses.size() != 1)
            throw new NoSuchMethodException("Unexpected SettingKeyEntry lookup call");
        Class<?> entryClass = entryClasses.iterator().next();
        String entryDesc = DexSignatures.descriptor(entryClass);
        Set<Method> intReaders = new LinkedHashSet<>();
        for (var call : reader.calls()) {
            if (!call.isStatic()
                    && entryDesc.equals(call.owner())
                    && call.parameters().isEmpty()
                    && "I".equals(call.result())) {
                Method method = call.method(loader);
                method.setAccessible(true);
                intReaders.add(method);
            }
        }
        if (intReaders.size() != 1)
            throw new NoSuchMethodException("Unexpected SettingKeyEntry int reader");
        Field uriField = NativeContract.findField(entryClass, null, Uri.class);
        return new Contract(intReaders.iterator().next(), uriField);
    }
}
