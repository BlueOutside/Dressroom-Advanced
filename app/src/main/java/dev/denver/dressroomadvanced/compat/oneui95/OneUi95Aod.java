package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

final class OneUi95Aod {
    static final String WIDGET_RESIZE_ANCHOR = "Monotone.getResizeSpec: cell=";

    static boolean artworkCleanup(Method method, Class<?> card) {
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 10 && parameters[0] == card && parameters[1] == View.class
                && parameters[3] == float.class && parameters[4] == ViewGroup.class
                && parameters[5] == ViewGroup.class && parameters[6] == View.class
                && parameters[7] == int.class && parameters[8] == ViewGroup.class
                && parameters[9].getName().equals("androidx.cardview.widget.CardView");
    }

    static Set<String> synchronousBlurAnchors() {
        return Set.of("applyTextBlurInner: Blur applied successfully with ",
                "applyTextBlurInner was skipped by blurredTextViews is empty");
    }

    static Method clockTransition(Class<?> clock, String springAnchor)
            throws ReflectiveOperationException {
        ClassLoader loader = clock.getClassLoader();
        String clockType = DexSignatures.descriptor(clock);
        try {
            Method springPolicy = OneUi95Editor.unique(
                    DexSignatures.find(loader, Set.of(springAnchor)).stream()
                            .filter(body -> !Modifier.isStatic(body.access())
                                    && body.result().equals("Z")
                                    && body.parameters().equals(List.of("Ljava/util/ArrayList;", "Z")))
                            .toList(), "clock spring policy").method(loader);
            return OneUi95Editor.unique(DexSignatures.callers(springPolicy).stream()
                    .filter(body -> body.owner().equals(clockType)
                            && !Modifier.isStatic(body.access()) && body.result().equals("V")
                            && body.parameters().equals(List.of("F", "F", "Z")))
                    .toList(), "clock font transition").method(loader);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Clock transition DEX unavailable", invalidDex);
        }
    }

    static boolean updatesBriefBlur(DexSignatures.Body body, String style, Class<?> brief)
            throws NoSuchMethodException {
        Method controller;
        try {
            controller = brief.getDeclaredMethod("getGenUISuggestionManagerController");
        } catch (NoSuchMethodException missing) {
            return false;
        }
        String owner = DexSignatures.descriptor(controller.getReturnType());
        return body.calls().stream().anyMatch(call -> call.name().equals(controller.getName())
                        && call.result().equals(owner) && call.parameters().isEmpty()
                        && call.owner().equals(DexSignatures.descriptor(brief)))
                && body.calls().stream().anyMatch(call -> !call.isStatic()
                        && call.owner().equals(owner) && call.result().equals("V")
                        && call.parameters().equals(List.of(style)));
    }

    static List<Method> directionalWidgetBlur(Method refresh, Class<?> curves, Class<?> preset)
            throws ReflectiveOperationException, IOException {
        List<String> parameters = List.of(DexSignatures.descriptor(preset), "F", "Z");
        String owner = DexSignatures.descriptor(curves);
        var calls = DexSignatures.body(refresh).calls().stream()
                .filter(call -> call.isStatic() && call.owner().equals(owner)
                        && call.parameters().equals(parameters)).toList();
        var curve = OneUi95Editor.unique(calls.stream()
                .filter(call -> call.result().equals("[F")).toList(), "directional widget blur curve");
        var tint = OneUi95Editor.unique(calls.stream()
                .filter(call -> call.result().equals("Ljava/lang/Integer;")).toList(),
                "directional widget blur tint");
        ClassLoader loader = refresh.getDeclaringClass().getClassLoader();
        return List.of(curve.method(loader), tint.method(loader));
    }

    static Method briefBlurSubmission(ClassLoader loader, Class<?> style)
            throws ReflectiveOperationException {
        String anchor = "setBackgroundCornerRadius(...)";
        try {
            return OneUi95Editor.unique(DexSignatures.find(loader, Set.of(anchor)).stream()
                    .filter(body -> Modifier.isStatic(body.access()) && body.result().equals("V")
                            && body.parameters().equals(List.of("Landroid/widget/FrameLayout;",
                                    "Landroid/content/Context;", DexSignatures.descriptor(style),
                                    "Z", "Ljava/lang/Float;"))
                            && body.calls().stream().anyMatch(call -> !call.isStatic()
                                    && call.name().equals("getBlurStyle")
                                    && call.owner().equals(DexSignatures.descriptor(style)))
                            && body.calls().stream().anyMatch(call -> !call.isStatic()
                                    && call.name().equals("semSetBlurInfo")
                                    && call.parameters().equals(List.of("Landroid/view/SemBlurInfo;"))))
                    .toList(), "GenUI blur submission").method(loader);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("GenUI blur submission DEX unavailable", invalidDex);
        }
    }
}
