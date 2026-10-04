package com.unisphere.semester.dto;

import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class SemesterDtos {

    private SemesterDtos() {
    }

    public record SemesterRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*$",
                    message = "may contain only letters, digits and hyphens") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank String academicYear,
            @NotNull @Min(1) @Max(3) Integer term,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull Instant registrationOpensAt,
            @NotNull Instant registrationClosesAt,
            @NotNull Instant addDropDeadline,
            boolean requiresApproval,
            @NotNull @Min(0) @Max(60) Integer minCredits,
            @NotNull @Min(1) @Max(60) Integer maxCredits) {

        public Semester.Schedule toSchedule() {
            return new Semester.Schedule(code, name, academicYear, term, startDate, endDate, registrationOpensAt,
                    registrationClosesAt, addDropDeadline, requiresApproval, minCredits, maxCredits);
        }
    }

    public record SemesterRef(Long id, String code, String name) {
        public static SemesterRef from(Semester s) {
            return new SemesterRef(s.getId(), s.getCode(), s.getName());
        }
    }

    public record SemesterResponse(
            Long id, String code, String name, String academicYear, int term,
            LocalDate startDate, LocalDate endDate,
            Instant registrationOpensAt, Instant registrationClosesAt, Instant addDropDeadline,
            boolean registrationOpenFlag, boolean registrationOpen, boolean requiresApproval,
            int minCredits, int maxCredits, boolean current, SemesterStatus status, long offeringCount) {

        /**
         * @param registrationOpenFlag what the administrator set
         * @param registrationOpen     whether students can actually register right now (flag and window)
         */
        public static SemesterResponse from(Semester s, Instant now, long offeringCount) {
            return new SemesterResponse(s.getId(), s.getCode(), s.getName(), s.getAcademicYear(), s.getTerm(),
                    s.getStartDate(), s.getEndDate(), s.getRegistrationOpensAt(), s.getRegistrationClosesAt(),
                    s.getAddDropDeadline(), s.isRegistrationOpen(), s.isRegistrationOpen(now), s.isRequiresApproval(),
                    s.getMinCredits(), s.getMaxCredits(), s.isCurrent(), s.status(now), offeringCount);
        }
    }
}
