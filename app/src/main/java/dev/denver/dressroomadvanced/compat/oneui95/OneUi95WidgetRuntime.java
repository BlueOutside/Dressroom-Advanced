package dev.denver.dressroomadvanced;

import android.view.View;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class OneUi95WidgetRuntime {
    static final String CLOCK_PAGE =
            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage";

    record Layout(Method applyLayout, Method refresh, Class<?> unit, List<Method> callers) {}

    static Layout layout(Class<?> controller) throws ReflectiveOperationException, IOException {
        ClassLoader loader = controller.getClassLoader();
        Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
        Class<?> widgets = Class.forName(
                "com.samsung.android.app.facewidget.complication.WidgetIdList", false, loader);
        Method apply = NativeContract.method(controller, "applyWidgetLayout", Object.class,
                widgets, boolean.class, continuation);
        Method refresh = NativeContract.method(controller, "applyComplicationRefresh", Object.class,
                continuation);
        var applyBody = DexSignatures.body(apply, Set.of(" updateWidgetLayout "));
        var refreshBody = DexSignatures.body(refresh, Set.of("refreshComplicationGridAndWidgets"));
        if (!applyBody.anchors().containsKey(" updateWidgetLayout ")
                || !refreshBody.anchors().containsKey("refreshComplicationGridAndWidgets")
                || refreshBody.calls().stream().noneMatch(call ->
                        call.owner().equals(DexSignatures.descriptor(controller))
                                && call.name().equals(apply.getName())
                                && call.parameters().equals(List.of(DexSignatures.descriptor(widgets),
                                        "Z", DexSignatures.descriptor(continuation)))))
            throw new NoSuchMethodException("Complication layout completion contract");
        var callers = new LinkedHashSet<Method>();
        callers.addAll(completionCallers(apply, continuation));
        callers.addAll(completionCallers(refresh, continuation));
        return new Layout(apply, refresh, Class.forName("kotlin.Unit", false, loader),
                List.copyOf(callers));
    }

    private static Set<Method> completionCallers(Method target, Class<?> continuation)
            throws ReflectiveOperationException, IOException {
        Class<?> controller = target.getDeclaringClass();
        ClassLoader loader = controller.getClassLoader();
        var callers = new LinkedHashSet<Method>();
        for (var body : DexSignatures.callers(target)) {
            if (body.name().startsWith("<")) continue;
            Method method = body.method(loader);
            callers.add(method);
            // Kotlin's nested continuations reach the private suspend body through an accessor.
            if (!method.isSynthetic() || !Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() != controller
                    || method.getParameterCount() == 0 || method.getParameterTypes()[0] != controller)
                continue;
            for (var resume : DexSignatures.callers(method))
                if (!resume.name().startsWith("<")) callers.add(resume.method(loader));
        }
        if (callers.stream().noneMatch(method -> continuation.isAssignableFrom(method.getDeclaringClass())
                && method.getReturnType() == Object.class
                && List.of(method.getParameterTypes()).equals(List.of(Object.class))))
            throw new NoSuchMethodException("Widget continuation for " + target.getName());
        return callers;
    }

    static Method currentClock(Class<?> page) throws NoSuchMethodException {
        return NativeContract.method(page, "getCurrentClockView", View.class);
    }
}
