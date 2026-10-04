package com.unisphere.student;

import com.unisphere.academic.ProgramService;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.student.dto.StudentDtos.StudentResponse;
import com.unisphere.student.dto.StudentDtos.UpdateStudentRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository students;
    private final ProgramService programs;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> search(String q, Long programId, Long departmentId, StudentStatus status,
                                                Integer yearOfStudy, Pageable pageable) {
        Specification<Student> spec = Specs.allOf(matchesQuery(q),
                programId == null ? null : (r, cq, cb) -> cb.equal(r.get("program").get("id"), programId),
                departmentId == null ? null : (r, cq, cb) -> cb.equal(r.get("program").get("department").get("id"), departmentId),
                status == null ? null : (r, cq, cb) -> cb.equal(r.get("status"), status),
                yearOfStudy == null ? null : (r, cq, cb) -> cb.equal(r.get("yearOfStudy"), yearOfStudy));
        return PageResponse.of(students.findAll(spec, pageable), StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentResponse.from(load(id));
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest r) {
        Student s = load(id);
        s.updateAcademicRecord(programs.load(r.programId()), r.yearOfStudy(), r.status());
        audit.record(AuditAction.UPDATED, "Student", id, Map.of(
                "studentNumber", s.getStudentNumber(), "program", s.getProgram().getCode(), "status", s.getStatus().name()));
        return StudentResponse.from(s);
    }

    private Student load(Long id) {
        return students.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Student", id));
    }

    private static Specification<Student> matchesQuery(String q) {
        String pattern = Specs.containsPattern(q);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> {
            var user = root.get("user");
            return cb.or(
                    cb.like(cb.lower(root.get("studentNumber")), pattern, '\\'),
                    cb.like(cb.lower(user.get("email")), pattern, '\\'),
                    cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))), pattern, '\\'),
                    cb.like(cb.lower(user.get("lastName")), pattern, '\\'));
        };
    }
}
