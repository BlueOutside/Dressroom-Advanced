package dev.denver.dressroomadvanced;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Bounded on-disk LRU cache for local animated-cover {@code .mp4} assets plus track-to-asset index
 * pointers and TTL-bounded negative lookup cache. Pure Java I/O so it runs directly in JVM tests.
 */
final class AnimatedCoverCache {
    static final long DEFAULT_MAX_BYTES = 150L * 1024L * 1024L;
    static final long MAX_SINGLE_FILE_BYTES = 40L * 1024L * 1024L;
    static final long DEFAULT_NEGATIVE_TTL_MS = 6L * 3600L * 1000L;
    private static final int MAX_NEGATIVE_ENTRIES = 64;

    private final File cacheDir;
    private final long maxBytes;
    private final long negativeTtlMs;
    private final LongSupplier clockMs;
    private final Object diskLock = new Object();
    private final Map<String, Long> negativeCache =
            new LinkedHashMap<>(MAX_NEGATIVE_ENTRIES + 4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_NEGATIVE_ENTRIES;
                }
            };

    AnimatedCoverCache(File cacheDir) {
        this(cacheDir, DEFAULT_MAX_BYTES, DEFAULT_NEGATIVE_TTL_MS, System::currentTimeMillis);
    }

    AnimatedCoverCache(File cacheDir, long maxBytes, long negativeTtlMs, LongSupplier clockMs) {
        this.cacheDir = cacheDir;
        this.maxBytes = Math.max(1024L * 1024L, maxBytes);
        this.negativeTtlMs = Math.max(0L, negativeTtlMs);
        this.clockMs = clockMs != null ? clockMs : System::currentTimeMillis;
    }

    File cacheDir() {
        return cacheDir;
    }

    boolean isNegative(String lookupKey) {
        if (lookupKey == null || lookupKey.isEmpty()) return false;
        long now = clockMs.getAsLong();
        synchronized (negativeCache) {
            Long expiresAt = negativeCache.get(lookupKey);
            if (expiresAt == null) return false;
            if (expiresAt <= now) {
                negativeCache.remove(lookupKey);
                return false;
            }
            return true;
        }
    }

    void markNegative(String lookupKey) {
        if (lookupKey == null || lookupKey.isEmpty() || negativeTtlMs <= 0L) return;
        long expiresAt = clockMs.getAsLong() + negativeTtlMs;
        synchronized (negativeCache) {
            negativeCache.put(lookupKey, expiresAt);
        }
    }

    void clearNegative(String lookupKey) {
        if (lookupKey == null || lookupKey.isEmpty()) return;
        synchronized (negativeCache) {
            negativeCache.remove(lookupKey);
        }
    }

    AnimatedCoverProvider.AnimatedCoverAsset getByAssetKey(String assetKey) {
        if (cacheDir == null || assetKey == null || assetKey.isEmpty()) return null;
        String hash = sha256Hex(assetKey);
        synchronized (diskLock) {
            File metaFile = new File(cacheDir, hash + ".meta");
            File mp4File = new File(cacheDir, hash + ".mp4");
            if (!metaFile.isFile() || !mp4File.isFile() || mp4File.length() <= 1024L) {
                return null;
            }
            AnimatedCoverProvider.AnimatedCoverAsset asset = readMetadata(metaFile, mp4File);
            if (asset != null) {
                touchQuietly(mp4File, clockMs.getAsLong());
            }
            return asset;
        }
    }

    /** Resolves via an alias pointer written under {@code lookupKey} (e.g. normalized track key). */
    AnimatedCoverProvider.AnimatedCoverAsset getByAlias(String lookupKey) {
        if (cacheDir == null || lookupKey == null || lookupKey.isEmpty()) return null;
        String aliasHash = sha256Hex(lookupKey);
        synchronized (diskLock) {
            File aliasFile = new File(cacheDir, aliasHash + ".alias");
            if (!aliasFile.isFile()) return null;
            try {
                String targetAssetKey = new String(Files.readAllBytes(aliasFile.toPath()), StandardCharsets.UTF_8).trim();
                if (targetAssetKey.isEmpty()) return null;
                AnimatedCoverProvider.AnimatedCoverAsset asset = getByAssetKey(targetAssetKey);
                if (asset == null) {
                    aliasFile.delete();
                } else {
                    touchQuietly(aliasFile, clockMs.getAsLong());
                }
                return asset;
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    void putAlias(String lookupKey, String assetKey) {
        if (cacheDir == null || lookupKey == null || lookupKey.isEmpty() || assetKey == null || assetKey.isEmpty()) {
            return;
        }
        String aliasHash = sha256Hex(lookupKey);
        synchronized (diskLock) {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) return;
            File aliasFile = new File(cacheDir, aliasHash + ".alias");
            try {
                Files.write(aliasFile.toPath(), assetKey.getBytes(StandardCharsets.UTF_8));
                touchQuietly(aliasFile, clockMs.getAsLong());
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Atomically commits {@code tempMp4} into the cache under {@code assetKey} and enforces the
     * bounded disk quota via least-recently-used eviction. Returns {@code null} and deletes
     * {@code tempMp4} on validation/write failure.
     */
    AnimatedCoverProvider.AnimatedCoverAsset commitAsset(
            String providerId,
            String assetKey,
            File tempMp4,
            String streamUrl,
            int width,
            int height,
            boolean tallPortrait) {
        if (cacheDir == null || assetKey == null || assetKey.isEmpty() || tempMp4 == null || !tempMp4.isFile()) {
            deleteQuietly(tempMp4);
            return null;
        }
        long length = tempMp4.length();
        if (length <= 1024L || length > MAX_SINGLE_FILE_BYTES) {
            deleteQuietly(tempMp4);
            return null;
        }
        String hash = sha256Hex(assetKey);
        synchronized (diskLock) {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                deleteQuietly(tempMp4);
                return null;
            }
            File finalMp4 = new File(cacheDir, hash + ".mp4");
            File metaFile = new File(cacheDir, hash + ".meta");
            long now = clockMs.getAsLong();
            try {
                moveReplacing(tempMp4.toPath(), finalMp4.toPath());
                touchQuietly(finalMp4, now);
                String metaText =
                        encodeMetadata(providerId, assetKey, streamUrl, width, height, tallPortrait, now);
                Files.write(metaFile.toPath(), metaText.getBytes(StandardCharsets.UTF_8));
                touchQuietly(metaFile, now);
                evictIfOverBudgetLocked(finalMp4);
                return new AnimatedCoverProvider.AnimatedCoverAsset(
                        providerId, assetKey, finalMp4, streamUrl, width, height, tallPortrait, now);
            } catch (IOException error) {
                deleteQuietly(tempMp4);
                deleteQuietly(finalMp4);
                deleteQuietly(metaFile);
                return null;
            }
        }
    }

    File createTempFile() throws IOException {
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            throw new IOException("Cannot create live cover cache directory: " + cacheDir);
        }
        return File.createTempFile("cover_", ".tmp", cacheDir);
    }

    long totalCachedVideoBytes() {
        synchronized (diskLock) {
            File[] files = cacheDir != null && cacheDir.isDirectory() ? cacheDir.listFiles() : null;
            if (files == null) return 0L;
            long total = 0L;
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".mp4")) {
                    total += file.length();
                }
            }
            return total;
        }
    }

    private void evictIfOverBudgetLocked(File protectedMp4) {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        List<File> mp4s = new ArrayList<>();
        long total = 0L;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".mp4")) {
                total += file.length();
                mp4s.add(file);
            }
        }
        if (total <= maxBytes) return;

        mp4s.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (File candidate : mp4s) {
            if (total <= maxBytes) break;
            if (candidate.equals(protectedMp4)) continue;
            long size = candidate.length();
            String base = stripExtension(candidate.getName(), ".mp4");
            if (candidate.delete()) {
                total -= size;
                if (!base.isEmpty()) {
                    deleteQuietly(new File(cacheDir, base + ".meta"));
                }
            }
        }
    }

    private static AnimatedCoverProvider.AnimatedCoverAsset readMetadata(File metaFile, File mp4File) {
        try {
            List<String> lines = Files.readAllLines(metaFile.toPath(), StandardCharsets.UTF_8);
            if (lines.size() < 7) return null;
            String providerId = lines.get(0).trim();
            String cacheKey = lines.get(1).trim();
            String streamUrl = lines.get(2).trim();
            int width = Integer.parseInt(lines.get(3).trim());
            int height = Integer.parseInt(lines.get(4).trim());
            boolean tall = Boolean.parseBoolean(lines.get(5).trim());
            long resolvedAtMs = Long.parseLong(lines.get(6).trim());
            if (cacheKey.isEmpty()) return null;
            return new AnimatedCoverProvider.AnimatedCoverAsset(
                    providerId, cacheKey, mp4File, streamUrl, width, height, tall, resolvedAtMs);
        } catch (IOException | NumberFormatException ignored) {
            return null;
        }
    }

    private static String encodeMetadata(
            String providerId,
            String assetKey,
            String streamUrl,
            int width,
            int height,
            boolean tallPortrait,
            long now) {
        return sanitizeLine(providerId)
                + '\n'
                + sanitizeLine(assetKey)
                + '\n'
                + sanitizeLine(streamUrl)
                + '\n'
                + width
                + '\n'
                + height
                + '\n'
                + tallPortrait
                + '\n'
                + now
                + '\n';
    }

    private static String sanitizeLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String stripExtension(String name, String ext) {
        return name.endsWith(ext) ? name.substring(0, name.length() - ext.length()) : "";
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void touchQuietly(File file, long timestampMs) {
        if (file != null && file.exists()) {
            file.setLastModified(timestampMs);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unreachable) {
            throw new IllegalStateException("SHA-256 unavailable", unreachable);
        }
    }
}
