package com.unisphere.result;

import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.registration.RegistrationItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A student's marks for one registered course. Marks change only while the offering's results are in
 * DRAFT; finalizing copies grade, grade points and pass/fail from the scale active at that moment, so
 * later scale changes never rewrite history (RES-5).
 */
@Getter
@Entity
@Table(name = "results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseResult extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_item_id", nullable = false, unique = true)
    private RegistrationItem registrationItem;

    @Column(name = "cat_mark", precision = 5, scale = 2)
    private BigDecimal catMark;

    @Column(name = "exam_mark", precision = 5, scale = 2)
    private BigDecimal examMark;

    @Column(name = "total_mark", precision = 5, scale = 2)
    private BigDecimal totalMark;

    @Column(length = 3)
    private String grade;

    @Column(name = "grade_points", precision = 3, scale = 2)
    private BigDecimal gradePoints;

    private Boolean passed;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "entered_by")
    private Long enteredBy;

    CourseResult(RegistrationItem registrationItem) {
        this.registrationItem = registrationItem;
    }

    public boolean isComplete() {
        return totalMark != null;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    /** Returns true when anything changed, so unchanged rows are not reported as edits. */
    boolean enterMarks(BigDecimal cat, BigDecimal exam, Long userId) {
        if (isFinalized()) {
            throw new IllegalStateException("Finalized result " + getId() + " cannot be changed");
        }
        if (same(catMark, cat) && same(examMark, exam)) {
            return false;
        }
        this.catMark = cat;
        this.examMark = exam;
        this.totalMark = GradingRules.total(cat, exam);
        this.enteredBy = userId;
        return true;
    }

    void finalizeWith(GradingRules.Band band, Instant now) {
        if (!isComplete()) {
            throw new IllegalStateException("Result " + getId() + " has no total mark");
        }
        this.grade = band.letter();
        this.gradePoints = band.gradePoints();
        this.passed = band.passing();
        this.finalizedAt = now;
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
