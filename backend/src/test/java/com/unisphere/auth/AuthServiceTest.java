package com.unisphere.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.auth.mail.AccountMailer;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.security.AccessTokenService;
import com.unisphere.common.security.AccessTokenService.AccessToken;
import com.unisphere.common.web.ClientInfo;
import com.unisphere.support.MutableClock;
import com.unisphere.support.TestFixtures;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import com.unisphere.user.UserRepository;
import com.unisphere.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String EMAIL = "student@uni.test";
    private static final String PASSWORD = "Correct-Horse-42";
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");

    // Low BCrypt cost keeps the unit tests fast; production uses cost 12.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T08:00:00Z"));
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AccountTokenService accountTokens = mock(AccountTokenService.class);
    private final AccessTokenService accessTokens = mock(AccessTokenService.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountMailer mailer = mock(AccountMailer.class);

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, accountTokens, accessTokens, encoder, audit, mailer,
                TestFixtures.properties(), clock);
        user = TestFixtures.activeUser(1L, EMAIL, encoder.encode(PASSWORD), clock.instant().minusSeconds(3600), Role.STUDENT);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(accessTokens.issue(any())).thenReturn(new AccessToken("jwt", clock.instant().plusSeconds(900), 900));
    }

    @Nested
    class Login {

        @Test
        void succeedsAndStoresOnlyTheHashOfTheRefreshToken() {
            AuthService.Session session = service.login(EMAIL, PASSWORD, CLIENT);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens).save(saved.capture());
            assertThat(session.accessToken().value()).isEqualTo("jwt");
            assertThat(saved.getValue().getTokenHash())
                    .isEqualTo(SecureTokens.sha256(session.refreshToken()))
                    .isNotEqualTo(session.refreshToken());
            assertThat(saved.getValue().getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
            assertThat(user.getLastLoginAt()).isEqualTo(clock.instant());
            verify(audit).recordAs(eq(1L), eq(AuditAction.LOGIN_SUCCEEDED), any(), any(), any());
        }

        @Test
        void normalisesEmailCaseAndWhitespace() {
            assertThat(service.login("  Student@UNI.test ", PASSWORD, CLIENT).user()).isSameAs(user);
        }

        @Test
        void unknownEmailAndWrongPasswordProduceTheSameError() {
            when(users.findByEmail("nobody@uni.test")).thenReturn(Optional.empty());

            ApiException unknown = catchApi(() -> service.login("nobody@uni.test", PASSWORD, CLIENT));
            ApiException wrong = catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT));

            assertThat(unknown.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(wrong.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
        }

        @Test
        void locksAccountAfterFiveFailedAttempts() {
            for (int i = 0; i < 5; i++) {
                catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT));
            }

            assertThat(user.isLocked(clock.instant())).isTrue();
            verify(audit).recordAs(eq(1L), eq(AuditAction.ACCOUNT_LOCKED), any(), any(), any());
            // Even the correct password is refused while locked.
            assertThat(catchApi(() -> service.login(EMAIL, PASSWORD, CLIENT)).getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        }

        @Test
        void lockExpiresAfterLockDuration() {
            for (int i = 0; i < 5; i++) {
                catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT));
            }
            clock.advance(Duration.ofMinutes(15).plusSeconds(1));

            assertThat(service.login(EMAIL, PASSWORD, CLIENT).user()).isSameAs(user);
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        void successfulLoginResetsFailedAttemptCounter() {
            catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT));
            catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT));
            service.login(EMAIL, PASSWORD, CLIENT);
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        void deactivatedAccountIsRevealedOnlyAfterCorrectPassword() {
            user.deactivate(clock.instant());

            assertThat(catchApi(() -> service.login(EMAIL, "Wrong-password-1", CLIENT)).getCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(catchApi(() -> service.login(EMAIL, PASSWORD, CLIENT)).getCode())
                    .isEqualTo(ErrorCode.ACCOUNT_DEACTIVATED);
            verify(refreshTokens, never()).save(any());
        }

        @Test
        void pendingAccountWithoutPasswordCannotLogIn() {
            User pending = new User("new@uni.test", "New", "User", java.util.Set.of(Role.STUDENT), clock.instant());
            when(users.findByEmail("new@uni.test")).thenReturn(Optional.of(pending));

            assertThat(catchApi(() -> service.login("new@uni.test", PASSWORD, CLIENT)).getCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    class Refresh {

        private RefreshToken existing;
        private final String raw = "raw-refresh-token";
        private final UUID family = UUID.randomUUID();

        @BeforeEach
        void storeToken() {
            existing = new RefreshToken(user, SecureTokens.sha256(raw), family, clock.instant(),
                    clock.instant().plus(Duration.ofDays(7)), "10.0.0.1", "JUnit");
            when(refreshTokens.findByTokenHash(SecureTokens.sha256(raw))).thenReturn(Optional.of(existing));
            clock.advance(Duration.ofMinutes(20));
        }

        @Test
        void rotatesTokenWithinTheSameFamily() {
            AuthService.Session session = service.refresh(raw, CLIENT);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokens).save(saved.capture());
            assertThat(existing.isRevoked()).isTrue();
            assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
            assertThat(session.refreshToken()).isNotEqualTo(raw);
        }

        @Test
        void replayOfRotatedTokenRevokesTheWholeFamily() {
            existing.revoke(clock.instant());
            clock.advance(Duration.ofMinutes(1));

            assertThat(catchApi(() -> service.refresh(raw, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(refreshTokens).revokeFamily(eq(family), any());
            verify(audit).recordAs(eq(1L), eq(AuditAction.REFRESH_TOKEN_REUSE_DETECTED), any(), any(), any());
        }

        @Test
        void concurrentRefreshWithinGracePeriodIsNotTreatedAsTheft() {
            existing.revoke(clock.instant());
            clock.advance(Duration.ofSeconds(2));

            assertThat(catchApi(() -> service.refresh(raw, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(refreshTokens, never()).revokeFamily(any(), any());
        }

        @Test
        void expiredTokenIsRejected() {
            clock.advance(Duration.ofDays(8));
            assertThat(catchApi(() -> service.refresh(raw, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
            verify(refreshTokens, never()).save(any());
        }

        @Test
        void tokenOfDeactivatedUserIsRejected() {
            user.deactivate(clock.instant());
            assertThat(catchApi(() -> service.refresh(raw, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        void tokenIssuedBeforePasswordChangeIsRejected() {
            user.changePassword(encoder.encode("Another-pass-99"), clock.instant());
            assertThat(catchApi(() -> service.refresh(raw, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        void unknownOrMissingTokenIsRejected() {
            assertThat(catchApi(() -> service.refresh("does-not-exist", CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
            assertThat(catchApi(() -> service.refresh(null, CLIENT)).getCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
        }
    }

    @Nested
    class PasswordReset {

        @Test
        void unknownEmailDoesNothingAndRaisesNoError() {
            when(users.findByEmail("nobody@uni.test")).thenReturn(Optional.empty());
            service.requestPasswordReset("nobody@uni.test");
            verifyNoInteractions(mailer, accountTokens);
        }

        @Test
        void activeUserReceivesResetLink() {
            when(accountTokens.issue(user, AccountTokenPurpose.PASSWORD_RESET)).thenReturn("reset-token");
            service.requestPasswordReset(EMAIL);
            verify(mailer).sendPasswordResetLink(user, "http://localhost:5173/reset-password?token=reset-token");
        }

        @Test
        void resetChangesPasswordAndRevokesAllSessions() {
            when(accountTokens.consume("reset-token", AccountTokenPurpose.PASSWORD_RESET)).thenReturn(user);

            service.resetPassword("reset-token", "Brand-new-pass-7");

            assertThat(encoder.matches("Brand-new-pass-7", user.getPasswordHash())).isTrue();
            assertThat(user.getSessionsValidAfter()).isEqualTo(clock.instant());
            verify(refreshTokens).revokeAllForUser(1L, clock.instant());
        }

        @Test
        void resetRejectsWeakPassword() {
            when(accountTokens.consume("reset-token", AccountTokenPurpose.PASSWORD_RESET)).thenReturn(user);
            assertThat(catchApi(() -> service.resetPassword("reset-token", "weak")).getCode()).isEqualTo(ErrorCode.WEAK_PASSWORD);
        }
    }

    @Nested
    class Activation {

        @Test
        void setsPasswordAndActivatesPendingAccount() {
            User pending = new User("new@uni.test", "New", "User", java.util.Set.of(Role.LECTURER), clock.instant());
            when(accountTokens.consume("activation", AccountTokenPurpose.ACTIVATION)).thenReturn(pending);

            service.activate("activation", "Lecturer-pass-1");

            assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(encoder.matches("Lecturer-pass-1", pending.getPasswordHash())).isTrue();
        }

        @Test
        void refusesToReactivateAlreadyActiveAccount() {
            when(accountTokens.consume("activation", AccountTokenPurpose.ACTIVATION)).thenReturn(user);
            assertThat(catchApi(() -> service.activate("activation", "Another-pass-1")).getCode()).isEqualTo(ErrorCode.INVALID_STATE);
        }
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        assertThat(catchApi(() -> service.changePassword(1L, "not-it-123", "Brand-new-pass-7", CLIENT)).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(refreshTokens, never()).revokeAllForUser(any(), any());
        verify(mailer, never()).sendPasswordResetLink(any(), anyString());
    }

    private static ApiException catchApi(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Expected ApiException");
    }
}
