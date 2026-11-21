package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

record OneUi80WidgetPanel(Field controller, Method show, Field mode, Method readMode) {
    private static final String UI_MODE =
            "Lcom/samsung/android/lockscreen/editor/item/domain/entity/preview/UIMode;";
    private static final String OBSERVABLE =
            "Lcom/samsung/android/lockscreen/editor/item/domain/entity/common/ObservableData;";

    static OneUi80WidgetPanel bind(Method create) throws ReflectiveOperationException {
        Class<?> preview = create.getDeclaringClass();
        ClassLoader loader = preview.getClassLoader();
        Class<?> callback = Class.forName("kotlin.jvm.functions.Function1", false, loader);
        Class<?>[] arguments = {View.class, View.class, View.class, Integer.class,
                Integer.class, Integer.class, boolean.class, callback};
        Field panel = OneUi95Editor.unique(java.util.Arrays.stream(preview.getDeclaredFields())
                .filter(field -> NativeContract.hasMethod(field.getType(), void.class, arguments))
                .toList(), "8.0 widget panel controller");
        panel.setAccessible(true);
        try {
            var body = DexSignatures.body(create);
            var modeCheck = OneUi95Editor.call(body, UI_MODE, "isLockScreen");
            var modeRepo = body.calls().stream().filter(call -> call.offset() < modeCheck.offset()
                    && call.parameters().isEmpty() && call.result().equals(OBSERVABLE))
                    .reduce((first, last) -> last)
                    .orElseThrow(() -> new NoSuchMethodException("8.0 widget preview mode"));
            Method readMode = modeRepo.method(loader);
            String owner = DexSignatures.descriptor(preview);
            Field mode = body.reads().stream().filter(read -> read.offset() < modeRepo.offset()
                    && read.field().owner().equals(owner)).reduce((first, last) -> last)
                    .orElseThrow(() -> new NoSuchFieldException("8.0 widget preview mode owner"))
                    .field().field(loader);
            if (!readMode.getDeclaringClass().isAssignableFrom(mode.getType()))
                throw new NoSuchFieldException("8.0 widget preview mode use case");
            return new OneUi80WidgetPanel(panel,
                    NativeContract.findMethod(panel.getType(), null, void.class, arguments),
                    mode, readMode);
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the One UI 8.0 widget panel", error);
        }
    }

    boolean main(Object preview) throws ReflectiveOperationException {
        Object repo = readMode.invoke(mode.get(preview));
        Object value = Reflect.call(repo, "getData");
        return Boolean.TRUE.equals(Reflect.call(value, "isLockScreen"));
    }

    record Catalog(Method open, Field loaded) {}

    static Catalog catalog(Class<?> preview) throws ReflectiveOperationException {
        String reload = "load data again : ";
        String owner = DexSignatures.descriptor(preview);
        var matches = new ArrayList<Catalog>();
        try {
            for (Method method : preview.getDeclaredMethods()) {
                if (method.getReturnType() != boolean.class || method.getParameterCount() != 0
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                var body = DexSignatures.body(method, Set.of(reload));
                if (!body.anchors().containsKey(reload)) continue;
                var loaded = body.uniqueRead(owner, "Z", reload);
                if (!loaded.equals(body.uniqueWrite(owner, "Z", reload))) continue;
                matches.add(new Catalog(body.method(preview.getClassLoader()),
                        loaded.field(preview.getClassLoader())));
            }
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the 8.0 widget catalog", error);
        }
        return OneUi95Editor.unique(matches, "8.0 widget catalog reload");
    }

    static Field toolbarView(Method render) throws ReflectiveOperationException {
        Class<?> owner = render.getDeclaringClass();
        try {
            return DexSignatures.body(render)
                    .uniqueRead(DexSignatures.descriptor(owner),
                            DexSignatures.descriptor(LinearLayout.class), null)
                    .field(owner.getClassLoader());
        } catch (IOException error) {
            throw new ReflectiveOperationException("Could not bind the 8.0 toolbar view", error);
        }
    }

    static void installToolbarFallback(Activity activity, EditorWidgets editor) {
        int rootId = id(activity, "editor_root");
        int previewId = id(activity, "lock_preview");
        int scrimId = id(activity, "panel_scrim");
        if (rootId == 0 || previewId == 0
                || !(activity.findViewById(rootId) instanceof RelativeLayout root)) return;

        Button advanced = new Button(activity);
        advanced.setText("Advanced");
        advanced.setAllCaps(false);
        advanced.setVisibility(View.GONE);
        advanced.setOnClickListener(view -> editor.openFromClock(activity));
        RelativeLayout.LayoutParams placement = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        placement.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        placement.addRule(RelativeLayout.CENTER_HORIZONTAL);
        root.addView(advanced, placement);

        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View preview = root.findViewById(previewId);
            View scrim = scrimId == 0 ? null : root.findViewById(scrimId);
            WindowInsets insets = root.getRootWindowInsets();
            boolean typing = insets != null && insets.isVisible(WindowInsets.Type.ime());
            boolean visible = preview != null && preview.isShown() && !typing
                    && (scrim == null || !scrim.isShown() || scrim.getAlpha() == 0f)
                    && !editor.nativeToolbarVisible(root);
            int visibility = visible ? View.VISIBLE : View.GONE;
            if (advanced.getVisibility() != visibility) advanced.setVisibility(visibility);
            if (!visible) return;
            int bottom = Math.round(8 * root.getResources().getDisplayMetrics().density);
            if (insets != null) bottom += insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            if (placement.bottomMargin != bottom) {
                placement.bottomMargin = bottom;
                advanced.setLayoutParams(placement);
            }
        });
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }
}
