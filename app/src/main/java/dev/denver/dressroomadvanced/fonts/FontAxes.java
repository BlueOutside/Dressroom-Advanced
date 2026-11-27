package dev.denver.dressroomadvanced;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** OpenType fvar/name metadata. No Android font initialization is needed to read it. */
final class FontAxes {
    static final float SAMSUNG_MIN_HEGT = 1f;
    static final float SAMSUNG_HORIZONTAL_MAX_HEGT = 700f;
    static final float SAMSUNG_VERTICAL_MAX_HEGT = 400f;

    record Axis(String tag, String name, float min, float defaultValue, float max, boolean hidden) {
        float clamp(float value) {
            return Math.max(min, Math.min(max, value));
        }

        String label() {
            return switch (tag) {
                case "wght" -> "Weight";
                case "wdth" -> "Width";
                case "opsz" -> "Optical size";
                case "slnt" -> "Slant";
                case "ital" -> "Italic";
                case "GRAD" -> "Grade";
                case "HEGT" -> "Height (HEGT)";
                case "HGHT" -> "Height (HGHT)";
                case "RNDN" -> "Roundness (RNDN)";
                case "YTLC" -> "Lowercase height (YTLC)";
                case "BLED" -> "Bleed";
                default -> name == null || name.isBlank() ? tag : name + " (" + tag + ")";
            };
        }

        String label(android.content.Context context) {
            return switch (tag) {
                case "wght" -> ModuleStrings.getOrFallback(context, R.string.font_axis_weight, "Weight");
                case "wdth" -> ModuleStrings.getOrFallback(context, R.string.font_axis_width, "Width");
                case "opsz" -> ModuleStrings.getOrFallback(context, R.string.font_axis_optical_size, "Optical size");
                case "slnt" -> ModuleStrings.getOrFallback(context, R.string.font_axis_slant, "Slant");
                case "ital" -> ModuleStrings.getOrFallback(context, R.string.font_axis_italic, "Italic");
                case "GRAD" -> ModuleStrings.getOrFallback(context, R.string.font_axis_grade, "Grade");
                case "HEGT" -> ModuleStrings.getOrFallback(context, R.string.font_axis_height_hegt, "Height (HEGT)");
                case "HGHT" -> ModuleStrings.getOrFallback(context, R.string.font_axis_height_hght, "Height (HGHT)");
                case "RNDN" -> ModuleStrings.getOrFallback(context, R.string.font_axis_roundness_rndn, "Roundness (RNDN)");
                case "YTLC" -> ModuleStrings.getOrFallback(context, R.string.font_axis_lowercase_height_ytlc, "Lowercase height (YTLC)");
                case "BLED" -> ModuleStrings.getOrFallback(context, R.string.font_axis_bleed, "Bleed");
                default -> name == null || name.isBlank()
                        ? tag
                        : ModuleStrings.getOrFallback(
                                context, R.string.font_axis_custom_named, "%1$s (%2$s)", name, tag);
            };
        }
    }

    private record Table(int offset, int length) {}

    private FontAxes() {}

    /**
     * Returns the font's vertical stretch height axis ({@code HEGT} preferred, else {@code HGHT})
     * when it spans a positive range, or {@code null} if the font has no height stretch axis.
     */
    static Axis heightAxis(List<Axis> axes) {
        if (axes == null || axes.isEmpty()) return null;
        Axis fallback = null;
        for (Axis axis : axes) {
            if (axis == null || axis.max() <= axis.min()) continue;
            if ("HEGT".equals(axis.tag())) return axis;
            if ("HGHT".equals(axis.tag()) && fallback == null) fallback = axis;
        }
        return fallback;
    }

    /**
     * Extracts the numeric value of {@code tag} from an OpenType {@code fontVariationSettings}
     * string such as {@code "'wght' 700, 'RNDN' 20, 'HEGT' 472"}, or {@link Float#NaN} if absent.
     */
    static float parseVariationAxis(String variationSettings, String tag) {
        if (variationSettings == null
                || variationSettings.isEmpty()
                || tag == null
                || tag.length() != 4) {
            return Float.NaN;
        }
        int length = variationSettings.length();
        int i = 0;
        while (i < length) {
            while (i < length && (variationSettings.charAt(i) <= ' ' || variationSettings.charAt(i) == ',')) {
                i++;
            }
            if (i >= length) break;
            char quote = variationSettings.charAt(i);
            int tagStart = i;
            if (quote == '\'' || quote == '"') {
                tagStart = ++i;
                if (tagStart + 4 > length || variationSettings.charAt(tagStart + 4) != quote) {
                    int comma = variationSettings.indexOf(',', i);
                    if (comma < 0) break;
                    i = comma + 1;
                    continue;
                }
                i = tagStart + 5;
            } else {
                if (tagStart + 4 > length) break;
                i = tagStart + 4;
            }
            boolean match = variationSettings.regionMatches(tagStart, tag, 0, 4);
            while (i < length && variationSettings.charAt(i) <= ' ') i++;
            int valStart = i;
            while (i < length && variationSettings.charAt(i) != ',') i++;
            if (match) {
                String token = variationSettings.substring(valStart, i).trim();
                if (token.isEmpty()) return Float.NaN;
                try {
                    float value = Float.parseFloat(token);
                    return Float.isFinite(value) ? value : Float.NaN;
                } catch (NumberFormatException ignored) {
                    return Float.NaN;
                }
            }
            i++;
        }
        return Float.NaN;
    }

    /**
     * Maps Samsung's per-digit {@code HEGT} axis value ({@code 1..700} horizontal or {@code
     * 1..400} vertical) linearly onto an imported font's own height axis range {@code [base, max]},
     * quantized cleanly for variable-font typeface cache reuse during spring animations.
     */
    static float mapStretchAxis(Axis axis, float nativeHegt, boolean vertical) {
        if (axis == null || !Float.isFinite(nativeHegt)) {
            return axis == null ? Float.NaN : axis.defaultValue();
        }
        float maxHegt = vertical ? SAMSUNG_VERTICAL_MAX_HEGT : SAMSUNG_HORIZONTAL_MAX_HEGT;
        float t = (nativeHegt - SAMSUNG_MIN_HEGT) / (maxHegt - SAMSUNG_MIN_HEGT);
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        float base = axis.defaultValue() < axis.max() ? axis.defaultValue() : axis.min();
        float mapped = base + t * (axis.max() - base);
        float span = axis.max() - axis.min();
        if (span >= 50f) {
            mapped = Math.round(mapped);
        } else if (span > 0f) {
            mapped = axis.min() + Math.round(((mapped - axis.min()) / span) * 200f) * (span / 200f);
        }
        return axis.clamp(mapped);
    }

    static boolean validTag(String tag) {
        if (tag == null || tag.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            char c = tag.charAt(i);
            if (c < 32 || c > 126 || c == '\'' || c == '\\') return false;
        }
        return true;
    }

    static List<Axis> parse(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return List.of();
        try {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            int base = 0;
            if (b.getInt(0) == 0x74746366) {
                if (bytes.length < 16 || b.getInt(8) <= 0) return List.of();
                base = b.getInt(12); // Typeface.Builder uses collection face zero too.
            }
            if (!range(bytes.length, base, 12)) return List.of();
            int count = u16(b, base + 4);
            if (count > 4096 || !range(bytes.length, (long) base + 12, (long) count * 16))
                return List.of();
            Table fvar = null, names = null;
            for (int i = 0; i < count; i++) {
                int p = base + 12 + i * 16;
                int tag = b.getInt(p), offset = b.getInt(p + 8), length = b.getInt(p + 12);
                if (!range(bytes.length, offset, length)) return List.of();
                if (tag == 0x66766172) fvar = new Table(offset, length);
                if (tag == 0x6e616d65) names = new Table(offset, length);
            }
            if (fvar == null || fvar.length < 16 || u16(b, fvar.offset) != 1) return List.of();
            int offset = u16(b, fvar.offset + 4),
                    axes = u16(b, fvar.offset + 8),
                    stride = u16(b, fvar.offset + 10);
            if (axes == 0
                    || axes > 64
                    || offset < 16
                    || stride < 20
                    || !range(fvar.length, offset, (long) axes * stride)) return List.of();
            Map<Integer, String> labels = names(b, names);
            List<Axis> result = new ArrayList<>(axes);
            HashSet<String> tags = new HashSet<>();
            for (int i = 0; i < axes; i++) {
                int p = fvar.offset + offset + i * stride;
                String tag = new String(bytes, p, 4, StandardCharsets.US_ASCII);
                float min = b.getInt(p + 4) / 65536f,
                        value = b.getInt(p + 8) / 65536f,
                        max = b.getInt(p + 12) / 65536f;
                if (!validTag(tag) || !tags.add(tag) || min > value || value > max)
                    return List.of();
                result.add(
                        new Axis(
                                tag,
                                labels.get(u16(b, p + 18)),
                                min,
                                value,
                                max,
                                (u16(b, p + 16) & 1) != 0));
            }
            return List.copyOf(result);
        } catch (IndexOutOfBoundsException | IllegalArgumentException error) {
            return List.of();
        }
    }

    private static Map<Integer, String> names(ByteBuffer b, Table table) {
        Map<Integer, String> result = new HashMap<>();
        if (table == null || table.length < 6 || u16(b, table.offset) > 1) return result;
        int count = u16(b, table.offset + 2), storage = u16(b, table.offset + 4);
        if (count > 4096 || storage < 6L + count * 12L || !range(table.length, 6, count * 12L))
            return result;
        Map<Integer, Integer> priorities = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int p = table.offset + 6 + i * 12;
            int platform = u16(b, p), language = u16(b, p + 4), id = u16(b, p + 6);
            int length = u16(b, p + 8), offset = u16(b, p + 10);
            if ((platform != 0 && platform != 3)
                    || length == 0
                    || length > 512
                    || (length & 1) != 0
                    || !range(table.length, (long) storage + offset, length)) continue;
            int priority = platform == 3 && language == 0x409 ? 3 : platform == 0 ? 2 : 1;
            if (priorities.getOrDefault(id, 0) >= priority) continue;
            String name =
                    new String(
                                    b.array(),
                                    table.offset + storage + offset,
                                    length,
                                    StandardCharsets.UTF_16BE)
                            .replaceAll("[\\p{Cntrl}]", " ")
                            .strip();
            if (name.length() > 80) name = name.substring(0, 80);
            result.put(id, name);
            priorities.put(id, priority);
        }
        return result;
    }

    private static int u16(ByteBuffer b, int p) {
        return b.getShort(p) & 0xffff;
    }

    private static boolean range(int size, long offset, long length) {
        return offset >= 0 && length >= 0 && offset <= size && length <= size - offset;
    }
}
