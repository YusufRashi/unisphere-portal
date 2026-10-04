package com.unisphere.offering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

class SemesterAndOfferingIT extends IntegrationTest {

    private String admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = adminBearer();
    }

    /** A semester running from today for ~3 months, registration open around now. */
    private Map<String, Object> semesterBody(String code) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Semester " + code);
        body.put("academicYear", today.getYear() + "/" + (today.getYear() + 1));
        body.put("term", 1);
        body.put("startDate", today.minusDays(1).toString());
        body.put("endDate", today.plusMonths(3).toString());
        body.put("registrationOpensAt", clock.instant().minusSeconds(86_400).toString());
        body.put("registrationClosesAt", clock.instant().plusSeconds(14 * 86_400).toString());
        body.put("addDropDeadline", clock.instant().plusSeconds(21 * 86_400).toString());
        body.put("requiresApproval", false);
        body.put("minCredits", 12);
        body.put("maxCredits", 24);
        return body;
    }

    private long createSemester() throws Exception {
        return call(postJson("/api/admin/semesters", semesterBody(unique("S")), admin), 201).get("id").asLong();
    }

    private long createCourse() throws Exception {
        long dept = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        return call(postJson("/api/admin/courses", Map.of("departmentId", dept, "code", unique("C"), "title", "Course",
                "credits", 3, "level", 100), admin), 201).get("id").asLong();
    }

    private long createLecturer() throws Exception {
        long dept = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        return call(postJson("/api/admin/lecturers", Map.of("firstName", "Grace", "lastName", "Hopper",
                "email", unique("gh") + "@uni.test", "departmentId", dept, "title", "Dr."), admin), 201).get("id").asLong();
    }

    private Map<String, Object> offeringBody(long courseId, long semesterId, String section, Long lecturerId,
                                             String day, String start, String end, String venue) {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("semesterId", semesterId);
        body.put("section", section);
        body.put("lecturerId", lecturerId);
        body.put("capacity", 40);
        body.put("catWeight", 30);
        body.put("examWeight", 70);
        body.put("sessions", List.of(Map.of("dayOfWeek", day, "startTime", start, "endTime", end, "venue", venue)));
        return body;
    }

    @Test
    void semesterDateRulesProduceFieldErrors() throws Exception {
        Map<String, Object> body = semesterBody(unique("S"));
        body.put("endDate", "2000-01-01");
        body.put("maxCredits", 6);
        JsonNode problem = call(postJson("/api/admin/semesters", body, admin), 400);
        List<String> fields = problem.get("errors").valueStream().map(e -> e.get("field").asString()).toList();
        assertThat(fields).contains("endDate", "maxCredits");
    }

    @Test
    void onlyOneSemesterIsCurrentAndRegistrationNeedsFlagAndWindow() throws Exception {
        long first = createSemester();
        long second = createSemester();

        call(post("/api/admin/semesters/" + first + "/make-current").header(HttpHeaders.AUTHORIZATION, admin), 200);
        JsonNode current = call(post("/api/admin/semesters/" + second + "/make-current")
                .header(HttpHeaders.AUTHORIZATION, admin), 200);
        assertThat(current.get("current").asBoolean()).isTrue();
        assertThat(call(get("/api/semesters/" + first).header(HttpHeaders.AUTHORIZATION, admin), 200)
                .get("current").asBoolean()).isFalse();
        assertThat(call(get("/api/semesters/current").header(HttpHeaders.AUTHORIZATION, admin), 200)
                .get("id").asLong()).isEqualTo(second);

        JsonNode closed = call(get("/api/semesters/" + second).header(HttpHeaders.AUTHORIZATION, admin), 200);
        assertThat(closed.get("registrationOpen").asBoolean()).isFalse();
        JsonNode opened = call(post("/api/admin/semesters/" + second + "/registration/open")
                .header(HttpHeaders.AUTHORIZATION, admin), 200);
        assertThat(opened.get("registrationOpen").asBoolean()).isTrue();
        assertThat(opened.get("status").asString()).isEqualTo("REGISTRATION_OPEN");
        assertThat(call(post("/api/admin/semesters/" + second + "/registration/close")
                .header(HttpHeaders.AUTHORIZATION, admin), 200).get("registrationOpen").asBoolean()).isFalse();
    }

    @Test
    void offeringLifecycleWithTimetableAndCapacityRules() throws Exception {
        long semester = createSemester();
        long courseA = createCourse();
        long courseB = createCourse();
        long lecturer = createLecturer();
        String venue = unique("LT");

        JsonNode created = call(postJson("/api/admin/offerings",
                offeringBody(courseA, semester, "A", lecturer, "MONDAY", "08:00", "10:00", venue), admin), 201);
        long offeringId = created.get("id").asLong();
        assertThat(created.get("seatsLeft").asInt()).isEqualTo(40);
        assertThat(created.get("lecturer").get("displayName").asString()).isEqualTo("Dr. Grace Hopper");
        assertThat(created.get("sessions").get(0).get("startTime").asString()).startsWith("08:00");

        // Same course, same section, same semester.
        assertThat(call(postJson("/api/admin/offerings",
                offeringBody(courseA, semester, "a", null, "FRIDAY", "08:00", "10:00", unique("V")), admin), 409)
                .get("code").asString()).isEqualTo("DUPLICATE_RESOURCE");

        // Another course in the same room at an overlapping time.
        JsonNode venueClash = call(postJson("/api/admin/offerings",
                offeringBody(courseB, semester, "A", null, "MONDAY", "09:00", "11:00", venue), admin), 409);
        assertThat(venueClash.get("code").asString()).isEqualTo("SCHEDULE_CONFLICT");
        assertThat(venueClash.get("detail").asString()).contains("already booked");

        // Same lecturer elsewhere at an overlapping time.
        JsonNode lecturerClash = call(postJson("/api/admin/offerings",
                offeringBody(courseB, semester, "A", lecturer, "MONDAY", "09:30", "10:30", unique("V")), admin), 409);
        assertThat(lecturerClash.get("detail").asString()).contains("lecturer already teaches");

        // Weights must add up to 100.
        Map<String, Object> badWeights = offeringBody(courseB, semester, "A", null, "TUESDAY", "08:00", "10:00", unique("V"));
        badWeights.put("examWeight", 60);
        assertThat(call(postJson("/api/admin/offerings", badWeights, admin), 400).get("errors").get(0).get("field").asString())
                .isEqualTo("examWeight");

        // Editing the offering itself does not clash with its own sessions.
        call(putJson("/api/admin/offerings/" + offeringId,
                offeringBody(courseA, semester, "A", lecturer, "MONDAY", "08:30", "10:30", venue), admin), 200);

        mvc.perform(get("/api/offerings").param("semesterId", String.valueOf(semester)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].course.id").value(courseA));

        mvc.perform(delete("/api/admin/offerings/" + offeringId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void inactiveCoursesCannotBeOffered() throws Exception {
        long semester = createSemester();
        long dept = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        long course = call(postJson("/api/admin/courses", Map.of("departmentId", dept, "code", unique("C"), "title", "Old",
                "credits", 3, "level", 100, "active", false), admin), 201).get("id").asLong();

        JsonNode problem = call(postJson("/api/admin/offerings",
                offeringBody(course, semester, "A", null, "MONDAY", "08:00", "10:00", unique("V")), admin), 400);
        assertThat(problem.get("errors").get(0).get("field").asString()).isEqualTo("courseId");
    }
}
