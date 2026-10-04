package com.unisphere.registration;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationItemRepository extends JpaRepository<RegistrationItem, Long> {

    /**
     * The class list of an offering: active items of approved registrations, ordered by student number.
     * Students whose registration still awaits approval are not (yet) in the class.
     */
    default List<RegistrationItem> classList(Long offeringId) {
        return findClassList(offeringId, RegistrationItem.Status.ACTIVE, RegistrationStatus.APPROVED);
    }

    @Query("""
            select i from RegistrationItem i join fetch i.registration r join fetch r.student s join fetch s.user
            where i.offering.id = :offeringId and i.status = :itemStatus and r.status = :registrationStatus
            order by s.studentNumber""")
    List<RegistrationItem> findClassList(@Param("offeringId") Long offeringId,
                                         @Param("itemStatus") RegistrationItem.Status itemStatus,
                                         @Param("registrationStatus") RegistrationStatus registrationStatus);
}
