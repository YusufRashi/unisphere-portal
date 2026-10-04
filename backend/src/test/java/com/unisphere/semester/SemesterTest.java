package com.unisphere.semester;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SemesterTest {

    private static final LocalDate START = LocalDate.parse("2026-09-07");
    private static final LocalDate END = LocalDate.parse("2026-12-18");
    private static final Instant OPENS = Instant.parse("2026-08-24T06:00:00Z");
    private static final Instant CLOSES = Instant.parse("2026-09-04T21:00:00Z");
    private static final Instant ADD_DROP = Instant.parse("2026-09-18T21:00:00Z");

    private static Semester.Schedule schedule() {
        return new Semester.Schedule("2026-S1", "Semester 1, 2026/2027", "2026/2027", 1, START, END, OPENS, CLOSES,
                ADD_DROP, false, 12, 24);
    }

    private static List<String> invalidFields(Semester.Schedule s) {
        return s.validate().stream().map(e -> e.get("field")).toList();
    }

    @Nested
    class Validation {

        @Test
        void acceptsConsistentSchedule() {
            assertThat(schedule().validate()).isEmpty();
        }

        @Test
        void rejectsNonConsecutiveAcademicYear() {
            var s = schedule();
            assertThat(invalidFields(new Semester.Schedule(s.code(), s.name(), "2026/2028", 1, START, END, OPENS, CLOSES,
                    ADD_DROP, false, 12, 24))).containsExactly("academicYear");
        }

        @Test
        void rejectsEndBeforeStart() {
            assertThat(invalidFields(new Semester.Schedule("X", "X", "2026/2027", 1, END, START, OPENS, CLOSES,
                    ADD_DROP, false, 12, 24))).contains("endDate");
        }

        @Test
        void rejectsRegistrationClosingBeforeItOpens() {
            assertThat(invalidFields(new Semester.Schedule("X", "X", "2026/2027", 1, START, END, CLOSES, OPENS,
                    ADD_DROP, false, 12, 24))).contains("registrationClosesAt");
        }

        @Test
        void rejectsAddDropDeadlineAfterSemesterEnds() {
            assertThat(invalidFields(new Semester.Schedule("X", "X", "2026/2027", 1, START, END, OPENS, CLOSES,
                    Instant.parse("2027-01-10T00:00:00Z"), false, 12, 24))).containsExactly("addDropDeadline");
        }

        @Test
        void rejectsMaxCreditsBelowMin() {
            assertThat(invalidFields(new Semester.Schedule("X", "X", "2026/2027", 1, START, END, OPENS, CLOSES,
                    ADD_DROP, false, 24, 12))).containsExactly("maxCredits");
        }

        @Test
        void errorsUseTheApiFieldErrorShape() {
            Map<String, String> error = new Semester.Schedule("X", "X", "bad", 1, START, END, OPENS, CLOSES,
                    ADD_DROP, false, 12, 24).validate().getFirst();
            assertThat(error).containsOnlyKeys("field", "message");
        }
    }

    @Nested
    class Status {

        private final Semester semester = new Semester(schedule());

        @Test
        void upcomingBeforeStartWhenRegistrationNotOpened() {
            assertThat(semester.status(Instant.parse("2026-08-30T10:00:00Z"))).isEqualTo(SemesterStatus.UPCOMING);
        }

        @Test
        void registrationOpenRequiresBothTheFlagAndTheWindow() {
            Instant insideWindow = Instant.parse("2026-08-30T10:00:00Z");
            assertThat(semester.isRegistrationOpen(insideWindow)).isFalse();

            semester.openRegistration();
            assertThat(semester.isRegistrationOpen(insideWindow)).isTrue();
            assertThat(semester.status(insideWindow)).isEqualTo(SemesterStatus.REGISTRATION_OPEN);
            assertThat(semester.isRegistrationOpen(OPENS.minusSeconds(1))).isFalse();
            assertThat(semester.isRegistrationOpen(CLOSES)).isFalse();
        }

        @Test
        void inProgressDuringTeachingWeeks() {
            assertThat(semester.status(Instant.parse("2026-10-15T10:00:00Z"))).isEqualTo(SemesterStatus.IN_PROGRESS);
        }

        @Test
        void completedAfterEndDate() {
            assertThat(semester.status(Instant.parse("2026-12-19T00:00:00Z"))).isEqualTo(SemesterStatus.COMPLETED);
            assertThat(semester.status(Instant.parse("2026-12-18T23:59:59Z"))).isEqualTo(SemesterStatus.IN_PROGRESS);
        }
    }
}
