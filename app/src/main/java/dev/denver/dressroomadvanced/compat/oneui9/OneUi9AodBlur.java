package dev.denver.dressroomadvanced;

import android.view.View;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class OneUi9AodBlur {
    private OneUi9AodBlur() {}

    static Class<?> blurUtils(ClassLoader loader) throws ReflectiveOperationException {
        List<DexSignatures.Body> matches;
        try {
            matches = DexSignatures.find(loader, Set.of("setBlurByCustomAttributes: "));
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Could not scan AOD blur helpers", invalidDex);
        }
        List<Method> resolved = new ArrayList<>();
        List<String> signature =
                List.of("Landroid/view/View;", "F", "[F", "Ljava/lang/Integer;");
        for (DexSignatures.Body body : matches) {
            if ((body.access() & 8) != 0
                    && body.result().equals("V")
                    && body.parameters().equals(signature)) {
                resolved.add(body.method(loader));
            }
        }
        if (resolved.size() != 1)
            throw new ClassNotFoundException(
                    "Ambiguous AOD blur utility class: " + resolved.size());
        return resolved.get(0).getDeclaringClass();
    }

    static List<Method> commonSubmissions(Class<?> utils) throws NoSuchMethodException {
        return List.of(
                NativeContract.findStaticMethod(
                        utils,
                        null,
                        void.class,
                        View.class,
                        float.class,
                        float[].class,
                        Integer.class),
                NativeContract.findStaticMethod(
                        utils,
                        null,
                        void.class,
                        View.class,
                        int.class,
                        float.class,
                        Integer.class,
                        String.class));
    }

    static List<Method> submissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> utils = blurUtils(loader);
        List<Method> common = commonSubmissions(utils);
        return List.of(
                NativeContract.findStaticMethod(utils, null, void.class, View.class),
                common.get(0),
                common.get(1));
    }

    static Method widgetSizing(ClassLoader loader, Class<?> spec)
            throws ReflectiveOperationException {
        List<DexSignatures.Body> matches;
        try {
            matches = DexSignatures.find(
                    loader, Set.of("getSizeSpec", OneUi95Aod.WIDGET_RESIZE_ANCHOR));
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Could not scan widget size helper", invalidDex);
        }
        String specDescriptor = DexSignatures.descriptor(spec);
        List<String> expectedParams = List.of("Landroid/content/Context;", "I", "I", "I", "I", "Z");
        List<Method> methods = new ArrayList<>();
        for (DexSignatures.Body body : matches) {
            if ((body.access() & 8) != 0
                    && body.result().equals(specDescriptor)
                    && body.parameters().equals(expectedParams)) {
                methods.add(body.method(loader));
            }
        }
        if (methods.size() != 1)
            throw new NoSuchMethodException(
                    "Ambiguous widget size helper: " + methods.size());
        return methods.get(0);
    }
}
