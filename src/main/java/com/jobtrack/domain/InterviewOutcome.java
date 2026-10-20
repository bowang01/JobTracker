package com.jobtrack.domain;

public enum InterviewOutcome {
    PENDING("Pending"),
    PASSED("Passed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String label;

    InterviewOutcome(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return "o-" + name().toLowerCase();
    }
}
