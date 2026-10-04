package com.unisphere.lecturer.dto;

import com.unisphere.academic.dto.AcademicDtos.DepartmentRef;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.student.dto.StudentDtos;
import com.unisphere.user.User;
import com.unisphere.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LecturerDtos {

    private LecturerDtos() {
    }

    public record CreateLecturerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = StudentDtos.PHONE_PATTERN, message = "must be a valid phone number") String phone,
            @NotNull Long departmentId,
            @Size(max = 30) String title,
            @Size(max = 60) String office) {
    }

    public record UpdateLecturerRequest(
            @NotNull Long departmentId,
            @Size(max = 30) String title,
            @Size(max = 60) String office) {
    }

    /** Compact form embedded in offerings. */
    public record LecturerRef(Long id, String displayName, String staffNumber) {
        public static LecturerRef from(Lecturer l) {
            return l == null ? null : new LecturerRef(l.getId(), l.getDisplayName(), l.getStaffNumber());
        }
    }

    public record LecturerResponse(
            Long id, Long userId, String staffNumber, String title, String firstName, String lastName,
            String displayName, String email, String phone, UserStatus accountStatus, DepartmentRef department,
            String office) {

        public static LecturerResponse from(Lecturer l) {
            User u = l.getUser();
            return new LecturerResponse(l.getId(), u.getId(), l.getStaffNumber(), l.getTitle(), u.getFirstName(),
                    u.getLastName(), l.getDisplayName(), u.getEmail(), u.getPhone(), u.getStatus(),
                    DepartmentRef.from(l.getDepartment()), l.getOffice());
        }
    }
}
