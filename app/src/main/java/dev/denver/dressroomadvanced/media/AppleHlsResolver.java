package dev.denver.dressroomadvanced;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java parser for Apple Music public SSR {@code serialized-server-data} HTML payloads, master
 * HLS playlists, single-file fMP4 byterange variant playlists, and aspect-preserving TextureView
 * center-crop transforms.
 */
final class AppleHlsResolver {
    private static final String SCRIPT_ID = "serialized-server-data";
    private static final int DEFAULT_MAX_WIDTH = 1080;
    private static final int PREFERRED_MIN_WIDTH = 480;

    record AppleEditorialVideo(String tallM3u8Url, String squareM3u8Url) {
        boolean isEmpty() {
            return (tallM3u8Url == null || tallM3u8Url.isEmpty())
                    && (squareM3u8Url == null || squareM3u8Url.isEmpty());
        }
    }

    record VideoVariant(
            int bandwidth, int width, int height, String codecs, boolean sdr, String uri) {
        boolean isHevc() {
            return codecs != null && codecs.toLowerCase(Locale.ROOT).contains("hvc1");
        }

        boolean isAvc() {
            return codecs != null && codecs.toLowerCase(Locale.ROOT).contains("avc1");
        }
    }

    record SearchLockup(
            String kind, String title, String subtitle, String collectionId, String albumUrl) {}

    private static final class JsonObjectFrame {
        final String parentKey;
        String pendingKey = "";
        String title = "";
        String subtitle = "";
        String itemKind = "";
        String kind = "";
        String url = "";
        String collectionId = "";
        String albumUrl = "";

        JsonObjectFrame(String parentKey) {
            this.parentKey = parentKey;
        }
    }

    private AppleHlsResolver() {}

    /**
     * Extracts {@code motionDetailTall} and {@code motionDetailSquare} master {@code .m3u8} URLs
     * from an Apple Music web album HTML document's {@code serialized-server-data} JSON block.
     */
    static AppleEditorialVideo extractEditorialVideoFromHtml(String html) {
        if (html == null || html.isEmpty()) return new AppleEditorialVideo("", "");
        String json = extractSerializedServerData(html);
        if (json.isEmpty()) return new AppleEditorialVideo("", "");
        String tall = extractNestedVideoUrl(json, "motionDetailTall");
        String square = extractNestedVideoUrl(json, "motionDetailSquare");
        return new AppleEditorialVideo(tall, square);
    }

    /**
     * Extracts song/album catalog lockups from a public {@code music.apple.com/<country>/search}
     * HTML SSR document's {@code serialized-server-data} JSON block.
     */
    static List<SearchLockup> extractSearchCandidatesFromHtml(String html) {
        List<SearchLockup> results = new ArrayList<>();
        if (html == null || html.isEmpty()) return results;
        String json = extractSerializedServerData(html);
        if (json.isEmpty()) return results;

        List<JsonObjectFrame> stack = new ArrayList<>();
        // Track whether each brace/bracket container is an object (true) or array (false).
        List<Boolean> containerIsObject = new ArrayList<>();
        List<String> containerKeys = new ArrayList<>();
        String nextObjectKey = "";
        int len = json.length();
        int i = 0;

        while (i < len) {
            char c = json.charAt(i);
            if (c == '"') {
                int endQuote = findStringEnd(json, i);
                if (endQuote < 0) break;
                int after = skipWhitespace(json, endQuote + 1);
                boolean isKey = after < len && json.charAt(after) == ':';
                boolean topIsObject =
                        !containerIsObject.isEmpty()
                                && containerIsObject.get(containerIsObject.size() - 1);
                if (isKey && topIsObject && !stack.isEmpty()) {
                    String key = unescapeJsonString(json, i + 1, endQuote);
                    stack.get(stack.size() - 1).pendingKey = key;
                    nextObjectKey = key;
                    i = after + 1;
                    continue;
                }
                if (topIsObject && !stack.isEmpty()) {
                    JsonObjectFrame frame = stack.get(stack.size() - 1);
                    String field = frame.pendingKey;
                    if (!field.isEmpty()) {
                        String val = unescapeJsonString(json, i + 1, endQuote);
                        switch (field) {
                            case "title" -> {
                                if (frame.title.isEmpty()) frame.title = val;
                            }
                            case "subtitle" -> {
                                if (frame.subtitle.isEmpty()) frame.subtitle = val;
                            }
                            case "itemKind" -> frame.itemKind = val;
                            case "kind" -> frame.kind = val;
                            case "url" -> frame.url = val;
                            default -> {
                            }
                        }
                        frame.pendingKey = "";
                    }
                }
                i = endQuote + 1;
                continue;
            }
            if (c == '{') {
                String parentKey = nextObjectKey;
                if (parentKey.isEmpty()
                        && !containerIsObject.isEmpty()
                        && !containerIsObject.get(containerIsObject.size() - 1)) {
                    parentKey = containerKeys.get(containerKeys.size() - 1);
                }
                nextObjectKey = "";
                containerIsObject.add(true);
                containerKeys.add(parentKey);
                stack.add(new JsonObjectFrame(parentKey));
            } else if (c == '[') {
                String arrayKey = nextObjectKey;
                nextObjectKey = "";
                containerIsObject.add(false);
                containerKeys.add(arrayKey);
            } else if (c == '}') {
                nextObjectKey = "";
                if (!containerIsObject.isEmpty()) {
                    boolean wasObj = containerIsObject.remove(containerIsObject.size() - 1);
                    containerKeys.remove(containerKeys.size() - 1);
                    if (wasObj && !stack.isEmpty()) {
                        JsonObjectFrame popped = stack.remove(stack.size() - 1);
                        JsonObjectFrame parent =
                                stack.isEmpty() ? null : stack.get(stack.size() - 1);
                        onJsonObjectPopped(popped, parent, results);
                    }
                }
            } else if (c == ']') {
                nextObjectKey = "";
                if (!containerIsObject.isEmpty()) {
                    containerIsObject.remove(containerIsObject.size() - 1);
                    containerKeys.remove(containerKeys.size() - 1);
                }
            } else if (c == ',') {
                nextObjectKey = "";
                if (!stack.isEmpty()) stack.get(stack.size() - 1).pendingKey = "";
            }
            i++;
        }
        return results;
    }

    static String artistFromSearchSubtitle(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return "";
        int dot = subtitle.lastIndexOf('\u00b7');
        String raw = dot >= 0 && dot + 1 < subtitle.length() ? subtitle.substring(dot + 1) : subtitle;
        return raw.replace('\u202f', ' ').replace('\u00a0', ' ').trim();
    }

    static String extractAlbumIdFromAppleUrl(String url) {
        if (!isHttpsUrl(url)) return "";
        try {
            String path = URI.create(url).getPath();
            if (path == null || !path.contains("/album/")) return "";
            int slash = path.lastIndexOf('/');
            if (slash < 0 || slash + 1 >= path.length()) return "";
            String tail = path.substring(slash + 1);
            for (int i = 0; i < tail.length(); i++) {
                if (!Character.isDigit(tail.charAt(i))) return "";
            }
            return tail;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    static String stripQueryFromUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static void onJsonObjectPopped(
            JsonObjectFrame popped, JsonObjectFrame parent, List<SearchLockup> results) {
        if ("subtitleLinks".equals(popped.parentKey)
                && parent != null
                && !popped.title.isEmpty()) {
            if (parent.subtitle.isEmpty()) parent.subtitle = popped.title;
            else parent.subtitle = parent.subtitle + ", " + popped.title;
            return;
        }
        if ("contentDescriptor".equals(popped.parentKey)
                && parent != null
                && parent.albumUrl.isEmpty()
                && !popped.url.isEmpty()) {
            String collectionId = extractAlbumIdFromAppleUrl(popped.url);
            if (!collectionId.isEmpty()) {
                parent.collectionId = collectionId;
                parent.albumUrl = stripQueryFromUrl(popped.url);
                if (parent.kind.isEmpty()) parent.kind = popped.kind;
            }
            return;
        }
        if (("destination".equals(popped.parentKey) || "segue".equals(popped.parentKey))
                && parent != null
                && parent.albumUrl.isEmpty()
                && !popped.albumUrl.isEmpty()
                && !popped.collectionId.isEmpty()) {
            parent.collectionId = popped.collectionId;
            parent.albumUrl = popped.albumUrl;
            if (parent.kind.isEmpty()) parent.kind = popped.kind;
            return;
        }
        if (!popped.title.isEmpty()
                && !popped.albumUrl.isEmpty()
                && !popped.collectionId.isEmpty()) {
            String kind = !popped.kind.isEmpty() ? popped.kind : popped.itemKind;
            results.add(
                    new SearchLockup(
                            kind,
                            popped.title,
                            popped.subtitle,
                            popped.collectionId,
                            popped.albumUrl));
        }
    }

    static String extractSerializedServerData(String html) {
        int idPos = html.indexOf(SCRIPT_ID);
        while (idPos >= 0) {
            int openScript = html.lastIndexOf("<script", idPos);
            int openEnd = openScript >= 0 ? html.indexOf('>', idPos) : -1;
            int closeScript = openEnd >= 0 ? html.indexOf("</script>", openEnd + 1) : -1;
            if (openScript >= 0 && openEnd >= 0 && closeScript > openEnd) {
                String attrs = html.substring(openScript, openEnd);
                if (attrs.contains("id=\"" + SCRIPT_ID + "\"")
                        || attrs.contains("id='" + SCRIPT_ID + "'")) {
                    return html.substring(openEnd + 1, closeScript).trim();
                }
            }
            idPos = html.indexOf(SCRIPT_ID, idPos + SCRIPT_ID.length());
        }
        return "";
    }

    /**
     * Parses a variant stream list from a master HLS {@code .m3u8} playlist, ignoring I-frame-only
     * trick-play entries.
     */
    static List<VideoVariant> parseMasterPlaylist(String m3u8Content, String baseUrl) {
        List<VideoVariant> result = new ArrayList<>();
        if (m3u8Content == null || m3u8Content.isEmpty()) return result;

        String[] lines = m3u8Content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue;
            Map<String, String> attrs = parseAttributes(line.substring("#EXT-X-STREAM-INF:".length()));
            String uri = "";
            for (int j = i + 1; j < lines.length; j++) {
                String candidate = lines[j].trim();
                if (candidate.isEmpty()) continue;
                if (candidate.startsWith("#")) break;
                uri = resolveUri(baseUrl, candidate);
                i = j;
                break;
            }
            if (!isHttpsUrl(uri)) continue;

            int[] res = parseResolution(attrs.get("RESOLUTION"));
            int bw = parseIntAttr(attrs.get("AVERAGE-BANDWIDTH"));
            if (bw <= 0) bw = parseIntAttr(attrs.get("BANDWIDTH"));
            String range = attrs.getOrDefault("VIDEO-RANGE", "");
            boolean sdr = range.isEmpty() || "SDR".equalsIgnoreCase(range);
            result.add(new VideoVariant(bw, res[0], res[1], attrs.getOrDefault("CODECS", ""), sdr, uri));
        }
        return result;
    }

    /**
     * Picks a hardware-friendly SDR stream capped at {@code maxWidthPx} (default 1080px), preferring
     * HEVC only when {@code preferHevc} is true.
     */
    static VideoVariant selectBestVariant(
            List<VideoVariant> variants, int maxWidthPx, boolean preferHevc) {
        if (variants == null || variants.isEmpty()) return null;
        int cap = maxWidthPx > 0 ? Math.min(DEFAULT_MAX_WIDTH, maxWidthPx) : DEFAULT_MAX_WIDTH;

        List<VideoVariant> valid = new ArrayList<>();
        boolean anySdr = false;
        for (VideoVariant v : variants) {
            if (v == null || v.width() <= 0 || v.height() <= 0 || !isHttpsUrl(v.uri())) continue;
            valid.add(v);
            if (v.sdr()) anySdr = true;
        }
        if (valid.isEmpty()) return null;

        VideoVariant best = null;
        for (VideoVariant candidate : valid) {
            if (anySdr && !candidate.sdr()) continue;
            if (best == null || isBetterVariant(candidate, best, cap, preferHevc)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Resolves a single self-contained {@code .mp4} URL from a byterange HLS variant playlist when
     * the {@code #EXT-X-MAP} init section and every media segment point to the exact same asset.
     */
    static String resolveSingleMp4FromVariantPlaylist(String variantM3u8, String variantBaseUrl) {
        if (variantM3u8 == null || variantM3u8.isEmpty()) return null;
        String[] lines = variantM3u8.split("\\r?\\n");
        String mapUri = null;
        String segmentUri = null;
        boolean sawSegment = false;
        boolean sawByterange = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-MAP:")) {
                Map<String, String> attrs = parseAttributes(line.substring("#EXT-X-MAP:".length()));
                String rawMap = attrs.get("URI");
                if (rawMap == null || rawMap.isEmpty()) return null;
                mapUri = resolveUri(variantBaseUrl, rawMap);
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                sawByterange = true;
            } else if (line.startsWith("#EXT-X-KEY:")) {
                // Reject encrypted streams cleanly rather than downloading unreadable payloads.
                String method = parseAttributes(line.substring("#EXT-X-KEY:".length())).get("METHOD");
                if (method != null && !"NONE".equalsIgnoreCase(method)) return null;
            } else if (!line.startsWith("#")) {
                String resolved = resolveUri(variantBaseUrl, line);
                if (!sawSegment) {
                    segmentUri = resolved;
                    sawSegment = true;
                } else if (!resolved.equals(segmentUri)) {
                    return null;
                }
            }
        }

        if (!sawSegment || segmentUri == null || !isHttpsUrl(segmentUri)) return null;
        if (mapUri != null && !mapUri.equals(segmentUri)) return null;
        if (lines.length > 0 && !sawByterange && countMediaSegments(lines) > 1) return null;
        String pathLower = URI.create(segmentUri).getPath();
        if (pathLower == null || !pathLower.toLowerCase(Locale.ROOT).endsWith(".mp4")) return null;
        return segmentUri;
    }

    /**
     * Computes center-crop {@code [scaleX, scaleY, pivotX, pivotY]} multipliers for a
     * {@link android.view.TextureView} sized {@code dstW x dstH} receiving a video buffer sized
     * {@code srcW x srcH}. Compensates for TextureView's implicit stretch-to-bounds before transform.
     */
    static float[] computeCenterCropScale(int srcW, int srcH, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            return new float[] {1f, 1f, Math.max(0f, dstW * 0.5f), Math.max(0f, dstH * 0.5f)};
        }
        float uniform = Math.max(dstW / (float) srcW, dstH / (float) srcH);
        float scaleX = (srcW * uniform) / dstW;
        float scaleY = (srcH * uniform) / dstH;
        return new float[] {scaleX, scaleY, dstW * 0.5f, dstH * 0.5f};
    }

    private static int countMediaSegments(String[] lines) {
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) count++;
        }
        return count;
    }

    private static boolean isBetterVariant(
            VideoVariant candidate, VideoVariant current, int widthCap, boolean preferHevc) {
        boolean candInCap = candidate.width() <= widthCap;
        boolean curInCap = current.width() <= widthCap;
        if (candInCap != curInCap) return candInCap;

        if (candInCap) {
            if (candidate.width() != current.width()) {
                return candidate.width() > current.width();
            }
        } else if (candidate.width() != current.width()) {
            return candidate.width() < current.width();
        }

        if (candidate.height() != current.height()) {
            return candidate.width() <= widthCap
                    ? candidate.height() > current.height()
                    : candidate.height() < current.height();
        }

        int candCodecRank = codecRank(candidate, preferHevc);
        int curCodecRank = codecRank(current, preferHevc);
        if (candCodecRank != curCodecRank) return candCodecRank > curCodecRank;

        boolean candSmall = candidate.width() < PREFERRED_MIN_WIDTH;
        boolean curSmall = current.width() < PREFERRED_MIN_WIDTH;
        if (candSmall != curSmall) return !candSmall;

        return candidate.bandwidth() > current.bandwidth();
    }

    private static int codecRank(VideoVariant variant, boolean preferHevc) {
        if (preferHevc) {
            if (variant.isHevc()) return 3;
            if (variant.isAvc()) return 2;
        } else {
            if (variant.isAvc()) return 3;
            if (variant.isHevc()) return 2;
        }
        return 1;
    }

    private static String extractNestedVideoUrl(String json, String containerKey) {
        String needle = "\"" + containerKey + "\"";
        int searchFrom = 0;
        while (searchFrom < json.length()) {
            int keyPos = json.indexOf(needle, searchFrom);
            if (keyPos < 0) return "";
            searchFrom = keyPos + needle.length();
            int cursor = skipWhitespace(json, searchFrom);
            if (cursor >= json.length() || json.charAt(cursor) != ':') continue;
            cursor = skipWhitespace(json, cursor + 1);
            if (cursor >= json.length() || json.charAt(cursor) != '{') continue;
            String found = scanObjectForVideoUrl(json, cursor);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String scanObjectForVideoUrl(String json, int openBracePos) {
        int depth = 0;
        int i = openBracePos;
        int len = json.length();
        while (i < len) {
            char c = json.charAt(i);
            if (c == '"') {
                int endQuote = findStringEnd(json, i);
                if (endQuote < 0) return "";
                if (depth == 1) {
                    String token = unescapeJsonString(json, i + 1, endQuote);
                    if ("video".equals(token)) {
                        int afterKey = skipWhitespace(json, endQuote + 1);
                        if (afterKey < len && json.charAt(afterKey) == ':') {
                            int valStart = skipWhitespace(json, afterKey + 1);
                            if (valStart < len && json.charAt(valStart) == '"') {
                                int valEnd = findStringEnd(json, valStart);
                                if (valEnd > valStart) {
                                    String url = unescapeJsonString(json, valStart + 1, valEnd);
                                    if (isHttpsUrl(url)) return url;
                                }
                            }
                        }
                    }
                }
                i = endQuote + 1;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth <= 0) return "";
            }
            i++;
        }
        return "";
    }

    private static int findStringEnd(String s, int openQuotePos) {
        for (int i = openQuotePos + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJsonString(String s, int start, int end) {
        StringBuilder out = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < end) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"', '\\', '/' -> out.append(next);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < end) {
                            String hex = s.substring(i + 1, i + 5);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                out.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int skipWhitespace(String s, int index) {
        while (index < s.length() && Character.isWhitespace(s.charAt(index))) index++;
        return index;
    }

    private static Map<String, String> parseAttributes(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        int len = raw.length();
        int i = 0;
        while (i < len) {
            int eq = raw.indexOf('=', i);
            if (eq < 0) break;
            String key = raw.substring(i, eq).trim();
            int valStart = eq + 1;
            String value;
            if (valStart < len && raw.charAt(valStart) == '"') {
                int close = raw.indexOf('"', valStart + 1);
                if (close < 0) break;
                value = raw.substring(valStart + 1, close);
                i = close + 1;
                if (i < len && raw.charAt(i) == ',') i++;
            } else {
                int comma = raw.indexOf(',', valStart);
                if (comma < 0) {
                    value = raw.substring(valStart).trim();
                    i = len;
                } else {
                    value = raw.substring(valStart, comma).trim();
                    i = comma + 1;
                }
            }
            if (!key.isEmpty()) map.put(key, value);
        }
        return map;
    }

    private static int[] parseResolution(String res) {
        if (res == null || res.isEmpty()) return new int[] {0, 0};
        int x = res.indexOf('x');
        if (x <= 0 || x >= res.length() - 1) return new int[] {0, 0};
        return new int[] {parseIntAttr(res.substring(0, x)), parseIntAttr(res.substring(x + 1))};
    }

    private static int parseIntAttr(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static String resolveUri(String baseUrl, String candidate) {
        if (candidate == null || candidate.isEmpty()) return "";
        if (candidate.startsWith("https://") || candidate.startsWith("http://")) return candidate;
        if (baseUrl == null || baseUrl.isEmpty()) return candidate;
        try {
            return URI.create(baseUrl).resolve(candidate).toString();
        } catch (IllegalArgumentException ignored) {
            return candidate;
        }
    }

    static boolean isHttpsUrl(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }
}
