package dev.denver.dressroomadvanced;

import java.util.function.Function;

final class ClockLabelFit {
    record Size(float width, float height) {
        boolean fits(Size limit) {
            return width <= limit.width && height <= limit.height;
        }
    }

    record Scales(float date, float weather) {}

    private static final Scales ZERO = new Scales(0, 0);

    private ClockLabelFit() {}

    static Scales fit(Size limit, Scales requested, float normal, Function<Scales, Size> size) {
        if (size.apply(requested).fits(limit)) return requested;
        Scales date = fitBetween(limit, ZERO, new Scales(requested.date, 0), size);
        Scales weather = fitBetween(limit, ZERO, new Scales(0, requested.weather), size);
        Scales capped = new Scales(date.date, weather.weather);
        if (size.apply(capped).fits(limit)) return capped;

        Scales base = new Scales(Math.min(capped.date, normal), Math.min(capped.weather, normal));
        if (!size.apply(base).fits(limit)) return fitBetween(limit, ZERO, base, size);
        return fitBetween(limit, base, capped, size);
    }

    private static Scales fitBetween(
            Size limit, Scales min, Scales max, Function<Scales, Size> size) {
        if (size.apply(max).fits(limit)) return max;
        if (!size.apply(min).fits(limit)) return min;
        float low = 0, high = 1;
        for (int step = 0; step < 12; step++) {
            float mid = (low + high) / 2;
            if (size.apply(between(min, max, mid)).fits(limit)) low = mid;
            else high = mid;
        }
        return between(min, max, low);
    }

    private static Scales between(Scales min, Scales max, float fraction) {
        return new Scales(min.date + (max.date - min.date) * fraction,
                min.weather + (max.weather - min.weather) * fraction);
    }

    static int centeredStart(float center, int length, int min, int max) {
        return Math.max(min, Math.min(Math.round(center - length / 2f), max - length));
    }
}
