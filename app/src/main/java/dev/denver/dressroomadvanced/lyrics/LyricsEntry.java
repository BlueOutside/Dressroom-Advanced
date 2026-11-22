package dev.denver.dressroomadvanced.lyrics;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable lyric entry representing fetched, cached, or negative results.
 */
public record LyricsEntry(
        String artist,
        String title,
        String album,
        long durationMs,
        String syncedLyrics,
        String plainLyrics,
        String source,
        long timestampMs,
        boolean notFound) {

    public LyricsEntry {
        artist = artist != null ? artist.trim() : "";
        title = title != null ? title.trim() : "";
        album = album != null ? album.trim() : "";
        durationMs = Math.max(0L, durationMs);
        syncedLyrics = syncedLyrics != null && !syncedLyrics.trim().isEmpty() ? syncedLyrics : null;
        plainLyrics = plainLyrics != null && !plainLyrics.trim().isEmpty() ? plainLyrics : null;
        source = source != null ? source.trim() : "none";
        timestampMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
    }

    public static LyricsEntry notFound(String artist, String title, String album, long durationMs) {
        return new LyricsEntry(artist, title, album, durationMs, null, null, "none", System.currentTimeMillis(), true);
    }

    public boolean hasSyncedLyrics() {
        return syncedLyrics != null && !syncedLyrics.isEmpty();
    }

    public boolean hasPlainLyrics() {
        return plainLyrics != null && !plainLyrics.isEmpty();
    }

    public boolean hasLyrics() {
        return hasSyncedLyrics() || hasPlainLyrics();
    }

    public boolean isExpired(long nowMs, long ttlMs) {
        return (nowMs - timestampMs) >= ttlMs;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("artist", artist);
        obj.put("title", title);
        obj.put("album", album);
        obj.put("durationMs", durationMs);
        if (syncedLyrics != null) obj.put("syncedLyrics", syncedLyrics);
        if (plainLyrics != null) obj.put("plainLyrics", plainLyrics);
        obj.put("source", source);
        obj.put("timestampMs", timestampMs);
        obj.put("notFound", notFound);
        return obj;
    }

    public static LyricsEntry fromJson(JSONObject obj) {
        if (obj == null) return null;
        String artist = obj.optString("artist", "");
        String title = obj.optString("title", "");
        String album = obj.optString("album", "");
        long durationMs = obj.optLong("durationMs", 0L);
        String synced = obj.has("syncedLyrics") ? obj.optString("syncedLyrics", null) : null;
        String plain = obj.has("plainLyrics") ? obj.optString("plainLyrics", null) : null;
        String source = obj.optString("source", "none");
        long timestampMs = obj.optLong("timestampMs", System.currentTimeMillis());
        boolean notFound = obj.optBoolean("notFound", false);
        return new LyricsEntry(artist, title, album, durationMs, synced, plain, source, timestampMs, notFound);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString("artist", artist);
        b.putString("title", title);
        b.putString("album", album);
        b.putLong("durationMs", durationMs);
        if (syncedLyrics != null) b.putString("syncedLyrics", syncedLyrics);
        if (plainLyrics != null) b.putString("plainLyrics", plainLyrics);
        b.putString("source", source);
        b.putLong("timestampMs", timestampMs);
        b.putBoolean("notFound", notFound);
        b.putBoolean("found", hasLyrics());
        return b;
    }

    public static LyricsEntry fromBundle(Bundle b) {
        if (b == null) return null;
        String artist = b.getString("artist", "");
        String title = b.getString("title", "");
        String album = b.getString("album", "");
        long durationMs = b.getLong("durationMs", 0L);
        String synced = b.getString("syncedLyrics", null);
        String plain = b.getString("plainLyrics", null);
        String source = b.getString("source", "none");
        long timestampMs = b.getLong("timestampMs", System.currentTimeMillis());
        boolean notFound = b.getBoolean("notFound", false);
        return new LyricsEntry(artist, title, album, durationMs, synced, plain, source, timestampMs, notFound);
    }
}
