package dev.denver.dressroomadvanced.lyrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to clean track titles and artist names from YouTube / streaming noise
 * (e.g. "(Official Music Video)", "[MV]", "(Lyric Video)", " - Topic", etc.)
 * to significantly improve LRCLIB and lyrics provider match rates.
 */
public final class LyricsSanitizer {

    private static final String NOISE_WORDS =
            "(?:official|music|video|audio|visualizer|lyric|lyrics|mv|m/v|clip|clip\\s+officiel"
            + "|hd|4k|1080p|hq|live|performance|explicit|clean|remaster|remastered"
            + "|remaster\\s+\\d{4}|\\d{4}\\s+remaster|version|radio\\s+edit|full\\s+song"
            + "|color\\s+coded|premiere|upgrade)";

    // Bracketed noise: (Official Music Video), [4K Upgrade], (2020 Remaster), etc.
    private static final Pattern PATTERN_BRACKET_NOISE = Pattern.compile(
            "(?i)\\s*[\\(\\[\\{【](?:\\s*" + NOISE_WORDS + "|\\s*[-–|/&,:]\\s*|\\s*\\d{4}\\s*)+[\\)\\]\\}】]");

    // Live concert tag in brackets: (Live at Wembley, 1986), [Live from London]
    private static final Pattern PATTERN_LIVE_BRACKET = Pattern.compile(
            "(?i)\\s*[\\(\\[\\{【]\\s*live(?:\\s+at|\\s+from|\\s+in)?\\s+[^()\\[\\]{}【】]+[\\)\\]\\}】]");

    // Trailing delimiter noise: " - Official Video", " | Music Video", " // Official Lyric Video"
    private static final Pattern PATTERN_TRAILING_DELIMITER = Pattern.compile(
            "(?i)\\s*(?:[-–|/]|//)\\s*(?:(?:\\s*" + NOISE_WORDS + "|\\s*[-–|/&,:]\\s*|\\s*\\d{4}\\s*)+)\\s*$");

    // Standalone trailing noise words: " official music video", " official audio"
    private static final Pattern PATTERN_TRAILING_WORDS = Pattern.compile(
            "(?i)\\s+(?:official\\s+(?:music\\s+)?video|official\\s+audio|official\\s+visualizer|music\\s+video|lyric\\s+video)\\s*$");

    // Leading track numbers: "01 - Title", "01. Title"
    private static final Pattern PATTERN_TRACK_NUM = Pattern.compile(
            "^\\s*\\d{1,3}\\s*[-–.]\\s*");

    // Featured artists: (feat. Drake), [ft. Artist], feat. Artist
    private static final Pattern PATTERN_FEATURES = Pattern.compile(
            "(?i)\\s*(?:[\\(\\[\\{【]\\s*)?(?:feat\\.?|ft\\.?)\\s+[^()\\[\\]{}【】]+(?:[\\)\\]\\}】])?");

    // YouTube / VEVO artist suffixes
    private static final Pattern PATTERN_TOPIC = Pattern.compile("(?i)\\s*-\\s*Topic\\s*$");
    private static final Pattern PATTERN_VEVO = Pattern.compile("(?i)\\s*VEVO\\s*$");
    private static final Pattern PATTERN_OFFICIAL_ARTIST = Pattern.compile("(?i)\\s*Official(?:\\s+Channel)?\\s*$");

    // Whitespace collapsing
    private static final Pattern PATTERN_WHITESPACE = Pattern.compile("\\s+");

    private LyricsSanitizer() {}

    /**
     * Sanitizes artist names, removing artifacts such as " - Topic", "VEVO", "Official".
     */
    public static String cleanArtist(String rawArtist) {
        if (rawArtist == null) return "";
        String s = rawArtist.trim();
        if (s.isEmpty()) return "";

        s = PATTERN_TOPIC.matcher(s).replaceAll("");
        s = PATTERN_VEVO.matcher(s).replaceAll("");
        s = PATTERN_OFFICIAL_ARTIST.matcher(s).replaceAll("");

        // Strip enclosing quotes if any
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }

        s = PATTERN_WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * Sanitizes track title, stripping video tags, bracketed suffixes, and remaster indicators.
     * Also strips duplicate artist prefix if present (e.g. "Artist - Track").
     */
    public static String cleanTitle(String rawTitle, String artist) {
        if (rawTitle == null) return "";
        String s = rawTitle.trim();
        if (s.isEmpty()) return "";

        // If title starts with the artist name: "Artist - Title" or "Artist : Title"
        if (artist != null && !artist.trim().isEmpty()) {
            String cleanArt = cleanArtist(artist);
            if (!cleanArt.isEmpty()) {
                String prefixPattern = "(?i)^\\s*" + Pattern.quote(cleanArt) + "\\s*[-–:]\\s*";
                s = s.replaceFirst(prefixPattern, "");
            }
        }

        // Strip track number prefix: "01 - " or "02. "
        s = PATTERN_TRACK_NUM.matcher(s).replaceFirst("");

        // Iteratively clean trailing and bracketed noise until string stabilizes
        String prev = null;
        int maxPasses = 5;
        while (!s.equals(prev) && maxPasses-- > 0) {
            prev = s;
            s = PATTERN_BRACKET_NOISE.matcher(s).replaceAll("");
            s = PATTERN_LIVE_BRACKET.matcher(s).replaceAll("");
            s = PATTERN_TRAILING_DELIMITER.matcher(s).replaceAll("");
            s = PATTERN_TRAILING_WORDS.matcher(s).replaceAll("");
            s = PATTERN_WHITESPACE.matcher(s).replaceAll(" ").trim();
        }

        // Strip enclosing quotes if any
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }

        s = PATTERN_WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s.isEmpty() ? rawTitle.trim() : s;
    }

    /**
     * Strips "feat. ..." / "ft. ..." clauses from title for fallback search.
     */
    public static String stripFeatures(String title) {
        if (title == null) return "";
        String s = PATTERN_FEATURES.matcher(title).replaceAll("");
        s = PATTERN_WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s.isEmpty() ? title.trim() : s;
    }

    /**
     * Pair container for resolved title and artist.
     */
    public record SanitizedTrack(String artist, String title) {}

    /**
     * Extracts and sanitizes both artist and title, detecting cases where
     * the artist was omitted or generic and embedded in the title ("Artist - Song").
     */
    public static SanitizedTrack sanitize(String rawArtist, String rawTitle) {
        String artist = rawArtist != null ? rawArtist.trim() : "";
        String title = rawTitle != null ? rawTitle.trim() : "";

        // If artist is missing or generic (e.g. YouTube app), inspect title for " - "
        boolean isGenericArtist = artist.isEmpty()
                || artist.equalsIgnoreCase("unknown")
                || artist.equalsIgnoreCase("unknown artist")
                || artist.equalsIgnoreCase("<unknown>");

        if (isGenericArtist && (title.contains(" - ") || title.contains(" – "))) {
            String[] parts = title.split("\\s+[-–]\\s+", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                artist = parts[0].trim();
                title = parts[1].trim();
            }
        }

        artist = cleanArtist(artist);
        title = cleanTitle(title, artist);

        return new SanitizedTrack(artist, title);
    }
}
