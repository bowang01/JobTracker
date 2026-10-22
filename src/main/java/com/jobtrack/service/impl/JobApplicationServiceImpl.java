package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.StatusChange;
import com.jobtrack.domain.User;
import com.jobtrack.repository.ApplicationFilter;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusChangeRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.ApplicationForm;
import com.jobtrack.web.form.ApplicationSearchCriteria;
import com.jobtrack.web.form.StatusChangeForm;

import com.jobtrack.service.AccessGuard;
import com.jobtrack.service.ApplicationDetail;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.DocumentService;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.JobApplicationService;
import com.jobtrack.service.Text;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

    /** Allowed clock skew, so a browser clock slightly ahead of the server is not treated as "in the future". */
    private static final int FUTURE_TOLERANCE_MINUTES = 5;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JobApplicationRepository applications;
    private final StatusChangeRepository history;
    private final InterviewRepository interviews;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final FileStorageService storage;
    private final AccessGuard guard;
    private final DocumentService documentService;
    private final Clock clock;

    public JobApplicationServiceImpl(JobApplicationRepository applications, StatusChangeRepository history,
                                 InterviewRepository interviews, DocumentRepository documents,
                                 UserRepository users, FileStorageService storage, AccessGuard guard,
                                 DocumentService documentService, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.interviews = interviews;
        this.documents = documents;
        this.users = users;
        this.storage = storage;
        this.guard = guard;
        this.documentService = documentService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Queries

    @Transactional(readOnly = true)
    @Override
    public Page<JobApplication> search(ApplicationSearchCriteria criteria, AppUserDetails me, int page) {
        // Regular users only ever see their own; admins may filter by user and see everyone by default
        UUID ownerScope = me.isAdmin() ? criteria.getOwnerId() : me.getId();
        ApplicationFilter filter = new ApplicationFilter(ownerScope, criteria.getCompany(), criteria.getPosition(),
                criteria.getStatus(), criteria.getAppliedFrom(), criteria.getAppliedTo());
        return applications.search(filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE, criteria.toSort()));
    }

    /** Loads an application for editing (owner only). */
    @Transactional(readOnly = true)
    @Override
    public JobApplication getForEdit(UUID id, AppUserDetails me) {
        return guard.loadForWrite(id, me);
    }

    @Transactional(readOnly = true)
    @Override
    public ApplicationDetail getDetail(UUID id, AppUserDetails me) {
        JobApplication app = guard.loadForRead(id, me);
        Map<DocumentType, List<ApplicationDocument>> byType = new EnumMap<>(DocumentType.class);
        for (DocumentType t : DocumentType.values()) {
            byType.put(t, new ArrayList<>());
        }
        for (ApplicationDocument d : documents.findByApplicationIdOrderByDocTypeAscVersionNoDesc(id)) {
            byType.get(d.getDocType()).add(d);
        }
        Integer maxRound = interviews.findMaxRound(id);
        return new ApplicationDetail(
                app,
                history.findTimeline(id),
                interviews.findByApplicationIdOrderByRoundNumberAsc(id),
                byType,
                maxRound == null ? 1 : maxRound + 1);
    }

    // ------------------------------------------------------------------ Create / edit / delete

    @Transactional
    @Override
    public JobApplication create(ApplicationForm form, AppUserDetails me) {
        if (me.isAdmin()) {
            // Admins manage and monitor users; they do not track their own applications
            throw new AccessDeniedException("Administrators cannot create applications");
        }
        User owner = users.getReferenceById(me.getId());
        LocalDateTime now = LocalDateTime.now(clock);

        JobApplication app = new JobApplication();
        app.setOwner(owner);
        form.applyTo(app);

        ApplicationStatus initial = form.getInitialStatus() == null ? ApplicationStatus.SAVED : form.getInitialStatus();
        app.setStatus(initial);
        // Created as already submitted with an applied date: the initial history entry is dated to the applied date (backfill)
        LocalDateTime initialOccurred = minute(now);
        if (initial.impliesSubmitted()) {
            if (app.getAppliedDate() == null) {
                app.setAppliedDate(now.toLocalDate());
            } else if (app.getAppliedDate().isBefore(now.toLocalDate())) {
                initialOccurred = app.getAppliedDate().atStartOfDay();
            }
        }
        applications.save(app);

        // Rule 2: the initial status is recorded in the history too, so the current status always has a source entry
        history.save(new StatusChange(app, null, initial, initialOccurred, now, "Application created", owner));
        return app;
    }

    @Transactional
    @Override
    public JobApplication create(ApplicationForm form, Map<DocumentType, MultipartFile> attachments, AppUserDetails me) {
        JobApplication app = create(form, me);
        if (attachments != null) {
            for (Map.Entry<DocumentType, MultipartFile> e : attachments.entrySet()) {
                MultipartFile file = e.getValue();
                if (file == null || file.isEmpty()) {
                    continue; // attachments are optional
                }
                try {
                    // Joins this transaction: an invalid file rolls back the application and every file written so far
                    documentService.upload(app.getId(), e.getKey(), file, me);
                } catch (BusinessException ex) {
                    throw new BusinessException(e.getKey().name(), ex.getMessage());
                }
            }
        }
        return app;
    }

    @Transactional
    @Override
    public void update(UUID id, ApplicationForm form, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(id, me);
        form.applyTo(app); // never touches owner or status
    }

    /**
     * Rule 7: delete the database rows first and the files on disk only after the transaction commits.
     * On rollback the files stay; if a file delete fails we only leave an orphan file (logged), never a record pointing at a missing file.
     */
    @Transactional
    @Override
    public void delete(UUID id, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(id, me);
        List<String> files = documents.findStoredFilenamesByApplicationId(id);
        history.deleteByApplicationId(id);
        interviews.deleteByApplicationId(id);
        documents.deleteByApplicationId(id);
        applications.delete(app);
        storage.deleteAfterCommit(files);
    }

    // ------------------------------------------------------------------ Status

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
    @Transactional
    @Override
    public void changeStatus(UUID id, StatusChangeForm form, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(id, me);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime occurred = minute(form.getOccurredAt() == null ? now : form.getOccurredAt());
        if (occurred.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw new BusinessException("occurredAt", "The status time cannot be in the future");
        }
        ApplicationStatus target = form.getToStatus();
        if (target == null) {
            throw new BusinessException("toStatus", "Please choose a new status");
        }

        StatusChange latest = history.findFirstByApplicationIdOrderByOccurredAtDescRecordedAtDesc(id).orElse(null);
        String latestTime = latest == null ? "" : latest.getOccurredAt().format(TIME_FMT);

        ApplicationStatus from;
        if (form.isHistoryOnly()) {
            if (latest == null || !occurred.isBefore(latest.getOccurredAt())) {
                throw new BusinessException("occurredAt", "A backfilled event must be earlier than the latest status change (" + latestTime
                        + "). If this is the latest update, untick \"Backfill history only\".");
            }
            from = history.findFirstByApplicationIdAndOccurredAtLessThanEqualOrderByOccurredAtDescRecordedAtDesc(id, occurred)
                    .map(StatusChange::getToStatus)
                    .orElse(null);
            if (from == target) {
                throw new BusinessException("toStatus", "The status at that time was already \"" + target.getLabel() + "\"");
            }
        } else {
            if (latest != null && occurred.isBefore(latest.getOccurredAt())) {
                throw new BusinessException("occurredAt", "The time cannot be earlier than the latest status change (" + latestTime
                        + "). To record an earlier event, tick \"Backfill history only\".");
            }
            from = app.getStatus();
            if (from == target) {
                throw new BusinessException("toStatus", "The current status is already \"" + target.getLabel() + "\"");
            }
            app.setStatus(target);
        }

        if (target.impliesSubmitted() && app.getAppliedDate() == null) {
            app.setAppliedDate(occurred.toLocalDate());
        }

        history.save(new StatusChange(app, from, target, occurred, now,
                Text.trimToNull(form.getNote()), users.getReferenceById(me.getId())));
    }

    private static LocalDateTime minute(LocalDateTime t) {
        return t.truncatedTo(ChronoUnit.MINUTES);
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
