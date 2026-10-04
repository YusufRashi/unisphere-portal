package com.unisphere.offering;

import com.unisphere.academic.Course;
import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.semester.Semester;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A course taught in a specific semester (and section), with its lecturer, capacity and timetable. */
@Getter
@Entity
@Table(name = "course_offerings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseOffering extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, length = 5)
    private String section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(name = "cat_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal catWeight;

    @Column(name = "exam_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal examWeight;

    @Column(name = "exam_starts_at")
    private Instant examStartsAt;

    @Column(name = "exam_venue", length = 80)
    private String examVenue;

    @Enumerated(EnumType.STRING)
    @Column(name = "results_status", nullable = false, length = 20)
    private ResultsStatus resultsStatus = ResultsStatus.DRAFT;

    @OneToMany(mappedBy = "offering", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek, startTime")
    private List<OfferingSession> sessions = new ArrayList<>();

    /** Editable attributes, grouped so create and update share one code path. */
    public record Details(
            String section, Lecturer lecturer, int capacity, BigDecimal catWeight, BigDecimal examWeight,
            Instant examStartsAt, String examVenue, List<TimetableRules.Slot> sessions) {
    }

    public CourseOffering(Course course, Semester semester, Details details) {
        this.course = course;
        this.semester = semester;
        apply(details);
    }

    public void apply(Details d) {
        this.section = d.section().trim().toUpperCase(Locale.ROOT);
        this.lecturer = d.lecturer();
        this.capacity = d.capacity();
        this.catWeight = d.catWeight();
        this.examWeight = d.examWeight();
        this.examStartsAt = d.examStartsAt();
        this.examVenue = d.examVenue() == null || d.examVenue().isBlank() ? null : d.examVenue().trim();
        sessions.clear();
        d.sessions().forEach(slot -> sessions.add(new OfferingSession(this, slot)));
    }

    public List<OfferingSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public int getSeatsLeft() {
        return Math.max(0, capacity - enrolledCount);
    }

    /** e.g. "CS201 (A)" — used in clash messages. */
    public String getLabel() {
        return course.getCode() + " (" + section + ")";
    }
}
