package dev.denver.dressroomadvanced;

import android.graphics.*;

/** Merge coverage by maximum alpha; SRC_OVER would double opaque edges shared by two passes. */
final class MaskAlpha {
    interface DetailPass { Bitmap run() throws Exception; }
    /** Detail improves a valid full-image matte; a failed optional pass must not discard it. */
    static Bitmap refine(Bitmap full,DetailPass detail) throws Exception {
        try {
            Bitmap extra=detail.run();
            if(extra==null || extra==full)return full;
            if(full==null)return extra;
            try{maximum(full,extra,0,0);return full;}
            finally{extra.recycle();}
        }catch(Exception | LinkageError error){
            if(full==null)throw error;
            android.util.Log.w(DressroomModule.TAG,"Detail subject pass failed; retaining full-image subject",error);
            return full;
        }
    }
    static void maximum(Bitmap destination,Bitmap source,int left,int top){
        int width=Math.min(source.getWidth(),destination.getWidth()-left),height=Math.min(source.getHeight(),destination.getHeight()-top);
        if(left<0 || top<0 || width<=0 || height<=0)throw new IllegalArgumentException("Invalid mask placement");
        int[] a=new int[width],b=new int[width];
        for(int y=0;y<height;y++){destination.getPixels(a,0,width,left,top+y,width,1);source.getPixels(b,0,width,0,y,width,1);
            for(int x=0;x<width;x++)a[x]=(Math.max(a[x]>>>24,b[x]>>>24)<<24)|0xffffff;
            destination.setPixels(a,0,width,left,top+y,width,1);
        }
    }
    static Bitmap detect(Object detector,Bitmap input,String pass)throws Exception {
        Object result=Reflect.call(detector,"capture",input);if(!Boolean.TRUE.equals(Reflect.call(result,"isCaptured")))return null;
        java.util.List<Object> objects=new java.util.ArrayList<>();Object output=Reflect.call(result,"getOutput");if(output!=null)objects.add(output);
        try{Object candidates=Reflect.call(result,"getObjects");if(candidates instanceof java.util.List<?> list)objects.addAll(list);}catch(NoSuchMethodException ignored){}
        java.util.Set<Object> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Bitmap combined=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);int count=0;
        try{for(Object object:objects){if(object==null || !seen.add(object))continue;
                Bitmap subject=(Bitmap)Reflect.call(object,"getObjBitmap");Rect original=(Rect)Reflect.call(object,"getBoundRect");
                if(subject==null || subject.isRecycled() || original==null || original.isEmpty())continue;
                Rect clip=new Rect(original);if(!clip.intersect(0,0,input.getWidth(),input.getHeight()))continue;
                Bitmap tile=Bitmap.createBitmap(clip.width(),clip.height(),Bitmap.Config.ARGB_8888);
                try{Paint alpha=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);alpha.setColorFilter(new BlendModeColorFilter(Color.WHITE,BlendMode.SRC_IN));
                    RectF local=new RectF(original);local.offset(-clip.left,-clip.top);new Canvas(tile).drawBitmap(subject,null,local,alpha);maximum(combined,tile,clip.left,clip.top);count++;
                }finally{tile.recycle();}
            }
            android.util.Log.i(DressroomModule.TAG,"Subject "+pass+": "+input.getWidth()+"x"+input.getHeight()+" objects="+count);
            if(count==0){combined.recycle();return null;}return combined;
        }catch(Exception error){combined.recycle();throw error;}
    }
    static byte[] encode(Bitmap mask){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();mask.compress(Bitmap.CompressFormat.PNG,100,bytes);return bytes.toByteArray();}
}
