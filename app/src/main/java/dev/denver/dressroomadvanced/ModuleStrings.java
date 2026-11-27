package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Resolves localized strings from this module's own APK resources in both module activities and
 * hooked host processes (Dressroom, SystemUI, AOD, ClockPack), tracking host locale changes and
 * falling back cleanly in JVM contract tests.
 */
public final class ModuleStrings {
    static final String MODULE_PACKAGE = "dev.denver.dressroomadvanced";

    private static final Object LOCK = new Object();
    private static volatile ApplicationInfo moduleInfo;
    private static String cachedLocaleTags;
    private static Resources cachedResources;

    private ModuleStrings() {}

    static void setModuleInfo(ApplicationInfo info) {
        moduleInfo = info;
    }

    public static Resources resources(Context context) {
        if (context == null) return null;
        try {
            if (MODULE_PACKAGE.equals(context.getPackageName())) {
                return context.getResources();
            }
            Resources hostResources = context.getResources();
            LocaleList hostLocales =
                    hostResources == null ? null : hostResources.getConfiguration().getLocales();
            String localeTags = hostLocales == null ? "" : hostLocales.toLanguageTags();
            synchronized (LOCK) {
                if (cachedResources != null && localeTags.equals(cachedLocaleTags)) {
                    return cachedResources;
                }
            }
            Resources resolved = loadModuleResources(context, hostLocales);
            if (resolved != null) {
                synchronized (LOCK) {
                    cachedResources = resolved;
                    cachedLocaleTags = localeTags;
                }
            }
            return resolved;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Resources loadModuleResources(Context host, LocaleList locales) {
        try {
            Context pkgCtx =
                    host.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            if (locales != null && !locales.isEmpty()) {
                Configuration config = new Configuration(pkgCtx.getResources().getConfiguration());
                config.setLocales(locales);
                Context localized = pkgCtx.createConfigurationContext(config);
                if (localized != null && localized.getResources() != null) {
                    return localized.getResources();
                }
            }
            return pkgCtx.getResources();
        } catch (Throwable ignored) {
        }
        ApplicationInfo info = moduleInfo;
        if (info != null) {
            try {
                return host.getPackageManager().getResourcesForApplication(info);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static String get(Context context, int resId) {
        return getOrFallback(context, resId, "");
    }

    public static String get(Context context, int resId, Object... args) {
        return getOrFallback(context, resId, "", args);
    }

    public static String getOrFallback(Context context, int resId, String fallback) {
        Resources res = resources(context);
        if (res != null && resId != 0) {
            try {
                return res.getString(resId);
            } catch (Throwable ignored) {
            }
        }
        return fallback == null ? "" : fallback;
    }

    public static String getOrFallback(
            Context context, int resId, String fallback, Object... args) {
        String template = getOrFallback(context, resId, fallback);
        return formatSafely(localeOf(context), template, args);
    }

    public static String quantity(
            Context context, int pluralsResId, int count, String fallback, Object... args) {
        Resources res = resources(context);
        if (res != null && pluralsResId != 0) {
            try {
                String template = res.getQuantityString(pluralsResId, count);
                return formatSafely(localeOf(context), template, args);
            } catch (Throwable ignored) {
            }
        }
        return formatSafely(localeOf(context), fallback == null ? "" : fallback, args);
    }

    static Locale localeOf(Context context) {
        if (context != null) {
            try {
                Resources res = context.getResources();
                if (res != null) {
                    LocaleList locales = res.getConfiguration().getLocales();
                    if (locales != null && !locales.isEmpty()) {
                        return locales.get(0);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return Locale.getDefault();
    }

    static String formatSafely(Locale locale, String template, Object... args) {
        if (template == null) return "";
        if (args == null || args.length == 0) return template;
        try {
            return String.format(locale == null ? Locale.getDefault() : locale, template, args);
        } catch (IllegalFormatException error) {
            return template;
        }
    }
}
