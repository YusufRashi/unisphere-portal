package com.unisphere.lecturer;

import com.unisphere.academic.Department;
import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lecturer profile, 1:1 with the {@link User} account that holds credentials. */
@Getter
@Entity
@Table(name = "lecturers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lecturer extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "staff_number", nullable = false, length = 20, unique = true)
    private String staffNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(length = 30)
    private String title;

    @Column(length = 60)
    private String office;

    public Lecturer(User user, String staffNumber, Department department, String title, String office) {
        this.user = user;
        this.staffNumber = staffNumber;
        update(department, title, office);
    }

    public void update(Department department, String title, String office) {
        this.department = department;
        this.title = blankToNull(title);
        this.office = blankToNull(office);
    }

    /** e.g. "Dr. Ada Lovelace" */
    public String getDisplayName() {
        return (title == null ? "" : title + " ") + user.getFullName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
