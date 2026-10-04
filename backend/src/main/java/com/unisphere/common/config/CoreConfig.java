package com.unisphere.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    /** Injected everywhere time matters so that tests can control "now". */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
