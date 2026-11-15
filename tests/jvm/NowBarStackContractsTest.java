package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public final class NowBarStackContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static final class PresetHost {
        static int lastFiveArgCount;
        static int lastFourArgCount;

        static int preset5(
                int count, Context context, boolean b1, boolean b2, boolean b3) {
            if (count != 62 || b1 || b2 || b3) {
                throw new AssertionError("Unexpected 5-arg preset arguments");
            }
            lastFiveArgCount = count;
            return 105;
        }

        static int preset4(Context context, boolean b1, boolean b2, int count) {
            if (count != 62 || b1 || b2) {
                throw new AssertionError("Unexpected 4-arg preset arguments");
            }
            lastFourArgCount = count;
            return 204;
        }

        static int unrelated(String tag) {
            return tag.length();
        }
    }

    public interface ShrunkSam {
        Object invoke();
    }

    public static final class ShrunkStackSize implements ShrunkSam {
        final StackStub owner;
        final int rank;

        public ShrunkStackSize(StackStub owner, int rank) {
            this.owner = owner;
            this.rank = rank;
        }

        @Override
        public Object invoke() {
            return owner == null ? 0 : rank;
        }
    }

    public static final class CardStub {
        public void setGetStackSize(ShrunkSam getter) {}
    }

    public static final class StackStub {
        boolean d0;
        boolean g0;
        boolean j0;
        boolean J;
        boolean P;
    }

    public static final class PipelineRootStub {
        boolean expandedFlag;
        boolean settingsFlag;
        boolean U;

        boolean k() {
            return expandedFlag;
        }

        boolean n() {
            return settingsFlag;
        }
    }

    private static DexSignatures.Body syntheticBody(
            String owner,
            String name,
            List<String> params,
            String result,
            int access,
            Map<String, Integer> anchors,
            List<DexSignatures.Read> reads,
            List<DexSignatures.Call> calls) {
        return syntheticBody(
                owner, name, params, result, access, anchors, reads, List.of(), calls);
    }

    private static DexSignatures.Body syntheticBody(
            String owner,
            String name,
            List<String> params,
            String result,
            int access,
            Map<String, Integer> anchors,
            List<DexSignatures.Read> reads,
            List<DexSignatures.Write> writes,
            List<DexSignatures.Call> calls) {
        return new DexSignatures.Body(
                owner, name, params, result, access, anchors, reads, writes, calls);
    }

    private static void verifyPresetSelectorResolutionAndInvocation() throws Exception {
        String hostDesc = DexSignatures.descriptor(PresetHost.class);
        ClassLoader loader = PresetHost.class.getClassLoader();
        List<DexSignatures.Call> fiveArgCalls =
                List.of(
                        new DexSignatures.Call(
                                1, hostDesc, "unrelated", List.of("Ljava/lang/String;"), "I", true),
                        new DexSignatures.Call(
                                2,
                                hostDesc,
                                "preset5",
                                List.of("I", "Landroid/content/Context;", "Z", "Z", "Z"),
                                "I",
                                true));
        Method m5 = NowBarGlass.selectPresetMethod(fiveArgCalls, loader);
        check("preset5".equals(m5.getName()), "5-arg preset selector must resolve");
        check(
                NowBarGlass.invokePresetSelector(m5, null) == 105 && PresetHost.lastFiveArgCount == 62,
                "5-arg preset selector must pass (62, context, false, false, false)");

        List<DexSignatures.Call> fourArgCalls =
                List.of(
                        new DexSignatures.Call(
                                1, hostDesc, "unrelated", List.of("Ljava/lang/String;"), "I", true),
                        new DexSignatures.Call(
                                2,
                                hostDesc,
                                "preset4",
                                List.of("Landroid/content/Context;", "Z", "Z", "I"),
                                "I",
                                true));
        Method m4 = NowBarGlass.selectPresetMethod(fourArgCalls, loader);
        check("preset4".equals(m4.getName()), "4-arg ClockPack preset selector must resolve");
        check(
                NowBarGlass.invokePresetSelector(m4, null) == 204 && PresetHost.lastFourArgCount == 62,
                "4-arg preset selector must pass (context, false, false, 62)");

        boolean ambiguousRejected = false;
        try {
            NowBarGlass.selectPresetMethod(
                    List.of(fiveArgCalls.get(1), fourArgCalls.get(1)), loader);
        } catch (NoSuchMethodException expected) {
            ambiguousRejected = true;
        }
        check(ambiguousRejected, "Ambiguous preset selectors must fail closed");

        boolean unsupportedRejected = false;
        try {
            NowBarGlass.invokePresetSelector(
                    PresetHost.class.getDeclaredMethod("unrelated", String.class), null);
        } catch (NoSuchMethodException expected) {
            unsupportedRejected = true;
        }
        check(unsupportedRejected, "Unsupported preset selector signature must fail closed");
    }

    private static void verifyBriefBlurSignatures() {
        String briefDesc = "Lcom/samsung/android/app/facewidget/presentation/NowBarBriefCardView;";
        String cardDesc = "Lcom/samsung/android/app/facewidget/presentation/a;";
        String styleDesc = "Lcom/samsung/android/genui/model/style/BlurStyle;";
        DexSignatures.Body validRefresh =
                syntheticBody(
                        briefDesc,
                        "g",
                        List.of("F"),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        4, briefDesc, "g0", List.of("F"), "I", false),
                                new DexSignatures.Call(
                                        10, styleDesc, "<init>", List.of(), "V", false),
                                new DexSignatures.Call(
                                        20,
                                        "Lcom/samsung/android/genui/GenUIManager;",
                                        "updateBlurStyle",
                                        List.of(styleDesc),
                                        "V",
                                        false)));
        check(
                NowBarGlass.isBriefRefreshBody(validRefresh, styleDesc),
                "Valid ClockPack-style brief blur refresh body must match");
        check(
                NowBarGlass.briefTintCalls(validRefresh, briefDesc).size() == 1
                        && "g0".equals(NowBarGlass.briefTintCalls(validRefresh, briefDesc).get(0).name()),
                "Brief blur refresh must identify single same-owner tint call");

        DexSignatures.Body missingGenUi =
                syntheticBody(
                        briefDesc,
                        "h",
                        List.of("F"),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(),
                        List.of(new DexSignatures.Call(10, styleDesc, "<init>", List.of(), "V", false)));
        check(
                !NowBarGlass.isBriefRefreshBody(missingGenUi, styleDesc),
                "Brief refresh body missing updateBlurStyle must be rejected");

        DexSignatures.Body validTint =
                syntheticBody(
                        briefDesc,
                        "g0",
                        List.of("F"),
                        "I",
                        Modifier.PRIVATE,
                        Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        8, cardDesc, "L", List.of("F", "F"), "I", false)));
        check(
                NowBarGlass.isBriefTintBody(validTint, cardDesc),
                "Brief tint body calling card.(F,F)I must match");
    }

    private static void verifyPillBoundsSignature() {
        String anchor = "isInContentsArea x = ";
        DexSignatures.Body valid =
                syntheticBody(
                        "Laod/tx2;",
                        "J",
                        List.of("Landroid/view/View;", "I", "I"),
                        "Z",
                        Modifier.PUBLIC | Modifier.STATIC,
                        Map.of(anchor, 4),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        22,
                                        "Landroid/graphics/Rect;",
                                        "contains",
                                        List.of("I", "I"),
                                        "Z",
                                        false)));
        check(OneUi9MediaBinding.isPillBoundsBody(valid), "9.0-clockpack pillBounds must match");

        DexSignatures.Body missingAnchor =
                syntheticBody(
                        "Laod/tx2;",
                        "K",
                        List.of("Landroid/view/View;", "I", "I"),
                        "Z",
                        Modifier.PUBLIC | Modifier.STATIC,
                        Map.of(),
                        List.of(),
                        valid.calls());
        check(
                !OneUi9MediaBinding.isPillBoundsBody(missingAnchor),
                "Unrelated static (View,int,int)->boolean without anchor must be rejected");
    }

    private static void verifyClockStyleStackNames() {
        NowBarStackBinding.Names n = OneUi9ClockStyleStack.names();
        check("aod.e54".equals(n.model()), "ClockStyle model mismatch");
        check("aod.k80".equals(n.states()), "ClockStyle states mismatch");
        check("aod.bo3".equals(n.manager()), "ClockStyle manager mismatch");
        check("aod.j54".equals(n.stackSize()), "ClockStyle stackSize mismatch");
        check("aod.ft6".equals(n.keys()), "ClockStyle keys mismatch");
        check("r".equals(n.key()), "ClockStyle key method mismatch");
        check("o".equals(n.items()), "ClockStyle items mismatch");
        check("O".equals(n.restricted()), "ClockStyle restricted mismatch");
        check("L".equals(n.mode()), "ClockStyle mode mismatch");
        check("N".equals(n.screenOn()), "ClockStyle screenOn mismatch");
        check("g0".equals(n.dragging()) && "j0".equals(n.reordering()) && "m0".equals(n.dismissing()),
                "ClockStyle motion flags mismatch");
        check("T".equals(n.expanding()), "ClockStyle expanding mismatch");
        check("U".equals(n.animationManager()) && "b".equals(n.animationsEnabled()),
                "ClockStyle animationManager fields mismatch");
        check("c".equals(n.animate()) && n.countedAnimate(), "ClockStyle animate mismatch");
        check("T".equals(n.bindItem()) && "r".equals(n.hideContents()),
                "ClockStyle card methods mismatch");
        check("x0".equals(n.wave()), "ClockStyle wave mismatch");
        check("com.samsung.android.app.clockpack".equals(n.resources()),
                "ClockStyle resources package mismatch");
    }

    private static void verifyStackBindingStructuralResolversAndStackState() throws Exception {
        String stackDesc = DexSignatures.descriptor(StackStub.class);
        String sizeDesc = DexSignatures.descriptor(ShrunkStackSize.class);
        String cardDesc = DexSignatures.descriptor(CardStub.class);
        DexSignatures.Body refreshBody =
                syntheticBody(
                        stackDesc,
                        "q",
                        List.of("Ljava/util/concurrent/CopyOnWriteArrayList;"),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        2, new DexSignatures.Member(stackDesc, "d0", "Z")),
                                new DexSignatures.Read(
                                        4, new DexSignatures.Member(stackDesc, "g0", "Z")),
                                new DexSignatures.Read(
                                        6, new DexSignatures.Member(stackDesc, "j0", "Z")),
                                new DexSignatures.Read(
                                        8, new DexSignatures.Member(stackDesc, "d0", "Z")),
                                new DexSignatures.Read(
                                        14, new DexSignatures.Member(stackDesc, "J", "Z")),
                                new DexSignatures.Read(
                                        16,
                                        new DexSignatures.Member(stackDesc, "unrelated", "I"))),
                        List.of(
                                new DexSignatures.Call(
                                        20, cardDesc, "setInitialDozing", List.of("Z"), "V", false),
                                new DexSignatures.Call(
                                        30,
                                        sizeDesc,
                                        "<init>",
                                        List.of(stackDesc, "I"),
                                        "V",
                                        false)));

        List<Field> flags =
                NowBarStackBinding.resolveMotionFlagsFromRefresh(
                        StackStub.class, refreshBody, OneUi85AodStack.names());
        check(
                "d0".equals(flags.get(0).getName())
                        && "g0".equals(flags.get(1).getName())
                        && "j0".equals(flags.get(2).getName()),
                "Motion flags must resolve by bytecode read order regardless of 8932 Names");

        Field screenOn =
                NowBarStackBinding.resolveScreenOnFromRefresh(
                        StackStub.class, refreshBody, "wrongFieldName");
        check("J".equals(screenOn.getName()), "screenOn must resolve via read before setInitialDozing");

        Constructor<?> sizeCtor =
                NowBarStackBinding.resolveStackSize(
                        ShrunkStackSize.class.getClassLoader(),
                        StackStub.class,
                        CardStub.class,
                        refreshBody,
                        "nonexistent.FallbackClass");
        ShrunkSam instance = (ShrunkSam) sizeCtor.newInstance(new StackStub(), 3);
        check(Integer.valueOf(3).equals(instance.invoke()),
                "stackSize must resolve shrunk SAM implementation without Function0");

        NowBarStackBinding.StackState rootState =
                NowBarStackBinding.StackState.of(PipelineRootStub.class);
        PipelineRootStub root = new PipelineRootStub();
        root.expandedFlag = true;
        check(Boolean.TRUE.equals(rootState.expanded.invoke(root)),
                "StackState must support NowBarPipelineProbe-style Root stubs");
        check(rootState.cardBlurAlpha == null && rootState.mode == null,
                "Optional StackState fields should remain null on test stubs without ViewMode");
        check(
                NowBarStackBinding.StackState.of(PipelineRootStub.class) == rootState,
                "StackState must be cached per root Class");

        DexSignatures.Body getCountBody =
                syntheticBody(
                        stackDesc,
                        "getCount",
                        List.of(),
                        "I",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        0,
                                        new DexSignatures.Member(
                                                stackDesc, "b", "Ljava/util/ArrayList;"))),
                        List.of());
        check(
                "b".equals(NowBarStackBinding.resolveItemsFieldName(stackDesc, getCountBody)),
                "items must resolve ArrayList field name");

        DexSignatures.Body getMaxBody =
                syntheticBody(
                        stackDesc,
                        "getStackMaxSize",
                        List.of(),
                        "I",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        0, new DexSignatures.Member(stackDesc, "K", "Z"))),
                        List.of());
        check(
                "K".equals(NowBarStackBinding.resolveRestrictedFieldName(stackDesc, getMaxBody)),
                "restricted must resolve boolean field name");

        DexSignatures.Body expandViBody =
                syntheticBody(
                        stackDesc,
                        "setExpandVIStarted",
                        List.of("Z"),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Write(
                                        0, new DexSignatures.Member(stackDesc, "P", "Z"))),
                        List.of());
        check(
                "P".equals(NowBarStackBinding.resolveExpandingFieldName(stackDesc, expandViBody)),
                "expanding must resolve boolean write field name");

        DexSignatures.Body expandedSetterBody =
                syntheticBody(
                        stackDesc,
                        "setExpandedCardState",
                        List.of("Z"),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        0,
                                        new DexSignatures.Member(
                                                stackDesc, "r0", "[Lkotlin/reflect/KProperty;")),
                                new DexSignatures.Read(
                                        4,
                                        new DexSignatures.Member(stackDesc, "M", "Laod/DA0;"))),
                        List.of());
        check(
                "M:Laod/DA0;".equals(
                        NowBarStackBinding.resolveExpandedDelegateKey(stackDesc, expandedSetterBody)),
                "expanded delegate resolution must ignore synthetic KProperty[] metadata array");

        DexSignatures.Body validDepth =
                syntheticBody(
                        stackDesc,
                        "e",
                        List.of("Landroid/view/View;", "Z"),
                        "I",
                        Modifier.PUBLIC,
                        Map.of("getCardDepthFromView exception ", 12),
                        List.of(),
                        List.of());
        DexSignatures.Body badDepth =
                syntheticBody(
                        stackDesc,
                        "e",
                        List.of("Landroid/view/View;", "Z"),
                        "I",
                        Modifier.PUBLIC,
                        Map.of("getCardDepthFromView exception", 12),
                        List.of(),
                        List.of());
        check(
                NowBarStackBinding.isDepthBody(validDepth)
                        && !NowBarStackBinding.isDepthBody(badDepth),
                "depth anchor must match exact DEX string with trailing space");

        DexSignatures.Body validWave =
                syntheticBody(
                        "Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;",
                        "y0",
                        List.of(),
                        "V",
                        Modifier.PUBLIC,
                        Map.of("updateWaveDisplay, isDozing = ", 8),
                        List.of(),
                        List.of());
        DexSignatures.Body badWave =
                syntheticBody(
                        "Lcom/samsung/android/app/facewidget/presentation/NowBarMediaCardView;",
                        "y0",
                        List.of(),
                        "V",
                        Modifier.PUBLIC,
                        Map.of("updateWaveDisplay", 8),
                        List.of(),
                        List.of());
        check(
                NowBarStackBinding.isWaveBody(validWave)
                        && !NowBarStackBinding.isWaveBody(badWave),
                "wave anchor must match exact One UI 9 DEX string 'updateWaveDisplay, isDozing = '");
    }

    public static void main(String[] args) throws Exception {
        verifyPresetSelectorResolutionAndInvocation();
        verifyBriefBlurSignatures();
        verifyPillBoundsSignature();
        verifyClockStyleStackNames();
        verifyStackBindingStructuralResolversAndStackState();
        System.out.println("Now bar stack contracts: " + checks + " checks passed");
    }
}
