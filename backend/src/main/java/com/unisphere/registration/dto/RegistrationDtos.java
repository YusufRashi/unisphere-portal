package com.unisphere.registration.dto;

import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.offering.dto.OfferingDtos.SessionResponse;
import com.unisphere.registration.RegistrationStatus;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    // ---- Requests ----

    public record AddCourseRequest(@NotNull Long offeringId) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    // ---- Student view ----

    /** The registration window of the semester, with what is possible right now. */
    public record SemesterWindow(
            Long id, String code, String name, Instant registrationOpensAt, Instant registrationClosesAt,
            Instant addDropDeadline, boolean registrationOpen, boolean addDropOpen, int minCredits, int maxCredits,
            boolean requiresApproval) {
    }

    public record ItemResponse(
            Long id, Long offeringId, Long courseId, String courseCode, String courseTitle, int credits, String section,
            String lecturer, List<SessionResponse> sessions, boolean seatReserved, Instant addedAt, Instant droppedAt) {
    }

    /**
     * What the student may do now. The UI enables buttons from these flags; the API enforces the same rules.
     *
     * @param removeMeansDrop true once submitted: removing a course is a "drop" that releases a seat
     * @param lockedReason    why changes are not possible right now, if they are not
     */
    public record Permissions(boolean canAdd, boolean canRemove, boolean canSubmit, boolean removeMeansDrop,
                              String lockedReason) {
    }

    /** {@code status} is null when the student has not started a registration for the semester yet. */
    public record RegistrationView(
            Long id, SemesterWindow semester, RegistrationStatus status, List<ItemResponse> items,
            List<ItemResponse> droppedItems, int totalCredits, Instant submittedAt, Instant reviewedAt,
            String rejectionReason, Permissions permissions) {
    }

    public enum Availability {
        AVAILABLE,
        IN_REGISTRATION,
        SAME_COURSE_REGISTERED,
        PREREQUISITES_MISSING,
        FULL,
        TIMETABLE_CLASH,
        CREDIT_LIMIT
    }

    public record PrerequisiteStatus(Long courseId, String code, boolean met) {
    }

    /** A catalogue row: the offering plus whether this student can add it, and if not, why. */
    public record CatalogueEntry(OfferingResponse offering, List<PrerequisiteStatus> prerequisites,
                                 Availability availability, String reason) {
    }

    public record HistoryEntry(Long id, SemesterRef semester, RegistrationStatus status, int totalCredits,
                               int courseCount, Instant submittedAt) {
    }

    public record TimetableEntry(
            Long offeringId, String courseCode, String courseTitle, String section, String lecturer,
            DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String venue) {
    }

    public record Timetable(SemesterRef semester, RegistrationStatus status, List<TimetableEntry> entries) {
    }

    // ---- Admin view ----

    public record StudentRef(Long id, String studentNumber, String name, String email, String programCode) {
    }

    public record RegistrationSummary(
            Long id, StudentRef student, SemesterRef semester, RegistrationStatus status, int totalCredits,
            int courseCount, Instant submittedAt, Instant reviewedAt) {
    }

    public record RegistrationDetail(RegistrationSummary summary, List<ItemResponse> items, List<ItemResponse> droppedItems,
                                     String rejectionReason, int minCredits, int maxCredits) {
    }
}
