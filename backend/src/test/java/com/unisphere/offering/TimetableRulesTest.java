package com.unisphere.offering;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.offering.TimetableRules.BookedSlot;
import com.unisphere.offering.TimetableRules.Slot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimetableRulesTest {

    private static Slot slot(DayOfWeek day, String from, String to, String venue) {
        return new Slot(day, LocalTime.parse(from), LocalTime.parse(to), venue);
    }

    @Nested
    class InternalProblems {

        @Test
        void acceptsDistinctSessions() {
            assertThat(TimetableRules.internalProblems(List.of(
                    slot(MONDAY, "08:00", "10:00", "LT1"),
                    slot(TUESDAY, "08:00", "10:00", "LT1")))).isEmpty();
        }

        @Test
        void backToBackSessionsDoNotOverlap() {
            assertThat(TimetableRules.internalProblems(List.of(
                    slot(MONDAY, "08:00", "10:00", "LT1"),
                    slot(MONDAY, "10:00", "12:00", "LT1")))).isEmpty();
        }

        @Test
        void reportsOverlappingSessions() {
            assertThat(TimetableRules.internalProblems(List.of(
                    slot(MONDAY, "08:00", "10:00", "LT1"),
                    slot(MONDAY, "09:00", "11:00", "LAB2"))))
                    .containsExactly("Sessions 1 and 2 overlap (Mon 08:00–10:00).");
        }

        @Test
        void reportsSessionThatEndsBeforeItStarts() {
            assertThat(TimetableRules.internalProblems(List.of(slot(MONDAY, "10:00", "09:00", "LT1"))))
                    .containsExactly("Session 1 must end after it starts.");
        }
    }

    @Nested
    class Conflicts {

        private final List<BookedSlot> booked = List.of(
                new BookedSlot(slot(MONDAY, "08:00", "10:00", "LT1"), "CS201 (A)", 7L));

        @Test
        void detectsVenueClashIgnoringCaseAndWhitespace() {
            assertThat(TimetableRules.findConflict(List.of(slot(MONDAY, "09:00", "11:00", " lt1 ")), 99L, booked))
                    .contains("lt1 is already booked by CS201 (A) on Mon 08:00–10:00.");
        }

        @Test
        void detectsLecturerTeachingTwoPlacesAtOnce() {
            assertThat(TimetableRules.findConflict(List.of(slot(MONDAY, "09:30", "10:30", "LAB2")), 7L, booked))
                    .contains("The lecturer already teaches CS201 (A) on Mon 08:00–10:00.");
        }

        @Test
        void allowsSameTimeInAnotherVenueWithAnotherLecturer() {
            assertThat(TimetableRules.findConflict(List.of(slot(MONDAY, "08:00", "10:00", "LAB2")), 99L, booked)).isEmpty();
        }

        @Test
        void allowsUnassignedLecturerInAnotherVenue() {
            assertThat(TimetableRules.findConflict(List.of(slot(MONDAY, "08:00", "10:00", "LAB2")), null, booked)).isEmpty();
        }

        @Test
        void allowsSameVenueRightAfterAnotherClass() {
            assertThat(TimetableRules.findConflict(List.of(slot(MONDAY, "10:00", "12:00", "LT1")), 7L, booked)).isEmpty();
        }

        @Test
        void allowsSameVenueOnAnotherDay() {
            assertThat(TimetableRules.findConflict(List.of(slot(TUESDAY, "08:00", "10:00", "LT1")), 7L, booked)).isEmpty();
        }
    }
}
