package com.unisphere.result.dto;

import com.unisphere.offering.ResultsStatus;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.result.GradingRules;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ResultDtos {

    private ResultDtos() {
    }

    // ---- Grading scale ----

    public record BandRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z+-]{0,2}$", message = "must be a letter grade such as A, B+ or C-")
            String letter,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minMark,
            @NotNull @DecimalMin("0") @DecimalMax("5") @Digits(integer = 1, fraction = 2) BigDecimal gradePoints,
            boolean passing) {

        public GradingRules.Band toRule() {
            return new GradingRules.Band(letter.trim(), minMark, gradePoints, passing);
        }
    }

    public record GradingScaleRequest(@NotNull @Size(min = 2, max = 15) List<@Valid @NotNull BandRequest> bands) {
    }

    public record BandResponse(String letter, BigDecimal minMark, BigDecimal gradePoints, boolean passing) {
    }

    /** Bands are ordered highest first; each covers marks from its minimum up to the next band's minimum. */
    public record GradingScaleResponse(Long id, String name, List<BandResponse> bands) {
    }

    // ---- Marks entry ----

    /** A null mark means "not entered yet"; marks are out of the offering's CAT / exam weight. */
    public record MarkEntry(
            @NotNull Long registrationItemId,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal catMark,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal examMark) {
    }

    /** Only the listed students change; the others keep their marks. */
    public record MarksRequest(@NotNull @Size(max = 1000) List<@Valid @NotNull MarkEntry> entries) {
    }

    public record ReturnRequest(@NotBlank @Size(max = 500) String comment) {
    }

    public record SheetOffering(
            Long id, String courseCode, String courseTitle, int credits, String section, SemesterRef semester,
            String lecturer, BigDecimal catWeight, BigDecimal examWeight, ResultsStatus resultsStatus,
            Instant submittedAt, Instant finalizedAt, String returnComment) {
    }

    /**
     * One student's line. {@code grade}, {@code gradePoints} and {@code passed} are the finalized values
     * once results are finalized, and a preview from the current scale before that.
     */
    public record MarkRow(
            Long registrationItemId, Long studentId, String studentNumber, String name, BigDecimal catMark,
            BigDecimal examMark, BigDecimal totalMark, String grade, BigDecimal gradePoints, Boolean passed) {
    }

    /** What the viewer may do now; the API enforces the same rules. */
    public record SheetPermissions(boolean canEdit, boolean canSubmit, String lockedReason) {
    }

    public record MarksSheet(SheetOffering offering, List<MarkRow> rows, int classSize, int complete,
                             SheetPermissions permissions) {
    }

    // ---- Lists ----

    public record TeachingOffering(OfferingResponse offering, int classSize, int marksComplete) {
    }

    public record ResultsQueueEntry(
            Long offeringId, String courseCode, String courseTitle, String section, SemesterRef semester,
            String lecturer, ResultsStatus resultsStatus, int classSize, int marksComplete, Instant submittedAt,
            Instant finalizedAt) {
    }

    // ---- Student academic record ----

    public record StudentInfo(Long id, String studentNumber, String name, String programCode, String programName) {
    }

    /** {@code countsTowardCgpa} is false for an earlier attempt of a course that was later repeated. */
    public record CourseGrade(
            Long resultId, Long courseId, String courseCode, String courseTitle, int credits, String section,
            BigDecimal catMark, BigDecimal examMark, BigDecimal totalMark, String grade, BigDecimal gradePoints,
            boolean passed, boolean countsTowardCgpa) {
    }

    public record SemesterRecord(SemesterRef semester, List<CourseGrade> courses, BigDecimal gpa, int creditsAttempted,
                                 int creditsEarned) {
    }

    /** Finalized results only, newest semester first. {@code cgpa} is null before the first finalized result. */
    public record AcademicRecord(StudentInfo student, List<SemesterRecord> semesters, BigDecimal cgpa,
                                 int creditsAttempted, int creditsEarned) {
    }
}
