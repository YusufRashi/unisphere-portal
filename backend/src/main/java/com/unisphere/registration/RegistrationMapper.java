package com.unisphere.registration;

import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.OfferingSession;
import com.unisphere.offering.dto.OfferingDtos.SessionResponse;
import com.unisphere.registration.RegistrationRules.OfferingFacts;
import com.unisphere.registration.RegistrationRules.Window;
import com.unisphere.registration.dto.RegistrationDtos.ItemResponse;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationSummary;
import com.unisphere.registration.dto.RegistrationDtos.SemesterWindow;
import com.unisphere.registration.dto.RegistrationDtos.StudentRef;
import com.unisphere.semester.Semester;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conversions between entities, rule inputs and API responses. */
final class RegistrationMapper {

    private RegistrationMapper() {
    }

    static OfferingFacts facts(CourseOffering o) {
        Map<Long, String> prerequisites = new LinkedHashMap<>();
        o.getCourse().getPrerequisites().forEach(p -> prerequisites.put(p.getId(), p.getCode()));
        return new OfferingFacts(o.getId(), o.getCourse().getId(), o.getCourse().getCode(), o.getSection(),
                o.getCourse().getCredits(), o.getSeatsLeft(), prerequisites,
                o.getSessions().stream().map(OfferingSession::toSlot).toList());
    }

    static Window window(Semester s) {
        return new Window(s.isRegistrationOpen(), s.getRegistrationOpensAt(), s.getRegistrationClosesAt(),
                s.getAddDropDeadline(), s.getName());
    }

    static SemesterWindow semesterWindow(Semester s, Instant now) {
        Window w = window(s);
        return new SemesterWindow(s.getId(), s.getCode(), s.getName(), s.getRegistrationOpensAt(),
                s.getRegistrationClosesAt(), s.getAddDropDeadline(), RegistrationRules.registrationOpen(w, now).isEmpty(),
                RegistrationRules.addDropOpen(w, now).isEmpty(), s.getMinCredits(), s.getMaxCredits(), s.isRequiresApproval());
    }

    static ItemResponse item(RegistrationItem i) {
        CourseOffering o = i.getOffering();
        return new ItemResponse(i.getId(), o.getId(), o.getCourse().getId(), o.getCourse().getCode(), o.getCourse().getTitle(),
                o.getCourse().getCredits(), o.getSection(), o.getLecturer() == null ? null : o.getLecturer().getDisplayName(),
                o.getSessions().stream().map(SessionResponse::from).toList(), i.isSeatReserved(), i.getAddedAt(), i.getDroppedAt());
    }

    static RegistrationSummary summary(Registration r) {
        Student s = r.getStudent();
        StudentRef student = new StudentRef(s.getId(), s.getStudentNumber(), s.getUser().getFullName(), s.getUser().getEmail(),
                s.getProgram().getCode());
        return new RegistrationSummary(r.getId(), student, SemesterRef.from(r.getSemester()), r.getStatus(), r.totalCredits(),
                r.activeItems().size(), r.getSubmittedAt(), r.getReviewedAt());
    }
}
