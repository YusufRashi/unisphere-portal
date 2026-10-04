package com.unisphere.transcript;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.result.AcademicRecordService;
import com.unisphere.result.dto.ResultDtos.AcademicRecord;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import com.unisphere.transcript.dto.TranscriptDtos.Transcript;
import com.unisphere.transcript.dto.TranscriptDtos.TranscriptStudent;
import com.unisphere.transcript.dto.TranscriptDtos.Verification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles unofficial transcripts (TRN-1, TRN-2) from the student's finalized academic record and records
 * each issue under its content reference (TRN-4), so a printed copy can be checked later.
 */
@Service
@RequiredArgsConstructor
public class TranscriptService {

    public static final String TITLE = "Unofficial Academic Transcript";
    public static final String WATERMARK = "UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION";

    private final StudentRepository students;
    private final AcademicRecordService records;
    private final TranscriptReferenceRepository references;
    private final AuditService audit;
    private final UniSphereProperties properties;
    private final Clock clock;

    /** The signed-in student's own transcript. */
    @Transactional
    public Transcript issueMine(String format) {
        Student student = students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only students have a transcript."));
        return issue(student.getId(), format);
    }

    /** Any student's transcript (registrar). */
    @Transactional
    public Transcript issueFor(Long studentId, String format) {
        return issue(studentId, format);
    }

    @Transactional(readOnly = true)
    public Verification verify(String reference) {
        String normalized = reference.trim().toUpperCase(Locale.ROOT);
        TranscriptReference ref = references.findByReference(normalized)
                .orElseThrow(() -> ApiException.notFound("Transcript reference", normalized));
        Student student = ref.getStudent();
        AcademicRecord record = records.recordFor(student.getId());
        boolean current = fingerprint(student, record).reference().equals(normalized);
        return new Verification(ref.getReference(), student.getStudentNumber(), student.getUser().getFullName(), ref.getCgpa(),
                ref.getCreditsEarned(), ref.getResultsCount(), ref.getFirstIssuedAt(), ref.getLastIssuedAt(), ref.getIssueCount(),
                current);
    }

    private Transcript issue(Long studentId, String format) {
        Student student = students.findDetailedById(studentId).orElseThrow(() -> ApiException.notFound("Student", studentId));
        AcademicRecord record = records.recordFor(studentId);
        TranscriptFingerprint.Fingerprint fp = fingerprint(student, record);
        Instant now = clock.instant();
        int resultsCount = record.semesters().stream().mapToInt(s -> s.courses().size()).sum();
        references.recordIssue(fp.reference(), studentId, fp.sha256(), record.cgpa(), record.creditsEarned(), resultsCount, now);
        audit.record(AuditAction.TRANSCRIPT_ISSUED, "Student", studentId,
                Map.of("reference", fp.reference(), "format", format, "studentNumber", student.getStudentNumber()));

        // A transcript reads oldest semester first; the results page shows newest first.
        List<SemesterRecord> chronological = record.semesters().reversed();
        var program = student.getProgram();
        TranscriptStudent info = new TranscriptStudent(student.getId(), student.getStudentNumber(), student.getUser().getFullName(),
                program.getCode(), program.getName(), program.getAwardLevel().name(), program.getDepartment().getName(),
                student.getAdmissionDate(), student.getYearOfStudy(), student.getStatus().name());
        return new Transcript(properties.universityName(), TITLE, info, chronological, record.cgpa(), record.creditsAttempted(),
                record.creditsEarned(), now, fp.reference(), WATERMARK);
    }

    private static TranscriptFingerprint.Fingerprint fingerprint(Student student, AcademicRecord record) {
        return TranscriptFingerprint.of(student.getStudentNumber(), record.semesters(), record.cgpa());
    }
}
