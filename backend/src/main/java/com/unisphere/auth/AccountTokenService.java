package com.unisphere.auth;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and consumes single-use activation / password-reset tokens. */
@Service
public class AccountTokenService {

    private final AccountTokenRepository repository;
    private final UniSphereProperties.Security settings;
    private final Clock clock;

    public AccountTokenService(AccountTokenRepository repository, UniSphereProperties properties, Clock clock) {
        this.repository = repository;
        this.settings = properties.security();
        this.clock = clock;
    }

    /** @return the raw token, to be delivered to the user exactly once (e.g. in an email link). */
    @Transactional
    public String issue(User user, AccountTokenPurpose purpose) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(user.getId(), purpose, now);
        String raw = SecureTokens.generate();
        repository.save(new AccountToken(user, SecureTokens.sha256(raw), purpose, now, now.plus(ttl(purpose))));
        return raw;
    }

    /** Validates and burns the token, returning its user. Unknown, used and expired tokens look identical. */
    @Transactional
    public User consume(String rawToken, AccountTokenPurpose purpose) {
        Instant now = clock.instant();
        AccountToken token = repository.findByHashAndPurpose(SecureTokens.sha256(rawToken), purpose)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN,
                        "This link is invalid or has expired. Please request a new one."));
        token.markUsed(now);
        return token.getUser();
    }

    private Duration ttl(AccountTokenPurpose purpose) {
        return switch (purpose) {
            case ACTIVATION -> settings.activationTokenTtl();
            case PASSWORD_RESET -> settings.passwordResetTokenTtl();
        };
    }
}
