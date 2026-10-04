package com.unisphere.result;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The grading scale: letter grades with their mark thresholds and grade points (RES-3). */
@Getter
@Entity
@Table(name = "grading_scales")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingScale extends AuditableEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("minMark desc")
    private List<GradeBand> bands = new ArrayList<>();

    public List<GradeBand> getBands() {
        return Collections.unmodifiableList(bands);
    }

    public List<GradingRules.Band> rules() {
        return bands.stream().map(GradeBand::toRule).toList();
    }

    /** Step 1 of a replacement; flush before {@link #addBands} so unique letters can be reused. */
    void clearBands() {
        bands.clear();
    }

    void addBands(List<GradingRules.Band> newBands) {
        GradingRules.highestFirst(newBands).forEach(b -> bands.add(new GradeBand(this, b)));
    }
}
