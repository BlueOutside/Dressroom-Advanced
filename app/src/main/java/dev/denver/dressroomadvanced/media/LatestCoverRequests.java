package dev.denver.dressroomadvanced;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Deduplicates running requests and keeps only the newest waiting cover. */
final class LatestCoverRequests<T> {
    private final Executor executor;
    private final int parallelism;
    private final Map<String, Job<T>> jobs = new HashMap<>();
    private int running;
    private Job<T> pending;

    private record Job<T>(String key, Supplier<T> load, CompletableFuture<T> result) {}

    LatestCoverRequests(Executor executor, int parallelism) {
        this.executor = executor;
        this.parallelism = parallelism;
    }

    CompletableFuture<T> request(String key, Supplier<T> load) {
        Job<T> job, dropped = null;
        boolean start;
        synchronized (this) {
            Job<T> existing = jobs.get(key);
            if (existing != null) return existing.result;
            job = new Job<>(key, load, new CompletableFuture<>());
            jobs.put(key, job);
            start = running < parallelism;
            if (start) running++;
            else {
                dropped = pending;
                pending = job;
                if (dropped != null) jobs.remove(dropped.key, dropped);
            }
        }
        if (dropped != null) dropped.result.complete(null);
        if (start) execute(job);
        return job.result;
    }

    private void execute(Job<T> job) {
        try {
            executor.execute(() -> {
                try {
                    job.result.complete(job.load.get());
                } catch (Throwable error) {
                    job.result.completeExceptionally(error);
                } finally {
                    finished(job);
                }
            });
        } catch (RuntimeException rejected) {
            job.result.completeExceptionally(rejected);
            finished(job);
        }
    }

    private void finished(Job<T> job) {
        Job<T> next;
        synchronized (this) {
            jobs.remove(job.key, job);
            running--;
            next = pending;
            pending = null;
            if (next != null) running++;
        }
        if (next != null) execute(next);
    }
}
