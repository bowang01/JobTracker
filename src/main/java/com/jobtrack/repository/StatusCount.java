package com.jobtrack.repository;

import com.jobtrack.domain.ApplicationStatus;

public record StatusCount(ApplicationStatus status, Long count) {
}
