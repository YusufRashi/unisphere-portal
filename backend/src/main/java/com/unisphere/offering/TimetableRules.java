package com.unisphere.offering;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Timetable consistency rules for course offerings. Pure functions, so every edge case is unit tested.
 *
 * <ul>
 *   <li>An offering's own sessions must not overlap each other.</li>
 *   <li>Two offerings in the same semester cannot use the same venue at overlapping times.</li>
 *   <li>A lecturer cannot teach two offerings at overlapping times.</li>
 * </ul>
 * Back-to-back sessions (one ends at 10:00, the next starts at 10:00) do not overlap.
 */
public final class TimetableRules {

    private TimetableRules() {
    }

    public record Slot(DayOfWeek day, LocalTime start, LocalTime end, String venue) {

        public Slot {
            Objects.requireNonNull(day);
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            venue = venue == null ? "" : venue.trim();
        }

        boolean overlaps(Slot other) {
            return day == other.day && start.isBefore(other.end) && other.start.isBefore(end);
        }

        boolean sameVenue(Slot other) {
            return !venue.isEmpty() && venue.toLowerCase(Locale.ROOT).equals(other.venue.toLowerCase(Locale.ROOT));
        }

        public String describe() {
            String d = day.name().charAt(0) + day.name().substring(1, 3).toLowerCase(Locale.ROOT);
            return "%s %s–%s".formatted(d, start, end);
        }
    }

    /** A session that already exists in the semester, with what is needed to explain a clash. */
    public record BookedSlot(Slot slot, String offeringLabel, Long lecturerId) {
    }

    /** @return a message for each invalid or mutually overlapping session of one offering */
    public static List<String> internalProblems(List<Slot> slots) {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot a = slots.get(i);
            if (!a.start().isBefore(a.end())) {
                problems.add("Session %d must end after it starts.".formatted(i + 1));
            }
            for (int j = i + 1; j < slots.size(); j++) {
                if (a.overlaps(slots.get(j))) {
                    problems.add("Sessions %d and %d overlap (%s).".formatted(i + 1, j + 1, a.describe()));
                }
            }
        }
        return problems;
    }

    /** @return the first clash with another offering's sessions, described for the user */
    public static Optional<String> findConflict(List<Slot> candidate, Long lecturerId, List<BookedSlot> booked) {
        for (Slot slot : candidate) {
            for (BookedSlot other : booked) {
                if (!slot.overlaps(other.slot())) {
                    continue;
                }
                if (slot.sameVenue(other.slot())) {
                    return Optional.of("%s is already booked by %s on %s."
                            .formatted(slot.venue(), other.offeringLabel(), other.slot().describe()));
                }
                if (lecturerId != null && lecturerId.equals(other.lecturerId())) {
                    return Optional.of("The lecturer already teaches %s on %s."
                            .formatted(other.offeringLabel(), other.slot().describe()));
                }
            }
        }
        return Optional.empty();
    }
}
