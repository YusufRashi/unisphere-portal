package com.unisphere.academic;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A degree or diploma students enrol in, e.g. "BSc Computer Science". */
@Getter
@Entity
@Table(name = "programs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "award_level", nullable = false, length = 20)
    private AwardLevel awardLevel;

    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    @Column(nullable = false)
    private boolean active = true;

    public Program(Department department, String code, String name, AwardLevel awardLevel, int durationYears) {
        update(department, code, name, awardLevel, durationYears, true);
    }

    public void update(Department department, String code, String name, AwardLevel awardLevel, int durationYears,
                       boolean active) {
        this.department = department;
        this.code = Department.normalizeCode(code);
        this.name = name.trim();
        this.awardLevel = awardLevel;
        this.durationYears = durationYears;
        this.active = active;
    }
}
