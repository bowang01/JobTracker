package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.Interview;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusCount;

import com.jobtrack.service.DashboardService;

/** Dashboard statistics. Every query is filtered by ownerId, so users only see their own data. */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final Clock clock;

    public DashboardServiceImpl(JobApplicationRepository applications, InterviewRepository interviews, Clock clock) {
        this.applications = applications;
        this.interviews = interviews;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Override
    public DashboardView build(UUID ownerId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (StatusCount sc : applications.countByStatusForOwner(ownerId)) {
            counts.put(sc.status(), sc.count());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long active = counts.entrySet().stream()
                .filter(e -> e.getKey().isActive())
                .mapToLong(Map.Entry::getValue).sum();
        long offers = counts.get(ApplicationStatus.OFFER) + counts.get(ApplicationStatus.ACCEPTED);

        LocalDateTime now = LocalDateTime.now(clock);
        List<Interview> upcoming = interviews.findForOwnerBetween(ownerId, now, now.plusDays(UPCOMING_DAYS));
        List<Interview> recent = new ArrayList<>(interviews.findForOwnerBetween(ownerId, now.minusDays(RECENT_DAYS), now));
        Collections.reverse(recent);

        return new DashboardView(total, active, offers, counts, upcoming, recent,
                applications.findTop5ByOwnerIdOrderByUpdatedAtDesc(ownerId));
    }
}
