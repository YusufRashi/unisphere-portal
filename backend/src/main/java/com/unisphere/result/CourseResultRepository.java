package com.unisphere.result;

import com.unisphere.registration.RegistrationItem;
import com.unisphere.registration.RegistrationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CourseResultRepository extends JpaRepository<CourseResult, Long> {

    @Query("select r from CourseResult r where r.registrationItem.offering.id = :offeringId")
    List<CourseResult> findByOfferingId(@Param("offeringId") Long offeringId);

    default Map<Long, ClassProgress> progress(Collection<Long> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        return findProgress(offeringIds, RegistrationItem.Status.ACTIVE, RegistrationStatus.APPROVED).stream()
                .collect(Collectors.toMap(ClassProgress::offeringId, Function.identity()));
    }

    @Query("""
            select new com.unisphere.result.ClassProgress(i.offering.id, count(i), count(r.totalMark))
            from RegistrationItem i join i.registration reg left join CourseResult r on r.registrationItem = i
            where i.offering.id in :ids and i.status = :itemStatus and reg.status = :registrationStatus
            group by i.offering.id""")
    List<ClassProgress> findProgress(@Param("ids") Collection<Long> offeringIds,
                                     @Param("itemStatus") RegistrationItem.Status itemStatus,
                                     @Param("registrationStatus") RegistrationStatus registrationStatus);

    @Query("""
            select r from CourseResult r join fetch r.registrationItem i join fetch i.offering o
            join fetch o.course join fetch o.semester
            where i.registration.student.id = :studentId and r.finalizedAt is not null""")
    List<CourseResult> findFinalizedByStudentId(@Param("studentId") Long studentId);

    @Query("""
            select distinct i.offering.course.id from CourseResult r join r.registrationItem i
            where i.registration.student.id = :studentId and r.finalizedAt is not null and r.passed = true""")
    List<Long> findPassedCourseIds(@Param("studentId") Long studentId);
}
