package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

public final class ClockBlurSyncProbe {
    static int checks;

    static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace(System.out);
            System.exit(1);
        }
    }

    static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        Clock view = new Clock(context);
        Glyph glyph = new Glyph(context);
        view.addView(glyph);
        view.layout(0, 0, 100, 100);
        glyph.layout(0, 0, 80, 80);
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        view.attached = true;
        view.layoutPending = false;
        view.draw(canvas);
        RectF before = new RectF();
        glyph.path.computeBounds(before, true);
        view.attached = false;
        int[] calls = {0};
        check(!ClockBlurSync.defer(view, v -> calls[0]++), "Detached clock accepted work");
        view.attached = true;
        view.layoutPending = true;
        glyph.size = 24;
        glyph.invalidate();
        check(ClockBlurSync.defer(view, v -> calls[0]++), "Attached clock rejected work");
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(calls[0] == 0, "Blur ran before glyph draw");
        ClockBlurSync.drawn(view);
        check(calls[0] == 0, "Blur ran while layout was pending");
        check(
                ClockBlurSync.defer(
                        view,
                        v -> {
                            check(ClockBlurSync.rebuilding(v), "Recursive blur call not scoped");
                            RectF current = new RectF();
                            glyph.path.computeBounds(current, true);
                            check(
                                    current.height() > before.height() * 2,
                                    "Blur consumed old compact glyph path");
                            calls[0] += 10;
                        }),
                "Latest request rejected");
        view.layoutPending = false;
        view.getViewTreeObserver().dispatchOnPreDraw();
        check(calls[0] == 0, "Layout completion treated as glyph completion");
        view.draw(canvas);
        check(calls[0] == 10, "Latest request did not coalesce after child draw");
        check(
                !ClockBlurSync.rebuilding(view) && !ClockBlurSync.pending(view),
                "Completed draw retained state");
        view.draw(canvas);
        check(calls[0] == 10, "One-shot blur repeated");
        ClockBlurSync.defer(view, v -> calls[0]++);
        ClockBlurSync.cancel(view);
        view.draw(canvas);
        check(calls[0] == 10, "Cancelled work ran");
        ClockBlurSync.defer(view, v -> calls[0]++);
        view.attached = false;
        ClockBlurSync.drawn(view);
        check(calls[0] == 10 && !ClockBlurSync.pending(view), "Retired clock updated");
        view.attached = true;
        ClockBlurSync.defer(view, v -> calls[0]++);
        ClockBlurSync.drawn(new Clock(context));
        check(calls[0] == 10, "Unrelated group flushed clock");
        view.draw(canvas);
        check(calls[0] == 11, "Reattached clock failed");
        ClockBlurSync.defer(
                view,
                v -> {
                    calls[0]++;
                    ClockBlurSync.defer(v, next -> calls[0]++);
                });
        view.draw(canvas);
        check(calls[0] == 12, "Reentrant request ran in same draw");
        view.draw(canvas);
        check(calls[0] == 13, "Reentrant request lost");
        ClockBlurSync.defer(
                view,
                v -> {
                    throw new IllegalStateException("Synthetic blur failure");
                });
        ClockBlurSync.drawn(view);
        checks++; // A broken consumer must not take down dispatchDraw.
        check(
                !ClockBlurSync.rebuilding(view) && !ClockBlurSync.pending(view),
                "Failure retained scope");
        ClockBlurSync.defer(view, v -> calls[0]++);
        view.draw(canvas);
        check(calls[0] == 14, "Failure blocked future work");
        check(BlurEffects.discardStrategy(view.h1.p), "Existing compositor strategy was not cleared");
        check(view.h1.p.clears == 1, "Compact surface was not cleared exactly once");
        check(
                view.h1.enabled && view.h1.radius == 123,
                "Surface cleanup changed native blur settings");
        view.h1.p = null;
        check(!BlurEffects.discardStrategy(view.h1.p), "Missing strategy not handled");
        glyph.S = true;
        glyph.R = true;
        check(!GlyphBlurReadiness.stale(glyph, Glyph.class), "Clean ready outline was rejected");
        glyph.dirtyGlyph = true;
        check(GlyphBlurReadiness.stale(glyph, Glyph.class), "Dirty ready outline was accepted");
        check(
                GlyphBlurReadiness.needsDraw(java.util.List.of(glyph), Glyph.class),
                "Dirty collected glyph did not defer native submission");
        glyph.dirtyGlyph = false;
        glyph.layoutGlyph = true;
        check(GlyphBlurReadiness.stale(glyph, Glyph.class), "Pending glyph layout was accepted");
        glyph.layoutGlyph = false;
        glyph.R = false;
        check(GlyphBlurReadiness.stale(glyph, Glyph.class), "Unready outline was accepted");
        glyph.S = false;
        check(
                !GlyphBlurReadiness.stale(glyph, Glyph.class),
                "Uncached text was treated as an onDraw cache");
        check(
                !GlyphBlurReadiness.stale(new View(context), Glyph.class),
                "Unrelated view was treated as a glyph");
        glyph.S = true;
        glyph.R = true;
        check(
                !GlyphBlurReadiness.needsDraw(java.util.List.of(glyph), Glyph.class),
                "Ready collection deferred repeatedly");
        FrameLayout blurHost = new FrameLayout(context);
        android.graphics.drawable.ColorDrawable nativeBackground =
                new android.graphics.drawable.ColorDrawable(0xff123456);
        blurHost.setBackground(nativeBackground);
        blurHost.layout(0, 0, 100, 100);
        blurHost.setAlpha(.8f);
        android.graphics.Rect originalBounds =
                new android.graphics.Rect(nativeBackground.getBounds());
        MediaBlurHost.hold(blurHost);
        check(blurHost.getAlpha() == 0f, "Native blur host was not suppressed");
        check(
                blurHost.getBackground() == nativeBackground,
                "Live native blur drawable was replaced");
        check(
                nativeBackground.getCallback() == blurHost
                        && nativeBackground.getBounds().equals(originalBounds),
                "Native drawable ownership/bounds changed");
        blurHost.setAlpha(MediaBlurHost.nativeAlpha(blurHost, .4f));
        MediaBlurHost.hold(blurHost);
        check(blurHost.getAlpha() == 0f, "Native alpha write exposed held blur");
        nativeBackground.setAlpha(17);
        int updatedDrawableAlpha = nativeBackground.getAlpha();
        MediaBlurHost.release(blurHost);
        check(blurHost.getAlpha() == .4f, "Latest native alpha was not restored");
        check(
                nativeBackground.getAlpha() == updatedDrawableAlpha,
                "Native drawable state was rolled back: "
                        + updatedDrawableAlpha
                        + " -> "
                        + nativeBackground.getAlpha());
        check(
                MediaBlurHost.nativeAlpha(blurHost, .7f) == .7f,
                "Released host still intercepted native alpha");
        MediaBlurHost.hold(blurHost);
        blurHost.setBackground(null);
        MediaBlurHost.release(blurHost);
        check(blurHost.getBackground() == null, "Cleared compact drawable was resurrected");
        MediaBlurHost.hold(blurHost);
        blurHost.setAlpha(MediaBlurHost.nativeAlpha(blurHost, 0f));
        MediaBlurHost.release(blurHost);
        check(blurHost.getAlpha() == 0f, "Native hidden state was lost");
        check(
                MediaBlurHost.nativeAlpha(new View(context), .6f) == .6f,
                "Unrelated view alpha was intercepted");
        View content = new View(context);
        content.setAlpha(.8f);
        MediaBlurHost.fade(content, .25f);
        check(
                Math.abs(content.getAlpha() - .2f) < .00001f,
                "Compact content ignored reveal factor");
        content.setAlpha(MediaBlurHost.nativeAlpha(content, .6f));
        check(
                Math.abs(content.getAlpha() - .15f) < .00001f,
                "Native alpha write escaped the delayed reveal");
        MediaBlurHost.fade(content, .5f);
        check(
                Math.abs(content.getAlpha() - .3f) < .00001f,
                "Reveal used its own alpha as the native source");
        MediaBlurHost.release(content);
        check(content.getAlpha() == .6f, "Reveal restored a stale native alpha");
        MediaBlurHost.fade(content, 0);
        content.setAlpha(MediaBlurHost.nativeAlpha(content, 0));
        MediaBlurHost.release(content);
        check(content.getAlpha() == 0, "Screen-off hidden state was lost at reveal completion");
        image.recycle();
        System.out.println("Clock blur/glyph-draw synchronization: " + checks + " checks passed");
    }

    static final class Clock extends FrameLayout {
        boolean attached, layoutPending;
        final Manager h1 = new Manager();

        Clock(Context context) {
            super(context);
        }

        @Override
        public boolean isAttachedToWindow() {
            return attached;
        }

        @Override
        public boolean isLayoutRequested() {
            return layoutPending;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            ClockBlurSync.drawn(this);
        }
    }

    static final class Manager {
        Strategy p = new Strategy();
        boolean enabled = true;
        int radius = 123;
    }

    static final class Strategy {
        int clears;

        public void c() {
            clears++;
        }
    }

    static final class Glyph extends View {
        float size = 8;
        boolean R, S = true, dirtyGlyph, layoutGlyph;
        final Path path = new Path();
        final Paint paint = new Paint();

        Glyph(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean isDirty() {
            return dirtyGlyph;
        }

        @Override
        public boolean isLayoutRequested() {
            return layoutGlyph;
        }

        @Override
        public boolean isShown() {
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (S) {
                path.reset();
                path.addRect(0, 40 - size, 4, 40, Path.Direction.CW);
                canvas.drawPath(path, paint);
            } else canvas.drawRect(0, 40 - size, 4, 40, paint);
            R = true;
        }
    }
}
