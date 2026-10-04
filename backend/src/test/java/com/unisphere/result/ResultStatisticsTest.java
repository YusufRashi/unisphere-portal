package com.unisphere.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.result.ResultStatistics.GradeCount;
import com.unisphere.result.ResultStatistics.Graded;
import com.unisphere.result.ResultStatistics.Statistics;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultStatisticsTest {

    private static final List<String> LETTERS = List.of("A", "B", "C", "F");

    private static Graded graded(String total, String letter, boolean passed) {
        return new Graded(new BigDecimal(total), letter, passed);
    }

    @Test
    void computesCentralTendencyRangeAndPassRate() {
        Statistics s = ResultStatistics.compute(5, List.of(
                graded("72", "A", true), graded("35", "F", false), graded("58.5", "C", true), graded("64", "B", true)),
                LETTERS);

        assertThat(s.classSize()).isEqualTo(5);
        assertThat(s.graded()).isEqualTo(4);
        assertThat(s.mean()).isEqualByComparingTo("57.38"); // 229.5 / 4 = 57.375 → 57.38
        assertThat(s.median()).isEqualByComparingTo("61.25"); // (58.5 + 64) / 2
        assertThat(s.highest()).isEqualByComparingTo("72");
        assertThat(s.lowest()).isEqualByComparingTo("35");
        assertThat(s.passRate()).isEqualByComparingTo("75.0");
        assertThat(s.distribution()).containsExactly(
                new GradeCount("A", 1), new GradeCount("B", 1), new GradeCount("C", 1), new GradeCount("F", 1));
    }

    @Test
    void medianOfAnOddCountIsTheMiddleValue() {
        Statistics s = ResultStatistics.compute(3, List.of(graded("90", "A", true), graded("10", "F", false), graded("55", "C", true)),
                LETTERS);
        assertThat(s.median()).isEqualByComparingTo("55");
        assertThat(s.passRate()).isEqualByComparingTo("66.7");
    }

    @Test
    void listsEveryGradeEvenWhenNobodyGotIt() {
        Statistics s = ResultStatistics.compute(1, List.of(graded("75", "A", true)), LETTERS);
        assertThat(s.distribution()).extracting(GradeCount::count).containsExactly(1, 0, 0, 0);
    }

    @Test
    void hasNoAveragesBeforeAnyMarksAreComplete() {
        Statistics s = ResultStatistics.compute(12, List.of(), LETTERS);
        assertThat(s.graded()).isZero();
        assertThat(s.mean()).isNull();
        assertThat(s.median()).isNull();
        assertThat(s.passRate()).isNull();
        assertThat(s.distribution()).hasSize(4);
    }
}
