package com.unisphere.student.dto;

import com.unisphere.academic.dto.AcademicDtos.DepartmentRef;
import com.unisphere.academic.dto.AcademicDtos.ProgramRef;
import com.unisphere.student.Student;
import com.unisphere.student.StudentStatus;
import com.unisphere.user.User;
import com.unisphere.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class StudentDtos {

    private StudentDtos() {
    }

    public static final String PHONE_PATTERN = "^\\+?[0-9 ()-]{7,30}$";

    public record CreateStudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = "must be a valid phone number") String phone,
            @NotNull Long programId,
            @NotNull @Min(1) @Max(8) Integer yearOfStudy,
            @NotNull LocalDate admissionDate) {
    }

    public record UpdateStudentRequest(
            @NotNull Long programId,
            @NotNull @Min(1) @Max(8) Integer yearOfStudy,
            @NotNull StudentStatus status) {
    }

    public record StudentResponse(
            Long id, Long userId, String studentNumber, String firstName, String lastName, String email, String phone,
            UserStatus accountStatus, ProgramRef program, DepartmentRef department, int yearOfStudy,
            LocalDate admissionDate, StudentStatus status, Instant createdAt) {

        public static StudentResponse from(Student s) {
            User u = s.getUser();
            return new StudentResponse(s.getId(), u.getId(), s.getStudentNumber(), u.getFirstName(), u.getLastName(),
                    u.getEmail(), u.getPhone(), u.getStatus(), ProgramRef.from(s.getProgram()),
                    DepartmentRef.from(s.getProgram().getDepartment()), s.getYearOfStudy(), s.getAdmissionDate(),
                    s.getStatus(), s.getCreatedAt());
        }
    }
}
