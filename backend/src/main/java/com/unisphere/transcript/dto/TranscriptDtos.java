package com.unisphere.transcript.dto;

import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TranscriptDtos {

    private TranscriptDtos() {
    }

    public record TranscriptStudent(
            Long id, String studentNumber, String name, String programCode, String programName, String awardLevel,
            String departmentName, LocalDate admissionDate, int yearOfStudy, String status) {
    }

    /**
     * An unofficial transcript. Semesters are in chronological order (oldest first), as on a printed
     * transcript; {@code reference} identifies this exact content and can be verified by the registrar.
     */
    public record Transcript(
            String universityName, String title, TranscriptStudent student, List<SemesterRecord> semesters,
            BigDecimal cgpa, int creditsAttempted, int creditsEarned, Instant generatedAt, String reference,
            String watermark) {
    }

    /**
     * Result of checking a reference printed on a transcript.
     *
     * @param current whether the student's record still produces this reference (false once results changed)
     */
    public record Verification(
            String reference, String studentNumber, String studentName, BigDecimal cgpa, int creditsEarned,
            int resultsCount, Instant firstIssuedAt, Instant lastIssuedAt, int issueCount, boolean current) {
    }
}
