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

@Entity
@Table(name = "stock_reconciliation_log")
public class StockReconciliationLog {

    public enum TriggerSource {
        SCHEDULED,
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private long campaignId;

    @Column(name = "database_stock", nullable = false, updatable = false)
    private int databaseStock;

    @Column(name = "redis_stock_before", nullable = false, updatable = false)
    private int redisStockBefore;

    @Column(name = "redis_stock_after", nullable = false, updatable = false)
    private int redisStockAfter;

    @Column(nullable = false, updatable = false)
    private int drift;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20, updatable = false)
    private TriggerSource triggerSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockReconciliationLog() {
    }

    public StockReconciliationLog(
            long campaignId,
            int databaseStock,
            int redisStockBefore,
            TriggerSource triggerSource,
            Instant createdAt
    ) {
        this.campaignId = campaignId;
        this.databaseStock = databaseStock;
        this.redisStockBefore = redisStockBefore;
        this.redisStockAfter = databaseStock;
        this.drift = redisStockBefore - databaseStock;
        this.triggerSource = triggerSource;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getCampaignId() {
        return campaignId;
    }

    public int getDatabaseStock() {
        return databaseStock;
    }

    public int getRedisStockBefore() {
        return redisStockBefore;
    }

    public int getRedisStockAfter() {
        return redisStockAfter;
    }

    public int getDrift() {
        return drift;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
