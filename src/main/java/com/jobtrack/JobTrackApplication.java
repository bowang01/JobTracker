package com.jobtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.jobtrack.config.AppTimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobTrackApplication {

    public static void main(String[] args) {
        // Before anything reads the clock: the app works in one configured zone, not the server's (see AppTimeZone)
        AppTimeZone.apply();
        SpringApplication.run(JobTrackApplication.class, args);
    }
}
