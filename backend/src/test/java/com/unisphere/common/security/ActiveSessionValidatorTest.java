package com.unisphere.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unisphere.user.UserRepository;
import com.unisphere.user.UserRepository.SessionState;
import com.unisphere.user.UserStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ActiveSessionValidatorTest {

    private static final Instant INVALIDATED_AT = Instant.parse("2026-10-04T10:00:00.400Z");

    private final UserRepository users = mock(UserRepository.class);
    private final ActiveSessionValidator validator = new ActiveSessionValidator(users);

    @Test
    void acceptsTokenOfActiveUserIssuedAfterInvalidation() {
        when(users.findSessionState(7L)).thenReturn(Optional.of(new SessionState(UserStatus.ACTIVE, INVALIDATED_AT)));
        assertThat(validator.validate(jwt("7", INVALIDATED_AT.plusSeconds(5))).hasErrors()).isFalse();
    }

    @Test
    void acceptsTokenIssuedInTheSameSecondAsInvalidation() {
        // "iat" has whole-second precision; a token issued right after a password change must still work.
        when(users.findSessionState(7L)).thenReturn(Optional.of(new SessionState(UserStatus.ACTIVE, INVALIDATED_AT)));
        assertThat(validator.validate(jwt("7", Instant.parse("2026-10-04T10:00:00Z"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenIssuedBeforeSessionsWereInvalidated() {
        when(users.findSessionState(7L)).thenReturn(Optional.of(new SessionState(UserStatus.ACTIVE, INVALIDATED_AT)));
        assertThat(validator.validate(jwt("7", INVALIDATED_AT.minusSeconds(60))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenOfDeactivatedUser() {
        when(users.findSessionState(7L)).thenReturn(Optional.of(new SessionState(UserStatus.DEACTIVATED, INVALIDATED_AT)));
        assertThat(validator.validate(jwt("7", INVALIDATED_AT.plusSeconds(5))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenOfUnknownUser() {
        when(users.findSessionState(7L)).thenReturn(Optional.empty());
        assertThat(validator.validate(jwt("7", INVALIDATED_AT.plusSeconds(5))).hasErrors()).isTrue();
    }

    @Test
    void rejectsNonNumericSubject() {
        assertThat(validator.validate(jwt("admin", INVALIDATED_AT.plusSeconds(5))).hasErrors()).isTrue();
    }

    private static Jwt jwt(String subject, Instant issuedAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
