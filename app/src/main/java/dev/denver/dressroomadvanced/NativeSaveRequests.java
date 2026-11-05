package dev.denver.dressroomadvanced;

import java.util.IdentityHashMap;

/** A suspended Apply owns its snapshot; a different callback must not consume it. */
final class NativeSaveRequests<T> {
    private final IdentityHashMap<Object, T> requests = new IdentityHashMap<>();
    synchronized void begin(Object request, T snapshot) { requests.put(request, snapshot); }
    synchronized T complete(Object request) { return requests.remove(request); }
    synchronized void fail(Object request) { requests.remove(request); }
    synchronized void clear() { requests.clear(); }
}
