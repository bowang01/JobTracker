package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobtrack.domain.ApplicationStatus;
import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.domain.Visibility;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.repository.InterviewQuestionRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.OwnerCount;
import com.jobtrack.repository.StatusCount;
import com.jobtrack.repository.UserRepository;

import com.jobtrack.service.AdminService;
import com.jobtrack.service.NotFoundException;
import com.jobtrack.service.Text;

/** Read-only statistics for administrators: monitoring regular users and site-wide activity. */
@Service
public class AdminServiceImpl implements AdminService {

    private final UserRepository users;
    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final DocumentRepository documents;
    private final InterviewQuestionRepository questions;
    private final Clock clock;

    public AdminServiceImpl(UserRepository users, JobApplicationRepository applications, InterviewRepository interviews,
                        DocumentRepository documents, InterviewQuestionRepository questions, Clock clock) {
        this.users = users;
        this.applications = applications;
        this.interviews = interviews;
        this.documents = documents;
        this.questions = questions;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Overview

    @Transactional(readOnly = true)
    @Override
    public Overview overview() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<ApplicationStatus, Long> byStatus = statusMap(applications.countByStatusAll());
        long totalApplications = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Map<UUID, Long> appCounts = appCountsByOwner();

        List<UserRow> recent = users.findTop5ByRoleOrderByCreatedAtDesc(Role.USER).stream()
                .map(u -> new UserRow(u, appCounts.getOrDefault(u.getId(), 0L)))
                .toList();

        List<OwnerCount> top = applications.topOwners(PageRequest.of(0, 5));
        Map<UUID, User> topUsers = users.findAllById(top.stream().map(OwnerCount::ownerId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<UserRow> mostActive = top.stream()
                .filter(oc -> topUsers.containsKey(oc.ownerId()))
                .map(oc -> new UserRow(topUsers.get(oc.ownerId()), oc.count()))
                .toList();

        return new Overview(
                users.countByRole(Role.USER),
                users.countByRoleAndEnabled(Role.USER, true),
                users.countByRoleAndEnabled(Role.USER, false),
                users.countByRoleAndCreatedAtGreaterThanEqual(Role.USER, now.minusDays(RECENT_DAYS)),
                users.countByRoleAndLastLoginAtGreaterThanEqual(Role.USER, now.minusDays(ACTIVE_DAYS)),
                totalApplications,
                applications.countByCreatedAtGreaterThanEqual(now.minusDays(RECENT_DAYS)),
                byStatus,
                interviews.countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(now, now.plusDays(UPCOMING_DAYS)),
                documents.count(),
                documents.totalBytes(),
                questions.count(),
                questions.countByVisibility(Visibility.PUBLIC),
                recent,
                mostActive);
    }

    // ------------------------------------------------------------------ User list / detail

    @Transactional(readOnly = true)
    @Override
    public List<UserRow> searchUsers(String q, Boolean enabled) {
        String keyword = Text.trimToNull(q);
        String like = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        Map<UUID, Long> appCounts = appCountsByOwner();
        return users.search(Role.USER, like, enabled).stream()
                .map(u -> new UserRow(u, appCounts.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetail userDetail(UUID userId) {
        User u = users.findById(userId).orElseThrow(NotFoundException::new);
        if (u.isAdmin()) {
            throw new NotFoundException(); // admin accounts have no application data to monitor
        }
        Map<ApplicationStatus, Long> byStatus = statusMap(applications.countByStatusForOwner(userId));
        return new UserDetail(u,
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                interviews.countForOwner(userId),
                documents.countForOwner(userId),
                documents.totalBytesForOwner(userId),
                questions.countByOwnerId(userId),
                applications.findByOwnerIdOrderByUpdatedAtDesc(userId));
    }

    // ------------------------------------------------------------------ Helpers

    private Map<UUID, Long> appCountsByOwner() {
        return applications.countPerOwner().stream()
                .collect(Collectors.toMap(OwnerCount::ownerId, OwnerCount::count));
    }

    private static Map<ApplicationStatus, Long> statusMap(List<StatusCount> counts) {
        Map<ApplicationStatus, Long> m = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            m.put(s, 0L);
        }
        for (StatusCount sc : counts) {
            m.put(sc.status(), sc.count());
        }
        return m;
    }
}
