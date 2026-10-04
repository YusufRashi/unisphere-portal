package com.unisphere.student;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.student.dto.StudentDtos.CreateStudentRequest;
import com.unisphere.student.dto.StudentDtos.StudentResponse;
import com.unisphere.student.dto.StudentDtos.UpdateStudentRequest;
import com.unisphere.user.AccountProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Students")
@RestController
@RequestMapping("/api/admin/students")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StudentAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "studentNumber", "studentNumber",
            "lastName", "user.lastName",
            "yearOfStudy", "yearOfStudy",
            "admissionDate", "admissionDate",
            "status", "status");

    private final StudentService students;
    private final AccountProvisioningService provisioning;

    @GetMapping
    public PageResponse<StudentResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) Integer yearOfStudy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORTABLE, Sort.by("studentNumber"));
        return students.search(q, programId, departmentId, status, yearOfStudy, pageable);
    }

    @GetMapping("/{id}")
    public StudentResponse get(@PathVariable Long id) {
        return students.get(id);
    }

    @Operation(summary = "Create a student account and email an activation link")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest body) {
        StudentResponse created = provisioning.createStudent(body);
        return ResponseEntity.created(URI.create("/api/admin/students/" + created.id())).body(created);
    }

    @Operation(summary = "Update program, year of study and academic status")
    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest body) {
        return students.update(id, body);
    }
}
