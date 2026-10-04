package com.unisphere.academic;

import com.unisphere.academic.dto.AcademicDtos.CourseDetail;
import com.unisphere.academic.dto.AcademicDtos.CourseRequest;
import com.unisphere.academic.dto.AcademicDtos.CourseSummary;
import com.unisphere.academic.dto.AcademicDtos.DepartmentRequest;
import com.unisphere.academic.dto.AcademicDtos.DepartmentResponse;
import com.unisphere.academic.dto.AcademicDtos.PrerequisitesRequest;
import com.unisphere.academic.dto.AcademicDtos.ProgramRequest;
import com.unisphere.academic.dto.AcademicDtos.ProgramResponse;
import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue endpoints. Reads are open to every signed-in user (students browse courses); writes live under
 * {@code /api/admin} and require the ADMIN role.
 */
@Tag(name = "Academic structure")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AcademicController {

    private static final Map<String, String> COURSE_SORTS = Map.of(
            "code", "code", "title", "title", "credits", "credits", "level", "level");

    private final DepartmentService departments;
    private final ProgramService programs;
    private final CourseService courses;

    // ---- Departments ----

    @Operation(summary = "List departments with program, course and lecturer counts")
    @GetMapping("/departments")
    public List<DepartmentResponse> listDepartments() {
        return departments.list();
    }

    @GetMapping("/departments/{id}")
    public DepartmentResponse getDepartment(@PathVariable Long id) {
        return departments.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest body) {
        DepartmentResponse created = departments.create(body);
        return ResponseEntity.created(URI.create("/api/departments/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/departments/{id}")
    public DepartmentResponse updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest body) {
        return departments.update(id, body);
    }

    @Operation(summary = "Delete an empty department (409 if it still has programs, courses or lecturers)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departments.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Programs ----

    @GetMapping("/programs")
    public List<ProgramResponse> listPrograms(@RequestParam(required = false) Long departmentId,
                                              @RequestParam(required = false) Boolean active) {
        return programs.list(departmentId, active);
    }

    @GetMapping("/programs/{id}")
    public ProgramResponse getProgram(@PathVariable Long id) {
        return programs.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/programs")
    public ResponseEntity<ProgramResponse> createProgram(@Valid @RequestBody ProgramRequest body) {
        ProgramResponse created = programs.create(body);
        return ResponseEntity.created(URI.create("/api/programs/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/programs/{id}")
    public ProgramResponse updateProgram(@PathVariable Long id, @Valid @RequestBody ProgramRequest body) {
        return programs.update(id, body);
    }

    // ---- Courses ----

    @Operation(summary = "Search the course catalogue")
    @GetMapping("/courses")
    public PageResponse<CourseSummary> searchCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, COURSE_SORTS, Sort.by("code", "id"));
        return courses.search(q, departmentId, level, active, pageable);
    }

    @GetMapping("/courses/{id}")
    public CourseDetail getCourse(@PathVariable Long id) {
        return courses.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/courses")
    public ResponseEntity<CourseDetail> createCourse(@Valid @RequestBody CourseRequest body) {
        CourseDetail created = courses.create(body);
        return ResponseEntity.created(URI.create("/api/courses/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/courses/{id}")
    public CourseDetail updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest body) {
        return courses.update(id, body);
    }

    @Operation(summary = "Replace a course's prerequisites (409 PREREQUISITE_CYCLE if it would create a cycle)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/courses/{id}/prerequisites")
    public CourseDetail replacePrerequisites(@PathVariable Long id, @Valid @RequestBody PrerequisitesRequest body) {
        return courses.replacePrerequisites(id, body.prerequisiteIds());
    }
}
