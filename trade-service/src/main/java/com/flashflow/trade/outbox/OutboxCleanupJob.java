package com.flashflow.trade.outbox;

import java.time.Duration;
import java.time.Instant;

import com.flashflow.trade.common.RedisDistributedLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes published Outbox events after the retention period. FAILED events are kept for
 * investigation. Without this the table grows with every order and, once it outgrows the InnoDB
 * buffer pool, every Outbox insert and dispatch scan starts paying for disk reads.
 */
@Component
@ConditionalOnProperty(
        name = "flashflow.jobs.outbox-cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);
    private static final String LOCK_KEY = "flashflow:lock:outbox-cleanup";

    private final OutboxStateService stateService;
    private final RedisDistributedLock distributedLock;
    private final Duration retention;
    private final int chunkSize;
    private final int maxChunksPerRun;
    private final Counter purgedCounter;

    public OutboxCleanupJob(
            OutboxStateService stateService,
            RedisDistributedLock distributedLock,
            @Value("${flashflow.outbox.retention:P7D}") Duration retention,
            @Value("${flashflow.outbox.cleanup-chunk-size:2000}") int chunkSize,
            @Value("${flashflow.outbox.cleanup-max-chunks:20}") int maxChunksPerRun,
            MeterRegistry meterRegistry
    ) {
        this.stateService = stateService;
        this.distributedLock = distributedLock;
        this.retention = retention;
        this.chunkSize = chunkSize;
        this.maxChunksPerRun = maxChunksPerRun;
        this.purgedCounter = meterRegistry.counter("flashflow.outbox.purged");
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.outbox-cleanup-delay-ms:60000}")
    public void purgePublishedEvents() {
        distributedLock.tryLock(LOCK_KEY, Duration.ofMinutes(2)).ifPresent(token -> {
            try {
                int purged = purge(Instant.now().minus(retention));
                if (purged > 0) {
                    log.info("Purged {} published outbox events older than {}", purged, retention);
                }
            } finally {
                distributedLock.unlock(token);
            }
        });
    }

    int purge(Instant cutoff) {
        int total = 0;
        for (int chunk = 0; chunk < maxChunksPerRun; chunk++) {
            // One short transaction per chunk, so the hot write path never waits behind a big delete.
            int deleted = stateService.purgePublishedBefore(cutoff, chunkSize);
            total += deleted;
            purgedCounter.increment(deleted);
            if (deleted < chunkSize) {
                break;
            }
        }
        return total;
    }
}
