package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

final class OneUi80Depth {
    private static final String SDK = "com.samsung.android.app.sdk.deepsky.wallpaper.";
    private static final String CROP =
            "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView";

    record Binding(Method with, Method capture, Method init, Method release,
            Field lock, Field initialized) {
        <T> T detect(Context context, ObjectCaptureBackend.Detection<T> scan) throws Exception {
            Object detector = capture.invoke(with.invoke(null, context));
            synchronized (lock.get(null)) {
                AtomicBoolean ready = (AtomicBoolean) initialized.get(null);
                boolean alreadyInitialized = ready.get();
                try {
                    init.invoke(detector);
                    return scan.run(detector);
                } finally {
                    // Dressroom's own intelligent crop can already have a session open.
                    if (!alreadyInitialized && ready.get()) release.invoke(detector);
                }
            }
        }
    }

    private static final ClassValue<Binding> bindings = new ClassValue<>() {
        @Override protected Binding computeValue(Class<?> provider) {
            try {
                return bind(provider);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported 8.0 Object Capture contract", error);
            }
        }
    };

    private OneUi80Depth() {}

    static Binding resolve(ClassLoader loader) throws ReflectiveOperationException {
        return bindings.get(Class.forName(SDK + "WallpaperProvider", false, loader));
    }

    private static Binding bind(Class<?> provider) throws ReflectiveOperationException {
        ClassLoader loader = provider.getClassLoader();
        String prefix = SDK + "objectcapture.";
        Class<?> contract = Class.forName(prefix + "ObjectCapture", false, loader);
        Class<?> result = Class.forName(prefix + "ObjectResult", false, loader);
        Class<?> info = Class.forName(prefix + "ObjectInfo", false, loader);
        Class<?> implementation = Class.forName(prefix + "ObjectCaptureImpl", false, loader);
        Method with = provider.getDeclaredMethod("with", Context.class);
        if (!Modifier.isStatic(with.getModifiers()) || with.getReturnType() != provider
                || !contract.isAssignableFrom(implementation))
            throw new NoSuchMethodException("8.0 WallpaperProvider factory");
        DepthBinding.validateDetector(contract, result, info);
        return new Binding(with, NativeContract.method(provider, "getObjectCapture", contract),
                NativeContract.method(implementation, "init", void.class),
                NativeContract.method(implementation, "release", void.class),
                staticField(implementation, "CLASS_LOCK", Object.class),
                staticField(implementation, "IS_INIT_STATE", AtomicBoolean.class));
    }

    static Method cropDrawable(Class<?> crop) throws NoSuchMethodException {
        if (!crop.getName().equals(CROP) || !ImageView.class.isAssignableFrom(crop))
            throw new NoSuchMethodException("8.0 wallpaper crop view");
        NativeContract.method(crop, "getOriginalDrawable", BitmapDrawable.class);
        NativeContract.method(crop, "getDrawableBounds", RectF.class);
        return ImageView.class.getMethod("getDrawable");
    }

    private static Field staticField(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type)
            throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field;
    }
}
