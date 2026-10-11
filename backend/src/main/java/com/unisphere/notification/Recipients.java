package com.unisphere.notification;

/**
 * SQL selecting the {@code user_id}s who receive a notification, for {@link NotificationWriter#sendTo}.
 * Deactivated users are left out; users who have not activated their account yet still receive them, so the
 * messages are waiting when they first sign in.
 */
final class Recipients {

    private Recipients() {
    }

    static final String ACTIVE_STUDENTS = """
            select s.user_id from students s
            join users u on u.id = s.user_id
            where s.status = 'ACTIVE' and u.status <> 'DEACTIVATED'""";

    /** Active students without a submitted or approved registration in {@code :semesterId}. */
    static final String STUDENTS_NOT_REGISTERED = ACTIVE_STUDENTS + """
             and not exists (select 1 from registrations r
                            where r.student_id = s.id and r.semester_id = :semesterId and r.status in ('SUBMITTED', 'APPROVED'))""";

    /** The class list of {@code :offeringId}: students with the course on an approved registration. */
    static final String CLASS_LIST = """
            select distinct s.user_id from registration_items i
            join registrations r on r.id = i.registration_id
            join students s on s.id = r.student_id
            join users u on u.id = s.user_id
            where i.offering_id = :offeringId and i.status = 'ACTIVE' and r.status = 'APPROVED' and u.status <> 'DEACTIVATED'""";
}
