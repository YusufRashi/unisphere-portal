package com.unisphere.auth.mail;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Used when no SMTP server is configured: nothing is sent. Links contain secrets, so they are only written to the
 * log when {@code MAIL_LOG_LINKS=true} (local development); otherwise only the event is logged.
 */
@Component
@ConditionalOnExpression(MailConfig.SMTP_DISABLED)
public class LoggingAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingAccountMailer.class);

    private final boolean logLinks;

    public LoggingAccountMailer(UniSphereProperties properties) {
        this.logLinks = properties.mail().logLinks();
    }

    @Override
    public void sendActivationLink(User user, String link) {
        send(user, "activation", link);
    }

    @Override
    public void sendPasswordResetLink(User user, String link) {
        send(user, "password reset", link);
    }

    private void send(User user, String kind, String link) {
        if (logLinks) {
            log.info("[DEV MAIL] {} link for user {}: {}", kind, user.getId(), link);
        } else {
            log.info("[MAIL] {} email queued for user {} (link hidden; set MAIL_LOG_LINKS=true in development)",
                    kind, user.getId());
        }
    }
}
