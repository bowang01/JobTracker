package com.jobtrack.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.jobtrack.domain.JobApplication;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.ApplicationForm;
import com.jobtrack.web.form.ApplicationSearchCriteria;
import com.jobtrack.web.form.StatusChangeForm;

public interface JobApplicationService {
    int PAGE_SIZE = 20;

    Page<JobApplication> search(ApplicationSearchCriteria criteria, AppUserDetails me, int page);

    /** Loads an application for editing (owner only). */
    JobApplication getForEdit(UUID id, AppUserDetails me);

    ApplicationDetail getDetail(UUID id, AppUserDetails me);

    JobApplication create(ApplicationForm form, AppUserDetails me);

    void update(UUID id, ApplicationForm form, AppUserDetails me);

    /**
     * Rule 7: delete the database rows first and the files on disk only after the transaction commits.
     * On rollback the files stay; if a file delete fails we only leave an orphan file (logged), never a record pointing at a missing file.
     */
    void delete(UUID id, AppUserDetails me);

    /**
     * Rules 2/3/8/9: the user updates the status manually. The current status and the history row are written in one transaction.
     * <p>Invariant: the current status = the toStatus of the history entry with the latest occurrence time. Therefore:
     * <ul>
     *   <li>Normal update: the time may be in the past (backfill) but not before the latest change, and not in the future;</li>
     *   <li>historyOnly (backfill only): the time must be before the latest change and the current status is unchanged;</li>
     *   <li>recordedAt is always the real entry time; occurrence times are kept to the minute;</li>
     *   <li>Entering "Applied" or a later status with no applied date fills it in with the event date.</li>
     * </ul>
     */
    void changeStatus(UUID id, StatusChangeForm form, AppUserDetails me);

    LocalDateTime now();
}
