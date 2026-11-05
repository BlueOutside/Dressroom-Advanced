package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Feed saved heights to native measurement/transition decisions as well as the visible clock. */
final class RuntimeClockData {
    private record Entry(int type,int font,int orientation,List<Float> heights) {}
    private static final Map<Object,Entry> sources=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private RuntimeClockData() {}
    static void invalidate(){sources.clear();}
    static List<Float> manualHeights(Bundle settings){
        return settings!=null && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))
            ?SavedStretch.heights(settings.getFloatArray("manualHeights")):null;
    }
    private static List<Float> heights(Object provider,Object font)throws ReflectiveOperationException {
        Context context=(Context)Reflect.field(provider,"context");
        try{if(context.getDisplay()!=null && context.getDisplay().getDisplayId()!=0)return null;}
        catch(UnsupportedOperationException ignored){} // Package contexts may not carry a Display.

        int type=((Number)Reflect.call(provider,"getConsideredThemeClockType")).intValue();
        int id=((Number)Reflect.call(font,"getFontType")).intValue();
        if(type<=0 || id<=0)return null;
        int orientation=LayoutOrientation.current(context);
        Entry entry=sources.get(provider);
        if(entry==null || entry.type!=type || entry.font!=id || entry.orientation!=orientation){
            Bundle query=new Bundle();query.putInt("type",type);query.putInt("font",id);
            // Read once per source/layout. Never carry the depth blobs into this small cache.
            Bundle settings=SettingsBridge.read(context,query);
            List<Float> values=manualHeights(settings);
            if(settings==null)return null; // Provider may still be starting after an update.
            entry=new Entry(type,id,orientation,values);sources.put(provider,entry);
        }
        return entry.heights;
    }
    static void install(DressroomModule module,ClassLoader loader)throws ReflectiveOperationException {
        Class<?> provider=Class.forName("com.samsung.android.app.facewidget.utils.KeyguardClockProvider",false,loader);
        Method data=provider.getDeclaredMethod("getClockFontData");
        Method values=provider.getDeclaredMethod("getClockFontHeights");
        try(HookTransaction hooks=new HookTransaction()){
            hooks.add(module.hook(data).setId("dressroom-advanced.native-clock-source").intercept(chain->{
                Object result=chain.proceed();if(result==null)return null;
                try{return SavedStretch.font(result,heights(chain.getThisObject(),result));}
                catch(ReflectiveOperationException | RuntimeException ignored){return result;}
            })::unhook);
            hooks.add(module.hook(values).setId("dressroom-advanced.native-clock-source-heights").intercept(chain->{
                Object result=chain.proceed();
                try{
                    Object owner=chain.getThisObject();Object font=Reflect.call(owner,"getClockFontData");
                    List<Float> saved=heights(owner,font);return saved==null?result:saved;
                }catch(ReflectiveOperationException | RuntimeException ignored){return result;}
            })::unhook);
            Method wallpaper=provider.getDeclaredMethod("updateWallpaperColorsItem",Object.class);
            hooks.add(module.hook(wallpaper).setId("dressroom-advanced.native-clock-source-reset").intercept(chain->{
                sources.remove(chain.getThisObject());return chain.proceed();
            })::unhook);
            module.deoptimize(data);module.deoptimize(values);module.deoptimize(wallpaper);
            module.deoptimize(provider.getDeclaredMethod("hasStretchFontHeights"));
            Class<?> model=Class.forName("com.samsung.android.app.facewidget.FaceWidgetViewModel",false,loader);
            module.deoptimize(model.getDeclaredMethod("C"));
            Class<?> page=Class.forName("com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage",false,loader);
            for(String method:new String[]{"y","C","getFullscreenClockFontData"})module.deoptimize(page.getDeclaredMethod(method));
            hooks.commit();
        }
        android.util.Log.i(DressroomModule.TAG,"Native clock font source/measurement sync installed");
    }
}
