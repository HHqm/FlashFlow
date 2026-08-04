package com.flashflow.trade.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_event")
public class OutboxEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEntity() {
    }

    public OutboxEntity(
            UUID eventId,
            String eventType,
            String aggregateId,
            String payload,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void markFailed(String error, int maxRetries, Instant now) {
        retryCount++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
        if (retryCount >= maxRetries) {
            status = OutboxStatus.FAILED;
            return;
        }
        long backoffSeconds = Math.min(300, 1L << Math.min(retryCount, 8));
        status = OutboxStatus.PENDING;
        nextAttemptAt = now.plusSeconds(backoffSeconds);
    }
}
