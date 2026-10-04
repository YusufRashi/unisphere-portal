package com.unisphere.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.result.GpaCalculator.Attempt;
import com.unisphere.result.GpaCalculator.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GpaCalculatorTest {

    private static final LocalDate S1 = LocalDate.of(2025, 9, 1);
    private static final LocalDate S2 = LocalDate.of(2026, 1, 10);
    private static final LocalDate S3 = LocalDate.of(2026, 9, 1);

    private static Attempt attempt(long resultId, long courseId, LocalDate semester, int credits, String points, boolean passed) {
        return new Attempt(resultId, courseId, semester, credits, new BigDecimal(points), passed);
    }

    @Nested
    class SemesterGpa {

        @Test
        void isTheCreditWeightedAverageOfGradePoints() {
            // (3×4.0 + 4×3.0 + 2×2.5) / 9 = 29 / 9 = 3.2222… → 3.22
            Summary s = GpaCalculator.summarize(List.of(
                    attempt(1, 10, S1, 3, "4.00", true),
                    attempt(2, 11, S1, 4, "3.00", true),
                    attempt(3, 12, S1, 2, "2.50", true)));
            assertThat(s.gpa()).isEqualByComparingTo("3.22");
            assertThat(s.creditsAttempted()).isEqualTo(9);
            assertThat(s.creditsEarned()).isEqualTo(9);
            assertThat(s.qualityPoints()).isEqualByComparingTo("29.00");
        }

        @Test
        void roundsHalfUpToTwoDecimals() {
            // (1×3.5 + 1×3.0 + 2×4.0) / 4 = 14.5 / 4 = 3.625 → 3.63 (half-up, not banker's 3.62)
            Summary s = GpaCalculator.summarize(List.of(
                    attempt(1, 10, S1, 1, "3.50", true),
                    attempt(2, 11, S1, 1, "3.00", true),
                    attempt(3, 12, S1, 2, "4.00", true)));
            assertThat(s.gpa()).isEqualByComparingTo("3.63");
            assertThat(s.gpa().scale()).isEqualTo(2);
        }

        @Test
        void failedCoursesCountTowardsAttemptedButNotEarnedCredits() {
            // (3×4.0 + 3×0.0) / 6 = 2.00
            Summary s = GpaCalculator.summarize(List.of(
                    attempt(1, 10, S1, 3, "4.00", true),
                    attempt(2, 11, S1, 3, "0.00", false)));
            assertThat(s.gpa()).isEqualByComparingTo("2.00");
            assertThat(s.creditsAttempted()).isEqualTo(6);
            assertThat(s.creditsEarned()).isEqualTo(3);
        }

        @Test
        void isNullWithoutResults() {
            Summary s = GpaCalculator.summarize(List.of());
            assertThat(s.gpa()).isNull();
            assertThat(s.creditsAttempted()).isZero();
            assertThat(s.qualityPoints()).isEqualByComparingTo("0");
        }

        @Test
        void allZeroGradePointsGiveZeroNotNull() {
            assertThat(GpaCalculator.summarize(List.of(attempt(1, 10, S1, 3, "0.00", false))).gpa()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    class Cgpa {

        @Test
        void combinesAllSemestersByCredits() {
            // S1: 3×4.0 + 3×2.0 = 18; S2: 4×3.0 = 12 → 30 / 10 = 3.00
            Summary s = GpaCalculator.cumulative(List.of(
                    attempt(1, 10, S1, 3, "4.00", true),
                    attempt(2, 11, S1, 3, "2.00", true),
                    attempt(3, 12, S2, 4, "3.00", true)));
            assertThat(s.gpa()).isEqualByComparingTo("3.00");
            assertThat(s.creditsAttempted()).isEqualTo(10);
        }

        @Test
        void weightsByCreditsNotBySemester() {
            // S1 GPA 4.00 over 2 credits, S2 GPA 2.00 over 6 credits.
            // Averaging the GPAs would give 3.00; the correct CGPA is (8 + 12) / 8 = 2.50.
            Summary s = GpaCalculator.cumulative(List.of(
                    attempt(1, 10, S1, 2, "4.00", true),
                    attempt(2, 11, S2, 6, "2.00", true)));
            assertThat(s.gpa()).isEqualByComparingTo("2.50");
        }

        @Test
        void aRepeatedCourseCountsOnlyItsLatestAttempt() {
            // Course 10 failed in S1 (0.0) and retaken in S2 (3.0). Only the S2 attempt counts:
            // (3×3.0 + 3×4.0) / 6 = 3.50. Counting both would give (0 + 9 + 12) / 9 = 2.33.
            List<Attempt> attempts = List.of(
                    attempt(1, 10, S1, 3, "0.00", false),
                    attempt(2, 11, S1, 3, "4.00", true),
                    attempt(3, 10, S2, 3, "3.00", true));
            Summary s = GpaCalculator.cumulative(attempts);
            assertThat(s.gpa()).isEqualByComparingTo("3.50");
            assertThat(s.creditsAttempted()).isEqualTo(6);
            assertThat(s.creditsEarned()).isEqualTo(6);
            assertThat(GpaCalculator.countedResultIds(attempts)).containsExactlyInAnyOrder(2L, 3L);
        }

        @Test
        void theLatestAttemptCountsEvenWhenItIsWorse() {
            // Passed with 4.0 in S1, retaken and failed in S3: the latest (0.0) attempt is the one that counts.
            List<Attempt> attempts = List.of(
                    attempt(1, 10, S1, 3, "4.00", true),
                    attempt(2, 10, S3, 3, "0.00", false));
            Summary s = GpaCalculator.cumulative(attempts);
            assertThat(s.gpa()).isEqualByComparingTo("0.00");
            assertThat(s.creditsEarned()).isZero();
            assertThat(GpaCalculator.countedResultIds(attempts)).containsExactly(2L);
        }

        @Test
        void attemptOrderIsByDateNotByInputOrder() {
            List<Attempt> attempts = List.of(
                    attempt(9, 10, S2, 3, "3.00", true),
                    attempt(4, 10, S1, 3, "1.00", true));
            assertThat(GpaCalculator.countedResultIds(attempts)).containsExactly(9L);
            assertThat(GpaCalculator.cumulative(attempts).gpa()).isEqualByComparingTo("3.00");
        }

        @Test
        void isNullForANewStudent() {
            assertThat(GpaCalculator.cumulative(List.of()).gpa()).isNull();
        }
    }
}
