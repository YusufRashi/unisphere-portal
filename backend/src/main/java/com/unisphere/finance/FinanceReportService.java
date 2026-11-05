package com.unisphere.finance;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
import com.unisphere.finance.dto.FinanceDtos.AccountRow;
import com.unisphere.finance.dto.FinanceDtos.BalanceTotals;
import com.unisphere.finance.dto.FinanceDtos.PaymentReport;
import com.unisphere.finance.dto.FinanceDtos.PaymentReportRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balances across students and payment reports (FIN-5). Balances are computed in SQL with the same rule as
 * {@link Ledger}: Σ active charges − Σ completed payments. Students without an account show a zero balance.
 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    /** API sort field → SQL expression. Only these strings ever reach the ORDER BY clause. */
    public static final Map<String, String> ACCOUNT_SORTS = Map.of(
            "balance", "x.balance",
            "studentNumber", "x.student_number",
            "name", "x.name");

    private static final String BALANCES = """
            select s.id as student_id, s.student_number, u.first_name || ' ' || u.last_name as name, u.email,
                   s.program_id, p.code as program_code,
                   coalesce(l.charged, 0) as charged, coalesce(l.paid, 0) as paid,
                   coalesce(l.charged, 0) - coalesce(l.paid, 0) as balance
            from students s
            join users u on u.id = s.user_id
            join programs p on p.id = s.program_id
            left join (
                select a.student_id,
                       (select sum(c.amount) from fee_charges c where c.account_id = a.id and c.status = 'ACTIVE') as charged,
                       (select sum(pm.amount) from payments pm where pm.account_id = a.id and pm.status = 'COMPLETED') as paid
                from fee_accounts a
            ) l on l.student_id = s.id""";

    private static final int MAX_REPORT_DAYS = 366;

    private final JdbcClient jdbc;
    private final PaymentRepository payments;
    private final UniSphereProperties properties;

    @Transactional(readOnly = true)
    public PageResponse<AccountRow> search(String q, Long programId, Boolean hasBalance, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String where = where(q, programId, hasBalance, params);
        long total = jdbc.sql("select count(*) from (" + BALANCES + ") x" + where).params(params).query(Long.class).single();
        params.put("limit", pageable.getPageSize());
        params.put("offset", pageable.getOffset());
        List<AccountRow> rows = jdbc.sql("select * from (" + BALANCES + ") x" + where + orderBy(pageable.getSort())
                        + " limit :limit offset :offset")
                .params(params).query(this::row).list();
        int pages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, pages);
    }

    /** Every student who owes money, largest balance first. */
    @Transactional(readOnly = true)
    public List<AccountRow> outstanding(Long programId) {
        Map<String, Object> params = new HashMap<>();
        String where = where(null, programId, true, params);
        return jdbc.sql("select * from (" + BALANCES + ") x" + where + " order by x.balance desc, x.student_number")
                .params(params).query(this::row).list();
    }

    /** The students who owe the most, largest balance first. */
    @Transactional(readOnly = true)
    public List<AccountRow> largestBalances(int limit) {
        return jdbc.sql("select * from (" + BALANCES + ") x where x.balance > 0 order by x.balance desc, x.student_number limit :limit")
                .param("limit", limit).query(this::row).list();
    }

    /** Totals across all students. A student in credit does not reduce what the others owe. */
    @Transactional(readOnly = true)
    public BalanceTotals balanceTotals() {
        return jdbc.sql("""
                        select coalesce(sum(x.charged), 0) as charged, coalesce(sum(x.paid), 0) as paid,
                               coalesce(sum(x.balance) filter (where x.balance > 0), 0) as outstanding,
                               count(*) filter (where x.balance > 0) as owing
                        from (""" + BALANCES + ") x")
                .query((rs, i) -> new BalanceTotals(rs.getBigDecimal("charged").setScale(2), rs.getBigDecimal("paid").setScale(2),
                        rs.getBigDecimal("outstanding").setScale(2), rs.getLong("owing")))
                .single();
    }

    /** Payments with a payment date in [from, to] (whole days, UTC), all statuses, oldest first. */
    @Transactional(readOnly = true)
    public PaymentReport payments(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("to", "must be on or after the start date");
        }
        if (from.plusDays(MAX_REPORT_DAYS).isBefore(to)) {
            throw ApiException.validation("to", "the report can cover at most " + MAX_REPORT_DAYS + " days");
        }
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<PaymentReportRow> rows = payments.findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(start, end).stream()
                .map(p -> new PaymentReportRow(p.getId(), p.getPaidAt(), p.getReference(), p.getAccount().getStudent().getStudentNumber(),
                        p.getAccount().getStudent().getUser().getFullName(), p.getMethod(), p.getChannel(), p.getStatus(), p.getAmount()))
                .toList();
        BigDecimal completed = rows.stream().filter(r -> r.status() == PaymentStatus.COMPLETED)
                .map(PaymentReportRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new PaymentReport(properties.finance().currency(), start, end, completed, rows);
    }

    private static String where(String q, Long programId, Boolean hasBalance, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        if (programId != null) {
            clauses.add("x.program_id = :programId");
            params.put("programId", programId);
        }
        String pattern = Specs.containsPattern(q);
        if (pattern != null) {
            clauses.add("(lower(x.student_number) like :pattern escape '\\' or lower(x.email) like :pattern escape '\\' "
                    + "or lower(x.name) like :pattern escape '\\')");
            params.put("pattern", pattern);
        }
        if (hasBalance != null) {
            clauses.add(hasBalance ? "x.balance > 0" : "x.balance <= 0");
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private static String orderBy(Sort sort) {
        String order = sort.stream()
                .map(o -> {
                    // PageableFactory adds "id" as a tie-breaker so pages stay stable.
                    String column = o.getProperty().equals("id") ? "x.student_id" : o.getProperty();
                    if (!column.equals("x.student_id") && !ACCOUNT_SORTS.containsValue(column)) {
                        throw new IllegalArgumentException("Unexpected sort column " + column);
                    }
                    return column + (o.isAscending() ? " asc" : " desc");
                })
                .collect(Collectors.joining(", "));
        return order.isEmpty() ? " order by x.student_number" : " order by " + order;
    }

    private AccountRow row(ResultSet rs, int i) throws SQLException {
        return new AccountRow(rs.getLong("student_id"), rs.getString("student_number"), rs.getString("name"), rs.getString("email"),
                rs.getString("program_code"), properties.finance().currency(), rs.getBigDecimal("charged").setScale(2),
                rs.getBigDecimal("paid").setScale(2), rs.getBigDecimal("balance").setScale(2));
    }
}
