package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structurally resolved DEX contracts for inline lockscreen clock seconds. */
final class ClockSecondsBinding {
    private static final String VIEW_MODEL =
            "com.samsung.android.app.facewidget.FaceWidgetViewModel";
    private static final String CLOCK_PAGE =
            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage";
    private static final String BASE_PAGE =
            "com.samsung.android.app.facewidget.pages.FaceWidgetPage";
    private static final String NONE_PADDING_TEXT =
            "com.samsung.android.uniform.widget.NonePaddingTextView";
    private static final String CLOCK_NONE_PADDING_TEXT =
            "com.samsung.android.uniform.widget.ClockNonePaddingTextView";

    private static final String TAP_ANCHOR = "servicebox_clock";
    private static final String BIND_ANCHOR = "initViews() mClockView=";
    private static final String TIME_CHANGED_ANCHOR = "onTimeChanged : ";
    private static final String ORIG_FONT_ANCHOR =
            "ClockNonePaddingTextView: mOriginalFontType = ";
    private static final String BLUR_ALPHA_ANCHOR = "applyAnimatedAlpha: alpha ";

    final Class<?> absClockBase;
    final Class<?> clockPageClass;
    final Class<?> glyphTextClass;
    final Method tap;
    final Method sleep;
    final Method visibilityChanged;
    final Method folderChanged;
    final Method darkAmount;
    final Method bindClock;
    final Method timeChanged;
    final Method contentsPosition;
    final Field clockView;
    final Field pageState;
    final Constructor<?> glyphCtor;
    final Method glyphPath;
    final GlyphFontCache.Resolved glyphFont;
    final Field originalFontType;
    final Field pathMode;
    final Field pathTextColor;
    final Method setVariableClockFont;
    final Field isVariableClockFont;
    final ClockBlurRetryBinding blurRetry;
    final ClockBlurBinding clockBlur;
    final Method applyAnimatedAlpha;
    final ClockPageTransition pageTransition;
    final WidgetBlurInfo semBlurCopies;
    final Method semSetBlurInfo;
    final Constructor<?> semBlurBuilderCtor;
    final Method semBlurSetRadius;
    final Method semBlurBuild;

    private final Map<String, Ids> idsByPackage = new HashMap<>();

    record Ids(
            int timeAreaContainer,
            int timeArea,
            int timeAreaHourMin,
            int stretchTimeArea,
            int minuteContainer,
            int colon,
            int minute10th,
            int minute1st,
            int hourMin,
            int minute,
            int stretchColon,
            int stretchMinute10th,
            int stretchMinute1st) {}

    private ClockSecondsBinding(
            Class<?> absClockBase,
            Class<?> clockPageClass,
            Class<?> glyphTextClass,
            Method tap,
            Method sleep,
            Method visibilityChanged,
            Method folderChanged,
            Method darkAmount,
            Method bindClock,
            Method timeChanged,
            Method contentsPosition,
            Field clockView,
            Field pageState,
            Constructor<?> glyphCtor,
            Method glyphPath,
            GlyphFontCache.Resolved glyphFont,
            Field originalFontType,
            Field pathMode,
            Field pathTextColor,
            Method setVariableClockFont,
            Field isVariableClockFont,
            ClockBlurRetryBinding blurRetry,
            ClockBlurBinding clockBlur,
            Method applyAnimatedAlpha,
            ClockPageTransition pageTransition,
            WidgetBlurInfo semBlurCopies,
            Method semSetBlurInfo,
            Constructor<?> semBlurBuilderCtor,
            Method semBlurSetRadius,
            Method semBlurBuild) {
        this.absClockBase = absClockBase;
        this.clockPageClass = clockPageClass;
        this.glyphTextClass = glyphTextClass;
        this.tap = tap;
        this.sleep = sleep;
        this.visibilityChanged = visibilityChanged;
        this.folderChanged = folderChanged;
        this.darkAmount = darkAmount;
        this.bindClock = bindClock;
        this.timeChanged = timeChanged;
        this.contentsPosition = contentsPosition;
        this.clockView = clockView;
        this.pageState = pageState;
        this.glyphCtor = glyphCtor;
        this.glyphPath = glyphPath;
        this.glyphFont = glyphFont;
        this.originalFontType = originalFontType;
        this.pathMode = pathMode;
        this.pathTextColor = pathTextColor;
        this.setVariableClockFont = setVariableClockFont;
        this.isVariableClockFont = isVariableClockFont;
        this.blurRetry = blurRetry;
        this.clockBlur = clockBlur;
        this.applyAnimatedAlpha = applyAnimatedAlpha;
        this.pageTransition = pageTransition;
        this.semBlurCopies = semBlurCopies;
        this.semSetBlurInfo = semSetBlurInfo;
        this.semBlurBuilderCtor = semBlurBuilderCtor;
        this.semBlurSetRadius = semBlurSetRadius;
        this.semBlurBuild = semBlurBuild;
    }

    static ClockSecondsBinding resolve(Class<?> base)
            throws ReflectiveOperationException, IOException {
        Class<?> abs = ClockBlurRetryBinding.absClockView(base);
        ClassLoader loader = abs.getClassLoader();
        Class<?> vmClass = Class.forName(VIEW_MODEL, false, loader);
        Class<?> pageClass = Class.forName(CLOCK_PAGE, false, loader);
        Class<?> basePageClass = Class.forName(BASE_PAGE, false, loader);
        Class<?> nonePaddingClass = Class.forName(NONE_PADDING_TEXT, false, loader);
        Class<?> glyphClass = Class.forName(CLOCK_NONE_PADDING_TEXT, false, loader);

        String vmDesc = DexSignatures.descriptor(vmClass);
        String pageDesc = DexSignatures.descriptor(pageClass);
        String absDesc = DexSignatures.descriptor(abs);
        String glyphDesc = DexSignatures.descriptor(glyphClass);

        List<DexSignatures.Body> anchored =
                DexSignatures.find(
                        loader,
                        Set.of(
                                TAP_ANCHOR,
                                BIND_ANCHOR,
                                TIME_CHANGED_ANCHOR,
                                ORIG_FONT_ANCHOR,
                                BLUR_ALPHA_ANCHOR));

        DexSignatures.Body tapBody =
                uniqueMethod(anchored, vmDesc, TAP_ANCHOR, "V", List.of("I", "I"));
        Method tap = tapBody.method(loader);

        Method sleep = NativeContract.method(vmClass, "onStartedGoingToSleep", void.class);
        Method visibilityChanged =
                NativeContract.method(
                        vmClass, "onKeyguardVisibilityHelperChanged", void.class, int.class);
        Method folderChanged =
                NativeContract.method(vmClass, "onFolderStateChanged", void.class, boolean.class);
        Method darkAmount =
                NativeContract.method(vmClass, "setDarkAmount", void.class, float.class);

        DexSignatures.Body bindBody =
                uniqueMethod(
                        anchored, pageDesc, BIND_ANCHOR, "V", List.of("Landroid/view/View;"));
        Method bindClock = bindBody.method(loader);
        Field clockView = bindBody.uniqueWrite(pageDesc, absDesc, null).field(loader);
        Field pageState = resolvePageStateField(bindBody, pageClass, loader);
        Method timeChanged = resolveSingleArgAnchored(anchored, absDesc, TIME_CHANGED_ANCHOR, loader);

        Method contentsPosition =
                NativeContract.method(basePageClass, "getContentsViewPosition", Rect.class);

        Constructor<?> glyphCtor =
                glyphClass.getDeclaredConstructor(Context.class, AttributeSet.class);
        glyphCtor.setAccessible(true);
        Method glyphPath = null;
        try {
            glyphPath = glyphClass.getDeclaredMethod("getPathForBlur");
            glyphPath.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }
        GlyphFontCache.Resolved glyphFont = GlyphFontCache.resolve(abs);
        Field originalFontType = resolveOriginalFontType(anchored, glyphDesc, loader);

        Field pathMode = null;
        Field pathTextColor = null;
        String noneDesc = DexSignatures.descriptor(nonePaddingClass);
        Method setColor = nonePaddingClass.getMethod("setTextColor", int.class);
        if (setColor.getDeclaringClass() == nonePaddingClass) {
            DexSignatures.Body colorBody = DexSignatures.body(setColor);
            pathMode = colorBody.uniqueRead(noneDesc, "Z", null).field(loader);
            pathTextColor = colorBody.uniqueWrite(noneDesc, "I", null).field(loader);
        }

        Method setVariableClockFont = null;
        Field isVariableClockFont = null;
        try {
            Method setter = nonePaddingClass.getMethod("setVariableClockFont", boolean.class);
            if (setter.getDeclaringClass() == nonePaddingClass) {
                setter.setAccessible(true);
                Field flag =
                        DexSignatures.body(setter).uniqueWrite(noneDesc, "Z", null).field(loader);
                flag.setAccessible(true);
                setVariableClockFont = setter;
                isVariableClockFont = flag;
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException ignored) {
        }

        ClockBlurRetryBinding blurRetry = null;
        ClockBlurBinding clockBlur = null;
        Method applyAnimatedAlpha = null;
        if (!ClockBinding.of(abs).clockStyle) {
            try {
                blurRetry = ClockBlurRetryBinding.of(abs);
                clockBlur = ClockBlurBinding.resolve(abs);
                String mgrDesc = DexSignatures.descriptor(blurRetry.manager.getType());
                applyAnimatedAlpha =
                        uniqueMethod(anchored, mgrDesc, BLUR_ALPHA_ANCHOR, "V", List.of("I"))
                                .method(loader);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        ClockPageTransition pageTransition = null;
        try {
            pageTransition = ClockPageTransition.resolve(loader);
        } catch (ReflectiveOperationException | IOException ignored) {
        }

        WidgetBlurInfo semBlurCopies = null;
        Method semSetBlurInfo = null;
        Constructor<?> semBlurBuilderCtor = null;
        Method semBlurSetRadius = null;
        Method semBlurBuild = null;
        try {
            Class<?> infoClass = Class.forName("android.view.SemBlurInfo");
            Class<?> builderClass = Class.forName("android.view.SemBlurInfo$Builder");
            WidgetBlurInfo copies = new WidgetBlurInfo(infoClass);
            if (copies.supportsPathClip()) semBlurCopies = copies;
            semSetBlurInfo = View.class.getDeclaredMethod("semSetBlurInfo", infoClass);
            semSetBlurInfo.setAccessible(true);
            semBlurBuilderCtor = builderClass.getConstructor(int.class);
            semBlurSetRadius = builderClass.getMethod("setRadius", int.class);
            semBlurBuild = builderClass.getMethod("build");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        return new ClockSecondsBinding(
                abs,
                pageClass,
                glyphClass,
                tap,
                sleep,
                visibilityChanged,
                folderChanged,
                darkAmount,
                bindClock,
                timeChanged,
                contentsPosition,
                clockView,
                pageState,
                glyphCtor,
                glyphPath,
                glyphFont,
                originalFontType,
                pathMode,
                pathTextColor,
                setVariableClockFont,
                isVariableClockFont,
                blurRetry,
                clockBlur,
                applyAnimatedAlpha,
                pageTransition,
                semBlurCopies,
                semSetBlurInfo,
                semBlurBuilderCtor,
                semBlurSetRadius,
                semBlurBuild);
    }

    private static Method resolveSingleArgAnchored(
            List<DexSignatures.Body> bodies, String owner, String anchor, ClassLoader loader)
            throws ReflectiveOperationException {
        DexSignatures.Body found = null;
        for (DexSignatures.Body body : bodies) {
            if ((body.access() & Modifier.STATIC) != 0
                    || !body.owner().equals(owner)
                    || !body.anchors().containsKey(anchor)
                    || !body.result().equals("V")
                    || body.parameters().size() != 1) continue;
            if (found != null)
                throw new NoSuchMethodException("Ambiguous anchored method for " + anchor);
            found = body;
        }
        if (found == null)
            throw new NoSuchMethodException("Missing anchored method for " + anchor);
        return found.method(loader);
    }

    private static DexSignatures.Body uniqueMethod(
            List<DexSignatures.Body> bodies,
            String owner,
            String anchor,
            String result,
            List<String> parameters)
            throws NoSuchMethodException {
        DexSignatures.Body found = null;
        for (DexSignatures.Body body : bodies) {
            if ((body.access() & Modifier.STATIC) != 0
                    || !body.owner().equals(owner)
                    || !body.anchors().containsKey(anchor)
                    || !body.result().equals(result)
                    || !body.parameters().equals(parameters)) continue;
            if (found != null)
                throw new NoSuchMethodException("Ambiguous anchored method for " + anchor);
            found = body;
        }
        if (found == null)
            throw new NoSuchMethodException("Missing anchored method for " + anchor);
        return found;
    }

    private static Field resolvePageStateField(
            DexSignatures.Body body, Class<?> pageClass, ClassLoader loader)
            throws ReflectiveOperationException {
        Set<Field> candidates = new HashSet<>();
        for (DexSignatures.Read read : body.reads()) {
            Field field;
            try {
                field = read.field().field(loader);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                continue;
            }
            if (!field.getDeclaringClass().isAssignableFrom(pageClass)) continue;
            Class<?> type = field.getType();
            if (!type.isEnum()) continue;
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            Set<String> names = new HashSet<>();
            for (Object c : constants) names.add(((Enum<?>) c).name());
            if (names.contains("SMALL") && names.contains("DASHBOARD")) candidates.add(field);
        }
        if (candidates.size() != 1)
            throw new NoSuchFieldException("Ambiguous clock page state field: " + candidates.size());
        return candidates.iterator().next();
    }

    private static Field resolveOriginalFontType(
            List<DexSignatures.Body> bodies, String glyphDesc, ClassLoader loader)
            throws ReflectiveOperationException {
        DexSignatures.Body ctor = null;
        for (DexSignatures.Body body : bodies) {
            if (body.owner().equals(glyphDesc)
                    && body.name().equals("<init>")
                    && body.anchors().containsKey(ORIG_FONT_ANCHOR)) {
                if (ctor != null)
                    throw new NoSuchMethodException("Ambiguous ClockNonePaddingTextView ctor");
                ctor = body;
            }
        }
        if (ctor == null)
            throw new NoSuchMethodException("Missing ClockNonePaddingTextView ctor");

        int recyclePc = -1;
        for (DexSignatures.Call call : ctor.calls()) {
            if (call.owner().equals("Landroid/content/res/TypedArray;")
                    && call.name().equals("recycle")
                    && call.parameters().isEmpty()) {
                recyclePc = call.offset();
                break;
            }
        }
        if (recyclePc < 0)
            throw new NoSuchMethodException("Missing TypedArray.recycle in glyph ctor");

        Set<DexSignatures.Member> writes = new HashSet<>();
        for (DexSignatures.Write write : ctor.writes()) {
            if (write.offset() < recyclePc
                    && write.field().owner().equals(glyphDesc)
                    && write.field().type().equals("I")) {
                writes.add(write.field());
            }
        }
        if (writes.size() != 1)
            throw new NoSuchFieldException("Ambiguous originalFontType field: " + writes.size());
        return writes.iterator().next().field(loader);
    }

    synchronized Ids ids(View clock) {
        Context context = clock.getContext();
        String pkg = context.getPackageName();
        Ids cached = idsByPackage.get(pkg);
        if (cached != null) return cached;
        Resources res = context.getResources();
        Ids resolved =
                new Ids(
                        res.getIdentifier("common_time_area_container", "id", pkg),
                        res.getIdentifier("common_time_area", "id", pkg),
                        res.getIdentifier("common_time_area_hour_min", "id", pkg),
                        res.getIdentifier("common_stretch_time_area", "id", pkg),
                        res.getIdentifier("common_minute_container", "id", pkg),
                        res.getIdentifier("common_colon", "id", pkg),
                        res.getIdentifier("common_minute10th", "id", pkg),
                        res.getIdentifier("common_minute1st", "id", pkg),
                        res.getIdentifier("common_hourMin", "id", pkg),
                        res.getIdentifier("common_minute", "id", pkg),
                        res.getIdentifier("common_stretch_colon", "id", pkg),
                        res.getIdentifier("common_stretch_minute10th", "id", pkg),
                        res.getIdentifier("common_stretch_minute1st", "id", pkg));
        idsByPackage.put(pkg, resolved);
        return resolved;
    }

    TextView newGlyphView(Context context) throws ReflectiveOperationException {
        return (TextView) glyphCtor.newInstance(context, null);
    }
}
