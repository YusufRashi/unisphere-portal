package com.unisphere.result;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.lecturer.LecturerRepository;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.CourseOfferingRepository;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.result.ClassResults.Row;
import com.unisphere.result.dto.ResultDtos.MarkEntry;
import com.unisphere.result.dto.ResultDtos.MarksSheet;
import com.unisphere.result.dto.ResultDtos.TeachingOffering;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks entry (RES-1, RES-2, RES-4). Only the lecturer assigned to an offering may change its marks, and
 * only while its results are in DRAFT; registrars may view the sheet and statistics. Every change locks the
 * offering row so a save can never interleave with a submission, return or finalization.
 */
@Service
@RequiredArgsConstructor
public class LecturerResultsService {

    private final CourseOfferingRepository offerings;
    private final LecturerRepository lecturers;
    private final SemesterRepository semesters;
    private final CourseResultRepository results;
    private final ClassResults classResults;
    private final AuditService audit;
    private final Clock clock;

    /** The signed-in lecturer's offerings in {@code semesterId}, or in the current semester when null. */
    @Transactional(readOnly = true)
    public List<TeachingOffering> myOfferings(Long semesterId) {
        Lecturer lecturer = lecturers.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only lecturers have teaching assignments."));
        Optional<Long> semester = Optional.ofNullable(semesterId).or(() -> semesters.findByCurrentTrue().map(Semester::getId));
        if (semester.isEmpty()) {
            return List.of();
        }
        List<CourseOffering> mine = offerings.findByLecturerIdAndSemesterIdOrderByCourseCodeAscSectionAsc(lecturer.getId(), semester.get());
        Map<Long, ClassProgress> progress = results.progress(mine.stream().map(CourseOffering::getId).toList());
        return mine.stream().map(o -> {
            ClassProgress p = progress.getOrDefault(o.getId(), ClassProgress.empty(o.getId()));
            return new TeachingOffering(OfferingResponse.from(o), p.classSize().intValue(), p.complete().intValue());
        }).toList();
    }

    @Transactional(readOnly = true)
    public MarksSheet sheet(Long offeringId) {
        CourseOffering o = viewable(offeringId);
        return classResults.sheet(o, classResults.load(offeringId));
    }

    @Transactional(readOnly = true)
    public ResultStatistics.Statistics statistics(Long offeringId) {
        viewable(offeringId);
        return classResults.statistics(classResults.load(offeringId));
    }

    /** Saves the listed students' marks (a draft: missing marks are allowed until submission). */
    @Transactional
    public MarksSheet saveMarks(Long offeringId, List<MarkEntry> entries) {
        CourseOffering o = lockOwnDraft(offeringId);
        Map<Long, Row> byItem = classResults.load(offeringId).stream()
                .collect(Collectors.toMap(r -> r.item().getId(), Function.identity()));
        validate(entries, byItem, o);

        Long userId = CurrentUser.id();
        int changed = 0;
        for (MarkEntry e : entries) {
            Row row = byItem.get(e.registrationItemId());
            CourseResult result = row.result();
            if (result == null) {
                if (e.catMark() == null && e.examMark() == null) {
                    continue;
                }
                result = results.save(new CourseResult(row.item()));
            }
            if (result.enterMarks(e.catMark(), e.examMark(), userId)) {
                changed++;
            }
        }
        if (changed > 0) {
            audit.record(AuditAction.MARKS_SAVED, "CourseOffering", offeringId,
                    Map.of("offering", o.getLabel(), "studentsChanged", changed));
        }
        return classResults.sheet(o, classResults.load(offeringId));
    }

    /** DRAFT → SUBMITTED. Every registered student needs both marks. */
    @Transactional
    public MarksSheet submit(Long offeringId) {
        CourseOffering o = lockOwnDraft(offeringId);
        List<Row> rows = classResults.load(offeringId);
        requireCompleteMarks(o, rows, "Enter them before submitting.");
        o.submitResults(clock.instant());
        audit.record(AuditAction.RESULTS_SUBMITTED, "CourseOffering", offeringId,
                Map.of("offering", o.getLabel(), "students", rows.size()));
        return classResults.sheet(o, rows);
    }

    /** Shared with the registrar's finalization, which re-checks completeness. */
    static void requireCompleteMarks(CourseOffering o, List<Row> rows, String hint) {
        if (rows.isEmpty()) {
            throw ApiException.invalidState("%s has no registered students, so there are no results to submit.".formatted(o.getLabel()));
        }
        long missing = rows.stream().filter(r -> !r.complete()).count();
        if (missing > 0) {
            throw new ApiException(ErrorCode.MARKS_INCOMPLETE, "%d of %d students in %s are missing a CAT or exam mark. %s"
                    .formatted(missing, rows.size(), o.getLabel(), hint)).with("missing", missing);
        }
    }

    private CourseOffering viewable(Long offeringId) {
        CourseOffering o = offerings.findDetailedById(offeringId).orElseThrow(() -> ApiException.notFound("Offering", offeringId));
        if (!ClassResults.taughtByCurrentUser(o) && !CurrentUser.hasRole("ADMIN")) {
            throw ApiException.forbidden("You do not teach %s.".formatted(o.getLabel()));
        }
        return o;
    }

    private CourseOffering lockOwnDraft(Long offeringId) {
        CourseOffering o = offerings.lockById(offeringId).orElseThrow(() -> ApiException.notFound("Offering", offeringId));
        if (!ClassResults.taughtByCurrentUser(o)) {
            throw ApiException.forbidden("Only the lecturer teaching %s can enter its marks.".formatted(o.getLabel()));
        }
        if (o.getResultsStatus() != ResultsStatus.DRAFT) {
            throw ApiException.invalidState(o.getResultsStatus() == ResultsStatus.SUBMITTED
                    ? "Marks for %s have been submitted. Ask the registrar to return them if a correction is needed.".formatted(o.getLabel())
                    : "Results for %s have been finalized and can no longer be changed.".formatted(o.getLabel()));
        }
        return o;
    }

    /** Collects every problem so the lecturer sees all of them at once, each next to its cell. */
    private static void validate(List<MarkEntry> entries, Map<Long, Row> classList, CourseOffering o) {
        List<Map<String, String>> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            MarkEntry e = entries.get(i);
            String prefix = "entries[" + i + "].";
            if (!seen.add(e.registrationItemId())) {
                errors.add(Map.of("field", prefix + "registrationItemId", "message", "appears more than once"));
            } else if (!classList.containsKey(e.registrationItemId())) {
                errors.add(Map.of("field", prefix + "registrationItemId", "message", "is not a student registered in this class"));
            }
            String cat = GradingRules.markProblem(e.catMark(), o.getCatWeight());
            if (cat != null) {
                errors.add(Map.of("field", prefix + "catMark", "message", cat));
            }
            String exam = GradingRules.markProblem(e.examMark(), o.getExamWeight());
            if (exam != null) {
                errors.add(Map.of("field", prefix + "examMark", "message", exam));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Some marks are not valid.").with("errors", errors);
        }
    }
}
