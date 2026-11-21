package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

final class OneUi80MediaHooks {
    private static final String PAGE =
            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage";
    private static final Map<View, State> screens = new WeakHashMap<>();

    private record State(boolean expanded, Field member, WeakReference<View> clock) {}

    private OneUi80MediaHooks() {}

    static Field clockField(ClassLoader loader, Class<?> base) throws ReflectiveOperationException {
        Class<?> page = Class.forName(PAGE, false, loader);
        return NativeContract.findField(page, null, base);
    }

    static io.github.libxposed.api.XposedInterface.HookHandle install(
            DressroomModule module, Class<?> base) throws ReflectiveOperationException, IOException {
        ClassLoader loader = base.getClassLoader();
        var observer = OneUi95MediaState.resolve(loader);
        Field clock = clockField(loader, base);
        module.deoptimize(observer.changed());
        return module.hook(observer.changed()).setId("dressroom-advanced.80-media-clock")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object owner = chain.getThisObject();
                        if (observer.status().get(owner) instanceof View status) {
                            boolean expanded = observer.expanded().getBoolean(owner);
                            State old = screens.get(status);
                            View previous = old == null ? null : old.clock().get();
                            if (old == null || old.expanded() != expanded
                                    || previous == null || !previous.isAttachedToWindow()) {
                                View active = findClock(status, clock);
                                sync(status, clock, previous, active, expanded);
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        DressroomModule.note("8.0 media clock refresh unavailable", error);
                    }
                    return result;
                });
    }

    static void attached(View clock) {
        if (screens.isEmpty()) return;
        for (View parent = clock; parent != null;
                parent = parent.getParent() instanceof View view ? view : null) {
            State state = screens.get(parent);
            if (state == null || !state.expanded()) continue;
            View status = parent;
            clock.post(() -> {
                State current = screens.get(status);
                if (current == null || !current.expanded() || !clock.isAttachedToWindow()) return;
                try {
                    if (findClock(status, current.member()) == clock)
                        sync(status, current.member(), current.clock().get(), clock, true);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    DressroomModule.note("8.0 media clock attachment unavailable", error);
                }
            });
            return;
        }
    }

    private static View findClock(View owner, Field member) throws IllegalAccessException {
        if (member.getDeclaringClass().isInstance(owner)) return (View) member.get(owner);
        if (owner instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findClock(group.getChildAt(i), member);
                if (candidate != null) return candidate;
            }
        return null;
    }

    private static void sync(View status, Field member, View previous, View active, boolean expanded) {
        screens.put(status, new State(expanded, member, new WeakReference<>(active)));
        if (previous != null && previous != active) {
            MediaClockVisibility.update(previous, false);
            MediaDepthTransition.restore(previous);
        }
        if (active != null) {
            MediaClockVisibility.update(active, expanded);
            MediaDepthTransition.update(active, expanded, null);
            ClockInlineSeconds.onMediaChanged(active);
        }
    }
}
