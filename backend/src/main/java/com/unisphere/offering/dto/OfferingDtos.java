package com.unisphere.offering.dto;

import com.unisphere.academic.Course;
import com.unisphere.lecturer.dto.LecturerDtos.LecturerRef;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.OfferingSession;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.offering.TimetableRules;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class OfferingDtos {

    private OfferingDtos() {
    }

    public record SessionRequest(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotBlank @Size(max = 80) String venue) {

        public TimetableRules.Slot toSlot() {
            return new TimetableRules.Slot(dayOfWeek, startTime, endTime, venue);
        }
    }

    public record OfferingRequest(
            @NotNull Long courseId,
            @NotNull Long semesterId,
            @NotBlank @Size(max = 5) @Pattern(regexp = "^[A-Za-z0-9]+$", message = "may contain only letters and digits") String section,
            Long lecturerId,
            @NotNull @Min(1) @Max(1000) Integer capacity,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal catWeight,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal examWeight,
            Instant examStartsAt,
            @Size(max = 80) String examVenue,
            @NotNull @Size(max = 10) List<@Valid @NotNull SessionRequest> sessions) {
    }

    public record OfferingCourse(Long id, String code, String title, int credits, int level, Long departmentId,
                                 String departmentCode) {
        public static OfferingCourse from(Course c) {
            return new OfferingCourse(c.getId(), c.getCode(), c.getTitle(), c.getCredits(), c.getLevel(),
                    c.getDepartment().getId(), c.getDepartment().getCode());
        }
    }

    public record SessionResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String venue) {
        public static SessionResponse from(OfferingSession s) {
            return new SessionResponse(s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getVenue());
        }
    }

    public record OfferingResponse(
            Long id, OfferingCourse course, SemesterRef semester, String section, LecturerRef lecturer,
            int capacity, int enrolledCount, int seatsLeft, BigDecimal catWeight, BigDecimal examWeight,
            Instant examStartsAt, String examVenue, ResultsStatus resultsStatus, List<SessionResponse> sessions) {

        public static OfferingResponse from(CourseOffering o) {
            return new OfferingResponse(o.getId(), OfferingCourse.from(o.getCourse()), SemesterRef.from(o.getSemester()),
                    o.getSection(), LecturerRef.from(o.getLecturer()), o.getCapacity(), o.getEnrolledCount(),
                    o.getSeatsLeft(), o.getCatWeight(), o.getExamWeight(), o.getExamStartsAt(), o.getExamVenue(),
                    o.getResultsStatus(), o.getSessions().stream().map(SessionResponse::from).toList());
        }
    }
}
