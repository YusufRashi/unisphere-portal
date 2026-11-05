package com.unisphere.dashboard;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.dashboard.dto.DashboardDtos.FeeStatus;
import com.unisphere.dashboard.dto.DashboardDtos.GpaPoint;
import com.unisphere.dashboard.dto.DashboardDtos.RegistrationLoad;
import com.unisphere.dashboard.dto.DashboardDtos.StudentDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.UpcomingExam;
import com.unisphere.finance.FeeAccountService;
import com.unisphere.finance.dto.FinanceDtos.AccountView;
import com.unisphere.offering.CourseOffering;
import com.unisphere.registration.Registration;
import com.unisphere.registration.RegistrationItem;
import com.unisphere.registration.RegistrationRepository;
import com.unisphere.registration.RegistrationStatus;
import com.unisphere.result.AcademicRecordService;
import com.unisphere.result.dto.ResultDtos.AcademicRecord;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything a student sees at a glance (US-10), taken from the same services as the detailed pages. */
@Service
@RequiredArgsConstructor
public class StudentDashboardService {

    private static final int MAX_EXAMS = 5;

    private final StudentRepository students;
    private final SemesterRepository semesters;
    private final RegistrationRepository registrations;
    private final AcademicRecordService academicRecords;
    private final FeeAccountService fees;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StudentDashboard mine() {
        Student student = students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only students have a student dashboard."));
        AcademicRecord record = academicRecords.myRecord();
        List<GpaPoint> trend = record.semesters().stream()
                .filter(s -> s.gpa() != null)
                .map(s -> new GpaPoint(s.semester(), s.gpa()))
                .toList()
                .reversed();
        AccountView account = fees.myAccount();

        Optional<Semester> current = semesters.findByCurrentTrue();
        Optional<Registration> registration = current.flatMap(s -> registrations.findByStudentIdAndSemesterId(student.getId(), s.getId()));
        RegistrationLoad load = current.map(s -> registration
                .map(r -> new RegistrationLoad(r.getStatus(), r.activeItems().size(), r.totalCredits(), s.getMinCredits(), s.getMaxCredits()))
                .orElseGet(() -> new RegistrationLoad(null, 0, 0, s.getMinCredits(), s.getMaxCredits())))
                .orElse(null);

        return new StudentDashboard(current.map(SemesterRef::from).orElse(null), record.cgpa(), record.creditsEarned(), trend, load,
                new FeeStatus(account.currency(), account.summary().balance()), registration.map(this::upcomingExams).orElse(List.of()));
    }

    /** A draft is only a plan, so its courses' exams are not the student's yet. */
    private List<UpcomingExam> upcomingExams(Registration r) {
        if (r.getStatus() != RegistrationStatus.SUBMITTED && r.getStatus() != RegistrationStatus.APPROVED) {
            return List.of();
        }
        Instant now = clock.instant();
        return r.activeItems().stream()
                .map(RegistrationItem::getOffering)
                .filter(o -> o.getExamStartsAt() != null && o.getExamStartsAt().isAfter(now))
                .sorted(Comparator.comparing(CourseOffering::getExamStartsAt))
                .limit(MAX_EXAMS)
                .map(o -> new UpcomingExam(o.getId(), o.getCourse().getCode(), o.getCourse().getTitle(), o.getExamStartsAt(), o.getExamVenue()))
                .toList();
    }
}
