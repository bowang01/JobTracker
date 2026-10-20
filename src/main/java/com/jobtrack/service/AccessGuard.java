package com.jobtrack.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.security.AppUserDetails;

/**
 * The single gate for data isolation: every by-ID access to an application (and its interviews, documents and history) goes through here.
 * <ul>
 *   <li>Read: the owner, or an admin (admins monitor users' data read-only);</li>
 *   <li>Write: the owner only. Admins manage accounts, not the content of users' applications.</li>
 * </ul>
 * Anyone else gets NotFoundException, exactly as if the application did not exist.
 * Declares no transaction of its own; the calling service method provides the transaction boundary.
 */
@Component
public class AccessGuard {

    private final JobApplicationRepository applications;

    public AccessGuard(JobApplicationRepository applications) {
        this.applications = applications;
    }

    public JobApplication loadForRead(UUID applicationId, AppUserDetails me) {
        JobApplication app = load(applicationId, me);
        if (!me.isAdmin() && !isOwner(app, me)) {
            throw new NotFoundException();
        }
        return app;
    }

    public JobApplication loadForWrite(UUID applicationId, AppUserDetails me) {
        JobApplication app = load(applicationId, me);
        if (!isOwner(app, me)) {
            throw new NotFoundException();
        }
        return app;
    }

    private JobApplication load(UUID applicationId, AppUserDetails me) {
        if (applicationId == null || me == null) {
            throw new NotFoundException();
        }
        return applications.findById(applicationId).orElseThrow(NotFoundException::new);
    }

    private static boolean isOwner(JobApplication app, AppUserDetails me) {
        return app.getOwner().getId().equals(me.getId());
    }
}
