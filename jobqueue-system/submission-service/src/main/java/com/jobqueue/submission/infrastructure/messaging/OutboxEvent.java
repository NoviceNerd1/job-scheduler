package com.jobqueue.submission.infrastructure.messaging;

import java.time.Instant;

/**
 * Represents an outbox event record read from the outbox table.
 */
public record OutboxEvent(
        Long id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        boolean published,
        Instant createdAt
) {}
