package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Keep native widget contents and compositor backgrounds on the same media-return fade. */
final class WidgetMediaReveal {
    private static final class Group {
        final WeakReference<View> root;
        float factor=1;
        boolean closing;
        Group(View root){this.root=new WeakReference<>(root);}
    }
    private static final class Alpha {
        float nativeAlpha=1;
        int nativeBlur=255;
        Group group;
    }
    private static final Map<View,Group> groups=new WeakHashMap<>();
    private static final Map<Object,Group> observers=new WeakHashMap<>();
    private static final Map<View,Alpha> hosts=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> writing=new ThreadLocal<>();
    private static final java.util.Set<ClassLoader> installed=java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private WidgetMediaReveal(){}
    static void bind(View root,Object observer){
        Group group=groups.computeIfAbsent(root,Group::new);observers.put(observer,group);
    }
    static void forget(View root){
        Group group=groups.remove(root);if(group==null)return;group.factor=1;group.closing=false;
        for(var entry:hosts.entrySet())if(entry.getValue().group==group){apply(entry.getKey(),entry.getValue());entry.getValue().group=null;}
        observers.values().removeIf(value->value==group);
    }
    private static Group group(View view){
        for(View parent=view;parent!=null;parent=parent.getParent() instanceof View up?up:null){Group value=groups.get(parent);if(value!=null)return value;}
        return null;
    }
    private static float factor(View view,Alpha alpha){
        Group group=group(view);alpha.group=group;return group==null?1:group.factor;
    }
    static float visualAlpha(float nativeAlpha,float factor){
        // Samsung's media observer supplies 255 to this float View-alpha API. Preserve that
        // outside our fade; while fading, multiply its visible opacity rather than 255.
        return factor>=1?nativeAlpha:Math.max(0,Math.min(1,nativeAlpha))*Math.max(0,factor);
    }
    static void frame(ViewGroup window,float remaining,boolean active){
        for(Group group:groups.values()){
            View root=group.root.get();if(root==null || root.getWindowToken()!=window.getWindowToken() || !group.closing)continue;
            group.factor=active?Math.max(0,Math.min(1,1-remaining)):1;
            if(!active || group.factor>=1)group.closing=false;
            for(var entry:hosts.entrySet())if(group(entry.getKey())==group)apply(entry.getKey(),entry.getValue());
        }
    }
    private static void apply(View view,Alpha alpha){
        Group group=group(view);float factor=group==null?1:group.factor;
        float desired=visualAlpha(alpha.nativeAlpha,factor);int blur=Math.round(alpha.nativeBlur*factor);
        Boolean previous=writing.get();writing.set(true);
        try{
            if(view.getAlpha()!=desired)Reflect.call(view,"setAlpha",alpha.nativeAlpha);
            Object blurView=Reflect.call(view,"getBlurView");
            int stored=((Number)Reflect.field(view,"blurBackgroundAlpha")).intValue();
            boolean changed=stored!=blur || blurView instanceof View background && background.getBackground()!=null && background.getBackground().getAlpha()!=blur;
            if(changed)Reflect.call(view,"setBlurBackgroundAlpha",alpha.nativeBlur);
        }catch(ReflectiveOperationException | RuntimeException error){android.util.Log.w(DressroomModule.TAG,"Native widget media fade unavailable",error);}
        finally{if(previous==null)writing.remove();else writing.set(previous);}
    }
    static void install(DressroomModule module,ClassLoader loader)throws ReflectiveOperationException {
        if(installed.contains(loader))return;
        Class<?> host=Class.forName("com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer",false,loader);
        Class<?> observer=Class.forName("com.samsung.android.app.facewidget.complication.WidgetObserverManager",false,loader);
        var opacity=host.getDeclaredMethod("setAlpha",float.class);
        var background=host.getDeclaredMethod("setBlurBackgroundAlpha",int.class);
        var refresh=host.getDeclaredMethod("refreshBlur",float.class);
        var state=observer.getDeclaredMethod("mediaExpandStateObserver$lambda$0",observer,boolean.class);
        try(HookTransaction hooks=new HookTransaction()){
            hooks.add(module.hook(opacity).setId("dressroom-advanced.widget-media-opacity").intercept(chain->{
                View view=(View)chain.getThisObject();Alpha alpha=hosts.computeIfAbsent(view,ignored->new Alpha());
                float nativeValue=((Number)chain.getArg(0)).floatValue();
                if(!Boolean.TRUE.equals(writing.get()))alpha.nativeAlpha=nativeValue;
                float actual=visualAlpha(nativeValue,factor(view,alpha));
                return actual==nativeValue?chain.proceed():chain.proceed(new Object[]{actual});
            })::unhook);
            hooks.add(module.hook(background).setId("dressroom-advanced.widget-media-background-opacity").intercept(chain->{
                View view=(View)chain.getThisObject();Alpha alpha=hosts.computeIfAbsent(view,ignored->new Alpha());
                int value=((Number)chain.getArg(0)).intValue();if(!Boolean.TRUE.equals(writing.get()))alpha.nativeBlur=value;
                int actual=Math.round(value*factor(view,alpha));return value==actual?chain.proceed():chain.proceed(new Object[]{actual});
            })::unhook);
            hooks.add(module.hook(refresh).setId("dressroom-advanced.widget-media-refresh").intercept(chain->{
                Object result=chain.proceed();View view=(View)chain.getThisObject();Alpha alpha=hosts.get(view);
                if(alpha!=null && factor(view,alpha)<1)apply(view,alpha);return result;
            })::unhook);
            hooks.add(module.hook(state).setId("dressroom-advanced.widget-media-state").intercept(chain->{
                Group group=observers.get(chain.getArg(0));View root=group==null?null:group.root.get();
                if(root!=null){
                    group.closing=Boolean.FALSE.equals(chain.getArg(1)) && !RuntimeExtras.enabled(root.getContext(),"fullscreenArt");
                    group.factor=group.closing?0:1;
                }
                return chain.proceed();
            })::unhook);
            module.deoptimize(opacity);module.deoptimize(background);module.deoptimize(refresh);module.deoptimize(state);
            module.deoptimize(host.getDeclaredMethod("updateHostViewAlpha",float.class));
            module.deoptimize(Class.forName("aod.i99",false,loader).getDeclaredMethod("onChanged",Object.class));
            hooks.commit();installed.add(loader);
        }
    }
}
