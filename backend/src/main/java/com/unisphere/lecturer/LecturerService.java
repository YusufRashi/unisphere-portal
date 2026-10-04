package com.unisphere.lecturer;

import com.unisphere.academic.DepartmentService;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.lecturer.dto.LecturerDtos.LecturerResponse;
import com.unisphere.lecturer.dto.LecturerDtos.UpdateLecturerRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LecturerService {

    private final LecturerRepository lecturers;
    private final DepartmentService departments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<LecturerResponse> search(String q, Long departmentId, Pageable pageable) {
        Specification<Lecturer> spec = Specs.allOf(matchesQuery(q),
                departmentId == null ? null : (r, cq, cb) -> cb.equal(r.get("department").get("id"), departmentId));
        return PageResponse.of(lecturers.findAll(spec, pageable), LecturerResponse::from);
    }

    @Transactional(readOnly = true)
    public LecturerResponse get(Long id) {
        return LecturerResponse.from(load(id));
    }

    @Transactional
    public LecturerResponse update(Long id, UpdateLecturerRequest r) {
        Lecturer l = load(id);
        l.update(departments.load(r.departmentId()), r.title(), r.office());
        audit.record(AuditAction.UPDATED, "Lecturer", id, Map.of("staffNumber", l.getStaffNumber()));
        return LecturerResponse.from(l);
    }

    public Lecturer load(Long id) {
        return lecturers.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Lecturer", id));
    }

    private static Specification<Lecturer> matchesQuery(String q) {
        String pattern = Specs.containsPattern(q);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> {
            var user = root.get("user");
            return cb.or(
                    cb.like(cb.lower(root.get("staffNumber")), pattern, '\\'),
                    cb.like(cb.lower(user.get("email")), pattern, '\\'),
                    cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))), pattern, '\\'),
                    cb.like(cb.lower(user.get("lastName")), pattern, '\\'));
        };
    }
}
