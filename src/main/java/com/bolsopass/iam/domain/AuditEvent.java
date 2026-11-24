package com.bolsopass.iam.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID userId,
        String action,
        String resource,
        String details,
        Instant occurredAt
) {
    public AuditEvent {
        Objects.requireNonNull(id, "Audit event ID cannot be null");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action is required");
        }
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Resource is required");
        }
        Objects.requireNonNull(occurredAt, "OccurredAt timestamp cannot be null");
    }
}

