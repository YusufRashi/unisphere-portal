package com.unisphere.user.dto;

import com.unisphere.student.dto.StudentDtos;
import com.unisphere.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** Administrative and finance staff (students and lecturers have their own endpoints with profiles). */
    public record CreateStaffRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = StudentDtos.PHONE_PATTERN, message = "must be a valid phone number") String phone,
            @NotEmpty Set<Role> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Pattern(regexp = StudentDtos.PHONE_PATTERN, message = "must be a valid phone number") String phone) {
    }
}
