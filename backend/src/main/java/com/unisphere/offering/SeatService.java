package com.unisphere.offering;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that changes an offering's seat count. Each operation is one guarded SQL statement, so two
 * students racing for the last seat cannot both win: the database row lock serialises the updates and the
 * {@code enrolled_count < capacity} condition makes the loser's update affect zero rows.
 */
@Service
@RequiredArgsConstructor
public class SeatService {

    private final CourseOfferingRepository offerings;

    /** Must run inside the caller's transaction so a later failure also gives the seat back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void take(Long offeringId, String label) {
        if (offerings.takeSeat(offeringId) == 0) {
            throw new ApiException(ErrorCode.COURSE_FULL, "%s is full. Choose another section or course.".formatted(label))
                    .with("offeringId", offeringId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long offeringId) {
        offerings.releaseSeat(offeringId);
    }
}
