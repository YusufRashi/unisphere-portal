package com.unisphere.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.auth.RefreshTokenCookies;
import com.unisphere.auth.mail.AccountMailer;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import com.unisphere.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for full-stack tests: real PostgreSQL (Testcontainers) with Flyway migrations, the complete
 * security filter chain and MockMvc. The container is shared by all subclasses through Spring's context cache.
 */
@SpringBootTest(properties = {
        // 32 random bytes, Base64 — test-only value.
        "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2Q=",
        "COOKIE_SECURE=false",
        "AUTH_RATE_LIMIT_PER_MINUTE=1000",
        "BOOTSTRAP_ADMIN_EMAIL=admin@uni.test",
        "BOOTSTRAP_ADMIN_PASSWORD=Registrar-Key-2026",
        // Tests run the reminder job themselves, at the times they choose.
        "NOTIFICATION_REMINDER_CRON=-",
})
@AutoConfigureMockMvc
@Import(IntegrationTestBeans.class)
public abstract class IntegrationTest {

    public static final String ADMIN_EMAIL = "admin@uni.test";
    public static final String ADMIN_PASSWORD = "Registrar-Key-2026";
    public static final String DEFAULT_PASSWORD = "Student-pass-1";

    /**
     * Singleton container: started once per JVM and shared by every test class (and cached Spring context).
     * Ryuk removes it when the JVM exits.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected CapturingMailer mailer;

    @BeforeEach
    void resetSharedState() {
        clock.set(Instant.now());
        mailer.links.clear();
    }

    /** Records links instead of sending email so tests can follow activation / reset flows. */
    public static class CapturingMailer implements AccountMailer {
        public final List<String> links = new ArrayList<>();

        @Override
        public void sendActivationLink(User user, String link) {
            links.add(link);
        }

        @Override
        public void sendPasswordResetLink(User user, String link) {
            links.add(link);
        }
    }

    protected User createActiveUser(Role... roles) {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@uni.test";
        User user = new User(email, "Test", "Person", Set.of(roles), clock.instant().minusSeconds(60));
        user.activate(passwordEncoder.encode(DEFAULT_PASSWORD));
        return users.save(user);
    }

    protected LoginResult login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        return new LoginResult(body.get("accessToken").asString(), cookie);
    }

    protected String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    protected String adminBearer() throws Exception {
        return bearer(login(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    /** Short random code: tests share one database, so every created code must be unique. */
    protected static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object body, String auth) {
        return withJson(post(url), body, auth);
    }

    protected MockHttpServletRequestBuilder putJson(String url, Object body, String auth) {
        return withJson(put(url), body, auth);
    }

    /** Performs the request, expects {@code expectedStatus} and returns the parsed JSON body. */
    protected JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body, String auth) {
        builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return auth == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, auth);
    }

    protected record LoginBody(String email, String password) {
    }

    protected record LoginResult(String accessToken, Cookie refreshCookie) {
    }
}
