package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.util.Range;

import java.util.ArrayList;
import java.util.List;

public final class AdapterContractsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private interface Checked {
        void run() throws Exception;
    }

    private static void missing(Checked action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (NoSuchFieldException | NoSuchMethodException expected) {
            checks++;
        }
    }

    private static final class Reference {
        private String W0;
        private static int staticField;

        private void update(String value) {}

        private static void staticMethod() {}
    }

    private static final class OtherBuild {
        private boolean W0;

        private int update(String value) {
            return 0;
        }
    }

    private static final class EqualRequest {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualRequest;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static class Renamed {
        private String value;

        private void renamed(String value) {}

        private static long renamedStatic(int value) {
            return value;
        }
    }

    private static class Ambiguous {
        private String first, second;

        private void first(String value) {}

        private void second(String value) {}

        private void overloaded(String value) {}

        private void overloaded(Integer value) {}

        private static long firstStatic(int value) {
            return value;
        }

        private static long secondStatic(int value) {
            return value;
        }
    }

    private static final class BoxConstraints {}

    private static final class PreviewScale {}

    private static final class ScreenSize {}

    private static final class BoxData {
        public BoxConstraints getSizeConstraints() {
            return null;
        }
    }

    private static final class RenamedPositioner {
        private Range<Float> measure(int width, int height, Rect area, BoxConstraints constraints) {
            return null;
        }

        private void resize(BoxData data, float scale) {}

        private boolean fits(BoxData data, ScreenSize screen) {
            return false;
        }

        private void map(BoxData data, PreviewScale scale, ScreenSize screen) {}

        private void wrongOrder(BoxData data, ScreenSize screen, PreviewScale scale) {}
    }

    private static class FailedDetector {
        int releases;

        public void init() {
            throw new IllegalStateException("Init failed, ret: 3");
        }

        public void release() {
            releases++;
        }
    }

    private static class SecondFailedDetector extends FailedDetector {
        @Override
        public void init() {
            throw new IllegalStateException("SRIB native library missing");
        }
    }

    private static class WorkingDetector {
        int inits, releases;

        public boolean isObjectCaptureSupported() {
            return false;
        }

        public void init() {
            inits++;
        }

        public void release() {
            releases++;
        }
    }

    public static void main(String[] args) throws Exception {
        check(
                NativeContract.field(Reference.class, "W0", String.class).getType() == String.class,
                "Private field rejected");
        missing(
                () -> NativeContract.field(OtherBuild.class, "W0", String.class),
                "Same name accepted with wrong field type");
        missing(
                () -> NativeContract.field(Reference.class, "staticField", int.class),
                "Static field accepted as instance state");
        missing(
                () -> NativeContract.method(OtherBuild.class, "update", void.class, String.class),
                "Wrong return type accepted");
        missing(
                () -> NativeContract.method(Reference.class, "update", void.class, Object.class),
                "Different parameter accepted");
        missing(
                () -> NativeContract.method(Reference.class, "staticMethod", void.class),
                "Static method accepted as instance operation");
        check(
                NativeContract.method(Reference.class, "update", void.class, String.class)
                                .getReturnType()
                        == void.class,
                "Exact method rejected");

        List<Integer> removed = new ArrayList<>();
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(() -> removed.add(1));
            hooks.add(() -> removed.add(2));
        }
        check(removed.equals(List.of(2, 1)), "Partial hooks did not roll back in reverse order");
        removed.clear();
        try (HookTransaction hooks = new HookTransaction()) {
            hooks.add(() -> removed.add(1));
            hooks.commit();
        }
        check(removed.isEmpty(), "Committed installation was removed");
        HookTransaction failing = new HookTransaction();
        failing.add(() -> removed.add(1));
        failing.add(
                () -> {
                    throw new IllegalStateException("Unhook failed");
                });
        try {
            failing.close();
            throw new AssertionError("Lost rollback failure");
        } catch (IllegalStateException expected) {
            checks++;
        }
        check(removed.equals(List.of(1)), "One unhook failure prevented remaining cleanup");
        failing.close();
        check(removed.equals(List.of(1)), "Rollback ran twice");

        removed.clear();
        HookTransaction incompatible = new HookTransaction();
        NoSuchMethodError linkage = new NoSuchMethodError("Old hook API");
        incompatible.add(() -> removed.add(3));
        incompatible.add(
                () -> {
                    throw linkage;
                });
        incompatible.add(
                () -> {
                    throw linkage;
                });
        try {
            incompatible.close();
            throw new AssertionError("Lost linkage failure");
        } catch (NoSuchMethodError expected) {
            check(expected == linkage, "Rollback replaced the original linkage error");
        }
        check(removed.equals(List.of(3)), "Linkage/self-suppression failure stopped cleanup");
        incompatible.close();
        check(removed.equals(List.of(3)), "Failed compatibility rollback ran twice");

        NativeSaveRequests<String> saves = new NativeSaveRequests<>();
        check(
                NativeContract.findField(Renamed.class, "old", String.class)
                        .getName()
                        .equals("value"),
                "Unique renamed field not found");
        check(
                NativeContract.findMethod(Renamed.class, "old", void.class, String.class)
                        .getName()
                        .equals("renamed"),
                "Unique renamed method not found");
        check(
                NativeContract.findStaticMethod(Renamed.class, "old", long.class, int.class)
                        .getName()
                        .equals("renamedStatic"),
                "Unique renamed static not found");
        EditorBinding.ClockBoxMethods clockBox = EditorBinding.clockBoxMethods(
                RenamedPositioner.class, BoxData.class, PreviewScale.class, ScreenSize.class);
        check(clockBox.limit().getName().equals("measure"), "Renamed clock scale limit not found");
        check(clockBox.scale().getName().equals("resize"), "Renamed clock scale setter not found");
        check(clockBox.valid().getName().equals("fits"), "Renamed clock validation not found");
        check(clockBox.mapPreview().getName().equals("map"), "Renamed clock positioner not found");
        missing(
                () -> NativeContract.findField(Ambiguous.class, "old", String.class),
                "Ambiguous field accepted");
        missing(
                () -> NativeContract.findMethod(Ambiguous.class, "old", void.class, String.class),
                "Ambiguous method accepted");
        missing(
                () ->
                        NativeContract.findStaticMethod(
                                Ambiguous.class, "old", long.class, int.class),
                "Ambiguous static accepted");
        missing(
                () -> NativeContract.findMethodPrefix(Ambiguous.class, "old", void.class),
                "Ambiguous prefix accepted");
        missing(
                () ->
                        NativeContract.findMethod(
                                Ambiguous.class, "overloaded", void.class, (Class<?>) null),
                "Named wildcard overload selected arbitrarily");
        check(
                NativeContract.findField(Ambiguous.class, "second", String.class)
                        .getName()
                        .equals("second"),
                "Verified field mapping lost its tie-breaker");
        check(
                NativeContract.findMethod(Ambiguous.class, "second", void.class, String.class)
                        .getName()
                        .equals("second"),
                "Verified method mapping lost its tie-breaker");

        missing(
                () -> NativeContract.findMethod(Renamed.class, "renamed", void.class, Object.class),
                "Narrower parameter accepted for a wider argument contract");
        missing(
                () ->
                        NativeContract.findMethodPrefix(
                                Renamed.class, "renamed", void.class, Object.class),
                "Narrower prefix parameter accepted");
        check(
                NativeContract.knownClass(
                                AdapterContractsTest.class.getClassLoader(),
                                type -> NativeContract.hasMethod(type, void.class, String.class),
                                OtherBuild.class.getName(),
                                Renamed.class.getName())
                        == Renamed.class,
                "Known class accepted an incompatible candidate");
        check(
                NativeContract.knownClass(
                                AdapterContractsTest.class.getClassLoader(),
                                type -> NativeContract.hasMethod(type, void.class, String.class),
                                OtherBuild.class.getName())
                        == null,
                "Failed known lookup did not leave discovery available");
        FailedDetector primary = new FailedDetector();
        WorkingDetector fallback = new WorkingDetector();
        var rejected = new ArrayList<Object>();
        Object detector =
                ObjectCaptureBackend.firstUsable(List.of(() -> primary, () -> fallback), rejected);
        check(detector == fallback, "Failed native detector blocked the Samsung fallback");
        check(fallback.inits == 1, "Support-report false prevented native initialization");
        check(primary.releases == 1, "Failed detector not released");
        check(fallback.releases == 0, "Live detector was released before capture");
        check(rejected.size() == 1 && rejected.get(0) == primary, "Failed detector identity lost");
        WorkingDetector afterMissingLibrary = new WorkingDetector();
        check(
                ObjectCaptureBackend.firstUsable(
                                List.of(
                                        () -> {
                                            throw new UnsatisfiedLinkError("missing library");
                                        },
                                        () -> afterMissingLibrary),
                                new ArrayList<>())
                        == afterMissingLibrary,
                "Missing native library blocked fallback");
        try {
            ObjectCaptureBackend.firstUsable(
                    List.of(() -> new FailedDetector()), new ArrayList<>());
            throw new AssertionError("All failed detectors reported success");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("ret: 3"), "Native failure reason lost");
        }

        try {
            ObjectCaptureBackend.firstUsable(
                    List.of(() -> new FailedDetector(), () -> new SecondFailedDetector()),
                    new ArrayList<>());
            throw new AssertionError("Both failed backends reported success");
        } catch (IllegalStateException expected) {
            check(
                    expected.getMessage().contains("ret: 3")
                            && expected.getMessage().contains("SRIB native library missing"),
                    "Exhausted fallback hid one of the backend errors");
        }

        var nestedHooks = new ArrayList<Integer>();
        try (HookTransaction parent = new HookTransaction()) {
            parent.add(() -> nestedHooks.add(1));
            try (HookTransaction child = new HookTransaction()) {
                child.add(() -> nestedHooks.add(2));
                child.commitTo(parent);
            }
            check(nestedHooks.isEmpty(), "Child hooks rolled back before parent completed");
        }
        check(nestedHooks.equals(List.of(2, 1)), "Transferred hooks escaped parent rollback");

        FailedDetector recoverable = new FailedDetector();
        var recoveryOrder = new ArrayList<String>();
        Object recovered =
                ObjectCaptureBackend.firstUsable(
                        List.of(
                                () -> recoverable,
                                () -> {
                                    recoveryOrder.add("fallback");
                                    return new WorkingDetector();
                                }),
                        new ArrayList<>(),
                        candidate -> {
                            recoveryOrder.add("recovery");
                            return true;
                        });
        check(
                recovered == recoverable && recoveryOrder.equals(List.of("recovery")),
                "Primary recovery ran after loading the fallback backend");
        check(recoverable.releases == 0, "Recovered detector was released before capture");

        WorkingDetector emptyScan = new WorkingDetector();
        WorkingDetector repeatedScan = new WorkingDetector();
        WorkingDetector alternateScan = new WorkingDetector() {};
        String subject = ObjectCaptureBackend.choose(
                List.of(() -> emptyScan, () -> repeatedScan, () -> alternateScan),
                new ArrayList<>(), candidate -> false,
                candidate -> candidate == alternateScan ? "subject" : null, false);
        check("subject".equals(subject), "An empty scan blocked the alternate backend");
        check(emptyScan.releases == 1 && alternateScan.releases == 1,
                "Detection must release both engines");
        check(repeatedScan.inits == 0, "Provider and direct factory retried the same backend");

        Object first = new EqualRequest(), second = new EqualRequest();
        saves.begin(first, "first");
        saves.begin(second, "second");
        check("second".equals(saves.complete(second)), "A second Apply consumed another request");
        check("first".equals(saves.complete(first)), "Suspended Apply lost its snapshot");
        check(saves.complete(first) == null, "Apply completed twice");
        saves.begin(first, "canceled");
        saves.fail(first);
        check(saves.complete(first) == null, "Failed native save was committed");
        saves.begin(first, "old editor");
        saves.clear();
        check(saves.complete(first) == null, "A new editor inherited a suspended save");

        var writeOwner = "Lcom/samsung/android/uniform/widget/clock/AbsClockView;";
        var ctrlDesc = "Laod/ctrl;";
        var writesBody =
                new DexSignatures.Body(
                        writeOwner,
                        "setMediaExpanded",
                        List.of("Z"),
                        "V",
                        0,
                        java.util.Map.of(),
                        List.of(),
                        List.of(
                                new DexSignatures.Write(
                                        1, new DexSignatures.Member(writeOwner, "c1", "Z")),
                                new DexSignatures.Write(
                                        2, new DexSignatures.Member(ctrlDesc, "h", "Z"))),
                        List.of());
        check(
                writesBody.uniqueWrite(writeOwner, "Z", null).name().equals("c1")
                        && writesBody.uniqueWrite(ctrlDesc, "Z", null).name().equals("h"),
                "Unique field write resolution failed");
        missing(
                () -> writesBody.uniqueWrite(writeOwner, "F", null),
                "Missing field write accepted");
        check(
                AodStretchExtras.preserveHeight(true, false, true, 0f, true)
                        && !AodStretchExtras.preserveHeight(true, true, true, 1f, false)
                        && !AodStretchExtras.preserveHeight(true, false, false, 0f, true)
                        && AodStretchExtras.preserveHeight(true, false, false, 0.25f, true),
                "AOD height transition preservation contract changed");
        check(
                SavedStretch.heights(new float[] {1.5f, 2.0f}).equals(List.of(1.5f, 2.0f))
                        && SavedStretch.heights(new float[0]) == null
                        && SavedStretch.heights(new float[] {0f, 1.5f}) == null
                        && SavedStretch.heights(new float[] {Float.NaN, 1.5f}) == null,
                "Saved stretch heights contract changed");
        System.out.println("Adapter contracts: " + checks + " checks passed");
    }
}
