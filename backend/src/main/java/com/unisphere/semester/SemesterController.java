package com.unisphere.semester;

import com.unisphere.semester.dto.SemesterDtos.SemesterRequest;
import com.unisphere.semester.dto.SemesterDtos.SemesterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Semesters")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService service;

    @GetMapping("/semesters")
    public List<SemesterResponse> list() {
        return service.list();
    }

    @Operation(summary = "The current semester (404 if none is set)")
    @GetMapping("/semesters/current")
    public SemesterResponse current() {
        return service.current();
    }

    @GetMapping("/semesters/{id}")
    public SemesterResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/semesters")
    public ResponseEntity<SemesterResponse> create(@Valid @RequestBody SemesterRequest body) {
        SemesterResponse created = service.create(body);
        return ResponseEntity.created(URI.create("/api/semesters/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/semesters/{id}")
    public SemesterResponse update(@PathVariable Long id, @Valid @RequestBody SemesterRequest body) {
        return service.update(id, body);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/semesters/{id}/make-current")
    public SemesterResponse makeCurrent(@PathVariable Long id) {
        return service.makeCurrent(id);
    }

    @Operation(summary = "Allow registration (students can register only inside the registration window)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/semesters/{id}/registration/open")
    public SemesterResponse openRegistration(@PathVariable Long id) {
        return service.openRegistration(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/semesters/{id}/registration/close")
    public SemesterResponse closeRegistration(@PathVariable Long id) {
        return service.closeRegistration(id);
    }
}
