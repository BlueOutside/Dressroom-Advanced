package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Samsung window blur is drawn by a sibling host, outside the clock's canvas. */
final class DepthBlurHooks {
    private static final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Scope> current = new ThreadLocal<>();
    private static final Set<View> verified = Collections.newSetFromMap(new WeakHashMap<>());
    private record Scope(View clock, PointF origin) {}
    private DepthBlurHooks() {}
    static synchronized void install(DressroomModule module, Class<?> base) {
        ClassLoader loader = base.getClassLoader();
        WidgetDepthBlur.install(module, loader);
        if (installed.contains(loader)) return;
        java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> handles = new java.util.ArrayList<>();
        try {
            Class<?> strategy = Class.forName("aod.tx0", false, loader);
            Class<?> weather = Class.forName("com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader);
            Class<?> options = Class.forName("aod.xb0", false, loader);
            Method render = strategy.getDeclaredMethod("b", android.widget.ImageView.class, weather, options);
            Method clip = Class.forName("aod.e70", false, loader).getDeclaredMethod("a",
                Class.forName("android.view.SemBlurInfo$Builder"), Path.class);
            handles.add(module.hook(render).setId("dressroom-advanced.depth-blur-owner").intercept(chain -> {
                Scope previous = current.get();
                try {
                    try {
                        Object owner = chain.getThisObject();
                        Object clock = Reflect.field(Reflect.field(owner, "b"), "o");
                        Object host = Reflect.call(owner, "d");
                        if (clock instanceof View && host instanceof ViewGroup)
                            current.set(new Scope((View) clock, (PointF) Reflect.call(owner, "e", host)));
                        else current.remove();
                    } catch (Exception error) { current.remove(); module.report("Blur depth owner unavailable", error); }
                    return chain.proceed();
                } finally { if (previous == null) current.remove(); else current.set(previous); }
            }));
            handles.add(module.hook(clip).setId("dressroom-advanced.depth-native-blur-path").intercept(chain -> {
                Scope scope = current.get();
                if (scope == null) return chain.proceed();
                MediaFullscreenExtras.clockPath(scope.clock,scope.origin.x,scope.origin.y,(Path)chain.getArg(1));
                Path subject = DepthEffects.nativeSubjectPath(scope.clock, scope.origin.x, scope.origin.y);
                if (subject == null) return chain.proceed();
                Path visible = DepthGeometry.subtract((Path)chain.getArg(1),subject);
                if (verified.add(scope.clock)) android.util.Log.i(DressroomModule.TAG,
                    "Depth applied to native blur path: " + scope.clock.getClass().getSimpleName());
                return chain.proceed(new Object[]{chain.getArg(0), visible});
            }));
            module.deoptimize(render); module.deoptimize(clip);
            Method clear=strategy.getDeclaredMethod("c");
            handles.add(module.hook(clear).setId("dressroom-advanced.media-clock-blur-clear").intercept(chain->{
                Object result=chain.proceed();try{Object clock=Reflect.field(Reflect.field(chain.getThisObject(),"b"),"o");
                    if(clock instanceof View view)MediaFullscreenExtras.clockPath(view,0,0,null);
                }catch(Exception ignored){}return result;
            }));module.deoptimize(clear);
            installed.add(loader);
            android.util.Log.i(DressroomModule.TAG, "Native blur subject clipping installed");
        } catch (Exception | LinkageError error) {
            for (io.github.libxposed.api.XposedInterface.HookHandle handle : handles) handle.unhook();
            module.report("Native blur depth unavailable", error);
        }
    }
}
