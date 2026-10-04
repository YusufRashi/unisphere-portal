package com.unisphere.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

class AccountProvisioningIT extends IntegrationTest {

    private String admin;
    private long programId;

    @BeforeEach
    void setUpProgram() throws Exception {
        admin = adminBearer();
        long dept = call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", unique("Dept ")), admin), 201)
                .get("id").asLong();
        programId = call(postJson("/api/admin/programs", Map.of("departmentId", dept, "code", unique("P"),
                "name", "BSc Testing", "awardLevel", "BACHELOR", "durationYears", 4), admin), 201).get("id").asLong();
    }

    private Map<String, Object> studentBody(String email) {
        return Map.of("firstName", "Amina", "lastName", "Otieno", "email", email, "phone", "+254 700 000000",
                "programId", programId, "yearOfStudy", 1, "admissionDate", "2026-09-01");
    }

    private String activationToken() {
        String link = mailer.links.getLast();
        assertThat(link).contains("/activate?token=");
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    void createdStudentActivatesAccountAndSignsInAsStudent() throws Exception {
        String email = unique("amina").toLowerCase() + "@uni.test";
        JsonNode student = call(postJson("/api/admin/students", studentBody(email), admin), 201);

        assertThat(student.get("studentNumber").asString()).matches("S2026-\\d{5}");
        assertThat(student.get("accountStatus").asString()).isEqualTo("PENDING_ACTIVATION");
        assertThat(student.get("program").get("id").asLong()).isEqualTo(programId);

        // No password exists until the student chooses one.
        call(postJson("/api/auth/login", Map.of("email", email, "password", "Anything-123"), null), 401);

        call(postJson("/api/auth/activate", Map.of("token", activationToken(), "password", "My-own-pass-2026"), null), 204);
        String auth = bearer(login(email, "My-own-pass-2026"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }

    @Test
    void studentNumbersAreUniqueAndEmailsCannotBeReused() throws Exception {
        String email = unique("dup").toLowerCase() + "@uni.test";
        String first = call(postJson("/api/admin/students", studentBody(email), admin), 201).get("studentNumber").asString();
        assertThat(call(postJson("/api/admin/students", studentBody(email.toUpperCase()), admin), 409).get("code").asString())
                .isEqualTo("DUPLICATE_RESOURCE");
        String second = call(postJson("/api/admin/students", studentBody(unique("x").toLowerCase() + "@uni.test"), admin), 201)
                .get("studentNumber").asString();
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void resendActivationInvalidatesThePreviousLink() throws Exception {
        String email = unique("re").toLowerCase() + "@uni.test";
        call(postJson("/api/admin/students", studentBody(email), admin), 201);
        String oldToken = activationToken();
        long userId = users.findByEmail(email).orElseThrow().getId();

        call(post("/api/admin/users/" + userId + "/resend-activation").header(HttpHeaders.AUTHORIZATION, admin), 204);
        String newToken = activationToken();
        assertThat(newToken).isNotEqualTo(oldToken);

        call(postJson("/api/auth/activate", Map.of("token", oldToken, "password", "My-own-pass-2026"), null), 401);
        call(postJson("/api/auth/activate", Map.of("token", newToken, "password", "My-own-pass-2026"), null), 204);
        // Active accounts cannot be sent another activation link.
        call(post("/api/admin/users/" + userId + "/resend-activation").header(HttpHeaders.AUTHORIZATION, admin), 409);
    }

    @Test
    void studentRecordCanBeUpdatedAndSearched() throws Exception {
        String email = unique("upd").toLowerCase() + "@uni.test";
        long id = call(postJson("/api/admin/students", studentBody(email), admin), 201).get("id").asLong();

        JsonNode updated = call(putJson("/api/admin/students/" + id,
                Map.of("programId", programId, "yearOfStudy", 2, "status", "SUSPENDED"), admin), 200);
        assertThat(updated.get("yearOfStudy").asInt()).isEqualTo(2);
        assertThat(updated.get("status").asString()).isEqualTo("SUSPENDED");

        mvc.perform(get("/api/admin/students").param("q", email).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email));
        mvc.perform(get("/api/admin/students").param("programId", String.valueOf(programId)).param("status", "SUSPENDED")
                        .param("sort", "lastName,asc").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void staffAccountsAreLimitedToAdminAndFinanceRoles() throws Exception {
        JsonNode problem = call(postJson("/api/admin/users/staff", Map.of("firstName", "Eve", "lastName", "Mallory",
                "email", unique("eve").toLowerCase() + "@uni.test", "roles", List.of("STUDENT")), admin), 400);
        assertThat(problem.get("errors").get(0).get("field").asString()).isEqualTo("roles");

        JsonNode staff = call(postJson("/api/admin/users/staff", Map.of("firstName", "Frank", "lastName", "Okoth",
                "email", unique("fin").toLowerCase() + "@uni.test", "roles", List.of("FINANCE")), admin), 201);
        assertThat(staff.get("roles").get(0).asString()).isEqualTo("FINANCE");
        assertThat(staff.get("status").asString()).isEqualTo("PENDING_ACTIVATION");
    }

    @Test
    void nonAdminsCannotProvisionAccounts() throws Exception {
        var finance = createActiveUser(com.unisphere.user.Role.FINANCE);
        String auth = bearer(login(finance.getEmail(), DEFAULT_PASSWORD));
        call(postJson("/api/admin/students", studentBody(unique("no").toLowerCase() + "@uni.test"), auth), 403);
        call(postJson("/api/admin/users/staff", Map.of("firstName", "A", "lastName", "B",
                "email", "x@uni.test", "roles", List.of("ADMIN")), auth), 403);
    }
}
