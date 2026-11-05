package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.Region;

/** Runs via app_process on the actual Samsung framework, without starting a UI. */
public final class DepthPlatformProbe {
    public static void main(String[] args) throws Exception {
        Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
        Class<?> blurInfo = Class.forName("android.view.SemBlurInfo");
        android.view.View.class.getDeclaredMethod("semSetBlurInfo", blurInfo);
        builder.getMethod("setBackgroundClipPath", Path.class);
        Path glyph = new Path(); glyph.addRect(0, 0, 100, 100, Path.Direction.CW);
        Path subject = new Path(); subject.addRect(25, 25, 75, 100, Path.Direction.CW);
        if (!glyph.op(subject, Path.Op.DIFFERENCE)) throw new AssertionError("Native Path subtraction failed");
        Region output = new Region(); output.setPath(glyph, new Region(0, 0, 100, 100));
        if (output.contains(50, 50) || !output.contains(10, 50)) throw new AssertionError("Native subject clip is inverted");
        System.out.println("Samsung background clip API and native path subtraction: passed");
        Path background = DepthGeometry.visibleBackground(100, 100, 20, subject);
        Region visible = new Region(); visible.setPath(background, new Region(0, 0, 100, 100));
        if (visible.contains(50, 50) || visible.contains(0, 0) || !visible.contains(10, 50))
            throw new AssertionError("Widget background lost subject subtraction or rounded corners");
        Path outside = new Path(); outside.addRect(200, 200, 300, 300, Path.Direction.CW);
        Path unaffected = DepthGeometry.visibleBackground(100, 100, 20, outside);
        Region full = new Region(); full.setPath(unaffected, new Region(0, 0, 100, 100));
        if (!full.contains(50, 50) || full.contains(0, 0))
            throw new AssertionError("Non-overlapping widget background changed");
        System.out.println("Widget background subject clipping and rounded corners: passed");
        android.graphics.Outline outline = new android.graphics.Outline(); outline.setPath(background);
        if (!outline.canClip()) throw new AssertionError("This framework cannot clip a widget render node to the subject path");
        System.out.println("Samsung supports subject-path clipping of cached widget render nodes: passed");
        DepthGeometry.outline(outline, new Path());
        android.graphics.Rect hiddenClip = new android.graphics.Rect();
        if (!outline.canClip() || !outline.getRect(hiddenClip) || android.graphics.Rect.intersects(hiddenClip, new android.graphics.Rect(0, 0, 100, 100)))
            throw new AssertionError("Full occlusion must retain a valid clip outside the widget bounds");
        System.out.println("Fully occluded widget retains an active clip with no visible area: passed");
        Object nativeBuilder = builder.getConstructor(int.class).newInstance(0);
        builder.getMethod("setRadius", int.class).invoke(nativeBuilder, 72);
        builder.getMethod("setBackgroundColor", int.class).invoke(nativeBuilder, 0x44112233);
        builder.getMethod("setBackgroundCornerRadius", float.class).invoke(nativeBuilder, 18f);
        builder.getMethod("setBackgroundClipPath", Path.class).invoke(nativeBuilder, outside);
        Object nativeInfo = builder.getMethod("build").invoke(nativeBuilder);
        WidgetBlurInfo copies = new WidgetBlurInfo(blurInfo);
        Path originalPath = copies.path(nativeInfo);
        Object clipped = copies.clipped(nativeInfo, background);
        if (clipped == nativeInfo || copies.path(nativeInfo) != originalPath || copies.path(clipped) == originalPath)
            throw new AssertionError("Widget clipping changed shared native info");
        for (java.lang.reflect.Field field : blurInfo.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().equals("mClipPath")) continue;
            field.setAccessible(true);
            if (!java.util.Objects.equals(field.get(nativeInfo), field.get(clipped)))
                throw new AssertionError("Widget clipping changed native " + field.getName());
        }
        System.out.println("Final Samsung blur copy preserves every native field and isolates subject path: passed");
        if (copies.submission(nativeInfo, new Path()) != null)
            throw new AssertionError("A fully covered widget must remove its blur surface");
        if (copies.submission(nativeInfo, background) == null || copies.path(nativeInfo) != originalPath)
            throw new AssertionError("A widget returning into view lost its original native blur info");
        System.out.println("Full occlusion removes native blur and partial visibility restores from intact original: passed");
        Object roundedBuilder = builder.getConstructor(int.class).newInstance(0);
        builder.getMethod("setBackgroundCornerRadius", float.class).invoke(roundedBuilder, 18f);
        Object roundedInfo = builder.getMethod("build").invoke(roundedBuilder);
        Path roundedClip = copies.visible(roundedInfo, 100, 100, subject);
        Region roundedRegion = new Region(); roundedRegion.setPath(roundedClip, new Region(0, 0, 100, 100));
        if (roundedRegion.contains(0, 0) || roundedRegion.contains(50, 50) || !roundedRegion.contains(10, 50))
            throw new AssertionError("Final widget clip lost native corner radii or subject subtraction");
        System.out.println("Final widget clip preserves native rounded shape and subject cutout: passed");
        if (args.length > 0) {
            ClassLoader loader = new dalvik.system.PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
            Class<?> model = Class.forName("vo.i", false, loader);
            Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
            Class.forName("vo.g", false, loader).getConstructor(model, boolean.class, continuation, int.class);
            model.getDeclaredMethod("c", model, boolean.class);
            Class.forName("kotlinx.coroutines.BuildersKt", false, loader).getMethod("launch$default",
                Class.forName("kotlinx.coroutines.CoroutineScope", false, loader),
                Class.forName("kotlin.coroutines.CoroutineContext", false, loader),
                Class.forName("kotlinx.coroutines.CoroutineStart", false, loader),
                Class.forName("kotlin.jvm.functions.Function2", false, loader), int.class, Object.class);
            Class.forName("dc.p", false, loader).getDeclaredMethod("Y",
                Class.forName("k6.s", false, loader), Class.forName("j6.d", false, loader));
            System.out.println("Dressroom weather transaction and native save signatures: passed");
        }
        if (args.length > 1) {
            ClassLoader loader = new dalvik.system.PathClassLoader(args[1], ClassLoader.getSystemClassLoader());
            Class.forName("aod.e70", false, loader).getDeclaredMethod("a", builder, Path.class);
            Class<?> utils = Class.forName("aod.e70", false, loader);
            utils.getDeclaredMethod("c", android.view.View.class, float.class, float[].class, Integer.class);
            utils.getDeclaredMethod("d", android.view.View.class, int.class, float.class, Integer.class, String.class);
            Class<?> container = Class.forName("com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer", false, loader);
            container.getDeclaredMethod("refreshBlur", float.class);
            container.getDeclaredMethod("addBgViews", int.class, double.class, float.class, boolean.class);
            container.getDeclaredMethod("getBlurView");
            container.getDeclaredField("darkAmount");
            Class<?> owner = Class.forName("com.samsung.android.app.facewidget.complication.ComplicationWidgetPluginController", false, loader);
            owner.getDeclaredField("container"); owner.getDeclaredField("keyguardClockProvider");
            owner.getDeclaredMethod("initialize", android.view.ViewGroup.class);
            owner.getDeclaredMethod("setDozing", boolean.class);
            owner.getDeclaredMethod("setDarkAmount", float.class);
            owner.getDeclaredMethod("requestAODState", boolean.class);
            owner.getDeclaredMethod("onFinishedWakingUp");
            owner.getDeclaredMethod("release");
            owner.getDeclaredMethod("refreshComplicationGridAndWidgets");
            Class<?> clockProvider = Class.forName("com.samsung.android.app.facewidget.utils.KeyguardClockProvider", false, loader);
            clockProvider.getDeclaredField("clockItem");
            clockProvider.getDeclaredMethod("onClockTypeChanged", boolean.class, boolean.class);
            clockProvider.getDeclaredMethod("refreshClockFontDataInternal",
                Class.forName("com.samsung.android.sdk.aodservice.entities.font.Font", false, loader),
                Class.forName("com.samsung.android.app.facewidget.KeyguardClockPositionItem", false, loader));
            System.out.println("Native widget owner, selected clock record and AOD transition hook signatures: passed");
            Class<?> font = Class.forName("com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);
            Object initialFont = font.getConstructor(int.class, String.class, Integer.class, Integer.class, java.util.List.class)
                .newInstance(803, "", null, 20, null);
            Object restoredFont = SavedStretch.font(initialFont, SavedStretch.heights(new float[]{2.1f, 1.3f, 1.1f, 1.8f, 2f}));
            if (Reflect.call(initialFont, "getFontHeights") != null
                    || !Reflect.call(restoredFont, "getFontRoundedCorners").equals(20)
                    || Reflect.call(restoredFont, "getFontWeight") != null
                    || !Reflect.call(restoredFont, "getFontHeights").equals(java.util.List.of(2.1f, 1.3f, 1.1f, 1.8f, 2f)))
                throw new AssertionError("Saved manual font copy changed native style or failed to restore heights");
            System.out.println("Installed provider font restores saved heights without changing native font/style: passed");
            Class.forName("aod.tx0", false, loader).getDeclaredMethod("b", android.widget.ImageView.class,
                Class.forName("com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader),
                Class.forName("aod.xb0", false, loader));
            System.out.println("AOD blur owner and clip-path hook signatures: passed");
        }
    }
}
