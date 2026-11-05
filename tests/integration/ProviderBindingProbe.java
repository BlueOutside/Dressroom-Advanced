package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Resolves an APK without installing it or creating a Samsung service. */
public final class ProviderBindingProbe {
    private static void verifyOwner(ClockBlurBinding binding) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        var instance = unsafeType.getDeclaredField("theUnsafe");instance.setAccessible(true);
        Object unsafe = instance.get(null);
        var allocate = unsafeType.getMethod("allocateInstance", Class.class);
        var ownerSlot = ClockBlurBinding.class.getDeclaredField("owner");ownerSlot.setAccessible(true);
        var clockSlot = ClockBlurBinding.class.getDeclaredField("clock");clockSlot.setAccessible(true);
        var ownerField = (java.lang.reflect.Field) ownerSlot.get(binding);
        var clockField = (java.lang.reflect.Field) clockSlot.get(binding);
        // Only field traversal is exercised; no Samsung service or View constructor runs.
        Object strategy = allocate.invoke(unsafe, binding.render.getDeclaringClass());
        Object owner = allocate.invoke(unsafe, ownerField.getType());
        android.view.View view = (android.view.View) allocate.invoke(unsafe, android.view.View.class);
        ownerField.set(strategy, owner);clockField.set(owner, view);
        if (binding.clock(strategy) != view) throw new AssertionError("Wrong native blur owner");
        if (clockField.getType() == Object.class) {
            clockField.set(owner, new Object());
            if (binding.clock(strategy) != null) throw new AssertionError("Non-view blur owner accepted");
        }
        System.out.println("Native blur owner traversal and non-view fallback passed");
    }

    public static void main(String[] args) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        if (args.length != 1) throw new IllegalArgumentException("provider.apk");
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> base = Class.forName("com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
        ClockBinding clock = ClockBinding.of(base);
        ClockSourceBinding source = new ClockSourceBinding(loader, clock);
        System.out.println("Clock source: " + source.callers.size() + " measurement callers resolved");
        if (!clock.clockStyle) {
            ClockBlurBinding blur = ClockBlurBinding.resolve(base);
            verifyOwner(blur);
        }
        WidgetHostSizing.validate(loader);
        WidgetEngine.validateLegacyGrid(Class.forName(WidgetEngine.GRID, false, loader));
        if (clock.legacy) {
            RuntimeWidgetHooks.legacyUpdates(Class.forName(
                    "com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController", false, loader));
            if (!clock.clockStyle) OneUi85AodBlur.submissions(loader);
        }
        System.out.println((clock.legacy ? "8.5 " : "9.0 ") + (clock.clockStyle ? "Clock Style" : "AOD")
                + ": clock, source, blur and widget bindings resolved");
    }
}
