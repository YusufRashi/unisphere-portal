package com.unisphere.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** SQL only returns periods that have data; a chart needs the quiet ones too, or the time axis would lie. */
final class TimeSeries {

    private TimeSeries() {
    }

    /**
     * @param first  start of the first period
     * @param points how many periods
     * @param values what the query found, keyed by the start of the period
     * @param zero   the value of a period without data
     */
    static <V, P> List<P> filled(LocalDate first, int points, int stepDays, Map<LocalDate, V> values, V zero, BiFunction<LocalDate, V, P> point) {
        List<P> series = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            LocalDate date = first.plusDays((long) i * stepDays);
            series.add(point.apply(date, values.getOrDefault(date, zero)));
        }
        return series;
    }
}
