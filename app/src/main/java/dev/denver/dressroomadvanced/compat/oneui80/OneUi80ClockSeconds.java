package dev.denver.dressroomadvanced;

import android.app.KeyguardManager;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

final class OneUi80ClockSeconds {
    private static final String DASHBOARD = "showFaceWidgetFullScreen() fromAOD=false, isKeyguardVisible=";
    private static final String WINDOW_MANAGER =
            "Lcom/samsung/android/app/facewidget/FaceWidgetWindowManager;";
    private static final String FACEWIDGET = "com.samsung.android.app.facewidget.";
    private static final ClassValue<Optional<Field>> clockGravity = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            try {
                Method setter = type.getMethod("setClockGravity", int.class);
                var body = DexSignatures.body(setter);
                return Optional.of(body.uniqueWrite(body.owner(), "I", null).field(type.getClassLoader()));
            } catch (ReflectiveOperationException | IOException | RuntimeException unavailable) {
                return Optional.empty();
            }
        }
    };

    private OneUi80ClockSeconds() {}

    static int gravity(View clock) throws IllegalAccessException {
        Field field = clockGravity.get(clock.getClass()).orElse(null);
        return field == null ? Gravity.CENTER_HORIZONTAL : field.getInt(clock);
    }

    record Dashboard(Method open, Field controller, Field model, Field status) {
        void install(DressroomModule module, HookTransaction hooks, Predicate<View> toggle) {
            hooks.add(module.hook(open).setId("dressroom-advanced.80-seconds-dashboard")
                    .intercept(chain -> {
                        try {
                            if (!Boolean.TRUE.equals(chain.getArg(0))) {
                                View root = statusView(chain.getThisObject());
                                if (awakeLockscreen(root) && toggle.test(root)) return null;
                            }
                        } catch (RuntimeException error) {
                            DressroomModule.note("8.0 inline clock seconds fallback", error);
                        }
                        return chain.proceed();
                    })::unhook);
            module.deoptimize(open);
        }

        private boolean awakeLockscreen(View root) {
            if (root == null || !root.isAttachedToWindow() || !root.isShown()) return false;
            var context = root.getContext();
            var keyguard = context.getSystemService(KeyguardManager.class);
            var power = context.getSystemService(PowerManager.class);
            return keyguard != null && keyguard.isKeyguardLocked()
                    && power != null && power.isInteractive();
        }

        private View statusView(Object dashboard) {
            try {
                Object owner = controller.get(dashboard);
                Object viewModel = owner == null ? null : model.get(owner);
                return viewModel == null ? null : (View) status.get(viewModel);
            } catch (IllegalAccessException | RuntimeException error) {
                DressroomModule.note("8.0 clock seconds status view unavailable", error);
                return null;
            }
        }
    }

    static Dashboard dashboard(ClassLoader loader) throws ReflectiveOperationException, IOException {
        var entry = OneUi95Editor.unique(DexSignatures.find(loader, Set.of(DASHBOARD)).stream()
                .filter(body -> body.anchors().containsKey(DASHBOARD) && (body.access() & 8) == 0
                        && body.parameters().equals(List.of("Z")) && body.result().equals("V"))
                .toList(), "8.0 clock dashboard open");
        var call = OneUi95Editor.unique(entry.calls().stream()
                .filter(c -> !c.isStatic() && c.owner().equals(WINDOW_MANAGER)
                        && c.result().equals("Z") && c.parameters().size() == 1)
                .toList(), "8.0 clock dashboard window");
        Method window = call.method(loader);
        if (!View.class.isAssignableFrom(window.getParameterTypes()[0]))
            throw new NoSuchMethodException("8.0 dashboard window view");
        Method open = entry.method(loader);
        Class<?> controller = Class.forName(FACEWIDGET + "FaceWidgetController", false, loader);
        Class<?> model = Class.forName(FACEWIDGET + "FaceWidgetViewModel", false, loader);
        Class<?> status = Class.forName(FACEWIDGET + "SecKeyguardStatusView", false, loader);
        return new Dashboard(open,
                NativeContract.findField(open.getDeclaringClass(), null, controller),
                NativeContract.findField(controller, null, model),
                NativeContract.findField(model, null, status));
    }
}
