package com.unisphere.result;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.CourseOfferingRepository;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.result.ClassResults.Row;
import com.unisphere.result.dto.ResultDtos.MarksSheet;
import com.unisphere.result.dto.ResultDtos.ResultsQueueEntry;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The registrar's side of results: the review queue, finalization (with grade snapshot) and returns. */
@Service
@RequiredArgsConstructor
public class ResultsAdminService {

    private final CourseOfferingRepository offerings;
    private final SemesterRepository semesters;
    private final CourseResultRepository results;
    private final ClassResults classResults;
    private final GradingScaleService scale;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Offerings of a semester (default: current) with their results status and marks progress. */
    @Transactional(readOnly = true)
    public PageResponse<ResultsQueueEntry> search(Long semesterId, ResultsStatus status, String q, Pageable pageable) {
        Long effectiveSemester = Optional.ofNullable(semesterId)
                .or(() -> semesters.findByCurrentTrue().map(Semester::getId))
                .orElse(null);
        if (effectiveSemester == null) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        String pattern = Specs.containsPattern(q);
        Specification<CourseOffering> spec = Specs.allOf(
                (r, cq, cb) -> cb.equal(r.get("semester").get("id"), effectiveSemester),
                status == null ? null : (r, cq, cb) -> cb.equal(r.get("resultsStatus"), status),
                pattern == null ? null : (r, cq, cb) -> cb.or(
                        cb.like(cb.lower(r.get("course").get("code")), pattern, '\\'),
                        cb.like(cb.lower(r.get("course").get("title")), pattern, '\\')));
        Page<CourseOffering> page = offerings.findAll(spec, pageable);
        Map<Long, ClassProgress> progress = results.progress(page.getContent().stream().map(CourseOffering::getId).toList());
        return PageResponse.of(page, o -> entry(o, progress.getOrDefault(o.getId(), ClassProgress.empty(o.getId()))));
    }

    /**
     * SUBMITTED → FINALIZED. Grades are computed from the active scale and stored on each result, so the
     * students' records never change if the scale is edited later (RES-5).
     */
    @Transactional
    public MarksSheet finalizeResults(Long offeringId) {
        CourseOffering o = lockSubmitted(offeringId, "finalized");
        List<Row> rows = classResults.load(offeringId);
        LecturerResultsService.requireCompleteMarks(o, rows, "Return the marks to the lecturer to complete them.");
        Instant now = clock.instant();
        List<GradingRules.Band> bands = scale.activeBands();
        long passed = 0;
        for (Row row : rows) {
            GradingRules.Band band = GradingRules.gradeFor(row.result().getTotalMark(), bands);
            row.result().finalizeWith(band, now);
            passed += band.passing() ? 1 : 0;
        }
        o.finalizeResults(CurrentUser.id(), now);
        audit.record(AuditAction.RESULTS_FINALIZED, "CourseOffering", offeringId,
                Map.of("offering", o.getLabel(), "students", rows.size(), "passed", passed));
        events.publishEvent(new ResultsFinalizedEvent(offeringId, o.getCourse().getCode(), o.getCourse().getTitle(), o.getSemester().getName()));
        return classResults.sheet(o, rows);
    }

    /** SUBMITTED → DRAFT, with a comment telling the lecturer what to correct. */
    @Transactional
    public MarksSheet returnToLecturer(Long offeringId, String comment) {
        CourseOffering o = lockSubmitted(offeringId, "returned");
        o.returnResults(comment.trim());
        audit.record(AuditAction.RESULTS_RETURNED, "CourseOffering", offeringId,
                Map.of("offering", o.getLabel(), "comment", comment.trim()));
        return classResults.sheet(o, classResults.load(offeringId));
    }

    private CourseOffering lockSubmitted(Long offeringId, String action) {
        CourseOffering o = offerings.lockById(offeringId).orElseThrow(() -> ApiException.notFound("Offering", offeringId));
        if (o.getResultsStatus() != ResultsStatus.SUBMITTED) {
            throw ApiException.invalidState("Only submitted results can be %s (results for %s are %s)."
                    .formatted(action, o.getLabel(), o.getResultsStatus().name().toLowerCase()));
        }
        return o;
    }

    private static ResultsQueueEntry entry(CourseOffering o, ClassProgress p) {
        return new ResultsQueueEntry(o.getId(), o.getCourse().getCode(), o.getCourse().getTitle(), o.getSection(),
                SemesterRef.from(o.getSemester()), o.getLecturer() == null ? null : o.getLecturer().getDisplayName(),
                o.getResultsStatus(), p.classSize().intValue(), p.complete().intValue(), o.getResultsSubmittedAt(),
                o.getResultsFinalizedAt());
    }
}
