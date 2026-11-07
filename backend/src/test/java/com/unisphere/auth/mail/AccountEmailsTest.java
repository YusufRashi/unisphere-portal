package com.unisphere.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.support.TestFixtures;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountEmailsTest {

    private final AccountEmails emails = new AccountEmails(TestFixtures.properties());
    private final User user = new User("brian@uni.test", "Brian", "Mwangi", Set.of(Role.LECTURER), Instant.now());

    @Test
    void activationExplainsWhatToDoAndHowLongItLasts() {
        AccountEmails.Email email = emails.activation(user, "https://x/activate?token=t");
        assertThat(email.subject()).isEqualTo("Activate your UniSphere University account");
        assertThat(email.body()).contains("Hello Brian,", "https://x/activate?token=t", "expires in 72 hours", "ask the registrar");
    }

    @Test
    void resetReassuresSomeoneWhoDidNotAskForIt() {
        AccountEmails.Email email = emails.passwordReset(user, "https://x/reset-password?token=t");
        assertThat(email.subject()).isEqualTo("Reset your UniSphere University password");
        assertThat(email.body()).contains("expires in 30 minutes", "your password stays the same");
    }

    @Test
    void describesDurations() {
        assertThat(AccountEmails.describe(Duration.ofHours(72))).isEqualTo("72 hours");
        assertThat(AccountEmails.describe(Duration.ofHours(1))).isEqualTo("1 hour");
        assertThat(AccountEmails.describe(Duration.ofMinutes(30))).isEqualTo("30 minutes");
        assertThat(AccountEmails.describe(Duration.ofMinutes(90))).isEqualTo("90 minutes");
    }
}
