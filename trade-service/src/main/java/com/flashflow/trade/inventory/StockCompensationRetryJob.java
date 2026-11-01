package com.flashflow.trade.inventory;

import java.time.Duration;

import com.flashflow.trade.common.RedisDistributedLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "flashflow.jobs.stock-compensation-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StockCompensationRetryJob {

    private static final String LOCK_KEY = "flashflow:lock:stock-compensation";

    private final StockCompensationService compensationService;
    private final RedisDistributedLock distributedLock;
    private final int batchSize;

    public StockCompensationRetryJob(
            StockCompensationService compensationService,
            RedisDistributedLock distributedLock,
            @Value("${flashflow.stock-compensation.batch-size:100}") int batchSize
    ) {
        this.compensationService = compensationService;
        this.distributedLock = distributedLock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.stock-compensation-delay-ms:5000}")
    public void retryPendingCompensations() {
        // Refresh gauges first: when Redis is down the lock below fails, and that is exactly
        // when the pending count matters most.
        compensationService.refreshGauges();
        distributedLock.tryLock(LOCK_KEY, Duration.ofSeconds(30)).ifPresent(token -> {
            try {
                compensationService.retryDueTasks(batchSize);
            } finally {
                distributedLock.unlock(token);
            }
        });
    }
}
