package com.jobtrack.web.form;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.jobtrack.domain.Interview;
import com.jobtrack.domain.InterviewOutcome;
import com.jobtrack.domain.InterviewType;
import com.jobtrack.service.Text;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class InterviewForm {

    @NotNull(message = "Round is required")
    @Min(value = 1, message = "Rounds start at 1")
    @Max(value = 99, message = "Round cannot exceed 99")
    private Integer roundNumber;

    @NotNull(message = "Please choose an interview type")
    private InterviewType interviewType = InterviewType.VIDEO;

    @DateTimeFormat(pattern = StatusChangeForm.DATETIME_PATTERN)
    private LocalDateTime scheduledAt;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 1440, message = "Duration cannot exceed 1440 minutes")
    private Integer durationMinutes;

    @Size(max = 200, message = "Interviewer must be at most 200 characters")
    private String interviewer;

    @Size(max = 500, message = "Location/link must be at most 500 characters")
    private String location;

    @NotNull(message = "Please choose an outcome")
    private InterviewOutcome outcome = InterviewOutcome.PENDING;

    @Size(max = 10000, message = "Notes must be at most 10000 characters")
    private String notes;

    public static InterviewForm withRound(int round) {
        InterviewForm f = new InterviewForm();
        f.roundNumber = round;
        return f;
    }

    public static InterviewForm from(Interview i) {
        InterviewForm f = new InterviewForm();
        f.roundNumber = i.getRoundNumber();
        f.interviewType = i.getInterviewType();
        f.scheduledAt = i.getScheduledAt();
        f.durationMinutes = i.getDurationMinutes();
        f.interviewer = i.getInterviewer();
        f.location = i.getLocation();
        f.outcome = i.getOutcome();
        f.notes = i.getNotes();
        return f;
    }

    public void applyTo(Interview i) {
        i.setRoundNumber(roundNumber);
        i.setInterviewType(interviewType);
        i.setScheduledAt(scheduledAt);
        i.setDurationMinutes(durationMinutes);
        i.setInterviewer(Text.trimToNull(interviewer));
        i.setLocation(Text.trimToNull(location));
        i.setOutcome(outcome);
        i.setNotes(Text.trimToNull(notes));
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public InterviewType getInterviewType() {
        return interviewType;
    }

    public void setInterviewType(InterviewType interviewType) {
        this.interviewType = interviewType;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getInterviewer() {
        return interviewer;
    }

    public void setInterviewer(String interviewer) {
        this.interviewer = interviewer;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public InterviewOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(InterviewOutcome outcome) {
        this.outcome = outcome;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
