package com.jobtrack.repository;

import java.time.LocalDate;
import java.util.UUID;

import com.jobtrack.domain.ApplicationStatus;

/**
 * Search conditions for job applications. Every field is optional (null = no condition).
 * {@code ownerId} null means "all owners" and must only be used for admins.
 */
public record ApplicationFilter(UUID ownerId,
                                String company,
                                String position,
                                ApplicationStatus status,
                                LocalDate appliedFrom,
                                LocalDate appliedTo) {
}
