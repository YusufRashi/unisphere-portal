package com.unisphere.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "AUTH_RATE_LIMIT_PER_MINUTE=3")
class AuthRateLimitIT extends IntegrationTest {

    @Test
    void loginIsThrottledPerClient() throws Exception {
        // Start at the beginning of a fixed window so the test cannot straddle a window boundary.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(1));
        String body = "{\"email\":\"someone@uni.test\",\"password\":\"Wrong-pass-1\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    /** Regression: every page load refreshes the session, so throttling refresh signed users out (Phase 2). */
    @Test
    void tokenRefreshIsNotThrottled() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(1));
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
