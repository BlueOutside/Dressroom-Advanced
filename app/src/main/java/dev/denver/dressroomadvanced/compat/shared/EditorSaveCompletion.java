package dev.denver.dressroomadvanced;

import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Final wallpaper-save contract shared by the 8.5 and 9.0 editors. */
final class EditorSaveCompletion {
    private static final String START = "Start save.", CHECK = "checkSaveCondition";
    private static final String FAILED = "save condition is false.";
    private static final String COUNT = "OnSaveListener Done. ",
            FINISH = "Save done. Finish the Activity.";
    private static final String EDITOR =
            "Lcom/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity;";
    final Method started, completed;
    private final Field owner;
    private final Value count, components, activity, button;
    private final Class<?> mediatorType;

    private interface Value {
        Object get(Object target) throws ReflectiveOperationException;
    }

    EditorSaveCompletion(ClassLoader loader) throws ReflectiveOperationException {
        this(loader, discover(loader));
    }

    private static List<DexSignatures.Body> discover(ClassLoader loader)
            throws ReflectiveOperationException {
        try {
            return DexSignatures.find(loader, Set.of(START, CHECK, FAILED, COUNT, FINISH));
        } catch (java.io.IOException error) {
            throw new ReflectiveOperationException("Could not read editor save signatures", error);
        }
    }

    EditorSaveCompletion(ClassLoader loader, List<DexSignatures.Body> bodies)
            throws ReflectiveOperationException {
        DexSignatures.Body begin = null, end = null;
        for (DexSignatures.Body body : bodies) {
            if (body.matches(START)
                    && (body.anchors().containsKey(CHECK) || body.anchors().containsKey(FAILED))) {
                if (begin != null) throw new NoSuchMethodException("Ambiguous editor save entry");
                begin = body;
            }
            if (body.matches(COUNT, FINISH)) {
                if (end != null)
                    throw new NoSuchMethodException("Ambiguous editor save completion");
                end = body;
            }
        }
        if (begin == null || end == null)
            throw new NoSuchMethodException("Editor save signature not found");
        if (!begin.anchors().containsKey(CHECK)) {
            // 8.5 keeps validation in a separate boolean helper. 9.0 inlines it.
            int validators = 0;
            for (DexSignatures.Body candidate : bodies) {
                if (!candidate.owner().equals(begin.owner())
                        || !candidate.parameters().isEmpty()
                        || !candidate.result().equals("Z")
                        || (candidate.access() & 8) != 0
                        || !candidate.anchors().containsKey(CHECK)) continue;
                for (DexSignatures.Call call : begin.calls())
                    if (!call.isStatic()
                            && call.owner().equals(candidate.owner())
                            && call.name().equals(candidate.name())
                            && call.result().equals("Z")
                            && call.parameters().isEmpty()) {
                        validators++;
                        break;
                    }
            }
            if (validators != 1)
                throw new NoSuchMethodException("Ambiguous save validation helper");
        }
        started = begin.method(loader);
        completed = end.method(loader);
        mediatorType = started.getDeclaringClass();
        String mediator = begin.owner();
        owner =
                oneRead(end, end.owner(), Set.of("Ljava/lang/Object;", mediator), FINISH)
                        .field(loader);
        count = value(end, mediator, Set.of("I"), FINISH, loader);
        components = value(end, mediator, Set.of("Ljava/util/ArrayList;"), FINISH, loader);
        activity = value(end, mediator, Set.of(EDITOR, "Landroid/app/Activity;"), null, loader);
        button = value(begin, mediator, Set.of("Landroid/widget/Button;"), null, loader);
        // Both phases must read the same collection, not merely two integer counters.
        DexSignatures.Member beginList =
                oneRead(begin, mediator, Set.of("Ljava/util/ArrayList;"), null);
        Field collection = beginList.field(loader);
        componentsIdentity = collection;
    }

    private final Field componentsIdentity;

    private static Set<DexSignatures.Member> reads(
            DexSignatures.Body body, String owner, Set<String> types, String before) {
        int limit = before == null ? Integer.MAX_VALUE : body.anchors().getOrDefault(before, -1);
        Set<DexSignatures.Member> fields = new LinkedHashSet<>();
        for (DexSignatures.Read read : body.reads())
            if (read.offset() < limit
                    && read.field().owner().equals(owner)
                    && types.contains(read.field().type())) fields.add(read.field());
        return fields;
    }

    private static DexSignatures.Member oneRead(
            DexSignatures.Body body, String owner, Set<String> types, String before)
            throws NoSuchFieldException {
        var fields = reads(body, owner, types, before);
        if (fields.size() != 1) throw new NoSuchFieldException("Ambiguous editor save field role");
        return fields.iterator().next();
    }

    private static Value value(
            DexSignatures.Body body,
            String owner,
            Set<String> types,
            String before,
            ClassLoader loader)
            throws ReflectiveOperationException {
        var fields = reads(body, owner, types, before);
        if (fields.size() > 1) throw new NoSuchFieldException("Ambiguous direct save field role");
        if (fields.size() == 1) {
            Field field = fields.iterator().next().field(loader);
            return field::get;
        }
        // Kotlin's older inner callbacks use synthetic accessors instead of iget.
        int limit = before == null ? Integer.MAX_VALUE : body.anchors().getOrDefault(before, -1);
        Set<Method> getters = new LinkedHashSet<>();
        for (DexSignatures.Call call : body.calls())
            if (call.offset() < limit
                    && call.owner().equals(owner)
                    && types.contains(call.result())
                    && (call.isStatic() && call.parameters().equals(List.of(owner))
                            || !call.isStatic() && call.parameters().isEmpty()))
                getters.add(call.method(loader));
        if (getters.size() != 1) throw new NoSuchMethodException("Ambiguous save accessor role");
        Method getter = getters.iterator().next();
        return java.lang.reflect.Modifier.isStatic(getter.getModifiers())
                ? target -> getter.invoke(null, target)
                : target -> getter.invoke(target);
    }

    boolean belongsTo(Object mediator, Object editor) throws ReflectiveOperationException {
        return mediatorType.isInstance(mediator)
                && editor != null
                && activity.get(mediator) == editor;
    }

    boolean running(Object mediator) throws ReflectiveOperationException {
        return button.get(mediator) instanceof Button save && !save.isEnabled();
    }

    boolean finished(Object listener, Object editor) throws ReflectiveOperationException {
        Object mediator = owner.get(listener);
        if (!belongsTo(mediator, editor)) return false;
        Object saved = components.get(mediator);
        if (saved != componentsIdentity.get(mediator))
            throw new IllegalStateException("Save phases refer to different component lists");
        return saved instanceof ArrayList<?> pending
                && !pending.isEmpty()
                && ((Number) count.get(mediator)).intValue() == pending.size();
    }
}
