package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Looper;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Exercises cached/native ink separation and callback scope on real TextViews. */
public final class ClockBlurColorProbe {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        try { run(); System.exit(0); }
        catch (Throwable failure) { failure.printStackTrace(System.out); System.exit(1); }
    }

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Glyph glyph = new Glyph(context), unrelated = new Glyph(context);
        TextView ordinary = new TextView(context);
        glyph.setTextColor(0xffeeeeee);
        glyph.cached = true;
        glyph.setTextColor(0x216ac9b0);
        unrelated.cached = true;
        unrelated.setTextColor(0xfff0beaa);
        ClockBlurColorBinding binding = new ClockBlurColorBinding(Glyph.class,
                Glyph.class.getDeclaredField("cached"), Glyph.class.getDeclaredField("pathColor"),
                Strategy.class.getDeclaredField("views"), List.of());
        Strategy strategy = new Strategy();
        strategy.views.add(glyph);
        ClockBlurColors.Scope scope = new ClockBlurColors.Scope(binding, strategy.views);
        check(glyph.getCurrentTextColor() == 0xffeeeeee, "Fixture did not separate path/native ink");
        check(ClockBlurColors.read(glyph) == null, "Unscoped getter changed native color");
        ClockBlurColors.Scope previous = ClockBlurColors.swap(scope);
        check(previous == null, "Initial callback scope was not empty");
        try {
            check(ClockBlurColors.read(glyph) == 0x216ac9b0, "Path RGB/alpha were not retained");
            check(TextColorHooks.read(glyph) == 0x216ac9b0,
                    "Unified TextColorHooks lost scoped clock path ink");
            check(ClockBlurColors.read(unrelated) == null, "Another clock's glyph was corrected");
            check(ClockBlurColors.read(ordinary) == null, "Ordinary TextView was corrected");
            glyph.cached = false;
            check(ClockBlurColors.read(glyph) == null, "Non-path rendering overrode native ink");
            glyph.cached = true;
            glyph.pathColor = 0x004cc3da;
            check(ClockBlurColors.read(glyph) == 0x004cc3da, "Transparent path color was discarded");
            glyph.pathColor = 0xff28394a;
            check(ClockBlurColors.read(glyph) == 0xff28394a, "Scope cached an earlier palette color");
            var nested = ClockBlurColors.swap(new ClockBlurColors.Scope(binding, List.of(unrelated)));
            try {
                check(ClockBlurColors.read(glyph) == null, "Nested callback retained outer membership");
                check(ClockBlurColors.read(unrelated) == 0xfff0beaa, "Nested owner lost its path color");
                throw new IllegalStateException("Synthetic native callback failure");
            } catch (IllegalStateException expected) {
                // Models the hook's finally even when native code throws.
            } finally { ClockBlurColors.swap(nested); }
            check(ClockBlurColors.read(glyph) == 0xff28394a, "Failure lost outer callback scope");
            Integer[] otherThread = {1};
            Thread worker = new Thread(() -> otherThread[0] = ClockBlurColors.read(glyph));
            worker.start();
            worker.join();
            check(otherThread[0] == null, "Color correction leaked onto another thread");
            strategy.views.clear();
            check(ClockBlurColors.read(glyph) == null, "Retired glyph retained ownership");
        } finally { ClockBlurColors.swap(previous); }
        check(ClockBlurColors.read(glyph) == null, "Callback scope leaked after return");
        check(glyph.getCurrentTextColor() == 0xffeeeeee, "Correction mutated TextView's native state");
        android.animation.ValueAnimator animation = new android.animation.ValueAnimator();
        strategy.animator = animation;
        Captures captures = new Captures();
        captures.first = ordinary;
        captures.second = strategy;
        var frame = new ClockBlurColorBinding.Writer(null, List.of(
                Captures.class.getDeclaredField("first"), Captures.class.getDeclaredField("second")),
                Strategy.class.getDeclaredField("animator"));
        check(ClockBlurColors.owner(frame, captures, animation, Strategy.class) == strategy,
                "Erased weather capture was mistaken for the strategy");
        check(ClockBlurColors.owner(frame, captures, new android.animation.ValueAnimator(),
                        Strategy.class) == null,
                "Unrelated R8 animation entered the color scope");
        Strategy other = new Strategy();
        captures.first = other;
        other.animator = animation;
        check(ClockBlurColors.owner(frame, captures, animation, Strategy.class) == null,
                "Multiple matching strategy owners were guessed");
        other.animator = null;
        check(ClockBlurColors.owner(frame, captures, animation, Strategy.class) == strategy,
                "An inactive captured strategy displaced the animator owner");
        strategy.animator = null;
        check(ClockBlurColors.owner(frame, captures, animation, Strategy.class) == null,
                "Retired animator kept its callback color scope");
        System.out.println("Clock path-color callback scope: " + checks + " checks passed");
    }

    public static final class Glyph extends TextView {
        public boolean cached;
        public int pathColor;
        Glyph(Context context) { super(context); }
        @Override public void setTextColor(int color) {
            pathColor = color;
            if (!cached) super.setTextColor(color);
        }
    }

    public static final class Strategy {
        public final ArrayList<TextView> views = new ArrayList<>();
        public android.animation.ValueAnimator animator;
    }

    public static final class Captures {
        public Object first, second;
    }
}
