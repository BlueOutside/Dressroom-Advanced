package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MediaContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
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
        return new DexSignatures.Body(
                owner, name, params, result, access, anchors, reads, List.of(), calls);
    }

    private static void verifyOneUi9BodyMatchers() {
        String vmCardDesc =
                "Lcom/samsung/android/app/facewidget/presentation/NowBarCardViewModel;";
        String maskDesc =
                "Lcom/samsung/android/uniform/nowbar/MediaMaskingFrameLayout;";
        String pillAnchor = "isInContentsArea x = ";

        DexSignatures.Body validExpand =
                syntheticBody(
                        "Lcom/samsung/android/app/facewidget/pages/music/FaceWidgetMusicViewModel;",
                        "z",
                        List.of(),
                        "V",
                        0,
                        Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        4, vmCardDesc, "e", List.of(), "V", false)));
        check(
                OneUi9MediaBinding.isViewModelExpandBody(validExpand),
                "Valid FaceWidgetMusicViewModel expand body rejected");

        check(
                !OneUi9MediaBinding.isViewModelExpandBody(
                        syntheticBody(
                                validExpand.owner(),
                                "z",
                                List.of("Z"),
                                "V",
                                0,
                                Map.of(),
                                List.of(),
                                validExpand.calls())),
                "Expand matcher accepted non-empty parameter list");
        check(
                !OneUi9MediaBinding.isViewModelExpandBody(
                        syntheticBody(
                                validExpand.owner(),
                                "z",
                                List.of(),
                                "Z",
                                0,
                                Map.of(),
                                List.of(),
                                validExpand.calls())),
                "Expand matcher accepted non-void return type");
        check(
                !OneUi9MediaBinding.isViewModelExpandBody(
                        syntheticBody(
                                validExpand.owner(),
                                "z",
                                List.of(),
                                "V",
                                Modifier.STATIC,
                                Map.of(),
                                List.of(),
                                validExpand.calls())),
                "Expand matcher accepted static method");
        check(
                !OneUi9MediaBinding.isViewModelExpandBody(
                        syntheticBody(
                                validExpand.owner(),
                                "z",
                                List.of(),
                                "V",
                                0,
                                Map.of(),
                                List.of(),
                                List.of(
                                        new DexSignatures.Call(
                                                4,
                                                "Ljava/lang/Object;",
                                                "toString",
                                                List.of(),
                                                "Ljava/lang/String;",
                                                false)))),
                "Expand matcher accepted unrelated call owner");

        DexSignatures.Call setProgressCall =
                new DexSignatures.Call(
                        12, maskDesc, "setAnimationProgress", List.of("F"), "V", false);
        DexSignatures.Body validAnimator =
                syntheticBody(
                        "Laod/k20;",
                        "onAnimationUpdate",
                        List.of("Landroid/animation/ValueAnimator;"),
                        "V",
                        0,
                        Map.of(),
                        List.of(),
                        List.of(setProgressCall));
        check(
                OneUi9MediaBinding.isMaskAnimatorUpdateListenerBody(validAnimator),
                "Valid mask AnimatorUpdateListener rejected");
        check(
                !OneUi9MediaBinding.isMaskAnimatorUpdateListenerBody(
                        syntheticBody(
                                "Laod/q10;",
                                "onAnimationUpdate",
                                List.of("Landroid/animation/ValueAnimator;"),
                                "V",
                                0,
                                Map.of(),
                                List.of(),
                                List.of(
                                        new DexSignatures.Call(
                                                12,
                                                maskDesc,
                                                "invalidate",
                                                List.of(),
                                                "V",
                                                false)))),
                "Mask animator matcher accepted sibling listener without setAnimationProgress");
        check(
                !OneUi9MediaBinding.isMaskAnimatorUpdateListenerBody(
                        syntheticBody(
                                "Laod/q10;",
                                "onAnimationUpdate",
                                List.of("Landroid/animation/ValueAnimator;"),
                                "V",
                                Modifier.STATIC,
                                Map.of(),
                                List.of(),
                                List.of(setProgressCall))),
                "Mask animator matcher accepted static method");
        check(
                !OneUi9MediaBinding.isMaskAnimatorUpdateListenerBody(
                        syntheticBody(
                                "Laod/q10;",
                                "onAnimationUpdate",
                                List.of("Landroid/animation/ValueAnimator;"),
                                "V",
                                0,
                                Map.of(),
                                List.of(),
                                List.of(
                                        new DexSignatures.Call(
                                                12,
                                                maskDesc,
                                                "setAnimationProgress",
                                                List.of("F"),
                                                "V",
                                                true)))),
                "Mask animator matcher accepted static call");

        DexSignatures.Call rectContains =
                new DexSignatures.Call(
                        20,
                        "Landroid/graphics/Rect;",
                        "contains",
                        List.of("I", "I"),
                        "Z",
                        false);
        DexSignatures.Body validPillBounds =
                syntheticBody(
                        "Laod/helper;",
                        "a",
                        List.of("Landroid/view/View;", "I", "I"),
                        "Z",
                        Modifier.STATIC,
                        Map.of(pillAnchor, 4),
                        List.of(),
                        List.of(rectContains));
        check(
                OneUi9MediaBinding.isPillBoundsBody(validPillBounds),
                "Valid pill bounds helper rejected");
        check(
                !OneUi9MediaBinding.isPillBoundsBody(
                        syntheticBody(
                                "Laod/helper;",
                                "a",
                                List.of("Landroid/view/View;", "I", "I"),
                                "V",
                                Modifier.STATIC,
                                Map.of(pillAnchor, 4),
                                List.of(),
                                List.of(rectContains))),
                "Pill bounds matcher accepted void return");
    }

    private static void verifyOutlineMemberResolution() throws NoSuchFieldException {
        String textDesc = "Lcom/samsung/android/uniform/widget/clock/NonePaddingTextView;";
        String superDesc = "Landroid/widget/TextView;";

        DexSignatures.Body uniqueIntRead =
                syntheticBody(
                        textDesc,
                        "onDraw",
                        List.of("Landroid/graphics/Canvas;"),
                        "V",
                        0,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        2, new DexSignatures.Member(superDesc, "other", "I")),
                                new DexSignatures.Read(
                                        6, new DexSignatures.Member(textDesc, "paddingFlag", "Z")),
                                new DexSignatures.Read(
                                        10, new DexSignatures.Member(textDesc, "P", "I")),
                                new DexSignatures.Read(
                                        14, new DexSignatures.Member(textDesc, "P", "I"))),
                        List.of());
        DexSignatures.Member resolved = MediaClockContrast.outlineMember(uniqueIntRead, textDesc);
        check(
                resolved != null && "P".equals(resolved.name()) && "I".equals(resolved.type()),
                "Unique outline color int read failed");

        DexSignatures.Body absentOutline =
                syntheticBody(
                        textDesc,
                        "onDraw",
                        List.of("Landroid/graphics/Canvas;"),
                        "V",
                        0,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        2, new DexSignatures.Member(textDesc, "k", "F")),
                                new DexSignatures.Read(
                                        4, new DexSignatures.Member(textDesc, "j", "Z"))),
                        List.of());
        check(
                MediaClockContrast.outlineMember(absentOutline, textDesc) == null,
                "Missing outline color field (8.5-8932/8928 style) must return null");

        DexSignatures.Body ambiguousOutline =
                syntheticBody(
                        textDesc,
                        "onDraw",
                        List.of("Landroid/graphics/Canvas;"),
                        "V",
                        0,
                        Map.of(),
                        List.of(
                                new DexSignatures.Read(
                                        2, new DexSignatures.Member(textDesc, "K", "I")),
                                new DexSignatures.Read(
                                        6, new DexSignatures.Member(textDesc, "L", "I"))),
                        List.of());
        boolean threw = false;
        try {
            MediaClockContrast.outlineMember(ambiguousOutline, textDesc);
        } catch (NoSuchFieldException expected) {
            threw = true;
        }
        check(threw, "Ambiguous outline color int reads must throw NoSuchFieldException");
    }

    static class FakeResources extends Resources {
        Map<String, Integer> ids;
        List<String> queriedPackages;

        @SuppressWarnings("deprecation")
        FakeResources() {
            super(null, null, null);
        }

        @Override
        public int getIdentifier(String name, String defType, String defPackage) {
            queriedPackages.add(defPackage);
            check(
                    "media_album_masking".equals(name) && "raw".equals(defType),
                    "Unexpected resource lookup: " + defType + "/" + name);
            Integer value = ids.get(defPackage);
            return value == null ? 0 : value;
        }
    }

    static class FakeContext extends ContextWrapper {
        String pkg;
        Resources res;

        FakeContext() {
            super(null);
        }

        @Override
        public String getPackageName() {
            return pkg;
        }

        @Override
        public Resources getResources() {
            return res;
        }
    }

    private static FakeContext fakeContext(
            String pkg, Map<String, Integer> ids, List<String> queriedOut) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        var singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        var allocate = unsafeType.getMethod("allocateInstance", Class.class);

        FakeResources resources = (FakeResources) allocate.invoke(unsafe, FakeResources.class);
        resources.ids = ids;
        resources.queriedPackages = queriedOut;

        FakeContext context = (FakeContext) allocate.invoke(unsafe, FakeContext.class);
        context.pkg = pkg;
        context.res = resources;
        return context;
    }

    private static void verifyResolveMaskingResource() throws Exception {
        check(NativeArtworkReveal.resolveMaskingResource(null) == 0, "Null context must yield 0");

        List<String> queried = new ArrayList<>();
        FakeContext ownHit =
                fakeContext(
                        "com.example.host",
                        Map.of(
                                "com.example.host", 101,
                                "com.samsung.android.app.aodservice", 202),
                        queried);
        check(
                NativeArtworkReveal.resolveMaskingResource(ownHit) == 101,
                "Host context resource lookup failed");
        check(
                queried.equals(List.of("com.example.host")),
                "Host lookup should short-circuit before fallback packages");

        queried.clear();
        FakeContext aodFallback =
                fakeContext(
                        "com.samsung.android.app.clockstyle",
                        Map.of("com.samsung.android.app.aodservice", 303),
                        queried);
        check(
                NativeArtworkReveal.resolveMaskingResource(aodFallback) == 303,
                "AOD fallback resource lookup failed");
        check(
                queried.equals(
                        List.of(
                                "com.samsung.android.app.clockstyle",
                                "com.samsung.android.app.aodservice")),
                "Unexpected query order for AOD fallback: " + queried);

        queried.clear();
        FakeContext clockPackFallback =
                fakeContext(
                        "com.example.other",
                        Map.of("com.samsung.android.app.clockpack", 404),
                        queried);
        check(
                NativeArtworkReveal.resolveMaskingResource(clockPackFallback) == 404,
                "ClockPack standalone resource lookup failed");
        check(
                queried.equals(
                        List.of(
                                "com.example.other",
                                "com.samsung.android.app.aodservice",
                                "com.samsung.android.app.clockpack")),
                "Unexpected query order for ClockPack fallback: " + queried);

        queried.clear();
        FakeContext duplicateClockPack =
                fakeContext("com.samsung.android.app.clockpack", Map.of(), queried);
        check(
                NativeArtworkReveal.resolveMaskingResource(duplicateClockPack) == 0,
                "Missing resource must return 0");
        check(
                queried.equals(
                        List.of(
                                "com.samsung.android.app.clockpack",
                                "com.samsung.android.app.aodservice")),
                "Duplicate ownPackage clockpack should not be queried twice: " + queried);
    }

    private static final class MinimalDexBuilder {
        final List<String> strings = new ArrayList<>();
        final List<Integer> types = new ArrayList<>();
        final List<int[]> protos = new ArrayList<>();
        final List<int[]> fields = new ArrayList<>();
        final List<int[]> methods = new ArrayList<>();
        final List<int[]> classDefs = new ArrayList<>();
        final ByteArrayOutputStream extraData = new ByteArrayOutputStream();
        final int dataBasePlaceholder = 0x10000;

        int internString(String s) {
            int idx = strings.indexOf(s);
            if (idx >= 0) return idx;
            strings.add(s);
            return strings.size() - 1;
        }

        int internType(String desc) {
            int strIdx = internString(desc);
            for (int i = 0; i < types.size(); i++) {
                if (types.get(i) == strIdx) return i;
            }
            types.add(strIdx);
            return types.size() - 1;
        }

        int internProto(String resultDesc, List<String> paramDescs) {
            StringBuilder shorty = new StringBuilder();
            shorty.append(shortyChar(resultDesc));
            for (String p : paramDescs) shorty.append(shortyChar(p));
            int shortyIdx = internString(shorty.toString());
            int retTypeIdx = internType(resultDesc);
            int[] paramTypes = new int[paramDescs.size()];
            for (int i = 0; i < paramDescs.size(); i++) {
                paramTypes[i] = internType(paramDescs.get(i));
            }
            for (int i = 0; i < protos.size(); i++) {
                int[] p = protos.get(i);
                if (p[0] == shortyIdx
                        && p[1] == retTypeIdx
                        && p.length - 2 == paramTypes.length) {
                    boolean eq = true;
                    for (int j = 0; j < paramTypes.length; j++) {
                        if (p[2 + j] != paramTypes[j]) {
                            eq = false;
                            break;
                        }
                    }
                    if (eq) return i;
                }
            }
            int[] entry = new int[2 + paramTypes.length];
            entry[0] = shortyIdx;
            entry[1] = retTypeIdx;
            System.arraycopy(paramTypes, 0, entry, 2, paramTypes.length);
            protos.add(entry);
            return protos.size() - 1;
        }

        int internField(String ownerDesc, String name, String typeDesc) {
            int ownerIdx = internType(ownerDesc);
            int typeIdx = internType(typeDesc);
            int nameIdx = internString(name);
            for (int i = 0; i < fields.size(); i++) {
                int[] f = fields.get(i);
                if (f[0] == ownerIdx && f[1] == typeIdx && f[2] == nameIdx) return i;
            }
            fields.add(new int[] {ownerIdx, typeIdx, nameIdx});
            return fields.size() - 1;
        }

        int internMethod(
                String ownerDesc, String name, String resultDesc, List<String> paramDescs) {
            int ownerIdx = internType(ownerDesc);
            int protoIdx = internProto(resultDesc, paramDescs);
            int nameIdx = internString(name);
            for (int i = 0; i < methods.size(); i++) {
                int[] m = methods.get(i);
                if (m[0] == ownerIdx && m[1] == protoIdx && m[2] == nameIdx) return i;
            }
            methods.add(new int[] {ownerIdx, protoIdx, nameIdx});
            return methods.size() - 1;
        }

        void addClassWithMethods(
                String classDesc,
                String superDesc,
                List<int[]> encodedDirectMethods,
                List<int[]> encodedVirtualMethods) {
            int classIdx = internType(classDesc);
            int superIdx = internType(superDesc);
            align4(extraData);
            int classDataOffsetMarker = dataBasePlaceholder + extraData.size();
            writeUleb(extraData, 0); // static_fields_size
            writeUleb(extraData, 0); // instance_fields_size
            writeUleb(extraData, encodedDirectMethods.size());
            writeUleb(extraData, encodedVirtualMethods.size());
            writeEncodedMethods(encodedDirectMethods);
            writeEncodedMethods(encodedVirtualMethods);
            classDefs.add(new int[] {classIdx, Modifier.PUBLIC, superIdx, classDataOffsetMarker});
        }

        int writeCodeItem(int registers, int ins, int[] insns) {
            align4(extraData);
            int offsetMarker = dataBasePlaceholder + extraData.size();
            writeU16(extraData, registers);
            writeU16(extraData, ins);
            writeU16(extraData, 6); // outs_size
            writeU16(extraData, 0); // tries_size
            writeU32(extraData, 0); // debug_info_off
            writeU32(extraData, insns.length);
            for (int insn : insns) writeU16(extraData, insn);
            return offsetMarker;
        }

        private void writeEncodedMethods(List<int[]> list) {
            int prevIdx = 0;
            for (int[] m : list) {
                writeUleb(extraData, m[0] - prevIdx);
                prevIdx = m[0];
                writeUleb(extraData, m[1]); // access_flags
                writeUleb(extraData, m[2]); // code_off
            }
        }

        byte[] build() {
            int stringIdsSize = strings.size();
            int typeIdsSize = types.size();
            int protoIdsSize = protos.size();
            int fieldIdsSize = fields.size();
            int methodIdsSize = methods.size();
            int classDefsSize = classDefs.size();

            int off = 112;
            int stringIdsOff = off;
            off += stringIdsSize * 4;
            int typeIdsOff = off;
            off += typeIdsSize * 4;
            int protoIdsOff = off;
            off += protoIdsSize * 12;
            int fieldIdsOff = off;
            off += fieldIdsSize * 8;
            int methodIdsOff = off;
            off += methodIdsSize * 8;
            int classDefsOff = off;
            off += classDefsSize * 32;

            ByteArrayOutputStream paramLists = new ByteArrayOutputStream();
            int[] protoParamOffsets = new int[protoIdsSize];
            for (int i = 0; i < protoIdsSize; i++) {
                int[] p = protos.get(i);
                if (p.length == 2) {
                    protoParamOffsets[i] = 0;
                } else {
                    align4(paramLists);
                    protoParamOffsets[i] = off + paramLists.size();
                    writeU32(paramLists, p.length - 2);
                    for (int j = 2; j < p.length; j++) writeU16(paramLists, p[j]);
                }
            }
            off += paramLists.size();
            int stringDataOff = off;
            ByteArrayOutputStream stringData = new ByteArrayOutputStream();
            int[] stringOffsets = new int[stringIdsSize];
            for (int i = 0; i < stringIdsSize; i++) {
                stringOffsets[i] = stringDataOff + stringData.size();
                byte[] utf8 = strings.get(i).getBytes(StandardCharsets.UTF_8);
                writeUleb(stringData, utf8.length);
                stringData.writeBytes(utf8);
                stringData.write(0);
            }
            off += stringData.size();
            while ((off & 3) != 0) {
                stringData.write(0);
                off++;
            }
            int dataBase = off;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] header = new byte[112];
            header[0] = 'd';
            header[1] = 'e';
            header[2] = 'x';
            header[3] = '\n';
            header[4] = '0';
            header[5] = '3';
            header[6] = '5';
            header[7] = 0;
            putU32(header, 32, dataBase + extraData.size());
            putU32(header, 36, 112);
            putU32(header, 40, 0x12345678);
            putU32(header, 56, stringIdsSize);
            putU32(header, 60, stringIdsOff);
            putU32(header, 64, typeIdsSize);
            putU32(header, 68, typeIdsOff);
            putU32(header, 72, protoIdsSize);
            putU32(header, 76, protoIdsOff);
            putU32(header, 80, fieldIdsSize);
            putU32(header, 84, fieldIdsOff);
            putU32(header, 88, methodIdsSize);
            putU32(header, 92, methodIdsOff);
            putU32(header, 96, classDefsSize);
            putU32(header, 100, classDefsOff);
            out.writeBytes(header);

            for (int sOff : stringOffsets) writeU32(out, sOff);
            for (int strIdx : types) writeU32(out, strIdx);
            for (int i = 0; i < protoIdsSize; i++) {
                int[] p = protos.get(i);
                writeU32(out, p[0]);
                writeU32(out, p[1]);
                writeU32(out, protoParamOffsets[i]);
            }
            for (int[] f : fields) {
                writeU16(out, f[0]);
                writeU16(out, f[1]);
                writeU32(out, f[2]);
            }
            for (int[] m : methods) {
                writeU16(out, m[0]);
                writeU16(out, m[1]);
                writeU32(out, m[2]);
            }
            for (int[] c : classDefs) {
                writeU32(out, c[0]);
                writeU32(out, c[1]);
                writeU32(out, c[2]);
                writeU32(out, 0);
                writeU32(out, -1);
                writeU32(out, 0);
                writeU32(out, c[3] - dataBasePlaceholder + dataBase);
                writeU32(out, 0);
            }
            out.writeBytes(paramLists.toByteArray());
            out.writeBytes(stringData.toByteArray());
            byte[] rawExtra = extraData.toByteArray();
            // Relocate code_off inside encoded_methods if needed: markers in encoded_methods are
            // already resolved relative to dataBase via fixup below.
            byte[] full = out.toByteArray();
            byte[] combined = new byte[full.length + rawExtra.length];
            System.arraycopy(full, 0, combined, 0, full.length);
            System.arraycopy(rawExtra, 0, combined, full.length, rawExtra.length);
            return combined;
        }

        int relocateMarker(int marker, int dataBase) {
            return marker - dataBasePlaceholder + dataBase;
        }

        private static char shortyChar(String desc) {
            return desc.startsWith("L") || desc.startsWith("[") ? 'L' : desc.charAt(0);
        }

        private static void align4(ByteArrayOutputStream s) {
            while ((s.size() & 3) != 0) s.write(0);
        }

        private static void writeU16(ByteArrayOutputStream s, int v) {
            s.write(v & 255);
            s.write((v >>> 8) & 255);
        }

        private static void writeU32(ByteArrayOutputStream s, int v) {
            s.write(v & 255);
            s.write((v >>> 8) & 255);
            s.write((v >>> 16) & 255);
            s.write((v >>> 24) & 255);
        }

        private static void putU32(byte[] buf, int off, int v) {
            buf[off] = (byte) (v & 255);
            buf[off + 1] = (byte) ((v >>> 8) & 255);
            buf[off + 2] = (byte) ((v >>> 16) & 255);
            buf[off + 3] = (byte) ((v >>> 24) & 255);
        }

        private static void writeUleb(ByteArrayOutputStream s, int v) {
            while ((v & ~0x7f) != 0) {
                s.write((v & 0x7f) | 0x80);
                v >>>= 7;
            }
            s.write(v & 0x7f);
        }
    }

    private static void verifyDexStaticReadWritesAndConstructorIntArg() throws Exception {
        String rIdDesc = "Ltest/R$id;";
        String holderDesc = "Ltest/Holder;";
        String viewDesc = "Landroid/view/View;";
        String vgDesc = "Landroid/view/ViewGroup;";
        String dimenDesc = "Ltest/Dimen;";
        String cardDesc = "Ltest/Card;";
        String lambdaDesc = "Ltest/ExpandLambda;";
        String objDesc = "Ljava/lang/Object;";

        // Build clean DEX using two passes so code_item offsets inside encoded_method use real dataBase.
        byte[] dexBytes =
                buildFixtureDex(
                        rIdDesc,
                        holderDesc,
                        viewDesc,
                        vgDesc,
                        dimenDesc,
                        cardDesc,
                        lambdaDesc,
                        objDesc);

        File tmp = Files.createTempFile("media-contracts-", ".dex").toFile();
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), dexBytes);

        Map<String, DexSignatures.Member> idFields =
                DexSignatures.readStaticReadWrites(
                        tmp, holderDesc, List.of(viewDesc), holderDesc);
        check(
                idFields != null && idFields.size() == 2,
                "Expected 2 static R.id -> View field mappings, got " + idFields);
        check(
                "titleArtist".equals(idFields.get("facewidget_music_page_title_artist_container").name()),
                "Failed to resolve sget -> findViewById -> check-cast -> iput-object");
        check(
                "bg".equals(idFields.get("facewidget_album_art_background").name()),
                "Failed to resolve sget -> findViewById -> iput-object");
        check(
                !idFields.containsKey("stale_id"),
                "Invalidated register (add-int/lit8) leaked into R.id mapping");

        Integer five =
                DexSignatures.readConstructorIntArgument(
                        tmp,
                        cardDesc,
                        "onExpandFive",
                        List.of("Z"),
                        "V",
                        lambdaDesc,
                        5,
                        4);
        check(
                five != null && five == 5,
                "Expected invoke-direct/range argIndex=4 to equal 5, got " + five);

        Integer three =
                DexSignatures.readConstructorIntArgument(
                        tmp,
                        cardDesc,
                        "onExpandThree",
                        List.of("Z"),
                        "V",
                        lambdaDesc,
                        5,
                        4);
        check(
                three != null && three == 3,
                "Expected ClockPack-style const/16 argIndex=4 to equal 3, got " + three);

        Integer missing =
                DexSignatures.readConstructorIntArgument(
                        tmp,
                        cardDesc,
                        "onExpandDynamic",
                        List.of("I"),
                        "V",
                        lambdaDesc,
                        5,
                        4);
        check(
                missing == null,
                "Dynamic non-constant int parameter should return null, got " + missing);
    }

    private static byte[] buildFixtureDex(
            String rIdDesc,
            String holderDesc,
            String viewDesc,
            String vgDesc,
            String dimenDesc,
            String cardDesc,
            String lambdaDesc,
            String objDesc) {
        int dataBase = 0;
        for (int pass = 0; pass < 4; pass++) {
            MinimalDexBuilder b = new MinimalDexBuilder();
            if (pass == 0) dataBase = b.dataBasePlaceholder;
            int fTitleId = b.internField(rIdDesc, "facewidget_music_page_title_artist_container", "I");
            int fBgId = b.internField(rIdDesc, "facewidget_album_art_background", "I");
            int fStaleId = b.internField(rIdDesc, "stale_id", "I");

            int fTitleField = b.internField(holderDesc, "titleArtist", viewDesc);
            int fBgField = b.internField(holderDesc, "bg", viewDesc);
            int fIgnoredField = b.internField(holderDesc, "ignored", viewDesc);

            int mHolderCtor = b.internMethod(holderDesc, "<init>", "V", List.of(viewDesc));
            int mFind = b.internMethod(viewDesc, "findViewById", viewDesc, List.of("I"));
            int mDimen = b.internMethod(dimenDesc, "getDimen", "I", List.of());

            int mCardFive = b.internMethod(cardDesc, "onExpandFive", "V", List.of("Z"));
            int mCardThree = b.internMethod(cardDesc, "onExpandThree", "V", List.of("Z"));
            int mCardDynamic = b.internMethod(cardDesc, "onExpandDynamic", "V", List.of("I"));
            int mLambdaCtor =
                    b.internMethod(
                            lambdaDesc,
                            "<init>",
                            "V",
                            List.of(objDesc, objDesc, objDesc, objDesc, "I"));

            int tVg = b.internType(vgDesc);

            int cHolder =
                    b.writeCodeItem(
                            4,
                            2,
                            new int[] {
                                // v0 = R.id.title_artist; v1 = p1.findViewById(v0)
                                0x0060, fTitleId,
                                0x206e, mFind, 0x0003,
                                0x010c,
                                // interleaved getDimen(p1) -> v0 (clobbers v0, leaves v1 intact)
                                0x106e, mDimen, 0x0003,
                                0x000a,
                                // check-cast v1, ViewGroup; p0.titleArtist = v1
                                0x011f, tVg,
                                0x215b, fTitleField,
                                // second field: bg
                                0x0060, fBgId,
                                0x206e, mFind, 0x0003,
                                0x010c,
                                0x215b, fBgField,
                                // invalidated register: add-int/lit8 v0, v0, 1; const/4 v1, 0; p0.ignored = v1
                                0x0060, fStaleId,
                                0x00d8, 0x0100,
                                0x0112,
                                0x215b, fIgnoredField,
                                0x000e
                            });
            int cFive =
                    b.writeCodeItem(
                            8,
                            2,
                            new int[] {
                                0x0012, 0x0112, 0x0212, 0x0312, 0x0412,
                                0x5512, // const/4 v5, 5
                                0x0213, 99, // unrelated v2 = 99
                                0x0676, mLambdaCtor, 0x0000, // invoke-direct/range v0..v5
                                0x000e
                            });
            int cThree =
                    b.writeCodeItem(
                            8,
                            2,
                            new int[] {
                                0x0012, 0x0112, 0x0212, 0x0312, 0x0412,
                                0x0513, 3, // const/16 v5, 3
                                0x0676, mLambdaCtor, 0x0000,
                                0x000e
                            });
            int cDynamic =
                    b.writeCodeItem(
                            8,
                            2,
                            new int[] {
                                // p1 is v7; const/4 v5, 4 then overwritten by move v5, v7
                                0x0012, 0x0112, 0x0212, 0x0312, 0x0412,
                                0x4512,
                                0x7501, // move v5, v7 (invalidates v5 constant)
                                0x0676, mLambdaCtor, 0x0000,
                                0x000e
                            });

            int shift = dataBase - b.dataBasePlaceholder;
            b.addClassWithMethods(
                    holderDesc,
                    objDesc,
                    List.of(new int[] {mHolderCtor, Modifier.PUBLIC, cHolder + shift}),
                    List.of());
            // Ensure Card virtual methods are sorted by ascending method_idx
            List<int[]> cardMethods = new ArrayList<>();
            cardMethods.add(new int[] {mCardFive, Modifier.PUBLIC, cFive + shift});
            cardMethods.add(new int[] {mCardThree, Modifier.PUBLIC, cThree + shift});
            cardMethods.add(new int[] {mCardDynamic, Modifier.PUBLIC, cDynamic + shift});
            cardMethods.sort((a, d) -> Integer.compare(a[0], d[0]));
            b.addClassWithMethods(cardDesc, objDesc, List.of(), cardMethods);

            byte[] built = b.build();
            int actualDataBase = built.length - b.extraData.size();
            if (pass > 0 && actualDataBase == dataBase) {
                return built;
            }
            dataBase = actualDataBase;
        }
        throw new IllegalStateException("Unreachable");
    }

    private static void verifyClockAuraContrastPolicy() {
        MediaClockContrast.AuraSpec pitchBlack =
                MediaClockContrast.computeAura(new ArtworkSamples.Stats(0d, 0d, 0d));
        check(pitchBlack.textColor() == 0xfffff9f2, "Pitch-black cover must use light text");
        check(
                pitchBlack.blurAlpha() == 0f
                        && pitchBlack.scrimAlpha() == 0f
                        && pitchBlack.conflict() == 0f,
                "Pitch-black cover must keep zero aura/scrim haze: " + pitchBlack);

        MediaClockContrast.AuraSpec brightWhite =
                MediaClockContrast.computeAura(new ArtworkSamples.Stats(1d, 1d, 1d));
        check(brightWhite.textColor() == 0xff17191c, "Bright-white cover must use dark text");
        check(
                brightWhite.blurAlpha() == 0f
                        && brightWhite.scrimAlpha() == 0f
                        && brightWhite.conflict() == 0f,
                "Bright-white cover must keep zero aura/scrim haze: " + brightWhite);

        MediaClockContrast.AuraSpec darkHotspot =
                MediaClockContrast.computeAura(new ArtworkSamples.Stats(.10d, 0d, 1d));
        check(darkHotspot.textColor() == 0xfffff9f2, "Dark cover with hotspot must use light text");
        check(darkHotspot.scrimColor() == 0xff000000, "Light text cushion must darken scrim");
        check(
                darkHotspot.conflict() > .95f
                        && darkHotspot.scrimAlpha() > .35f
                        && darkHotspot.blurAlpha() > .80f,
                "Dark cover with white hotspot must enable blurred aura and dark scrim: "
                        + darkHotspot);

        MediaClockContrast.AuraSpec split =
                MediaClockContrast.computeAura(new ArtworkSamples.Stats(.50d, 0d, 1d));
        check(split.textColor() == 0xff17191c, "50/50 split cover must use dark text");
        check(split.scrimColor() == 0xfffff9f2, "Dark text cushion must lighten scrim");
        check(
                split.conflict() == 1f && split.scrimAlpha() > .40f && split.blurAlpha() == 1f,
                "50/50 black-and-white split cover must maximize blur aura and scrim: " + split);

        MediaClockContrast.AuraSpec subtleDark =
                MediaClockContrast.computeAura(new ArtworkSamples.Stats(.015d, .005d, .08d));
        check(subtleDark.textColor() == 0xfffff9f2, "Subtle dark cover must use light text");
        check(
                subtleDark.blurAlpha() > .20f
                        && subtleDark.scrimAlpha() < .06f
                        && subtleDark.scrimAlpha() < darkHotspot.scrimAlpha(),
                "Subtle dark texture should prefer blurred aura over heavy scrim: " + subtleDark);
    }

    public static void main(String[] args) throws Exception {
        verifyOneUi9BodyMatchers();
        verifyOutlineMemberResolution();
        verifyResolveMaskingResource();
        verifyDexStaticReadWritesAndConstructorIntArg();
        verifyClockAuraContrastPolicy();
        System.out.println("Media contracts: " + checks + " checks passed");
    }
}
