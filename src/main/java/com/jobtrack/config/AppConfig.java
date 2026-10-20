package com.jobtrack.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Single source of time, replaceable in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
