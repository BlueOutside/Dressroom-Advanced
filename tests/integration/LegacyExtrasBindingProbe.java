package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;

import dalvik.system.PathClassLoader;

public final class LegacyExtrasBindingProbe {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> owner = Class.forName("com.android.systemui.power.SecPowerUI", false, loader);
        Class<?> card =
                Class.forName("com.android.systemui.power.view.ChargerNowBarView", false, loader);
        NativeContract.field(owner, "mContext", Context.class);
        NativeContract.field(owner, "mChargerNowBarView", card);
        NativeContract.field(owner, "mCurrentChargingAnimation", String.class);
        NativeContract.method(owner, "onChargerAnimationEnd", void.class);
        NativeContract.method(owner, "removeNowBarView", void.class);
        NativeContract.method(card, "onAttachedToWindow", void.class);
        Class<?> bridge =
                Class.forName(
                        "com.android.systemui.facewidget.plugin.FaceWidgetNotificationControllerWrapper",
                        false,
                        loader);
        NativeContract.method(
                bridge,
                "initPlugin",
                void.class,
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.PluginNotificationController",
                        false,
                        loader),
                Context.class);
        Class<?> item =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                        false,
                        loader);
        item.getConstructor();
        NativeContract.method(bridge, "updateItem", void.class, item);
        NativeContract.method(bridge, "removeItem", void.class, item);
        for (String name :
                new String[] {
                    "setPrimaryInfo", "setSecondaryInfo", "setNowBarKey", "setNowBarPackage"
                }) NativeContract.method(item, name, void.class, String.class);
        NativeContract.method(item, "setContentViewForNormalCard", void.class, View.class);
        System.out.println(
                "8.5 SystemUI: charging ownership/completion and custom Now Bar bridge resolved");
    }
}
