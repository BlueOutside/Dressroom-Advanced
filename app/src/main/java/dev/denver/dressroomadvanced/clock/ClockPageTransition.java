package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

record ClockPageTransition(Method update, Method attach, Method finish, Field source, Field target) {
    private static final String PAGE =
            "Lcom/samsung/android/app/facewidget/pages/FaceWidgetPage;";
    private static final String UPDATE = "updateContentsView()";
    private static final String ATTACH = "makeSourceData()";
    private static final String FINISH = "handleTransitionFinished()";
    private static final String VIEW = "Landroid/view/View;";
    private static final String GROUP = "Landroid/view/ViewGroup;";

    static ClockPageTransition resolve(ClassLoader loader)
            throws ReflectiveOperationException, IOException {
        var bodies = DexSignatures.find(loader, Set.of(UPDATE, ATTACH, FINISH));
        var page = one(bodies.stream()
                .filter(body -> body.owner().equals(PAGE) && body.matches(UPDATE)).toList(), UPDATE);
        var attach = one(bodies.stream()
                .filter(body -> body.matches(ATTACH) && calls(page, body)
                        && viewOperation(body, GROUP, "addView", VIEW, "I")
                        && viewOperation(body, VIEW, "setVisibility", "I")).toList(), ATTACH);
        var finish = one(bodies.stream()
                .filter(body -> body.owner().equals(attach.owner()) && body.matches(FINISH)
                        && calls(page, body) && viewOperation(body, GROUP, "addView", VIEW)
                        && viewOperation(body, GROUP, "removeView", VIEW)).toList(), FINISH);
        Field source = null;
        Field target = null;
        for (var read : attach.reads().stream().map(DexSignatures.Read::field).distinct().toList()) {
            if (!read.owner().equals(attach.owner()) || !read.type().equals(VIEW)
                    || finish.reads().stream().noneMatch(other -> other.field().equals(read))) continue;
            Field field = read.field(loader);
            if (Modifier.isFinal(field.getModifiers())) {
                if (source != null) throw new NoSuchFieldException("Ambiguous clock transition source");
                source = field;
            } else {
                if (target != null) throw new NoSuchFieldException("Ambiguous clock transition target");
                target = field;
            }
        }
        if (source == null || target == null)
            throw new NoSuchFieldException("Clock transition source or target");
        return new ClockPageTransition(page.method(loader), attach.method(loader), finish.method(loader),
                source, target);
    }

    private static DexSignatures.Body one(List<DexSignatures.Body> matches, String anchor)
            throws NoSuchMethodException {
        if (matches.size() != 1)
            throw new NoSuchMethodException("Expected one " + anchor + ", found " + matches.size());
        return matches.get(0);
    }

    private static boolean calls(DexSignatures.Body caller, DexSignatures.Body callee) {
        return caller.calls().stream().anyMatch(call -> !call.isStatic()
                && call.owner().equals(callee.owner()) && call.name().equals(callee.name())
                && call.parameters().equals(callee.parameters()) && call.result().equals(callee.result()));
    }

    private static boolean viewOperation(DexSignatures.Body body, String owner, String name,
            String... parameters) {
        return body.calls().stream().anyMatch(call -> !call.isStatic()
                && call.owner().equals(owner) && call.name().equals(name)
                && call.result().equals("V") && call.parameters().equals(List.of(parameters)));
    }

    View clock(Object transition, Field field, Class<?> base) {
        try {
            return clock((View) field.get(transition), base);
        } catch (IllegalAccessException | RuntimeException error) {
            DressroomModule.note("Clock transition view unavailable", error);
            return null;
        }
    }

    static View clock(View root, Class<?> base) {
        if (base.isInstance(root)) return root;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View clock = clock(group.getChildAt(i), base);
                if (clock != null) return clock;
            }
        }
        return null;
    }
}
