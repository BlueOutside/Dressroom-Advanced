package dev.denver.dressroomadvanced;

public final class BlurRetryStateTest {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        BlurRetryState state = new BlurRetryState();
        Object clock = new Object(), other = new Object();
        check(state.current(clock) == null, "Unobserved native work gained a ticket");
        check(!state.accepts(null), "Missing ticket accepted");
        BlurRetryState.Ticket first = state.begin(clock);
        BlurRetryState.Ticket queued = state.current(clock);
        BlurRetryState.Ticket independent = state.begin(other);
        check(state.accepts(first) && state.accepts(queued), "Current retry rejected");
        BlurRetryState.Ticket second = state.begin(clock);
        check(!state.accepts(first) && !state.accepts(queued), "Older request survived replacement");
        check(state.accepts(second), "Replacement request rejected");
        check(state.accepts(independent), "Another clock invalidated");
        state.cancel(clock);
        check(!state.accepts(second), "Detached or disabled clock retained retry");
        check(state.current(clock) == null, "Cancellation retained pending request");
        BlurRetryState.Ticket reattached = state.begin(clock);
        check(!state.accepts(second), "Reattach revived an old request");
        check(state.accepts(reattached), "Reattached clock rejected new request");
        state.cancel(clock);
        state.cancel(clock);
        check(!state.accepts(reattached), "Repeated cancellation revived retry");
        BlurRetryState.Ticket retired = state.begin(new Object());
        retired.owner().clear();
        check(!state.accepts(retired), "Retired manager accepted retry");
        check(state.accepts(independent), "Retirement leaked into another clock");
        System.out.println("Native blur retry generations: " + checks + " checks passed");
    }
}
