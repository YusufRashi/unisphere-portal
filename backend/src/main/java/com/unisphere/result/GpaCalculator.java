package com.unisphere.result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single place where GPA is computed (RES-7, RES-8, RES-9).
 *
 * <ul>
 *   <li>GPA = Σ(credits × grade points) / Σ(credits), rounded half-up to 2 decimal places.</li>
 *   <li>Semester GPA uses every finalized result of that semester.</li>
 *   <li>CGPA uses every semester, but a repeated course counts only once: its latest attempt.</li>
 * </ul>
 * Arithmetic is exact ({@link BigDecimal}) until the final rounding, so no floating-point drift.
 */
public final class GpaCalculator {

    private static final int SCALE = 2;

    private GpaCalculator() {
    }

    /**
     * One finalized result. {@code semesterStart} orders attempts in time; {@code resultId} breaks ties
     * (two attempts in one semester cannot happen through registration, but the order stays deterministic).
     */
    public record Attempt(long resultId, long courseId, LocalDate semesterStart, int credits, BigDecimal gradePoints,
                          boolean passed) {
    }

    /**
     * @param gpa              null when there are no credits to average
     * @param creditsAttempted credits of the attempts that were counted
     * @param creditsEarned    credits of the counted attempts that were passed
     * @param qualityPoints    Σ(credits × grade points)
     */
    public record Summary(BigDecimal gpa, int creditsAttempted, int creditsEarned, BigDecimal qualityPoints) {
    }

    /** GPA over exactly the given attempts (one semester's results). */
    public static Summary summarize(Collection<Attempt> attempts) {
        int attempted = 0;
        int earned = 0;
        BigDecimal quality = BigDecimal.ZERO;
        for (Attempt a : attempts) {
            attempted += a.credits();
            earned += a.passed() ? a.credits() : 0;
            quality = quality.add(a.gradePoints().multiply(BigDecimal.valueOf(a.credits())));
        }
        BigDecimal gpa = attempted == 0 ? null
                : quality.divide(BigDecimal.valueOf(attempted), SCALE, RoundingMode.HALF_UP);
        return new Summary(gpa, attempted, earned, quality.setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** CGPA over a student's whole history, counting only the latest attempt of each course. */
    public static Summary cumulative(Collection<Attempt> attempts) {
        return summarize(latestAttempts(attempts).values());
    }

    /** Ids of the results that count towards the CGPA (the latest attempt of each course). */
    public static Set<Long> countedResultIds(Collection<Attempt> attempts) {
        return latestAttempts(attempts).values().stream().map(Attempt::resultId).collect(Collectors.toSet());
    }

    private static Map<Long, Attempt> latestAttempts(Collection<Attempt> attempts) {
        Comparator<Attempt> chronological = Comparator.comparing(Attempt::semesterStart).thenComparingLong(Attempt::resultId);
        return attempts.stream().collect(Collectors.toMap(Attempt::courseId, Function.identity(),
                (a, b) -> chronological.compare(a, b) >= 0 ? a : b));
    }
}
