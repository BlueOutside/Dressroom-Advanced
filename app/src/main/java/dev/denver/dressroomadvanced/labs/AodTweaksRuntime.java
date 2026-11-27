package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hooks for the AOD Labs extras. Every target is resolved from DEX structure and string anchors, and
 * every installer is optional: a failure is reported and leaves Samsung's behavior untouched.
 */
final class AodTweaksRuntime {
    private static final Set<ClassLoader> stretchInstalled =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<ClassLoader> eraseInstalled =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Boolean> reading = new ThreadLocal<>();
    private static volatile Boolean editorErase;
    private static boolean floatingInstalled;
    private static volatile int fullscreenState; // 0 unknown, 1 enabled, 2 disabled
    private static volatile int autoAodState; // 0 unknown, 1 enabled, 2 disabled

    private AodTweaksRuntime() {}

    static Context application() {
        try {
            return (Context)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Reads committed Extras without recursing into feature checks made by the reader itself. */
    private static Bundle extras() {
        Context app = application();
        if (app == null || reading.get() != null) return null;
        reading.set(Boolean.TRUE);
        try {
            return RuntimeExtras.get(app);
        } catch (RuntimeException unavailable) {
            return null;
        } finally {
            reading.remove();
        }
    }

    /** The editor reports its unsaved draft so the first Apply already unlocks the cutout. */
    static void editorDraft(Bundle draft) {
        editorErase = draft == null ? null : draft.getBoolean(AodTweaks.ERASE_UNLOCK);
    }

    // Floating features: Samsung reads AOD fullscreen and AOD item tokens once per process, so
    // changes apply after SystemUI, the AOD service, or the wallpaper process restarts.
    static synchronized void installFloatingFeature(DressroomModule module) {
        if (floatingInstalled) return;
        floatingInstalled = true;
        try {
            Class<?> feature = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            int hooked = 0;
            for (Method method : feature.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        || method.getParameterTypes()[0] != String.class) continue;
                if (method.getName().equals("getInt") && method.getReturnType() == int.class) {
                    module.hook(method)
                            .setId("dressroom-advanced.aod-fullscreen-capability")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (!AodTweaks.FULLSCREEN_FEATURE.equals(chain.getArg(0))
                                                || !(result instanceof Integer value)
                                                || value > 0) return result;
                                        int state = fullscreenState;
                                        if (state == 0) {
                                            Bundle extras = extras();
                                            if (extras == null) return result;
                                            state =
                                                    extras.getBoolean(AodTweaks.FULLSCREEN_UNLOCK)
                                                            ? 1
                                                            : 2;
                                            fullscreenState = state;
                                        }
                                        return state == 1 ? 1 : result;
                                    });
                    module.deoptimize(method);
                    hooked++;
                } else if (method.getName().equals("getString")
                        && method.getReturnType() == String.class) {
                    module.hook(method)
                            .setId("dressroom-advanced.aod-auto-capability")
                            .intercept(
                                    chain -> {
                                        Object result = chain.proceed();
                                        if (!AodTweaks.AOD_ITEM_FEATURE.equals(chain.getArg(0))
                                                || !(result instanceof String value)
                                                || !value.contains(
                                                        AodTweaks.AUTO_AOD_EXCLUDED_TOKEN))
                                            return result;
                                        int state = autoAodState;
                                        if (state == 0) {
                                            Bundle extras = extras();
                                            if (extras == null) return result;
                                            state =
                                                    extras.getBoolean(AodTweaks.AUTO_AOD_UNLOCK)
                                                            ? 1
                                                            : 2;
                                            autoAodState = state;
                                        }
                                        return state == 1
                                                ? AodTweaks.stripFeatureToken(
                                                        value, AodTweaks.AUTO_AOD_EXCLUDED_TOKEN)
                                                : result;
                                    });
                    module.deoptimize(method);
                    hooked++;
                }
            }
            if (hooked == 0) throw new NoSuchMethodException("SemFloatingFeature.getInt/getString");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            floatingInstalled = false;
            module.report("Optional AOD floating feature hook unavailable", error);
        }
    }

    // Dressroom saves the Erase background cutout only when its subject check accepts the tag.
    static void installEraseUnlock(DressroomModule module, ClassLoader loader) {
        if (loader == null || !eraseInstalled.add(loader)) return;
        try {
            List<DexSignatures.Body> matches =
                    DexSignatures.find(loader, Set.of("object counts ("));
            Method check = null;
            Field cutout = null;
            for (DexSignatures.Body body : matches) {
                if (!"Z".equals(body.result())
                        || !body.parameters().equals(List.of("Ljava/lang/Boolean;"))) continue;
                Field found = null;
                for (DexSignatures.Read read : body.reads()) {
                    if (!"Landroid/graphics/Bitmap;".equals(read.field().type())) continue;
                    if (found != null) throw new NoSuchMethodException("Ambiguous subject cutout");
                    String owner = read.field().owner();
                    Class<?> holder =
                            Class.forName(
                                    owner.substring(1, owner.length() - 1).replace('/', '.'),
                                    false,
                                    loader);
                    found = holder.getDeclaredField(read.field().name());
                    found.setAccessible(true);
                }
                if (found == null) continue;
                if (check != null) throw new NoSuchMethodException("Ambiguous subject check");
                check = body.method(loader);
                cutout = found;
            }
            if (check == null) throw new NoSuchMethodException("Subject check not found");
            Field cutoutField = cutout;
            module.hook(check)
                    .setId("dressroom-advanced.aod-erase-subject")
                    .intercept(
                            chain -> {
                                Object result = chain.proceed();
                                if (Boolean.TRUE.equals(result) || chain.getArg(0) != null)
                                    return result;
                                Boolean draft = editorErase;
                                if (draft == null) {
                                    Bundle extras = extras();
                                    draft = extras != null && extras.getBoolean(AodTweaks.ERASE_UNLOCK);
                                }
                                if (!draft) return result;
                                return cutoutField.get(chain.getThisObject()) instanceof Bitmap;
                            });
            module.deoptimize(check);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException
                | LinkageError error) {
            eraseInstalled.remove(loader);
            module.report("Optional AOD erase background unlock unavailable", error);
        }
    }

    // Samsung builds this config from Settings.System plus a hardcoded zero restitution.
    static void installStretchPhysics(DressroomModule module, ClassLoader loader) {
        if (loader == null || !stretchInstalled.add(loader)) return;
        try {
            List<DexSignatures.Body> matches =
                    DexSignatures.find(loader, Set.of("StretchAnimationConfig(accelerationUp="));
            Constructor<?> config = null;
            for (DexSignatures.Body body : matches) {
                if (!"toString".equals(body.name())) continue;
                Class<?> owner = body.method(loader).getDeclaringClass();
                Constructor<?> candidate =
                        owner.getDeclaredConstructor(
                                float.class, float.class, float.class, float.class, float.class,
                                float.class, float.class, float.class);
                if (config != null && config.getDeclaringClass() != owner)
                    throw new NoSuchMethodException("Ambiguous stretch config");
                config = candidate;
            }
            if (config == null) {
                stretchInstalled.remove(loader);
                return;
            }
            module.hook(config)
                    .setId("dressroom-advanced.stretch-physics")
                    .intercept(
                            chain -> {
                                Bundle extras = extras();
                                int speed = AodTweaks.speed(extras), bounce = AodTweaks.bounce(extras);
                                if (!AodTweaks.stretchActive(speed, bounce)) return chain.proceed();
                                float[] tuned = AodTweaks.stretch(speed, bounce);
                                Object[] args = chain.getArgs().toArray();
                                for (int i = 0; i < tuned.length; i++) args[i] = tuned[i];
                                return chain.proceed(args);
                            });
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException
                | LinkageError error) {
            stretchInstalled.remove(loader);
            module.report("Optional clock stretch physics unavailable", error);
        }
    }

}
