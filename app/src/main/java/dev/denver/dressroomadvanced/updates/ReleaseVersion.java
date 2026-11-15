package dev.denver.dressroomadvanced;

import java.util.regex.Pattern;

final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT =
            Pattern.compile(
                    "^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");
    private final long[] numbers;
    private final String suffix;

    private ReleaseVersion(long[] numbers, String suffix) {
        this.numbers = numbers;
        this.suffix = suffix;
    }

    static ReleaseVersion parse(String text) {
        if (text == null || text.length() > 100)
            throw new IllegalArgumentException("Invalid version");
        var match = FORMAT.matcher(text.trim());
        if (!match.matches()) throw new IllegalArgumentException("Invalid version: " + text);
        return new ReleaseVersion(
                new long[] {
                    Long.parseLong(match.group(1)),
                    Long.parseLong(match.group(2)),
                    Long.parseLong(match.group(3))
                },
                match.group(4));
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        for (int i = 0; i < numbers.length; i++) {
            int order = Long.compare(numbers[i], other.numbers[i]);
            if (order != 0) return order;
        }
        if (suffix == null) return other.suffix == null ? 0 : 1;
        if (other.suffix == null) return -1;
        String[] left = suffix.split("\\."), right = other.suffix.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean a = left[i].matches("[0-9]+"), b = right[i].matches("[0-9]+");
            int order =
                    a && b
                            ? new java.math.BigInteger(left[i])
                                    .compareTo(new java.math.BigInteger(right[i]))
                            : a != b ? (a ? -1 : 1) : left[i].compareTo(right[i]);
            if (order != 0) return order;
        }
        return Integer.compare(left.length, right.length);
    }
}
