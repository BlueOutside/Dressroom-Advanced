package dev.denver.dressroomadvanced;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** A queued native retry belongs to one request, not the manager's next use. */
final class BlurRetryState {
    record Ticket(WeakReference<Object> owner, Object generation) {}

    private final Map<Object, Object> generations = new WeakHashMap<>();

    synchronized Ticket begin(Object owner) {
        Object generation = new Object();
        generations.put(owner, generation);
        return new Ticket(new WeakReference<>(owner), generation);
    }

    synchronized Ticket current(Object owner) {
        Object generation = generations.get(owner);
        return generation == null ? null : new Ticket(new WeakReference<>(owner), generation);
    }

    synchronized boolean accepts(Ticket ticket) {
        if (ticket == null) return false;
        Object owner = ticket.owner().get();
        return owner != null && generations.get(owner) == ticket.generation();
    }

    synchronized void cancel(Object owner) {
        generations.remove(owner);
    }
}
