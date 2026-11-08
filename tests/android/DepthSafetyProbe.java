package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.widget.ImageView;

import org.json.JSONObject;

public final class DepthSafetyProbe {
    public static final class Crop extends ImageView {
        private BitmapDrawable original;

        Crop(Context c) {
            super(c);
            setScaleType(ScaleType.MATRIX);
            Bitmap pixels = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            pixels.eraseColor(0xff4080c0);
            original = new BitmapDrawable(c.getResources(), pixels);
            setImageDrawable(original);
        }

        public Drawable getDrawableInner() {
            return getDrawable();
        }

        public BitmapDrawable getOriginalDrawable() {
            return original;
        }

        @Override
        public boolean isShown() {
            return true;
        }
    }

    static Crop bind(Context context, byte[] mask, int width, int height) throws Exception {
        Crop source = new Crop(context);
        source.layout(0, 0, width, height);
        source.getDrawable().setBounds(0, 0, width, height);
        Bundle saved = new Bundle();
        saved.putInt("depthSourceVersion", 2);
        saved.putString(DepthMetadata.FINGERPRINT, LiveSubject.fingerprint(source));
        saved.putByteArray(DepthMetadata.SOURCE, mask);
        saved.putByteArray("depthMask", mask);
        DepthEffects.restoreSource(source, saved, saved.getString(DepthMetadata.FINGERPRINT));
        return source;
    }

    private static int checks;

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
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

    private static void run() throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance", memory)
                .invoke(null, memory.getMethod("create").invoke(null));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context context = (Context) at.getMethod("getSystemContext").invoke(thread);
        Bundle data = new Bundle();
        data.putBoolean("depthEnabled", true);
        data.putBoolean("manual", true);
        data.putBoolean("hideDate", true);
        data.putBoolean("weatherEnabled", true);
        data.putByteArray("depthMask", new byte[] {1});
        data.putString("depthMaskRef", "old");
        data.putByteArray(DepthMetadata.SOURCE, new byte[] {2});
        data.putString(DepthMetadata.SOURCE + "Ref", "old-source");
        data.putString(DepthMetadata.FINGERPRINT, "old");
        data.putInt("depthSourceVersion", 2);
        data.putFloatArray("manualHeights", new float[] {2, 3});
        DepthSafety.disable(data);
        check(!data.getBoolean("depthEnabled"), "Failure left depth enabled");
        for (String field :
                new String[] {
                    "depthMask",
                    "depthMaskRef",
                    DepthMetadata.SOURCE,
                    DepthMetadata.SOURCE + "Ref",
                    DepthMetadata.FINGERPRINT,
                    "depthSourceVersion"
                }) check(!data.containsKey(field), "Failure retained " + field);
        check(
                data.getBoolean("manual") && data.getFloatArray("manualHeights")[0] == 2,
                "Failure destroyed user-owned manual stretch");
        check(
                data.getBoolean("hideDate") && data.getBoolean("weatherEnabled"),
                "Failure changed unrelated clock settings");
        data.putBoolean("depthOwnsManual", true);
        data.putIntArray("manualProgress", new int[] {12, 34});
        DepthSafety.disable(data);
        check(
                !data.getBoolean("manual") && !data.getBoolean("depthOwnsManual"),
                "Failure retained depth-owned stretch");
        check(
                !data.containsKey("manualHeights") && !data.containsKey("manualProgress"),
                "Failure retained depth-owned segment data");
        data.putString("depthImageKey", "identity");
        JSONObject json = new JSONObject();
        DepthMetadata.write(json, data);
        Bundle restored = new Bundle();
        DepthMetadata.read(json, restored);
        check(
                restored.getString("depthImageKey").equals("identity"),
                "Source identity lost in persistence");
        Bitmap mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        mask.eraseColor(-1);
        byte[] png = MaskAlpha.encode(mask);
        check(DepthEffects.editorMask(png) == null, "Unverified editor fallback accepted");
        Crop source = bind(context, png, 100, 100);
        check(DepthEffects.editorMask(png) != null, "Verified current source rejected");
        String fingerprint = LiveSubject.fingerprint(source);
        source.setColorFilter(
                new ColorMatrixColorFilter(
                        new float[] {
                            0, 0, 0, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0
                        }));
        LiveSubject tracked = LiveSubject.bind(source, png, fingerprint, null);
        check(tracked.changed(), "Initial wallpaper transform not reported");
        check(!tracked.changed(), "Unchanged transform reported repeatedly");
        Matrix crop = new Matrix();
        crop.setScale(2, 2);
        crop.postTranslate(-10, 5);
        source.setImageMatrix(crop);
        tracked.global(); // Draw/blur queries must not consume the observer's change.
        check(tracked.changed(), "Zoom change consumed by a drawing query");
        check(!tracked.changed(), "Settled zoom repeatedly invalidated depth");
        tracked.global().postTranslate(99, 77);
        check(!tracked.changed(), "Caller modified the stored transform snapshot");
        check(
                LiveSubject.fingerprint(source).equals(fingerprint),
                "Filter/crop changed original image identity");
        check(DepthEffects.editorMask(png) != null, "Crop/filter invalidated current subject");
        source.original = new BitmapDrawable(context.getResources(), mask);
        source.setImageDrawable(source.original);
        check(tracked.global() == null, "Replacement image reused the last valid transform");
        check(DepthEffects.editorMask(png) == null, "Old source survived drawable replacement");
        DepthEffects.clearEditorSubject();
        check(DepthEffects.editorMask(png) == null, "Clear restored an old fallback");
        mask.recycle();
        System.out.println("Depth source safety: " + checks + " checks passed");
    }
}
