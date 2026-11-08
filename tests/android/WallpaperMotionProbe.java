package dev.denver.dressroomadvanced;

import android.os.SharedMemory;
import java.nio.ByteOrder;

public final class WallpaperMotionProbe {
    public static void main(String[] args) throws Exception {
        try (SharedMemory shared = SharedMemory.create("Dressroom-motion-check", WallpaperMotionPacket.SIZE)) {
            var writer = shared.mapReadWrite().order(ByteOrder.nativeOrder());
            var reader = shared.mapReadOnly().order(ByteOrder.nativeOrder());
            try {
                var expected = new WallpaperMotionPacket.Frame(0, 47, 6, 1440, 3120,
                        1.05f, 1.05f, -.025f, -.025f);
                if (!WallpaperMotionPacket.write(writer, expected)) throw new AssertionError("Publish failed");
                var actual = WallpaperMotionPacket.read(reader, WallpaperMotionPacket.slot(6));
                if (actual == null || !actual.fits(47, 1080, 2340)
                        || actual.scaleX() != 1.05f || actual.offsetX() != -.025f)
                    throw new AssertionError("Shared matrix was not visible to the reader");
                boolean protectedMemory = false;
                if (!reader.isReadOnly()) throw new AssertionError("Reader mapping is not read-only");
                try { reader.putInt(0, 0); }
                catch (java.nio.ReadOnlyBufferException expectedFailure) { protectedMemory = true; }
                if (!protectedMemory) throw new AssertionError("Reader can write the mapping");
            } finally {
                SharedMemory.unmap(writer);
                SharedMemory.unmap(reader);
            }
        }
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        try (WallpaperMotionChannel channel = new WallpaperMotionChannel(() -> {})) {
            android.os.Parcel parcel = android.os.Parcel.obtain();
            try {
                parcel.writeBundle(channel.message());
                parcel.setDataPosition(0);
                android.os.Bundle transported = parcel.readBundle(android.os.ResultReceiver.class.getClassLoader());
                android.os.ResultReceiver receiver = transported.getParcelable("receiver", android.os.ResultReceiver.class);
                if (receiver == null || receiver.getClass() != android.os.ResultReceiver.class)
                    throw new AssertionError("Callback requires the module class loader");
                try (WallpaperMotionChannel.Sink sink = new WallpaperMotionChannel.Sink(transported)) {
                    sink.write(new WallpaperMotionPacket.Frame(0, 47, 6, 1440, 3120,
                            1.05f, 1.05f, -.025f, -.025f));
                    var frame = channel.read(WallpaperMotionPacket.slot(6));
                    if (frame == null || frame.scaleX() != 1.05f)
                        throw new AssertionError("Transported memory handle does not share frames");
                }
            } finally { parcel.recycle(); }
        }
        if (args.length > 0) {
            ClassLoader loader = new dalvik.system.PathClassLoader(args[0],
                    WallpaperMotionProbe.class.getClassLoader());
            WallpaperMotionBinding binding = new WallpaperMotionBinding(loader);
            System.out.println("Native wallpaper draw contracts resolved: "
                    + binding.engine.getDeclaringClass().getName() + ", "
                    + binding.bitmap.getDeclaringClass().getName());
        }
        System.out.println("Android shared wallpaper motion and framework parcel checks passed");
        System.exit(0);
    }
}
