package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

final class OneUi85ArtworkBinding {
    private static final String SHOW_HIDE_ANCHOR = "showHideMusicPageWithAnimation isShow = ";
    private static final String ARTWORK_ANCHOR = "FaceWidgetMusicPageViewHolder";

    final Constructor<?> constructor;
    final Field sourceArtwork;
    final boolean early;

    OneUi85ArtworkBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected legacy artwork binding");
        early = clock.early;
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
        Class<?> holder = resolveHolder(loader, page, model, early);
        constructor = holderConstructor(holder, page, model, early);
        if (constructor == null) throw new NoSuchMethodException("Native music holder changed");
        sourceArtwork = sourceArtwork(loader, holder);
    }

    private static Class<?> resolveHolder(ClassLoader loader, Class<?> page, Class<?> model, boolean early)
            throws ReflectiveOperationException {
        java.util.function.Predicate<Class<?>> match =
                type ->
                        NativeContract.hasField(type, page)
                                && NativeContract.hasField(type, model)
                                && holderConstructor(type, page, model, early) != null;
        try {
            return DexSignatures.cached(() -> scanHolder(loader, page, model, match, early));
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (ClassNotFoundException)
                    new ClassNotFoundException("Native legacy music holder not found").initCause(error);
        }
    }

    private static Field sourceArtwork(ClassLoader loader, Class<?> holder) {
        String owner = DexSignatures.descriptor(holder);
        String bitmap = DexSignatures.descriptor(Bitmap.class);
        String drawable = "Landroid/graphics/drawable/Drawable;";
        try {
            Set<DexSignatures.Member> fields = new LinkedHashSet<>();
            for (var body : DexSignatures.find(loader, Set.of(ARTWORK_ANCHOR))) {
                if (!body.name().equals("run") || !body.result().equals("V")
                        || !body.parameters().isEmpty()) continue;
                boolean loadsArtwork = body.calls().stream().anyMatch(call ->
                        call.owner().equals("Landroid/graphics/drawable/Icon;")
                                && call.name().equals("loadDrawable") && call.result().equals(drawable));
                boolean scalesArtwork = body.calls().stream().anyMatch(call ->
                        call.owner().equals(bitmap) && call.name().equals("createScaledBitmap"));
                if (!loadsArtwork || !scalesArtwork) continue;
                for (var write : body.writes())
                    if (write.field().owner().equals(owner) && write.field().type().equals(bitmap))
                        fields.add(write.field());
            }
            if (fields.size() == 1) return fields.iterator().next().field(loader);
            throw new NoSuchFieldException("Expected one source artwork write, found " + fields.size());
        } catch (IOException | ReflectiveOperationException | RuntimeException error) {
            DressroomModule.note("Legacy source artwork unavailable", error);
            return null;
        }
    }

    private static Class<?> scanHolder(
            ClassLoader loader,
            Class<?> page,
            Class<?> model,
            java.util.function.Predicate<Class<?>> match,
            boolean early)
            throws IOException, ReflectiveOperationException {
        Set<Class<?>> found = new LinkedHashSet<>();
        var prefix = java.util.List.of(DexSignatures.descriptor(Context.class),
                DexSignatures.descriptor(View.class), DexSignatures.descriptor(page),
                DexSignatures.descriptor(model));
        if (!early) {
            for (Method method : page.getDeclaredMethods()) {
                for (var call : DexSignatures.body(method).calls()) {
                    if (!"<init>".equals(call.name()) || call.parameters().size() != 7
                            || !call.parameters().subList(0, 4).equals(prefix)) continue;
                    Class<?> candidate = loadDescriptor(loader, call.owner());
                    if (match.test(candidate)) found.add(candidate);
                }
            }
            if (found.size() == 1) return found.iterator().next();
            found.clear();
        }
        for (var body : DexSignatures.find(loader, Set.of(SHOW_HIDE_ANCHOR))) {
            Class<?> candidate = loadDescriptor(loader, body.owner());
            if (match.test(candidate)) found.add(candidate);
        }
        if (found.size() == 1) return found.iterator().next();
        throw new ClassNotFoundException(
                "Unique legacy music holder for " + page.getName() + " and " + model.getName());
    }

    private static Class<?> loadDescriptor(ClassLoader loader, String descriptor)
            throws ClassNotFoundException {
        return Class.forName(
                descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, loader);
    }

    private static Constructor<?> holderConstructor(
            Class<?> type, Class<?> page, Class<?> model, boolean early) {
        Constructor<?> found = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            Class<?>[] args = candidate.getParameterTypes();
            if (args.length == (early ? 10 : 7)
                    && args[0] == Context.class
                    && args[1] == View.class
                    && args[2] == page
                    && args[early ? 4 : 3] == model) {
                if (found != null) return null;
                found = candidate;
            }
        }
        return found;
    }
}
