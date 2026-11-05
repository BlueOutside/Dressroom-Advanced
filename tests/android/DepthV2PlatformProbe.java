package dev.denver.dressroomadvanced;
import android.graphics.*;
import android.os.Bundle;
import java.util.Arrays;
import org.json.JSONObject;

public final class DepthV2PlatformProbe {
    public record Info(Bitmap bitmap,Rect bounds){public Bitmap getObjBitmap(){return bitmap;}public Rect getBoundRect(){return bounds;}}
    public record Result(Info first,java.util.List<Info> objects){public boolean isCaptured(){return true;}public Info getOutput(){return first;}public java.util.List<Info> getObjects(){return objects;}}
    public record Detector(Result result){public Result capture(Bitmap input){return result;}}
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static boolean contains(Path path,float x,float y){Matrix scale=new Matrix();scale.setScale(16,16);Path scaled=new Path();path.transform(scale,scaled);
        Region region=new Region();region.setPath(scaled,new Region(-160,-160,1600,1600));return region.contains(Math.round(x*16),Math.round(y*16));}
    public static void main(String[] args)throws Exception {
        Bitmap mask=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
        check(MaskContour.extract(mask,127.5f).isEmpty(),"Empty matte");mask.eraseColor(-1);
        Path contour=MaskContour.extract(mask,127.5f);RectF bounds=new RectF();contour.computeBounds(bounds,true);
        check(bounds.equals(new RectF(0,0,8,8)),"Full image bounds");
        for(int y=2;y<6;y++)for(int x=2;x<6;x++)mask.setPixel(x,y,0);contour=MaskContour.extract(mask,127.5f);
        check(!contains(contour,4,4)&&contains(contour,1,4),"Holes and foreground ring");
        mask.eraseColor(0);mask.setPixel(1,1,-1);mask.setPixel(6,6,-1);contour=MaskContour.extract(mask,127.5f);
        check(contains(contour,1.5f,1.5f)&&contains(contour,6.5f,6.5f)&&!contains(contour,4,4),"Separate subjects");
        mask.eraseColor(0);for(int y=0;y<8;y++)mask.setPixel(y,y,-1);contour=MaskContour.extract(mask,127.5f);
        check(contains(contour,3.5f,3.5f)&&!contains(contour,1,6),"Thin diagonal");
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)mask.setPixel(x,y,((x*32)<<24)|0xffffff);
        contour=MaskContour.extract(mask,127.5f);contour.computeBounds(bounds,true);check(Math.abs(bounds.left-4.484375f)<.001,"Interpolated edge");
        Matrix crop=new Matrix();crop.setScale(2.2f,2.2f);crop.postTranslate(-80,25);
        Matrix global=new Matrix();global.setRotate(25);global.postTranslate(20,40);
        Matrix mapped=LiveSubject.compose(8,8,new RectF(10,15,90,95),crop,global),inverse=new Matrix();check(mapped.invert(inverse),"Invertible crop");
        float[] point={1.5f,6.5f};mapped.mapPoints(point);inverse.mapPoints(point);check(Math.abs(point[0]-1.5f)<.0001&&Math.abs(point[1]-6.5f)<.0001,"Source crop rotation roundtrip");
        Bundle data=new Bundle();data.putByteArray("depthMask",new byte[]{1,2,3});data.putByteArray(DepthMetadata.SOURCE,new byte[]{4,5,6});
        data.putInt("depthSourceVersion",2);data.putString(DepthMetadata.FINGERPRINT,"source");JSONObject json=new JSONObject();DepthMetadata.write(json,data);
        Bundle loaded=new Bundle();DepthMetadata.read(json,loaded);check(Arrays.equals(data.getByteArray(DepthMetadata.SOURCE),loaded.getByteArray(DepthMetadata.SOURCE)),"Source persistence");
        check(Arrays.equals(data.getByteArray("depthMask"),loaded.getByteArray("depthMask")),"Screen fallback persistence");
        check(loaded.getInt("depthSourceVersion")==2&&loaded.getString(DepthMetadata.FINGERPRINT).equals("source"),"Source basis identity");
        Bundle frozen=new Bundle();DepthMetadata.copy(data,frozen);data.getByteArray(DepthMetadata.SOURCE)[0]=9;check(frozen.getByteArray(DepthMetadata.SOURCE)[0]==4,"Frozen source isolation");
        JSONObject legacy=new JSONObject();legacy.put("depthMask",android.util.Base64.encodeToString(new byte[]{7,8},0));loaded=new Bundle();DepthMetadata.read(legacy,loaded);
        check(Arrays.equals(loaded.getByteArray("depthMask"),new byte[]{7,8})&&!loaded.containsKey(DepthMetadata.SOURCE),"Legacy stays screen-space");
        data=new Bundle();String reference="a".repeat(64);data.putString("depthMaskRef",reference);json=new JSONObject();DepthMetadata.write(json,data);loaded=new Bundle();DepthMetadata.read(json,loaded);
        check(reference.equals(loaded.getString("depthMaskRef"))&&!loaded.containsKey("depthMask"),"File reference stays outside Binder bytes");
        try{DepthStorage.uri("../escape");throw new AssertionError("Invalid path accepted");}catch(IllegalArgumentException expected){checks++;}
        Bundle widgets=WidgetPreferences.defaults(),clock=new Bundle();widgets.putByteArray("depthMask",new byte[]{1});clock.putByteArray("depthMask",new byte[]{2});
        check(WidgetPreferences.mask(widgets,clock)[0]==2,"Inherited widget source follows clock");widgets.putInt("depth",1);check(WidgetPreferences.mask(widgets,clock)[0]==1,"Independent widget source retained");
        Bitmap source=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888),part=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);part.eraseColor(0x80804020);
        Info one=new Info(part,new Rect(0,0,2,2)),two=new Info(part,new Rect(1,0,3,2));
        Bitmap union=MaskAlpha.detect(new Detector(new Result(one,java.util.List.of(one,two))),source,"probe");
        check((union.getPixel(1,1)>>>24)==128,"Overlapping passes must not raise alpha to 192");
        check((union.getPixel(2,1)>>>24)==128 && union.getPixel(3,3)==0,"Individual objects retained at correct bounds");
        check(!source.isRecycled() && !part.isRecycled() && part.getPixel(0,0)==0x80804020,"SDK/input bitmaps retained unchanged");
        union.recycle();part.recycle();source.recycle();mask.recycle();System.out.println("Depth v2 Android contracts: "+checks+" checks passed");
    }
}
