package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Region;

/** Executes against Android's real rasterizer without loading the target APKs. */
public final class DepthRegionProbe {
    private static int checks;
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Depth region check " + checks); checks++; }
    private static boolean contains(Path p, float x, float y) {
        Matrix m = new Matrix(); m.setScale(2,2); Path scaled = new Path(); p.transform(m,scaled);
        Region r = new Region(); r.setPath(scaled,new Region(-100,-100,500,500));
        return r.contains(Math.round(x*2),Math.round(y*2));
    }
    public static void main(String[] args) {
        Path clock = new Path(); clock.addRect(0,0,100,100,Path.Direction.CW);
        Path stem = new Path(); stem.addRect(49.5f,0,50.5f,100,Path.Direction.CW);
        Path visible = DepthGeometry.subtractRegion(clock,stem);
        check(contains(visible,40,50)); check(!contains(visible,50,50)); check(contains(visible,60,50));
        Path leaves = new Path(); leaves.setFillType(Path.FillType.EVEN_ODD);
        leaves.addRect(20,20,80,80,Path.Direction.CW); leaves.addRect(40,40,60,60,Path.Direction.CW);
        visible = DepthGeometry.subtractRegion(clock,leaves);
        check(!contains(visible,30,30)); check(contains(visible,50,50)); check(contains(visible,10,10));
        check(DepthGeometry.subtractRegion(clock,clock).isEmpty());
        check(contains(DepthGeometry.subtractRegion(clock,new Path()),50,50));
        check(!contains(DepthGeometry.simplify(stem,.75f),40,50));
        check(contains(DepthGeometry.simplify(stem,.75f),50,50));
        check(contains(DepthGeometry.simplify(leaves,.75f),30,30));
        check(!contains(DepthGeometry.simplify(leaves,.75f),50,50));
        Path cut=DepthGeometry.subtract(clock,stem);
        cut.reset(); // Callers must not be able to mutate the cached result.
        check(contains(DepthGeometry.subtract(clock,stem),40,50));
        check(!contains(DepthGeometry.subtract(clock,stem),50,50));
        Path stairs=new Path(); stairs.moveTo(0,0);
        for(int i=1;i<=100;i++){stairs.lineTo(i,i-1);stairs.lineTo(i,i);}
        stairs.lineTo(100,0);stairs.close();
        check(DepthGeometry.simplify(stairs,.75f).approximate(.1f).length < stairs.approximate(.1f).length/4);
        Matrix crop=new Matrix();crop.setScale(2,3);crop.postTranslate(-10,-20);
        Matrix global=new Matrix();global.setScale(.5f,.5f);global.postTranslate(100,200);
        Matrix map=LiveSubject.compose(100,200,new android.graphics.RectF(10,20,210,420),crop,global);
        float[] point={50,100};map.mapPoints(point);
        check(Math.abs(point[0]-205)<.001f && Math.abs(point[1]-520)<.001f);
        if(args.length>0) benchmark(args[0]);
        System.out.println("Native depth region probe: " + checks + " checks passed");
    }
    private static void benchmark(String file) {
        android.graphics.Bitmap mask=android.graphics.BitmapFactory.decodeFile(file);
        Region region=new Region();int[] row=new int[mask.getWidth()];
        for(int y=0;y<mask.getHeight();y++) {
            mask.getPixels(row,0,row.length,0,y,row.length,1);int start=-1;
            for(int x=0;x<=row.length;x++) {
                boolean inside=x<row.length && (row[x]>>>24)>=128;
                if(inside && start<0)start=x;
                if(!inside && start>=0){region.op(start,y,x,y+1,Region.Op.UNION);start=-1;}
            }
        }
        Path raw=region.getBoundaryPath();long begin=System.nanoTime();
        Path simple=DepthGeometry.simplify(raw,.75f);
        System.out.println("Mask vertices "+raw.approximate(.1f).length/3+" -> "+simple.approximate(.1f).length/3
            +" simplify ms="+(System.nanoTime()-begin)/1000000f);
        Path clock=new Path();clock.addRect(0,0,mask.getWidth(),mask.getHeight()*.6f,Path.Direction.CW);
        begin=System.nanoTime();DepthGeometry.subtract(clock,simple);
        System.out.println("First cut ms="+(System.nanoTime()-begin)/1000000f);
        begin=System.nanoTime();for(int i=0;i<10;i++)DepthGeometry.subtractRegion(clock,simple);
        System.out.println("Direct region mean ms="+(System.nanoTime()-begin)/10000000f);
        begin=System.nanoTime();for(int i=0;i<100;i++)DepthGeometry.subtract(clock,simple);
        System.out.println("Cached cut mean ms="+(System.nanoTime()-begin)/100000000f);
    }
}
