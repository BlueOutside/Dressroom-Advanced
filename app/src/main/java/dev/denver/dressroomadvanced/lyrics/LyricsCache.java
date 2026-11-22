package dev.denver.dressroomadvanced.lyrics;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * File-backed on-disk LRU cache for lyrics in the module app's internal storage.
 * Enforces max tracks, storage caps, and negative lookup TTLs.
 */
public final class LyricsCache {
    public static final String CACHE_DIR_NAME = "lyrics_cache";
    public static final int MAX_ENTRIES = 500;
    public static final long MAX_STORAGE_BYTES = 20L * 1024L * 1024L; // 20 MB
    public static final long NEGATIVE_TTL_MS = 24L * 3600L * 1000L;    // 24 hours

    private final File cacheDir;
    private final Object lock = new Object();

    public record CacheInfo(int trackCount, long totalBytes) {
        public String formatSize() {
            if (totalBytes < 1024) return totalBytes + " B";
            if (totalBytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", totalBytes / 1024.0);
            return String.format(Locale.ROOT, "%.1f MB", totalBytes / (1024.0 * 1024.0));
        }
    }

    public LyricsCache(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public File cacheDir() {
        return cacheDir;
    }

    public static String cacheKey(String artist, String title) {
        String cleanArtist = artist != null ? artist.trim().toLowerCase(Locale.ROOT) : "";
        String cleanTitle = title != null ? title.trim().toLowerCase(Locale.ROOT) : "";
        String combined = cleanArtist + "|" + cleanTitle;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(combined.hashCode());
        }
    }

    public LyricsEntry get(String artist, String title) {
        if (artist == null && title == null) return null;
        String key = cacheKey(artist, title);
        synchronized (lock) {
            if (!cacheDir.isDirectory()) return null;
            File file = new File(cacheDir, key + ".json");
            if (!file.isFile()) return null;

            try {
                String jsonText = readFileToString(file);
                JSONObject json = new JSONObject(jsonText);
                if (json.optInt("lyricsSchema") < 2) return null;
                LyricsEntry entry = LyricsEntry.fromJson(json);
                if (entry == null) {
                    file.delete();
                    return null;
                }

                long now = System.currentTimeMillis();
                if (entry.notFound() && entry.isExpired(now, NEGATIVE_TTL_MS)) {
                    file.delete();
                    return null;
                }

                // Touch timestamp for LRU order
                file.setLastModified(now);
                return entry;
            } catch (Exception e) {
                file.delete();
                return null;
            }
        }
    }

    public void put(LyricsEntry entry) {
        if (entry == null) return;
        put(entry.artist(), entry.title(), entry);
    }

    public void put(String artist, String title, LyricsEntry entry) {
        if (entry == null) return;
        String key = cacheKey(artist, title);
        synchronized (lock) {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) return;

            File tempFile = null;
            File targetFile = new File(cacheDir, key + ".json");
            try {
                tempFile = File.createTempFile("lrc_", ".tmp", cacheDir);
                String jsonText = entry.toJson().toString();
                writeStringToFile(tempFile, jsonText);

                if (!tempFile.renameTo(targetFile)) {
                    // Try delete and rename
                    targetFile.delete();
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.delete();
                        return;
                    }
                }
                targetFile.setLastModified(System.currentTimeMillis());
                evictIfOverBudgetLocked();
            } catch (Exception error) {
                if (tempFile != null) tempFile.delete();
            }
        }
    }

    public CacheInfo getCacheInfo() {
        synchronized (lock) {
            if (!cacheDir.isDirectory()) return new CacheInfo(0, 0L);
            File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) return new CacheInfo(0, 0L);

            int count = 0;
            long totalBytes = 0L;
            for (File f : files) {
                if (f.isFile()) {
                    count++;
                    totalBytes += f.length();
                }
            }
            return new CacheInfo(count, totalBytes);
        }
    }

    public void clear() {
        synchronized (lock) {
            if (!cacheDir.isDirectory()) return;
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    private void evictIfOverBudgetLocked() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length <= 1) return;

        long totalBytes = 0L;
        for (File f : files) totalBytes += f.length();

        if (files.length <= MAX_ENTRIES && totalBytes <= MAX_STORAGE_BYTES) {
            return;
        }

        // Sort oldest first
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int targetCount = (int) (MAX_ENTRIES * 0.9);
        long targetBytes = (long) (MAX_STORAGE_BYTES * 0.9);

        for (File f : files) {
            if (files.length <= targetCount && totalBytes <= targetBytes) break;
            long size = f.length();
            if (f.delete()) {
                totalBytes -= size;
            }
        }
    }

    private static String readFileToString(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static void writeStringToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }
}
