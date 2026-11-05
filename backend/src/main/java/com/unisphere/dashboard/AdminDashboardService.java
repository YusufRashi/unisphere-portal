package com.unisphere.dashboard;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.dashboard.dto.DashboardDtos.AdminDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.DayCount;
import com.unisphere.dashboard.dto.DashboardDtos.OutstandingFees;
import com.unisphere.dashboard.dto.DashboardDtos.RecentResult;
import com.unisphere.dashboard.dto.DashboardDtos.RegistrationCounts;
import com.unisphere.finance.FinanceReportService;
import com.unisphere.finance.dto.FinanceDtos.BalanceTotals;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** System-wide figures for the registrar (ADM-1). Read-only aggregate queries. */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int ACTIVITY_DAYS = 30;
    private static final int RECENT_RESULTS = 5;

    private final JdbcClient jdbc;
    private final SemesterRepository semesters;
    private final FinanceReportService finance;
    private final UniSphereProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboard get() {
        Optional<Semester> current = semesters.findByCurrentTrue();
        Optional<Long> semesterId = current.map(Semester::getId);
        BalanceTotals balances = finance.balanceTotals();
        return new AdminDashboard(
                current.map(SemesterRef::from).orElse(null),
                count("select count(*) from students"),
                count("select count(*) from students s join users u on u.id = s.user_id where s.status = 'ACTIVE' and u.status <> 'DEACTIVATED'"),
                count("select count(*) from lecturers l join users u on u.id = l.user_id where u.status <> 'DEACTIVATED'"),
                count("select count(*) from courses where active"),
                semesterId.map(this::registrationCounts).orElse(new RegistrationCounts(0, 0, 0, 0)),
                semesterId.map(this::resultsAwaitingReview).orElse(0L),
                new OutstandingFees(properties.finance().currency(), balances.outstanding(), balances.studentsOwing()),
                registrationActivity(semesterId.orElse(null)),
                recentResults());
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private RegistrationCounts registrationCounts(Long semesterId) {
        Map<String, Long> byStatus = new HashMap<>();
        jdbc.sql("select status, count(*) as n from registrations where semester_id = :semesterId group by status")
                .param("semesterId", semesterId)
                .query(rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("n"));
                });
        return new RegistrationCounts(byStatus.getOrDefault("APPROVED", 0L), byStatus.getOrDefault("SUBMITTED", 0L),
                byStatus.getOrDefault("DRAFT", 0L), byStatus.getOrDefault("REJECTED", 0L));
    }

    private long resultsAwaitingReview(Long semesterId) {
        return jdbc.sql("select count(*) from course_offerings where semester_id = :semesterId and results_status = 'SUBMITTED'")
                .param("semesterId", semesterId)
                .query(Long.class).single();
    }

    private List<DayCount> registrationActivity(Long semesterId) {
        LocalDate first = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(ACTIVITY_DAYS - 1L);
        Map<LocalDate, Long> perDay = new HashMap<>();
        if (semesterId != null) {
            jdbc.sql("""
                            select cast(submitted_at at time zone 'UTC' as date) as day, count(*) as n
                            from registrations
                            where semester_id = :semesterId and submitted_at >= :from
                            group by 1""")
                    .param("semesterId", semesterId)
                    .param("from", OffsetDateTime.of(first.atStartOfDay(), ZoneOffset.UTC))
                    .query(rs -> {
                        perDay.put(rs.getObject("day", LocalDate.class), rs.getLong("n"));
                    });
        }
        return TimeSeries.filled(first, ACTIVITY_DAYS, 1, perDay, 0L, DayCount::new);
    }

    private List<RecentResult> recentResults() {
        return jdbc.sql("""
                        select o.id, c.code, c.title, o.section, s.name as semester_name, o.results_finalized_at,
                               count(r.id) as students, count(r.id) filter (where r.passed) as passed
                        from course_offerings o
                        join courses c on c.id = o.course_id
                        join semesters s on s.id = o.semester_id
                        left join registration_items i on i.offering_id = o.id
                        left join results r on r.registration_item_id = i.id and r.finalized_at is not null
                        where o.results_finalized_at is not null
                        group by o.id, c.code, c.title, o.section, s.name, o.results_finalized_at
                        order by o.results_finalized_at desc, o.id desc
                        limit :limit""")
                .param("limit", RECENT_RESULTS)
                .query((rs, i) -> new RecentResult(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getString("section"),
                        rs.getString("semester_name"), rs.getObject("results_finalized_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("students"), rs.getLong("passed")))
                .list();
    }
}
