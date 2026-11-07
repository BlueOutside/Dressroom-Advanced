package dev.denver.dressroomadvanced;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;

/** Read-only ABI probe for the Fold8 APK pair; never constructs a Samsung view or saves data. */
public final class Fold8BindingProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("editor.apk aod.apk");
        ClassLoader editor = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        EditorBinding binding = EditorBinding.resolve(editor, EditorBinding.DRESSROOM_3001);
        if (!binding.editor3001 || binding.legacy) throw new AssertionError("Wrong editor adapter");
        new WallpaperSourceBinding(editor, false, true);
        DepthBinding.validateEditor(editor);
        OneUi9Editor3001Widgets.validate(editor);
        Class<?> model = Class.forName("so.j", false, editor);
        Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, editor);
        Class.forName("so.h", false, editor).getDeclaredConstructor(model, boolean.class, continuation, int.class);
        model.getDeclaredMethod("c", model, boolean.class);
        Class<?> save = Class.forName("dc.p", false, editor);
        boolean request = false;
        for (Method m : save.getDeclaredMethods())
            if (m.getName().equals("Y") && m.getParameterCount() == 2) request = true;
        if (!request) throw new AssertionError("Missing save request");
        Class<?> callback = Class.forName("androidx.work.impl.utils.c", false, editor);
        callback.getDeclaredConstructor(int.class, Object.class, Object.class);
        callback.getDeclaredMethod("invoke");
        System.out.println("PASS Fold8 editor, wallpaper, native transactions and save declarations");
        ClassLoader aod = new PathClassLoader(args[1], ClassLoader.getSystemClassLoader());
        OneUi9FoldProfiles.validate(aod);
        Class<?> base = Class.forName("com.samsung.android.uniform.widget.clock.AbsClockView", false, aod);
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.aod8957 || clock.legacy || clock.clockStyle) throw new AssertionError("Wrong clock adapter");
        ClockBlurBinding blur = ClockBlurBinding.resolve(base);
        if (!blur.render.getDeclaringClass().getName().equals("aod.jx0"))
            throw new AssertionError("Wrong native blur adapter");
        if (!blur.clip.getDeclaringClass().getName().equals("aod.x60"))
            throw new AssertionError("Wrong native blur clip");
        NowBarStackBinding.of(base);
        WidgetHostSizing.validate(aod);
        WidgetEngine.validateLegacyGrid(Class.forName(WidgetEngine.GRID, false, aod));
        Class<?> options = Class.forName("aod.qb0", false, aod);
        Class<?> strategy = Class.forName("aod.jx0", false, aod);
        strategy.getDeclaredMethod("h", int.class, android.widget.ImageView.class);
        Class<?> manager = Class.forName("aod.sp0", false, aod);
        manager.getDeclaredMethod("h");
        manager.getDeclaredMethod("d", java.util.ArrayList.class, int.class, float.class);
        manager.getDeclaredMethod("c", int.class, int.class, boolean.class);
        base.getDeclaredMethod("C0");
        base.getDeclaredMethod("t0", int.class, android.widget.TextView[].class);
        base.getDeclaredMethod("G0", float.class, boolean.class);
        Class.forName("aod.i16", false, aod).getDeclaredMethod("A");
        Class.forName("aod.i16", false, aod).getDeclaredMethod("C", Object.class);
        Class.forName("aod.ev2", false, aod).getDeclaredMethod("O", int.class);
        Class.forName("aod.q39", false, aod).getConstructor(double.class, float.class);
        Class<?> holder = OneUi9Aod8957Runtime.type("aod.fa2", false, aod);
        for (String field : new String[] {"G0", "U", "O", "t0", "o0", "Q", "S", "P", "c0"}) holder.getDeclaredField(field);
        Class<?> motion = OneUi9Aod8957Runtime.type("aod.ag4", false, aod);
        motion.getDeclaredMethod("d", android.graphics.drawable.Drawable.class);
        motion.getDeclaredMethod("e", android.graphics.drawable.Drawable.class);
        OneUi9Aod8957Runtime.type("aod.n82", false, aod).getDeclaredMethod("invoke");
        OneUi9Aod8957Runtime.type("aod.o8", false, aod).getDeclaredMethod("onAnimationUpdate", android.animation.ValueAnimator.class);
        OneUi9Aod8957Runtime.type("aod.di1", false, aod).getDeclaredMethod("z", android.view.View.class, int.class, int.class);
        System.out.println("PASS Fold8 widget editor/runtime, Now Bar stack, blur, AOD stretch and media bindings");
        FontBinding.of(base);
        new ClockSourceBinding(aod, clock);
        base.getDeclaredMethod("setMediaExpanded", boolean.class);
        base.getDeclaredMethod("isStretchable", java.util.List.class);
        System.out.println("PASS Fold8 AOD clock, font, source, native depth blur and runtime declarations");
    }
}
