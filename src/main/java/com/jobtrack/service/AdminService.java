package com.jobtrack.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.domain.User;

/** Read-only statistics for administrators: monitoring regular users and site-wide activity. */
public interface AdminService {
    int RECENT_DAYS = 7;
    int UPCOMING_DAYS = 14;
    int ACTIVE_DAYS = 30;

    record UserRow(User user, long applicationCount) {
    }

    record Overview(long totalUsers,
                    long enabledUsers,
                    long disabledUsers,
                    long newUsersRecent,
                    long activeUsers,
                    long totalApplications,
                    long newApplicationsRecent,
                    Map<ApplicationStatus, Long> applicationsByStatus,
                    long upcomingInterviews,
                    long documentCount,
                    long storageBytes,
                    long totalQuestions,
                    long publicQuestions,
                    List<UserRow> recentRegistrations,
                    List<UserRow> mostActiveUsers) {

        public String getStorageLabel() {
            return Text.humanBytes(storageBytes);
        }
    }

    record UserDetail(User user,
                      long applicationCount,
                      Map<ApplicationStatus, Long> applicationsByStatus,
                      long interviewCount,
                      long documentCount,
                      long storageBytes,
                      long questionCount,
                      List<JobApplication> applications) {

        public String getStorageLabel() {
            return Text.humanBytes(storageBytes);
        }
    }

    Overview overview();

    List<UserRow> searchUsers(String q, Boolean enabled);

    UserDetail userDetail(UUID userId);
}
