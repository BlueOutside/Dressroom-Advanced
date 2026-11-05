package dev.denver.dressroomadvanced;

import android.view.View;
import android.graphics.Canvas;
import android.view.ViewGroup;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Native glyph outlines are cached by onDraw, so blur must consume them after child drawing. */
final class ClockBlurSync {
    private static final Map<View,Consumer<View>> pending=new WeakHashMap<>();
    private static final ThreadLocal<View> rebuilding=new ThreadLocal<>();
    private static boolean installed;
    static synchronized void install(DressroomModule module)throws ReflectiveOperationException {
        if(installed)return;
        java.lang.reflect.Method draw=ViewGroup.class.getDeclaredMethod("dispatchDraw",Canvas.class);
        module.hook(draw).setId("dressroom-advanced.media-clock-glyph-drawn").intercept(chain->{
            Object result=chain.proceed();
            if(!pending.isEmpty())drawn((View)chain.getThisObject());
            return result;
        });module.deoptimize(draw);installed=true;
    }
    static boolean rebuilding(View clock){return rebuilding.get()==clock;}
    static boolean pending(View clock){return pending.containsKey(clock);}
    // Consumers receive the clock as an argument and must not capture it strongly.
    static boolean defer(View clock,Consumer<View> work){
        if(!clock.isAttachedToWindow()){cancel(clock);return false;}
        pending.put(clock,work);
        // Force this group to re-record too, even when its child display lists were dirty.
        clock.invalidate();return true;
    }
    static void drawn(View clock){
        Consumer<View> work=pending.get(clock);if(work==null)return;
        if(!clock.isAttachedToWindow()){cancel(clock);return;}
        if(clock.isLayoutRequested()){clock.invalidate();return;}
        pending.remove(clock);View previous=rebuilding.get();rebuilding.set(clock);
        // Consumers run inside ViewGroup.dispatchDraw: their failure must not crash rendering.
        try{work.accept(clock);}catch(RuntimeException ignored){}finally{if(previous==null)rebuilding.remove();else rebuilding.set(previous);}
    }
    static void cancel(View clock){pending.remove(clock);}
}
