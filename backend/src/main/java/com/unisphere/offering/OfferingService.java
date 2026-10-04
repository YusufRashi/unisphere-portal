package com.unisphere.offering;

import com.unisphere.academic.Course;
import com.unisphere.academic.CourseService;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.lecturer.LecturerService;
import com.unisphere.offering.dto.OfferingDtos.OfferingRequest;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.offering.dto.OfferingDtos.SessionRequest;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.SemesterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfferingService {

    private static final String ENTITY = "CourseOffering";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CourseOfferingRepository offerings;
    private final CourseService courses;
    private final SemesterService semesters;
    private final SemesterRepository semesterRepository;
    private final LecturerService lecturers;
    private final AuditService audit;
    private final Clock clock;

    /** Lists offerings of {@code semesterId}, or of the current semester when it is null. */
    @Transactional(readOnly = true)
    public PageResponse<OfferingResponse> search(Long semesterId, String q, Long departmentId, Integer level,
                                                 Long lecturerId, Pageable pageable) {
        Long effectiveSemester = Optional.ofNullable(semesterId)
                .or(() -> semesterRepository.findByCurrentTrue().map(Semester::getId))
                .orElse(null);
        if (effectiveSemester == null) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        return PageResponse.of(searchInSemester(effectiveSemester, q, departmentId, level, lecturerId, pageable),
                OfferingResponse::from);
    }

    /** Entity-level search used by other features (e.g. the student registration catalogue). */
    @Transactional(readOnly = true)
    public Page<CourseOffering> searchInSemester(Long effectiveSemester, String q, Long departmentId, Integer level,
                                                 Long lecturerId, Pageable pageable) {
        String pattern = Specs.containsPattern(q);
        Specification<CourseOffering> spec = Specs.allOf(
                (r, cq, cb) -> cb.equal(r.get("semester").get("id"), effectiveSemester),
                pattern == null ? null : (r, cq, cb) -> cb.or(
                        cb.like(cb.lower(r.get("course").get("code")), pattern, '\\'),
                        cb.like(cb.lower(r.get("course").get("title")), pattern, '\\')),
                departmentId == null ? null : (r, cq, cb) -> cb.equal(r.get("course").get("department").get("id"), departmentId),
                level == null ? null : (r, cq, cb) -> cb.equal(r.get("course").get("level"), level),
                lecturerId == null ? null : (r, cq, cb) -> cb.equal(r.get("lecturer").get("id"), lecturerId));
        return offerings.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public OfferingResponse get(Long id) {
        return OfferingResponse.from(loadDetailed(id));
    }

    @Transactional
    public OfferingResponse create(OfferingRequest r) {
        Course course = courses.load(r.courseId());
        if (!course.isActive()) {
            throw ApiException.validation("courseId", "Inactive courses cannot be offered.");
        }
        Semester semester = semesters.load(r.semesterId());
        CourseOffering.Details details = validate(r, course, semester, null, 0);
        CourseOffering offering = offerings.save(new CourseOffering(course, semester, details));
        audit.record(AuditAction.CREATED, ENTITY, offering.getId(),
                Map.of("offering", offering.getLabel(), "semester", semester.getCode()));
        return OfferingResponse.from(offering);
    }

    @Transactional
    public OfferingResponse update(Long id, OfferingRequest r) {
        CourseOffering offering = loadDetailed(id);
        if (!offering.getCourse().getId().equals(r.courseId())) {
            throw ApiException.validation("courseId", "The course of an existing offering cannot be changed.");
        }
        if (!offering.getSemester().getId().equals(r.semesterId())) {
            throw ApiException.validation("semesterId", "The semester of an existing offering cannot be changed.");
        }
        if (offering.getResultsStatus() != ResultsStatus.DRAFT) {
            throw ApiException.invalidState("Results for %s have been %s, so the offering can no longer be changed."
                    .formatted(offering.getLabel(), offering.getResultsStatus().name().toLowerCase()));
        }
        boolean weightsChanged = offering.getCatWeight().compareTo(r.catWeight()) != 0
                || offering.getExamWeight().compareTo(r.examWeight()) != 0;
        if (weightsChanged && offerings.hasMarks(id)) {
            throw ApiException.validation("catWeight", "Marks have already been entered against the current weights.");
        }
        // Read the live seat count: the entity copy may be stale if a student registered meanwhile.
        offering.apply(validate(r, offering.getCourse(), offering.getSemester(), id, offerings.currentEnrolledCount(id)));
        audit.record(AuditAction.UPDATED, ENTITY, id, Map.of("offering", offering.getLabel()));
        return OfferingResponse.from(offering);
    }

    /** Offerings with enrolled students cannot be deleted. */
    @Transactional
    public void delete(Long id) {
        CourseOffering offering = loadDetailed(id);
        int enrolled = offerings.currentEnrolledCount(id);
        if (enrolled > 0) {
            throw ApiException.inUse("%s has %d enrolled student(s) and cannot be deleted."
                    .formatted(offering.getLabel(), enrolled));
        }
        String label = offering.getLabel();
        offerings.delete(offering);
        audit.record(AuditAction.DELETED, ENTITY, id, Map.of("offering", label));
    }

    private CourseOffering.Details validate(OfferingRequest r, Course course, Semester semester, Long excludeId,
                                            int enrolledCount) {
        Instant now = clock.instant();
        if (semester.isCompleted(now)) {
            throw ApiException.invalidState("Offerings of a completed semester cannot be changed.");
        }
        if (r.catWeight().add(r.examWeight()).compareTo(HUNDRED) != 0) {
            throw ApiException.validation("examWeight", "CAT and exam weights must add up to 100.");
        }
        if (r.capacity() < enrolledCount) {
            throw ApiException.validation("capacity",
                    "Capacity cannot be lower than the %d students already enrolled.".formatted(enrolledCount));
        }
        if (offerings.sectionTaken(course.getId(), semester.getId(), r.section().trim(), excludeId)) {
            throw ApiException.duplicate("Section", "%s %s in %s".formatted(course.getCode(), r.section().trim().toUpperCase(),
                    semester.getCode()));
        }
        if (r.examStartsAt() != null) {
            Instant start = semester.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = semester.getEndDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (r.examStartsAt().isBefore(start) || !r.examStartsAt().isBefore(end)) {
                throw ApiException.validation("examStartsAt", "The exam must take place within the semester dates.");
            }
        }

        List<TimetableRules.Slot> slots = r.sessions().stream().map(SessionRequest::toSlot).toList();
        List<String> internal = TimetableRules.internalProblems(slots);
        if (!internal.isEmpty()) {
            throw ApiException.validation("sessions", String.join(" ", internal));
        }
        Lecturer lecturer = r.lecturerId() == null ? null : lecturers.load(r.lecturerId());
        List<TimetableRules.BookedSlot> booked = offerings.findSessionsInSemester(semester.getId(), excludeId).stream()
                .map(s -> new TimetableRules.BookedSlot(s.toSlot(), s.getOffering().getLabel(),
                        s.getOffering().getLecturer() == null ? null : s.getOffering().getLecturer().getId()))
                .toList();
        TimetableRules.findConflict(slots, lecturer == null ? null : lecturer.getId(), booked).ifPresent(message -> {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT, message);
        });

        return new CourseOffering.Details(r.section(), lecturer, r.capacity(), r.catWeight(), r.examWeight(),
                r.examStartsAt(), r.examVenue(), slots);
    }

    public CourseOffering loadDetailed(Long id) {
        return offerings.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Offering", id));
    }
}
