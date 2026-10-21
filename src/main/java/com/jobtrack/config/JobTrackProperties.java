package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "jobtrack")
public record JobTrackProperties(Storage storage, Upload upload, Admin admin, Demo demo) {

    public record Storage(String dir) {
    }

    public record Upload(DataSize maxFileSize) {
    }

    public record Admin(String email, String password) {
    }

    /** Optional public demo account (see DemoAccount / DemoDataInitializer). {@code resetCron} "-" turns the nightly reset off. */
    public record Demo(boolean enabled, String email, String password, String resetCron) {
    }
}
