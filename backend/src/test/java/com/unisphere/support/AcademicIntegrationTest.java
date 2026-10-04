package com.unisphere.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.unisphere.academic.DepartmentRepository;
import com.unisphere.academic.ProgramRepository;
import com.unisphere.lecturer.Lecturer;
import com.unisphere.lecturer.LecturerRepository;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Integration tests that need an academic setting: a fresh department, program and current semester with
 * open registration for every test, plus helpers to create lecturers, students, offerings and results.
 */
public abstract class AcademicIntegrationTest extends IntegrationTest {

    @Autowired
    protected StudentRepository studentRepository;
    @Autowired
    protected ProgramRepository programRepository;
    @Autowired
    protected DepartmentRepository departmentRepository;
    @Autowired
    protected LecturerRepository lecturerRepository;
    @Autowired
    protected IdentifierGenerator identifiers;
    @Autowired
    protected TransactionTemplate tx;

    protected String admin;
    protected long departmentId;
    protected long programId;
    protected long semesterId;

    protected record Person(long id, String number, String auth) {
    }

    @BeforeEach
    void setUpAcademicStructure() throws Exception {
        admin = adminBearer();
        departmentId = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        programId = call(postJson("/api/admin/programs", Map.of("departmentId", departmentId, "code", unique("P"),
                "name", "BSc", "awardLevel", "BACHELOR", "durationYears", 4), admin), 201).get("id").asLong();
        openSemester();
    }

    // ---- Fixtures ----

    protected void openSemester() throws Exception {
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

    protected Person lecturer() throws Exception {
        User user = createActiveUser(Role.LECTURER);
        long id = tx.execute(status -> lecturerRepository.save(new Lecturer(user, identifiers.nextStaffNumber(),
                departmentRepository.findById(departmentId).orElseThrow(), "Dr.", null)).getId());
        return new Person(id, null, bearer(login(user.getEmail(), DEFAULT_PASSWORD)));
    }

    protected Person student() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        Student s = tx.execute(status -> studentRepository.save(new Student(user, identifiers.nextStudentNumber(2026),
                programRepository.findById(programId).orElseThrow(), 1, LocalDate.of(2026, 9, 1))));
        return new Person(s.getId(), s.getStudentNumber(), bearer(login(user.getEmail(), DEFAULT_PASSWORD)));
    }

    protected long course(int credits) throws Exception {
        return call(postJson("/api/admin/courses", Map.of("departmentId", departmentId, "code", unique("C"),
                "title", "Course", "credits", credits, "level", 100), admin), 201).get("id").asLong();
    }

    protected Map<String, Object> offeringBody(long courseId, long lecturerId, int catWeight, String day) {
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

    protected long offering(long courseId, Person lecturer, String day) throws Exception {
        return call(postJson("/api/admin/offerings", offeringBody(courseId, lecturer.id(), 30, day), admin), 201).get("id").asLong();
    }

    /** Registers the student for the offerings and submits (no approval needed in this semester). */
    protected void register(Person student, long... offeringIds) throws Exception {
        for (long id : offeringIds) {
            call(postJson("/api/registrations/me/current/items", Map.of("offeringId", id), student.auth()), 200);
        }
        assertThat(as(student.auth(), post("/api/registrations/me/current/submit"), 200).get("status").asString())
                .isEqualTo("APPROVED");
    }

    protected JsonNode as(String auth, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return call(request.header(HttpHeaders.AUTHORIZATION, auth), expectedStatus);
    }

    protected JsonNode sheet(String auth, long offeringId, int expectedStatus) throws Exception {
        return as(auth, get("/api/lecturer/offerings/" + offeringId + "/results"), expectedStatus);
    }

    /** registrationItemId of each student on the sheet, keyed by student number. */
    protected Map<String, Long> itemIds(JsonNode sheet) {
        Map<String, Long> ids = new HashMap<>();
        sheet.get("rows").forEach(r -> ids.put(r.get("studentNumber").asString(), r.get("registrationItemId").asLong()));
        return ids;
    }

    protected static Map<String, Object> mark(long itemId, String cat, String exam) {
        Map<String, Object> m = new HashMap<>();
        m.put("registrationItemId", itemId);
        m.put("catMark", cat == null ? null : new BigDecimal(cat));
        m.put("examMark", exam == null ? null : new BigDecimal(exam));
        return m;
    }

    protected JsonNode saveMarks(String auth, long offeringId, int expectedStatus, Map<?, ?>... entries) throws Exception {
        return call(putJson("/api/lecturer/offerings/" + offeringId + "/results", Map.of("entries", List.of(entries)), auth),
                expectedStatus);
    }

    protected JsonNode row(JsonNode sheet, Person student) {
        return sheet.get("rows").valueStream()
                .filter(r -> r.get("studentNumber").asString().equals(student.number()))
                .findFirst().orElseThrow();
    }

    /** Enters marks for one student, submits and finalizes the offering's results. */
    protected void finalizeResult(Person lecturer, long offeringId, Person student, String cat, String exam) throws Exception {
        long item = itemIds(sheet(lecturer.auth(), offeringId, 200)).get(student.number());
        saveMarks(lecturer.auth(), offeringId, 200, mark(item, cat, exam));
        as(lecturer.auth(), post("/api/lecturer/offerings/" + offeringId + "/results/submit"), 200);
        as(admin, post("/api/admin/offerings/" + offeringId + "/results/finalize"), 200);
    }
}
