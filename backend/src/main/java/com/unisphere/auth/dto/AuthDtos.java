package com.unisphere.auth.dto;

import com.unisphere.user.Role;
import com.unisphere.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response bodies for {@code /api/auth}. Grouped in one file because they are small and cohesive. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=****]";
        }
    }

    public record ActivateAccountRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "ActivateAccountRequest[****]";
        }
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[****]";
        }
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(max = 200) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[****]";
        }
    }

    public record CurrentUserResponse(Long id, String email, String firstName, String lastName, List<Role> roles) {

        public static CurrentUserResponse from(User user) {
            return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getRoles().stream().sorted().toList());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {
    }
}
