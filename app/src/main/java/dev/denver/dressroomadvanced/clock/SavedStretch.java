package dev.denver.dressroomadvanced;

import android.graphics.Rect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class SavedStretch {
    private record Rectangles(Method getter, Method setter) {}
    private static final ClassValue<Rectangles> rectangles = new ClassValue<>() {
        @Override protected Rectangles computeValue(Class<?> type) {
            try {
                return new Rectangles(
                        NativeContract.method(type, "getFontHeightRects", List.class),
                        NativeContract.method(type, "setFontHeightRects", void.class, List.class));
            } catch (NoSuchMethodException early) {
                return new Rectangles(null, null);
            }
        }
    };

    private SavedStretch() {}

    @SuppressWarnings("unchecked")
    static List<Rect> editorRects(Object data) throws ReflectiveOperationException {
        Method getter = rectangles.get(data.getClass()).getter();
        return getter == null ? null : (List<Rect>) getter.invoke(data);
    }

    static Method editorRectSetter(Class<?> type) {
        return rectangles.get(type).setter();
    }

    static void editorRects(Object data, List<Rect> values) throws ReflectiveOperationException {
        Method setter = editorRectSetter(data.getClass());
        if (setter != null) setter.invoke(data, values);
    }

    static List<Float> heights(float[] values) {
        if (values == null || values.length == 0) return null;
        List<Float> result = new ArrayList<>();
        for (float value : values) {
            if (!Float.isFinite(value) || value <= 0) return null;
            result.add(value);
        }
        return result;
    }

    static Object font(Object original, List<Float> heights) throws ReflectiveOperationException {
        if (heights == null || heights.equals(Reflect.call(original, "getFontHeights")))
            return original;
        return copy(original, heights);
    }

    static Object copy(Object original, List<Float> heights) throws ReflectiveOperationException {
        return Reflect.call(
                original,
                "copy",
                Reflect.call(original, "getFontType"),
                Reflect.call(original, "getFontPath"),
                Reflect.call(original, "getFontWeight"),
                Reflect.call(original, "getFontRoundedCorners"),
                new ArrayList<>(heights));
    }
}
