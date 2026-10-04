package com.unisphere.result;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Marks and grading rules (RES-2, RES-3) as pure functions. A grade band covers marks from its
 * {@code minMark} up to, but not including, the next higher band's {@code minMark}; the top band runs to 100.
 * Defining bands by their lower bound alone makes gaps and overlaps impossible by construction.
 */
public final class GradingRules {

    public static final BigDecimal MAX_TOTAL = BigDecimal.valueOf(100);

    private GradingRules() {
    }

    /** One band of a grading scale, as the rules see it. */
    public record Band(String letter, BigDecimal minMark, BigDecimal gradePoints, boolean passing) {
    }

    /** Highest band first — the order in which {@link #gradeFor} searches and in which scales are shown. */
    public static List<Band> highestFirst(List<Band> bands) {
        return bands.stream().sorted(Comparator.comparing(Band::minMark).reversed()).toList();
    }

    /** The band a total mark falls into. The scale must be valid (see {@link #problems}). */
    public static Band gradeFor(BigDecimal total, List<Band> bands) {
        if (total.signum() < 0 || total.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException("Total mark out of range: " + total);
        }
        return highestFirst(bands).stream()
                .filter(b -> total.compareTo(b.minMark()) >= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Grading scale does not start at 0"));
    }

    /** Total out of 100, or null until both components are known. */
    public static BigDecimal total(BigDecimal catMark, BigDecimal examMark) {
        return catMark == null || examMark == null ? null : catMark.add(examMark);
    }

    /** Why a mark is not acceptable for a component worth {@code weight} marks, or null if it is. */
    public static String markProblem(BigDecimal mark, BigDecimal weight) {
        if (mark == null) {
            return null;
        }
        if (mark.signum() < 0) {
            return "must not be negative";
        }
        if (mark.compareTo(weight) > 0) {
            return "must be at most " + weight.stripTrailingZeros().toPlainString();
        }
        if (mark.stripTrailingZeros().scale() > 2) {
            return "may have at most 2 decimal places";
        }
        return null;
    }

    /** Everything wrong with a proposed scale, in user-facing words; empty when the scale is usable. */
    public static List<String> problems(List<Band> bands) {
        List<String> problems = new ArrayList<>();
        if (bands.size() < 2) {
            problems.add("A grading scale needs at least two bands (one passing and one failing).");
            return problems;
        }
        Set<String> letters = new HashSet<>();
        Set<BigDecimal> mins = new HashSet<>();
        for (Band b : bands) {
            if (!letters.add(b.letter().toUpperCase(Locale.ROOT))) {
                problems.add("Grade %s appears more than once.".formatted(b.letter()));
            }
            if (!mins.add(b.minMark().stripTrailingZeros())) {
                problems.add("Two bands start at %s.".formatted(plain(b.minMark())));
            }
        }
        List<Band> ordered = highestFirst(bands);
        Band lowest = ordered.getLast();
        if (lowest.minMark().signum() != 0) {
            problems.add("The lowest band must start at 0 so every mark gets a grade (it starts at %s)."
                    .formatted(plain(lowest.minMark())));
        }
        if (ordered.stream().noneMatch(Band::passing)) {
            problems.add("At least one band must be a pass.");
        }
        if (ordered.stream().allMatch(Band::passing)) {
            problems.add("At least one band must be a fail.");
        }
        for (int i = 1; i < ordered.size(); i++) {
            Band higher = ordered.get(i - 1);
            Band lower = ordered.get(i);
            if (lower.gradePoints().compareTo(higher.gradePoints()) > 0) {
                problems.add("%s is worth more grade points than the higher grade %s.".formatted(lower.letter(), higher.letter()));
            }
            if (lower.passing() && !higher.passing()) {
                problems.add("%s is a pass but the higher grade %s is a fail.".formatted(lower.letter(), higher.letter()));
            }
        }
        return problems;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
