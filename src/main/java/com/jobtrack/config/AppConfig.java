package com.jobtrack.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // demo account reset (DemoDataInitializer)
public class AppConfig {

    /** Single source of time, replaceable in tests. Always in the app's zone, never the server's (see AppTimeZone). */
    @Bean
    public Clock clock() {
        return Clock.system(AppTimeZone.apply());
    }
}
