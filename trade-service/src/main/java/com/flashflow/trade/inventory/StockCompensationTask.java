package com.flashflow.trade.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Durable record of a Redis reservation that must be given back. MySQL is already correct when
 * the task is written; the task only guarantees the cache eventually catches up.
 */
@Entity
@Table(name = "stock_compensation_task")
public class StockCompensationTask {

    public enum Outcome {
        /** The Lua script found the reservation and returned the stock. */
        RESTORED,
        /** The reservation was already returned or has expired, so nothing was changed. */
        NOOP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40, updatable = false)
    private String orderNo;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private long campaignId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private StockCompensationReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCompensationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outcome outcome;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StockCompensationTask() {
    }

    public StockCompensationTask(
            String orderNo,
            long campaignId,
            long userId,
            String idempotencyKey,
            int quantity,
            StockCompensationReason reason,
            Instant now,
            Instant firstAttemptAt
    ) {
        this.orderNo = orderNo;
        this.campaignId = campaignId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.quantity = quantity;
        this.reason = reason;
        this.status = StockCompensationStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = now;
        this.nextAttemptAt = firstAttemptAt;
    }

    public void markDone(Outcome outcome, Instant now) {
        this.status = StockCompensationStatus.DONE;
        this.outcome = outcome;
        this.completedAt = now;
        this.lastError = null;
    }

    public void markFailed(String error, int maxRetries, Instant now) {
        retryCount++;
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
        if (retryCount >= maxRetries) {
            status = StockCompensationStatus.FAILED;
            return;
        }
        long backoffSeconds = Math.min(300, 1L << Math.min(retryCount, 8));
        status = StockCompensationStatus.PENDING;
        nextAttemptAt = now.plusSeconds(backoffSeconds);
    }

    /** Manual retry from the admin console after the root cause has been fixed. */
    public void resetForRetry(Instant now) {
        if (status != StockCompensationStatus.FAILED) {
            throw new IllegalStateException("Only failed compensation tasks can be retried");
        }
        status = StockCompensationStatus.PENDING;
        retryCount = 0;
        nextAttemptAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getCampaignId() {
        return campaignId;
    }

    public long getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getQuantity() {
        return quantity;
    }

    public StockCompensationReason getReason() {
        return reason;
    }

    public StockCompensationStatus getStatus() {
        return status;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
