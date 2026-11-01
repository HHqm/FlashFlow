package com.flashflow.trade.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public enum StockState {
        CONSISTENT,
        /** Drift seen once; repaired only if nothing changes during the quiet window. */
        DRIFT_OBSERVED,
        REPAIRED,
        /** A compensation task for this campaign is still running and will close the gap. */
        PENDING_COMPENSATION,
        /** Redis changed between the read and the compare-and-set, so the repair was skipped. */
        CAS_CONFLICT,
        /** The stock key is gone. Re-warming needs an operator because buyer quotas went with it. */
        MISSING_KEY
    }

    public record CampaignStockView(
            long campaignId,
            int totalStock,
            int databaseStock,
            Integer redisStock,
            Integer drift,
            long pendingCompensations,
            StockState state
    ) {
    }

    public record ReconciliationReport(
            Instant checkedAt,
            long quietWindowSeconds,
            int campaigns,
            int consistent,
            int drifting,
            int repaired,
            List<CampaignStockView> items
    ) {
        static ReconciliationReport of(
                Instant checkedAt,
                Duration quietWindow,
                List<CampaignStockView> items
        ) {
            int consistent = (int) items.stream()
                    .filter(item -> item.state() == StockState.CONSISTENT)
                    .count();
            int repaired = (int) items.stream()
                    .filter(item -> item.state() == StockState.REPAIRED)
                    .count();
            return new ReconciliationReport(
                    checkedAt,
                    quietWindow.toSeconds(),
                    items.size(),
                    consistent,
                    items.size() - consistent - repaired,
                    repaired,
                    items);
        }
    }

    public record CompensationTaskView(
            long id,
            String orderNo,
            long campaignId,
            long userId,
            int quantity,
            StockCompensationReason reason,
            StockCompensationStatus status,
            StockCompensationTask.Outcome outcome,
            int retryCount,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant completedAt
    ) {
        static CompensationTaskView from(StockCompensationTask task) {
            return new CompensationTaskView(
                    task.getId(),
                    task.getOrderNo(),
                    task.getCampaignId(),
                    task.getUserId(),
                    task.getQuantity(),
                    task.getReason(),
                    task.getStatus(),
                    task.getOutcome(),
                    task.getRetryCount(),
                    task.getNextAttemptAt(),
                    task.getLastError(),
                    task.getCreatedAt(),
                    task.getCompletedAt());
        }
    }

    public record ReconciliationLogView(
            long id,
            long campaignId,
            int databaseStock,
            int redisStockBefore,
            int redisStockAfter,
            int drift,
            StockReconciliationLog.TriggerSource triggerSource,
            Instant createdAt
    ) {
        static ReconciliationLogView from(StockReconciliationLog log) {
            return new ReconciliationLogView(
                    log.getId(),
                    log.getCampaignId(),
                    log.getDatabaseStock(),
                    log.getRedisStockBefore(),
                    log.getRedisStockAfter(),
                    log.getDrift(),
                    log.getTriggerSource(),
                    log.getCreatedAt());
        }
    }
}
