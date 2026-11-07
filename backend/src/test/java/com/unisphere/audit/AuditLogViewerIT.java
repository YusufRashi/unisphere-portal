package com.unisphere.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.unisphere.support.AcademicIntegrationTest;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AuditLogViewerIT extends AcademicIntegrationTest {

    private static final String URL = "/api/admin/audit-logs";

    private String today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
    }

    @Test
    void showsWhoDidWhatWithDetailsNewestFirst() throws Exception {
        as(admin, post("/api/admin/semesters/" + semesterId + "/registration/close"), 200);

        JsonNode entry = as(admin, get(URL).param("action", "REGISTRATION_CLOSED"), 200).get("content").get(0);

        assertThat(entry.get("entityType").asString()).isEqualTo("Semester");
        assertThat(entry.get("entityId").asLong()).isEqualTo(semesterId);
        assertThat(entry.get("actor").get("email").asString()).isEqualTo(ADMIN_EMAIL);
        assertThat(entry.get("details").get("code").asString()).startsWith("S");

        JsonNode all = as(admin, get(URL).param("size", "50"), 200).get("content");
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i - 1).get("id").asLong()).isGreaterThan(all.get(i).get("id").asLong());
        }
    }

    @Test
    void filtersByActorEntityAndDate() throws Exception {
        User lecturerUser = createActiveUser(Role.FINANCE);
        login(lecturerUser.getEmail(), DEFAULT_PASSWORD);

        JsonNode byEmail = as(admin, get(URL).param("actor", lecturerUser.getEmail().toUpperCase()), 200);
        assertThat(byEmail.get("totalElements").asLong()).isEqualTo(1);
        assertThat(byEmail.get("content").get(0).get("action").asString()).isEqualTo("LOGIN_SUCCEEDED");

        assertThat(as(admin, get(URL).param("actorId", String.valueOf(lecturerUser.getId())), 200).get("totalElements").asLong()).isEqualTo(1);
        // A wildcard in the search is taken literally.
        assertThat(as(admin, get(URL).param("actor", "%"), 200).get("totalElements").asLong()).isZero();

        JsonNode semesters = as(admin, get(URL).param("entityType", "Semester").param("size", "100"), 200).get("content");
        assertThat(semesters.valueStream().map(e -> e.get("entityType").asString())).containsOnly("Semester");

        assertThat(as(admin, get(URL).param("from", today()).param("to", today()), 200).get("totalElements").asLong()).isPositive();
        String yesterday = LocalDate.parse(today()).minusDays(1).toString();
        assertThat(as(admin, get(URL).param("actorId", String.valueOf(lecturerUser.getId())).param("to", yesterday), 200)
                .get("totalElements").asLong()).isZero();
        as(admin, get(URL).param("from", today()).param("to", yesterday), 400);
        as(admin, get(URL).param("action", "NOT_AN_ACTION"), 400);
        as(admin, get(URL).param("size", "101"), 400);
    }

    @Test
    void offersEveryActionAndTheEntityTypesInUse() throws Exception {
        JsonNode filters = as(admin, get(URL + "/filters"), 200);

        assertThat(filters.get("actions").valueStream().map(JsonNode::asString)).contains("LOGIN_SUCCEEDED", "ANNOUNCEMENT_PUBLISHED");
        assertThat(filters.get("entityTypes").valueStream().map(JsonNode::asString)).contains("Semester");
    }

    @Test
    void isForAdministratorsOnly() throws Exception {
        call(get(URL), 401);
        User finance = createActiveUser(Role.FINANCE);
        String auth = bearer(login(finance.getEmail(), DEFAULT_PASSWORD));
        as(auth, get(URL), 403);
        as(auth, get(URL + "/filters"), 403);
        as(student().auth(), get(URL), 403);
    }
}
