package com.unisphere.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unisphere.result.GradingRules.Band;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradingRulesTest {

    /** The scale seeded by migration V5 (deliberately listed out of order). */
    static final List<Band> STANDARD = List.of(
            band("C", 50, "2.00", true),
            band("A", 70, "4.00", true),
            band("B+", 65, "3.50", true),
            band("B", 60, "3.00", true),
            band("C+", 55, "2.50", true),
            band("F", 0, "0.00", false),
            band("D", 40, "1.00", true));

    static Band band(String letter, double min, String points, boolean passing) {
        return new Band(letter, BigDecimal.valueOf(min), new BigDecimal(points), passing);
    }

    @Nested
    class GradeFor {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "100, A", "70, A", "69.99, B+", "65, B+", "64.5, B", "60, B", "59.99, C+", "55, C+",
                "50, C", "49.99, D", "40, D", "39.99, F", "0, F",
        })
        void usesInclusiveLowerBounds(String total, String expected) {
            assertThat(GradingRules.gradeFor(new BigDecimal(total), STANDARD).letter()).isEqualTo(expected);
        }

        @Test
        void carriesPointsAndPassFromTheBand() {
            Band d = GradingRules.gradeFor(new BigDecimal("42"), STANDARD);
            assertThat(d.gradePoints()).isEqualByComparingTo("1.00");
            assertThat(d.passing()).isTrue();
            assertThat(GradingRules.gradeFor(new BigDecimal("39"), STANDARD).passing()).isFalse();
        }

        @Test
        void rejectsTotalsOutsideZeroToHundred() {
            assertThatThrownBy(() -> GradingRules.gradeFor(new BigDecimal("100.01"), STANDARD))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> GradingRules.gradeFor(new BigDecimal("-1"), STANDARD))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Marks {

        @Test
        void totalNeedsBothComponents() {
            assertThat(GradingRules.total(new BigDecimal("25.5"), new BigDecimal("60"))).isEqualByComparingTo("85.5");
            assertThat(GradingRules.total(new BigDecimal("25"), null)).isNull();
            assertThat(GradingRules.total(null, null)).isNull();
        }

        @Test
        void aMarkMustFitTheComponentWeight() {
            BigDecimal catWeight = new BigDecimal("30.00");
            assertThat(GradingRules.markProblem(null, catWeight)).isNull();
            assertThat(GradingRules.markProblem(new BigDecimal("0"), catWeight)).isNull();
            assertThat(GradingRules.markProblem(new BigDecimal("30"), catWeight)).isNull();
            assertThat(GradingRules.markProblem(new BigDecimal("30.01"), catWeight)).isEqualTo("must be at most 30");
            assertThat(GradingRules.markProblem(new BigDecimal("-0.5"), catWeight)).isEqualTo("must not be negative");
            assertThat(GradingRules.markProblem(new BigDecimal("12.345"), catWeight)).isEqualTo("may have at most 2 decimal places");
        }
    }

    @Nested
    class ScaleValidation {

        @Test
        void acceptsTheStandardScale() {
            assertThat(GradingRules.problems(STANDARD)).isEmpty();
        }

        @Test
        void theLowestBandMustStartAtZero() {
            assertThat(GradingRules.problems(List.of(band("A", 50, "4.00", true), band("F", 10, "0.00", false))))
                    .containsExactly("The lowest band must start at 0 so every mark gets a grade (it starts at 10).");
        }

        @Test
        void rejectsDuplicateLettersAndThresholds() {
            assertThat(GradingRules.problems(List.of(
                    band("A", 70, "4.00", true), band("a", 60, "3.00", true), band("B", 60, "2.00", true), band("F", 0, "0", false))))
                    .contains("Grade a appears more than once.", "Two bands start at 60.");
        }

        @Test
        void needsAPassAndAFail() {
            assertThat(GradingRules.problems(List.of(band("A", 50, "4.00", true), band("B", 0, "3.00", true))))
                    .containsExactly("At least one band must be a fail.");
            assertThat(GradingRules.problems(List.of(band("E", 50, "0.00", false), band("F", 0, "0.00", false))))
                    .containsExactly("At least one band must be a pass.");
        }

        @Test
        void higherGradesCannotBeWorthLessOrFailWhileLowerOnesPass() {
            assertThat(GradingRules.problems(List.of(
                    band("A", 70, "3.00", false), band("B", 50, "3.50", true), band("F", 0, "0.00", false))))
                    .containsExactlyInAnyOrder(
                            "B is worth more grade points than the higher grade A.",
                            "B is a pass but the higher grade A is a fail.");
        }

        @Test
        void needsAtLeastTwoBands() {
            assertThat(GradingRules.problems(List.of(band("P", 0, "4.00", true)))).hasSize(1);
        }
    }
}
