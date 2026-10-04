package com.unisphere.user;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Role-based access control and admin user management, end to end. */
class UserAdminIT extends IntegrationTest {

    private String adminAuth;

    @BeforeEach
    void signInAdmin() throws Exception {
        adminAuth = bearer(login(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void nonAdminRolesGet403() throws Exception {
        for (Role role : new Role[] {Role.STUDENT, Role.LECTURER, Role.FINANCE}) {
            User user = createActiveUser(role);
            String auth = bearer(login(user.getEmail(), DEFAULT_PASSWORD));
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(post("/api/admin/users/1/deactivate").header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanSearchFilterAndPaginate() throws Exception {
        User lecturer = createActiveUser(Role.LECTURER);

        mvc.perform(get("/api/admin/users")
                        .param("role", "LECTURER")
                        .param("size", "5")
                        .param("sort", "email,asc")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[*].roles", everyItem(hasItem("LECTURER"))));

        mvc.perform(get("/api/admin/users").param("q", lecturer.getEmail()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(lecturer.getEmail()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void searchTreatsLikeWildcardsLiterally() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidSortAndFilterValuesAreRejected() throws Exception {
        mvc.perform(get("/api/admin/users").param("sort", "passwordHash,asc").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/admin/users").param("role", "SUPERUSER").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void deactivationImmediatelyInvalidatesTheUsersAccessToken() throws Exception {
        User student = createActiveUser(Role.STUDENT);
        String studentAuth = bearer(login(student.getEmail(), DEFAULT_PASSWORD));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, studentAuth)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/{id}/deactivate", student.getId()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, studentAuth)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/{id}", student.getId()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        mvc.perform(post("/api/admin/users/{id}/activate", student.getId()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent());
        login(student.getEmail(), DEFAULT_PASSWORD);
    }

    @Test
    void adminCannotDeactivateThemselves() throws Exception {
        Long adminId = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        mvc.perform(post("/api/admin/users/{id}/deactivate", adminId).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void unknownUserGives404() throws Exception {
        mvc.perform(get("/api/admin/users/999999").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
