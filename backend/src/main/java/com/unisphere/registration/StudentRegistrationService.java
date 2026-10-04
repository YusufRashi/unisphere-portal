package com.unisphere.registration;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.PageResponse;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.OfferingService;
import com.unisphere.offering.SeatService;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.registration.RegistrationRules.OfferingFacts;
import com.unisphere.registration.RegistrationRules.Violation;
import com.unisphere.registration.RegistrationRules.Window;
import com.unisphere.registration.dto.RegistrationDtos.Availability;
import com.unisphere.registration.dto.RegistrationDtos.CatalogueEntry;
import com.unisphere.registration.dto.RegistrationDtos.HistoryEntry;
import com.unisphere.registration.dto.RegistrationDtos.ItemResponse;
import com.unisphere.registration.dto.RegistrationDtos.Permissions;
import com.unisphere.registration.dto.RegistrationDtos.PrerequisiteStatus;
import com.unisphere.registration.dto.RegistrationDtos.RegistrationView;
import com.unisphere.registration.dto.RegistrationDtos.Timetable;
import com.unisphere.registration.dto.RegistrationDtos.TimetableEntry;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import com.unisphere.student.StudentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course registration from the student's point of view. Every operation works on the signed-in student's
 * own registration for the current semester — there is no way to address another student's data.
 *
 * <p>Mutations lock the registration row first, so concurrent requests from one student are serialised;
 * seats are taken through {@link SeatService}, so concurrent requests from different students cannot
 * oversubscribe an offering.
 */
@Service
@RequiredArgsConstructor
public class StudentRegistrationService {

    private final RegistrationRepository registrations;
    private final StudentRepository students;
    private final SemesterRepository semesters;
    private final OfferingService offerings;
    private final SeatService seats;
    private final AcademicHistory history;
    private final AuditService audit;
    private final Clock clock;

    // ---- Queries ----

    @Transactional(readOnly = true)
    public RegistrationView currentRegistration() {
        Student student = currentStudent();
        Semester semester = currentSemester();
        Optional<Registration> registration = registrations.findByStudentIdAndSemesterId(student.getId(), semester.getId());
        return view(student, semester, registration.orElse(null));
    }

    /** Offerings of the current semester with, for each, whether this student can add it and why not. */
    @Transactional(readOnly = true)
    public PageResponse<CatalogueEntry> catalogue(String q, Long departmentId, Integer level, Pageable pageable) {
        Student student = currentStudent();
        Semester semester = currentSemester();
        List<OfferingFacts> registered = registrations.findByStudentIdAndSemesterId(student.getId(), semester.getId())
                .map(r -> r.activeItems().stream().map(i -> RegistrationMapper.facts(i.getOffering())).toList())
                .orElse(List.of());
        Set<Long> passed = history.passedCourseIds(student.getId());
        Page<CourseOffering> page = offerings.searchInSemester(semester.getId(), q, departmentId, level, null, pageable);
        return PageResponse.of(page, o -> catalogueEntry(o, registered, passed, semester.getMaxCredits()));
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history() {
        Student student = currentStudent();
        return registrations.findByStudentIdOrderBySemesterStartDateDesc(student.getId()).stream()
                .map(r -> new HistoryEntry(r.getId(), SemesterRef.from(r.getSemester()), r.getStatus(), r.totalCredits(),
                        r.activeItems().size(), r.getSubmittedAt()))
                .toList();
    }

    /** Weekly sessions of the courses the student has submitted for the current semester. */
    @Transactional(readOnly = true)
    public Timetable timetable() {
        Student student = currentStudent();
        Semester semester = currentSemester();
        Optional<Registration> registration = registrations.findByStudentIdAndSemesterId(student.getId(), semester.getId())
                .filter(r -> r.getStatus() == RegistrationStatus.SUBMITTED || r.getStatus() == RegistrationStatus.APPROVED);
        List<TimetableEntry> entries = registration.map(r -> r.activeItems().stream()
                        .flatMap(i -> {
                            CourseOffering o = i.getOffering();
                            String lecturer = o.getLecturer() == null ? null : o.getLecturer().getDisplayName();
                            return o.getSessions().stream().map(s -> new TimetableEntry(o.getId(), o.getCourse().getCode(),
                                    o.getCourse().getTitle(), o.getSection(), lecturer, s.getDayOfWeek(), s.getStartTime(),
                                    s.getEndTime(), s.getVenue()));
                        })
                        .sorted(Comparator.comparing(TimetableEntry::dayOfWeek).thenComparing(TimetableEntry::startTime))
                        .toList())
                .orElse(List.of());
        return new Timetable(SemesterRef.from(semester), registration.map(Registration::getStatus).orElse(null), entries);
    }

    // ---- Commands ----

    @Transactional
    public RegistrationView addCourse(Long offeringId) {
        Student student = eligibleStudent();
        Semester semester = currentSemester();
        Registration registration = lockOrCreate(student, semester);
        CourseOffering offering = offerings.loadDetailed(offeringId);
        if (!offering.getSemester().getId().equals(semester.getId())) {
            throw ApiException.validation("offeringId", "This course is not offered in %s.".formatted(semester.getName()));
        }
        Instant now = clock.instant();
        Window window = RegistrationMapper.window(semester);

        if (registration.getStatus() == RegistrationStatus.SUBMITTED) {
            throw awaitingApproval();
        }
        boolean afterSubmission = registration.isApproved();
        fail((afterSubmission ? RegistrationRules.addDropOpen(window, now) : RegistrationRules.registrationOpen(window, now)));

        List<OfferingFacts> registered = registration.activeItems().stream().map(i -> RegistrationMapper.facts(i.getOffering())).toList();
        List<Violation> violations = RegistrationRules.violationsForAdding(RegistrationMapper.facts(offering), registered,
                history.passedCourseIds(student.getId()), semester.getMaxCredits());
        if (!violations.isEmpty()) {
            fail(Optional.of(violations.getFirst()));
        }

        RegistrationItem item = registration.add(offering, now);
        if (afterSubmission) {
            // Already registered: a newly added course takes its seat immediately.
            seats.take(offering.getId(), offering.getLabel());
            item.markSeatReserved(true);
            audit.record(AuditAction.COURSE_ADDED_AFTER_SUBMISSION, "Registration", registration.getId(),
                    Map.of("offering", offering.getLabel()));
        }
        registrations.flush();
        return view(student, semester, registration);
    }

    /** Removes a course from a draft, or drops it (releasing the seat) after submission. */
    @Transactional
    public RegistrationView removeCourse(Long itemId) {
        Student student = eligibleStudent();
        Semester semester = currentSemester();
        Registration registration = registrations.lockByStudentAndSemester(student.getId(), semester.getId())
                .orElseThrow(() -> ApiException.notFound("Registration item", itemId));
        RegistrationItem item = registration.activeItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Registration item", itemId));
        Instant now = clock.instant();
        Window window = RegistrationMapper.window(semester);

        if (registration.getStatus() == RegistrationStatus.SUBMITTED) {
            throw awaitingApproval();
        }
        if (registration.isEditableDraft()) {
            fail(RegistrationRules.registrationOpen(window, now));
            registration.removeDraftItem(item);
        } else {
            fail(RegistrationRules.addDropOpen(window, now));
            fail(RegistrationRules.dropKeepsMinimum(RegistrationMapper.facts(item.getOffering()), registration.totalCredits(),
                    semester.getMinCredits()));
            if (item.isSeatReserved()) {
                seats.release(item.getOffering().getId());
            }
            registration.drop(item, now);
            audit.record(AuditAction.COURSE_DROPPED, "Registration", registration.getId(),
                    Map.of("offering", item.getOffering().getLabel()));
        }
        registrations.flush();
        return view(student, semester, registration);
    }

    /**
     * Confirms the registration: re-checks the rules, then takes a seat for every course. Seats are taken in
     * one transaction, so if any course turns out to be full nothing is reserved and nothing changes.
     */
    @Transactional
    public RegistrationView submit() {
        Student student = eligibleStudent();
        Semester semester = currentSemester();
        Registration registration = registrations.lockByStudentAndSemester(student.getId(), semester.getId())
                .orElseThrow(() -> ApiException.invalidState("Add at least one course before submitting."));
        if (!registration.isEditableDraft()) {
            throw ApiException.invalidState(registration.getStatus() == RegistrationStatus.SUBMITTED
                    ? "Your registration has already been submitted and is awaiting approval."
                    : "Your registration has already been confirmed. Use add/drop to make changes.");
        }
        Instant now = clock.instant();
        fail(RegistrationRules.registrationOpen(RegistrationMapper.window(semester), now));
        List<RegistrationItem> items = registration.activeItems();
        if (items.isEmpty()) {
            throw ApiException.invalidState("Add at least one course before submitting.");
        }
        fail(RegistrationRules.withinCreditRange(registration.totalCredits(), semester.getMinCredits(), semester.getMaxCredits()));
        Set<Long> passed = history.passedCourseIds(student.getId());
        for (RegistrationItem item : items) {
            fail(RegistrationRules.prerequisitesMet(RegistrationMapper.facts(item.getOffering()), passed));
        }

        for (RegistrationItem item : items) {
            seats.take(item.getOffering().getId(), item.getOffering().getLabel());
            item.markSeatReserved(true);
        }
        registration.submit(now, semester.isRequiresApproval());
        audit.record(AuditAction.REGISTRATION_SUBMITTED, "Registration", registration.getId(), Map.of(
                "credits", registration.totalCredits(), "status", registration.getStatus().name()));
        registrations.flush();
        return view(student, semester, registration);
    }

    // ---- Helpers ----

    private Registration lockOrCreate(Student student, Semester semester) {
        return registrations.lockByStudentAndSemester(student.getId(), semester.getId())
                .orElseGet(() -> registrations.saveAndFlush(new Registration(student, semester)));
    }

    private Student currentStudent() {
        return students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only students have a course registration."));
    }

    private Student eligibleStudent() {
        Student student = currentStudent();
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new ApiException(ErrorCode.STUDENT_NOT_ELIGIBLE,
                    "Only active students can register. Your academic status is %s; please contact the registrar."
                            .formatted(student.getStatus().name().toLowerCase()));
        }
        return student;
    }

    private Semester currentSemester() {
        return semesters.findByCurrentTrue()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No semester is currently open for registration."));
    }

    private static ApiException awaitingApproval() {
        return ApiException.invalidState("Your registration is awaiting approval by the registrar and cannot be changed right now.");
    }

    private static void fail(Optional<Violation> violation) {
        violation.ifPresent(v -> {
            throw new ApiException(v.code(), v.message());
        });
    }

    private CatalogueEntry catalogueEntry(CourseOffering o, List<OfferingFacts> registered, Set<Long> passed, int maxCredits) {
        OfferingFacts facts = RegistrationMapper.facts(o);
        List<PrerequisiteStatus> prerequisites = facts.prerequisites().entrySet().stream()
                .map(e -> new PrerequisiteStatus(e.getKey(), e.getValue(), passed.contains(e.getKey())))
                .sorted(Comparator.comparing(PrerequisiteStatus::code))
                .toList();
        if (registered.stream().anyMatch(r -> r.offeringId() == facts.offeringId())) {
            return new CatalogueEntry(OfferingResponse.from(o), prerequisites, Availability.IN_REGISTRATION, null);
        }
        List<Violation> violations = RegistrationRules.violationsForAdding(facts, registered, passed, maxCredits);
        if (violations.isEmpty()) {
            return new CatalogueEntry(OfferingResponse.from(o), prerequisites, Availability.AVAILABLE, null);
        }
        Violation first = violations.getFirst();
        Availability availability = switch (first.code()) {
            case DUPLICATE_COURSE -> Availability.SAME_COURSE_REGISTERED;
            case PREREQUISITE_NOT_MET -> Availability.PREREQUISITES_MISSING;
            case COURSE_FULL -> Availability.FULL;
            case TIMETABLE_CLASH -> Availability.TIMETABLE_CLASH;
            default -> Availability.CREDIT_LIMIT;
        };
        return new CatalogueEntry(OfferingResponse.from(o), prerequisites, availability, first.message());
    }

    private RegistrationView view(Student student, Semester semester, Registration registration) {
        Instant now = clock.instant();
        var window = RegistrationMapper.semesterWindow(semester, now);
        boolean studentActive = student.getStatus() == StudentStatus.ACTIVE;
        if (registration == null) {
            String locked = !studentActive ? "Only active students can register."
                    : window.registrationOpen() ? null : "Registration is not open.";
            return new RegistrationView(null, window, null, List.of(), List.of(), 0, null, null, null,
                    new Permissions(locked == null, false, false, false, locked));
        }
        List<ItemResponse> active = registration.activeItems().stream().map(RegistrationMapper::item).toList();
        List<ItemResponse> dropped = registration.getItems().stream().filter(i -> !i.isActive()).map(RegistrationMapper::item).toList();
        return new RegistrationView(registration.getId(), window, registration.getStatus(), active, dropped,
                registration.totalCredits(), registration.getSubmittedAt(), registration.getReviewedAt(),
                registration.getRejectionReason(), permissions(registration, window.registrationOpen(), window.addDropOpen(), studentActive));
    }

    private static Permissions permissions(Registration r, boolean registrationOpen, boolean addDropOpen, boolean studentActive) {
        if (!studentActive) {
            return new Permissions(false, false, false, false, "Only active students can change a registration.");
        }
        return switch (r.getStatus()) {
            case DRAFT, REJECTED -> new Permissions(registrationOpen, registrationOpen,
                    registrationOpen && !r.activeItems().isEmpty(), false,
                    registrationOpen ? null : "Registration is not open.");
            case SUBMITTED -> new Permissions(false, false, false, true, "Awaiting approval by the registrar.");
            case APPROVED -> new Permissions(addDropOpen, addDropOpen, false, true,
                    addDropOpen ? null : "The add/drop deadline has passed.");
        };
    }
}
