package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class OneUi95Widgets {
    private static final List<String> ADAPTER_ARGUMENTS =
            List.of("Landroid/view/View;", "I", "I", "F", "I", "I", "Z");
    private static final Class<?>[] MENU_ARGS = {int.class, int.class, BitmapDrawable.class,
            String.class, String.class, int.class, Runnable.class, int.class};

    record Toolbar(Method render, Field items, Field tag, Field context, Constructor<?> item) {
        void add(Object owner, Function<Context, Runnable> openFactory, String marker)
                throws ReflectiveOperationException {
            Object current = items.get(owner);
            if (current == null || Array.getLength(current) == 0) return;
            int count = Array.getLength(current);
            for (int index = 0; index < count; index++)
                if (marker.equals(tag.get(Array.get(current, index)))) return;
            Context screen = (Context) context.get(owner);
            int icon = screen.getResources().getIdentifier(
                    "preview_menu_icon_effect", "drawable", screen.getPackageName());
            if (icon == 0) icon = android.R.drawable.ic_menu_manage;
            Object updated = Array.newInstance(item.getDeclaringClass(), count + 1);
            System.arraycopy(current, 0, updated, 0, count);
            Object entry = item.newInstance(-1, icon, null, "Advanced", marker, 0,
                    openFactory.apply(screen), 0);
            Array.set(updated, count, entry);
            items.set(owner, updated);
        }
    }

    static Toolbar toolbar(Class<?> owner) throws ReflectiveOperationException {
        ClassLoader loader = owner.getClassLoader();
        List<Field> arrays = java.util.Arrays.stream(owner.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && field.getType().isArray()
                        && NativeContract.hasConstructor(field.getType().getComponentType(), MENU_ARGS))
                .toList();
        if (arrays.isEmpty()) return null;
        Field items = OneUi95Editor.unique(arrays, "editor toolbar items");
        items.setAccessible(true);
        Class<?> itemType = items.getType().getComponentType();
        String anchor = "weatherEffect";
        String ownerType = DexSignatures.descriptor(owner);
        try {
            Method render = OneUi95Editor.unique(DexSignatures.find(loader, Set.of(anchor))
                    .stream().filter(body -> body.owner().equals(ownerType)
                            && body.result().equals("V") && body.parameters().isEmpty()
                            && !Modifier.isStatic(body.access())
                            && body.reads().stream().anyMatch(read ->
                                    read.field().name().equals(items.getName())
                                            && read.field().owner().equals(ownerType))
                            && body.calls().stream().anyMatch(call -> call.name().equals("removeAllViews")
                                    && call.owner().equals("Landroid/view/ViewGroup;")))
                    .toList(), "editor toolbar renderer").method(loader);
            Method lookup = NativeContract.findMethod(owner, null, itemType, String.class);
            Field tag = DexSignatures.body(lookup).uniqueRead(
                    DexSignatures.descriptor(itemType), "Ljava/lang/String;", null).field(loader);
            Constructor<?> constructor = itemType.getDeclaredConstructor(MENU_ARGS);
            constructor.setAccessible(true);
            Field context = OneUi95Editor.unique(java.util.Arrays.stream(owner.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers())
                            && field.getType() == Context.class)
                    .toList(), "editor toolbar context");
            context.setAccessible(true);
            return new Toolbar(render, items, tag, context, constructor);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Native toolbar DEX unavailable", invalidDex);
        }
    }

    static Method gridMetrics(ClassLoader loader, Class<?> grid, Class<?> align)
            throws ReflectiveOperationException {
        try {
            return OneUi95Editor.unique(DexSignatures.find(loader,
                    Set.of("AbstractMethodError getLockWidgetGridSize")).stream()
                    .filter(body -> !Modifier.isStatic(body.access()) && body.result().equals("V")
                            && body.parameters().equals(List.of(DexSignatures.descriptor(grid),
                                    DexSignatures.descriptor(align), "Z")))
                    .toList(), "lockscreen widget grid measurements").method(loader);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Widget grid DEX unavailable", invalidDex);
        }
    }

    static Method cellMetrics(ClassLoader loader, Class<?> grid, Class<?> size)
            throws ReflectiveOperationException {
        try {
            Class<?> owner = OneUi95Editor.unique(DexSignatures.find(loader,
                    Set.of("WidgetSizeRepositoryImpl")).stream()
                    .filter(body -> body.name().equals("getTag") && body.parameters().isEmpty()
                            && body.result().equals("Ljava/lang/String;"))
                    .toList(), "lockscreen widget size repository").loadOwner(loader);
            return NativeContract.findMethodPrefix(owner, null, void.class, grid, size);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Widget size DEX unavailable", invalidDex);
        }
    }

    static Method resizeAnimation(ClassLoader loader) throws ReflectiveOperationException {
        try {
            return OneUi95Editor.unique(
                    DexSignatures.find(loader, Set.of("view")).stream()
                            .filter(body -> Modifier.isStatic(body.access())
                                    && body.result().equals("V")
                                    && body.parameters().equals(ADAPTER_ARGUMENTS)
                                    && callsView(body, "getTag", "I")
                                    && callsView(body, "setTag", "I", "Ljava/lang/Object;")
                                    && callsView(body, "getTranslationX")
                                    && callsView(body, "getTranslationY"))
                            .toList(), "widget resize animation").method(loader);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Widget resize animation DEX unavailable", invalidDex);
        }
    }

    private static boolean callsView(DexSignatures.Body body, String name, String... parameters) {
        return body.calls().stream().anyMatch(call -> call.owner().equals("Landroid/view/View;")
                && call.name().equals(name) && call.parameters().equals(List.of(parameters))
                && !call.isStatic());
    }

    static Method lifecycle(ClassLoader loader) throws ReflectiveOperationException {
        Set<String> anchors = Set.of("resume check ui mode ", "requestChangeToSettingHostIds");
        try {
            Method lifecycle = OneUi95Editor.unique(
                    DexSignatures.find(loader, anchors).stream()
                            .filter(body -> !Modifier.isStatic(body.access())
                                    && body.parameters().size() == 1 && body.result().equals("V")
                                    && body.anchors().keySet().containsAll(anchors)
                                    && body.calls().stream().anyMatch(call ->
                                            call.name().equals("getWidgetAlignType")))
                            .toList(), "widget resume lifecycle").method(loader);
            Class<?> event = lifecycle.getParameterTypes()[0];
            if (!event.isEnum() || !event.getField("Resume").isEnumConstant())
                throw new NoSuchMethodException("Widget lifecycle event is missing Resume");
            return lifecycle;
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Widget lifecycle DEX unavailable", invalidDex);
        }
    }

    static Method rotationGuide(ClassLoader loader) throws ReflectiveOperationException {
        String tag = "layout/widget_align_assist_layout_0";
        String invalid = "The tag for widget_align_assist_layout is invalid. Received: ";
        String dataBinding = "Landroidx/databinding/ViewDataBinding;";
        try {
            DexSignatures.Body mapper = OneUi95Editor.unique(
                    DexSignatures.find(loader, Set.of(tag, invalid)).stream()
                            .filter(body -> body.name().equals("getDataBinder")
                                    && body.result().equals(dataBinding)
                                    && body.parameters().equals(List.of(
                                            "Landroidx/databinding/DataBindingComponent;",
                                            "Landroid/view/View;", "I"))
                                    && body.anchors().keySet().containsAll(Set.of(tag, invalid)))
                            .toList(), "widget rotation guide layout mapping");
            DexSignatures.Call constructor = OneUi95Editor.unique(mapper.calls().stream()
                    .filter(call -> call.offset() > mapper.anchors().get(tag)
                            && call.offset() < mapper.anchors().get(invalid)
                            && call.name().equals("<init>")
                            && call.parameters().equals(List.of("Landroid/view/View;",
                                    "Landroidx/databinding/DataBindingComponent;")))
                    .toList(), "widget rotation guide constructor");
            Class<?> binding = Class.forName(constructor.owner().substring(1,
                    constructor.owner().length() - 1).replace('/', '.'), false, loader);
            Class<?> base = Class.forName("androidx.databinding.ViewDataBinding", false, loader);
            if (!base.isAssignableFrom(binding))
                throw new ClassNotFoundException("Widget rotation guide is not a native binding");
            return NativeContract.method(binding, "executeBindings", void.class);
        } catch (IOException invalidDex) {
            throw new ReflectiveOperationException("Widget rotation guide DEX unavailable", invalidDex);
        }
    }
}
