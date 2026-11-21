package dev.denver.dressroomadvanced;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class OneUi80ClockDraft {
    private static final String ENTITY =
            "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.";
    private static final Set<String> models = Set.of(
            ENTITY + "ClockTypeData", ENTITY + "ClockColorData", ENTITY + "ClockFontData",
            ENTITY + "ClockStretchData", ENTITY + "ClockViewMeasureState",
            ENTITY + "SizeConstraintData");

    private record Shape(Constructor<?> constructor, List<Field> fields) {}

    private static final ClassValue<Shape> shapes = new ClassValue<>() {
        @Override protected Shape computeValue(Class<?> type) {
            try {
                return new Shape(type.getConstructor(), Arrays.stream(type.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .peek(field -> field.setAccessible(true)).toList());
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("8.0 clock draft constructor unavailable", error);
            }
        }
    };

    private OneUi80ClockDraft() {}

    static Object copy(Object source) throws ReflectiveOperationException {
        Shape shape = shapes.get(source.getClass());
        Object target = shape.constructor().newInstance();
        for (Field field : shape.fields())
            field.set(target, copyField(field.get(source)));
        return target;
    }

    private static Object copyField(Object value) throws ReflectiveOperationException {
        if (value == null) return null;
        if (models.contains(value.getClass().getName())) return copy(value);
        if (value instanceof Rect rect) return new Rect(rect);
        if (value instanceof RectF rect) return new RectF(rect);
        if (value instanceof Point point) return new Point(point);
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return value;
    }
}
