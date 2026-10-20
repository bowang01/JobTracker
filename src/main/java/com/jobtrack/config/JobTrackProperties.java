package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "jobtrack")
public record JobTrackProperties(Storage storage, Upload upload, Admin admin) {

    public record Storage(String dir) {
    }

    public record Upload(DataSize maxFileSize) {
    }

    public record Admin(String email, String password) {
    }
}
