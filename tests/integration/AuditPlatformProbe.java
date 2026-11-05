package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Looper;
import java.lang.reflect.Method;

/** Ownership regression checks run against Android's bitmap implementation. */
public final class AuditPlatformProbe {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Bitmap subject=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);subject.eraseColor(0x80804020);
        Method encode=DepthEffects.class.getDeclaredMethod("encodeMask",int.class,int.class,Bitmap.class,Rect.class);encode.setAccessible(true);
        byte[] bytes=(byte[])encode.invoke(null,20,20,subject,new Rect(5,5,15,15));
        check(!subject.isRecycled(),"Mask encoding recycled SDK output");
        check(subject.getPixel(4,4)==0x80804020,"Mask encoding recolored SDK output");
        Bitmap mask=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        check(Color.alpha(mask.getPixel(8,8))==128,"Mask lost subject alpha");
        check((mask.getPixel(8,8)&0xffffff)==0xffffff,"Mask RGB must be neutral");
        check(Color.alpha(mask.getPixel(1,1))==0,"Mask filled pixels outside the subject bounds");
        encode.invoke(null,10,10,subject,new Rect(0,0,10,10));
        check(!subject.isRecycled() && subject.getPixel(4,4)==0x80804020,"Identity-sized mask encoding aliased SDK bitmap");
        Bitmap artwork=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        artwork.eraseColor(0xffffffff);
        android.graphics.RectF cover=new android.graphics.RectF(0,0,100,100);
        check(ArtworkSamples.read(artwork).sample(cover,new Rect(0,0,100,100))>.99,"White cover must choose dark text");
        artwork.eraseColor(0xff000000);
        check(ArtworkSamples.read(artwork).sample(cover,new Rect(0,0,100,100))<.01,"Black cover must choose light text");
        for(int y=0;y<100;y++)for(int x=0;x<50;x++)artwork.setPixel(x,y,0xffffffff);
        check(ArtworkSamples.read(artwork).sample(cover,new Rect(0,0,40,100))>.99,"Contrast must sample behind the clock, not the whole cover");
        check(ArtworkSamples.read(artwork).sample(cover,new Rect(60,0,100,100))<.01,"Bright artwork elsewhere must not invert dark clock region");
        artwork.recycle();
        subject.recycle();mask.recycle();
        System.out.println("Mask ownership and artwork contrast: "+checks+" checks passed");
    }
}
