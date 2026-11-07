package com.unisphere.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.support.TestFixtures;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SmtpAccountMailerTest {

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    private SmtpAccountMailer mailer;

    @AfterEach
    void stop() throws InterruptedException {
        if (mailer != null) {
            mailer.destroy();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    static UniSphereProperties properties(String host, int port, String from) {
        UniSphereProperties p = TestFixtures.properties();
        return new UniSphereProperties(p.appBaseUrl(), p.universityName(), p.security(),
                new UniSphereProperties.Mail(false, host, port, null, null, UniSphereProperties.MailSecurity.NONE, from),
                p.bootstrapAdmin(), p.finance(), p.notifications());
    }

    private SmtpAccountMailer mailer(int port) {
        mailer = SmtpAccountMailer.create(properties("127.0.0.1", port, "UniSphere <no-reply@uni.test>"), Duration.ofMillis(10));
        return mailer;
    }

    private static User amina() {
        return new User("amina@uni.test", "Amina", "Otieno", Set.of(Role.STUDENT), Instant.now());
    }

    @Test
    void sendsAPlainTextEmailWithTheLink() throws Exception {
        mailer(smtp.getSmtp().getPort()).sendActivationLink(amina(), "https://portal.uni.test/activate?token=abc123");

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = smtp.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("amina@uni.test");
        assertThat(message.getFrom()[0].toString()).isEqualTo("UniSphere <no-reply@uni.test>");
        assertThat(message.getSubject()).isEqualTo("Activate your UniSphere University account");
        assertThat(message.getContentType()).startsWith("text/plain");
        assertThat(GreenMailUtil.getBody(message)).contains("Hello Amina", "https://portal.uni.test/activate?token=abc123", "72 hours");
    }

    @Test
    void waitsForTheTransactionToCommit() {
        SmtpAccountMailer m = mailer(smtp.getSmtp().getPort());
        TransactionSynchronizationManager.initSynchronization();

        m.sendPasswordResetLink(amina(), "https://portal.uni.test/reset-password?token=xyz");
        assertThat(smtp.waitForIncomingEmail(500, 1)).isFalse();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        assertThat(GreenMailUtil.getBody(smtp.getReceivedMessages()[0])).contains("30 minutes");
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        SmtpAccountMailer m = mailer(smtp.getSmtp().getPort());
        TransactionSynchronizationManager.initSynchronization();

        m.sendActivationLink(amina(), "https://portal.uni.test/activate?token=abc");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(smtp.waitForIncomingEmail(1000, 1)).isFalse();
    }

    @Test
    void anUnreachableServerNeverFailsTheCaller() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SmtpAccountMailer m = mailer(closedPort);

        m.sendActivationLink(amina(), "https://portal.uni.test/activate?token=abc");
        m.destroy(); // waits for the retries to finish
        mailer = null;

        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    void refusesToStartWithoutAValidSender() {
        assertThatThrownBy(() -> SmtpAccountMailer.create(properties("smtp.uni.test", 587, " "), Duration.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> SmtpAccountMailer.create(properties("smtp.uni.test", 587, "not an address"), Duration.ZERO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");
    }
}
