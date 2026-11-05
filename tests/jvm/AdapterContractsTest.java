package dev.denver.dressroomadvanced;

import java.util.ArrayList;
import java.util.List;

public final class AdapterContractsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private interface Checked { void run() throws Exception; }
    private static void missing(Checked action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); }
        catch (NoSuchFieldException | NoSuchMethodException expected) { checks++; }
    }
    private static final class Reference {
        private String W0;
        private static int staticField;
        private void update(String value) {}
        private static void staticMethod() {}
    }
    private static final class OtherBuild {
        private boolean W0;
        private int update(String value) { return 0; }
    }
    private static final class EqualRequest {
        @Override public boolean equals(Object other) { return other instanceof EqualRequest; }
        @Override public int hashCode() { return 1; }
    }
    public static void main(String[] args) throws Exception {
        check(NativeContract.field(Reference.class, "W0", String.class).getType() == String.class, "Private field rejected");
        missing(() -> NativeContract.field(OtherBuild.class, "W0", String.class), "Same name accepted with wrong field type");
        missing(() -> NativeContract.field(Reference.class, "staticField", int.class), "Static field accepted as instance state");
        missing(() -> NativeContract.method(OtherBuild.class, "update", void.class, String.class), "Wrong return type accepted");
        missing(() -> NativeContract.method(Reference.class, "update", void.class, Object.class), "Different parameter accepted");
        missing(() -> NativeContract.method(Reference.class, "staticMethod", void.class), "Static method accepted as instance operation");
        check(NativeContract.method(Reference.class, "update", void.class, String.class).getReturnType() == void.class, "Exact method rejected");

        List<Integer> removed = new ArrayList<>();
        try (HookTransaction hooks = new HookTransaction()) { hooks.add(() -> removed.add(1)); hooks.add(() -> removed.add(2)); }
        check(removed.equals(List.of(2, 1)), "Partial hooks did not roll back in reverse order");
        removed.clear();
        try (HookTransaction hooks = new HookTransaction()) { hooks.add(() -> removed.add(1)); hooks.commit(); }
        check(removed.isEmpty(), "Committed installation was removed");
        HookTransaction failing = new HookTransaction();
        failing.add(() -> removed.add(1));
        failing.add(() -> { throw new IllegalStateException("Unhook failed"); });
        try { failing.close(); throw new AssertionError("Lost rollback failure"); }
        catch (IllegalStateException expected) { checks++; }
        check(removed.equals(List.of(1)), "One unhook failure prevented remaining cleanup");
        failing.close();
        check(removed.equals(List.of(1)), "Rollback ran twice");

        NativeSaveRequests<String> saves = new NativeSaveRequests<>();
        Object first = new EqualRequest(), second = new EqualRequest();
        saves.begin(first, "first"); saves.begin(second, "second");
        check("second".equals(saves.complete(second)), "A second Apply consumed another request");
        check("first".equals(saves.complete(first)), "Suspended Apply lost its snapshot");
        check(saves.complete(first) == null, "Apply completed twice");
        saves.begin(first, "canceled"); saves.fail(first);
        check(saves.complete(first) == null, "Failed native save was committed");
        saves.begin(first, "old editor"); saves.clear();
        check(saves.complete(first) == null, "A new editor inherited a suspended save");
        System.out.println("Adapter contracts: " + checks + " checks passed");
    }
}
