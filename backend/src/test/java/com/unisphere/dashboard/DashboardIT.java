package com.unisphere.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.unisphere.support.AcademicIntegrationTest;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** System-wide figures share a database with other tests, so they are checked as changes, not absolute values. */
class DashboardIT extends AcademicIntegrationTest {

    private String finance;

    @BeforeEach
    void signInFinance() throws Exception {
        User user = createActiveUser(Role.FINANCE);
        finance = bearer(login(user.getEmail(), DEFAULT_PASSWORD));
    }

    private JsonNode studentDashboard(Person p) throws Exception {
        return as(p.auth(), get("/api/students/me/dashboard"), 200);
    }

    private JsonNode adminDashboard() throws Exception {
        return as(admin, get("/api/admin/dashboard"), 200);
    }

    private JsonNode financeDashboard() throws Exception {
        return as(finance, get("/api/fees/dashboard"), 200);
    }

    private void charge(Person student, String amount) throws Exception {
        call(postJson("/api/fees/accounts/" + student.id() + "/charges", Map.of("description", "Fee", "amount", amount), finance), 200);
    }

    private JsonNode pay(Person student, String amount, String reference) throws Exception {
        return call(postJson("/api/fees/accounts/" + student.id() + "/payments",
                Map.of("amount", amount, "method", "BANK_TRANSFER", "reference", reference), finance), 200);
    }

    private static BigDecimal money(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }

    private String todayUtc() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
    }

    /** An offering whose exam starts {@code examInDays} from now. */
    private long examOffering(long courseId, Person lecturer, String day, int examInDays) throws Exception {
        Map<String, Object> body = offeringBody(courseId, lecturer.id(), 30, day);
        body.put("examStartsAt", clock.instant().plusSeconds(examInDays * 86_400L).toString());
        body.put("examVenue", unique("HALL"));
        return call(postJson("/api/admin/offerings", body, admin), 201).get("id").asLong();
    }

    @Test
    void newStudentSeesAnEmptyButCompleteDashboard() throws Exception {
        JsonNode d = studentDashboard(student());

        assertThat(d.get("semester").get("name").asString()).isEqualTo(semesterName);
        assertThat(d.get("cgpa").isNull()).isTrue();
        assertThat(d.get("gpaTrend")).isEmpty();
        assertThat(d.get("registration").get("status").isNull()).isTrue();
        assertThat(d.get("registration").get("maxCredits").asInt()).isEqualTo(24);
        assertThat(money(d.get("fees"), "balance")).isEqualByComparingTo("0");
        assertThat(d.get("upcomingExams")).isEmpty();
    }

    @Test
    void studentSeesGradesLoadBalanceAndOnlyFutureExamsOfTheirCourses() throws Exception {
        Person lecturer = lecturer();
        long graded = offering(course(3), lecturer, "MONDAY");
        Person student = student();
        register(student, graded);
        finalizeResult(lecturer, graded, student, "25", "60");
        charge(student, "500.00");

        // A later semester, with exams later, sooner and already past.
        openSemester();
        long late = examOffering(course(3), lecturer, "TUESDAY", 20);
        long early = examOffering(course(4), lecturer, "WEDNESDAY", 10);
        long past = examOffering(course(2), lecturer, "THURSDAY", -1);
        register(student, late, early, past);

        JsonNode d = studentDashboard(student);

        assertThat(d.get("cgpa").decimalValue().signum()).isPositive();
        assertThat(d.get("creditsEarned").asInt()).isEqualTo(3);
        assertThat(d.get("gpaTrend")).hasSize(1);
        JsonNode load = d.get("registration");
        assertThat(load.get("status").asString()).isEqualTo("APPROVED");
        assertThat(load.get("courses").asInt()).isEqualTo(3);
        assertThat(load.get("credits").asInt()).isEqualTo(9);
        assertThat(money(d.get("fees"), "balance")).isEqualByComparingTo("500");
        assertThat(d.get("upcomingExams").valueStream().map(e -> e.get("offeringId").asLong())).containsExactly(early, late);
    }

    @Test
    void lecturerSeesWhatIsLeftToDo() throws Exception {
        Person lecturer = lecturer();
        long unmarked = offering(course(3), lecturer, "MONDAY");
        long submitted = offering(course(3), lecturer, "TUESDAY");
        Person a = student();
        Person b = student();
        register(a, unmarked, submitted);
        register(b, unmarked);
        long item = itemIds(sheet(lecturer.auth(), submitted, 200)).get(a.number());
        saveMarks(lecturer.auth(), submitted, 200, mark(item, "20", "50"));
        as(lecturer.auth(), post("/api/lecturer/offerings/" + submitted + "/results/submit"), 200);

        JsonNode d = as(lecturer.auth(), get("/api/lecturer/dashboard"), 200);

        assertThat(d.get("courses").asInt()).isEqualTo(2);
        assertThat(d.get("students").asInt()).isEqualTo(3);
        assertThat(d.get("marksMissing").asInt()).isEqualTo(2);
        assertThat(d.get("awaitingReview").asInt()).isEqualTo(1);
        assertThat(d.get("offerings")).hasSize(2);
    }

    @Test
    void adminSeesSystemWideFigures() throws Exception {
        JsonNode before = adminDashboard();
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "MONDAY");
        Person student = student();
        register(student, o);
        charge(student, "750.00");

        JsonNode d = adminDashboard();

        assertThat(d.get("totalStudents").asLong()).isEqualTo(before.get("totalStudents").asLong() + 1);
        assertThat(d.get("activeStudents").asLong()).isEqualTo(before.get("activeStudents").asLong() + 1);
        assertThat(d.get("lecturers").asLong()).isEqualTo(before.get("lecturers").asLong() + 1);
        assertThat(d.get("activeCourses").asLong()).isEqualTo(before.get("activeCourses").asLong() + 1);
        assertThat(d.get("registrations").get("approved").asLong()).isEqualTo(1);
        assertThat(money(d.get("outstandingFees"), "amount").subtract(money(before.get("outstandingFees"), "amount")))
                .isEqualByComparingTo("750");

        JsonNode activity = d.get("registrationActivity");
        assertThat(activity).hasSize(30);
        JsonNode today = activity.get(29);
        assertThat(today.get("date").asString()).isEqualTo(todayUtc());
        assertThat(today.get("count").asLong()).isEqualTo(1);

        finalizeResult(lecturer, o, student, "25", "60");
        JsonNode recent = adminDashboard().get("recentResults").get(0);
        assertThat(recent.get("offeringId").asLong()).isEqualTo(o);
        assertThat(recent.get("students").asLong()).isEqualTo(1);
        assertThat(recent.get("passed").asLong()).isEqualTo(1);
    }

    @Test
    void financeCountsOnlyMoneyThatArrivedAndCreditDoesNotOffsetDebt() throws Exception {
        JsonNode before = financeDashboard();
        Person owing = student();
        Person inCredit = student();
        charge(owing, "9999999.00");
        pay(owing, "1000.00", unique("RCPT"));
        JsonNode afterSecond = pay(owing, "200.00", unique("RCPT"));
        long toReverse = afterSecond.get("payments").valueStream()
                .filter(p -> p.get("amount").decimalValue().compareTo(new BigDecimal("200")) == 0)
                .findFirst().orElseThrow().get("id").asLong();
        call(postJson("/api/fees/payments/" + toReverse + "/reverse", Map.of("reason", "Bounced"), finance), 200);
        pay(inCredit, "300.00", unique("RCPT"));

        JsonNode d = financeDashboard();

        assertThat(money(d, "totalCharged").subtract(money(before, "totalCharged"))).isEqualByComparingTo("9999999");
        assertThat(money(d, "totalCollected").subtract(money(before, "totalCollected"))).isEqualByComparingTo("1300");
        assertThat(money(d, "outstanding").subtract(money(before, "outstanding"))).isEqualByComparingTo("9998999");
        assertThat(d.get("studentsOwing").asLong()).isEqualTo(before.get("studentsOwing").asLong() + 1);
        assertThat(money(d, "collectedLast30Days").subtract(money(before, "collectedLast30Days"))).isEqualByComparingTo("1300");

        JsonNode weeks = d.get("weeklyCollections");
        assertThat(weeks).hasSize(12);
        assertThat(money(weeks.get(11), "amount").subtract(money(before.get("weeklyCollections").get(11), "amount")))
                .isEqualByComparingTo("1300");
        assertThat(LocalDate.parse(weeks.get(11).get("weekStart").asString()).getDayOfWeek().getValue()).isEqualTo(1);

        assertThat(d.get("methodsLast30Days").valueStream().map(m -> m.get("method").asString())).contains("BANK_TRANSFER");
        assertThat(d.get("largestBalances").get(0).get("studentNumber").asString()).isEqualTo(owing.number());
        assertThat(d.get("largestBalances").size()).isLessThanOrEqualTo(5);
    }

    @Test
    void eachDashboardIsOnlyForItsRole() throws Exception {
        Person student = student();
        Person lecturer = lecturer();

        call(get("/api/students/me/dashboard"), 401);
        call(get("/api/admin/dashboard"), 401);
        as(lecturer.auth(), get("/api/students/me/dashboard"), 403);
        as(student.auth(), get("/api/lecturer/dashboard"), 403);
        as(student.auth(), get("/api/admin/dashboard"), 403);
        as(finance, get("/api/admin/dashboard"), 403);
        as(student.auth(), get("/api/fees/dashboard"), 403);
        as(lecturer.auth(), get("/api/fees/dashboard"), 403);
        as(admin, get("/api/fees/dashboard"), 200);
    }
}
