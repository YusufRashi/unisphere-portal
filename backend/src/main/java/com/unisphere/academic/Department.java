package com.unisphere.academic;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "departments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends AuditableEntity {

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String faculty;

    public Department(String code, String name, String faculty) {
        update(code, name, faculty);
    }

    public void update(String code, String name, String faculty) {
        this.code = normalizeCode(code);
        this.name = name.trim();
        this.faculty = faculty == null || faculty.isBlank() ? null : faculty.trim();
    }

    static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
