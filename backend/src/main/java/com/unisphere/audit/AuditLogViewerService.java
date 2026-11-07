package com.unisphere.audit;

import com.unisphere.audit.dto.AuditDtos.Actor;
import com.unisphere.audit.dto.AuditDtos.AuditEntry;
import com.unisphere.audit.dto.AuditDtos.AuditFilters;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.user.User;
import com.unisphere.user.UserRepository;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The registrar's read-only view of the audit log (ADM-4). Nothing can change or remove an entry. */
@Service
@RequiredArgsConstructor
public class AuditLogViewerService {

    private final AuditLogRepository logs;
    private final UserRepository users;
    private final JsonMapper jsonMapper;

    /**
     * @param actor part of the actor's name or email
     * @param from  first day to include (UTC)
     * @param to    last day to include (UTC)
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> search(Long actorId, String actor, AuditAction action, String entityType, LocalDate from, LocalDate to,
                                           Pageable pageable) {
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.validation("to", "must be on or after the start date");
        }
        String pattern = Specs.containsPattern(actor);
        Specification<AuditLog> spec = Specs.allOf(
                actorId == null ? null : (r, q, cb) -> cb.equal(r.get("actorId"), actorId),
                pattern == null ? null : (r, q, cb) -> {
                    Subquery<Long> matching = q.subquery(Long.class);
                    var user = matching.from(User.class);
                    matching.select(user.get("id")).where(cb.or(
                            cb.like(cb.lower(user.get("email")), pattern, '\\'),
                            cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))), pattern, '\\')));
                    return r.get("actorId").in(matching);
                },
                action == null ? null : (r, q, cb) -> cb.equal(r.get("action"), action),
                entityType == null || entityType.isBlank() ? null : (r, q, cb) -> cb.equal(r.get("entityType"), entityType),
                from == null ? null : (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from.atStartOfDay(ZoneOffset.UTC).toInstant()),
                to == null ? null : (r, q, cb) -> cb.lessThan(r.get("createdAt"), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));

        Page<AuditLog> page = logs.findAll(spec, pageable);
        List<Long> actorIds = page.getContent().stream().map(AuditLog::getActorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> actors = users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, log -> entry(log, actors.get(log.getActorId())));
    }

    @Transactional(readOnly = true)
    public AuditFilters filters() {
        return new AuditFilters(Arrays.asList(AuditAction.values()), logs.distinctEntityTypes());
    }

    private AuditEntry entry(AuditLog log, User actor) {
        return new AuditEntry(log.getId(), log.getCreatedAt(),
                actor == null ? null : new Actor(actor.getId(), actor.getFullName(), actor.getEmail()),
                log.getAction(), log.getEntityType(), log.getEntityId(), details(log), log.getIpAddress());
    }

    private Map<String, Object> details(AuditLog log) {
        return log.getDetails() == null ? Map.of() : jsonMapper.readValue(log.getDetails(), new TypeReference<>() {
        });
    }
}
