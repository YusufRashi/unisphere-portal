package com.unisphere.registration;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.common.exception.ErrorCode;
import com.unisphere.offering.TimetableRules.Slot;
import com.unisphere.registration.RegistrationRules.OfferingFacts;
import com.unisphere.registration.RegistrationRules.Violation;
import com.unisphere.registration.RegistrationRules.Window;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegistrationRulesTest {

    private static final long CS101 = 1, CS201 = 2, MA101 = 3;

    private static OfferingFacts offering(long offeringId, long courseId, String code, String section, int credits,
                                          int seatsLeft, Map<Long, String> prerequisites, Slot... slots) {
        return new OfferingFacts(offeringId, courseId, code, section, credits, seatsLeft, prerequisites, List.of(slots));
    }

    private static Slot monday(String from, String to) {
        return new Slot(MONDAY, LocalTime.parse(from), LocalTime.parse(to), "LT-1");
    }

    private static ErrorCode code(Optional<Violation> v) {
        return v.map(Violation::code).orElse(null);
    }

    private final OfferingFacts cs101A = offering(10, CS101, "CS101", "A", 3, 5, Map.of(), monday("08:00", "10:00"));
    private final OfferingFacts cs101B = offering(11, CS101, "CS101", "B", 3, 5, Map.of(), new Slot(TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), "LT-2"));
    private final OfferingFacts cs201 = offering(20, CS201, "CS201", "A", 4, 5, Map.of(CS101, "CS101"), monday("10:00", "12:00"));
    private final OfferingFacts ma101 = offering(30, MA101, "MA101", "A", 3, 0, Map.of(), monday("09:00", "11:00"));

    @Nested
    class Windows {

        private final Instant opens = Instant.parse("2026-09-01T06:00:00Z");
        private final Instant closes = opens.plus(Duration.ofDays(14));
        private final Window window = new Window(true, opens, closes, closes.plus(Duration.ofDays(7)), "Semester 1");

        @Test
        void registrationNeedsTheAdminFlagAndTheWindow() {
            assertThat(RegistrationRules.registrationOpen(window, opens.plusSeconds(60))).isEmpty();
            assertThat(RegistrationRules.registrationOpen(window, opens.minusSeconds(1)))
                    .map(Violation::message).contains("Registration for Semester 1 has not opened yet.");
            assertThat(RegistrationRules.registrationOpen(window, closes))
                    .map(Violation::message).contains("Registration for Semester 1 is closed.");
            Window flagOff = new Window(false, opens, closes, closes, "Semester 1");
            assertThat(code(RegistrationRules.registrationOpen(flagOff, opens.plusSeconds(60)))).isEqualTo(ErrorCode.REGISTRATION_CLOSED);
        }

        @Test
        void addDropRunsUntilItsOwnDeadlineEvenAfterRegistrationCloses() {
            assertThat(RegistrationRules.addDropOpen(window, closes.plusSeconds(60))).isEmpty();
            assertThat(code(RegistrationRules.addDropOpen(window, closes.plus(Duration.ofDays(8)))))
                    .isEqualTo(ErrorCode.ADD_DROP_DEADLINE_PASSED);
        }
    }

    @Test
    void sameOfferingTwiceIsRejected() {
        assertThat(RegistrationRules.notAlreadyRegistered(cs101A, List.of(cs101A)))
                .map(Violation::message).contains("CS101 is already in your registration.");
    }

    @Test
    void sameCourseInAnotherSectionIsRejected() {
        Optional<Violation> v = RegistrationRules.notAlreadyRegistered(cs101B, List.of(cs101A));
        assertThat(code(v)).isEqualTo(ErrorCode.DUPLICATE_COURSE);
        assertThat(v.get().message()).isEqualTo("You already have CS101 in your registration (section A).");
    }

    @Test
    void missingPrerequisitesAreNamed() {
        OfferingFacts advanced = offering(40, 4, "CS401", "A", 3, 5, Map.of(CS101, "CS101", CS201, "CS201", MA101, "MA101"));
        assertThat(RegistrationRules.prerequisitesMet(advanced, Set.of(CS101)))
                .map(Violation::message).contains("CS401 requires CS201 and MA101, which you have not passed yet.");
        assertThat(RegistrationRules.prerequisitesMet(advanced, Set.of(CS101, CS201, MA101))).isEmpty();
    }

    @Test
    void fullOfferingsCannotBeAdded() {
        assertThat(code(RegistrationRules.hasSeat(ma101))).isEqualTo(ErrorCode.COURSE_FULL);
        assertThat(RegistrationRules.hasSeat(cs101A)).isEmpty();
    }

    @Test
    void maximumCreditsAreEnforcedWithAClearMessage() {
        assertThat(RegistrationRules.withinMaxCredits(cs201, 20, 24)).isEmpty();
        assertThat(RegistrationRules.withinMaxCredits(cs201, 21, 24)).map(Violation::message)
                .contains("Adding CS201 (4 credits) would bring you to 25 credits; the maximum is 24.");
    }

    @Test
    void creditRangeIsCheckedOnSubmission() {
        assertThat(RegistrationRules.withinCreditRange(12, 12, 24)).isEmpty();
        assertThat(RegistrationRules.withinCreditRange(9, 12, 24)).map(Violation::message)
                .contains("You have 9 credits; at least 12 are required.");
        assertThat(code(RegistrationRules.withinCreditRange(25, 12, 24))).isEqualTo(ErrorCode.CREDIT_LIMIT);
    }

    @Test
    void droppingBelowTheMinimumIsRejected() {
        assertThat(RegistrationRules.dropKeepsMinimum(cs101A, 15, 12)).isEmpty();
        assertThat(code(RegistrationRules.dropKeepsMinimum(cs201, 15, 12))).isEqualTo(ErrorCode.CREDIT_LIMIT);
    }

    @Test
    void overlappingClassesClashButBackToBackOnesDoNot() {
        OfferingFacts overlapping = offering(50, 5, "PH101", "A", 3, 5, Map.of(), monday("09:00", "10:30"));
        assertThat(RegistrationRules.noTimetableClash(overlapping, List.of(cs101A))).map(Violation::message)
                .contains("PH101 (A) clashes with CS101 (A) on Mon 08:00–10:00.");
        // cs201 starts at 10:00 exactly when cs101A ends.
        assertThat(RegistrationRules.noTimetableClash(cs201, List.of(cs101A))).isEmpty();
    }

    @Test
    void combinedCheckReportsEveryBrokenRuleInPriorityOrder() {
        List<Violation> violations = RegistrationRules.violationsForAdding(cs201, List.of(cs101A), Set.of(), 6);
        assertThat(violations).extracting(Violation::code)
                .containsExactly(ErrorCode.PREREQUISITE_NOT_MET, ErrorCode.CREDIT_LIMIT);
        assertThat(RegistrationRules.violationsForAdding(cs201, List.of(cs101A), Set.of(CS101), 24)).isEmpty();
    }
}
