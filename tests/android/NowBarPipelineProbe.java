package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

/** Real Android views exercise gap, expanded-card and released-widget decisions. */
public final class NowBarPipelineProbe {
    static final IBinder token=new Binder();
    public static class Root extends FrameLayout {
        public boolean U,expanded,settings;
        Root(Context c){super(c);}
        public boolean k(){return expanded;}
        public boolean n(){return settings;}
        @Override public IBinder getWindowToken(){return token;}
    }
    public static class Content extends View {
        boolean measured=true;
        Content(Context c){super(c);}
        @Override public boolean isShown(){return true;}
        @Override public boolean getGlobalVisibleRect(Rect r,Point offset){r.set(100,500,400,600);return measured;}
        @Override public IBinder getWindowToken(){return token;}
    }
    public static class Card extends FrameLayout {
        Content content;
        Card(Context c){super(c);content=new Content(c);addView(content);}
        public View getNormalContentRootView(){return content;}
    }
    public record LazyValue(View value){public View getValue(){return value;}}
    public static class Charger extends Root {
        public LazyValue batteryLevelContainer$delegate;
        View icon,shadow;
        Charger(Context c){super(c);batteryLevelContainer$delegate=new LazyValue(new View(c));icon=new View(c);shadow=new View(c);}
        public View getBatteryIcon(){return icon;}
        public View getBatteryIconShadow(){return shadow;}
    }
    static int checks;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        Class<?> memory=Class.forName("com.android.internal.os.ApplicationSharedMemory");
        memory.getMethod("setInstance",memory).invoke(null,memory.getMethod("create").invoke(null));
        Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
        Context context=(Context)at.getMethod("getSystemContext").invoke(thread);
        Root root=new Root(context);Card card=new Card(context);root.addView(card);
        check(Boolean.TRUE.equals(PillTouchBounds.contains(root,200,550)),"Pill lost touches");
        check(Boolean.FALSE.equals(PillTouchBounds.contains(root,200,200)),"Gap blocks unlock");
        check(Boolean.FALSE.equals(PillTouchBounds.contains(root,700,550)),"Outside width blocks unlock");
        root.expanded=true;check(PillTouchBounds.contains(root,200,200)==null,"Expanded override");root.expanded=false;
        root.settings=true;check(PillTouchBounds.contains(root,200,200)==null,"Settings override");root.settings=false;
        root.U=true;check(PillTouchBounds.contains(root,200,200)==null,"Expansion override");root.U=false;
        card.content.measured=false;check(PillTouchBounds.contains(root,200,550)==null,"Unknown layout must defer");card.content.measured=true;
        card.setAlpha(0);check(PillTouchBounds.contains(root,200,550)==null,"Invisible pill considered visible");card.setAlpha(1);
        Path cover=new Path();cover.addRect(0,0,100,100,Path.Direction.CW);WidgetMediaOcclusion.publish(root,cover);
        check(WidgetMediaOcclusion.source(root)==null,"Unregistered root inherits cover");
        WidgetMediaOcclusion.register(root);
        check(WidgetMediaOcclusion.source(card.content)!=null,"Registered descendant lost cover");
        WidgetMediaOcclusion.forget(root);
        check(WidgetMediaOcclusion.source(card.content)==null,"Released widget retains cover");
        Root replacement=new Root(context);WidgetMediaOcclusion.register(replacement);
        check(WidgetMediaOcclusion.source(replacement)!=null,"Replacement lost window cover");
        WidgetMediaOcclusion.publish(root,null);check(WidgetMediaOcclusion.source(replacement)==null,"Closed media retains cover");
        Charger charger=new Charger(context);charger.setAlpha(.35f);charger.setTranslationY(40);
        View text=charger.batteryLevelContainer$delegate.getValue();text.setAlpha(0);text.setScaleX(1.05f);text.setTranslationY(-12);
        charger.icon.setAlpha(0);charger.shadow.setTranslationX(8);charger.shadow.setTranslationY(8);
        ChargingVisuals.restore(charger);
        check(text.getAlpha()==1 && text.getScaleX()==1 && text.getTranslationY()==0,"Held charging text remains hidden");
        check(charger.icon.getAlpha()==1 && charger.shadow.getTranslationX()==0 && charger.shadow.getTranslationY()==0,"Held icon entry offsets remain");
        check(charger.getAlpha()==.35f && charger.getTranslationY()==40,"Native card swipe overwritten");
        System.out.println("Now Bar touch and widget lifecycle: "+checks+" checks passed");
        System.exit(0);
    }
}
