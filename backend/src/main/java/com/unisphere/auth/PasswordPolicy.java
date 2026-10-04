package com.unisphere.auth;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Password rules (requirement AUTH-3). Checked on the server for every password that is set; the
 * frontend mirrors the rules only to give early feedback.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    /** BCrypt only uses the first 72 bytes; reject longer input instead of silently truncating it. */
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static List<String> violations(String password, String email) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters long.");
            return problems;
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_LENGTH) {
            problems.add("Password must be at most " + MAX_LENGTH + " bytes long.");
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            problems.add("Password must contain at least one letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Password must contain at least one digit.");
        }
        if (password.isBlank() || !password.strip().equals(password)) {
            problems.add("Password must not start or end with whitespace.");
        }
        if (email != null) {
            String lower = password.toLowerCase(Locale.ROOT);
            String localPart = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (lower.equals(email.toLowerCase(Locale.ROOT)) || (localPart.length() >= 4 && lower.contains(localPart))) {
                problems.add("Password must not contain your email address.");
            }
        }
        return problems;
    }

    public static void validate(String password, String email) {
        List<String> problems = violations(password, email);
        if (!problems.isEmpty()) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD, String.join(" ", problems))
                    .with("violations", problems);
        }
    }
}
