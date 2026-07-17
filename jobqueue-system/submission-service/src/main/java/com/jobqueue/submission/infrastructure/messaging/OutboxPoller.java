package com.jobqueue.submission.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table every second and publishes unpublished events to Kafka.
 * Implements the Transactional Outbox pattern:
 *   - Events are written atomically with the job record (same DB transaction)
 *   - This poller guarantees at-least-once delivery to Kafka
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String TOPIC = "job.events";

    private final JdbcClient jdbcClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(JdbcClient jdbcClient, KafkaTemplate<String, String> kafkaTemplate) {
        this.jdbcClient = jdbcClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @SuppressWarnings("null")
    public void publishOutboxEvents() {
        List<OutboxEvent> events = jdbcClient.sql("""
                SELECT id, event_id, aggregate_id, event_type, payload, published, created_at
                FROM outbox
                WHERE published = FALSE
                ORDER BY created_at ASC
                LIMIT 100
                """)
                .query((rs, rowNum) -> new OutboxEvent(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getBoolean("published"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();

        if (events.isEmpty()) return;

        log.debug("[OutboxPoller] Publishing {} outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                String aggregateId = event.aggregateId() != null ? event.aggregateId() : "";
                String payload = event.payload() != null ? event.payload() : "";
                kafkaTemplate.send(TOPIC, aggregateId, payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("[OutboxPoller] Failed to publish event id={}: {}",
                                        event.id(), ex.getMessage());
                            } else {
                                markPublished(event.id());
                            }
                        });
            } catch (Exception e) {
                log.error("[OutboxPoller] Error sending event id={}: {}", event.id(), e.getMessage());
                // Will retry on next poll
            }
        }
    }

    private void markPublished(Long eventId) {
        jdbcClient.sql("""
                UPDATE outbox SET published = TRUE, published_at = ?
                WHERE id = ?
                """)
                .param(Timestamp.from(Instant.now()))
                .param(eventId)
                .update();
    }

    /**
     * Write an outbox event atomically alongside a job operation.
     * Call this from within the same transaction as the job save.
     */
    public void writeEvent(String aggregateId, String eventType, String payload) {
        String eventId = java.util.UUID.randomUUID().toString();
        jdbcClient.sql("""
                INSERT INTO outbox (event_id, aggregate_id, event_type, payload, published, created_at)
                VALUES (?, ?, ?, ?::jsonb, FALSE, ?)
                """)
                .param(eventId)
                .param(aggregateId)
                .param(eventType)
                .param(payload)
                .param(Timestamp.from(Instant.now()))
                .update();
        log.debug("[OutboxPoller] Wrote outbox event type={} aggregateId={}", eventType, aggregateId);
    }
}
