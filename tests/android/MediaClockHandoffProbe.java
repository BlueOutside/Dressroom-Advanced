package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;

/** Detached framework views and real RenderNodes; no hooks, services or saved settings. */
public final class MediaClockHandoffProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void alpha(View view, float expected, String message) {
        check(Math.abs(view.getAlpha() - expected) < .001f, message + ": " + view.getAlpha());
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(String name) throws Exception {
        return (Map<Object, Object>) field(MediaClockContrast.class, name).get(null);
    }

    private static Object snapshot(FrameLayout root, Bitmap image) throws Exception {
        Class<?> type = Class.forName(MediaClockContrast.class.getName() + "$Snapshot");
        var ctor = type.getDeclaredConstructor(WeakReference.class, int.class, ArtworkSamples.class);
        ctor.setAccessible(true);
        Object state = ctor.newInstance(new WeakReference<>(image), image.getGenerationId(),
                ArtworkSamples.read(image));
        field(type, "spec").set(state, new MediaClockContrast.Palette(0xffffffff, .5f));
        ((RectF) field(type, "localBounds").get(state)).set(0, 0, 80, 60);
        map("samples").put(root, state);
        return state;
    }

    private static void record(RenderNode node) {
        node.setPosition(0, 0, 100, 100);
        Canvas canvas = node.beginRecording();
        canvas.drawColor(0xff367baa);
        node.endRecording();
    }

    private static void drawGlass(FrameLayout root) throws Exception {
        drawGlass(root, new View(root.getContext()), 0xffffffff);
    }

    private static void drawGlass(FrameLayout root, View glyph, int tint) throws Exception {
        root.addView(glyph);
        root.layout(0, 0, 100, 100);
        glyph.layout(0, 0, 80, 60);
        Class<?> draw = Class.forName(MediaClockContrast.class.getName() + "$DrawGlyph");
        Object callback = java.lang.reflect.Proxy.newProxyInstance(
                draw.getClassLoader(), new Class<?>[] {draw},
                (proxy, method, args) -> {
                    ((Canvas) args[0]).drawColor(0xffffffff);
                    return null;
                });
        var render = MediaClockContrast.class.getDeclaredMethod("drawArtworkGlyph",
                Canvas.class, View.class, android.view.ViewGroup.class,
                float.class, int.class, draw);
        render.setAccessible(true);
        RenderNode output = new RenderNode("Clock glyph frame");
        output.setPosition(0, 0, 100, 100);
        Canvas canvas = output.beginRecording();
        try {
            render.invoke(null, canvas, glyph, root, 1f, tint, callback);
        } finally {
            output.endRecording();
            output.discardDisplayList();
            root.removeView(glyph);
        }
    }

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        FrameLayout root = new FrameLayout(context);
        View host = new View(context);
        root.addView(host);
        host.setAlpha(.73f);
        MediaBlurHost.holdForArtwork(host, root);
        alpha(host, .73f, "Native glass was hidden before palette readiness");

        Bitmap image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        image.eraseColor(0xff235689);
        Object state = snapshot(root, image);
        check(MediaClockContrast.wantsGlass(root), "Prepared palette was not a glass request");
        check(!MediaClockContrast.hasGlass(root), "Palette alone was treated as a rendered frame");
        MediaBlurHost.refreshArtwork(root);
        alpha(host, .73f, "Missing RenderNode suppressed the native fallback");

        RenderNode art = new RenderNode("Clock handoff test");
        RectF bounds = new RectF(0, 0, 100, 100);
        MediaClockContrast.artwork(root, art, 1, bounds, null, 1f);
        check(!MediaClockContrast.hasGlass(root), "Unrecorded RenderNode counted as ready");
        alpha(host, .73f, "Unrecorded replacement hid native glass");
        record(art);
        MediaClockContrast.artwork(root, art, 2, bounds, null, 1f);
        check(!MediaClockContrast.hasGlass(root), "Undrawn replacement acquired the host");
        alpha(host, .73f, "Native glass vanished before the glyph consumed its frame");
        drawGlass(root);
        check(MediaClockContrast.hasGlass(root), "Drawn glass frame was not ready");
        alpha(host, 0f, "Ready player glass did not suppress the native duplicate");
        check(MediaBlurHost.nativeAlpha(host, .42f) == 0f,
                "A native alpha write escaped while replacement owned the host");
        check(Math.abs(MediaBlurHost.requestedAlpha(host) - .42f) < .001f,
                "Handoff lost Samsung's latest requested opacity");

        MediaClockContrast.artwork(root, art, 2, bounds, null, 0f);
        check(!MediaClockContrast.hasGlass(root), "Zero-opacity frame still owned glass");
        alpha(host, .42f, "Zero-opacity replacement did not restore native glass");

        FrameLayout other = new FrameLayout(context);
        View otherHost = new View(context);
        other.addView(otherHost);
        otherHost.setAlpha(.88f);
        snapshot(other, image);
        MediaBlurHost.holdForArtwork(otherHost, other);
        MediaClockContrast.artwork(root, art, 3, bounds, null, 1f);
        drawGlass(root);
        alpha(host, 0f, "Re-recorded replacement failed to acquire its host");
        alpha(otherHost, .88f, "One window's replacement hid another window's native glass");

        art.discardDisplayList();
        check(!MediaClockContrast.hasGlass(root), "Discarded frame remained drawable");
        MediaClockContrast.artwork(root, art, 4, bounds, null, 1f);
        alpha(host, .42f, "Discarded replacement did not restore native fallback");
        record(art);
        MediaClockContrast.artwork(root, art, 5, bounds, null, 1f);
        drawGlass(root);
        TextView clock = new TextView(context);
        clock.setTextColor(0x6040b8d0);
        root.addView(clock);
        Class<?> savedType = Class.forName(MediaClockContrast.class.getName() + "$Saved");
        var savedCtor = savedType.getDeclaredConstructor(android.view.ViewGroup.class, TextView.class);
        savedCtor.setAccessible(true);
        Object savedInk = savedCtor.newInstance(root, clock);
        field(savedType, "initialized").setBoolean(savedInk, true);
        field(savedType, "color").setInt(savedInk, 0xff17191c);
        field(savedType, "emphasis").setFloat(savedInk, 1f);
        map("colors").put(clock, savedInk);
        Class<?> pendingType = Class.forName(MediaClockContrast.class.getName() + "$PendingSample");
        var pendingCtor = pendingType.getDeclaredConstructor(Bitmap.class, int.class, RectF.class, View.class);
        pendingCtor.setAccessible(true);
        map("pendingSamples").put(root,
                pendingCtor.newInstance(image, image.getGenerationId(), bounds, host));
        MediaClockContrast.returnToNative(root, 1f);
        check(!map("pendingSamples").containsKey(root),
                "Late palette completion could reapply player ink during collapse");
        alpha(host, 0f, "Return start exposed native glass before the blend");
        MediaClockContrast.returnToNative(root, .5f);
        int halfInk = field(savedType, "color").getInt(savedInk);
        check(halfInk != 0xff17191c, "Bright-cover dark ink stayed fixed until collapse ended");
        check((halfInk >>> 24) < 255, "Return forced native translucent ink opaque");
        alpha(host, .21f, "Native glass did not return with collapse progress");
        check(field(savedType, "animator").get(savedInk) == null,
                "Progress return started an independent color timer");
        check(field(savedType, "emphasis").getFloat(savedInk) == .5f,
                "Player glass emphasis did not follow collapse");
        MediaClockContrast.clear(root);
        check(field(savedType, "animator").get(savedInk) == null,
                "Repeated clear restarted the progress return");
        field(savedType, "nativeColors").set(savedInk, ColorStateList.valueOf(0x4042c8f0));
        MediaClockContrast.returnToNative(root, .25f);
        check(field(savedType, "color").getInt(savedInk) != halfInk,
                "Return ignored a new native palette");
        alpha(host, .315f, "Native host waited for the last animation frame");
        alpha(otherHost, .88f, "Closing one clock changed another root's blur");
        MediaClockContrast.returnToNative(root, 0f);
        check(clock.getCurrentTextColor() == 0x4042c8f0,
                "Completed return restored a stale native color");
        check(!map("colors").containsKey(clock), "Completed return retained player ink");
        MediaClockContrast.clearImmediate(root);
        alpha(host, .42f, "Final handoff failed to restore native opacity");
        check(!MediaClockContrast.wantsGlass(root), "Completed player retained its snapshot");

        // Cached-path ink can differ from TextView's ordinary palette and have zero alpha.
        state = snapshot(root, image);
        CachedInk cachedClock = new CachedInk(context);
        cachedClock.ink = 0x004bbce0;
        cachedClock.cached = true;
        var outlineSlot = field(MediaClockContrast.class, "customOutlineColor");
        var modeSlot = field(MediaClockContrast.class, "customPathMode");
        Object oldOutline = outlineSlot.get(null), oldMode = modeSlot.get(null);
        @SuppressWarnings("unchecked")
        var customTypes = (java.util.Set<Class<?>>) field(MediaClockContrast.class, "customClasses").get(null);
        customTypes.add(CachedInk.class);
        outlineSlot.set(null, field(CachedInk.class, "ink"));
        modeSlot.set(null, field(CachedInk.class, "cached"));
        Object cachedInk = savedCtor.newInstance(root, cachedClock);
        field(savedType, "initialized").setBoolean(cachedInk, true);
        field(savedType, "color").setInt(cachedInk, 0xff17191c);
        field(savedType, "emphasis").setFloat(cachedInk, .6f);
        map("colors").put(cachedClock, cachedInk);
        MediaClockContrast.artwork(root, art, 6, bounds, null, 1f);
        drawGlass(root, cachedClock, 0x205599bb);
        check(((android.graphics.Paint) field(state.getClass(), "ink").get(state)).getColor()
                        == 0x205599bb,
                "Opaque cached mask forced translucent return fill opaque");
        MediaClockContrast.returnToNative(root, .8f);
        check(field(savedType, "color").getInt(cachedInk) == 0xff17191c
                        && field(savedType, "emphasis").getFloat(cachedInk) == .6f,
                "Partial-opening return jumped to a new initial appearance");
        MediaClockContrast.returnToNative(root, .4f);
        check(Math.abs(field(savedType, "emphasis").getFloat(cachedInk) - .3f) < .001f,
                "Return used absolute progress instead of the interrupted opening's progress");
        check((field(savedType, "color").getInt(cachedInk) >>> 24) < 255,
                "Cached-path return ignored native ink alpha");
        check(cachedClock.ink == 0x004bbce0, "Return overwrote Samsung's cached palette");
        MediaClockContrast.returnToNative(root, 0f);
        outlineSlot.set(null, oldOutline);
        modeSlot.set(null, oldMode);
        customTypes.remove(CachedInk.class);

        // The weather icon can finish after the last text fade.
        state = snapshot(root, image);
        field(state.getClass(), "returning").setBoolean(state, true);
        ImageView weather = new ImageView(context);
        weather.setAlpha(.37f);
        root.addView(weather);
        Class<?> iconType = Class.forName(MediaClockContrast.class.getName() + "$Icon");
        var iconCtor = iconType.getDeclaredConstructor(android.view.ViewGroup.class, ImageView.class);
        iconCtor.setAccessible(true);
        Object icon = iconCtor.newInstance(root, weather);
        map("icons").put(weather, icon);
        var prune = MediaClockContrast.class.getDeclaredMethod("pruneSnapshot",
                android.view.ViewGroup.class);
        prune.setAccessible(true);
        prune.invoke(null, root);
        check(map("samples").get(root) == state,
                "Last text fade dropped glass while weather still owned it");
        var restore = MediaClockContrast.class.getDeclaredMethod("restoreIcon", ImageView.class, iconType);
        restore.setAccessible(true);
        restore.invoke(null, weather, icon);
        check(!map("samples").containsKey(root), "Last icon leaked a returning snapshot");
        alpha(weather, .37f, "Weather cleanup lost native opacity");

        MediaBlurHost.release(host);
        host.setAlpha(.61f);
        MediaBlurHost.refreshArtwork(root);
        alpha(host, .61f, "Released host remained under player ownership");
        MediaClockContrast.clearImmediate(other);
        MediaBlurHost.release(otherHost);
        image.recycle();
        art.discardDisplayList();
        System.out.println("Media clock handoff: " + checks + " checks passed");
    }

    public static final class CachedInk extends TextView {
        public int ink;
        public boolean cached;
        CachedInk(Context context) { super(context); }
    }

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}

