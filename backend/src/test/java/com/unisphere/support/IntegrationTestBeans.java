package com.unisphere.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test doubles shared by all integration tests: a controllable clock and a mailer that captures links. */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestBeans {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now());
    }

    @Bean
    @Primary
    IntegrationTest.CapturingMailer capturingMailer() {
        return new IntegrationTest.CapturingMailer();
    }
}
