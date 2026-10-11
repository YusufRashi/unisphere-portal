package com.unisphere.semester;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.semester.dto.SemesterDtos.SemesterRequest;
import com.unisphere.semester.dto.SemesterDtos.SemesterResponse;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private static final String ENTITY = "Semester";

    private final SemesterRepository semesters;
    private final EntityManager entityManager;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SemesterResponse> list() {
        Map<Long, Long> counts = offeringCounts();
        return semesters.findAllByOrderByStartDateDesc().stream()
                .map(s -> SemesterResponse.from(s, clock.instant(), counts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SemesterResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public SemesterResponse current() {
        return semesters.findByCurrentTrue()
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No semester is currently active."));
    }

    @Transactional
    public SemesterResponse create(SemesterRequest r) {
        Semester.Schedule schedule = validated(r, null);
        Semester s = semesters.save(new Semester(schedule));
        audit.record(AuditAction.CREATED, ENTITY, s.getId(), Map.of("code", s.getCode()));
        return toResponse(s);
    }

    @Transactional
    public SemesterResponse update(Long id, SemesterRequest r) {
        Semester s = load(id);
        if (s.isCompleted(clock.instant())) {
            throw ApiException.invalidState("Completed semesters cannot be edited.");
        }
        s.apply(validated(r, id));
        audit.record(AuditAction.UPDATED, ENTITY, id, Map.of("code", s.getCode()));
        return toResponse(s);
    }

    /** Makes this the single current semester; the partial unique index guarantees there is only one. */
    @Transactional
    public SemesterResponse makeCurrent(Long id) {
        Semester s = load(id);
        if (s.isCompleted(clock.instant())) {
            throw ApiException.invalidState("A completed semester cannot be made current.");
        }
        semesters.clearCurrentExcept(id);
        s.markCurrent(true);
        audit.record(AuditAction.SEMESTER_MADE_CURRENT, ENTITY, id, Map.of("code", s.getCode()));
        return toResponse(s);
    }

    @Transactional
    public SemesterResponse openRegistration(Long id) {
        Semester s = load(id);
        if (s.isCompleted(clock.instant())) {
            throw ApiException.invalidState("Registration cannot be opened for a completed semester.");
        }
        s.openRegistration();
        audit.record(AuditAction.REGISTRATION_OPENED, ENTITY, id, Map.of("code", s.getCode()));
        events.publishEvent(new RegistrationOpenedEvent(id, s.getName(), s.getRegistrationOpensAt(), s.getRegistrationClosesAt()));
        return toResponse(s);
    }

    @Transactional
    public SemesterResponse closeRegistration(Long id) {
        Semester s = load(id);
        s.closeRegistration();
        audit.record(AuditAction.REGISTRATION_CLOSED, ENTITY, id, Map.of("code", s.getCode()));
        return toResponse(s);
    }

    public Semester load(Long id) {
        return semesters.findById(id).orElseThrow(() -> ApiException.notFound(ENTITY, id));
    }

    private Semester.Schedule validated(SemesterRequest r, Long excludeId) {
        Semester.Schedule schedule = r.toSchedule();
        List<Map<String, String>> errors = schedule.validate();
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "One or more fields are invalid.").with("errors", errors);
        }
        if (semesters.codeTaken(r.code().trim(), excludeId)) {
            throw ApiException.duplicate("Semester code", r.code().trim().toUpperCase());
        }
        return schedule;
    }

    private SemesterResponse toResponse(Semester s) {
        return SemesterResponse.from(s, clock.instant(), offeringCounts().getOrDefault(s.getId(), 0L));
    }

    /** Offering counts per semester (JPQL by entity name keeps this package free of a compile-time cycle). */
    private Map<Long, Long> offeringCounts() {
        Map<Long, Long> counts = new HashMap<>();
        entityManager.createQuery("select o.semester.id, count(o) from CourseOffering o group by o.semester.id", Object[].class)
                .getResultList()
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }
}
