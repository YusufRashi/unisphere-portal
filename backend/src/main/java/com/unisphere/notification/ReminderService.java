package com.unisphere.notification;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.finance.FinanceReportService;
import com.unisphere.finance.dto.FinanceDtos.AccountRow;
import com.unisphere.notification.NotificationWriter.Message;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Scheduled reminders (NTF-4). Each reminder carries a dedupe key, so running this more often than planned, or on
 * several instances at once, never sends a student the same reminder twice.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final String ACTIVE_STUDENT_USERS = Recipients.ACTIVE_STUDENTS.replace("select s.user_id", "select s.id, s.user_id");

    private final JdbcClient jdbc;
    private final NotificationWriter writer;
    private final FinanceReportService finance;
    private final UniSphereProperties properties;
    private final Clock clock;

    public record Result(int registrationDeadline, int feeBalance) {
    }

    /** Sends every reminder that is due now. Each notification group is written in its own transaction. */
    public Result sendDueReminders() {
        return new Result(registrationDeadlines(), feeBalances());
    }

    /**
     * Students who have not submitted a registration, once per semester, when registration closes within the
     * configured lead time.
     */
    int registrationDeadlines() {
        Instant now = clock.instant();
        Duration lead = properties.notifications().registrationDeadlineLead();
        List<ClosingSemester> closing = jdbc.sql("""
                        select id, name, registration_closes_at from semesters
                        where registration_open and registration_opens_at <= :now
                          and registration_closes_at > :now and registration_closes_at <= :until""")
                .param("now", utc(now))
                .param("until", utc(now.plus(lead)))
                .query((rs, i) -> new ClosingSemester(rs.getLong("id"), rs.getString("name"),
                        rs.getObject("registration_closes_at", OffsetDateTime.class).toInstant()))
                .list();
        int sent = 0;
        for (ClosingSemester s : closing) {
            Message message = new Message(NotificationType.REGISTRATION_DEADLINE, "Registration for %s closes soon".formatted(s.name()),
                    "Registration closes on %s and you have not submitted yours yet.".formatted(Formats.dateTime(s.closesAt())),
                    "/student/registration");
            sent += writer.sendTo(Recipients.STUDENTS_NOT_REGISTERED, Map.of("semesterId", s.id()), message,
                    "REGISTRATION_DEADLINE:" + s.id());
        }
        return sent;
    }

    /**
     * Students who owe money, at most once per reminder interval. Intervals are fixed windows of time, so everyone
     * reminded in the same window shares one dedupe key.
     */
    int feeBalances() {
        List<AccountRow> owing = finance.outstanding(null);
        if (owing.isEmpty()) {
            return 0;
        }
        Map<Long, Long> userIds = new HashMap<>();
        jdbc.sql(ACTIVE_STUDENT_USERS).query(rs -> {
            userIds.put(rs.getLong("id"), rs.getLong("user_id"));
        });
        long window = clock.instant().getEpochSecond() / properties.notifications().feeReminderInterval().toSeconds();
        String key = "FEE_REMINDER:" + window;
        int sent = 0;
        for (AccountRow row : owing) {
            Long userId = userIds.get(row.studentId());
            if (userId == null) {
                continue;
            }
            Message message = new Message(NotificationType.FEE_REMINDER, "Fee balance reminder",
                    "Your fee balance is %s. You can pay online from the Fees page.".formatted(Formats.money(row.currency(), row.balance())),
                    "/student/fees");
            sent += writer.send(userId, message, key);
        }
        return sent;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ClosingSemester(Long id, String name, Instant closesAt) {
    }
}
