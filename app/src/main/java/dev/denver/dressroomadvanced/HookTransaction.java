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

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (committed) return;
        RuntimeException failure = null;
        for (int i = rollback.size() - 1; i >= 0; i--) {
            try {
                rollback.get(i).run();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        rollback.clear();
        if (failure != null) throw failure;
    }
}
