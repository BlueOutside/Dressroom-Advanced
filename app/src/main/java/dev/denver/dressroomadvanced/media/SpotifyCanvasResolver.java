package dev.denver.dressroomadvanced;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure-Java Spotify Canvas protocol helpers: TOTP anonymous-token auth, Pathfinder v2 track search
 * parsing/ranking, {@code spotify-canva.vercel.app} response parsing, and MediaSession URI
 * unwrapping. Kept free of {@code android.*} and {@code org.json.*} so all logic runs under JVM
 * contract tests.
 */
final class SpotifyCanvasResolver {
    static final int FALLBACK_TOTP_VERSION = 61;
    static final int[] FALLBACK_TOTP_SECRET = {
        44, 55, 47, 42, 70, 40, 34, 114, 76, 74, 50, 111, 120, 97, 75, 76, 94, 102, 43, 69, 49, 120,
        118, 80, 64, 78
    };
    static final String SEARCH_DESKTOP_HASH =
            "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c";

    private static final long TOTP_PERIOD_SECONDS = 30L;
    private static final Pattern SPOTIFY_COLON_TRACK =
            Pattern.compile("^spotify:track:([A-Za-z0-9]{22})(?:[?#].*)?$");
    private static final Pattern SPOTIFY_SLASH_TRACK =
            Pattern.compile("^spotify://track/([A-Za-z0-9]{22})(?:[?#].*)?$");
    private static final Pattern SPOTIFY_WEB_TRACK =
            Pattern.compile(
                    "^https?://open\\.spotify\\.com/(?:intl-[A-Za-z0-9-]+/)?track/([A-Za-z0-9]{22})(?:[?#/].*)?$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern CDN_HOST_PATTERN = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CANVAS_ID_SANITIZE = Pattern.compile("[^A-Za-z0-9_-]");

    private static final String[] VARIANT_NOISE_TERMS = {
        "remix",
        "sped up",
        "slowed",
        "instrumental",
        "karaoke",
        "acoustic",
        "live",
        "radio edit",
        "cover",
        "tribute"
    };

    record TotpSecret(int version, int[] secretBytes) {}

    record AnonymousToken(String accessToken, long expiresAtMs) {}

    record TrackCandidate(String trackId, String song, String artist, String album, int score) {}

    record CanvasVideo(String canvasId, String videoUrl, String trackUri) {}

    private SpotifyCanvasResolver() {}

    static String trackCacheKey(AnimatedCoverProvider.TrackQuery query) {
        if (query == null) return "";
        String na =
                AnimatedCoverProvider.MediaTextNormalize.normalize(
                        AnimatedCoverProvider.MediaTextNormalize.primaryArtist(query.artist()));
        String ns = normalizeKeepingVariants(query.song());
        String nal = AnimatedCoverProvider.MediaTextNormalize.normalize(query.album());
        return SpotifyCanvasAnimatedCoverProvider.PROVIDER_ID + "|track|" + na + "|" + ns + "|" + nal;
    }

    static String extractSpotifyTrackId(String mediaIdOrUri) {
        if (mediaIdOrUri == null) return "";
        String trimmed = mediaIdOrUri.trim();
        if (trimmed.isEmpty()) return "";
        Matcher colon = SPOTIFY_COLON_TRACK.matcher(trimmed);
        if (colon.matches()) return colon.group(1);
        Matcher slash = SPOTIFY_SLASH_TRACK.matcher(trimmed);
        if (slash.matches()) return slash.group(1);
        Matcher web = SPOTIFY_WEB_TRACK.matcher(trimmed);
        if (web.matches()) return web.group(1);
        return "";
    }

    static String unwrapCanvasMediaUri(String rawUri) {
        if (rawUri == null) return "";
        String trimmed = rawUri.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return isSupportedCanvasVideoUrl(trimmed) ? trimmed : "";
        }
        if (!trimmed.regionMatches(true, 0, "content://", 0, 10)) return "";
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        if (!"content".equalsIgnoreCase(uri.getScheme())) return "";
        String path = uri.getPath();
        if (path == null || !path.startsWith("/canvas/")) return "";
        String remainder = path.substring("/canvas/".length());
        while (remainder.startsWith("/")) remainder = remainder.substring(1);
        if (remainder.isEmpty()
                || remainder.contains("..")
                || remainder.indexOf('\\') >= 0
                || remainder.indexOf('\0') >= 0) {
            return "";
        }

        String cdn = extractQueryParam(uri.getRawQuery(), "cdn");
        if (cdn.isEmpty()) cdn = "canvaz.scdn.co";
        String cdnLower = cdn.toLowerCase(Locale.ROOT);
        if (!CDN_HOST_PATTERN.matcher(cdnLower).matches()
                || (!cdnLower.endsWith(".scdn.co") && !cdnLower.endsWith(".spotifycdn.com"))
                || cdnLower.startsWith(".")) {
            return "";
        }

        String built = "https://" + cdnLower + "/" + remainder;
        return isSupportedCanvasVideoUrl(built) ? built : "";
    }

    static boolean isSupportedCanvasVideoUrl(String url) {
        if (!AppleHlsResolver.isHttpsUrl(url)) return false;
        String path;
        try {
            path = URI.create(url.trim()).getPath();
        } catch (IllegalArgumentException ignored) {
            path = url.trim();
        }
        if (path == null) return false;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#');
        if (h >= 0) path = path.substring(0, h);
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".svg")
                || lower.endsWith(".avif")) {
            return false;
        }
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm");
    }

    static TotpSecret parseLatestSecret(String secretDictJson) {
        Object parsed = parseJson(secretDictJson);
        if (!(parsed instanceof Map<?, ?> map)) return null;
        int bestVer = -1;
        int[] bestBytes = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String keyStr)) continue;
            int ver;
            try {
                ver = Integer.parseInt(keyStr.trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (ver <= 0 || !(entry.getValue() instanceof List<?> rawList) || rawList.isEmpty()) {
                continue;
            }
            int[] values = new int[rawList.size()];
            boolean valid = true;
            for (int i = 0; i < rawList.size(); i++) {
                if (!(rawList.get(i) instanceof Number num)) {
                    valid = false;
                    break;
                }
                values[i] = num.intValue();
            }
            if (valid && ver > bestVer) {
                bestVer = ver;
                bestBytes = values;
            }
        }
        return bestVer > 0 && bestBytes != null ? new TotpSecret(bestVer, bestBytes) : null;
    }

    static String computeTotp(int[] secretData, long unixSeconds) {
        if (secretData == null || secretData.length == 0 || unixSeconds < 0L) return "";
        StringBuilder secretBuilder = new StringBuilder(secretData.length * 3);
        for (int i = 0; i < secretData.length; i++) {
            int mapped = secretData[i] ^ ((i % 33) + 9);
            secretBuilder.append(mapped);
        }
        byte[] keyBytes = secretBuilder.toString().getBytes(StandardCharsets.UTF_8);
        long counter = unixSeconds / TOTP_PERIOD_SECONDS;
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] digest = mac.doFinal(msg);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary =
                    ((digest[offset] & 0x7f) << 24)
                            | ((digest[offset + 1] & 0xff) << 16)
                            | ((digest[offset + 2] & 0xff) << 8)
                            | (digest[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            return "";
        }
    }

    static long parseServerTimeSeconds(String serverTimeJson) {
        Object parsed = parseJson(serverTimeJson);
        if (!(parsed instanceof Map<?, ?> map)) return -1L;
        Object val = map.get("serverTime");
        if (val instanceof Number num) {
            long sec = num.longValue();
            return sec > 0L ? sec : -1L;
        }
        return -1L;
    }

    static AnonymousToken parseAnonymousToken(String tokenJson, long nowMs) {
        Object parsed = parseJson(tokenJson);
        if (!(parsed instanceof Map<?, ?> map)) return null;
        String accessToken = asString(map.get("accessToken"));
        if (accessToken.isEmpty()) return null;
        long expiresAtMs = nowMs + 300_000L;
        Object rawExp = map.get("accessTokenExpirationTimestampMs");
        if (rawExp instanceof Number num && num.longValue() > nowMs) {
            expiresAtMs = num.longValue();
        }
        return new AnonymousToken(accessToken, expiresAtMs);
    }

    static String buildPathfinderSearchRequestBody(String searchTerm, int limit) {
        int clampedLimit = Math.max(1, Math.min(20, limit > 0 ? limit : 5));
        String term = searchTerm == null ? "" : searchTerm.trim();
        return "{\"variables\":{\"searchTerm\":\""
                + escapeJsonString(term)
                + "\",\"offset\":0,\"limit\":"
                + clampedLimit
                + ",\"numberOfTopResults\":"
                + clampedLimit
                + ",\"includeAudiobooks\":false,\"includeArtistHasConcertsField\":false,"
                + "\"includePreReleases\":false},\"operationName\":\"searchDesktop\","
                + "\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\""
                + SEARCH_DESKTOP_HASH
                + "\"}}}";
    }

    static List<TrackCandidate> parseSearchTrackCandidates(
            String pathfinderJson, AnimatedCoverProvider.TrackQuery query) {
        if (pathfinderJson == null || pathfinderJson.isEmpty() || query == null) {
            return List.of();
        }
        Object parsed = parseJson(pathfinderJson);
        Map<?, ?> root = asMap(parsed);
        Map<?, ?> data = asMap(root != null ? root.get("data") : null);
        Map<?, ?> searchV2 = asMap(data != null ? data.get("searchV2") : null);
        Map<?, ?> tracksV2 = asMap(searchV2 != null ? searchV2.get("tracksV2") : null);
        List<?> items = asList(tracksV2 != null ? tracksV2.get("items") : null);
        if (items == null || items.isEmpty()) return List.of();

        String qSongNorm = AnimatedCoverProvider.MediaTextNormalize.normalize(query.song());
        String qAlbumNorm = AnimatedCoverProvider.MediaTextNormalize.normalize(query.album());
        String qSongVar = normalizeKeepingVariants(query.song());
        String qAlbumVar = normalizeKeepingVariants(query.album());
        String qPrimaryArtist =
                AnimatedCoverProvider.MediaTextNormalize.normalize(
                        AnimatedCoverProvider.MediaTextNormalize.primaryArtist(query.artist()));

        Map<String, TrackCandidate> deduped = new LinkedHashMap<>();
        for (Object entryObj : items) {
            Map<?, ?> entry = asMap(entryObj);
            Map<?, ?> item = asMap(entry != null ? entry.get("item") : null);
            Map<?, ?> trackData = asMap(item != null ? item.get("data") : null);
            if (trackData == null) continue;

            String trackId = asString(trackData.get("id"));
            if (!isBase62TrackId(trackId)) {
                trackId = extractSpotifyTrackId(asString(trackData.get("uri")));
            }
            if (!isBase62TrackId(trackId)) continue;

            String candSong = asString(trackData.get("name"));
            Map<?, ?> albumObj = asMap(trackData.get("albumOfTrack"));
            String candAlbum = asString(albumObj != null ? albumObj.get("name") : null);

            List<String> artistNames = new ArrayList<>();
            Map<?, ?> artistsObj = asMap(trackData.get("artists"));
            List<?> artistItems = asList(artistsObj != null ? artistsObj.get("items") : null);
            if (artistItems != null) {
                for (Object aObj : artistItems) {
                    Map<?, ?> aMap = asMap(aObj);
                    Map<?, ?> profile = asMap(aMap != null ? aMap.get("profile") : null);
                    String name = asString(profile != null ? profile.get("name") : null);
                    if (!name.isEmpty()) artistNames.add(name);
                }
            }
            String joinedArtists = String.join(", ", artistNames);
            if (joinedArtists.isEmpty()) continue;

            boolean artistMatches = matchesArtist(query.artist(), joinedArtists);
            if (!artistMatches) {
                for (String artistName : artistNames) {
                    if (matchesArtist(query.artist(), artistName)) {
                        artistMatches = true;
                        break;
                    }
                }
            }
            if (!artistMatches) continue;

            String cSongNorm = AnimatedCoverProvider.MediaTextNormalize.normalize(candSong);
            String cAlbumNorm = AnimatedCoverProvider.MediaTextNormalize.normalize(candAlbum);
            if (!qSongNorm.isEmpty()) {
                if (cSongNorm.isEmpty()
                        || (!cSongNorm.contains(qSongNorm) && !qSongNorm.contains(cSongNorm))) {
                    continue;
                }
            } else {
                if (qAlbumNorm.isEmpty()
                        || cAlbumNorm.isEmpty()
                        || (!cAlbumNorm.contains(qAlbumNorm) && !qAlbumNorm.contains(cAlbumNorm))) {
                    continue;
                }
            }

            boolean exactPrimaryArtist = false;
            if (!qPrimaryArtist.isEmpty()) {
                for (String artistName : artistNames) {
                    if (qPrimaryArtist.equals(
                            AnimatedCoverProvider.MediaTextNormalize.normalize(artistName))) {
                        exactPrimaryArtist = true;
                        break;
                    }
                }
            }

            String cSongVar = normalizeKeepingVariants(candSong);
            String cAlbumVar = normalizeKeepingVariants(candAlbum);

            int score = 0;
            if (!qSongNorm.isEmpty()) {
                if (cSongNorm.equals(qSongNorm)) score += 140;
                else score += 50;
            }
            if (!qSongVar.isEmpty() && cSongVar.equals(qSongVar)) score += 35;
            if (!qAlbumNorm.isEmpty() && !cAlbumNorm.isEmpty()) {
                if (cAlbumNorm.equals(qAlbumNorm)) score += 90;
                else if (cAlbumNorm.contains(qAlbumNorm) || qAlbumNorm.contains(cAlbumNorm)) {
                    score += 30;
                }
            }
            if (exactPrimaryArtist) score += 40;

            for (String noise : VARIANT_NOISE_TERMS) {
                boolean inCand =
                        containsToken(cSongNorm, noise)
                                || containsToken(cAlbumNorm, noise)
                                || containsToken(cSongVar, noise)
                                || containsToken(cAlbumVar, noise);
                boolean inQuery =
                        containsToken(qSongNorm, noise)
                                || containsToken(qAlbumNorm, noise)
                                || containsToken(qSongVar, noise)
                                || containsToken(qAlbumVar, noise);
                if (inCand && !inQuery) score -= 45;
            }

            TrackCandidate candidate =
                    new TrackCandidate(trackId, candSong, joinedArtists, candAlbum, score);
            TrackCandidate existing = deduped.get(trackId);
            if (existing == null || candidate.score() > existing.score()) {
                deduped.put(trackId, candidate);
            }
        }

        if (deduped.isEmpty()) return List.of();
        List<TrackCandidate> ranked = new ArrayList<>(deduped.values());
        ranked.sort(Comparator.comparingInt(TrackCandidate::score).reversed());
        return ranked.size() <= 4 ? ranked : new ArrayList<>(ranked.subList(0, 4));
    }

    static CanvasVideo parseCanvasResponse(String canvasApiJson) {
        Object parsed = parseJson(canvasApiJson);
        Map<?, ?> root = asMap(parsed);
        if (root == null) return null;
        List<?> list = asList(root.get("canvasesList"));
        if (list == null || list.isEmpty()) return null;
        for (Object itemObj : list) {
            Map<?, ?> item = asMap(itemObj);
            if (item == null) continue;
            String canvasUrl = asString(item.get("canvasUrl"));
            if (!isSupportedCanvasVideoUrl(canvasUrl)) continue;
            String canvasId = asString(item.get("id"));
            String trackUri = asString(item.get("trackUri"));
            return new CanvasVideo(canvasId, canvasUrl, trackUri);
        }
        return null;
    }

    static String deriveCanvasAssetId(String canvasId, String videoUrl) {
        if (canvasId != null && !canvasId.isEmpty()) {
            String cleaned = CANVAS_ID_SANITIZE.matcher(canvasId.trim()).replaceAll("");
            if (!cleaned.isEmpty() && cleaned.length() <= 64) {
                return cleaned;
            }
        }
        return AnimatedCoverCache.sha256Hex(videoUrl == null ? "" : videoUrl.trim());
    }

    private static boolean matchesArtist(String queryArtist, String candidateArtist) {
        String qPrimary =
                AnimatedCoverProvider.MediaTextNormalize.normalize(
                        AnimatedCoverProvider.MediaTextNormalize.primaryArtist(queryArtist));
        String qFull = AnimatedCoverProvider.MediaTextNormalize.normalize(queryArtist);
        String cArtist = AnimatedCoverProvider.MediaTextNormalize.normalize(candidateArtist);
        String cPrimary =
                AnimatedCoverProvider.MediaTextNormalize.normalize(
                        AnimatedCoverProvider.MediaTextNormalize.primaryArtist(candidateArtist));
        if (cArtist.isEmpty() || (qPrimary.isEmpty() && qFull.isEmpty())) return false;
        return (!qPrimary.isEmpty()
                        && (cArtist.contains(qPrimary) || qPrimary.contains(cArtist)))
                || (!qFull.isEmpty()
                        && (qFull.contains(cArtist)
                                || (!cPrimary.isEmpty() && qFull.contains(cPrimary))));
    }

    private static boolean containsToken(String text, String token) {
        if (text == null || text.isEmpty()) return false;
        return (" " + text + " ").contains(" " + token + " ");
    }

    private static String normalizeKeepingVariants(String input) {
        if (input == null || input.isEmpty()) return "";
        String deaccented =
                DIACRITICS_PATTERN
                        .matcher(Normalizer.normalize(input, Normalizer.Form.NFD))
                        .replaceAll("");
        return NON_ALNUM_PATTERN
                .matcher(deaccented.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }

    private static boolean isBase62TrackId(String id) {
        if (id == null || id.length() != 22) return false;
        for (int i = 0; i < 22; i++) {
            char c = id.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!ok) return false;
        }
        return true;
    }

    private static String extractQueryParam(String rawQuery, String paramName) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            if (!paramName.equals(key)) continue;
            if (eq < 0) return "";
            try {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8.name())
                        .trim();
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
                return "";
            }
        }
        return "";
    }

    private static String escapeJsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static Map<?, ?> asMap(Object obj) {
        return obj instanceof Map<?, ?> map ? map : null;
    }

    private static List<?> asList(Object obj) {
        return obj instanceof List<?> list ? list : null;
    }

    private static String asString(Object obj) {
        return obj instanceof String s ? s.trim() : "";
    }

    private static Object parseJson(String text) {
        if (text == null) return null;
        JsonReader reader = new JsonReader(text);
        Object value = reader.readValue(0);
        reader.skipWhitespace();
        return reader.pos == reader.src.length() ? value : null;
    }

    private static final class JsonReader {
        private static final int MAX_DEPTH = 64;
        final String src;
        int pos;

        JsonReader(String src) {
            this.src = src;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) return null;
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> readObject(depth + 1);
                case '[' -> readArray(depth + 1);
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> (c == '-' || (c >= '0' && c <= '9')) ? readNumber() : null;
            };
        }

        private Map<String, Object> readObject(int depth) {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != '"') return null;
                String key = readString();
                if (key == null) return null;
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != ':') return null;
                pos++;
                int before = pos;
                Object value = readValue(depth);
                if (pos == before) return null;
                map.put(key, value);
                skipWhitespace();
                if (pos >= src.length()) return null;
                char next = src.charAt(pos++);
                if (next == '}') return map;
                if (next != ',') return null;
            }
            return null;
        }

        private List<Object> readArray(int depth) {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                int before = pos;
                Object value = readValue(depth);
                if (pos == before) return null;
                list.add(value);
                skipWhitespace();
                if (pos >= src.length()) return null;
                char next = src.charAt(pos++);
                if (next == ']') return list;
                if (next != ',') return null;
            }
            return null;
        }

        private String readString() {
            if (pos >= src.length() || src.charAt(pos) != '"') return null;
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c < 0x20) return null;
                if (c == '\\') {
                    if (pos >= src.length()) return null;
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) return null;
                            try {
                                int code = Integer.parseInt(src.substring(pos, pos + 4), 16);
                                sb.append((char) code);
                                pos += 4;
                            } catch (NumberFormatException ignored) {
                                return null;
                            }
                        }
                        default -> {
                            return null;
                        }
                    }
                } else {
                    sb.append(c);
                }
            }
            return null;
        }

        private Object readLiteral(String literal, Object result) {
            if (src.startsWith(literal, pos)) {
                pos += literal.length();
                return result;
            }
            return null;
        }

        private Number readNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            boolean isFloat = false;
            if (pos >= src.length()) return null;
            if (src.charAt(pos) == '0') {
                pos++;
            } else if (src.charAt(pos) >= '1' && src.charAt(pos) <= '9') {
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            } else {
                return null;
            }
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) return null;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) return null;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String token = src.substring(start, pos);
            try {
                if (isFloat) return Double.parseDouble(token);
                return Long.parseLong(token);
            } catch (NumberFormatException ignored) {
                try {
                    return Double.parseDouble(token);
                } catch (NumberFormatException ignored2) {
                    return null;
                }
            }
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
                else break;
            }
        }
    }
}
