package com.jobtrack.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * The single time zone JobTrack works in. All times are stored as local date-times (DATETIME), so the server
 * must agree with its users on what "now" is. A Docker container runs in UTC by default, which made "now" 13 hours
 * behind New Zealand: the status form was prefilled with the wrong time and the real current time was rejected
 * as "in the future".
 * <p>The zone comes from the {@code jobtrack.time-zone} system property or the {@code JOBTRACK_TIME_ZONE}
 * environment variable, defaulting to Pacific/Auckland. {@code main} applies it as the JVM default before anything
 * starts, so the application clock, JDBC, validation and scheduling all use the same zone wherever the app runs.
 * It must not be changed later at runtime: JDBC drivers (H2 in particular) cache the default zone, and switching it
 * mid-run shifts stored dates by a day. Tests get the same zone from the surefire argLine in pom.xml.
 */
public final class AppTimeZone {

    public static final String DEFAULT = "Pacific/Auckland";

    private AppTimeZone() {
    }

    public static ZoneId zone() {
        String configured = System.getProperty("jobtrack.time-zone");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("JOBTRACK_TIME_ZONE");
        }
        return ZoneId.of(configured == null || configured.isBlank() ? DEFAULT : configured.trim());
    }

    /** Makes the configured zone the JVM default and returns it. Safe to call more than once. */
    public static ZoneId apply() {
        ZoneId zone = zone();
        if (!TimeZone.getDefault().toZoneId().equals(zone)) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
        }
        return zone;
    }
}
