package com.unisphere.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import com.unisphere.user.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

class AcademicStructureIT extends IntegrationTest {

    private String admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = adminBearer();
    }

    private long createDepartment(String code) throws Exception {
        return call(postJson("/api/admin/departments",
                Map.of("code", code, "name", "Department " + code, "faculty", "Science"), admin), 201).get("id").asLong();
    }

    private long createCourse(long departmentId, String code) throws Exception {
        return call(postJson("/api/admin/courses", Map.of("departmentId", departmentId, "code", code,
                "title", "Course " + code, "credits", 3, "level", 200), admin), 201).get("id").asLong();
    }

    @Test
    void departmentLifecycleWithUniquenessAndSafeDelete() throws Exception {
        String code = unique("D");
        long id = createDepartment(code);

        // Codes are unique case-insensitively.
        JsonNode duplicate = call(postJson("/api/admin/departments",
                Map.of("code", code.toLowerCase(), "name", "Another name"), admin), 409);
        assertThat(duplicate.get("code").asString()).isEqualTo("DUPLICATE_RESOURCE");

        JsonNode updated = call(putJson("/api/admin/departments/" + id,
                Map.of("code", code, "name", "Renamed " + code, "faculty", "Engineering"), admin), 200);
        assertThat(updated.get("faculty").asString()).isEqualTo("Engineering");

        // A department that owns a course cannot be deleted...
        long courseDept = createDepartment(unique("D"));
        createCourse(courseDept, unique("C"));
        assertThat(call(delete("/api/admin/departments/" + courseDept).header(HttpHeaders.AUTHORIZATION, admin), 409)
                .get("code").asString()).isEqualTo("RESOURCE_IN_USE");

        // ...but an empty one can.
        mvc.perform(delete("/api/admin/departments/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/departments/" + id).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        JsonNode problem = call(postJson("/api/admin/courses", Map.of("departmentId", 1, "code", "bad code!",
                "title", "", "credits", 12, "level", 250), admin), 400);
        List<String> fields = problem.get("errors").valueStream().map(e -> e.get("field").asString()).toList();
        assertThat(fields).contains("code", "title", "credits");

        long dept = createDepartment(unique("D"));
        JsonNode level = call(postJson("/api/admin/courses", Map.of("departmentId", dept, "code", unique("C"),
                "title", "Odd level", "credits", 3, "level", 250), admin), 400);
        assertThat(level.get("errors").get(0).get("field").asString()).isEqualTo("level");
    }

    @Test
    void programsBelongToADepartmentAndCanBeDeactivated() throws Exception {
        long dept = createDepartment(unique("D"));
        String code = unique("P");
        JsonNode program = call(postJson("/api/admin/programs", Map.of("departmentId", dept, "code", code,
                "name", "BSc " + code, "awardLevel", "BACHELOR", "durationYears", 4), admin), 201);
        long id = program.get("id").asLong();
        assertThat(program.get("department").get("id").asLong()).isEqualTo(dept);
        assertThat(program.get("active").asBoolean()).isTrue();

        call(putJson("/api/admin/programs/" + id, Map.of("departmentId", dept, "code", code, "name", "BSc " + code,
                "awardLevel", "BACHELOR", "durationYears", 4, "active", false), admin), 200);

        mvc.perform(get("/api/programs").param("departmentId", String.valueOf(dept)).param("active", "true")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void prerequisiteCyclesAreRejectedWithTheCyclePath() throws Exception {
        long dept = createDepartment(unique("D"));
        String a = unique("A"), b = unique("B"), c = unique("C");
        long idA = createCourse(dept, a), idB = createCourse(dept, b), idC = createCourse(dept, c);

        call(putJson("/api/admin/courses/" + idB + "/prerequisites", Map.of("prerequisiteIds", List.of(idA)), admin), 200);
        JsonNode detail = call(putJson("/api/admin/courses/" + idC + "/prerequisites",
                Map.of("prerequisiteIds", List.of(idB)), admin), 200);
        assertThat(detail.get("prerequisites").get(0).get("code").asString()).isEqualTo(b);

        JsonNode cycle = call(putJson("/api/admin/courses/" + idA + "/prerequisites",
                Map.of("prerequisiteIds", List.of(idC)), admin), 409);
        assertThat(cycle.get("code").asString()).isEqualTo("PREREQUISITE_CYCLE");
        assertThat(cycle.get("detail").asString()).contains(a + " → " + c + " → " + b + " → " + a);

        JsonNode self = call(putJson("/api/admin/courses/" + idA + "/prerequisites",
                Map.of("prerequisiteIds", List.of(idA)), admin), 400);
        assertThat(self.get("code").asString()).isEqualTo("VALIDATION_ERROR");

        call(putJson("/api/admin/courses/" + idA + "/prerequisites",
                Map.of("prerequisiteIds", List.of(999_999)), admin), 400);
    }

    @Test
    void courseSearchFiltersAndPaginates() throws Exception {
        long dept = createDepartment(unique("D"));
        String code = unique("SRCH");
        createCourse(dept, code);
        createCourse(dept, unique("OTHER"));

        mvc.perform(get("/api/courses").param("q", code.toLowerCase()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(code))
                .andExpect(jsonPath("$.content[0].department.id").value(dept));

        mvc.perform(get("/api/courses").param("departmentId", String.valueOf(dept)).param("sort", "code,desc")
                        .param("size", "1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].code").value(code));
    }

    @Test
    void studentsCanReadTheCatalogueButNotChangeIt() throws Exception {
        var student = createActiveUser(Role.STUDENT);
        String auth = bearer(login(student.getEmail(), DEFAULT_PASSWORD));

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        mvc.perform(get("/api/departments").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        call(postJson("/api/admin/departments", Map.of("code", unique("D"), "name", "Nope"), auth), 403);
        call(putJson("/api/admin/courses/1/prerequisites", Map.of("prerequisiteIds", List.of()), auth), 403);
    }
}
