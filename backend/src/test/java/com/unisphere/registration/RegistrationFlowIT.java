package com.unisphere.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.unisphere.academic.ProgramRepository;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import com.unisphere.student.StudentStatus;
import com.unisphere.support.IntegrationTest;
import com.unisphere.support.StubAcademicHistory;
import com.unisphere.user.IdentifierGenerator;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class RegistrationFlowIT extends IntegrationTest {

    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private IdentifierGenerator identifiers;
    @Autowired
    private StubAcademicHistory academicHistory;
    @Autowired
    private TransactionTemplate tx;

    private String admin;
    private long departmentId;
    private long programId;
    private long semesterId;

    private record StudentLogin(long studentId, String auth) {
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = adminBearer();
        departmentId = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        programId = call(postJson("/api/admin/programs", Map.of("departmentId", departmentId, "code", unique("P"),
                "name", "BSc", "awardLevel", "BACHELOR", "durationYears", 4), admin), 201).get("id").asLong();
    }

    @AfterEach
    void resetHistory() {
        academicHistory.clear();
    }

    // ---- Fixture helpers ----

    private void openSemester(boolean requiresApproval, int minCredits, int maxCredits) throws Exception {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Map<String, Object> body = new HashMap<>();
        body.put("code", unique("S"));
        body.put("name", "Semester under test");
        body.put("academicYear", today.getYear() + "/" + (today.getYear() + 1));
        body.put("term", 1);
        body.put("startDate", today.minusDays(3).toString());
        body.put("endDate", today.plusMonths(3).toString());
        body.put("registrationOpensAt", clock.instant().minus(Duration.ofDays(1)).toString());
        body.put("registrationClosesAt", clock.instant().plus(Duration.ofDays(7)).toString());
        body.put("addDropDeadline", clock.instant().plus(Duration.ofDays(14)).toString());
        body.put("requiresApproval", requiresApproval);
        body.put("minCredits", minCredits);
        body.put("maxCredits", maxCredits);
        semesterId = call(postJson("/api/admin/semesters", body, admin), 201).get("id").asLong();
        call(post("/api/admin/semesters/" + semesterId + "/make-current").header(HttpHeaders.AUTHORIZATION, admin), 200);
        call(post("/api/admin/semesters/" + semesterId + "/registration/open").header(HttpHeaders.AUTHORIZATION, admin), 200);
    }

    private long course(int credits, Long... prerequisites) throws Exception {
        long id = call(postJson("/api/admin/courses", Map.of("departmentId", departmentId, "code", unique("C"),
                "title", "Course", "credits", credits, "level", 100), admin), 201).get("id").asLong();
        if (prerequisites.length > 0) {
            call(putJson("/api/admin/courses/" + id + "/prerequisites", Map.of("prerequisiteIds", List.of(prerequisites)), admin), 200);
        }
        return id;
    }

    private long offering(long courseId, String section, int capacity, String day, String start, String end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("semesterId", semesterId);
        body.put("section", section);
        body.put("capacity", capacity);
        body.put("catWeight", 30);
        body.put("examWeight", 70);
        body.put("sessions", List.of(Map.of("dayOfWeek", day, "startTime", start, "endTime", end, "venue", unique("R"))));
        return call(postJson("/api/admin/offerings", body, admin), 201).get("id").asLong();
    }

    private StudentLogin student() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        long studentId = tx.execute(status -> studentRepository.save(new Student(user, identifiers.nextStudentNumber(2026),
                programRepository.findById(programId).orElseThrow(), 1, LocalDate.of(2026, 9, 1))).getId());
        return new StudentLogin(studentId, bearer(login(user.getEmail(), DEFAULT_PASSWORD)));
    }

    private JsonNode add(StudentLogin s, long offeringId, int expectedStatus) throws Exception {
        return call(postJson("/api/registrations/me/current/items", Map.of("offeringId", offeringId), s.auth()), expectedStatus);
    }

    private JsonNode submit(StudentLogin s, int expectedStatus) throws Exception {
        return call(post("/api/registrations/me/current/submit").header(HttpHeaders.AUTHORIZATION, s.auth()), expectedStatus);
    }

    private JsonNode as(String auth, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return call(request.header(HttpHeaders.AUTHORIZATION, auth), expectedStatus);
    }

    private int enrolled(long offeringId) throws Exception {
        return as(admin, get("/api/offerings/" + offeringId), 200).get("enrolledCount").asInt();
    }

    // ---- Tests ----

    @Test
    void studentBuildsDraftSubmitsAndSeesTimetable() throws Exception {
        openSemester(false, 6, 12);
        long a = offering(course(3), "A", 30, "MONDAY", "08:00", "10:00");
        long b = offering(course(4), "A", 30, "TUESDAY", "08:00", "10:00");
        StudentLogin s = student();

        JsonNode empty = as(s.auth(), get("/api/registrations/me/current"), 200);
        assertThat(empty.get("status").isNull()).isTrue();
        assertThat(empty.get("permissions").get("canAdd").asBoolean()).isTrue();

        add(s, a, 200);
        JsonNode draft = add(s, b, 200);
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("totalCredits").asInt()).isEqualTo(7);
        assertThat(draft.get("permissions").get("canSubmit").asBoolean()).isTrue();
        // A draft holds no seats.
        assertThat(enrolled(a)).isZero();

        JsonNode catalogue = as(s.auth(), get("/api/registrations/me/catalogue"), 200);
        assertThat(catalogue.get("content").valueStream()
                .filter(e -> e.get("offering").get("id").asLong() == a)
                .findFirst().orElseThrow().get("availability").asString()).isEqualTo("IN_REGISTRATION");

        JsonNode submitted = submit(s, 200);
        assertThat(submitted.get("status").asString()).isEqualTo("APPROVED");
        assertThat(submitted.get("items").get(0).get("seatReserved").asBoolean()).isTrue();
        assertThat(enrolled(a)).isEqualTo(1);
        assertThat(enrolled(b)).isEqualTo(1);

        JsonNode timetable = as(s.auth(), get("/api/students/me/timetable"), 200);
        assertThat(timetable.get("entries")).hasSize(2);
        assertThat(timetable.get("entries").get(0).get("dayOfWeek").asString()).isEqualTo("MONDAY");

        // Submitting again is not possible.
        assertThat(submit(s, 409).get("code").asString()).isEqualTo("INVALID_STATE");
    }

    @Test
    void registrationRulesAreEnforcedWithSpecificCodes() throws Exception {
        openSemester(false, 6, 8);
        long intro = course(3);
        long introA = offering(intro, "A", 30, "MONDAY", "08:00", "10:00");
        long introB = offering(intro, "B", 30, "FRIDAY", "08:00", "10:00");
        long advanced = offering(course(3, intro), "A", 30, "WEDNESDAY", "08:00", "10:00");
        long clashing = offering(course(3), "A", 30, "MONDAY", "09:00", "11:00");
        long heavy = offering(course(6), "A", 30, "THURSDAY", "08:00", "10:00");
        StudentLogin s = student();

        add(s, introA, 200);
        assertThat(add(s, introB, 409).get("code").asString()).isEqualTo("DUPLICATE_COURSE");
        JsonNode prerequisite = add(s, advanced, 422);
        assertThat(prerequisite.get("code").asString()).isEqualTo("PREREQUISITE_NOT_MET");
        assertThat(prerequisite.get("detail").asString()).contains("which you have not passed yet");
        assertThat(add(s, clashing, 409).get("code").asString()).isEqualTo("TIMETABLE_CLASH");
        assertThat(add(s, heavy, 422).get("code").asString()).isEqualTo("CREDIT_LIMIT");

        // Under the 6-credit minimum.
        JsonNode tooFew = submit(s, 422);
        assertThat(tooFew.get("detail").asString()).isEqualTo("You have 3 credits; at least 6 are required.");

        // Once the prerequisite is passed, the course can be added and the registration submitted.
        academicHistory.pass(s.studentId(), intro);
        add(s, advanced, 200);
        assertThat(submit(s, 200).get("totalCredits").asInt()).isEqualTo(6);
    }

    @Test
    void closedRegistrationRejectsChanges() throws Exception {
        openSemester(false, 3, 12);
        long a = offering(course(3), "A", 30, "MONDAY", "08:00", "10:00");
        StudentLogin s = student();
        call(post("/api/admin/semesters/" + semesterId + "/registration/close").header(HttpHeaders.AUTHORIZATION, admin), 200);

        JsonNode closed = add(s, a, 422);
        assertThat(closed.get("code").asString()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(as(s.auth(), get("/api/registrations/me/current"), 200).get("permissions").get("canAdd").asBoolean()).isFalse();
    }

    @Test
    void lastSeatGoesToExactlyOneOfTwoConcurrentStudents() throws Exception {
        openSemester(false, 3, 12);
        long scarce = offering(course(3), "A", 1, "MONDAY", "08:00", "10:00");
        StudentLogin first = student();
        StudentLogin second = student();
        add(first, scarce, 200);
        add(second, scarce, 200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> outcomes = new ArrayList<>();
        for (StudentLogin s : List.of(first, second)) {
            Callable<Integer> task = () -> {
                start.await();
                return mvc.perform(post("/api/registrations/me/current/submit").header(HttpHeaders.AUTHORIZATION, s.auth()))
                        .andReturn().getResponse().getStatus();
            };
            outcomes.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : outcomes) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(enrolled(scarce)).isEqualTo(1);
    }

    @Test
    void approvalWorkflowWithRejectionAndResubmission() throws Exception {
        openSemester(true, 3, 12);
        long a = offering(course(3), "A", 10, "MONDAY", "08:00", "10:00");
        long b = offering(course(3), "A", 10, "TUESDAY", "08:00", "10:00");
        StudentLogin s = student();
        add(s, a, 200);

        JsonNode pending = submit(s, 200);
        long registrationId = pending.get("id").asLong();
        assertThat(pending.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(enrolled(a)).isEqualTo(1);
        // Locked while awaiting approval.
        assertThat(add(s, b, 409).get("code").asString()).isEqualTo("INVALID_STATE");

        JsonNode queue = as(admin, get("/api/admin/registrations").param("status", "SUBMITTED"), 200);
        assertThat(queue.get("content").valueStream().anyMatch(r -> r.get("id").asLong() == registrationId)).isTrue();

        call(postJson("/api/admin/registrations/" + registrationId + "/reject", Map.of("reason", ""), admin), 400);
        JsonNode rejected = call(postJson("/api/admin/registrations/" + registrationId + "/reject",
                Map.of("reason", "Please add a second course."), admin), 200);
        assertThat(rejected.get("summary").get("status").asString()).isEqualTo("REJECTED");
        assertThat(enrolled(a)).isZero();

        JsonNode reopened = add(s, b, 200);
        assertThat(reopened.get("status").asString()).isEqualTo("DRAFT");
        submit(s, 200);
        assertThat(as(admin, post("/api/admin/registrations/" + registrationId + "/approve"), 200)
                .get("summary").get("status").asString()).isEqualTo("APPROVED");
        assertThat(enrolled(a)).isEqualTo(1);
        assertThat(enrolled(b)).isEqualTo(1);
        // Decisions are only possible on pending registrations.
        as(admin, post("/api/admin/registrations/" + registrationId + "/approve"), 409);
    }

    @Test
    void addAndDropAfterSubmissionMoveSeatsUntilTheDeadline() throws Exception {
        openSemester(false, 6, 12);
        long a = offering(course(3), "A", 10, "MONDAY", "08:00", "10:00");
        long b = offering(course(3), "A", 10, "TUESDAY", "08:00", "10:00");
        long c = offering(course(3), "A", 10, "WEDNESDAY", "08:00", "10:00");
        StudentLogin s = student();
        add(s, a, 200);
        add(s, b, 200);
        submit(s, 200);

        // Adding after submission takes a seat immediately.
        JsonNode withC = add(s, c, 200);
        assertThat(enrolled(c)).isEqualTo(1);
        long itemA = withC.get("items").valueStream().filter(i -> i.get("offeringId").asLong() == a).findFirst().orElseThrow()
                .get("id").asLong();
        long itemB = withC.get("items").valueStream().filter(i -> i.get("offeringId").asLong() == b).findFirst().orElseThrow()
                .get("id").asLong();

        JsonNode dropped = as(s.auth(), delete("/api/registrations/me/current/items/" + itemA), 200);
        assertThat(dropped.get("droppedItems")).hasSize(1);
        assertThat(enrolled(a)).isZero();
        // 6 credits left; dropping again would go below the minimum.
        assertThat(as(s.auth(), delete("/api/registrations/me/current/items/" + itemB), 422).get("code").asString())
                .isEqualTo("CREDIT_LIMIT");

        clock.advance(Duration.ofDays(15));
        assertThat(add(s, a, 422).get("code").asString()).isEqualTo("ADD_DROP_DEADLINE_PASSED");
    }

    @Test
    void studentsOnlyEverTouchTheirOwnRegistration() throws Exception {
        openSemester(false, 3, 12);
        long a = offering(course(3), "A", 10, "MONDAY", "08:00", "10:00");
        StudentLogin owner = student();
        StudentLogin other = student();
        long itemId = add(owner, a, 200).get("items").get(0).get("id").asLong();

        as(other.auth(), delete("/api/registrations/me/current/items/" + itemId), 404);
        as(other.auth(), get("/api/admin/registrations"), 403);
        // Staff have no student registration.
        as(admin, get("/api/registrations/me/current"), 403);
    }

    @Test
    void suspendedStudentsCannotRegister() throws Exception {
        openSemester(false, 3, 12);
        long a = offering(course(3), "A", 10, "MONDAY", "08:00", "10:00");
        StudentLogin s = student();
        call(putJson("/api/admin/students/" + s.studentId(),
                Map.of("programId", programId, "yearOfStudy", 1, "status", StudentStatus.SUSPENDED.name()), admin), 200);

        assertThat(add(s, a, 403).get("code").asString()).isEqualTo("STUDENT_NOT_ELIGIBLE");
    }
}
