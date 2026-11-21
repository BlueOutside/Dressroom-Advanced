package dev.denver.dressroomadvanced;

import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class DepthContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

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

    private static URLClassLoader compileFixture(
            Path root, Map<String, String> sources) throws IOException {
        Path srcDir = root.resolve("src");
        Path clsDir = root.resolve("cls");
        Files.createDirectories(clsDir);
        List<String> files = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            Path p = srcDir.resolve(entry.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, entry.getValue(), StandardCharsets.UTF_8);
            files.add(p.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "System JavaCompiler required for JVM depth contract fixtures");
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
                new URL[] {clsDir.toUri().toURL()}, DepthContractsTest.class.getClassLoader());
    }

    private static Map<String, String> commonDressroomSources() {
        Map<String, String> map = new HashMap<>();
        map.put(
                "androidx/core/util/Consumer.java",
                "package androidx.core.util;\n"
                        + "public interface Consumer<T> { void accept(T t); }\n");
        map.put(
                "com/samsung/android/app/dressroom/data/profile/wallpaper/EditorData.java",
                "package com.samsung.android.app.dressroom.data.profile.wallpaper;\n"
                        + "import android.net.Uri;\n"
                        + "public class EditorData {\n"
                        + "    public Uri originalUri;\n"
                        + "    public Uri uri;\n"
                        + "    public String path;\n"
                        + "    public int subType;\n"
                        + "    public int parentSubType;\n"
                        + "    public Uri getOriginalUri() { return originalUri; }\n"
                        + "    public Uri getUri() { return uri; }\n"
                        + "    public String getStorageFilePath() { return path; }\n"
                        + "    public int getWallpaperSubType() { return subType; }\n"
                        + "    public int getWallpaperParentSubType() { return parentSubType; }\n"
                        + "}\n");
        map.put(
                "com/samsung/android/app/dressroom/presentation/preview/widget/CropImageView.java",
                "package com.samsung.android.app.dressroom.presentation.preview.widget;\n"
                        + "public class CropImageView {}\n");
        map.put(
                "com/samsung/android/app/dressroom/editorcomponent/component/wallpaper/image/effects/weather/WeatherSurfaceView.java",
                "package com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather;\n"
                        + "public class WeatherSurfaceView {}\n");
        return map;
    }

    private static void verifyShifted85WithAdverseAlphabeticalDormantField(Path tempRoot)
            throws Exception {
        Map<String, String> src = commonDressroomSources();
        src.put(
                "X2/W.java",
                "package X2;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class W {\n"
                        + "    public EditorData Y;\n"
                        + "    public void a3(EditorData d) { this.Y = d; }\n"
                        + "    public void b3(Object unrelated) {}\n"
                        + "}\n");
        src.put(
                "X2/B.java",
                "package X2;\n"
                        + "public class B {\n"
                        + "    public W a = new W();\n"
                        + "    public W z = new W();\n"
                        + "    public void recreateCheck() {}\n"
                        + "}\n");
        src.put(
                "P3/f.java",
                "package P3;\n" + "public class f { public X2.B v = new X2.B(); }\n");
        src.put(
                "com/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity.java",
                "package com.samsung.android.app.dressroom.presentation.preview.activity;\n"
                        + "public class EditBaseActivity { public P3.f n = new P3.f(); }\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("s85"), src)) {
            byte[] dexBytes = buildShifted85Dex();
            List<DexSignatures.Body> anchors =
                    DexSignatures.inspect(dexBytes, Set.of("need_to_recreate_preview"));
            WallpaperSourceBinding.DexResolver dex =
                    new WallpaperSourceBinding.DexResolver() {
                        @Override
                        public List<DexSignatures.Body> find(Set<String> keys) {
                            return anchors;
                        }

                        @Override
                        public DexSignatures.Body body(Method method) {
                            throw new UnsupportedOperationException("Unexpected body lookup");
                        }
                    };
            WallpaperSourceBinding binding = new WallpaperSourceBinding(loader, true, dex);
            check("a3".equals(binding.changed.getName()), "Shifted 8.5 must resolve a3");

            Object activity =
                    loader.loadClass(
                                    "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity")
                            .getDeclaredConstructor()
                            .newInstance();
            Object p3f = activity.getClass().getField("n").get(activity);
            Object b = p3f.getClass().getField("v").get(p3f);
            Object dormant = b.getClass().getField("a").get(b);
            Object active = b.getClass().getField("z").get(b);
            Object dataA = binding.dataType.getDeclaredConstructor().newInstance();
            Object dataZ = binding.dataType.getDeclaredConstructor().newInstance();
            binding.dataType.getField("path").set(dataA, "/wrong.jpg");
            binding.dataType.getField("path").set(dataZ, "/active85.jpg");
            binding.changed.invoke(dormant, dataA);
            binding.changed.invoke(active, dataZ);

            WallpaperSourceBinding.Source read = binding.read(activity);
            check(
                    read != null
                            && read.data() == dataZ
                            && "/active85.jpg".equals(read.key())
                            && read.image(),
                    "Adversarial dormant field 'a' beat active field 'z'");
        }
    }

    private static void verifySubclassPruningAndTieBreaker(Path tempRoot) throws Exception {
        Map<String, String> src = commonDressroomSources();
        src.put(
                "o6/e0.java",
                "package o6;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class e0 {\n"
                        + "    public EditorData T;\n"
                        + "    public void c1(EditorData d) { this.T = d; }\n"
                        + "}\n");
        src.put(
                "o6/n.java",
                "package o6;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class n extends e0 {\n"
                        + "    public EditorData subData;\n"
                        + "    public void D1(EditorData d) { this.subData = d; }\n"
                        + "    public void F1(EditorData d) {}\n"
                        + "}\n");
        src.put(
                "o6/t.java",
                "package o6;\n"
                        + "public class t {\n"
                        + "    public n s = new n();\n"
                        + "    public e0 t = new e0();\n"
                        + "}\n");
        src.put("p8/a.java", "package p8;\n" + "public class a { public o6.t p = new o6.t(); }\n");
        src.put(
                "com/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity.java",
                "package com.samsung.android.app.dressroom.presentation.preview.activity;\n"
                        + "public class EditBaseActivity { public p8.a altEntry = new p8.a(); }\n");

        WallpaperSourceBinding.DexResolver emptyDex =
                new WallpaperSourceBinding.DexResolver() {
                    @Override
                    public List<DexSignatures.Body> find(Set<String> anchors) {
                        return List.of();
                    }

                    @Override
                    public DexSignatures.Body body(Method method) {
                        throw new UnsupportedOperationException("No DEX bodies");
                    }
                };

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("prune"), src)) {
            WallpaperSourceBinding binding = new WallpaperSourceBinding(loader, false, emptyDex);
            check("o6.e0".equals(binding.changed.getDeclaringClass().getName()), "Prune o6.n");
            check("c1".equals(binding.changed.getName()), "Expected c1 on o6.e0");
        }

        // Two unrelated base-type fields with no 't'/'w' name and no DEX bodies must fail closed
        Map<String, String> ambig = commonDressroomSources();
        ambig.put(
                "x/Ctrl.java",
                "package x;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class Ctrl { public EditorData d; public void setD(EditorData v) { d = v; } }\n");
        ambig.put(
                "x/Host.java",
                "package x;\n" + "public class Host { public Ctrl p1; public Ctrl p2; }\n");
        ambig.put("x/Root.java", "package x;\n" + "public class Root { public Host h; }\n");
        ambig.put(
                "com/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity.java",
                "package com.samsung.android.app.dressroom.presentation.preview.activity;\n"
                        + "public class EditBaseActivity { public x.Root r; }\n");
        try (URLClassLoader loader = compileFixture(tempRoot.resolve("ambig"), ambig)) {
            fails(
                    () -> new WallpaperSourceBinding(loader, false, emptyDex),
                    NoSuchFieldException.class,
                    "Ambiguous controller fields without DEX must fail closed");
        }
    }

    private static void verifyRenamedMultiSegmentAndWeatherHooks(Path tempRoot) throws Exception {
        Map<String, String> src = commonDressroomSources();
        src.put(
                "com/renamed/deep/CtrlBase.java",
                "package com.renamed.deep;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class CtrlBase {\n"
                        + "    public EditorData payloadSlot;\n"
                        + "    public void decoySetter(EditorData d) {}\n"
                        + "    public void applyPayload(EditorData d) { this.payloadSlot = d; }\n"
                        + "}\n");
        src.put(
                "com/renamed/deep/CtrlSub.java",
                "package com.renamed.deep;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class CtrlSub extends CtrlBase {\n"
                        + "    public EditorData subSlot;\n"
                        + "    public void subA(EditorData d) { this.subSlot = d; }\n"
                        + "    public void subB(EditorData d) {}\n"
                        + "}\n");
        src.put(
                "com/renamed/deep/HostPane.java",
                "package com.renamed.deep;\n"
                        + "public class HostPane {\n"
                        + "    public CtrlSub sub = new CtrlSub();\n"
                        + "    public CtrlBase a = new CtrlBase();\n"
                        + "    public CtrlBase z = new CtrlBase();\n"
                        + "    public void checkRecreate() {}\n"
                        + "}\n");
        src.put(
                "com/renamed/entry/ComponentRoot.java",
                "package com.renamed.entry;\n"
                        + "public class ComponentRoot { public com.renamed.deep.HostPane pane = new com.renamed.deep.HostPane(); }\n");
        src.put(
                "com/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity.java",
                "package com.samsung.android.app.dressroom.presentation.preview.activity;\n"
                        + "public class EditBaseActivity { public com.renamed.entry.ComponentRoot entry = new com.renamed.entry.ComponentRoot(); }\n");
        src.put(
                "com/renamed/fx/WeatherHost.java",
                "package com.renamed.fx;\n"
                        + "import androidx.core.util.Consumer;\n"
                        + "import com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather.WeatherSurfaceView;\n"
                        + "import com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView;\n"
                        + "public class WeatherHost {\n"
                        + "    public CropImageView cropView = new CropImageView();\n"
                        + "    public WeatherSurfaceView fxView = new WeatherSurfaceView();\n"
                        + "    public void initRenamed(Consumer<Object> cb) {}\n"
                        + "    public void playRenamed() {}\n"
                        + "    public void decoyTwoParams(Consumer<Object> cb, boolean flag) {}\n"
                        + "    public void decoyMissingTag() {}\n"
                        + "}\n");
        src.put(
                "l3/h.java",
                "package l3;\n"
                        + "import androidx.core.util.Consumer;\n"
                        + "import com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather.WeatherSurfaceView;\n"
                        + "import com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView;\n"
                        + "public class h {\n"
                        + "    public CropImageView c = new CropImageView();\n"
                        + "    public WeatherSurfaceView s = new WeatherSurfaceView();\n"
                        + "    public void u(Consumer<Object> cb) {}\n"
                        + "    public void z() {}\n"
                        + "}\n");
        src.put(
                "i7/g.java",
                "package i7;\n"
                        + "import androidx.core.util.Consumer;\n"
                        + "import com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather.WeatherSurfaceView;\n"
                        + "import com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView;\n"
                        + "public class g {\n"
                        + "    public CropImageView c = new CropImageView();\n"
                        + "    public WeatherSurfaceView s = new WeatherSurfaceView();\n"
                        + "    public void c(Consumer<Object> cb) {}\n"
                        + "    public void e() {}\n"
                        + "}\n");

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("renamed"), src)) {
            byte[] dexBytes = buildRenamedAndWeatherDex();
            List<DexSignatures.Body> allBodies =
                    DexSignatures.inspect(
                            dexBytes,
                            Set.of(
                                    "need_to_recreate_preview",
                                    WeatherPreviewBinding.PREVIEW_TAG,
                                    WeatherPreviewBinding.INIT_ANCHOR,
                                    WeatherPreviewBinding.PLAY_ANCHOR));

            WallpaperSourceBinding.DexResolver dex =
                    new WallpaperSourceBinding.DexResolver() {
                        @Override
                        public List<DexSignatures.Body> find(Set<String> anchors) {
                            return allBodies;
                        }

                        @Override
                        public DexSignatures.Body body(Method method) throws IOException {
                            return DexSignatures.inspect(dexBytes, method);
                        }
                    };

            for (boolean legacy : new boolean[] {false, true}) {
                WallpaperSourceBinding binding = new WallpaperSourceBinding(loader, legacy, dex);
                check(
                        "com.renamed.deep.CtrlBase".equals(binding.changed.getDeclaringClass().getName()),
                        "Renamed multi-segment CtrlBase not selected for legacy=" + legacy);
                check(
                        "applyPayload".equals(binding.changed.getName()),
                        "Write-disambiguated applyPayload failed for legacy=" + legacy);
                Object activity =
                        loader.loadClass(
                                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity")
                                .getDeclaredConstructor()
                                .newInstance();
                Object root = activity.getClass().getField("entry").get(activity);
                Object pane = root.getClass().getField("pane").get(root);
                Object activeCtrl = pane.getClass().getField("z").get(pane);
                Object data = binding.dataType.getDeclaredConstructor().newInstance();
                binding.dataType.getField("path").set(data, "/renamed.jpg");
                binding.changed.invoke(activeCtrl, data);
                WallpaperSourceBinding.Source validSrc = binding.read(activity);
                check(
                        validSrc != null && validSrc.image() && "/renamed.jpg".equals(validSrc.key()),
                        "Renamed read() failed");

                binding.dataType.getField("path").set(data, "");
                check(
                        binding.read(activity) == null,
                        "Empty image source path must return null");
            }

            // Fail closed when multiple void(EditorData) methods lack a distinguishing field write
            WallpaperSourceBinding.DexResolver noWriteDex =
                    new WallpaperSourceBinding.DexResolver() {
                        @Override
                        public List<DexSignatures.Body> find(Set<String> anchors) {
                            return allBodies;
                        }

                        @Override
                        public DexSignatures.Body body(Method method) {
                            String ownerDesc = DexSignatures.descriptor(method.getDeclaringClass());
                            return new DexSignatures.Body(
                                    ownerDesc,
                                    method.getName(),
                                    List.of(
                                            "Lcom/samsung/android/app/dressroom/data/profile/wallpaper/EditorData;"),
                                    "V",
                                    Modifier.PUBLIC,
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    List.of());
                        }
                    };
            fails(
                    () -> new WallpaperSourceBinding(loader, false, noWriteDex),
                    ReflectiveOperationException.class,
                    "Zero candidates when setter disambiguation finds no field write must fail closed");

            // WeatherPreviewBinding via real parsed DEX bytes
            Class<?> cropType =
                    loader.loadClass(
                            "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView");
            Class<?> surfaceType =
                    loader.loadClass(
                            "com.samsung.android.app.dressroom.editorcomponent.component.wallpaper.image.effects.weather.WeatherSurfaceView");
            WeatherPreviewBinding.Hooks renamedWeather =
                    WeatherPreviewBinding.resolve(loader, cropType, surfaceType, false, allBodies);
            check(
                    "com.renamed.fx.WeatherHost".equals(renamedWeather.owner().getName()),
                    "Renamed WeatherHost not resolved");
            check("cropView".equals(renamedWeather.crop().getName()), "Weather crop field mismatch");
            check("fxView".equals(renamedWeather.surface().getName()), "Weather surface field mismatch");
            Set<String> weatherNames = new HashSet<>();
            for (Method m : renamedWeather.methods()) weatherNames.add(m.getName());
            check(
                    Set.of("initRenamed", "playRenamed").equals(weatherNames),
                    "Decoys leaked or methods missing: " + weatherNames);

            // Reflection fallback when DEX bodies yield empty list (8.5 l3.h vs 9.0 i7.g)
            WeatherPreviewBinding.Hooks fb85 =
                    WeatherPreviewBinding.resolve(loader, cropType, surfaceType, true, List.of());
            check(
                    "l3.h".equals(fb85.owner().getName())
                            && fb85.methods().size() == 1
                            && "u".equals(fb85.methods().iterator().next().getName()),
                    "8.5 weather reflection fallback failed");
            WeatherPreviewBinding.Hooks fb90 =
                    WeatherPreviewBinding.resolve(loader, cropType, surfaceType, false, List.of());
            check(
                    "i7.g".equals(fb90.owner().getName())
                            && fb90.methods().size() == 1
                            && "e".equals(fb90.methods().iterator().next().getName()),
                    "9.0 weather reflection fallback failed");
        }
    }

    private static void verifyEffectSourceContracts(Path tempRoot) throws Exception {
        check(
                WallpaperSourceBinding.nonEmpty(
                                new Object() {
                                    @Override
                                    public String toString() {
                                        return "";
                                    }
                                })
                        .isEmpty(),
                "Empty Uri-like toString must be treated as empty");
        check(
                "content://a/b".equals(WallpaperSourceBinding.nonEmpty("content://a/b")),
                "Non-empty key was stripped");

        WallpaperSourceBinding.Source legacySource =
                new WallpaperSourceBinding.Source("data", "k", true);
        check(!legacySource.effectApplied(), "3-arg Source constructor must default effectApplied=false");

        Map<String, String> src = commonDressroomSources();
        src.put(
                "com/samsung/android/app/dressroom/data/profile/wallpaper/EditorData.java",
                "package com.samsung.android.app.dressroom.data.profile.wallpaper;\n"
                        + "import android.net.Uri;\n"
                        + "public class EditorData {\n"
                        + "    public Uri originalUri;\n"
                        + "    public Uri uri;\n"
                        + "    public String originalFilePath;\n"
                        + "    public String path;\n"
                        + "    public String extraPath;\n"
                        + "    public String previewPath;\n"
                        + "    public String fileName;\n"
                        + "    public String weatherId;\n"
                        + "    public int subType;\n"
                        + "    public int parentSubType;\n"
                        + "    public boolean isCover;\n"
                        + "    public boolean applyEffect;\n"
                        + "    public String imageFilterInfo;\n"
                        + "    public Uri getOriginalUri() { return originalUri; }\n"
                        + "    public Uri getUri() { return uri; }\n"
                        + "    public String getOriginalFilePath() { return originalFilePath; }\n"
                        + "    public String getStorageFilePath() { return path; }\n"
                        + "    public String getPath() { return extraPath; }\n"
                        + "    public String getPreviewPath() { return previewPath; }\n"
                        + "    public String getFileName() { return fileName; }\n"
                        + "    public String getWeatherId() { return weatherId; }\n"
                        + "    public int getWallpaperSubType() { return subType; }\n"
                        + "    public int getWallpaperParentSubType() { return parentSubType; }\n"
                        + "    public boolean getIsCover() { return isCover; }\n"
                        + "    public boolean isApplyEffect() { return applyEffect; }\n"
                        + "    public String getImageFilterInfo() { return imageFilterInfo; }\n"
                        + "}\n");
        src.put(
                "com/effect/Ctrl.java",
                "package com.effect;\n"
                        + "import com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData;\n"
                        + "public class Ctrl {\n"
                        + "    public EditorData slot;\n"
                        + "    public void setSlot(EditorData d) { this.slot = d; }\n"
                        + "}\n");
        src.put(
                "com/effect/Host.java",
                "package com.effect;\n"
                        + "public class Host { public Ctrl ctrl = new Ctrl(); public void check() {} }\n");
        src.put(
                "com/effect/Entry.java",
                "package com.effect;\n"
                        + "public class Entry { public Host host = new Host(); }\n");
        src.put(
                "com/samsung/android/app/dressroom/presentation/preview/activity/EditBaseActivity.java",
                "package com.samsung.android.app.dressroom.presentation.preview.activity;\n"
                        + "public class EditBaseActivity { public com.effect.Entry entry = new com.effect.Entry(); }\n");

        String edDesc = "Lcom/samsung/android/app/dressroom/data/profile/wallpaper/EditorData;";
        DexSignatures.Body hostBody =
                new DexSignatures.Body(
                        "Lcom/effect/Host;",
                        "check",
                        List.of(),
                        "V",
                        Modifier.PUBLIC,
                        Map.of("need_to_recreate_preview", 0),
                        List.of(
                                new DexSignatures.Read(
                                        2,
                                        new DexSignatures.Member(
                                                "Lcom/effect/Host;", "ctrl", "Lcom/effect/Ctrl;"))),
                        List.of(),
                        List.of(
                                new DexSignatures.Call(
                                        4,
                                        "Lcom/effect/Ctrl;",
                                        "setSlot",
                                        List.of(edDesc),
                                        "V",
                                        false)));
        DexSignatures.Body setBody =
                new DexSignatures.Body(
                        "Lcom/effect/Ctrl;",
                        "setSlot",
                        List.of(edDesc),
                        "V",
                        Modifier.PUBLIC,
                        Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Write(
                                        0, new DexSignatures.Member("Lcom/effect/Ctrl;", "slot", edDesc))),
                        List.of());
        WallpaperSourceBinding.DexResolver dex =
                new WallpaperSourceBinding.DexResolver() {
                    @Override
                    public List<DexSignatures.Body> find(Set<String> anchors) {
                        return List.of(hostBody);
                    }

                    @Override
                    public DexSignatures.Body body(Method method) {
                        return "check".equals(method.getName()) ? hostBody : setBody;
                    }
                };

        try (URLClassLoader loader = compileFixture(tempRoot.resolve("effects"), src)) {
            WallpaperSourceBinding binding = new WallpaperSourceBinding(loader, false, dex);
            Object activity =
                    loader.loadClass(
                                    "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity")
                            .getDeclaredConstructor()
                            .newInstance();
            Object entry = activity.getClass().getField("entry").get(activity);
            Object host = entry.getClass().getField("host").get(entry);
            Object ctrl = host.getClass().getField("ctrl").get(host);
            Object data = binding.dataType.getDeclaredConstructor().newInstance();
            binding.changed.invoke(ctrl, data);

            // Cover frame (subType=5, isCover=true) accepted; main multipack (isCover=false) rejected
            binding.dataType.getField("subType").setInt(data, 5);
            binding.dataType.getField("isCover").setBoolean(data, true);
            binding.dataType.getField("path").set(data, "/cover-frame.jpg");
            WallpaperSourceBinding.Source coverFrame = binding.read(activity);
            check(
                    coverFrame != null
                            && coverFrame.image()
                            && "/cover-frame.jpg".equals(coverFrame.key()),
                    "Cover frame subType=5 was rejected");

            binding.dataType.getField("isCover").setBoolean(data, false);
            WallpaperSourceBinding.Source multipack = binding.read(activity);
            check(
                    multipack != null && !multipack.image(),
                    "Main-screen multipack subType=5 must not be marked as still image");

            // Fallback getters: originalFilePath, extraPath, previewPath, fileName, weatherId
            binding.dataType.getField("subType").setInt(data, 12);
            binding.dataType.getField("path").set(data, "");
            binding.dataType.getField("originalFilePath").set(data, "/orig-path.jpg");
            WallpaperSourceBinding.Source origPathSrc = binding.read(activity);
            check(
                    origPathSrc != null
                            && origPathSrc.image()
                            && "/orig-path.jpg".equals(origPathSrc.key()),
                    "originalFilePath fallback failed");

            binding.dataType.getField("subType").setInt(data, 409);
            binding.dataType.getField("originalFilePath").set(data, "");
            binding.dataType.getField("previewPath").set(data, "/motion-preview.jpg");
            WallpaperSourceBinding.Source motionPreview = binding.read(activity);
            check(
                    motionPreview != null
                            && motionPreview.image()
                            && "/motion-preview.jpg".equals(motionPreview.key()),
                    "previewPath fallback failed for MotionPhoto");

            binding.dataType.getField("previewPath").set(data, "");
            binding.dataType.getField("fileName").set(data, "named.jpg");
            WallpaperSourceBinding.Source namedSrc = binding.read(activity);
            check(
                    namedSrc != null && namedSrc.image() && "named.jpg".equals(namedSrc.key()),
                    "fileName fallback failed");

            binding.dataType.getField("subType").setInt(data, 402);
            binding.dataType.getField("fileName").set(data, "");
            binding.dataType.getField("weatherId").set(data, "rain_03");
            WallpaperSourceBinding.Source weatherSrc = binding.read(activity);
            check(
                    weatherSrc != null
                            && weatherSrc.image()
                            && "weather:rain_03".equals(weatherSrc.key()),
                    "Weather ID fallback failed for subType=402");

            // Effect metadata propagation: isApplyEffect vs imageFilterInfo
            binding.dataType.getField("imageFilterInfo").set(data, "Original");
            binding.dataType.getField("applyEffect").setBoolean(data, false);
            check(!binding.read(activity).effectApplied(), "Original filter falsely flagged effectApplied");

            binding.dataType.getField("imageFilterInfo").set(data, "OilPaint");
            check(binding.read(activity).effectApplied(), "Style imageFilterInfo missed effectApplied");

            binding.dataType.getField("imageFilterInfo").set(data, "Original");
            binding.dataType.getField("applyEffect").setBoolean(data, true);
            check(binding.read(activity).effectApplied(), "isApplyEffect missed effectApplied");
        }
    }

    private static byte[] buildShifted85Dex() {
        String hostDesc = "LX2/B;";
        String ctrlDesc = "LX2/W;";
        String objDesc = "Ljava/lang/Object;";
        int dataBase = 0;
        for (int pass = 0; pass < 4; pass++) {
            MinimalDexBuilder b = new MinimalDexBuilder();
            if (pass == 0) dataBase = b.dataBasePlaceholder;
            int sAnchor = b.internString("need_to_recreate_preview");
            int fActive = b.internField(hostDesc, "z", ctrlDesc);
            int mRecreate = b.internMethod(hostDesc, "recreateCheck", "V", List.of());
            int cRecreate =
                    b.writeCodeItem(
                            2,
                            1,
                            new int[] {
                                0x001a, sAnchor, // const-string v0, "need_to_recreate_preview"
                                0x1054, fActive, // iget-object v0, v1(p0), X2/B->z
                                0x000e // return-void
                            });
            int shift = dataBase - b.dataBasePlaceholder;
            b.addClassWithMethods(
                    hostDesc,
                    objDesc,
                    List.of(),
                    List.of(new int[] {mRecreate, Modifier.PUBLIC, cRecreate + shift}));
            byte[] built = b.build();
            int actualDataBase = built.length - b.extraData.size();
            if (pass > 0 && actualDataBase == dataBase) return built;
            dataBase = actualDataBase;
        }
        throw new IllegalStateException("Unreachable");
    }

    private static byte[] buildRenamedAndWeatherDex() {
        String hostDesc = "Lcom/renamed/deep/HostPane;";
        String ctrlDesc = "Lcom/renamed/deep/CtrlBase;";
        String subDesc = "Lcom/renamed/deep/CtrlSub;";
        String edDesc = "Lcom/samsung/android/app/dressroom/data/profile/wallpaper/EditorData;";
        String weatherDesc = "Lcom/renamed/fx/WeatherHost;";
        String consumerDesc = "Landroidx/core/util/Consumer;";
        String objDesc = "Ljava/lang/Object;";
        int dataBase = 0;
        for (int pass = 0; pass < 4; pass++) {
            MinimalDexBuilder b = new MinimalDexBuilder();
            if (pass == 0) dataBase = b.dataBasePlaceholder;
            int sRecreate = b.internString("need_to_recreate_preview");
            int sPreview = b.internString(WeatherPreviewBinding.PREVIEW_TAG);
            int sInit = b.internString(WeatherPreviewBinding.INIT_ANCHOR);
            int sPlay = b.internString(WeatherPreviewBinding.PLAY_ANCHOR);

            int fActive = b.internField(hostDesc, "z", ctrlDesc);
            int fPayload = b.internField(ctrlDesc, "payloadSlot", edDesc);
            int fSubSlot = b.internField(subDesc, "subSlot", edDesc);

            int mRecreate = b.internMethod(hostDesc, "checkRecreate", "V", List.of());
            int mDecoy = b.internMethod(ctrlDesc, "decoySetter", "V", List.of(edDesc));
            int mApply = b.internMethod(ctrlDesc, "applyPayload", "V", List.of(edDesc));
            int mSubA = b.internMethod(subDesc, "subA", "V", List.of(edDesc));
            int mSubB = b.internMethod(subDesc, "subB", "V", List.of(edDesc));
            int mInitRenamed =
                    b.internMethod(weatherDesc, "initRenamed", "V", List.of(consumerDesc));
            int mPlayRenamed = b.internMethod(weatherDesc, "playRenamed", "V", List.of());
            int mDecoyTwo =
                    b.internMethod(weatherDesc, "decoyTwoParams", "V", List.of(consumerDesc, "Z"));
            int mDecoyMissing = b.internMethod(weatherDesc, "decoyMissingTag", "V", List.of());

            int cRecreate =
                    b.writeCodeItem(
                            2,
                            1,
                            new int[] {0x001a, sRecreate, 0x1054, fActive, 0x000e});
            int cDecoy = b.writeCodeItem(2, 2, new int[] {0x000e});
            int cApply =
                    b.writeCodeItem(
                            2,
                            2,
                            new int[] {0x015b, fPayload, 0x000e}); // iput-object p1(v1), p0(v0), payloadSlot
            int cSubA = b.writeCodeItem(2, 2, new int[] {0x015b, fSubSlot, 0x000e});
            int cSubB = b.writeCodeItem(2, 2, new int[] {0x000e});
            int cInit =
                    b.writeCodeItem(
                            3,
                            2,
                            new int[] {0x001a, sPreview, 0x001a, sInit, 0x000e});
            int cPlay =
                    b.writeCodeItem(
                            2,
                            1,
                            new int[] {0x001a, sPreview, 0x001a, sPlay, 0x000e});
            int cDecoyTwo =
                    b.writeCodeItem(
                            4,
                            3,
                            new int[] {0x001a, sPreview, 0x001a, sInit, 0x000e});
            int cDecoyMissing =
                    b.writeCodeItem(2, 1, new int[] {0x001a, sPlay, 0x000e});

            int shift = dataBase - b.dataBasePlaceholder;
            b.addClassWithMethods(
                    hostDesc,
                    objDesc,
                    List.of(),
                    List.of(new int[] {mRecreate, Modifier.PUBLIC, cRecreate + shift}));

            List<int[]> ctrlMethods = new ArrayList<>();
            ctrlMethods.add(new int[] {mDecoy, Modifier.PUBLIC, cDecoy + shift});
            ctrlMethods.add(new int[] {mApply, Modifier.PUBLIC, cApply + shift});
            ctrlMethods.sort(Comparator.comparingInt(a -> a[0]));
            b.addClassWithMethods(ctrlDesc, objDesc, List.of(), ctrlMethods);

            List<int[]> subMethods = new ArrayList<>();
            subMethods.add(new int[] {mSubA, Modifier.PUBLIC, cSubA + shift});
            subMethods.add(new int[] {mSubB, Modifier.PUBLIC, cSubB + shift});
            subMethods.sort(Comparator.comparingInt(a -> a[0]));
            b.addClassWithMethods(subDesc, ctrlDesc, List.of(), subMethods);

            List<int[]> weatherMethods = new ArrayList<>();
            weatherMethods.add(new int[] {mInitRenamed, Modifier.PUBLIC, cInit + shift});
            weatherMethods.add(new int[] {mPlayRenamed, Modifier.PUBLIC, cPlay + shift});
            weatherMethods.add(new int[] {mDecoyTwo, Modifier.PUBLIC, cDecoyTwo + shift});
            weatherMethods.add(new int[] {mDecoyMissing, Modifier.PUBLIC, cDecoyMissing + shift});
            weatherMethods.sort(Comparator.comparingInt(a -> a[0]));
            b.addClassWithMethods(weatherDesc, objDesc, List.of(), weatherMethods);

            byte[] built = b.build();
            int actualDataBase = built.length - b.extraData.size();
            if (pass > 0 && actualDataBase == dataBase) return built;
            dataBase = actualDataBase;
        }
        throw new IllegalStateException("Unreachable");
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
            for (int i = 0; i < paramDescs.size(); i++) paramTypes[i] = internType(paramDescs.get(i));
            for (int i = 0; i < protos.size(); i++) {
                int[] p = protos.get(i);
                if (p[0] == shortyIdx && p[1] == retTypeIdx && p.length - 2 == paramTypes.length) {
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

        int internMethod(String ownerDesc, String name, String resultDesc, List<String> paramDescs) {
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
            writeUleb(extraData, 0);
            writeUleb(extraData, 0);
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
            writeU16(extraData, 2);
            writeU16(extraData, 0);
            writeU32(extraData, 0);
            writeU32(extraData, insns.length);
            for (int insn : insns) writeU16(extraData, insn);
            return offsetMarker;
        }

        private void writeEncodedMethods(List<int[]> list) {
            int prevIdx = 0;
            for (int[] m : list) {
                writeUleb(extraData, m[0] - prevIdx);
                prevIdx = m[0];
                writeUleb(extraData, m[1]);
                writeUleb(extraData, m[2]);
            }
        }

        byte[] build() {
            int off =
                    112
                            + strings.size() * 4
                            + types.size() * 4
                            + protos.size() * 12
                            + fields.size() * 8
                            + methods.size() * 8
                            + classDefs.size() * 32;
            ByteArrayOutputStream paramLists = new ByteArrayOutputStream();
            int[] protoParamOffsets = new int[protos.size()];
            for (int i = 0; i < protos.size(); i++) {
                int[] p = protos.get(i);
                if (p.length == 2) protoParamOffsets[i] = 0;
                else {
                    align4(paramLists);
                    protoParamOffsets[i] = off + paramLists.size();
                    writeU32(paramLists, p.length - 2);
                    for (int j = 2; j < p.length; j++) writeU16(paramLists, p[j]);
                }
            }
            off += paramLists.size();
            int stringDataOff = off;
            ByteArrayOutputStream stringData = new ByteArrayOutputStream();
            int[] stringOffsets = new int[strings.size()];
            for (int i = 0; i < strings.size(); i++) {
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
            putU32(header, 32, dataBase + extraData.size());
            putU32(header, 36, 112);
            putU32(header, 40, 0x12345678);
            putU32(header, 56, strings.size());
            putU32(header, 60, 112);
            putU32(header, 64, types.size());
            putU32(header, 68, 112 + strings.size() * 4);
            putU32(header, 72, protos.size());
            putU32(header, 76, 112 + strings.size() * 4 + types.size() * 4);
            putU32(header, 80, fields.size());
            putU32(header, 84, 112 + strings.size() * 4 + types.size() * 4 + protos.size() * 12);
            putU32(header, 88, methods.size());
            putU32(
                    header,
                    92,
                    112 + strings.size() * 4 + types.size() * 4 + protos.size() * 12 + fields.size() * 8);
            putU32(header, 96, classDefs.size());
            putU32(
                    header,
                    100,
                    112
                            + strings.size() * 4
                            + types.size() * 4
                            + protos.size() * 12
                            + fields.size() * 8
                            + methods.size() * 8);
            out.writeBytes(header);
            for (int sOff : stringOffsets) writeU32(out, sOff);
            for (int strIdx : types) writeU32(out, strIdx);
            for (int i = 0; i < protos.size(); i++) {
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
            byte[] extra = extraData.toByteArray();
            byte[] full = out.toByteArray();
            byte[] combined = new byte[full.length + extra.length];
            System.arraycopy(full, 0, combined, 0, full.length);
            System.arraycopy(extra, 0, combined, full.length, extra.length);
            return combined;
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

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("depth-contracts-");
        try {
            verifyShifted85WithAdverseAlphabeticalDormantField(tempRoot);
            verifySubclassPruningAndTieBreaker(tempRoot);
            verifyRenamedMultiSegmentAndWeatherHooks(tempRoot);
            verifyEffectSourceContracts(tempRoot);
        } finally {
            deleteTree(tempRoot);
        }
        System.out.println("Depth contracts: " + checks + " checks passed");
    }
}
