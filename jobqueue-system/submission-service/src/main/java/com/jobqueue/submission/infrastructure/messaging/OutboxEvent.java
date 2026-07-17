package com.jobqueue.submission.infrastructure.messaging;

import java.time.Instant;

/**
 * Represents an outbox event record read from the outbox table.
 */
public record OutboxEvent(
        Long id,
        String eventId,
        String aggregateId,
        String eventType,
        String payload,
        boolean published,
        Instant createdAt
) {}
