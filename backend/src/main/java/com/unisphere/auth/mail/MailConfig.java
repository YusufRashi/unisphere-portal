package com.unisphere.auth.mail;

import com.unisphere.common.config.UniSphereProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the account mailer: SMTP when {@code MAIL_HOST} is set, otherwise {@link LoggingAccountMailer}. */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    static final String SMTP_ENABLED = "!'${unisphere.mail.host:}'.isBlank()";
    static final String SMTP_DISABLED = "'${unisphere.mail.host:}'.isBlank()";

    @Bean
    @ConditionalOnExpression(SMTP_ENABLED)
    SmtpAccountMailer smtpAccountMailer(UniSphereProperties properties) {
        return SmtpAccountMailer.create(properties, Duration.ofSeconds(2));
    }
}
