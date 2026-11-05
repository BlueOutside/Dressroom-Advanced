package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import java.io.InputStream;

/** Exercise the real SystemUI Lottie asset/classes without starting SystemUI services. */
public final class ChargingLiquidProbe {
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory=Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance",memory).invoke(null,memory.getMethod("create").invoke(null));
        Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
        Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
        Context context=system.createPackageContext("com.android.systemui",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=context.getClassLoader();
        Class<?> animation=Class.forName("com.airbnb.lottie.LottieDrawable",true,loader);
        Class<?> factory=Class.forName("com.airbnb.lottie.LottieCompositionFactory",true,loader);
        int checks=0;
        for(String asset:new String[]{"nowbar_gradient_normal.json","nowbar_gradient_fast.json","nowbar_gradient_superfast.json",
                "rtl_nowbar_gradient_normal.json","rtl_nowbar_gradient_fast.json","rtl_nowbar_gradient_superfast.json"}) {
            Object result;
            try(InputStream input=context.getAssets().open(asset)) {
                result=factory.getMethod("fromJsonInputStreamSync",InputStream.class,String.class).invoke(null,input,null);
            }
            Object composition=Reflect.field(result,"value");if(composition==null)throw new AssertionError("Asset failed: "+asset);
            Object view=animation.getConstructor().newInstance();
            Reflect.call(view,"setComposition",composition);Reflect.call(view,"setProgress",.05f);
            ChargingVisuals.finishLiquid(view);
            if(((android.animation.ValueAnimator)Reflect.field(view,"animator")).getAnimatedFraction()<.999f)throw new AssertionError("Unfinished wave: "+asset);checks++;
            if(((android.animation.ValueAnimator)Reflect.field(view,"animator")).isRunning())throw new AssertionError("Liquid replay: "+asset);checks++;
            ChargingVisuals.finishLiquid(view);
            if(((android.animation.ValueAnimator)Reflect.field(view,"animator")).getAnimatedFraction()<.999f)throw new AssertionError("Returning wave reset: "+asset);checks++;
        }
        System.out.println("Samsung charging liquid assets: "+checks+" checks passed");System.exit(0);
    }
}
