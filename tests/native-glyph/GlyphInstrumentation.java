package dev.denver.nativeglyphprobe;
import android.app.Instrumentation;
import android.os.Bundle;
public class GlyphInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();final Throwable[] error={null};
  runOnMainSync(()->{try{NativeGlyphCache.test(getTargetContext());}catch(Throwable t){error[0]=t;}});
  if(error[0]!=null){java.io.StringWriter text=new java.io.StringWriter();error[0].printStackTrace(new java.io.PrintWriter(text));result.putString("stream",text.toString());finish(1,result);}
  else{result.putString("stream","NATIVE_GLYPH_PASS=8\n");finish(-1,result);}
 }
}
