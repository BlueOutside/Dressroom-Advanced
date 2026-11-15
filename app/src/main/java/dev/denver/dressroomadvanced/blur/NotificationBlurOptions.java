package dev.denver.dressroomadvanced;

import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

final class NotificationBlurOptions {
    static final String COLORIZED = "blurColorizedNotifications";
    static final String CUSTOM = "blurCustomNotifications";
    private static final String ROWS = "com.android.systemui.statusbar.notification.row.";
    private static final ThreadLocal<Policy> policy = new ThreadLocal<>();
    private static boolean installed;

    private record Policy(View row, Notification colorized, boolean custom) {}

    private NotificationBlurOptions() {}

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (installed || android.os.Build.VERSION.SDK_INT < 37) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> background = Class.forName(ROWS + "ActivatableNotificationView", false, loader);
            Class<?> row = Class.forName(ROWS + "ExpandableNotificationRow", false, loader);
            Field onKeyguard = NativeContract.field(background, "mOnKeyguard", boolean.class);
            Field entry = backgroundEntry(row);
            Method sbn = entry.getType().getMethod("getSbn");
            if (sbn.getReturnType() != StatusBarNotification.class) return;
            Method eligible = NativeContract.method(background, "isBlurBackground", boolean.class);
            Method colorized = Notification.class.getMethod("isColorized");
            Method custom = resolveCustomPolicy(loader, row, DexSignatures.body(eligible, Set.of()).calls());
            if (custom == null) return;
            hooks.add(module.hook(eligible).setId("dressroom-advanced.lockscreen-notification-blur-policy")
                    .intercept(chain -> {
                        Object owner = chain.getThisObject();
                        Policy next = null;
                        if (row.isInstance(owner) && onKeyguard.getBoolean(owner)) {
                            View view = (View) owner;
                            try {
                                var options = RuntimeExtras.get(view.getContext());
                                boolean wantsColor = options.getBoolean(COLORIZED);
                                boolean wantsCustom = options.getBoolean(CUSTOM);
                                if ((wantsColor || wantsCustom)
                                        && BlurAppearance.liveWindowBlurAllowed(view.getContext())) {
                                    Object adapter = entry.get(owner);
                                    StatusBarNotification data = adapter == null ? null
                                            : (StatusBarNotification) sbn.invoke(adapter);
                                    if (data != null)
                                        next = new Policy(view, wantsColor ? data.getNotification() : null,
                                                wantsCustom);
                                }
                            } catch (ReflectiveOperationException | RuntimeException ignored) {
                            }
                        }
                        Policy previous = policy.get();
                        if (next == null) policy.remove();
                        else policy.set(next);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) policy.remove();
                            else policy.set(previous);
                        }
                    })::unhook);
            hooks.add(module.hook(colorized).setId("dressroom-advanced.notification-colorized-glass")
                    .intercept(chain -> {
                        Policy current = policy.get();
                        return current != null && current.colorized == chain.getThisObject()
                                ? false : chain.proceed();
                    })::unhook);
            hooks.add(module.hook(custom).setId("dressroom-advanced.notification-custom-glass")
                    .intercept(chain -> {
                        Policy current = policy.get();
                        return current != null && current.custom && current.row == chain.getArg(0)
                                ? false : chain.proceed();
                    })::unhook);
            module.deoptimize(eligible);
            module.deoptimize(custom);
            hooks.commit();
            installed = true;
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            module.report("Optional lockscreen notification blur controls unavailable", unavailable);
        }
    }

    static Method resolveCustomPolicy(
            ClassLoader loader, Class<?> row, java.util.List<DexSignatures.Call> calls)
            throws ReflectiveOperationException {
        String rowType = DexSignatures.descriptor(row);
        boolean readsColor =
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner().equals("Landroid/app/Notification;")
                                                && call.name().equals("isColorized")
                                                && call.parameters().isEmpty()
                                                && call.result().equals("Z"));
        var customCalls =
                calls.stream()
                        .filter(
                                call ->
                                        call.isStatic()
                                                && call.name().equals("isCustom")
                                                && call.parameters().equals(java.util.List.of(rowType))
                                                && call.result().equals("Z"))
                        .toList();
        if (!readsColor || customCalls.size() != 1) return null;
        return customCalls.get(0).method(loader);
    }

    private static Field backgroundEntry(Class<?> row) throws NoSuchFieldException {
        Field field = row.getDeclaredField("mEntryAdapter");
        field.setAccessible(true);
        return field;
    }
}
