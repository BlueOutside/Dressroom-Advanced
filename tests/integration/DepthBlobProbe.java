package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

public final class DepthBlobProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (var method : android.content.ContentResolver.class.getDeclaredMethods())
                if (method.getName().equals("wrap")) System.out.println(method);
            for (var constructor :
                    android.content.ContentProviderClient.class.getDeclaredConstructors())
                System.out.println(constructor);
            for (var method : Class.forName("android.app.IActivityManager").getDeclaredMethods())
                if (method.getName().contains("ContentProviderExternal"))
                    System.out.println(method);
            return;
        }
        android.os.Looper.prepareMainLooper();
        Class<?> activity = Class.forName("android.app.ActivityThread");
        Object thread = activity.getMethod("systemMain").invoke(null);
        Context system = (Context) activity.getMethod("getSystemContext").invoke(thread);
        Object manager =
                Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> service = Class.forName("android.app.IActivityManager");
        android.os.Binder token = new android.os.Binder();
        Object holder =
                service.getMethod(
                                "getContentProviderExternal",
                                String.class,
                                int.class,
                                android.os.IBinder.class,
                                String.class)
                        .invoke(
                                manager,
                                SettingsProvider.URI.getAuthority(),
                                0,
                                token,
                                "depth-blob-probe");
        Object provider = holder.getClass().getField("provider").get(holder);
        android.content.ContentProviderClient client =
                (android.content.ContentProviderClient)
                        android.content.ContentProviderClient.class
                                .getConstructor(
                                        android.content.ContentResolver.class,
                                        Class.forName("android.content.IContentProvider"),
                                        boolean.class)
                                .newInstance(system.getContentResolver(), provider, true);
        android.content.ContentResolver resolver = android.content.ContentResolver.wrap(client);
        Context context =
                new android.content.ContextWrapper(system) {
                    @Override
                    public android.content.ContentResolver getContentResolver() {
                        return resolver;
                    }
                };
        byte[] large = new byte[1_200_000];
        new java.util.Random(19011).nextBytes(large);
        Bundle data = new Bundle();
        data.putByteArray("depthMask", large);
        data.putByteArray(DepthMetadata.SOURCE, new byte[] {10, 20, 30});
        Bundle packed = DepthStorage.pack(context, data);
        if (packed.containsKey("depthMask") || packed.containsKey(DepthMetadata.SOURCE))
            throw new AssertionError("Bytes crossed Binder");
        java.lang.reflect.Field field = DepthStorage.class.getDeclaredField("cache");
        field.setAccessible(true);
        ((android.util.LruCache<?, ?>) field.get(null)).evictAll();
        Bundle loaded = null;
        RuntimeException failure = null;
        for (int retry = 0; retry < 40; retry++)
            try {
                loaded = DepthStorage.unpack(context, packed);
                break;
            } catch (RuntimeException error) {
                failure = error;
                Thread.sleep(50);
            }
        if (loaded == null) throw failure;
        if (!java.util.Arrays.equals(large, loaded.getByteArray("depthMask")))
            throw new AssertionError("Large blob corrupted");
        if (!java.util.Arrays.equals(
                new byte[] {10, 20, 30}, loaded.getByteArray(DepthMetadata.SOURCE)))
            throw new AssertionError("Source blob corrupted");
        System.out.println("Mask file bridge: 1.2 MB roundtrip passed; profiles unchanged");
        service.getMethod(
                        "removeContentProviderExternalAsUser",
                        String.class,
                        android.os.IBinder.class,
                        int.class)
                .invoke(manager, SettingsProvider.URI.getAuthority(), token, 0);
        System.exit(0);
    }
}
