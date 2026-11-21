package dev.denver.dressroomadvanced;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class Mod9ExtrasContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void fails(Checked action, Class<? extends Throwable> type, String message)
            throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (Throwable thrown) {
            if (!type.isInstance(thrown)) throw thrown;
            checks++;
        }
    }

    private static URLClassLoader compileFixture(Path root, Map<String, String> sources)
            throws IOException {
        Path srcDir = root.resolve("src");
        Path clsDir = root.resolve("cls");
        Files.createDirectories(clsDir);
        List<String> files = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            Path target = srcDir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
            files.add(target.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "System JavaCompiler required for Mod9 contracts");
        List<String> args = new ArrayList<>();
        args.add("--release");
        args.add("17");
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-classpath");
        args.add(System.getProperty("java.class.path"));
        args.add("-d");
        args.add(clsDir.toString());
        args.addAll(files);
        int rc = compiler.run(null, null, null, args.toArray(String[]::new));
        check(rc == 0, "Fixture javac failed with code " + rc);
        return new URLClassLoader(
                new URL[] {clsDir.toUri().toURL()},
                Mod9ExtrasContractsTest.class.getClassLoader());
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static DexSignatures.Body body(
            String owner,
            String name,
            List<String> params,
            String result,
            String anchor,
            List<DexSignatures.Call> calls) {
        return new DexSignatures.Body(
                owner,
                name,
                params,
                result,
                0x1,
                Map.of(anchor, 0),
                List.of(),
                List.of(),
                calls);
    }

    private static void verifyNowBarExtrasEndCallbacks(Path tempRoot) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/android/systemui/power/feature/charginganimation/ChargingAnimationUI$forceDismissRunnable$1.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class ChargingAnimationUI$forceDismissRunnable$1 implements Runnable {\n"
                        + "    @Override public void run() {}\n"
                        + "}\n");
        src.put(
                "com/android/systemui/power/feature/charginganimation/ChargingAnimationUI.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class ChargingAnimationUI {\n"
                        + "    private ChargingAnimationUI$forceDismissRunnable$1 forceDismissRunnable;\n"
                        + "    public void dismissOverlay() {}\n"
                        + "}\n");
        src.put(
                "com/android/systemui/power/view/ChargerNowBarView.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView {}\n");
        src.put(
                "com/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$2.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView$addOnChargerAnimationEndHandler$2 {\n"
                        + "    public Object invokeSuspend(Object obj) { return null; }\n"
                        + "}\n");
        src.put(
                "com/android/systemui/power/view/ChargerNowBarView$other$1.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView$other$1 {\n"
                        + "    public Object invokeSuspend(Object obj) { return null; }\n"
                        + "}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("nb_concrete"), src)) {
            Class<?> ui =
                    loader.loadClass(
                            "com.android.systemui.power.feature.charginganimation.ChargingAnimationUI");
            Class<?> card = loader.loadClass("com.android.systemui.power.view.ChargerNowBarView");
            String uiDesc = DexSignatures.descriptor(ui);
            DexSignatures.Call dismissCall =
                    new DexSignatures.Call(10, uiDesc, "dismissOverlay", List.of(), "V", false);
            DexSignatures.Body wrongInner =
                    body(
                            "Lcom/android/systemui/power/view/ChargerNowBarView$other$1;",
                            "invokeSuspend",
                            List.of("Ljava/lang/Object;"),
                            "Ljava/lang/Object;",
                            "addOnChargerAnimationEndHandler, call onChargerAnimationEnd()",
                            List.of());
            DexSignatures.Body endLambda =
                    body(
                            "Lcom/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$2;",
                            "invokeSuspend",
                            List.of("Ljava/lang/Object;"),
                            "Ljava/lang/Object;",
                            "addOnChargerAnimationEndHandler, call onChargerAnimationEnd()",
                            List.of(dismissCall));
            List<Method> callbacks =
                    NowBarExtras.resolveEndCallbacks(
                            loader, ui, card, anchors -> List.of(wrongInner, endLambda));
            check(callbacks.size() == 2, "Expected both charging dismissal callbacks");
            check(
                    callbacks
                            .get(0)
                            .getDeclaringClass()
                            .getName()
                            .endsWith("ChargingAnimationUI$forceDismissRunnable$1"),
                    "Concrete forceDismissRunnable field type must resolve directly");
            check(
                    callbacks
                            .get(1)
                            .getDeclaringClass()
                            .getName()
                            .endsWith("ChargerNowBarView$addOnChargerAnimationEndHandler$2"),
                    "Shifted $2 addOnChargerAnimationEndHandler coroutine must resolve structurally");

            fails(
                    () -> NowBarExtras.resolveEndCallbacks(loader, ui, card, anchors -> List.of(wrongInner)),
                    NoSuchMethodException.class,
                    "Missing dismissOverlay call must fail structural resolution");
            fails(
                    () ->
                            NowBarExtras.resolveEndCallbacks(
                                    loader, ui, card, anchors -> List.of(endLambda, endLambda)),
                    NoSuchMethodException.class,
                    "Ambiguous end callbacks must fail resolution");
        }

        Map<String, String> ifaceSrc = new LinkedHashMap<>();
        ifaceSrc.put(
                "com/android/systemui/power/feature/charginganimation/ChargingAnimationUI.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class ChargingAnimationUI {\n"
                        + "    private Runnable forceDismissRunnable;\n"
                        + "    public void dismissOverlay() {}\n"
                        + "}\n");
        ifaceSrc.put(
                "com/android/systemui/power/feature/charginganimation/ChargingAnimationUI$forceDismissRunnable$3.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class ChargingAnimationUI$forceDismissRunnable$3 implements Runnable {\n"
                        + "    @Override public void run() {}\n"
                        + "}\n");
        ifaceSrc.put(
                "com/android/systemui/power/view/ChargerNowBarView.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView {}\n");
        ifaceSrc.put(
                "com/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$4.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView$addOnChargerAnimationEndHandler$4 {\n"
                        + "    public Object invokeSuspend(Object obj) { return null; }\n"
                        + "}\n");
        ifaceSrc.put(
                "com/android/systemui/power/feature/charginganimation/MissingFieldUI.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class MissingFieldUI {}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("nb_iface"), ifaceSrc)) {
            Class<?> ui =
                    loader.loadClass(
                            "com.android.systemui.power.feature.charginganimation.ChargingAnimationUI");
            Class<?> card = loader.loadClass("com.android.systemui.power.view.ChargerNowBarView");
            Class<?> missingUi =
                    loader.loadClass(
                            "com.android.systemui.power.feature.charginganimation.MissingFieldUI");
            String uiDesc = DexSignatures.descriptor(ui);
            DexSignatures.Call dismissCall =
                    new DexSignatures.Call(10, uiDesc, "dismissOverlay", List.of(), "V", false);
            DexSignatures.Body runnableBody =
                    body(
                            "Lcom/android/systemui/power/feature/charginganimation/ChargingAnimationUI$forceDismissRunnable$3;",
                            "run",
                            List.of(),
                            "V",
                            "forceDismissOverlay: current=",
                            List.of(dismissCall));
            DexSignatures.Body coroutineBody =
                    body(
                            "Lcom/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$4;",
                            "invokeSuspend",
                            List.of("Ljava/lang/Object;"),
                            "Ljava/lang/Object;",
                            "addOnChargerAnimationEndHandler, call onChargerAnimationEnd()",
                            List.of(dismissCall));
            NowBarExtras.AnchorFinder finder =
                    anchors ->
                            anchors.contains("forceDismissOverlay: current=")
                                    ? List.of(runnableBody)
                                    : List.of(coroutineBody);
            List<Method> callbacks = NowBarExtras.resolveEndCallbacks(loader, ui, card, finder);
            check(
                    callbacks
                            .get(0)
                            .getDeclaringClass()
                            .getName()
                            .endsWith("ChargingAnimationUI$forceDismissRunnable$3"),
                    "Runnable-typed field must fall back to DEX anchor resolution");
            check(
                    callbacks
                            .get(1)
                            .getDeclaringClass()
                            .getName()
                            .endsWith("ChargerNowBarView$addOnChargerAnimationEndHandler$4"),
                    "Renumbered $4 coroutine must resolve via anchor");
            fails(
                    () -> NowBarExtras.resolveEndCallbacks(loader, missingUi, card, finder),
                    NoSuchFieldException.class,
                    "Missing forceDismissRunnable field must fail contract check");
        }
    }

    private static void verifyNowBarCardHierarchyDetection(Path tempRoot) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/samsung/android/app/facewidget/presentation/NowBarBriefCardView.java",
                "package com.samsung.android.app.facewidget.presentation;\n"
                        + "public class NowBarBriefCardView {}\n");
        src.put(
                "com/samsung/android/app/facewidget/presentation/NowBarMediaCardView.java",
                "package com.samsung.android.app.facewidget.presentation;\n"
                        + "public class NowBarMediaCardView {}\n");
        src.put(
                "com/samsung/android/app/facewidget/presentation/CustomBriefSub.java",
                "package com.samsung.android.app.facewidget.presentation;\n"
                        + "public class CustomBriefSub extends NowBarBriefCardView {}\n");
        src.put(
                "com/samsung/android/app/facewidget/utils/NowBarCardStackView.java",
                "package com.samsung.android.app.facewidget.utils;\n"
                        + "public class NowBarCardStackView {}\n");
        src.put(
                "com/samsung/android/app/sublauncher/presentation/view/nowbar/SubLauncherNowBarBriefCardView.java",
                "package com.samsung.android.app.sublauncher.presentation.view.nowbar;\n"
                        + "public class SubLauncherNowBarBriefCardView {}\n");
        src.put(
                "com/samsung/android/app/sublauncher/presentation/view/nowbar/SubLauncherNowBarMediaCardView.java",
                "package com.samsung.android.app.sublauncher.presentation.view.nowbar;\n"
                        + "public class SubLauncherNowBarMediaCardView {}\n");
        src.put(
                "com/samsung/android/app/sublauncher/presentation/view/nowbar/SubLauncherMediaSub.java",
                "package com.samsung.android.app.sublauncher.presentation.view.nowbar;\n"
                        + "public class SubLauncherMediaSub extends SubLauncherNowBarMediaCardView {}\n");
        src.put(
                "com/samsung/android/app/sublauncher/presentation/view/nowbar/SubLauncherNowBarCardStackView.java",
                "package com.samsung.android.app.sublauncher.presentation.view.nowbar;\n"
                        + "public class SubLauncherNowBarCardStackView {}\n");
        src.put(
                "com/samsung/android/app/sublauncher/presentation/view/nowbar/SubLauncherNowBarRootview.java",
                "package com.samsung.android.app.sublauncher.presentation.view.nowbar;\n"
                        + "public class SubLauncherNowBarRootview {}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("cards"), src)) {
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView")),
                    "NowBarBriefCardView must be detected");
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView")),
                    "NowBarMediaCardView must be detected");
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.facewidget.presentation.CustomBriefSub")),
                    "Subclass of NowBarCardView must be detected via superclass walk");
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarBriefCardView")),
                    "SubLauncherNowBarBriefCardView must be detected for Fold/Flip cover");
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarMediaCardView")),
                    "SubLauncherNowBarMediaCardView must be detected for Fold/Flip cover");
            check(
                    BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherMediaSub")),
                    "Subclass of SubLauncherNowBarCardView must be detected via superclass walk");
            check(
                    !BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.facewidget.utils.NowBarCardStackView")),
                    "NowBarCardStackView must not be classified as a CardView");
            check(
                    !BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarCardStackView")),
                    "SubLauncherNowBarCardStackView must not be classified as a CardView");
            check(
                    !BlurAppearance.isNowBarCardType(
                            loader.loadClass(
                                    "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarRootview")),
                    "SubLauncherNowBarRootview must not be classified as a CardView");
            check(!BlurAppearance.isNowBarCardType(null), "Null view type must return false");
        }
    }

    private static void verifyUnlockBlurBoundaries() {
        check(UnlockBlur.duringSwipe(true, true, 0f), "Swipe at 0 stays active");
        check(UnlockBlur.duringSwipe(true, true, 0.99f), "First drag frame stays active");
        check(!UnlockBlur.duringSwipe(true, true, 1f), "Progress 1 disables override");
        check(!UnlockBlur.duringSwipe(false, true, 0.5f), "Non-drag fade ignored");
        check(!UnlockBlur.duringSwipe(true, false, 0.5f), "Disabled setting ignored");
        check(!UnlockBlur.duringSwipe(true, true, Float.NaN), "NaN progress ignored");
        check(!UnlockBlur.duringSwipe(true, true, -0.1f), "Negative progress ignored");
    }

    private static void verifyNotificationBlurPolicyResolver(Path tempRoot) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/android/systemui/statusbar/notification/row/ExpandableNotificationRow.java",
                "package com.android.systemui.statusbar.notification.row;\n"
                        + "public class ExpandableNotificationRow {}\n");
        src.put(
                "noticolorpicker/NotificationColorPicker.java",
                "package noticolorpicker;\n"
                        + "import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;\n"
                        + "public class NotificationColorPicker {\n"
                        + "    public static boolean isCustom(ExpandableNotificationRow row) { return false; }\n"
                        + "}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("notif"), src)) {
            Class<?> row =
                    loader.loadClass(
                            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            String rowDesc = DexSignatures.descriptor(row);
            DexSignatures.Call colorizedCall =
                    new DexSignatures.Call(
                            4,
                            "Landroid/app/Notification;",
                            "isColorized",
                            List.of(),
                            "Z",
                            false);
            DexSignatures.Call customCall =
                    new DexSignatures.Call(
                            8,
                            "Lnoticolorpicker/NotificationColorPicker;",
                            "isCustom",
                            List.of(rowDesc),
                            "Z",
                            true);
            Method resolved =
                    NotificationBlurOptions.resolveCustomPolicy(
                            loader, row, List.of(colorizedCall, customCall));
            check(
                    resolved != null && resolved.getName().equals("isCustom"),
                    "NotificationColorPicker.isCustom must resolve from isBlurBackground calls");
            check(
                    NotificationBlurOptions.resolveCustomPolicy(loader, row, List.of(customCall))
                            == null,
                    "Missing isColorized call must disable notification blur policy");
            check(
                    NotificationBlurOptions.resolveCustomPolicy(
                                    loader, row, List.of(colorizedCall, customCall, customCall))
                            == null,
                    "Duplicate isCustom calls must fail closed");
        }
    }

    private static void verifyNowBriefStyleBoxBlurContract(Path tempRoot) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/samsung/android/genui/model/style/BaseStyle.java",
                "package com.samsung.android.genui.model.style;\n"
                        + "public class BaseStyle {}\n");
        src.put(
                "com/samsung/android/genui/kit/nodes/StyleBox.java",
                "package com.samsung.android.genui.kit.nodes;\n"
                        + "import android.content.Context;\n"
                        + "import android.view.View;\n"
                        + "import com.samsung.android.genui.model.style.BaseStyle;\n"
                        + "public final class StyleBox {\n"
                        + "    private final void updateBlur(View view, Context context, BaseStyle style, boolean supportBlur, Float radius) {}\n"
                        + "    public static void updateBlur$default(StyleBox self, View view, Context context, BaseStyle style, boolean supportBlur, Float radius, int mask, Object obj) {}\n"
                        + "}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("stylebox_ok"), src)) {
            Class<?> style =
                    loader.loadClass("com.samsung.android.genui.model.style.BaseStyle");
            Method updateBlur = NowBriefFill.resolveStyleBoxUpdateBlur(loader, style);
            check("updateBlur".equals(updateBlur.getName()), "StyleBox.updateBlur must resolve");
        }

        Map<String, String> missing = new LinkedHashMap<>();
        missing.put(
                "com/samsung/android/genui/model/style/BaseStyle.java",
                src.get("com/samsung/android/genui/model/style/BaseStyle.java"));
        missing.put(
                "com/samsung/android/genui/kit/nodes/StyleBox.java",
                "package com.samsung.android.genui.kit.nodes;\n"
                        + "public final class StyleBox {}\n");
        try (URLClassLoader loader = compileFixture(tempRoot.resolve("stylebox_missing"), missing)) {
            Class<?> style =
                    loader.loadClass("com.samsung.android.genui.model.style.BaseStyle");
            fails(
                    () -> NowBriefFill.resolveStyleBoxUpdateBlur(loader, style),
                    NoSuchMethodException.class,
                    "Missing StyleBox.updateBlur must fail resolution");
        }

        check(!NowBriefFill.isBriefBlurView(null), "Outside updateBlur must not match null view");
        try {
            String value =
                    NowBriefFill.withUpdateBlur(
                            null,
                            () -> {
                                check(
                                        NowBriefFill.isBriefBlurView(null),
                                        "Thread must be marked active inside StyleBox.updateBlur");
                                NowBriefFill.withUpdateBlur(
                                        null,
                                        () -> {
                                            check(
                                                    NowBriefFill.isBriefBlurView(null),
                                                    "Nested StyleBox.updateBlur must stay active");
                                            return null;
                                        });
                                check(
                                        NowBriefFill.isBriefBlurView(null),
                                        "Outer StyleBox.updateBlur must stay active after inner exits");
                                return "ok";
                            });
            check("ok".equals(value), "withUpdateBlur must return callback value");
        } catch (Throwable error) {
            throw error instanceof Exception e ? e : new RuntimeException(error);
        }
        check(!NowBriefFill.isBriefBlurView(null), "Thread flag must clear after outer exit");

        boolean threw = false;
        try {
            NowBriefFill.withUpdateBlur(
                    null,
                    () -> {
                        throw new IllegalStateException("boom");
                    });
        } catch (IllegalStateException expected) {
            threw = true;
        } catch (Throwable other) {
            throw other instanceof Exception e ? e : new RuntimeException(other);
        }
        check(threw, "Exception from updateBlur body must propagate");
        check(!NowBriefFill.isBriefBlurView(null), "Thread flag must clear on exception");
        check(NowBarGlass.glassPreset(null, null) == null, "Null View glassPreset must return null");
    }

    private static Map<String, String> widgetClickBlockSources(boolean includeGridDispatch) {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/samsung/android/app/facewidget/complication/KeyguardComplicationWidgetContainer.java",
                "package com.samsung.android.app.facewidget.complication;\n"
                        + "import android.view.MotionEvent;\n"
                        + "import android.view.View;\n"
                        + "public final class KeyguardComplicationWidgetContainer {\n"
                        + "    public boolean dispatchTouchEvent(MotionEvent ev) { return false; }\n"
                        + "    public boolean containsTouchableRect(int x, int y) { return false; }\n"
                        + "    public boolean onTspEvent(View view, Object tsp) { return false; }\n"
                        + "}\n");
        src.put(
                "com/samsung/android/app/facewidget/complication/ComplicationContainer.java",
                "package com.samsung.android.app.facewidget.complication;\n"
                        + "import android.view.View;\n"
                        + "public final class ComplicationContainer {\n"
                        + "    public boolean containsTouchableRect(int x, int y) { return false; }\n"
                        + "    public boolean onTspEvent(View view, Object tsp) { return false; }\n"
                        + "}\n");
        src.put(
                "com/samsung/android/app/facewidget/complication/ComplicationWidgetPluginController.java",
                "package com.samsung.android.app.facewidget.complication;\n"
                        + "import android.view.View;\n"
                        + "public final class ComplicationWidgetPluginController {\n"
                        + "    public void dispatchTspEventToWidget(View view, int x, int y) {}\n"
                        + "}\n");
        src.put(
                "com/samsung/android/app/facewidget/complication/ComplicationManager.java",
                "package com.samsung.android.app.facewidget.complication;\n"
                        + "import android.view.ViewGroup;\n"
                        + "public final class ComplicationManager {\n"
                        + "    private ViewGroup container;\n"
                        + "    public boolean isInContentBounds(float x, float y) { return false; }\n"
                        + "}\n");
        src.put(
                "com/samsung/android/app/aodservice/common/grid/view/GridItemView.java",
                "package com.samsung.android.app.aodservice.common.grid.view;\n"
                        + "import android.view.MotionEvent;\n"
                        + "public final class GridItemView {\n"
                        + (includeGridDispatch
                                ? "    public boolean dispatchTouchEvent(MotionEvent ev) { return false; }\n"
                                : "")
                        + "}\n");
        return src;
    }

    private static void verifyWidgetClickBlockContracts(Path tempRoot) throws Exception {
        check(
                Arrays.asList(ExtrasPreferences.FLAGS).contains(WidgetClickBlock.KEY),
                "ExtrasPreferences.FLAGS missing WidgetClickBlock.KEY");
        for (int action : new int[] {0, 1, 2, 3, 211}) {
            check(
                    !WidgetClickBlock.shouldSuppressTouch(false, action),
                    "Widget clicks enabled must allow action " + action);
        }
        for (int action : new int[] {0, 1, 2, 211}) {
            check(
                    WidgetClickBlock.shouldSuppressTouch(true, action),
                    "Widget clicks disabled must suppress action " + action);
        }
        check(
                !WidgetClickBlock.shouldSuppressTouch(true, 3),
                "ACTION_CANCEL must still clean up gesture state");

        check(
                WidgetClickBlock.isLockscreenContainerName(WidgetClickBlock.KEYGUARD_CONTAINER),
                "KeyguardComplicationWidgetContainer must match lockscreen container check");
        check(
                WidgetClickBlock.isLockscreenContainerName(WidgetClickBlock.COMPLICATION_CONTAINER),
                "ComplicationContainer must match lockscreen container check");
        check(
                !WidgetClickBlock.isLockscreenContainerName(null)
                        && !WidgetClickBlock.isLockscreenContainerName("")
                        && !WidgetClickBlock.isLockscreenContainerName(
                                "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer")
                        && !WidgetClickBlock.isLockscreenContainerName(
                                "com.samsung.android.app.sublauncher.presentation.view.nowbar.SubLauncherNowBarRootview"),
                "Non-lockscreen complication classes must not match");

        try (URLClassLoader loader9 =
                compileFixture(tempRoot.resolve("wcb_9x"), widgetClickBlockSources(true))) {
            WidgetClickBlock.Targets targets9 = WidgetClickBlock.resolveTargets(loader9);
            check(
                    targets9.keyguardDispatchTouch() != null
                            && targets9.keyguardContainsTouchable() != null
                            && targets9.keyguardOnTsp().size() == 1,
                    "9.x KeyguardComplicationWidgetContainer targets must resolve");
            check(
                    targets9.complicationContainsTouchable() != null
                            && targets9.complicationOnTsp().size() == 1,
                    "9.x ComplicationContainer targets must resolve");
            check(
                    targets9.controllerDispatchTsp() != null,
                    "9.x dispatchTspEventToWidget must resolve");
            check(
                    targets9.managerInContentBounds() != null
                            && targets9.managerContainer() != null,
                    "9.x ComplicationManager bounds target must resolve");
            check(
                    targets9.gridItemDispatchTouch() != null,
                    "9.x GridItemView.dispatchTouchEvent must resolve");
        }

        try (URLClassLoader loader8 =
                compileFixture(tempRoot.resolve("wcb_80"), widgetClickBlockSources(false))) {
            WidgetClickBlock.Targets targets8 = WidgetClickBlock.resolveTargets(loader8);
            check(
                    targets8.keyguardDispatchTouch() != null
                            && targets8.keyguardContainsTouchable() != null
                            && targets8.keyguardOnTsp().size() == 1,
                    "8.0 KeyguardComplicationWidgetContainer targets must resolve");
            check(
                    targets8.complicationContainsTouchable() != null
                            && targets8.complicationOnTsp().size() == 1,
                    "8.0 ComplicationContainer targets must resolve");
            check(
                    targets8.controllerDispatchTsp() != null,
                    "8.0 dispatchTspEventToWidget must resolve");
            check(
                    targets8.managerInContentBounds() != null
                            && targets8.managerContainer() != null,
                    "8.0 ComplicationManager bounds target must resolve");
            check(
                    targets8.gridItemDispatchTouch() == null,
                    "8.0 fixture without GridItemView.dispatchTouchEvent must leave gridItemDispatchTouch null");
        }
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("mod9-extras-contracts-");
        try {
            verifyNowBarExtrasEndCallbacks(tempRoot);
            verifyNowBarCardHierarchyDetection(tempRoot);
            verifyUnlockBlurBoundaries();
            verifyNotificationBlurPolicyResolver(tempRoot);
            verifyNowBriefStyleBoxBlurContract(tempRoot);
            verifyWidgetClickBlockContracts(tempRoot);
            System.out.println("Mod9 extras contracts: " + checks + " checks passed");
        } finally {
            deleteTree(tempRoot);
        }
    }
}
