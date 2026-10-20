package com.jobtrack.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    SAVED("Saved"),
    APPLIED("Applied"),
    ASSESSMENT("Assessment"),
    INTERVIEWING("Interviewing"),
    OFFER("Offer"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn"),
    NO_RESPONSE("No response");

    /** Statuses that mean the application has been submitted. */
    private static final Set<ApplicationStatus> SUBMITTED =
            EnumSet.of(APPLIED, ASSESSMENT, INTERVIEWING, OFFER, ACCEPTED);

    /** Statuses that are still in progress (Dashboard "Active" count). */
    private static final Set<ApplicationStatus> ACTIVE =
            EnumSet.of(APPLIED, ASSESSMENT, INTERVIEWING, OFFER);

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Rule 9: entering one of these statuses fills in a missing applied date with the event date. */
    public boolean impliesSubmitted() {
        return SUBMITTED.contains(this);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    /** CSS class name used in templates. */
    public String getCss() {
        return "s-" + name().toLowerCase().replace('_', '-');
    }
}
