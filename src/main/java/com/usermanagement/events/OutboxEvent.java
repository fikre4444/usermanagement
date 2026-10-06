package com.usermanagement.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A domain event waiting to be (or already) delivered. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String stream;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected OutboxEvent() {
    }

    OutboxEvent(String stream, String eventType, String aggregateType, String aggregateId, Instant occurredAt,
                String payload) {
        this.id = UUID.randomUUID();
        this.stream = stream;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = occurredAt;
        this.payload = payload;
    }

    void markPublished(Instant now) {
        publishedAt = now;
        lastError = null;
    }

    /** Schedules a retry with exponential backoff, or gives up after {@code maxAttempts}. */
    void markAttemptFailed(Instant now, String error, int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (attempts >= maxAttempts) {
            failedAt = now;
            return;
        }
        long factor = 1L << Math.min(attempts - 1, 20);
        Duration backoff = initialBackoff.multipliedBy(factor);
        nextAttemptAt = now.plus(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }

    OutboxMessage toMessage() {
        return new OutboxMessage(id, stream, eventType, aggregateType, aggregateId, occurredAt, payload);
    }

    public UUID getId() {
        return id;
    }

    public String getStream() {
        return stream;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public int getAttempts() {
        return attempts;
    }
}
