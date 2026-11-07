package com.unisphere.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.audit.AuditService;
import com.unisphere.common.config.UniSphereProperties;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the demo data into a database of its own (the seeder only runs on an empty one) and reads it back through
 * the real API, so a change to a service that the seeded data no longer satisfies fails here.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2Q=",
        "COOKIE_SECURE=false",
        "AUTH_RATE_LIMIT_PER_MINUTE=1000",
        "NOTIFICATION_REMINDER_CRON=-",
        "DEMO_PASSWORD=" + DemoDataSeederIT.PASSWORD,
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoDataSeederIT {

    static final String PASSWORD = "Campus-tour-2026";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DemoDataSeeder seeder;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditService audit;
    @Autowired
    private UniSphereProperties properties;
    @Autowired
    private Clock clock;

    private String signIn(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private JsonNode getJson(String auth, String url) throws Exception {
        String body = mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private void postOk(String auth, String url) throws Exception {
        mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    private long offeringOf(String courseCode) {
        return jdbc.sql("""
                        select o.id from course_offerings o join courses c on c.id = o.course_id join semesters s on s.id = o.semester_id
                        where c.code = :code and s.is_current""")
                .param("code", courseCode).query(Long.class).single();
    }

    @Test
    void studentHasAHistoryATermInProgressAndABalance() throws Exception {
        String student = signIn(DemoDataSeeder.STUDENT_EMAIL);

        JsonNode d = getJson(student, "/api/students/me/dashboard");
        assertThat(d.get("cgpa").decimalValue().signum()).isPositive();
        assertThat(d.get("gpaTrend")).hasSize(3);
        assertThat(d.get("gpaTrend").get(2).get("gpa").decimalValue()).isGreaterThan(d.get("gpaTrend").get(0).get("gpa").decimalValue());
        assertThat(d.get("registration").get("status").asString()).isEqualTo("APPROVED");
        assertThat(d.get("fees").get("balance").decimalValue()).isEqualByComparingTo(DemoDataSeeder.STUDENT_BALANCE);
        assertThat(d.get("upcomingExams")).isNotEmpty();

        assertThat(getJson(student, "/api/transcripts/me").get("semesters")).hasSize(3);
        assertThat(getJson(student, "/api/students/me/timetable").toString()).contains("CS301");
        assertThat(getJson(student, "/api/announcements").get("totalElements").asLong()).isPositive();
        assertThat(getJson(student, "/api/notifications/unread-count").get("count").asLong()).isPositive();
    }

    @Test
    void lecturerHasMarksInEveryState() throws Exception {
        String lecturer = signIn(DemoDataSeeder.LECTURER_EMAIL);

        JsonNode d = getJson(lecturer, "/api/lecturer/dashboard");
        assertThat(d.get("marksMissing").asInt()).isPositive();
        assertThat(d.get("offerings").valueStream().map(o -> o.get("courseCode").asString())).contains("CS301", "CS302");

        // The complete class can really be submitted.
        postOk(lecturer, "/api/lecturer/offerings/" + offeringOf("CS302") + "/results/submit");
    }

    @Test
    void registrarSeesTheUniversityAndCanActOnWhatIsWaiting() throws Exception {
        String registrar = signIn(DemoDataSeeder.REGISTRAR_EMAIL);

        JsonNode d = getJson(registrar, "/api/admin/dashboard");
        assertThat(d.get("totalStudents").asLong()).isEqualTo(24);
        assertThat(d.get("activeStudents").asLong()).isEqualTo(23);
        assertThat(d.get("lecturers").asLong()).isEqualTo(4);
        assertThat(d.get("registrations").get("awaitingApproval").asLong()).isPositive();
        assertThat(d.get("registrations").get("draft").asLong()).isPositive();
        assertThat(d.get("registrations").get("rejected").asLong()).isEqualTo(1);
        assertThat(d.get("resultsAwaitingReview").asLong()).isPositive();
        assertThat(d.get("recentResults")).hasSize(5);
        assertThat(d.get("registrationActivity").valueStream().mapToLong(p -> p.get("count").asLong()).sum()).isGreaterThan(10);

        postOk(registrar, "/api/admin/offerings/" + offeringOf("BUS201") + "/results/finalize");
        // A Commerce student: approving someone into a class whose marks are complete would rightly reopen it for marking.
        long pending = getJson(registrar, "/api/admin/registrations?status=SUBMITTED").get("content").valueStream()
                .filter(r -> r.get("student").get("programCode").asString().equals("BCOM"))
                .findFirst().orElseThrow().get("id").asLong();
        postOk(registrar, "/api/admin/registrations/" + pending + "/approve");
    }

    @Test
    void financeSeesCollectionsAndEveryKindOfPayment() throws Exception {
        String finance = signIn(DemoDataSeeder.FINANCE_EMAIL);

        JsonNode d = getJson(finance, "/api/fees/dashboard");
        assertThat(d.get("outstanding").decimalValue().signum()).isPositive();
        assertThat(d.get("collectedLast30Days").decimalValue().signum()).isPositive();
        assertThat(d.get("weeklyCollections").valueStream().filter(w -> w.get("amount").decimalValue().signum() > 0).count()).isGreaterThan(3);
        assertThat(d.get("methodsLast30Days").size()).isGreaterThan(1);
        assertThat(d.get("largestBalances")).isNotEmpty();
        assertThat(jdbc.sql("select distinct status from payments").query(String.class).list()).contains("COMPLETED", "FAILED", "REVERSED");
    }

    @Test
    void seatsMatchWhoHoldsThem() {
        assertThat(jdbc.sql("""
                        select count(*) from course_offerings o
                        where o.enrolled_count <> (select count(*) from registration_items i where i.offering_id = o.id and i.seat_reserved)""")
                .query(Long.class).single()).isZero();
    }

    @Test
    void neverLoadsTwiceOrWithoutAPassword() {
        long students = count("students");
        long payments = count("payments");

        seeder.run(null);

        assertThat(count("students")).isEqualTo(students);
        assertThat(count("payments")).isEqualTo(payments);
        assertThatThrownBy(() -> new DemoDataSeeder(jdbc, passwordEncoder, audit, properties, clock, " ").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");
    }
}
