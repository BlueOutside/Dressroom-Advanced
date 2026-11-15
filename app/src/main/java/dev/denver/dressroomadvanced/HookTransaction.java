package dev.denver.dressroomadvanced;

import java.util.ArrayList;
import java.util.List;

final class HookTransaction implements AutoCloseable {
    private final List<Runnable> rollback = new ArrayList<>();
    private boolean committed, closed;

    void add(Runnable unhook) {
        if (closed || committed)
            throw new IllegalStateException("Hook transaction already finished");
        rollback.add(java.util.Objects.requireNonNull(unhook));
    }

    void commit() {
        if (closed || committed)
            throw new IllegalStateException("Hook transaction already finished");
        committed = true;
        rollback.clear();
    }

    void commitTo(HookTransaction parent) {
        if (parent == this || closed || committed || parent.closed || parent.committed)
            throw new IllegalStateException("Hook transaction already finished");
        parent.rollback.addAll(rollback);
        commit();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (committed) return;
        Throwable failure = null;
        for (int i = rollback.size() - 1; i >= 0; i--) {
            try {
                rollback.get(i).run();
            } catch (RuntimeException | LinkageError error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        rollback.clear();
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof LinkageError error) throw error;
    }
}
