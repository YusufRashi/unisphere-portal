package com.unisphere.notification;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.notification.NotificationWriter.Message;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementPublished;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementRequest;
import com.unisphere.notification.dto.NotificationDtos.AnnouncementView;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.CourseOfferingRepository;
import com.unisphere.registration.RegistrationItem;
import com.unisphere.registration.RegistrationStatus;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.user.UserRepository;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course announcements (NTF-3). The lecturer teaching an offering (or the registrar) posts one, and every student
 * on the class list is notified in the same transaction: the announcement and its notifications exist together
 * or not at all.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    /** How much of the announcement the notification shows; the full text is on the announcements page. */
    static final int PREVIEW_LENGTH = 300;

    private final AnnouncementRepository announcements;
    private final CourseOfferingRepository offerings;
    private final UserRepository users;
    private final NotificationWriter writer;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public AnnouncementPublished publish(Long offeringId, AnnouncementRequest r) {
        CourseOffering o = offerings.findDetailedById(offeringId).orElseThrow(() -> ApiException.notFound("Offering", offeringId));
        Long userId = CurrentUser.id();
        boolean teaches = o.getLecturer() != null && o.getLecturer().getUser().getId().equals(userId);
        if (!teaches && !CurrentUser.hasRole("ADMIN")) {
            throw ApiException.forbidden("Only the lecturer teaching %s can post announcements to it.".formatted(o.getLabel()));
        }
        Announcement a = announcements.save(new Announcement(o, users.getReferenceById(userId), r.title(), r.body(), clock.instant()));
        Message message = new Message(NotificationType.ANNOUNCEMENT, o.getCourse().getCode() + ": " + a.getTitle(),
                NotificationWriter.abbreviate(a.getBody(), PREVIEW_LENGTH), "/announcements?offeringId=" + offeringId);
        int notified = writer.sendTo(Recipients.CLASS_LIST, Map.of("offeringId", offeringId), message, null);
        audit.record(AuditAction.ANNOUNCEMENT_PUBLISHED, "CourseOffering", offeringId,
                Map.of("offering", o.getLabel(), "title", a.getTitle(), "notified", notified));
        return new AnnouncementPublished(view(a), notified);
    }

    /** Announcements of the offerings the user teaches or is enrolled in; administrators see all of them. */
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementView> visible(Long offeringId, Pageable pageable) {
        return PageResponse.of(announcements.visibleTo(CurrentUser.id(), CurrentUser.hasRole("ADMIN"), offeringId,
                RegistrationItem.Status.ACTIVE, RegistrationStatus.APPROVED, pageable), AnnouncementService::view);
    }

    private static AnnouncementView view(Announcement a) {
        CourseOffering o = a.getOffering();
        return new AnnouncementView(a.getId(), o.getId(), o.getCourse().getCode(), o.getCourse().getTitle(), o.getSection(),
                SemesterRef.from(o.getSemester()), a.getTitle(), a.getBody(), a.getAuthor().getFullName(), a.getCreatedAt());
    }
}
