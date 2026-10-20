package com.jobtrack.domain;

public enum InterviewType {
    PHONE_SCREEN("Phone screen"),
    VIDEO("Video"),
    TECHNICAL("Technical"),
    ONSITE("Onsite"),
    HR("HR"),
    FINAL("Final"),
    OTHER("Other");

    private final String label;

    InterviewType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
