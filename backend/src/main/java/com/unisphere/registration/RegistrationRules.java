package com.unisphere.registration;

import com.unisphere.common.exception.ErrorCode;
import com.unisphere.offering.TimetableRules.Slot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Course-registration business rules (requirements REG-1…REG-9) as pure functions over small value
 * objects. The service gathers the data, these functions decide, and the same functions drive the
 * eligibility shown in the student catalogue — so what the UI promises is exactly what the API enforces.
 */
public final class RegistrationRules {

    private RegistrationRules() {
    }

    /** A broken rule: the error code fixes the HTTP status, the message is shown to the student. */
    public record Violation(ErrorCode code, String message) {
    }

    /** An offering as the rules see it. {@code prerequisites} maps course id → code. */
    public record OfferingFacts(
            long offeringId, long courseId, String courseCode, String section, int credits, int seatsLeft,
            Map<Long, String> prerequisites, List<Slot> slots) {

        String label() {
            return courseCode + " (" + section + ")";
        }
    }

    /** The registration window as the rules see it. */
    public record Window(boolean openFlag, Instant opensAt, Instant closesAt, Instant addDropDeadline, String semesterName) {

        boolean registrationOpen(Instant now) {
            return openFlag && !now.isBefore(opensAt) && now.isBefore(closesAt);
        }

        boolean addDropOpen(Instant now) {
            return !now.isBefore(opensAt) && now.isBefore(addDropDeadline);
        }
    }

    // ---- Individual rules --------------------------------------------------------------------------

    /** Draft changes and submission are possible only while registration is open. */
    public static Optional<Violation> registrationOpen(Window window, Instant now) {
        if (window.registrationOpen(now)) {
            return Optional.empty();
        }
        String when = now.isBefore(window.opensAt()) ? "has not opened yet" : "is closed";
        return Optional.of(new Violation(ErrorCode.REGISTRATION_CLOSED,
                "Registration for %s %s.".formatted(window.semesterName(), when)));
    }

    /** Changes to a submitted registration (add/drop) are possible until the add/drop deadline. */
    public static Optional<Violation> addDropOpen(Window window, Instant now) {
        return window.addDropOpen(now)
                ? Optional.empty()
                : Optional.of(new Violation(ErrorCode.ADD_DROP_DEADLINE_PASSED,
                        "The add/drop deadline for %s has passed.".formatted(window.semesterName())));
    }

    /** The same course cannot be taken twice in one semester, not even in a different section. */
    public static Optional<Violation> notAlreadyRegistered(OfferingFacts candidate, List<OfferingFacts> registered) {
        return registered.stream()
                .filter(r -> r.courseId() == candidate.courseId())
                .findFirst()
                .map(r -> new Violation(ErrorCode.DUPLICATE_COURSE, r.offeringId() == candidate.offeringId()
                        ? "%s is already in your registration.".formatted(candidate.courseCode())
                        : "You already have %s in your registration (section %s).".formatted(r.courseCode(), r.section())));
    }

    public static Optional<Violation> prerequisitesMet(OfferingFacts candidate, Set<Long> passedCourseIds) {
        List<String> missing = missingPrerequisites(candidate, passedCourseIds);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Violation(ErrorCode.PREREQUISITE_NOT_MET,
                "%s requires %s, which you have not passed yet.".formatted(candidate.courseCode(), joinAnd(missing))));
    }

    public static List<String> missingPrerequisites(OfferingFacts candidate, Set<Long> passedCourseIds) {
        return candidate.prerequisites().entrySet().stream()
                .filter(e -> !passedCourseIds.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
    }

    public static Optional<Violation> hasSeat(OfferingFacts candidate) {
        return candidate.seatsLeft() > 0
                ? Optional.empty()
                : Optional.of(new Violation(ErrorCode.COURSE_FULL,
                        "%s is full. Choose another section or course.".formatted(candidate.label())));
    }

    public static Optional<Violation> withinMaxCredits(OfferingFacts candidate, int currentCredits, int maxCredits) {
        int after = currentCredits + candidate.credits();
        return after <= maxCredits
                ? Optional.empty()
                : Optional.of(new Violation(ErrorCode.CREDIT_LIMIT,
                        "Adding %s (%d credits) would bring you to %d credits; the maximum is %d."
                                .formatted(candidate.courseCode(), candidate.credits(), after, maxCredits)));
    }

    public static Optional<Violation> withinCreditRange(int totalCredits, int minCredits, int maxCredits) {
        if (totalCredits < minCredits) {
            return Optional.of(new Violation(ErrorCode.CREDIT_LIMIT,
                    "You have %d credits; at least %d are required.".formatted(totalCredits, minCredits)));
        }
        if (totalCredits > maxCredits) {
            return Optional.of(new Violation(ErrorCode.CREDIT_LIMIT,
                    "You have %d credits; the maximum is %d.".formatted(totalCredits, maxCredits)));
        }
        return Optional.empty();
    }

    /** Dropping must not take a submitted registration below the minimum load. */
    public static Optional<Violation> dropKeepsMinimum(OfferingFacts dropped, int currentCredits, int minCredits) {
        int after = currentCredits - dropped.credits();
        return after >= minCredits
                ? Optional.empty()
                : Optional.of(new Violation(ErrorCode.CREDIT_LIMIT,
                        "Dropping %s would leave you with %d credits; at least %d are required. Add another course first."
                                .formatted(dropped.courseCode(), after, minCredits)));
    }

    /** A student cannot attend two classes at the same time. */
    public static Optional<Violation> noTimetableClash(OfferingFacts candidate, List<OfferingFacts> registered) {
        for (OfferingFacts other : registered) {
            for (Slot mine : candidate.slots()) {
                for (Slot theirs : other.slots()) {
                    if (overlaps(mine, theirs)) {
                        return Optional.of(new Violation(ErrorCode.TIMETABLE_CLASH,
                                "%s clashes with %s on %s.".formatted(candidate.label(), other.label(), theirs.describe())));
                    }
                }
            }
        }
        return Optional.empty();
    }

    // ---- Combined checks ---------------------------------------------------------------------------

    /**
     * All rules for adding a course, in the order a student would want to hear about them. Seat
     * availability is checked here for a friendly message; the atomic seat update is the real guarantee.
     */
    public static List<Violation> violationsForAdding(OfferingFacts candidate, List<OfferingFacts> registered,
                                                      Set<Long> passed, int maxCredits) {
        int credits = registered.stream().mapToInt(OfferingFacts::credits).sum();
        List<Violation> violations = new ArrayList<>();
        notAlreadyRegistered(candidate, registered).ifPresent(violations::add);
        prerequisitesMet(candidate, passed).ifPresent(violations::add);
        hasSeat(candidate).ifPresent(violations::add);
        noTimetableClash(candidate, registered).ifPresent(violations::add);
        withinMaxCredits(candidate, credits, maxCredits).ifPresent(violations::add);
        return violations;
    }

    /** Back-to-back classes (one ends 10:00, the next starts 10:00) do not clash. */
    private static boolean overlaps(Slot a, Slot b) {
        return a.day() == b.day() && a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }

    private static String joinAnd(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return items.subList(0, items.size() - 1).stream().collect(Collectors.joining(", ")) + " and " + items.getLast();
    }
}
