package com.unisphere.auth.mail;

import com.unisphere.auth.mail.AccountEmails.Email;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends account emails through an SMTP server (any provider: SES, SendGrid, Mailgun, Microsoft 365, …).
 *
 * <p>Emails go out only after the transaction that created the link commits, so a rolled-back action never sends
 * one, and on a background thread with a few retries, so a slow or unavailable mail server never delays or fails
 * the request. That also keeps "forgot password" equally fast whether or not the address has an account.
 * A failure is logged without the link; the registrar can resend an activation link from the user's record.
 */
public class SmtpAccountMailer implements AccountMailer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailer.class);
    private static final int ATTEMPTS = 3;
    private static final int TIMEOUT_MS = 10_000;

    private final JavaMailSender sender;
    private final InternetAddress from;
    private final AccountEmails emails;
    private final ExecutorService executor;
    private final Duration retryDelay;

    SmtpAccountMailer(JavaMailSender sender, InternetAddress from, AccountEmails emails, ExecutorService executor, Duration retryDelay) {
        this.sender = sender;
        this.from = from;
        this.emails = emails;
        this.executor = executor;
        this.retryDelay = retryDelay;
    }

    /** Builds the mailer from {@code unisphere.mail.*}, refusing to start without a valid sender address. */
    public static SmtpAccountMailer create(UniSphereProperties properties, Duration retryDelay) {
        UniSphereProperties.Mail mail = properties.mail();
        InternetAddress from = sender(mail.from());
        JavaMailSenderImpl smtp = new JavaMailSenderImpl();
        smtp.setHost(mail.host().strip());
        smtp.setPort(mail.port());
        smtp.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = smtp.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        if (mail.username() != null && !mail.username().isBlank()) {
            smtp.setUsername(mail.username());
            smtp.setPassword(mail.password());
            props.put("mail.smtp.auth", "true");
        }
        switch (mail.security()) {
            case STARTTLS -> {
                // Required, not just enabled: never fall back to sending links in the clear.
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case TLS -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> log.warn("Account emails are sent WITHOUT encryption (MAIL_SECURITY=NONE). Use this only with a local test server.");
        }
        log.info("Account emails are sent through {}:{} ({}) from {}", mail.host().strip(), mail.port(), mail.security(), from.getAddress());
        return new SmtpAccountMailer(smtp, from, new AccountEmails(properties), newExecutor(), retryDelay);
    }

    private static InternetAddress sender(String from) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("MAIL_HOST is set, so MAIL_FROM is required: the sender of account emails, "
                    + "e.g. \"UniSphere <no-reply@university.edu>\".");
        }
        try {
            InternetAddress address = new InternetAddress(from.strip(), true);
            address.validate();
            return address;
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM is not a valid email address: " + e.getMessage(), e);
        }
    }

    /** Two senders at most, a bounded queue, and daemon threads so a stuck server cannot keep the JVM alive. */
    private static ExecutorService newExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), r -> {
            Thread t = new Thread(r, "account-mail-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void sendActivationLink(User user, String link) {
        dispatch(user, emails.activation(user, link));
    }

    @Override
    public void sendPasswordResetLink(User user, String link) {
        dispatch(user, emails.passwordReset(user, link));
    }

    private void dispatch(User user, Email email) {
        Long userId = user.getId();
        String to = user.getEmail();
        Runnable task = () -> deliver(userId, to, email);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(task, userId, email);
                }
            });
        } else {
            submit(task, userId, email);
        }
    }

    private void submit(Runnable task, Long userId, Email email) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.error("Could not queue the {} email for user {}: too many waiting or shutting down", email.kind(), userId);
        }
    }

    private void deliver(Long userId, String to, Email email) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(email.subject());
                helper.setText(email.body(), false);
                sender.send(message);
                log.info("Sent the {} email to user {}", email.kind(), userId);
                return;
            } catch (MailException | MessagingException e) {
                if (attempt == ATTEMPTS) {
                    // The message names the server problem; the link itself is never logged.
                    log.error("Could not send the {} email to user {} after {} attempts: {}", email.kind(), userId, ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Sending the {} email to user {} failed (attempt {} of {}), retrying: {}", email.kind(), userId, attempt, ATTEMPTS, e.getMessage());
                try {
                    Thread.sleep(retryDelay.multipliedBy(attempt).toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** On shutdown, emails already queued get a short while to go out. */
    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("Shutting down with account emails still unsent");
            executor.shutdownNow();
        }
    }
}
