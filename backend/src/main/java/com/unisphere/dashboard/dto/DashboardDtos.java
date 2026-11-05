package com.unisphere.dashboard.dto;

import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.dto.FinanceDtos.AccountRow;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.registration.RegistrationStatus;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    // ---- Student ----

    /**
     * @param semester     the current semester, or null when none is set
     * @param cgpa         null before the first finalized result
     * @param gpaTrend     semester GPAs, oldest first
     * @param registration null when there is no current semester
     */
    public record StudentDashboard(SemesterRef semester, BigDecimal cgpa, int creditsEarned, List<GpaPoint> gpaTrend,
                                   RegistrationLoad registration, FeeStatus fees, List<UpcomingExam> upcomingExams) {
    }

    public record GpaPoint(SemesterRef semester, BigDecimal gpa) {
    }

    /** @param status null when the student has not started registering for the semester */
    public record RegistrationLoad(RegistrationStatus status, int courses, int credits, int minCredits, int maxCredits) {
    }

    public record FeeStatus(String currency, BigDecimal balance) {
    }

    public record UpcomingExam(Long offeringId, String courseCode, String courseTitle, Instant startsAt, String venue) {
    }

    // ---- Lecturer ----

    /**
     * @param marksMissing  marks still to enter in courses the lecturer can edit (results in draft)
     * @param awaitingReview courses submitted to the registrar and not yet finalized
     */
    public record LecturerDashboard(SemesterRef semester, int courses, int students, int marksMissing, int awaitingReview,
                                    List<CourseProgress> offerings) {
    }

    public record CourseProgress(Long offeringId, String courseCode, String courseTitle, String section, ResultsStatus resultsStatus,
                                 int classSize, int marksComplete) {
    }

    // ---- Administrator ----

    /** @param registrationActivity registrations submitted per day (UTC) in the current semester, last 30 days */
    public record AdminDashboard(SemesterRef semester, long totalStudents, long activeStudents, long lecturers, long activeCourses,
                                 RegistrationCounts registrations, long resultsAwaitingReview, OutstandingFees outstandingFees,
                                 List<DayCount> registrationActivity, List<RecentResult> recentResults) {
    }

    /** Registrations of the current semester by status. */
    public record RegistrationCounts(long approved, long awaitingApproval, long draft, long rejected) {
    }

    public record OutstandingFees(String currency, BigDecimal amount, long students) {
    }

    public record DayCount(LocalDate date, long count) {
    }

    public record RecentResult(Long offeringId, String courseCode, String courseTitle, String section, String semesterName,
                               Instant finalizedAt, long students, long passed) {
    }

    // ---- Finance ----

    /**
     * @param outstanding      what students owe; a student in credit does not reduce it
     * @param collectionRate   collected as a percentage of charged; null before anything has been charged
     * @param weeklyCollections completed payments per week (Monday, UTC), last 12 weeks, oldest first
     * @param methodsLast30Days completed payments by method, largest first; methods with none are left out
     * @param largestBalances  the students who owe the most
     */
    public record FinanceDashboard(String currency, BigDecimal totalCharged, BigDecimal totalCollected, BigDecimal outstanding,
                                   long studentsOwing, BigDecimal collectionRate, BigDecimal collectedLast30Days,
                                   List<WeekAmount> weeklyCollections, List<MethodAmount> methodsLast30Days,
                                   List<AccountRow> largestBalances) {
    }

    public record WeekAmount(LocalDate weekStart, BigDecimal amount) {
    }

    public record MethodAmount(PaymentMethod method, BigDecimal amount, long payments) {
    }
}
