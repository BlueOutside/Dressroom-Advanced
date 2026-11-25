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

    private static void verifyClockGlassContrastPolicy() {
        check(!MediaFullscreenExtras.keepClockGlass(.49f, false, false, false),
                "Opening must retain native clock glass before the player reaches it");
        check(MediaFullscreenExtras.keepClockGlass(.5f, false, false, false),
                "Player glass must start at its opening boundary");
        for (float progress : new float[] {1f, .8f, .49f, .08f, .006f}) {
            check(MediaFullscreenExtras.keepClockGlass(progress, true, false, true),
                    "Closing removed player glass before native return: " + progress);
            check(MediaFullscreenExtras.keepClockGlass(progress, false, true, true),
                    "Thumbnail return removed player glass before completion: " + progress);
        }
        check(!MediaFullscreenExtras.keepClockGlass(.8f, true, false, false),
                "Closing must not resurrect a player clock that never became active");
        check(!MediaFullscreenExtras.keepClockGlass(0f, true, false, true),
                "Completed return retained player clock overrides");
        check(!MediaFullscreenExtras.keepClockGlass(Float.NaN, true, false, true),
                "Invalid progress retained player glass");
        var black = MediaClockContrast.computePalette(new ArtworkSamples.Stats(0d, 0d, 0d));
        var white = MediaClockContrast.computePalette(new ArtworkSamples.Stats(1d, 1d, 1d));
        var mixed = MediaClockContrast.computePalette(new ArtworkSamples.Stats(.50d, 0d, 1d));
        check(black.textColor() == 0xfffff9f2, "Black cover should use light glass");
        check(white.textColor() == 0xff17191c, "White cover should use dark glass");
        check(mixed.textColor() == white.textColor() && mixed.conflict() > white.conflict(),
                "Mixed cover should strengthen the tint");
        check(MediaClockContrast.videoInk(.19, black.textColor()) == black.textColor()
                        && MediaClockContrast.videoInk(.21, white.textColor()) == white.textColor(),
                "Small video exposure changes should keep the current tint");
        check(MediaClockContrast.videoInk(.4, black.textColor()) == white.textColor()
                        && MediaClockContrast.videoInk(.06, white.textColor()) == black.textColor(),
                "Strong exposure changes should swap video tint");
    }

    private static void verifyArtworkUrlUpgrades() {
        String apple512 =
                "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/c5/df/dd/"
                        + "c5dfdd9c-24f2-de01-246c-fcc5e028f705/12UMGIM53864.rgb.jpg/512x512bb.jpg";
        check(
                MediaArtworkUncap.upgradeArtworkUrl(apple512, 2048)
                        .endsWith("/12UMGIM53864.rgb.jpg/2000x2000bb.jpg"),
                "Apple Music mzstatic URL should upgrade 512x512bb.jpg to 2000x2000bb.jpg");
        String yt544 = "https://lh3.googleusercontent.com/abc123=w544-h544-l90-rj";
        check(
                MediaArtworkUncap.upgradeArtworkUrl(yt544, 2048)
                        .equals("https://lh3.googleusercontent.com/abc123=w1600-h1600-l90-rj"),
                "YouTube Music googleusercontent URL should upgrade w544-h544 to w1600-h1600");
        String spotify300 = "https://i.scdn.co/image/ab67616d00001e02deadbeef";
        check(
                MediaArtworkUncap.upgradeArtworkUrl(spotify300, 2048)
                        .equals("https://i.scdn.co/image/ab67616d0000b273deadbeef"),
                "Spotify scdn URL should upgrade 300px variant to 640px variant");
        String deezer1000 =
                "https://cdn-images.dzcdn.net/images/cover/638b000cf958d58d4a36969380f14437/1000x1000-000000-80-0-0.jpg";
        check(
                MediaArtworkUncap.upgradeArtworkUrl(deezer1000, 2048)
                        .endsWith("/1400x1400-000000-80-0-0.jpg"),
                "Deezer dzcdn URL should upgrade 1000x1000 to 1400x1400");
        check(
                MediaArtworkUncap.cleanSearchQuery(
                                "ZZT, Tiga & Zombie Nation", "Rock the Peace (Bonus)")
                        .equals("ZZT Rock the Peace"),
                "cleanSearchQuery should isolate primary artist and strip parentheticals");
        check(
                MediaArtworkUncap.matchesTrack(
                        "ZZT, Tiga & Zombie Nation",
                        "Rock the Peace",
                        "Partys Over Earth (Bonus Tracks)",
                        "ZZT",
                        "Rock the Peace",
                        "Partys Over Earth"),
                "matchesTrack should match multi-artist credit against primary artist");
        check(
                MediaArtworkUncap.matchesTrack(
                        "Angèle, Caroline Polachek & SebastiAn",
                        "Love Triangle",
                        "Instinct",
                        "Angèle",
                        "Love Triangle",
                        "Instinct - Single"),
                "matchesTrack should normalize accents and match primary artist");
        check(
                !MediaArtworkUncap.matchesTrack(
                        "SebastiAn",
                        "Kindercut",
                        "Total",
                        "Plácido Domingo",
                        "Total",
                        "The Best of Plácido Domingo"),
                "matchesTrack must reject unrelated artist even when album word overlaps");
    }

    private static void verifyBlurCacheSizingAndAmbientClosePolicy() {
        int[] portrait = ArtworkBlurFilter.cacheSize(1440, 3120);
        check(
                portrait[0] == 118 && portrait[1] == 256,
                "Expected 1440x3120 blur cache 118x256, got " + portrait[0] + "x" + portrait[1]);
        float scale = ArtworkBlurFilter.cacheScale(1440, 3120, portrait[0], portrait[1]);
        check(
                scale > 0.08f && scale < 0.09f,
                "Expected proportional blur cache scale near 256/3120, got " + scale);

        int[] square = ArtworkBlurFilter.cacheSize(1080, 1080);
        check(
                square[0] == 256 && square[1] == 256,
                "Expected square blur cache 256x256, got " + square[0] + "x" + square[1]);

        int[] small = ArtworkBlurFilter.cacheSize(200, 100);
        check(
                small[0] == 200 && small[1] == 100,
                "Small viewport below 256px should keep exact dimensions");

        int[] empty = ArtworkBlurFilter.cacheSize(0, 100);
        check(empty[0] == 0 && empty[1] == 0, "Zero-sized viewport must yield 0x0 blur cache");

        check(
                MediaFullscreenExtras.shouldCloseAmbient(false, true, false, false, false),
                "Transition from expanded to collapsed must trigger ambient close");
        check(
                MediaFullscreenExtras.shouldCloseAmbient(false, false, true, false, false),
                "Collapsed update while ambient cover is visible must close ambient cover");
        check(
                MediaFullscreenExtras.shouldCloseAmbient(false, false, false, true, true),
                "Collapsed update while backdrop is showing in ambient handoff must close");
        check(
                !MediaFullscreenExtras.shouldCloseAmbient(true, true, true, true, true),
                "Expanded state must never close ambient cover");
        check(
                !MediaFullscreenExtras.shouldCloseAmbient(false, false, false, false, false),
                "Idle collapsed state without visible ambient cover should be a no-op");

        float[] inactive = ArtworkBlurFilter.topBandRange(100f, 100f, 3f, 2000f);
        check(
                inactive[0] == 100f && inactive[1] == 100f,
                "Top blur band must be inactive when clock does not overlap cover");
        float[] emptyMax = ArtworkBlurFilter.topBandRange(0f, 500f, 3f, 0f);
        check(
                emptyMax[0] == 0f && emptyMax[1] == 0f,
                "Top blur band must be inactive for empty cover height");
        float[] badDensity = ArtworkBlurFilter.topBandRange(0f, 500f, 0f, 2000f);
        check(
                badDensity[0] == 0f && badDensity[1] == 0f,
                "Top blur band must be inactive for non-positive density");

        float[] realistic = ArtworkBlurFilter.topBandRange(0f, 850f, 3f, 3120f);
        check(
                Math.abs(realistic[0] - 742f) < 0.01f && Math.abs(realistic[1] - 1030f) < 0.01f,
                "Expected realistic top blur band [742, 1030], got ["
                        + realistic[0]
                        + ", "
                        + realistic[1]
                        + "]");

        float[] clamped = ArtworkBlurFilter.topBandRange(0f, 1000f, 3f, 1020f);
        check(
                clamped[0] < clamped[1] && clamped[1] == 1020f,
                "Expected clamped top blur band to stay inside maxBottom");

        check(
                MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, false, false, 1f, false, false),
                "Settled awake song change should crossfade");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, false, false, 1f, false, true),
                "Active native reveal must suppress fallback song crossfade");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, false, false, 1f, true, false),
                "First awake frame exiting ambient mode must not crossfade from pre-doze art");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        false, true, false, false, 1f, false, false),
                "Hidden backdrop must not crossfade");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, false, false, false, 1f, false, false),
                "Switched holder must not crossfade from previous holder");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, true, false, 1f, false, false),
                "Closing player must not start a song crossfade");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, false, true, 1f, false, false),
                "Compact return must not start a song crossfade");
        check(
                !MediaFullscreenExtras.shouldStartSongCrossfade(
                        true, true, false, false, 0.90f, false, false),
                "Opening player below settled threshold must not start a song crossfade");

        check(
                MediaFullscreenExtras.shouldDeferToRunningCrossfade(true, true, 0.10f),
                "Native reveal must defer once fallback crossfade has visibly progressed");
        check(
                !MediaFullscreenExtras.shouldDeferToRunningCrossfade(true, true, 0.0f),
                "At 0% fallback crossfade can still hand off to native reveal");
        check(
                !MediaFullscreenExtras.shouldDeferToRunningCrossfade(true, true, 0.02f),
                "Boundary 0.02f must still allow native reveal handoff");
        check(
                !MediaFullscreenExtras.shouldDeferToRunningCrossfade(true, false, 0.50f),
                "Crossfade targeting an older song must not defer a newer song reveal");
        check(
                !MediaFullscreenExtras.shouldDeferToRunningCrossfade(false, true, 0.50f),
                "Inactive crossfade must not defer native reveal");

        check(
                NativeArtworkReveal.shouldPreserveOutgoingOnBegin(true, false, 0f, true, false),
                "Duplicate 0% begin with missing/matching before must keep outgoing art");
        check(
                NativeArtworkReveal.shouldPreserveOutgoingOnBegin(true, false, 0f, false, true),
                "Repeated 0% begin for same outgoing bitmap must preserve outgoing art");
        check(
                !NativeArtworkReveal.shouldPreserveOutgoingOnBegin(true, false, 0.25f, true, false),
                "Progressed reveal must accept new begin");
        check(
                !NativeArtworkReveal.shouldPreserveOutgoingOnBegin(true, true, 0f, true, false),
                "When old equals incoming artwork, begin must refresh outgoing art");
        check(
                !NativeArtworkReveal.shouldPreserveOutgoingOnBegin(true, false, 0f, false, false),
                "Distinct new outgoing bitmap at 0% must replace old artwork");
        check(
                NativeArtworkReveal.shouldUseOutgoingZoom(true, true, 0f),
                "At 0% with outgoing art still bound, drawFade must keep outgoing zoom");
        check(
                NativeArtworkReveal.shouldUseOutgoingZoom(true, true, 0.25f),
                "While current still matches old mid-reveal, drawFade must keep outgoing zoom");
        check(
                !NativeArtworkReveal.shouldUseOutgoingZoom(true, true, 1f),
                "Completed reveal must not use outgoing zoom");
        check(
                !NativeArtworkReveal.shouldUseOutgoingZoom(true, false, 0.25f),
                "Once incoming art differs from old, drawFade may use incoming zoom");
        check(
                !NativeArtworkReveal.shouldUseOutgoingZoom(false, true, 0f),
                "Without valid outgoing art, drawFade must not override zoom");

        check(
                MediaFullscreenExtras.shouldHoldWakeArtwork(true, true, 1000L, 2500L),
                "Should hold wake cover while native artwork still references pre-doze bitmap");
        check(
                !MediaFullscreenExtras.shouldHoldWakeArtwork(false, true, 1000L, 2500L),
                "Without wake cover bitmap there is nothing to hold");
        check(
                !MediaFullscreenExtras.shouldHoldWakeArtwork(true, false, 1000L, 2500L),
                "Must release wake hold as soon as native artwork advances past pre-doze bitmap");
        check(
                !MediaFullscreenExtras.shouldHoldWakeArtwork(true, true, 2500L, 2500L),
                "Must release wake hold once timeout expires");

        check(
                MediaClockContrast.glassBlendAlpha(0f, 1f) == 0,
                "Zero mix should produce zero glass artwork alpha");
        check(
                MediaClockContrast.glassBlendAlpha(1f, 0f) == 0,
                "Zero artwork opacity should produce zero glass artwork alpha");
        check(
                MediaClockContrast.glassBlendAlpha(1f, 1f) == 199,
                "Full glass blend should retain contrast ink under 78% artwork, got "
                        + MediaClockContrast.glassBlendAlpha(1f, 1f));
        check(
                MediaClockContrast.glassBlendAlpha(2f, 2f) == 199,
                "Glass blend alpha must clamp mix and opacity above 1");
        check(
                MediaClockContrast.glassBlendAlpha(-0.5f, 1f) == 0,
                "Glass blend alpha must clamp negative inputs to 0");

        check(
                MediaFullscreenExtras.isTopBlurDisabled(true, false),
                "Top clock blur must disable when hide-clock preference is enabled");
        check(
                MediaFullscreenExtras.isTopBlurDisabled(false, true),
                "Top clock blur must disable when clock is hidden in window");
        check(
                !MediaFullscreenExtras.isTopBlurDisabled(false, false),
                "Top clock blur must remain active when clock is visible on player");

        check(
                Math.abs(MediaFullscreenExtras.controlsBandOpacity(0f, 0.45f, true, false) - 0.45f)
                        < 0.001f,
                "Controls blur must stay live with progress while closing even when controls fade out");
        check(
                Math.abs(MediaFullscreenExtras.controlsBandOpacity(0f, 0.35f, false, true) - 0.35f)
                        < 0.001f,
                "Controls blur must stay live with progress during compact return");
        check(
                Math.abs(MediaFullscreenExtras.controlsBandOpacity(0.15f, 0.6f, false, false) - 0.6f)
                        < 0.001f,
                "Controls blur must stay live at max(live, progress) while opening");
        check(
                Math.abs(MediaFullscreenExtras.controlsBandOpacity(0.4f, 1f, false, false) - 0.4f)
                        < 0.001f,
                "Settled controls blur must respect live controls alpha");

        check(
                MediaFullscreenExtras.topBandOpacity(0.8f, false, false, true) == 0f,
                "Top blur opacity must be 0 when clock is disabled on fullscreen player");
        check(
                Math.abs(MediaFullscreenExtras.topBandOpacity(0.55f, true, false, false) - 0.55f)
                        < 0.001f,
                "Top blur must track cover progress while closing");
        check(
                Math.abs(MediaFullscreenExtras.topBandOpacity(0.4f, false, false, false) - 0.4f)
                        < 0.001f,
                "Top blur must track cover progress while opening");
        check(
                MediaFullscreenExtras.topBandOpacity(1f, false, false, false) == 1f,
                "Settled top blur must be full opacity when clock is enabled");

        check(
                ArtworkBlurFilter.topCropHeight(256, 70f, 20) == 96,
                "Expected quantized top blur crop height 96, got "
                        + ArtworkBlurFilter.topCropHeight(256, 70f, 20));
        check(
                ArtworkBlurFilter.topCropHeight(256, 250f, 20) == 256,
                "Top blur crop height must clamp to cacheHeight");
        check(
                ArtworkBlurFilter.topCropHeight(256, 0f, 20) == 0,
                "Inactive top blur band must yield 0 crop height");
        check(
                ArtworkBlurFilter.topCropHeight(0, 50f, 20) == 0,
                "Empty blur cache must yield 0 crop height");

        check(
                !MediaStatusBarContrast.shouldUseDarkIcons(0.26, null),
                "Static status bar icons stay light at threshold boundary");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(0.27, null),
                "Static status bar icons flip dark above bright threshold");
        check(
                !MediaStatusBarContrast.shouldUseDarkIcons(0.0, null),
                "Black cover must keep light status bar icons");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(1.0, null),
                "White cover must use dark status bar icons");
        check(
                !MediaStatusBarContrast.shouldUseDarkIcons(Double.NaN, null),
                "Non-finite luma without prior state must keep light icons");
        check(
                !MediaStatusBarContrast.shouldUseDarkIcons(0.29, Boolean.FALSE),
                "Light status bar icons on video stay light below hysteresis dark threshold");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(0.31, Boolean.FALSE),
                "Light status bar icons on video flip dark above hysteresis threshold");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(0.20, Boolean.TRUE),
                "Dark status bar icons on video stay dark inside hysteresis band");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(0.18, Boolean.TRUE),
                "Dark status bar icons on video stay dark at lower hysteresis boundary");
        check(
                !MediaStatusBarContrast.shouldUseDarkIcons(0.17, Boolean.TRUE),
                "Dark status bar icons on video flip light below hysteresis floor");
        check(
                MediaStatusBarContrast.shouldUseDarkIcons(Double.NaN, Boolean.TRUE),
                "Non-finite luma must preserve prior dark status bar state");

        double expectedStaticAmbient = Math.pow((255 - 0x60) / 255.0, 2.2);
        double expectedLiveAmbient = Math.pow((255 - 0x30) / 255.0, 2.2);
        check(
                Math.abs(
                                MediaStatusBarContrast.effectiveAmbientLuminance(1.0, 0x60000000)
                                        - expectedStaticAmbient)
                        < 0.001,
                "Static ambient dim luminance mismatch");
        check(
                Math.abs(
                                MediaStatusBarContrast.effectiveAmbientLuminance(1.0, 0x30000000)
                                        - expectedLiveAmbient)
                        < 0.001,
                "Live ambient dim luminance mismatch");
        check(
                MediaStatusBarContrast.effectiveAmbientLuminance(0.5, 0x00000000) == 0.5,
                "Zero-alpha dim must preserve raw luminance");
        check(
                MediaStatusBarContrast.effectiveAmbientLuminance(1.0, 0xff000000) == 0.0,
                "Opaque dim must yield zero ambient luminance");
        check(
                Double.isNaN(
                        MediaStatusBarContrast.effectiveAmbientLuminance(Double.NaN, 0x60000000)),
                "Non-finite luminance must stay NaN through ambient dim");

        check(
                MediaStatusBarContrast.shouldUseDarkIconsAmbient(1.0, 0x60000000, null),
                "White static ambient cover must invert dark icons");
        check(
                MediaStatusBarContrast.shouldUseDarkIconsAmbient(1.0, 0x30000000, null),
                "White live ambient cover must invert dark icons");
        check(
                !MediaStatusBarContrast.shouldUseDarkIconsAmbient(0.1, 0x60000000, null),
                "Dark ambient cover must keep light icons");
        check(
                !MediaStatusBarContrast.shouldUseDarkIconsAmbient(0.35, 0x60000000, null),
                "Mid-tone cover darkened by static ambient dim must keep light icons");
        check(
                MediaStatusBarContrast.shouldUseDarkIconsAmbient(0.60, 0x60000000, null),
                "Bright cover still above ambient threshold after dim must invert dark icons");
        check(
                MediaStatusBarContrast.shouldUseDarkIconsAmbient(
                        Double.NaN, 0x60000000, Boolean.TRUE),
                "Non-finite ambient luma must preserve prior dark state");
        check(
                !MediaStatusBarContrast.shouldUseDarkIconsAmbient(
                        Double.NaN, 0x60000000, Boolean.FALSE),
                "Non-finite ambient luma must preserve prior light state");

        check(
                MediaStatusBarContrast.nioColorFor(true, false)
                        == MediaStatusBarContrast.DARK_STATUS_COLOR,
                "Dark awake NIO color mismatch");
        check(
                MediaStatusBarContrast.nioColorFor(true, true)
                        == MediaStatusBarContrast.DARK_STATUS_COLOR,
                "Dark ambient NIO color mismatch");
        check(
                MediaStatusBarContrast.nioColorFor(false, false)
                        == MediaStatusBarContrast.LIGHT_STATUS_COLOR,
                "Light awake NIO color mismatch");
        check(
                MediaStatusBarContrast.nioColorFor(false, true)
                        == MediaStatusBarContrast.AOD_LIGHT_STATUS_COLOR,
                "Light ambient NIO color mismatch");

        String nioOwner = "Lcom/samsung/android/uniform/widget/notification/NotificationIconsOnlyContainer;";
        check(
                MediaStatusBarContrast.isNioUpdateIconColorBody(
                        syntheticBody(
                                nioOwner,
                                "e",
                                List.of("I"),
                                "V",
                                0,
                                Map.of(MediaStatusBarContrast.NIO_UPDATE_ICON_COLOR_ANCHOR, 19),
                                List.of(),
                                List.of())),
                "Valid NIO updateIconColor body rejected");
        check(
                !MediaStatusBarContrast.isNioUpdateIconColorBody(
                        syntheticBody(
                                nioOwner,
                                "e",
                                List.of("I"),
                                "V",
                                Modifier.STATIC,
                                Map.of(MediaStatusBarContrast.NIO_UPDATE_ICON_COLOR_ANCHOR, 19),
                                List.of(),
                                List.of())),
                "Static NIO updateIconColor body must be rejected");
        check(
                !MediaStatusBarContrast.isNioUpdateIconColorBody(
                        syntheticBody(
                                nioOwner,
                                "e",
                                List.of(),
                                "V",
                                0,
                                Map.of(MediaStatusBarContrast.NIO_UPDATE_ICON_COLOR_ANCHOR, 19),
                                List.of(),
                                List.of())),
                "Wrong-arity NIO updateIconColor body must be rejected");
        check(
                !MediaStatusBarContrast.isNioUpdateIconColorBody(
                        syntheticBody(
                                nioOwner,
                                "e",
                                List.of("I"),
                                "I",
                                0,
                                Map.of(MediaStatusBarContrast.NIO_UPDATE_ICON_COLOR_ANCHOR, 19),
                                List.of(),
                                List.of())),
                "Non-void NIO updateIconColor body must be rejected");
        check(
                !MediaStatusBarContrast.isNioUpdateIconColorBody(
                        syntheticBody(
                                nioOwner,
                                "setIconColor",
                                List.of("I"),
                                "V",
                                0,
                                Map.of(),
                                List.of(),
                                List.of())),
                "Unanchored NIO (I)V body must be rejected");

        check(
                MediaStatusBarContrast.aodBatteryDarkIntensity(true) == 1.0f,
                "Dark AOD battery intensity must be 1.0f");
        check(
                MediaStatusBarContrast.aodBatteryDarkIntensity(false) == 0.0f,
                "Light AOD battery intensity must be 0.0f");
        check(
                MediaStatusBarContrast.aodDndColorFor(true)
                        == MediaStatusBarContrast.DARK_STATUS_COLOR,
                "Dark AOD DND icon color mismatch");
        check(
                MediaStatusBarContrast.aodDndColorFor(false)
                        == MediaStatusBarContrast.AOD_DND_LIGHT_COLOR,
                "Light AOD DND icon color mismatch");
        check(
                MediaStatusBarContrast.isAodBatteryTag("PluginFaceWidgetManager"),
                "AOD battery tag must match PluginFaceWidgetManager");
        check(
                !MediaStatusBarContrast.isAodBatteryTag("KeyguardStatusBarView"),
                "Non-AOD battery tag must be rejected");
        check(
                !MediaStatusBarContrast.isAodBatteryTag(null),
                "Null battery tag must be rejected");
    }

    public static void main(String[] args) throws Exception {
        verifyOneUi9BodyMatchers();
        verifyOutlineMemberResolution();
        verifyResolveMaskingResource();
        verifyDexStaticReadWritesAndConstructorIntArg();
        verifyClockGlassContrastPolicy();
        verifyArtworkUrlUpgrades();
        verifyBlurCacheSizingAndAmbientClosePolicy();
        System.out.println("Media contracts: " + checks + " checks passed");
    }
}
