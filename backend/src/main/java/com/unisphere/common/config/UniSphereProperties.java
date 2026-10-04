package com.unisphere.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed application settings bound from the {@code unisphere.*} namespace.
 * Validated at startup so that misconfiguration (e.g. a missing JWT secret) fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "unisphere")
public record UniSphereProperties(
        @NotBlank String appBaseUrl,
        @NotNull @Valid Security security,
        @NotNull Mail mail,
        @NotNull BootstrapAdmin bootstrapAdmin) {

    public record Security(
            @NotNull @Valid Jwt jwt,
            @NotNull Duration refreshTokenTtl,
            boolean cookieSecure,
            List<String> corsAllowedOrigins,
            @NotNull @Valid Lockout lockout,
            @NotNull @Valid RateLimit rateLimit,
            @NotNull Duration activationTokenTtl,
            @NotNull Duration passwordResetTokenTtl) {

        public Security {
            corsAllowedOrigins = corsAllowedOrigins == null
                    ? List.of()
                    : corsAllowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(@Min(1) int authRequestsPerMinute) {
    }

    public record Mail(boolean logLinks) {
    }

    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
