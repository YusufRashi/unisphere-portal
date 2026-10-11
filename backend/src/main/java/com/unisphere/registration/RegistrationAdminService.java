package com.unisphere.registration;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.offering.SeatService;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationDetail;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationSummary;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The registrar's view: the approval queue and decisions. */
@Service
@RequiredArgsConstructor
public class RegistrationAdminService {

    private final RegistrationRepository registrations;
    private final SemesterRepository semesters;
    private final SeatService seats;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<RegistrationSummary> search(Long semesterId, RegistrationStatus status, String q, Pageable pageable) {
        Long effectiveSemester = Optional.ofNullable(semesterId)
                .or(() -> semesters.findByCurrentTrue().map(Semester::getId))
                .orElse(null);
        if (effectiveSemester == null) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        String pattern = Specs.containsPattern(q);
        Specification<Registration> spec = Specs.allOf(
                (r, cq, cb) -> cb.equal(r.get("semester").get("id"), effectiveSemester),
                status == null ? null : (r, cq, cb) -> cb.equal(r.get("status"), status),
                pattern == null ? null : (r, cq, cb) -> {
                    var student = r.get("student");
                    var user = student.get("user");
                    return cb.or(
                            cb.like(cb.lower(student.get("studentNumber")), pattern, '\\'),
                            cb.like(cb.lower(user.get("email")), pattern, '\\'),
                            cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))), pattern, '\\'));
                });
        return PageResponse.of(registrations.findAll(spec, pageable), RegistrationMapper::summary);
    }

    @Transactional(readOnly = true)
    public RegistrationDetail get(Long id) {
        return detail(registrations.findById(id).orElseThrow(() -> ApiException.notFound("Registration", id)));
    }

    @Transactional
    public RegistrationDetail approve(Long id) {
        Registration r = lockPending(id);
        r.approve(CurrentUser.id(), clock.instant());
        audit.record(AuditAction.REGISTRATION_APPROVED, "Registration", id,
                Map.of("student", r.getStudent().getStudentNumber(), "credits", r.totalCredits()));
        publishStatus(r, null);
        return detail(r);
    }

    /** Rejecting gives the seats back so other students can take them; the student can edit and resubmit. */
    @Transactional
    public RegistrationDetail reject(Long id, String reason) {
        Registration r = lockPending(id);
        Instant now = clock.instant();
        r.activeItems().stream().filter(RegistrationItem::isSeatReserved).forEach(item -> {
            seats.release(item.getOffering().getId());
            item.markSeatReserved(false);
        });
        r.reject(CurrentUser.id(), now, reason.trim());
        audit.record(AuditAction.REGISTRATION_REJECTED, "Registration", id,
                Map.of("student", r.getStudent().getStudentNumber(), "reason", reason.trim()));
        publishStatus(r, reason.trim());
        return detail(r);
    }

    private void publishStatus(Registration r, String reason) {
        events.publishEvent(new RegistrationStatusChangedEvent(r.getStudent().getUser().getId(), r.getSemester().getName(), r.getStatus(), reason));
    }

    private Registration lockPending(Long id) {
        Registration r = registrations.lockById(id).orElseThrow(() -> ApiException.notFound("Registration", id));
        if (r.getStatus() != RegistrationStatus.SUBMITTED) {
            throw ApiException.invalidState("Only registrations awaiting approval can be approved or rejected (this one is %s)."
                    .formatted(r.getStatus().name().toLowerCase()));
        }
        return r;
    }

    private static RegistrationDetail detail(Registration r) {
        return new RegistrationDetail(RegistrationMapper.summary(r),
                r.activeItems().stream().map(RegistrationMapper::item).toList(),
                r.getItems().stream().filter(i -> !i.isActive()).map(RegistrationMapper::item).toList(),
                r.getRejectionReason(), r.getSemester().getMinCredits(), r.getSemester().getMaxCredits());
    }
}
