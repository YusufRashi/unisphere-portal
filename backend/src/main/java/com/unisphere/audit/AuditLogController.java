package com.unisphere.audit;

import com.unisphere.audit.dto.AuditDtos.AuditEntry;
import com.unisphere.audit.dto.AuditDtos.AuditFilters;
import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit log")
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditLogViewerService viewer;

    @Operation(summary = "Audit entries, newest first; filter by actor, action, entity type and date range (inclusive, UTC)")
    @GetMapping
    public PageResponse<AuditEntry> search(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return viewer.search(actorId, actor, action, entityType, from, to, PageableFactory.of(page, size, null, Map.of(), NEWEST_FIRST));
    }

    @Operation(summary = "The actions and entity types the filters can offer")
    @GetMapping("/filters")
    public AuditFilters filters() {
        return viewer.filters();
    }
}
