package com.unisphere.audit;

import com.unisphere.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit entries in the caller's transaction: if the business operation rolls back, so does its
 * audit entry (the action never happened). Never pass secrets (passwords, tokens) in {@code details}.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, Map<String, ?> details) {
        recordAs(CurrentUser.idOrNull(), action, entityType, entityId, details);
    }

    /** For actions where the actor is known but not yet authenticated (e.g. login). */
    @Transactional
    public void recordAs(Long actorId, AuditAction action, String entityType, Object entityId, Map<String, ?> details) {
        String json = details == null || details.isEmpty() ? null : jsonMapper.writeValueAsString(details);
        repository.save(new AuditLog(
                actorId,
                action,
                entityType,
                entityId == null ? null : String.valueOf(entityId),
                json,
                currentIp(),
                clock.instant()));
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
