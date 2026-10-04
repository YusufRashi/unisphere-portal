package com.unisphere.result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Class statistics for an offering (RES-10): mean, median, range, pass rate and grade distribution. */
public final class ResultStatistics {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ResultStatistics() {
    }

    /** A student's total and the grade it maps to. */
    public record Graded(BigDecimal total, String letter, boolean passed) {
    }

    public record GradeCount(String letter, int count) {
    }

    /**
     * Averages are null while nobody has a complete mark.
     *
     * @param classSize registered students
     * @param graded    students with both marks entered
     * @param passRate  percentage of graded students who passed, 1 decimal place
     */
    public record Statistics(int classSize, int graded, BigDecimal mean, BigDecimal median, BigDecimal highest,
                             BigDecimal lowest, BigDecimal passRate, List<GradeCount> distribution) {
    }

    /** {@code letters} fixes the order of the distribution (highest grade first) and includes empty grades. */
    public static Statistics compute(int classSize, List<Graded> graded, List<String> letters) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        letters.forEach(l -> counts.put(l, 0));
        graded.forEach(g -> counts.merge(g.letter(), 1, Integer::sum));
        List<GradeCount> distribution = counts.entrySet().stream().map(e -> new GradeCount(e.getKey(), e.getValue())).toList();
        if (graded.isEmpty()) {
            return new Statistics(classSize, 0, null, null, null, null, null, distribution);
        }

        List<BigDecimal> totals = graded.stream().map(Graded::total).sorted().toList();
        int n = totals.size();
        BigDecimal sum = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal median = n % 2 == 1
                ? totals.get(n / 2).setScale(2, RoundingMode.HALF_UP)
                : totals.get(n / 2 - 1).add(totals.get(n / 2)).divide(TWO, 2, RoundingMode.HALF_UP);
        long passed = graded.stream().filter(Graded::passed).count();
        BigDecimal passRate = BigDecimal.valueOf(passed).multiply(HUNDRED).divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
        return new Statistics(classSize, n, mean, median, totals.getLast(), totals.getFirst(), passRate, distribution);
    }
}
