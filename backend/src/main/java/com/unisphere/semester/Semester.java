package com.unisphere.semester;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An academic term with its registration window. Dates are interpreted in UTC (see requirements §6).
 * Registration is possible only when an administrator has opened it <em>and</em> "now" is inside the window,
 * so a forgotten "open" flag can never let students register outside the published dates.
 */
@Getter
@Entity
@Table(name = "semesters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Semester extends AuditableEntity {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("(\\d{4})/(\\d{4})");

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(nullable = false)
    private int term;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "registration_opens_at", nullable = false)
    private Instant registrationOpensAt;

    @Column(name = "registration_closes_at", nullable = false)
    private Instant registrationClosesAt;

    @Column(name = "add_drop_deadline", nullable = false)
    private Instant addDropDeadline;

    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "min_credits", nullable = false)
    private int minCredits;

    @Column(name = "max_credits", nullable = false)
    private int maxCredits;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    /** All schedule-related fields, validated together because the rules relate them to each other. */
    public record Schedule(
            String code, String name, String academicYear, int term,
            LocalDate startDate, LocalDate endDate,
            Instant registrationOpensAt, Instant registrationClosesAt, Instant addDropDeadline,
            boolean requiresApproval, int minCredits, int maxCredits) {

        /** @return field errors in the API's {@code {field, message}} shape; empty when valid */
        public List<Map<String, String>> validate() {
            List<Map<String, String>> errors = new ArrayList<>();
            Matcher year = ACADEMIC_YEAR.matcher(academicYear == null ? "" : academicYear);
            if (!year.matches() || Integer.parseInt(year.group(2)) != Integer.parseInt(year.group(1)) + 1) {
                errors.add(error("academicYear", "must look like 2026/2027 (consecutive years)"));
            }
            if (!endDate.isAfter(startDate)) {
                errors.add(error("endDate", "must be after the start date"));
            }
            if (!registrationClosesAt.isAfter(registrationOpensAt)) {
                errors.add(error("registrationClosesAt", "must be after registration opens"));
            }
            Instant endOfSemester = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (!registrationOpensAt.isBefore(endOfSemester)) {
                errors.add(error("registrationOpensAt", "must be before the semester ends"));
            }
            if (addDropDeadline.isBefore(registrationOpensAt) || addDropDeadline.isAfter(endOfSemester)) {
                errors.add(error("addDropDeadline", "must be between registration opening and the end of the semester"));
            }
            if (maxCredits < minCredits) {
                errors.add(error("maxCredits", "must be greater than or equal to the minimum credits"));
            }
            return errors;
        }

        private static Map<String, String> error(String field, String message) {
            return Map.of("field", field, "message", message);
        }
    }

    public Semester(Schedule schedule) {
        apply(schedule);
    }

    public void apply(Schedule s) {
        this.code = s.code().trim().toUpperCase(Locale.ROOT);
        this.name = s.name().trim();
        this.academicYear = s.academicYear().trim();
        this.term = s.term();
        this.startDate = s.startDate();
        this.endDate = s.endDate();
        this.registrationOpensAt = s.registrationOpensAt();
        this.registrationClosesAt = s.registrationClosesAt();
        this.addDropDeadline = s.addDropDeadline();
        this.requiresApproval = s.requiresApproval();
        this.minCredits = s.minCredits();
        this.maxCredits = s.maxCredits();
    }

    public SemesterStatus status(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (today.isAfter(endDate)) {
            return SemesterStatus.COMPLETED;
        }
        if (isRegistrationOpen(now)) {
            return SemesterStatus.REGISTRATION_OPEN;
        }
        return today.isBefore(startDate) ? SemesterStatus.UPCOMING : SemesterStatus.IN_PROGRESS;
    }

    /** True when students may register: opened by an administrator and inside the published window. */
    public boolean isRegistrationOpen(Instant now) {
        return registrationOpen && !now.isBefore(registrationOpensAt) && now.isBefore(registrationClosesAt);
    }

    public boolean isCompleted(Instant now) {
        return status(now) == SemesterStatus.COMPLETED;
    }

    public void openRegistration() {
        this.registrationOpen = true;
    }

    public void closeRegistration() {
        this.registrationOpen = false;
    }

    void markCurrent(boolean current) {
        this.current = current;
    }
}
