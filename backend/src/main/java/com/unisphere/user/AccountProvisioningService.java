package com.unisphere.user;

import com.unisphere.academic.DepartmentService;
import com.unisphere.academic.Program;
import com.unisphere.academic.ProgramService;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.auth.AccountTokenPurpose;
import com.unisphere.auth.AccountTokenService;
import com.unisphere.auth.mail.AccountMailer;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.lecturer.LecturerRepository;
import com.unisphere.lecturer.dto.LecturerDtos.CreateLecturerRequest;
import com.unisphere.lecturer.dto.LecturerDtos.LecturerResponse;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import com.unisphere.student.dto.StudentDtos.CreateStudentRequest;
import com.unisphere.student.dto.StudentDtos.StudentResponse;
import com.unisphere.user.dto.AccountDtos.CreateStaffRequest;
import com.unisphere.user.dto.AccountDtos.UpdateUserRequest;
import com.unisphere.user.dto.UserResponse;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts on behalf of the registrar. Every account starts as PENDING_ACTIVATION without a
 * password; the person chooses their own password through a single-use activation link, so no
 * administrator ever knows (or emails) a user's password.
 */
@Service
@RequiredArgsConstructor
public class AccountProvisioningService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.FINANCE);

    private final UserRepository users;
    private final StudentRepository students;
    private final LecturerRepository lecturers;
    private final ProgramService programs;
    private final DepartmentService departments;
    private final IdentifierGenerator identifiers;
    private final AccountTokenService accountTokens;
    private final AccountMailer mailer;
    private final AuditService audit;
    private final UniSphereProperties properties;
    private final Clock clock;

    @Transactional
    public StudentResponse createStudent(CreateStudentRequest r) {
        Program program = programs.load(r.programId());
        if (!program.isActive()) {
            throw ApiException.validation("programId", "This program is not accepting students.");
        }
        User user = newUser(r.firstName(), r.lastName(), r.email(), r.phone(), Set.of(Role.STUDENT));
        Student student = students.save(new Student(user,
                identifiers.nextStudentNumber(r.admissionDate().getYear()), program, r.yearOfStudy(), r.admissionDate()));
        audit.record(AuditAction.USER_CREATED, "Student", student.getId(),
                Map.of("studentNumber", student.getStudentNumber(), "email", user.getEmail()));
        sendActivation(user);
        return StudentResponse.from(student);
    }

    @Transactional
    public LecturerResponse createLecturer(CreateLecturerRequest r) {
        var department = departments.load(r.departmentId());
        User user = newUser(r.firstName(), r.lastName(), r.email(), r.phone(), Set.of(Role.LECTURER));
        Lecturer lecturer = lecturers.save(new Lecturer(user, identifiers.nextStaffNumber(), department, r.title(), r.office()));
        audit.record(AuditAction.USER_CREATED, "Lecturer", lecturer.getId(),
                Map.of("staffNumber", lecturer.getStaffNumber(), "email", user.getEmail()));
        sendActivation(user);
        return LecturerResponse.from(lecturer);
    }

    @Transactional
    public UserResponse createStaff(CreateStaffRequest r) {
        if (!STAFF_ROLES.containsAll(r.roles())) {
            throw ApiException.validation("roles",
                    "Staff accounts can only have the ADMIN and/or FINANCE roles. Use the student or lecturer forms for other roles.");
        }
        User user = newUser(r.firstName(), r.lastName(), r.email(), r.phone(), r.roles());
        audit.record(AuditAction.USER_CREATED, "User", user.getId(),
                Map.of("email", user.getEmail(), "roles", r.roles().stream().map(Enum::name).sorted().toList()));
        sendActivation(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest r) {
        User user = load(id);
        user.updateProfile(r.firstName(), r.lastName(), r.phone());
        audit.record(AuditAction.USER_UPDATED, "User", id, Map.of("email", user.getEmail()));
        return UserResponse.from(user);
    }

    /** Issues a fresh activation link; earlier links stop working. */
    @Transactional
    public void resendActivation(Long id) {
        User user = load(id);
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw ApiException.invalidState("Only accounts that are pending activation can be sent an activation link.");
        }
        sendActivation(user);
        audit.record(AuditAction.ACTIVATION_RESENT, "User", id, Map.of("email", user.getEmail()));
    }

    private User newUser(String firstName, String lastName, String email, String phone, Set<Role> roles) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.duplicate("An account with email", normalized);
        }
        User user = new User(normalized, firstName, lastName, roles, clock.instant());
        user.updateProfile(firstName, lastName, phone);
        return users.save(user);
    }

    private void sendActivation(User user) {
        String token = accountTokens.issue(user, AccountTokenPurpose.ACTIVATION);
        mailer.sendActivationLink(user, properties.appBaseUrl() + "/activate?token=" + token);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }
}
