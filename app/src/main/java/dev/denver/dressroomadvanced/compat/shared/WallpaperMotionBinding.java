package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/** Follow the layered engine's draw pass, without depending on its obfuscated names. */
final class WallpaperMotionBinding {
    static final String ENGINE = "drawRenderNodes: durDraw=";
    static final String BITMAP = "draw: drawable is null";
    final Method engine, bitmap, which, sourceWhich, context, command;
    final java.lang.reflect.Field drawable;

    WallpaperMotionBinding(ClassLoader loader) throws Exception {
        List<DexSignatures.Body> bodies = DexSignatures.find(loader, Set.of(ENGINE, BITMAP));
        engine = unique(bodies, ENGINE, "Landroid/view/SurfaceHolder;").method(loader);
        bitmap = unique(bodies, BITMAP, "Landroid/graphics/Canvas;").method(loader);
        Class<?> owner = engine.getDeclaringClass();
        which = owner.getMethod("getWhich");
        sourceWhich = owner.getMethod("getSourceWhich");
        context = owner.getMethod("getAppContext");
        Class<?> endpoint;
        try {
            endpoint =
                    Class.forName(
                            "com.samsung.android.wallpaper.live.sdk.service.switchable.SwitchableWallpaperService$DelegatorEngine",
                            false,
                            loader);
        } catch (ClassNotFoundException ignored) {
            endpoint = android.service.wallpaper.WallpaperService.Engine.class;
        }
        if (!android.service.wallpaper.WallpaperService.Engine.class.isAssignableFrom(endpoint))
            throw new NoSuchMethodException("Wallpaper command endpoint is not an engine");
        command =
                endpoint.getMethod(
                        "onCommand",
                        String.class,
                        int.class,
                        int.class,
                        int.class,
                        android.os.Bundle.class,
                        boolean.class);

        if (which.getReturnType() != int.class
                || sourceWhich.getReturnType() != int.class
                || !android.content.Context.class.isAssignableFrom(context.getReturnType()))
            throw new NoSuchMethodException("Layered engine identity contract changed");
        if (engine.getParameterTypes()[0] != SurfaceHolder.class
                || bitmap.getParameterTypes()[0] != Canvas.class)
            throw new NoSuchMethodException("Layered renderer canvas contract changed");
        DexSignatures.Body draw = DexSignatures.body(bitmap);
        drawable =
                draw.uniqueRead(draw.owner(), "Landroid/graphics/drawable/Drawable;", null)
                        .field(loader);
        boolean scales =
                draw.calls().stream()
                        .anyMatch(
                                call ->
                                        call.owner().equals("Landroid/graphics/Canvas;")
                                                && call.name().equals("scale")
                                                && call.parameters()
                                                        .equals(List.of("F", "F", "F", "F")));
        boolean draws =
                draw.calls().stream()
                        .anyMatch(
                                call ->
                                        call.owner().equals("Landroid/graphics/drawable/Drawable;")
                                                && call.name().equals("draw")
                                                && call.parameters()
                                                        .equals(
                                                                List.of(
                                                                        "Landroid/graphics/Canvas;")));
        if (!scales || !draws) throw new NoSuchMethodException("Layered bitmap transform missing");
    }

    static DexSignatures.Body unique(
            List<DexSignatures.Body> bodies, String anchor, String parameter)
            throws NoSuchMethodException {
        List<DexSignatures.Body> matches =
                bodies.stream()
                        .filter(
                                body ->
                                        body.anchors().containsKey(anchor)
                                                && (body.access() & 8) == 0
                                                && body.result().equals("V")
                                                && body.parameters().equals(List.of(parameter)))
                        .toList();
        if (matches.size() != 1)
            throw new NoSuchMethodException(
                    "Expected one layered draw contract, found " + matches.size());
        return matches.get(0);
    }
}
