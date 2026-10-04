package com.unisphere.offering;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A weekly meeting slot of an offering, e.g. Monday 08:00–10:00 in LT-1. */
@Getter
@Entity
@Table(name = "offering_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 80)
    private String venue;

    OfferingSession(CourseOffering offering, TimetableRules.Slot slot) {
        this.offering = offering;
        this.dayOfWeek = slot.day();
        this.startTime = slot.start();
        this.endTime = slot.end();
        this.venue = slot.venue();
    }

    public TimetableRules.Slot toSlot() {
        return new TimetableRules.Slot(dayOfWeek, startTime, endTime, venue);
    }
}
