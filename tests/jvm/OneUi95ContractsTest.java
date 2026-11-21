package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class OneUi95ContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private interface ObfuscatedLazy {
        Object get();
    }

    private interface NotASupplier {
        Object get();

        int size();
    }

    private interface WrongReturnSupplier {
        String get();
    }

    private static final class ConcreteSupplier {
        public Object get() {
            return null;
        }
    }

    private static final class FakeWrapper {}

    private static final class FakePreviewWithObfuscatedLazy {
        final ObfuscatedLazy a;

        FakePreviewWithObfuscatedLazy(Object target) {
            this.a = () -> target;
        }
    }

    private static final class FakePreviewWithMovedLazy {
        final String a = "unrelated";
        final ObfuscatedLazy q;

        FakePreviewWithMovedLazy(Object target) {
            this.q = () -> target;
        }
    }

    private static final class Preview95Shape {
        boolean L;
        View K;
        View N;
    }

    private static final class Preview90Shape {
        View L;
        View K;
    }

    private static final class PreviewOld90Shape {
        View K;
    }

    private static final class FakeModel95 {
        Animator f;
    }

    private static final class FakeCard95 {
        FakeModel95 getShowHideAnimationData() {
            return null;
        }
    }

    private static final class FakeManager95 {
        boolean b;

        void d(View view, FakeModel95 model, int count) {}
    }

    private static final class FakeStack95 {
        FakeManager95 R;
    }

    private static final class DirectController90 {
        final Object complicationGridUIController = "grid90";
        final Object keyguardClockProvider = "clock90";
        final ObfuscatedLazy aodDataManager = () -> "aod90";
    }

    private static final class ProviderController95 {
        final ObfuscatedLazy complicationGridUIControllerProvider = () -> "grid95";
        final ObfuscatedLazy keyguardClockProviderProvider = () -> "clock95";
        final ObfuscatedLazy aodDataManagerProvider = () -> "aod95";
    }

    public interface Sam95 {
        Object invoke();
    }

    public static final class StackSize95 implements Sam95 {
        final StackHost95 stack;
        final int mode;

        public StackSize95(StackHost95 stack, int mode) {
            this.stack = stack;
            this.mode = mode;
        }

        @Override
        public Object invoke() {
            return stack == null ? -1 : mode;
        }
    }

    public static final class CardHost95 {
        public void setGetStackSize(Sam95 getter) {}
    }

    public static final class StackHost95 {
        boolean K;
    }

    // Minimal DEX 041 container builder with two sections sharing one string table in section 1.
    private static byte[] buildDex041Container(
            String class0,
            String method0,
            String anchor0,
            String class1,
            String method1,
            String anchor1) {
        // Shared string table (sorted lexicographically as required by DEX, though Reader scans linearly):
        List<String> strings = List.of(
                anchor0,
                anchor1,
                class0,
                class1,
                "Ljava/lang/Object;",
                "V",
                method0,
                method1);
        int idxAnchor0 = 0;
        int idxAnchor1 = 1;
        int idxClass0 = 2;
        int idxClass1 = 3;
        int idxObj = 4;
        int idxVoid = 5;
        int idxMethod0 = 6;
        int idxMethod1 = 7;

        // Section 0 layout:
        // [0..120)     header 0 (120 bytes)
        // [120..128)   type_ids (2 * 4 = 8 bytes): type 0 = class0, type 1 = V, type 2 = Object -> 3 * 4 = 12 bytes
        // [132..144)   proto_ids (1 * 12 = 12 bytes): ()V
        // [144..152)   method_ids (1 * 8 = 8 bytes): class0.method0()V
        // [152..184)   class_defs (1 * 32 = 32 bytes)
        // [184..212)   code_item 0 (16 header + 6 bytes insns = 22 bytes, padded to 4 -> 24 bytes -> 208)
        // [208..224)   class_data_item 0 (padded to 4 -> 224)
        // Section 1 starts at 224:
        // [224..344)   header 1 (120 bytes)
        // [344..376)   shared string_ids (8 * 4 = 32 bytes)
        // [376..388)   type_ids (3 * 4 = 12 bytes): type 0 = class1, type 1 = V, type 2 = Object
        // [388..400)   proto_ids (1 * 12 = 12 bytes): ()V
        // [400..408)   method_ids (1 * 8 = 8 bytes): class1.method1()V
        // [408..440)   class_defs (1 * 32 = 32 bytes)
        // [440..464)   code_item 1 (24 bytes)
        // [464..480)   class_data_item 1 (16 bytes)
        // [480..end)   string_data items
        int sec0Size = 224;
        int sec1Off = sec0Size;
        int sharedStringIdsOff = sec1Off + 120; // 344
        int sec1TypesOff = sharedStringIdsOff + strings.size() * 4; // 376
        int sec1ProtosOff = sec1TypesOff + 12; // 388
        int sec1MethodsOff = sec1ProtosOff + 12; // 400
        int sec1ClassDefsOff = sec1MethodsOff + 8; // 408
        int sec1CodeOff = sec1ClassDefsOff + 32; // 440
        int sec1ClassDataOff = sec1CodeOff + 24; // 464
        int stringDataStart = sec1ClassDataOff + 16; // 480

        ByteArrayOutputStream stringBlob = new ByteArrayOutputStream();
        int[] stringOffsets = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            stringOffsets[i] = stringDataStart + stringBlob.size();
            byte[] utf = strings.get(i).getBytes(StandardCharsets.UTF_8);
            writeUleb(stringBlob, strings.get(i).length());
            stringBlob.write(utf, 0, utf.length);
            stringBlob.write(0);
        }
        while (stringBlob.size() % 4 != 0) stringBlob.write(0);
        byte[] strBytes = stringBlob.toByteArray();
        int containerSize = stringDataStart + strBytes.length;
        int sec1Size = containerSize - sec1Off;

        byte[] buf = new byte[containerSize];

        // Write shared string data + shared string_ids
        System.arraycopy(strBytes, 0, buf, stringDataStart, strBytes.length);
        for (int i = 0; i < stringOffsets.length; i++) {
            put32(buf, sharedStringIdsOff + i * 4, stringOffsets[i]);
        }

        // Write Section 0
        writeHeader041(
                buf,
                0,
                sec0Size,
                strings.size(),
                sharedStringIdsOff,
                3,
                120,
                1,
                132,
                1,
                144,
                1,
                152,
                containerSize);
        put32(buf, 120, idxClass0);
        put32(buf, 124, idxVoid);
        put32(buf, 128, idxObj);
        put32(buf, 132, idxVoid); // shorty
        put32(buf, 136, 1); // return_type = V (type idx 1)
        put32(buf, 140, 0); // no params
        put16(buf, 144, 0); // class_idx = 0 (class0)
        put16(buf, 146, 0); // proto_idx = 0
        put32(buf, 148, idxMethod0);
        put32(buf, 152, 0); // class_idx = 0
        put32(buf, 156, 1); // public
        put32(buf, 160, 2); // superclass = Object
        put32(buf, 176, 208); // class_data_off = 208
        writeConstStringCode(buf, 184, idxAnchor0);
        writeSingleVirtualMethodClassData(buf, 208, 0, 1, 184);

        // Write Section 1
        writeHeader041(
                buf,
                sec1Off,
                sec1Size,
                strings.size(),
                sharedStringIdsOff,
                3,
                sec1TypesOff,
                1,
                sec1ProtosOff,
                1,
                sec1MethodsOff,
                1,
                sec1ClassDefsOff,
                containerSize);
        put32(buf, sec1TypesOff, idxClass1);
        put32(buf, sec1TypesOff + 4, idxVoid);
        put32(buf, sec1TypesOff + 8, idxObj);
        put32(buf, sec1ProtosOff, idxVoid);
        put32(buf, sec1ProtosOff + 4, 1);
        put32(buf, sec1ProtosOff + 8, 0);
        put16(buf, sec1MethodsOff, 0);
        put16(buf, sec1MethodsOff + 2, 0);
        put32(buf, sec1MethodsOff + 4, idxMethod1);
        put32(buf, sec1ClassDefsOff, 0);
        put32(buf, sec1ClassDefsOff + 4, 1);
        put32(buf, sec1ClassDefsOff + 8, 2);
        put32(buf, sec1ClassDefsOff + 24, sec1ClassDataOff);
        writeConstStringCode(buf, sec1CodeOff, idxAnchor1);
        writeSingleVirtualMethodClassData(buf, sec1ClassDataOff, 0, 1, sec1CodeOff);

        return buf;
    }

    private static void writeHeader041(
            byte[] buf,
            int off,
            int fileSize,
            int stringCount,
            int stringOff,
            int typeCount,
            int typeOff,
            int protoCount,
            int protoOff,
            int methodCount,
            int methodOff,
            int classCount,
            int classOff,
            int containerSize) {
        byte[] magic = {'d', 'e', 'x', '\n', '0', '4', '1', 0};
        System.arraycopy(magic, 0, buf, off, magic.length);
        put32(buf, off + 32, fileSize);
        put32(buf, off + 36, 120);
        put32(buf, off + 40, 0x12345678);
        put32(buf, off + 56, stringCount);
        put32(buf, off + 60, stringOff);
        put32(buf, off + 64, typeCount);
        put32(buf, off + 68, typeOff);
        put32(buf, off + 72, protoCount);
        put32(buf, off + 76, protoOff);
        put32(buf, off + 88, methodCount);
        put32(buf, off + 92, methodOff);
        put32(buf, off + 96, classCount);
        put32(buf, off + 100, classOff);
        put32(buf, off + 112, containerSize);
        put32(buf, off + 116, off);
    }

    private static void writeConstStringCode(byte[] buf, int off, int stringIdx) {
        put16(buf, off, 1); // registers_size
        put16(buf, off + 2, 1); // ins_size
        put16(buf, off + 4, 0); // outs_size
        put16(buf, off + 6, 0); // tries_size
        put32(buf, off + 8, 0); // debug_info_off
        put32(buf, off + 12, 3); // insns_size = 3 code units (const-string = 2, return-void = 1)
        put16(buf, off + 16, 0x001a); // const-string v0, string@idx
        put16(buf, off + 18, stringIdx);
        put16(buf, off + 20, 0x000e); // return-void
    }

    private static void writeSingleVirtualMethodClassData(
            byte[] buf, int off, int methodIdxDiff, int access, int codeOff) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUleb(out, 0); // static_fields
        writeUleb(out, 0); // instance_fields
        writeUleb(out, 0); // direct_methods
        writeUleb(out, 1); // virtual_methods
        writeUleb(out, methodIdxDiff);
        writeUleb(out, access);
        writeUleb(out, codeOff);
        byte[] encoded = out.toByteArray();
        System.arraycopy(encoded, 0, buf, off, encoded.length);
    }

    private static void writeUleb(ByteArrayOutputStream out, int value) {
        int remaining = value;
        do {
            int b = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) b |= 0x80;
            out.write(b);
        } while (remaining != 0);
    }

    private static void put16(byte[] buf, int off, int v) {
        buf[off] = (byte) (v & 0xff);
        buf[off + 1] = (byte) ((v >>> 8) & 0xff);
    }

    private static void put32(byte[] buf, int off, int v) {
        buf[off] = (byte) (v & 0xff);
        buf[off + 1] = (byte) ((v >>> 8) & 0xff);
        buf[off + 2] = (byte) ((v >>> 16) & 0xff);
        buf[off + 3] = (byte) ((v >>> 24) & 0xff);
    }

    private static void verifyDex041Container(Path tempRoot) throws Exception {
        byte[] dex041 =
                buildDex041Container(
                        "Lcom/example/SecZero;",
                        "firstMethod",
                        "anchor_in_section_0",
                        "Lcom/example/SecOne;",
                        "secondMethod",
                        "anchor_in_section_1");
        List<DexSignatures.Body> inSec0 =
                DexSignatures.inspect(dex041, Set.of("anchor_in_section_0"));
        check(inSec0.size() == 1, "Expected 1 match in DEX 041 section 0, got " + inSec0.size());
        check(
                "Lcom/example/SecZero;".equals(inSec0.get(0).owner())
                        && "firstMethod".equals(inSec0.get(0).name()),
                "Wrong section 0 DEX 041 owner/method");

        List<DexSignatures.Body> inSec1 =
                DexSignatures.inspect(dex041, Set.of("anchor_in_section_1"));
        check(inSec1.size() == 1, "Expected 1 match in DEX 041 section 1, got " + inSec1.size());
        check(
                "Lcom/example/SecOne;".equals(inSec1.get(0).owner())
                        && "secondMethod".equals(inSec1.get(0).name()),
                "Wrong section 1 DEX 041 owner/method");

        Path dexFile = tempRoot.resolve("container.dex");
        Files.write(dexFile, dex041);
        List<DexSignatures.Body> uncached =
                DexSignatures.read(dexFile.toFile(), Set.of("anchor_in_section_1"));
        check(uncached.size() == 1, "Uncached DEX 041 file read missed section 1");
        List<DexSignatures.Body> cached =
                DexSignatures.cached(
                        () -> DexSignatures.read(dexFile.toFile(), Set.of("anchor_in_section_1")));
        check(cached.size() == 1, "Cached DEX 041 file read missed section 1");

        // Truncated container must fail cleanly with IOException.
        byte[] truncated = java.util.Arrays.copyOf(dex041, 260);
        put32(truncated, 112, truncated.length); // valid container_size <= length, but section 1 overflows
        boolean failed = false;
        try {
            DexSignatures.inspect(truncated, Set.of("anchor_in_section_1"));
        } catch (IOException expected) {
            failed = true;
        }
        check(failed, "Truncated DEX 041 container must reject invalid section bounds");
    }

    private static void verifySupplierAndWidgetRoot() throws Exception {
        check(
                WidgetEditorBinding.isZeroArgSupplier(ObfuscatedLazy.class),
                "R8 obfuscated single-method Object get() interface must match");
        check(
                !WidgetEditorBinding.isZeroArgSupplier(NotASupplier.class),
                "Multi-method interface must not match supplier");
        check(
                !WidgetEditorBinding.isZeroArgSupplier(WrongReturnSupplier.class),
                "Non-Object get() interface must not match erased Lazy/Provider supplier");
        check(
                !WidgetEditorBinding.isZeroArgSupplier(ConcreteSupplier.class),
                "Concrete class must not match supplier interface");

        FakeWrapper expected = new FakeWrapper();
        check(
                WidgetEditorBinding.unwrapSupplier(
                                new FakePreviewWithObfuscatedLazy(expected), "a", FakeWrapper.class)
                        == expected,
                "Preferred field 'a' obfuscated Lazy must unwrap cleanly");
        check(
                WidgetEditorBinding.unwrapSupplier(
                                new FakePreviewWithMovedLazy(expected), "a", FakeWrapper.class)
                        == expected,
                "Moved obfuscated Lazy field must unwrap via fallback scan");

        Field root95 = OneUi9WidgetEditor.resolvePreviewRoot(Preview95Shape.class);
        check(
                "N".equals(root95.getName()),
                "9.5 preview shape with boolean L and panel K must bind N, got " + root95.getName());
        Field root90 = OneUi9WidgetEditor.resolvePreviewRoot(Preview90Shape.class);
        check(
                "L".equals(root90.getName()),
                "9.0 preview shape with View L must keep L, got " + root90.getName());
        Field rootOld90 = OneUi9WidgetEditor.resolvePreviewRoot(PreviewOld90Shape.class);
        check(
                "K".equals(rootOld90.getName()),
                "Older 9.0 preview shape with only K must keep K, got " + rootOld90.getName());
    }

    private static void verifyNowBarStack95Resolution() throws Exception {
        ClassLoader loader = OneUi95ContractsTest.class.getClassLoader();
        NowBarStackBinding.Names unrelatedNames =
                new NowBarStackBinding.Names(
                        "aod.missingModel",
                        "aod.missingStates",
                        "aod.missingManager",
                        "aod.missingStackSize",
                        "aod.missingKeys",
                        "c",
                        "b",
                        "F",
                        "H",
                        "J",
                        "d0",
                        "g0",
                        "j0",
                        "U",
                        "Q",
                        "b",
                        "d",
                        true,
                        "Y",
                        "t",
                        "w0",
                        "com.samsung.android.app.aodservice");
        Class<?> model =
                NowBarStackBinding.resolveModelClass(loader, FakeCard95.class, unrelatedNames);
        check(model == FakeModel95.class, "Expected dynamic model discovery from getShowHideAnimationData");
        Class<?> manager =
                NowBarStackBinding.resolveManagerClass(
                        loader, FakeStack95.class, model, unrelatedNames);
        check(manager == FakeManager95.class, "Expected dynamic manager discovery from stack fields");

        String stackDesc = DexSignatures.descriptor(StackHost95.class);
        String sizeDesc = DexSignatures.descriptor(StackSize95.class);
        DexSignatures.Body emptyRefresh =
                new DexSignatures.Body(
                        stackDesc,
                        "x",
                        List.of("Ljava/lang/Object;", "Ljava/util/concurrent/CopyOnWriteArrayList;"),
                        "V",
                        0x1,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of());
        DexSignatures.Body makeCardBody =
                new DexSignatures.Body(
                        stackDesc,
                        "t",
                        List.of("Ljava/lang/Object;", stackDesc, "I"),
                        "Landroid/view/View;",
                        0x1,
                        Map.of("NowBar_NowBarCardStackView.#makeNowBarCardView", 0),
                        List.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        40,
                                        sizeDesc,
                                        "<init>",
                                        List.of(stackDesc, "I"),
                                        "V",
                                        false)));
        Constructor<?> stackSizeCtor =
                NowBarStackBinding.resolveStackSize(
                        loader,
                        StackHost95.class,
                        CardHost95.class,
                        emptyRefresh,
                        makeCardBody,
                        "aod.missingStackSize");
        check(
                stackSizeCtor.getDeclaringClass() == StackSize95.class,
                "Expected stackSize constructor discovered from makeNowBarCardView body");
        Object created = stackSizeCtor.newInstance(new StackHost95(), 0);
        check(Integer.valueOf(0).equals(((Sam95) created).invoke()), "Constructed stackSize lambda failed");
    }

    private static void verifyRuntimeWidgetProviderUnwrap() throws Exception {
        DirectController90 c90 = new DirectController90();
        check(
                "grid90"
                        .equals(
                                RuntimeWidgetHooks.resolveControllerMember(
                                        c90,
                                        "complicationGridUIController",
                                        "complicationGridUIControllerProvider")),
                "Direct 9.0 complicationGridUIController must resolve");
        check(
                "aod90"
                        .equals(
                                RuntimeWidgetHooks.resolveControllerProvider(
                                        c90, "aodDataManager", "aodDataManagerProvider")),
                "9.0 aodDataManager provider must unwrap");

        ProviderController95 c95 = new ProviderController95();
        check(
                "grid95"
                        .equals(
                                RuntimeWidgetHooks.resolveControllerMember(
                                        c95,
                                        "complicationGridUIController",
                                        "complicationGridUIControllerProvider")),
                "9.5 complicationGridUIControllerProvider must unwrap");
        check(
                "clock95"
                        .equals(
                                RuntimeWidgetHooks.resolveControllerMember(
                                        c95,
                                        "keyguardClockProvider",
                                        "keyguardClockProviderProvider")),
                "9.5 keyguardClockProviderProvider must unwrap");
        check(
                "aod95"
                        .equals(
                                RuntimeWidgetHooks.resolveControllerProvider(
                                        c95, "aodDataManager", "aodDataManagerProvider")),
                "9.5 aodDataManagerProvider must unwrap");
    }

    private static void verifyDozeDeadlineCallback(Path tempRoot) throws Exception {
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
                "com/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$1.java",
                "package com.android.systemui.power.view;\n"
                        + "public class ChargerNowBarView$addOnChargerAnimationEndHandler$1 {\n"
                        + "    public Object invokeSuspend(Object obj) { return null; }\n"
                        + "}\n");
        src.put(
                "com/android/systemui/power/feature/charginganimation/ChargingAnimationController$scheduleDozeDeadline$1$1.java",
                "package com.android.systemui.power.feature.charginganimation;\n"
                        + "public class ChargingAnimationController$scheduleDozeDeadline$1$1 {\n"
                        + "    public Object invokeSuspend(Object obj) { return null; }\n"
                        + "}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("doze_deadline"), src)) {
            Class<?> ui =
                    loader.loadClass(
                            "com.android.systemui.power.feature.charginganimation.ChargingAnimationUI");
            Class<?> card = loader.loadClass("com.android.systemui.power.view.ChargerNowBarView");
            String uiDesc = DexSignatures.descriptor(ui);
            DexSignatures.Call dismissCall =
                    new DexSignatures.Call(10, uiDesc, "dismissOverlay", List.of(), "V", false);
            DexSignatures.Body endBody =
                    new DexSignatures.Body(
                            "Lcom/android/systemui/power/view/ChargerNowBarView$addOnChargerAnimationEndHandler$1;",
                            "invokeSuspend",
                            List.of("Ljava/lang/Object;"),
                            "Ljava/lang/Object;",
                            0x1,
                            Map.of(
                                    "addOnChargerAnimationEndHandler, call onChargerAnimationEnd()",
                                    0),
                            List.of(),
                            List.of(),
                            List.of(dismissCall));
            DexSignatures.Body dozeBody =
                    new DexSignatures.Body(
                            "Lcom/android/systemui/power/feature/charginganimation/ChargingAnimationController$scheduleDozeDeadline$1$1;",
                            "invokeSuspend",
                            List.of("Ljava/lang/Object;"),
                            "Ljava/lang/Object;",
                            0x1,
                            Map.of("timeout", 0),
                            List.of(),
                            List.of(),
                            List.of(dismissCall));
            List<Method> withDoze =
                    NowBarExtras.resolveEndCallbacks(
                            loader,
                            ui,
                            card,
                            anchors ->
                                    anchors.contains("timeout")
                                            ? List.of(dozeBody)
                                            : List.of(endBody));
            check(withDoze.size() == 3, "Expected 3 charging end callbacks on 9.5, got " + withDoze.size());

            List<Method> withoutDoze =
                    NowBarExtras.resolveEndCallbacks(
                            loader,
                            ui,
                            card,
                            anchors -> anchors.contains("timeout") ? List.of() : List.of(endBody));
            check(withoutDoze.size() == 2, "Expected 2 charging end callbacks when doze deadline absent");
        }
    }

    private static void verifyPreviewScale95Fallback(Path tempRoot) throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put(
                "com/samsung/android/lockscreen/editor/item/domain/entity/system/PreviewScale.java",
                "package com.samsung.android.lockscreen.editor.item.domain.entity.system;\n"
                        + "public class PreviewScale {\n"
                        + "    private final float scale;\n"
                        + "    public PreviewScale(float scale) { this.scale = scale; }\n"
                        + "    public float getScale() { return scale; }\n"
                        + "}\n");
        src.put(
                "fake/ScaleRepo.java",
                "package fake;\n"
                        + "import com.samsung.android.lockscreen.editor.item.domain.entity.system.PreviewScale;\n"
                        + "public class ScaleRepo {\n"
                        + "    private final PreviewScale value = new PreviewScale(0.75f);\n"
                        + "    public Object getData() { return value; }\n"
                        + "}\n");
        src.put(
                "fake/RefreshPos14.java",
                "package fake;\n"
                        + "public class RefreshPos14 {\n"
                        + "    public final ScaleRepo i = new ScaleRepo();\n"
                        + "    public RefreshPos14(Object a1, Object a2, Object a3, Object a4, Object a5,"
                        + " Object a6, Object a7, Object a8, Object a9, Object a10, Object a11,"
                        + " Object a12, Object a13, Object a14) {}\n"
                        + "}\n");
        src.put(
                "fake/PreviewVm9.java",
                "package fake;\n"
                        + "import android.content.Context;\n"
                        + "public class PreviewVm9 {\n"
                        + "    public PreviewVm9(Object a1, Object a2, Object a3, Object a4, Object a5,"
                        + " Object a6, Object a7, Object a8, Context ctx) {}\n"
                        + "}\n");
        src.put(
                "fake/WidgetPreview95.java",
                "package fake;\n"
                        + "public class WidgetPreview95 {\n"
                        + "    public final PreviewVm9 i = new PreviewVm9(null,null,null,null,null,null,null,null,null);\n"
                        + "    public final RefreshPos14 B = new RefreshPos14(null,null,null,null,null,null,null,null,null,null,null,null,null,null);\n"
                        + "}\n");
        try (URLClassLoader loader = compileFixture(tempRoot.resolve("preview_scale_95"), src)) {
            Object preview = loader.loadClass("fake.WidgetPreview95").getConstructor().newInstance();
            float scale = OneUi9WidgetEditor.previewScale(preview);
            check(
                    Math.abs(scale - 0.75f) < 0.0001f,
                    "Expected 9.5 previewScale fallback to read RefreshPos14.i, got " + scale);
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
        check(compiler != null, "System JavaCompiler required");
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
                OneUi95ContractsTest.class.getClassLoader());
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyWallpaperSourceAlias() {
        var source = new EditorDepthSources.State();
        source.image = true;
        String gallery = "content://example/gallery/1";
        String galleryKey = DepthStorage.hash(gallery.getBytes(StandardCharsets.UTF_8));
        String copy = "file:///copy-1";
        String copyKey = DepthStorage.hash(copy.getBytes(StandardCharsets.UTF_8));
        source.observe(new WallpaperSourceBinding.Source(null, copy, true, false, gallery));
        check(source.imageKey().equals(galleryKey), "Persist the original gallery identity");
        check(source.profileKey(copyKey).equals(galleryKey), "Accept an old working-copy key");
        check(!source.observe(new WallpaperSourceBinding.Source(
                        null, "file:///copy-2", true, false, gallery)),
                "A second copy of the same source retains depth");
        check(source.observe(new WallpaperSourceBinding.Source(
                        null, "file:///copy-3", true, false, "content://example/gallery/2")),
                "A different gallery image invalidates depth");
        check(source.profileKey(galleryKey).equals(galleryKey), "Reject a stale depth alias");
        check(source.observe(new WallpaperSourceBinding.Source(
                        null, "file:///copy-3", true, false, gallery)),
                "A reused working path cannot hide a gallery image replacement");
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("oneui95-contracts-");
        try {
            verifyDex041Container(tempRoot);
            verifySupplierAndWidgetRoot();
            verifyNowBarStack95Resolution();
            verifyRuntimeWidgetProviderUnwrap();
            verifyDozeDeadlineCallback(tempRoot);
            verifyPreviewScale95Fallback(tempRoot);
            verifyWallpaperSourceAlias();
            check(!OneUi95BriefFill.hasVisibleBlur(121, 0f), "Keep the fill with invisible GenUI blur");
            check(!OneUi95BriefFill.hasVisibleBlur(null, 1f), "Keep the fill without a blur preset");
            check(OneUi95BriefFill.hasVisibleBlur(121, null), "Allow native default blur opacity");
            check(BlurAppearance.staleDozeTint(0xa6000000, 0), "Recognize the retained AOD tint");
            check(!BlurAppearance.staleDozeTint(0xa6000000, 0xa6000000), "Keep an opaque awake tint");
            check(!BlurAppearance.staleDozeTint(0xa6aa0011, 0), "Keep a colored tint");
        } finally {
            deleteTree(tempRoot);
        }
        System.out.println("One UI 9.5 contracts: " + checks + " checks passed");
    }
}
