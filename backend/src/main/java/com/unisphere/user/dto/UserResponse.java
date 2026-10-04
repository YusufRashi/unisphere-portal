package com.unisphere.user.dto;

import com.unisphere.user.Role;
import com.unisphere.user.User;
import com.unisphere.user.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/** Account view for administrators. Never includes the password hash or lockout internals. */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        Set<Role> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getStatus(),
                new TreeSet<>(user.getRoles()),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
