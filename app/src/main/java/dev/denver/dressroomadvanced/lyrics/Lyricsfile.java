package dev.denver.dressroomadvanced.lyrics;

import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Reads LRCLIB Lyricsfile 1.0 on the provider worker, preserving source timings. */
public final class Lyricsfile {
    private static final int MAX_SIZE = 262144;
    private static final int MAX_LINES = 1000;
    public record Document(List<LyricLine> lines, String plain, boolean instrumental) {}

    private Lyricsfile() {}

    public static Document parse(String source) {
        if (source == null || source.isBlank()) return new Document(List.of(), null, false);
        if (source.length() > MAX_SIZE) throw new IllegalArgumentException("Lyricsfile is too large");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(MAX_SIZE);
        Object loaded = new Yaml(new SafeConstructor(options)).load(source);
        if (!(loaded instanceof Map<?, ?> doc) || !"1.0".equals(doc.get("version")))
            throw new IllegalArgumentException("Unsupported Lyricsfile version");
        Map<?, ?> metadata = doc.get("metadata") instanceof Map<?, ?> map ? map : Map.of();
        boolean instrumental = Boolean.TRUE.equals(metadata.get("instrumental"));
        String plain = doc.get("plain") instanceof String text ? text : null;
        if (instrumental) return new Document(List.of(), plain, true);
        if (!(doc.get("lines") instanceof List<?> rows)) return new Document(List.of(), plain, false);
        if (rows.size() > MAX_LINES) throw new IllegalArgumentException("Too many lyric lines");
        List<Map<?, ?>> timed = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("text") instanceof String
                    && millis(map.get("start_ms"), -1L) >= 0L) timed.add(map);
        }
        timed.sort(Comparator.comparingLong(row -> millis(row.get("start_ms"), -1L)));
        List<LyricLine> lines = new ArrayList<>(timed.size());
        for (int i = 0; i < timed.size(); i++) {
            Map<?, ?> row = timed.get(i);
            String rawText = (String) row.get("text");
            if (rawText.isBlank()) continue;
            long start = millis(row.get("start_ms"), 0L);
            long next = -1L;
            for (int j = i + 1; j < timed.size(); j++) {
                long candidate = millis(timed.get(j).get("start_ms"), -1L);
                if (candidate > start) { next = candidate; break; }
            }
            long end = millis(row.get("end_ms"), -1L);
            if (end < 0L) end = next > start ? next
                    : Math.max(start + 3200L, millis(metadata.get("duration_ms"), 0L));
            if (end < start) continue;
            List<LyricLine.Word> words = words(row.get("words"), rawText, end);
            lines.add(new LyricLine(start, end, rawText, words, false, false));
        }
        return new Document(List.copyOf(lines), plain, false);
    }

    private static List<LyricLine.Word> words(Object value, String line, long lineEnd) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 512) return List.of();
        List<Map<?, ?>> segments = new ArrayList<>(list.size());
        StringBuilder joined = new StringBuilder();
        long last = -1L;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> word) || !(word.get("text") instanceof String text)) return List.of();
            long start = millis(word.get("start_ms"), -1L);
            if (start < last || start < 0L) return List.of();
            last = start;
            joined.append(text);
            segments.add(word);
        }
        // Never guess text ranges when the payload's words and display text differ.
        if (!line.contentEquals(joined)) return List.of();
        String trimmed = line.trim();
        int trimStart = line.indexOf(trimmed), cursor = 0;
        List<LyricLine.Word> result = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            Map<?, ?> word = segments.get(i);
            String text = (String) word.get("text");
            long start = millis(word.get("start_ms"), 0L);
            long end = millis(word.get("end_ms"), -1L);
            if (end < 0L) end = i + 1 < segments.size()
                    ? millis(segments.get(i + 1).get("start_ms"), lineEnd) : lineEnd;
            if (end < start) return List.of();
            int first = Math.max(0, cursor - trimStart);
            cursor += text.length();
            int limit = Math.min(trimmed.length(), cursor - trimStart);
            while (first < limit && Character.isWhitespace(trimmed.charAt(first))) first++;
            while (limit > first && Character.isWhitespace(trimmed.charAt(limit - 1))) limit--;
            if (limit > first) result.add(new LyricLine.Word(start, end, first, limit, trimmed.substring(first, limit)));
        }
        return List.copyOf(result);
    }

    private static long millis(Object value, long fallback) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            return fallback;
        long number = ((Number) value).longValue();
        return number >= 0L && number <= 86400000L ? number : fallback;
    }

    public static JSONArray encode(List<LyricLine> lines) {
        JSONArray array = new JSONArray();
        try {
            for (LyricLine line : lines) {
                JSONObject row = new JSONObject().put("text", line.text())
                        .put("start", line.timestampMs()).put("end", line.endTimestampMs());
                JSONArray words = new JSONArray();
                for (LyricLine.Word word : line.words()) words.put(new JSONObject()
                        .put("start", word.startMs()).put("end", word.endMs())
                        .put("first", word.startChar()).put("limit", word.endChar()).put("text", word.text()));
                row.put("words", words);
                array.put(row);
            }
        } catch (org.json.JSONException invalid) {
            throw new IllegalArgumentException("Invalid lyric timeline", invalid);
        }
        return array;
    }

    public static List<LyricLine> decode(JSONArray array) {
        if (array == null || array.length() > MAX_LINES) return List.of();
        List<LyricLine> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            List<LyricLine.Word> words = new ArrayList<>();
            JSONArray spans = row.optJSONArray("words");
            if (spans != null && spans.length() <= 512) for (int j = 0; j < spans.length(); j++) {
                JSONObject word = spans.optJSONObject(j);
                if (word != null) words.add(new LyricLine.Word(word.optLong("start"), word.optLong("end"),
                        word.optInt("first"), word.optInt("limit"), word.optString("text")));
            }
            result.add(new LyricLine(row.optLong("start"), row.optLong("end"),
                    row.optString("text"), words, false, false));
        }
        return List.copyOf(result);
    }
}
