package com.unisphere.notification;

import com.unisphere.registration.RegistrationItem;
import com.unisphere.registration.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * Announcements a user may read: everything when {@code all}, otherwise those of offerings the user teaches
     * or is enrolled in (an active course on an approved registration).
     */
    @EntityGraph(attributePaths = {"offering", "offering.course", "offering.semester", "author"})
    @Query("""
            select a from Announcement a
            join a.offering o
            left join o.lecturer l
            where (:offeringId is null or o.id = :offeringId)
              and (:all = true
                   or l.user.id = :userId
                   or o.id in (select i.offering.id from RegistrationItem i
                               where i.registration.student.user.id = :userId
                                 and i.status = :active and i.registration.status = :approved))""")
    Page<Announcement> visibleTo(@Param("userId") Long userId, @Param("all") boolean all, @Param("offeringId") Long offeringId,
                                 @Param("active") RegistrationItem.Status active, @Param("approved") RegistrationStatus approved,
                                 Pageable pageable);
}
