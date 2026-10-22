package com.jobtrack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import com.jobtrack.config.AppConfig;
import com.jobtrack.config.AppTimeZone;

/** "Now" must be New Zealand time even on a server (or Docker container) that runs in UTC. */
class AppTimeZoneTest {

    @Test
    void appClockUsesTheConfiguredZoneNotTheServerZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // like a Docker container

            assertThat(new AppConfig().clock().getZone()).isEqualTo(ZoneId.of("Pacific/Auckland"));
            assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");   // the bean never changes the JVM default

            assertThat(AppTimeZone.apply()).isEqualTo(ZoneId.of("Pacific/Auckland"));   // what main() does
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Pacific/Auckland"));

            System.setProperty("jobtrack.time-zone", "Europe/London");
            assertThat(AppTimeZone.zone()).isEqualTo(ZoneId.of("Europe/London"));
        } finally {
            System.clearProperty("jobtrack.time-zone");
            TimeZone.setDefault(original);
        }
    }
}
