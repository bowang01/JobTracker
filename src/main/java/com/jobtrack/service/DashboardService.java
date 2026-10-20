package com.jobtrack.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.Interview;
import com.jobtrack.domain.JobApplication;

/** Dashboard statistics. Every query is filtered by ownerId, so users only see their own data. */
public interface DashboardService {
    int UPCOMING_DAYS = 14;
    int RECENT_DAYS = 14;

    record DashboardView(long total,
                         long active,
                         long offers,
                         Map<ApplicationStatus, Long> countsByStatus,
                         List<Interview> upcomingInterviews,
                         List<Interview> recentInterviews,
                         List<JobApplication> recentApplications) {

        public long interviewing() {
            return countsByStatus.getOrDefault(ApplicationStatus.INTERVIEWING, 0L);
        }
    }

    DashboardView build(UUID ownerId);
}
