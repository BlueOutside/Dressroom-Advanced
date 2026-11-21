package dev.denver.dressroomadvanced;

import java.io.File;
import java.util.Locale;

/**
 * Contract and immutable value objects for resolving optional animated (motion) album covers
 * across lockscreen media players.
 */
interface AnimatedCoverProvider {
    String id();

    /**
     * Resolves an animated cover asset for {@code query}, preferring cached results and only hitting
     * the network when {@code allowNetwork} is true.
     */
    AnimatedCoverAsset resolve(TrackQuery query, boolean allowNetwork, AnimatedCoverCache cache);

    record TrackQuery(
            String artist,
            String song,
            String album,
            String packageName,
            String artworkUri,
            int maxWidthPx) {
        public TrackQuery {
            artist = sanitize(artist);
            song = sanitize(song);
            album = sanitize(album);
            packageName = sanitize(packageName);
            artworkUri = sanitize(artworkUri);
            maxWidthPx = maxWidthPx > 0 ? maxWidthPx : 1080;
        }

        boolean isValid() {
            return !artist.isEmpty() && (!song.isEmpty() || !album.isEmpty());
        }

        String normalizedAlbumKey() {
            String a = MediaTextNormalize.primaryArtist(artist);
            String na = MediaTextNormalize.normalize(a);
            String nal = MediaTextNormalize.normalize(album);
            if (na.isEmpty() || nal.isEmpty()) return "";
            return "album|" + na + "|" + nal;
        }

        String normalizedTrackKey() {
            String na = MediaTextNormalize.normalize(MediaTextNormalize.primaryArtist(artist));
            String ns = MediaTextNormalize.normalize(song);
            String nal = MediaTextNormalize.normalize(album);
            return "track|" + na + "|" + ns + "|" + nal;
        }

        String playbackKey() {
            return packageName + "|" + normalizedTrackKey() + "|" + song.toLowerCase(Locale.ROOT);
        }

        private static String sanitize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    record AnimatedCoverAsset(
            String providerId,
            String cacheKey,
            File videoFile,
            String streamUrl,
            int videoWidth,
            int videoHeight,
            boolean tallPortrait,
            long resolvedAtMs) {
        boolean isPlayableLocalFile() {
            return videoFile != null && videoFile.isFile() && videoFile.length() > 1024L;
        }
    }

    /** Pure-Java track string normalization shared between providers and JVM tests. */
    final class MediaTextNormalize {
        private static final java.util.regex.Pattern PARENS_PATTERN =
                java.util.regex.Pattern.compile("\\([^)]*\\)|\\[[^]]*\\]");
        private static final java.util.regex.Pattern ARTIST_SPLIT_PATTERN =
                java.util.regex.Pattern.compile(
                        "[,&/]|\\s+(?:feat\\.?|ft\\.?|with|x)\\s+",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
        private static final java.util.regex.Pattern DIACRITICS_PATTERN =
                java.util.regex.Pattern.compile("\\p{M}+");
        private static final java.util.regex.Pattern NON_ALNUM_PATTERN =
                java.util.regex.Pattern.compile("[^a-z0-9]+");

        private MediaTextNormalize() {}

        static String primaryArtist(String artist) {
            if (artist == null || artist.isEmpty()) return "";
            String cleaned = PARENS_PATTERN.matcher(artist).replaceAll(" ").trim();
            String[] parts = ARTIST_SPLIT_PATTERN.split(cleaned, 2);
            String primary = parts.length > 0 ? parts[0].trim() : cleaned;
            return primary.isEmpty() ? artist.trim() : primary;
        }

        static String cleanSearchQuery(String artist, String songOrAlbum) {
            String a = primaryArtist(artist);
            String s = songOrAlbum == null ? "" : PARENS_PATTERN.matcher(songOrAlbum).replaceAll(" ").trim();
            if (s.isEmpty() && songOrAlbum != null) s = songOrAlbum.trim();
            return (a + " " + s).trim().replaceAll("\\s+", " ");
        }

        static String normalize(String input) {
            if (input == null || input.isEmpty()) return "";
            String withoutParens = PARENS_PATTERN.matcher(input).replaceAll(" ");
            String cleaned = normalizeAscii(withoutParens);
            if (!cleaned.isEmpty()) return cleaned;
            return normalizeAscii(input);
        }

        static boolean matchesTrack(
                String queryArtist,
                String querySong,
                String queryAlbum,
                String candidateArtist,
                String candidateSong,
                String candidateAlbum) {
            String qPrimary = normalize(primaryArtist(queryArtist));
            String qFull = normalize(queryArtist);
            String qSong = normalize(querySong);
            String qAlbum = normalize(queryAlbum);

            String cArtist = normalize(candidateArtist);
            String cPrimary = normalize(primaryArtist(candidateArtist));
            String cSong = normalize(candidateSong);
            String cAlbum = normalize(candidateAlbum);

            if (cArtist.isEmpty() || (qPrimary.isEmpty() && qFull.isEmpty())) return false;
            boolean artistMatch =
                    (!qPrimary.isEmpty() && (cArtist.contains(qPrimary) || qPrimary.contains(cArtist)))
                            || (!qFull.isEmpty()
                                    && (qFull.contains(cArtist)
                                            || (!cPrimary.isEmpty() && qFull.contains(cPrimary))));
            if (!artistMatch) return false;

            boolean songMatch =
                    !qSong.isEmpty() && !cSong.isEmpty() && (cSong.contains(qSong) || qSong.contains(cSong));
            boolean albumMatch =
                    !qAlbum.isEmpty() && !cAlbum.isEmpty() && (cAlbum.contains(qAlbum) || qAlbum.contains(cAlbum));
            return songMatch || albumMatch;
        }

        private static String normalizeAscii(String input) {
            String deaccented =
                    DIACRITICS_PATTERN
                            .matcher(java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD))
                            .replaceAll("");
            return NON_ALNUM_PATTERN
                    .matcher(deaccented.toLowerCase(Locale.ROOT))
                    .replaceAll(" ")
                    .trim();
        }
    }
}
