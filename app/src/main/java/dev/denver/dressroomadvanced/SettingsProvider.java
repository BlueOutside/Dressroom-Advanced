package dev.denver.dressroomadvanced;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SettingsProvider extends ContentProvider {
    private final Object maskReady = new Object();
    private final java.util.concurrent.ExecutorService maskWriter =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    static final Uri URI = Uri.parse("content://dev.denver.dressroomadvanced.settings");
    private static final Set<String> READERS =
            new HashSet<>(
                    Arrays.asList(
                            "com.android.systemui",
                            "com.samsung.android.app.aodservice",
                            "com.samsung.android.app.clockpack",
                            "com.samsung.android.app.dressroom"));

    @Override
    public boolean onCreate() {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> getContext().getContentResolver().notifyChange(URI, null));
        return true;
    }

    private void checkCaller(boolean write) {
        if (Binder.getCallingUid() == android.os.Process.myUid()) return;
        String[] packages =
                getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null)
            for (String name : packages)
                if (write
                        ? name.equals("com.samsung.android.app.dressroom")
                        : READERS.contains(name)) return;
        throw new SecurityException("Unsupported caller");
    }

    @Override
    public synchronized Bundle call(String method, String argument, Bundle extras) {
        boolean write =
                "commit".equals(method)
                        || "commitWidgets".equals(method)
                        || "commitExtras".equals(method);
        checkCaller(write);
        if (write && extras != null) awaitMasks(extras);
        if ("readExtras".equals(method) || "commitExtras".equals(method)) {
            try {
                return ExtrasPreferences.store(getContext(), method, extras);
            } catch (org.json.JSONException e) {
                throw new IllegalArgumentException("Invalid extra settings", e);
            }
        }
        if (extras != null && !extras.containsKey("wallpaperId")) {
            extras = new Bundle(extras);
            extras.putInt("wallpaperId", SettingsBridge.wallpaperId(getContext()));
        }
        android.content.SharedPreferences prefs =
                getContext().getSharedPreferences("committed_clock_settings", 0);
        try {
            if ("readWidgets".equals(method) || "commitWidgets".equals(method))
                return WidgetPreferences.store(getContext(), method, extras);
            if (write) {
                if (extras == null) throw new IllegalArgumentException("Missing settings");
                int type = extras.getInt("type", -1), font = extras.getInt("font", -1);
                if (type < 0 || font < 0 || extras.getInt("wallpaperId", -1) < 0)
                    throw new IllegalArgumentException("Missing clock or wallpaper identity");
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("font", font);
                json.put("scene", extras.getString("scene", ""));
                json.put("wallpaperId", extras.getInt("wallpaperId", -1));
                json.put("enabled", extras.getBoolean("enabled"));
                json.put("manual", extras.getBoolean("manual"));
                json.put("hideDate", extras.getBoolean("hideDate"));
                json.put("weatherEnabled", extras.getBoolean("weatherEnabled"));
                json.put("forceBlur", extras.getBoolean("forceBlur"));
                json.put("blurRadius", extras.getInt("blurRadius", 200));
                json.put("blurVisibility", extras.getFloat("blurVisibility", -1f));
                json.put("depthEnabled", extras.getBoolean("depthEnabled"));
                json.put("depthOwnsManual", extras.getBoolean("depthOwnsManual"));
                DepthMetadata.write(json, extras);
                float[] heights = extras.getFloatArray("manualHeights");
                if (heights != null) {
                    JSONArray values = new JSONArray();
                    for (float height : heights) values.put(height);
                    json.put("manualHeights", values);
                }
                int[] progress = extras.getIntArray("manualProgress");
                if (progress != null) {
                    JSONArray values = new JSONArray();
                    for (int position : progress) values.put(position);
                    json.put("manualProgress", values);
                }
                json.put("radius", extras.getFloat("radius"));
                json.put("dx", extras.getFloat("dx"));
                json.put("dy", extras.getFloat("dy"));
                json.put("color", extras.getInt("color"));
                String key = key(extras);
                if (!prefs.edit()
                        .putString(key, json.toString())
                        .putBoolean("migrated_" + type + "_" + font, true)
                        .commit())
                    throw new IllegalStateException("Could not save shadow settings");
                getContext().getContentResolver().notifyChange(URI, null);
                Bundle result = new Bundle();
                result.putBoolean("saved", true);
                return result;
            }
            if (!"read".equals(method) || extras == null)
                throw new IllegalArgumentException("Unsupported operation");
            String legacy = "shadow_" + extras.getInt("type", -1) + "_" + extras.getInt("font", -1);
            String migrated =
                    "migrated_" + extras.getInt("type", -1) + "_" + extras.getInt("font", -1);
            String saved = LayoutOrientation.read(prefs, baseKey(extras), extras);
            if (saved == null
                    && SettingsScope.canImportLegacy(
                            extras.getInt("wallpaperId", -1), prefs.getBoolean(migrated, false))) {
                saved = prefs.getString(legacy, null);
                if (saved != null && extras.getInt("wallpaperId", -1) >= 0) {
                    if (!prefs.edit()
                            .putString(key(extras), saved)
                            .putBoolean(migrated, true)
                            .commit())
                        throw new IllegalStateException("Could not migrate clock settings");
                }
            }
            if (saved == null) return null;
            JSONObject json = new JSONObject(saved);
            String scene = extras.getString("scene", "");
            String storedScene = json.optString("scene", "");
            if (!SettingsScope.sceneMatches(scene, storedScene)) return null;
            Bundle result = new Bundle();
            result.putInt("type", extras.getInt("type", -1));
            result.putInt("font", extras.getInt("font", -1));
            result.putInt("wallpaperId", extras.getInt("wallpaperId", -1));
            LayoutOrientation.copy(extras, result);
            result.putString("scene", storedScene);
            result.putBoolean("enabled", json.getBoolean("enabled"));
            result.putBoolean("manual", json.optBoolean("manual"));
            result.putBoolean("hideDate", json.optBoolean("hideDate"));
            if (json.has("weatherEnabled"))
                result.putBoolean("weatherEnabled", json.getBoolean("weatherEnabled"));
            result.putBoolean("forceBlur", json.optBoolean("forceBlur"));
            result.putInt("blurRadius", json.optInt("blurRadius", 200));
            result.putFloat("blurVisibility", (float) json.optDouble("blurVisibility", -1));
            result.putBoolean("depthEnabled", json.optBoolean("depthEnabled"));
            result.putBoolean("depthOwnsManual", json.optBoolean("depthOwnsManual"));
            DepthMetadata.read(json, result);
            JSONArray values = json.optJSONArray("manualHeights");
            if (values != null) {
                float[] heights = new float[values.length()];
                for (int i = 0; i < heights.length; i++) heights[i] = (float) values.getDouble(i);
                result.putFloatArray("manualHeights", heights);
            }
            JSONArray progress = json.optJSONArray("manualProgress");
            if (progress != null) {
                int[] positions = new int[progress.length()];
                for (int i = 0; i < positions.length; i++) positions[i] = progress.getInt(i);
                result.putIntArray("manualProgress", positions);
            }
            result.putFloat("radius", (float) json.getDouble("radius"));
            result.putFloat("dx", (float) json.getDouble("dx"));
            result.putFloat("dy", (float) json.getDouble("dy"));
            result.putInt("color", json.getInt("color"));
            return result;
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("Invalid settings", e);
        }
    }

    private static String key(Bundle value) {
        return LayoutOrientation.key(baseKey(value), value);
    }

    private static String baseKey(Bundle value) {
        int wallpaper = value.getInt("wallpaperId", -1);
        return SettingsScope.key(wallpaper, value.getInt("type", -1), value.getInt("font", -1));
    }

    @Override
    public android.os.ParcelFileDescriptor openFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        boolean write = "w".equals(mode);
        if (!write && !"r".equals(mode))
            throw new java.io.FileNotFoundException("Unsupported mode");
        checkCaller(write);
        java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() != 2
                || !segments.get(0).equals("masks")
                || !segments.get(1).matches("[0-9a-f]{64}"))
            throw new java.io.FileNotFoundException("Invalid mask path");
        java.io.File directory = new java.io.File(getContext().getFilesDir(), "depth-masks");
        if (write && !directory.isDirectory() && !directory.mkdirs())
            throw new java.io.FileNotFoundException("Could not create mask directory");
        java.io.File file = new java.io.File(directory, segments.get(1));
        if (!write)
            return android.os.ParcelFileDescriptor.open(
                    file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        if (file.isFile())
            return android.os.ParcelFileDescriptor.open(
                    new java.io.File("/dev/null"), android.os.ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            java.io.File temporary = java.io.File.createTempFile("upload-", ".tmp", directory);
            return android.os.ParcelFileDescriptor.open(
                    temporary,
                    android.os.ParcelFileDescriptor.MODE_WRITE_ONLY,
                    new android.os.Handler(android.os.Looper.getMainLooper()),
                    error ->
                            maskWriter.execute(
                                    () -> {
                                        try {
                                            if (error != null) throw error;
                                            if (temporary.length() > 16 * 1024 * 1024)
                                                throw new java.io.IOException("Mask too large");
                                            java.security.MessageDigest digest =
                                                    java.security.MessageDigest.getInstance(
                                                            "SHA-256");
                                            try (java.io.InputStream input =
                                                    new java.io.FileInputStream(temporary)) {
                                                byte[] chunk = new byte[16384];
                                                int count;
                                                while ((count = input.read(chunk)) != -1)
                                                    digest.update(chunk, 0, count);
                                            }
                                            if (!DepthStorage.hex(digest.digest())
                                                    .equals(file.getName()))
                                                throw new java.io.IOException(
                                                        "Invalid mask checksum");
                                            if (!file.isFile() && !temporary.renameTo(file))
                                                throw new java.io.IOException(
                                                        "Could not publish mask");
                                        } catch (Exception failure) {
                                            android.util.Log.w(
                                                    DressroomModule.TAG,
                                                    "Mask upload rejected",
                                                    failure);
                                        } finally {
                                            temporary.delete();
                                            synchronized (maskReady) {
                                                maskReady.notifyAll();
                                            }
                                        }
                                    }));
        } catch (java.io.IOException error) {
            throw new java.io.FileNotFoundException(error.getMessage());
        }
    }

    private void awaitMasks(Bundle data) {
        for (String field : DepthMetadata.BLOBS) {
            String key = data.getString(field + "Ref");
            if (key == null) continue;
            DepthStorage.uri(key);
            java.io.File file =
                    new java.io.File(
                            new java.io.File(getContext().getFilesDir(), "depth-masks"), key);
            long deadline = android.os.SystemClock.uptimeMillis() + 3000;
            synchronized (maskReady) {
                while (!file.isFile()) {
                    long remaining = deadline - android.os.SystemClock.uptimeMillis();
                    if (remaining <= 0)
                        throw new IllegalStateException("Subject mask upload did not complete");
                    try {
                        maskReady.wait(remaining);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                }
            }
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri u) {
        return "vnd.android.cursor.item/vnd.dressroomadvanced.settings";
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}
