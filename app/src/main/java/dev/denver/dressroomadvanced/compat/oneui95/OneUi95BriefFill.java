package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class OneUi95BriefFill {
    private static final String PILL_KEY = "key_secure_suggestion_container";
    final Method update;
    private final Class<?> linear;
    private final Field type, doze, supportBlur;
    private final Method key, style, children, blur, blurPreset, blurAlpha, copyLinear, copyStyle;

    OneUi95BriefFill(ClassLoader loader, Class<?> node, Class<?> baseStyle)
            throws ReflectiveOperationException {
        String nodeType = DexSignatures.descriptor(node);
        String styleType = DexSignatures.descriptor(baseStyle);
        try {
            DexSignatures.Body body = OneUi95Editor.unique(
                    DexSignatures.find(loader, Set.of(PILL_KEY)).stream()
                            .filter(method -> method.result().equals(nodeType)
                                    && Modifier.isStatic(method.access())
                                    && method.parameters().size() == 3
                                    && method.parameters().get(0).equals(nodeType)
                                    && method.parameters().get(2).equals("Ljava/lang/Boolean;"))
                            .toList(), "Now Brief node style update");
            update = body.method(loader);
            Class<?> modifier = update.getParameterTypes()[1];
            type = NativeContract.assignableField(modifier, "type", Enum.class);
            if (!type.getType().getField("SECURE_SUGGESTION_COLLAPSE").isEnumConstant())
                throw new NoSuchFieldException("GenUI collapsed suggestion type");
            doze = NativeContract.field(modifier, "doze", boolean.class);
            supportBlur = NativeContract.field(modifier, "supportBlur", boolean.class);
            int pill = body.anchors().get(PILL_KEY);
            DexSignatures.Call pillKey = body.calls().stream()
                    .filter(call -> call.offset() < pill && call.name().equals("getKey")
                            && call.owner().startsWith(nodeType.substring(0, nodeType.length() - 1) + "$"))
                    .reduce((first, last) -> last)
                    .orElseThrow(() -> new NoSuchMethodException("Now Brief pill node key"));
            key = pillKey.method(loader);
            linear = key.getDeclaringClass();
            style = NativeContract.method(linear, "getStyle", baseStyle);
            children = NativeContract.findMethod(linear, null, List.class);
            blur = baseStyle.getMethod("getBlurStyle");
            blurPreset = NativeContract.method(blur.getReturnType(), "getPreset", Integer.class);
            blurAlpha = NativeContract.method(
                    blur.getReturnType(), "getBackgroundAlpha", Float.class);
            copyLinear = OneUi95Editor.unique(body.calls().stream()
                    .filter(call -> call.offset() > pill && call.isStatic()
                            && call.owner().equals(pillKey.owner())
                            && call.result().equals(pillKey.owner())
                            && call.parameters().equals(List.of(pillKey.owner(),
                                    "Ljava/util/List;", styleType, "I")))
                    .toList(), "Now Brief pill copy").method(loader);
            copyStyle = resolveStyleCopy(baseStyle, blur.getReturnType());
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Now Brief node style DEX unavailable", invalidDex);
        }
    }

    private static Method resolveStyleCopy(Class<?> baseStyle, Class<?> blur)
            throws ReflectiveOperationException {
        // Beta BaseStyle has 33 Kotlin constructor properties and two default masks.
        Method copy = OneUi95Editor.unique(Arrays.stream(baseStyle.getDeclaredMethods())
                .filter(method -> {
                    Class<?>[] p = method.getParameterTypes();
                    return method.getName().equals("copy$default") && method.getReturnType() == baseStyle
                            && Modifier.isStatic(method.getModifiers()) && p.length == 37
                            && p[0] == baseStyle && p[9] == List.class && p[10] == List.class
                            && p[30] == blur && p[34] == int.class && p[35] == int.class
                            && p[36] == Object.class;
                }).toList(), "Now Brief base style copy");
        copy.setAccessible(true);
        return copy;
    }

    float darkAmount(Object modifier) throws IllegalAccessException {
        return doze.getBoolean(modifier) ? 1f : 0f;
    }

    boolean isPill(Object node, Object modifier) throws ReflectiveOperationException {
        return modifier != null && supportBlur.getBoolean(modifier)
                && linear.isInstance(node) && PILL_KEY.equals(key.invoke(node))
                && type.get(modifier) instanceof Enum<?> mode
                && mode.name().equals("SECURE_SUGGESTION_COLLAPSE");
    }

    Object clear(Object node) throws ReflectiveOperationException {
        Object fill = style.invoke(node);
        Object blurStyle = blur.invoke(fill);
        if (blurStyle == null || !hasVisibleBlur(
                (Integer) blurPreset.invoke(blurStyle), (Float) blurAlpha.invoke(blurStyle)))
            return node;
        Object[] args = new Object[37];
        args[0] = fill;
        Class<?>[] parameters = copyStyle.getParameterTypes();
        for (int i = 1; i <= 33; i++) {
            if (parameters[i] == int.class) args[i] = 0;
            else if (parameters[i] == float.class) args[i] = 0f;
            else if (parameters[i] == boolean.class) args[i] = false;
        }
        args[34] = ~((1 << 8) | (1 << 9));
        args[35] = -1;
        return copyLinear.invoke(null, node, children.invoke(node), copyStyle.invoke(null, args), 0);
    }

    static boolean hasVisibleBlur(Integer preset, Float alpha) {
        return preset != null && preset > 0 && (alpha == null || Float.isFinite(alpha) && alpha > 0f);
    }
}
