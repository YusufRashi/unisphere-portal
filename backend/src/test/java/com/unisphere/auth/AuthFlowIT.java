package com.unisphere.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AuthFlowIT extends IntegrationTest {

    @Autowired
    private AccountTokenService accountTokens;

    @Test
    void bootstrapAdminCanSignIn() throws Exception {
        LoginResult admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginSetsHardenedRefreshCookieAndNoStoreCacheHeader() throws Exception {
        User user = createActiveUser(Role.STUDENT);

        mvc.perform(postJson("/api/auth/login", Map.of("email", user.getEmail(), "password", DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    void invalidCredentialsReturnProblemDetails() throws Exception {
        mvc.perform(postJson("/api/auth/login", Map.of("email", "nobody@uni.test", "password", "Whatever-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedLoginBodyReturnsFieldErrors() throws Exception {
        mvc.perform(postJson("/api/auth/login", Map.of("email", "not-an-email", "password", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void unparseableJsonDoesNotLeakParserDetails() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail", not(containsString("jackson"))));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        for (int i = 0; i < 5; i++) {
            mvc.perform(postJson("/api/auth/login", Map.of("email", user.getEmail(), "password", "Wrong-pass-1")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(postJson("/api/auth/login", Map.of("email", user.getEmail(), "password", DEFAULT_PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesCookieAndOldCookieIsRejected() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        LoginResult first = login(user.getEmail(), DEFAULT_PASSWORD);

        MvcResult refreshed = mvc.perform(refresh(first.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(rotated.getValue()).isNotEqualTo(first.refreshCookie().getValue());

        // Replaying the old cookie after the grace period is treated as theft: the whole family dies...
        clock.advance(Duration.ofSeconds(30));
        mvc.perform(refresh(first.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        // ...including the legitimately rotated token.
        mvc.perform(refresh(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresCsrfHeader() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        LoginResult session = login(user.getEmail(), DEFAULT_PASSWORD);

        mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_CHECK_FAILED"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        LoginResult session = login(user.getEmail(), DEFAULT_PASSWORD);

        mvc.perform(post("/api/auth/logout").cookie(session.refreshCookie()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(refresh(session.refreshCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void activationFlowSetsPasswordAndLinkIsSingleUse() throws Exception {
        User pending = users.save(new User("lecturer.new@uni.test", "Ada", "Lovelace", Set.of(Role.LECTURER), clock.instant()));
        String token = accountTokens.issue(pending, AccountTokenPurpose.ACTIVATION);

        mvc.perform(postJson("/api/auth/activate", Map.of("token", token, "password", "Analytical-Engine-1")))
                .andExpect(status().isNoContent());
        login("lecturer.new@uni.test", "Analytical-Engine-1");

        mvc.perform(postJson("/api/auth/activate", Map.of("token", token, "password", "Analytical-Engine-2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void activationRejectsWeakPasswordWithViolations() throws Exception {
        User pending = users.save(new User("weak.pw@uni.test", "Weak", "Password", Set.of(Role.STUDENT), clock.instant()));
        String token = accountTokens.issue(pending, AccountTokenPurpose.ACTIVATION);

        mvc.perform(postJson("/api/auth/activate", Map.of("token", token, "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void passwordResetFlowInvalidatesExistingSessions() throws Exception {
        User user = createActiveUser(Role.FINANCE);
        LoginResult before = login(user.getEmail(), DEFAULT_PASSWORD);

        mvc.perform(postJson("/api/auth/password/forgot", Map.of("email", user.getEmail())))
                .andExpect(status().isAccepted());
        // Unknown emails get the same response.
        mvc.perform(postJson("/api/auth/password/forgot", Map.of("email", "ghost@uni.test")))
                .andExpect(status().isAccepted());
        assertThat(mailer.links).hasSize(1);
        String token = mailer.links.getFirst().substring(mailer.links.getFirst().indexOf("token=") + 6);

        clock.advance(Duration.ofSeconds(2));
        mvc.perform(postJson("/api/auth/password/reset", Map.of("token", token, "password", "Fresh-start-2026")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(before)))
                .andExpect(status().isUnauthorized());
        mvc.perform(refresh(before.refreshCookie())).andExpect(status().isUnauthorized());
        login(user.getEmail(), "Fresh-start-2026");
    }

    @Test
    void changePasswordReturnsNewSessionAndRevokesOldOne() throws Exception {
        User user = createActiveUser(Role.STUDENT);
        LoginResult before = login(user.getEmail(), DEFAULT_PASSWORD);
        clock.advance(Duration.ofSeconds(2));

        MvcResult result = mvc.perform(postJson("/api/auth/password/change",
                        Map.of("currentPassword", DEFAULT_PASSWORD, "newPassword", "Changed-pass-77"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(before)))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(before))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("X-Request-Id"));
    }

    private MockHttpServletRequestBuilder postJson(String url, Object body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder refresh(Cookie cookie) {
        return post("/api/auth/refresh").cookie(cookie).header("X-Requested-With", "XMLHttpRequest");
    }
}
