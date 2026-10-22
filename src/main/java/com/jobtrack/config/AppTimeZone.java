package com.jobtrack.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * The single time zone JobTrack works in. All times are stored as local date-times (DATETIME), so the server
 * must agree with its users on what "now" is. A Docker container runs in UTC by default, which made "now" 13 hours
 * behind New Zealand: the status form was prefilled with the wrong time and the real current time was rejected
 * as "in the future".
 * <p>The zone comes from the {@code jobtrack.time-zone} system property or the {@code JOBTRACK_TIME_ZONE}
 * environment variable, defaulting to Pacific/Auckland. It is applied as the JVM default before anything reads
 * the clock (in {@code main}, and again when the Clock bean is created, which also covers the tests), so the
 * application clock, JDBC, validation and scheduling all use the same zone wherever the app runs.
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
