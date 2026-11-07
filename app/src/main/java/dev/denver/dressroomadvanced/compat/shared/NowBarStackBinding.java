package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/** Checked entry points for the stack engines; obfuscated names live with their provider. */
final class NowBarStackBinding {
    record Names(
            String model,
            String states,
            String manager,
            String stackSize,
            String keys,
            String key,
            String items,
            String restricted,
            String mode,
            String screenOn,
            String dragging,
            String reordering,
            String dismissing,
            String expanding,
            String animationManager,
            String animationsEnabled,
            String animate,
            boolean countedAnimate,
            String bindItem,
            String hideContents,
            String wave,
            String resources) {}

    final Class<?> stack, card, model;
    final Field items, restricted, mode, screenOn, dragging, reordering, dismissing, expanding;
    final Field animationManager, animationsEnabled, state, animator;
    final Method refresh,
            animate,
            expanded,
            content,
            data,
            item,
            key,
            blur,
            bindItem,
            hideContents,
            wave;
    final Constructor<?> stackSize;
    final String resources;

    static NowBarStackBinding of(Class<?> clock) throws ReflectiveOperationException {
        ClockBinding provider = ClockBinding.of(clock);
        return new NowBarStackBinding(
                clock.getClassLoader(),
                provider.legacy
                        ? (provider.clockStyle ? OneUi85Stack.names() : OneUi85AodStack.names())
                        : OneUi9Stack.names());
    }

    NowBarStackBinding(ClassLoader loader, Names names) throws ReflectiveOperationException {
        stack =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.NowBarCardStackView",
                        false,
                        loader);
        card = Class.forName("com.samsung.android.app.facewidget.presentation.a", false, loader);
        Class<?> itemType =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                        false,
                        loader);
        model = Class.forName(names.model, false, loader);
        Class<?> manager = Class.forName(names.manager, false, loader);
        items = NativeContract.field(stack, names.items, ArrayList.class);
        restricted = NativeContract.field(stack, names.restricted, boolean.class);
        mode = NativeContract.assignableField(stack, names.mode, Enum.class);
        screenOn = NativeContract.field(stack, names.screenOn, boolean.class);
        dragging = NativeContract.field(stack, names.dragging, boolean.class);
        reordering = NativeContract.field(stack, names.reordering, boolean.class);
        dismissing = NativeContract.field(stack, names.dismissing, boolean.class);
        expanding = NativeContract.field(stack, names.expanding, boolean.class);
        animationManager = NativeContract.field(stack, names.animationManager, manager);
        animationsEnabled = NativeContract.field(manager, names.animationsEnabled, boolean.class);
        state = NativeContract.field(model, "a", Class.forName(names.states, false, loader));
        animator = NativeContract.field(model, "f", Animator.class);
        refresh = NativeContract.method(stack, "q", void.class, CopyOnWriteArrayList.class);
        animate =
                names.countedAnimate
                        ? NativeContract.method(
                                manager, names.animate, void.class, View.class, model, int.class)
                        : NativeContract.method(
                                manager, names.animate, void.class, View.class, model);
        expanded = NativeContract.method(stack, "k", boolean.class);
        content = NativeContract.method(card, "getNormalContentRootView", ViewGroup.class);
        data = NativeContract.method(card, "getShowHideAnimationData", model);
        item = NativeContract.method(card, "getNowbarItem", itemType);
        blur = NativeContract.method(card, "getBlurContainer", View.class);
        bindItem = NativeContract.method(card, names.bindItem, void.class, itemType, Integer.class);
        hideContents =
                NativeContract.method(
                        card, names.hideContents, void.class, int.class, boolean.class);
        key = Class.forName(names.keys, false, loader).getDeclaredMethod(names.key, itemType);
        key.setAccessible(true);
        stackSize =
                Class.forName(names.stackSize, false, loader)
                        .getDeclaredConstructor(stack, int.class);
        stackSize.setAccessible(true);
        wave =
                names.wave == null
                        ? null
                        : NativeContract.method(
                                Class.forName(
                                        "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView",
                                        false,
                                        loader),
                                names.wave,
                                void.class);
        resources = names.resources;
    }
}
