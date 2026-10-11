package com.unisphere.notification.dto;

import com.unisphere.notification.NotificationType;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    // ---- Notifications ----

    /** {@code link} is a path inside the web app (e.g. {@code /student/results}) or null. */
    public record NotificationView(Long id, NotificationType type, String title, String message, String link, boolean read,
                                   Instant createdAt) {
    }

    public record UnreadCount(long count) {
    }

    // ---- Announcements ----

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String body) {
    }

    public record AnnouncementView(Long id, Long offeringId, String courseCode, String courseTitle, String section,
                                   SemesterRef semester, String title, String body, String authorName, Instant createdAt) {
    }

    /** @param notified how many enrolled students were notified */
    public record AnnouncementPublished(AnnouncementView announcement, int notified) {
    }
}
