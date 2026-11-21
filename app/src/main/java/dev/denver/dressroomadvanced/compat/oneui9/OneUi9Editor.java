package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class OneUi9Editor {
    static final String BLUR_HELPER = "ah.g";

    record BlurMethods(Method enable, Method text) {}

    private OneUi9Editor() {}

    static EditorBinding.Names names() {
        return new EditorBinding.Names(
                false, "vo.x", "mf.o0", "oc.a", "Y", "b", "w", "a", "d", "e", "e", "mf.i0", null,
                null, null);
    }

    static Class<?> blurHelper(ClassLoader loader, Class<?> clockView)
            throws ClassNotFoundException {
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasMethod(cls, void.class, clockView, boolean.class)
                                && NativeContract.hasMethod(cls, void.class, clockView),
                BLUR_HELPER);
    }

    static BlurMethods blurMethods(ClassLoader loader, Class<?> clockView, Class<?> extension)
            throws ReflectiveOperationException {
        try {
            String clockDesc = DexSignatures.descriptor(clockView);
            List<Method> enables = new ArrayList<>();
            for (DexSignatures.Body body :
                    DexSignatures.callers(
                            loader, extension, "setBlurEffectEnabled", void.class, boolean.class)) {
                if ((body.access() & 8) == 0
                        && body.result().equals("V")
                        && body.parameters().equals(List.of(clockDesc, "Z"))) {
                    enables.add(body.method(loader));
                }
            }
            if (enables.size() == 1) {
                Method enable = enables.get(0);
                String ownerDesc = DexSignatures.descriptor(enable.getDeclaringClass());
                List<Method> texts = new ArrayList<>();
                for (DexSignatures.Body body :
                        DexSignatures.callers(loader, extension, "setClockTextBlur", void.class)) {
                    if ((body.access() & 8) == 0
                            && body.owner().equals(ownerDesc)
                            && body.result().equals("V")
                            && body.parameters().equals(List.of(clockDesc))) {
                        texts.add(body.method(loader));
                    }
                }
                if (texts.size() == 1) return new BlurMethods(enable, texts.get(0));
            }
        } catch (java.io.IOException | ReflectiveOperationException | LinkageError fallback) {
        }
        Class<?> helper = blurHelper(loader, clockView);
        return new BlurMethods(
                NativeContract.findMethod(helper, "m", void.class, clockView, boolean.class),
                NativeContract.findMethod(helper, "o", void.class, clockView));
    }
}
