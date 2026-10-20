package com.jobtrack.repository;

import java.util.UUID;

public record OwnerCount(UUID ownerId, Long count) {
}
