package dev.denver.dressroomadvanced;

import android.view.View;

/** Finish only the charging entry animation, leaving the Now Bar's card transforms alone. */
final class ChargingVisuals {
    static void finishLiquid(Object progress) throws ReflectiveOperationException {
        Reflect.call(progress,"pauseAnimation");
        // The asset's wave mask grows during entry. Freezing at frame zero reduces
        // the filled width even after the native percentage translation is correct.
        Reflect.call(progress,"setProgress",1f);
        ClassLoader loader=progress.getClass().getClassLoader();
        Class<?> constants=Class.forName("com.android.systemui.power.constants.ChargerAnimationConstants",false,loader);
        Class<?> property=Class.forName("com.airbnb.lottie.LottieProperty",false,loader);
        Class<?> callback=Class.forName("com.airbnb.lottie.value.LottieValueCallback",false,loader);
        Object wave=constants.getField("WAVE_KEY_PATH").get(null);
        Object blur=property.getField("BLUR_RADIUS").get(null);
        Object zero=callback.getConstructor(Object.class).newInstance(Float.valueOf(0));
        Reflect.call(progress,"addValueCallback",wave,blur,zero);
    }
    static void restore(View card) throws ReflectiveOperationException {
        Object container=Reflect.call(Reflect.field(card,"batteryLevelContainer$delegate"),"getValue");
        finish((View)container);
        finish((View)Reflect.call(card,"getBatteryIcon"));
        finish((View)Reflect.call(card,"getBatteryIconShadow"));
    }
    private static void finish(View view) {
        view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);
        view.setTranslationX(0);view.setTranslationY(0);
    }
}
