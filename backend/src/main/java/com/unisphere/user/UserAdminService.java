package com.unisphere.user;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.auth.RefreshTokenRepository;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import com.unisphere.user.dto.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative account operations. Creation of students/lecturers is added in Phase 2. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, UserStatus status, Pageable pageable) {
        Specification<User> spec = Specs.allOf(
                UserSpecifications.matchesQuery(q),
                UserSpecifications.hasRole(role),
                UserSpecifications.hasStatus(status));
        return PageResponse.of(users.findAll(spec, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id));
    }

    @Transactional
    public void deactivate(Long id, Long actingUserId) {
        if (id.equals(actingUserId)) {
            throw ApiException.invalidState("You cannot deactivate your own account.");
        }
        User user = load(id);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return; // idempotent
        }
        Instant now = clock.instant();
        user.deactivate(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record(AuditAction.USER_DEACTIVATED, "User", user.getId(), Map.of("email", user.getEmail()));
    }

    @Transactional
    public void reactivate(Long id) {
        User user = load(id);
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            return; // idempotent
        }
        user.reactivate();
        audit.record(AuditAction.USER_REACTIVATED, "User", user.getId(), Map.of("email", user.getEmail()));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }
}
