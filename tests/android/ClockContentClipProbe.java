package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;

public final class ClockContentClipProbe {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static void main(String[] args){try{run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    private static void run()throws Exception {
        Looper.prepareMainLooper();Class<?> memory=Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance",memory).invoke(null,memory.getMethod("create").invoke(null));
        Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
        Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
        Context context=system.createPackageContext("com.samsung.android.app.dressroom",Context.CONTEXT_IGNORE_SECURITY);
        FrameLayout main=new FrameLayout(context),tile=new FrameLayout(context);View clock=new View(context),thumbnail=new View(context);
        main.setId(context.getResources().getIdentifier("clock_preview","id","com.samsung.android.app.dressroom"));
        tile.setId(context.getResources().getIdentifier("clock_container","id","com.samsung.android.app.dressroom"));
        check(main.getId()!=0 && tile.getId()!=0,"Native resource identities missing");main.addView(clock);tile.addView(thumbnail);
        check(DepthEffects.isEditorClock(clock),"Main clock rejected");check(!DepthEffects.isEditorClock(thumbnail),"Picker thumbnail hijacks depth");
        int w=context.getResources().getDisplayMetrics().widthPixels,h=context.getResources().getDisplayMetrics().heightPixels;
        main.layout(0,0,w,h);clock.layout(0,0,w,h);
        Bitmap mask=Bitmap.createBitmap(144,312,Bitmap.Config.ARGB_8888);new Canvas(mask).drawRect(0,0,72,312,new Paint(){{setColor(Color.WHITE);}});
        ByteArrayOutputStream png=new ByteArrayOutputStream();mask.compress(Bitmap.CompressFormat.PNG,100,png);
        ViewOutlineProvider original=clock.getOutlineProvider();boolean originalClip=clock.getClipToOutline();
        DepthEffects.apply(clock,true,png.toByteArray());ClockContentClip.watch(clock,true);
        check(clock.getClipToOutline(),"Render-node clip not active");
        Outline outline=new Outline();clock.getOutlineProvider().getOutline(clock,outline);
        Path path=(Path)Reflect.field(outline,"mPath");Region visible=new Region();visible.setPath(path,new Region(0,0,w,h));
        check(!visible.contains(w/4,h/2) && visible.contains(3*w/4,h/2),"Clock bitmap/background clip lost subject geometry");
        DepthEffects.media(clock,true);clock.getViewTreeObserver().dispatchOnPreDraw();
        check(clock.getOutlineProvider()==original && clock.getClipToOutline()==originalClip,"Media did not suspend clip");
        DepthEffects.media(clock,false);clock.getViewTreeObserver().dispatchOnPreDraw();check(clock.getClipToOutline(),"Depth did not resume after media");
        DepthEffects.apply(clock,false,null);check(clock.getOutlineProvider()==original && clock.getClipToOutline()==originalClip,"Disable did not restore native outline");
        ViewOutlineProvider containerOutline=main.getOutlineProvider();boolean containerClip=main.getClipToOutline();
        DepthSafetyProbe.Crop source = DepthSafetyProbe.bind(context, png.toByteArray(), w, h);
        DepthEffects.applyEditorPreview(main,true,png.toByteArray(),true);
        check(main.getClipToOutline(),"Composite city container not clipped");
        outline=new Outline();main.getOutlineProvider().getOutline(main,outline);
        path=(Path)Reflect.field(outline,"mPath");visible=new Region();visible.setPath(path,new Region(0,0,w,h));
        check(!visible.contains(w/4,h/2) && visible.contains(3*w/4,h/2),"Composite clip lost selected subject");
        DepthEffects.applyEditorPreview(main,true,png.toByteArray(),false);
        check(main.getOutlineProvider()==containerOutline && main.getClipToOutline()==containerClip,"Switching style left composite clip enabled");
        DepthEffects.applyEditorPreview(main,true,png.toByteArray(),true);DepthEffects.resetEditor();
        check(main.getOutlineProvider()==containerOutline && main.getClipToOutline()==containerClip,"Draft reset retained composite clip");
        mask.recycle();System.out.println("Clock style clipping and editor ownership: "+checks+" checks passed");
    }
}
