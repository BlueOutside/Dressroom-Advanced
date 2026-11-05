package dev.denver.dressroomadvanced;

import android.graphics.Paint;
import android.graphics.Path;
import java.util.Objects;

/** Reuse geometry only within one native draw; paint effects do not change glyph outlines. */
final class GlyphOutlineMemo {
    private final Paint measurement=new Paint();private final Path cached=new Path();
    private String text;private int start,end;private float x,y;private boolean ready;
    void reset(){ready=false;text=null;measurement.reset();cached.rewind();}
    boolean reuse(Paint paint,String value,int from,int to,float left,float top,Path output){
        if(!ready || start!=from || end!=to || x!=left || y!=top || !Objects.equals(text,value)
                || !paint.equalsForTextMeasurement(measurement))return false;
        output.set(cached);return true;
    }
    void capture(Paint paint,String value,int from,int to,float left,float top,Path output){
        measurement.set(paint);cached.set(output);text=value;start=from;end=to;x=left;y=top;ready=true;
    }
}
