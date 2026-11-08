package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Checks 8.5's nullable initial grid alignment without constructing a native controller. */
public final class Widget85AlignmentProbe {
    public static void main(String[] args) {
        try {
            ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
            Class<?> grid = Class.forName(WidgetEngine.GRID, false, loader);
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            var singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            Object owner =
                    unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, grid);
            var field = grid.getDeclaredField("widgetAlignType");
            field.setAccessible(true);
            field.set(owner, null);
            Object horizontal = WidgetEngine.savedAlignment(owner, false);
            Object vertical = WidgetEngine.savedAlignment(owner, true);
            if (!((Enum<?>) horizontal).name().equals("Default")
                    || !((Enum<?>) vertical).name().equals("Vertical"))
                throw new AssertionError("Wrong saved alignment");
            if (field.get(owner) != null) throw new AssertionError("Resolver changed native state");
            field.set(owner, vertical);
            if (WidgetEngine.savedAlignment(owner, false) != horizontal)
                throw new AssertionError(
                        "Saved horizontal orientation depends on stale native value");
            System.out.println("Nullable initial alignment and saved orientation: 4 checks passed");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
