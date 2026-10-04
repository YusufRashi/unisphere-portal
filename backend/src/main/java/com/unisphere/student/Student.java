package com.unisphere.student;

import com.unisphere.academic.Program;
import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Student profile, 1:1 with the {@link User} account that holds credentials. */
@Getter
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "student_number", nullable = false, length = 20, unique = true)
    private String studentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "year_of_study", nullable = false)
    private int yearOfStudy;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status;

    public Student(User user, String studentNumber, Program program, int yearOfStudy, LocalDate admissionDate) {
        this.user = user;
        this.studentNumber = studentNumber;
        this.program = program;
        this.yearOfStudy = yearOfStudy;
        this.admissionDate = admissionDate;
        this.status = StudentStatus.ACTIVE;
    }

    public void updateAcademicRecord(Program program, int yearOfStudy, StudentStatus status) {
        this.program = program;
        this.yearOfStudy = yearOfStudy;
        this.status = status;
    }
}
