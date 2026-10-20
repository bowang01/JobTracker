package com.jobtrack.web.form;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.service.Text;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/edit form for an application. Deliberately has no owner field (rule 1);
 * the status cannot be edited here either, only through "Update status" (rule 2).
 */
public class ApplicationForm {

    @NotBlank(message = "Company is required")
    @Size(max = 200, message = "Company must be at most 200 characters")
    private String company;

    @NotBlank(message = "Position is required")
    @Size(max = 200, message = "Position must be at most 200 characters")
    private String positionTitle;

    @Size(max = 200, message = "Location must be at most 200 characters")
    private String location;

    /** Only http/https, so links like javascript: are never rendered on the page. */
    @Size(max = 1000, message = "Link must be at most 1000 characters")
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Link must start with http:// or https://")
    private String jobUrl;

    @Size(max = 100, message = "Source must be at most 100 characters")
    private String source;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(message = "Applied date cannot be in the future")
    private LocalDate appliedDate;

    @Size(max = 50000, message = "JD text must be at most 50000 characters")
    private String jdText;

    @Size(max = 10000, message = "Notes must be at most 10000 characters")
    private String notes;

    /** Only used on creation; defaults to SAVED (rule 9). */
    private ApplicationStatus initialStatus = ApplicationStatus.SAVED;

    public static ApplicationForm from(JobApplication a) {
        ApplicationForm f = new ApplicationForm();
        f.company = a.getCompany();
        f.positionTitle = a.getPositionTitle();
        f.location = a.getLocation();
        f.jobUrl = a.getJobUrl();
        f.source = a.getSource();
        f.appliedDate = a.getAppliedDate();
        f.jdText = a.getJdText();
        f.notes = a.getNotes();
        f.initialStatus = a.getStatus();
        return f;
    }

    /** Copies the editable fields onto the entity (never owner or status). */
    public void applyTo(JobApplication a) {
        a.setCompany(company.trim());
        a.setPositionTitle(positionTitle.trim());
        a.setLocation(Text.trimToNull(location));
        a.setJobUrl(Text.trimToNull(jobUrl));
        a.setSource(Text.trimToNull(source));
        a.setAppliedDate(appliedDate);
        a.setJdText(Text.trimToNull(jdText));
        a.setNotes(Text.trimToNull(notes));
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl == null ? null : jobUrl.trim();
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ApplicationStatus getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(ApplicationStatus initialStatus) {
        this.initialStatus = initialStatus;
    }
}
