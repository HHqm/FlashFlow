package com.flashflow.trade.inventory;

import java.time.Duration;

import com.flashflow.trade.common.RedisDistributedLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "flashflow.jobs.stock-reconciliation-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StockReconciliationJob {

    private static final String LOCK_KEY = "flashflow:lock:stock-reconciliation";

    private final StockReconciliationService reconciliationService;
    private final RedisDistributedLock distributedLock;

    public StockReconciliationJob(
            StockReconciliationService reconciliationService,
            RedisDistributedLock distributedLock
    ) {
        this.reconciliationService = reconciliationService;
        this.distributedLock = distributedLock;
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.stock-reconciliation-delay-ms:15000}")
    public void reconcile() {
        distributedLock.tryLock(LOCK_KEY, Duration.ofSeconds(60)).ifPresent(token -> {
            try {
                reconciliationService.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
            } finally {
                distributedLock.unlock(token);
            }
        });
    }
}
