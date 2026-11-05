package dev.denver.blurprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Method;

/** A temporary window test. It does not read or alter wallpaper/settings. */
public final class BlurActivity extends Activity {
    private View panel;
    private WindowManager windows;
    private static final class Checker extends View {
        final Paint paint = new Paint();
        Checker(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            for (int y = 0; y < getHeight(); y += 12) for (int x = 0; x < getWidth(); x += 12) {
                paint.setColor(((x / 12 + y / 12) & 1) == 0 ? Color.BLACK : Color.WHITE);
                canvas.drawRect(x, y, x + 12, y + 12, paint);
            }
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setShowWhenLocked(true); setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        setContentView(new Checker(this));
        getWindow().getDecorView().post(this::showPanels);
    }
    private void showPanels() {
        try {
            int width = Math.min(1280, getWindow().getDecorView().getWidth() - 40), unit = width / 4;
            int height = 360;
            FrameLayout root = new FrameLayout(this); panel = root;
            View windowBlur = new View(this); windowBlur.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout.LayoutParams second = new FrameLayout.LayoutParams(unit, height); second.leftMargin = unit;
            root.addView(windowBlur, second);
            FrameLayout canvasGroup = new FrameLayout(this);
            if (getIntent().getBooleanExtra("sameCanvas", false))
                canvasGroup.addView(new Checker(this), new FrameLayout.LayoutParams(unit, height));
            View canvasBlur = new View(this); canvasBlur.setBackgroundColor(Color.TRANSPARENT);
            canvasGroup.addView(canvasBlur, new FrameLayout.LayoutParams(unit, height));
            FrameLayout.LayoutParams third = new FrameLayout.LayoutParams(unit, height); third.leftMargin = unit * 2;
            root.addView(canvasGroup, third);
            View gpu = new Checker(this); gpu.setRenderEffect(RenderEffect.createBlurEffect(24, 24, Shader.TileMode.CLAMP));
            FrameLayout.LayoutParams fourth = new FrameLayout.LayoutParams(unit, height); fourth.leftMargin = unit * 3;
            root.addView(gpu, fourth);
            TextView title = new TextView(this); title.setText("Plain | Samsung window | Samsung canvas | Android GPU");
            title.setTextColor(Color.RED); title.setBackgroundColor(Color.WHITE);
            root.addView(title, new FrameLayout.LayoutParams(width, 42));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
            params.token = getWindow().getDecorView().getWindowToken(); params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 20; params.y = 180; params.setTitle("Dressroom temporary blur comparison");
            windows = getSystemService(WindowManager.class); windows.addView(root, params);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Object windowInfo = blur(0), canvasInfo = blur(2);
                    Class<?> info = Class.forName("android.view.SemBlurInfo");
                    View.class.getMethod("semSetBlurInfo", info).invoke(windowBlur, windowInfo);
                    View.class.getMethod("semSetBlurInfo", info).invoke(canvasBlur, canvasInfo);
                    int[] xy = new int[2]; root.getLocationOnScreen(xy);
                    android.util.Log.i("DressroomBlurProbe", "READY x=" + xy[0] + " y=" + xy[1] + " unit=" + unit
                        + " hardware=" + root.isHardwareAccelerated() + " window=" + windowInfo + " canvas=" + canvasInfo);
                } catch (Throwable error) { android.util.Log.e("DressroomBlurProbe", "Blur setup failed", error); }
            }, 700);
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 35000);
        } catch (Throwable error) { android.util.Log.e("DressroomBlurProbe", "Window setup failed", error); finish(); }
    }
    private Object blur(int mode) throws Exception {
        Class<?> type = Class.forName("android.view.SemBlurInfo$Builder"); Object builder = type.getConstructor(int.class).newInstance(mode);
        type.getMethod("setRadius", int.class).invoke(builder, 64);
        if (mode == 0) type.getMethod("setBackgroundColor", int.class).invoke(builder, Color.TRANSPARENT);
        return type.getMethod("build").invoke(builder);
    }
    @Override protected void onDestroy() {
        if (panel != null && panel.isAttachedToWindow()) windows.removeViewImmediate(panel);
        super.onDestroy();
    }
    public static final class Runner extends Instrumentation {
        private boolean sameCanvas;
        @Override public void onCreate(Bundle args) {
            super.onCreate(args); sameCanvas = args != null && "true".equals(args.getString("sameCanvas")); start();
        }
        @Override public void onStart() {
            try {
                startActivitySync(new Intent(getTargetContext(), BlurActivity.class).putExtra("sameCanvas", sameCanvas).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(38000); finish(Activity.RESULT_OK, new Bundle());
            } catch (Throwable error) { Bundle result = new Bundle(); result.putString("error", error.toString()); finish(Activity.RESULT_CANCELED, result); }
        }
    }
}
