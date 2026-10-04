package com.unisphere.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.unisphere.academic.DepartmentRepository;
import com.unisphere.academic.ProgramRepository;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.lecturer.LecturerRepository;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import com.unisphere.support.IntegrationTest;
import com.unisphere.user.IdentifierGenerator;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Marks entry → submission → return/finalization → student results, against real PostgreSQL. Registration
 * uses the test stub for prerequisites, so the real {@link ResultsAcademicHistory} is checked directly.
 */
class ResultsFlowIT extends IntegrationTest {

    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LecturerRepository lecturerRepository;
    @Autowired
    private IdentifierGenerator identifiers;
    @Autowired
    private ResultsAcademicHistory resultsHistory;
    @Autowired
    private TransactionTemplate tx;

    private String admin;
    private long departmentId;
    private long programId;
    private long semesterId;

    private record Person(long id, String number, String auth) {
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = adminBearer();
        departmentId = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        programId = call(postJson("/api/admin/programs", Map.of("departmentId", departmentId, "code", unique("P"),
                "name", "BSc", "awardLevel", "BACHELOR", "durationYears", 4), admin), 201).get("id").asLong();
        openSemester();
    }

    // ---- Fixtures ----

    private void openSemester() throws Exception {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Map<String, Object> body = new HashMap<>();
        body.put("code", unique("S"));
        body.put("name", "Results semester");
        body.put("academicYear", today.getYear() + "/" + (today.getYear() + 1));
        body.put("term", 1);
        body.put("startDate", today.minusDays(3).toString());
        body.put("endDate", today.plusMonths(3).toString());
        body.put("registrationOpensAt", clock.instant().minus(Duration.ofDays(1)).toString());
        body.put("registrationClosesAt", clock.instant().plus(Duration.ofDays(7)).toString());
        body.put("addDropDeadline", clock.instant().plus(Duration.ofDays(14)).toString());
        body.put("requiresApproval", false);
        body.put("minCredits", 0);
        body.put("maxCredits", 24);
        semesterId = call(postJson("/api/admin/semesters", body, admin), 201).get("id").asLong();
        as(admin, post("/api/admin/semesters/" + semesterId + "/make-current"), 200);
        as(admin, post("/api/admin/semesters/" + semesterId + "/registration/open"), 200);
    }

    private Person lecturer() throws Exception {
        User user = createActiveUser(Role.LECTURER);
        long id = tx.execute(status -> lecturerRepository.save(new Lecturer(user, identifiers.nextStaffNumber(),
                departmentRepository.findById(departmentId).orElseThrow(), "Dr.", null)).getId());
        return new Person(id, null, bearer(login(user.getEmail(), DEFAULT_PASSWORD)));
    }

    private Person student() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        Student s = tx.execute(status -> studentRepository.save(new Student(user, identifiers.nextStudentNumber(2026),
                programRepository.findById(programId).orElseThrow(), 1, LocalDate.of(2026, 9, 1))));
        return new Person(s.getId(), s.getStudentNumber(), bearer(login(user.getEmail(), DEFAULT_PASSWORD)));
    }

    private long course(int credits) throws Exception {
        return call(postJson("/api/admin/courses", Map.of("departmentId", departmentId, "code", unique("C"),
                "title", "Course", "credits", credits, "level", 100), admin), 201).get("id").asLong();
    }

    private Map<String, Object> offeringBody(long courseId, long lecturerId, int catWeight, String day) {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("semesterId", semesterId);
        body.put("section", "A");
        body.put("lecturerId", lecturerId);
        body.put("capacity", 30);
        body.put("catWeight", catWeight);
        body.put("examWeight", 100 - catWeight);
        body.put("sessions", List.of(Map.of("dayOfWeek", day, "startTime", "08:00", "endTime", "10:00", "venue", unique("R"))));
        return body;
    }

    private long offering(long courseId, Person lecturer, String day) throws Exception {
        return call(postJson("/api/admin/offerings", offeringBody(courseId, lecturer.id(), 30, day), admin), 201).get("id").asLong();
    }

    /** Registers the student for the offerings and submits (no approval needed in this semester). */
    private void register(Person student, long... offeringIds) throws Exception {
        for (long id : offeringIds) {
            call(postJson("/api/registrations/me/current/items", Map.of("offeringId", id), student.auth()), 200);
        }
        assertThat(as(student.auth(), post("/api/registrations/me/current/submit"), 200).get("status").asString())
                .isEqualTo("APPROVED");
    }

    private JsonNode as(String auth, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return call(request.header(HttpHeaders.AUTHORIZATION, auth), expectedStatus);
    }

    private JsonNode sheet(String auth, long offeringId, int expectedStatus) throws Exception {
        return as(auth, get("/api/lecturer/offerings/" + offeringId + "/results"), expectedStatus);
    }

    /** registrationItemId of each student on the sheet, keyed by student number. */
    private Map<String, Long> itemIds(JsonNode sheet) {
        Map<String, Long> ids = new HashMap<>();
        sheet.get("rows").forEach(r -> ids.put(r.get("studentNumber").asString(), r.get("registrationItemId").asLong()));
        return ids;
    }

    private static Map<String, Object> mark(long itemId, String cat, String exam) {
        Map<String, Object> m = new HashMap<>();
        m.put("registrationItemId", itemId);
        m.put("catMark", cat == null ? null : new BigDecimal(cat));
        m.put("examMark", exam == null ? null : new BigDecimal(exam));
        return m;
    }

    private JsonNode saveMarks(String auth, long offeringId, int expectedStatus, Map<?, ?>... entries) throws Exception {
        return call(putJson("/api/lecturer/offerings/" + offeringId + "/results", Map.of("entries", List.of(entries)), auth),
                expectedStatus);
    }

    private JsonNode row(JsonNode sheet, Person student) {
        return sheet.get("rows").valueStream()
                .filter(r -> r.get("studentNumber").asString().equals(student.number()))
                .findFirst().orElseThrow();
    }

    // ---- Tests ----

    @Test
    void marksFlowFromLecturerThroughRegistrarToStudent() throws Exception {
        Person lecturer = lecturer();
        long programming = course(3);
        long maths = course(4);
        long o1 = offering(programming, lecturer, "MONDAY");
        long o2 = offering(maths, lecturer, "TUESDAY");
        Person amina = student();
        Person brian = student();
        Person chen = student();
        register(amina, o1, o2);
        register(brian, o1);
        register(chen, o1);

        JsonNode empty = sheet(lecturer.auth(), o1, 200);
        assertThat(empty.get("classSize").asInt()).isEqualTo(3);
        assertThat(empty.get("complete").asInt()).isZero();
        assertThat(empty.get("permissions").get("canEdit").asBoolean()).isTrue();
        assertThat(empty.get("permissions").get("canSubmit").asBoolean()).isFalse();
        Map<String, Long> items = itemIds(empty);

        // A partial save is a draft: incomplete rows are allowed, complete ones get a grade preview.
        JsonNode draft = saveMarks(lecturer.auth(), o1, 200,
                mark(items.get(amina.number()), "25", "60"), mark(items.get(brian.number()), "20", null));
        assertThat(draft.get("complete").asInt()).isEqualTo(1);
        assertThat(row(draft, amina).get("totalMark").decimalValue()).isEqualByComparingTo("85");
        assertThat(row(draft, amina).get("grade").asString()).isEqualTo("A");
        assertThat(row(draft, brian).get("totalMark").isNull()).isTrue();

        JsonNode incomplete = as(lecturer.auth(), post("/api/lecturer/offerings/" + o1 + "/results/submit"), 422);
        assertThat(incomplete.get("code").asString()).isEqualTo("MARKS_INCOMPLETE");
        assertThat(incomplete.get("detail").asString()).startsWith("2 of 3 students");

        // Students never see marks before finalization.
        JsonNode before = as(amina.auth(), get("/api/results/me"), 200);
        assertThat(before.get("semesters")).isEmpty();
        assertThat(before.get("cgpa").isNull()).isTrue();

        saveMarks(lecturer.auth(), o1, 200, mark(items.get(brian.number()), "20", "30"), mark(items.get(chen.number()), "10", "25"));
        JsonNode submitted = as(lecturer.auth(), post("/api/lecturer/offerings/" + o1 + "/results/submit"), 200);
        assertThat(submitted.get("offering").get("resultsStatus").asString()).isEqualTo("SUBMITTED");
        assertThat(submitted.get("permissions").get("canEdit").asBoolean()).isFalse();
        assertThat(saveMarks(lecturer.auth(), o1, 409, mark(items.get(chen.number()), "30", "70")).get("code").asString())
                .isEqualTo("INVALID_STATE");

        // The registrar returns the marks with a comment; the lecturer can edit again and resubmit.
        JsonNode returned = as(admin, postJson("/api/admin/offerings/" + o1 + "/results/return",
                Map.of("comment", "Please re-check Chen's exam script."), null), 200);
        assertThat(returned.get("offering").get("resultsStatus").asString()).isEqualTo("DRAFT");
        assertThat(sheet(lecturer.auth(), o1, 200).get("offering").get("returnComment").asString())
                .isEqualTo("Please re-check Chen's exam script.");
        saveMarks(lecturer.auth(), o1, 200, mark(items.get(chen.number()), "10", "26"));
        JsonNode resubmitted = as(lecturer.auth(), post("/api/lecturer/offerings/" + o1 + "/results/submit"), 200);
        assertThat(resubmitted.get("offering").get("returnComment").isNull()).isTrue();

        JsonNode finalized = as(admin, post("/api/admin/offerings/" + o1 + "/results/finalize"), 200);
        assertThat(finalized.get("offering").get("resultsStatus").asString()).isEqualTo("FINALIZED");
        assertThat(row(finalized, amina).get("grade").asString()).isEqualTo("A");
        assertThat(row(finalized, brian).get("grade").asString()).isEqualTo("C");
        assertThat(row(finalized, chen).get("grade").asString()).isEqualTo("F");
        assertThat(row(finalized, chen).get("passed").asBoolean()).isFalse();
        assertThat(as(admin, post("/api/admin/offerings/" + o1 + "/results/finalize"), 409).get("code").asString())
                .isEqualTo("INVALID_STATE");

        // Class statistics: totals 85, 50, 36.
        JsonNode stats = as(lecturer.auth(), get("/api/lecturer/offerings/" + o1 + "/statistics"), 200);
        assertThat(stats.get("graded").asInt()).isEqualTo(3);
        assertThat(stats.get("mean").decimalValue()).isEqualByComparingTo("57.00");
        assertThat(stats.get("median").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(stats.get("passRate").decimalValue()).isEqualByComparingTo("66.7");

        // Second course for Amina: 15 + 45 = 60 → B (3.0).
        long mathsItem = itemIds(sheet(lecturer.auth(), o2, 200)).get(amina.number());
        saveMarks(lecturer.auth(), o2, 200, mark(mathsItem, "15", "45"));
        as(lecturer.auth(), post("/api/lecturer/offerings/" + o2 + "/results/submit"), 200);
        as(admin, post("/api/admin/offerings/" + o2 + "/results/finalize"), 200);

        // GPA = (3×4.0 + 4×3.0) / 7 = 24 / 7 = 3.428… → 3.43
        JsonNode record = as(amina.auth(), get("/api/results/me"), 200);
        assertThat(record.get("semesters")).hasSize(1);
        JsonNode semester = record.get("semesters").get(0);
        assertThat(semester.get("courses")).hasSize(2);
        assertThat(semester.get("gpa").decimalValue()).isEqualByComparingTo("3.43");
        assertThat(semester.get("creditsEarned").asInt()).isEqualTo(7);
        assertThat(record.get("cgpa").decimalValue()).isEqualByComparingTo("3.43");

        JsonNode chenRecord = as(chen.auth(), get("/api/results/me"), 200);
        assertThat(chenRecord.get("cgpa").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(chenRecord.get("creditsAttempted").asInt()).isEqualTo(3);
        assertThat(chenRecord.get("creditsEarned").asInt()).isZero();
        // The registrar can see any student's record.
        assertThat(as(admin, get("/api/admin/students/" + chen.id() + "/results"), 200).get("student").get("studentNumber").asString())
                .isEqualTo(chen.number());

        // Finalized passes satisfy prerequisites; fails do not.
        assertThat(resultsHistory.passedCourseIds(amina.id())).containsExactlyInAnyOrder(programming, maths);
        assertThat(resultsHistory.passedCourseIds(chen.id())).isEmpty();

        // The lecturer's course list shows progress.
        JsonNode mine = as(lecturer.auth(), get("/api/lecturer/offerings"), 200);
        JsonNode first = mine.valueStream().filter(t -> t.get("offering").get("id").asLong() == o1).findFirst().orElseThrow();
        assertThat(first.get("classSize").asInt()).isEqualTo(3);
        assertThat(first.get("marksComplete").asInt()).isEqualTo(3);
        assertThat(first.get("offering").get("resultsStatus").asString()).isEqualTo("FINALIZED");

        // The registrar's queue filters by status.
        JsonNode queue = as(admin, get("/api/admin/results").param("semesterId", String.valueOf(semesterId))
                .param("status", "FINALIZED"), 200);
        assertThat(queue.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void finalizedGradesDoNotChangeWhenTheScaleChanges() throws Exception {
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "WEDNESDAY");
        Person student = student();
        register(student, o);
        long item = itemIds(sheet(lecturer.auth(), o, 200)).get(student.number());
        saveMarks(lecturer.auth(), o, 200, mark(item, "22", "50")); // 72 → A on the standard scale
        as(lecturer.auth(), post("/api/lecturer/offerings/" + o + "/results/submit"), 200);
        as(admin, post("/api/admin/offerings/" + o + "/results/finalize"), 200);

        JsonNode original = as(admin, get("/api/grading-scale"), 200);
        try {
            List<Map<String, Object>> stricter = List.of(
                    Map.of("letter", "A", "minMark", 80, "gradePoints", 4, "passing", true),
                    Map.of("letter", "B", "minMark", 60, "gradePoints", 3, "passing", true),
                    Map.of("letter", "C", "minMark", 50, "gradePoints", 2, "passing", true),
                    Map.of("letter", "F", "minMark", 0, "gradePoints", 0, "passing", false));
            JsonNode replaced = call(putJson("/api/admin/grading-scale", Map.of("bands", stricter), admin), 200);
            assertThat(replaced.get("bands")).hasSize(4);
            assertThat(replaced.get("bands").get(0).get("minMark").decimalValue()).isEqualByComparingTo("80");

            JsonNode course = as(student.auth(), get("/api/results/me"), 200).get("semesters").get(0).get("courses").get(0);
            assertThat(course.get("grade").asString()).isEqualTo("A");
            assertThat(course.get("gradePoints").decimalValue()).isEqualByComparingTo("4.00");
        } finally {
            List<Map<String, Object>> restore = original.get("bands").valueStream()
                    .map(b -> Map.<String, Object>of("letter", b.get("letter").asString(), "minMark", b.get("minMark").decimalValue(),
                            "gradePoints", b.get("gradePoints").decimalValue(), "passing", b.get("passing").asBoolean()))
                    .toList();
            call(putJson("/api/admin/grading-scale", Map.of("bands", restore), admin), 200);
        }
    }

    @Test
    void anInvalidScaleIsRejectedWithEveryProblem() throws Exception {
        JsonNode problem = call(putJson("/api/admin/grading-scale", Map.of("bands", List.of(
                Map.of("letter", "A", "minMark", 50, "gradePoints", 4, "passing", true),
                Map.of("letter", "B", "minMark", 10, "gradePoints", 3, "passing", true))), admin), 400);
        assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem.get("errors").valueStream().map(e -> e.get("message").asString()))
                .contains("At least one band must be a fail.",
                        "The lowest band must start at 0 so every mark gets a grade (it starts at 10).");

        Person student = student();
        assertThat(as(student.auth(), get("/api/grading-scale"), 200).get("bands")).hasSize(7);
        as(student.auth(), put("/api/admin/grading-scale"), 403);
    }

    @Test
    void onlyTheAssignedLecturerEntersMarks() throws Exception {
        Person lecturer = lecturer();
        Person otherLecturer = lecturer();
        long o = offering(course(3), lecturer, "THURSDAY");
        Person student = student();
        register(student, o);
        long item = itemIds(sheet(lecturer.auth(), o, 200)).get(student.number());

        assertThat(sheet(otherLecturer.auth(), o, 403).get("detail").asString()).startsWith("You do not teach");
        saveMarks(otherLecturer.auth(), o, 403, mark(item, "10", "10"));
        as(otherLecturer.auth(), post("/api/lecturer/offerings/" + o + "/results/submit"), 403);
        as(otherLecturer.auth(), get("/api/lecturer/offerings/" + o + "/statistics"), 403);

        // Registrars can look but not type marks in.
        JsonNode adminView = sheet(admin, o, 200);
        assertThat(adminView.get("permissions").get("canEdit").asBoolean()).isFalse();
        assertThat(adminView.get("permissions").get("lockedReason").asString()).contains("Only the lecturer");
        saveMarks(admin, o, 403, mark(item, "10", "10"));

        // Students reach none of it, and lecturers cannot finalize.
        sheet(student.auth(), o, 403);
        as(student.auth(), get("/api/lecturer/offerings"), 403);
        saveMarks(student.auth(), o, 403, mark(item, "30", "70"));
        as(lecturer.auth(), post("/api/admin/offerings/" + o + "/results/finalize"), 403);
        as(lecturer.auth(), get("/api/results/me"), 403);
        call(get("/api/lecturer/offerings/" + o + "/results"), 401);

        assertThat(sheet(lecturer.auth(), o, 200).get("complete").asInt()).isZero();
    }

    @Test
    void marksAreValidatedAgainstTheOfferingWeights() throws Exception {
        Person lecturer = lecturer();
        long courseId = course(3);
        long o = offering(courseId, lecturer, "FRIDAY");
        Person student = student();
        register(student, o);
        long item = itemIds(sheet(lecturer.auth(), o, 200)).get(student.number());

        JsonNode tooHigh = saveMarks(lecturer.auth(), o, 400, mark(item, "30.5", "70"), mark(987654321L, "1", "1"));
        assertThat(tooHigh.get("errors").valueStream().map(e -> e.get("field").asString() + " " + e.get("message").asString()))
                .containsExactlyInAnyOrder("entries[0].catMark must be at most 30",
                        "entries[1].registrationItemId is not a student registered in this class");
        JsonNode negative = saveMarks(lecturer.auth(), o, 400, mark(item, "10", "-1"));
        assertThat(negative.get("errors").get(0).get("field").asString()).isEqualTo("entries[0].examMark");
        // Nothing from a rejected request is stored.
        assertThat(sheet(lecturer.auth(), o, 200).get("complete").asInt()).isZero();

        // Once marks exist, the weights they were entered against are fixed.
        saveMarks(lecturer.auth(), o, 200, mark(item, "30", "70"));
        Map<String, Object> reweighted = offeringBody(courseId, lecturer.id(), 40, "FRIDAY");
        reweighted.put("sessions", List.of(Map.of("dayOfWeek", "FRIDAY", "startTime", "08:00", "endTime", "10:00",
                "venue", as(admin, get("/api/offerings/" + o), 200).get("sessions").get(0).get("venue").asString())));
        assertThat(call(putJson("/api/admin/offerings/" + o, reweighted, admin), 400).get("errors").get(0).get("field").asString())
                .isEqualTo("catWeight");

        // After submission the offering cannot be edited at all.
        as(lecturer.auth(), post("/api/lecturer/offerings/" + o + "/results/submit"), 200);
        reweighted.put("catWeight", 30);
        reweighted.put("examWeight", 70);
        reweighted.put("capacity", 40);
        assertThat(call(putJson("/api/admin/offerings/" + o, reweighted, admin), 409).get("code").asString())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void theClassListFreezesOnceResultsAreSubmitted() throws Exception {
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "SATURDAY");
        long other = offering(course(3), lecturer, "SUNDAY");
        Person enrolled = student();
        register(enrolled, o, other);
        long item = itemIds(sheet(lecturer.auth(), o, 200)).get(enrolled.number());
        saveMarks(lecturer.auth(), o, 200, mark(item, "20", "40"));
        as(lecturer.auth(), post("/api/lecturer/offerings/" + o + "/results/submit"), 200);

        long registrationItem = as(enrolled.auth(), get("/api/registrations/me/current"), 200).get("items").valueStream()
                .filter(i -> i.get("offeringId").asLong() == o).findFirst().orElseThrow().get("id").asLong();
        JsonNode drop = as(enrolled.auth(), delete("/api/registrations/me/current/items/" + registrationItem), 409);
        assertThat(drop.get("detail").asString()).contains("class list can no longer change");

        Person latecomer = student();
        JsonNode add = call(postJson("/api/registrations/me/current/items", Map.of("offeringId", o), latecomer.auth()), 409);
        assertThat(add.get("detail").asString()).contains("class list can no longer change");
    }
}
