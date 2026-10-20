package com.jobtrack.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.InterviewForm;

import com.jobtrack.service.AccessGuard;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.InterviewService;
import com.jobtrack.service.NotFoundException;

/**
 * Rule 4: any number of interview rounds, but round numbers are unique within an application.
 * Checked in the application for a friendly message; the database unique constraint covers races (the controller catches DataIntegrityViolationException).
 */
@Service
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRepository interviews;
    private final AccessGuard guard;

    public InterviewServiceImpl(InterviewRepository interviews, AccessGuard guard) {
        this.interviews = interviews;
        this.guard = guard;
    }

    @Transactional
    @Override
    public Interview add(UUID appId, InterviewForm form, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(appId, me);
        if (interviews.existsByApplicationIdAndRoundNumber(appId, form.getRoundNumber())) {
            throw new BusinessException("roundNumber", "Round " + form.getRoundNumber() + " already exists; please use a different round number");
        }
        Interview i = new Interview();
        i.setApplication(app);
        form.applyTo(i);
        return interviews.saveAndFlush(i);
    }

    @Transactional(readOnly = true)
    @Override
    public InterviewWithApp get(UUID appId, UUID interviewId, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(appId, me);
        return new InterviewWithApp(app, loadOwned(app, interviewId));
    }

    @Transactional
    @Override
    public void update(UUID appId, UUID interviewId, InterviewForm form, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(appId, me);
        Interview i = loadOwned(app, interviewId);
        if (interviews.existsByApplicationIdAndRoundNumberAndIdNot(appId, form.getRoundNumber(), interviewId)) {
            throw new BusinessException("roundNumber", "Round " + form.getRoundNumber() + " already exists; please use a different round number");
        }
        form.applyTo(i);
        interviews.flush();
    }

    @Transactional
    @Override
    public void delete(UUID appId, UUID interviewId, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(appId, me);
        interviews.delete(loadOwned(app, interviewId));
    }

    /** The interview must belong to the application in the URL, so "my application ID + someone else's interview ID" is rejected. */
    private Interview loadOwned(JobApplication app, UUID interviewId) {
        Interview i = interviews.findById(interviewId).orElseThrow(NotFoundException::new);
        if (!i.getApplication().getId().equals(app.getId())) {
            throw new NotFoundException();
        }
        return i;
    }
}
