package com.jobtrack.domain;

public enum DocumentType {
    JD("JD file"),
    CV("CV / Resume"),
    COVER_LETTER("Cover Letter"),
    OTHER("Other");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
