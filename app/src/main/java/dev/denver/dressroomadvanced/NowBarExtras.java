package dev.denver.dressroomadvanced;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Extends Samsung's Now Bar item lifecycle without extending its charging/doze session. */
final class NowBarExtras {
    private static final String KEY="dressroom_advanced_text";
    private static final ThreadLocal<Boolean> finishing=ThreadLocal.withInitial(()->false);
    private static final Map<Object,WeakReference<View>> held=new WeakHashMap<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static WeakReference<Object> wrapper=new WeakReference<>(null);
    private static Context context;
    private static ClassLoader loader;
    private static Object textItem;
    private static String textSignature="";
    private static boolean installed;
    private NowBarExtras() {}
    static void install(DressroomModule module,ClassLoader classes) {
        if(installed)return;loader=classes;
        try { Class.forName("com.android.systemui.power.feature.charginganimation.ChargingAnimationUI", false, classes); }
        catch (ClassNotFoundException legacy) { installLegacy(module, classes); return; }
        try {
            Class<?> bridge=Class.forName("com.android.systemui.facewidget.plugin.FaceWidgetNotificationControllerWrapper",false,classes);
            Class<?> plugin=Class.forName("com.android.systemui.plugins.keyguardstatusview.PluginNotificationController",false,classes);
            module.hook(bridge.getDeclaredMethod("initPlugin",plugin,Context.class)).setId("dressroom-advanced.extra-nowbar-host").intercept(chain->{
                Object result=chain.proceed();wrapper=new WeakReference<>(chain.getThisObject());textSignature="";textItem=null;
                if(chain.getArg(1) instanceof Context c)bind(c);
                main.post(NowBarExtras::updateText);return result;
            });
            Class<?> ui=Class.forName("com.android.systemui.power.feature.charginganimation.ChargingAnimationUI",false,classes);
            Method remove=ui.getDeclaredMethod("removeNowBarView");
            module.hook(remove).setId("dressroom-advanced.keep-charging-card").intercept(chain->{
                Object owner=chain.getThisObject();
                try {
                    Context c=(Context)Reflect.field(owner,"context");
                    if(finishing.get() && RuntimeExtras.enabled(c,"keepCharging") && plugged(c)
                            && "NowBar".equals(String.valueOf(Reflect.field(Reflect.call(Reflect.field(owner,"_uiState"),"getValue"),"currentOverlay")))) {
                        Object card=Reflect.field(owner,"chargerNowBarView");
                        if(card instanceof View view) {
                            bind(c);held.put(owner,new WeakReference<>(view));
                            settle(view);
                            view.setAlpha(1);view.setVisibility(View.VISIBLE);
                            // dismissOverlay still clears uiState and releases the native doze session.
                            return null;
                        }
                    }
                }catch(Exception e){module.report("Charging-card hold skipped",e);}
                held.remove(owner);return chain.proceed();
            });module.deoptimize(remove);
            Class<?> cardClass=Class.forName("com.android.systemui.power.view.ChargerNowBarView",false,classes);
            Method attach=cardClass.getDeclaredMethod("onAttachedToWindow");
            module.hook(attach).setId("dressroom-advanced.charging-card-reattach").intercept(chain->{
                View view=(View)chain.getThisObject();
                if(RuntimeExtras.enabled(view.getContext(),"keepCharging"))sync(view,false);
                Object result=chain.proceed();
                // Samsung posts its entry animation from onAttachedToWindow. Run after it.
                view.post(()->{if(isHeld(view))try{settle(view);}catch(Exception error){module.report("Charging reattach refresh unavailable",error);}});
                return result;
            });module.deoptimize(attach);
            Method resources=cardClass.getDeclaredMethod("switchAnimationResources",int.class);
            module.hook(resources).setId("dressroom-advanced.held-charging-resources").intercept(chain->{
                Object result=chain.proceed();View view=(View)chain.getThisObject();
                if(isHeld(view))try{settle(view);}catch(Exception error){module.report("Charging resource refresh unavailable",error);}
                return result;
            });module.deoptimize(resources);
            for(String name:new String[]{"com.android.systemui.power.view.ChargerNowBarView$addOnChargerAnimationEndHandler$1",
                    "com.android.systemui.power.feature.charginganimation.ChargingAnimationUI$forceDismissRunnable$1"}) {
                Class<?> end=Class.forName(name,false,classes);
                Method callback=end.getDeclaredMethod(name.contains("forceDismiss")?"run":"invokeSuspend",
                    name.contains("forceDismiss")?new Class<?>[0]:new Class<?>[]{Object.class});
                module.hook(callback).setId("dressroom-advanced.charging-finish-"+callback.getName()).intercept(chain->{
                    boolean previous=finishing.get();finishing.set(true);
                    try{return chain.proceed();}finally{finishing.set(previous);}
                });module.deoptimize(callback);
            }
            installed=true;
            RuntimeExtras.listen(()->{releaseIfNeeded();updateText();});
            android.util.Log.i(DressroomModule.TAG,"Charging-card and custom Now Bar hooks installed");
        }catch(Exception | LinkageError e){module.report("Now Bar extras unavailable",e);}
    }
    private static void installLegacy(DressroomModule module, ClassLoader classes) {
        try {
            Class<?> owner = Class.forName("com.android.systemui.power.SecPowerUI", false, classes);
            Class<?> card = Class.forName("com.android.systemui.power.view.ChargerNowBarView", false, classes);
            NativeContract.field(owner, "mContext", Context.class);
            NativeContract.field(owner, "mChargerNowBarView", card);
            NativeContract.field(owner, "mCurrentChargingAnimation", String.class);
            Method end = NativeContract.method(owner, "onChargerAnimationEnd", void.class);
            Method remove = NativeContract.method(owner, "removeNowBarView", void.class);
            Method attach = NativeContract.method(card, "onAttachedToWindow", void.class);
            Class<?> bridge = Class.forName("com.android.systemui.facewidget.plugin.FaceWidgetNotificationControllerWrapper", false, classes);
            Method plugin = NativeContract.method(bridge, "initPlugin", void.class,
                Class.forName("com.android.systemui.plugins.keyguardstatusview.PluginNotificationController", false, classes), Context.class);
            try (HookTransaction hooks = new HookTransaction()) {
                var connection = module.hook(plugin).setId("dressroom-advanced.85-nowbar-host").intercept(chain -> {
                    Object result = chain.proceed(); wrapper = new WeakReference<>(chain.getThisObject()); textSignature = ""; textItem = null;
                    if (chain.getArg(1) instanceof Context c) bind(c);
                    main.post(NowBarExtras::updateText); return result;
                }); hooks.add(connection::unhook); module.deoptimize(plugin);
                var completion = module.hook(end).setId("dressroom-advanced.85-charging-end").intercept(chain -> {
                    boolean previous = finishing.get(); finishing.set(true);
                    try { return chain.proceed(); } finally { finishing.set(previous); }
                }); hooks.add(completion::unhook); module.deoptimize(end);
                var hold = module.hook(remove).setId("dressroom-advanced.85-charging-hold").intercept(chain -> {
                    Object ui = chain.getThisObject();
                    try {
                        Context c = (Context) Reflect.field(ui, "mContext");
                        if (finishing.get() && RuntimeExtras.enabled(c, "keepCharging") && plugged(c)
                                && Reflect.field(ui, "mChargerNowBarView") instanceof View view
                                && ("now_bar_charging_vi".equals(Reflect.field(ui, "mCurrentChargingAnimation")) || isHeld(view))) {
                            settle(view); bind(c); held.put(ui, new WeakReference<>(view));
                            view.setAlpha(1); view.setVisibility(View.VISIBLE); return null;
                        }
                    } catch (Exception error) { module.report("8.5 charging hold skipped", error); }
                    held.remove(ui); return chain.proceed();
                }); hooks.add(hold::unhook); module.deoptimize(remove);
                var reattach = module.hook(attach).setId("dressroom-advanced.85-charging-reattach").intercept(chain -> {
                    View view = (View) chain.getThisObject();
                    if (RuntimeExtras.enabled(view.getContext(), "keepCharging")) sync(view, false);
                    Object result = chain.proceed();
                    view.post(() -> { if (isHeld(view)) try { settle(view); }
                        catch (Exception error) { module.report("8.5 charging reattach refresh unavailable", error); } });
                    return result;
                }); hooks.add(reattach::unhook); module.deoptimize(attach);
                hooks.commit(); installed = true;
            }
            RuntimeExtras.listen(() -> { releaseIfNeeded(); updateText(); });
            android.util.Log.i(DressroomModule.TAG, "8.5 charging-card and custom Now Bar hooks installed");
        } catch (Exception | LinkageError error) { module.report("8.5 Now Bar extras unavailable", error); }
    }
    private static boolean plugged(Context c) {
        Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery!=null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
    }
    private static void bind(Context c) {
        if(context!=null)return;context=c.getApplicationContext();RuntimeExtras.get(context);
        context.registerReceiver(new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                if(i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==0){releaseIfNeeded();return;}
                for(WeakReference<View> ref:new ArrayList<>(held.values())) {
                    View view=ref.get();if(view==null)continue;
                    sync(view,true);
                }
            }
        },new IntentFilter(Intent.ACTION_BATTERY_CHANGED),Context.RECEIVER_NOT_EXPORTED);
    }
    private static boolean isHeld(View view) {
        for(WeakReference<View> ref:held.values())if(ref.get()==view)return true;
        return false;
    }
    private static void settle(View view) throws ReflectiveOperationException {
        Object animation=Reflect.field(view,"chargerAnimationSet");
        // This APK uses androidx.core.animation, not android.animation.
        ChargingState.pause(animation);
        ChargingVisuals.finishLiquid((View)Reflect.call(view,"getBatteryProgressbar"));
        ChargingVisuals.restore(view);
        sync(view,true);
    }
    private static void sync(View view,boolean visual) {
        Intent battery=view.getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery==null)return;
        int percent=ChargingState.percent(battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),
            battery.getIntExtra(BatteryManager.EXTRA_SCALE,100));
        if(percent<0)return;
        try {
            Reflect.set(view,"currentBatteryLevel",percent);
            if(!visual)return;
            Object lazy=Reflect.field(view,"batteryLevel$delegate");
            ((TextView)Reflect.call(lazy,"getValue")).setText(String.format(java.util.Locale.getDefault(),"%d",percent));
            View progress=(View)Reflect.call(view,"getBatteryProgressbar");
            if(view.getWidth()>0)progress.setTranslationX(ChargingState.translation(view.getWidth(),
                ((Number)Reflect.field(view,"currentLayoutDirection")).intValue(),percent));
        }catch(ReflectiveOperationException error){android.util.Log.w(DressroomModule.TAG,"Charging level refresh unavailable",error);}
    }
    private static void releaseIfNeeded() {
        if(context==null || (plugged(context) && RuntimeExtras.enabled(context,"keepCharging")))return;
        for(Object ui:new ArrayList<>(held.keySet()))try{Reflect.call(ui,"removeNowBarView");}
        catch(Exception e){android.util.Log.w(DressroomModule.TAG,"Charging card cleanup failed",e);}
        held.clear();
    }
    private static void updateText() {
        Object host=wrapper.get();if(context==null || host==null)return;
        Bundle b=RuntimeExtras.get(context);
        try {
            if(!b.getBoolean("textCard")) {
                if(textItem!=null)Reflect.call(host,"removeItem",textItem);
                textItem=null;textSignature="";return;
            }
            String title=b.getString("title","");if(title.isBlank())title="My Now Bar";
            String message=b.getString("message","");String signature=title+"\u0000"+message;
            if(signature.equals(textSignature))return;
            Class<?> item=Class.forName("com.android.systemui.plugins.keyguardstatusview.NowBarItem",false,loader);
            Object card=item.getConstructor().newInstance();
            Reflect.call(card,"setNowBarKey",KEY);Reflect.call(card,"setNowBarPackage","com.android.systemui");
            Reflect.call(card,"setNowBarViewStyle",6);Reflect.call(card,"setUserId",android.os.Process.myUid()/100000);
            Reflect.call(card,"setPrimaryInfo",title);Reflect.call(card,"setSecondaryInfo",message);
            Reflect.call(card,"setContentViewForNormalCard",textView(title,message,false));
            Reflect.call(card,"setContentViewForExpandCard",textView(title,message,true));
            Bundle extra=new Bundle();extra.putInt("nowbar_key_screen_type",3);Reflect.call(card,"setExtraData",extra);
            Reflect.call(host,"updateItem",card);textItem=card;textSignature=signature;
        }catch(Exception e){android.util.Log.w(DressroomModule.TAG,"Custom Now Bar unavailable",e);}
    }
    private static View textView(String title,String message,boolean expanded) {
        LinearLayout rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(android.view.Gravity.CENTER_VERTICAL);int p=Math.round(16*context.getResources().getDisplayMetrics().density);
        rows.setPadding(p,p/2,p,p/2);
        TextView heading=new TextView(context);heading.setText(title);heading.setTextSize(15);heading.setTextColor(Color.WHITE);
        heading.setMaxLines(expanded?3:1);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);rows.addView(heading);
        if(!message.isEmpty()){TextView body=new TextView(context);body.setText(message);body.setTextSize(12);body.setTextColor(0xffdddddd);
            body.setMaxLines(expanded?5:1);body.setEllipsize(android.text.TextUtils.TruncateAt.END);rows.addView(body);}
        rows.setContentDescription(title+" "+message);return rows;
    }
}
