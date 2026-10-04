package com.unisphere.registration;

import com.unisphere.common.persistence.AuditableEntity;
import com.unisphere.offering.CourseOffering;
import com.unisphere.semester.Semester;
import com.unisphere.student.Student;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A student's course registration for one semester. State changes go through methods so the lifecycle
 * rules (who may change what, and when seats are held) cannot be bypassed.
 */
@Getter
@Entity
@Table(name = "registrations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Registration extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt, id")
    private List<RegistrationItem> items = new ArrayList<>();

    public Registration(Student student, Semester semester) {
        this.student = student;
        this.semester = semester;
    }

    public List<RegistrationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<RegistrationItem> activeItems() {
        return items.stream().filter(RegistrationItem::isActive).toList();
    }

    public int totalCredits() {
        return activeItems().stream().mapToInt(i -> i.getOffering().getCourse().getCredits()).sum();
    }

    /** Draft-like states the student edits freely without holding seats. */
    public boolean isEditableDraft() {
        return status == RegistrationStatus.DRAFT || status == RegistrationStatus.REJECTED;
    }

    /** Submitted and accepted: changes now take or release seats immediately (add/drop). */
    public boolean isApproved() {
        return status == RegistrationStatus.APPROVED;
    }

    RegistrationItem add(CourseOffering offering, Instant now) {
        reopenIfRejected();
        RegistrationItem item = new RegistrationItem(this, offering, now);
        items.add(item);
        return item;
    }

    /** Drafts forget removed courses entirely; nothing was ever reserved for them. */
    void removeDraftItem(RegistrationItem item) {
        reopenIfRejected();
        items.remove(item);
    }

    void drop(RegistrationItem item, Instant now) {
        item.drop(now);
    }

    void submit(Instant now, boolean requiresApproval) {
        this.status = requiresApproval ? RegistrationStatus.SUBMITTED : RegistrationStatus.APPROVED;
        this.submittedAt = now;
        this.rejectionReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    void approve(Long reviewerId, Instant now) {
        this.status = RegistrationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
    }

    void reject(Long reviewerId, Instant now, String reason) {
        this.status = RegistrationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    private void reopenIfRejected() {
        if (status == RegistrationStatus.REJECTED) {
            status = RegistrationStatus.DRAFT;
        }
    }
}
