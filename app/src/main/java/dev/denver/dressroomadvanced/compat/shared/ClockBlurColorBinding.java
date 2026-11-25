package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only correct writers which read TextView ink instead of the cached path ink. */
final class ClockBlurColorBinding {
    record Writer(Method method, List<Field> owners, Field animator) {}

    final Class<?> glyph;
    final Field mode, color, textViews;
    final List<Writer> writers;

    ClockBlurColorBinding(Class<?> glyph, Field mode, Field color, Field textViews,
            List<Writer> writers) {
        this.glyph = glyph;
        this.mode = mode;
        this.color = color;
        this.textViews = textViews;
        this.writers = List.copyOf(writers);
    }

    static ClockBlurColorBinding resolve(ClockBlurRetryBinding retry)
            throws ReflectiveOperationException, IOException {
        Class<?> strategy = retry.strategy.getType();
        ClassLoader loader = strategy.getClassLoader();
        Class<?> glyph = Class.forName(
                "com.samsung.android.uniform.widget.NonePaddingTextView", false, loader);
        Method setter = glyph.getMethod("setTextColor", int.class);
        // Earlier renderers inherit TextView's setter and have no split color state.
        if (setter.getDeclaringClass() != glyph) return null;
        String glyphType = DexSignatures.descriptor(glyph);
        var body = DexSignatures.body(setter);
        var mode = body.uniqueRead(glyphType, "Z", null);
        var color = body.uniqueWrite(glyphType, "I", null);
        Method transition = NativeContract.findMethod(
                strategy, null, void.class, int.class, ImageView.class);
        var transitionBody = DexSignatures.body(transition);
        if (transitionBody.calls().stream().noneMatch(call ->
                call.owner().equals("Landroid/animation/ValueAnimator;")
                        && call.name().equals("ofFloat")))
            throw new NoSuchMethodException("Clock blur transition is not a float animator");

        Map<Method, Writer> writers = new LinkedHashMap<>();
        for (Method method : strategy.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || !List.of(method.getParameterTypes()).equals(List.of(strategy, int.class)))
                continue;
            if (needsCorrection(DexSignatures.body(method), mode, color)) {
                method.setAccessible(true);
                writers.put(method, new Writer(method, List.of(), null));
            }
        }
        int finalizers = writers.size();
        for (var call : transitionBody.calls()) {
            if (!call.name().equals("<init>") || call.owner().startsWith("Landroid/")) continue;
            Class<?> listener = Class.forName(call.owner().substring(1, call.owner().length() - 1)
                    .replace('/', '.'), false, loader);
            if (!ValueAnimator.AnimatorUpdateListener.class.isAssignableFrom(listener)) continue;
            Method frame = listener.getDeclaredMethod("onAnimationUpdate", ValueAnimator.class);
            var frameBody = DexSignatures.body(frame);
            if (!needsCorrection(frameBody, mode, color)) continue;
            List<Field> owners = frameOwners(frameBody, listener, strategy, retry.textViews);
            frame.setAccessible(true);
            writers.put(frame, new Writer(frame, owners,
                    NativeContract.findField(strategy, null, ValueAnimator.class)));
        }
        // 9.5 already reads both cached fields in its frame and finalizer.
        if (writers.isEmpty()) return null;
        if (finalizers != 1 || writers.size() != 2)
            throw new NoSuchMethodException("Ambiguous stale clock color writers: "
                    + finalizers + "/" + writers.size());
        return new ClockBlurColorBinding(glyph, mode.field(loader), color.field(loader),
                retry.textViews, new ArrayList<>(writers.values()));
    }

    private static List<Field> frameOwners(DexSignatures.Body body, Class<?> listener, Class<?> strategy,
            Field textViews) throws ReflectiveOperationException {
        String strategyType = DexSignatures.descriptor(strategy);
        var list = new DexSignatures.Member(strategyType, textViews.getName(),
                DexSignatures.descriptor(textViews.getType()));
        if (body.reads().stream().noneMatch(read -> read.field().equals(list)))
            throw new NoSuchFieldException("Clock color frame does not read its strategy's glyph list");
        String listenerType = DexSignatures.descriptor(listener);
        var typed = body.reads().stream().map(DexSignatures.Read::field)
                .filter(field -> field.owner().equals(listenerType)
                        && field.type().equals(strategyType)).distinct().toList();
        if (typed.size() == 1) return List.of(typed.get(0).field(listener.getClassLoader()));
        if (!typed.isEmpty()) throw new NoSuchFieldException("Ambiguous clock color frame owner");
        // 8.5 R8 erases both the owner and weather captures to Object. Do not
        // guess from field order: the hook requires the strategy type AND its
        // exact current animator, and rejects multiple matching owners.
        var captures = body.reads().stream().map(DexSignatures.Read::field)
                .filter(field -> field.owner().equals(listenerType)
                        && field.type().equals("Ljava/lang/Object;")).distinct().toList();
        if (captures.isEmpty() || captures.size() > 2)
            throw new NoSuchFieldException("Ambiguous erased clock color captures");
        List<Field> fields = new ArrayList<>();
        for (var capture : captures) fields.add(capture.field(listener.getClassLoader()));
        return List.copyOf(fields);
    }

    private static boolean needsCorrection(DexSignatures.Body body, DexSignatures.Member mode,
            DexSignatures.Member color) throws NoSuchFieldException {
        boolean getter = false, setter = false;
        for (var call : body.calls()) {
            if (!call.owner().equals("Landroid/widget/TextView;")) continue;
            getter |= call.name().equals("getCurrentTextColor") && call.parameters().isEmpty()
                    && call.result().equals("I");
            setter |= call.name().equals("setTextColor")
                    && call.parameters().equals(List.of("I")) && call.result().equals("V");
        }
        if (!getter || !setter) return false;
        boolean readsMode = body.reads().stream().anyMatch(read -> read.field().equals(mode));
        boolean readsColor = body.reads().stream().anyMatch(read -> read.field().equals(color));
        if (readsMode != readsColor)
            throw new NoSuchFieldException("Partial native clock path-color policy");
        return !readsColor;
    }

    Integer read(TextView view, List<?> views) throws IllegalAccessException {
        if (!glyph.isInstance(view) || !mode.getBoolean(view)) return null;
        for (Object item : views) if (item == view) return color.getInt(view);
        return null;
    }
}
