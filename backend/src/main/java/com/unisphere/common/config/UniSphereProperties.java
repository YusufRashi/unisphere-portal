package com.unisphere.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        /** Printed on transcripts and other documents. */
        @NotBlank String universityName,
        @NotNull @Valid Security security,
        @NotNull Mail mail,
        @NotNull BootstrapAdmin bootstrapAdmin,
        @NotNull @Valid Finance finance,
        @NotNull @Valid Notifications notifications) {

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

    /**
     * Account emails. With {@code host} blank they are not sent and {@link com.unisphere.auth.mail.LoggingAccountMailer} only logs
     * them; with it set they go out through that SMTP server, and {@code from} is required.
     *
     * @param logLinks development only: print the links in the log when no SMTP server is configured
     */
    public record Mail(boolean logLinks, String host, int port, String username, String password, MailSecurity security, String from) {

        public Mail {
            security = security == null ? MailSecurity.STARTTLS : security;
        }

        public boolean smtpEnabled() {
            return host != null && !host.isBlank();
        }
    }

    /** STARTTLS upgrades a plain connection (usually port 587); TLS connects encrypted (usually 465); NONE is for local test servers. */
    public enum MailSecurity { STARTTLS, TLS, NONE }

    /** {@code currency} is an ISO 4217 code used for every fee account (one currency per installation). */
    public record Finance(@NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code such as KES") String currency) {
    }

    /**
     * @param reminderCron              when the reminder job runs (Spring cron, UTC); "-" disables it
     * @param registrationDeadlineLead  how long before registration closes students without a submitted registration are reminded
     * @param feeReminderInterval       how often students with an outstanding balance are reminded
     */
    public record Notifications(@NotBlank String reminderCron, @NotNull Duration registrationDeadlineLead,
                                @NotNull Duration feeReminderInterval) {
    }

    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
