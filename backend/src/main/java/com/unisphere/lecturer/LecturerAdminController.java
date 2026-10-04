package com.unisphere.lecturer;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.lecturer.dto.LecturerDtos.CreateLecturerRequest;
import com.unisphere.lecturer.dto.LecturerDtos.LecturerResponse;
import com.unisphere.lecturer.dto.LecturerDtos.UpdateLecturerRequest;
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

@Tag(name = "Admin · Lecturers")
@RestController
@RequestMapping("/api/admin/lecturers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LecturerAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "staffNumber", "staffNumber",
            "lastName", "user.lastName",
            "department", "department.name");

    private final LecturerService lecturers;
    private final AccountProvisioningService provisioning;

    @GetMapping
    public PageResponse<LecturerResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORTABLE, Sort.by("user.lastName", "id"));
        return lecturers.search(q, departmentId, pageable);
    }

    @GetMapping("/{id}")
    public LecturerResponse get(@PathVariable Long id) {
        return lecturers.get(id);
    }

    @Operation(summary = "Create a lecturer account and email an activation link")
    @PostMapping
    public ResponseEntity<LecturerResponse> create(@Valid @RequestBody CreateLecturerRequest body) {
        LecturerResponse created = provisioning.createLecturer(body);
        return ResponseEntity.created(URI.create("/api/admin/lecturers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public LecturerResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLecturerRequest body) {
        return lecturers.update(id, body);
    }
}
