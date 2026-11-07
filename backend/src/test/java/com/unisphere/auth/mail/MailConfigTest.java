package com.unisphere.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Exactly one mailer exists, chosen by whether MAIL_HOST is set. */
class MailConfigTest {

    private ApplicationContextRunner runner(UniSphereProperties properties) {
        return new ApplicationContextRunner()
                .withBean(UniSphereProperties.class, () -> properties)
                .withUserConfiguration(MailConfig.class, LoggingAccountMailer.class);
    }

    @Test
    void logsOnlyWhenNoSmtpServerIsSet() {
        runner(TestFixtures.properties()).run(context -> {
            assertThat(context).hasSingleBean(AccountMailer.class);
            assertThat(context).hasSingleBean(LoggingAccountMailer.class);
        });
    }

    @Test
    void sendsThroughSmtpWhenAHostIsSet() {
        runner(SmtpAccountMailerTest.properties("smtp.uni.test", 587, "no-reply@uni.test"))
                .withPropertyValues("unisphere.mail.host=smtp.uni.test")
                .run(context -> {
                    assertThat(context).hasSingleBean(AccountMailer.class);
                    assertThat(context).hasSingleBean(SmtpAccountMailer.class);
                });
    }

    @Test
    void refusesToStartWithAHostButNoSender() {
        runner(SmtpAccountMailerTest.properties("smtp.uni.test", 587, ""))
                .withPropertyValues("unisphere.mail.host=smtp.uni.test")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("MAIL_FROM"));
    }
}
