package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Repairs Samsung's copy-paste bug in {@code NonePaddingTextView.measureDigitAlignmentBounds},
 * where the {@code "0123456789"} outline was written to an unreferenced {@link Path} and the
 * {@code "0"} outline was measured twice, forcing {@code "0"} as the alignment sample even when
 * digits {@code 1..9} have different vertical bounds.
 */
final class DigitAlignmentFix {
    static final String ZERO_SAMPLE = "0";
    static final String ALL_DIGITS_SAMPLE = "0123456789";

    private static final Set<ClassLoader> installed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    record Contract(
            Method measure,
            Method setTypeface,
            Field clipDigitForAlign,
            Field alignmentSampleReady,
            Field alignmentSampleText,
            Field alignmentSampleLength) {}

    private DigitAlignmentFix() {}

    static String chooseAlignmentSample(float zeroHeight, float allDigitsHeight) {
        return Float.compare(allDigitsHeight, zeroHeight) == 0 ? ZERO_SAMPLE : ALL_DIGITS_SAMPLE;
    }

    static synchronized void install(DressroomModule module, ClassLoader loader) {
        if (loader == null || !installed.add(loader)) return;
        try {
            Class<?> textClass;
            try {
                textClass =
                        Class.forName(
                                "com.samsung.android.uniform.widget.NonePaddingTextView",
                                false,
                                loader);
            } catch (ClassNotFoundException absent) {
                return;
            }
            Method candidate = findMeasureMethod(textClass);
            if (candidate == null) return;
            DexSignatures.Body body = DexSignatures.cached(() -> DexSignatures.body(candidate));
            Contract contract = resolve(textClass, candidate, body, loader);
            if (contract == null) return;
            try (HookTransaction hooks = new HookTransaction()) {
                hooks.add(
                        module.hook(contract.measure())
                                        .setId("dressroom-advanced.digit-alignment-bounds")
                                        .intercept(
                                                chain -> {
                                                    Object view = chain.getThisObject();
                                                    Object paintArg = chain.getArg(0);
                                                    if (paintArg instanceof TextPaint paint
                                                            && Boolean.TRUE.equals(chain.getArg(1))
                                                            && !contract.clipDigitForAlign()
                                                                    .getBoolean(view)
                                                            && !contract.alignmentSampleReady()
                                                                    .getBoolean(view)) {
                                                        Path samplePath = new Path();
                                                        RectF zeroBounds = new RectF();
                                                        paint.getTextPath(
                                                                ZERO_SAMPLE,
                                                                0,
                                                                1,
                                                                0f,
                                                                0f,
                                                                samplePath);
                                                        samplePath.computeBounds(zeroBounds, true);
                                                        samplePath.rewind();
                                                        RectF allDigitsBounds = new RectF();
                                                        paint.getTextPath(
                                                                ALL_DIGITS_SAMPLE,
                                                                0,
                                                                10,
                                                                0f,
                                                                0f,
                                                                samplePath);
                                                        samplePath.computeBounds(
                                                                allDigitsBounds, true);
                                                        String chosen =
                                                                chooseAlignmentSample(
                                                                        zeroBounds.height(),
                                                                        allDigitsBounds.height());
                                                        contract.alignmentSampleText()
                                                                .set(view, chosen);
                                                        contract.alignmentSampleLength()
                                                                .setInt(view, chosen.length());
                                                        contract.alignmentSampleReady()
                                                                .setBoolean(view, true);
                                                    }
                                                    return chain.proceed();
                                                })
                                ::unhook);
                module.deoptimize(contract.measure());
                if (contract.setTypeface() != null) {
                    hooks.add(
                            module.hook(contract.setTypeface())
                                            .setId("dressroom-advanced.digit-alignment-typeface")
                                            .intercept(
                                                    chain -> {
                                                        contract.alignmentSampleReady()
                                                                .setBoolean(
                                                                        chain.getThisObject(),
                                                                        false);
                                                        return chain.proceed();
                                                    })
                                    ::unhook);
                    module.deoptimize(contract.setTypeface());
                }
                hooks.commit();
            }
        } catch (Exception | LinkageError error) {
            installed.remove(loader);
            module.report("Optional NonePaddingTextView digit alignment fix unavailable", error);
        }
    }

    static Method findMeasureMethod(Class<?> textClass) throws NoSuchMethodException {
        List<Method> matches = new ArrayList<>();
        for (Method method : textClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge()) continue;
            if (method.getReturnType() == Rect.class
                    && List.of(method.getParameterTypes())
                            .equals(List.of(TextPaint.class, boolean.class))) {
                method.setAccessible(true);
                matches.add(method);
            }
        }
        if (matches.isEmpty()) return null;
        if (matches.size() > 1)
            throw new NoSuchMethodException(
                    "Ambiguous digit alignment methods on " + textClass.getName());
        return matches.get(0);
    }

    static Contract resolve(
            Class<?> textClass, Method measure, DexSignatures.Body body, ClassLoader loader)
            throws ReflectiveOperationException {
        boolean hasTextPath =
                body.calls().stream().anyMatch(call -> "getTextPath".equals(call.name()));
        boolean hasRectHeight =
                body.calls().stream()
                        .anyMatch(
                                call ->
                                        "Landroid/graphics/RectF;".equals(call.owner())
                                                && "height".equals(call.name()));
        if (!hasTextPath || !hasRectHeight) return null;
        String ownerDesc = DexSignatures.descriptor(textClass);
        Set<DexSignatures.Member> boolWrites = new LinkedHashSet<>();
        Set<DexSignatures.Member> stringWrites = new LinkedHashSet<>();
        Set<DexSignatures.Member> intWrites = new LinkedHashSet<>();
        for (var write : body.writes()) {
            var member = write.field();
            if (!ownerDesc.equals(member.owner())) continue;
            switch (member.type()) {
                case "Z" -> boolWrites.add(member);
                case "Ljava/lang/String;" -> stringWrites.add(member);
                case "I" -> intWrites.add(member);
                default -> {}
            }
        }
        if (boolWrites.size() != 1 || stringWrites.size() != 1 || intWrites.size() != 1)
            throw new NoSuchFieldException("Unexpected digit alignment write contract");
        DexSignatures.Member readyMember = boolWrites.iterator().next();
        Set<DexSignatures.Member> clipReads = new LinkedHashSet<>();
        for (var read : body.reads()) {
            var member = read.field();
            if (ownerDesc.equals(member.owner())
                    && "Z".equals(member.type())
                    && !member.equals(readyMember)) {
                clipReads.add(member);
            }
        }
        if (clipReads.size() != 1)
            throw new NoSuchFieldException("Unexpected digit alignment clip flag contract");
        Field clipField = clipReads.iterator().next().field(loader);
        Field readyField = readyMember.field(loader);
        Field sampleTextField = stringWrites.iterator().next().field(loader);
        Field sampleLenField = intWrites.iterator().next().field(loader);
        clipField.setAccessible(true);
        readyField.setAccessible(true);
        sampleTextField.setAccessible(true);
        sampleLenField.setAccessible(true);
        Method setTypeface = null;
        try {
            setTypeface = textClass.getDeclaredMethod("setTypeface", Typeface.class);
            setTypeface.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }
        return new Contract(
                measure, setTypeface, clipField, readyField, sampleTextField, sampleLenField);
    }
}
