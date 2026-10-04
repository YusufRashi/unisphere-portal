package com.unisphere.academic;

import com.unisphere.academic.dto.AcademicDtos.DepartmentRequest;
import com.unisphere.academic.dto.AcademicDtos.DepartmentResponse;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private static final String ENTITY = "Department";

    private final DepartmentRepository departments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        Map<Long, Long> programs = toMap(departments.countProgramsByDepartment());
        Map<Long, Long> courses = toMap(departments.countCoursesByDepartment());
        Map<Long, Long> lecturers = toMap(departments.countLecturersByDepartment());
        return departments.findAllByOrderByNameAsc().stream()
                .map(d -> toResponse(d, programs, courses, lecturers))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        Department d = load(id);
        return toResponse(d, toMap(departments.countProgramsByDepartment()),
                toMap(departments.countCoursesByDepartment()), toMap(departments.countLecturersByDepartment()));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        checkUnique(request, null);
        Department d = departments.save(new Department(request.code(), request.name(), request.faculty()));
        audit.record(AuditAction.CREATED, ENTITY, d.getId(), Map.of("code", d.getCode()));
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getFaculty(), 0, 0, 0);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department d = load(id);
        checkUnique(request, id);
        d.update(request.code(), request.name(), request.faculty());
        audit.record(AuditAction.UPDATED, ENTITY, id, Map.of("code", d.getCode()));
        return get(id);
    }

    /** Only empty departments can be deleted; otherwise the history they anchor would be lost. */
    @Transactional
    public void delete(Long id) {
        DepartmentResponse current = get(id);
        if (current.programCount() + current.courseCount() + current.lecturerCount() > 0) {
            throw ApiException.inUse("%s still has %d program(s), %d course(s) and %d lecturer(s). Move or remove them first."
                    .formatted(current.code(), current.programCount(), current.courseCount(), current.lecturerCount()));
        }
        departments.deleteById(id);
        audit.record(AuditAction.DELETED, ENTITY, id, Map.of("code", current.code()));
    }

    public Department load(Long id) {
        return departments.findById(id).orElseThrow(() -> ApiException.notFound(ENTITY, id));
    }

    private void checkUnique(DepartmentRequest request, Long excludeId) {
        if (departments.codeTaken(request.code().trim(), excludeId)) {
            throw ApiException.duplicate("Department code", request.code().trim().toUpperCase());
        }
        if (departments.nameTaken(request.name().trim(), excludeId)) {
            throw ApiException.duplicate("Department", request.name().trim());
        }
    }

    private static DepartmentResponse toResponse(Department d, Map<Long, Long> programs, Map<Long, Long> courses,
                                                 Map<Long, Long> lecturers) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getFaculty(),
                programs.getOrDefault(d.getId(), 0L), courses.getOrDefault(d.getId(), 0L),
                lecturers.getOrDefault(d.getId(), 0L));
    }

    static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        rows.forEach(r -> map.put((Long) r[0], (Long) r[1]));
        return map;
    }
}
