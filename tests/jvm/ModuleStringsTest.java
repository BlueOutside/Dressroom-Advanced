package dev.denver.dressroomadvanced;

import java.util.Locale;

public final class ModuleStringsTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(ModuleStrings.resources(null) == null, "null context resolves null resources");
        check("".equals(ModuleStrings.get(null, 0)), "get(null, 0) returns empty string");
        check("".equals(ModuleStrings.getOrFallback(null, 0, null)), "null fallback returns empty string");
        check(
                "Fallback".equals(ModuleStrings.getOrFallback(null, 0, "Fallback")),
                "plain fallback returned when context is null");
        check(
                "Hello world (3)"
                        .equals(
                                ModuleStrings.getOrFallback(
                                        null, 0, "Hello %1$s (%2$d)", "world", 3)),
                "formatted fallback resolves positional arguments");
        check(
                "5 shortcuts".equals(ModuleStrings.quantity(null, 0, 5, "%1$d shortcuts", 5)),
                "quantity fallback formats arguments");
        check(
                "Reordered 7 ok"
                        .equals(ModuleStrings.formatSafely(Locale.US, "Reordered %2$d %1$s", "ok", 7)),
                "positional reordering works");
        check(
                "Broken %1$d"
                        .equals(ModuleStrings.formatSafely(Locale.US, "Broken %1$d", "not-an-int")),
                "mismatched translator format specifier falls back without throwing");
        check(
                "".equals(ModuleStrings.formatSafely(Locale.US, null, "x")),
                "null format template returns empty string");
        check(
                "Bat %".equals(ModuleStrings.formatSafely(Locale.US, "Bat %")),
                "no-arg string with literal percent is untouched");
        check(
                ModuleStrings.localeOf(null) != null,
                "localeOf(null) returns JVM default locale");

        System.out.println("ModuleStrings: " + checks + " checks passed");
    }
}
