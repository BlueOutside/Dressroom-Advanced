package dev.denver.dressroomadvanced;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class DeferredDiscovery<T> {
    private final Map<T, Boolean> seen = new IdentityHashMap<>();
    private final Consumer<Runnable> post;
    private final Predicate<T> install;

    DeferredDiscovery(Consumer<Runnable> post, Predicate<T> install) {
        this.post = post;
        this.install = install;
    }

    void offer(T key) {
        synchronized (this) {
            if (seen.containsKey(key)) return;
            seen.put(key, false);
        }
        try {
            post.accept(
                    () -> {
                        boolean done = false;
                        try {
                            done = install.test(key);
                        } finally {
                            synchronized (this) {
                                if (done) seen.put(key, true);
                                else seen.remove(key);
                            }
                        }
                    });
        } catch (RuntimeException e) {
            synchronized (this) {
                seen.remove(key);
            }
            throw e;
        }
    }
}
