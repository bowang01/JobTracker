package com.jobtrack.domain;

public enum QuestionCategory {
    TECHNICAL("Technical"),
    BEHAVIORAL("Behavioral");

    private final String label;

    QuestionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCss() {
        return "qc-" + name().toLowerCase();
    }
}
