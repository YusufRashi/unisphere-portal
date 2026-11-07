package com.unisphere.audit.dto;

import com.unisphere.audit.AuditAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {
    }

    /** {@code actor} is null for system actions and failed logins of unknown users. */
    public record AuditEntry(Long id, Instant createdAt, Actor actor, AuditAction action, String entityType, String entityId,
                             Map<String, Object> details, String ipAddress) {
    }

    public record Actor(Long id, String name, String email) {
    }

    /** What the viewer's filters can offer. */
    public record AuditFilters(List<AuditAction> actions, List<String> entityTypes) {
    }
}
