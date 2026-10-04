package com.unisphere.registration;

import com.unisphere.offering.CourseOffering;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One offering in a registration. Items are dropped (kept for history) once they have been submitted. */
@Getter
@Entity
@Table(name = "registration_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistrationItem {

    public enum Status { ACTIVE, DROPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "seat_reserved", nullable = false)
    private boolean seatReserved;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "dropped_at")
    private Instant droppedAt;

    RegistrationItem(Registration registration, CourseOffering offering, Instant now) {
        this.registration = registration;
        this.offering = offering;
        this.addedAt = now;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    void markSeatReserved(boolean reserved) {
        this.seatReserved = reserved;
    }

    void drop(Instant now) {
        this.status = Status.DROPPED;
        this.droppedAt = now;
        this.seatReserved = false;
    }
}
