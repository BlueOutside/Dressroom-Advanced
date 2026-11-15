package dev.denver.dressroomadvanced;

import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.os.Bundle;

public final class DepthStorageReadProbe {
    private static final class SourceRequested extends AssertionError {}

    private static ContextWrapper caller(String name) {
        return new ContextWrapper(null) {
            @Override
            public String getOpPackageName() {
                return name;
            }

            @Override
            public ContentResolver getContentResolver() {
                throw new SourceRequested();
            }
        };
    }

    public static void main(String[] args) {
        Bundle packed = new Bundle();
        packed.putString(DepthMetadata.SOURCE + "Ref", "a".repeat(64));
        packed.putByteArray("depthMask", new byte[] {1, 2});
        packed.putBoolean("depthEnabled", true);
        int checks = 0;
        for (String name :
                new String[] {
                    "com.android.systemui",
                    "com.samsung.android.app.aodservice",
                    "com.samsung.android.app.clockpack"
                }) {
            Bundle result = DepthStorage.unpack(caller(name), packed);
            if (result.getByteArray(DepthMetadata.SOURCE) != null
                    || result.getByteArray("depthMask").length != 2
                    || !result.getBoolean("depthEnabled"))
                throw new AssertionError("Runtime source omission changed the mask: " + name);
            checks++;
        }
        try {
            DepthStorage.unpack(caller("com.samsung.android.app.dressroom"), packed);
            throw new AssertionError("Editor source restoration was disabled");
        } catch (SourceRequested expected) {
            checks++;
        }
        System.out.println("Runtime/editor depth reads: " + checks + " checks passed");
    }
}
