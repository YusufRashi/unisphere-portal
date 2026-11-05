package com.unisphere.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.dashboard.dto.DashboardDtos.DayCount;
import com.unisphere.finance.dto.FinanceDtos.BalanceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeSeriesTest {

    @Test
    void fillsQuietPeriodsWithZero() {
        LocalDate first = LocalDate.of(2026, 10, 30);
        List<DayCount> series = TimeSeries.filled(first, 4, 1, Map.of(first.plusDays(2), 5L), 0L, DayCount::new);
        assertThat(series).containsExactly(
                new DayCount(LocalDate.of(2026, 10, 30), 0),
                new DayCount(LocalDate.of(2026, 10, 31), 0),
                new DayCount(LocalDate.of(2026, 11, 1), 5),
                new DayCount(LocalDate.of(2026, 11, 2), 0));
    }

    @Test
    void stepsByWeek() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        List<DayCount> series = TimeSeries.filled(monday, 2, 7, Map.of(), 0L, DayCount::new);
        assertThat(series).extracting(DayCount::date).containsExactly(monday, LocalDate.of(2026, 11, 9));
    }

    @Test
    void collectionRateIsAPercentageOfWhatWasCharged() {
        assertThat(FinanceDashboardService.collectionRate(totals("0.00", "0.00"))).isNull();
        assertThat(FinanceDashboardService.collectionRate(totals("3000.00", "1000.00"))).isEqualByComparingTo("33.3");
    }

    private static BalanceTotals totals(String charged, String paid) {
        return new BalanceTotals(new BigDecimal(charged), new BigDecimal(paid), BigDecimal.ZERO, 0);
    }
}
