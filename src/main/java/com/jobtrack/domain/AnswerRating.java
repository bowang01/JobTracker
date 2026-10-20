package com.jobtrack.domain;

/** The user's own assessment of how well they answered a question; used to pick what to review. */
public enum AnswerRating {
    GOOD("Answered well"),
    OK("Okay"),
    POOR("Needs work");

    private final String label;

    AnswerRating(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return "r-" + name().toLowerCase();
    }
}
