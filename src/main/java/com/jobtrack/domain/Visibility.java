package com.jobtrack.domain;

public enum Visibility {
    PRIVATE("Private", "Only you can see it"),
    PUBLIC("Public", "Shown in the interview hall to all users (your answer stays private)");

    private final String label;
    private final String description;

    Visibility(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
