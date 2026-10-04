package com.unisphere.common.security;

import com.unisphere.user.UserRepository;
import com.unisphere.user.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Makes stateless access tokens revocable: a single primary-key lookup per request rejects tokens of
 * deactivated users and tokens issued before a password change / logout-everywhere.
 */
public class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "Session is no longer valid", null);

    private final UserRepository users;

    public ActiveSessionValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        Instant issuedAt = jwt.getIssuedAt();
        return users.findSessionState(userId)
                .filter(state -> state.status() == UserStatus.ACTIVE)
                // JWT "iat" has second precision, so compare at second precision.
                .filter(state -> issuedAt != null
                        && !issuedAt.isBefore(state.sessionsValidAfter().truncatedTo(ChronoUnit.SECONDS)))
                .map(state -> OAuth2TokenValidatorResult.success())
                .orElseGet(() -> OAuth2TokenValidatorResult.failure(REVOKED));
    }
}
