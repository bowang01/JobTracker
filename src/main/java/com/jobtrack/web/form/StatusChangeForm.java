package com.jobtrack.web.form;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.format.annotation.DateTimeFormat;

import com.jobtrack.domain.ApplicationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StatusChangeForm {

    public static final String DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm";

    @NotNull(message = "Please choose a new status")
    private ApplicationStatus toStatus;

    /** When the event happened; a past time when backfilling (rule 3). */
    @NotNull(message = "Please enter when the status changed")
    @DateTimeFormat(pattern = DATETIME_PATTERN)
    private LocalDateTime occurredAt;

    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;

    /** When ticked, only backfills a history entry without changing the current status (e.g. a missed assessment last week). */
    private boolean historyOnly;

    public static StatusChangeForm defaults(LocalDateTime now) {
        StatusChangeForm f = new StatusChangeForm();
        f.occurredAt = now.truncatedTo(ChronoUnit.MINUTES);
        return f;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ApplicationStatus toStatus) {
        this.toStatus = toStatus;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isHistoryOnly() {
        return historyOnly;
    }

    public void setHistoryOnly(boolean historyOnly) {
        this.historyOnly = historyOnly;
    }
}
