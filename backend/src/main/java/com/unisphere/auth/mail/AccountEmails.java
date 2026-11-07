package com.unisphere.auth.mail;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.User;
import java.time.Duration;

/**
 * The text of account emails. Plain text only: nothing a user typed is ever interpreted as markup, and the subjects
 * contain nothing a user typed at all.
 */
public final class AccountEmails {

    public record Email(String kind, String subject, String body) {
    }

    private final String university;
    private final Duration activationTtl;
    private final Duration resetTtl;

    public AccountEmails(UniSphereProperties properties) {
        // Configuration, not user input, but a line break must never reach a header.
        this.university = properties.universityName().replaceAll("[\\r\\n]+", " ").strip();
        this.activationTtl = properties.security().activationTokenTtl();
        this.resetTtl = properties.security().passwordResetTokenTtl();
    }

    public Email activation(User user, String link) {
        return new Email("activation", "Activate your %s account".formatted(university), """
                Hello %s,

                An account has been created for you on the %s portal.

                To activate it, choose your password here:
                %s

                The link works once and expires in %s. If it has expired, ask the registrar to send a new one.

                If you were not expecting this email, you can ignore it.
                """.formatted(user.getFirstName(), university, link, describe(activationTtl)));
    }

    public Email passwordReset(User user, String link) {
        return new Email("password reset", "Reset your %s password".formatted(university), """
                Hello %s,

                Someone asked to reset the password of your %s account. To choose a new password, open:
                %s

                The link works once and expires in %s.

                If you did not ask for this, ignore this email: your password stays the same.
                """.formatted(user.getFirstName(), university, link, describe(resetTtl)));
    }

    /** "72 hours", "30 minutes", "1 hour". */
    static String describe(Duration d) {
        if (d.toMinutes() % 60 != 0) {
            return plural(d.toMinutes(), "minute");
        }
        return plural(d.toHours(), "hour");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
