package com.unisphere.notification;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.notification.dto.NotificationDtos.NotificationView;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's inbox (NTF-1, NTF-2). Nobody can see or change another user's notifications. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;
    private final NotificationStream stream;

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> mine(boolean unreadOnly, Pageable pageable) {
        Long userId = CurrentUser.id();
        var page = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNull(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return PageResponse.of(page, NotificationService::view);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByUserIdAndReadAtIsNull(CurrentUser.id());
    }

    /** Marking an already read notification again is a no-op; someone else's is "not found", not "forbidden". */
    @Transactional
    public void markRead(Long id) {
        Long userId = CurrentUser.id();
        if (!notifications.existsByIdAndUserId(id, userId)) {
            throw ApiException.notFound("Notification", id);
        }
        notifications.markRead(id, userId, clock.instant());
        stream.changed(List.of(userId));
    }

    @Transactional
    public void markAllRead() {
        Long userId = CurrentUser.id();
        notifications.markAllRead(userId, clock.instant());
        stream.changed(List.of(userId));
    }

    private static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.getReadAt() != null,
                n.getCreatedAt());
    }
}
