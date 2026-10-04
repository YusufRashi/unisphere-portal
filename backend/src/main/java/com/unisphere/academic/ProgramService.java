package com.unisphere.academic;

import com.unisphere.academic.dto.AcademicDtos.ProgramRequest;
import com.unisphere.academic.dto.AcademicDtos.ProgramResponse;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Programs are never deleted (students reference them); they are deactivated instead. */
@Service
@RequiredArgsConstructor
public class ProgramService {

    private static final String ENTITY = "Program";

    private final ProgramRepository programs;
    private final DepartmentService departments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ProgramResponse> list(Long departmentId, Boolean active) {
        Map<Long, Long> students = DepartmentService.toMap(programs.countStudentsByProgram());
        return programs.search(departmentId, active).stream()
                .map(p -> ProgramResponse.from(p, students.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramResponse get(Long id) {
        Program p = load(id);
        return ProgramResponse.from(p, DepartmentService.toMap(programs.countStudentsByProgram()).getOrDefault(id, 0L));
    }

    @Transactional
    public ProgramResponse create(ProgramRequest r) {
        checkUnique(r.code(), null);
        Department department = departments.load(r.departmentId());
        Program p = programs.save(new Program(department, r.code(), r.name(), r.awardLevel(), r.durationYears()));
        if (Boolean.FALSE.equals(r.active())) {
            p.update(department, r.code(), r.name(), r.awardLevel(), r.durationYears(), false);
        }
        audit.record(AuditAction.CREATED, ENTITY, p.getId(), Map.of("code", p.getCode()));
        return ProgramResponse.from(p, 0);
    }

    @Transactional
    public ProgramResponse update(Long id, ProgramRequest r) {
        Program p = load(id);
        checkUnique(r.code(), id);
        p.update(departments.load(r.departmentId()), r.code(), r.name(), r.awardLevel(), r.durationYears(),
                r.active() == null || r.active());
        audit.record(AuditAction.UPDATED, ENTITY, id, Map.of("code", p.getCode(), "active", p.isActive()));
        return get(id);
    }

    public Program load(Long id) {
        return programs.findById(id).orElseThrow(() -> ApiException.notFound(ENTITY, id));
    }

    private void checkUnique(String code, Long excludeId) {
        if (programs.codeTaken(code.trim(), excludeId)) {
            throw ApiException.duplicate("Program code", code.trim().toUpperCase());
        }
    }
}
