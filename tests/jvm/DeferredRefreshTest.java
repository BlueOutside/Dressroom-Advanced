package dev.denver.dressroomadvanced;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class DeferredRefreshTest {
    private static int checks;

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        List<Boolean> calls = new ArrayList<>();
        Object owner = new Object(), other = new Object();
        DeferredRefresh<Object> refresh =
                new DeferredRefresh<>(jobs::add, (value, force) -> calls.add(force));
        refresh.offer(owner, false);
        refresh.offer(owner, true);
        refresh.offer(owner, false);
        check(jobs.size() == 1, "Nested updates did not coalesce");
        jobs.remove().run();
        check(calls.equals(List.of(true)), "A weaker update discarded force");
        refresh.offer(owner, true);
        refresh.cancel(owner);
        jobs.remove().run();
        check(calls.size() == 1, "Release ran pending work");
        refresh.offer(owner, true);
        refresh.cancel(owner);
        refresh.offer(owner, false);
        jobs.remove().run();
        check(calls.size() == 1, "Old lifecycle consumed the new ticket");
        check(jobs.size() == 1, "New lifecycle lost its scheduled callback");
        jobs.remove().run();
        check(calls.equals(List.of(true, false)), "Old force flag leaked into reinitialized owner");
        refresh.offer(owner, false);
        refresh.offer(other, true);
        refresh.cancel(owner);
        jobs.remove().run();
        jobs.remove().run();
        check(calls.equals(List.of(true, false, true)), "Releasing one owner cancelled another");
        @SuppressWarnings("unchecked")
        DeferredRefresh<Object>[] nested = new DeferredRefresh[1];
        nested[0] =
                new DeferredRefresh<>(
                        jobs::add,
                        (value, force) -> {
                            calls.add(force);
                            if (force) nested[0].offer(value, false);
                        });
        nested[0].offer(owner, true);
        jobs.remove().run();
        check(jobs.size() == 1, "Refresh lost a reentrant native update");
        jobs.remove().run();
        check(!calls.get(calls.size() - 1), "Reentrant callback was not delivered");
        DeferredRefresh<Object> failure =
                new DeferredRefresh<>(
                        jobs::add,
                        (value, force) -> {
                            throw new IllegalStateException("Expected");
                        });
        failure.offer(owner, false);
        try {
            jobs.remove().run();
            throw new AssertionError("Failure hidden");
        } catch (IllegalStateException expected) {
            checks++;
        }
        failure.offer(owner, true);
        check(jobs.size() == 1, "Failed refresh permanently blocked its owner");
        failure.cancel(owner);
        jobs.remove().run();
        check(jobs.isEmpty(), "Cancelled failure retried");

        java.util.concurrent.atomic.AtomicBoolean reject =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        DeferredRefresh<Object> retry =
                new DeferredRefresh<>(
                        task -> {
                            if (reject.getAndSet(false))
                                throw new java.util.concurrent.RejectedExecutionException();
                            jobs.add(task);
                        },
                        (value, force) -> calls.add(force));
        try {
            retry.offer(owner, true);
            throw new AssertionError("Scheduler rejection hidden");
        } catch (java.util.concurrent.RejectedExecutionException expected) {
            checks++;
        }
        retry.offer(owner, false);
        check(jobs.size() == 1, "Rejected scheduling permanently blocked its owner");
        jobs.remove().run();
        check(!calls.get(calls.size() - 1), "Rejected ticket leaked its force flag");

        var concurrentJobs = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        var seen = new ArrayList<Boolean>();
        DeferredRefresh<Object> concurrent =
                new DeferredRefresh<>(concurrentJobs::add, (value, force) -> seen.add(force));
        var gate = new java.util.concurrent.CountDownLatch(1);
        var fault = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread[] threads = new Thread[6];
        for (int i = 0; i < threads.length; i++) {
            final boolean strong = i % 2 == 0;
            threads[i] =
                    new Thread(
                            () -> {
                                try {
                                    gate.await();
                                    for (int j = 0; j < 200; j++) concurrent.offer(owner, strong);
                                } catch (Throwable error) {
                                    fault.compareAndSet(null, error);
                                }
                            });
            threads[i].start();
        }
        gate.countDown();
        for (Thread thread : threads) thread.join();
        check(fault.get() == null, "Concurrent offers failed: " + fault.get());
        check(concurrentJobs.size() == 1, "Concurrent callbacks were not coalesced");
        concurrentJobs.remove().run();
        check(seen.equals(List.of(true)), "Concurrent updates lost the force request");
        System.out.println("Deferred refresh lifecycle: " + checks + " checks passed");
    }
}
