package com.unisphere.notification;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementPublished;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementRequest;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementView;
import com.unisphere.notification.dto.NotificationDtos.NotificationView;
import com.unisphere.notification.dto.NotificationDtos.UnreadCount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The user's own notifications, and course announcements. Every signed-in user has an inbox. */
@Tag(name = "Notifications & announcements")
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationService notifications;
    private final AnnouncementService announcements;

    // ---- Notifications ----

    @Operation(summary = "My notifications, newest first; unreadOnly=true for the unread ones")
    @GetMapping("/api/notifications")
    public PageResponse<NotificationView> mine(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return notifications.mine(unreadOnly, PageableFactory.of(page, size, null, Map.of(), NEWEST_FIRST));
    }

    @Operation(summary = "How many of my notifications are unread (for the bell badge)")
    @GetMapping("/api/notifications/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(notifications.unreadCount());
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notifications.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllRead() {
        notifications.markAllRead();
        return ResponseEntity.noContent().build();
    }

    // ---- Announcements ----

    @Operation(summary = "Announcements for the courses I teach or am enrolled in (all of them for administrators)")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'ADMIN')")
    @GetMapping("/api/announcements")
    public PageResponse<AnnouncementView> announcements(@RequestParam(required = false) Long offeringId,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
        return announcements.visible(offeringId, PageableFactory.of(page, size, null, Map.of(), NEWEST_FIRST));
    }

    @Operation(summary = "Post an announcement to an offering I teach; every enrolled student is notified")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PostMapping("/api/lecturer/offerings/{offeringId}/announcements")
    public ResponseEntity<AnnouncementPublished> publish(@PathVariable Long offeringId, @Valid @RequestBody AnnouncementRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcements.publish(offeringId, body));
    }
}
