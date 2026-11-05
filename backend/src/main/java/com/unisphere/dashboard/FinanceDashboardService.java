package com.unisphere.dashboard;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.dashboard.dto.DashboardDtos.FinanceDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.MethodAmount;
import com.unisphere.dashboard.dto.DashboardDtos.WeekAmount;
import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.FinanceReportService;
import com.unisphere.finance.dto.FinanceDtos.BalanceTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collections at a glance. Only completed payments count: declined and reversed ones brought in no money. */
@Service
@RequiredArgsConstructor
public class FinanceDashboardService {

    private static final int WEEKS = 12;
    private static final int RECENT_DAYS = 30;
    private static final int LARGEST_BALANCES = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;
    private final FinanceReportService reports;
    private final UniSphereProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinanceDashboard get() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        BalanceTotals totals = reports.balanceTotals();
        List<MethodAmount> methods = methodsSince(today.minusDays(RECENT_DAYS - 1L));
        BigDecimal recent = methods.stream().map(MethodAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new FinanceDashboard(properties.finance().currency(), totals.charged(), totals.paid(), totals.outstanding(),
                totals.studentsOwing(), collectionRate(totals), recent, weeklyCollections(today), methods,
                reports.largestBalances(LARGEST_BALANCES));
    }

    static BigDecimal collectionRate(BalanceTotals totals) {
        if (totals.charged().signum() == 0) {
            return null;
        }
        return totals.paid().multiply(HUNDRED).divide(totals.charged(), 1, RoundingMode.HALF_UP);
    }

    private List<WeekAmount> weeklyCollections(LocalDate today) {
        LocalDate first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L);
        Map<LocalDate, BigDecimal> perWeek = new HashMap<>();
        // date_trunc('week') starts weeks on Monday.
        jdbc.sql("""
                        select cast(date_trunc('week', paid_at at time zone 'UTC') as date) as week, sum(amount) as total
                        from payments
                        where status = 'COMPLETED' and paid_at >= :from
                        group by 1""")
                .param("from", startOf(first))
                .query(rs -> {
                    perWeek.put(rs.getObject("week", LocalDate.class), rs.getBigDecimal("total").setScale(2));
                });
        return TimeSeries.filled(first, WEEKS, 7, perWeek, BigDecimal.ZERO.setScale(2), WeekAmount::new);
    }

    private List<MethodAmount> methodsSince(LocalDate from) {
        return jdbc.sql("""
                        select method, sum(amount) as total, count(*) as n
                        from payments
                        where status = 'COMPLETED' and paid_at >= :from
                        group by method
                        order by total desc, method""")
                .param("from", startOf(from))
                .query((rs, i) -> new MethodAmount(PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("total").setScale(2), rs.getLong("n")))
                .list();
    }

    private static OffsetDateTime startOf(LocalDate date) {
        return OffsetDateTime.of(date.atStartOfDay(), ZoneOffset.UTC);
    }
}
