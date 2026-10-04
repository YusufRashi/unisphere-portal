package com.unisphere.registration;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.registration.dto.RegistrationDtos.AddCourseRequest;
import com.unisphere.registration.dto.RegistrationDtos.CatalogueEntry;
import com.unisphere.registration.dto.RegistrationDtos.HistoryEntry;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationDetail;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationSummary;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationView;
import com.unisphere.registration.dto.RegistrationDtos.RejectRequest;
import com.unisphere.registration.dto.RegistrationDtos.Timetable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course registration")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegistrationController {

    private static final Map<String, String> CATALOGUE_SORTS = Map.of("code", "course.code", "title", "course.title",
            "level", "course.level");
    private static final Map<String, String> ADMIN_SORTS = Map.of("submittedAt", "submittedAt", "status", "status",
            "studentNumber", "student.studentNumber");

    private final StudentRegistrationService studentService;
    private final RegistrationAdminService adminService;

    // ---- Student ----

    @Operation(summary = "My registration for the current semester, with what I can do right now")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/registrations/me/current")
    public RegistrationView current() {
        return studentService.currentRegistration();
    }

    @Operation(summary = "Current semester's offerings with my eligibility for each")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/registrations/me/catalogue")
    public PageResponse<CatalogueEntry> catalogue(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, CATALOGUE_SORTS, Sort.by("course.code", "section", "id"));
        return studentService.catalogue(q, departmentId, level, pageable);
    }

    @Operation(summary = "Add a course (409/422 with a specific code when a registration rule is broken)")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/registrations/me/current/items")
    public RegistrationView addCourse(@Valid @RequestBody AddCourseRequest body) {
        return studentService.addCourse(body.offeringId());
    }

    @Operation(summary = "Remove a course from a draft, or drop it after submission (before the add/drop deadline)")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/registrations/me/current/items/{itemId}")
    public RegistrationView removeCourse(@PathVariable Long itemId) {
        return studentService.removeCourse(itemId);
    }

    @Operation(summary = "Submit the registration and reserve seats (all or nothing)")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/registrations/me/current/submit")
    public RegistrationView submit() {
        return studentService.submit();
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/registrations/me")
    public List<HistoryEntry> history() {
        return studentService.history();
    }

    @Operation(summary = "My weekly timetable for the current semester")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/students/me/timetable")
    public Timetable timetable() {
        return studentService.timetable();
    }

    // ---- Registrar ----

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/registrations")
    public PageResponse<RegistrationSummary> search(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, ADMIN_SORTS,
                Sort.by(Sort.Order.desc("submittedAt").nullsLast(), Sort.Order.asc("id")));
        return adminService.search(semesterId, status, q, pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/registrations/{id}")
    public RegistrationDetail get(@PathVariable Long id) {
        return adminService.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/registrations/{id}/approve")
    public RegistrationDetail approve(@PathVariable Long id) {
        return adminService.approve(id);
    }

    @Operation(summary = "Reject with a reason; the student's seats are released")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/registrations/{id}/reject")
    public RegistrationDetail reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body) {
        return adminService.reject(id, body.reason());
    }
}
