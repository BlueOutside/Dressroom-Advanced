package dev.denver.dressroomadvanced;

import java.util.IdentityHashMap;

final class NativeSaveRequests<T> {
    private final IdentityHashMap<Object, T> requests = new IdentityHashMap<>();

    synchronized void begin(Object request, T snapshot) {
        requests.put(request, snapshot);
    }

    synchronized T complete(Object request) {
        return requests.remove(request);
    }

    synchronized void fail(Object request) {
        requests.remove(request);
    }

    synchronized void clear() {
        requests.clear();
    }
}
