package com.jobtrack.service;

import java.util.UUID;

import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.InterviewForm;

/**
 * Rule 4: any number of interview rounds, but round numbers are unique within an application.
 * Checked in the application for a friendly message; the database unique constraint covers races (the controller catches DataIntegrityViolationException).
 */
public interface InterviewService {
    record InterviewWithApp(JobApplication application, Interview interview) {
    }

    Interview add(UUID appId, InterviewForm form, AppUserDetails me);

    InterviewWithApp get(UUID appId, UUID interviewId, AppUserDetails me);

    void update(UUID appId, UUID interviewId, InterviewForm form, AppUserDetails me);

    void delete(UUID appId, UUID interviewId, AppUserDetails me);
}
