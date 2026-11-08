package dev.denver.dressroomadvanced;

import java.util.function.Consumer;

public final class FoldStateMonitorTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        int[] attempts = {0}, events = {0};
        @SuppressWarnings("unchecked") Consumer<Boolean>[] callback = new Consumer[1];
        FoldStateMonitor monitor = new FoldStateMonitor(new FoldStateMonitor.Backend() {
            public boolean folded() { return attempts[0] > 0; }
            public void register(Consumer<Boolean> value) {
                if (++attempts[0] == 1) throw new IllegalStateException("Transient subscription failure");
                callback[0] = value;
            }
        }, () -> events[0]++);
        boolean failed = false;
        try { monitor.current(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, "Registration failure was hidden");
        check(monitor.current() == 2 && attempts[0] == 2, "Retry reused a stale state sample");
        callback[0].accept(false);
        check(monitor.current() == 1, "Retried subscription lost unfold callbacks");
        check(attempts[0] == 2, "Successful subscription was installed twice");
        check(events[0] == 1, "Unfold event was not delivered once");
        callback[0].accept(false);
        check(events[0] == 1, "Duplicate state emitted an extra notification");
        callback[0].accept(true);
        check(monitor.current() == 2 && events[0] == 2, "Cover event did not update identity");
        FoldStateMonitor synchronous = new FoldStateMonitor(new FoldStateMonitor.Backend() {
            public boolean folded() { return false; }
            public void register(Consumer<Boolean> value) { value.accept(true); }
        }, () -> {});
        check(synchronous.current() == 2, "Initial read overwrote registration-time callback");
        System.out.println("Fold subscription retry: " + checks + " checks passed");
    }
}
