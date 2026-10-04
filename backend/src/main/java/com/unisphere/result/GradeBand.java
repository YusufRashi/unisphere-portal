package com.unisphere.result;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One letter grade of a scale. Covers marks from {@code minMark} up to the next higher band's minimum. */
@Getter
@Entity
@Table(name = "grade_bands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeBand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private GradingScale scale;

    @Column(nullable = false, length = 3)
    private String letter;

    @Column(name = "min_mark", nullable = false, precision = 5, scale = 2)
    private BigDecimal minMark;

    @Column(name = "grade_points", nullable = false, precision = 3, scale = 2)
    private BigDecimal gradePoints;

    @Column(nullable = false)
    private boolean passing;

    GradeBand(GradingScale scale, GradingRules.Band band) {
        this.scale = scale;
        this.letter = band.letter().trim().toUpperCase(Locale.ROOT);
        this.minMark = band.minMark();
        this.gradePoints = band.gradePoints();
        this.passing = band.passing();
    }

    GradingRules.Band toRule() {
        return new GradingRules.Band(letter, minMark, gradePoints, passing);
    }
}
