package com.unisphere.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private static final String EMAIL = "jane.doe@uni.test";

    @Test
    void acceptsPasswordMeetingAllRules() {
        assertThat(PasswordPolicy.violations("Correct-Horse-42", EMAIL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short1", "abcdefghi"})
    void rejectsPasswordsShorterThanMinimum(String password) {
        assertThat(PasswordPolicy.violations(password, EMAIL))
                .anyMatch(v -> v.contains("at least 10 characters"));
    }

    @Test
    void requiresAtLeastOneDigit() {
        assertThat(PasswordPolicy.violations("OnlyLettersHere", EMAIL)).containsExactly("Password must contain at least one digit.");
    }

    @Test
    void requiresAtLeastOneLetter() {
        assertThat(PasswordPolicy.violations("1234567890123", EMAIL)).containsExactly("Password must contain at least one letter.");
    }

    @Test
    void rejectsPasswordContainingEmailLocalPart() {
        assertThat(PasswordPolicy.violations("Jane.Doe-2026!", EMAIL)).contains("Password must not contain your email address.");
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        assertThat(PasswordPolicy.violations("a1".repeat(40), EMAIL)).contains("Password must be at most 72 bytes long.");
    }

    @Test
    void rejectsLeadingOrTrailingWhitespace() {
        assertThat(PasswordPolicy.violations(" Password123", EMAIL)).contains("Password must not start or end with whitespace.");
    }

    @Test
    void validateThrowsWeakPasswordWithAllViolations() {
        assertThatThrownBy(() -> PasswordPolicy.validate("NoDigitsAtAll", EMAIL))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.WEAK_PASSWORD);
                    assertThat(api.getProperties()).containsKey("violations");
                });
    }
}
