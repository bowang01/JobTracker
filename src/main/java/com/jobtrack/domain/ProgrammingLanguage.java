package com.jobtrack.domain;

/**
 * The language a technical interview question is about (optional; behavioral questions never have one).
 * GENERAL is for language-agnostic questions (algorithms, OS, networking, system design).
 * Adding a value needs a Flyway migration that updates the ck_q_language CHECK constraint.
 */
public enum ProgrammingLanguage {
    JAVA("Java"),
    CSHARP("C#"),
    PYTHON("Python"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    CPP("C / C++"),
    GO("Go"),
    SQL("SQL"),
    GENERAL("General / any language"),
    OTHER("Other");

    private final String label;

    ProgrammingLanguage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
