package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Source identity and invalidation belong to a screen, not the editor's current posture. */
final class EditorDepthSources {
    static final class State {
        final DepthSourcePolicy source = new DepthSourcePolicy();
        boolean known, image, reset;
        long revision;
        String primaryUri = "", externalUri = "";
        void invalidate() { revision++; reset = true; }
        String imageKey() {
            String key = source.key();
            return key == null ? "" : hash(key);
        }

        boolean observe(WallpaperSourceBinding.Source next) {
            String original = next.externalUri();
            boolean sameImage = next.image() && (original.isEmpty() || externalUri.isEmpty()
                    ? (next.key().equals(primaryUri) || next.key().equals(externalUri)
                            || (!original.isEmpty() && original.equals(primaryUri)))
                    : original.equals(externalUri));
            if (sameImage && original.isEmpty()) original = externalUri;
            // Dressroom can replace its working URI while retaining the gallery original.
            boolean changed = source.observe(original.isEmpty() ? next.key() : original);
            primaryUri = next.key();
            externalUri = original;
            return changed && !sameImage;
        }

        String profileKey(String savedKey) {
            return image && !externalUri.isEmpty() && !primaryUri.isEmpty()
                    && hash(primaryUri).equals(savedKey)
                    ? imageKey() : savedKey;
        }

        private static String hash(String key) {
            return DepthStorage.hash(key.getBytes(StandardCharsets.UTF_8));
        }
    }
    private final Map<Integer, State> screens = new HashMap<>();
    State screen(int screen) { return screens.computeIfAbsent(screen, ignored -> new State()); }
    void clear() { screens.clear(); }
    static void forScreen(Iterable<Bundle> profiles, int screen, Consumer<Bundle> action) {
        for (Bundle profile : profiles)
            if (profile != null && ScreenIdentity.value(profile) == screen) action.accept(profile);
    }
    void stamp(Bundle snapshot) {
        var layouts = snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            for (Bundle layout : layouts) stamp(layout);
            return;
        }
        State state = screens.get(ScreenIdentity.value(snapshot));
        if (state != null && state.known) snapshot.putString("depthImageKey", state.imageKey());
    }
}
