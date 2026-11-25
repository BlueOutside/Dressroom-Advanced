package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;

/** Read-only ABI check, without constructing Samsung views or installing hooks. */
public final class ClockBlurColorBindingProbe {
    public static void main(String[] args) {
        try { run(args); System.exit(0); }
        catch (Throwable failure) { failure.printStackTrace(System.out); System.exit(1); }
    }

    private static void run(String[] args) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
        if (args.length != 2) throw new IllegalArgumentException("provider.apk expected-writers");
        System.out.println("Clock color binding: loading declarations");
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> base = Class.forName(
                "com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
        System.out.println("Clock color binding: reading native contracts");
        ClockBlurColorBinding colors = DexSignatures.cached(() -> {
            ClockBlurRetryBinding retry = ClockBlurRetryBinding.of(base);
            System.out.println("Clock color binding: native owner resolved");
            return ClockBlurColorBinding.resolve(retry);
        });
        int count = colors == null ? 0 : colors.writers.size();
        if (count != Integer.parseInt(args[1]))
            throw new AssertionError("Expected " + args[1] + " unsafe color writers, got " + count);
        System.out.println("Clock blur color contract: " + count + " writers, "
                + (colors == null ? "native policy retained" : "path fields resolved"));
    }
}
