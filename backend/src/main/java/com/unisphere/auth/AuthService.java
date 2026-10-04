package com.unisphere.auth;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.auth.mail.AccountMailer;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.security.AccessTokenService;
import com.unisphere.common.security.AccessTokenService.AccessToken;
import com.unisphere.common.web.ClientInfo;
import com.unisphere.user.User;
import com.unisphere.user.UserRepository;
import com.unisphere.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases. Methods that both record state (failed-attempt counters, token-family
 * revocation, audit entries) <em>and</em> reject the request use {@code noRollbackFor = ApiException.class}
 * so the bookkeeping is committed even though the caller receives an error.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";
    /**
     * A refresh token presented again within this window after rotation is treated as a benign race
     * (e.g. two browser tabs refreshing at once) rather than as theft.
     */
    private static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(10);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccountTokenService accountTokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AccountMailer mailer;
    private final UniSphereProperties properties;
    private final Clock clock;
    /** Hash compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, AccountTokenService accountTokens,
                       AccessTokenService accessTokens, PasswordEncoder passwordEncoder, AuditService audit,
                       AccountMailer mailer, UniSphereProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accountTokens = accountTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Result of a successful authentication: the access token for the body, the refresh token for the cookie. */
    public record Session(User user, AccessToken accessToken, String refreshToken, Duration refreshTokenTtl) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String email, String password, ClientInfo client) {
        Instant now = clock.instant();
        User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);

        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            audit.recordAs(null, AuditAction.LOGIN_FAILED, "User", null, Map.of("reason", "unknown_email"));
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed sign-in attempts. Try again in a few minutes or reset your password.");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            if (user.getPasswordHash() == null) {
                passwordEncoder.matches(password, dummyHash);
            }
            var lockout = properties.security().lockout();
            user.registerFailedLogin(now, lockout.maxFailedAttempts(), lockout.lockDuration());
            boolean lockedNow = user.isLocked(now);
            audit.recordAs(user.getId(), lockedNow ? AuditAction.ACCOUNT_LOCKED : AuditAction.LOGIN_FAILED,
                    "User", user.getId(), Map.of("reason", "bad_password"));
            throw invalidCredentials();
        }
        // Status is only revealed after the correct password has been supplied.
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new ApiException(ErrorCode.ACCOUNT_DEACTIVATED,
                    "This account has been deactivated. Please contact the registrar's office.");
        }

        user.registerSuccessfulLogin(now);
        audit.recordAs(user.getId(), AuditAction.LOGIN_SUCCEEDED, "User", user.getId(), Map.of());
        return startSession(user, UUID.randomUUID(), client, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw sessionExpired();
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(AuthService::sessionExpired);

        if (token.isRevoked()) {
            boolean benignRace = token.getRevokedAt().plus(REUSE_GRACE_PERIOD).isAfter(now);
            if (!benignRace) {
                // A rotated token was replayed: assume it was stolen and kill every session in its family.
                refreshTokens.revokeFamily(token.getFamilyId(), now);
                audit.recordAs(token.getUser().getId(), AuditAction.REFRESH_TOKEN_REUSE_DETECTED, "User",
                        token.getUser().getId(), Map.of("family", token.getFamilyId().toString()));
                log.warn("Refresh token reuse detected for user {}", token.getUser().getId());
            }
            throw sessionExpired();
        }
        User user = token.getUser();
        if (token.isExpired(now) || !user.isActive() || token.getCreatedAt().isBefore(user.getSessionsValidAfter())) {
            token.revoke(now);
            throw sessionExpired();
        }

        token.revoke(now);
        return startSession(user, token.getFamilyId(), client, now);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            audit.recordAs(token.getUser().getId(), AuditAction.LOGOUT, "User", token.getUser().getId(), Map.of());
        });
    }

    @Transactional(readOnly = true)
    public User currentUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
    }

    @Transactional
    public void activate(String rawToken, String newPassword) {
        User user = accountTokens.consume(rawToken, AccountTokenPurpose.ACTIVATION);
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw ApiException.invalidState("This account is already activated or has been deactivated.");
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.activate(passwordEncoder.encode(newPassword));
        audit.recordAs(user.getId(), AuditAction.ACCOUNT_ACTIVATED, "User", user.getId(), Map.of());
    }

    /** Always succeeds from the caller's point of view, so it cannot be used to discover registered emails. */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(User.normalizeEmail(email))
                .filter(User::isActive)
                .ifPresent(user -> {
                    String token = accountTokens.issue(user, AccountTokenPurpose.PASSWORD_RESET);
                    mailer.sendPasswordResetLink(user, properties.appBaseUrl() + "/reset-password?token=" + token);
                    audit.recordAs(user.getId(), AuditAction.PASSWORD_RESET_REQUESTED, "User", user.getId(), Map.of());
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = accountTokens.consume(rawToken, AccountTokenPurpose.PASSWORD_RESET);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.INVALID_TOKEN, "This link is invalid or has expired. Please request a new one.");
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.recordAs(user.getId(), AuditAction.PASSWORD_RESET_COMPLETED, "User", user.getId(), Map.of());
    }

    /** Changes the password, signs out every other session and returns a fresh session for this client. */
    @Transactional
    public Session changePassword(Long userId, String currentPassword, String newPassword, ClientInfo client) {
        User user = currentUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD, "New password must be different from the current one.");
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.recordAs(user.getId(), AuditAction.PASSWORD_CHANGED, "User", user.getId(), Map.of());
        return startSession(user, UUID.randomUUID(), client, now);
    }

    private Session startSession(User user, UUID familyId, ClientInfo client, Instant now) {
        Duration ttl = properties.security().refreshTokenTtl();
        String raw = SecureTokens.generate();
        refreshTokens.save(new RefreshToken(user, SecureTokens.sha256(raw), familyId, now, now.plus(ttl),
                client.ipAddress(), client.userAgent()));
        return new Session(user, accessTokens.issue(user), raw, ttl);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }

    private static ApiException sessionExpired() {
        return new ApiException(ErrorCode.INVALID_TOKEN, "Your session has expired. Please sign in again.");
    }
}
