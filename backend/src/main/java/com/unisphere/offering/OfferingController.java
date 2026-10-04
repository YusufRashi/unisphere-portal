package com.unisphere.offering;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.offering.dto.OfferingDtos.OfferingRequest;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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

@Tag(name = "Course offerings")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfferingController {

    private static final Map<String, String> SORTABLE = Map.of(
            "code", "course.code",
            "title", "course.title",
            "level", "course.level",
            "capacity", "capacity",
            "enrolled", "enrolledCount");

    private final OfferingService service;

    @Operation(summary = "Offerings of a semester (defaults to the current semester)")
    @GetMapping("/offerings")
    public PageResponse<OfferingResponse> search(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Long lecturerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, SORTABLE, Sort.by("course.code", "section", "id"));
        return service.search(semesterId, q, departmentId, level, lecturerId, pageable);
    }

    @GetMapping("/offerings/{id}")
    public OfferingResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Create an offering (409 SCHEDULE_CONFLICT on venue or lecturer clashes)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/offerings")
    public ResponseEntity<OfferingResponse> create(@Valid @RequestBody OfferingRequest body) {
        OfferingResponse created = service.create(body);
        return ResponseEntity.created(URI.create("/api/offerings/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/offerings/{id}")
    public OfferingResponse update(@PathVariable Long id, @Valid @RequestBody OfferingRequest body) {
        return service.update(id, body);
    }

    @Operation(summary = "Delete an offering with no enrolled students")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/offerings/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
