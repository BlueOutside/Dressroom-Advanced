package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

final class OneUi85WallpaperFadeBinding {
    private static final String BLUR_ANCHOR = "animateBlur isExpand=";
    private static final String CLIP_ANCHOR = "animateClipContainer ";

    final Method changed;
    private final Field animator, expanded, host;

    OneUi85WallpaperFadeBinding(Class<?> base) throws ReflectiveOperationException {
        ClockBinding clock = ClockBinding.of(base);
        if (!clock.legacy) throw new NoSuchMethodException("Expected 8.5 wallpaper transition");
        ClassLoader loader = base.getClassLoader();
        Class<?> observer =
                Class.forName(
                        "com.samsung.android.app.facewidget.presentation.MediaExpandStateObserver",
                        false,
                        loader);
        Class<?> status =
                Class.forName(
                        "com.samsung.android.app.facewidget.SecKeyguardStatusView", false, loader);
        changed = NativeContract.findMethod(observer, "onChanged", void.class, Object.class);
        host = uniqueExactField(observer, status);
        try {
            DexSignatures.Body body =
                    DexSignatures.cached(
                            () -> DexSignatures.body(changed, Set.of(BLUR_ANCHOR, CLIP_ANCHOR)));
            String observerDesc = "L" + observer.getName().replace('.', '/') + ";";
            expanded = body.uniqueRead(observerDesc, "Z", BLUR_ANCHOR).field(loader);
            expanded.setAccessible(true);
            if (expanded.getType() != boolean.class) {
                throw new NoSuchFieldException("Expanded flag type mismatch: " + expanded);
            }
            animator =
                    body.uniqueWrite(
                                    observerDesc, "Landroid/animation/ValueAnimator;", CLIP_ANCHOR)
                            .field(loader);
            animator.setAccessible(true);
            if (animator.getType() != ValueAnimator.class) {
                throw new NoSuchFieldException("Animator field type mismatch: " + animator);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw (NoSuchFieldException)
                    new NoSuchFieldException("Unable to inspect MediaExpandStateObserver")
                            .initCause(error);
        }
    }

    private static Field uniqueExactField(Class<?> owner, Class<?> type)
            throws NoSuchFieldException {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() != type) continue;
            if (found != null) {
                throw new NoSuchFieldException(
                        "Multiple " + type.getName() + " fields on " + owner.getName());
            }
            found = field;
        }
        if (found == null) {
            throw new NoSuchFieldException(
                    "Missing " + type.getName() + " field on " + owner.getName());
        }
        found.setAccessible(true);
        return found;
    }

    void update(Object observer) throws IllegalAccessException {
        OneUi85FullscreenArt.wallpaperFade(
                (View) host.get(observer),
                (ValueAnimator) animator.get(observer),
                expanded.getBoolean(observer));
    }
}
