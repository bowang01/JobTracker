package com.jobtrack.service;

/** A business rule was violated; the message is safe to show to the user. */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The related form field, may be null. */
    public String getField() {
        return field;
    }
}
