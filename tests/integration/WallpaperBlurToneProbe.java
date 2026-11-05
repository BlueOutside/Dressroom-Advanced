package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;

public final class WallpaperBlurToneProbe {
    public static final class SampleClock extends View {
        public boolean L0=true;
        public int t;
        public Object s;
        SampleClock(Context c){super(c);}
    }
    public static void main(String[] args) {
        try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    private static void run()throws Exception {
        Looper.prepareMainLooper();Class<?> memory=Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance",memory).invoke(null,memory.getMethod("create").invoke(null));
        Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
        Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
        Context context=system.createPackageContext("com.samsung.android.app.aodservice",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=context.getClassLoader();Class<?> manager=Class.forName("aod.cq0",false,loader);
        SampleClock clock=new SampleClock(system);int checks=0;
        for(int sample:new int[]{Color.BLACK,Color.WHITE,0xff2f94db,0xffff4040}) {
            clock.t=sample;Object tone=WallpaperBlurTone.read(manager,clock);
            double actual=((Number)Reflect.field(tone,"a")).doubleValue();
            double expected=((Number)Class.forName("aod.rv2",false,loader).getDeclaredMethod("w",int.class).invoke(null,sample)).doubleValue();
            if(Math.abs(actual-expected)>.0001)throw new AssertionError("Brightness changed");checks++;
            float[] hsv=new float[3];Color.colorToHSV(sample,hsv);
            if(Math.abs(((Number)Reflect.field(tone,"b")).floatValue()-hsv[1])>.0001)throw new AssertionError("Saturation changed");checks++;
        }
        clock.L0=false;if(WallpaperBlurTone.read(manager,clock)!=null)throw new AssertionError("Missing runtime sample did not defer");checks++;
        System.out.println("Native wallpaper blur tone: "+checks+" checks passed");
    }
}
