package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

final class OneUi85ArtworkBinding {
    private static final String[] HOLDER_CANDIDATES = {
        "aod.TK", "aod.YR", "aod.fS", "aod.TR"
    };
    private static final String SHOW_HIDE_ANCHOR = "showHideMusicPageWithAnimation isShow = ";

    final Constructor<?> constructor;

    OneUi85ArtworkBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 artwork binding");
        ClassLoader loader = base.getClassLoader();
        Class<?> page =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage",
                        false,
                        loader);
        Class<?> model =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicViewModel",
                        false,
                        loader);
        Class<?> holder = resolveHolder(loader, page, model);
        constructor = holderConstructor(holder, page, model);
        if (constructor == null) throw new NoSuchMethodException("Native music holder changed");
    }

    private static Class<?> resolveHolder(ClassLoader loader, Class<?> page, Class<?> model)
            throws ReflectiveOperationException {
        java.util.function.Predicate<Class<?>> match =
                type ->
                        NativeContract.hasField(type, page)
                                && NativeContract.hasField(type, model)
                                && holderConstructor(type, page, model) != null;
        try {
            return NativeContract.findClass(loader, match, HOLDER_CANDIDATES);
        } catch (ClassNotFoundException ignored) {
            // Fall through to DEX call-graph / anchor resolution if R8 renamed the holder class.
        }
        try {
            return DexSignatures.cached(() -> scanHolder(loader, page, model, match));
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (ClassNotFoundException)
                    new ClassNotFoundException("Native 8.5 music holder not found").initCause(error);
        }
    }

    private static Class<?> scanHolder(
            ClassLoader loader,
            Class<?> page,
            Class<?> model,
            java.util.function.Predicate<Class<?>> match)
            throws IOException, ReflectiveOperationException {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Method method : page.getDeclaredMethods()) {
            for (var call : DexSignatures.body(method).calls()) {
                if (!"<init>".equals(call.name()) || call.parameters().size() != 7) continue;
                Class<?> candidate = loadDescriptor(loader, call.owner());
                if (match.test(candidate)) found.add(candidate);
            }
        }
        if (found.size() == 1) return found.iterator().next();
        found.clear();
        for (var body : DexSignatures.find(loader, Set.of(SHOW_HIDE_ANCHOR))) {
            Class<?> candidate = loadDescriptor(loader, body.owner());
            if (match.test(candidate)) found.add(candidate);
        }
        if (found.size() == 1) return found.iterator().next();
        throw new ClassNotFoundException(
                "Unique 8.5 music holder for " + page.getName() + " and " + model.getName());
    }

    private static Class<?> loadDescriptor(ClassLoader loader, String descriptor)
            throws ClassNotFoundException {
        return Class.forName(
                descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, loader);
    }

    private static Constructor<?> holderConstructor(
            Class<?> type, Class<?> page, Class<?> model) {
        Constructor<?> found = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            Class<?>[] args = candidate.getParameterTypes();
            if (args.length == 7
                    && args[0] == Context.class
                    && args[1] == View.class
                    && args[2] == page
                    && args[3] == model) {
                if (found != null) return null;
                found = candidate;
            }
        }
        return found;
    }
}
