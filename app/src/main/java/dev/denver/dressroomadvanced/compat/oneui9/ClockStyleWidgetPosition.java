package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.util.SizeF;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Keeps an explicitly positioned clock in the same coordinate area as the editor. */
final class ClockStyleWidgetPosition {
    record Binding(Method update, Method resolve, Field hasWidgets) {}

    private record Request(Object owner, boolean widgets) {}

    private static final ThreadLocal<Request> active = new ThreadLocal<>();
    private static final Set<Object> reported =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private ClockStyleWidgetPosition() {}

    static Binding bind(ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        Class<?> manager =
                Class.forName(
                        "com.samsung.android.app.facewidget.manager.KeyguardClockPositionManager",
                        false,
                        loader);
        String owner = "L" + manager.getName().replace('.', '/') + ";";
        Method dump = NativeContract.method(manager, "dump", void.class, PrintWriter.class);
        Field widgets = DexSignatures.body(dump).uniqueRead(owner, "Z", null).field(loader);
        List<Method> candidates = new ArrayList<>();
        for (DexSignatures.Body body :
                DexSignatures.find(loader, Set.of("ClockMovableAreaResolver[clock_position]"))) {
            if ((body.access() & 8) == 0
                    && body.result().equals("Landroid/graphics/Rect;")
                    && body.parameters()
                            .equals(
                                    List.of(
                                            "Ljava/lang/Integer;",
                                            "Landroid/graphics/Rect;",
                                            "Landroid/util/SizeF;",
                                            "Ljava/lang/String;")))
                candidates.add(body.method(loader));
        }
        if (candidates.size() != 1)
            throw new NoSuchMethodException(
                    "Clock Style movable area resolver: " + candidates.size());
        Method resolve = candidates.get(0);
        candidates.clear();
        for (DexSignatures.Body body : DexSignatures.callers(resolve)) {
            if (body.owner().equals(owner)
                    && body.result().equals("V")
                    && body.parameters().equals(List.of("Z"))
                    && (body.access() & 8) == 0) candidates.add(body.method(loader));
        }
        if (candidates.size() != 1)
            throw new NoSuchMethodException("Clock Style position update: " + candidates.size());
        return new Binding(candidates.get(0), resolve, widgets);
    }

    static void install(DressroomModule module, ClassLoader loader)
            throws ReflectiveOperationException, java.io.IOException {
        Binding binding = bind(loader);
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(
                    module.hook(binding.update)
                                    .setId("dressroom-advanced.clockstyle-widget-position-context")
                                    .intercept(
                                            chain -> {
                                                Request previous = active.get();
                                                Object owner = chain.getThisObject();
                                                try {
                                                    active.set(
                                                            new Request(
                                                                    owner,
                                                                    binding.hasWidgets.getBoolean(
                                                                            owner)));
                                                } catch (ReflectiveOperationException
                                                        | RuntimeException
                                                        | LinkageError error) {
                                                    // An optional correction must not block
                                                    // Samsung's update.
                                                    active.remove();
                                                }
                                                try {
                                                    return chain.proceed();
                                                } finally {
                                                    if (previous == null) active.remove();
                                                    else active.set(previous);
                                                }
                                            })
                            ::unhook);
            hooks.add(
                    module.hook(binding.resolve)
                                    .setId("dressroom-advanced.clockstyle-widget-position-area")
                                    .intercept(
                                            chain -> {
                                                Object result = chain.proceed();
                                                Request request = active.get();
                                                if (request == null
                                                        || !request.widgets
                                                        || !(result instanceof Rect nativeArea))
                                                    return result;
                                                // The caller supplies a boundary only for an
                                                // explicit wallpaper
                                                // position. Null means Samsung still owns default
                                                // placement.
                                                Rect saved = (Rect) chain.getArg(1);
                                                SizeF display = (SizeF) chain.getArg(2);
                                                if (!useSaved(
                                                        nativeArea,
                                                        saved,
                                                        display,
                                                        request.widgets)) return result;
                                                if (reported.add(request.owner))
                                                    android.util.Log.i(
                                                            DressroomModule.TAG,
                                                            "Clock Style widget position uses saved"
                                                                    + " editor bounds");
                                                return new Rect(saved);
                                            })
                            ::unhook);
            module.deoptimize(binding.update);
            module.deoptimize(binding.resolve);
            hooks.commit();
        }
    }

    static boolean useSaved(Rect nativeArea, Rect saved, SizeF display, boolean widgets) {
        return widgets
                && display != null
                && Float.isFinite(display.getWidth())
                && Float.isFinite(display.getHeight())
                && display.getWidth() > 0
                && display.getHeight() > 0
                && valid(nativeArea, display)
                && valid(saved, display)
                && !nativeArea.equals(saved);
    }

    private static boolean valid(Rect area, SizeF display) {
        return area != null
                && !area.isEmpty()
                && area.left >= 0
                && area.top >= 0
                && area.right <= display.getWidth()
                && area.bottom <= display.getHeight();
    }
}
