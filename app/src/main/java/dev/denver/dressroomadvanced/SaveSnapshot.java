package dev.denver.dressroomadvanced;

/** Holds the exact settings frozen for an in-flight native save, not a later draft. */
final class SaveSnapshot<T> {
    private T frozen;
    // Native Apply finishes asynchronously. Reading the editor again on completion
    // would commit whatever the user changed after tapping Apply.
    synchronized void begin(T immutableSnapshot) { frozen = immutableSnapshot; }
    synchronized T complete() { T value = frozen; frozen = null; return value; }
    synchronized void cancel() { frozen = null; }
}
