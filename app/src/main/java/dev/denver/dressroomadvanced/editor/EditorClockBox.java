package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.util.Range;
import android.util.Size;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class EditorClockBox {
    private static final Map<Object, Preview> previews = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Class<?>> unsupported = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<ClockLabelFit.Size> scaleSize = new ThreadLocal<>();

    private static final class Preview {
        final WeakReference<View> renderer;
        final int type;
        Frame frame;

        Preview(View renderer, int type) {
            this.renderer = new WeakReference<>(renderer);
            this.type = type;
        }
    }

    private record Frame(int top, int height, float scale, float center, boolean labels) {}

    private EditorClockBox() {}

    static void bind(DressroomModule module, EditorBinding binding, Object owner, View clock, Object data) {
        try {
            updateBudget(clock, data);
            if (binding.legacy) return;
            Object positioner = binding.positioner(owner);
            install(module, positioner.getClass(), data.getClass());
            Preview preview = previews.get(positioner);
            int type = number(data, "getClockType");
            if (preview == null || preview.type != type || preview.renderer.get() != clock) {
                preview = new Preview(clock, type);
                int height = number(data, "getScaledHeight");
                int top = number(data, "getY");
                float scale = ((Number) Reflect.call(data, "getScale")).floatValue();
                if (height > 0) preview.frame = new Frame(top, height, scale, top + height / 2f, false);
                previews.put(positioner, preview);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.report("Clock box sizing unavailable", error);
        }
    }

    private static View renderer(Object positioner, Object data) throws ReflectiveOperationException {
        Preview preview = previews.get(positioner);
        if (preview == null || preview.type != number(data, "getClockType")) return null;
        View clock = preview.renderer.get();
        return clock != null && clock.isAttachedToWindow() ? clock : null;
    }

    private static synchronized void install(DressroomModule module, Class<?> owner, Class<?> data)
            throws ReflectiveOperationException {
        if (installed.contains(owner) || unsupported.contains(owner)) return;
        try (HookTransaction hooks = new HookTransaction()) {
            Class<?> areaType = data.getMethod("getSizeConstraints").getReturnType();
            ClassLoader loader = owner.getClassLoader();
            Class<?> previewScale = Class.forName(
                    "com.samsung.android.lockscreen.editor.item.domain.entity.system.PreviewScale", false, loader);
            Class<?> screenSize = Class.forName(
                    "com.samsung.android.lockscreen.editor.item.domain.entity.system.ScreenSize", false, loader);
            Method limit = NativeContract.method(owner, "q", Range.class,
                    int.class, int.class, Rect.class, areaType);
            Method scale = NativeContract.method(owner, "i", void.class, data, float.class);
            Method valid = NativeContract.method(owner, "e", boolean.class, data, screenSize);
            Method position = NativeContract.method(owner, "l", void.class, data, previewScale, screenSize);

            // Native clock scale range
            hooks.add(module.hook(limit).setId("dressroom-advanced.label-scale-range").intercept(chain -> {
                ClockLabelFit.Size stock = scaleSize.get();
                if (stock == null || stock.width() <= 0 || stock.height() <= 0) return chain.proceed();
                return chain.proceed(new Object[] {(int) Math.ceil(stock.width()),
                        (int) Math.ceil(stock.height()), chain.getArg(2), chain.getArg(3)});
            })::unhook);
            for (Method method : new Method[] {scale, valid}) {
                hooks.add(module.hook(method).setId("dressroom-advanced.label-clock-scale").intercept(chain -> {
                    Object model = chain.getArg(0);
                    ClockLabelFit.Size previous = scaleSize.get();
                    try {
                        View clock = renderer(chain.getThisObject(), model);
                        scaleSize.set(clock == null ? null : DateEffects.stockSize(clock));
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        scaleSize.remove();
                        module.report("Clock scale range unavailable", error);
                    }
                    try {
                        return chain.proceed();
                    } finally {
                        if (previous == null) scaleSize.remove();
                        else scaleSize.set(previous);
                    }
                })::unhook);
                module.deoptimize(method);
            }

            // Vertical center
            hooks.add(module.hook(position).setId("dressroom-advanced.label-box-position").intercept(chain -> {
                Object model = chain.getArg(0);
                try {
                    View clock = renderer(chain.getThisObject(), model);
                    if (clock != null) center(previews.get(chain.getThisObject()), clock, model);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    module.report("Clock box placement unavailable", error);
                }
                return chain.proceed();
            })::unhook);
            module.deoptimize(limit);
            module.deoptimize(position);
            hooks.commit();
            installed.add(owner);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            unsupported.add(owner);
            throw error;
        }
    }

    private static void updateBudget(View clock, Object data) throws ReflectiveOperationException {
        Object constraints = Reflect.call(data, "getSizeConstraints");
        Size max = (Size) Reflect.call(constraints, "getMaxSize");
        DateEffects.editorBounds(clock, (Rect) Reflect.call(data, "getMovableArea"),
                ((Number) Reflect.call(data, "getScale")).floatValue(), max.getHeight());
    }

    private static void center(Preview preview, View clock, Object data) throws ReflectiveOperationException {
        updateBudget(clock, data);
        if (Boolean.TRUE.equals(Reflect.call(data, "isSupportSeparateDateTimeScale"))) return;
        Rect area = (Rect) Reflect.call(data, "getMovableArea");
        int height = number(data, "getScaledHeight");
        if (area.isEmpty() || height <= 0 || height > area.height()) return;

        int top = number(data, "getY");
        float scale = ((Number) Reflect.call(data, "getScale")).floatValue();
        boolean labels = DateEffects.stockSize(clock) != null;
        float center = top + height / 2f;
        Frame previous = preview.frame;
        if (previous != null && scale == previous.scale && (labels || previous.labels)) {
            if (height != previous.height) {
                center = previous.center;
                int centered = ClockLabelFit.centeredStart(center, height, area.top, area.bottom);
                if (centered != top) Reflect.call(data, "setY", centered);
                top = centered;
            } else if (top == previous.top) {
                center = previous.center;
            }
        }
        preview.frame = new Frame(top, height, scale, center, labels);
    }

    private static int number(Object target, String name) throws ReflectiveOperationException {
        return ((Number) Reflect.call(target, name)).intValue();
    }
}
