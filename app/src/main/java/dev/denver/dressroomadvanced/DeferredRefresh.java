package dev.denver.dressroomadvanced;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

final class DeferredRefresh<T> {
    private static final class Ticket {
        boolean force;

        Ticket(boolean force) {
            this.force = force;
        }
    }

    private final Map<T, Ticket> pending = new WeakHashMap<>();
    private final Executor executor;
    private final BiConsumer<T, Boolean> refresh;

    DeferredRefresh(Executor executor, BiConsumer<T, Boolean> refresh) {
        this.executor = executor;
        this.refresh = refresh;
    }

    void offer(T owner, boolean force) {
        Ticket ticket;
        synchronized (pending) {
            Ticket existing = pending.get(owner);
            if (existing != null) {
                existing.force |= force;
                return;
            }
            ticket = new Ticket(force);
            pending.put(owner, ticket);
        }
        WeakReference<T> reference = new WeakReference<>(owner);
        try {
            executor.execute(
                    () -> {
                        T value = reference.get();
                        boolean requested;
                        synchronized (pending) {
                            if (value == null || pending.get(value) != ticket) return;
                            pending.remove(value);
                            requested = ticket.force;
                        }
                        refresh.accept(value, requested);
                    });
        } catch (RuntimeException | LinkageError error) {
            synchronized (pending) {
                if (pending.get(owner) == ticket) pending.remove(owner);
            }
            throw error;
        }
    }

    void cancel(T owner) {
        synchronized (pending) {
            pending.remove(owner);
        }
    }
}
