package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

final class OneUi80NowBarText {
    private static final String MANAGER =
            "Lcom/samsung/android/app/facewidget/domain/NowBarItemManager;";
    private static final String ITEM =
            "Lcom/android/systemui/plugins/keyguardstatusview/NowBarItem;";
    private static final String LIST = "Ljava/util/concurrent/CopyOnWriteArrayList;";
    private static final String FILTER_MARKER = "nowbar_key_need_to_blockcard";

    private OneUi80NowBarText() {}

    static Method filter(ClassLoader loader) throws ReflectiveOperationException {
        try {
            return OneUi95Editor.unique(DexSignatures.find(loader, Set.of(FILTER_MARKER))
                    .stream()
                    .filter(body -> body.owner().equals(MANAGER)
                            && body.parameters().equals(List.of(LIST))
                            && body.result().equals(LIST)
                            && body.calls().stream().anyMatch(call -> call.owner().equals(ITEM)
                                    && call.name().equals("getNotiID") && call.parameters().isEmpty()))
                    .toList(), "8.0 Now Bar notification filter").method(loader);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the 8.0 Now Bar filter", error);
        }
    }

    static io.github.libxposed.api.XposedInterface.HookHandle install(
            DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        Method filter = filter(loader);
        module.deoptimize(filter);
        return module.hook(filter).setId("dressroom-advanced.80-now-bar-custom-message")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object message = NowBarExtras.customMessage();
                    if (message == null
                            || !(chain.getArg(0) instanceof List<?> cards)
                            || !(result instanceof CopyOnWriteArrayList<?> visible)
                            || visible.contains(message)) return result;
                    int position = 0;
                    for (Object card : cards) {
                        if (card == message) {
                            // The explicit message is our own view. Keep Samsung's
                            // filtered result for every actual notification.
                            var restored = new CopyOnWriteArrayList<Object>(visible);
                            restored.add(Math.min(position, restored.size()), message);
                            return restored;
                        }
                        if (visible.contains(card)) position++;
                    }
                    return result;
                });
    }
}
