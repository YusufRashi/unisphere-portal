package com.unisphere.finance;

import com.unisphere.academic.Program;
import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.semester.Semester;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The flat fee a program charges for one semester. */
@Getter
@Entity
@Table(name = "fee_structures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructure extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 120)
    private String description;

    FeeStructure(Program program, Semester semester, BigDecimal amount, String description) {
        this.program = program;
        this.semester = semester;
        update(amount, description);
    }

    void update(BigDecimal amount, String description) {
        this.amount = amount;
        this.description = description.trim();
    }
}
